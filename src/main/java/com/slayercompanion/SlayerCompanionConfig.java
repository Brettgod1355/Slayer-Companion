/*
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.slayercompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SlayerCompanionConfig.GROUP)
public interface SlayerCompanionConfig extends Config
{
	String GROUP = "slayercompanion";

	enum CombatStyle
	{
		AUTO, MELEE, RANGED, MAGIC
	}

	enum PointStrategy
	{
		/** Never suggest skipping; suggest the master that pays most for milestone tasks. */
		KEEP_STREAK,
		/** Also suggest skipping tasks on the skip list when that is cheaper than doing them. */
		MAX_POINTS,
		OFF
	}

	@ConfigSection(name = "Display", description = "Overlay and panel display options", position = 0)
	String display = "display";

	@ConfigSection(name = "Locations and routing", description = "Locations, map markers and Shortest Path", position = 1)
	String routing = "routing";

	@ConfigSection(name = "Gear", description = "Gear advice", position = 2)
	String gear = "gear";

	@ConfigSection(name = "Points", description = "Point and streak planning", position = 3)
	String points = "points";

	@ConfigSection(name = "Tracker", description = "Loot and supplies tracking", position = 4)
	String tracker = "tracker";

	@ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show the on-screen task summary overlay", section = display, position = 0)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(keyName = "overlayShowProfit", name = "Overlay: session profit", description = "Show loot minus supplies for the current task on the overlay", section = display, position = 1)
	default boolean overlayShowProfit()
	{
		return true;
	}

	@ConfigItem(keyName = "overlayShowRisk", name = "Overlay: wilderness risk", description = "Show the value you would lose on death while in the Wilderness", section = display, position = 2)
	default boolean overlayShowRisk()
	{
		return true;
	}

	@ConfigItem(keyName = "showWildernessTab", name = "Show Wilderness tab", description = "Show the Wilderness information tab in the panel", section = display, position = 3)
	default boolean showWildernessTab()
	{
		return true;
	}

	@ConfigItem(keyName = "showMapMarkers", name = "Map markers", description = "Mark the current task's locations on the world map", section = routing, position = 0)
	default boolean showMapMarkers()
	{
		return true;
	}

	@ConfigItem(keyName = "autoRouteFavourite", name = "Auto-route to favourite", description = "When a new task is assigned, ask Shortest Path to draw the route to your favourite location for it", section = routing, position = 1)
	default boolean autoRouteFavourite()
	{
		return false;
	}

	@ConfigItem(keyName = "preferredStyle", name = "Preferred style", description = "Which combat style's gear table to show first", section = gear, position = 0)
	default CombatStyle preferredStyle()
	{
		return CombatStyle.AUTO;
	}

	@ConfigItem(keyName = "pointStrategy", name = "Point strategy", description = "How the Points tab advises you: keep your streak, maximise points, or off", section = points, position = 0)
	default PointStrategy pointStrategy()
	{
		return PointStrategy.KEEP_STREAK;
	}

	@ConfigItem(keyName = "eliteKourendDiary", name = "Elite Kourend & Kebos diary", description = "You have the elite Kourend & Kebos diary (Konar gives more points)", section = points, position = 1)
	default boolean eliteKourendDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "eliteWesternDiary", name = "Elite Western diary", description = "You have the elite Western Provinces diary (Nieve/Steve give more points)", section = points, position = 2)
	default boolean eliteWesternDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "skipList", name = "Tasks to skip", description = "Comma-separated task names you would rather skip when maximising points", section = points, position = 3)
	default String skipList()
	{
		return "";
	}

	@ConfigItem(keyName = "trackSupplies", name = "Track supplies", description = "Count food, potions, runes and ammo used during a task", section = tracker, position = 0)
	default boolean trackSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "supplyItemNames", name = "Extra supply items", description = "Comma-separated item names to also count as supplies", section = tracker, position = 1)
	default String supplyItemNames()
	{
		return "";
	}
}
