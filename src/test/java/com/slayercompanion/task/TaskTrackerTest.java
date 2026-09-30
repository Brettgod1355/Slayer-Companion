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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;

/** Rows and values below are the game cache's (SlayerTask 113, SlayerMasterTask 114). */
public class TaskTrackerTest
{
	private static final int GARGOYLES_ID = 46;
	private static final int GARGOYLES_ROW = 6259;
	private static final int ROCKSLUGS_ROW = 6264;
	private static final int MORTIMER_ROCKSLUGS = 7175;
	private static final int MORTIMER_GARGOYLES = 7192;
	private static final int GET_SMASHED_ROW = 6420;

	private Client client;
	private TaskTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		tracker = new TaskTracker(client, mock(ClientThread.class), new EventBus());
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(150);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(GARGOYLES_ID);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(150);
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, GARGOYLES_ID))
			.thenReturn(Collections.singletonList(GARGOYLES_ROW));
		when(client.getDBTableField(GARGOYLES_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Gargoyles"});
		when(client.getDBTableField(GARGOYLES_ROW, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, 0))
			.thenReturn(new Object[]{GET_SMASHED_ROW, 200, 250});
		master(SlayerMaster.MORTIMER);
		when(client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0, 10))
			.thenReturn(Arrays.asList(MORTIMER_ROCKSLUGS, MORTIMER_GARGOYLES));
		row(MORTIMER_ROCKSLUGS, ROCKSLUGS_ROW, 35, 50);
		row(MORTIMER_GARGOYLES, GARGOYLES_ROW, 120, 180);
	}

	private void master(SlayerMaster master)
	{
		when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(master.getVarbitValue());
	}

	private void row(int row, int taskRow, int min, int max)
	{
		when(client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_TASK, 0)).thenReturn(new Object[]{taskRow});
		when(client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, 0)).thenReturn(new Object[]{min});
		when(client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, 0)).thenReturn(new Object[]{max});
	}

	private void modifier(int type, int value, boolean negative)
	{
		when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)).thenReturn(type);
		when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE)).thenReturn(value);
		when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE)).thenReturn(negative ? 1 : 0);
	}

	private CurrentTask read()
	{
		tracker.refresh();
		return tracker.current().orElseThrow(AssertionError::new);
	}

	@Test
	public void rangeComesFromTheMastersRowForTheTaskWithItsExtension()
	{
		CurrentTask t = read();
		assertEquals("Gargoyles", t.getName());
		assertEquals(new Assignment(120, 180, 200, 250), t.getAssignment());
		assertNull(t.getModifier());
	}

	@Test
	public void anAdditiveExtensionIsAddedToTheMastersRange()
	{
		when(client.getDBTableField(GARGOYLES_ROW, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, 0)).thenReturn(new Object[0]);
		when(client.getDBTableField(GARGOYLES_ROW, DBTableID.SlayerTask.COL_EXTENSION_ADDITIVE, 0))
			.thenReturn(new Object[]{GET_SMASHED_ROW, 80, 80});
		assertEquals(new Assignment(120, 180, 200, 260), read().getAssignment());
	}

	@Test
	public void noExtensionLeavesItNull()
	{
		when(client.getDBTableField(GARGOYLES_ROW, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, 0))
			.thenThrow(new IllegalArgumentException("empty column"));
		assertEquals(new Assignment(120, 180, null, null), read().getAssignment());
	}

	@Test
	public void aMasterWithoutARowForTheTaskHasNoRange()
	{
		master(SlayerMaster.TURAEL);
		when(client.getDBRowsByValue(eq(DBTableID.SlayerMasterTask.ID), eq(DBTableID.SlayerMasterTask.COL_MASTER_ID), eq(0), eq(1)))
			.thenReturn(Collections.singletonList(MORTIMER_ROCKSLUGS));
		assertNull(read().getAssignment());
	}

	@Test
	public void anUnreadableTableIsRetriedOnTheNextRead()
	{
		when(client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0, 10))
			.thenThrow(new IllegalStateException("tables not loaded"))
			.thenReturn(Collections.singletonList(MORTIMER_GARGOYLES));
		assertNull(read().getAssignment());
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(149);
		assertEquals(new Assignment(120, 180, 200, 250), read().getAssignment());
	}

	@Test
	public void bossTasksHaveNoRange()
	{
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
		when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(7);
		when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 7))
			.thenReturn(Collections.singletonList(1));
		when(client.getDBTableField(1, DBTableID.SlayerTaskSublist.COL_TASK, 0)).thenReturn(new Object[]{GARGOYLES_ROW});
		CurrentTask t = read();
		assertTrue(t.isBossTask());
		assertNull(t.getAssignment());
	}

	@Test
	public void mortimersAmountModifierChangesTheInitialCountBothWays()
	{
		modifier(TaskModifier.AMOUNT, 65, false);
		CurrentTask t = read();
		assertEquals(215, t.getInitialAmount());
		assertEquals("+65 assigned", t.getModifier().describe());

		modifier(TaskModifier.AMOUNT, 20, true);
		t = read();
		assertEquals(130, t.getInitialAmount());
		assertEquals("-20 assigned", t.getModifier().describe());
	}

	@Test
	public void otherModifiersLeaveTheCountAlone()
	{
		modifier(TaskModifier.POINTS, 25, false);
		CurrentTask t = read();
		assertEquals(150, t.getInitialAmount());
		assertEquals("+25 Slayer points", t.getModifier().describe());
		modifier(TaskModifier.CLUES, 100, false);
		assertEquals("+100% clue chance", read().getModifier().describe());
		modifier(TaskModifier.SUPERIORS, 250, false);
		assertEquals("+250% superior unique chance", read().getModifier().describe());
		modifier(TaskModifier.XP, 50, false);
		assertEquals("+50% Slayer XP", read().getModifier().describe());
	}

	@Test
	public void noOrUnknownModifierIsNull()
	{
		assertNull(TaskModifier.of(0, 0, false));
		assertNull(TaskModifier.of(6, 10, false));
		assertNull(TaskModifier.of(TaskModifier.POINTS, 0, false));
		assertNotNull(TaskModifier.of(TaskModifier.XP, 1, false));
	}

	@Test
	public void aModifierChangeIsANewSnapshot()
	{
		CurrentTask before = read();
		modifier(TaskModifier.XP, 50, false);
		CurrentTask after = read();
		assertTrue(before.sameAssignment(after));
		assertNotEquals(before, after);
	}
}
