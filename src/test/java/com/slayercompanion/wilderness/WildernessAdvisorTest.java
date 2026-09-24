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
package com.slayercompanion.wilderness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slayercompanion.gear.OwnedItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SkullIcon;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class WildernessAdvisorTest
{
	private static final int WHIP = 4151;
	private static final int DRAGON_BOOTS = 11840;
	private static final int AMULET_OF_GLORY = 1712;
	private static final int RUNE_PLATEBODY = 1127;
	private static final int SHARK = 385;
	private static final int FIRE_CAPE = 6570;
	private static final int COINS = 995;

	private Client client;
	private ItemManager itemManager;
	private Player player;
	private WildernessAdvisor advisor;
	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		OwnedItems owned = mock(OwnedItems.class);
		when(owned.inventory()).thenReturn(inventory);
		when(owned.equipment()).thenReturn(equipment);
		player = mock(Player.class);
		when(player.getSkullIcon()).thenReturn(SkullIcon.NONE);
		when(client.getLocalPlayer()).thenReturn(player);

		item(WHIP, "Abyssal whip", true, 1_500_000, 72_000);
		item(DRAGON_BOOTS, "Dragon boots", true, 200_000, 30_000);
		item(AMULET_OF_GLORY, "Amulet of glory(4)", true, 12_000, 10_000);
		item(RUNE_PLATEBODY, "Rune platebody", true, 38_000, 39_000);
		item(SHARK, "Shark", true, 800, 90);
		item(COINS, "Coins", true, 1, 1);
		// Untradeable: ranked by alchemy value, never counted as gold at risk.
		item(FIRE_CAPE, "Fire cape", false, 0, 40_000);

		advisor = new WildernessAdvisor(client, itemManager, owned);
	}

	private void item(int id, String name, boolean tradeable, int price, int alch)
	{
		ItemComposition c = mock(ItemComposition.class);
		when(c.getName()).thenReturn(name);
		when(c.isTradeable()).thenReturn(tradeable);
		when(c.getHaPrice()).thenReturn(alch);
		when(itemManager.getItemComposition(id)).thenReturn(c);
		when(itemManager.getItemPrice(id)).thenReturn(price);
	}

	private void levelText(String text)
	{
		Widget w = mock(Widget.class);
		when(w.getText()).thenReturn(text);
		when(client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL)).thenReturn(w);
	}

	private static List<String> names(List<WildernessStatus.CarriedItem> items)
	{
		List<String> out = new ArrayList<>();
		for (WildernessStatus.CarriedItem i : items)
		{
			out.add(i.getName() + " x" + i.getQuantity());
		}
		return out;
	}

	// --- items kept on death ---

	@Test
	public void threeItemsKeptNormally()
	{
		assertEquals(3, advisor.status().getItemsKept());
	}

	@Test
	public void protectItemKeepsOneMore()
	{
		when(client.isPrayerActive(Prayer.PROTECT_ITEM)).thenReturn(true);
		assertEquals(4, advisor.status().getItemsKept());
	}

	@Test
	public void skulledKeepsNothingOrOneWithProtectItem()
	{
		when(player.getSkullIcon()).thenReturn(SkullIcon.SKULL);
		WildernessStatus s = advisor.status();
		assertTrue(s.isSkulled());
		assertEquals(0, s.getItemsKept());
		when(client.isPrayerActive(Prayer.PROTECT_ITEM)).thenReturn(true);
		assertEquals(1, advisor.status().getItemsKept());
	}

	@Test
	public void noLocalPlayerIsTreatedAsUnskulled()
	{
		when(client.getLocalPlayer()).thenReturn(null);
		assertFalse(advisor.status().isSkulled());
		assertEquals(3, advisor.status().getItemsKept());
	}

	// --- value at risk ---

	@Test
	public void mostValuableItemsAreKeptAndTheRestIsAtRisk()
	{
		equipment.put(WHIP, 1);
		equipment.put(DRAGON_BOOTS, 1);
		equipment.put(AMULET_OF_GLORY, 1);
		equipment.put(RUNE_PLATEBODY, 1);
		inventory.put(SHARK, 10);
		WildernessStatus s = advisor.status();
		assertEquals(names(s.getKept()).toString(), 3, s.getKept().size());
		assertTrue(names(s.getKept()).contains("Abyssal whip x1"));
		assertTrue(names(s.getKept()).contains("Dragon boots x1"));
		assertTrue(names(s.getKept()).contains("Rune platebody x1"));
		assertEquals(12_000 + 8_000, s.getRiskValue());
		assertEquals(1_500_000 + 200_000 + 12_000 + 38_000 + 8_000, s.getCarriedValue());
	}

	@Test
	public void keptSlotsProtectSingleUnitsOfAStack()
	{
		inventory.put(SHARK, 10);
		WildernessStatus s = advisor.status();
		assertEquals("[Shark x3]", names(s.getKept()).toString());
		assertEquals("[Shark x7]", names(s.getLost()).toString());
		assertEquals(7 * 800, s.getRiskValue());
	}

	@Test
	public void skulledLosesEverything()
	{
		when(player.getSkullIcon()).thenReturn(SkullIcon.SKULL);
		equipment.put(WHIP, 1);
		inventory.put(COINS, 50_000);
		WildernessStatus s = advisor.status();
		assertTrue(s.getKept().isEmpty());
		assertEquals(1_500_000 + 50_000, s.getRiskValue());
	}

	@Test
	public void untradeablesTakeKeptSlotsButAreNotGoldAtRisk()
	{
		equipment.put(WHIP, 1);
		equipment.put(FIRE_CAPE, 1);
		equipment.put(DRAGON_BOOTS, 1);
		equipment.put(RUNE_PLATEBODY, 1);
		WildernessStatus s = advisor.status();
		// The cape outranks the platebody on alchemy value, so it uses the third slot.
		assertTrue(names(s.getKept()).contains("Fire cape x1"));
		assertEquals("[Rune platebody x1]", names(s.getLost()).toString());
		assertEquals(38_000, s.getRiskValue());
		assertEquals("[Fire cape x1]", names(s.getUntradeables()).toString());
		assertEquals(1_500_000 + 200_000 + 38_000, s.getCarriedValue());
	}

	@Test
	public void lostUntradeablesAreNotListedAsLost()
	{
		when(player.getSkullIcon()).thenReturn(SkullIcon.SKULL);
		equipment.put(FIRE_CAPE, 1);
		WildernessStatus s = advisor.status();
		assertTrue(s.getLost().isEmpty());
		assertEquals(0, s.getRiskValue());
		assertEquals(1, s.getUntradeables().size());
	}

	@Test
	public void tradeableWithoutAPriceFallsBackToAlchemyValue()
	{
		when(itemManager.getItemPrice(RUNE_PLATEBODY)).thenReturn(0);
		when(player.getSkullIcon()).thenReturn(SkullIcon.SKULL);
		equipment.put(RUNE_PLATEBODY, 1);
		assertEquals(39_000, advisor.status().getRiskValue());
	}

	@Test
	public void nothingCarriedIsNoRisk()
	{
		WildernessStatus s = advisor.status();
		assertEquals(0, s.getRiskValue());
		assertTrue(s.getKept().isEmpty());
		assertTrue(s.getLost().isEmpty());
	}

	// --- wilderness level ---

	@Test
	public void levelIsParsedFromTheIndicator()
	{
		assertEquals(1, WildernessAdvisor.parseWildernessLevel("Level: 1"));
		assertEquals(56, WildernessAdvisor.parseWildernessLevel("Level: 56"));
		assertEquals(0, WildernessAdvisor.parseWildernessLevel(null));
		assertEquals(0, WildernessAdvisor.parseWildernessLevel(""));
		assertEquals(0, WildernessAdvisor.parseWildernessLevel("Level: "));
		assertEquals(0, WildernessAdvisor.parseWildernessLevel("Level: abc"));
		assertEquals(0, WildernessAdvisor.parseWildernessLevel("Guarded"));
	}

	@Test
	public void statusReportsTheLevelOnlyInsideTheWilderness()
	{
		WildernessStatus outside = advisor.status();
		assertFalse(outside.isInWilderness());
		assertEquals(0, outside.getWildernessLevel());

		levelText("Level: 30");
		WildernessStatus inside = advisor.status();
		assertTrue(inside.isInWilderness());
		assertEquals(30, inside.getWildernessLevel());

		when(client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL).isHidden()).thenReturn(true);
		assertFalse(advisor.status().isInWilderness());
	}
}
