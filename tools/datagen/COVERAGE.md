# Slayer Companion datagen – coverage report (consolidation pass, 2026-09-23)

Generated from `out/tasks.json` / `out/report.json` after running
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
| With `{{Recommended equipment}}` gear tables | 13 (+4 with example setups only = 17 with any equipment) |
| With locations (any record, incl. coordinate-less curated) | 148 |
| With generated locations that have coordinates | 144 |
| With curated locations | 148 |
| With master assignment ranges (`{{Infobox Slayer}}`) | 76 |
| With Slayer XP per kill | 147 |
| Location records total (all tasks) | 913 |
| Location records with coordinates | 827 |
| Curated location records | 502 (417 with coordinates, 85 without) |
| Curated records with multi known (`true`/`false`/`partial`) | 416 of 502 ({'False': 250, 'True': 153, 'unknown': 86, 'partial': 13}) |
| Curated records with cannon known | 435 of 502 ({'True': 285, 'False': 149, 'unknown': 67, 'partial': 1}) |
| Tasks whose curated locations all have multi known | 95 |
| Tasks whose curated locations all have cannon known | 110 |

Generated (non-curated) location records carry `multi`/`cannon` = `"unknown"` by design; the
plugin should only trust the curated values.

Curated location matching after this pass: `unmatchedCuratedLocations` 85 (was 100),
`ambiguousCuratedLocations` 0 (was 10), `curatedProblems` 0.

## Tasks lacking each

### No task page
none.

### No gear tables (135)
Aberrant spectres, The Abyssal Sire, The Alchemical Hydra, Ankou, Araxxor, Araxytes, Aviansies, Bandits, Banshees, Barrows Brothers, Basilisks, Bats, Bears, Birds, Black demons, Black dragons, Black Knights, Bloodveld, Blue dragons, Brine rats, Callisto, Catablepon, Cave bugs, Cave crawlers, Cave horrors, Cave kraken, Cave slimes, Cerberus, Chaos druids, The Chaos Elemental, The Chaos Fanatic, Cockatrice, Cows, Crabs, Crawling hands, Crazy Archaeologists, Crocodiles, Custodian Stalkers, Dagannoth, Dagannoth Kings, Dark warriors, Deranged Archaeologist, Dogs, Duke Sucellus, Dwarves, Earth warriors, Elves, Ents, Fever spiders, Fire giants, Fleshcrawlers, Frost dragons, General Graardor, Ghosts, Ghouls, The Giant Mole, Goblins, Greater demons, Green dragons, Gryphons, Harpie bug swarms, Hellhounds, Hill giants, Hobgoblins, Hydras, Icefiends, Ice giants, Ice warriors, Infernal mages, TzTok-Jad, Jungle horrors, Kalphites, The Kalphite Queen, Killerwatts, The King Black Dragon, The Cave Kraken Boss, Kree'arra, K'ril Tsutsaroth, Kurask, Lava Dragons, Lesser demons, Lesser Nagua, Lizardmen, Lizards, The Maggot King, Magic axes, Mammoths, Metal dragons, Minotaurs, Mogres, Molanisks, Monkeys, Moss giants, Ogres, Otherworldly beings, The Phantom Muspah, Pirates, Pyrefiends, Rats, Red dragons, Revenants, Rogues, Sarachnis, Scabarites, Scorpia, Scorpions, Sea snakes, Shades, Shadow warriors, The Shellbane Gryphon, Skeletal wyverns, Smoke devils, Sourhogs, Spiders, Spiritual creatures, Terror dogs, The Leviathan, The Whisperer, The Thermonuclear Smoke Devil, Turoth, Tzhaar, Vampyres, Vardorvis, Venators, Venenatis, Vet'ion, Vorkath, Wall beasts, Warped Creatures, Werewolves, Wolves, Commander Zilyana, Zombies, TzKal-Zuk, Zulrah

Of these, 131 also have no example setup (the 4 with setups only: Araxytes, Cave horrors, Gryphons, Warped Creatures).
The 13 with tables: Abyssal demons, Aquanites, Dark beasts, Drakes, Dust devils, Fossil island wyverns, Jellies, Nechryael, Skeletons, Suqahs, Trolls, Waterfiends, Wyrms.

### No generated locations at all (4) – curated-only, no coordinates
Barrows Brothers, TzTok-Jad, TzKal-Zuk, Zulrah (instanced encounters; the wiki has no `{{LocLine}}` for them).

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

### Curated locations without coordinates (85)
No generated `{{LocLine}}` exists for them because the variant monster's page is not among the
task's monster pages (or the encounter is instanced). Adding the named page to
`EXTRA_MONSTER_PAGES` and re-running with `--only` would supply most of them (about 45 new
fetches; not done in this pass).
- Aberrant spectres: Catacombs of Kourend (would come from: Deviant spectre)
- Banshees: Catacombs of Kourend (would come from: Twisted Banshee)
- Barrows Brothers: East of Mort'ton (would come from: instanced / no LocLine on the wiki)
- Bats: Coal Trucks (would come from: Giant bat)
- Bats: Taverley Dungeon (would come from: Giant bat)
- Bears: Fremennik Province - woods outside of Rellekka (would come from: Grizzly bear)
- Bears: Kebos Lowlands - south of Mount Quidamortem (would come from: Grizzly bear)
- Bears: North-east of Ardougne (would come from: Grizzly bear / Black bear)
- Bears: Outside of the Mind Altar (would come from: Grizzly bear)
- Bears: Varrock south-east mine (would come from: Black bear)
- Bears: West of the Graveyard of Shadows (would come from: Grizzly bear)
- Black dragons: Catacombs of Kourend (would come from: Brutal black dragon)
- Blue dragons: Catacombs of Kourend (north-west) (would come from: Brutal blue dragon)
- Chaos druids: Chaos Temple (Wilderness) (would come from: Elder Chaos druid)
- Cockatrice: Neypotzli - Earthbound Cavern (would come from: unknown variant page)
- Dagannoth: Waterbirth Island Dungeon (would come from: Dagannoth (Waterbirth Island))
- Dogs: Brimhaven Dungeon (would come from: Wild dog)
- Dogs: Handelmort Mansion (would come from: Guard dog)
- Dogs: Hosidius (would come from: Wild dog / Guard dog)
- Dwarves: Deep Wilderness Dungeon (would come from: Chaos dwarf)
- Dwarves: Taverley Dungeon (would come from: Chaos dwarf)
- Elves: Lletya (would come from: Elf Warrior / Elf Archer)
- Elves: Mourner Headquarters (would come from: Mourner)
- Elves: Prifddinas (would come from: Elf Warrior / Elf Archer / Guard (Prifddinas))
- Goblins: Dorgeshuun Mines (would come from: Cave goblin miner / guard)
- Goblins: Goblin Village (would come from: not on the Goblin page LocLines (separate NPC page))
- Goblins: Lumbridge Swamp Caves (would come from: Cave goblin (monster))
- Goblins: Stronghold of Security (would come from: separate NPC page)
- Hill giants: God Wars Dungeon (would come from: Cyclops page only lists Warriors' Guild)
- Jellies: Catacombs of Kourend (would come from: Warped Jelly)
- Jellies: Grimstone Dungeon (would come from: Chilled jelly)
- Jellies: Ruins of Tapoyauik (would come from: Chilled jelly)
- Lesser Nagua: Tonali Cavern - Sun Chamber (would come from: Earthen Nagua)
- Lizardmen: Lizardman Caves, underneath Lizardman Settlement (Slayer task only) (would come from: Lizardman shaman)
- Lizardmen: Lizardman Temple, beneath Molch (would come from: Lizardman shaman)
- Lizards: Karuulm Slayer Dungeon (middle level) (would come from: Sulphur lizard)
- Lizards: Neypotzli - Streambound Cavern (would come from: Grimy lizard)
- Lizards: West of Ruins of Ullek (would come from: Desert Lizard)
- Mogres: Ardent Ocean (would come from: Mogre (sea) - no page fetched)
- Monkeys: Ape Atoll Dungeon (would come from: Monkey Zombie)
- Monkeys: Ardougne Zoo (would come from: Monkey (overview page has no LocLine))
- Monkeys: Karamja, near the volcano (would come from: Monkey (Karamja) variant)
- Monkeys: Mos Le'Harmless (would come from: Monkey variant)
- Monkeys: North-east of Shilo Village (would come from: Monkey variant)
- Monkeys: Temple of Marimbo (would come from: Monkey Guard)
- Moss giants: Iorwerth Dungeon (would come from: not on the Moss giant LocLines (separate variant page))
- Nechryael: Catacombs of Kourend (would come from: Greater Nechryael)
- Nechryael: Iorwerth Dungeon (would come from: Greater Nechryael)
- Nechryael: Wilderness Slayer Cave (would come from: Greater Nechryael)
- Ogres: Corsair Cove Dungeon (would come from: Ogress Warrior / Ogress Shaman (Ogress overview page has no LocLine))
- Pirates: Chaos Temple (Wilderness) (would come from: Zombie pirate)
- Rats: Barrows crypt (would come from: Crypt rat)
- Rats: Lumbridge Swamp (would come from: Giant rat)
- Red dragons: Catacombs of Kourend (would come from: Brutal red dragon)
- Scabarites: Uzer Mastaba - lower level (would come from: Small scarab)
- Scorpions: Lava Maze (would come from: King Scorpion)
- Skeletons: Ape Atoll Dungeon (would come from: Monkey skeleton)
- Skeletons: Catacombs of Kourend (would come from: separate Skeleton variant page)
- Skeletons: Skeletal Tomb (would come from: Calvar'ion page has no LocLine)
- Skeletons: Stronghold of Security (would come from: separate Skeleton variant page)
- Spiders: East side of Lumbridge (would come from: Giant spider)
- Spiders: Web Chasm (would come from: Spindel)
- Trolls: Ice Path (would come from: Ice troll)
- Trolls: North of the Troll arena (would come from: Mountain troll)
- Trolls: Northern Jatizso and Neitiznot (would come from: Ice troll)
- Trolls: South of Mount Quidamortem (would come from: Mountain troll)
- Trolls: Troll Stronghold (Surface) (would come from: Mountain troll)
- Trolls: Trollweiss Dungeon (would come from: Ice troll)
- Trolls: Tunnel entrance to Keldagrim (would come from: Mountain troll)
- Trolls: Wyrmscraig Cavern (would come from: Troll variant)
- TzKal-Zuk: Inferno (Mor Ul Rek) (would come from: instanced / no LocLine)
- TzTok-Jad: Mor Ul Rek - Fight Caves (would come from: instanced / no LocLine)
- Vampyres: Apsul Hunting Ground (would come from: Venator)
- Vampyres: God Wars Dungeon (would come from: Vampyre (GWD variant page))
- Vampyres: Haunted Woods (would come from: Vampyre Juvinate / Feral Vampyre)
- Vampyres: West of Burgh De Rott (would come from: Vampyre Juvinate)
- Vampyres: Wilderness God Wars Dungeon (would come from: Vampyre (GWD variant page))
- Werewolves: Canifis (would come from: Werewolf page lists Canifis only as a bullet ('but in human form'), not as a LocLine)
- Zombies: Alice's farm west of the Ectofuntus (would come from: unknown)
- Zombies: Chaos Temple (Wilderness) (would come from: Zombie pirate)
- Zombies: Forthos Dungeon (would come from: Undead Druid)
- Zombies: Graveyard of Shadows (Wilderness) (would come from: separate Zombie variant page)
- Zombies: Ruins (east) (Wilderness) (would come from: separate Zombie variant page)
- Zombies: Stronghold of Security (Catacomb of Famine) (would come from: Zombie (Stronghold of Security))
- Zulrah: Zulrah's Shrine (east of Zul-Andra) (would come from: instanced / no LocLine)

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
- 135 tasks have no wiki gear tables; gear for them exists only as curated `requiredItems`/`usefulItems`.
- Ents: no Slayer XP per kill on the wiki infobox.
- Aliases that are approximations rather than exact spots: Crabs/Ruins of Tapoyauik (middle floor only), Spiders/Forthos Dungeon (Sarachnis tomb, not the Spider's Den), Trolls/Troll Stronghold (Underground) (a troll-boss spawn inside the stronghold), Fire giants/Brimhaven Dungeon (1st floor only), Rats/Stronghold of Security (Vault of War only).
- `xcheck_area_rules.py` is a name-substring heuristic; areas whose curated name does not contain the wiki area title were not cross-checked.
