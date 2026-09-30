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
import lombok.Value;

/**
 * The bonus Mortimer attaches to a task ({@code VarbitID.SLAYER_MODIFIER_ID} and {@code _VALUE}), in
 * the game's own wording: points, a bigger or smaller amount, or a better clue, superior or XP rate.
 */
@Value
public class TaskModifier
{
	public static final int POINTS = 1;
	public static final int AMOUNT = 2;
	public static final int CLUES = 3;
	public static final int SUPERIORS = 4;
	public static final int XP = 5;

	int type;
	/** Signed for {@link #AMOUNT} (the game keeps the sign in {@code SLAYER_MODIFIER_NEGATIVE}). */
	int value;

	/** The modifier the varbits describe, or null when there is none. */
	@Nullable
	public static TaskModifier of(int type, int value, boolean negative)
	{
		if (type < POINTS || type > XP || value <= 0)
		{
			return null;
		}
		return new TaskModifier(type, type == AMOUNT && negative ? -value : value);
	}

	public String describe()
	{
		switch (type)
		{
			case POINTS:
				return "+" + value + " Slayer points";
			case AMOUNT:
				return (value > 0 ? "+" : "") + value + " assigned";
			case CLUES:
				return "+" + value + "% clue chance";
			case SUPERIORS:
				return "+" + value + "% superior unique chance";
			default:
				return "+" + value + "% Slayer XP";
		}
	}
}
