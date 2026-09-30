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

import com.google.gson.Gson;
import com.slayercompanion.data.ItemStats;
import com.slayercompanion.data.MonsterCombatStats;
import com.slayercompanion.data.SlayerData;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Worked examples of the OSRS Wiki's formulas (Maximum magic hit, Maximum melee hit, Prayer,
 * Osmumten's fang, Colossal blade, Keris, Diamond / Ruby bolts (e)), and the two accuracy formulas
 * against brute force.
 */
public class DpsFormulaTest
{
	private static final SlayerData DATA = new SlayerData(new Gson());

	private static PlayerStats.PlayerStatsBuilder maxed()
	{
		return PlayerStats.builder().attack(99).strength(99).defence(99).ranged(99).magic(99).prayer(99)
			.potions(true).prayers(true).spellbook(Spell.STANDARD);
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

	private static MonsterCombatStats dummy(int size, String... attributes)
	{
		MonsterCombatStats m = new MonsterCombatStats();
		m.setDefenceLevel(1);
		m.setMagicLevel(1);
		m.setHitpoints(250);
		m.setSize(size);
		m.setAttributes(java.util.Arrays.asList(attributes));
		return m;
	}

	private static AttackStyle style(String category, String name)
	{
		return WeaponStyles.of(category).stream().filter(s -> s.getName().equals(name)).findFirst().get();
	}

	private static AttackStyle autocast()
	{
		return new AttackStyle("Autocast", AttackStyle.Type.MAGIC, AttackStyle.Stance.AUTOCAST);
	}

	private static AttackStyle poweredAccurate()
	{
		return new AttackStyle("Accurate", AttackStyle.Type.MAGIC, AttackStyle.Stance.ACCURATE);
	}

	@Test
	public void magicPrayersAddMagicDamage()
	{
		// Tumeken's shadow at 99 + magic potion (103): floor(103 / 3 + 1) = 35 before bonuses; its own
		// damage bonus is tripled. Augury adds 4%, Mystic Vigour 3%, Mystic Might 2% (Maximum magic hit).
		Gear shadow = gear("Tumeken's shadow");
		int gearTenths = Math.min(1000, shadow.magicDamageTenths() * 3);
		assertEquals(35 + 35 * gearTenths / 1000,
			DpsCalculator.calc(maxed().prayers(false).build(), shadow, poweredAccurate(), null, dummy(1)).getMaxHit());
		assertEquals(35 + 35 * (gearTenths + 40) / 1000,
			DpsCalculator.calc(maxed().auguryUnlocked(true).build(), shadow, poweredAccurate(), null, dummy(1)).getMaxHit());
		assertEquals(35 + 35 * (gearTenths + 30) / 1000,
			DpsCalculator.calc(maxed().mysticVigourUnlocked(true).build(), shadow, poweredAccurate(), null, dummy(1)).getMaxHit());
		assertEquals(35 + 35 * (gearTenths + 20) / 1000,
			DpsCalculator.calc(maxed().build(), shadow, poweredAccurate(), null, dummy(1)).getMaxHit());
		assertEquals("Mystic Vigour", DpsCalculator.prayerName(maxed().mysticVigourUnlocked(true).build(), AttackStyle.Type.MAGIC));
	}

	@Test
	public void eliteVoidAddsFivePercentMagicDamage()
	{
		// Trident of the swamp at 103 Magic: floor(103 / 3) - 2 = 32; elite void +5%, no prayer: floor(32 * 1.05) = 33.
		Gear g = gear("Trident of the Swamp", "Void mage helm", "Elite void top", "Elite void robe", "Void knight gloves");
		assertEquals(32 + 32 * (g.magicDamageTenths() + 50) / 1000,
			DpsCalculator.calc(maxed().prayers(false).build(), g, poweredAccurate(), null, dummy(1)).getMaxHit());
	}

	@Test
	public void elementalWeaknessComesBeforeTheSlayerHelmet()
	{
		// Fire Surge (24) vs a 40% fire-weak monster on task: floor((24 + floor(24 * 0.4)) * 1.15) = floor(33 * 1.15) = 37.
		MonsterCombatStats weak = dummy(1);
		weak.setWeakness("fire");
		weak.setWeaknessPercent(40);
		Spell surge = Spell.best(Spell.STANDARD, 99);
		assertEquals("Fire Surge", surge.getName());
		Gear g = gear("Slayer helmet (i)");
		int max = DpsCalculator.calc(maxed().prayers(false).build(), g, autocast(), surge, weak).getMaxHit();
		assertEquals(((24 + 24 * g.magicDamageTenths() / 1000) + 9) * 115 / 100, max);
	}

	@Test
	public void magicEffectiveLevelFollowsTheWikiOrder()
	{
		// 103 Magic with Mystic Might: floor(103 * 1.15) = 118; a spell adds 8 (Damage per second/Magic).
		MonsterCombatStats target = dummy(1);
		target.setMagicLevel(200);
		target.setMagic(50);
		long defence = (200 + 9) * (50 + 64);
		Spell surge = Spell.best(Spell.STANDARD, 99);
		Gear none = new Gear(new HashMap<>());
		assertEquals(DpsCalculator.hitChance((118 + 8) * 64L, defence),
			DpsCalculator.calc(maxed().build(), none, autocast(), surge, target).getHitChance(), 1e-12);
		// Void multiplies before the +8: floor(118 * 1.45) + 8 = 179.
		Gear voidMage = gear("Void mage helm", "Void knight top", "Void knight robe", "Void knight gloves");
		assertEquals(DpsCalculator.hitChance(179L * (voidMage.attackBonus(AttackStyle.Type.MAGIC) + 64), defence),
			DpsCalculator.calc(maxed().build(), voidMage, autocast(), surge, target).getHitChance(), 1e-12);
		// A powered staff on Accurate adds 3: 118 + 3 + 8.
		Gear shadowless = gear("Trident of the Swamp");
		assertEquals(DpsCalculator.hitChance(129L * (shadowless.attackBonus(AttackStyle.Type.MAGIC) + 64), defence),
			DpsCalculator.calc(maxed().build(), shadowless, poweredAccurate(), null, target).getHitChance(), 1e-12);
	}

	@Test
	public void aMagicHitThatRollsZeroDealsOne()
	{
		Spell surge = Spell.best(Spell.STANDARD, 99);
		DpsCalculator.Result r = DpsCalculator.calc(maxed().build(), new Gear(new HashMap<>()), autocast(), surge, dummy(1));
		assertEquals(r.getHitChance() * (r.getMaxHit() / 2.0 + 1.0 / (r.getMaxHit() + 1)) / 3.0, r.getDps(), 1e-12);
	}

	@Test
	public void deadeyeIsEighteenPercent()
	{
		PlayerStats deadeye = maxed().potions(false).deadeyeUnlocked(true).build();
		assertEquals("Deadeye", DpsCalculator.prayerName(deadeye, AttackStyle.Type.RANGED));
		// 99 Ranged with Deadeye: floor(99 * 1.18) = 116, rapid adds nothing, + 8 = 124.
		Gear g = gear("Magic shortbow", "Rune arrow");
		assertEquals((124 * (g.rangedStrength() + 64) + 320) / 640,
			DpsCalculator.calc(deadeye, g, style("Bow", "Rapid"), null, dummy(1)).getMaxHit());
		assertEquals("Rigour wins when unlocked", "Rigour",
			DpsCalculator.prayerName(maxed().rigourUnlocked(true).deadeyeUnlocked(true).build(), AttackStyle.Type.RANGED));
		assertEquals("Eagle Eye without the scroll", "Eagle Eye", DpsCalculator.prayerName(maxed().build(), AttackStyle.Type.RANGED));
	}

	@Test
	public void fangAccuracyMatchesTwoRollsAgainstOneDefenceRoll()
	{
		for (int[] ad : new int[][]{{5, 7}, {9, 4}, {6, 6}, {0, 3}, {12, 30}})
		{
			int a = ad[0];
			int d = ad[1];
			long wins = 0;
			for (int r1 = 0; r1 <= a; r1++)
			{
				for (int r2 = 0; r2 <= a; r2++)
				{
					for (int def = 0; def <= d; def++)
					{
						if (Math.max(r1, r2) > def)
						{
							wins++;
						}
					}
				}
			}
			double exact = wins / (double) ((a + 1) * (a + 1) * (d + 1));
			assertEquals("a=" + a + " d=" + d, exact, DpsCalculator.fangHitChance(a, d), 1e-12);
		}
	}

	@Test
	public void hitChanceMatchesOneRollAgainstOneDefenceRoll()
	{
		for (int[] ad : new int[][]{{5, 7}, {9, 4}, {6, 6}, {0, 3}, {30, 12}})
		{
			long wins = 0;
			for (int r = 0; r <= ad[0]; r++)
			{
				for (int def = 0; def <= ad[1]; def++)
				{
					if (r > def)
					{
						wins++;
					}
				}
			}
			assertEquals(wins / (double) ((ad[0] + 1) * (ad[1] + 1)), DpsCalculator.hitChance(ad[0], ad[1]), 1e-12);
		}
	}

	@Test
	public void colossalBladeAddsTwoPerTileOfSizeUpToTen()
	{
		Gear blade = gear("Colossal blade");
		AttackStyle hack = style("2h Sword", "Slash");
		int small = DpsCalculator.calc(maxed().build(), blade, hack, null, dummy(1)).getMaxHit();
		int large = DpsCalculator.calc(maxed().build(), blade, hack, null, dummy(5)).getMaxHit();
		int huge = DpsCalculator.calc(maxed().build(), blade, hack, null, dummy(7)).getMaxHit();
		assertEquals(10 - 2, large - small);
		assertEquals("capped at size 5", large, huge);
	}

	@Test
	public void kerisHitsThirtyThreePercentHarderOnKalphites()
	{
		Gear keris = gear("Keris partisan");
		AttackStyle lunge = style("Partisan", "Lunge");
		int plain = DpsCalculator.calc(maxed().build(), keris, lunge, null, dummy(1)).getMaxHit();
		int kalphite = DpsCalculator.calc(maxed().build(), keris, lunge, null, dummy(1, "kalphite")).getMaxHit();
		assertEquals(plain * 133 / 100, kalphite);
	}

	@Test
	public void berserkerNecklaceAndObsidianArmourAddUp()
	{
		// Tzhaar-ket-om, Pummel (aggressive +3), super combat + Piety: floor(118 * 1.23) + 3 + 8 = 156.
		AttackStyle pummel = style("Blunt", "Pummel");
		Gear all = gear("Tzhaar-ket-om", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs", "Berserker necklace");
		int base = (156 * (all.strength() + 64) + 320) / 640;
		assertEquals("+10% armour and +20% necklace add up to 1.3", base + base * 30 / 100,
			DpsCalculator.calc(maxed().build(), all, pummel, null, dummy(1)).getMaxHit());
		Gear necklace = gear("Tzhaar-ket-om", "Berserker necklace");
		int base2 = (156 * (necklace.strength() + 64) + 320) / 640;
		assertEquals(base2 + base2 * 20 / 100, DpsCalculator.calc(maxed().build(), necklace, pummel, null, dummy(1)).getMaxHit());
	}

	@Test
	public void enchantedBoltsAddTheirProcs()
	{
		MonsterCombatStats target = dummy(1);
		target.setDefenceLevel(300);
		target.setRangedHeavy(200);
		AttackStyle rapid = style("Crossbow", "Rapid");
		PlayerStats p = maxed().rigourUnlocked(true).build();

		DpsCalculator.Result diamond = DpsCalculator.calc(p, gear("Rune crossbow", "Diamond bolts (e)"), rapid, null, target);
		double expected = (0.1 * (diamond.getMaxHit() * 115 / 100) / 2.0 + 0.9 * diamond.getHitChance() * diamond.getMaxHit() / 2.0)
			/ (diamond.getSpeedTicks() * 0.6);
		assertEquals(expected, diamond.getDps(), 1e-9);

		DpsCalculator.Result ruby = DpsCalculator.calc(p, gear("Rune crossbow", "Ruby bolts (e)"), rapid, null, target);
		expected = (0.06 * 50 + 0.94 * ruby.getHitChance() * ruby.getMaxHit() / 2.0) / (ruby.getSpeedTicks() * 0.6);
		assertEquals("20% of 250 hitpoints", expected, ruby.getDps(), 1e-9);

		target.setHitpoints(2000);
		DpsCalculator.Result capped = DpsCalculator.calc(p, gear("Rune crossbow", "Ruby bolts (e)"), rapid, null, target);
		assertEquals((0.06 * 100 + 0.94 * capped.getHitChance() * capped.getMaxHit() / 2.0) / (capped.getSpeedTicks() * 0.6),
			capped.getDps(), 1e-9);
	}

	@Test
	public void bowsThatMakeTheirOwnShotsIgnoreTheAmmoSlot()
	{
		for (String bow : new String[]{"Webweaver bow", "Craw's bow", "Venator bow", "Bow of Faerdhinen", "Crystal bow"})
		{
			Gear g = gear(bow, "Dragon arrow");
			assertEquals(bow, false, g.firesAmmo());
			assertEquals(bow + " keeps only its own ranged strength", item(bow).getRangedStr(), g.rangedStrength());
		}
		Gear tbow = gear("Twisted bow", "Dragon arrow");
		assertEquals(item("Twisted bow").getRangedStr() + item("Dragon arrow").getRangedStr(), tbow.rangedStrength());
	}

	@Test
	public void sanguinestiStaffStartsAtTwentySevenAtEightyTwoMagic()
	{
		assertEquals(27, DpsCalculator.poweredStaffMax("sanguinesti staff", 82));
		assertEquals(34, DpsCalculator.poweredStaffMax("tumeken's shadow", 99));
	}
}
