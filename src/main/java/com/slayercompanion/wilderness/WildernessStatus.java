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
package com.slayercompanion.wilderness;

import java.util.List;
import lombok.Value;

/**
 * Informational snapshot of what the player is carrying and what the game's death rules would
 * keep or drop. Never used to warn or alert; the panel simply displays it.
 */
@Value
public class WildernessStatus
{
	@Value
	public static class CarriedItem
	{
		int itemId;
		String name;
		int quantity;
		/** Grand Exchange value of the whole stack, 0 for untradeables. */
		long value;
		boolean tradeable;
	}

	boolean inWilderness;
	/** Wilderness level from the game's own indicator, 0 when not in the Wilderness. */
	int wildernessLevel;
	boolean skulled;
	boolean protectItemActive;
	/** Number of items the death rules keep for this player right now. */
	int itemsKept;
	List<CarriedItem> kept;
	List<CarriedItem> lost;
	List<CarriedItem> untradeables;
	/** Total GE value of the tradeable items that would be lost. */
	long riskValue;
	/** Total GE value of everything carried. */
	long carriedValue;
}
