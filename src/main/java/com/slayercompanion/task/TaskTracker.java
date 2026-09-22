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

import com.slayercompanion.events.TaskChanged;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reads the current Slayer assignment straight from the client's varps and cache database tables,
 * the same way RuneLite's core Slayer plugin does, and publishes {@link TaskChanged} events.
 * <p>
 * All reads happen on the client thread.
 */
@Slf4j
@Singleton
public class TaskTracker
{
	/** {@code VarPlayerID.SLAYER_TARGET} value used for boss assignments. */
	private static final int BOSS_TASK_ID = 98;

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;

	@Nullable
	private CurrentTask current;

	@Inject
	TaskTracker(Client client, ClientThread clientThread, EventBus eventBus)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
	}

	public void startUp()
	{
		eventBus.register(this);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::refresh);
		}
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		current = null;
	}

	public Optional<CurrentTask> current()
	{
		return Optional.ofNullable(current);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::refresh);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varp = event.getVarpId();
		int varbit = event.getVarbitId();
		if (varp == VarPlayerID.SLAYER_COUNT
			|| varp == VarPlayerID.SLAYER_AREA
			|| varp == VarPlayerID.SLAYER_TARGET
			|| varp == VarPlayerID.SLAYER_COUNT_ORIGINAL
			|| varp == VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED
			|| varbit == VarbitID.SLAYER_TARGET_BOSSID
			|| varbit == VarbitID.SLAYER_MODIFIER_ID
			|| varbit == VarbitID.SLAYER_MODIFIER_VALUE
			|| varbit == VarbitID.SLAYER_MODIFIER_NEGATIVE
			|| varbit == VarbitID.SLAYER_MASTER
			|| varbit == VarbitID.SLAYER_POINTS
			|| varbit == VarbitID.SLAYER_TASKS_COMPLETED
			|| varbit == VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)
		{
			refresh();
		}
	}

	/** Re-read the assignment from the client. Must run on the client thread. */
	public void refresh()
	{
		CurrentTask next = read();
		CurrentTask prev = current;
		boolean changed = prev == null ? next != null : !prev.equals(next);
		current = next;
		if (changed)
		{
			log.debug("Slayer task changed: {} -> {}", prev, next);
			eventBus.post(new TaskChanged(prev, next));
		}
	}

	@Nullable
	private CurrentTask read()
	{
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (remaining <= 0)
		{
			return null;
		}

		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		boolean boss = taskId == BOSS_TASK_ID;
		int taskRow;
		if (boss)
		{
			List<Integer> bossRows = client.getDBRowsByValue(
				DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0,
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (bossRows.isEmpty())
			{
				return null;
			}
			taskRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (rows.isEmpty())
			{
				return null;
			}
			taskRow = rows.get(0);
		}

		String name = (String) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];

		String area = null;
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (areaId > 0)
		{
			List<Integer> areaRows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
			if (!areaRows.isEmpty())
			{
				area = (String) client.getDBTableField(areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
			}
		}

		int initial = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == 2)
		{
			boolean negative = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1;
			int value = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
			initial += negative ? -value : value;
		}

		SlayerMaster master = SlayerMaster.fromVarbit(client.getVarbitValue(VarbitID.SLAYER_MASTER));
		int points = client.getVarbitValue(VarbitID.SLAYER_POINTS);
		int streak = streakFor(master);

		return new CurrentTask(name, remaining, initial, area, master, boss, points, streak);
	}

	/** Streak counter that applies to the given master. Client thread only. */
	public int streakFor(@Nullable SlayerMaster master)
	{
		if (master == SlayerMaster.KRYSTILIA)
		{
			return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
		}
		if (master == SlayerMaster.MORTIMER)
		{
			return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
		}
		return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
	}

	/** Current points. Client thread only. */
	public int points()
	{
		return client.getVarbitValue(VarbitID.SLAYER_POINTS);
	}
}
