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

import com.slayercompanion.dps.AttackStyle.Stance;
import com.slayercompanion.dps.AttackStyle.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The attack styles of each weapon category (the wiki's "combat_style" names). Categories the
 * plugin does not model (chinchompas, salamanders, multi-style oddities) have none.
 */
public final class WeaponStyles
{
	private static final Map<String, List<AttackStyle>> STYLES = new HashMap<>();

	static
	{
		melee("2h sword", s("Chop", Type.SLASH, Stance.ACCURATE), s("Slash", Type.SLASH, Stance.AGGRESSIVE), s("Smash", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.SLASH, Stance.DEFENSIVE));
		melee("axe", s("Chop", Type.SLASH, Stance.ACCURATE), s("Hack", Type.SLASH, Stance.AGGRESSIVE), s("Smash", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.SLASH, Stance.DEFENSIVE));
		melee("blunt", s("Pound", Type.CRUSH, Stance.ACCURATE), s("Pummel", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.CRUSH, Stance.DEFENSIVE));
		melee("bludgeon", s("Pound", Type.CRUSH, Stance.AGGRESSIVE), s("Pummel", Type.CRUSH, Stance.AGGRESSIVE), s("Smash", Type.CRUSH, Stance.AGGRESSIVE));
		melee("bulwark", s("Pummel", Type.CRUSH, Stance.ACCURATE));
		melee("claw", s("Chop", Type.SLASH, Stance.ACCURATE), s("Slash", Type.SLASH, Stance.AGGRESSIVE), s("Lunge", Type.STAB, Stance.CONTROLLED), s("Block", Type.SLASH, Stance.DEFENSIVE));
		melee("flail", s("Pound", Type.CRUSH, Stance.ACCURATE), s("Pummel", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.CRUSH, Stance.DEFENSIVE));
		melee("partisan", s("Stab", Type.STAB, Stance.ACCURATE), s("Lunge", Type.STAB, Stance.AGGRESSIVE), s("Pound", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.STAB, Stance.DEFENSIVE));
		melee("pickaxe", s("Spike", Type.STAB, Stance.ACCURATE), s("Impale", Type.STAB, Stance.AGGRESSIVE), s("Smash", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.STAB, Stance.DEFENSIVE));
		melee("polearm", s("Jab", Type.STAB, Stance.CONTROLLED), s("Swipe", Type.SLASH, Stance.AGGRESSIVE), s("Fend", Type.STAB, Stance.DEFENSIVE));
		melee("polestaff", s("Bash", Type.CRUSH, Stance.ACCURATE), s("Pound", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.CRUSH, Stance.DEFENSIVE));
		melee("scythe", s("Reap", Type.SLASH, Stance.ACCURATE), s("Chop", Type.SLASH, Stance.AGGRESSIVE), s("Jab", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.SLASH, Stance.DEFENSIVE));
		melee("slash sword", s("Chop", Type.SLASH, Stance.ACCURATE), s("Slash", Type.SLASH, Stance.AGGRESSIVE), s("Lunge", Type.STAB, Stance.CONTROLLED), s("Block", Type.SLASH, Stance.DEFENSIVE));
		melee("spear", s("Lunge", Type.STAB, Stance.CONTROLLED), s("Swipe", Type.SLASH, Stance.CONTROLLED), s("Pound", Type.CRUSH, Stance.CONTROLLED), s("Block", Type.STAB, Stance.DEFENSIVE));
		melee("spiked", s("Pound", Type.CRUSH, Stance.ACCURATE), s("Pummel", Type.CRUSH, Stance.AGGRESSIVE), s("Spike", Type.STAB, Stance.CONTROLLED), s("Block", Type.CRUSH, Stance.DEFENSIVE));
		melee("stab sword", s("Stab", Type.STAB, Stance.ACCURATE), s("Lunge", Type.STAB, Stance.AGGRESSIVE), s("Slash", Type.SLASH, Stance.AGGRESSIVE), s("Block", Type.STAB, Stance.DEFENSIVE));
		melee("unarmed", s("Punch", Type.CRUSH, Stance.ACCURATE), s("Kick", Type.CRUSH, Stance.AGGRESSIVE), s("Block", Type.CRUSH, Stance.DEFENSIVE));
		melee("whip", s("Flick", Type.SLASH, Stance.ACCURATE), s("Lash", Type.SLASH, Stance.CONTROLLED), s("Deflect", Type.SLASH, Stance.DEFENSIVE));
		melee("staff", s("Bash", Type.CRUSH, Stance.ACCURATE), s("Pound", Type.CRUSH, Stance.AGGRESSIVE), s("Focus", Type.CRUSH, Stance.DEFENSIVE));
		melee("bladed staff", s("Jab", Type.STAB, Stance.ACCURATE), s("Swipe", Type.SLASH, Stance.AGGRESSIVE), s("Fend", Type.CRUSH, Stance.DEFENSIVE));
		for (String ranged : new String[]{"bow", "crossbow", "thrown"})
		{
			melee(ranged, s("Accurate", Type.RANGED, Stance.ACCURATE), s("Rapid", Type.RANGED, Stance.RAPID), s("Longrange", Type.RANGED, Stance.LONGRANGE));
		}
		for (String powered : new String[]{"powered staff", "powered wand"})
		{
			melee(powered, s("Accurate", Type.MAGIC, Stance.ACCURATE), s("Longrange", Type.MAGIC, Stance.LONGRANGE));
		}
	}

	private WeaponStyles()
	{
	}

	private static AttackStyle s(String name, Type type, Stance stance)
	{
		return new AttackStyle(name, type, stance);
	}

	private static void melee(String category, AttackStyle... styles)
	{
		STYLES.put(category, Arrays.asList(styles));
	}

	/** Styles of a category (case-insensitive); empty when the plugin does not model it. */
	public static List<AttackStyle> of(@Nullable String category)
	{
		return category == null ? Collections.emptyList() : STYLES.getOrDefault(category.trim().toLowerCase(Locale.ROOT), Collections.emptyList());
	}

	/** Staves that can autocast combat spells. */
	public static boolean canAutocast(@Nullable String category)
	{
		String c = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
		return c.equals("staff") || c.equals("bladed staff");
	}
}
