#!/usr/bin/env python3
"""
Slayer Companion - wiki data generator (maintainer tool, never shipped to users).

Data flow
=========

    task-enum.txt ----------------------------------------------+
    (ENUM, display name, alt NPC names)                         |
                                                                v
    +-------------+   Slayer task/<name>   +---------------+  build_task()
    | WikiCache   | <--------------------- | resolve_task_ |  one record per
    | (API fetch, |   <singular> / <boss>  | page()        |  display name
    |  disk cache)|                        +---------------+
    |             |   monster page(s)      +---------------+
    |             | <--------------------- | resolve_      |
    +-------------+                        | monster_pages |
          |                                +---------------+
          v  raw wikitext
    parse_infobox_slayer()      requirements, task id, per-master ranges
    parse_gear_sections()       <tabber> labels -> Recommended equipment,
                                Equipment, Inventory, Rune pouch templates
    parse_strategy()            intro + ==Strategy== prose as plain text
    parse_unlocks()             "Related slayer shop options" table
    parse_infobox_monster()     slayer xp, combat, hp, style, max hit, ...
    parse_loclines()            {{LocLine}} -> locations with spawn coords
          |
          v
    merge_locations()           dedupe by (location text, plane) -> stable id
    apply_curated()             curated/verified/*.json overrides + extra locs
    apply_access()              requirements[] text -> access{groups[any[rule]]}
          |
          v
    out/tasks.json  out/locations.json  out/report.json

Standard library only.  All network access goes through WikiCache which
stores every page (and every "missing page" answer) under cache/ so re-runs
are free unless --refresh is given.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

# --------------------------------------------------------------------------
# Constants
# --------------------------------------------------------------------------

USER_AGENT = "SlayerCompanion-datagen/0.1 (github.com/Brettgod1355/Slayer-Companion)"
API_URL = "https://oldschool.runescape.wiki/api.php"
FETCH_SLEEP = 0.5
BACKOFF_SECONDS = (2, 4, 8)

MASTERS = (
    "turael", "spria", "mazchna", "vannaka", "chaeldar",
    "konar", "nieve", "duradel", "krystilia", "mortimer",
)

GEAR_SLOTS = (
    "head", "neck", "cape", "body", "legs", "weapon", "shield",
    "ammo", "hands", "feet", "ring", "special",
)

# Task display name -> exact wiki page to use as the "task page".
# Fill this in as the report shows tasks whose page could not be found.
TASK_PAGE_OVERRIDES: dict[str, str] = {
    "Tzhaar": "Slayer task/TzHaar",
    "Cave kraken": "Slayer task/Cave krakens",
    "Fossil island wyverns": "Slayer task/Fossil Island wyverns",
    "The Cave Kraken Boss": "Kraken",
    "The Thermonuclear Smoke Devil": "Thermonuclear smoke devil",
    "Elves": "Slayer task/Elves",
    "Mutated zygomites": "Slayer task/Zygomites",
}

# Task display name -> list of monster pages that REPLACE the automatic
# singular guess (alternatives from the task list are still added).
MONSTER_PAGE_OVERRIDES: dict[str, list[str]] = {
    "Bloodveld": ["Bloodveld"],
    "The Cave Kraken Boss": ["Kraken"],
    "The Thermonuclear Smoke Devil": ["Thermonuclear smoke devil"],
    # overview pages without {{Infobox Monster}}: use the linked variant pages
    "Barrows Brothers": ["Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
                         "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"],
    "Dagannoth Kings": ["Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"],
    "Kalphites": ["Kalphite Worker", "Kalphite Soldier", "Kalphite Guardian"],
    "Revenants": ["Revenant imp", "Revenant goblin", "Revenant pyrefiend", "Revenant hobgoblin",
                  "Revenant cyclops", "Revenant hellhound", "Revenant demon", "Revenant ork",
                  "Revenant dark beast", "Revenant knight", "Revenant dragon", "Revenant maledictus"],
    "Sea snakes": ["Sea Snake Hatchling", "Sea Snake Young", "Giant Sea Snake"],
    "Tzhaar": ["TzHaar-Ket", "TzHaar-Xil", "TzHaar-Mej", "TzHaar-Hur"],
    "Custodian Stalkers": ["Elder custodian stalker", "Mature custodian stalker",
                           "Custodian stalker (monster)", "Ancient custodian stalker"],
}

# Monster page -> page whose drop lines stand for it, when the monster's own page
# has none (the Grotesque Guardians' shared table is on the boss page; the loot
# comes when Dusk, the last of the pair, dies).
DROP_PAGE_OVERRIDES: dict[str, str] = {
    "Dusk": "Grotesque Guardians",
}

# Tasks whose task page is a monster page but whose {{Infobox Monster}} `cat`
# does not contain "Bosses" although the task is a boss-style assignment.
BOSS_TASK_OVERRIDES = {"TzTok-Jad", "TzKal-Zuk"}

# Task display name -> extra monster pages fetched IN ADDITION to the
# automatic guess and the alternatives (e.g. variants with their own page).
EXTRA_MONSTER_PAGES: dict[str, list[str]] = {
    "Bloodveld": ["Mutated Bloodveld"],
    "Barrows Brothers": ["Barrows"],
    # variant pages that carry the LocLines the curated data refers to
    "Callisto": ["Artio"],                 # Hunter's End
    "Black dragons": ["King Black Dragon",   # King Black Dragon Lair (Wilderness)
                      "Brutal black dragon", "Baby black dragon"],  # Catacombs of Kourend
    "Rats": ["Brine rat",                  # Brine Rat Cavern
             "Crypt rat",                  # Barrows crypt (alias)
             "Giant rat"],                 # Lumbridge Swamp
    "Gryphons": ["Shellbane gryphon"],     # Shellbane Gryphon Cave ({{Map}} on the boss page)
    "Mutated zygomites": ["Ancient Zygomite"],  # Fossil Island (Konar's other zygomite area)
    # Every page below has an {{Infobox Monster}} whose Slayer `cat` names the
    # task (checked when added); each supplies the LocLine of a curated location.
    "Aberrant spectres": ["Deviant spectre"],        # Catacombs of Kourend
    "Banshees": ["Twisted Banshee"],                 # Catacombs of Kourend
    "Bats": ["Giant bat", "Albino bat"],                           # Coal Trucks, Taverley Dungeon
    "Bears": ["Grizzly bear", "Black bear",          # Ardougne, Mind Altar, Varrock, Wilderness, ...
              "Bear cub", "Grizzly bear cub"],                             # Woods outside of Rellekka (alias)
    "Blue dragons": ["Brutal blue dragon", "Baby blue dragon"],          # Catacombs of Kourend (north-west)
    "Chaos druids": ["Elder Chaos druid"],           # Chaos Temple (Wilderness)
    "Cockatrice": ["Moonlight Cockatrice"],          # Neypotzli - Earthbound Cavern
    "Dagannoth": ["Dagannoth (Waterbirth Island)", "Dagannoth spawn", "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"],  # Waterbirth Island Dungeon (alias)
    "Dogs": ["Wild dog", "Guard dog", "Shadow Hound"],               # Brimhaven Dungeon, Handelmort Mansion, Hosidius
    "Dwarves": ["Chaos dwarf", "Dwarf gang member", "Black Guard Berserker", "Guard (dwarf)"],                      # Taverley Dungeon, Deep Wilderness Dungeon
    "Elves": ["Elf Warrior", "Elf Archer", "Guard (Prifddinas)"],  # Lletya, Prifddinas
    "Goblins": ["Cave goblin miner", "Cave goblin guard",          # Dorgeshuun Mines
                "Cave goblin (monster)",                           # Lumbridge Swamp Caves
                "Goblin (Goblin Village)", "Goblin (God Wars Dungeon)"],                        # Goblin Village
    "Hill giants": ["Cyclops (God Wars Dungeon)", "Cyclops (Catacombs of Kourend)"],   # God Wars Dungeon
    "Jellies": ["Warped Jelly", "Chilled jelly"],    # Catacombs, Grimstone Dungeon, Ruins of Tapoyauik
    "Lesser Nagua": ["Earthen Nagua"],               # Tonali Cavern - Sun Chamber
    "Lizardmen": ["Lizardman shaman"],               # Lizardman Caves, Lizardman Temple
    "Lizards": ["Sulphur lizard", "Grimy lizard", "Desert Lizard"],  # Karuulm, Neypotzli, Ullek
    "Mogres": ["Mogre (sea)"],                       # Ardent Ocean (alias)
    "Monkeys": ["Monkey (monster)", "Monkey Zombie", "Monkey Guard", "Maniacal monkey", "Monkey Archer"],  # Karamja, zoo, Ape Atoll, Marimbo
    "Moss giants": ["Moss Giant (Iorwerth Dungeon)"],  # Iorwerth Dungeon
    "Nechryael": ["Greater Nechryael"],              # Catacombs, Iorwerth Dungeon, Wilderness Slayer Cave
    "Ogres": ["Ogress Warrior", "Ogress Shaman", "Ogre chieftain", "Mogre (sea)"],    # Corsair Cove Dungeon
    "Pirates": ["Zombie pirate"],                    # Chaos Temple (Wilderness)
    "Red dragons": ["Brutal red dragon"],            # Catacombs of Kourend
    "Scabarites": ["Small scarab"],                  # Uzer Mastaba - lower level
    "Scorpions": ["King Scorpion"],                  # Lava Maze
    "Skeletons": ["Skeleton (Ape Atoll)", "Skeleton (Catacombs of Kourend)",
                  "Skeleton (Stronghold of Security)"],  # Ape Atoll Dungeon, Catacombs, SoS (alias)
    "Spiders": ["Giant spider"],                     # East side of Lumbridge
    "Trolls": ["Mountain troll", "Ice troll",        # Trollheim, Keldagrim tunnel, Ice Path, ...
               "Ice troll runt", "Ice troll male", "Ice troll female", "Ice troll grunt", "Rock (Troll)", "Troll general", "Thrower Troll", "Thrower troll (Trollheim)", "Troll spectator"],  # Fremennik Isles
    "Vampyres": ["Feral Vampyre", "Venator", "Vyrewatch Sentinel", "Vampyre Juvinate"],        # Haunted Woods, Burgh de Rott, GWD; Apsul
    "Zombies": ["Undead Druid", "Zombie pirate",     # Forthos Dungeon, Chaos Temple
                "Zombie (Wilderness)",               # Graveyard of Shadows, Ruins (east)
                "Zombie (Stronghold of Security)",   # Catacomb of Famine
                "Undead cow", "Undead chicken"],     # Alice's farm
    # instanced encounters: the entrance from the activity/location page's
    # infobox {{Map}} (parse_map_locations fallback)
    "TzTok-Jad": ["TzHaar Fight Cave"],
    "TzKal-Zuk": ["Inferno"],
    "Zulrah": ["Zul-Andra"],
    "Bandits": ["Guard Bandit"],
    "Basilisks": ["Basilisk Knight"],
    "Birds": ["Penguin (monster)", "Undead chicken", "Mounted terrorbird gnome", "Entrana firebird", "Oomlie bird"],
    "Cows": ["Cow calf", "Undead cow"],
    "Ghosts": ["Ghost (Melzar's Maze)", "Ghost (Grave of Scorpius)"],
    # ^ the last additions (Guard Bandit ... Vampyre Juvinate, Dagannoth Kings on a dagannoth task) came from the
    # 2026-09-24 coverage research: each page's infobox `cat` names the task and its {{LocLine}}s were checked.
}

# Recommended-equipment tables mostly live on "<Monster>/Strategies" subpages.
# resolve_strategy_pages() tries "<task page>/Strategies" and
# "<primary monster page>/Strategies" automatically; these tables add pages
# that do not follow that pattern.
#
# STRATEGY_PAGE_OVERRIDES: the task's own strategy page under another name;
# tab labels are kept as they are.
STRATEGY_PAGE_OVERRIDES: dict[str, list[str]] = {
    "Barrows Brothers": ["Barrows/Strategies"],
    "TzTok-Jad": ["TzHaar Fight Cave/Strategies"],
    "TzKal-Zuk": ["Inferno/Strategies"],
    "Metal dragons": ["Metal dragons/Strategies"],  # bronze, iron and steel dragons
}
# STRATEGY_VARIANT_PAGES: strategy pages of a variant that counts for the task
# but is not the task's own monster; each label gets the page subject as
# prefix ("Artio: Melee").
STRATEGY_VARIANT_PAGES: dict[str, list[str]] = {
    "Metal dragons": ["Mithril dragon/Strategies", "Adamant dragon/Strategies",
                      "Rune dragon/Strategies"],
    "Black dragons": ["Brutal black dragon/Strategies"],
    "Basilisks": ["Basilisk Knight/Strategies"],
    "Lizardmen": ["Lizardman shaman/Strategies"],
    "Callisto": ["Artio/Strategies"],
    "Vet'ion": ["Calvar'ion/Strategies"],
    "Venenatis": ["Spindel/Strategies"],
}

# GEAR_PAGES: further wiki pages with {{Recommended equipment}} tables for a task,
# found by the gear research pass (2026-09-24) and verified with the generator's own
# parser.  Each entry: {"page": title, "variant": monster name or None, "tabs": [labels]}.
# "variant" attributes the tables to one selectable variant (the plugin shows them when
# that variant is chosen); None adds them as the task's own.  "tabs", when given, keeps
# only tables whose label is listed (pages with tabs for several monsters).
GEAR_PAGES: dict[str, list[dict]] = {
    "Abyssal demons": [{"page": "Abyssal Sire/Strategies", "variant": "Abyssal Sire"}],
    "Araxytes": [{"page": "Araxxor/Strategies", "variant": "Araxxor"}],
    "Aviansies": [{"page": "Kree'arra/Strategies", "variant": "Kree'arra"}],
    "Bears": [{"page": "Callisto/Strategies", "variant": "Callisto", "tabs": ["Ranged", "Magic"]},
              {"page": "Artio/Strategies", "variant": "Artio", "tabs": ["Ranged", "Magic"]}],
    "Black demons": [{"page": "Demonic gorilla/Strategies", "variant": "Demonic gorilla"},
                     {"page": "Skotizo/Strategies", "variant": "Skotizo"}],
    "Black dragons": [{"page": "King Black Dragon/Strategies", "variant": "King Black Dragon"}],
    "Blue dragons": [{"page": "Vorkath/Strategies", "variant": "Vorkath", "tabs": ["Ranged", "Melee"]}],
    "Cave kraken": [{"page": "Kraken/Strategies", "variant": "Kraken", "tabs": ["Equipment"]}],
    "Cows": [{"page": "Brutus/Strategies", "variant": "Brutus"}],
    "Custodian Stalkers": [{"page": "Slayer task/Custodian stalker",
                            "tabs": ["Ranged (Multicombat)", "Magic (Multicombat)", "Melee (Single combat)"]}],
    "Dagannoth Kings": [{"page": "Dagannoth Kings/Strategies", "variant": "Dagannoth Rex", "tabs": ["Magic (Rex only)"]},
                        {"page": "Dagannoth Kings/Strategies", "variant": "Dagannoth Prime", "tabs": ["Ranged (Prime only)"]}],
    "Fire giants": [{"page": "Royal Titans/Strategies", "variant": "Branda the Fire Queen"}],
    "Greater demons": [{"page": "K'ril Tsutsaroth/Strategies", "variant": "K'ril Tsutsaroth"},
                       {"page": "Skotizo/Strategies", "variant": "Skotizo"},
                       {"page": "Tormented Demon/Strategies", "variant": "Tormented Demon"}],
    "Gryphons": [{"page": "Shellbane gryphon/Strategies", "variant": "Shellbane gryphon"}],
    "Hellhounds": [{"page": "Cerberus/Strategies", "variant": "Cerberus", "tabs": ["Melee", "Ranged"]}],
    "Ice giants": [{"page": "Royal Titans/Strategies", "variant": "Eldric the Ice King"}],
    "Kurask": [{"page": "Slayer task/Kurasks", "tabs": ["Melee", "Range", "Mage"]}],
    "Lesser Nagua": [{"page": "Amoxliatl/Strategies", "variant": "Amoxliatl"}],
    # Wilderness-only tasks: Krystilia's general Wilderness Slayer melee setup
    "Mammoths": [{"page": "Krystilia/Strategies", "tabs": ["Melee"]}],
    "Magic axes": [{"page": "Krystilia/Strategies", "tabs": ["Melee"]}],
    "Monkeys": [{"page": "Demonic gorilla/Strategies", "variant": "Demonic gorilla"},
                {"page": "Maniacal monkey/Strategies", "variant": "Maniacal monkey"}],
    "Moss giants": [{"page": "Money making guide/Killing Bryophyta (free-to-play)", "variant": "Bryophyta", "tabs": ["Killing Bryophyta"]},
                    {"page": "Money making guide/Killing Bryophyta (free-to-play)", "variant": "Moss giant", "tabs": ["Obtaining keys"]}],
    "Nechryael": [{"page": "Slayer task/Nechryael", "variant": "Greater Nechryael", "tabs": ["Magic (Greater Nechryael)"]}],
    "Pirates": [{"page": "Money making guide/Killing zombie pirates (Max efficiency)", "variant": "Zombie pirate", "tabs": ["Gear setup"]},
                {"page": "Money making guide/Killing zombie pirates (Budget)", "variant": "Zombie pirate", "tabs": ["Strategy"]}],
    "Scorpions": [{"page": "Scorpia/Strategies", "variant": "Scorpia"}],
    "Skeletons": [{"page": "Slayer task/Skeletons", "variant": "Skeleton (Ape Atoll)", "tabs": ["Ranged", "Magic"]},
                  {"page": "Vet'ion/Strategies", "variant": "Vet'ion"},
                  {"page": "Calvar'ion/Strategies", "variant": "Calvar'ion"}],
    "Spiders": [{"page": "Sarachnis/Strategies", "variant": "Sarachnis"},
                {"page": "Venenatis/Strategies", "variant": "Venenatis"},
                {"page": "Spindel/Strategies", "variant": "Spindel"},
                {"page": "Araxxor/Strategies", "variant": "Araxxor", "tabs": ["Melee", "Ranged"]}],
    "Trolls": [{"page": "Slayer task/Trolls", "variant": "Mountain troll",
                "tabs": ["Melee (Low level)", "Magic (Low level)", "Melee (Mountain trolls)", "Magic"]},
               {"page": "Slayer task/Trolls", "variant": "Ice troll", "tabs": ["Magic (Low level)", "Magic"]},
               {"page": "Slayer task/Trolls", "variant": "Ice troll runt", "tabs": ["Melee (Ice trolls)"]},
               {"page": "Slayer task/Trolls", "variant": "Ice troll male", "tabs": ["Melee (Ice trolls)"]},
               {"page": "Slayer task/Trolls", "variant": "Ice troll female", "tabs": ["Melee (Ice trolls)"]},
               {"page": "Slayer task/Trolls", "variant": "Ice troll grunt", "tabs": ["Melee (Ice trolls)"]}],
    "Tzhaar": [{"page": "TzHaar Fight Cave/Strategies", "variant": "TzTok-Jad"},
               {"page": "Inferno/Strategies", "variant": "TzKal-Zuk"}],
    "Vampyres": [{"page": "Venator/Strategies", "variant": "Venator", "tabs": ["Equipment"]},
                 {"page": "Money making guide/Slaying Venators", "variant": "Venator"}],
    "Venators": [{"page": "Money making guide/Slaying Venators"}],
    "Zombies": [{"page": "Vorkath/Strategies", "variant": "Vorkath"},
                {"page": "Money making guide/Killing zombie pirates (Max efficiency)", "variant": "Zombie pirate"},
                {"page": "Money making guide/Killing zombie pirates (Budget)", "variant": "Zombie pirate"}],
}

# EXTRA_LOCATION_PAGES: pages whose {{LocLine}}s give coordinates for a task's spot
# without being a variant of their own.  Each entry: {"page": title, "monster": the
# variant name the spawns count as, "rename": location name to file them under (so
# they merge into one spot and meet the curated record), "only": [LocLine location
# names to keep] (optional)}.
EXTRA_LOCATION_PAGES: dict[str, list[dict]] = {
    # The Canifis citizens' LocLines are where each one turns into a werewolf.
    "Werewolves": [{"page": n, "monster": "Werewolf", "rename": "Canifis"} for n in (
        "Boris", "Imre", "Yuri", "Joseph", "Nikolai", "Eduard", "Lev", "Georgy", "Svetlana", "Irina",
        "Alexis", "Milla", "Galina", "Sofiya", "Ksenia", "Yadviga", "Nikita", "Vera", "Zoja", "Liliya")],
    # Only the level-108 mourners in the headquarters' tunnels are elves.
    "Elves": [{"page": "Mourner", "monster": "Mourner", "rename": "Mourner Headquarters",
               "only": ["Mourner Tunnels"]}],
    # The Vault of War rooms (first level of the Stronghold of Security).
    "Goblins": [{"page": "Goblin (Vault of War)", "monster": "Goblin", "rename": "Stronghold of Security"}],
}

# Curated location names that the normalised-name passes of apply_curated()
# cannot match (or match ambiguously), per task display name.  The key is the
# curated record's displayName or name (displayName is looked up first so two
# curated records with the same name can be told apart); the value is the id
# of the generated location to use.  None forces "no match" when the loose
# passes would otherwise pick a wrong record (the curated record is then
# appended without coordinates, as for any unmatched curated location).
CURATED_LOCATION_ALIASES: dict[str, dict[str, str | None]] = {
    "Bats": {"Abandoned Mine": "abandoned-mine-level-1-p0"},
    "Birds": {"Farmer Fred's chicken pen": "lumbridge-west-farm-p0",
              "River Lum": "along-the-river-lum-south-of-varrock-p0"},
    "Black dragons": {"Corsair Cove Dungeon": "corsair-cove-dungeon-myths-guild-basement-p0"},
    "Blue dragons": {"Corsair Cove Dungeon": "corsair-cove-dungeon-myths-guild-basement-p1"},
    "Crabs": {"Ruins of Tapoyauik": "ruins-of-tapoyauik-middle-floor-p1"},
    "Custodian Stalkers": {
        "Stalker Den – south-western caves (multicombat, cannonable)": "stalker-den-multicombat-p0",
        "Stalker Den – single-way caves (south-east and north)": "stalker-den-single-combat-p0"},
    "Dark warriors": {"Dark Warriors' Fortress": "dark-warriors-fortress-p0"},
    "Fire giants": {"Brimhaven Dungeon": "brimhaven-dungeon-p1"},
    "Gryphons": {"Shellbane Gryphon Cave": "shellbane-gryphon-p0"},
    "Hobgoblins": {"Tree Gnome Village dungeon": "tree-gnome-village-dungeon-roaming-p0"},
    "The Maggot King": {"Vampyrium": "maggot-king-p0"},
    "Moss giants": {"Tonali Cavern": "tonali-cavern-southern-chamber-p0"},
    "Rats": {"Stronghold of Security": "stronghold-of-security-vault-of-war-p0",
             "Barrows crypt": "barrows-tunnels-p0"},  # Crypt rat LocLine "Barrows (tunnels)"
    "Rogues": {"Rogues' Castle": "rogues-castle-p0"},
    "Scorpions": {"Stonecutter Outpost temple": "south-west-of-stonecutter-outpost-p0"},
    "The Shellbane Gryphon": {"Shellbane Gryphon Cave": "shellbane-gryphon-p0"},
    "Spiders": {"Forthos Dungeon": "forthos-dungeon-burial-tomb-p0"},
    "Trolls": {"Troll Stronghold (Surface)": "troll-stronghold-surface-p0",        # Mountain troll LocLines
               "Troll Stronghold (Underground)": "troll-stronghold-underground-p0",
               "Northern Jatizso and Neitiznot": "fremennik-isles-p0"},          # ice troll variants
    "Barrows Brothers": {"East of Mort'ton": "barrows-p0"},       # Barrows infobox {{Map}}
    "Bears": {"Fremennik Province - woods outside of Rellekka": "woods-outside-of-rellekka-p0",  # Bear Cub
              # the task page's map pins for this row are the 10 Grizzly bear spawns of this LocLine
              "West of the Graveyard of Shadows": "north-west-of-the-ferox-enclave-p0"},
    "Elves": {"Lletya": "lletya-p0"},                          # ground floor (1st floor has 1 archer)
    "Monkeys": {"Temple of Marimbo": "temple-of-marimbo-p0"},  # ground floor (9 of 12 Monkey Guards)
    "TzTok-Jad": {"Mor Ul Rek - Fight Caves": "tzhaar-fight-cave-p0"},  # cave entrance in Mor Ul Rek
    "Zulrah": {"Zulrah's Shrine (east of Zul-Andra)": "zul-andra-p0"},  # boat to the shrine
    "Dagannoth": {"Waterbirth Island Dungeon": "waterbirth-island-dungeon-entrance-hall-p0"},
    "Mogres": {"Ardent Ocean": "mudskipper-sound-south-of-musa-point-p0"},  # first of the three straits
    "Skeletons": {"Stronghold of Security": "stronghold-of-security-sepulchre-of-death-level-4-p0"},
    "Vampyres": {"Meiyerditch": "meiyerditch-p0"},
    "Zombies": {"Varrock Sewers": "varrock-sewers-hallway-p0"},
}

# Wilderness surface bounding box (plane 0), used only as a hint that curated
# data can override.
WILDERNESS_BOX = (2944, 3520, 3392, 3970)


# --------------------------------------------------------------------------
# Wikitext primitives
# --------------------------------------------------------------------------

@dataclass
class Template:
    start: int
    end: int
    name: str      # lowercase, underscores -> spaces
    body: str      # text after the first '|' (without the braces)

    @property
    def args(self) -> tuple[list[str], dict[str, str]]:
        return split_args(self.body)


def find_templates(text: str, names: Iterable[str] | None = None) -> list[Template]:
    """Return every template (nested ones included) whose lowercase name is in
    `names` (or all templates when names is None), ordered by start offset."""
    wanted = None if names is None else {n.lower() for n in names}
    found: list[Template] = []
    stack: list[int] = []
    i, n = 0, len(text)
    while i < n - 1:
        if text[i] == "{" and text[i + 1] == "{":
            stack.append(i)
            i += 2
            continue
        if text[i] == "}" and text[i + 1] == "}" and stack:
            start = stack.pop()
            inner = text[start + 2:i]
            m = re.match(r"\s*([^|{}]*?)\s*(\||$)", inner, re.S)
            name = m.group(1).replace("_", " ").strip().lower() if m else ""
            body = inner[m.end():] if (m and m.group(2) == "|") else ""
            if wanted is None or name in wanted:
                found.append(Template(start, i + 2, name, body))
            i += 2
            continue
        i += 1
    found.sort(key=lambda t: t.start)
    return found


def _split_top_level(body: str, sep: str = "|") -> list[str]:
    parts: list[str] = []
    cur: list[str] = []
    depth_t = depth_l = 0
    i, n = 0, len(body)
    while i < n:
        two = body[i:i + 2]
        if two == "{{":
            depth_t += 1
            cur.append(two)
            i += 2
            continue
        if two == "}}":
            depth_t -= 1
            cur.append(two)
            i += 2
            continue
        if two == "[[":
            depth_l += 1
            cur.append(two)
            i += 2
            continue
        if two == "]]":
            depth_l -= 1
            cur.append(two)
            i += 2
            continue
        if body[i] == sep and depth_t == 0 and depth_l == 0:
            parts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(body[i])
        i += 1
    parts.append("".join(cur))
    return parts


def split_args(body: str) -> tuple[list[str], dict[str, str]]:
    """Split a template body into positional args and named args (keys lowercased)."""
    positional: list[str] = []
    named: dict[str, str] = {}
    for part in _split_top_level(body):
        eq = -1
        depth_t = depth_l = 0
        for j in range(len(part)):
            two = part[j:j + 2]
            if two == "{{":
                depth_t += 1
            elif two == "}}":
                depth_t -= 1
            elif two == "[[":
                depth_l += 1
            elif two == "]]":
                depth_l -= 1
            elif part[j] == "=" and depth_t == 0 and depth_l == 0:
                eq = j
                break
        key = part[:eq] if eq >= 0 else ""
        if eq >= 0 and re.fullmatch(r"\s*[\w\s\-']+\s*", key):
            named[key.strip().lower()] = part[eq + 1:].strip()
        else:
            positional.append(part.strip())
    return positional, named


_INNER_TEMPLATE = re.compile(r"\{\{([^{}]*)\}\}", re.S)


def _ordinal(n: int) -> str:
    if 10 <= n % 100 <= 20:
        suf = "th"
    else:
        suf = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f"{n}{suf}"


def render_template_text(inner: str) -> str:
    """Plain-text rendering of one (innermost) template; unknown templates render as ''."""
    positional, named = split_args(inner)
    name = positional[0].replace("_", " ").strip().lower() if positional else ""
    rest = positional[1:]
    if name in ("scp", "skill clickpic", "skill"):
        if len(rest) >= 2:
            return f"{rest[1]} {rest[0]}"
        return " ".join(rest)
    if name == "fairycode":
        return "fairy ring " + (rest[0].upper() if rest else "")
    if name == "floornumber":
        uk = named.get("uk")
        if uk is not None and uk.strip().lstrip("-").isdigit():
            k = int(uk)
            if k == 0:
                return "ground floor"
            if k < 0:
                return f"basement {abs(k)}"
            return f"{_ordinal(k)} floor"
        return "floor"
    if name in ("plink", "plinkp", "plinkt", "plinkl"):
        return named.get("txt") or (rest[0].split("#")[0] if rest else "")
    if name in ("nowrap", "nobr"):
        return rest[0] if rest else ""
    if name == "coins":
        return (rest[0] + " coins") if rest else ""
    if name == "na":
        return "N/A"
    if name in ("yes", "no"):
        return name.capitalize()
    if name in ("cannon",):
        return "cannon"
    return ""


def render_templates(text: str) -> str:
    prev = None
    while prev != text:
        prev = text
        text = _INNER_TEMPLATE.sub(lambda m: render_template_text(m.group(1)), text)
    return text


def remove_templates(text: str, names: Iterable[str] | None = None) -> str:
    """Remove (outermost) templates, optionally only those with the given names."""
    tpls = find_templates(text, names)
    out = []
    pos = 0
    last_end = -1
    for t in tpls:
        if t.start < last_end:
            continue  # nested inside an already-removed template
        out.append(text[pos:t.start])
        pos = t.end
        last_end = t.end
    out.append(text[pos:])
    return "".join(out)


def remove_files(text: str) -> str:
    out = []
    i, n = 0, len(text)
    while i < n:
        if text.startswith("[[", i) and re.match(r"\[\[\s*(File|Image|Media):", text[i:i + 14], re.I):
            depth = 0
            j = i
            while j < n:
                if text.startswith("[[", j):
                    depth += 1
                    j += 2
                    continue
                if text.startswith("]]", j):
                    depth -= 1
                    j += 2
                    if depth == 0:
                        break
                    continue
                j += 1
            i = j
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def remove_tables(text: str) -> str:
    out = []
    depth = 0
    for line in text.split("\n"):
        s = line.lstrip()
        if s.startswith("{|"):
            depth += 1
            continue
        if s.startswith("|}") and depth > 0:
            depth -= 1
            continue
        if depth == 0:
            out.append(line)
    return "\n".join(out)


def links_to_text(text: str) -> str:
    for _ in range(3):
        text = re.sub(r"\[\[([^\[\]|]*)\|([^\[\]]*)\]\]", r"\2", text)
        text = re.sub(r"\[\[([^\[\]|]*)\]\]", r"\1", text)
    text = re.sub(r"\[(?:https?|ftp)://[^\s\]]+\s+([^\]]*)\]", r"\1", text)
    text = re.sub(r"\[(?:https?|ftp)://[^\s\]]+\]", "", text)
    return text


def strip_refs(text: str) -> str:
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<ref[^>/]*/\s*>", "", text, flags=re.I)
    text = re.sub(r"<ref[^>]*>.*?</ref\s*>", "", text, flags=re.S | re.I)
    text = re.sub(r"<gallery[^>]*>.*?</gallery\s*>", "", text, flags=re.S | re.I)
    text = re.sub(r"<(?:math|score)[^>]*>.*?</(?:math|score)\s*>", "", text, flags=re.S | re.I)
    return text


def plain_text(wikitext: str) -> str:
    """Convert a wikitext fragment to plain text (templates rendered or dropped,
    link text kept, refs / files / tables dropped)."""
    t = strip_refs(wikitext)
    t = remove_tables(t)
    t = render_templates(t)
    t = remove_files(t)
    t = links_to_text(t)
    t = re.sub(r"<br\s*/?\s*>", "\n", t, flags=re.I)
    t = re.sub(r"</?br\s*>", "\n", t, flags=re.I)
    t = re.sub(r"'''''|'''|''", "", t)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t)
    # The panel calls her "Konar" everywhere.
    t = t.replace("Konar quo Maten", "Konar")
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r" *\n *", "\n", t)
    t = re.sub(r"\(\s*\)", "", t)
    t = re.sub(r" +([,.;:])", r"\1", t)
    return t.strip()


def paragraphs(plain: str) -> list[str]:
    """Split plain text into paragraphs; bullet lines are grouped into one paragraph."""
    paras: list[str] = []
    cur: list[str] = []
    cur_is_list = False
    for raw in plain.split("\n"):
        line = raw.strip()
        if not line:
            if cur:
                paras.append("\n".join(cur))
                cur, cur_is_list = [], False
            continue
        m = re.match(r"^(={2,6})\s*(.+?)\s*=+$", line)
        if m:
            if cur:
                paras.append("\n".join(cur))
                cur, cur_is_list = [], False
            paras.append(m.group(2) + ":")
            continue
        is_list = line[0] in "*#;:"
        if is_list:
            line = "- " + line.lstrip("*#;: ").strip()
            if cur and not cur_is_list:
                paras.append("\n".join(cur))
                cur = []
            cur_is_list = True
            cur.append(line)
        else:
            if cur and cur_is_list:
                paras.append("\n".join(cur))
                cur = []
            cur_is_list = False
            cur.append(line)
    if cur:
        paras.append("\n".join(cur))
    paras = [p for p in paras if p and re.search(r"[A-Za-z0-9]", p)]
    # drop heading-only paragraphs whose section produced no prose (e.g. a
    # heading followed only by a table that was stripped)
    cleaned: list[str] = []
    for i, p in enumerate(paras):
        is_heading = p.endswith(":") and "\n" not in p and len(p) < 80
        nxt = paras[i + 1] if i + 1 < len(paras) else None
        if is_heading and (nxt is None or (nxt.endswith(":") and "\n" not in nxt and len(nxt) < 80)):
            continue
        cleaned.append(p)
    return cleaned


_HEADING = re.compile(r"^(={2,6})\s*(.+?)\s*=+\s*$", re.M)


def sections(text: str) -> list[dict]:
    """Return [{level, title, start(body start), end(body end), head_start}]."""
    heads = list(_HEADING.finditer(text))
    out = []
    for i, m in enumerate(heads):
        level = len(m.group(1))
        body_start = m.end()
        end = len(text)
        for later in heads[i + 1:]:
            if len(later.group(1)) <= level:
                end = later.start()
                break
        out.append({"level": level, "title": m.group(2).strip(), "start": body_start,
                    "end": end, "head_start": m.start()})
    return out


def to_int(value) -> int | None:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    m = re.search(r"-?\d+", s)
    if not m:
        return None
    try:
        return int(m.group(0))
    except ValueError:
        return None


def slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s or "unnamed"


# --------------------------------------------------------------------------
# Fetching with cache
# --------------------------------------------------------------------------

@dataclass
class Page:
    requested: str
    title: str | None
    wikitext: str | None
    redirected: bool
    status: str            # ok | missing | error
    error: str | None = None

    @property
    def ok(self) -> bool:
        return self.status == "ok" and self.wikitext is not None


def sanitise_title(title: str) -> str:
    return title.replace("/", "__").replace(" ", "_").replace(":", "_")


# --------------------------------------------------------------------------
# Bucket: the wiki's structured-data API for external tools
# --------------------------------------------------------------------------
#
# The OSRS Wiki asks external users to read its data through Bucket
# (api.php?action=bucket, https://oldschool.runescape.wiki/w/RuneScape:Bucket)
# "without needing to scrape or parse wiki pages".  The generator asks Bucket
# first and only reads a page for what Bucket does not hold (location names of
# {{LocLine}}s, gear tab labels and tiers, {{Infobox Slayer}}, prose).

BUCKET_PAGE_SIZE = 5000
MONSTER_BUCKET_FIELDS = ["page_name", "page_name_sub", "default_version", "name", "id", "combat_level",
                         "hitpoints", "size", "attack_speed", "slayer_level", "slayer_experience",
                         "slayer_category", "assigned_by", "cannon_immune"]


class BucketClient:
    """Runs Bucket queries, caching every answer under cache/bucket/ (one file per query).

    A failed query returns None; callers then fall back to reading pages."""

    def __init__(self, cache_dir: Path, refresh: bool = False, sleep: float = FETCH_SLEEP):
        self.dir = cache_dir / "bucket"
        self.dir.mkdir(parents=True, exist_ok=True)
        self.refresh = refresh
        self.sleep = sleep
        self.fetches = 0
        self.failed: dict[str, str] = {}

    def run(self, query: str) -> list[dict] | None:
        import hashlib
        path = self.dir / (hashlib.sha1(query.encode("utf-8")).hexdigest() + ".json")
        if not self.refresh and path.exists():
            return json.loads(path.read_text(encoding="utf-8"))["rows"]
        url = API_URL + "?" + urllib.parse.urlencode({"action": "bucket", "query": query, "format": "json"})
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        for attempt in range(len(BACKOFF_SECONDS) + 1):
            try:
                self.fetches += 1
                print(f"  bucket [{self.fetches}] {query[:100]}", file=sys.stderr)
                with urllib.request.urlopen(req, timeout=120) as resp:
                    data = json.load(resp)
                time.sleep(self.sleep)
                break
            except urllib.error.HTTPError as e:
                if e.code == 429 and attempt < len(BACKOFF_SECONDS):
                    time.sleep(BACKOFF_SECONDS[attempt])
                    continue
                self.failed[query] = f"HTTP {e.code}"
                return None
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
                self.failed[query] = str(e)
                return None
        if "error" in data or "bucket" not in data:
            self.failed[query] = str(data.get("error", "no bucket in answer"))
            return None
        rows = data["bucket"]
        path.write_text(json.dumps({"query": query, "fetched": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                                    "rows": rows}, ensure_ascii=False), encoding="utf-8")
        return rows

    def table(self, bucket: str, fields: list[str]) -> list[dict] | None:
        """Every row of a bucket (the selected fields), fetched BUCKET_PAGE_SIZE rows at a time."""
        out: list[dict] = []
        offset = 0
        select = ",".join(f"'{f}'" for f in fields)
        while True:
            rows = self.run(f"bucket('{bucket}').select({select}).limit({BUCKET_PAGE_SIZE}).offset({offset}).run()")
            if rows is None:
                return None
            out += rows
            if len(rows) < BUCKET_PAGE_SIZE:
                return out
            offset += BUCKET_PAGE_SIZE


class BucketData:
    """The parts of Bucket the generator uses, loaded once per run."""

    def __init__(self, client: BucketClient | None):
        self.monsters: dict[str, list[dict]] = {}
        self.gear_pages: set[str] | None = None
        if client is None:
            return
        rows = client.table("infobox_monster", MONSTER_BUCKET_FIELDS)
        for r in rows or []:
            self.monsters.setdefault(r.get("page_name"), []).append(r)
        gear = client.table("recommended_equipment", ["page_name"])
        if gear is not None:
            self.gear_pages = {r.get("page_name") for r in gear if r.get("page_name")}


BUCKET: BucketData = BucketData(None)


def _bucket_scalar(rows: list[dict], field: str, conv):
    """The field's value when every row (version) of the page that has it agrees, else None."""
    values = []
    for r in rows:
        if r.get(field) not in (None, "", []):
            v = conv(r[field])
            if v is not None and v not in values:
                values.append(v)
    return values[0] if len(values) == 1 else None


def _bucket_ids(v) -> list[int] | None:
    ids = [to_int(x) for part in (v if isinstance(v, list) else [v]) for x in str(part).split(",")]
    ids = [i for i in ids if i is not None]
    return ids or None


def _bucket_list(v) -> list[str]:
    return [str(x).strip() for x in (v if isinstance(v, list) else [v]) if str(x).strip()]


_BUCKET_MONSTER_FIELDS = {
    # our key: (bucket field, converter)
    "slayerXp": ("slayer_experience", lambda v: int(float(v)) if v not in (None, "") else None),
    "slayerLevel": ("slayer_level", to_int),
    "combat": ("combat_level", to_int),
    "hitpoints": ("hitpoints", to_int),
    "size": ("size", to_int),
    "attackSpeed": ("attack_speed", lambda v: to_int(v) or None),  # 0 means "not set"
    "slayerCategory": ("slayer_category", lambda v: ", ".join(_bucket_list(v)) or None),
    "assignedBy": ("assigned_by", lambda v: tuple(x.lower() for x in _bucket_list(v)) or None),
    "immuneCannon": ("cannon_immune", lambda v: {"immune": "Yes", "not immune": "No"}.get(str(v).strip().lower(), str(v).strip() or None)),
    "npcIds": ("id", lambda v: tuple(_bucket_ids(v) or ()) or None),
}


# --------------------------------------------------------------------------
# Bucket-only extras: combat stats, drops, Combat Achievements, item bonuses
# --------------------------------------------------------------------------
#
# Read with table-wide Bucket queries only (no page reads).  --buckets-only applies them to the
# existing tasks.json, so a refresh of these needs no page cache.

MONSTER_COMBAT_FIELDS = ["page_name", "page_name_sub", "version_anchor", "default_version", "attribute",
                         "defence_level", "magic_level", "stab_defence_bonus", "slash_defence_bonus",
                         "crush_defence_bonus", "magic_defence_bonus", "range_defence_bonus",
                         "light_range_defence_bonus", "standard_range_defence_bonus",
                         "heavy_range_defence_bonus", "elemental_weakness", "elemental_weakness_percent",
                         "flat_armour", "hitpoints", "size"]
BONUS_FIELDS = ["page_name", "page_name_sub", "stab_attack_bonus", "slash_attack_bonus", "crush_attack_bonus",
                "range_attack_bonus", "magic_attack_bonus", "strength_bonus", "ranged_strength_bonus",
                "magic_damage_bonus", "prayer_bonus", "equipment_slot", "weapon_attack_speed", "combat_style"]
ITEM_FIELDS = ["page_name", "page_name_sub", "item_name", "item_id", "default_version", "tradeable"]
DROP_FIELDS = ["page_name", "item_name", "drop_json", "rare_drop_table"]
CA_FIELDS = ["id", "name", "monster", "task", "tier", "type"]

_COMBAT_KEYS = {
    "defenceLevel": "defence_level", "magicLevel": "magic_level", "stab": "stab_defence_bonus",
    "slash": "slash_defence_bonus", "crush": "crush_defence_bonus", "magic": "magic_defence_bonus",
    "ranged": "range_defence_bonus", "rangedLight": "light_range_defence_bonus",
    "rangedStandard": "standard_range_defence_bonus", "rangedHeavy": "heavy_range_defence_bonus",
    "flatArmour": "flat_armour", "hitpoints": "hitpoints", "size": "size",
    "weaknessPercent": "elemental_weakness_percent",
}


def _default_row(rows: list[dict]) -> dict:
    """The page's default version (Bucket marks it with a default_version key), else the first."""
    return next((r for r in rows if "default_version" in r), rows[0])


def _by_page(rows: list[dict] | None) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    for r in rows or []:
        if r.get("page_name"):
            out.setdefault(r["page_name"], []).append(r)
    return out


def monster_combat_stats(row: dict) -> dict:
    """Defensive stats and attributes of one monster version, for the plugin's DPS estimate."""
    out: dict = {k: to_int(row.get(f)) for k, f in _COMBAT_KEYS.items() if to_int(row.get(f)) is not None}
    out["attributes"] = sorted({a.strip().lower() for a in _bucket_list(row.get("attribute")) if a.strip()})
    if row.get("elemental_weakness"):
        out["weakness"] = str(row["elemental_weakness"]).strip().lower()
    out["version"] = row.get("version_anchor") or None
    return out


_RARITY = re.compile(r"([\d.,]+)\s*/\s*([\d.,]+)")


def parse_rarity(text: str | None) -> float | None:
    """Chance per roll: "Always" is 1, "4/128" or "~1/200" a fraction; anything else unknown."""
    if not text:
        return None
    t = str(text).strip().lower()
    if t == "always":
        return 1.0
    m = _RARITY.search(t)
    if not m:
        return None
    try:
        num, den = float(m.group(1).replace(",", "")), float(m.group(2).replace(",", ""))
    except ValueError:
        return None
    times = re.match(r"\s*(\d+)\s*[×x]\s*", t)  # "2 × 1/128": two separate chances
    return (int(times.group(1)) if times else 1) * num / den if den > 0 else None


# Drops that come from killing the monster (a boss's reward chest counts); pickpocketing etc. do not.
KILL_DROP_TYPES = {"combat", "reward", None}


def parse_drop(row: dict, item_ids: dict[str, int]) -> dict | None:
    try:
        d = json.loads(row.get("drop_json") or "{}")
    except json.JSONDecodeError:
        return None
    if (d.get("Drop type") or None) not in KILL_DROP_TYPES:
        return None
    name = d.get("Dropped item") or row.get("item_name")
    if not name:
        return None
    rate = parse_rarity(d.get("Rarity"))
    lo, hi = to_int(d.get("Quantity Low")), to_int(d.get("Quantity High"))
    drop = {
        "item": name,
        "itemId": item_ids.get(name.lower()),
        "rarity": d.get("Rarity") or None,
        "rate": round(rate, 8) if rate is not None else None,
        "rolls": to_int(d.get("Rolls")) or 1,
        "quantityLow": lo if lo is not None else 1,
        "quantityHigh": hi if hi is not None else (lo if lo is not None else 1),
        "from": d.get("Dropped from") or None,
    }
    if "rare_drop_table" in row:
        drop["rareDropTable"] = True
    return drop


def drops_for_page(rows: list[dict], item_ids: dict[str, int], default_version: str | None) -> tuple[str | None, list[dict]]:
    """One version's drop table: the monster's default version when its rows name it
    ("Abyssal demon#Standard"), else the version with the most rows.  Returns (version, drops)."""
    drops = [d for d in (parse_drop(r, item_ids) for r in rows) if d]
    versions: dict[str, list[dict]] = {}
    for d in drops:
        anchor = (d.get("from") or "").partition("#")[2]
        versions.setdefault(anchor, []).append(d)
    if default_version and default_version in versions:
        version = default_version
    else:
        version = max(versions, key=lambda v: len(versions[v])) if versions else None
    chosen = versions.get(version, []) if version is not None else []
    for d in chosen:
        d.pop("from", None)
    return version or None, chosen


def item_id_map(items: list[dict] | None) -> dict[str, int]:
    """Lower-case item name to its item id (the default version's first id)."""
    out: dict[str, int] = {}
    for rows in _by_page(items).values():
        for r in sorted(rows, key=lambda r: "default_version" not in r):
            ids = _bucket_ids(r.get("item_id"))
            name = (r.get("item_name") or r.get("page_name") or "").lower()
            if ids and name and name not in out:
                out[name] = ids[0]
    return out


def build_items(bonuses: list[dict] | None, items: list[dict] | None) -> list[dict]:
    """Equipment bonuses per item version, with every item id of that version.  An id that two
    versions share (the wiki lists some ids under several versions) stays only with the default
    version, else with the first version listed."""
    ids_by_sub: dict[str, list[int]] = {}
    names_by_sub: dict[str, str] = {}
    default_subs: set[str] = set()
    for r in items or []:
        sub = r.get("page_name_sub") or r.get("page_name")
        ids = _bucket_ids(r.get("item_id"))
        if sub and ids:
            ids_by_sub.setdefault(sub, []).extend(i for i in ids if i not in ids_by_sub.get(sub, []))
            names_by_sub[sub] = r.get("item_name") or r.get("page_name")
            if "default_version" in r:
                default_subs.add(sub)
    out: list[dict] = []
    for r in bonuses or []:
        sub = r.get("page_name_sub") or r.get("page_name")
        ids = ids_by_sub.get(sub) or ids_by_sub.get(r.get("page_name"))
        slot = (r.get("equipment_slot") or "").strip().lower()
        if not ids or not slot:
            continue
        item = {
            "ids": sorted(set(ids)),
            "name": names_by_sub.get(sub) or r.get("page_name"),
            "slot": slot,
            "stab": to_int(r.get("stab_attack_bonus")) or 0,
            "slash": to_int(r.get("slash_attack_bonus")) or 0,
            "crush": to_int(r.get("crush_attack_bonus")) or 0,
            "ranged": to_int(r.get("range_attack_bonus")) or 0,
            "magic": to_int(r.get("magic_attack_bonus")) or 0,
            "str": to_int(r.get("strength_bonus")) or 0,
            "rangedStr": to_int(r.get("ranged_strength_bonus")) or 0,
            "magicDmg": float(r.get("magic_damage_bonus") or 0),
            "prayer": to_int(r.get("prayer_bonus")) or 0,
        }
        if slot in ("weapon", "2h"):
            item["speed"] = to_int(r.get("weapon_attack_speed")) or None
            item["category"] = (r.get("combat_style") or "").strip() or None
        item["_default"] = sub in default_subs
        out.append(item)
    # One row per id: default versions first, then page order.
    taken: set[int] = set()
    kept: list[dict] = []
    for item in sorted(out, key=lambda i: not i["_default"]):
        item["ids"] = [i for i in item["ids"] if i not in taken]
        taken.update(item["ids"])
        del item["_default"]
        if item["ids"]:
            kept.append(item)
    kept.sort(key=lambda i: (i["name"] or "", i["ids"]))
    return kept


def apply_bucket_extras(tasks: list[dict], client: "BucketClient", report: dict) -> tuple[list[dict], list[dict]]:
    """Combat stats on every monster record and Combat Achievements on every task; returns the item
    bonus list (items.json) and one drop table per monster page (drops.json)."""
    monsters = _by_page(client.table("infobox_monster", MONSTER_COMBAT_FIELDS))
    items = client.table("infobox_item", ITEM_FIELDS)
    ids = item_id_map(items)
    drops = _by_page(client.table("dropsline", DROP_FIELDS))
    cas = client.table("combat_achievement", CA_FIELDS) or []
    ca_by_monster: dict[str, list[dict]] = {}
    for r in cas:
        for m in _bucket_list(r.get("monster")):
            ca_by_monster.setdefault(m.lower(), []).append({
                "id": to_int(r.get("id")), "name": r.get("name"), "monster": m,
                "task": r.get("task"), "tier": r.get("tier"), "type": r.get("type")})
    missing_drops: list[str] = []
    drop_tables: dict[str, dict] = {}
    for t in tasks:
        pages = []
        for mon in t.get("monsters", []):
            page = mon.get("page")
            rows = monsters.get(page)
            default = _default_row(rows) if rows else {}
            if rows:
                mon["combatStats"] = monster_combat_stats(default)
            if page and page not in drop_tables:
                version, table = drops_for_page(drops.get(DROP_PAGE_OVERRIDES.get(page, page), []), ids,
                                                default.get("version_anchor"))
                drop_tables[page] = {"page": page, "version": version, "drops": table}
                if not table:
                    missing_drops.append(page)
            pages.append((page or "").lower())
        for extra in [t.get("wikiTaskPage")] + t.get("wikiMonsterPages", []):
            if extra:
                pages.append(extra.lower())
        seen: set[int] = set()
        t["combatAchievements"] = []
        for page in pages:
            for ca in ca_by_monster.get(page, []):
                if ca["id"] is not None and ca["id"] not in seen:
                    seen.add(ca["id"])
                    t["combatAchievements"].append(ca)
        t["combatAchievements"].sort(key=lambda c: c["id"])
    report["bucketExtras"] = {
        "monstersWithCombatStats": sum(1 for t in tasks for m in t.get("monsters", []) if m.get("combatStats")),
        "monsterPagesWithDrops": sum(1 for d in drop_tables.values() if d["drops"]),
        "monsterPagesWithoutDrops": missing_drops,
        "tasksWithCombatAchievements": sum(1 for t in tasks if t["combatAchievements"]),
        "itemNamesWithIds": len(ids),
    }
    tables = sorted((d for d in drop_tables.values() if d["drops"]), key=lambda d: d["page"])
    return build_items(client.table("infobox_bonuses", BONUS_FIELDS), items), tables


def monster_info(page: "Page") -> tuple[dict | None, str]:
    """{{Infobox Monster}} facts for a page: Bucket first, the page's own infobox for the
    rest.  Returns (info, source) with source "bucket", "bucket+page", "page" or "none"."""
    from_page = parse_infobox_monster(page.wikitext)
    rows = BUCKET.monsters.get(page.title) or []
    if not rows:
        return from_page, "page" if from_page else "none"
    # The name stays the page's: Bucket's default version of a disguised monster is the
    # disguise ("Rocks" for a Rock Crab), and name_variants() falls back to the page title.
    info = dict(from_page) if from_page else {"name": None, "versions": []}
    for key, (field, conv) in _BUCKET_MONSTER_FIELDS.items():
        v = _bucket_scalar(rows, field, conv)
        if v is not None:
            info[key] = list(v) if isinstance(v, tuple) else v
    return info, "bucket+page" if from_page else "bucket"


def bucket_category_candidates(tasks: list[dict]) -> dict[str, list[str]]:
    """Maintainer hint in report.json: monster pages whose Bucket slayer_category names one of a
    task's categories but that the task does not include yet (candidates for
    EXTRA_MONSTER_PAGES; review each, many are quest or event monsters)."""
    by_category: dict[str, set[str]] = {}
    for page, rows in BUCKET.monsters.items():
        for r in rows:
            for c in _bucket_list(r.get("slayer_category") or []):
                by_category.setdefault(c.lower(), set()).add(page)
    out: dict[str, list[str]] = {}
    for t in tasks:
        cats = {c.strip().lower() for m in t.get("monsters", []) for c in (m.get("slayerCategory") or "").split(",") if c.strip()}
        cats.discard("bosses")
        have = {m.get("page") for m in t.get("monsters", [])}
        missing = sorted(set().union(*(by_category.get(c, set()) for c in cats)) - have) if cats else []
        if missing:
            out[t["task"]] = missing
    return out


class WikiCache:
    """Fetches wikitext via the MediaWiki API, caching every answer on disk."""

    def __init__(self, cache_dir: Path, refresh: bool = False, sleep: float = FETCH_SLEEP):
        self.cache_dir = cache_dir
        self.refresh = refresh
        self.sleep = sleep
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.fetches = 0
        self.failed: dict[str, str] = {}
        self._mem: dict[str, Page] = {}

    def has(self, title: str) -> bool:
        """Whether the page's answer (ok or missing) is already cached."""
        return title.strip() in self._mem or self._paths(title.strip())[1].exists()

    def _paths(self, title: str) -> tuple[Path, Path]:
        base = self.cache_dir / sanitise_title(title)
        return base.with_suffix(".wikitext"), base.with_suffix(".meta.json")

    def get(self, title: str) -> Page:
        title = title.strip()
        if title in self._mem:
            return self._mem[title]
        text_path, meta_path = self._paths(title)
        if not self.refresh and meta_path.exists():
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            if meta.get("status") == "ok" and text_path.exists():
                page = Page(title, meta.get("title"), text_path.read_text(encoding="utf-8"),
                            bool(meta.get("redirected")), "ok")
                self._mem[title] = page
                return page
            if meta.get("status") == "missing":
                page = Page(title, None, None, False, "missing", meta.get("error"))
                self._mem[title] = page
                return page
        page = self._fetch(title)
        if page.status in ("ok", "missing"):
            meta = {"requested": title, "title": page.title, "redirected": page.redirected,
                    "status": page.status, "error": page.error, "fetched": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
            meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
            if page.ok:
                text_path.write_text(page.wikitext, encoding="utf-8")
        else:
            self.failed[title] = page.error or "unknown error"
        self._mem[title] = page
        return page

    def _fetch(self, title: str) -> Page:
        params = {"action": "parse", "page": title, "prop": "wikitext", "format": "json",
                  "formatversion": "2", "redirects": "1"}
        url = API_URL + "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        attempts = 0
        while True:
            attempts += 1
            try:
                self.fetches += 1
                print(f"  fetch [{self.fetches}] {title}", file=sys.stderr)
                with urllib.request.urlopen(req, timeout=60) as resp:
                    data = json.load(resp)
                time.sleep(self.sleep)
                break
            except urllib.error.HTTPError as e:
                if e.code == 429 and attempts <= len(BACKOFF_SECONDS):
                    wait = BACKOFF_SECONDS[attempts - 1]
                    print(f"  429 for {title}; backing off {wait}s", file=sys.stderr)
                    time.sleep(wait)
                    continue
                time.sleep(self.sleep)
                return Page(title, None, None, False, "error", f"HTTP {e.code}")
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
                time.sleep(self.sleep)
                return Page(title, None, None, False, "error", f"{type(e).__name__}: {e}")
        if "error" in data:
            code = data["error"].get("code", "")
            if code in ("missingtitle", "nosuchpageid", "invalidtitle"):
                return Page(title, None, None, False, "missing", code)
            return Page(title, None, None, False, "error", json.dumps(data["error"]))
        parse = data.get("parse", {})
        final = parse.get("title")
        redirected = bool(parse.get("redirects")) or (
            final is not None and _norm_title(final) != _norm_title(title))
        return Page(title, final, parse.get("wikitext", ""), redirected, "ok")


def _norm_title(t: str) -> str:
    t = t.replace("_", " ").strip()
    return (t[:1].upper() + t[1:]) if t else t


# --------------------------------------------------------------------------
# Page mapping
# --------------------------------------------------------------------------

def strip_the(name: str) -> str:
    return re.sub(r"^The\s+", "", name, flags=re.I)


def singular_variants(name: str) -> list[str]:
    """Best-effort singular forms of a task name, most likely first."""
    out: list[str] = []
    base = strip_the(name)
    low = base.lower()
    if low.endswith("ves"):
        out += [base[:-3] + "f", base[:-3] + "fe"]
    if low.endswith("ies"):
        out.append(base[:-3] + "y")
    if low.endswith("s") and not low.endswith("ss"):
        out.append(base[:-1])
    if low.endswith("es"):
        out.append(base[:-2])
    out.append(base)
    seen: list[str] = []
    for v in out:
        if v and v not in seen:
            seen.append(v)
    return seen


def resolve_task_page(cache: WikiCache, display: str) -> tuple[Page | None, list[str]]:
    """Try the override, 'Slayer task/<name>' variants, then the bare name."""
    tried: list[str] = []
    candidates: list[str] = []
    if display in TASK_PAGE_OVERRIDES:
        candidates.append(TASK_PAGE_OVERRIDES[display])
    candidates.append(f"Slayer task/{display}")
    if strip_the(display) != display:
        candidates.append(f"Slayer task/{strip_the(display)}")
    for s in singular_variants(display):
        candidates.append(f"Slayer task/{s}")
    candidates.append(display)
    candidates.append(strip_the(display))
    for s in singular_variants(display):
        candidates.append(s)
    seen: set[str] = set()
    for c in candidates:
        if c in seen:
            continue
        seen.add(c)
        tried.append(c)
        page = cache.get(c)
        if page.ok:
            return page, tried
    return None, tried


def resolve_monster_pages(cache: WikiCache, display: str, alternatives: list[str],
                          task_page: Page | None) -> tuple[list[Page], list[str], list[Page]]:
    """Monster pages: override list or first existing singular guess, plus every
    alternative NPC name, plus the task page itself if it is a monster page.
    The third value is the task's own (primary) pages: the override list or
    the singular guess, without alternatives or EXTRA_MONSTER_PAGES."""
    pages: list[Page] = []
    primary: list[Page] = []
    tried: list[str] = []
    seen_titles: set[str] = set()

    def add(page: Page) -> None:
        if page.ok and page.title not in seen_titles:
            seen_titles.add(page.title)
            pages.append(page)

    if display in MONSTER_PAGE_OVERRIDES:
        for t in MONSTER_PAGE_OVERRIDES[display]:
            tried.append(t)
            p = cache.get(t)
            add(p)
            if p.ok:
                primary.append(p)
    else:
        for s in singular_variants(display):
            tried.append(s)
            p = cache.get(s)
            if p.ok and "{{infobox monster" in p.wikitext.lower():
                add(p)
                primary.append(p)
                break
    for extra in EXTRA_MONSTER_PAGES.get(display, []) + alternatives:
        extra = extra.strip()
        if extra:
            tried.append(extra)
            add(cache.get(extra))
    if task_page and task_page.ok and "{{infobox monster" in task_page.wikitext.lower():
        add(task_page)
    return pages, tried, primary


def strategy_subject(title: str) -> str:
    """'Mithril dragon/Strategies' -> 'Mithril dragon'."""
    return re.sub(r"/Strategies$", "", title)


def resolve_strategy_pages(cache: WikiCache, display: str, task_page: Page | None,
                           primary: list[Page]) -> tuple[list[tuple[Page, bool]], list[str]]:
    """Strategy pages whose gear sections are added to the task's own.

    Tried in order: '<task page>/Strategies', '<primary monster page>/Strategies'
    for each primary page, STRATEGY_PAGE_OVERRIDES[display], then
    STRATEGY_VARIANT_PAGES[display].  Returns [(page, is_variant)] deduplicated
    by final title (a redirect back to the task page itself is dropped) and the
    list of titles tried.  Alternatives and EXTRA_MONSTER_PAGES are not tried
    automatically: they are mostly other encounters (bosses) that also count
    for the task; list them in STRATEGY_VARIANT_PAGES to opt in."""
    candidates: list[tuple[str, bool]] = []
    if task_page:
        candidates.append((f"{task_page.title}/Strategies", False))
    for p in primary:
        candidates.append((f"{p.title}/Strategies", False))
    automatic = {c for c, _ in candidates}
    candidates += [(t, False) for t in STRATEGY_PAGE_OVERRIDES.get(display, [])]
    candidates += [(t, True) for t in STRATEGY_VARIANT_PAGES.get(display, [])]
    seen = {task_page.title} if task_page else set()
    tried: list[str] = []
    out: list[tuple[Page, bool]] = []
    for title, variant in candidates:
        if title in tried:
            continue
        tried.append(title)
        # Only the automatic "/Strategies" guesses are checked against Bucket's list of pages
        # with gear tables; a guess not on it is not fetched (it was usually a missing page).
        if (title in automatic and BUCKET.gear_pages is not None and title not in BUCKET.gear_pages
                and not cache.has(title)):
            continue
        page = cache.get(title)
        if page.ok and page.title not in seen:
            seen.add(page.title)
            out.append((page, variant))
    return out, tried


def _gear_key(table: dict) -> str:
    return json.dumps([table.get("style"), table.get("slots")], sort_keys=True)


def collect_gear(task_page: Page | None, strategy_pages: list[tuple[Page, bool]]) -> tuple[list[dict], list[dict], list[str]]:
    """gearTables / exampleSetups from the task page followed by its strategy
    pages.  Every table and setup records its wiki page in `source`.  Tables of
    STRATEGY_VARIANT_PAGES get the page subject as label prefix ("Artio: Melee")
    so they cannot be mistaken for the task's own monster.  A table identical
    (style + slots) to one already collected is skipped.  Returns (tables,
    setups, pages that contributed)."""
    tables: list[dict] = []
    setups: list[dict] = []
    sources: list[str] = []
    seen: set[str] = set()
    pages = ([(task_page, False)] if task_page else []) + strategy_pages
    for page, variant in pages:
        g, s = parse_gear_sections(page.wikitext)
        prefix = strategy_subject(page.title) if variant else None
        added = False
        for t in g:
            key = _gear_key(t)
            if key in seen:
                continue
            seen.add(key)
            if prefix:
                t["label"] = f"{prefix}: {t['label']}" if t["label"] else prefix
                t["variant"] = prefix
            t["source"] = page.title
            tables.append(t)
            added = True
        for e in s:
            if prefix:
                e["label"] = f"{prefix}: {e['label']}" if e["label"] else prefix
                e["variant"] = prefix
            e["source"] = page.title
            setups.append(e)
            added = True
        if added:
            sources.append(page.title)
    _disambiguate_labels(tables)
    return tables, setups, sources


def collect_extra_gear(cache: WikiCache, display: str, tables: list[dict], setups: list[dict],
                       sources: list[str]) -> None:
    """Add the tables and setups of GEAR_PAGES[display] (see its comment), skipping tables
    identical to ones already collected.  Variant tables get the variant as label prefix."""
    seen = {(t.get("variant"), _gear_key(t)) for t in tables}
    for entry in GEAR_PAGES.get(display, []):
        page = cache.get(entry["page"])
        if not page.ok:
            continue
        variant = entry.get("variant")
        tabs = entry.get("tabs") or None
        g, s = parse_gear_sections(page.wikitext)
        added = False
        for tb in g:
            if tabs and tb.get("label") not in tabs:
                continue
            # The same table may serve the task and a variant (a task page tab for one
            # variant), but only once each.
            key = (variant, _gear_key(tb))
            if key in seen:
                continue
            seen.add(key)
            if variant:
                tb["label"] = f"{variant}: {tb['label']}" if tb["label"] else variant
                tb["variant"] = variant
                tb["_variantOnly"] = True
            tb["source"] = page.title
            tables.append(tb)
            added = True
        for e in s:
            if tabs and e.get("label") not in tabs:
                continue
            if variant:
                e["label"] = f"{variant}: {e['label']}" if e["label"] else variant
                e["variant"] = variant
                e["_variantOnly"] = True
            e["source"] = page.title
            setups.append(e)
            added = True
        if added and page.title not in sources:
            sources.append(page.title)
    _disambiguate_labels(tables)


def selectable_variants(task: dict) -> set[str]:
    """Names the plugin offers as variants (TaskInfo.variants()): monster records with a
    name that some location lists, superior excluded; lower-cased."""
    superior = (task.get("superior") or "").lower()
    placed = {m.lower() for loc in task.get("locations", []) for m in (loc.get("monsters") or [])}
    return {m["name"].lower() for m in task.get("monsters", [])
            if m.get("name") and m["name"].lower() != superior and m["name"].lower() in placed}


def finalise_variant_gear(tasks: list[dict]) -> None:
    """A table or setup keeps its variant only when the plugin can select that variant.
    Otherwise a GEAR_PAGES entry (a variant's own gear) is dropped, and a
    STRATEGY_VARIANT_PAGES table becomes part of the task's own gear (label keeps the
    prefix) unless the task already has the same table."""
    for task in tasks:
        names = selectable_variants(task)
        for kind in ("gearTables", "exampleSetups"):
            if kind not in task:
                continue  # reused through --only: tasks.json no longer carries gear
            own = {_gear_key(t) for t in task.get(kind, []) if t.get("variant") is None} if kind == "gearTables" else set()
            kept = []
            for item in task.get(kind, []):
                v = item.get("variant")
                variant_only = item.pop("_variantOnly", False)
                if v is not None and v.lower() not in names:
                    if variant_only or (kind == "gearTables" and _gear_key(item) in own):
                        continue
                    del item["variant"]
                kept.append(item)
            task[kind] = kept


def _disambiguate_labels(tables: list[dict]) -> None:
    """The plugin lists tables by label, so a repeated label (two tabbers on one
    page, e.g. Abyssal Sire phase 1 / phase 2 "Ranged", or the same tab on the task
    page and its strategy page) is extended on its second and later occurrence.
    When its style differs from the first table's: the style itself if it starts
    with the label ("Melee" -> "Melee (Punish)"), else "<label> (<style>)";
    otherwise "<label> (<source page>)", else "<label> #n"."""
    used: set[str] = set()
    first_source: dict[str, str] = {}
    first_style: dict[str, str] = {}
    for t in tables:
        label = t.get("label")
        if label is None:
            continue
        if label not in used:
            used.add(label)
            first_source[label] = t.get("source")
            first_style[label] = t.get("style") or ""
            continue
        style = t.get("style") or ""
        options = []
        if style and style != label and style != first_style[label]:
            if style.lower().startswith(label.lower()):
                options.append(style)
            options.append(f"{label} ({style})")
        if t.get("source") and t.get("source") != first_source.get(label):
            options.append(f"{label} ({t['source']})")
        n = 2
        while f"{label} #{n}" in used:
            n += 1
        options.append(f"{label} #{n}")
        new = next(o for o in options if o not in used)
        used.add(new)
        t["label"] = new


# --------------------------------------------------------------------------
# Parsers: task page
# --------------------------------------------------------------------------

def parse_master_string(raw: str) -> dict | None:
    """'120-170 (200-250) (Weighting 9)' -> min,max,extMin,extMax,weight.
    Mortimer's '120-180 (200-250) <br/> or 170-280 (250-350)' keeps both ranges."""
    if raw is None:
        return None
    clean = render_templates(strip_refs(raw))
    weight = None
    mw = re.search(r"weighting\s*:?\s*(\d+)", clean, re.I)
    if mw:
        weight = int(mw.group(1))
    clean_no_w = re.sub(r"\(?\s*weighting\s*:?\s*\d+\s*\)?", "", clean, flags=re.I)
    parts = [p.strip() for p in re.split(r"<br\s*/?>|\bor\b|\n", clean_no_w, flags=re.I) if p.strip()]
    ranges_out: list[dict] = []
    for part in parts:
        # "120-170 (200-250)" or a fixed amount "50 (91-150)"
        ranges = [(int(a), int(b) if b else int(a))
                  for a, b in re.findall(r"(\d+)(?:\s*[-–]\s*(\d+))?", part)]
        if not ranges:
            continue
        r = {"min": ranges[0][0], "max": ranges[0][1], "extMin": None, "extMax": None}
        if len(ranges) > 1:
            r["extMin"], r["extMax"] = ranges[1]
        ranges_out.append(r)
    if not ranges_out:
        return None
    first = ranges_out[0]
    return {
        "raw": raw.strip(),
        "min": first["min"], "max": first["max"],
        "extMin": first["extMin"], "extMax": first["extMax"],
        "weight": weight,
        "alternatives": ranges_out[1:],
    }


def parse_skill_reqs(raw: str | None) -> list[dict]:
    out = []
    if not raw:
        return out
    for t in find_templates(raw, ("scp", "skill clickpic")):
        pos, _ = t.args
        if len(pos) >= 2 and to_int(pos[1]) is not None:
            out.append({"skill": pos[0].strip(), "level": to_int(pos[1])})
    return out


def parse_infobox_slayer(text: str) -> dict | None:
    tpls = find_templates(text, ("infobox slayer",))
    if not tpls:
        return None
    _, named = tpls[0].args
    masters = {}
    for m in MASTERS:
        if m in named:
            parsed = parse_master_string(named[m])
            if parsed:
                masters[m] = parsed
    combat = None
    for t in find_templates(named.get("combatreq", ""), ("scp", "skill clickpic")):
        pos, _ = t.args
        if len(pos) >= 2 and pos[0].strip().lower() == "combat":
            combat = to_int(pos[1])
    other = plain_text(named.get("otherreq", "")) or None
    if other and other.lower() == "none":
        other = None
    skills = parse_skill_reqs(named.get("skillreq"))
    slayer_level = next((s["level"] for s in skills if s["skill"].lower() == "slayer"), None)
    return {
        "name": plain_text(named.get("name", "")) or None,
        "taskId": to_int(named.get("id")),
        "requirements": {
            "skills": skills,
            "slayer": slayer_level,
            "combat": combat,
            "other": other,
        },
        "masters": masters,
    }


def collect_efn_names(text: str) -> dict[str, str]:
    """Map efn name -> note text for every named footnote defined on the page."""
    out: dict[str, str] = {}
    for t in find_templates(text, ("efn",)):
        pos, named = t.args
        note = pos[0] if pos else ""
        if named.get("name") and note and named["name"] not in out:
            out[named["name"]] = plain_text(note)
    return out


def _extract_items(value: str) -> list[dict]:
    items = []
    for t in find_templates(value, ("plink", "plinkp", "plinkt", "plinkl")):
        pos, named = t.args
        if not pos:
            continue
        name = pos[0].split("#")[0].strip()
        if not name:
            continue
        item = {"name": name}
        if named.get("txt"):
            item["txt"] = plain_text(named["txt"])
        if named.get("pic"):
            item["pic"] = named["pic"].strip()
        items.append(item)
    return items


def parse_recommended_equipment(t: Template, efn_names: dict[str, str]) -> dict:
    _, named = t.args
    slots: dict[str, list[list[dict]]] = {}
    slot_notes: dict[str, list[str]] = {}
    # "2h1", "2h2", … (two-handed weapon row, e.g. Kree'arra/Strategies) fill the
    # weapon slot when the table has no weaponN row of its own.
    two_handed_as_weapon = not any(re.fullmatch(r"weapon\d", k) for k in named)
    for key, value in named.items():
        m = re.fullmatch(r"([a-z]+|2h)(\d)", key)
        if not m:
            continue
        slot, tier = m.group(1), int(m.group(2))
        if slot == "2h" and two_handed_as_weapon:
            slot = "weapon"
        if slot not in GEAR_SLOTS or not (1 <= tier <= 5):
            continue
        notes = []
        for e in find_templates(value, ("efn",)):
            pos, en = e.args
            note = plain_text(pos[0]) if pos else ""
            if not note and en.get("name"):
                note = efn_names.get(en["name"], "")
            if note:
                notes.append(f"tier {tier}: {note}")
        stripped = remove_templates(value, ("efn",))
        items = _extract_items(stripped)
        leftover = remove_templates(stripped)
        leftover = re.sub(r"<br\s*/?>", " ", leftover, flags=re.I)
        leftover = re.sub(r"[/>]|\bor:?\b", " ", leftover)
        leftover = plain_text(leftover)
        leftover = re.sub(r"[\s()]+", " ", leftover).strip()
        if re.search(r"[A-Za-z]", leftover):
            notes.append(f"tier {tier}: {leftover}")
        tiers = slots.setdefault(slot, [[], [], [], [], []])
        tiers[tier - 1] = items
        if notes:
            slot_notes.setdefault(slot, []).extend(notes)
    # Drop empty tiers (the wiki leaves some rows blank, e.g. no ammo in a melee setup): tiers are an
    # order of preference, so the next filled row moves up. Slots left with nothing are dropped.
    for slot in list(slots):
        slots[slot] = [tier for tier in slots[slot] if tier]
        if not slots[slot]:
            del slots[slot]
    return {
        "style": plain_text(named.get("style", "")) or None,
        "slots": slots,
        "slotNotes": {k: " | ".join(v) for k, v in slot_notes.items()},
    }


_EQUIPMENT_IGNORE = {"align", "buttons", "type", "title", "text", "width", "style", "class", "caption", "name", "summary"}


def parse_equipment(t: Template) -> dict:
    _, named = t.args
    equipment = {}
    for k, v in named.items():
        if k in _EQUIPMENT_IGNORE:
            continue
        val = plain_text(v)
        if val:
            equipment[k] = val
    return equipment


def parse_inventory(t: Template) -> dict:
    pos, named = t.args
    grid: list[str | None] = [None] * 28
    idx = 0
    for p in pos:
        idx += 1
        val = plain_text(p)
        if 1 <= idx <= 28 and val:
            grid[idx - 1] = val
    for k, v in named.items():
        if k.isdigit():
            n = int(k)
            val = plain_text(v)
            if 1 <= n <= 28 and val:
                grid[n - 1] = val
    return {"grid": grid, "items": [g for g in grid if g]}


def parse_rune_pouch(t: Template) -> list[str]:
    pos, named = t.args
    runes: dict[int, str] = {}
    for i, p in enumerate(pos, start=1):
        val = plain_text(p)
        if val:
            runes[i] = val
    for k, v in named.items():
        if k.isdigit():
            val = plain_text(v)
            if val:
                runes[int(k)] = val
    return [runes[k] for k in sorted(runes)]


def _tab_label(tab: str) -> tuple[str | None, str]:
    lm = re.match(r"\s*([^=\n{}]+?)\s*=(.*)$", tab, re.S)
    if lm:
        return lm.group(1).strip(), lm.group(2)
    return None, tab


def _nested_tabber_chunks(label: str | None, tab: str) -> list[tuple[str | None, str]]:
    """A tab may hold a nested {{#tag:tabber|A=…{{!}}-{{!}}B=…}} (strategy pages
    such as Kalphite Queen/Strategies).  Each inner tab becomes its own chunk
    labelled "<outer> - <inner>"; the rest of the outer tab keeps the outer label."""
    nested = find_templates(tab, ("#tag:tabber",))
    top = [t for t in nested if not any(o.start < t.start and t.end <= o.end for o in nested)]
    if not top:
        return [(label, tab)]
    rest: list[str] = []
    inner_chunks: list[tuple[str | None, str]] = []
    pos = 0
    for t in top:
        rest.append(tab[pos:t.start])
        pos = t.end
        for inner in re.split(r"\{\{!\}\}-\{\{!\}\}", t.body):
            inner_label, inner_text = _tab_label(inner)
            if label and inner_label:
                inner_label = f"{label} - {inner_label}"
            inner_chunks.append((inner_label or label, inner_text))
    rest.append(tab[pos:])
    return [(label, "".join(rest))] + inner_chunks


def _chunks(text: str) -> list[tuple[str | None, str]]:
    """Split a page into (label, text) chunks: one per <tabber> tab (label = tab
    label; nested {{#tag:tabber}} tabs get "<outer> - <inner>") and, for text
    outside tabbers, one per level-2 section."""
    chunks: list[tuple[str | None, str]] = []
    outside: list[str] = []
    pos = 0
    for m in re.finditer(r"<tabber>(.*?)</tabber>", text, re.S | re.I):
        outside.append(text[pos:m.start()])
        pos = m.end()
        for tab in re.split(r"\|-\|", m.group(1)):
            chunks.extend(_nested_tabber_chunks(*_tab_label(tab)))
    outside.append(text[pos:])
    rest = "\n".join(outside)
    secs = sections(rest)
    if not secs:
        chunks.append((None, rest))
    else:
        chunks.append((None, rest[:secs[0]["head_start"]]))
        for s in secs:
            if s["level"] == 2:
                chunks.append((s["title"], rest[s["start"]:s["end"]]))
    return chunks


def parse_gear_sections(text: str) -> tuple[list[dict], list[dict]]:
    """Return (gearTables, exampleSetups) for the whole page."""
    efn_names = collect_efn_names(text)
    gear_tables: list[dict] = []
    setups: list[dict] = []
    for label, chunk in _chunks(text):
        recs = find_templates(chunk, ("recommended equipment",))
        equips = find_templates(chunk, ("equipment",))
        invs = find_templates(chunk, ("inventory",))
        pouches = find_templates(chunk, ("rune pouch",))
        if not (recs or equips or invs or pouches):
            continue
        notes_text = remove_templates(chunk)
        notes = paragraphs(plain_text(notes_text))
        notes_joined = "\n\n".join(notes)
        if len(notes_joined) > 1500:
            notes_joined = notes_joined[:1500].rsplit(" ", 1)[0] + "…"
        for r in recs:
            parsed = parse_recommended_equipment(r, efn_names)
            parsed["label"] = label
            gear_tables.append(parsed)
        n = max(len(equips), len(invs), len(pouches))
        for i in range(n):
            setup = {
                "label": label if n == 1 or label is None else f"{label} #{i + 1}",
                "equipment": parse_equipment(equips[i]) if i < len(equips) else {},
                "inventory": parse_inventory(invs[i])["items"] if i < len(invs) else [],
                "inventoryGrid": parse_inventory(invs[i])["grid"] if i < len(invs) else [],
                "runePouch": parse_rune_pouch(pouches[i]) if i < len(pouches) else [],
                "notes": notes_joined if i == 0 else None,
            }
            setups.append(setup)
    return gear_tables, setups


def parse_strategy(text: str, intro_paragraphs: int = 3, cap: int = 2500) -> tuple[list[str], str | None]:
    """First N intro paragraphs + every ==Strategy== paragraph, capped."""
    body = re.sub(r"<tabber>.*?</tabber>", "", text, flags=re.S | re.I)
    secs = sections(body)
    intro_end = secs[0]["head_start"] if secs else len(body)
    intro = paragraphs(plain_text(body[:intro_end]))
    strat: list[str] = []
    for s in secs:
        if s["level"] == 2 and re.fullmatch(r"strateg(y|ies)", s["title"], re.I):
            strat_body = body[s["start"]:s["end"]]
            strat = paragraphs(plain_text(strat_body))
            break
    summary = None
    if intro:
        summary = intro[0]
        if len(summary) > 400:
            cut = summary[:400]
            dot = cut.rfind(". ")
            summary = (cut[:dot + 1] if dot > 120 else cut.rsplit(" ", 1)[0] + "…")
    out: list[str] = []
    total = 0
    for p in intro[:intro_paragraphs] + strat:
        if total + len(p) > cap:
            room = cap - total
            if room > 200:
                out.append(p[:room].rsplit(" ", 1)[0] + "…")
            break
        out.append(p)
        total += len(p) + 2
    return out, summary


def _table_rows(table: str) -> list[list[str]]:
    rows: list[list[str]] = []
    body = table
    body = re.sub(r"^\{\|[^\n]*\n?", "", body)
    body = re.sub(r"\n\|\}\s*$", "", body)
    for row in re.split(r"\n\|-[^\n]*", "\n" + body):
        cells: list[str] = []
        lines = [ln for ln in row.split("\n") if ln.strip()]
        for ln in lines:
            s = ln.strip()
            if s.startswith("!") or s.startswith("|+"):
                continue
            if s.startswith("|"):
                for cell in re.split(r"\|\|", s[1:]):
                    cells.append(cell)
            elif cells:
                cells[-1] += "\n" + s
        if cells:
            rows.append(cells)
    return rows


def parse_unlocks(text: str) -> list[dict]:
    unlocks: list[dict] = []
    for s in sections(text):
        if not re.search(r"slayer (shop options|unlocks?|rewards?)", s["title"], re.I):
            continue
        body = text[s["start"]:s["end"]]
        for tbl in re.finditer(r"\{\|.*?\n\|\}", body, re.S):
            for cells in _table_rows(tbl.group(0)):
                texts = []
                for c in cells:
                    if "[[File:" in c or "[[file:" in c:
                        continue
                    texts.append(plain_text(c))
                texts = [t for t in texts if t]
                if len(texts) < 2:
                    continue
                cost = to_int(texts[1]) if re.fullmatch(r"[\d,]+", texts[1].strip()) else None
                note = " ".join(texts[2:]) if cost is not None else " ".join(texts[1:])
                unlocks.append({"name": texts[0], "cost": cost, "note": note or None})
    return unlocks


# --------------------------------------------------------------------------
# Parsers: monster page
# --------------------------------------------------------------------------

def _versioned(named: dict[str, str], key: str, versions: list[str], conv):
    """Return (scalar, byVersion) for possibly versioned infobox fields (key, key1, key2...)."""
    by_version = {}
    for i, vname in enumerate(versions, start=1):
        if f"{key}{i}" in named:
            by_version[vname] = conv(named[f"{key}{i}"])
    scalar = conv(named[key]) if key in named else None
    if scalar is None and by_version:
        scalar = next(iter(by_version.values()))
    return scalar, (by_version or None)


def _styles(raw: str) -> list[str]:
    txt = plain_text(raw)
    parts = re.split(r",|/| and |;", txt)
    return [p.strip() for p in parts if p.strip()]


def parse_infobox_monster(text: str) -> dict | None:
    tpls = find_templates(text, ("infobox monster",))
    if not tpls:
        return None
    _, named = tpls[0].args
    versions = []
    i = 1
    while f"version{i}" in named:
        versions.append(plain_text(named[f"version{i}"]) or f"v{i}")
        i += 1
    out: dict = {"name": plain_text(named.get("name", "")) or None, "versions": versions}
    fields = {
        "slayxp": ("slayerXp", to_int),
        "slaylvl": ("slayerLevel", to_int),
        "combat": ("combat", to_int),
        "hitpoints": ("hitpoints", to_int),
        "max hit": ("maxHit", lambda v: plain_text(v) or None),
        "attack style": ("attackStyles", _styles),
        "weakness": ("weakness", lambda v: plain_text(v) or None),
        "attributes": ("attributes", lambda v: plain_text(v) or None),
        "size": ("size", to_int),
        "aggressive": ("aggressive", lambda v: plain_text(v) or None),
        "poisonous": ("poisonous", lambda v: plain_text(v) or None),
        "attack speed": ("attackSpeed", to_int),
        "immunecannon": ("immuneCannon", lambda v: plain_text(v) or None),
        "immunethrall": ("immuneThrall", lambda v: plain_text(v) or None),
        "id": ("npcIds", lambda v: [to_int(x) for x in v.split(",") if to_int(x) is not None]),
        "cat": ("slayerCategory", lambda v: plain_text(v) or None),
        "assignedby": ("assignedBy", lambda v: [x.strip().lower() for x in plain_text(v).split(",") if x.strip()]),
    }
    for key, (out_key, conv) in fields.items():
        scalar, by_version = _versioned(named, key, versions, conv)
        out[out_key] = scalar
        if by_version:
            out[out_key + "ByVersion"] = by_version
    return out


_COORD = re.compile(r"x\s*:\s*(-?\d+)\s*,\s*y\s*:\s*(-?\d+)(?:\s*,\s*plane\s*:\s*(\d+))?", re.I)
_BARE_COORD = re.compile(r"^\s*(-?\d+)\s*,\s*(-?\d+)\s*(?:,|$)")


def coords_in_arg(arg: str) -> list[tuple[int, int, int | None]]:
    """Coordinates in one positional map/LocLine argument: 'x:3025,y:4916[,plane:1]'
    or the bare '3025,4916[,icon:...]' form."""
    found = [(int(m.group(1)), int(m.group(2)), int(m.group(3)) if m.group(3) else None)
             for m in _COORD.finditer(arg)]
    if not found:
        m = _BARE_COORD.match(arg)
        if m:
            found.append((int(m.group(1)), int(m.group(2)), None))
    return found


def parse_location_text(raw: str) -> dict:
    """'[[Slayer Tower]] ({{FloorNumber|uk=2}})' -> name/displayName/link/annotations."""
    annotations = []
    for t in find_templates(raw):
        rendered = render_templates(raw[t.start:t.end])
        annotations.append({"raw": raw[t.start:t.end], "text": rendered or None})
    link = None
    lm = re.search(r"\[\[([^\[\]|]*)(?:\|[^\[\]]*)?\]\]", raw)
    if lm:
        link = lm.group(1).split("#")[0].strip()
    name = plain_text(remove_templates(raw))
    name = re.sub(r"\s+", " ", name).strip(" -,")
    display = plain_text(raw)
    display = re.sub(r"\s+", " ", display).strip(" -,")
    return {"name": name or display or "Unknown", "displayName": display or name or "Unknown",
            "link": link, "annotations": annotations}


def parse_loclines(text: str, page_title: str) -> list[dict]:
    locs: list[dict] = []
    for t in find_templates(text, ("locline",)):
        pos, named = t.args
        spawns = []
        for p in pos:
            for x, y, pl in coords_in_arg(p):
                spawns.append({"x": x, "y": y, "plane": pl})
        loc_info = parse_location_text(named.get("location", ""))
        plane = to_int(named.get("plane")) or 0
        levels_txt = plain_text(named.get("levels", ""))
        levels = [int(x) for x in re.findall(r"\d+", levels_txt.replace(",", " "))]
        locs.append({
            **loc_info,
            "monster": plain_text(named.get("name", "")) or None,
            "page": page_title,
            "levels": levels,
            "levelsRaw": levels_txt or None,
            "members": plain_text(named.get("members", "")) or None,
            "mapID": to_int(named.get("mapid")),
            "plane": plane,
            "dropversion": plain_text(named.get("dropversion", "")) or None,
            "spawns": [[s["x"], s["y"]] for s in spawns],
            "source": "locline",
        })
    return locs


def _map_location(t: Template, page_title: str) -> dict | None:
    pos, named = t.args
    spawns: list[list[int]] = []
    titles: list[str] = []
    for p in pos:
        for x, y, _pl in coords_in_arg(p):
            spawns.append([x, y])
            tm = re.search(r"title\s*:\s*([^,|]+)", p)
            if tm:
                titles.append(tm.group(1).strip())
    if to_int(named.get("x")) is not None and to_int(named.get("y")) is not None:
        spawns.append([to_int(named["x"]), to_int(named["y"])])
    if not spawns:
        return None
    caption = plain_text(named.get("caption", "")) or plain_text(named.get("name", ""))
    if caption == page_title:
        caption = ""
    display = f"{page_title} ({caption.rstrip('.')})" if caption else page_title
    return {
        "name": page_title, "displayName": display, "link": page_title,
        "annotations": [{"raw": None, "text": tt} for tt in ([caption] if caption else []) + titles],
        "monster": page_title, "page": page_title, "levels": [], "levelsRaw": None,
        "members": None, "mapID": to_int(named.get("mapid")),
        "plane": to_int(named.get("plane")) or 0, "dropversion": None,
        "spawns": spawns, "source": "map",
    }


def parse_map_locations(text: str, page_title: str) -> list[dict]:
    """Fallback for pages without {{LocLine}} (instanced bosses): {{Map}} templates
    inside ==Location== / ==Transportation== sections; failing that, the `map`
    field of an {{Infobox Location}} or {{Infobox Activity}} (pages such as Barrows, TzHaar Fight
    Cave, Inferno, Zul-Andra listed in EXTRA_MONSTER_PAGES for their entrance)."""
    locs: list[dict] = []
    for s in sections(text):
        if not re.fullmatch(r"(locations?|transportation|getting there)", s["title"], re.I):
            continue
        body = text[s["start"]:s["end"]]
        for t in find_templates(body, ("map",)):
            loc = _map_location(t, page_title)
            if loc:
                locs.append(loc)
    if not locs:
        for box in find_templates(text, ("infobox location", "infobox activity")):
            for t in find_templates(box.args[1].get("map", ""), ("map",)):
                loc = _map_location(t, page_title)
                if loc:
                    locs.append(loc)
    return locs


# --------------------------------------------------------------------------
# Location merging
# --------------------------------------------------------------------------

def location_id(name: str, plane: int | None) -> str:
    return f"{slugify(name)}-p{plane if plane is not None else 'x'}"


def centroid(spawns: list[list[int]]) -> tuple[int | None, int | None]:
    if not spawns:
        return None, None
    xs = [s[0] for s in spawns]
    ys = [s[1] for s in spawns]
    return round(sum(xs) / len(xs)), round(sum(ys) / len(ys))


def looks_wilderness(loc: dict) -> bool:
    if re.search(r"wilderness", (loc.get("name") or "") + " " + (loc.get("link") or ""), re.I):
        return True
    if loc.get("plane", 0) == 0:
        x0, y0, x1, y1 = WILDERNESS_BOX
        for x, y in loc.get("spawns", []):
            if x0 <= x <= x1 and y0 <= y <= y1:
                return True
    return False


def merge_locations(raw_locs: list[dict]) -> list[dict]:
    """Dedupe LocLine records by (location text, plane) into TaskLocation records."""
    merged: dict[str, dict] = {}
    for loc in raw_locs:
        lid = location_id(loc["name"], loc["plane"])
        if lid not in merged:
            merged[lid] = {
                "id": lid,
                "name": loc["name"],
                "displayName": loc["displayName"],
                "link": loc["link"],
                "annotations": [a["text"] for a in loc["annotations"] if a["text"]],
                "annotationsRaw": [a["raw"] for a in loc["annotations"] if a["raw"]],
                "source": loc.get("source", "locline"),
                "plane": loc["plane"],
                "mapID": loc["mapID"],
                "members": loc["members"],
                "monsters": [],
                "levels": [],
                "dropversions": [],
                "pages": [],
                "spawns": [],
                "spawnsByMonster": {},
            }
        m = merged[lid]
        if loc["monster"] and loc["monster"] not in m["monsters"]:
            m["monsters"].append(loc["monster"])
        for lv in loc["levels"]:
            if lv not in m["levels"]:
                m["levels"].append(lv)
        if loc["dropversion"] and loc["dropversion"] not in m["dropversions"]:
            m["dropversions"].append(loc["dropversion"])
        if loc["page"] not in m["pages"]:
            m["pages"].append(loc["page"])
        if m["mapID"] is None:
            m["mapID"] = loc["mapID"]
        for s in loc["spawns"]:
            if s not in m["spawns"]:
                m["spawns"].append(s)
        key = loc["monster"] or "?"
        m["spawnsByMonster"].setdefault(key, [])
        for s in loc["spawns"]:
            if s not in m["spawnsByMonster"][key]:
                m["spawnsByMonster"][key].append(s)
        if len(m["displayName"]) < len(loc["displayName"]):
            m["displayName"] = loc["displayName"]
    out = []
    for m in merged.values():
        x, y = centroid(m["spawns"])
        m.update({
            "x": x, "y": y,
            "spawnCount": len(m["spawns"]),
            "coordsMissing": x is None,
            "rank": None,
            "multi": "unknown",
            "cannon": "unknown",
            "wilderness": looks_wilderness(m),
            "wildernessLevelMin": None,
            "wildernessLevelMax": None,
            "konarAssignable": "unknown",
            "requirements": [],
            "notes": None,
            "curated": False,
        })
        m["levels"].sort()
        out.append(m)
    out.sort(key=lambda l: (l["name"].lower(), l["plane"]))
    return out


# --------------------------------------------------------------------------
# Curated overrides
# --------------------------------------------------------------------------

_FLOOR_PAREN = re.compile(
    r"\((?:[^()]*(?:floor|basement|upstairs|downstairs|level \d|ground|top|\d+(?:st|nd|rd|th))[^()]*)\)", re.I)


def normalise_loc_name(name: str, strip_all_parens: bool = False) -> str:
    s = name or ""
    s = _FLOOR_PAREN.sub(" ", s)
    if strip_all_parens:
        s = re.sub(r"\([^()]*\)", " ", s)
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def load_curated(curated_dir: Path) -> tuple[dict[str, dict], list[str]]:
    """Load curated/verified/*.json -> {task display name lower: record}."""
    by_task: dict[str, dict] = {}
    problems: list[str] = []
    if not curated_dir.exists():
        return by_task, problems
    for f in sorted(curated_dir.glob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            problems.append(f"{f.name}: {e}")
            continue
        if isinstance(data, dict):
            data = data.get("tasks", [data])
        if not isinstance(data, list):
            problems.append(f"{f.name}: expected an array of task objects")
            continue
        for rec in data:
            if not isinstance(rec, dict) or not rec.get("task"):
                problems.append(f"{f.name}: record without 'task' field skipped")
                continue
            key = rec["task"].strip().lower()
            if key in by_task:
                problems.append(f"{f.name}: duplicate curated record for '{rec['task']}' (later file wins)")
            rec["_file"] = f.name
            by_task[key] = rec
    return by_task, problems


_CURATED_TASK_FIELDS = ("summary", "recommendedStyle", "requiredItems", "usefulItems",
                        "superior", "alternatives", "bossTask")
_CURATED_LOC_FIELDS = ("rank", "multi", "cannon", "wilderness", "wildernessLevelMin",
                       "wildernessLevelMax", "konarAssignable", "requirements", "notes")


def apply_curated(task: dict, curated: dict | None, report: dict) -> None:
    if not curated:
        return
    task["curatedFile"] = curated.get("_file")
    for f in _CURATED_TASK_FIELDS:
        if f in curated and curated[f] is not None:
            task[f] = curated[f]
    locs = task["locations"]
    for cl in curated.get("locations") or []:
        cname = cl.get("name")
        if not cname:
            continue
        # tier 0: explicit alias (CURATED_LOCATION_ALIASES) by displayName, then name;
        # tier 1: exact (punctuation-insensitive) match on name or displayName;
        # tier 2: floor/basement parentheticals stripped; tier 3: all parentheticals stripped
        aliases = CURATED_LOCATION_ALIASES.get(task["task"], {})
        alias_key = next((k for k in (cl.get("displayName"), cname) if k in aliases), None)
        if alias_key is not None:
            target_id = aliases[alias_key]
            matches = [l for l in locs if l["id"] == target_id] if target_id else []
            if target_id and not matches:
                report["curatedProblems"].append(
                    f"{task['task']}: alias for '{alias_key}' points to missing location id '{target_id}'")
            aliased = True
        else:
            aliased = False
        exact = re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", cname.lower())).strip()
        matches = matches if aliased else [l for l in locs if exact in (
            re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", l["name"].lower())).strip(),
            re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", l["displayName"].lower())).strip())]
        if not matches and not aliased:
            norm = normalise_loc_name(cname)
            matches = [l for l in locs if normalise_loc_name(l["name"]) == norm
                       or normalise_loc_name(l["displayName"]) == norm]
        if not matches and not aliased:
            norm2 = normalise_loc_name(cname, strip_all_parens=True)
            matches = [l for l in locs if normalise_loc_name(l["name"], True) == norm2]
        if len(matches) > 1:
            report["ambiguousCuratedLocations"].append(
                {"task": task["task"], "curated": cname, "matches": [m["id"] for m in matches]})
        if matches:
            target = matches[0]
            for f in _CURATED_LOC_FIELDS:
                if f in cl and cl[f] is not None:
                    target[f] = cl[f]
            target["curated"] = True
            target["curatedName"] = cname
        else:
            report["unmatchedCuratedLocations"].append({"task": task["task"], "curated": cname})
            new = {
                "id": location_id(cname, None),
                "name": cname, "displayName": cname, "link": None,
                "annotations": [], "annotationsRaw": [], "plane": None, "mapID": None,
                "members": None, "monsters": [], "levels": [], "dropversions": [], "pages": [],
                "spawns": [], "spawnsByMonster": {}, "x": None, "y": None, "spawnCount": 0,
                "coordsMissing": True, "rank": None, "multi": "unknown", "cannon": "unknown",
                "wilderness": False, "wildernessLevelMin": None, "wildernessLevelMax": None,
                "konarAssignable": "unknown", "requirements": [], "notes": None,
                "curated": True, "curatedName": cname,
            }
            for f in _CURATED_LOC_FIELDS:
                if f in cl and cl[f] is not None:
                    new[f] = cl[f]
            locs.append(new)
    locs.sort(key=lambda l: (l["rank"] if isinstance(l.get("rank"), int) else 10 ** 6,
                             l["name"].lower(), l["plane"] if l["plane"] is not None else -1))


# --------------------------------------------------------------------------
# Access rules: free-text location requirements -> checkable structure
# --------------------------------------------------------------------------
# parse_access() turns the curated `requirements` strings of a location into
# {"groups": [{"text", "any": [rule...], "manual", "note"?}]}.  The plugin
# (AccessChecker) ANDs the groups and ORs the rules inside `any`; a group whose
# `any` evaluates false is a hard lock, so anything that might be an
# unexpressed *alternative* (an item, a boss task, a combat-achievement tier,
# an unknown parenthetical) collapses the group to `any: []` + manual.  A
# missing lock is always preferred to a wrong one.

# RuneLite Quest enum name -> display name (net.runelite.api.Quest).
QUESTS: dict[str, str] = {
    "ANIMAL_MAGNETISM": "Animal Magnetism", "ANOTHER_SLICE_OF_HAM": "Another Slice of H.A.M.",
    "THE_ASCENT_OF_ARCEUUS": "The Ascent of Arceuus", "ALFRED_GRIMHANDS_BARCRAWL": "Alfred Grimhand's Barcrawl",
    "BEAR_YOUR_SOUL": "Bear Your Soul", "BELOW_ICE_MOUNTAIN": "Below Ice Mountain",
    "BETWEEN_A_ROCK": "Between a Rock...", "BIG_CHOMPY_BIRD_HUNTING": "Big Chompy Bird Hunting",
    "BIOHAZARD": "Biohazard", "BLACK_KNIGHTS_FORTRESS": "Black Knights' Fortress", "BONE_VOYAGE": "Bone Voyage",
    "CABIN_FEVER": "Cabin Fever", "CLIENT_OF_KOUREND": "Client of Kourend", "CLOCK_TOWER": "Clock Tower",
    "COLD_WAR": "Cold War", "CONTACT": "Contact!", "COOKS_ASSISTANT": "Cook's Assistant",
    "THE_CORSAIR_CURSE": "The Corsair Curse", "CREATURE_OF_FENKENSTRAIN": "Creature of Fenkenstrain",
    "CURSE_OF_THE_EMPTY_LORD": "Curse of the Empty Lord", "DADDYS_HOME": "Daddy's Home",
    "DARKNESS_OF_HALLOWVALE": "Darkness of Hallowvale", "DEATH_PLATEAU": "Death Plateau",
    "DEATH_TO_THE_DORGESHUUN": "Death to the Dorgeshuun", "DEMON_SLAYER": "Demon Slayer",
    "THE_DEPTHS_OF_DESPAIR": "The Depths of Despair", "DESERT_TREASURE_I": "Desert Treasure I",
    "DEVIOUS_MINDS": "Devious Minds", "THE_DIG_SITE": "The Dig Site", "DORICS_QUEST": "Doric's Quest",
    "DRAGON_SLAYER_I": "Dragon Slayer I", "DRAGON_SLAYER_II": "Dragon Slayer II", "DREAM_MENTOR": "Dream Mentor",
    "DRUIDIC_RITUAL": "Druidic Ritual", "DWARF_CANNON": "Dwarf Cannon", "EADGARS_RUSE": "Eadgar's Ruse",
    "EAGLES_PEAK": "Eagles' Peak", "ELEMENTAL_WORKSHOP_I": "Elemental Workshop I",
    "ELEMENTAL_WORKSHOP_II": "Elemental Workshop II", "ENAKHRAS_LAMENT": "Enakhra's Lament",
    "THE_ENCHANTED_KEY": "The Enchanted Key", "ENLIGHTENED_JOURNEY": "Enlightened Journey",
    "ENTER_THE_ABYSS": "Enter the Abyss", "ERNEST_THE_CHICKEN": "Ernest the Chicken",
    "THE_EYES_OF_GLOUPHRIE": "The Eyes of Glouphrie", "FAIRYTALE_I__GROWING_PAINS": "Fairytale I - Growing Pains",
    "FAIRYTALE_II__CURE_A_QUEEN": "Fairytale II - Cure a Queen", "FAMILY_CREST": "Family Crest",
    "FAMILY_PEST": "Family Pest", "THE_FEUD": "The Feud", "FIGHT_ARENA": "Fight Arena",
    "FISHING_CONTEST": "Fishing Contest", "FORGETTABLE_TALE": "Forgettable Tale...",
    "THE_FORSAKEN_TOWER": "The Forsaken Tower", "THE_FREMENNIK_EXILES": "The Fremennik Exiles",
    "THE_FREMENNIK_ISLES": "The Fremennik Isles", "THE_FREMENNIK_TRIALS": "The Fremennik Trials",
    "GARDEN_OF_TRANQUILLITY": "Garden of Tranquillity", "THE_GENERALS_SHADOW": "The General's Shadow",
    "GERTRUDES_CAT": "Gertrude's Cat", "GETTING_AHEAD": "Getting Ahead", "GHOSTS_AHOY": "Ghosts Ahoy",
    "THE_GIANT_DWARF": "The Giant Dwarf", "GOBLIN_DIPLOMACY": "Goblin Diplomacy", "THE_GOLEM": "The Golem",
    "THE_GRAND_TREE": "The Grand Tree", "THE_GREAT_BRAIN_ROBBERY": "The Great Brain Robbery",
    "GRIM_TALES": "Grim Tales", "THE_HAND_IN_THE_SAND": "The Hand in the Sand", "HAUNTED_MINE": "Haunted Mine",
    "HAZEEL_CULT": "Hazeel Cult", "HEROES_QUEST": "Heroes' Quest", "HOLY_GRAIL": "Holy Grail",
    "HORROR_FROM_THE_DEEP": "Horror from the Deep", "ICTHLARINS_LITTLE_HELPER": "Icthlarin's Little Helper",
    "IMP_CATCHER": "Imp Catcher", "IN_AID_OF_THE_MYREQUE": "In Aid of the Myreque",
    "IN_SEARCH_OF_KNOWLEDGE": "In Search of Knowledge", "IN_SEARCH_OF_THE_MYREQUE": "In Search of the Myreque",
    "JUNGLE_POTION": "Jungle Potion", "A_KINGDOM_DIVIDED": "A Kingdom Divided", "KINGS_RANSOM": "King's Ransom",
    "THE_KNIGHTS_SWORD": "The Knight's Sword", "LAIR_OF_TARN_RAZORLOR": "Lair of Tarn Razorlor",
    "LEGENDS_QUEST": "Legends' Quest", "LOST_CITY": "Lost City", "THE_LOST_TRIBE": "The Lost Tribe",
    "LUNAR_DIPLOMACY": "Lunar Diplomacy", "MAGE_ARENA_I": "Mage Arena I", "MAGE_ARENA_II": "Mage Arena II",
    "MAKING_FRIENDS_WITH_MY_ARM": "Making Friends with My Arm", "MAKING_HISTORY": "Making History",
    "MERLINS_CRYSTAL": "Merlin's Crystal", "MISTHALIN_MYSTERY": "Misthalin Mystery",
    "MONKEY_MADNESS_I": "Monkey Madness I", "MONKEY_MADNESS_II": "Monkey Madness II",
    "MONKS_FRIEND": "Monk's Friend", "MOUNTAIN_DAUGHTER": "Mountain Daughter",
    "MOURNINGS_END_PART_I": "Mourning's End Part I", "MOURNINGS_END_PART_II": "Mourning's End Part II",
    "MURDER_MYSTERY": "Murder Mystery", "MY_ARMS_BIG_ADVENTURE": "My Arm's Big Adventure",
    "NATURE_SPIRIT": "Nature Spirit", "A_NIGHT_AT_THE_THEATRE": "A Night at the Theatre",
    "OBSERVATORY_QUEST": "Observatory Quest", "OLAFS_QUEST": "Olaf's Quest", "ONE_SMALL_FAVOUR": "One Small Favour",
    "PIRATES_TREASURE": "Pirate's Treasure", "PLAGUE_CITY": "Plague City",
    "A_PORCINE_OF_INTEREST": "A Porcine of Interest", "PRIEST_IN_PERIL": "Priest in Peril",
    "PRINCE_ALI_RESCUE": "Prince Ali Rescue", "THE_QUEEN_OF_THIEVES": "The Queen of Thieves",
    "RAG_AND_BONE_MAN_I": "Rag and Bone Man I", "RAG_AND_BONE_MAN_II": "Rag and Bone Man II",
    "RATCATCHERS": "Ratcatchers", "RECIPE_FOR_DISASTER": "Recipe for Disaster",
    "RECRUITMENT_DRIVE": "Recruitment Drive", "REGICIDE": "Regicide", "THE_RESTLESS_GHOST": "The Restless Ghost",
    "ROMEO__JULIET": "Romeo & Juliet", "ROVING_ELVES": "Roving Elves", "ROYAL_TROUBLE": "Royal Trouble",
    "RUM_DEAL": "Rum Deal", "RUNE_MYSTERIES": "Rune Mysteries", "SCORPION_CATCHER": "Scorpion Catcher",
    "SEA_SLUG": "Sea Slug", "SHADES_OF_MORTTON": "Shades of Mort'ton", "SHADOW_OF_THE_STORM": "Shadow of the Storm",
    "SHEEP_HERDER": "Sheep Herder", "SHEEP_SHEARER": "Sheep Shearer", "SHIELD_OF_ARRAV": "Shield of Arrav",
    "SHILO_VILLAGE": "Shilo Village", "SINS_OF_THE_FATHER": "Sins of the Father",
    "SKIPPY_AND_THE_MOGRES": "Skippy and the Mogres", "THE_SLUG_MENACE": "The Slug Menace",
    "SONG_OF_THE_ELVES": "Song of the Elves", "A_SOULS_BANE": "A Soul's Bane",
    "SPIRITS_OF_THE_ELID": "Spirits of the Elid", "SWAN_SONG": "Swan Song", "TAI_BWO_WANNAI_TRIO": "Tai Bwo Wannai Trio",
    "A_TAIL_OF_TWO_CATS": "A Tail of Two Cats", "TALE_OF_THE_RIGHTEOUS": "Tale of the Righteous",
    "A_TASTE_OF_HOPE": "A Taste of Hope", "TEARS_OF_GUTHIX": "Tears of Guthix", "TEMPLE_OF_IKOV": "Temple of Ikov",
    "THRONE_OF_MISCELLANIA": "Throne of Miscellania", "THE_TOURIST_TRAP": "The Tourist Trap",
    "TOWER_OF_LIFE": "Tower of Life", "TREE_GNOME_VILLAGE": "Tree Gnome Village", "TRIBAL_TOTEM": "Tribal Totem",
    "TROLL_ROMANCE": "Troll Romance", "TROLL_STRONGHOLD": "Troll Stronghold", "UNDERGROUND_PASS": "Underground Pass",
    "VAMPYRE_SLAYER": "Vampyre Slayer", "WANTED": "Wanted!", "WATCHTOWER": "Watchtower",
    "WATERFALL_QUEST": "Waterfall Quest", "WHAT_LIES_BELOW": "What Lies Below", "WITCHS_HOUSE": "Witch's House",
    "WITCHS_POTION": "Witch's Potion", "X_MARKS_THE_SPOT": "X Marks the Spot",
    "ZOGRE_FLESH_EATERS": "Zogre Flesh Eaters", "THE_FROZEN_DOOR": "The Frozen Door",
    "LAND_OF_THE_GOBLINS": "Land of the Goblins", "HOPESPEARS_WILL": "Hopespear's Will",
    "TEMPLE_OF_THE_EYE": "Temple of the Eye", "BENEATH_CURSED_SANDS": "Beneath Cursed Sands",
    "SLEEPING_GIANTS": "Sleeping Giants", "THE_GARDEN_OF_DEATH": "The Garden of Death",
    "INTO_THE_TOMBS": "Into the Tombs",
    "RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST": "Recipe for Disaster - Another Cook's Quest",
    "RECIPE_FOR_DISASTER__MOUNTAIN_DWARF": "Recipe for Disaster - Mountain Dwarf",
    "RECIPE_FOR_DISASTER__WARTFACE__BENTNOZE": "Recipe for Disaster - Wartface & Bentnoze",
    "RECIPE_FOR_DISASTER__PIRATE_PETE": "Recipe for Disaster - Pirate Pete",
    "RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE": "Recipe for Disaster - Lumbridge Guide",
    "RECIPE_FOR_DISASTER__EVIL_DAVE": "Recipe for Disaster - Evil Dave",
    "RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE": "Recipe for Disaster - Skrach Uglogwee",
    "RECIPE_FOR_DISASTER__SIR_AMIK_VARZE": "Recipe for Disaster - Sir Amik Varze",
    "RECIPE_FOR_DISASTER__KING_AWOWOGEI": "Recipe for Disaster - King Awowogei",
    "RECIPE_FOR_DISASTER__CULINAROMANCER": "Recipe for Disaster - Culinaromancer",
    "SECRETS_OF_THE_NORTH": "Secrets of the North",
    "DESERT_TREASURE_II__THE_FALLEN_EMPIRE": "Desert Treasure II - The Fallen Empire",
    "HIS_FAITHFUL_SERVANTS": "His Faithful Servants", "THE_PATH_OF_GLOUPHRIE": "The Path of Glouphrie",
    "CHILDREN_OF_THE_SUN": "Children of the Sun", "BARBARIAN_TRAINING": "Barbarian Training",
    "DEFENDER_OF_VARROCK": "Defender of Varrock", "WHILE_GUTHIX_SLEEPS": "While Guthix Sleeps",
    "TWILIGHTS_PROMISE": "Twilight's Promise", "AT_FIRST_LIGHT": "At First Light",
    "PERILOUS_MOONS": "Perilous Moons",
    "THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE": "The Ribbiting Tale of a Lily Pad Labour Dispute",
    "THE_HEART_OF_DARKNESS": "The Heart of Darkness", "DEATH_ON_THE_ISLE": "Death on the Isle",
    "MEAT_AND_GREET": "Meat and Greet", "ETHICALLY_ACQUIRED_ANTIQUITIES": "Ethically Acquired Antiquities",
    "THE_CURSE_OF_ARRAV": "The Curse of Arrav", "THE_FINAL_DAWN": "The Final Dawn",
    "SHADOWS_OF_CUSTODIA": "Shadows of Custodia", "SCRAMBLED": "Scrambled!", "VALE_TOTEMS": "Vale Totems",
    "PANDEMONIUM": "Pandemonium", "PRYING_TIMES": "Prying Times", "CURRENT_AFFAIRS": "Current Affairs",
    "TROUBLED_TORTUGANS": "Troubled Tortugans", "THE_RED_REEF": "The Red Reef",
    "FALLEN_FROM_GRACE": "Fallen From Grace", "LEARNING_THE_ROPES": "Learning the Ropes",
    "THE_IDES_OF_MILK": "The Ides of Milk", "THE_BLOOD_MOON_RISES": "The Blood Moon Rises",
    "A_RUFF_SITUATION": "A Ruff Situation", "CRAB_QUEST": "Crab Quest",
}

# Short forms / spellings seen in the requirement strings -> Quest enum name.
# Keys are normalised with _quest_key() (lower-case, alphanumerics only, no leading "the").
QUEST_ALIASES: dict[str, str] = {
    "ds1": "DRAGON_SLAYER_I", "ds2": "DRAGON_SLAYER_II", "dsii": "DRAGON_SLAYER_II",
    "dragonslayer": "DRAGON_SLAYER_I",
    "sote": "SONG_OF_THE_ELVES", "mm1": "MONKEY_MADNESS_I", "mm2": "MONKEY_MADNESS_II",
    "monkeymadness": "MONKEY_MADNESS_I",
    "mep1": "MOURNINGS_END_PART_I", "mep2": "MOURNINGS_END_PART_II", "mepii": "MOURNINGS_END_PART_II",
    "dt1": "DESERT_TREASURE_I", "dt2": "DESERT_TREASURE_II__THE_FALLEN_EMPIRE",
    "deserttreasureii": "DESERT_TREASURE_II__THE_FALLEN_EMPIRE",
    "deserttreasure2": "DESERT_TREASURE_II__THE_FALLEN_EMPIRE",
    "fairytaleii": "FAIRYTALE_II__CURE_A_QUEEN", "fairytale2": "FAIRYTALE_II__CURE_A_QUEEN",
    "fairytalei": "FAIRYTALE_I__GROWING_PAINS", "fairytale1": "FAIRYTALE_I__GROWING_PAINS",
    "rfd": "RECIPE_FOR_DISASTER",
    "recipefordisasterfreeingsiramikvarze": "RECIPE_FOR_DISASTER__SIR_AMIK_VARZE",
    "recipefordisastersiramikvarze": "RECIPE_FOR_DISASTER__SIR_AMIK_VARZE",
    "rfdsiramikvarze": "RECIPE_FOR_DISASTER__SIR_AMIK_VARZE",
    "sotf": "SINS_OF_THE_FATHER", "atoh": "A_TASTE_OF_HOPE", "doh": "DARKNESS_OF_HALLOWVALE",
    "hftd": "HORROR_FROM_THE_DEEP", "pip": "PRIEST_IN_PERIL", "wgs": "WHILE_GUTHIX_SLEEPS",
    "legendsquest": "LEGENDS_QUEST", "heroesquest": "HEROES_QUEST", "olafsquest": "OLAFS_QUEST",
    "waterfallquest": "WATERFALL_QUEST", "fremennikexiles": "THE_FREMENNIK_EXILES",
    "fremenniktrials": "THE_FREMENNIK_TRIALS", "fremennikisles": "THE_FREMENNIK_ISLES",
    "enterabyss": "ENTER_THE_ABYSS", "entertheabyss": "ENTER_THE_ABYSS",
}

# RuneLite Skill enum names.  "combat" is handled separately (type "combat").
SKILLS = ("ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC", "COOKING",
          "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING", "SMITHING", "MINING",
          "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING", "RUNECRAFT", "HUNTER",
          "CONSTRUCTION", "SAILING")
SKILL_ALIASES = {"range": "RANGED", "ranging": "RANGED", "hp": "HITPOINTS", "runecrafting": "RUNECRAFT",
                 "wc": "WOODCUTTING", "str": "STRENGTH", "def": "DEFENCE", "att": "ATTACK"}

# Diary region (normalised) -> (VarbitID prefix, display name).  Karamja only has an ELITE varbit.
DIARY_REGIONS = {
    "ardougne": ("ARDOUGNE", "Ardougne"), "falador": ("FALADOR", "Falador"),
    "wilderness": ("WILDERNESS", "Wilderness"), "western": ("WESTERN", "Western Provinces"),
    "westernprovinces": ("WESTERN", "Western Provinces"), "kandarin": ("KANDARIN", "Kandarin"),
    "varrock": ("VARROCK", "Varrock"), "desert": ("DESERT", "Desert"), "morytania": ("MORYTANIA", "Morytania"),
    "fremennik": ("FREMENNIK", "Fremennik"), "lumbridge": ("LUMBRIDGE", "Lumbridge & Draynor"),
    "lumbridgeanddraynor": ("LUMBRIDGE", "Lumbridge & Draynor"), "lumbridgedraynor": ("LUMBRIDGE", "Lumbridge & Draynor"),
    "karamja": ("KARAMJA", "Karamja"), "kourend": ("KOUREND", "Kourend & Kebos"),
    "kourendandkebos": ("KOUREND", "Kourend & Kebos"), "kourendkebos": ("KOUREND", "Kourend & Kebos"),
    "kebos": ("KOUREND", "Kourend & Kebos"),
}
DIARY_TIERS = ("easy", "medium", "hard", "elite")
DIARY_MISSING_VARBITS = {("KARAMJA", "easy"), ("KARAMJA", "medium"), ("KARAMJA", "hard")}

# Slayer reward unlock names (exact, as the live reward list shows them).
UNLOCK_NAMES = (
    "Gargoyle Smasher", "Slug Salter", "Reptile Freezer", "'Shroom Sprayer", "Malevolent Masquerade",
    "Ring Bling", "Broader Fletching", "Seeing Red", "Watch the Birdie", "Hot Stuff", "Like a Boss",
    "Reptile Got Ripped", "Bigger and Badder", "Duly Noted", "Stop the Wyvern", "Double Trouble",
    "Basilocked", "Actual Vampyre Slayer", "Task Storage", "I Wildy More Slayer", "Warped Reality",
    "Lured In", "Wings Spread", "Chance of Heavy Frost", "Need More Darkness", "Ankou Very Much",
    "Suq-a-nother One", "Fire & Darkness", "Pedal to the Metals", "Spiritual Fervour", "Augment my Abbies",
    "It's Dark in Here", "Greater Challenge", "Bleed Me Dry", "Smell Ya Later", "Birds of a Feather",
    "Horrorific", "To Dust You Shall Return", "Wyver-nother One", "Get Smashed", "Nechs Please", "Krack On",
    "Get Scabaright on It", "Wyver-nother Two", "Basilonger", "More at Stake", "Revenenenenenants",
    "More eyes than sense", "Un-restraining Order", "Let's Stay All Aquanite", "Can of Wyrms",
    "Gryphon and on", "I see Dragons",
)

# Whole-string classifiers, checked first (regex on the lower-cased string -> note).
# Any hit makes the string a manual group with no rules.
_ACCESS_WHOLE_STRING = (
    # master assignment requirements: not location requirements
    (re.compile(r"to be assigned|to be offered|to receive .*\btasks?\b|\bfor the (?:boss )?task\b|"
                r"\bassigns?\b|\bto assign\b|slayer task list requirement|"
                r"\bfor (?:a|an) [\w' -]*\btask\b|god wars (?:dungeon )?(?:slayer )?(?:tasks|assignments)|gwd tasks"),
     "assignment requirement"),
    (re.compile(r"\boptional\b|\brecommended\b"), "optional"),
    (re.compile(r"\bnot available\b|\bno longer\b"), "negative requirement"),
    (re.compile(r"^unknown\b"), "unknown"),
)
_ACCESS_NONE = re.compile(r"^none\b")
_TASK_ONLY = re.compile(r"task-only|on-task|^must be on a|^active\b.*\btask\b|^on a\b.*\btask\b|"
                        r"^[\w' ]+ task(?: \(|$)")

# Parenthetical handling: alternatives ("or ..."), harmless qualifiers, and
# "alternative-ish" text that might describe another way in (drops the rules).
_PAREN_ALT = re.compile(r"^(?:or|unless|not needed with|permanent after)\b\s*(.*)$", re.I)
_PAREN_HARMLESS = re.compile(
    r"^(?:(?:not |un)?boostable|cannot be boosted|boosted|not boostable\b.*|"
    r"partial(?:\b.*)?|started(?:\b.*)?|completed|complete|"
    r"(?:to |for )?[\w' ]*\baccess(?: to [\w' ]+)?|to (?:access|enter|reach)\b.*|"
    r"reached? [\w' ]+|defeat(?:ed)? dad|dad defeated|fairy rings? \w+(?: .*)?|"
    r"boulder|jutting wall|dungeon entrance obstacle|varlamore|free-to-play|\d+ (?:slayer reward )?points)$", re.I)
_PAREN_ALTISH = re.compile(r"\bor\b|\bunless\b|bypass|instead|\bwith\b|\bwithout\b|\bonly\b|\bfree\b|\bhalf\b|"
                           r"\bformerly\b|\bneeds nothing\b|\bafter\b", re.I)
_OR_SPLIT = re.compile(r",?\s+or\s+(?!better\b)", re.I)
_AND_SPLIT = re.compile(r"\s*;\s+|,\s+|\s+and\s+|\s+plus\s+", re.I)
_UNLESS = re.compile(r",?\s+(?:unless|not needed with)\s+", re.I)
_QUALIFIER_BAD = re.compile(r"shortcut|route|entrance|task page|stepping stones|\bper\b|section", re.I)

_SKILL_RE = re.compile(r"^(?:level )?(\d+)\s+([a-z]+)(?P<tail>.*)$", re.I)
_SKILL_RE2 = re.compile(r"^([a-z]+)\s+(\d+)$", re.I)
_SKILL_TAIL_OK = re.compile(r"^(?:,?\s*(?:boostable|not boostable|unboostable|not boostable))?"
                            r"(?:\s+(?:to|for)\s+.+)?$", re.I)
_DIARY_RE = re.compile(r"^(?:the\s+)?(?:(easy|medium|hard|elite)\s+)?(.+?)(?:\s+(easy|medium|hard|elite))?\s+diary"
                       r"(?:\s+is\s+(?:complete|done)|\s+completed?)?$", re.I)
_UNLOCK_RE = re.compile(r"^'?([\w' &!-]+?)'?\s+(?:slayer\s+)?unlock(?:ed)?$", re.I)
_MEMBERS_RE = re.compile(r"^members$", re.I)
_QUEST_LEAD = re.compile(r"^(?:partial completion of|completion of|partial|started|complete|completed)\s+", re.I)
# What may follow a quest name: state words, then one "how far" / "what for" clause.
_QUEST_TAIL_OK = re.compile(
    r"^(?:(?i:quest|miniquest|started|completed?|progressed|partial)\s*)*"
    r"(?:(?i:far enough\s+)?(?:(?i:to (?:the point of|access|enter|reach|board))|for [A-Z][\w']* access)\b.*)?$")
_IN_PROGRESS = re.compile(r"partial|started|progressed|to the point|far enough|during", re.I)
_QUEST_LOOKING = re.compile(r"quest|partial|started|complet|progress", re.I)
_SMALL_WORDS = {"of", "the", "a", "an", "and", "in", "to", "on", "from", "for", "with", "&", "-", "at", "i", "ii", "iii"}


def _quest_key(name: str) -> str:
    key = re.sub(r"[^a-z0-9]+", "", name.lower())
    return key[3:] if key.startswith("the") and len(key) > 3 else key


_QUEST_BY_KEY: dict[str, str] = {}
for _enum, _display in QUESTS.items():
    _QUEST_BY_KEY[_quest_key(_display)] = _enum
    _QUEST_BY_KEY[re.sub(r"[^a-z0-9]+", "", _display.lower())] = _enum
_QUEST_BY_KEY.update(QUEST_ALIASES)


def _strip_parens(text: str) -> tuple[str, list[str]]:
    """Return text without (...) groups and the list of their contents (nesting-aware)."""
    out, parens, depth, buf = [], [], 0, []
    for ch in text:
        if ch == "(":
            if depth == 0:
                buf = []
            else:
                buf.append(ch)
            depth += 1
        elif ch == ")" and depth:
            depth -= 1
            if depth == 0:
                parens.append("".join(buf).strip())
            else:
                buf.append(ch)
        elif depth:
            buf.append(ch)
        else:
            out.append(ch)
    main = re.sub(r"\s+([,;:])", r"\1", re.sub(r"\s+", " ", "".join(out))).strip(" ,;:")
    return main, parens


_QUEST_NAME_IN_TEXT = re.compile(
    r"\b(?:" + "|".join(sorted((re.escape(d) for d in QUESTS.values() if len(d) >= 8), key=len, reverse=True)) + r")\b",
    re.I)


def _looks_like_quest(piece: str) -> bool:
    if re.search(r"combat achievement", piece, re.I):
        return False
    if _QUEST_LOOKING.search(piece) or _QUEST_NAME_IN_TEXT.search(piece):
        return True
    words = piece.split()
    caps = [w for w in words if w[:1].isupper()]
    return len(words) >= 2 and len(caps) >= 2 and all(w[:1].isupper() or w.lower() in _SMALL_WORDS for w in words)


def _classify_piece(piece: str, state_text: str) -> dict | None:
    """One alternative / conjunct (parentheticals already removed) -> rule or None."""
    p = piece.strip(" ,;:")
    if not p:
        return None
    if _MEMBERS_RE.match(p):
        return {"type": "members"}
    m = _SKILL_RE.match(p) or None
    if m:
        level, word, tail = int(m.group(1)), m.group(2).lower(), m.group("tail")
        if _SKILL_TAIL_OK.match(tail) and not _QUALIFIER_BAD.search(tail):
            if word == "combat":
                return {"type": "combat", "level": level}
            skill = SKILL_ALIASES.get(word, word.upper())
            if skill in SKILLS:
                return {"type": "skill", "skill": skill, "level": level}
    m = _SKILL_RE2.match(p)
    if m:
        word, level = m.group(1).lower(), int(m.group(2))
        if word == "combat":
            return {"type": "combat", "level": level}
        skill = SKILL_ALIASES.get(word, word.upper())
        if skill in SKILLS:
            return {"type": "skill", "skill": skill, "level": level}
    m = _DIARY_RE.match(p)
    if m:
        tier = (m.group(1) or m.group(3) or "").lower()
        region = re.sub(r"[^a-z]+", "", m.group(2).lower().replace("&", "and"))
        if tier in DIARY_TIERS and region in DIARY_REGIONS:
            prefix, display = DIARY_REGIONS[region]
            if (prefix, tier) in DIARY_MISSING_VARBITS:
                return None
            return {"type": "diary", "varbit": f"{prefix}_DIARY_{tier.upper()}_COMPLETE",
                    "name": f"{display} {tier.title()} diary"}
    m = _UNLOCK_RE.match(p)
    if m:
        wanted = re.sub(r"\s+", " ", m.group(1).lower())
        for name in UNLOCK_NAMES:
            if name.lower() == wanted:
                return {"type": "unlock", "name": name}
        return None
    enum = _match_quest(p)
    if enum:
        state = "IN_PROGRESS" if _IN_PROGRESS.search(state_text) else "FINISHED"
        return {"type": "quest", "quest": enum, "name": QUESTS[enum], "state": state}
    return None


def _match_quest(piece: str) -> str | None:
    """Longest word prefix of the piece that is a known quest, provided the rest is an allowed tail."""
    rest = _QUEST_LEAD.sub("", piece.strip())
    words = rest.split()
    for i in range(len(words), 0, -1):
        enum = _QUEST_BY_KEY.get(_quest_key(" ".join(words[:i])))
        if enum and _QUEST_TAIL_OK.match(" ".join(words[i:])):
            return enum
    return None


def _split_outside_parens(text: str, rx: re.Pattern) -> list[str]:
    """Split on rx matches that are not inside parentheses."""
    parts, last = [], 0
    for m in rx.finditer(text):
        before = text[:m.start()]
        if before.count("(") == before.count(")"):
            parts.append(text[last:m.start()])
            last = m.end()
    parts.append(text[last:])
    return [x.strip() for x in parts if x.strip()]


def _piece(raw: str) -> tuple[str, list[str], bool, bool]:
    """raw alternative/conjunct -> (text without parens, extra alternatives, altish, descriptive)."""
    main, parens = _strip_parens(raw)
    extra, altish, descriptive = [], False, False
    for p in parens:
        am = _PAREN_ALT.match(p)
        if am:
            extra.append(am.group(1))
        elif _PAREN_HARMLESS.match(p):
            continue
        elif _PAREN_ALTISH.search(p):
            altish = True      # may describe another way in: never lock on the rules alone
        else:
            descriptive = True  # unknown detail: keep the rules, flag the group manual
    return main, extra, altish, descriptive


def parse_access(requirements: list[str]) -> dict:
    """Pure: free-text requirement strings -> {"groups": [...]} (see README, "access")."""
    groups: list[dict] = []
    for raw in requirements or []:
        text = re.sub(r"\s+", " ", str(raw)).strip()
        if not text:
            continue
        low = text.lower()
        if _ACCESS_NONE.match(low):
            groups.append({"text": text, "any": [], "manual": False, "note": "no requirement"})
            continue
        note = next((n for rx, n in _ACCESS_WHOLE_STRING if rx.search(low)), None)
        if note is None and "diary" not in low and _TASK_ONLY.search(low):
            note = "task-only"
        if note is not None:
            groups.append({"text": text, "any": [], "manual": True, "note": note})
            continue

        unparsed: list[str] = []

        def classify(raw_piece: str) -> tuple[dict | None, bool, bool, list[str]]:
            main, extra, altish, descriptive = _piece(raw_piece)
            rule = _classify_piece(main, raw_piece)   # the piece's own words decide the quest state
            if rule is None and _looks_like_quest(main):
                unparsed.append(main)
            return rule, altish, descriptive, extra

        alts = _split_outside_parens(_UNLESS.sub(" or ", text), _OR_SPLIT)
        if len(alts) > 1:
            rules, uncheckable, descriptive = [], False, False
            queue = list(alts)
            while queue:
                r, altish, desc, extra = classify(queue.pop(0))
                queue.extend(extra)
                descriptive = descriptive or desc
                if r is None or altish:
                    uncheckable = True
                else:
                    rules.append(r)
            if uncheckable or not rules:
                groups.append({"text": text, "any": [], "manual": True, "_unparsed": unparsed})
            else:
                groups.append({"text": text, "any": rules, "manual": descriptive, "_unparsed": unparsed})
            continue

        whole, altish, descriptive, extra = classify(text)
        if whole is not None or extra:
            # single requirement, possibly with "(or ...)" alternatives in parentheses
            rules = [whole] if whole else []
            uncheckable = whole is None or altish
            for e in extra:
                r, a2, d2, more = classify(e)
                descriptive = descriptive or d2
                if r is None or a2 or more:
                    uncheckable = True
                else:
                    rules.append(r)
            if uncheckable:
                groups.append({"text": text, "any": [], "manual": True, "_unparsed": unparsed})
            else:
                groups.append({"text": text, "any": rules, "manual": descriptive, "_unparsed": unparsed})
            continue

        # conjunction: every checkable part is a necessary condition -> its own (AND) group
        conjuncts = _split_outside_parens(text, _AND_SPLIT)
        checkable: list[dict] = []
        if len(conjuncts) > 1:
            unparsed.clear()
            for c in conjuncts:
                r, a2, d2, more = classify(c)
                if a2 or more:
                    altish = True
                descriptive = descriptive or d2
                if r is not None:
                    checkable.append(r)
        if altish or not checkable:
            groups.append({"text": text, "any": [], "manual": True, "_unparsed": unparsed})
            continue
        for r in checkable:
            groups.append({"text": text, "any": [r], "manual": False, "_unparsed": []})
        if len(checkable) < len(conjuncts) or descriptive:
            groups.append({"text": text, "any": [], "manual": True, "_unparsed": unparsed})
    for g in groups:
        for r in g["any"]:
            if r["type"] == "quest" and r["quest"] not in QUESTS:
                raise AssertionError(f"unknown quest {r['quest']}")
    return {"groups": groups}


def apply_access(tasks: list[dict], report: dict) -> None:
    """Attach `access` to every location record; fill report accessCounts/accessUnparsed."""
    counts = {"locations": 0, "strings": 0, "distinctStrings": 0, "groups": 0,
              "checkable": 0, "checkableStrict": 0, "manual": 0, "distinctStringsCheckable": 0,
              "distinctStringsManualOnly": 0}
    unparsed: dict[str, int] = {}
    distinct: dict[str, bool] = {}
    for t in tasks:
        for loc in t["locations"]:
            reqs = loc.get("requirements") or []
            access = parse_access(reqs)
            counts["locations"] += 1
            counts["strings"] += len(reqs)
            for g in access["groups"]:
                counts["groups"] += 1
                if g["any"]:
                    counts["checkable"] += 1
                    if not g["manual"]:
                        counts["checkableStrict"] += 1
                if g["manual"]:
                    counts["manual"] += 1
                if g.pop("_unparsed", None):
                    unparsed[g["text"]] = unparsed.get(g["text"], 0) + 1
                distinct[g["text"]] = distinct.get(g["text"], False) or bool(g["any"])
            loc["access"] = access
    counts["distinctStrings"] = len(distinct)
    counts["distinctStringsCheckable"] = sum(1 for v in distinct.values() if v)
    counts["distinctStringsManualOnly"] = sum(1 for v in distinct.values() if not v)
    report["accessCounts"] = counts
    report["accessUnparsed"] = dict(sorted(unparsed.items()))


def _strip_unparsed(access: dict) -> dict:
    for g in access["groups"]:
        g.pop("_unparsed", None)
    return access


def self_test() -> int:
    """`generate.py --self-test`: assertions for parse_access on the tricky strings."""
    def one(s: str) -> list[dict]:
        return _strip_unparsed(parse_access([s]))["groups"]

    def rules(s: str) -> list[dict]:
        gs = one(s)
        assert len(gs) == 1, (s, gs)
        return gs[0]["any"]

    def quest(enum: str, state: str = "FINISHED") -> dict:
        return {"type": "quest", "quest": enum, "name": QUESTS[enum], "state": state}

    def manual(s: str, note: str | None = None) -> None:
        gs = one(s)
        assert gs and all(g["any"] == [] and g["manual"] for g in gs), (s, gs)
        if note is not None:
            assert gs[0].get("note") == note, (s, gs)

    checks = 0
    # skills / combat / members
    assert rules("40 Slayer") == [{"type": "skill", "skill": "SLAYER", "level": 40}]; checks += 1
    assert rules("Slayer 1") == [{"type": "skill", "skill": "SLAYER", "level": 1}]; checks += 1
    assert rules("40 Slayer (not boostable)") == [{"type": "skill", "skill": "SLAYER", "level": 40}]; checks += 1
    assert one("40 Slayer (not boostable)")[0]["manual"] is False; checks += 1
    assert rules("45 Agility (boostable) for the forest obstacles") == [{"type": "skill", "skill": "AGILITY", "level": 45}]; checks += 1
    assert rules("58 Sailing (not boostable) to dock on Laguna Aurorae the first time") == [{"type": "skill", "skill": "SAILING", "level": 58}]; checks += 1
    assert rules("Members (Varlamore)") == [{"type": "members"}]; checks += 1
    assert rules("Members") == [{"type": "members"}]; checks += 1
    g = one("66 Slayer (82 for Ancient Wyverns)")
    assert g[0]["any"] == [{"type": "skill", "skill": "SLAYER", "level": 66}] and g[0]["manual"] is True, g; checks += 1
    manual("91 Slayer (boostable with wild pie on a hellhound task, must stay at or above 91)"); checks += 1
    manual("Optional: 72 Agility shortcut", "optional"); checks += 1
    manual("82 Agility (boostable) shortcut recommended", "optional"); checks += 1
    manual("59 Agility for the southern entrance"); checks += 1
    # OR alternatives
    assert rules("60 Strength (boulder) or 60 Agility (jutting wall), boostable") == [
        {"type": "skill", "skill": "STRENGTH", "level": 60}, {"type": "skill", "skill": "AGILITY", "level": 60}]; checks += 1
    assert rules("60 Strength or 60 Agility to enter the dungeon") == [
        {"type": "skill", "skill": "STRENGTH", "level": 60}, {"type": "skill", "skill": "AGILITY", "level": 60}]; checks += 1
    assert rules("Partial completion of The Corsair Curse or level 10 Agility") == [
        quest("THE_CORSAIR_CURSE", "IN_PROGRESS"), {"type": "skill", "skill": "AGILITY", "level": 10}]; checks += 1
    manual("Dusty key or 70 Agility"); checks += 1   # item alternative -> never lock on Agility alone
    manual("Medium Wilderness Diary (or a Callisto boss Slayer task)"); checks += 1
    manual("Hard Wilderness Diary OR a Callisto boss Slayer task"); checks += 1
    manual("Hard Wilderness Diary (a skeleton task does not bypass it; only a Vet'ion boss task does)"); checks += 1
    manual("Boots of stone / brimstone / granite boots unless elite Kourend & Kebos Diary"); checks += 1
    manual("Rope, unless the medium Kandarin Diary is done"); checks += 1
    manual("Partial Troll Stronghold (defeat Dad) or Easy Combat Achievements"); checks += 1
    manual("Partial Troll Stronghold (or Easy Combat Achievements for Ghommal's hilt teleport) and 60 Strength or 60 Agility to enter the dungeon"); checks += 1
    # diaries
    assert rules("Medium Wilderness Diary") == [{"type": "diary", "varbit": "WILDERNESS_DIARY_MEDIUM_COMPLETE", "name": "Wilderness Medium diary"}]; checks += 1
    assert rules("Elite Kourend & Kebos Diary is complete") == [{"type": "diary", "varbit": "KOUREND_DIARY_ELITE_COMPLETE", "name": "Kourend & Kebos Elite diary"}]; checks += 1
    assert rules("Morytania hard diary") == [{"type": "diary", "varbit": "MORYTANIA_DIARY_HARD_COMPLETE", "name": "Morytania Hard diary"}]; checks += 1
    manual("Karamja hard diary"); checks += 1
    manual("Medium Wilderness Diary recommended for the improved drop table", "optional"); checks += 1
    # quests
    assert rules("Priest in Peril") == [quest("PRIEST_IN_PERIL")]; checks += 1
    assert rules("Heroes' Quest") == [quest("HEROES_QUEST")]; checks += 1
    assert rules("Partial completion of Heroes' Quest") == [quest("HEROES_QUEST", "IN_PROGRESS")]; checks += 1
    assert rules("Olaf's Quest started") == [quest("OLAFS_QUEST", "IN_PROGRESS")]; checks += 1
    g = one("Olaf's Quest (Slayer task list says partial completion; Brine rat and Konar pages say completion)")
    assert g[0]["any"] == [quest("OLAFS_QUEST", "IN_PROGRESS")] and g[0]["manual"] is True and "note" not in g[0], g; checks += 1
    assert one("Hot Stuff unlock (100 Slayer reward points)")[0]["manual"] is False; checks += 1
    assert "note" not in one("Hard Wilderness Diary OR a Callisto boss Slayer task")[0]; checks += 1
    manual("Troll Stronghold partial (defeat Dad) or Easy Combat Achievements (Ghommal's hilt teleport)"); checks += 1
    assert rules("Troll Stronghold partial") == [quest("TROLL_STRONGHOLD", "IN_PROGRESS")]; checks += 1
    assert "note" not in one("Hard Wilderness Diary (a skeleton task does not bypass it; only a Vet'ion boss task does)")[0]; checks += 1
    assert one("Skippy and the Mogres miniquest (per Slayer task list Required column; the Mogre page says this was formerly required)")[0]["any"] == []; checks += 1
    assert rules("Priest in Peril (Mort'ton access)") == [quest("PRIEST_IN_PERIL")]; checks += 1
    assert one("Priest in Peril for Morytania access")[0]["manual"] is False; checks += 1
    assert rules("Completion of Desert Treasure II - The Fallen Empire") == [quest("DESERT_TREASURE_II__THE_FALLEN_EMPIRE")]; checks += 1
    assert rules("Dragon Slayer II completed") == [quest("DRAGON_SLAYER_II")]; checks += 1
    assert rules("DS2") == [quest("DRAGON_SLAYER_II")]; checks += 1
    assert rules("Started The Lost Tribe") == [quest("THE_LOST_TRIBE", "IN_PROGRESS")]; checks += 1
    assert rules("Desert Treasure I started (reached the diamond search)") == [quest("DESERT_TREASURE_I", "IN_PROGRESS")]; checks += 1
    assert rules("Partial completion of Monkey Madness II") == [quest("MONKEY_MADNESS_II", "IN_PROGRESS")]; checks += 1
    assert rules("Perilous Moons (partial completion for access)") == [quest("PERILOUS_MOONS", "IN_PROGRESS")]; checks += 1
    assert rules("The Path of Glouphrie (partial)") == [quest("THE_PATH_OF_GLOUPHRIE", "IN_PROGRESS")]; checks += 1
    assert rules("Regicide completed to the point of reaching Port Tyras") == [quest("REGICIDE", "IN_PROGRESS")]; checks += 1
    assert rules("Lunar Diplomacy started far enough to board the Lady Zay") == [quest("LUNAR_DIPLOMACY", "IN_PROGRESS")]; checks += 1
    assert rules("Watchtower quest progressed to the point of enclave access") == [quest("WATCHTOWER", "IN_PROGRESS")]; checks += 1
    assert rules("Recipe for Disaster: Freeing Sir Amik Varze started") == [quest("RECIPE_FOR_DISASTER__SIR_AMIK_VARZE", "IN_PROGRESS")]; checks += 1
    assert rules("Enter the Abyss miniquest or partial Fairytale II - Cure a Queen (fairy rings unlocked)") == [
        quest("ENTER_THE_ABYSS"), quest("FAIRYTALE_II__CURE_A_QUEEN", "IN_PROGRESS")]; checks += 1
    g = one("Mourning's End Part II (started; Slayer ring teleport needs it completed)")
    assert g[0]["any"] == [quest("MOURNINGS_END_PART_II", "IN_PROGRESS")] and g[0]["manual"] is False, g; checks += 1
    manual("Song of the Elves to use the Gwenith rowboat"); checks += 1
    manual("Lair of Tarn Razorlor miniquest completed for more dogs to spawn"); checks += 1
    manual("Dragon Slayer I progressed to Crandor for the Crandor side (the Karamja side needs nothing)"); checks += 1
    manual("Full mourner gear, or Mourning's End Part II for the Slayer ring dark beast teleport"); checks += 1
    manual("Not available after Song of the Elves", "negative requirement"); checks += 1
    manual("Access to Fossil Island"); checks += 1
    manual("Waterfall Quest completed (or Glarial's amulet during it)"); checks += 1
    # assignment requirements (not location requirements)
    manual("Horror from the Deep (to be assigned)", "assignment requirement"); checks += 1
    manual("Combat 75 (to be assigned)", "assignment requirement"); checks += 1
    manual("Death Plateau (for God Wars Dungeon slayer tasks)", "assignment requirement"); checks += 1
    manual("Like a Boss unlock (200 Slayer reward points) to receive boss tasks", "assignment requirement"); checks += 1
    manual("18 Slayer for the boss task", "assignment requirement"); checks += 1
    manual("Dragon Slayer I started (Krystilia only assigns dragons to players who have started it)", "assignment requirement"); checks += 1
    # unlocks
    assert rules("Like a Boss unlock (200 Slayer reward points)") == [{"type": "unlock", "name": "Like a Boss"}]; checks += 1
    assert rules("Hot stuff unlock (100 Slayer reward points)") == [{"type": "unlock", "name": "Hot Stuff"}]; checks += 1
    assert rules("Watch the birdie unlock (80 Slayer reward points)") == [{"type": "unlock", "name": "Watch the Birdie"}]; checks += 1
    # conjunctions: each checkable part is its own group, the rest one manual group
    g = one("70 Agility, 70 Hitpoints, 70 Ranged and 70 Strength")
    assert [x["any"][0]["skill"] for x in g] == ["AGILITY", "HITPOINTS", "RANGED", "STRENGTH"] and not any(x["manual"] for x in g), g; checks += 1
    g = one("70 Ranged, crossbow and mith grapple")
    assert g[0]["any"] == [{"type": "skill", "skill": "RANGED", "level": 70}] and g[0]["manual"] is False
    assert g[1]["any"] == [] and g[1]["manual"] is True and len(g) == 2, g; checks += 1
    g = one("Pickaxe and 50 Mining")
    assert g[0]["any"] == [{"type": "skill", "skill": "MINING", "level": 50}] and g[1]["manual"], g; checks += 1
    g = one("73 Sailing and a boat with an adamant keel or better to reach Ynysdail")
    assert g[0]["any"] == [{"type": "skill", "skill": "SAILING", "level": 73}] and len(g) == 2, g; checks += 1
    g = one("Shades of Mort'ton (completed) and a bronze shade key or better to enter")
    assert g[0]["any"] == [quest("SHADES_OF_MORTTON")] and len(g) == 2, g; checks += 1
    manual("Axe to cut vines (10 Woodcutting); 30 Agility per the task page, 56 Agility stepping stones for the fast route"); checks += 1
    manual("Skippy and the Mogres"[:0] + "Axe and 875 coins (or unlimited access bought from Saniboch)"); checks += 1
    # misc manual
    manual("Light source"); checks += 1
    manual("Task-only area", "task-only"); checks += 1
    manual("Must be on a fire giant task", "task-only"); checks += 1
    manual("Black dragon Slayer task (task-only area)", "task-only"); checks += 1
    manual("Warriors' Guild access (Attack + Strength 130, or 99 in one)"); checks += 1
    manual("10,000 coins to Sandicrahb (half price with easy Kourend & Kebos Diary, free with medium)"); checks += 1
    manual("40 Saradomin kill count (35/30/25/15 with the hard/elite/master/grandmaster Combat Achievement tiers) or an ecumenical key"); checks += 1
    assert one("None (free-to-play)") == [{"text": "None (free-to-play)", "any": [], "manual": False, "note": "no requirement"}]; checks += 1
    assert parse_access([]) == {"groups": []} and parse_access(["", "  "]) == {"groups": []}; checks += 1
    # the quest-looking phrase in a mixed string is reported, the string never gets a wrong rule
    acc = parse_access(["Darkness of Hallowvale (the laboratories are entered during the quest); Sins of the Father for full access"])
    assert [g["any"] for g in acc["groups"]] == [[quest("DARKNESS_OF_HALLOWVALE", "IN_PROGRESS")], []], acc
    assert "Sins of the Father for full access" in acc["groups"][1]["_unparsed"], acc; checks += 1
    # every quest rule refers to a real enum name, every diary varbit to a known constant
    for s in ("Priest in Peril", "SotE", "MM2", "RFD"):
        r = rules(s)[0]
        assert r["type"] == "quest" and r["quest"] in QUESTS and r["name"] == QUESTS[r["quest"]], r
    checks += 1
    # tidy_note: research bookkeeping goes, advice stays
    assert tidy_note("14 goblins (levels 2, 5 and 13; 14 LocLine pins).") == "14 goblins (levels 2, 5 and 13)."; checks += 1
    assert tidy_note("'In the Wilderness. Largest concentration.' (11 LocLine pins). Safespottable per the task table.") == \
        "'In the Wilderness. Largest concentration.' Safespottable."; checks += 1
    assert tidy_note("Exact level range not stated; the fortress itself is level 14-16.") == "The fortress itself is level 14-16."; checks += 1
    assert tidy_note("12 level-96 araxytes only; worse drops, so the task page says to avoid them.") == \
        "12 level-96 araxytes only; worse drops, so the wiki says to avoid them."; checks += 1
    assert tidy_note("Spider LocLine 'Hosidius'.") is None; checks += 1
    # tidy_strategy: a cut-off list item or an intro to a removed message goes
    assert tidy_strategy(["A cannon cannot be set up here; if attempted, players will receive the message:", "Next."]) == \
        ["A cannon cannot be set up here.", "Next."]; checks += 1
    assert tidy_strategy(["Styles:\n- Melee: bites.\n- Ranged: rears up on his\u2026"]) == ["Styles:\n- Melee: bites."]; checks += 1
    assert tidy_strategy(["Melee:", "Use a whip."]) == ["Melee:", "Use a whip."]; checks += 1
    # gear rules: every rule item exists in the bundled item list
    here = Path(__file__).resolve().parent
    items_json = here.parent.parent / "src" / "main" / "resources" / "com" / "slayercompanion" / "data" / "items.json"
    if items_json.exists():
        missing = check_gear_rules(load_gear_rules(), (i["name"] for i in json.loads(items_json.read_text(encoding="utf-8"))))
        assert not missing, missing
        checks += 1
    # master assignments: the cache snapshot replaces the wiki ranges, and a task it lacks has none
    snap = {"Hydras": {"mortimer": {"weight": 10, "min": 150, "max": 200}},
            "Gryphons": {"vannaka": {"weight": 10, "min": 30, "max": 80, "extMin": 110, "extMax": 160, "extUnlock": "x"}}}
    t = {"task": "Hydras", "masters": {"mortimer": {"min": 150, "max": 200, "extMin": 200, "extMax": 300,
                                                    "raw": "150-200 (200-300)", "alternatives": []}}}
    apply_master_assignments(t, snap)
    assert t["masters"] == {"mortimer": {"weight": 10, "min": 150, "max": 200, "extMin": None, "extMax": None}}; checks += 1
    t = {"task": "Gryphons", "masters": {}}
    apply_master_assignments(t, snap)
    assert t["masters"]["vannaka"]["extMin"] == 110 and "extUnlock" not in t["masters"]["vannaka"]; checks += 1
    t = {"task": "Vorkath", "masters": {"nieve": {"min": 3}}}
    apply_master_assignments(t, snap)
    assert t["masters"] == {}; checks += 1
    t = {"task": "Vorkath", "masters": {"nieve": {"min": 3}}}
    apply_master_assignments(t, {})
    assert t["masters"] == {"nieve": {"min": 3}}, "no snapshot: keep the wiki data"; checks += 1
    assert tidy_other_requirement("Completion of Quest Priest in Peril") == "Completion of Priest in Peril"; checks += 1
    assert tidy_other_requirement("Partial completion of Quest Lunar Diplomacy") == "Partial completion of Lunar Diplomacy"; checks += 1
    assert tidy_other_requirement("Quest points: 32") == "Quest points: 32"; checks += 1
    print(f"self-test OK: {checks} checks")
    return 0


# --------------------------------------------------------------------------
# Task assembly
# --------------------------------------------------------------------------

@dataclass
class TaskRow:
    enum: str
    display: str
    alternatives: list[str] = field(default_factory=list)


def read_task_list(path: Path) -> list[TaskRow]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.rstrip("\n").split("\t")
        enum = parts[0].strip()
        display = parts[1].strip() if len(parts) > 1 else enum
        alts = [a.strip() for a in parts[2].split("|")] if len(parts) > 2 and parts[2].strip() else []
        rows.append(TaskRow(enum, display, alts))
    return rows


def name_variants(monsters: list[dict]) -> None:
    """Give every monster record a name the plugin can use as a variant key.

    Variant names must be unique within a task. A record without an infobox name, or whose
    name another record of the task shares (e.g. "Dagannoth" on both `Dagannoth` and
    `Dagannoth (Waterbirth Island)`), takes its page title instead; the old name is kept in
    `infoboxName` so build_task() can rename that page's {{LocLine}} monsters to match.
    """
    counts: dict[str, int] = {}
    for m in monsters:
        if m.get("name"):
            counts[m["name"].lower()] = counts.get(m["name"].lower(), 0) + 1
    for m in monsters:
        name = m.get("name")
        if not m.get("hasInfobox"):
            continue
        # A qualified page title says which one it is ("Guard (dwarf)", "Rock (Troll)");
        # "(monster)" only separates the NPC page from an item or scenery page.
        qualified = bool(name) and m["page"].startswith(name + " (") and not m["page"].endswith("(monster)")
        if not name or qualified or (counts[name.lower()] > 1 and name != m["page"]):
            if name:
                m["infoboxName"] = name
            m["name"] = m["page"]


def build_task(cache: WikiCache, row: TaskRow, report: dict) -> dict:
    print(f"== {row.display}", file=sys.stderr)
    task_page, tried = resolve_task_page(cache, row.display)
    monster_pages, mtried, primary_pages = resolve_monster_pages(cache, row.display, row.alternatives, task_page)
    strategy_pages, stried = resolve_strategy_pages(cache, row.display, task_page, primary_pages)

    task: dict = {
        "enum": row.enum,
        "task": row.display,
        "alternatives": list(row.alternatives),
        "wikiTaskPage": task_page.title if task_page else None,
        "wikiTaskPageRequested": task_page.requested if task_page else None,
        "wikiTaskPageRedirected": task_page.redirected if task_page else None,
        "wikiTaskPageKind": None,
        "wikiTaskPageTried": tried,
        "wikiMonsterPages": [p.title for p in monster_pages],
        "wikiMonsterPagesTried": mtried,
        "bossTask": None,  # decided after the pages are parsed (see below)
        "taskId": None,
        "requirements": {"skills": [], "slayer": None, "combat": None, "other": None},
        "masters": {},
        "summary": None,
        "recommendedStyle": None,
        "requiredItems": [],
        "usefulItems": [],
        "superior": None,
        "xpPerKill": None,
        "gearTables": [],
        "exampleSetups": [],
        "strategy": [],
        "unlocks": [],
        "monsters": [],
        "locations": [],
        "curatedFile": None,
    }

    if task_page:
        text = task_page.wikitext
        infobox = parse_infobox_slayer(text)
        task["wikiTaskPageKind"] = "slayer-task" if infobox else "monster"
        if infobox:
            task["taskId"] = infobox["taskId"]
            task["requirements"] = infobox["requirements"]
            task["masters"] = infobox["masters"]
        gear_tables, setups, gear_sources = collect_gear(task_page, strategy_pages)
        collect_extra_gear(cache, row.display, gear_tables, setups, gear_sources)
        report["gearSources"][row.display] = {"pages": gear_sources, "strategyPagesTried": stried}
        task["gearTables"] = gear_tables
        task["exampleSetups"] = setups
        task["strategy"], task["summary"] = parse_strategy(text)
        task["unlocks"] = parse_unlocks(text)
        styles = []
        for g in gear_tables:
            if g["style"] and g["style"] not in styles:
                styles.append(g["style"])
        task["recommendedStyle"] = styles[0] if styles else None
    else:
        report["tasksWithoutTaskPage"].append({"task": row.display, "tried": tried})

    raw_locs: list[dict] = []
    for p in monster_pages:
        info, info_source = monster_info(p)
        report.setdefault("monsterInfoSources", {}).setdefault(info_source, 0)
        report["monsterInfoSources"][info_source] += 1
        locs = parse_loclines(p.wikitext, p.title)
        map_locs = [] if locs else parse_map_locations(p.wikitext, p.title)
        raw_locs.extend(locs + map_locs)
        entry = {"page": p.title, "redirected": p.redirected, "hasInfobox": info is not None,
                 "locLines": len(locs), "mapLocations": len(map_locs)}
        if info:
            entry.update(info)
        task["monsters"].append(entry)
    name_variants(task["monsters"])
    # A page's {{LocLine}}s name its monster as the infobox does, in another case, or by
    # the page title ("Frost Dragon", "Monkey (monster)"); file them under the record's
    # final (variant) name so the plugin can match spots to variants.
    # A LocLine without a name is the page's own monster; "TzHaar-Xil (Melee)" is a
    # version of TzHaar-Xil.
    by_page = {m["page"]: m for m in task["monsters"] if m.get("name")}
    for loc in raw_locs:
        m = by_page.get(loc.get("page"))
        if not m:
            continue
        if not loc.get("monster"):
            loc["monster"] = m["name"]
            continue
        aliases = {(m.get("infoboxName") or "").lower(), m["name"].lower(), m["page"].lower()} - {""}
        low = loc["monster"].lower()
        if low in aliases or any(low.startswith(a + " (") for a in aliases):
            loc["monster"] = m["name"]
    for extra in EXTRA_LOCATION_PAGES.get(row.display, []):
        page = cache.get(extra["page"])
        if not page.ok:
            continue
        for loc in parse_loclines(page.wikitext, page.title):
            if extra.get("only") and loc["name"] not in extra["only"]:
                continue
            if extra.get("rename"):
                loc["name"] = loc["displayName"] = extra["rename"]
            if extra.get("monster"):
                loc["monster"] = extra["monster"]
            raw_locs.append(loc)
    task["locations"] = merge_locations(raw_locs)

    primary = None
    for m in task["monsters"]:
        if m.get("slayerXp") is not None:
            primary = m
            break
    if primary:
        task["xpPerKill"] = primary["slayerXp"]
        if task["requirements"]["slayer"] is None and primary.get("slayerLevel"):
            task["requirements"]["slayer"] = primary["slayerLevel"]

    # Boss task: the "task page" is a monster page (no {{Infobox Slayer}}) whose
    # {{Infobox Monster}} `cat` lists "Bosses" (or an explicit override).
    # Plain monsters without a Slayer task page (e.g. Cows) also land on a
    # monster page but are not bosses.  Curated data can override this.
    boss = False
    if task_page and task["wikiTaskPageKind"] == "monster":
        cats = [m.get("slayerCategory") or "" for m in task["monsters"] if m["page"] == task_page.title]
        if not cats:  # overview page without its own infobox: look at the variant pages
            cats = [m.get("slayerCategory") or "" for m in task["monsters"] if m.get("hasInfobox")]
            boss = bool(cats) and all("bosses" in c.lower() for c in cats)
        else:
            boss = any("bosses" in c.lower() for c in cats)
        boss = boss or row.display in BOSS_TASK_OVERRIDES
    task["bossTask"] = boss

    if not task["gearTables"] and not task["exampleSetups"]:
        report["tasksWithoutEquipment"].append(row.display)
    if not task["locations"]:
        report["tasksWithoutLocations"].append({"task": row.display, "monsterPagesTried": mtried})
    if not task["monsters"]:
        report["tasksWithoutMonsterPage"].append({"task": row.display, "tried": mtried})
    return task


def build_locations_index(tasks: list[dict]) -> list[dict]:
    index: dict[str, dict] = {}
    for t in tasks:
        for loc in t["locations"]:
            entry = index.setdefault(loc["id"], {
                "id": loc["id"], "name": loc["name"], "displayName": loc["displayName"],
                "link": loc["link"], "annotations": loc["annotations"], "plane": loc["plane"],
                "mapID": loc["mapID"], "x": None, "y": None, "spawns": [], "spawnsByTask": {},
                "tasks": [], "wilderness": loc["wilderness"], "coordsMissing": True,
                "requirementsByTask": {}, "accessByTask": {},
            })
            if t["task"] not in entry["tasks"]:
                entry["tasks"].append(t["task"])
            entry["spawnsByTask"][t["task"]] = loc["spawns"]
            entry["requirementsByTask"][t["task"]] = loc.get("requirements") or []
            entry["accessByTask"][t["task"]] = loc.get("access") or parse_access(loc.get("requirements") or [])
            for s in loc["spawns"]:
                if s not in entry["spawns"]:
                    entry["spawns"].append(s)
            if entry["mapID"] is None:
                entry["mapID"] = loc["mapID"]
            entry["wilderness"] = entry["wilderness"] or loc["wilderness"]
    out = []
    for e in index.values():
        e["x"], e["y"] = centroid(e["spawns"])
        e["coordsMissing"] = e["x"] is None
        e["spawnCount"] = len(e["spawns"])
        out.append(e)
    out.sort(key=lambda e: e["id"])
    return out


# --------------------------------------------------------------------------
# Post-processing: task summary, derived styles, gear pages
# --------------------------------------------------------------------------

TRAINING_PAGE = "Slayer training"


def _section_body(text: str, title_re: str, level: int = 2) -> str | None:
    for s in sections(text):
        if s["level"] == level and re.fullmatch(title_re, s["title"], re.I):
            return text[s["start"]:s["end"]]
    return None


_CELL_ATTR = re.compile(r'^\s*(?:[\w\-]+\s*=\s*(?:"[^"]*"|\'[^\']*\'|[^\s|]+)\s*)+\|(?!\|)')


def _cell_text(cell: str, line_sep: str = " ") -> str:
    cell = _CELL_ATTR.sub("", cell)
    txt = plain_text(cell)
    lines = [ln.strip() for ln in txt.split("\n") if ln.strip()]
    return line_sep.join(lines)


def parse_task_summary(text: str) -> list[dict]:
    """Rows of the ==Task summary== wikitable (Assignment, Slayer level, Weight,
    Pros, Cons, Recommendation, Approx. XP/h)."""
    body = _section_body(text, r"task summary")
    if body is None:
        return []
    m = re.search(r"\{\|.*?\n\|\}", body, re.S)
    if not m:
        return []
    rows: list[dict] = []
    for cells in _table_rows(m.group(0)):
        if len(cells) < 7:
            continue
        assignment = _cell_text(cells[0])
        if not assignment:
            continue
        rows.append({
            "assignment": assignment,
            "slayerLevel": to_int(_cell_text(cells[1])),
            "weight": to_int(_cell_text(cells[2])),
            "pros": _cell_text(cells[3]),
            "cons": _cell_text(cells[4]),
            "recommendation": _cell_text(cells[5]),
            "xpPerHour": _cell_text(cells[6], "; "),
        })
    return rows


def _norm_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def _plural_variants(name: str) -> list[str]:
    low = name.lower()
    out = [name + "s", name + "es"]
    if low.endswith("y"):
        out.append(name[:-1] + "ies")
    if low.endswith("f"):
        out.append(name[:-1] + "ves")
    if low.endswith("fe"):
        out.append(name[:-2] + "ves")
    if low.endswith("man"):
        out.append(name[:-3] + "men")
    return out


def _name_keys(name: str | None) -> set[str]:
    """Normalised forms of a name: as is, without 'The', singular and plural."""
    keys: set[str] = set()
    if not name:
        return keys
    forms = {name, strip_the(name)}
    for base in list(forms):
        forms.update(singular_variants(base))
    for base in list(forms):
        forms.update(_plural_variants(base))
    for f in forms:
        k = _norm_name(f)
        if k:
            keys.add(k)
    return keys


def _task_key_tiers(task: dict) -> list[set[str]]:
    """Matching keys in priority order: display name, alternatives, monster names."""
    tier0 = _name_keys(task["task"])
    tier1: set[str] = set()
    for alt in task.get("alternatives", []):
        tier1 |= _name_keys(alt)
    tier2: set[str] = set()
    for m in task.get("monsters", []):
        tier2 |= _name_keys(m.get("page"))
        tier2 |= _name_keys(m.get("name"))
        for v in m.get("versions") or []:
            tier2 |= _name_keys(v)
    return [tier0, tier1 - tier0, tier2 - tier0 - tier1]


def apply_training_summary(tasks: list[dict], cache: WikiCache, report: dict | None = None) -> list[dict]:
    """Attach the "Slayer training" task-summary row to the matching task as
    task["trainingSummary"]; returns the rows that matched no task (also written
    to report["trainingSummaryUnmatched"] when a report is given)."""
    for t in tasks:
        t.pop("trainingSummary", None)
    page = cache.get(TRAINING_PAGE)
    rows = parse_task_summary(page.wikitext) if page.ok else []
    tiers = [_task_key_tiers(t) for t in tasks]
    unmatched: list[dict] = []
    ambiguous: list[dict] = []
    for row in rows:
        keys = _name_keys(row["assignment"])
        hit: dict | None = None
        for level in range(3):
            cands = [t for t, tk in zip(tasks, tiers) if tk[level] & keys]
            if cands:
                if len(cands) > 1:
                    ambiguous.append({"assignment": row["assignment"], "tasks": [c["task"] for c in cands]})
                hit = cands[0]
                break
        if hit is None:
            unmatched.append(dict(row, reason="no task"))
        elif "trainingSummary" in hit:
            unmatched.append(dict(row, reason=f"task {hit['task']!r} already has a row"))
        else:
            hit["trainingSummary"] = {
                "assignment": row["assignment"],
                "pros": row["pros"],
                "cons": row["cons"],
                "recommendation": row["recommendation"],
                "xpPerHour": row["xpPerHour"],
            }
    if report is not None:
        report["trainingSummaryRows"] = len(rows)
        report["trainingSummaryUnmatched"] = unmatched
        report["trainingSummaryAmbiguous"] = ambiguous
    return unmatched


_STYLE_WORDS = (
    (r"\bmagic\b|\bbarrag(e|ing)\b|\bburst(s|ing)?\b", "Magic"),
    (r"\branged?\b|\bchin(s|ning|chompas?)?\b", "Ranged"),
    (r"\bmelee\b", "Melee"),
)

_WEAKNESS_WORDS = (
    (r"\b(crush|slash|stab)\b", "Melee"),
    (r"\b(fire|water|earth|air|magic)\b", "Magic"),
    (r"\b(ranged?|arrows?|bolts?)\b", "Ranged"),
)


def _style_from_text(text: str | None, rules=_STYLE_WORDS) -> str | None:
    """First style named in `text`, in order of appearance."""
    if not text:
        return None
    best: tuple[int, str] | None = None
    for pattern, style in rules:
        m = re.search(pattern, text, re.I)
        if m and (best is None or m.start() < best[0]):
            best = (m.start(), style)
    return best[1] if best else None


def _cached_monster_page(cache: WikiCache, task: dict, final_title: str) -> Page | None:
    """The cached page whose final title is `final_title`, never fetching."""
    for requested in [final_title] + list(task.get("wikiMonsterPagesTried", [])):
        _, meta_path = cache._paths(requested)
        if not meta_path.exists():
            continue
        p = cache.get(requested)
        if p.ok and p.title == final_title:
            return p
    return None


def _monster_weakness(cache: WikiCache, task: dict) -> str | None:
    """Text of the infobox `weakness` / `elementalweaknesstype` field(s) of the
    task's monsters (the wiki replaced `weakness` with `elementalweaknesstype`)."""
    parts: list[str] = []
    for m in task.get("monsters", []):
        if not m.get("hasInfobox"):
            continue
        page = _cached_monster_page(cache, task, m["page"])
        if page is None:
            continue
        tpls = find_templates(page.wikitext, ("infobox monster",))
        if not tpls:
            continue
        _, named = tpls[0].args
        for key, value in named.items():
            if re.fullmatch(r"weakness\d*", key):
                txt = plain_text(value)
                if txt:
                    parts.append(txt)
            elif re.fullmatch(r"elementalweaknesstype(\d*)", key):
                # A mild elemental weakness (most monsters have 15-20%) is not a reason to
                # recommend Magic; only a strong one (>= 50%) counts.
                suffix = key[len("elementalweaknesstype"):]
                pct_txt = plain_text(named.get("elementalweaknesspercent" + suffix, "") or "")
                m = re.search(r"\d+", pct_txt)
                pct = int(m.group(0)) if m else 0
                txt = plain_text(value)
                # Disabled: an elemental weakness says which spell hits hardest, not that Magic is
                # the best way to do the task (spectres, dragons and kraken are weak to elements but
                # are not normally maged). Kept for reference; raise the bar to re-enable.
                if txt and pct >= 1000:
                    parts.append(txt)
    return "; ".join(parts) or None


def derive_recommended_styles(tasks: list[dict], cache: WikiCache) -> dict[str, int]:
    """Fill recommendedStyle where it is null, from data already in the record,
    and record where it came from in recommendedStyleSource."""
    counts: dict[str, int] = {}
    for t in tasks:
        kept = t.get("recommendedStyleSource")
        if kept in ("gearTables", "curated") and "gearTables" not in t:
            # reused through --only: tasks.json does not keep the gear tables and the
            # curated file is not applied again, so keep the style
            counts[kept] = counts.get(kept, 0) + 1
            continue
        if t.get("recommendedStyleSource") is not None:
            # derived on a previous run (task reused through --only): redo it
            t["recommendedStyle"] = None
        t.pop("recommendedStyleSource", None)
        source = None
        if t["recommendedStyle"]:
            source = "curated" if t.get("curatedFile") else "gearTables"
        else:
            style = next((g["style"] for g in t.get("gearTables", []) if g.get("style")), None)
            if style:
                t["recommendedStyle"], source = style, "gearTables"
            else:
                ts = t.get("trainingSummary") or {}
                style = _style_from_text(ts.get("recommendation")) or _style_from_text(ts.get("xpPerHour"))
                if style:
                    t["recommendedStyle"], source = style, "trainingSummary"
                else:
                    style = _style_from_text(_monster_weakness(cache, t), _WEAKNESS_WORDS)
                    if style:
                        t["recommendedStyle"], source = style, "monsterWeakness"
        if source:
            t["recommendedStyleSource"] = source
            counts[source] = counts.get(source, 0) + 1
    return counts


def apply_gear_pages(tasks: list[dict]) -> None:
    """The wiki page the plugin's "Recommended gear & strategy" link opens: `gearPage` is the
    page of the task's first own gear table (null when it has none; the plugin then opens the
    task page); `variantGearPages` maps each selectable variant with its own tables to their
    page.  The tables themselves are not shipped (plugin 0.6.0 links to the wiki instead), so
    they are removed here.  Tasks reused through --only have no tables left and keep the pages
    they already have."""
    for t in tasks:
        if "gearTables" not in t:
            continue
        tables = t.pop("gearTables")
        t.pop("exampleSetups", None)
        t["gearPage"] = next((g["source"] for g in tables if g.get("variant") is None), None)
        pages: dict[str, str] = {}
        for g in tables:
            if g.get("variant") is not None:
                pages.setdefault(g["variant"], g["source"])
        t["variantGearPages"] = pages


# ---------------------------------------------------------------- tidy (no network)
#
# Post-processing that only reads the task records, so it can be re-applied to an existing
# tasks.json with --tidy.  Every step is idempotent.

GEAR_RULES_PATH = Path(__file__).resolve().parent / "curated" / "gear-rules.json"
MASTER_ASSIGNMENTS_PATH = Path(__file__).resolve().parent / "curated" / "master-assignments.json"

# Research notes that ended up in location notes: where a count or a claim came from.  The
# curated files keep them as evidence; the player only needs the advice.
_PROVENANCE = re.compile(
    r"LocLine|\bpins?\b|\binfobox\b|task (?:page )?table|monster table|inhabitants table|monster list|"
    r"page places it|pages read|this run|Location [Cc]omparison|Monster Variants|task page row|"
    r"not on the task page|[Nn]ot in the task page|listed only on|the count comes only|Slayer task list names|"
    r"wikitext|\[\[|'\||\(see evidence\)|left unknown|recorded here|not stated|does not prove|the range here|"
    r"pages? read|\bthe union\b|taken from|task list row|maplinks|area page|area list|prose says|page only says|"
    r"details recorded",
    re.I)
# ...and inside brackets also any "page says" / "per ..." aside.
_PAREN_PROVENANCE = re.compile(_PROVENANCE.pattern + r"|page (?:says|lists|calls|adds)|\bper\b|\btable\b|^[A-Z][\w' ]* page$",
                               re.I)
_CITATION = re.compile(r"\s*\((?:task table|task page|Slayer task/[^()]+|[A-Z][\w'&. -]{0,40} (?:page|changelog|update history))\)")


def _split_sentences(text: str) -> list[str]:
    # a full stop (not an ellipsis), "!" or "?", optionally inside a closing quote
    end = r"(?:(?<=[^.]\.)|(?<=[!?])|(?<=[^.]\.['\"’])|(?<=[!?]['\"’]))"
    return [s for s in re.split(end + r"\s+(?=[A-Z'\"(])", text) if s]


def _split_clauses(sentence: str) -> list[str]:
    """Split at "; " outside brackets."""
    out, depth, start = [], 0, 0
    for i, ch in enumerate(sentence):
        depth += 1 if ch == "(" else -1 if ch == ")" and depth else 0
        if ch == ";" and depth == 0:
            out.append(sentence[start:i].strip())
            start = i + 1
    out.append(sentence[start:].strip())
    return [c for c in out if c]


def _tidy_parenthetical(inner: str) -> str:
    keep = [part.strip() for part in inner.split(";")
            if part.strip() and not _PAREN_PROVENANCE.search(part)]
    return "; ".join(keep)


def tidy_note(text: str | None) -> str | None:
    """A location note without the research bookkeeping ("12 LocLine pins", "per the task table",
    "(Canifis)" after a wiki quote); the advice itself stays."""
    if not text:
        return text
    t = _CITATION.sub("", text)
    # "'quote' (Page name)": the page a wiki quote came from
    t = re.sub(r"(['’\"])\s*\((?:[A-Z][\w'&.-]*(?: [\w'&.()-]+){0,5})\)", r"\1", t)

    t = re.sub(r",?\s*\bper the [^;.()]*(?:LocLine|table|infobox)[^;.()]*", " ", t)

    def paren(m: re.Match) -> str:
        kept = _tidy_parenthetical(m.group(1))
        return f" ({kept})" if kept else ""
    t = re.sub(r"\s*\(([^()]*)\)", paren, t)
    t = re.sub(r",?\s*listed only on the [^;.]*|;?\s*not on the task page[^;.]*", "", t)
    t = re.sub(r"\bRoutes from the same page:", "Routes:", t)
    t = re.sub(r",?\s*\b(?:per|according to) (?:the )?(?:task (?:page )?table|task page|[A-Z][\w' ]* page)\b", "", t)
    t = re.sub(r"\b([Tt])he (?:task|[A-Z][\w']*(?: \([\w ]+\))?) page (says|adds|calls)\b", r"\1he wiki \2", t)
    sentences = []
    for sentence in _split_sentences(t):
        clauses = [c for c in _split_clauses(sentence) if not _PROVENANCE.search(c)]
        if clauses:
            joined = "; ".join(clauses).strip()
            if not re.search(r"[.!?]['\"’)]?$", joined):
                joined += "."
            sentences.append(joined[0].upper() + joined[1:])
    t = " ".join(sentences).strip()
    t = re.sub(r"\bTask page:", "The wiki:", t)
    t = re.sub(r"\b([Tt])he task page\b", r"\1he wiki", t)
    t = re.sub(r"(^|\s)(['\"])\s+", r"\1\2", t)
    t = re.sub(r"([.!?]['\"’])\.", r"\1", t)
    t = re.sub(r"\s+([,.;:])", r"\1", t)
    t = re.sub(r"\s{2,}", " ", t)
    t = re.sub(r"^[;,.\s]+", "", t)
    return t or None


def tidy_strategy(paras: list[str]) -> list[str]:
    """Drop what reads as cut off: a sentence ending in ':' whose list or message was a table or
    template the text converter removed, and a paragraph cut mid-sentence at the length cap."""
    out: list[str] = []
    for i, p in enumerate(paras):
        text = p.strip()
        nxt = paras[i + 1].lstrip() if i + 1 < len(paras) else ""
        if text.endswith(":") and not nxt.startswith("- "):
            if "\n" not in text and len(text) < 40:
                if not nxt:
                    continue  # a heading with nothing under it
            else:
                # "... cannot be set up here; if attempted, players will receive the message:" keeps
                # the part before the semicolon; an intro to a removed table goes.
                sentences = _split_sentences(text)
                last = sentences.pop()
                if ";" in last:
                    sentences.append(last[:last.rindex(";")].rstrip() + ".")
                text = " ".join(sentences).strip()
        if text.endswith("…"):
            if "\n" in text:
                text = text[:text.rindex("\n")].rstrip()  # drop the list item that was cut
            else:
                text = " ".join(_split_sentences(text)[:-1]).strip()
        if text:
            out.append(text)
    return out


def load_gear_rules(path: Path = GEAR_RULES_PATH) -> dict:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"requiredGear": [], "meleeReach": {}}


def _gear_pattern_matches(pattern: str, name: str) -> bool:
    p, n = pattern.lower(), name.lower()
    if p.startswith("*"):
        return p[1:] in n
    return n == p or n.startswith(p + " (")


def check_gear_rules(rules: dict, item_names: Iterable[str]) -> list[str]:
    """Rule items that match no item in items.json (typos would silently never match)."""
    names = list(item_names)
    return [pat for r in rules.get("requiredGear", []) for pat in r["items"]
            if not any(_gear_pattern_matches(pat, n) for n in names)]


def apply_gear_rules(task: dict, rules: dict) -> None:
    variants = [m["name"] for m in task.get("monsters") or [] if m.get("name")]
    out = []
    for r in rules.get("requiredGear", []):
        if task["task"] not in r["tasks"]:
            continue
        rule = {"slot": r["slot"], "items": list(r["items"]), "reason": r["reason"],
                "unlessKourendElite": bool(r.get("unlessKourendElite"))}
        if r.get("onlyVariants"):
            rule["onlyVariants"] = [v for v in r["onlyVariants"] if v in variants]
            if not rule["onlyVariants"]:
                continue
        if r.get("exceptVariantsMatching"):
            rule["exceptVariants"] = [v for v in variants if re.search(r["exceptVariantsMatching"], v)]
        out.append(rule)
    task["requiredGear"] = out
    reach = rules.get("meleeReach", {})
    for m in task.get("monsters") or []:
        stats = m.get("combatStats")
        if stats is not None:
            entry = reach.get(m.get("name"))
            if entry:
                stats["meleeReach"] = entry["reach"]
            else:
                stats.pop("meleeReach", None)


def tidy_locations(task: dict) -> None:
    locs = task.get("locations") or []
    # A floor or a second pin of a curated Wilderness spot (the same wiki link, singular or plural)
    # is in the Wilderness too: the Rogues' Castle floors, the "Revenant Cave" pin.
    def key(link: str | None) -> str | None:
        return re.sub(r"s$", "", link.strip().lower()) if link else None

    wild_by_link = {}
    for l in locs:
        if l.get("curated") and l.get("wilderness") and l.get("link"):
            wild_by_link.setdefault(key(l["link"]), l)
    for l in locs:
        if not l.get("curated") and not l.get("wilderness"):
            src = wild_by_link.get(key(l.get("link")))
            if src is not None:
                l["wilderness"] = True
                l["wildernessLevelMin"] = src.get("wildernessLevelMin")
                l["wildernessLevelMax"] = src.get("wildernessLevelMax")
        if l.get("notes"):
            l["notes"] = tidy_note(l["notes"])
        if l.get("requirements"):
            l["requirements"] = [r for r in l["requirements"] if not re.match(r"(?i)^none\b", r.strip())]


def load_master_assignments(path: Path = MASTER_ASSIGNMENTS_PATH) -> dict[str, dict]:
    """curated/master-assignments.json: {task: {master: {weight, min, max, extMin?, extMax?, extUnlock?}}}."""
    return json.loads(path.read_text(encoding="utf-8"))["tasks"] if path.exists() else {}


def apply_master_assignments(task: dict, snapshot: dict[str, dict]) -> None:
    """Replace the wiki-parsed assignment ranges with the game cache's (tables 113/114). The wiki
    infoboxes miss weights and some masters, and Mortimer's column mixes his quantity-modifier range
    into the extension slot. A task the snapshot lacks is assigned by no master (boss tasks)."""
    if not snapshot:
        return
    task["masters"] = {m: {"weight": e["weight"], "min": e["min"], "max": e["max"],
                           "extMin": e.get("extMin"), "extMax": e.get("extMax")}
                       for m, e in snapshot.get(task["task"], {}).items()}


# "{{SCP|Quest}} [[Priest in Peril]]" in an infobox renders as "Quest Priest in Peril": drop the icon's word.
_QUEST_ICON_WORD = re.compile(r"(?<=\bof )Quest (?=[A-Z])")


def tidy_other_requirement(text: str | None) -> str | None:
    return _QUEST_ICON_WORD.sub("", text) if text else text


def tidy_tasks(tasks: list[dict], rules: dict | None = None, assignments: dict[str, dict] | None = None) -> None:
    rules = load_gear_rules() if rules is None else rules
    assignments = load_master_assignments() if assignments is None else assignments
    for t in tasks:
        t.pop("styleNotes", None)  # labels of the gear tables the plugin no longer ships
        t["strategy"] = tidy_strategy(t.get("strategy") or [])
        tidy_locations(t)
        apply_gear_rules(t, rules)
        apply_master_assignments(t, assignments)
        if t.get("requirements"):
            t["requirements"]["other"] = tidy_other_requirement(t["requirements"].get("other"))


def tidy_only(args) -> int:
    """--tidy: re-apply tidy_tasks() to the existing out/tasks.json; reads nothing from the wiki."""
    path = args.out / "tasks.json"
    tasks = json.loads(path.read_text(encoding="utf-8"))
    rules = load_gear_rules()
    items_path = args.out / "items.json"
    if items_path.exists():
        missing = check_gear_rules(rules, (i["name"] for i in json.loads(items_path.read_text(encoding="utf-8"))))
        if missing:
            print(f"gear-rules.json names items that are not in items.json: {missing}", file=sys.stderr)
            return 1
    tidy_tasks(tasks, rules)
    write_json(path, tasks)
    print(f"tidied {len(tasks)} tasks")
    return 0


def buckets_only(args) -> int:
    """--buckets-only: apply the Bucket extras to out/tasks.json and write items.json."""
    path = args.out / "tasks.json"
    tasks = json.loads(path.read_text(encoding="utf-8"))
    client = BucketClient(args.cache, refresh=args.refresh, sleep=args.sleep)
    report: dict = {}
    items, drops = apply_bucket_extras(tasks, client, report)
    if client.failed:
        print(f"Bucket queries failed, nothing written: {client.failed}", file=sys.stderr)
        return 1
    tidy_tasks(tasks)
    write_json(path, tasks)
    write_json_rows(args.out / "items.json", items)
    write_json_rows(args.out / "drops.json", drops)
    print(json.dumps({k: v for k, v in report["bucketExtras"].items() if k != "monsterPagesWithoutDrops"}, indent=2))
    print(f"items.json: {len(items)} items; drops.json: {len(drops)} monster pages; bucket queries this run: {client.fetches}; "
          f"monster pages without drops: {len(report['bucketExtras']['monsterPagesWithoutDrops'])}")
    return 0


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")


def write_json_rows(path: Path, rows: list) -> None:
    """A JSON array with one compact record per line: small, and still diffs per record."""
    lines = [json.dumps(r, sort_keys=True, ensure_ascii=False, separators=(",", ":")) for r in rows]
    path.write_text("[\n" + ",\n".join(lines) + "\n]\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--out", type=Path, default=here / "out")
    ap.add_argument("--cache", type=Path, default=here / "cache")
    ap.add_argument("--curated", type=Path, default=here / "curated" / "verified")
    ap.add_argument("--tasks", type=Path, default=here / "task-enum.txt")
    ap.add_argument("--refresh", action="store_true", help="ignore the cache and re-fetch every page")
    ap.add_argument("--only", help="comma-separated display names (or substrings) to (re)build; "
                                  "other tasks are loaded from the existing out/tasks.json")
    ap.add_argument("--limit", type=int, default=0, help="stop after N tasks (testing)")
    ap.add_argument("--sleep", type=float, default=FETCH_SLEEP)
    ap.add_argument("--no-bucket", action="store_true",
                    help="do not use the Bucket API (pages only; for comparing outputs)")
    ap.add_argument("--self-test", action="store_true", help="run the parse_access() unit tests and exit")
    ap.add_argument("--tidy", action="store_true",
                    help="only re-apply the offline tidy-up (notes, strategy, gear rules) to the existing out/tasks.json")
    ap.add_argument("--buckets-only", action="store_true",
                    help="only (re)apply the Bucket extras (combat stats, drops, Combat Achievements, "
                         "items.json) to the existing out/tasks.json; reads no pages")
    args = ap.parse_args(argv)
    if args.self_test:
        return self_test()
    if args.buckets_only:
        return buckets_only(args)
    if args.tidy:
        return tidy_only(args)

    cache = WikiCache(args.cache, refresh=args.refresh, sleep=args.sleep)
    global BUCKET
    bucket_client = None if args.no_bucket else BucketClient(args.cache, refresh=args.refresh, sleep=args.sleep)
    BUCKET = BucketData(bucket_client)
    rows = read_task_list(args.tasks)
    if args.limit:
        rows = rows[:args.limit]

    existing: dict[str, dict] = {}
    only: list[str] | None = None
    if args.only:
        only = [s.strip().lower() for s in args.only.split(",") if s.strip()]
        prev = args.out / "tasks.json"
        if prev.exists():
            for t in json.loads(prev.read_text(encoding="utf-8")):
                existing[t["task"].lower()] = t

    curated, curated_problems = load_curated(args.curated)
    report: dict = {
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "taskCount": len(rows),
        "fetches": 0,
        "failedPages": {},
        "tasksWithoutTaskPage": [],
        "tasksWithoutMonsterPage": [],
        "tasksWithoutEquipment": [],
        "gearSources": {},
        "tasksWithoutLocations": [],
        "tasksWithoutMasters": [],
        "unmatchedCuratedLocations": [],
        "ambiguousCuratedLocations": [],
        "curatedProblems": curated_problems,
        "curatedTasksUnused": [],
        "counts": {},
    }

    tasks: list[dict] = []
    used_curated: set[str] = set()
    for row in rows:
        key = row.display.lower()
        if only is not None and not any(o == key or o in key for o in only) and key in existing:
            t = existing[key]
        else:
            t = build_task(cache, row, report)
            apply_curated(t, curated.get(key), report)
        if key in curated:
            used_curated.add(key)
        if not t["masters"]:
            report["tasksWithoutMasters"].append(row.display)
        tasks.append(t)

    report["curatedTasksUnused"] = sorted(k for k in curated if k not in used_curated)

    # post-processing from the "Slayer training" page (cached like any other page)
    apply_training_summary(tasks, cache, report)
    style_sources = derive_recommended_styles(tasks, cache)
    apply_access(tasks, report)
    finalise_variant_gear(tasks)
    report["failedPages"] = dict(cache.failed)
    report["fetches"] = cache.fetches
    report["bucketQueries"] = bucket_client.fetches if bucket_client else 0
    report["bucketFailed"] = dict(bucket_client.failed) if bucket_client else {}
    report["bucketCategoryCandidates"] = bucket_category_candidates(tasks)
    report["counts"] = {
        "tasks": len(tasks),
        "withTaskPage": sum(1 for t in tasks if t["wikiTaskPage"]),
        "withSlayerInfobox": sum(1 for t in tasks if t["wikiTaskPageKind"] == "slayer-task"),
        "withGearTables": sum(1 for t in tasks if t.get("gearTables")),
        "withAnyEquipment": sum(1 for t in tasks if t.get("gearTables") or t.get("exampleSetups")),
        "withLocations": sum(1 for t in tasks if t["locations"]),
        "withMasters": sum(1 for t in tasks if t["masters"]),
        "withStrategy": sum(1 for t in tasks if t["strategy"]),
        "withUnlocks": sum(1 for t in tasks if t["unlocks"]),
        "withXp": sum(1 for t in tasks if t["xpPerKill"] is not None),
        "curatedTasks": len(used_curated),
        "withTrainingSummary": sum(1 for t in tasks if t.get("trainingSummary")),
        "withRecommendedStyle": sum(1 for t in tasks if t["recommendedStyle"]),
        "recommendedStyleBySource": style_sources,
        "accessGroups": report["accessCounts"]["groups"],
        "accessGroupsCheckable": report["accessCounts"]["checkable"],
        "accessGroupsManual": report["accessCounts"]["manual"],
        "accessUnparsed": len(report["accessUnparsed"]),
    }

    extras = apply_bucket_extras(tasks, bucket_client, report) if bucket_client else None
    apply_gear_pages(tasks)
    tidy_tasks(tasks)
    args.out.mkdir(parents=True, exist_ok=True)
    write_json(args.out / "tasks.json", tasks)
    if extras is not None:
        write_json_rows(args.out / "items.json", extras[0])
        write_json_rows(args.out / "drops.json", extras[1])
    write_json(args.out / "locations.json", build_locations_index(tasks))
    write_json(args.out / "report.json", report)

    print(json.dumps(report["counts"], indent=2))
    print(f"fetches this run: {cache.fetches}; failed pages: {len(cache.failed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
