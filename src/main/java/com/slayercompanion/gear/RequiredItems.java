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
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Which of a task's required items the player owns nowhere (bank, inventory or worn). */
@Singleton
public class RequiredItems
{
	private final ItemNameResolver resolver;
	private final OwnedItems owned;

	@Inject
	RequiredItems(ItemNameResolver resolver, OwnedItems owned)
	{
		this.resolver = resolver;
		this.owned = owned;
	}

	/** Names of required items the player does not own at all (lines that name no item are left out). */
	public List<String> missing(@Nullable List<String> names)
	{
		List<String> missing = new ArrayList<>();
		if (names == null)
		{
			return missing;
		}
		for (String name : names)
		{
			boolean isItem = false;
			boolean has = false;
			// "Nose peg or Slayer helmet", "Anti-dragon shield, dragonfire shield or dragonfire ward": any alternative counts.
			for (String alt : alternatives(name))
			{
				for (int id : resolver.resolve(alt))
				{
					isItem = true;
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
			if (isItem && !has)
			{
				missing.add(name);
			}
		}
		return missing;
	}

	/** Required lines that name no item at all ("87 Slayer (cannot be boosted)", "On a Kraken task"). */
	public List<String> notItems(@Nullable List<String> names)
	{
		List<String> out = new ArrayList<>();
		if (names == null)
		{
			return out;
		}
		for (String name : names)
		{
			if (alternatives(name).stream().allMatch(alt -> resolver.resolve(alt).isEmpty()))
			{
				out.add(name);
			}
		}
		return out;
	}

	/**
	 * The item names in a wiki "required items" line: remarks in brackets dropped ("(except for baby
	 * dragons)"; a short variant tag such as "(i)" or "(lit)" stays), then split at commas, slashes,
	 * "or" and "and/or".
	 */
	public static List<String> alternatives(String line)
	{
		String s = line.replaceAll("\\s*\\((?=[^()]*[\\s,;])[^()]*\\)", " ");
		List<String> out = new ArrayList<>();
		for (String part : s.split(",|/|\\s+and/or\\s+|\\s+or\\s+"))
		{
			String p = part.trim().replaceAll("^(?:or|and/or)\\s+", "");
			if (!p.isEmpty())
			{
				out.add(p);
			}
		}
		return out;
	}
}
