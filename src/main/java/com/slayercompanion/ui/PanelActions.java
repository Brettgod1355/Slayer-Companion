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

import com.slayercompanion.data.TaskLocation;
import javax.annotation.Nullable;

/** Callbacks from the panel into the plugin. Implementations hop to the client thread as needed. */
public interface PanelActions
{
	void routeTo(TaskLocation location);

	void clearRoute();

	/** Ask Shortest Path to draw the way to a Slayer master (by display name). */
	void routeToMaster(String masterName);

	void setFavourite(String taskName, @Nullable String locationId);

	/** Choose which monster variant of the task the player is doing (filters locations and XP). */
	void setVariant(String taskName, @Nullable String monsterName);

	/** Save what the player is wearing and carrying as the task's loadout. */
	void saveLoadout(String taskName);

	void deleteLoadout(String taskName);

	/** Link one of the player's Inventory Setups setups to the task; null unlinks. */
	void linkInventorySetup(String taskName, @Nullable String setupName);

	/** Ask Inventory Setups to open the setup (it filters the bank to it). */
	void openInventorySetup(String setupName);

	/** Work out the best-DPS gear from the bank for the task's monster. */
	void recommendLoadout(String taskName);

	/** Save recommendation option {@code index} as the task's worn gear (the saved inventory stays). */
	void useRecommendation(String taskName, int index);

	void resetSession();

	void refresh();

	void openWiki(String pageTitle);
}
