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
package com.slayercompanion.data;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * Equipment the wiki says must be worn for a task (an anti-dragon shield against dragonfire, a
 * mirror shield against basilisks...), so the loadout recommendation keeps it in its slot.
 */
@Data
public class RequiredGear
{
	/** head, cape, neck, ammo, weapon, body, shield, legs, hands, feet or ring. */
	private String slot;
	/** Wiki item names; a name also matches its " (...)" versions, and a leading '*' matches any name containing the rest. */
	private List<String> items;
	/** What it protects against, e.g. "against dragonfire". */
	private String reason;
	/** Only these variants need it (empty: all of them). */
	@Nullable
	private List<String> onlyVariants;
	/** These variants do not (baby dragons...). */
	@Nullable
	private List<String> exceptVariants;
	/** Not needed once the elite Kourend & Kebos diary is done (the Karuulm dungeon's heat). */
	private boolean unlessKourendElite;

	public List<String> itemsOrEmpty()
	{
		return items == null ? Collections.emptyList() : items;
	}

	/** Whether the rule applies to the chosen variant. */
	public boolean appliesTo(@Nullable String variant, boolean kourendElite)
	{
		if (unlessKourendElite && kourendElite)
		{
			return false;
		}
		if (onlyVariants != null && !onlyVariants.isEmpty() && (variant == null || !containsIgnoreCase(onlyVariants, variant)))
		{
			return false;
		}
		return variant == null || exceptVariants == null || !containsIgnoreCase(exceptVariants, variant);
	}

	/** Whether an item of this name satisfies the rule. */
	public boolean accepts(@Nullable String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		String n = itemName.toLowerCase(Locale.ROOT);
		for (String pattern : itemsOrEmpty())
		{
			String p = pattern.toLowerCase(Locale.ROOT);
			if (p.startsWith("*") ? n.contains(p.substring(1)) : n.equals(p) || n.startsWith(p + " ("))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsIgnoreCase(List<String> list, String value)
	{
		for (String s : list)
		{
			if (s.equalsIgnoreCase(value))
			{
				return true;
			}
		}
		return false;
	}
}
