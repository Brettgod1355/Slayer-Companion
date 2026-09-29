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

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/** A saved loadout ready to draw: every item with its name and where the player has it right now. */
@Value
public class LoadoutDisplay
{
	public enum Status
	{
		/** Worn (equipment) or in the inventory (inventory items), in the saved amount. */
		ON_YOU,
		/** On you, but fewer than saved, or in the inventory instead of worn (or the other way round). */
		PARTLY,
		IN_BANK,
		NOT_OWNED,
		/** Not on you, and the bank has not been seen yet. */
		UNKNOWN
	}

	@Value
	public static class Slot
	{
		int itemId;
		int quantity;
		boolean stackable;
		String name;
		Status status;
		/** Where the item is, for the tooltip ("In your bank"). */
		String where;
	}

	/** Equipment slot index to item. */
	Map<Integer, Slot> equipment;
	/** 28 entries in inventory order; null for an empty slot. */
	List<Slot> inventory;

	public int count(Status status)
	{
		int n = 0;
		for (Slot s : equipment.values())
		{
			n += s.getStatus() == status ? 1 : 0;
		}
		for (@Nullable Slot s : inventory)
		{
			n += s != null && s.getStatus() == status ? 1 : 0;
		}
		return n;
	}

	/** True when everything saved is on the player in the saved amounts. */
	public boolean isComplete()
	{
		return count(Status.ON_YOU) == equipment.size() + (int) inventory.stream().filter(s -> s != null).count();
	}
}
