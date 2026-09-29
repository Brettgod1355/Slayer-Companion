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

	/** Names of required items the player does not own at all. */
	public List<String> missing(@Nullable List<String> names)
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
}
