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

import com.slayercompanion.data.ItemStats;
import com.slayercompanion.data.MonsterCombatStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Picks the highest-DPS gear from the items the player owns, per combat type. The strongest few
 * weapons of each type (scored alone, bows with their best arrows) are tried with each of their
 * styles; for each, the other slots are filled one at a time with whatever raises DPS most,
 * repeating until nothing improves. Only the strongest few items per slot for the style are tried,
 * plus every item with a special effect (Slayer helmet, salve, void, crystal, obsidian, ...); void
 * sets are also tried as a starting point because no single void piece helps on its own. Slots
 * where nothing changes DPS stay empty. Slots the task needs something in (an anti-dragon shield
 * against dragons) only take those items, and start filled with one.
 */
public final class LoadoutOptimizer
{
	public enum Kind
	{
		MELEE, RANGED, MAGIC
	}

	@Value
	public static class Option
	{
		Kind kind;
		/** Equipment slot index to item stats. */
		Map<Integer, ItemStats> gear;
		AttackStyle style;
		@Nullable
		Spell spell;
		DpsCalculator.Result result;
	}

	/** Slots filled after the weapon; ammo first so bows can be scored at all. */
	private static final int[] FILL_ORDER = {Gear.AMMO, Gear.HEAD, Gear.NECK, Gear.BODY, Gear.LEGS, Gear.HANDS,
		Gear.FEET, Gear.CAPE, Gear.RING, Gear.SHIELD};
	private static final int MAX_PASSES = 4;
	/** Weapon-and-style pairs climbed per combat type. */
	private static final int WEAPONS_PER_KIND = 6;
	/** Items per slot kept for each of the two bonuses that matter for a style (see {@link #shortlist}). */
	private static final int ITEMS_PER_BONUS = 5;
	/** Items whose value is a special effect rather than their bonuses; never pruned. */
	private static final String[] SPECIAL = {"slayer helmet", "black mask", "salve amulet", "void ", "elite void",
		"crystal helm", "crystal body", "crystal legs", "obsidian", "inquisitor's", "berserker necklace"};

	private LoadoutOptimizer()
	{
	}

	/** The best option per combat type, highest DPS first; empty when nothing owned can hurt the monster. */
	public static List<Option> best(PlayerStats p, Collection<ItemStats> owned, MonsterCombatStats monster)
	{
		return best(p, owned, monster, Collections.emptyMap());
	}

	/**
	 * As {@link #best(PlayerStats, Collection, MonsterCombatStats)}, with {@code required} slots only
	 * taking items the predicate accepts (left empty when the player owns none). Two-handed weapons
	 * are skipped when the shield slot is required.
	 */
	public static List<Option> best(PlayerStats p, Collection<ItemStats> owned, MonsterCombatStats monster,
		Map<Integer, Predicate<ItemStats>> required)
	{
		Map<Integer, List<ItemStats>> bySlot = new HashMap<>();
		for (ItemStats s : new LinkedHashSet<>(owned))
		{
			int slot = Gear.slotOf(s.getSlot());
			if (slot >= 0)
			{
				bySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(s);
			}
		}
		for (Map.Entry<Integer, Predicate<ItemStats>> r : required.entrySet())
		{
			List<ItemStats> allowed = new ArrayList<>();
			for (ItemStats s : bySlot.getOrDefault(r.getKey(), Collections.emptyList()))
			{
				if (r.getValue().test(s))
				{
					allowed.add(s);
				}
			}
			bySlot.put(r.getKey(), allowed);
		}
		boolean shieldRequired = required.containsKey(Gear.SHIELD);
		// Score every weapon and style alone (bows with their best ammo), keep the strongest per kind.
		Map<Kind, List<Object[]>> candidates = new EnumMap<>(Kind.class);
		for (ItemStats weapon : bySlot.getOrDefault(Gear.WEAPON, new ArrayList<>()))
		{
			if (weapon.getName() == null || weapon.getName().toLowerCase(Locale.ROOT).contains("uncharged")
				|| (shieldRequired && weapon.isTwoHanded()))
			{
				continue;
			}
			for (Map.Entry<AttackStyle, Spell> style : styles(p, weapon).entrySet())
			{
				Gear alone = withBestAmmo(new Gear(new HashMap<>()).with(Gear.WEAPON, weapon), bySlot);
				double dps = DpsCalculator.calc(p, alone, style.getKey(), style.getValue(), monster).getDps();
				if (dps > 0)
				{
					candidates.computeIfAbsent(kind(style.getKey()), k -> new ArrayList<>())
						.add(new Object[]{weapon, style.getKey(), style.getValue(), dps});
				}
			}
		}
		Map<Kind, Option> best = new EnumMap<>(Kind.class);
		for (List<Object[]> list : candidates.values())
		{
			list.sort(Comparator.comparingDouble((Object[] c) -> (double) c[3]).reversed());
			for (Object[] c : list.subList(0, Math.min(WEAPONS_PER_KIND, list.size())))
			{
				ItemStats weapon = (ItemStats) c[0];
				AttackStyle style = (AttackStyle) c[1];
				Spell spell = (Spell) c[2];
				Map<Integer, List<ItemStats>> shortlist = shortlist(bySlot, style, required.keySet());
				for (Gear seed : seeds(weapon, bySlot, required.keySet()))
				{
					Option o = climb(p, seed, style, spell, shortlist, monster);
					if (o.getResult().getDps() <= 0)
					{
						continue;
					}
					Kind kind = kind(style);
					Option current = best.get(kind);
					if (current == null || o.getResult().getDps() > current.getResult().getDps())
					{
						best.put(kind, o);
					}
				}
			}
		}
		List<Option> out = new ArrayList<>(best.values());
		out.sort(Comparator.comparingDouble((Option o) -> o.getResult().getDps()).reversed());
		return out;
	}

	/**
	 * The strongest few items per slot for the style's two bonuses, plus every special-effect item;
	 * required slots keep everything they may take.
	 */
	private static Map<Integer, List<ItemStats>> shortlist(Map<Integer, List<ItemStats>> bySlot, AttackStyle style, Set<Integer> required)
	{
		Map<Integer, List<ItemStats>> out = new HashMap<>();
		for (Map.Entry<Integer, List<ItemStats>> e : bySlot.entrySet())
		{
			if (e.getKey() == Gear.WEAPON)
			{
				continue;
			}
			if (required.contains(e.getKey()))
			{
				out.put(e.getKey(), e.getValue());
				continue;
			}
			Set<ItemStats> keep = new LinkedHashSet<>();
			for (ItemStats s : e.getValue())
			{
				String name = s.getName() == null ? "" : s.getName().toLowerCase(Locale.ROOT);
				for (String special : SPECIAL)
				{
					if (name.startsWith(special) || name.contains(special))
					{
						keep.add(s);
					}
				}
			}
			keep.addAll(top(e.getValue(), s -> accuracy(s, style.getType())));
			keep.addAll(top(e.getValue(), s -> damage(s, style.getType())));
			out.put(e.getKey(), new ArrayList<>(keep));
		}
		return out;
	}

	private static List<ItemStats> top(List<ItemStats> items, java.util.function.ToDoubleFunction<ItemStats> score)
	{
		List<ItemStats> sorted = new ArrayList<>(items);
		sorted.sort(Comparator.comparingDouble(score).reversed());
		List<ItemStats> out = new ArrayList<>();
		for (ItemStats s : sorted)
		{
			if (out.size() >= ITEMS_PER_BONUS || score.applyAsDouble(s) <= 0)
			{
				break;
			}
			out.add(s);
		}
		return out;
	}

	private static double accuracy(ItemStats s, AttackStyle.Type type)
	{
		switch (type)
		{
			case STAB:
				return s.getStab();
			case SLASH:
				return s.getSlash();
			case CRUSH:
				return s.getCrush();
			case RANGED:
				return s.getRanged();
			default:
				return s.getMagic();
		}
	}

	private static double damage(ItemStats s, AttackStyle.Type type)
	{
		return type.isMelee() ? s.getStr() : type == AttackStyle.Type.RANGED ? s.getRangedStr() : s.getMagicDmg();
	}

	/** A bow or crossbow with the highest ranged-strength ammo it can fire; other weapons unchanged. */
	private static Gear withBestAmmo(Gear gear, Map<Integer, List<ItemStats>> bySlot)
	{
		if (!gear.firesAmmo())
		{
			return gear;
		}
		Gear best = gear;
		for (ItemStats ammo : bySlot.getOrDefault(Gear.AMMO, new ArrayList<>()))
		{
			Gear g = gear.with(Gear.AMMO, ammo);
			if (g.ammoFits() && (best.get(Gear.AMMO) == null || ammo.getRangedStr() > best.get(Gear.AMMO).getRangedStr()))
			{
				best = g;
			}
		}
		return best;
	}

	/** DPS of the given worn gear with its best style (and autocast spell, if any); null without a weapon style. */
	@Nullable
	public static Option evaluate(PlayerStats p, Map<Integer, ItemStats> worn, MonsterCombatStats monster)
	{
		Gear gear = new Gear(worn);
		ItemStats weapon = gear.get(Gear.WEAPON);
		Map<AttackStyle, Spell> styles = weapon == null
			? stylesOf(p, "Unarmed") : styles(p, weapon);
		Option best = null;
		for (Map.Entry<AttackStyle, Spell> s : styles.entrySet())
		{
			DpsCalculator.Result r = DpsCalculator.calc(p, gear, s.getKey(), s.getValue(), monster);
			if (best == null || r.getDps() > best.getResult().getDps())
			{
				best = new Option(kind(s.getKey()), gear.items(), s.getKey(), s.getValue(), r);
			}
		}
		return best;
	}

	private static Option climb(PlayerStats p, Gear start, AttackStyle style, @Nullable Spell spell,
		Map<Integer, List<ItemStats>> bySlot, MonsterCombatStats monster)
	{
		Gear gear = start;
		double dps = DpsCalculator.calc(p, gear, style, spell, monster).getDps();
		for (int pass = 0; pass < MAX_PASSES; pass++)
		{
			boolean improved = false;
			for (int slot : FILL_ORDER)
			{
				for (ItemStats candidate : bySlot.getOrDefault(slot, new ArrayList<>()))
				{
					Gear next = gear.with(slot, candidate);
					if (next == gear)
					{
						continue;
					}
					double d = DpsCalculator.calc(p, next, style, spell, monster).getDps();
					if (d > dps + 1e-9)
					{
						gear = next;
						dps = d;
						improved = true;
					}
				}
			}
			if (!improved)
			{
				break;
			}
		}
		return new Option(kind(style), gear.items(), style, spell, DpsCalculator.calc(p, gear, style, spell, monster));
	}

	/**
	 * The weapon alone, plus the weapon with each complete void set the player owns; every seed
	 * starts with the first item owned for each required slot.
	 */
	private static List<Gear> seeds(ItemStats weapon, Map<Integer, List<ItemStats>> bySlot, Set<Integer> required)
	{
		List<Gear> seeds = new ArrayList<>();
		for (Gear g : voidSeeds(weapon, bySlot))
		{
			for (int slot : required)
			{
				List<ItemStats> allowed = bySlot.getOrDefault(slot, Collections.emptyList());
				if (slot != Gear.WEAPON && !allowed.isEmpty())
				{
					g = g.with(slot, allowed.get(0));
				}
			}
			seeds.add(g);
		}
		return seeds;
	}

	private static List<Gear> voidSeeds(ItemStats weapon, Map<Integer, List<ItemStats>> bySlot)
	{
		List<Gear> seeds = new ArrayList<>();
		Gear bare = new Gear(new HashMap<>()).with(Gear.WEAPON, weapon);
		seeds.add(bare);
		for (String helm : new String[]{"void melee helm", "void ranger helm", "void mage helm"})
		{
			for (boolean elite : new boolean[]{false, true})
			{
				ItemStats h = find(bySlot, Gear.HEAD, helm);
				ItemStats top = find(bySlot, Gear.BODY, elite ? "elite void top" : "void knight top");
				ItemStats robe = find(bySlot, Gear.LEGS, elite ? "elite void robe" : "void knight robe");
				ItemStats gloves = find(bySlot, Gear.HANDS, "void knight gloves");
				if (h != null && top != null && robe != null && gloves != null)
				{
					seeds.add(bare.with(Gear.HEAD, h).with(Gear.BODY, top).with(Gear.LEGS, robe).with(Gear.HANDS, gloves));
				}
			}
		}
		return seeds;
	}

	@Nullable
	private static ItemStats find(Map<Integer, List<ItemStats>> bySlot, int slot, String prefix)
	{
		for (ItemStats s : bySlot.getOrDefault(slot, new ArrayList<>()))
		{
			if (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).startsWith(prefix))
			{
				return s;
			}
		}
		return null;
	}

	/** Every style of the weapon; autocasting staves also get the best spell of the player's spellbook. */
	private static Map<AttackStyle, Spell> styles(PlayerStats p, ItemStats weapon)
	{
		Map<AttackStyle, Spell> out = stylesOf(p, weapon.getCategory());
		if (WeaponStyles.canAutocast(weapon.getCategory()))
		{
			Spell spell = Spell.best(p.getSpellbook(), p.getMagic() + (p.isPotions() ? 4 : 0));
			if (spell != null)
			{
				out.put(new AttackStyle("Autocast " + spell.getName(), AttackStyle.Type.MAGIC, AttackStyle.Stance.AUTOCAST), spell);
			}
		}
		return out;
	}

	private static Map<AttackStyle, Spell> stylesOf(PlayerStats p, @Nullable String category)
	{
		Map<AttackStyle, Spell> out = new LinkedHashMap<>();
		for (AttackStyle s : WeaponStyles.of(category))
		{
			if (s.getStance() != AttackStyle.Stance.DEFENSIVE && s.getStance() != AttackStyle.Stance.LONGRANGE)
			{
				out.put(s, null);
			}
		}
		return out;
	}

	private static Kind kind(AttackStyle style)
	{
		return style.getType().isMelee() ? Kind.MELEE : style.getType() == AttackStyle.Type.RANGED ? Kind.RANGED : Kind.MAGIC;
	}

	/** Slot index to item id, for saving an option as a loadout. */
	public static Map<Integer, Integer> itemIds(Map<Integer, ItemStats> gear, Set<Integer> owned)
	{
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, ItemStats> e : gear.entrySet())
		{
			Integer id = e.getValue().idsOrEmpty().stream().filter(owned::contains).findFirst()
				.orElse(e.getValue().idsOrEmpty().isEmpty() ? null : e.getValue().idsOrEmpty().get(0));
			if (id != null)
			{
				out.put(e.getKey(), id);
			}
		}
		return out;
	}
}
