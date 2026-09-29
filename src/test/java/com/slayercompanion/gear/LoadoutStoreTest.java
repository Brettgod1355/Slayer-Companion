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
import static com.slayercompanion.gear.GearFixture.NOSE_PEG;
import static com.slayercompanion.gear.GearFixture.RUNE_SCIMITAR;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET_I;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import org.junit.Before;
import org.junit.Test;

public class LoadoutStoreTest
{
	private static final int DEATH_RUNE = 560;
	private static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int CAPE = EquipmentInventorySlot.CAPE.getSlotIdx();

	private final GearFixture world = new GearFixture();
	private final Map<String, String> profile = new HashMap<>();
	private LoadoutStore store;

	@Before
	public void setUp()
	{
		when(world.itemManager.getItemComposition(anyInt())).thenAnswer(inv -> world.client.getItemDefinition(inv.getArgument(0)));
		ItemComposition rune = mock(ItemComposition.class);
		when(rune.getName()).thenReturn("Death rune");
		when(rune.isStackable()).thenReturn(true);
		when(rune.getNote()).thenReturn(-1);
		when(rune.getPlaceholderTemplateId()).thenReturn(-1);
		when(world.client.getItemDefinition(DEATH_RUNE)).thenReturn(rune);

		// The RuneScape profile config, kept in a map.
		when(world.configManager.getRSProfileConfiguration(eq(SlayerCompanionConfig.GROUP), anyString()))
			.thenAnswer(inv -> profile.get(inv.getArgument(1)));
		doAnswer(inv -> profile.put(inv.getArgument(1), inv.getArgument(2)))
			.when(world.configManager).setRSProfileConfiguration(eq(SlayerCompanionConfig.GROUP), anyString(), anyString());
		doAnswer(inv -> profile.remove(inv.getArgument(1)))
			.when(world.configManager).unsetRSProfileConfiguration(eq(SlayerCompanionConfig.GROUP), anyString());

		store = new LoadoutStore(world.client, world.itemManager, world.configManager, new Gson(), world.owned);
	}

	/** What the client's containers hold when "Save" is pressed. */
	private void wearing(Item... worn)
	{
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(worn);
		when(world.client.getItemContainer(InventoryID.WORN)).thenReturn(c);
	}

	private void carrying(Item... inv)
	{
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(inv);
		when(world.client.getItemContainer(InventoryID.INV)).thenReturn(c);
	}

	private static Item[] worn(int head, int cape, int weapon)
	{
		Item[] items = new Item[14];
		items[HEAD] = new Item(head, 1);
		items[CAPE] = new Item(cape, 1);
		items[WEAPON] = new Item(weapon, 1);
		return items;
	}

	private Loadout saved()
	{
		wearing(worn(SLAYER_HELMET_I, FIRE_CAPE, WHIP));
		Item[] inv = new Item[28];
		inv[0] = new Item(DEATH_RUNE, 1000);
		inv[5] = new Item(NOSE_PEG, 1);
		inv[6] = new Item(NOSE_PEG, 1);
		carrying(inv);
		return store.saveCurrent("Aberrant spectres");
	}

	@Test
	public void saveKeepsSlotsPositionsAndAmountsPerTask()
	{
		saved();
		Loadout loaded = store.get("Aberrant spectres").get();
		assertEquals(3, loaded.getEquipment().size());
		assertEquals(WHIP, loaded.getEquipment().get(WEAPON).getId());
		assertEquals(28, loaded.getInventory().size());
		assertEquals(new Loadout.Slot(DEATH_RUNE, 1000), loaded.getInventory().get(0));
		assertEquals(-1, loaded.getInventory().get(1).getId());
		assertEquals(NOSE_PEG, loaded.getInventory().get(6).getId());
		assertFalse(store.get("Bloodveld").isPresent());

		store.delete("Aberrant spectres");
		assertFalse(store.get("Aberrant spectres").isPresent());
	}

	@Test
	public void everythingOnYouIsComplete()
	{
		Loadout loadout = saved();
		world.bank().worn(SLAYER_HELMET_I, FIRE_CAPE, WHIP);
		world.inventory();
		carryingNow(new Item(DEATH_RUNE, 1200), new Item(NOSE_PEG, 1), new Item(NOSE_PEG, 1));
		LoadoutDisplay d = store.display(loadout);
		assertTrue(d.isComplete());
		assertEquals("Slayer helmet (i)", d.getEquipment().get(HEAD).getName());
		assertTrue(d.getInventory().get(0).isStackable());
		assertNull(d.getInventory().get(1));
	}

	@Test
	public void saysWhereMissingItemsAre()
	{
		Loadout loadout = saved();
		// Whip in the bank, fire cape nowhere, helmet in the inventory instead of worn.
		world.bank(WHIP).worn();
		carryingNow(new Item(SLAYER_HELMET_I, 1), new Item(DEATH_RUNE, 400), new Item(NOSE_PEG, 1));
		LoadoutDisplay d = store.display(loadout);

		assertEquals(LoadoutDisplay.Status.PARTLY, d.getEquipment().get(HEAD).getStatus());
		assertEquals("In your inventory, not worn", d.getEquipment().get(HEAD).getWhere());
		assertEquals(LoadoutDisplay.Status.IN_BANK, d.getEquipment().get(WEAPON).getStatus());
		assertEquals(LoadoutDisplay.Status.NOT_OWNED, d.getEquipment().get(CAPE).getStatus());

		assertEquals(LoadoutDisplay.Status.PARTLY, d.getInventory().get(0).getStatus());
		assertEquals("You have 400 of 1000", d.getInventory().get(0).getWhere());
		// Two nose pegs saved, one carried: the first slot is covered, the second is not.
		assertEquals(LoadoutDisplay.Status.ON_YOU, d.getInventory().get(5).getStatus());
		assertEquals(LoadoutDisplay.Status.NOT_OWNED, d.getInventory().get(6).getStatus());
		assertFalse(d.isComplete());
		assertEquals(2, d.count(LoadoutDisplay.Status.NOT_OWNED));
	}

	@Test
	public void unseenBankIsUnknownNotMissing()
	{
		Loadout loadout = saved();
		world.worn(RUNE_SCIMITAR);
		LoadoutDisplay d = store.display(loadout);
		assertEquals(LoadoutDisplay.Status.UNKNOWN, d.getEquipment().get(WEAPON).getStatus());
	}

	@Test
	public void linkAndUnlinkAnInventorySetup()
	{
		assertFalse(store.linkedSetup("Vorkath").isPresent());
		store.link("Vorkath", "Vorkath ranged");
		assertEquals("Vorkath ranged", store.linkedSetup("Vorkath").get());
		store.link("Vorkath", null);
		assertFalse(store.linkedSetup("Vorkath").isPresent());
	}

	/** Feed the live inventory (with quantities) to OwnedItems. */
	private void carryingNow(Item... items)
	{
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(items);
		world.owned.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(InventoryID.INV, c));
	}
}
