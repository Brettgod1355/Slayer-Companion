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
package com.slayercompanion.gear;

import static com.slayercompanion.gear.GearFixture.RUNE_SCIMITAR;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static com.slayercompanion.gear.GearFixture.table;
import static com.slayercompanion.gear.GearFixture.tier;
import static com.slayercompanion.gear.GearFixture.tiers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slayercompanion.data.GearTable;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.game.LiveSlayerCatalog;
import com.slayercompanion.task.SlayerMaster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class UpgradeAdvisorTest
{
	private static final double EPS = 1e-9;

	private final GearFixture world = new GearFixture();
	private final SlayerData data = mock(SlayerData.class);
	private final LiveSlayerCatalog catalog = mock(LiveSlayerCatalog.class);
	private final List<TaskInfo> tasks = new ArrayList<>();

	private UpgradeAdvisor advisor()
	{
		when(data.tasks()).thenReturn(tasks);
		List<GearTable> all = new ArrayList<>();
		for (TaskInfo t : tasks)
		{
			all.addAll(t.gearTablesOrEmpty());
		}
		world.index(all.toArray(new GearTable[0]));
		return new UpgradeAdvisor(data, world.resolver, world.owned, world.itemManager, catalog);
	}

	private TaskInfo task(String name, GearTable... tables)
	{
		TaskInfo t = new TaskInfo();
		t.setTask(name);
		t.setGearTables(Arrays.asList(tables));
		tasks.add(t);
		return t;
	}

	private static UpgradeAdvisor.UpgradeSuggestion find(List<UpgradeAdvisor.UpgradeSuggestion> list, String item)
	{
		for (UpgradeAdvisor.UpgradeSuggestion s : list)
		{
			if (s.getItemName().equals(item))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void itemCountsOncePerTaskHoweverOftenItIsListed()
	{
		// Both style tables, and two slots of one table, list the helmet.
		task("Abyssal demons",
			table("Melee", "head", tiers(tier("Slayer helmet (i)")), "special", tiers(tier("Slayer helmet (i)"))),
			table("Ranged", "head", tiers(tier("Slayer helmet (i)"))));
		UpgradeAdvisor.UpgradeSuggestion helm = find(advisor().suggest(null, null), "Slayer helmet (i)");
		assertEquals(1.0, helm.getScore(), EPS);
		assertEquals(Collections.singletonList("Abyssal demons"), helm.getTasksHelped());
	}

	@Test
	public void itemCountsAtItsBestTierWhateverTheTableOrder()
	{
		// Second tier in the first table, top tier in the second: the task counts it as top tier.
		task("Abyssal demons",
			table("Melee", "weapon", tiers(tier("Abyssal tentacle"), tier("Abyssal whip"))),
			table("Ranged", "weapon", tiers(tier("Abyssal whip"))));
		assertEquals(1.0, find(advisor().suggest(null, null), "Abyssal whip").getScore(), EPS);
	}

	@Test
	public void eachTaskHelpedAddsToTheScore()
	{
		task("Abyssal demons", table("Melee", "head", tiers(tier("Slayer helmet (i)"))));
		task("Nechryael", table("Melee", "head", tiers(tier("Slayer helmet (i)"))), table("Magic", "head", tiers(tier("Slayer helmet (i)"))));
		UpgradeAdvisor.UpgradeSuggestion helm = find(advisor().suggest(null, null), "Slayer helmet (i)");
		assertEquals(2.0, helm.getScore(), EPS);
		assertEquals(Arrays.asList("Abyssal demons", "Nechryael"), helm.getTasksHelped());
		assertEquals("head", helm.getSlot());
	}

	@Test
	public void secondTierCountsHalfAndLowerTiersNotAtAll()
	{
		task("Gargoyles", table("Melee", "weapon", tiers(tier("Abyssal tentacle"), tier("Abyssal whip"), tier("Rune scimitar"))));
		List<UpgradeAdvisor.UpgradeSuggestion> out = advisor().suggest(null, null);
		assertEquals(1.0, find(out, "Abyssal tentacle").getScore(), EPS);
		assertEquals(0.5, find(out, "Abyssal whip").getScore(), EPS);
		assertNull(find(out, "Rune scimitar"));
		assertEquals("best first", "Abyssal tentacle", out.get(0).getItemName());
	}

	@Test
	public void ownedItemsAndWhatTheyBeatAreNotSuggested()
	{
		task("Gargoyles", table("Melee",
			"weapon", tiers(tier("Abyssal tentacle"), tier("Abyssal whip", "Rune scimitar")),
			"cape", tiers(tier("Fire cape"), tier("Rune scimitar"))));
		world.bank(WHIP);
		List<UpgradeAdvisor.UpgradeSuggestion> out = advisor().suggest(null, null);
		// Owning a tier-2 weapon still leaves the tier-1 one, but not the other tier-2 choice.
		assertEquals(1.0, find(out, "Abyssal tentacle").getScore(), EPS);
		assertNull(find(out, "Abyssal whip"));
		assertEquals("Rune scimitar still helps in the cape slot", 0.5, find(out, "Rune scimitar").getScore(), EPS);

		world.bank(GearFixture.FIRE_CAPE, WHIP, RUNE_SCIMITAR);
		out = advisor().suggest(null, null);
		assertNull(find(out, "Fire cape"));
		assertNull(find(out, "Rune scimitar"));
	}

	@Test
	public void bossTasksAreSkipped()
	{
		TaskInfo boss = task("Cerberus", table("Melee", "head", tiers(tier("Slayer helmet (i)"))));
		boss.setBossTask(true);
		assertTrue(advisor().suggest(null, null).isEmpty());
	}

	@Test
	public void masterWeightsTasksByAssignmentWeight()
	{
		task("Abyssal demons", table("Melee", "head", tiers(tier("Slayer helmet (i)"))));
		task("Gargoyles", table("Melee", "head", tiers(tier("Slayer helmet (i)")), "weapon", tiers(tier("Abyssal tentacle"))));
		task("Nechryael", table("Melee", "weapon", tiers(tier("Abyssal whip"))));
		when(catalog.assignmentsFor(SlayerMaster.DURADEL)).thenReturn(Arrays.asList(
			new LiveSlayerCatalog.MasterAssignment(SlayerMaster.DURADEL, "ABYSSAL DEMONS", 12, 130, 200),
			new LiveSlayerCatalog.MasterAssignment(SlayerMaster.DURADEL, "Gargoyles", 8, 130, 200),
			new LiveSlayerCatalog.MasterAssignment(SlayerMaster.DURADEL, "Gargoyles", 1, 130, 200)));
		List<UpgradeAdvisor.UpgradeSuggestion> out = advisor().suggest(SlayerMaster.DURADEL, null);
		assertEquals(21.0, find(out, "Slayer helmet (i)").getScore(), EPS);
		assertEquals(9.0, find(out, "Abyssal tentacle").getScore(), EPS);
		assertNull("Duradel does not assign Nechryael here", find(out, "Abyssal whip"));
	}

	@Test
	public void styleFilterKeepsOnlyThatStyle()
	{
		task("Abyssal demons",
			table("Melee", "weapon", tiers(tier("Abyssal tentacle"))),
			table("Magic", "weapon", tiers(tier("Trident of the swamp"))));
		List<UpgradeAdvisor.UpgradeSuggestion> out = advisor().suggest(null, "magic");
		assertEquals(1, out.size());
		assertEquals("Trident of the swamp", out.get(0).getItemName());
	}

	@Test
	public void suggestionsCarryIdAndPriceAndAreCapped()
	{
		for (int i = 0; i < 20; i++)
		{
			task("Task " + i, table("Melee", "weapon", tiers(tier("Unknown item " + i))));
		}
		task("Gargoyles", table("Melee", "weapon", tiers(tier("Abyssal whip"))));
		task("Nechryael", table("Melee", "weapon", tiers(tier("Abyssal whip"))));
		when(world.itemManager.getItemPrice(WHIP)).thenReturn(1_500_000);
		List<UpgradeAdvisor.UpgradeSuggestion> out = advisor().suggest(null, null);
		assertEquals(15, out.size());
		UpgradeAdvisor.UpgradeSuggestion whip = out.get(0);
		assertEquals("Abyssal whip", whip.getItemName());
		assertEquals(Integer.valueOf(WHIP), whip.getItemId());
		assertEquals(1_500_000, whip.getPrice());
		UpgradeAdvisor.UpgradeSuggestion unknown = out.get(1);
		assertNull(unknown.getItemId());
		assertEquals(0, unknown.getPrice());
		assertFalse(unknown.getTasksHelped().isEmpty());
	}
}
