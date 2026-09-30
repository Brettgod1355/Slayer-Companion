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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

public class LiveSlayerCatalogTest
{
	// Bit indexes from the game cache (SlayerUnlock.COL_BIT).
	private static final int GARGOYLE_SMASHER_BIT = 0;
	private static final int LIKE_A_BOSS_BIT = 19;
	private static final int BIGGER_AND_BADDER_BIT = 35;
	private static final int TASK_STORAGE_BIT = 51;
	private static final int FROST_DRAGONS_BIT = 65;

	private Client client;
	private LiveSlayerCatalog catalog;
	private final List<Integer> rows = new ArrayList<>();
	private final int[] varps = new int[3];

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getDBTableRows(DBTableID.SlayerUnlock.ID)).thenReturn(rows);
		when(client.getVarpValue(VarPlayerID.SLAYER_REWARDS_UNLOCKS)).thenAnswer(i -> varps[0]);
		when(client.getVarpValue(VarPlayerID.SLAYER_REWARDS_UNLOCKS1)).thenAnswer(i -> varps[1]);
		when(client.getVarpValue(VarPlayerID.SLAYER_REWARDS_UNLOCKS2)).thenAnswer(i -> varps[2]);
		// The varbit RuneLite names for Bigger and Badder reads the same bit the game keeps it in.
		when(client.getVarbitValue(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS)).thenAnswer(i -> (varps[1] >>> 3) & 1);
		catalog = new LiveSlayerCatalog(client);
	}

	private void row(int row, String name, int cost, int bit, int page, int position)
	{
		rows.add(row);
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_NAME, 0)).thenReturn(new Object[]{name});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_DESCRIPTION, 0)).thenReturn(new Object[]{name + " description"});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_COST, 0)).thenReturn(new Object[]{cost});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_BIT, 0)).thenReturn(new Object[]{bit});
		when(client.getDBTableField(row, DBTableID.SlayerUnlock.COL_LIST_POSITION, 0)).thenReturn(new Object[]{page, position});
	}

	private void set(int bit)
	{
		varps[bit / 32] |= 1 << (bit % 32);
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

	private void shop()
	{
		row(1, "Like a Boss", 200, LIKE_A_BOSS_BIT, 0, 11);
		row(2, "Bigger and Badder", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		row(3, "I see Dragons", 100, FROST_DRAGONS_BIT, 1, 28);
		row(4, "Gargoyle Smasher", 120, GARGOYLE_SMASHER_BIT, 0, 0);
		row(5, "Task Storage", 500, TASK_STORAGE_BIT, 0, 18);
	}

	@Test
	public void unlocksAreReadInShopOrder()
	{
		shop();
		List<LiveSlayerCatalog.Unlock> unlocks = catalog.unlocks();
		assertEquals(5, unlocks.size());
		assertEquals("Gargoyle Smasher", unlocks.get(0).getName());
		assertEquals("Like a Boss", unlocks.get(1).getName());
		assertEquals(200, unlocks.get(1).getCost());
		assertEquals("Like a Boss description", unlocks.get(1).getDescription());
		assertEquals("extensions come after the unlocks page", "I see Dragons", unlocks.get(4).getName());
	}

	@Test
	public void ownedStateIsTheUnlockBitAcrossTheThreeVarps()
	{
		shop();
		for (String name : new String[]{"Like a Boss", "Bigger and Badder", "I see Dragons", "Gargoyle Smasher", "Task Storage"})
		{
			assertEquals(name, Boolean.FALSE, catalog.isUnlocked(find(name)));
		}
		set(LIKE_A_BOSS_BIT);
		set(TASK_STORAGE_BIT);
		set(FROST_DRAGONS_BIT);
		set(GARGOYLE_SMASHER_BIT);
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Like a Boss")));
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Task Storage")));
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("I see Dragons")));
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Gargoyle Smasher")));
		assertEquals(Boolean.FALSE, catalog.isUnlocked(find("Bigger and Badder")));
		set(BIGGER_AND_BADDER_BIT);
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Bigger and Badder")));
	}

	@Test
	public void topBitOfAVarpCounts()
	{
		row(2, "Bigger and Badder", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		row(6, "Top bit", 10, 31, 0, 30);
		varps[0] = Integer.MIN_VALUE;
		assertEquals(Boolean.TRUE, catalog.isUnlocked(find("Top bit")));
		assertTrue(LiveSlayerCatalog.bitSet(Integer.MIN_VALUE, 63));
		assertFalse(LiveSlayerCatalog.bitSet(Integer.MAX_VALUE, 31));
	}

	@Test
	public void aSanityBitThatDisagreesWithItsVarbitGivesUnknown()
	{
		// The layout changed: the Bigger and Badder row no longer points at the bit its varbit reads.
		row(2, "Bigger and Badder", 50, 36, 0, 12);
		row(1, "Like a Boss", 200, LIKE_A_BOSS_BIT, 0, 11);
		set(BIGGER_AND_BADDER_BIT);
		assertNull(catalog.isUnlocked(find("Like a Boss")));
		assertNull(catalog.isUnlocked(find("Bigger and Badder")));
	}

	@Test
	public void missingSanityRowGivesUnknown()
	{
		row(1, "Like a Boss", 200, LIKE_A_BOSS_BIT, 0, 11);
		set(LIKE_A_BOSS_BIT);
		assertNull(catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void sanityRowNameIsMatchedLoosely()
	{
		row(2, "Bigger & Badder", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		row(1, "Like a Boss", 200, LIKE_A_BOSS_BIT, 0, 11);
		assertNull("'&' is not 'and'", catalog.isUnlocked(find("Like a Boss")));

		catalog.reset();
		rows.clear();
		row(2, "BIGGER AND BADDER!", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		row(1, "Like a Boss", 200, LIKE_A_BOSS_BIT, 0, 11);
		assertEquals(Boolean.FALSE, catalog.isUnlocked(find("Like a Boss")));
	}

	@Test
	public void bitOutOfRangeOrMissingGivesUnknown()
	{
		row(2, "Bigger and Badder", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		row(7, "Far bit", 10, 96, 0, 40);
		rows.add(8);
		when(client.getDBTableField(8, DBTableID.SlayerUnlock.COL_NAME, 0)).thenReturn(new Object[]{"No bit"});
		assertNull(catalog.isUnlocked(find("Far bit")));
		assertEquals(-1, find("No bit").getBit());
		assertNull(catalog.isUnlocked(find("No bit")));
	}

	@Test
	public void failingVarReadGivesUnknown()
	{
		shop();
		when(client.getVarpValue(VarPlayerID.SLAYER_REWARDS_UNLOCKS)).thenThrow(new IllegalStateException("not logged in"));
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
		row(2, "Bigger and Badder", 50, BIGGER_AND_BADDER_BIT, 0, 12);
		rows.add(3);
		when(client.getDBTableField(3, DBTableID.SlayerUnlock.COL_NAME, 0)).thenReturn(new Object[0]);
		assertEquals(1, catalog.unlocks().size());
		assertFalse(catalog.unlocks().get(0).getName().isEmpty());
	}
}
