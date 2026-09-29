# Slayer Companion

A RuneLite plugin that puts everything about your current Slayer task in one side panel.

- **Task** – one view that answers *do it?*, *where?* and *what to wear?* top to bottom. With no task, it offers a *Route* to the master who pays most for your next task and to your last master, with their travel tips.
  - **Verdict and details** – a **verdict** first: the wiki's do / skip / block advice for the task and what skipping or blocking costs you in points, plus what the kills you have left are worth on average (wiki drop rates, today's GE prices) and your chance of the big drops before the task ends ("Abyssal whip (1/512): 30%"). Then how the task is normally done, what to bring (checked against your bank), what else counts, XP per kill, how many the assigning master gives, and the **Combat Achievements** for the monster with the ones you have done ticked.
  - **Where** – every location with multi-combat and cannon flags, Wilderness level, requirements and notes. A padlock shows whether you meet the requirements the client can check (quests, diaries, levels, membership, Slayer unlocks): open shackle = you can go, closed = locked with the reason, grey = nothing checkable. Locked spots stay listed and routable. Star a favourite; it is listed first and marked on the world map. A *Route* button hands the spot to the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin, which draws the way. This plugin never walks, clicks or moves the camera. Only your favourite spot (or the first) starts open; click a spot's name for the rest.
  - **Gear** – one link to the wiki page with the recommended gear and strategy for your task. If you picked a variant with its own guide (e.g. Vorkath on a Blue dragons task), the link follows it. Below it, **your loadout**: press *Save what I have on* and the plugin keeps your worn equipment and inventory for that task, drawn like the game's Worn Equipment tab and inventory. Slots you do not have on you are tinted red (amber when you have some but not enough, or it is in the wrong place); hover one to see whether it is in your bank. If you use the [Inventory Setups](https://runelite.net/plugin-hub/show/inventory-setups) plugin, link one of your setups to the task: *Open in Inventory Setups* shows it there (Inventory Setups filters your bank to it), and it can open by itself when you get the task. **Recommend from my bank** fills your loadout's gear with the highest-DPS gear you own (bank, inventory and worn) for the task's monster, keeping your saved inventory, and *Undo* puts it back; a dropdown switches between the best melee, ranged and magic gear. It works from your levels and the wiki's item and monster stats: Slayer helmet on task, salve against undead, demonbane, dragonbane, Keris, leaf-bladed weapons for Kurask and Turoth, Twisted bow scaling, void, crystal and more. The card shows the style, max hit, accuracy and DPS next to what your worn gear does now. With the bank open, **Show in bank** filters the bank to your loadout, laid out the same way: the equipment in the Worn Equipment shape on the left and your 28 inventory slots on the right (through RuneLite's Bank Tags plugin; items you do not have show as placeholders). An estimate: it assumes potions and your best prayers (both can be turned off), and does not check level requirements, ammo tiers, runes or charges.
- **Points** – points, streaks, the next milestone task and which master pays most for it.
- **Loot** – kills, Slayer XP, loot value, supplies used and profit for the current task, and your **luck**: your loot against the wiki average for the same kills. A summary of the task you just finished, and recent history with its luck.
- **Wild** – what you are carrying and what the game's death rules would keep or lose, with the rules explained. Purely informational.
- **Unlocks** – the reward shop with live costs and what you already own, ordered by suggested priority.

## Privacy

The plugin makes **no network requests of its own**. Your saved loadouts, favourites, bank snapshot and task history are stored in your RuneLite profile settings like any other plugin's configuration. All reference data (task
notes, locations, drop rates, item and monster stats, Combat Achievements, master and reward information) is bundled inside the plugin. It is
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
cd tools/datagen && python3 generate.py --out ../../src/main/resources/com/slayercompanion/data
```

The script caches pages under `tools/datagen/cache/` and is polite to the wiki (identifying
User-Agent, half a second between requests, back-off on 429).

## Licence

BSD 2-Clause. See `LICENSE`. Wiki-derived data: CC BY-NC-SA 3.0, Old School RuneScape Wiki contributors.
RuneScape and Old School RuneScape are trademarks of Jagex Ltd; this plugin is not affiliated with Jagex.
