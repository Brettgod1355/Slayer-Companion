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
package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.UnlockInfo;
import com.slayercompanion.unlocks.UnlockAdvice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class UnlocksTabTest
{
	private static UnlockAdvice u(String name, String category, int cost, int priority, Boolean owned, String elsewhere, String... tasks)
	{
		return new UnlockAdvice(name, "", cost, owned, true, priority, category, null, "", Arrays.asList(tasks), elsewhere);
	}

	/** The bundled shop as the panel sees it when the game's list is unavailable (nothing known to be owned). */
	private static List<UnlockAdvice> bundledShop()
	{
		List<UnlockAdvice> out = new ArrayList<>();
		for (UnlockInfo b : new SlayerData(new Gson()).unlocks())
		{
			out.add(new UnlockAdvice(b.getName(), "", b.getCost(), null, false, b.getPriority(), b.getCategory(), null, "",
				b.getAffectsTasks() == null ? Collections.emptyList() : b.getAffectsTasks(), b.getElsewhere()));
		}
		return out;
	}

	private static List<String> names(List<UnlockAdvice> list)
	{
		List<String> out = new ArrayList<>();
		for (UnlockAdvice a : list)
		{
			out.add(a.getName());
		}
		return out;
	}

	// --- Buy next ---

	@Test
	public void buyNextOrdersByPriorityThenCost()
	{
		List<UnlockAdvice> shop = Arrays.asList(
			u("Task Storage", "unlock", 500, 2, false, null),
			u("Like a Boss", "unlock", 200, 2, null, null),
			u("Malevolent Masquerade", "unlock", 400, 1, false, null),
			u("Bigger and Badder", "unlock", 50, 1, false, null),
			u("Slug Salter", "unlock", 10, 3, false, null));
		assertEquals(Arrays.asList("Bigger and Badder", "Malevolent Masquerade", "Like a Boss", "Task Storage", "Slug Salter"),
			names(UnlocksTab.buyNext(shop)));
	}

	@Test
	public void buyNextSkipsOwnedFreeLowPriorityAndNonPurchases()
	{
		List<UnlockAdvice> shop = Arrays.asList(
			u("Owned", "unlock", 50, 1, true, null),
			u("Toggle", "unlock", 0, 1, false, null),
			u("Situational", "unlock", 50, 4, false, null),
			u("Extension", "extend", 100, 2, false, null),
			u("Block task", "block", 40, 2, false, null),
			u("Cosmetic", "cosmetic", 200, 1, false, null),
			u("Uncategorised", null, 200, 1, false, null),
			u("Broad bolts", "buy", 35, 3, false, null),
			u("Ring Bling", "unlock", 150, 3, null, null));
		assertEquals(Arrays.asList("Broad bolts", "Ring Bling"), names(UnlocksTab.buyNext(shop)));
	}

	@Test
	public void buyNextShowsAtMostFive()
	{
		List<UnlockAdvice> shop = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			shop.add(u("Unlock " + i, "unlock", 100 - i, 1, false, null));
		}
		List<UnlockAdvice> next = UnlocksTab.buyNext(shop);
		assertEquals(5, next.size());
		assertEquals("Unlock 7", next.get(0).getName());
		assertTrue(UnlocksTab.buyNext(Collections.emptyList()).isEmpty());
	}

	@Test
	public void cheaperElsewhereItemsNeverReachBuyNext()
	{
		List<UnlockAdvice> shop = Arrays.asList(
			u("Rune pouch", "buy", 750, 5, false, "Buy the note on the GE."),
			u("Herb sack", "buy", 750, 5, false, "250 Tithe Farm points."),
			u("Broad bolts", "buy", 35, 4, false, null),
			u("Bigger and Badder", "unlock", 50, 1, false, null));
		assertEquals(Collections.singletonList("Bigger and Badder"), names(UnlocksTab.buyNext(shop)));
	}

	@Test
	public void buyNextFromTheBundledShopHasNoCheaperRoutes()
	{
		List<UnlockAdvice> shop = bundledShop();
		List<UnlockAdvice> next = UnlocksTab.buyNext(shop);
		assertFalse("the bundled shop should have something to buy", next.isEmpty());
		assertTrue(next.size() <= 5);
		int lastPriority = 0;
		for (UnlockAdvice u : next)
		{
			assertNull(u.getName() + " is cheaper elsewhere", u.getElsewhere());
			assertTrue(u.getPriority() <= 3);
			assertTrue(u.getCost() > 0);
			assertTrue(u.getPriority() >= lastPriority);
			lastPriority = u.getPriority();
		}
	}

	// --- For your task ---

	@Test
	public void forTaskMatchesTheGameNameLoosely()
	{
		List<UnlockAdvice> shop = Arrays.asList(
			u("Double Trouble", "unlock", 500, 5, false, null, "Gargoyles", "Grotesque Guardians"),
			u("Get Smashed", "extend", 100, 2, false, null, "Gargoyles"),
			u("Gargoyle Smasher", "unlock", 120, 2, false, null, "gargoyles"),
			u("Owned", "unlock", 50, 1, true, null, "Gargoyles"),
			u("Free", "unlock", 0, 1, false, null, "Gargoyles"),
			u("Other", "unlock", 50, 1, false, null, "Nechryael"));
		assertEquals(Arrays.asList("Get Smashed", "Gargoyle Smasher", "Double Trouble"), names(UnlocksTab.forTask(shop, "GARGOYLES")));
	}

	@Test
	public void forTaskIgnoresArticlesAndPunctuation()
	{
		List<UnlockAdvice> shop = Collections.singletonList(u("Like a Boss", "unlock", 200, 2, false, null, "The Abyssal Sire", "Kree'arra"));
		assertEquals(1, UnlocksTab.forTask(shop, "Abyssal Sire").size());
		assertEquals(1, UnlocksTab.forTask(shop, "KREEARRA").size());
		assertTrue(UnlocksTab.forTask(shop, "Abyssal demons").isEmpty());
	}

	@Test
	public void forTaskFindsBundledUnlocksForARealTask()
	{
		List<UnlockAdvice> forTask = UnlocksTab.forTask(bundledShop(), "Abyssal demons");
		assertFalse(forTask.isEmpty());
		for (int i = 1; i < forTask.size(); i++)
		{
			assertTrue("cheapest first", forTask.get(i - 1).getCost() <= forTask.get(i).getCost());
		}
	}
}
