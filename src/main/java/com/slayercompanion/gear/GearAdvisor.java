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

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.GearItem;
import com.slayercompanion.data.GearTable;
import com.slayercompanion.data.TaskInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Compares a wiki gear table against what the player owns and tells them, per slot, the best
 * listed item they have and what would be an upgrade.
 */
@Singleton
public class GearAdvisor
{
	/** Slot display order. */
	public static final List<String> SLOT_ORDER = Arrays.asList(
		"head", "cape", "neck", "ammo", "weapon", "body", "shield", "legs", "hands", "feet", "ring", "special");

	private final ItemNameResolver resolver;
	private final OwnedItems owned;
	private final SlayerCompanionConfig config;

	@Inject
	GearAdvisor(ItemNameResolver resolver, OwnedItems owned, SlayerCompanionConfig config)
	{
		this.resolver = resolver;
		this.owned = owned;
		this.config = config;
	}

	/** Pick the gear table to show first: preferred style, else the task's recommended style, else the first. */
	@Nullable
	public GearTable defaultTable(TaskInfo task)
	{
		List<GearTable> tables = task.gearTablesOrEmpty();
		if (tables.isEmpty())
		{
			return null;
		}
		String preferred = config.preferredStyle() == SlayerCompanionConfig.CombatStyle.AUTO
			? task.getRecommendedStyle() : config.preferredStyle().name();
		if (preferred != null)
		{
			for (GearTable t : tables)
			{
				if (t.getStyle() != null && t.getStyle().equalsIgnoreCase(preferred))
				{
					return t;
				}
			}
		}
		return tables.get(0);
	}

	public List<SlotAdvice> advise(GearTable table)
	{
		List<SlotAdvice> out = new ArrayList<>();
		if (table.getSlots() == null)
		{
			return out;
		}
		List<String> slots = new ArrayList<>(SLOT_ORDER);
		for (String s : table.getSlots().keySet())
		{
			if (!slots.contains(s))
			{
				slots.add(s);
			}
		}
		for (String slot : slots)
		{
			List<List<GearItem>> tiers = table.getSlots().get(slot);
			if (tiers == null || tiers.isEmpty())
			{
				continue;
			}
			out.add(adviseSlot(slot, tiers, table.getSlotNotes() == null ? null : table.getSlotNotes().get(slot)));
		}
		return out;
	}

	private SlotAdvice adviseSlot(String slot, List<List<GearItem>> tiers, @Nullable String note)
	{
		String ownedBest = null;
		Integer ownedId = null;
		int ownedTier = 0;
		boolean equipped = false;
		for (int i = 0; i < tiers.size() && ownedBest == null; i++)
		{
			for (GearItem item : tiers.get(i))
			{
				Integer id = ownedId(item);
				if (id != null)
				{
					ownedBest = item.label();
					ownedId = id;
					ownedTier = i + 1;
					equipped = owned.equipment().containsKey(id);
					break;
				}
			}
		}
		List<String> missing = new ArrayList<>();
		int upTo = ownedBest == null ? tiers.size() : ownedTier - 1;
		for (int i = 0; i < upTo; i++)
		{
			for (GearItem item : tiers.get(i))
			{
				missing.add(item.label());
			}
		}
		List<String> best = new ArrayList<>();
		for (GearItem item : tiers.get(0))
		{
			best.add(item.label());
		}
		return new SlotAdvice(slot, best, ownedBest, ownedId, ownedTier, equipped,
			Collections.unmodifiableList(missing), note);
	}

	/** Canonical id of an owned item matching any of the entry's names, else null. */
	@Nullable
	public Integer ownedId(GearItem item)
	{
		for (String candidate : item.candidates())
		{
			for (int id : resolver.resolve(candidate))
			{
				if (owned.owns(id))
				{
					return id;
				}
			}
		}
		return null;
	}

	/** Names of required/useful items the player does not own at all. */
	public List<String> missingItems(List<String> names)
	{
		List<String> missing = new ArrayList<>();
		if (names == null)
		{
			return missing;
		}
		for (String name : names)
		{
			boolean has = false;
			// "Nose peg or Slayer helmet": any alternative counts.
			for (String alt : name.split("\\s+or\\s+"))
			{
				for (int id : resolver.resolve(alt.trim()))
				{
					if (owned.owns(id))
					{
						has = true;
						break;
					}
				}
				if (has)
				{
					break;
				}
			}
			if (!has)
			{
				missing.add(name);
			}
		}
		return missing;
	}

	/** Map of slot -> item id for what is currently worn. */
	public Map<Integer, Integer> currentEquipment()
	{
		return owned.equipment();
	}
}
