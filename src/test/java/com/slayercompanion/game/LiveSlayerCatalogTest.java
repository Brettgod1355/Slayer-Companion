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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

public class LiveSlayerCatalogTest
{
	private static final int LIKE_A_BOSS_VARBIT = 4723;

	private Client client;
	private LiveSlayerCatalog catalog;
	private final List<Integer> rows = new ArrayList<>();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getDBTableRows(DBTableID.SlayerUnlock.ID)).thenReturn(rows);
		catalog = new LiveSlayerCatalog(client);
	}

	private void row(int row, String name, int cost, int bit, int position)
	{
		rows.add(row);
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_NAME, 0)).thenReturn(new Object[]{name});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_DESCRIPTION, 0)).thenReturn(new Object[]{name + " description"});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_COST, 0)).thenReturn(new Object[]{cost});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_BIT, 0)).thenReturn(new Object[]{bit});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_LIST_POSITION, 0)).thenReturn(new Object[]{position});
	}

	private LiveSlayerCatalog.Unlock find(String name)
	{
		for (LiveSlayerCatalog.Unlock u : catalog.unlocks())
		{
			if (u.getName().equals(name))
			{
				return u;
			}
		}
		throw new AssertionError(name + " not read");
	}

	@Test
	public void unlocksAreReadInShopOrder()
	{
		row(1, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 5);
		row(2, "Bigger and Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 2);
		List<LiveSlayerCatalog.Unlock> unlocks = catalog.unlocks();
		assertEquals(2, unlocks.size());
		assertEquals("Bigger and Badder", unlocks.get(0).getName());
		assertEquals(200, unlocks.get(1).getCost());
		assertEquals("Like a Boss description", unlocks.get(1).getDescription());
	}

	@Test
	public void ownedStateIsReadOnceBitsAreProvenToBeVarbits()
	{
		row(1, "Bigger and Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		row(2, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 2);
		when(client.getVarbitValue(LIKE_A_BOSS_VARBIT)).thenReturn(1);
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Like a Boss")));
		when(client.getVarbitValue(LIKE_A_BOSS_VARBIT)).thenReturn(0);
		assertEquals(Boolean.FALSE, catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void bitsThatAreNotVarbitsGiveUnknown()
	{
		// The sanity row's bit does not match its known varbit: the column is something else.
		row(1, "Bigger and Badder", 150, 7, 1);
		row(2, "Like a Boss", 200, 8, 2);
		when(client.getVarbitValue(anyInt())).thenReturn(1);
		assertNull(catalog.isUnlocked(find("Like a Boss")));
		assertNull(catalog.isUnlocked(find("Bigger and Badder")));
	}

	@Test
	public void missingSanityRowGivesUnknown()
	{
		row(2, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 2);
		when(client.getVarbitValue(anyInt())).thenReturn(1);
		assertNull(catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void sanityRowNameIsMatchedLoosely()
	{
		row(1, "Bigger & Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		row(2, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 2);
		assertNull("'&' is not 'and'", catalog.isUnlocked(find("Like a Boss")));

		catalog.reset();
		rows.clear();
		row(1, "BIGGER AND BADDER!", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		row(2, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 2);
		assertEquals(Boolean.FALSE, catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void implausibleBitGivesUnknown()
	{
		row(1, "Bigger and Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		row(2, "Task Storage", 500, 0, 2);
		assertNull(catalog.isUnlocked(find("Task Storage")));
	}

	@Test
	public void failingVarbitReadGivesUnknown()
	{
		row(1, "Bigger and Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		row(2, "Like a Boss", 200, LIKE_A_BOSS_VARBIT, 2);
		when(client.getVarbitValue(LIKE_A_BOSS_VARBIT)).thenThrow(new IllegalArgumentException("no such varbit"));
		assertNull(catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void unreadableCacheGivesAnEmptyList()
	{
		when(client.getDBTableRows(DBTableID.SlayerUnlock.ID)).thenThrow(new IllegalStateException("cache not loaded"));
		assertTrue(catalog.unlocks().isEmpty());
	}

	@Test
	public void rowsWithoutANameAreSkipped()
	{
		row(1, "Bigger and Badder", 150, VarbitID.SLAYER_UNLOCK_SUPERIORMOBS, 1);
		rows.add(3);
		when(client.getDBTableField(3, DBTableID.SlayerUnlock.COL_NAME, 0)).thenReturn(new Object[0]);
		assertEquals(1, catalog.unlocks().size());
		assertFalse(catalog.unlocks().get(0).getName().isEmpty());
	}
}
