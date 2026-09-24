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

import static com.slayercompanion.gear.GearFixture.ARDOUGNE_CLOAK_4;
import static com.slayercompanion.gear.GearFixture.DRAGON_DEFENDER_T;
import static com.slayercompanion.gear.GearFixture.FIRE_CAPE;
import static com.slayercompanion.gear.GearFixture.IMBUED_SARADOMIN_CAPE;
import static com.slayercompanion.gear.GearFixture.NOSE_PEG;
import static com.slayercompanion.gear.GearFixture.NOTED_WHIP;
import static com.slayercompanion.gear.GearFixture.RUNE_DEFENDER;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET_I;
import static com.slayercompanion.gear.GearFixture.TENTACLE;
import static com.slayercompanion.gear.GearFixture.TRIDENT_SWAMP_E;
import static com.slayercompanion.gear.GearFixture.UNCHARGED_TOXIC_TRIDENT;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static com.slayercompanion.gear.GearFixture.WHIP_OR;
import static com.slayercompanion.gear.GearFixture.table;
import static com.slayercompanion.gear.GearFixture.tier;
import static com.slayercompanion.gear.GearFixture.tiers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.GearItem;
import com.slayercompanion.data.GearTable;
import com.slayercompanion.data.GeneralGearFile;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GearAdvisorTest
{
	private final GearFixture world = new GearFixture();

	private static SlayerCompanionConfig style(SlayerCompanionConfig.CombatStyle style)
	{
		return new SlayerCompanionConfig()
		{
			@Override
			public CombatStyle preferredStyle()
			{
				return style;
			}
		};
	}

	private GearAdvisor advisor()
	{
		return new GearAdvisor(world.resolver, world.owned, style(SlayerCompanionConfig.CombatStyle.AUTO));
	}

	private static SlotAdvice slot(List<SlotAdvice> advice, String slot)
	{
		for (SlotAdvice a : advice)
		{
			if (a.getSlot().equals(slot))
			{
				return a;
			}
		}
		throw new AssertionError("no advice for " + slot);
	}

	private static final GearTable MELEE = table("Melee",
		"weapon", tiers(tier("Abyssal tentacle"), tier("Abyssal whip"), tier("Rune scimitar")),
		"shield", tiers(tier("Avernic defender"), tier("Dragon defender"), tier("Rune defender")),
		"head", tiers(tier("Slayer helmet (i)")),
		"cape", tiers(tier("Infernal cape"), tier("Fire cape")));

	// --- best owned item per slot ---

	@Test
	public void bestOwnedItemAndWhatWouldBeBetter()
	{
		world.index(MELEE).bank(WHIP, RUNE_DEFENDER);
		SlotAdvice weapon = slot(advisor().advise(MELEE), "weapon");
		assertEquals("Abyssal whip", weapon.getOwnedBest());
		assertEquals(Integer.valueOf(WHIP), weapon.getOwnedBestItemId());
		assertEquals(2, weapon.getOwnedTier());
		assertEquals(Collections.singletonList("Abyssal tentacle"), weapon.getMissingBetter());
		assertEquals(Collections.singletonList("Abyssal tentacle"), weapon.getWikiBest());
		assertFalse(weapon.isEquipped());

		SlotAdvice shield = slot(advisor().advise(MELEE), "shield");
		assertEquals(3, shield.getOwnedTier());
		assertEquals(Arrays.asList("Avernic defender", "Dragon defender"), shield.getMissingBetter());
	}

	@Test
	public void higherTierWinsWhenSeveralAreOwned()
	{
		world.index(MELEE).bank(WHIP, TENTACLE);
		SlotAdvice weapon = slot(advisor().advise(MELEE), "weapon");
		assertEquals("Abyssal tentacle", weapon.getOwnedBest());
		assertEquals(1, weapon.getOwnedTier());
		assertTrue(weapon.getMissingBetter().isEmpty());
	}

	@Test
	public void nothingOwnedListsEveryTier()
	{
		world.index(MELEE);
		SlotAdvice cape = slot(advisor().advise(MELEE), "cape");
		assertNull(cape.getOwnedBest());
		assertNull(cape.getOwnedBestItemId());
		assertEquals(0, cape.getOwnedTier());
		assertEquals(Arrays.asList("Infernal cape", "Fire cape"), cape.getMissingBetter());
	}

	@Test
	public void wornAndInventoryItemsCountAndWornIsFlagged()
	{
		world.index(MELEE).worn(FIRE_CAPE).inventory(WHIP);
		List<SlotAdvice> advice = advisor().advise(MELEE);
		assertTrue(slot(advice, "cape").isEquipped());
		assertEquals("Abyssal whip", slot(advice, "weapon").getOwnedBest());
		assertFalse(slot(advice, "weapon").isEquipped());
	}

	@Test
	public void notedItemsInTheBankCount()
	{
		world.index(MELEE).bank(NOTED_WHIP);
		assertEquals(Integer.valueOf(WHIP), slot(advisor().advise(MELEE), "weapon").getOwnedBestItemId());
	}

	@Test
	public void slotsFollowDisplayOrderThenExtraSlots()
	{
		GearTable t = table("Melee",
			"special", tiers(tier("Nose peg")),
			"tail", tiers(tier("Fire cape")),
			"weapon", tiers(tier("Abyssal whip")),
			"legs", tiers(),
			"head", tiers(tier("Slayer helmet (i)")));
		Map<String, String> notes = new HashMap<>();
		notes.put("head", "Imbue it");
		t.setSlotNotes(notes);
		world.index(t);
		List<String> order = new ArrayList<>();
		for (SlotAdvice a : advisor().advise(t))
		{
			order.add(a.getSlot());
		}
		// Empty slots are dropped; slots the display order does not know go last.
		assertEquals(Arrays.asList("head", "weapon", "special", "tail"), order);
		assertEquals("Imbue it", slot(advisor().advise(t), "head").getNote());
		assertNull(slot(advisor().advise(t), "weapon").getNote());
	}

	@Test
	public void tableWithoutSlotsGivesNoAdvice()
	{
		assertTrue(advisor().advise(new GearTable()).isEmpty());
	}

	// --- variant-aware ownership ---

	@Test
	public void ornamentVariantCountsAsOwned()
	{
		// The (or) whip is untradeable, so only the client's item index knows it.
		world.index(MELEE).bank(WHIP_OR);
		SlotAdvice weapon = slot(advisor().advise(MELEE), "weapon");
		assertEquals("Abyssal whip", weapon.getOwnedBest());
		assertEquals(Integer.valueOf(WHIP_OR), weapon.getOwnedBestItemId());
	}

	@Test
	public void trimmedVariantCountsAsOwned()
	{
		world.index(MELEE).bank(DRAGON_DEFENDER_T);
		SlotAdvice shield = slot(advisor().advise(MELEE), "shield");
		assertEquals("Dragon defender", shield.getOwnedBest());
		assertEquals(2, shield.getOwnedTier());
	}

	@Test
	public void chargedVariantCountsAsOwned()
	{
		GearTable magic = table("Magic", "weapon", tiers(tier("Trident of the swamp")));
		world.index(magic).bank(TRIDENT_SWAMP_E);
		assertEquals(Integer.valueOf(TRIDENT_SWAMP_E), slot(advisor().advise(magic), "weapon").getOwnedBestItemId());
	}

	@Test
	public void imbuedVariantCountsForThePlainName()
	{
		GearTable t = table("Melee", "head", tiers(tier("Slayer helmet")));
		world.index(t).bank(SLAYER_HELMET_I);
		assertEquals(Integer.valueOf(SLAYER_HELMET_I), slot(advisor().advise(t), "head").getOwnedBestItemId());
	}

	@Test
	public void plainItemDoesNotCountForTheImbuedName()
	{
		world.index(MELEE).bank(GearFixture.SLAYER_HELMET);
		assertNull(slot(advisor().advise(MELEE), "head").getOwnedBest());
	}

	@Test
	public void differentItemsWithASharedPrefixDoNotCount()
	{
		GearTable magic = table("Magic", "weapon", tiers(tier("Trident of the swamp")));
		world.index(magic).bank(UNCHARGED_TOXIC_TRIDENT);
		assertNull(slot(advisor().advise(magic), "weapon").getOwnedBest());

		GearTable melee = table("Melee", "weapon", tiers(tier("Abyssal whip")));
		world.index(melee).bank(TENTACLE);
		assertNull(slot(advisor().advise(melee), "weapon").getOwnedBest());
	}

	@Test
	public void picturedItemIsTriedBeforeThePageName()
	{
		GearItem godCape = new GearItem();
		godCape.setName("God capes");
		godCape.setPic("Imbued saradomin cape");
		godCape.setTxt("Imbued god cape");
		GearTable t = table("Magic", "cape", Collections.singletonList(Collections.singletonList(godCape)));
		world.index(t).bank(IMBUED_SARADOMIN_CAPE);
		SlotAdvice cape = slot(advisor().advise(t), "cape");
		assertEquals("Imbued god cape", cape.getOwnedBest());
		assertEquals(Integer.valueOf(IMBUED_SARADOMIN_CAPE), cape.getOwnedBestItemId());
	}

	@Test
	public void prefixSearchIsTheLastResort()
	{
		GearTable t = table("Magic", "cape", tiers(tier("Ardougne cloak")));
		world.index(t).bank(ARDOUGNE_CLOAK_4);
		assertEquals(Integer.valueOf(ARDOUGNE_CLOAK_4), slot(advisor().advise(t), "cape").getOwnedBestItemId());
	}

	// --- required items ---

	@Test
	public void missingItemsAcceptAnyAlternative()
	{
		world.index(Arrays.asList("Nose peg", "Slayer helmet", "Fire cape")).bank(SLAYER_HELMET_I);
		GearAdvisor advisor = advisor();
		assertEquals(Collections.singletonList("Fire cape"),
			advisor.missingItems(Arrays.asList("Nose peg or Slayer helmet", "Fire cape")));
		world.bank(NOSE_PEG, FIRE_CAPE);
		assertTrue(advisor.missingItems(Arrays.asList("Nose peg or Slayer helmet", "Fire cape")).isEmpty());
		assertTrue(advisor.missingItems(null).isEmpty());
	}

	// --- which table to show first ---

	private static List<GearTable> styles(String... styles)
	{
		List<GearTable> out = new ArrayList<>();
		for (String s : styles)
		{
			GearTable t = new GearTable();
			t.setStyle(s);
			out.add(t);
		}
		return out;
	}

	@Test
	public void preferredStyleIsHonoured()
	{
		List<GearTable> tables = styles("Magic (Barrage)", "Ranged", null, "Melee");
		GearAdvisor melee = new GearAdvisor(world.resolver, world.owned, style(SlayerCompanionConfig.CombatStyle.MELEE));
		GearAdvisor magic = new GearAdvisor(world.resolver, world.owned, style(SlayerCompanionConfig.CombatStyle.MAGIC));
		GearAdvisor ranged = new GearAdvisor(world.resolver, world.owned, style(SlayerCompanionConfig.CombatStyle.RANGED));
		// The configured style beats the task's recommendation.
		assertEquals(3, melee.defaultTableIndex(tables, "Magic"));
		assertEquals(0, magic.defaultTableIndex(tables, "Melee"));
		assertEquals(1, ranged.defaultTableIndex(tables, null));
	}

	@Test
	public void autoFollowsTheTaskRecommendation()
	{
		List<GearTable> tables = styles("Magic", "Ranged", "Melee");
		GearAdvisor auto = advisor();
		assertEquals(2, auto.defaultTableIndex(tables, "melee"));
		assertEquals(0, auto.defaultTableIndex(tables, "Any"));
		assertEquals(0, auto.defaultTableIndex(tables, null));
	}

	@Test
	public void missingPreferredStyleFallsBackToTheFirstTable()
	{
		GearAdvisor magic = new GearAdvisor(world.resolver, world.owned, style(SlayerCompanionConfig.CombatStyle.MAGIC));
		assertEquals(0, magic.defaultTableIndex(styles("Ranged", "Melee"), "Melee"));
		assertEquals(0, magic.defaultTableIndex(Collections.emptyList(), "Melee"));
	}

	@Test
	public void preferredStyleIsHonouredForEveryBundledTask()
	{
		SlayerData data = new SlayerData(new Gson());
		for (SlayerCompanionConfig.CombatStyle s : new SlayerCompanionConfig.CombatStyle[]{
			SlayerCompanionConfig.CombatStyle.MELEE, SlayerCompanionConfig.CombatStyle.RANGED, SlayerCompanionConfig.CombatStyle.MAGIC})
		{
			GearAdvisor advisor = new GearAdvisor(world.resolver, world.owned, style(s));
			for (TaskInfo task : data.tasks())
			{
				List<GearTable> tables = GearAdvisor.tablesFor(task, data.generalGear());
				boolean offered = false;
				for (GearTable t : tables)
				{
					offered |= t.getStyle() != null && t.getStyle().toLowerCase().startsWith(s.name().toLowerCase());
				}
				if (offered)
				{
					String chosen = tables.get(advisor.defaultTableIndex(tables, task.getRecommendedStyle())).getStyle();
					assertTrue(task.getTask() + " shows " + chosen + " for " + s, chosen.toLowerCase().startsWith(s.name().toLowerCase()));
				}
			}
		}
	}

	// --- general tables when the task has none ---

	@Test
	public void taskWithoutTablesUsesTheGeneralOnes()
	{
		GeneralGearFile general = new GeneralGearFile();
		general.setGearTables(Collections.singletonList(MELEE));
		TaskInfo bare = new TaskInfo();
		assertSame(general.getGearTables(), GearAdvisor.tablesFor(bare, general));

		TaskInfo own = new TaskInfo();
		own.setGearTables(Collections.singletonList(table("Ranged")));
		assertSame(own.getGearTables(), GearAdvisor.tablesFor(own, general));

		assertTrue(GearAdvisor.tablesFor(null, general).isEmpty());
		assertTrue(GearAdvisor.tablesFor(bare, new GeneralGearFile()).isEmpty());
	}

	@Test
	public void everyBundledTaskGetsAdviceWithAnEmptyBank()
	{
		SlayerData data = new SlayerData(new Gson());
		assertFalse("general-gear.json should be bundled", data.generalGear().getGearTables().isEmpty());
		GearAdvisor advisor = advisor();
		for (TaskInfo task : data.tasks())
		{
			List<GearTable> tables = GearAdvisor.tablesFor(task, data.generalGear());
			assertFalse(task.getTask() + " has no gear tables at all", tables.isEmpty());
			for (GearTable t : tables)
			{
				for (SlotAdvice a : advisor.advise(t))
				{
					assertNull(a.getOwnedBest());
					assertFalse(task.getTask() + " " + a.getSlot() + " has an empty best tier", a.getWikiBest().isEmpty());
				}
			}
		}
	}
}
