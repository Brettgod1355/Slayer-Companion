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
package com.slayercompanion.dps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.ItemStats;
import com.slayercompanion.data.MonsterCombatStats;
import com.slayercompanion.data.SlayerData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DpsCalculatorTest
{
	private static final SlayerData DATA = new SlayerData(new Gson());

	private static PlayerStats maxed(boolean boosts)
	{
		return PlayerStats.builder().attack(99).strength(99).defence(99).ranged(99).magic(99).prayer(99)
			.potions(boosts).prayers(boosts).rigourUnlocked(true).auguryUnlocked(true).spellbook(Spell.STANDARD).build();
	}

	private static ItemStats item(String name)
	{
		for (ItemStats s : DATA.itemStats().values())
		{
			if (name.equals(s.getName()))
			{
				return s;
			}
		}
		throw new AssertionError("no item " + name);
	}

	private static Gear gear(String... names)
	{
		Map<Integer, ItemStats> m = new HashMap<>();
		for (String n : names)
		{
			ItemStats s = item(n);
			m.put(Gear.slotOf(s.getSlot()), s);
		}
		return new Gear(m);
	}

	private static MonsterCombatStats monster(String task)
	{
		return DATA.task(task).get().mainMonster(null).getCombatStats();
	}

	private static AttackStyle style(String category, String name)
	{
		return WeaponStyles.of(category).stream().filter(s -> s.getName().equals(name)).findFirst().get();
	}

	@Test
	public void meleeMaxHitFollowsTheGameFormula()
	{
		MonsterCombatStats dummy = new MonsterCombatStats();
		dummy.setDefenceLevel(1);
		// 99 Strength, no boosts, Flick (accurate: no strength bonus): (99+8) * (82+64) + 320 over 640 = 24.
		assertEquals(24, DpsCalculator.calc(maxed(false), gear("Abyssal whip"), style("Whip", "Flick"), null, dummy).getMaxHit());
		// Super combat (118) and Piety (floor(118 * 1.23) = 145), controlled +1 +8 = 154: (154 * 146 + 320) / 640 = 35.
		assertEquals(35, DpsCalculator.calc(maxed(true), gear("Abyssal whip"), style("Whip", "Lash"), null, dummy).getMaxHit());
	}

	@Test
	public void hitChanceFormula()
	{
		assertEquals(1 - 102 / (2.0 * 201), DpsCalculator.hitChance(200, 100), 1e-12);
		assertEquals(100 / (2.0 * 201), DpsCalculator.hitChance(100, 200), 1e-12);
	}

	@Test
	public void twistedBowScalesWithMagicLevel()
	{
		// Accuracy reaches its 140% cap; damage at magic 250 is 250 + 7 - 42 = 215%.
		assertEquals(140, DpsCalculator.twistedBow(100, 250, true));
		assertEquals(215, DpsCalculator.twistedBow(100, 250, false));
		// Against magic level 1: 140 - 100 = 40% accuracy, 250 - 196 = 54% damage.
		assertEquals(40, DpsCalculator.twistedBow(100, 1, true));
		assertEquals(54, DpsCalculator.twistedBow(100, 1, false));
	}

	@Test
	public void onTaskSlayerHelmetAndSalvePrecedence()
	{
		MonsterCombatStats undead = new MonsterCombatStats();
		undead.setDefenceLevel(1);
		undead.setAttributes(Collections.singletonList("undead"));
		AttackStyle lash = style("Whip", "Lash");
		int plain = DpsCalculator.calc(maxed(true), gear("Abyssal whip"), lash, null, undead).getMaxHit();
		int helm = DpsCalculator.calc(maxed(true), gear("Abyssal whip", "Slayer helmet (i)"), lash, null, undead).getMaxHit();
		int both = DpsCalculator.calc(maxed(true), gear("Abyssal whip", "Slayer helmet (i)", "Salve amulet(ei)"), lash, null, undead).getMaxHit();
		assertEquals(plain * 7 / 6, helm);
		assertEquals(plain * 6 / 5, both);
	}

	@Test
	public void leafyMonstersNeedLeafBladedWeapons()
	{
		MonsterCombatStats kurask = monster("Kurask");
		assertTrue(kurask.is("leafy"));
		assertEquals(0, DpsCalculator.calc(maxed(true), gear("Abyssal whip"), style("Whip", "Lash"), null, kurask).getDps(), 0);
		assertTrue(DpsCalculator.calc(maxed(true), gear("Leaf-bladed battleaxe"), style("Axe", "Hack"), null, kurask).getDps() > 0);
	}

	@Test
	public void poweredStaffMaxHits()
	{
		assertEquals(28, DpsCalculator.poweredStaffMax("trident of the seas", 99));
		assertEquals(31, DpsCalculator.poweredStaffMax("trident of the swamp", 99));
		assertEquals(34, DpsCalculator.poweredStaffMax("tumeken's shadow", 99));
		assertEquals(0, DpsCalculator.poweredStaffMax("uncharged trident", 99));
	}

	@Test
	public void optimizerPicksTheBestOwnedGearPerStyle()
	{
		List<ItemStats> owned = new ArrayList<>();
		for (String n : Arrays.asList("Abyssal whip", "Abyssal tentacle", "Rune scimitar", "Slayer helmet (i)", "Amulet of fury",
			"Fighter torso", "Dragon defender", "Barrows gloves", "Dragon boots", "Berserker ring", "Rune crossbow",
			"Adamant bolts", "Rune arrow", "Magic shortbow", "Trident of the Swamp", "Occult necklace"))
		{
			owned.add(item(n));
		}
		List<LoadoutOptimizer.Option> best = LoadoutOptimizer.best(maxed(true), owned, monster("Abyssal demons"));
		assertEquals(3, best.size());
		LoadoutOptimizer.Option melee = best.stream().filter(o -> o.getKind() == LoadoutOptimizer.Kind.MELEE).findFirst().get();
		assertEquals("Abyssal tentacle", melee.getGear().get(Gear.WEAPON).getName());
		assertEquals("Slayer helmet (i)", melee.getGear().get(Gear.HEAD).getName());
		assertEquals("Dragon defender", melee.getGear().get(Gear.SHIELD).getName());
		LoadoutOptimizer.Option ranged = best.stream().filter(o -> o.getKind() == LoadoutOptimizer.Kind.RANGED).findFirst().get();
		ItemStats ammo = ranged.getGear().get(Gear.AMMO);
		assertNotNull(ammo);
		String weapon = ranged.getGear().get(Gear.WEAPON).getName();
		assertTrue(weapon + " with " + ammo.getName(), weapon.equals("Rune crossbow") ? ammo.getName().contains("bolt") : ammo.getName().contains("arrow"));
		LoadoutOptimizer.Option magic = best.stream().filter(o -> o.getKind() == LoadoutOptimizer.Kind.MAGIC).findFirst().get();
		assertEquals("Trident of the Swamp", magic.getGear().get(Gear.WEAPON).getName());
		assertEquals("Occult necklace", magic.getGear().get(Gear.NECK).getName());
	}

	@Test
	public void meleeCannotReachFlyingOrOutOfReachMonsters()
	{
		MonsterCombatStats aviansie = DATA.task("Aviansies").get().monster("Aviansie").getCombatStats();
		assertTrue(aviansie.is("flying"));
		AttackStyle slash = WeaponStyles.of("Whip").get(1);
		assertEquals(0, DpsCalculator.calc(maxed(true), gear("Abyssal whip"), slash, null, aviansie).getDps(), 0);
		AttackStyle halberd = WeaponStyles.of("Polearm").get(1);
		assertTrue(DpsCalculator.calc(maxed(true), gear("Dragon halberd"), halberd, null, aviansie).getDps() > 0);
		// The kraken: not even a halberd.
		MonsterCombatStats kraken = monster("The Cave Kraken Boss");
		assertEquals("none", kraken.getMeleeReach());
		assertEquals(0, DpsCalculator.calc(maxed(true), gear("Dragon halberd"), halberd, null, kraken).getDps(), 0);
	}

	@Test
	public void optimizerKeepsTheShieldADragonTaskNeeds()
	{
		List<ItemStats> owned = new ArrayList<>();
		for (String n : Arrays.asList("Abyssal whip", "Dragon defender", "Anti-dragon shield", "Scythe of Vitur", "Slayer helmet (i)"))
		{
			owned.add(item(n));
		}
		Map<Integer, java.util.function.Predicate<ItemStats>> required = new HashMap<>();
		com.slayercompanion.data.RequiredGear rule = DATA.task("Black dragons").get().requiredGearOrEmpty().get(0);
		required.put(Gear.SHIELD, s -> rule.accepts(s.getName()));
		List<LoadoutOptimizer.Option> best = LoadoutOptimizer.best(maxed(true), owned, monster("Black dragons"), required);
		LoadoutOptimizer.Option melee = best.get(0);
		assertEquals("Anti-dragon shield", melee.getGear().get(Gear.SHIELD).getName());
		// The two-handed scythe would empty the shield slot, so it is not used.
		assertEquals("Abyssal whip", melee.getGear().get(Gear.WEAPON).getName());
		// Without the rule the defender wins the slot.
		LoadoutOptimizer.Option free = LoadoutOptimizer.best(maxed(true), owned, monster("Black dragons")).get(0);
		assertTrue(free.getGear().get(Gear.SHIELD) == null || !"Anti-dragon shield".equals(free.getGear().get(Gear.SHIELD).getName()));
	}

	@Test
	public void aBowKeepsItsArrowsWhenTheBankIsFullOfBolts()
	{
		List<ItemStats> owned = new ArrayList<>();
		for (String n : Arrays.asList("Twisted bow", "Dragon arrow", "Runite bolts", "Dragon bolts", "Adamant bolts",
			"Diamond bolts (e)", "Ruby bolts (e)", "Rune crossbow"))
		{
			owned.add(item(n));
		}
		LoadoutOptimizer.Option ranged = LoadoutOptimizer.best(maxed(true), owned, monster("Hydras")).stream()
			.filter(o -> o.getKind() == LoadoutOptimizer.Kind.RANGED).findFirst().orElseThrow(AssertionError::new);
		assertEquals("Twisted bow", ranged.getGear().get(Gear.WEAPON).getName());
		assertEquals("Dragon arrow", ranged.getGear().get(Gear.AMMO).getName());
	}

	@Test
	public void requiredGearRulesMatchVariantsAndItemVersions()
	{
		com.slayercompanion.data.RequiredGear shield = DATA.task("Black dragons").get().requiredGearOrEmpty().get(0);
		assertTrue(shield.appliesTo("Black dragon", false));
		assertTrue(!shield.appliesTo("Baby black dragon", false));
		assertTrue(shield.accepts("Dragonfire shield (uncharged)"));
		assertTrue(!shield.accepts("Dragon defender"));
		com.slayercompanion.data.RequiredGear boots = DATA.task("Hydras").get().requiredGearOrEmpty().get(0);
		assertTrue(boots.appliesTo("Hydra", false));
		assertTrue(!boots.appliesTo("Hydra", true));
		com.slayercompanion.data.RequiredGear head = DATA.task("Banshees").get().requiredGearOrEmpty().get(0);
		assertTrue(head.accepts("Black slayer helmet (i)"));
		assertTrue(head.accepts("Earmuffs"));
		assertTrue(!head.accepts("Neitiznot faceguard"));
	}

	@Test
	public void optimizerIsQuickOnABigBank()
	{
		List<ItemStats> owned = new ArrayList<>(new java.util.LinkedHashSet<>(DATA.itemStats().values())).subList(0, 1500);
		long start = System.nanoTime();
		LoadoutOptimizer.best(maxed(true), owned, monster("Abyssal demons"));
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("optimizer on 1500 owned items: " + ms + " ms");
		assertTrue("took " + ms + " ms", ms < 2_000);
	}
}
