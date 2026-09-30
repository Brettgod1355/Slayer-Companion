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
package com.slayercompanion.worth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.data.MasterInfo;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.task.SlayerMaster;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

public class WorthTest
{
	private static final int WHIP = 4151;
	private final SlayerData data = new SlayerData(new Gson());

	@Test
	public void lootEstimateAndUniqueChances()
	{
		ItemManager items = mock(ItemManager.class);
		when(items.getItemPrice(anyInt())).thenReturn(0L);
		when(items.getItemPrice(WHIP)).thenReturn(1_536_000L);
		TaskInfo abyssal = data.task("Abyssal demons").get();

		LootEstimate e = new LootEstimator(data, items).estimate(abyssal, null, 180).get();
		assertEquals("Abyssal demon", e.getMonster());
		assertEquals(3000, e.getPerKill());
		assertEquals(540_000, e.getTotal());
		LootEstimate.Unique whip = e.getUniques().get(0);
		assertEquals("Abyssal whip", whip.getItem());
		assertEquals("1/512", whip.getRarity());
		assertEquals(1 - Math.pow(511 / 512.0, 180), whip.getChanceOverKills(), 1e-6);

		// A picked variant uses its own drops.
		assertEquals("Abyssal Sire", new LootEstimator(data, items).estimate(abyssal, "Abyssal Sire", 10).get().getMonster());
	}

	@Test
	public void verdictReadsTheWikiFirstWord()
	{
		assertEquals(Verdict.Kind.DO, VerdictAdvisor.kind("Do"));
		assertEquals(Verdict.Kind.SKIP, VerdictAdvisor.kind("Skip unless you are an ironman hunting for a black mask"));
		assertEquals(Verdict.Kind.BLOCK, VerdictAdvisor.kind("Block unless killing Cerberus for profit"));
		assertEquals(Verdict.Kind.BLOCK, VerdictAdvisor.kind("Block with the unique unlock"));
		assertEquals(Verdict.Kind.DEPENDS, VerdictAdvisor.kind("Do for profit; skip for XP"));
		assertEquals(Verdict.Kind.DEPENDS, VerdictAdvisor.kind("Do if you have access to Blood Barrage/can consistently do Fight Caves. Otherwise, do not unlock."));
		assertEquals(Verdict.Kind.NONE, VerdictAdvisor.kind("Should not be unlocked"));
		assertEquals(Verdict.Kind.NONE, VerdictAdvisor.kind("Don't unlock for XP; can unlock for profit or hunting the basilisk jaw on an ironman"));
		assertEquals(Verdict.Kind.NONE, VerdictAdvisor.kind(null));
	}

	@Test
	public void verdictAddsWhatSkippingCosts()
	{
		MasterInfo duradel = data.master(SlayerMaster.DURADEL).get();
		TaskInfo skipped = data.tasks().stream()
			.filter(t -> t.getTrainingSummary() != null && "Skip".equals(t.getTrainingSummary().getRecommendation()))
			.findFirst().get();
		Verdict rich = VerdictAdvisor.verdict(skipped, duradel, 500);
		assertEquals(Verdict.Kind.SKIP, rich.getKind());
		assertEquals("Skipping costs " + duradel.getSkipCost() + " points; you have 500.", rich.getNotes().get(0));
		Verdict poor = VerdictAdvisor.verdict(skipped, duradel, 5);
		assertTrue(poor.getNotes().get(0).endsWith("not enough."));

		Verdict none = VerdictAdvisor.verdict(null, duradel, 500);
		assertEquals(Verdict.Kind.NONE, none.getKind());
		assertNull(none.getWikiSays());
		assertTrue(none.getNotes().isEmpty());
	}

	@Test
	public void blockNotesNameTheMastersList()
	{
		TaskInfo blocked = data.tasks().stream()
			.filter(t -> t.getTrainingSummary() != null && t.getTrainingSummary().getRecommendation() != null
				&& t.getTrainingSummary().getRecommendation().startsWith("Block"))
			.findFirst().get();
		MasterInfo duradel = data.master(SlayerMaster.DURADEL).get();
		List<String> notes = VerdictAdvisor.verdict(blocked, duradel, 500).getNotes();
		assertEquals("Blocking costs 100 points and stops Duradel assigning it again.", notes.get(1));
		assertEquals(2, notes.size());

		notes = VerdictAdvisor.verdict(blocked, data.master(SlayerMaster.MORTIMER).get(), 500).getNotes();
		assertEquals("Skipping costs 100 points; you have 500.", notes.get(0));
		assertEquals("Blocking costs 120 points and stops Mortimer assigning it again.", notes.get(1));
		assertEquals("Mortimer has only 2 block slots.", notes.get(2));

		notes = VerdictAdvisor.verdict(blocked, data.master(SlayerMaster.SPRIA).get(), 500).getNotes();
		assertEquals("Blocking costs 40 points and stops Turael and Spria assigning it again.", notes.get(1));
	}

	@Test
	public void combatAchievementBits()
	{
		Client client = mock(Client.class);
		when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_0)).thenReturn(1 << 5);
		when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_13)).thenReturn(1 << 31);
		assertTrue(CombatAchievements.isDone(client, 5));
		assertFalse(CombatAchievements.isDone(client, 6));
		assertTrue(CombatAchievements.isDone(client, 13 * 32 + 31));
		assertNull(CombatAchievements.isDone(client, 21 * 32));
		assertNull(CombatAchievements.isDone(client, -1));
	}
}
