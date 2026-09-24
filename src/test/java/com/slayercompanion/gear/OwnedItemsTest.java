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

import static com.slayercompanion.gear.GearFixture.FIRE_CAPE;
import static com.slayercompanion.gear.GearFixture.NOTED_WHIP;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slayercompanion.SlayerCompanionConfig;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

public class OwnedItemsTest
{
	private final GearFixture world = new GearFixture();
	private final OwnedItems owned = world.owned;

	private void container(int containerId, Item... items)
	{
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(items);
		owned.onItemContainerChanged(new ItemContainerChanged(containerId, c));
	}

	private static GameStateChanged state(GameState s)
	{
		GameStateChanged e = new GameStateChanged();
		e.setGameState(s);
		return e;
	}

	@Test
	public void bankIsCountedByCanonicalIdAndPersisted()
	{
		// Noted and unnoted whips merge; bank placeholders (quantity 0) and empty slots are ignored.
		container(InventoryID.BANK, new Item(WHIP, 1), new Item(NOTED_WHIP, 4), new Item(FIRE_CAPE, 0), new Item(-1, 0), null);
		assertEquals(5, owned.quantity(WHIP));
		assertFalse(owned.owns(FIRE_CAPE));
		assertTrue(owned.isBankKnown());
		assertTrue(owned.isBankSeenThisSession());
		verify(world.configManager).setRSProfileConfiguration(SlayerCompanionConfig.GROUP, "bankSnapshot", "{\"" + WHIP + "\":5}");
	}

	@Test
	public void quantitiesAddUpAcrossContainers()
	{
		container(InventoryID.BANK, new Item(WHIP, 1));
		container(InventoryID.INV, new Item(WHIP, 1));
		container(InventoryID.WORN, new Item(WHIP, 1), new Item(FIRE_CAPE, 1));
		assertEquals(3, owned.quantity(WHIP));
		assertTrue(owned.carrying(FIRE_CAPE));
		assertEquals(Integer.valueOf(1), owned.equipment().get(FIRE_CAPE));

		// A container update replaces what was there.
		container(InventoryID.WORN, new Item(WHIP, 1));
		assertFalse(owned.owns(FIRE_CAPE));
		container(InventoryID.INV);
		container(InventoryID.WORN);
		assertFalse("the bank does not count as carried", owned.carrying(WHIP));
		assertTrue(owned.owns(WHIP));
	}

	@Test
	public void emptyBankIsNotPersisted()
	{
		container(InventoryID.BANK);
		verify(world.configManager, never()).setRSProfileConfiguration(anyString(), anyString(), anyString());
	}

	@Test
	public void persistedBankIsLoadedAtLogin()
	{
		when(world.configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, "bankSnapshot"))
			.thenReturn("{\"" + WHIP + "\":2}");
		owned.onGameStateChanged(state(GameState.LOGGED_IN));
		assertTrue(owned.isBankKnown());
		assertFalse(owned.isBankSeenThisSession());
		assertEquals(2, owned.quantity(WHIP));
	}

	@Test
	public void liveBankIsNotReplacedByTheSnapshot()
	{
		container(InventoryID.BANK, new Item(FIRE_CAPE, 1));
		when(world.configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, "bankSnapshot"))
			.thenReturn("{\"" + WHIP + "\":2}");
		owned.onGameStateChanged(state(GameState.LOGGED_IN));
		assertTrue(owned.owns(FIRE_CAPE));
		assertFalse(owned.owns(WHIP));
	}

	@Test
	public void loggingOutForgetsTheBank()
	{
		container(InventoryID.BANK, new Item(WHIP, 1));
		owned.onGameStateChanged(state(GameState.LOGIN_SCREEN));
		assertFalse(owned.owns(WHIP));
		assertFalse(owned.isBankKnown());
		assertFalse(owned.isBankSeenThisSession());

		// The next account has no snapshot.
		when(world.configManager.getRSProfileConfiguration(eq(SlayerCompanionConfig.GROUP), anyString())).thenReturn(null);
		owned.onGameStateChanged(state(GameState.LOGGED_IN));
		assertFalse(owned.isBankKnown());
	}

	@Test
	public void corruptSnapshotIsIgnored()
	{
		when(world.configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, "bankSnapshot")).thenReturn("{not json");
		owned.onGameStateChanged(state(GameState.LOGGED_IN));
		assertFalse(owned.isBankKnown());
		assertEquals(0, owned.quantity(WHIP));
	}
}
