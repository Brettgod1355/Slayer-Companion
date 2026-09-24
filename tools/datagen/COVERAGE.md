# Slayer Companion datagen – coverage report (strategy-page + coordinates pass, 2026-09-24)

Updated after the strategy-page / coordinates pass (see the section of that name below); the
multi/cannon, master-range and access sections are unchanged from the consolidation pass of
2026-09-23 except for the record totals. Originally generated from `out/tasks.json` / `out/report.json` after running
`python3 generate.py --out out` with the curated overrides in `curated/verified/*.json`
(cache warm; 1 wiki page fetched by the generator: `Shellbane gryphon`; 2 pages fetched into
`scratchpad/wiki/` for the cross-check: `Entrana`, `Falador`). Counts were recomputed from
`tasks.json` by `out/coverage_stats.json`; the generator's own `report.json → counts` block
counts a task as "withLocations" even when its only locations are coordinate-less curated records,
so the split below is stricter.

## Counts

| Measure | Count |
| --- | --- |
| Tasks total | 148 |
| With a wiki task page | 148 (76 `Slayer task/…` pages with `{{Infobox Slayer}}`, 72 monster pages) |
| With `{{Recommended equipment}}` gear tables | 60 (was 13; 188 tables; +4 with example setups only = 64 with any equipment) |
| With locations (any record, incl. coordinate-less curated) | 148 |
| With generated locations that have coordinates | 148 (was 144) |
| With curated locations | 148 |
| With master assignment ranges (`{{Infobox Slayer}}`) | 76 |
| With Slayer XP per kill | 147 |
| Location records total (all tasks) | 1025 (was 913) |
| Location records with coordinates | 1019 (was 827) |
| Curated location records | 502 (497 with coordinates, 5 without; was 417 / 85) |
| Curated records with multi known (`true`/`false`/`partial`) | 416 of 502 ({'False': 250, 'True': 153, 'unknown': 86, 'partial': 13}) |
| Curated records with cannon known | 435 of 502 ({'True': 285, 'False': 149, 'unknown': 67, 'partial': 1}) |
| Tasks whose curated locations all have multi known | 95 |
| Tasks whose curated locations all have cannon known | 110 |

Generated (non-curated) location records carry `multi`/`cannon` = `"unknown"` by design; the
plugin should only trust the curated values.

Curated location matching after this pass: `unmatchedCuratedLocations` 5 (was 85; 100 before the
consolidation pass), `ambiguousCuratedLocations` 0, `curatedProblems` 0.

## Strategy-page + coordinates pass (2026-09-24)

`generate.py` gained `resolve_strategy_pages()` / `collect_gear()` (see README → page mapping)
with two explicit tables, `STRATEGY_PAGE_OVERRIDES` and `STRATEGY_VARIANT_PAGES`, an
`{{Infobox Location}}` / `{{Infobox Activity}}` map fallback for entrance pages, 64 new
pages in `EXTRA_MONSTER_PAGES`, 12 new `CURATED_LOCATION_ALIASES` and 2 changed ones (Trolls). `GearTable` and
`ExampleSetup` (Java) gained a `@Nullable String source` (wiki page of the table). 444 wiki pages
were fetched into the cache for this pass (137 existing pages, 307 "missing" answers, mostly
`<page>/Strategies` probes); a rebuild from the cache fetches nothing.

Gear: 13 → 60 tasks with tables. Strategy pages used (54): Abyssal Sire, Adamant dragon,
Alchemical Hydra, Aquanite, Araxxor, Artio, Aviansie, Barrows, Basilisk Knight, Brutal black
dragon, Callisto, Calvar'ion, Cerberus, Chaos Elemental, Chaos Fanatic, Commander Zilyana, Crazy
archaeologist, Dagannoth Kings, Deranged archaeologist, Duke Sucellus, Frost dragon, General
Graardor, Giant Mole, Green dragon, Inferno, K'ril Tsutsaroth, Kalphite Queen, King Black Dragon,
Kraken, Kree'arra, Lava dragon, Lizardman shaman, Maggot King, Metal dragons, Mithril dragon,
Phantom Muspah, Revenants, Rune dragon, Sarachnis, Scorpia, Shellbane gryphon, Skeletal Wyvern,
Smoke devil, Spindel, The Leviathan, The Whisperer, Thermonuclear smoke devil, TzHaar Fight Cave,
Vardorvis, Venator, Venenatis, Vet'ion, Vorkath, Zulrah (each `…/Strategies`). Every table on
them parsed with the existing `parse_gear_sections()`; none had to be skipped. Parser notes:
tiers 6-7 (`neck6`, `body6`, `head7`, … on about a dozen tables) are dropped as before; one stray `weapon =`
row without a tier number (Thermonuclear smoke devil, not rendered by the wiki either) is ignored;
free-text cells ("None if using a two-handed weapon", "See magic section") become `slotNotes`.

Deliberately **not** pulled in: strategy pages of alternatives that are separate encounters
(Abyssal Sire for Abyssal demons, Cerberus for Hellhounds, Vorkath for Blue dragons / Zombies,
Kree'arra for Aviansies, Demonic gorilla / Skotizo for Black demons, Araxxor / Sarachnis /
Venenatis for Spiders, K'ril Tsutsaroth / Tormented Demon for Greater demons, Revenants for
Ghosts, Scorpia for Scorpions, Brutus for Cows, Amoxliatl for Lesser Nagua, KBD for Black
dragons, Shellbane gryphon for Gryphons); those tasks keep the plugin's general-gear fallback.
Add a page to `STRATEGY_VARIANT_PAGES` to opt in.

Coordinates: 85 → 5 curated records without coordinates. Pages added to `EXTRA_MONSTER_PAGES`
(each has an `{{Infobox Monster}}` whose Slayer `cat` names the task): Deviant spectre, Twisted
Banshee, Giant bat, Grizzly bear, Black bear, Bear cub, Brutal black/blue/red dragon, Elder Chaos
druid, Moonlight Cockatrice, Dagannoth (Waterbirth Island), Wild dog, Guard dog, Chaos dwarf, Elf
Warrior, Elf Archer, Guard (Prifddinas), Cave goblin miner, Cave goblin guard, Cave goblin
(monster), Goblin (Goblin Village), Cyclops (God Wars Dungeon), Warped Jelly, Chilled jelly,
Earthen Nagua, Lizardman shaman, Sulphur lizard, Grimy lizard, Desert Lizard, Mogre (sea), Monkey
(monster), Monkey Zombie, Monkey Guard, Moss Giant (Iorwerth Dungeon), Greater Nechryael, Ogress
Warrior, Ogress Shaman, Zombie pirate (Pirates, Zombies), Crypt rat, Giant rat, Small scarab, King
Scorpion, Skeleton (Ape Atoll / Catacombs of Kourend / Stronghold of Security), Giant spider,
Mountain troll, Ice troll, Ice troll runt/male/female/grunt, Feral Vampyre, Venator, Undead Druid,
Zombie (Wilderness), Zombie (Stronghold of Security), Undead cow, Undead chicken; plus the
entrance pages TzHaar Fight Cave, Inferno and Zul-Andra (infobox `{{Map}}`; Barrows was already
listed). Mourner (Elves) and Undead one (Zombies) were checked and left out: their `cat` is not
the task's.

New and changed aliases (`CURATED_LOCATION_ALIASES`):

| Task | Curated name | Generated id | Why |
| --- | --- | --- | --- |
| Barrows Brothers | East of Mort'ton | `barrows-p0` | Barrows infobox `{{Map}}` (3567,3291) |
| TzTok-Jad | Mor Ul Rek - Fight Caves | `tzhaar-fight-cave-p0` | TzHaar Fight Cave infobox `{{Map}}`, the cave entrance (2439,5172) |
| Zulrah | Zulrah's Shrine (east of Zul-Andra) | `zul-andra-p0` | Zul-Andra infobox `{{Map}}`; the shrine is reached by boat from there |
| Bears | Fremennik Province - woods outside of Rellekka | `woods-outside-of-rellekka-p0` | Bear Cub LocLine 'Woods outside of Rellekka' |
| Bears | West of the Graveyard of Shadows | `north-west-of-the-ferox-enclave-p0` | the task page's map pins for this row are exactly the 10 Grizzly bear spawns of that LocLine |
| Dagannoth | Waterbirth Island Dungeon | `waterbirth-island-dungeon-entrance-hall-p0` | four LocLines match; entrance hall (54 spawns) chosen – approximation |
| Elves | Lletya | `lletya-p0` | ground floor (was ambiguous with the 1st-floor archer) |
| Monkeys | Temple of Marimbo | `temple-of-marimbo-p0` | ground floor, 9 of 12 Monkey Guards (was ambiguous) |
| Mogres | Ardent Ocean | `mudskipper-sound-south-of-musa-point-p0` | curated record covers three straits; the first named – approximation |
| Rats | Barrows crypt | `barrows-tunnels-p0` | Crypt rat LocLine 'Barrows (tunnels)' |
| Skeletons | Stronghold of Security | `stronghold-of-security-sepulchre-of-death-level-4-p0` | curated displayName says Sepulchre of Death |
| Trolls | Northern Jatizso and Neitiznot | `fremennik-isles-p0` | ice troll runt/male/female/grunt LocLines |
| Trolls | Troll Stronghold (Surface) | `troll-stronghold-surface-p0` | Mountain troll LocLine (was forced to no match) |
| Trolls | Troll Stronghold (Underground) | `troll-stronghold-underground-p0` | Mountain troll LocLine (was a troll-boss spawn used as a proxy) |

TzKal-Zuk's "Inferno (Mor Ul Rek)" matches the new `inferno-p0` record by name.

Side effect: `xpPerKill` is the Slayer XP of the first monster page with one, and
`EXTRA_MONSTER_PAGES` come before the task-list alternatives, so it changed for tasks whose
primary guess is an overview page: Bears 1300 (Callisto) → 27 (Grizzly bear), Dogs 27 (Jackal) →
62 (Wild dog), Lesser Nagua 105 (Sulphur Nagua) → 165 (Earthen Nagua), Monkeys 210 (Tortured
gorilla) → 6 (Monkey), Scabarites 1 → 40 (Small scarab), Trolls 126 (Dad) → 90 (Mountain troll),
Vampyres 90 (Vyrewatch) → 40 (Feral Vampyre). The plugin's default variant (first placed
monster) moves the same way.

## Tasks lacking each

### No task page
none.

### No gear tables (88)
Aberrant spectres, Ankou, Araxytes, Bandits, Banshees, Bats, Bears, Birds, Black demons, Black Knights, Bloodveld, Blue dragons, Brine rats, Catablepon, Cave bugs, Cave crawlers, Cave horrors, Cave kraken, Cave slimes, Chaos druids, Cockatrice, Cows, Crabs, Crawling hands, Crocodiles, Custodian Stalkers, Dagannoth, Dark warriors, Dogs, Dwarves, Earth warriors, Elves, Ents, Fever spiders, Fire giants, Fleshcrawlers, Ghosts, Ghouls, Goblins, Greater demons, Gryphons, Harpie bug swarms, Hellhounds, Hill giants, Hobgoblins, Hydras, Icefiends, Ice giants, Ice warriors, Infernal mages, Jungle horrors, Kalphites, Killerwatts, Kurask, Lesser demons, Lesser Nagua, Lizards, Magic axes, Mammoths, Minotaurs, Mogres, Molanisks, Monkeys, Moss giants, Ogres, Otherworldly beings, Pirates, Pyrefiends, Rats, Red dragons, Rogues, Scabarites, Scorpions, Sea snakes, Shades, Shadow warriors, Sourhogs, Spiders, Spiritual creatures, Terror dogs, Turoth, Tzhaar, Vampyres, Wall beasts, Warped Creatures, Werewolves, Wolves, Zombies

Of these, 84 also have no example setup (the 4 with setups only: Araxytes, Cave horrors, Gryphons, Warped Creatures).
None of them has a `<task page>/Strategies` or `<primary monster>/Strategies` page on the wiki
(see `report.json → gearSources` for the titles tried). Checked by hand and also missing: Black
demon, Hellhound, Kurask, Bloodveld, Hydra, Ankou, Gargoyle, Cave horror, Deviant spectre, Wyrm,
Ice troll, Warped Creature, Custodian stalker, Araxyte, Revenant, Frost/Sulphur Nagua, Brutal
blue/red dragon, Spiritual creatures, God Wars Dungeon (all `…/Strategies`).
The 60 with tables: Abyssal demons, The Abyssal Sire, The Alchemical Hydra, Aquanites, Araxxor, Aviansies, Barrows Brothers, Basilisks, Black dragons, Callisto, Cerberus, The Chaos Elemental, The Chaos Fanatic, Crazy Archaeologists, Dagannoth Kings, Dark beasts, Deranged Archaeologist, Drakes, Duke Sucellus, Dust devils, Fossil island wyverns, Frost dragons, General Graardor, The Giant Mole, Green dragons, TzTok-Jad, Jellies, The Kalphite Queen, The King Black Dragon, The Cave Kraken Boss, Kree'arra, K'ril Tsutsaroth, Lava Dragons, Lizardmen, The Maggot King, Metal dragons, Nechryael, The Phantom Muspah, Revenants, Sarachnis, Scorpia, The Shellbane Gryphon, Skeletal wyverns, Skeletons, Smoke devils, Suqahs, The Leviathan, The Whisperer, The Thermonuclear Smoke Devil, Trolls, Vardorvis, Venators, Venenatis, Vet'ion, Vorkath, Waterfiends, Wyrms, Commander Zilyana, TzKal-Zuk, Zulrah.

### No generated locations at all
none (was 4: Barrows Brothers, TzTok-Jad, TzKal-Zuk and Zulrah now get their entrance from the
activity/location page's infobox `{{Map}}`).

### No master ranges (72)
Monster-page tasks without `{{Infobox Slayer}}` (the plugin reads live ranges from the game cache):
The Abyssal Sire, The Alchemical Hydra, Araxxor, Barrows Brothers, Black Knights, Brine rats, Callisto, Catablepon, Cave bugs, Cave crawlers, Cave slimes, Cerberus, Chaos druids, The Chaos Elemental, The Chaos Fanatic, Cockatrice, Cows, Crawling hands, Crazy Archaeologists, Crocodiles, Custodian Stalkers, Dagannoth Kings, Dark warriors, Deranged Archaeologist, Duke Sucellus, Ents, Fever spiders, Fleshcrawlers, Frost dragons, General Graardor, Ghouls, The Giant Mole, Harpie bug swarms, Ice warriors, Infernal mages, TzTok-Jad, Jungle horrors, The Kalphite Queen, Killerwatts, The King Black Dragon, The Cave Kraken Boss, Kree'arra, K'ril Tsutsaroth, Kurask, Lava Dragons, The Maggot King, Magic axes, Mammoths, Minotaurs, Mogres, Otherworldly beings, The Phantom Muspah, Revenants, Rogues, Sarachnis, Scorpia, Shadow warriors, The Shellbane Gryphon, Skeletal wyverns, Sourhogs, Terror dogs, The Leviathan, The Whisperer, The Thermonuclear Smoke Devil, Vardorvis, Venenatis, Vet'ion, Vorkath, Werewolves, Commander Zilyana, TzKal-Zuk, Zulrah

### No Slayer XP per kill
Ents (the `Ent` infobox has no `slayxp`).

## Alias fixes made (step 2)

`generate.py` gained `CURATED_LOCATION_ALIASES` (task → curated displayName-or-name → generated
location id, `None` = force no match) consulted as "tier 0" in `apply_curated()`, and four
zero-cost entries in `EXTRA_MONSTER_PAGES` (pages already in the cache except `Shellbane gryphon`).
The README documents both. A backup of the previous script is `generate.py.bak_consolidate`.

| Task | Curated name | Generated id | Why |
| --- | --- | --- | --- |
| Bats | Abandoned Mine | `abandoned-mine-level-1-p0` | curated displayName says 'Abandoned Mine level 1' |
| Birds | Farmer Fred's chicken pen | `lumbridge-west-farm-p0` | Chicken LocLine 'Lumbridge West Farm' (33 spawns at 3178,3294); 'North of Lumbridge West Farm' has 1 spawn |
| Birds | River Lum | `along-the-river-lum-south-of-varrock-p0` | curated displayName 'River Lum south of Varrock (ducks)' |
| Black dragons | Corsair Cove Dungeon | `corsair-cove-dungeon-myths-guild-basement-p0` | LocLine text 'Corsair Cove Dungeon/Myths' Guild (basement)' |
| Blue dragons | Corsair Cove Dungeon | `corsair-cove-dungeon-myths-guild-basement-p1` | same, plane 1 |
| Crabs | Ruins of Tapoyauik | `ruins-of-tapoyauik-middle-floor-p1` | curated covers middle+bottom floors; middle floor has 16 of the 23 Frost Crab spawns (approximation) |
| Custodian Stalkers | Stalker Den – south-western caves (multicombat, cannonable) [displayName] | `stalker-den-multicombat-p0` | two curated records share the name 'Stalker Den'; keyed by displayName |
| Custodian Stalkers | Stalker Den – single-way caves (south-east and north) [displayName] | `stalker-den-single-combat-p0` | as above |
| Dark warriors | Dark Warriors' Fortress | `dark-warriors-fortress-p0` | ground floor + courtyard (was ambiguous with p1) |
| Fire giants | Brimhaven Dungeon | `brimhaven-dungeon-p1` | 1st floor holds 16 of the 21 spawns (was ambiguous with p0) |
| Gryphons | Shellbane Gryphon Cave | `shellbane-gryphon-p0` | {{Map}} 'The entrance to the cave' on the Shellbane gryphon page, added via EXTRA_MONSTER_PAGES |
| Hobgoblins | Tree Gnome Village dungeon | `tree-gnome-village-dungeon-roaming-p0` | 9 roaming vs 7 imprisoned (was ambiguous) |
| The Maggot King | Vampyrium | `maggot-king-p0` | {{Map}} fallback location named after the Maggot King page |
| Moss giants | Tonali Cavern | `tonali-cavern-southern-chamber-p0` | curated note: 'southern chamber'; ground floor has 5 of 8 spawns |
| Rats | Stronghold of Security | `stronghold-of-security-vault-of-war-p0` | curated is about the first level (Vault of War, 56 rats); Catacomb of Famine is the second level |
| Rogues | Rogues' Castle | `rogues-castle-p0` | ground floor (was ambiguous across p0-p3) |
| Scorpions | Stonecutter Outpost temple | `south-west-of-stonecutter-outpost-p0` | curated evidence cites Scorpion LocLine 'South-west of Stonecutter Outpost' |
| The Shellbane Gryphon | Shellbane Gryphon Cave | `shellbane-gryphon-p0` | {{Map}} 'The entrance to the cave' on the boss page |
| Spiders | Forthos Dungeon | `forthos-dungeon-burial-tomb-p0` | only the Sarachnis tomb LocLine exists (Temple Spider page not fetched); approximation for the Spider's Den |
| Trolls | Troll Stronghold (Surface) | `(none, forced)` | the loose pass matched the troll-boss spawn 'Troll Stronghold (Bottom Level)'; the outside mountain trolls have no generated LocLine, so the record stays coordinate-less |
| Trolls | Troll Stronghold (Underground) | `troll-stronghold-bottom-level-p0` | Berry's spawn inside the stronghold (2832,10083) as a proxy for the inside; Mountain troll page not fetched |
| Vampyres | Meiyerditch | `meiyerditch-p0` | ground floor (was ambiguous across p0-p3) |
| Zombies | Varrock Sewers | `varrock-sewers-hallway-p0` | hallway (was ambiguous with 'room with Ladder') |

Extra monster pages:

| Task | Page added | Curated location now matched |
| --- | --- | --- |
| Callisto | Artio | Hunter's End (matched by name after the page was added) |
| Black dragons | King Black Dragon | King Black Dragon Lair (Wilderness) (exact displayName match) |
| Rats | Brine rat | Brine Rat Cavern (exact name match) |
| Gryphons | Shellbane gryphon | Shellbane Gryphon Cave via alias (1 new fetch, the only wiki fetch of the datagen run) |

Net effect: 15 previously unmatched curated locations now have coordinates and all 10 ambiguous
matches are pinned to one record (before, both `Stalker Den` curated records were written onto
the same generated record, the second overwriting the first).

## Multi / cannon cross-check against research/area-rules.json (step 3)

Method: `xcheck_area_rules.py` matched every curated location name/displayName against the area
names of `cannonProhibited`, `cannonAllowed` and `multicombat` (whole-word substring; caveated
"partial" areas skipped). 353 comparisons, 322 agreements. The 31 raw disagreements plus 52
"partial-area" notes were reviewed by hand; most are substring false positives (e.g. a display
name saying "south of Castle Wars", "and Scurrius", the Fossil Island "Wyvern Cave" rule hitting
the Asgarnian "wyvern cave", Nex's "Ancient Prison" hitting Neypotzli's Ancient Prison,
Stronghold of Security levels 2-4 vs the "first level only" rule, spots *outside* a listed multi
area such as "West of the Graveyard of Shadows", "West of the Dark Warriors' Fortress",
"West of the Lava Maze", "Goblin Village (north of Falador)").

Decision rule applied to the real conflicts: the area's own wiki page > the dedicated
`Multicombat area` / `Dwarf multicannon#Prohibited areas` lists > a cell in a `Slayer task/…`
Location Comparison table.

### Changes made to the verified batch files (4)

| File | Task / location | Field | Before → after | Basis (quoted in the record's `evidence`) |
| --- | --- | --- | --- | --- |
| batch10.json | Spiders / Ogre Enclave | multi | true → false | Ogre Enclave page: "A dwarf multicannon may be used here, but it is '''not''' a multi-combat area." (task-table cell said Yes; Ogre Enclave is not in the Multicombat area list) |
| batch5.json | Greater demons / Entrana Dungeon | cannon | true → false | `Dwarf multicannon#Prohibited areas` lists Entrana; Entrana page: "Some items without bonuses or which can't be equipped are still not allowed:" table includes `{{plink|Dwarf multicannon|pic=Cannon barrels}}` (task-table cell said Yes) |
| batch6.json | Ice giants / White Wolf Mountain On top | multi | false → true | Multicombat area list: "*[[White Wolf Mountain]]"; the White Wolf Mountain page is silent (task-table cell said No) |
| batch12.json | Zombies / Graveyard of Shadows (Wilderness) | multi | false → true | Multicombat area list, Wilderness: "**The [[Graveyard of Shadows]]"; the Graveyard of Shadows page is silent (task-table cell said No). Safer direction for a Wilderness spot |

### Conflicts reviewed and left as curated

- Zombies / Ruins (east) (Wilderness) multi=false: the Ruins (east) page says "They lie a [[single-way combat]] area in between level 26 to 29 Wilderness." (area page wins over the list's "Abandoned Farm").
- Black dragons, Greater demons / Lava Maze Dungeon multi=false: "The dungeon is entirely single-way combat" (Lava Maze Dungeon); the list's "Lava Maze" is the surface maze.
- Bats / Slayer Tower Entrance cannon=true: the Slayer Tower note ("Dwarf multicannons '''cannot''' be used here") is about the tower; the bats are outside and the task table says Yes.
- Skeletal wyverns / Asgarnian Ice Dungeon cannon=true: `Skeletal Wyvern/Strategies`: allowed "only in the lower part of the cave; the upper Slayer-exclusive part is a no cannon zone" – arguably `partial`; left `true` with that caveat in the displayName.
- Lesser Nagua / Neypotzli - Ancient Prison cannon=true: Neypotzli page "a dwarven cannon can be set up for Slayer tasks within the caverns"; the prohibited "Ancient Prison" is Nex's.
- Dwarves / White Wolf Mountain Tunnels, Ice giants + Ice warriors / Ice Queen's Lair multi=false: separate underground areas, not on the list; task tables say No.
- Dwarves / Surface of Mining Guild multi=false: the list says "Most sections of [[Falador]]" without naming the guild; task table says No.
- Waterfiends / Kraken Cove (Slayer Task Only) multi=false: the list names "Inner [[Kraken Cove]]"; the Kraken Cove page does not say where the waterfiends stand relative to the inner cove; task table says No.
- Smoke devils / Smoke Devil Dungeon cannon=true: matched "Castle Wars" only through the displayName; the dungeon page is not on the prohibited list.
- Rats / Varrock Sewers cannon=true: matched "Scurrius" only through the displayName ("The dwarf multicannon can be used here" – Varrock Sewers).
- All Catacombs of Kourend records: dragons (brutal dragon area) are `false`, everything else `true`, consistent with "Multicombat everywhere except the brutal dragon area".
- Basilisks / Jormungand's Prison `false`, Dagannoth / Jormungand's Prison `true`: consistent with the partial rule.

## Master range spot-check (step 4)

Five tasks drawn with `random.seed(20260923)` from the 76 with `{{Infobox Slayer}}`; parsed
`masters` compared by eye with the raw infobox lines of the cached task page:

| Task | Raw infobox lines | Parsed | OK |
| --- | --- | --- | --- |
| Lizardmen | chaeldar = 50-90; konar = 90-110; nieve = 90-120; duradel = 130-210 | same min/max, no ext | yes |
| Greater demons | krystilia = 100-150 (200-250); chaeldar = 70-130 (200-250); konar = 120-170 (200-250); nieve = 120-185 (200-250); duradel = 130-200 (200-250) | min/max + extMin/extMax 200/250 | yes |
| Hydras | konar = 125-190; mortimer = 150-200 (200-300) | 125/190; 150/200 ext 200/300 | yes |
| Molanisks | vannaka = 39-50 | 39/50 | yes |
| Pyrefiends | mazchna = 30-50; vannaka = 40-90; mortimer = 35-50 (20-80) | 30/50; 40/90; 35/50 ext 20/80 | yes |

No weighting values on these pages (`weight` is `null` for all five).

## Remaining unknowns

### Curated locations without coordinates (5)
- Elves: Mourner Headquarters (the Mourner page's Slayer `cat` is None and its LocLines are Arandar / Mourner Tunnels; not added)
- Goblins: Stronghold of Security (Goblin (Vault of War) LocLines are bare room names such as "Western room"; not aliased because the plugin would show that name)
- Skeletons: Skeletal Tomb (Calvar'ion page has neither LocLine nor `{{Map}}`)
- Spiders: Web Chasm (Spindel page has neither LocLine nor `{{Map}}`)
- Werewolves: Canifis (human-form werewolves are separate NPC pages; the Werewolf page lists Canifis only in prose)

### Curated locations with multi = "unknown" (86)
- Barrows Brothers: East of Mort'ton
- Birds: Lumbridge East Farm
- Black Knights: North-east of Bone Yard (Wilderness)
- Cave bugs: Lumbridge Swamp Caves
- Cave crawlers: Fremennik Slayer Dungeon; Lumbridge Swamp Caves; Ruins of Tapoyauik - top level
- Cave slimes: Lumbridge Swamp Caves
- Cerberus: Cerberus' Lair
- Chaos druids: Chaos Temple (Wilderness); Edgeville Dungeon (Wilderness)
- Cockatrice: Fremennik Slayer Cave
- Cows: Lumbridge East farm; South Falador Farm
- Crabs: Avium Savannah - southern coast; Crabclaw Caves; Crabclaw Isle; Hosidius - southern coast; Isle of Souls; Mushroom Forest; Ruins of Tapoyauik middle floor; Waterbirth Island; West of Mount Quidamortem
- Crawling hands: Slayer Tower
- Crocodiles: Along the River Elid; Near the Ruins of Ullek; Near the Ruins of Unkah
- Deranged Archaeologist: Fossil Island – South end of the Tar Swamp
- Duke Sucellus: Ghorrock Prison - Asylum
- Ents: East of Chaos Temple (Wilderness); North of Chaos Temple (Wilderness)
- Fever spiders: Braindeath Island
- Frost dragons: Grimstone Dungeon
- Ghouls: North of Mort Myre Swamp; South of the Slayer Tower
- Greater demons: Sisterhood Sanctuary
- Green dragons: North of the Graveyard of Shadows; South of the Lava Maze
- Harpie bug swarms: East of the Colossal Wyrm Remains; North-east of Tai Bwo Wannai
- Hill giants: Lava Maze
- Hobgoblins: Bandit Camp mine (Wilderness); Clock Tower Dungeon; Crandor; North of Rellekka; South of Tai Bwo Wannai; The Hollows; Tree Gnome Village dungeon (roaming); Waterbirth Island; Witchaven Dungeon
- Ice warriors: White Wolf Mountain
- Jungle horrors: Mos Le'Harmless
- Killerwatts: Killerwatt plane
- Lesser demons: Wilderness - by the muddy chest in the Lava Maze
- Lizards: West of Ruins of Ullek
- Mammoths: South-east of Ferox Enclave
- Mogres: Ardent Ocean
- Moss giants: Bryophyta's Lair (Varrock Sewers); Tonali Cavern - Southern Chamber; West of Ralos' Rise
- Otherworldly beings: Zanaris
- Rats: Barrows crypt; Lumbridge Swamp
- Red dragons: Dragon Nest
- Rogues: South-west of Mage Arena
- Scorpions: Wilderness (West of the Air Obelisk)
- Shadow warriors: Legends' Guild Dungeon
- Skeletal wyverns: Asgarnian Ice Dungeon
- Sourhogs: Sourhog Cave
- Spiders: East side of Lumbridge; Wilderness spider nest holding a Sapphire spawn
- Suqahs: Lady Zay - Cage on the bottom floor
- The Leviathan: The Scar
- The Maggot King: Maggot King
- The Phantom Muspah: Ghorrock Dungeon
- The Whisperer: Lassar Undercity - Sunken Cathedral
- Trolls: Ice Path; North of the Troll arena; Troll Stronghold (Bottom Level); Wyrmscraig Cavern
- TzKal-Zuk: Inferno (Mor Ul Rek)
- TzTok-Jad: Mor Ul Rek - Fight Caves
- Vardorvis: The Stranglewood - Ritual Site
- Vorkath: Ungael
- Wall beasts: Lumbridge Swamp Caves
- Zulrah: Zulrah's Shrine (east of Zul-Andra)

### Curated locations with cannon = "unknown" (67)
- Barrows Brothers: East of Mort'ton
- Birds: Lumbridge East Farm
- Black Knights: Lava Maze (Wilderness); North-east of Bone Yard (Wilderness)
- Callisto: Hunter's End
- Chaos druids: Chaos Temple (Wilderness)
- Cows: Lumbridge East farm; North of Hosidius Town Square; South Falador Farm
- Crabs: Haunted Woods; Waterbirth Island; West of Mount Quidamortem
- Crazy Archaeologists: Ruins (west)
- Crocodiles: Along the River Elid; Near the Ruins of Ullek; Near the Ruins of Unkah
- Custodian Stalkers: Stalker Den (single-combat)
- Dark warriors: Dark Warriors' Fortress
- Deranged Archaeologist: Fossil Island – South end of the Tar Swamp
- Ents: East of Chaos Temple (Wilderness); North of Chaos Temple (Wilderness)
- Fever spiders: Braindeath Island
- General Graardor: God Wars Dungeon (Bandos boss room)
- Ghouls: North of Mort Myre Swamp; South of the Slayer Tower
- Goblins: Clock Tower Dungeon; God Wars Dungeon (Bandos boss room); Outside Port Sarim; Tree Gnome Village maze
- Greater demons: Sisterhood Sanctuary
- Green dragons: North of the Graveyard of Shadows
- Harpie bug swarms: East of the Colossal Wyrm Remains
- Hill giants: Bone Yard Hunter area; Gnome Maze; North of the Observatory
- Hobgoblins: Clock Tower Dungeon; Crandor; North of Rellekka; South of Tai Bwo Wannai; The Hollows; Tree Gnome Village dungeon (roaming); Waterbirth Island; Witchaven Dungeon
- Ice giants: Settlement Ruins
- Jungle horrors: Mos Le'Harmless
- Lava Dragons: Lava Dragon Isle
- Lizards: West of Ruins of Ullek
- Moss giants: West of Ralos' Rise
- Otherworldly beings: Zanaris
- Rats: Barrows crypt; Lumbridge Swamp
- Rogues: Rogues' Castle; South-west of Mage Arena
- Scorpions: Lava Maze; Wilderness (West of the Air Obelisk)
- Shades: Mort'ton
- Skeletons: Skeletal Tomb
- Spiders: East side of Lumbridge; Web Chasm
- Suqahs: Lady Zay - Cage on the bottom floor
- The Giant Mole: Mole Hole (Under Falador Park)
- The Maggot King: Maggot King
- Trolls: Ice Path; North of the Troll arena; Troll Stronghold (Bottom Level); Trollweiss Dungeon; Wyrmscraig Cavern

### Other
- 72 tasks have no master ranges (no `{{Infobox Slayer}}`); the plugin is expected to read live ranges from the game cache.
- 88 tasks have no wiki gear tables; gear for them exists only as curated `requiredItems`/`usefulItems` (the plugin shows its general Slayer gear instead).
- Ents: no Slayer XP per kill on the wiki infobox.
- Aliases that are approximations rather than exact spots: Crabs/Ruins of Tapoyauik (middle floor only), Spiders/Forthos Dungeon (Sarachnis tomb, not the Spider's Den), Trolls/Troll Stronghold (Underground) (a troll-boss spawn inside the stronghold), Fire giants/Brimhaven Dungeon (1st floor only), Rats/Stronghold of Security (Vault of War only).
- `xcheck_area_rules.py` is a name-substring heuristic; areas whose curated name does not contain the wiki area title were not cross-checked.

## Access rules (`access` on every location record)

`parse_access()` output for the current build (`report.json → accessCounts`), regenerated with
`python3 generate.py --cache <cache> --out out2` (0 fetches); `python3 generate.py --self-test`
passes 90 checks.

| Measure | Count |
| --- | --- |
| Location records (all tasks) | 1025 (913 before the coordinates pass; the new records carry no requirements) |
| Requirement strings on them | 537 (377 distinct) |
| Access groups total | 551 |
| Groups with at least one checkable rule (`any` non-empty) | 264 |
| … of which fully checkable (`manual: false`) | 252 |
| Groups flagged `manual` (no rules, or rules plus an unchecked detail) | 298 |
| Distinct strings that yield at least one rule | 156 |
| Distinct strings that are manual only | 221 |

Rule types emitted: quest 139, skill 113, unlock 14, members 13, diary 1. Most diary strings
are "diary OR boss task" alternatives and are therefore manual (the boss-task half cannot be
checked, and a group with rules is a hard lock in the plugin). Assignment requirements
(`… to be assigned`, `Death Plateau (for God Wars Dungeon slayer tasks)`, …) are manual groups
with `note: "assignment requirement"` – they gate the master, not the location.

### `accessUnparsed` – quest-looking strings that did not become a rule (19)

Count = number of location records carrying the string. None of these produce a rule; add an
entry to `QUEST_ALIASES` or reword the curated text to make them checkable.

- `A Taste of Hope started for a weapon that can damage vyrewatch (38 Slayer to begin)` (1)
- `A leaf-bladed weapon, broad ammunition or Magic Dart` (1)
- `Access to Fossil Island` (1)
- `Access to Great Kourend (Boss page: 'Accessed Great Kourend at least once by boat' for the Sarachnis boss task)` (1)
- `Barbarian Training progressed to the pyre ships section (Barbarian Firemaking, 35 Firemaking)` (1)
- `Boat with Jarvald at Rellekka: 1,000 coins per trip unless The Fremennik Trials is complete` (1)
- `Darkness of Hallowvale (the laboratories are entered during the quest); Sins of the Father for full access` (1)
- `Dragon Slayer I progressed to Crandor for the Crandor side (the Karamja side needs nothing)` (1)
- `Fallen From Grace: the task page's location table says the wyrmling nest 'Requires partial completion of Fallen From Grace to access', while its prose says surface wyrmlings are accessible before partial completion; full completion unlocks the buildable bank and slayer ring/necklace of passage teleports` (1)
- `Full mourner gear, or Mourning's End Part II for the Slayer ring dark beast teleport` (1)
- `Lair of Tarn Razorlor miniquest completed for more dogs to spawn` (1)
- `Light source or Fire of Eternal Light` (1)
- `Partial Troll Stronghold (or Easy Combat Achievements for Ghommal's hilt teleport) and 60 Strength or 60 Agility to enter the dungeon` (1)
- `Partial Troll Stronghold quest (or Trollheim Teleport with 61 Magic and Eadgar's Ruse)` (1)
- `Partial Troll Stronghold quest to reach Trollheim by foot (or Trollheim Teleport with 61 Magic and Eadgar's Ruse)` (1)
- `Sins of the Father started (city entry); completed to kill vyrewatch sentinels` (1)
- `Song of the Elves to use the Gwenith rowboat` (1)
- `Started Zogre Flesh Eaters (zogres) / partial completion (skogres)` (1)
- `Witchwood icon or Protect from Melee` (1)

Notes: `Access to Fossil Island` could be mapped to Bone Voyage and `Boat with Jarvald …`
to The Fremennik Trials, but such mappings are game knowledge rather than text and were left
out on purpose. `A leaf-bladed weapon, broad ammunition or Magic Dart`, `Light source or Fire
of Eternal Light` and `Witchwood icon or Protect from Melee` are item strings caught by the
Title-Case heuristic; they are correctly manual.
