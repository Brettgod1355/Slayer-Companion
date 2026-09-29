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
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.location.LocationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * The player's own loadout per task, and the Inventory Setups setup they linked to it. Both are
 * kept per account in the RuneScape profile config.
 */
@Slf4j
@Singleton
public class LoadoutStore
{
	private static final String LOADOUT_PREFIX = "loadout.";
	private static final String LINK_PREFIX = "loadoutSetup.";

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final Gson gson;
	private final OwnedItems owned;

	@Inject
	LoadoutStore(Client client, ItemManager itemManager, ConfigManager configManager, Gson gson, OwnedItems owned)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.gson = gson;
		this.owned = owned;
	}

	public Optional<Loadout> get(String taskName)
	{
		String json = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, LOADOUT_PREFIX + LocationService.slug(taskName));
		if (json == null || json.isEmpty())
		{
			return Optional.empty();
		}
		try
		{
			return Optional.ofNullable(gson.fromJson(json, Loadout.class));
		}
		catch (RuntimeException e)
		{
			log.debug("Bad saved loadout for {}", taskName, e);
			return Optional.empty();
		}
	}

	public void delete(String taskName)
	{
		configManager.unsetRSProfileConfiguration(SlayerCompanionConfig.GROUP, LOADOUT_PREFIX + LocationService.slug(taskName));
	}

	/** Save what is worn and in the inventory right now as the task's loadout. Client thread only. */
	public Loadout saveCurrent(String taskName)
	{
		Loadout loadout = new Loadout();
		loadout.setSavedAtEpochMs(System.currentTimeMillis());
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item[] items = worn.getItems();
			for (int slot = 0; slot < items.length; slot++)
			{
				if (items[slot] != null && items[slot].getId() > 0)
				{
					loadout.getEquipment().put(slot, new Loadout.Slot(items[slot].getId(), items[slot].getQuantity()));
				}
			}
		}
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		Item[] items = inv == null ? new Item[0] : inv.getItems();
		for (int i = 0; i < Loadout.INVENTORY_SIZE; i++)
		{
			Item it = i < items.length ? items[i] : null;
			loadout.getInventory().add(it != null && it.getId() > 0 ? new Loadout.Slot(it.getId(), it.getQuantity()) : new Loadout.Slot(-1, 0));
		}
		configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, LOADOUT_PREFIX + LocationService.slug(taskName), gson.toJson(loadout));
		return loadout;
	}

	/** Name of the Inventory Setups setup linked to the task. */
	public Optional<String> linkedSetup(String taskName)
	{
		String name = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, LINK_PREFIX + LocationService.slug(taskName));
		return name == null || name.isEmpty() ? Optional.empty() : Optional.of(name);
	}

	/** Link an Inventory Setups setup to the task, or unlink with null. */
	public void link(String taskName, @Nullable String setupName)
	{
		String key = LINK_PREFIX + LocationService.slug(taskName);
		if (setupName == null || setupName.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(SlayerCompanionConfig.GROUP, key);
		}
		else
		{
			configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, key, setupName);
		}
	}

	/** Every saved item with where the player has it now. Client thread only (item definitions). */
	public LoadoutDisplay display(Loadout loadout)
	{
		Map<Integer, Integer> worn = owned.equipment();
		Map<Integer, Integer> inv = owned.inventory();

		Map<Integer, LoadoutDisplay.Slot> equipment = new HashMap<>();
		for (Map.Entry<Integer, Loadout.Slot> e : loadout.getEquipment().entrySet())
		{
			Loadout.Slot s = e.getValue();
			if (s == null || s.getId() <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(s.getId());
			int wearing = worn.getOrDefault(canonical, 0);
			if (wearing >= s.getQuantity())
			{
				equipment.put(e.getKey(), slot(s, LoadoutDisplay.Status.ON_YOU, "Worn"));
			}
			else if (wearing > 0)
			{
				equipment.put(e.getKey(), slot(s, LoadoutDisplay.Status.PARTLY, "You are wearing " + wearing + " of " + s.getQuantity()));
			}
			else if (inv.getOrDefault(canonical, 0) > 0)
			{
				equipment.put(e.getKey(), slot(s, LoadoutDisplay.Status.PARTLY, "In your inventory, not worn"));
			}
			else
			{
				equipment.put(e.getKey(), away(s, canonical));
			}
		}

		// Count-aware: two saved slots of the same potion need two in the inventory.
		Map<Integer, Integer> left = new HashMap<>(inv);
		List<LoadoutDisplay.Slot> inventory = new ArrayList<>();
		for (int i = 0; i < Loadout.INVENTORY_SIZE; i++)
		{
			Loadout.Slot s = i < loadout.getInventory().size() ? loadout.getInventory().get(i) : null;
			if (s == null || s.getId() <= 0)
			{
				inventory.add(null);
				continue;
			}
			int canonical = itemManager.canonicalize(s.getId());
			int have = left.getOrDefault(canonical, 0);
			if (have >= s.getQuantity())
			{
				left.put(canonical, have - s.getQuantity());
				inventory.add(slot(s, LoadoutDisplay.Status.ON_YOU, "In your inventory"));
			}
			else if (have > 0)
			{
				left.put(canonical, 0);
				inventory.add(slot(s, LoadoutDisplay.Status.PARTLY, "You have " + have + " of " + s.getQuantity()));
			}
			else if (worn.getOrDefault(canonical, 0) > 0)
			{
				inventory.add(slot(s, LoadoutDisplay.Status.PARTLY, "Worn, not in your inventory"));
			}
			else
			{
				inventory.add(away(s, canonical));
			}
		}
		return new LoadoutDisplay(equipment, inventory);
	}

	private LoadoutDisplay.Slot away(Loadout.Slot s, int canonical)
	{
		if (owned.bank().getOrDefault(canonical, 0) > 0)
		{
			return slot(s, LoadoutDisplay.Status.IN_BANK, "In your bank");
		}
		if (!owned.isBankKnown())
		{
			return slot(s, LoadoutDisplay.Status.UNKNOWN, "Not on you (open your bank once so the plugin can check it)");
		}
		return slot(s, LoadoutDisplay.Status.NOT_OWNED, "Not in your bank or on you");
	}

	private LoadoutDisplay.Slot slot(Loadout.Slot s, LoadoutDisplay.Status status, String where)
	{
		ItemComposition comp = itemManager.getItemComposition(s.getId());
		return new LoadoutDisplay.Slot(s.getId(), s.getQuantity(), comp.isStackable(), comp.getName(), status, where);
	}
}
