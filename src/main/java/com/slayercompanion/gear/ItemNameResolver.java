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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Maps wiki item names to item ids using the client's item name index. Exact matches win; a
 * name with no exact match falls back to well-known variants (imbued, ornamented, charged,
 * trimmed) so that "Slayer helmet" also finds "Slayer helmet (i)".
 * <p>
 * Results are cached per session. Safe off the client thread (the search index is in memory).
 */
@Singleton
public class ItemNameResolver
{
	private static final String[] VARIANT_SUFFIXES = {
		" (i)", " (or)", " (t)", " (g)", " (e)", " (f)", " (uncharged)", " (charged)", " (full)",
		" (max)", " (nz)", " (u)", " (l)", " (bh)", " (kit)", " (4)", " (3)", " (2)", " (1)",
	};

	private final ItemManager itemManager;
	private final ItemIndex index;
	private final Map<String, List<Integer>> cache = new HashMap<>();

	@Inject
	ItemNameResolver(ItemManager itemManager, ItemIndex index)
	{
		this.itemManager = itemManager;
		this.index = index;
	}

	/** Drop cached lookups (call when the item index finishes scanning). */
	public synchronized void invalidate()
	{
		cache.clear();
	}

	/** All item ids whose name matches, exact name first. Empty when nothing matches. */
	public synchronized List<Integer> resolve(String wikiName)
	{
		if (wikiName == null || wikiName.isEmpty())
		{
			return Collections.emptyList();
		}
		String key = wikiName.trim().toLowerCase();
		List<Integer> cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}
		List<Integer> ids = new ArrayList<>();
		addExact(key, ids);
		if (ids.isEmpty())
		{
			// Try the name with a variant suffix, and the base name of a variant.
			for (String suffix : VARIANT_SUFFIXES)
			{
				addExact(key + suffix, ids);
			}
			int paren = key.indexOf(" (");
			if (paren > 0)
			{
				addExact(key.substring(0, paren), ids);
			}
		}
		if (ids.isEmpty())
		{
			// Last resort: prefix match on the search index (e.g. "Ardougne cloak" -> all tiers).
			for (ItemPrice p : itemManager.search(key))
			{
				if (p.getName().toLowerCase().startsWith(key))
				{
					ids.add(itemManager.canonicalize(p.getId()));
				}
			}
		}
		List<Integer> result = Collections.unmodifiableList(ids);
		if (index.isComplete() || !ids.isEmpty())
		{
			cache.put(key, result);
		}
		return result;
	}

	private void addExact(String name, List<Integer> into)
	{
		// The client's own item definitions cover untradeables; the price list covers the rest.
		for (int id : index.ids(name))
		{
			if (!into.contains(id))
			{
				into.add(id);
			}
		}
		for (ItemPrice p : itemManager.search(name))
		{
			if (p.getName().equalsIgnoreCase(name))
			{
				int id = itemManager.canonicalize(p.getId());
				if (!into.contains(id))
				{
					into.add(id);
				}
			}
		}
	}
}
