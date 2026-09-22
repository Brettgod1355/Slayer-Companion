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
package com.slayercompanion.tracker;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Loot, supplies and progress for one Slayer assignment. Mutable so it can be updated in place
 * and serialised to config with Gson.
 */
@Data
public class TaskSession
{
	private String taskName;
	private String masterName;
	private long startedAtEpochMs;
	private long updatedAtEpochMs;
	private int initialAmount;
	private int kills;
	private long slayerXpGained;
	/** Canonical item id -> quantity received as loot from task targets. */
	private Map<Integer, Integer> loot = new HashMap<>();
	private long lootValue;
	/** Canonical item id -> quantity consumed (food, potions, runes, ammo). */
	private Map<Integer, Integer> supplies = new HashMap<>();
	private long suppliesValue;
	private boolean completed;

	public long getProfit()
	{
		return lootValue - suppliesValue;
	}

	public long getDurationMs()
	{
		return Math.max(0, updatedAtEpochMs - startedAtEpochMs);
	}
}
