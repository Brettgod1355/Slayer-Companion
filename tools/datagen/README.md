# Slayer Companion – wiki data generator (`tools/datagen`)

`generate.py` (re)builds the bundled data files for the Slayer Companion RuneLite plugin
from the Old School RuneScape Wiki. **It is a maintainer tool.** End users never run it and the
plugin never contacts the wiki: the JSON it produces is committed under
`src/main/resources/com/slayercompanion/data/`.

Python 3.11, standard library only (`urllib`, `json`, `re`, `pathlib`, `time`, `argparse`, `html`).

## Usage

```
python3 generate.py --out out                # full build (uses cache/, ~300-500 fetches first time)
python3 generate.py --only "Bloodveld,Elves" # rebuild only these tasks, keep the rest from out/tasks.json
python3 generate.py --refresh                # ignore the cache and re-fetch every page
python3 generate.py --limit 5                # smoke test on the first five tasks
python3 generate.py --self-test              # unit tests for parse_access() (no network, no cache)
```

| Option | Default | Meaning |
| --- | --- | --- |
| `--tasks PATH` | `../research/task-enum.txt` | tab-separated `ENUM_NAME<TAB>display name<TAB>alt1|alt2` |
| `--out DIR` | `out/` | where `tasks.json`, `locations.json`, `report.json`, `item-names.txt` are written |
| `--cache DIR` | `cache/` | raw wikitext cache (`<sanitised title>.wikitext` + `.meta.json`) |
| `--curated DIR` | `curated/verified/` | curated override files (may be empty or absent) |
| `--refresh` | off | re-fetch even if cached |
| `--only A,B` | – | comma-separated display names or substrings; other tasks are copied from the previous `out/tasks.json` |
| `--limit N` | 0 | only the first N tasks (testing) |
| `--sleep S` | 0.5 | seconds between network fetches |
| `--self-test` | off | run the `parse_access()` assertions and exit (90 checks on the tricky requirement strings) |

Progress goes to stderr; a summary of the `counts` block of `report.json` goes to stdout.

### Fetching / cache

* MediaWiki API: `action=parse&prop=wikitext&formatversion=2&redirects=1`, User-Agent
  `SlayerCompanion-datagen/0.1 (github.com/Brettgod1355/Slayer-Companion)`.
* 0.5 s sleep between fetches. HTTP 429 → exponential back-off 2 s, 4 s, 8 s (3 attempts) then the
  page is given up and listed in `report.json → failedPages`.
* Every answer is cached: `cache/<title>.wikitext` + `cache/<title>.meta.json` (requested title,
  final title, `redirected`, status `ok`/`missing`). Missing pages are cached too, so re-runs do not
  re-ask for them. Title sanitising: space → `_`, `/` → `__`, `:` → `_`.

### Page mapping

For every display name the script tries, in order, until a page exists:

1. `TASK_PAGE_OVERRIDES[display]` (explicit table at the top of the script),
2. `Slayer task/<display>`, `Slayer task/<display without "The ">`, `Slayer task/<singular variants>`,
3. `<display>`, `<display without "The ">`, `<singular variants>` (this is how boss tasks such as
   "The Abyssal Sire" end up on the `Abyssal Sire` monster page).

Singular variants: `…ves → …f/…fe`, `…ies → …y`, drop trailing `s`, drop trailing `es`.
`wikiTaskPageKind` records whether the page has an `{{Infobox Slayer}}` (`slayer-task`) or is a
monster page (`monster`); see boss detection below.

Monster pages: `MONSTER_PAGE_OVERRIDES[display]` (replaces the guess) or the first singular variant
whose page contains `{{Infobox Monster}}`, plus `EXTRA_MONSTER_PAGES[display]`, plus every
alternative NPC name from column 3 of the task list, plus the task page itself when it is a monster
page. **When the report shows a task with no monster page or no locations, add an entry to one of
these tables and re-run with `--only`.**

Boss detection (`bossTask`): the task page is a monster page and its `{{Infobox Monster}}` `cat`
contains "Bosses" (for overview pages such as *Dagannoth Kings*, every variant page must be a boss),
or the display name is in `BOSS_TASK_OVERRIDES` (TzTok-Jad, TzKal-Zuk whose `cat` is "TzHaar").
Plain monsters that have no `Slayer task/…` page (Cows, Kurask, …) also resolve to their monster
page but are *not* bosses.

Locations come from `{{LocLine}}` templates (`source: "locline"`). A monster page without any
LocLine falls back to `{{Map}}` templates in its `==Location==` / `==Transportation==` section
(`source: "map"`, named after the page, caption and pin titles kept as annotations), and failing
that to the `map` field of an `{{Infobox Location}}` / `{{Infobox Activity}}`. The latter only
matters for the location pages listed in `EXTRA_MONSTER_PAGES` to give an instanced encounter its
entrance: `Barrows` (Barrows Brothers), `TzHaar Fight Cave` (TzTok-Jad), `Inferno` (TzKal-Zuk),
`Zul-Andra` (Zulrah, the boat to the shrine).

Strategy pages (gear): most `{{Recommended equipment}}` tables live on `<Monster>/Strategies`
subpages. `resolve_strategy_pages()` tries, in order, `<task page>/Strategies`,
`<primary monster page>/Strategies` (primary = the `MONSTER_PAGE_OVERRIDES` list or the singular
guess; alternatives and `EXTRA_MONSTER_PAGES` are **not** tried, they are mostly other encounters
such as bosses that also count for the task), then `STRATEGY_PAGE_OVERRIDES[display]` (the task's
own strategy page under another name, e.g. `TzHaar Fight Cave/Strategies` for TzTok-Jad,
`Inferno/Strategies`, `Barrows/Strategies`, `Metal dragons/Strategies`) and
`STRATEGY_VARIANT_PAGES[display]` (a variant that counts for the task, e.g. `Brutal black
dragon/Strategies`, `Artio/Strategies` for Callisto, `Lizardman shaman/Strategies`). Pages are
de-duplicated by final title; a `/Strategies` redirect back to the task page itself (e.g. `Dark
beast/Strategies` → `Slayer task/Dark beast`) is dropped. `collect_gear()` runs every page through
`parse_gear_sections()` after the task page's own tables:

* every table and setup gets `source` = the wiki page it came from;
* labels of `STRATEGY_VARIANT_PAGES` tables are prefixed with the page subject (`Artio: Ranged`);
* a table identical (style + slots) to one already collected is skipped;
* a label that repeats within a task (two tabbers on one page, e.g. Abyssal Sire phase 1 / phase 2
  "Ranged") is extended on its second occurrence: with the style when it differs from the first
  table's (`Melee (Punish)`, `Ranged (Phase 2 Ranged)`), else with the source page
  (`Melee Slash/Stab (Aquanite/Strategies)`), else `#n`.

A nested `{{#tag:tabber|A=…{{!}}-{{!}}B=…}}` inside a `<tabber>` tab (Kalphite Queen,
Lizardman shaman) gives each inner tab its own label `"<outer> - <inner>"`. Rows `2h1`…`2h5`
(two-handed weapon, Kree'arra) fill the `weapon` slot when the table has no `weaponN` row. Tiers
above 5 (`neck6`, `body6`, …) are ignored, as before. `report.json → gearSources{task → {pages,
strategyPagesTried}}` lists the pages that contributed and every strategy title tried.

### Curated overrides

`curated/verified/*.json` – each file is an array of task objects:

```json
[{
  "task": "Bloodveld",
  "summary": "…", "recommendedStyle": "Melee",
  "requiredItems": ["…"], "usefulItems": ["…"], "superior": "Insatiable Bloodveld",
  "alternatives": ["Mutated Bloodveld"], "bossTask": false,
  "locations": [{
    "name": "Stronghold Slayer Cave", "rank": 1, "multi": "false", "cannon": "true",
    "wilderness": false, "wildernessLevelMin": null, "wildernessLevelMax": null,
    "konarAssignable": "true", "requirements": ["Slayer ring recommended"], "notes": "…"
  }]
}]
```

* Tasks match by display name, case-insensitive. Curated task fields override generated ones.
* Curated locations match generated locations by normalised name in three passes: exact
  (lower-case, punctuation stripped) against `name` or `displayName`; then with floor/basement
  parentheticals stripped; then with every parenthetical stripped. So `"Slayer Tower (basement)"`
  and `"Slayer Tower (1st floor)"` each hit their own record, while `"Slayer tower"` alone falls
  through to the floor-stripped pass.
  Curated location fields override the generated ones. Curated locations with no match are appended
  with `x`/`y`/`plane` = `null` and `coordsMissing: true`, and are listed in
  `report.json → unmatchedCuratedLocations`; multiple matches are listed under
  `ambiguousCuratedLocations` (the first match is used).
* Before those passes, `CURATED_LOCATION_ALIASES[display][displayName or name]` (a table at the
  top of the script) can pin a curated record to one generated location `id` ("tier 0"); the
  displayName is looked up first so two curated records sharing a `name` can be told apart. A
  value of `None` forces "no match" when the loose passes would pick a wrong record. An alias that
  points at an id the task does not have is listed in `report.json → curatedProblems`.
* The directory may be empty or missing.

## Outputs

All JSON is pretty-printed, keys sorted, UTF-8, `ensure_ascii=False`.

### `out/tasks.json` – array, one record per display name

| Field | Type | Source |
| --- | --- | --- |
| `enum`, `task`, `alternatives[]` | string(s) | task-enum.txt |
| `wikiTaskPage`, `wikiTaskPageRequested`, `wikiTaskPageRedirected`, `wikiTaskPageKind`, `wikiTaskPageTried[]` | | page mapping (`kind`: `slayer-task` / `monster` / null) |
| `wikiMonsterPages[]`, `wikiMonsterPagesTried[]` | | monster page mapping |
| `bossTask` | bool | monster-page task whose infobox `cat` lists "Bosses" (see page mapping; curated can override) |
| `taskId` | int/null | `{{Infobox Slayer}}` `id` |
| `requirements` | `{skills:[{skill,level}], slayer, combat, other}` | `skillreq`, `combatreq`, `otherreq`; `slayer` falls back to the monster's `slaylvl` |
| `masters` | `{master → {raw, min, max, extMin, extMax, weight, alternatives:[{min,max,extMin,extMax}]}}` | infobox fields `turael…mortimer`; e.g. `120-170 (200-250) (Weighting 9)`. A fixed amount `50 (91-150)` gives `min = max = 50`. Mortimer's `A <br/> or B` keeps B in `alternatives`. Only present for pages with `{{Infobox Slayer}}` (76 of 148); the plugin reads live ranges from the game cache anyway |
| `summary` | string/null | first intro paragraph (≤ 400 chars) – curated overrides |
| `recommendedStyle`, `styleNotes[]` | | style of the first gear table; `"<label>: <style>"` for each table |
| `requiredItems[]`, `usefulItems[]`, `superior` | | curated only (empty/null otherwise) |
| `xpPerKill` | int/null | first monster page's `slayxp` |
| `gearTables[]` | `{label, style, slots{slot → [tier1[], … tier5[]]}, slotNotes{slot → text}, source}` | every `{{Recommended equipment}}` of the task page and its strategy pages (see page mapping); `source` is the wiki page; `label` is the `<tabber>` tab label (or level-2 heading / null); tier entries are `{name, txt?, pic?}` from `{{plink|Name|txt=…|pic=…}}` (`name` has any `#anchor` removed); `{{efn}}` footnotes and leftover text such as "(task only)" become `slotNotes` prefixed with `tier N:` |
| `exampleSetups[]` | `{label, equipment{slot → item}, inventory[], inventoryGrid[28], runePouch[], notes, source}` | `{{Equipment}}`, `{{Inventory}}`, `{{Rune pouch}}` in the same tab/section; `notes` is the tab's prose (≤ 1500 chars) |
| `strategy[]` | strings | first 3 intro paragraphs + all `==Strategy==` paragraphs, plain text, capped at 2500 chars; bullet lists become `- ` lines inside one paragraph |
| `unlocks[]` | `{name, cost, note}` | "Related slayer shop options" / "Slayer unlocks" table |
| `monsters[]` | `{page, redirected, hasInfobox, locLines, mapLocations, name, versions[], slayerXp, slayerLevel, combat, hitpoints, maxHit, attackStyles[], weakness, attributes, size, aggressive, poisonous, attackSpeed, immuneCannon, immuneThrall, npcIds[], slayerCategory, assignedBy[]}` + `<field>ByVersion{version → value}` when the infobox is versioned | `{{Infobox Monster}}` of every monster page |
| `locations[]` | see below | merged `{{LocLine}}` records + curated |
| `curatedFile` | string/null | which curated file matched |

`locations[]` entry (TaskLocation):

| Field | Meaning |
| --- | --- |
| `id` | stable id: slug of location text + `-p<plane>` (`slayer-tower-p2`, `slayer-tower-basement-p0`; `-px` for curated-only locations without a plane) |
| `name` | location text with templates stripped ("Slayer Tower") |
| `displayName` | text with templates rendered ("Slayer Tower (2nd floor)", "Abyssal Area (fairy ring ALR)") |
| `link` | wikilink target of the location |
| `annotations[]`, `annotationsRaw[]` | rendered / raw templates found in the location text (`{{Fairycode|alr}}`, `{{FloorNumber|uk=2}}`) |
| `source` | `locline` or `map` (see page mapping) |
| `plane`, `mapID`, `members`, `levels[]`, `dropversions[]`, `monsters[]`, `pages[]` | from the LocLine(s) |
| `x`, `y` | centroid of all spawns (rounded); `null` when no spawns |
| `spawns[[x,y]…]`, `spawnsByMonster{monster → [[x,y]…]}`, `spawnCount` | full spawn list |
| `coordsMissing` | true when there are no coordinates |
| `rank`, `multi`, `cannon`, `wilderness`, `wildernessLevelMin`, `wildernessLevelMax`, `konarAssignable`, `requirements[]`, `notes` | curated fields (defaults: `null`, `"unknown"`, `"unknown"`, heuristic bool, `null`, `null`, `"unknown"`, `[]`, `null`). `wilderness` defaults to true when the name contains "Wilderness" or a spawn lies in the surface Wilderness box |
| `curated`, `curatedName` | whether a curated record matched and under which name |
| `access` | `{groups[]}` derived from `requirements[]` by `parse_access()`, see below |

### `out/locations.json` – array of unique locations across all tasks

`{id, name, displayName, link, annotations[], plane, mapID, x, y, spawns[], spawnsByTask{task → [[x,y]…]}, spawnCount, tasks[], wilderness, coordsMissing, requirementsByTask{task → [str]}, accessByTask{task → access}}`.
`x`/`y` is the centroid of the union of spawns from every task that uses the location; use
`spawnsByTask` (or the per-task copy in `tasks.json`) for task-specific positions. Requirements
are curated per task (a task-only cave is task-only for one task), so they and their parsed
`access` are keyed by task rather than merged: a union would lock a location for the wrong task.

### `out/report.json`

`counts` (tasks, withTaskPage, withSlayerInfobox, withGearTables, withAnyEquipment, withLocations,
withMasters, withStrategy, withUnlocks, withXp, curatedTasks), `fetches`, `failedPages{title → error}`,
`gearSources{task → {pages[], strategyPagesTried[]}}`,
`tasksWithoutTaskPage[{task, tried}]`, `tasksWithoutMonsterPage[]`, `tasksWithoutEquipment[]`,
`tasksWithoutLocations[]`, `tasksWithoutMasters[]`, `unmatchedCuratedLocations[]`,
`ambiguousCuratedLocations[]`, `curatedProblems[]`, `curatedTasksUnused[]`.
`trainingSummaryRows`, `trainingSummaryUnmatched[]`, `trainingSummaryAmbiguous[]` (see below),
`accessCounts{…}`, `accessUnparsed{string → count}` (see "access"); `counts` also gains
`accessGroups`, `accessGroupsCheckable`, `accessGroupsManual`, `accessUnparsed`.

### `out/general-gear.json` – general Slayer gear (not task-specific)

Built by `build_general_gear()` from the `==Equipment==` section of the wiki page **Slayer training**
(cached like every other page under `cache/Slayer_training.wikitext`). Same parser as the per-task
gear, so the objects have the same shape as in `tasks.json`:

```json
{"source": "Slayer training",
 "gearTables": [GearTable…],      // one {{Recommended equipment}} per <tabber> tab: Melee, Melee (Hallowfell), Magic (Barrage), Ranged, Ranged (Wilderness Slayer)
 "exampleSetups": [ExampleSetup…], // the {{Equipment}} / {{Inventory}} example of each tab (label = tab label)
 "notes": ["…"]}                   // the section's prose before the tabber, plain text, at most 8 paragraphs
```

### `trainingSummary` / `recommendedStyleSource` on `tasks.json` records

`apply_training_summary()` parses the `==Task summary==` wikitable of *Slayer training*
(Assignment, Slayer level, Weight, Pros, Cons, Recommendation, Approx. XP/h) and attaches each row
to the matching task as

```json
"trainingSummary": {"assignment": "Abyssal demons", "pros": "…", "cons": "…",
                    "recommendation": "Do for money; block for XP",
                    "xpPerHour": "80,000 (Magic); 53,000 (Venator Bow); 40,000 (Melee)"}
```

(wikitext stripped, link labels kept, `<br>` / line breaks in the XP cell become `; `). Rows are
matched by normalised name (lower-case, non-alphanumerics dropped, singular/plural variants) against,
in priority order, the task's display name, its `alternatives`, then its monster page names /
infobox names. Tasks without a row simply lack the key. Rows that match nothing (e.g. *Bosses*,
*Gargoyles*, *Mutated Zygomites*) are listed in `report.json → trainingSummaryUnmatched` (with a
`reason`); rows matching several tasks go to `trainingSummaryAmbiguous` (first task wins).

`derive_recommended_styles()` then fills `recommendedStyle` where it is `null`, recording the origin
in `recommendedStyleSource`:

| `recommendedStyleSource` | Rule |
| --- | --- |
| `gearTables` | style of the task's first `{{Recommended equipment}}` table (the original behaviour) |
| `curated` | style came from a curated file |
| `trainingSummary` | `recommendation` or `xpPerHour` names a style: Magic / barrage / burst → `Magic`, Ranged / chin → `Ranged`, Melee → `Melee` (first mention wins) |
| `monsterWeakness` | the monster infobox `weakness` / `elementalweaknesstype` field: crush / slash / stab → `Melee`; fire / water / earth / air / magic → `Magic`; ranged / arrows / bolts → `Ranged` |

Nothing else is guessed; tasks matching no rule keep `null` and no `recommendedStyleSource`. Note
that the OSRS wiki no longer fills `weakness`; every hit of the last rule comes from
`elementalweaknesstype`, i.e. an elemental (Magic) weakness, whatever its percentage. `report.json →
counts` gains `withTrainingSummary`, `withRecommendedStyle`, `recommendedStyleBySource` and
`generalGearTables`.

### `out/item-names.txt`

Every distinct item name referenced by gear tables (`name` and `pic` of each `{{plink}}`; `txt`
labels such as "Rada's blessing 3/2" are not real items and are left out), example equipment,
inventories and rune pouches – one per line, sorted. The plugin resolves these through
`ItemManager.search` at runtime, so they must be exact wiki item names; names that are wiki
*pages* rather than items (e.g. `God capes`, `Cape of Accomplishment (t)`) will simply not resolve
and the accompanying `pic` name (`Imbued Saradomin cape`, `Strength cape(t)`) will.


### `access` on `locations[]` – checkable requirements

`apply_access()` runs after the curated overrides and turns every location's free-text
`requirements[]` into a structure the plugin's `AccessChecker` can evaluate through the RuneLite
API (quest states, real skill levels, combat level, diary varbits, membership, the live Slayer
reward list). `parse_access(requirements) -> dict` is a pure, table-driven function; `--self-test`
runs its unit tests.

```
"access": {
  "groups": [                      // every group must be satisfied (AND)
    { "text": "<original requirement string>",
      "any": [ rule, ... ],        // alternatives (OR); empty when nothing is checkable
      "manual": true|false,        // true when the string (or part of it) cannot be checked by the client
      "note": "..." }              // optional: why (see the table below)
  ]
}
rule = {"type":"quest","quest":"PRIEST_IN_PERIL","name":"Priest in Peril","state":"FINISHED"|"IN_PROGRESS"}
     | {"type":"skill","skill":"AGILITY","level":70}
     | {"type":"combat","level":75}
     | {"type":"diary","varbit":"MORYTANIA_DIARY_HARD_COMPLETE","name":"Morytania Hard diary"}
     | {"type":"unlock","name":"Like a Boss"}       // slayer reward unlock, checked by name against the live reward list
     | {"type":"members"}
```

`quest` is the RuneLite `Quest` enum name and `name` its display name (`QUESTS` table in the
script); `skill` a RuneLite `Skill` enum name; `varbit` a `VarbitID` constant name (`1` = tier
complete). `IN_PROGRESS` means "at least started"; the checker treats `FINISHED` as satisfying it.

The checker ANDs the groups and ORs the rules inside `any`; a group whose rules all evaluate
false is a hard lock whatever `manual` says, and a group with an empty `any` is shown as a
manual reminder. **A wrong lock is worse than a missing one**, so the parser is deliberately
conservative:

| Input | Output |
| --- | --- |
| whole string mentions assignment (`to be assigned`, `to be offered`, `for the (boss) task`, `assigns`, `to receive … tasks`, `Slayer task list requirement`, `for God Wars Dungeon slayer tasks`…) | `any: []`, `manual: true`, `note: "assignment requirement"` – a master requirement, not a location requirement |
| `Optional: …`, `… recommended …` | `any: []`, manual, `note: "optional"` |
| `Not available after …`, `no longer …` | `any: []`, manual, `note: "negative requirement"` |
| `None (free-to-play)` | `any: []`, `manual: false`, `note: "no requirement"` |
| `Task-only area`, `Must be on a … task`, `<Monster> Slayer task`, `On-task only` | `any: []`, manual, `note: "task-only"` |
| `N Skill` / `Skill N` / `level N Skill` (+ `(boostable)`, `(not boostable)`, `to …`, `for …`) | skill rule; `Combat N` / `N Combat` → combat rule. A qualifier naming a `shortcut`, `route`, `entrance`, `stepping stones`, `per the task page` makes it manual (it may not gate the whole location) |
| `<Easy/Medium/Hard/Elite> <Region> Diary` in any word order, optional `the`, `is complete`/`is done` | diary rule; Karamja easy/medium/hard have no varbit → manual |
| `Members`, `Members (Varlamore)` | members rule |
| `<Name> unlock (N Slayer reward points)`, `'<Name>' Slayer unlock` | unlock rule when `<Name>` matches `UNLOCK_NAMES` case-insensitively (the exact live name is emitted) |
| quest name, optionally led by `Partial completion of` / `Started` / `Completion of` and followed by `quest`/`miniquest`/`started`/`completed`/`partial`, then at most one `to access|enter|reach|board …`, `to the point of …`, `far enough to …` or `for <Region> access` clause | quest rule. The longest word prefix that is a quest wins, so `Heroes' Quest` keeps its "Quest". State is `IN_PROGRESS` when the piece (with its own parentheticals) says `partial`, `started`, `progressed`, `to the point`, `far enough` or `during`, else `FINISHED`. Names are matched after lower-casing, dropping punctuation and a leading "The"; `QUEST_ALIASES` adds short forms (`DS2`, `SotE`, `MM2`, `RFD`, `Recipe for Disaster: Freeing Sir Amik Varze`, …) |
| `A or B`, `A / B`, `A, or B`, `A unless B`, `A (not needed with B)`, `A (or B)`, `A (permanent after B)` | alternatives: every alternative must parse, otherwise the whole group becomes `any: []` + manual (`Dusty key or 70 Agility` never locks on Agility alone; `Medium Wilderness Diary (or a Callisto boss task)` never locks on the diary) |
| `A, B and C`, `A; B`, `A plus B` (no `or` outside parentheses) | conjunction: each checkable part becomes its own group (a necessary condition is always safe to lock on), the uncheckable parts one extra manual group with the same `text` |
| parentheticals | `(boostable)`, `(not boostable)`, `(partial …)`, `(started …)`, `(completed)`, `(… access)`, `(to enter …)`, `(reached …)`, `(defeat Dad)`, `(fairy ring X)`, `(N Slayer reward points)`, `(boulder)`, `(jutting wall)` are ignored; a parenthetical containing `or`, `unless`, `bypass`, `instead`, `with`, `without`, `only`, `free`, `half`, `formerly`, `after` may describe another way in and drops the rules (`any: []`); any other parenthetical keeps the rules but sets `manual: true` (`66 Slayer (82 for Ancient Wyverns)`) |
| everything else (items, keys, light sources, ropes, fees, kill counts, boat spots, combat-achievement tiers, "X to avoid aggression", "Label: …" sub-area notes) | `any: []`, `manual: true` |

A phrase that looks like a quest (contains `quest`, `partial`, `started`, `complet…`, `progress`,
a known quest name, or is a Title Case phrase) but does not parse is **never** turned into a
rule; it is reported in `report.json → accessUnparsed` with the number of location records that
carry it, so the maintainer can add an alias or fix the curated text. `accessCounts` gives
`locations`, `strings`, `distinctStrings`, `groups`, `checkable` (non-empty `any`),
`checkableStrict` (non-empty `any` and `manual: false`), `manual`, `distinctStringsCheckable`,
`distinctStringsManualOnly`. Rules are never invented: when in doubt the string is manual.

## Coverage of the current build (2026-09-23)

148 tasks: 148 with a task page (76 `Slayer task/…` pages with `{{Infobox Slayer}}`, 72 monster
pages of which 35 are boss tasks), 60 with `{{Recommended equipment}}` tables (188 tables from
the task pages and 54 strategy pages; +4 with example setups only), 148 with located records
(1025 location records, 1019 with coordinates; 5 curated records without), 147 with XP per kill,
31 with a Slayer-unlock table; 0 failed pages (a warm cache needs 0 fetches). Instanced encounters
get their entrance from the activity page's infobox `{{Map}}`. Missing XP: Ents only (the `Ent`
infobox has no `slayxp`).
Access: 1025 location records carry 537 requirement strings (377 distinct) →
551 groups, 264 with rules (252 fully checkable), 298 manual;
19 strings listed in `accessUnparsed` (see COVERAGE.md).

## Maintaining

1. Run the full build, open `report.json`.
2. For each entry in `tasksWithoutTaskPage` / `tasksWithoutMonsterPage` / `tasksWithoutLocations`,
   look at `tried` and add the right page to `TASK_PAGE_OVERRIDES`, `MONSTER_PAGE_OVERRIDES` or
   `EXTRA_MONSTER_PAGES`.
3. Re-run with `--only "<task>"` – the cache makes this instant unless a new page is needed.
4. `tasksWithoutEquipment` is expected for tasks whose wiki page and strategy pages have no gear
   tables (mostly low-level tasks); `gearSources` shows which `/Strategies` titles were tried. When
   the wiki has a strategy page under another name, add it to `STRATEGY_PAGE_OVERRIDES` (the task's
   own monster) or `STRATEGY_VARIANT_PAGES` (a variant; labels get a prefix).

### Extra gear pages, variant gear and extra location pages (2026-09-24)

* `GEAR_PAGES[task]` — list of `{"page", "variant", "tabs"}`: further pages with
  `{{Recommended equipment}}` tables, each checked with the generator's own parser. `variant`
  (a monster name) makes the tables that variant's own: they get `"variant"` in `tasks.json` and
  the plugin shows them only when that variant is chosen; without it they are the task's own.
  `tabs` keeps only the tables with those labels (pages with tabs for several monsters). A task
  page tab may be copied to a variant (`Slayer task/Trolls` → `Ice troll male`); a table is kept
  at most once per variant.
* `STRATEGY_VARIANT_PAGES` tables also carry `variant`.
* `finalise_variant_gear()` (after all tasks are built): a table whose variant the plugin cannot
  select is dropped when it came from `GEAR_PAGES`; one from `STRATEGY_VARIANT_PAGES` becomes the
  task's own (label keeps the prefix) unless the task already has the same table.
* `EXTRA_LOCATION_PAGES[task]` — list of `{"page", "monster", "rename", "only"}`: pages whose
  `{{LocLine}}`s give coordinates for a spot without being a variant of their own (the 20 Canifis
  citizens who turn into werewolves; the level-108 mourners in the tunnels; the Vault of War
  goblins). `monster` files the spawns under a variant name, `rename` under one location name (so
  they merge and meet the curated record), `only` keeps listed LocLine locations.
* LocLine monster names are matched to monster records case-insensitively, by infobox name or
  page title, and `Name (version)` counts as `Name`; a LocLine without a name is the page's own
  monster. A record whose page title qualifies its name (`Guard (dwarf)`, `Rock (Troll)`, but not
  `… (monster)`) takes the page title as its variant name.

### Variant names and empty tiers

* `name_variants()` gives every monster record a name that works as the plugin's variant key: a
  record without an infobox name, or one whose name another record of the same task shares
  (`Dagannoth` vs `Dagannoth (Waterbirth Island)`), takes its page title. Locations' `monsters`
  lists already use the page title in those cases, so the variant filter lines up.
* Gear slots drop empty tiers (the wiki leaves some rows blank); the next filled row moves up and
  a slot with nothing left is removed.

