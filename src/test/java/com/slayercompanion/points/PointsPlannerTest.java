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

import static org.junit.Assert.assertEquals;

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.MasterInfo;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.task.SlayerMaster;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PointsPlannerTest
{
	@Test
	public void milestoneIntervalPicksLargest()
	{
		assertEquals(0, PointsPlanner.milestoneInterval(7));
		assertEquals(10, PointsPlanner.milestoneInterval(10));
		assertEquals(10, PointsPlanner.milestoneInterval(20));
		assertEquals(50, PointsPlanner.milestoneInterval(50));
		assertEquals(100, PointsPlanner.milestoneInterval(100));
		assertEquals(50, PointsPlanner.milestoneInterval(150));
		assertEquals(250, PointsPlanner.milestoneInterval(250));
		assertEquals(1000, PointsPlanner.milestoneInterval(1000));
		assertEquals(250, PointsPlanner.milestoneInterval(2250));
	}

	@Test
	public void tasksUntilNextMilestoneCountsInclusive()
	{
		assertEquals(1, PointsPlanner.tasksUntilNextMilestone(10));
		assertEquals(10, PointsPlanner.tasksUntilNextMilestone(1));
		assertEquals(3, PointsPlanner.tasksUntilNextMilestone(48));
	}

	@Test
	public void pointsForTaskUsesMilestoneTable()
	{
		MasterInfo konar = new MasterInfo();
		konar.setId(SlayerMaster.KONAR.getDataId());
		konar.setPointsPerTask(18);
		konar.setPointsPerTaskBoosted(20);
		Map<String, Integer> table = new HashMap<>();
		table.put("10", 90);
		table.put("50", 270);
		table.put("100", 450);
		table.put("250", 630);
		table.put("1000", 900);
		konar.setMilestones(table);
		Map<String, Integer> boosted = new HashMap<>();
		boosted.put("10", 100);
		konar.setMilestonesBoosted(boosted);

		PointsPlanner planner = new PointsPlanner(new SlayerData(new com.google.gson.Gson()), new SlayerCompanionConfig()
		{
		});
		assertEquals(0, planner.pointsForTask(konar, 4, false));
		assertEquals(18, planner.pointsForTask(konar, 5, false));
		assertEquals(90, planner.pointsForTask(konar, 10, false));
		assertEquals(450, planner.pointsForTask(konar, 100, false));
		assertEquals(20, planner.pointsForTask(konar, 11, true));
		assertEquals(100, planner.pointsForTask(konar, 10, true));
		// Boosted table lacks the 50 entry: falls back to the boosted per-task rate rather than the unboosted table.
		assertEquals(20, planner.pointsForTask(konar, 50, true));
	}
}
