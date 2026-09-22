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

import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Value;

/** Immutable snapshot of the detected Slayer assignment. */
@Value
public class CurrentTask
{
	/** Display name as the game shows it, e.g. "Abyssal demons". */
	String name;
	int remaining;
	int initialAmount;
	/** Area name the master assigned (Konar / Krystilia), or null. */
	@Nullable
	String areaName;
	@Nullable
	SlayerMaster master;
	boolean bossTask;
	int points;
	/** Streak for the assigning master's counter. */
	int streak;

	public int getKills()
	{
		return Math.max(0, initialAmount - remaining);
	}

	/** True when both snapshots describe the same assignment (ignoring progress). */
	public boolean sameAssignment(CurrentTask other)
	{
		return other != null
			&& name.equalsIgnoreCase(other.name)
			&& Objects.equals(areaName, other.areaName)
			&& master == other.master;
	}
}
