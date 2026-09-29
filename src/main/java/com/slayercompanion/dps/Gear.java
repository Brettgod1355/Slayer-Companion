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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.EquipmentInventorySlot;

/** A set of worn items (equipment slot index to item stats) with the bonus sums the formulas need. */
public final class Gear
{
	public static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
	public static final int CAPE = EquipmentInventorySlot.CAPE.getSlotIdx();
	public static final int NECK = EquipmentInventorySlot.AMULET.getSlotIdx();
	public static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	public static final int BODY = EquipmentInventorySlot.BODY.getSlotIdx();
	public static final int SHIELD = EquipmentInventorySlot.SHIELD.getSlotIdx();
	public static final int LEGS = EquipmentInventorySlot.LEGS.getSlotIdx();
	public static final int HANDS = EquipmentInventorySlot.GLOVES.getSlotIdx();
	public static final int FEET = EquipmentInventorySlot.BOOTS.getSlotIdx();
	public static final int RING = EquipmentInventorySlot.RING.getSlotIdx();
	public static final int AMMO = EquipmentInventorySlot.AMMO.getSlotIdx();

	private final Map<Integer, ItemStats> items;

	public Gear(Map<Integer, ItemStats> items)
	{
		this.items = Collections.unmodifiableMap(new HashMap<>(items));
	}

	/** Equipment slot index for the wiki's slot name ("2h" is the weapon slot); -1 when unknown. */
	public static int slotOf(@Nullable String slot)
	{
		switch (slot == null ? "" : slot.toLowerCase(Locale.ROOT))
		{
			case "head":
				return HEAD;
			case "cape":
				return CAPE;
			case "neck":
				return NECK;
			case "weapon":
			case "2h":
				return WEAPON;
			case "body":
				return BODY;
			case "shield":
				return SHIELD;
			case "legs":
				return LEGS;
			case "hands":
				return HANDS;
			case "feet":
				return FEET;
			case "ring":
				return RING;
			case "ammo":
				return AMMO;
			default:
				return -1;
		}
	}

	public Map<Integer, ItemStats> items()
	{
		return items;
	}

	@Nullable
	public ItemStats get(int slot)
	{
		return items.get(slot);
	}

	/** A copy with {@code item} in {@code slot} (null empties it); a two-handed weapon empties the shield. */
	public Gear with(int slot, @Nullable ItemStats item)
	{
		Map<Integer, ItemStats> next = new HashMap<>(items);
		if (item == null)
		{
			next.remove(slot);
		}
		else
		{
			next.put(slot, item);
			if (slot == WEAPON && item.isTwoHanded())
			{
				next.remove(SHIELD);
			}
			if (slot == SHIELD && items.get(WEAPON) != null && items.get(WEAPON).isTwoHanded())
			{
				return this;
			}
		}
		return new Gear(next);
	}

	/** Lower-case name of the item in the slot, "" when empty. */
	public String name(int slot)
	{
		ItemStats s = items.get(slot);
		return s == null || s.getName() == null ? "" : s.getName().toLowerCase(Locale.ROOT);
	}

	public String category()
	{
		ItemStats w = items.get(WEAPON);
		return w == null || w.getCategory() == null ? "unarmed" : w.getCategory().trim().toLowerCase(Locale.ROOT);
	}

	/** Bows and crossbows fire the ammo slot; crystal bows, thrown weapons and the rest do not. */
	public boolean firesAmmo()
	{
		String c = category();
		String w = name(WEAPON);
		return (c.equals("bow") && !w.startsWith("crystal bow") && !w.startsWith("bow of faerdhinen")) || c.equals("crossbow");
	}

	/** True when the ammo slot holds what the weapon fires (arrows for bows, bolts for crossbows). */
	public boolean ammoFits()
	{
		String a = name(AMMO);
		return category().equals("bow") ? a.contains("arrow") : category().equals("crossbow") && a.contains("bolt");
	}

	public int attackBonus(AttackStyle.Type type)
	{
		int total = 0;
		for (Map.Entry<Integer, ItemStats> e : items.entrySet())
		{
			ItemStats s = e.getValue();
			if (type == AttackStyle.Type.RANGED && e.getKey() == AMMO && !firesAmmo())
			{
				continue;
			}
			switch (type)
			{
				case STAB:
					total += s.getStab();
					break;
				case SLASH:
					total += s.getSlash();
					break;
				case CRUSH:
					total += s.getCrush();
					break;
				case RANGED:
					total += s.getRanged();
					break;
				default:
					total += s.getMagic();
					break;
			}
		}
		return total;
	}

	public int strength()
	{
		return items.values().stream().mapToInt(ItemStats::getStr).sum();
	}

	public int rangedStrength()
	{
		int total = 0;
		for (Map.Entry<Integer, ItemStats> e : items.entrySet())
		{
			if (e.getKey() != AMMO || firesAmmo())
			{
				total += e.getValue().getRangedStr();
			}
		}
		return total;
	}

	/** Magic damage bonus in tenths of a percent (the game's unit). */
	public int magicDamageTenths()
	{
		return (int) Math.round(items.values().stream().mapToDouble(ItemStats::getMagicDmg).sum() * 10);
	}
}
