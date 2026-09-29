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

import com.slayercompanion.data.DropTable;
import com.slayercompanion.data.MonsterInfo;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

/**
 * Average loot per kill from the wiki's drop rates and the client's Grand Exchange prices, and the
 * chance of the task's rare drops before it ends. Untradeable drops count as nothing.
 */
@Singleton
public class LootEstimator
{
	/** A drop counts as a unique when it is at least this rare... */
	static final double UNIQUE_MAX_CHANCE = 1 / 50.0;
	/** ...and one of it is worth at least this much (so a big stack of coins is not a unique). */
	static final long UNIQUE_MIN_PRICE = 20_000;
	static final int MAX_UNIQUES = 3;

	private final SlayerData data;
	private final ItemManager itemManager;

	@Inject
	LootEstimator(SlayerData data, ItemManager itemManager)
	{
		this.data = data;
		this.itemManager = itemManager;
	}

	/** Estimate for {@code kills} kills of the chosen variant (or the main monster). Client thread only. */
	public Optional<LootEstimate> estimate(TaskInfo task, @Nullable String variant, int kills)
	{
		MonsterInfo monster = task.mainMonster(variant);
		if (monster == null)
		{
			return Optional.empty();
		}
		Optional<DropTable> table = data.drops(monster.getPage());
		if (!table.isPresent() || table.get().dropsOrEmpty().isEmpty())
		{
			return Optional.empty();
		}
		double perKill = 0;
		int unpriced = 0;
		List<LootEstimate.Unique> uniques = new ArrayList<>();
		for (DropTable.Drop d : table.get().dropsOrEmpty())
		{
			if (d.getRate() == null || d.getItemId() == null)
			{
				unpriced++;
				continue;
			}
			long price = Math.max(0, itemManager.getItemPrice(d.getItemId()));
			double chance = d.chancePerKill();
			perKill += chance * d.averageQuantity() * price;
			if (chance > 0 && chance <= UNIQUE_MAX_CHANCE && price >= UNIQUE_MIN_PRICE)
			{
				uniques.add(new LootEstimate.Unique(d.getItem(), d.getRarity(), price * d.getQuantityLow(), chance,
					1 - Math.pow(1 - chance, Math.max(0, kills))));
			}
		}
		uniques.sort(Comparator.comparingLong(LootEstimate.Unique::getPrice).reversed());
		return Optional.of(new LootEstimate(monster.getName(), Math.round(perKill), Math.max(0, kills),
			uniques.size() > MAX_UNIQUES ? new ArrayList<>(uniques.subList(0, MAX_UNIQUES)) : uniques, unpriced));
	}
}
