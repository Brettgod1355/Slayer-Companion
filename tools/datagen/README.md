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
(`source: "map"`, named after the page, caption and pin titles kept as annotations). Instanced
bosses with neither (Zulrah, TzTok-Jad, TzKal-Zuk) and the Barrows Brothers end up with no
locations and must be curated.

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
| `gearTables[]` | `{label, style, slots{slot → [tier1[], … tier5[]]}, slotNotes{slot → text}}` | every `{{Recommended equipment}}`; `label` is the `<tabber>` tab label (or level-2 heading / null); tier entries are `{name, txt?, pic?}` from `{{plink|Name|txt=…|pic=…}}` (`name` has any `#anchor` removed); `{{efn}}` footnotes and leftover text such as "(task only)" become `slotNotes` prefixed with `tier N:` |
| `exampleSetups[]` | `{label, equipment{slot → item}, inventory[], inventoryGrid[28], runePouch[], notes}` | `{{Equipment}}`, `{{Inventory}}`, `{{Rune pouch}}` in the same tab/section; `notes` is the tab's prose (≤ 1500 chars) |
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

### `out/locations.json` – array of unique locations across all tasks

`{id, name, displayName, link, annotations[], plane, mapID, x, y, spawns[], spawnsByTask{task → [[x,y]…]}, spawnCount, tasks[], wilderness, coordsMissing}`.
`x`/`y` is the centroid of the union of spawns from every task that uses the location; use
`spawnsByTask` (or the per-task copy in `tasks.json`) for task-specific positions.

### `out/report.json`

`counts` (tasks, withTaskPage, withSlayerInfobox, withGearTables, withAnyEquipment, withLocations,
withMasters, withStrategy, withUnlocks, withXp, curatedTasks), `fetches`, `failedPages{title → error}`,
`tasksWithoutTaskPage[{task, tried}]`, `tasksWithoutMonsterPage[]`, `tasksWithoutEquipment[]`,
`tasksWithoutLocations[]`, `tasksWithoutMasters[]`, `unmatchedCuratedLocations[]`,
`ambiguousCuratedLocations[]`, `curatedProblems[]`, `curatedTasksUnused[]`.
`trainingSummaryRows`, `trainingSummaryUnmatched[]`, `trainingSummaryAmbiguous[]` (see below).

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

## Coverage of the current build (2026-09-23)

148 tasks: 148 with a task page (76 `Slayer task/…` pages with `{{Infobox Slayer}}`, 72 monster
pages of which 35 are boss tasks), 13 with `{{Recommended equipment}}` tables (+4 with example
setups only), 144 with locations, 147 with XP per kill, 31 with a Slayer-unlock table; 540 fetches,
0 failed pages. Missing locations: Barrows Brothers, TzTok-Jad, TzKal-Zuk, Zulrah (instanced /
no `{{LocLine}}` on the wiki). Missing XP: Ents only (the `Ent` infobox has no `slayxp`).

## Maintaining

1. Run the full build, open `report.json`.
2. For each entry in `tasksWithoutTaskPage` / `tasksWithoutMonsterPage` / `tasksWithoutLocations`,
   look at `tried` and add the right page to `TASK_PAGE_OVERRIDES`, `MONSTER_PAGE_OVERRIDES` or
   `EXTRA_MONSTER_PAGES`.
3. Re-run with `--only "<task>"` – the cache makes this instant unless a new page is needed.
4. `tasksWithoutEquipment` is expected for tasks whose wiki page has no gear tables (many low-level
   tasks and all boss pages); fill those through curated data if wanted.
