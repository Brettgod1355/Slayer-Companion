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
package com.slayercompanion.ui;

import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import com.slayercompanion.gear.LoadoutDisplay;
import com.slayercompanion.points.PointsPlan;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.tracker.TaskSession;
import com.slayercompanion.unlocks.UnlockAdvice;
import com.slayercompanion.wilderness.WildernessStatus;
import com.slayercompanion.worth.LootEstimate;
import com.slayercompanion.worth.Verdict;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable snapshot handed from the client thread to the Swing panel. Everything the panel
 * renders comes from here so the UI never touches the client directly.
 */
@Value
@Builder
public class PanelModel
{
	@Nullable
	CurrentTask task;
	@Nullable
	TaskInfo info;
	List<TaskLocation> locations;
	@Nullable
	String favouriteLocationId;
	/** Variant monster names the player can pick for this task (empty when only one). */
	List<String> variants;
	@Nullable
	String selectedVariant;
	/** Slayer XP per kill of the selected variant, or the task's default. */
	@Nullable
	Integer xpPerKill;
	boolean shortestPathAvailable;
	boolean bankKnown;
	List<String> missingRequiredItems;
	/** The player's saved loadout for this task with where each item is now; null when none is saved. */
	@Nullable
	LoadoutDisplay loadout;
	/** Setup names from the Inventory Setups plugin; empty when it is not installed or has none. */
	List<String> inventorySetups;
	/** The Inventory Setups setup linked to this task. */
	@Nullable
	String linkedSetup;
	/** Best-DPS gear from the bank for the current task and variant; null until asked for. */
	@Nullable
	com.slayercompanion.dps.Recommendation recommendation;
	/** Where each recommended item is, one per {@code recommendation} option. */
	List<LoadoutDisplay> recommendationDisplays;
	/** True while the recommendation is being worked out. */
	boolean recommending;
	@Nullable
	PointsPlan pointsPlan;
	/** Do / skip / block for the current task. */
	@Nullable
	Verdict verdict;
	/** Average loot for the kills left, from the wiki's drop rates and GE prices. */
	@Nullable
	LootEstimate lootEstimate;
	/** Average loot for the current session's kills (the luck line compares loot with it). */
	@Nullable
	Long sessionExpected;
	/** Average loot for each {@code history} entry's kills, same order; null where unknown. */
	List<Long> historyExpected;
	/** Combat Achievement task ids the player has completed (only the current task's are read). */
	java.util.Set<Integer> completedAchievements;
	int sharedStreak;
	int wildernessStreak;
	int mortimerStreak;
	@Nullable
	TaskSession session;
	List<TaskSession> history;
	@Nullable
	WildernessStatus wilderness;
	List<UnlockAdvice> unlocks;
	/** Item id -> display name for everything the Loot tab shows. */
	java.util.Map<Integer, String> itemNames;
	/** Location id -> lock state for the current task's locations. */
	java.util.Map<String, com.slayercompanion.game.LockState> locks;
	int points;
	boolean loggedIn;
}
