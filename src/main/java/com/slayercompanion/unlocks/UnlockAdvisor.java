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
package com.slayercompanion.unlocks;

import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.UnlockInfo;
import com.slayercompanion.game.LiveSlayerCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Merges the game's own reward list (names, costs, owned state from the cache and varbits) with
 * the bundled recommendations. When the live list is unavailable the bundled list is shown alone.
 * Client thread only.
 */
@Singleton
public class UnlockAdvisor
{
	private final SlayerData data;
	private final LiveSlayerCatalog catalog;
	private final ItemManager itemManager;

	@Inject
	UnlockAdvisor(SlayerData data, LiveSlayerCatalog catalog, ItemManager itemManager)
	{
		this.data = data;
		this.catalog = catalog;
		this.itemManager = itemManager;
	}

	public List<UnlockAdvice> advise(int points)
	{
		Map<String, UnlockInfo> bundled = new HashMap<>();
		for (UnlockInfo u : data.unlocks())
		{
			bundled.put(SlayerData.normalise(u.getName()), u);
		}

		List<UnlockAdvice> out = new ArrayList<>();
		List<LiveSlayerCatalog.Unlock> live = catalog.unlocks();
		if (!live.isEmpty())
		{
			for (LiveSlayerCatalog.Unlock u : live)
			{
				UnlockInfo b = bundled.remove(SlayerData.normalise(u.getName()));
				String effect = u.getDescription() != null && !u.getDescription().isEmpty() ? u.getDescription()
					: (b == null || b.getEffect() == null ? "" : b.getEffect());
				out.add(new UnlockAdvice(u.getName(), u.getDescription(), u.getCost(), catalog.isUnlocked(u),
					points >= u.getCost(),
					b == null ? 9 : b.getPriority(),
					b == null ? null : b.getCategory(),
					b == null ? null : b.getRationale(),
					effect,
					tasks(b),
					elsewhere(b)));
			}
		}
		// Bundled entries the live list did not have (e.g. when the cache read failed).
		for (UnlockInfo b : bundled.values())
		{
			out.add(new UnlockAdvice(b.getName(), b.getEffect() == null ? "" : b.getEffect(), b.getCost(), null,
				points >= b.getCost(), b.getPriority(), b.getCategory(), b.getRationale(),
				b.getEffect() == null ? "" : b.getEffect(), tasks(b), elsewhere(b)));
		}
		out.sort((a, c) ->
		{
			int byOwned = Boolean.compare(Boolean.TRUE.equals(a.getUnlocked()), Boolean.TRUE.equals(c.getUnlocked()));
			if (byOwned != 0)
			{
				return byOwned;
			}
			int byPriority = Integer.compare(a.getPriority(), c.getPriority());
			return byPriority != 0 ? byPriority : Integer.compare(a.getCost(), c.getCost());
		});
		return Collections.unmodifiableList(out);
	}

	/** The cheaper route, with the item's current Grand Exchange price when the bundle names one. */
	@Nullable
	private String elsewhere(@Nullable UnlockInfo b)
	{
		if (b == null || b.getElsewhere() == null || b.getElsewhere().isEmpty())
		{
			return null;
		}
		if (b.getElsewhereItemId() != null)
		{
			int price = itemManager.getItemPrice(b.getElsewhereItemId());
			if (price > 0)
			{
				return b.getElsewhere() + " The note is about " + QuantityFormatter.quantityToStackSize(price) + " gp on the Grand Exchange right now.";
			}
		}
		return b.getElsewhere();
	}

	/** Real task names only; bundled placeholders like "(all tasks)" are dropped. */
	private static List<String> tasks(UnlockInfo b)
	{
		List<String> out = new ArrayList<>();
		if (b != null && b.getAffectsTasks() != null)
		{
			for (String t : b.getAffectsTasks())
			{
				if (t != null && !t.startsWith("("))
				{
					out.add(t);
				}
			}
		}
		return out;
	}
}
