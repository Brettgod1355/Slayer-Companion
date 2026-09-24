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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.data.GearItem;
import com.slayercompanion.data.GearTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * A small fake item world for the gear classes: a client item list for {@link ItemIndex}, a Grand
 * Exchange search for the tradeables, and a real {@link OwnedItems} fed by container events.
 */
class GearFixture
{
	static final int WHIP = 4151;
	static final int WHIP_OR = 26482;
	static final int TENTACLE = 12006;
	static final int NOTED_WHIP = 4152;
	static final int SLAYER_HELMET = 11864;
	static final int SLAYER_HELMET_I = 11865;
	static final int DRAGON_DEFENDER = 12954;
	static final int DRAGON_DEFENDER_T = 19722;
	static final int TRIDENT_SWAMP = 12899;
	static final int TRIDENT_SWAMP_E = 22292;
	static final int UNCHARGED_TOXIC_TRIDENT = 12900;
	static final int RUNE_DEFENDER = 8850;
	static final int RUNE_SCIMITAR = 1333;
	static final int FIRE_CAPE = 6570;
	static final int IMBUED_SARADOMIN_CAPE = 21791;
	static final int NOSE_PEG = 4168;
	static final int ARDOUGNE_CLOAK_4 = 13124;

	final Client client = mock(Client.class);
	final ItemManager itemManager = mock(ItemManager.class);
	final ConfigManager configManager = mock(ConfigManager.class);
	final ItemIndex index;
	final ItemNameResolver resolver;
	final OwnedItems owned;

	private final Map<Integer, String> names = new LinkedHashMap<>();
	private final List<Integer> tradeable = new ArrayList<>();

	GearFixture()
	{
		item(WHIP, "Abyssal whip", true);
		item(WHIP_OR, "Abyssal whip (or)", false);
		item(TENTACLE, "Abyssal tentacle", false);
		item(SLAYER_HELMET, "Slayer helmet", false);
		item(SLAYER_HELMET_I, "Slayer helmet (i)", false);
		item(DRAGON_DEFENDER, "Dragon defender", false);
		item(DRAGON_DEFENDER_T, "Dragon defender (t)", false);
		item(TRIDENT_SWAMP, "Trident of the swamp", false);
		item(TRIDENT_SWAMP_E, "Trident of the swamp (e)", false);
		item(UNCHARGED_TOXIC_TRIDENT, "Uncharged toxic trident", true);
		item(RUNE_DEFENDER, "Rune defender", false);
		item(RUNE_SCIMITAR, "Rune scimitar", true);
		item(FIRE_CAPE, "Fire cape", false);
		item(IMBUED_SARADOMIN_CAPE, "Imbued saradomin cape", false);
		item(NOSE_PEG, "Nose peg", true);
		item(ARDOUGNE_CLOAK_4, "Ardougne cloak 4", true);

		when(client.getItemCount()).thenReturn(30000);
		// A noted whip shares the name but must not be indexed.
		ItemComposition noted = mock(ItemComposition.class);
		when(noted.getName()).thenReturn("Abyssal whip");
		when(noted.getNote()).thenReturn(799);
		when(noted.getPlaceholderTemplateId()).thenReturn(-1);
		when(client.getItemDefinition(NOTED_WHIP)).thenReturn(noted);

		when(itemManager.canonicalize(anyInt())).thenAnswer(inv ->
		{
			int id = inv.getArgument(0);
			return id == NOTED_WHIP ? WHIP : id;
		});
		when(itemManager.search(anyString())).thenAnswer(inv -> search(inv.getArgument(0)));
		when(itemManager.getItemPrice(anyInt())).thenReturn(0);

		EventBus eventBus = mock(EventBus.class);
		index = new ItemIndex(client, eventBus);
		resolver = new ItemNameResolver(itemManager, index);
		owned = new OwnedItems(client, mock(ClientThread.class), itemManager, configManager, eventBus, new Gson());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
	}

	private void item(int id, String name, boolean isTradeable)
	{
		names.put(id, name);
		if (isTradeable)
		{
			tradeable.add(id);
		}
		ItemComposition c = mock(ItemComposition.class);
		when(c.getName()).thenReturn(name);
		when(c.getNote()).thenReturn(-1);
		when(c.getPlaceholderTemplateId()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(c);
	}

	/** Like the client's price search: tradeables whose name contains the query. */
	private List<ItemPrice> search(String query)
	{
		List<ItemPrice> out = new ArrayList<>();
		for (int id : tradeable)
		{
			if (names.get(id).toLowerCase().contains(query.toLowerCase()))
			{
				ItemPrice p = mock(ItemPrice.class);
				when(p.getId()).thenReturn(id);
				when(p.getName()).thenReturn(names.get(id));
				out.add(p);
			}
		}
		return out;
	}

	/** Index the given names the way the plugin does at startup, running the scan to the end. */
	GearFixture index(Collection<String> wanted)
	{
		index.want(wanted);
		while (!index.isComplete())
		{
			index.onGameTick(new GameTick());
		}
		resolver.invalidate();
		return this;
	}

	/** Index every name a table mentions. */
	GearFixture index(GearTable... tables)
	{
		List<String> wanted = new ArrayList<>();
		for (GearTable t : tables)
		{
			for (List<List<GearItem>> tiers : t.getSlots().values())
			{
				for (List<GearItem> tier : tiers)
				{
					for (GearItem i : tier)
					{
						wanted.addAll(i.candidates());
					}
				}
			}
		}
		return index(wanted);
	}

	GearFixture bank(int... ids)
	{
		container(InventoryID.BANK, ids);
		return this;
	}

	GearFixture worn(int... ids)
	{
		container(InventoryID.WORN, ids);
		return this;
	}

	GearFixture inventory(int... ids)
	{
		container(InventoryID.INV, ids);
		return this;
	}

	private void container(int containerId, int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(items);
		owned.onItemContainerChanged(new ItemContainerChanged(containerId, c));
	}

	static GearItem gi(String name)
	{
		GearItem g = new GearItem();
		g.setName(name);
		return g;
	}

	@SafeVarargs
	static List<List<GearItem>> tiers(List<GearItem>... tiers)
	{
		return Arrays.asList(tiers);
	}

	static List<GearItem> tier(String... names)
	{
		List<GearItem> out = new ArrayList<>();
		for (String n : names)
		{
			out.add(gi(n));
		}
		return out;
	}

	static GearTable table(String style, Object... slotsAndTiers)
	{
		GearTable t = new GearTable();
		t.setStyle(style);
		t.setLabel(style);
		Map<String, List<List<GearItem>>> slots = new LinkedHashMap<>();
		for (int i = 0; i < slotsAndTiers.length; i += 2)
		{
			@SuppressWarnings("unchecked")
			List<List<GearItem>> tiers = (List<List<GearItem>>) slotsAndTiers[i + 1];
			slots.put((String) slotsAndTiers[i], tiers);
		}
		t.setSlots(slots);
		return t;
	}
}
