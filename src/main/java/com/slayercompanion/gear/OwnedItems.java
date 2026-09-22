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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.events.OwnedItemsChanged;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Tracks which items the player owns: the bank as last seen, plus the live inventory and worn
 * equipment. Quantities are keyed by canonical item id (unnoted, un-placeholdered).
 * <p>
 * The bank snapshot is persisted per RuneScape profile (ids and quantities only) so gear advice
 * works right after login, before the bank has been opened this session.
 */
@Slf4j
@Singleton
public class OwnedItems
{
	private static final String BANK_KEY = "bankSnapshot";

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final EventBus eventBus;
	private final Gson gson;

	private final Map<Integer, Integer> bank = new HashMap<>();
	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();

	/** True once the bank has been read from the live client in this session. */
	@Getter
	private boolean bankSeenThisSession;
	/** True when a bank snapshot (live or persisted) is available at all. */
	@Getter
	private boolean bankKnown;

	@Inject
	OwnedItems(Client client, ItemManager itemManager, ConfigManager configManager, EventBus eventBus, Gson gson)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.eventBus = eventBus;
		this.gson = gson;
	}

	public void startUp()
	{
		eventBus.register(this);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadPersistedBank();
		}
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		bank.clear();
		inventory.clear();
		equipment.clear();
		bankSeenThisSession = false;
		bankKnown = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadPersistedBank();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}
		if (id == InventoryID.BANK)
		{
			read(container, bank);
			bankSeenThisSession = true;
			bankKnown = true;
			persistBank();
			eventBus.post(new OwnedItemsChanged(true));
		}
		else if (id == InventoryID.INV)
		{
			read(container, inventory);
			eventBus.post(new OwnedItemsChanged(false));
		}
		else if (id == InventoryID.WORN)
		{
			read(container, equipment);
			eventBus.post(new OwnedItemsChanged(false));
		}
	}

	private void read(ItemContainer container, Map<Integer, Integer> into)
	{
		into.clear();
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(item.getId());
			into.merge(canonical, item.getQuantity(), Integer::sum);
		}
	}

	private void persistBank()
	{
		if (bank.isEmpty())
		{
			return;
		}
		configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, BANK_KEY, gson.toJson(bank));
	}

	private void loadPersistedBank()
	{
		if (bankSeenThisSession)
		{
			return;
		}
		String json = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, BANK_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<Integer, Integer> saved = gson.fromJson(json, new TypeToken<Map<Integer, Integer>>()
			{
			}.getType());
			if (saved != null)
			{
				bank.clear();
				bank.putAll(saved);
				bankKnown = true;
				eventBus.post(new OwnedItemsChanged(true));
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read persisted bank snapshot", e);
		}
	}

	/** Total quantity owned across bank, inventory and equipment for a canonical item id. */
	public int quantity(int canonicalItemId)
	{
		return bank.getOrDefault(canonicalItemId, 0)
			+ inventory.getOrDefault(canonicalItemId, 0)
			+ equipment.getOrDefault(canonicalItemId, 0);
	}

	public boolean owns(int canonicalItemId)
	{
		return quantity(canonicalItemId) > 0;
	}

	/** True when the item is currently in the inventory or worn. */
	public boolean carrying(int canonicalItemId)
	{
		return inventory.getOrDefault(canonicalItemId, 0) + equipment.getOrDefault(canonicalItemId, 0) > 0;
	}

	public Map<Integer, Integer> bank()
	{
		return Collections.unmodifiableMap(bank);
	}

	public Map<Integer, Integer> inventory()
	{
		return Collections.unmodifiableMap(inventory);
	}

	public Map<Integer, Integer> equipment()
	{
		return Collections.unmodifiableMap(equipment);
	}
}
