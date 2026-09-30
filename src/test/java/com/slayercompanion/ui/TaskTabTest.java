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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.slayercompanion.data.MasterAssignmentInfo;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.task.Assignment;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import java.util.Collections;
import org.junit.Test;

public class TaskTabTest
{
	private static TaskInfo bundled(String master, int min, int max, Integer extMin, Integer extMax)
	{
		MasterAssignmentInfo a = new MasterAssignmentInfo();
		a.setMin(min);
		a.setMax(max);
		a.setExtMin(extMin);
		a.setExtMax(extMax);
		TaskInfo info = new TaskInfo();
		info.setMasters(Collections.singletonMap(master, a));
		return info;
	}

	private static CurrentTask task(SlayerMaster master, Assignment live)
	{
		return new CurrentTask("Gargoyles", 100, 150, null, master, false, 0, 0, live, null);
	}

	@Test
	public void theLiveRangeWinsOverTheBundledOne()
	{
		TaskInfo info = bundled("mortimer", 1, 2, 3, 4);
		assertEquals("120-180 (200-250 extended)",
			TaskTab.assignedRange(task(SlayerMaster.MORTIMER, new Assignment(120, 180, 200, 250)), info));
		assertEquals("150-200", TaskTab.assignedRange(task(SlayerMaster.MORTIMER, new Assignment(150, 200, null, null)), info));
	}

	@Test
	public void theBundledRangeIsTheFallback()
	{
		TaskInfo info = bundled("mortimer", 120, 180, 200, 250);
		assertEquals("120-180 (200-250 extended)", TaskTab.assignedRange(task(SlayerMaster.MORTIMER, null), info));
		assertNull("no entry for this master", TaskTab.assignedRange(task(SlayerMaster.KONAR, null), info));
		assertNull("no master", TaskTab.assignedRange(task(null, null), info));
	}
}
