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

import com.slayercompanion.data.MonsterCombatStats;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Expected damage per second against a monster, from the game's accuracy and max hit formulas as
 * the OSRS Wiki documents them, with the equipment effects that matter on Slayer tasks. It is an
 * estimate: no special attacks, no overkill, and the monster's defence is never drained.
 */
public final class DpsCalculator
{
	/** The player is always on task here, so Slayer helmet / black mask bonuses apply. */
	@Value
	public static class Result
	{
		double dps;
		int maxHit;
		double hitChance;
		int speedTicks;
	}

	private static final Result NONE = new Result(0, 0, 0, 4);

	private DpsCalculator()
	{
	}

	public static Result calc(PlayerStats p, Gear g, AttackStyle style, @Nullable Spell spell, MonsterCombatStats m)
	{
		if (style.getType().isMelee())
		{
			return melee(p, g, style, m);
		}
		return style.getType() == AttackStyle.Type.RANGED ? ranged(p, g, style, m) : magic(p, g, style, spell, m);
	}

	// ---------------------------------------------------------------- melee

	private static Result melee(PlayerStats p, Gear g, AttackStyle style, MonsterCombatStats m)
	{
		String weapon = g.name(Gear.WEAPON);
		if (!meleeReaches(m, g.category()))
		{
			return NONE;
		}
		if (m.is("leafy") && !weapon.startsWith("leaf-bladed"))
		{
			return NONE;
		}
		if (!canHurtVampyre(m, weapon))
		{
			return NONE;
		}
		int atkLvl = p.getAttack() + (p.isPotions() ? 5 + p.getAttack() * 15 / 100 : 0);
		int strLvl = p.getStrength() + (p.isPotions() ? 5 + p.getStrength() * 15 / 100 : 0);
		int[] prayer = meleePrayer(p);
		int effAtk = atkLvl * prayer[0] / 100 + stanceAttack(style) + 8;
		int effStr = strLvl * prayer[1] / 100 + stanceStrength(style) + 8;
		if (voidSet(g, "void melee helm"))
		{
			effAtk = effAtk * 11 / 10;
			effStr = effStr * 11 / 10;
		}
		long baseRoll = (long) effAtk * (g.attackBonus(style.getType()) + 64);
		int baseMax = (effStr * (g.strength() + 64) + 320) / 640;
		long roll = baseRoll;
		int max = baseMax;

		String neck = g.name(Gear.NECK);
		if (neck.startsWith("salve amulet") && m.is("undead"))
		{
			boolean enchanted = neck.contains("(e") || neck.contains("(ei)");
			roll = enchanted ? roll * 6 / 5 : roll * 7 / 6;
			max = enchanted ? max * 6 / 5 : max * 7 / 6;
		}
		else if (slayerHelm(g))
		{
			roll = roll * 7 / 6;
			max = max * 7 / 6;
		}
		if (m.is("demon"))
		{
			int bane = weapon.startsWith("arclight") || weapon.startsWith("emberlight") ? 70
				: weapon.startsWith("darklight") || weapon.startsWith("silverlight") ? 60 : 0;
			roll += roll * bane / 100;
			max += max * bane / 100;
		}
		if (m.is("dragon") && weapon.startsWith("dragon hunter lance"))
		{
			roll = roll * 6 / 5;
			max = max * 6 / 5;
		}
		if (m.is("kalphite") && weapon.startsWith("keris"))
		{
			if (weapon.contains("breaching"))
			{
				roll = roll * 133 / 100;
			}
			max = max * 4 / 3;
		}
		if (m.is("leafy") && weapon.startsWith("leaf-bladed battleaxe"))
		{
			max = max * 1175 / 1000;
		}
		if (tzhaarWeapon(weapon) && obsidianSet(g))
		{
			roll += baseRoll / 10;
			max += baseMax / 10;
		}
		if (tzhaarWeapon(weapon) && neck.startsWith("berserker necklace"))
		{
			max = max * 6 / 5;
		}
		if (style.getType() == AttackStyle.Type.CRUSH)
		{
			int inq = (g.name(Gear.HEAD).startsWith("inquisitor's great helm") ? 1 : 0)
				+ (g.name(Gear.BODY).startsWith("inquisitor's hauberk") ? 2 : 0)
				+ (g.name(Gear.LEGS).startsWith("inquisitor's plateskirt") ? 2 : 0);
			roll = roll * (200 + inq) / 200;
			max = max * (200 + inq) / 200;
		}
		long def = (long) (val(m.getDefenceLevel()) + 9) * (defenceBonus(m, style.getType()) + 64);
		double hit = hitChance(roll, def);
		if (weapon.startsWith("osmumten's fang") && style.getType() == AttackStyle.Type.STAB)
		{
			hit = 1 - (1 - hit) * (1 - hit);
		}
		int speed = speed(g, style);
		double perHit = max / 2.0;
		if (weapon.startsWith("scythe of vitur") || weapon.contains("scythe of vitur"))
		{
			int size = val(m.getSize());
			perHit = size >= 3 ? max / 2.0 + (max / 2) / 2.0 + (max / 4) / 2.0 : size == 2 ? max / 2.0 + (max / 2) / 2.0 : perHit;
		}
		return new Result(hit * perHit / (speed * 0.6), max, hit, speed);
	}

	private static int[] meleePrayer(PlayerStats p)
	{
		if (!p.isPrayers())
		{
			return new int[]{100, 100};
		}
		if (p.getPrayer() >= 70 && p.getDefence() >= 70)
		{
			return new int[]{120, 123};
		}
		if (p.getPrayer() >= 60 && p.getDefence() >= 65)
		{
			return new int[]{115, 118};
		}
		int atk = p.getPrayer() >= 34 ? 115 : p.getPrayer() >= 16 ? 110 : p.getPrayer() >= 7 ? 105 : 100;
		int str = p.getPrayer() >= 31 ? 115 : p.getPrayer() >= 13 ? 110 : p.getPrayer() >= 4 ? 105 : 100;
		return new int[]{atk, str};
	}

	/** Prayer names for the assumptions line. */
	public static String prayerName(PlayerStats p, AttackStyle.Type type)
	{
		if (!p.isPrayers())
		{
			return null;
		}
		if (type.isMelee())
		{
			return p.getPrayer() >= 70 && p.getDefence() >= 70 ? "Piety" : p.getPrayer() >= 60 && p.getDefence() >= 65 ? "Chivalry"
				: p.getPrayer() >= 34 ? "Ultimate Strength + Incredible Reflexes" : p.getPrayer() >= 13 ? "the best strength/attack prayers" : null;
		}
		if (type == AttackStyle.Type.RANGED)
		{
			return p.isRigourUnlocked() && p.getPrayer() >= 74 && p.getDefence() >= 70 ? "Rigour" : p.getPrayer() >= 44 ? "Eagle Eye"
				: p.getPrayer() >= 26 ? "Hawk Eye" : p.getPrayer() >= 8 ? "Sharp Eye" : null;
		}
		return p.isAuguryUnlocked() && p.getPrayer() >= 77 && p.getDefence() >= 70 ? "Augury" : p.getPrayer() >= 45 ? "Mystic Might"
			: p.getPrayer() >= 27 ? "Mystic Lore" : p.getPrayer() >= 9 ? "Mystic Will" : null;
	}

	// ---------------------------------------------------------------- ranged

	private static Result ranged(PlayerStats p, Gear g, AttackStyle style, MonsterCombatStats m)
	{
		String weapon = g.name(Gear.WEAPON);
		if (g.firesAmmo() && !g.ammoFits())
		{
			return NONE;
		}
		if (m.is("leafy") && !(g.firesAmmo() && g.name(Gear.AMMO).contains("broad")))
		{
			return NONE;
		}
		if (m.is("vampyre2") || m.is("vampyre3"))
		{
			return NONE;
		}
		int lvl = p.getRanged() + (p.isPotions() ? 4 + p.getRanged() * 10 / 100 : 0);
		int[] prayer = rangedPrayer(p);
		int stance = style.getStance() == AttackStyle.Stance.ACCURATE ? 3 : 0;
		int effAtk = lvl * prayer[0] / 100 + stance + 8;
		int effStr = lvl * prayer[1] / 100 + stance + 8;
		if (voidSet(g, "void ranger helm"))
		{
			effAtk = effAtk * 11 / 10;
			effStr = eliteVoid(g) ? effStr * 9 / 8 : effStr * 11 / 10;
		}
		long roll = (long) effAtk * (g.attackBonus(AttackStyle.Type.RANGED) + 64);
		int max = (effStr * (g.rangedStrength() + 64) + 320) / 640;

		String neck = g.name(Gear.NECK);
		if (neck.startsWith("salve amulet(i)") && m.is("undead"))
		{
			roll = roll * 7 / 6;
			max = max * 7 / 6;
		}
		else if (neck.startsWith("salve amulet(ei)") && m.is("undead"))
		{
			roll = roll * 6 / 5;
			max = max * 6 / 5;
		}
		else if (slayerHelm(g) && imbued(g))
		{
			roll = roll * 23 / 20;
			max = max * 23 / 20;
		}
		if (weapon.startsWith("twisted bow"))
		{
			int magic = Math.min(250, val(m.getMagicLevel()));
			roll = twistedBow(roll, magic, true);
			max = (int) twistedBow(max, magic, false);
		}
		if (m.is("dragon") && weapon.startsWith("dragon hunter crossbow"))
		{
			roll = roll * 13 / 10;
			max = max * 5 / 4;
		}
		if (m.is("demon") && weapon.startsWith("scorching bow"))
		{
			roll += roll * 30 / 100;
			max += max * 30 / 100;
		}
		if (weapon.startsWith("crystal bow") || weapon.startsWith("bow of faerdhinen"))
		{
			int helm = g.name(Gear.HEAD).startsWith("crystal helm") ? 1 : 0;
			int legs = g.name(Gear.LEGS).startsWith("crystal legs") ? 1 : 0;
			int body = g.name(Gear.BODY).startsWith("crystal body") ? 1 : 0;
			roll = roll * (1000 + 50 * helm + 100 * legs + 150 * body) / 1000;
			max = max * (1000 + 25 * helm + 50 * legs + 75 * body) / 1000;
		}
		long def = (long) (val(m.getDefenceLevel()) + 9) * (rangedDefence(m, g.category()) + 64);
		int speed = speed(g, style);
		double hit = hitChance(roll, def);
		return new Result(hit * max / 2.0 / (speed * 0.6), max, hit, speed);
	}

	private static int[] rangedPrayer(PlayerStats p)
	{
		if (!p.isPrayers())
		{
			return new int[]{100, 100};
		}
		if (p.isRigourUnlocked() && p.getPrayer() >= 74 && p.getDefence() >= 70)
		{
			return new int[]{120, 123};
		}
		int f = p.getPrayer() >= 44 ? 115 : p.getPrayer() >= 26 ? 110 : p.getPrayer() >= 8 ? 105 : 100;
		return new int[]{f, f};
	}

	/** Twisted bow accuracy / damage scaling with the target's magic level. */
	static long twistedBow(long current, int magic, boolean accuracy)
	{
		int factor = accuracy ? 10 : 14;
		int base = accuracy ? 140 : 250;
		long t2 = (3L * magic - factor) / 100;
		long x = (3L * magic / 10) - 10L * factor;
		long t3 = x * x / 100;
		long bonus = Math.max(0, Math.min(base, base + t2 - t3));
		return current * bonus / 100;
	}

	// ---------------------------------------------------------------- magic

	private static Result magic(PlayerStats p, Gear g, AttackStyle style, @Nullable Spell spell, MonsterCombatStats m)
	{
		String weapon = g.name(Gear.WEAPON);
		if (m.is("leafy") || m.is("vampyre2") || m.is("vampyre3"))
		{
			return NONE;
		}
		int lvl = p.getMagic() + (p.isPotions() ? 4 : 0);
		boolean powered = spell == null;
		int base = powered ? poweredStaffMax(weapon, lvl) : spell.getMaxHit();
		if (base <= 0)
		{
			return NONE;
		}
		int prayer = magicPrayer(p);
		int effAtk = lvl * prayer / 100 + (powered && style.getStance() == AttackStyle.Stance.ACCURATE ? 2 : 0) + 9;
		if (voidSet(g, "void mage helm"))
		{
			effAtk = effAtk * 29 / 20;
		}
		boolean shadow = weapon.startsWith("tumeken's shadow");
		int magicBonus = g.attackBonus(AttackStyle.Type.MAGIC) * (shadow ? 3 : 1);
		long baseRoll = (long) effAtk * (magicBonus + 64);
		long roll = baseRoll;
		int dmg = g.magicDamageTenths() * (shadow ? 3 : 1);
		if (shadow)
		{
			dmg = Math.min(1000, dmg);
		}
		if (voidSet(g, "void mage helm") && eliteVoid(g))
		{
			dmg += 25;
		}
		String neck = g.name(Gear.NECK);
		boolean helm = false;
		if (neck.startsWith("salve amulet(ei)") && m.is("undead"))
		{
			roll = roll * 120 / 100;
			dmg += 200;
		}
		else if (neck.startsWith("salve amulet(i)") && m.is("undead"))
		{
			roll = roll * 115 / 100;
			dmg += 150;
		}
		else if (slayerHelm(g) && imbued(g))
		{
			helm = true;
		}
		if (helm)
		{
			roll = roll * 23 / 20;
		}
		String element = spell == null ? null : spell.getElement();
		int weakness = element != null && element.equals(m.getWeakness()) ? val(m.getWeaknessPercent()) : 0;
		roll += baseRoll * weakness / 100;

		int max = base + base * dmg / 1000;
		if (helm)
		{
			max = max * 23 / 20;
		}
		max += base * weakness / 100;

		long def = (long) (val(m.getMagicLevel()) + 9) * (val(m.getMagic()) + 64);
		int speed = powered ? speed(g, style) : 5;
		double hit = hitChance(roll, def);
		return new Result(hit * max / 2.0 / (speed * 0.6), max, hit, speed);
	}

	private static int magicPrayer(PlayerStats p)
	{
		if (!p.isPrayers())
		{
			return 100;
		}
		if (p.isAuguryUnlocked() && p.getPrayer() >= 77 && p.getDefence() >= 70)
		{
			return 125;
		}
		return p.getPrayer() >= 45 ? 115 : p.getPrayer() >= 27 ? 110 : p.getPrayer() >= 9 ? 105 : 100;
	}

	/** Built-in spell max hit of a powered staff at this magic level; 0 when the staff is not modelled or uncharged. */
	static int poweredStaffMax(String weapon, int magic)
	{
		if (weapon.startsWith("uncharged") || weapon.contains("(uncharged)"))
		{
			return 0;
		}
		if (weapon.startsWith("trident of the seas"))
		{
			return Math.max(1, magic / 3 - 5);
		}
		if (weapon.startsWith("trident of the swamp"))
		{
			return Math.max(1, magic / 3 - 2);
		}
		if (weapon.startsWith("sanguinesti staff") || weapon.startsWith("holy sanguinesti staff"))
		{
			return Math.max(1, magic / 3);
		}
		if (weapon.startsWith("tumeken's shadow"))
		{
			return Math.max(1, magic / 3 + 1);
		}
		if (weapon.startsWith("accursed sceptre") || weapon.startsWith("eye of ayak"))
		{
			return Math.max(1, magic / 3 - 6);
		}
		if (weapon.startsWith("thammaron's sceptre"))
		{
			return Math.max(1, magic / 3 - 8);
		}
		if (weapon.startsWith("warped sceptre"))
		{
			return Math.max(1, (8 * magic + 96) / 37);
		}
		if (weapon.startsWith("bone staff"))
		{
			return Math.max(1, magic / 3 - 5) + 10;
		}
		if (weapon.startsWith("starter staff"))
		{
			return 8;
		}
		if (weapon.startsWith("crystal staff (basic)") || weapon.startsWith("corrupted staff (basic)"))
		{
			return 23;
		}
		if (weapon.startsWith("crystal staff (attuned)") || weapon.startsWith("corrupted staff (attuned)"))
		{
			return 31;
		}
		if (weapon.startsWith("crystal staff (perfected)") || weapon.startsWith("corrupted staff (perfected)"))
		{
			return 39;
		}
		return 0;
	}

	// ---------------------------------------------------------------- shared

	/** The game's hit chance from the two rolls. */
	static double hitChance(long attack, long defence)
	{
		if (attack > defence)
		{
			return 1 - (defence + 2) / (2.0 * (attack + 1));
		}
		return attack / (2.0 * (defence + 1));
	}

	private static int stanceAttack(AttackStyle s)
	{
		return s.getStance() == AttackStyle.Stance.ACCURATE ? 3 : s.getStance() == AttackStyle.Stance.CONTROLLED ? 1 : 0;
	}

	private static int stanceStrength(AttackStyle s)
	{
		return s.getStance() == AttackStyle.Stance.AGGRESSIVE ? 3 : s.getStance() == AttackStyle.Stance.CONTROLLED ? 1 : 0;
	}

	private static int speed(Gear g, AttackStyle style)
	{
		com.slayercompanion.data.ItemStats w = g.get(Gear.WEAPON);
		int speed = w == null || w.getSpeed() == null ? 4 : w.getSpeed();
		return style.getStance() == AttackStyle.Stance.RAPID ? Math.max(1, speed - 1) : speed;
	}

	private static int defenceBonus(MonsterCombatStats m, AttackStyle.Type type)
	{
		switch (type)
		{
			case STAB:
				return val(m.getStab());
			case SLASH:
				return val(m.getSlash());
			default:
				return val(m.getCrush());
		}
	}

	/** Light for thrown weapons, standard for bows, heavy for crossbows; the old single value when the split is missing. */
	private static int rangedDefence(MonsterCombatStats m, String category)
	{
		Integer split = category.equals("thrown") ? m.getRangedLight() : category.equals("crossbow") ? m.getRangedHeavy() : m.getRangedStandard();
		return split != null ? split : val(m.getRanged());
	}

	private static boolean slayerHelm(Gear g)
	{
		String head = g.name(Gear.HEAD);
		return head.contains("slayer helmet") || head.startsWith("black mask");
	}

	private static boolean imbued(Gear g)
	{
		return g.name(Gear.HEAD).contains("(i)");
	}

	private static boolean voidSet(Gear g, String helm)
	{
		String body = g.name(Gear.BODY);
		String legs = g.name(Gear.LEGS);
		return g.name(Gear.HEAD).startsWith(helm)
			&& (body.startsWith("void knight top") || body.startsWith("elite void top"))
			&& (legs.startsWith("void knight robe") || legs.startsWith("elite void robe"))
			&& g.name(Gear.HANDS).startsWith("void knight gloves");
	}

	private static boolean eliteVoid(Gear g)
	{
		return g.name(Gear.BODY).startsWith("elite void top") && g.name(Gear.LEGS).startsWith("elite void robe");
	}

	private static boolean obsidianSet(Gear g)
	{
		return g.name(Gear.HEAD).startsWith("obsidian helmet") && g.name(Gear.BODY).startsWith("obsidian platebody")
			&& g.name(Gear.LEGS).startsWith("obsidian platelegs");
	}

	private static boolean tzhaarWeapon(String weapon)
	{
		return weapon.startsWith("tzhaar-ket") || weapon.startsWith("toktz-xil") || weapon.startsWith("toktz-mej");
	}

	/**
	 * Flying monsters (aviansies, Kree'arra) and Zulrah can be hit in melee only with a halberd or a
	 * salamander; the kraken and TzKal-Zuk not at all.
	 */
	static boolean meleeReaches(MonsterCombatStats m, String weaponCategory)
	{
		String reach = m.getMeleeReach() != null ? m.getMeleeReach() : m.is("flying") ? "halberd" : null;
		if (reach == null)
		{
			return true;
		}
		return "halberd".equals(reach) && ("polearm".equals(weaponCategory) || "salamander".equals(weaponCategory));
	}

	/** Vyrewatch and similar vampyres only take damage from their special weapons. */
	private static boolean canHurtVampyre(MonsterCombatStats m, String weapon)
	{
		if (!m.is("vampyre2") && !m.is("vampyre3"))
		{
			return true;
		}
		boolean tier3 = weapon.contains("blisterwood") || weapon.contains("ivandis flail") || weapon.contains("sunspear") || weapon.contains("hallowed flail");
		return tier3 || (m.is("vampyre2") && (weapon.contains("silver sickle") || weapon.contains("rod of ivandis")));
	}

	private static int val(@Nullable Integer v)
	{
		return v == null ? 0 : v;
	}
}
