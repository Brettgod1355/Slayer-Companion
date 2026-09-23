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
package com.slayercompanion.task;

import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Slayer masters keyed by the value of {@code VarbitID.SLAYER_MASTER}.
 * <p>
 * Values 7 (Krystilia) and 10 (Mortimer) are used by RuneLite's core Slayer plugin. The remaining
 * values follow the order the game lists masters in and match the SlayerPlus plugin's catalogue;
 * they are not confirmed by RuneLite source, so anything keyed on them degrades gracefully when
 * {@link #fromVarbit(int)} returns {@code null}.
 */
@Getter
public enum SlayerMaster
{
	TURAEL(1, "Turael", "turael", new String[]{"Turael", "Aya"}, new WorldPoint(2931, 3536, 0), false, false),
	MAZCHNA(2, "Mazchna", "mazchna", new String[]{"Mazchna", "Achtryn"}, new WorldPoint(3510, 3509, 0), false, false),
	VANNAKA(3, "Vannaka", "vannaka", new String[]{"Vannaka"}, new WorldPoint(3147, 9913, 0), false, false),
	CHAELDAR(4, "Chaeldar", "chaeldar", new String[]{"Chaeldar"}, new WorldPoint(2445, 4431, 0), false, false),
	DURADEL(5, "Duradel", "duradel", new String[]{"Duradel", "Kuradal"}, new WorldPoint(2869, 2982, 1), false, false),
	NIEVE(6, "Nieve", "nieve", new String[]{"Nieve", "Steve"}, new WorldPoint(2432, 3423, 0), false, false),
	KRYSTILIA(7, "Krystilia", "krystilia", new String[]{"Krystilia"}, new WorldPoint(3108, 3516, 0), true, true),
	KONAR(8, "Konar quo Maten", "konar", new String[]{"Konar quo Maten", "Konar"}, new WorldPoint(1308, 3786, 0), true, false),
	SPRIA(9, "Spria", "spria", new String[]{"Spria"}, new WorldPoint(3091, 3267, 0), false, false),
	MORTIMER(10, "Mortimer", "mortimer", new String[]{"Mortimer"}, new WorldPoint(2589, 8614, 0), false, true);

	private final int varbitValue;
	private final String displayName;
	/** Key into the bundled masters.json. */
	private final String dataId;
	private final String[] npcNames;
	private final WorldPoint location;
	/** Tasks must be completed in the assigned area. */
	private final boolean areaRestricted;
	/** Uses its own streak counter instead of the shared one. */
	private final boolean separateStreak;

	SlayerMaster(int varbitValue, String displayName, String dataId, String[] npcNames, WorldPoint location,
		boolean areaRestricted, boolean separateStreak)
	{
		this.varbitValue = varbitValue;
		this.displayName = displayName;
		this.dataId = dataId;
		this.npcNames = npcNames;
		this.location = location;
		this.areaRestricted = areaRestricted;
		this.separateStreak = separateStreak;
	}

	@Nullable
	public static SlayerMaster fromVarbit(int value)
	{
		for (SlayerMaster m : values())
		{
			if (m.varbitValue == value)
			{
				return m;
			}
		}
		return null;
	}

	@Nullable
	public static SlayerMaster fromDataId(String id)
	{
		for (SlayerMaster m : values())
		{
			if (m.dataId.equalsIgnoreCase(id))
			{
				return m;
			}
		}
		return null;
	}

	public boolean givesPoints()
	{
		return this != TURAEL && this != SPRIA;
	}
}
