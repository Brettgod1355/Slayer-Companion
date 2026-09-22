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

import com.slayercompanion.data.GearItem;
import com.slayercompanion.data.GearTable;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.game.LiveSlayerCatalog;
import com.slayercompanion.task.SlayerMaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.client.game.ItemManager;

/**
 * Suggests what to buy or save for: items in the top tiers of gear tables the player does not
 * own, weighted by how many tasks they help with and how likely the player's master is to assign
 * those tasks.
 */
@Singleton
public class UpgradeAdvisor
{
	@Value
	public static class UpgradeSuggestion
	{
		String itemName;
		@Nullable
		Integer itemId;
		int price;
		String slot;
		List<String> tasksHelped;
		double score;
	}

	private static final int TOP_TIERS = 2;
	private static final int MAX_RESULTS = 15;

	private final SlayerData data;
	private final ItemNameResolver resolver;
	private final OwnedItems owned;
	private final ItemManager itemManager;
	private final LiveSlayerCatalog catalog;

	@Inject
	UpgradeAdvisor(SlayerData data, ItemNameResolver resolver, OwnedItems owned, ItemManager itemManager,
		LiveSlayerCatalog catalog)
	{
		this.data = data;
		this.resolver = resolver;
		this.owned = owned;
		this.itemManager = itemManager;
		this.catalog = catalog;
	}

	/**
	 * @param master     the master whose task list weights the suggestions; null uses all tasks equally.
	 * @param styleFilter "Melee"/"Ranged"/"Magic" to only consider that style, or null for all.
	 *                   Must run on the client thread when {@code master} is non-null (reads the cache).
	 */
	public List<UpgradeSuggestion> suggest(@Nullable SlayerMaster master, @Nullable String styleFilter)
	{
		Map<String, Double> taskWeight = new HashMap<>();
		if (master != null)
		{
			for (LiveSlayerCatalog.MasterAssignment a : catalog.assignmentsFor(master))
			{
				taskWeight.merge(SlayerData.normalise(a.getTaskName()), (double) a.getWeight(), Double::sum);
			}
		}

		Map<String, Double> score = new HashMap<>();
		Map<String, Set<String>> helps = new HashMap<>();
		Map<String, String> slotOf = new HashMap<>();
		for (TaskInfo task : data.tasks())
		{
			double w = taskWeight.isEmpty() ? 1.0 : taskWeight.getOrDefault(SlayerData.normalise(task.getTask()), 0.0);
			if (w <= 0 || task.isBossTask())
			{
				continue;
			}
			for (GearTable table : task.gearTablesOrEmpty())
			{
				if (styleFilter != null && (table.getStyle() == null || !table.getStyle().equalsIgnoreCase(styleFilter)))
				{
					continue;
				}
				if (table.getSlots() == null)
				{
					continue;
				}
				for (Map.Entry<String, List<List<GearItem>>> slot : table.getSlots().entrySet())
				{
					List<List<GearItem>> tiers = slot.getValue();
					int bestOwnedTier = ownedTier(tiers);
					for (int t = 0; t < Math.min(TOP_TIERS, tiers.size()); t++)
					{
						if (bestOwnedTier != 0 && bestOwnedTier <= t + 1)
						{
							break;
						}
						double tierWeight = t == 0 ? 1.0 : 0.5;
						for (GearItem item : tiers.get(t))
						{
							if (isOwned(item))
							{
								continue;
							}
							String name = item.label();
							score.merge(name, w * tierWeight, Double::sum);
							helps.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(task.getTask());
							slotOf.putIfAbsent(name, slot.getKey());
						}
					}
				}
			}
		}

		List<UpgradeSuggestion> out = new ArrayList<>();
		for (Map.Entry<String, Double> e : score.entrySet())
		{
			List<Integer> ids = resolver.resolve(e.getKey());
			Integer id = ids.isEmpty() ? null : ids.get(0);
			int price = id == null ? 0 : itemManager.getItemPrice(id);
			out.add(new UpgradeSuggestion(e.getKey(), id, price, slotOf.get(e.getKey()),
				new ArrayList<>(helps.get(e.getKey())), e.getValue()));
		}
		out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
		return Collections.unmodifiableList(out.size() > MAX_RESULTS ? out.subList(0, MAX_RESULTS) : out);
	}

	private int ownedTier(List<List<GearItem>> tiers)
	{
		for (int i = 0; i < tiers.size(); i++)
		{
			for (GearItem item : tiers.get(i))
			{
				if (isOwned(item))
				{
					return i + 1;
				}
			}
		}
		return 0;
	}

	private boolean isOwned(GearItem item)
	{
		for (String candidate : item.candidates())
		{
			for (int id : resolver.resolve(candidate))
			{
				if (owned.owns(id))
				{
					return true;
				}
			}
		}
		return false;
	}
}
