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
package com.slayercompanion.task;

import com.slayercompanion.SlayerCompanionConfig;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Remembers, per RuneScape profile, which assignment the once-per-task actions (open the linked
 * Inventory Setup, route to the favourite spot) last ran for. A login, world hop or plugin restart
 * reports the task in hand as if it were new; this tells the two apart.
 */
@Singleton
public class AssignmentMemory
{
	static final String KEY = "autoActionsTask";

	private final ConfigManager configManager;

	@Inject
	AssignmentMemory(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** True the first time this assignment is seen on this profile; remembers it. */
	public boolean firstSighting(CurrentTask task)
	{
		String key = key(task);
		if (key.equals(configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, KEY)))
		{
			return false;
		}
		configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, KEY, key);
		return true;
	}

	/** The task was finished: the next one is new even if it is the same monster from the same master. */
	public void forget()
	{
		configManager.unsetRSProfileConfiguration(SlayerCompanionConfig.GROUP, KEY);
	}

	static String key(CurrentTask task)
	{
		return task.getName().toLowerCase(Locale.ROOT) + "|" + (task.getMaster() == null ? "" : task.getMaster().name())
			+ "|" + (task.getAreaName() == null ? "" : task.getAreaName());
	}
}
