# Slayer Companion

A RuneLite plugin that puts everything about your current Slayer task in one side panel.

- **Task** – how the task is normally done, what to bring (checked against your bank), what else counts, XP per kill and how many the assigning master gives.
- **Where** – every location with multi-combat and cannon flags, Wilderness level, requirements and notes. Star a favourite; it is listed first and marked on the world map. A *Route* button hands the spot to the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin, which draws the way. This plugin never walks, clicks or moves the camera.
- **Gear** – the wiki's recommended equipment per slot next to the best item you actually own; save your own setup per task and see what you are missing; a "worth saving for" list weighted by what your master assigns.
- **Points** – points, streaks, the next milestone task and which master pays most for it.
- **Loot** – kills, Slayer XP, loot value, supplies used and profit for the current task, plus recent history.
- **Wild** – what you are carrying and what the game's death rules would keep or lose, with the rules explained. Purely informational.
- **Unlocks** – the reward shop with live costs and what you already own, ordered by suggested priority.

## Privacy

The plugin makes **no network requests** and sends nothing anywhere. All reference data (task
notes, gear tables, locations, master and reward information) is bundled inside the plugin. It is
derived from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki) under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) and refreshed by the
maintainer with `tools/datagen` – never by the client.

## Building

```
./gradlew build      # requires JDK 11
./gradlew run        # starts a development client with the plugin loaded
```

## Refreshing the bundled wiki data (maintainers)

```
python3 tools/datagen/generate.py --out src/main/resources/com/slayercompanion/data
```

The script caches pages under `tools/datagen/cache/` and is polite to the wiki (identifying
User-Agent, half a second between requests, back-off on 429).

## Licence

BSD 2-Clause. See `LICENSE`. Wiki-derived data: CC BY-NC-SA 3.0, Old School RuneScape Wiki contributors.
RuneScape and Old School RuneScape are trademarks of Jagex Ltd; this plugin is not affiliated with Jagex.
