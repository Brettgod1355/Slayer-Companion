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
package com.slayercompanion.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads Slayer reference data that the game client already carries in its cache database tables:
 * the Slayer reward unlocks with their point costs, and whether each is on. This is always current
 * with the game, so it is preferred over the bundled wiki snapshot wherever both exist.
 * <p>
 * Every method must be called on the client thread. Results are cached per session.
 */
@Slf4j
@Singleton
public class LiveSlayerCatalog
{
	@Value
	public static class Unlock
	{
		String name;
		String description;
		int cost;
		/** Value of {@code SlayerUnlock.COL_BIT}: an index into the reward-unlock bits, see {@link #isUnlocked}; -1 when missing. */
		int bit;
		int listPosition;
	}

	/**
	 * The varps that hold the reward-unlock bits, 32 to a varp: bit {@code n} is bit {@code n % 32} of
	 * {@code UNLOCK_VARPS[n / 32]}. Every unlock varbit RuneLite names sits exactly there (checked against
	 * the game cache), e.g. Bigger and Badder is bit 35, bit 3 of {@code SLAYER_REWARDS_UNLOCKS1}.
	 */
	private static final int[] UNLOCK_VARPS = {
		VarPlayerID.SLAYER_REWARDS_UNLOCKS, VarPlayerID.SLAYER_REWARDS_UNLOCKS1, VarPlayerID.SLAYER_REWARDS_UNLOCKS2,
	};

	private final Client client;

	private List<Unlock> unlocks;
	/** The "Bigger and Badder" row, whose bit is cross-checked against the varbit RuneLite names for it. */
	private Unlock sanityRow;

	@Inject
	LiveSlayerCatalog(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		unlocks = null;
	}

	/** All reward-shop unlocks read from {@code DBTableID.SlayerUnlock}. */
	public List<Unlock> unlocks()
	{
		if (unlocks == null)
		{
			unlocks = readUnlocks();
		}
		return unlocks;
	}

	/**
	 * Whether an unlock's bit is set, which is how the reward shop records it. {@code null} means
	 * unknown: the bit is out of range, the vars cannot be read, or the "Bigger and Badder" bit
	 * disagrees with its own varbit (the layout would then have changed).
	 */
	public Boolean isUnlocked(Unlock unlock)
	{
		unlocks();
		try
		{
			boolean superiors = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS) != 0;
			if (sanityRow == null || !Boolean.valueOf(superiors).equals(bitSet(sanityRow.getBit())))
			{
				return null;
			}
			return bitSet(unlock.getBit());
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private Boolean bitSet(int bit)
	{
		if (bit < 0 || bit >= UNLOCK_VARPS.length * 32)
		{
			return null;
		}
		return bitSet(client.getVarpValue(UNLOCK_VARPS[bit / 32]), bit);
	}

	/** Bit {@code bit % 32} of one unlock varp's value. */
	static boolean bitSet(int varpValue, int bit)
	{
		return (varpValue >>> (bit % 32) & 1) != 0;
	}

	private List<Unlock> readUnlocks()
	{
		List<Unlock> out = new ArrayList<>();
		try
		{
			for (int row : client.getDBTableRows(DBTableID.SlayerUnlock.ID))
			{
				Object[] name = client.getDBTableField(row, DBTableID.SlayerUnlock.COL_NAME, 0);
				if (name == null || name.length == 0)
				{
					continue;
				}
				Object[] desc = client.getDBTableField(row, DBTableID.SlayerUnlock.COL_DESCRIPTION, 0);
				out.add(new Unlock(
					(String) name[0],
					desc == null || desc.length == 0 ? "" : String.valueOf(desc[0]),
					intField(row, DBTableID.SlayerUnlock.COL_COST, 0),
					intField(row, DBTableID.SlayerUnlock.COL_BIT, -1),
					listPosition(row)));
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read slayer unlocks from cache", e);
		}
		out.sort((a, b) -> Integer.compare(a.getListPosition(), b.getListPosition()));
		// Prove the bit layout at run time on the one row whose varbit RuneLite names.
		sanityRow = null;
		for (Unlock u : out)
		{
			if ("biggerandbadder".equals(u.getName().toLowerCase().replaceAll("[^a-z0-9]", "")))
			{
				sanityRow = u;
			}
		}
		if (sanityRow == null)
		{
			log.debug("No Bigger and Badder row in SlayerUnlock; owned state unavailable");
		}
		return Collections.unmodifiableList(out);
	}

	/** {@code COL_LIST_POSITION} is (shop page, position on the page): unlocks come before extensions. */
	private int listPosition(int row)
	{
		Object[] v = client.getDBTableField(row, DBTableID.SlayerUnlock.COL_LIST_POSITION, 0);
		if (v == null || v.length == 0 || !(v[0] instanceof Integer))
		{
			return 0;
		}
		int page = (Integer) v[0];
		int position = v.length > 1 && v[1] instanceof Integer ? (Integer) v[1] : 0;
		return page * 1000 + position;
	}

	private int intField(int row, int column, int missing)
	{
		Object[] v = client.getDBTableField(row, column, 0);
		if (v == null || v.length == 0 || !(v[0] instanceof Integer))
		{
			return missing;
		}
		return (Integer) v[0];
	}
}
