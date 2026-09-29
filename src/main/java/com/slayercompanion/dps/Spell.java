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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

/** An autocast combat spell the recommendation considers. */
@Value
public class Spell
{
	/** Game spellbook varbit values. */
	public static final int STANDARD = 0;
	public static final int ANCIENT = 1;

	String name;
	int level;
	int maxHit;
	/** "fire", "water", ... or null for spells with no element. */
	@Nullable
	String element;

	private static final List<Spell> STANDARD_SPELLS = new ArrayList<>();
	private static final List<Spell> ANCIENT_SPELLS = new ArrayList<>();

	static
	{
		STANDARD_SPELLS.add(new Spell("Fire Surge", 95, 24, "fire"));
		STANDARD_SPELLS.add(new Spell("Fire Wave", 75, 20, "fire"));
		STANDARD_SPELLS.add(new Spell("Fire Blast", 59, 16, "fire"));
		STANDARD_SPELLS.add(new Spell("Fire Bolt", 35, 12, "fire"));
		STANDARD_SPELLS.add(new Spell("Fire Strike", 13, 8, "fire"));
		ANCIENT_SPELLS.add(new Spell("Ice Barrage", 94, 30, null));
		ANCIENT_SPELLS.add(new Spell("Ice Blitz", 82, 26, null));
		ANCIENT_SPELLS.add(new Spell("Ice Burst", 70, 22, null));
		ANCIENT_SPELLS.add(new Spell("Ice Rush", 58, 18, null));
	}

	/** The strongest fire (standard) or ice (ancient) spell the magic level allows; null on other spellbooks. */
	@Nullable
	public static Spell best(int spellbook, int magicLevel)
	{
		List<Spell> book = spellbook == STANDARD ? STANDARD_SPELLS : spellbook == ANCIENT ? ANCIENT_SPELLS : null;
		if (book == null)
		{
			return null;
		}
		for (Spell s : book)
		{
			if (magicLevel >= s.level)
			{
				return s;
			}
		}
		return null;
	}
}
