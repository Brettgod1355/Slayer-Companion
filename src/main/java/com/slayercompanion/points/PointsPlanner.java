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
package com.slayercompanion.points;

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.MasterInfo;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Works out how many points the next task is worth at each master, which task number is the next
 * milestone (every 10th, 50th, 100th, 250th, 1000th consecutive task, higher interval wins), and
 * which master pays best for it. Pure function of streak, points and the bundled master tables.
 */
@Singleton
public class PointsPlanner
{
	static final int[] MILESTONES = {1000, 250, 100, 50, 10};
	/** Points start on the fifth consecutive task. */
	static final int FIRST_PAID_TASK = 5;

	private final SlayerData data;
	private final SlayerCompanionConfig config;

	@Inject
	PointsPlanner(SlayerData data, SlayerCompanionConfig config)
	{
		this.data = data;
		this.config = config;
	}

	/**
	 * @param sharedStreak the shared streak counter (Turael..Konar); Krystilia and Mortimer keep
	 *                     their own counters and are shown separately.
	 */
	public PointsPlan plan(int points, int sharedStreak, @Nullable CurrentTask task)
	{
		int next = sharedStreak + 1;
		int interval = milestoneInterval(next);
		int untilMilestone = tasksUntilNextMilestone(next);

		List<PointsPlan.MasterOption> options = new ArrayList<>();
		for (SlayerMaster m : SlayerMaster.values())
		{
			if (m.isSeparateStreak() || !m.givesPoints())
			{
				continue;
			}
			MasterInfo info = data.master(m).orElse(null);
			if (info == null)
			{
				continue;
			}
			int pts = pointsForTask(info, next, boosted(m));
			String note = interval > 0 ? "milestone task " + next : "";
			options.add(new PointsPlan.MasterOption(m.getDisplayName(), pts, interval > 0, note));
		}
		options.sort((a, b) -> Integer.compare(b.getPointsForNextTask(), a.getPointsForNextTask()));

		PointsPlan.MasterOption recommended = null;
		String summary;
		if (config.pointStrategy() == SlayerCompanionConfig.PointStrategy.OFF)
		{
			summary = "Point planning is turned off in the plugin settings.";
		}
		else if (next < FIRST_PAID_TASK)
		{
			summary = "Points start on your 5th consecutive task. " + (FIRST_PAID_TASK - next) + " more to go.";
		}
		else
		{
			recommended = options.isEmpty() ? null : options.get(0);
			StringBuilder sb = new StringBuilder();
			if (interval > 0 && recommended != null)
			{
				sb.append("Task ").append(next).append(" is a milestone (every ").append(interval)
					.append("th). ").append(recommended.getMasterName()).append(" pays ")
					.append(recommended.getPointsForNextTask()).append(" points for it.");
			}
			else
			{
				sb.append("Next milestone in ").append(untilMilestone).append(" task")
					.append(untilMilestone == 1 ? "" : "s").append(" (task ").append(next + untilMilestone - 1)
					.append("). ");
				if (recommended != null)
				{
					sb.append("Highest regular rate: ").append(recommended.getMasterName()).append(" at ")
						.append(recommended.getPointsForNextTask()).append(" points per task.");
				}
			}
			if (config.pointStrategy() == SlayerCompanionConfig.PointStrategy.MAX_POINTS && task != null
				&& isOnSkipList(task.getName()))
			{
				MasterInfo current = data.master(task.getMaster()).orElse(null);
				int skipCost = current == null ? 30 : current.getSkipCost();
				sb.append(" This task is on your skip list; skipping costs ").append(skipCost)
					.append(" points and keeps your streak.");
			}
			summary = sb.toString();
		}

		return new PointsPlan(points, sharedStreak, next, interval, untilMilestone, recommended,
			Collections.unmodifiableList(options), summary);
	}

	/** Points a completed task numbered {@code taskNumber} in the streak awards at this master. */
	public int pointsForTask(MasterInfo info, int taskNumber, boolean boosted)
	{
		if (taskNumber < FIRST_PAID_TASK)
		{
			return 0;
		}
		int interval = milestoneInterval(taskNumber);
		Map<String, Integer> table = boosted && info.getMilestonesBoosted() != null
			? info.getMilestonesBoosted() : info.getMilestones();
		if (interval > 0 && table != null && table.get(String.valueOf(interval)) != null)
		{
			return table.get(String.valueOf(interval));
		}
		return boosted && info.getPointsPerTaskBoosted() != null ? info.getPointsPerTaskBoosted() : info.getPointsPerTask();
	}

	/** Largest milestone interval that divides the task number, or 0. */
	public static int milestoneInterval(int taskNumber)
	{
		if (taskNumber <= 0)
		{
			return 0;
		}
		for (int m : MILESTONES)
		{
			if (taskNumber % m == 0)
			{
				return m;
			}
		}
		return 0;
	}

	/** How many tasks including {@code taskNumber} until a milestone task (1 when it is one). */
	public static int tasksUntilNextMilestone(int taskNumber)
	{
		int n = Math.max(1, taskNumber);
		int steps = 1;
		while (milestoneInterval(n) == 0)
		{
			n++;
			steps++;
		}
		return steps;
	}

	private boolean boosted(SlayerMaster m)
	{
		if (m == SlayerMaster.KONAR)
		{
			return config.eliteKourendDiary();
		}
		if (m == SlayerMaster.NIEVE)
		{
			return config.eliteWesternDiary();
		}
		return false;
	}

	private boolean isOnSkipList(String taskName)
	{
		for (String s : config.skipList().split(","))
		{
			if (!s.trim().isEmpty() && s.trim().equalsIgnoreCase(taskName))
			{
				return true;
			}
		}
		return false;
	}
}
