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
import com.slayercompanion.gear.GearSetup;
import com.slayercompanion.gear.SetupStore;
import com.slayercompanion.gear.UpgradeAdvisor;
import com.slayercompanion.points.PointsPlan;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.tracker.TaskSession;
import com.slayercompanion.unlocks.UnlockAdvice;
import com.slayercompanion.wilderness.WildernessStatus;
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
	boolean shortestPathAvailable;
	boolean bankKnown;
	List<String> missingRequiredItems;
	@Nullable
	GearSetup savedSetup;
	List<SetupStore.Difference> setupDifferences;
	List<UpgradeAdvisor.UpgradeSuggestion> upgrades;
	@Nullable
	PointsPlan pointsPlan;
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
	/** Slot advice per gear table index of {@code info.gearTablesOrEmpty()}. */
	java.util.Map<Integer, List<com.slayercompanion.gear.SlotAdvice>> gearAdvice;
	int points;
	boolean loggedIn;
}
