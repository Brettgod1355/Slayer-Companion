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
package com.slayercompanion.unlocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.UnlockInfo;
import com.slayercompanion.game.LiveSlayerCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class UnlockAdvisorTest
{
	private SlayerData data;
	private LiveSlayerCatalog catalog;
	private ItemManager itemManager;
	private final List<UnlockInfo> bundled = new ArrayList<>();
	private final List<LiveSlayerCatalog.Unlock> live = new ArrayList<>();

	@Before
	public void setUp()
	{
		data = mock(SlayerData.class);
		when(data.unlocks()).thenReturn(bundled);
		catalog = mock(LiveSlayerCatalog.class);
		when(catalog.unlocks()).thenReturn(live);
		itemManager = mock(ItemManager.class);
	}

	private UnlockInfo bundle(String name, String category, int cost, int priority, String... tasks)
	{
		UnlockInfo u = new UnlockInfo();
		u.setName(name);
		u.setCategory(category);
		u.setCost(cost);
		u.setPriority(priority);
		u.setEffect(name + " (bundled)");
		u.setAffectsTasks(Arrays.asList(tasks));
		bundled.add(u);
		return u;
	}

	private LiveSlayerCatalog.Unlock live(String name, int cost, Boolean owned)
	{
		LiveSlayerCatalog.Unlock u = new LiveSlayerCatalog.Unlock(name, name + " (game)", cost, 1, live.size());
		live.add(u);
		when(catalog.isUnlocked(u)).thenReturn(owned);
		return u;
	}

	private List<UnlockAdvice> advise(int points)
	{
		return new UnlockAdvisor(data, catalog, itemManager).advise(points);
	}

	private static UnlockAdvice find(List<UnlockAdvice> list, String name)
	{
		for (UnlockAdvice u : list)
		{
			if (u.getName().equals(name))
			{
				return u;
			}
		}
		return null;
	}

	@Test
	public void liveEntriesTakeBundledAdviceByNormalisedName()
	{
		bundle("'Shroom Sprayer", "unlock", 110, 4, "Mutated zygomites");
		live("Shroom sprayer", 110, false);
		List<UnlockAdvice> out = advise(200);
		assertEquals(1, out.size());
		UnlockAdvice u = out.get(0);
		assertEquals("the game's name wins", "Shroom sprayer", u.getName());
		assertEquals(4, u.getPriority());
		assertEquals("unlock", u.getCategory());
		assertEquals("Shroom sprayer (game)", u.getEffect());
		assertEquals(Collections.singletonList("Mutated zygomites"), u.getAffectsTasks());
		assertEquals(Boolean.FALSE, u.getUnlocked());
		assertTrue(u.isAffordable());
	}

	@Test
	public void liveCostAndAffordabilityWin()
	{
		bundle("Like a Boss", "unlock", 200, 2);
		live("Like a Boss", 250, null);
		UnlockAdvice u = advise(200).get(0);
		assertEquals(250, u.getCost());
		assertFalse(u.isAffordable());
		assertNull(u.getUnlocked());
		assertTrue(advise(250).get(0).isAffordable());
	}

	@Test
	public void unknownLiveEntryHasNoOpinion()
	{
		live("Brand new unlock", 100, false);
		UnlockAdvice u = advise(0).get(0);
		assertEquals(9, u.getPriority());
		assertNull(u.getCategory());
		assertTrue(u.getAffectsTasks().isEmpty());
	}

	@Test
	public void bundledOnlyWhenTheLiveListIsMissing()
	{
		bundle("Like a Boss", "unlock", 200, 2);
		bundle("Bigger and Badder", "unlock", 150, 1);
		List<UnlockAdvice> out = advise(0);
		assertEquals(2, out.size());
		for (UnlockAdvice u : out)
		{
			assertNull("owned state is unknown without the game's list", u.getUnlocked());
			assertEquals(u.getName() + " (bundled)", u.getEffect());
		}
	}

	@Test
	public void bundledEntriesTheGameLacksAreStillListed()
	{
		bundle("Like a Boss", "unlock", 200, 2);
		bundle("Rune pouch", "buy", 750, 5);
		live("Like a Boss", 200, true);
		assertEquals(2, advise(0).size());
		assertNull(find(advise(0), "Rune pouch").getUnlocked());
	}

	@Test
	public void ownedLastThenPriorityThenCost()
	{
		bundle("A", "unlock", 300, 2);
		bundle("B", "unlock", 100, 2);
		bundle("C", "unlock", 500, 1);
		bundle("D", "unlock", 10, 1);
		live("A", 300, false);
		live("B", 100, null);
		live("C", 500, false);
		live("D", 10, true);
		List<String> order = new ArrayList<>();
		for (UnlockAdvice u : advise(0))
		{
			order.add(u.getName());
		}
		assertEquals(Arrays.asList("C", "B", "A", "D"), order);
	}

	@Test
	public void placeholderTaskNamesAreDropped()
	{
		bundle("Ring Bling", "unlock", 150, 3, "(all tasks)");
		bundle("Double Trouble", "unlock", 500, 5, "Gargoyles", "(boss variant)");
		assertTrue(find(advise(0), "Ring Bling").getAffectsTasks().isEmpty());
		assertEquals(Collections.singletonList("Gargoyles"), find(advise(0), "Double Trouble").getAffectsTasks());
	}

	@Test
	public void elsewhereLineCarriesTheLivePriceWhenKnown()
	{
		UnlockInfo pouch = bundle("Rune pouch", "buy", 750, 5);
		pouch.setElsewhere("Buy the rune pouch note on the GE.");
		pouch.setElsewhereItemId(24587);
		when(itemManager.getItemPrice(24587)).thenReturn(4_200_000);
		String line = find(advise(0), "Rune pouch").getElsewhere();
		assertTrue(line, line.startsWith("Buy the rune pouch note on the GE. "));
		assertTrue(line, line.contains("4.2M gp"));

		when(itemManager.getItemPrice(anyInt())).thenReturn(0);
		assertEquals("Buy the rune pouch note on the GE.", find(advise(0), "Rune pouch").getElsewhere());
	}

	@Test
	public void elsewhereWithoutAnItemIsPlainAndEmptyIsNull()
	{
		UnlockInfo sack = bundle("Herb sack", "buy", 750, 5);
		sack.setElsewhere("250 Tithe Farm points.");
		UnlockInfo empty = bundle("Looting bag", "buy", 10, 5);
		empty.setElsewhere("");
		assertEquals("250 Tithe Farm points.", find(advise(0), "Herb sack").getElsewhere());
		assertNull(find(advise(0), "Looting bag").getElsewhere());
	}

	@Test
	public void bundledCheaperRoutesAreLowPriority()
	{
		// Anything with a cheaper route elsewhere should never be pushed as a purchase.
		SlayerData real = new SlayerData(new Gson());
		boolean any = false;
		for (UnlockInfo u : real.unlocks())
		{
			if (u.getElsewhere() != null && !u.getElsewhere().isEmpty())
			{
				any = true;
				assertTrue(u.getName() + " has a cheaper route but priority " + u.getPriority(), u.getPriority() > 3);
			}
		}
		assertTrue("unlocks.json should name some cheaper routes", any);
		for (UnlockAdvice u : new UnlockAdvisor(real, catalog, itemManager).advise(0))
		{
			assertNotNull(u.getName());
			assertTrue(u.getName() + " has priority " + u.getPriority(), u.getPriority() >= 1 && u.getPriority() <= 5);
		}
	}
}
