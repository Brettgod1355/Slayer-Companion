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

import com.slayercompanion.task.SlayerMaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads Slayer reference data that the game client already carries in its cache database tables:
 * each master's assignment list with weights and amounts, and the Slayer reward unlocks with
 * their point costs. This is always current with the game, so it is preferred over the bundled
 * wiki snapshot wherever both exist.
 * <p>
 * Every method must be called on the client thread. Results are cached per session.
 */
@Slf4j
@Singleton
public class LiveSlayerCatalog
{
	@Value
	public static class MasterAssignment
	{
		SlayerMaster master;
		String taskName;
		int weight;
		int minAmount;
		int maxAmount;
	}

	@Value
	public static class Unlock
	{
		String name;
		String description;
		int cost;
		/** Value of {@code SlayerUnlock.COL_BIT}; expected to be a varbit id, see {@link #isUnlocked}. */
		int bit;
		int listPosition;
	}

	private final Client client;

	private List<MasterAssignment> assignments;
	private List<Unlock> unlocks;
	/** True once the "Bigger and Badder" row's bit matched its known varbit id. */
	private boolean bitsAreVarbits;

	@Inject
	LiveSlayerCatalog(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		assignments = null;
		unlocks = null;
	}

	/** All assignments for all masters, read from {@code DBTableID.SlayerMasterTask}. */
	public List<MasterAssignment> assignments()
	{
		if (assignments == null)
		{
			assignments = readAssignments();
		}
		return assignments;
	}

	public List<MasterAssignment> assignmentsFor(SlayerMaster master)
	{
		List<MasterAssignment> out = new ArrayList<>();
		for (MasterAssignment a : assignments())
		{
			if (a.getMaster() == master)
			{
				out.add(a);
			}
		}
		return out;
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
	 * Whether an unlock is owned. {@code COL_BIT} is treated as a varbit id; a value that is not a
	 * plausible varbit (0 or negative) yields {@code null} meaning unknown.
	 */
	public Boolean isUnlocked(Unlock unlock)
	{
		unlocks();
		if (!bitsAreVarbits || unlock.getBit() <= 0)
		{
			return null;
		}
		try
		{
			return client.getVarbitValue(unlock.getBit()) != 0;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private List<MasterAssignment> readAssignments()
	{
		List<MasterAssignment> out = new ArrayList<>();
		try
		{
			for (int row : client.getDBTableRows(DBTableID.SlayerMasterTask.ID))
			{
				Object[] masterId = client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0);
				Object[] taskRow = client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_TASK, 0);
				if (masterId == null || masterId.length == 0 || taskRow == null || taskRow.length == 0)
				{
					continue;
				}
				SlayerMaster master = SlayerMaster.fromVarbit((Integer) masterId[0]);
				if (master == null)
				{
					continue;
				}
				Object[] name = client.getDBTableField((Integer) taskRow[0], DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
				if (name == null || name.length == 0)
				{
					continue;
				}
				out.add(new MasterAssignment(
					master,
					(String) name[0],
					intField(row, DBTableID.SlayerMasterTask.COL_WEIGHT),
					intField(row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT),
					intField(row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT)));
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read slayer master assignments from cache", e);
		}
		return Collections.unmodifiableList(out);
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
					intField(row, DBTableID.SlayerUnlock.COL_COST),
					intField(row, DBTableID.SlayerUnlock.COL_BIT),
					intField(row, DBTableID.SlayerUnlock.COL_LIST_POSITION)));
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read slayer unlocks from cache", e);
		}
		out.sort((a, b) -> Integer.compare(a.getListPosition(), b.getListPosition()));
		// The cache column is assumed to hold varbit ids; prove it on a row whose varbit RuneLite names.
		bitsAreVarbits = false;
		for (Unlock u : out)
		{
			if ("biggerandbadder".equals(u.getName().toLowerCase().replaceAll("[^a-z0-9]", "")))
			{
				bitsAreVarbits = u.getBit() == VarbitID.SLAYER_UNLOCK_SUPERIORMOBS;
			}
		}
		if (!bitsAreVarbits)
		{
			log.debug("SlayerUnlock.COL_BIT does not look like a varbit id; owned state unavailable");
		}
		return Collections.unmodifiableList(out);
	}

	private int intField(int row, int column)
	{
		Object[] v = client.getDBTableField(row, column, 0);
		if (v == null || v.length == 0 || !(v[0] instanceof Integer))
		{
			return 0;
		}
		return (Integer) v[0];
	}
}
