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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * Saves and compares per-task loadouts. "Save current" snapshots what is worn and carried right
 * now (client thread); "compare" lists what differs from the saved setup.
 */
@Slf4j
@Singleton
public class SetupStore
{
	private static final String PREFIX = "setup.";

	@Value
	public static class Difference
	{
		String description;
		boolean missing;
	}

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final Gson gson;
	private final OwnedItems owned;

	@Inject
	SetupStore(Client client, ItemManager itemManager, ConfigManager configManager, Gson gson, OwnedItems owned)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.gson = gson;
		this.owned = owned;
	}

	public Optional<GearSetup> get(String taskName)
	{
		String json = configManager.getConfiguration(SlayerCompanionConfig.GROUP, PREFIX + LocationService.slug(taskName));
		if (json == null || json.isEmpty())
		{
			return Optional.empty();
		}
		try
		{
			return Optional.ofNullable(gson.fromJson(json, GearSetup.class));
		}
		catch (RuntimeException e)
		{
			log.debug("Bad saved setup for {}", taskName, e);
			return Optional.empty();
		}
	}

	public void delete(String taskName)
	{
		configManager.unsetConfiguration(SlayerCompanionConfig.GROUP, PREFIX + LocationService.slug(taskName));
	}

	/** Snapshot worn equipment and inventory. Client thread only. */
	public GearSetup saveCurrent(String taskName)
	{
		GearSetup setup = new GearSetup();
		setup.setName(taskName + " setup");
		setup.setTaskName(taskName);
		setup.setSavedAtEpochMs(System.currentTimeMillis());
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item[] items = worn.getItems();
			for (int slot = 0; slot < items.length; slot++)
			{
				Item it = items[slot];
				if (it != null && it.getId() > 0)
				{
					setup.getEquipment().put(slot, itemManager.canonicalize(it.getId()));
				}
			}
		}
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item it : inv.getItems())
			{
				if (it != null && it.getId() > 0)
				{
					setup.getInventory().merge(itemManager.canonicalize(it.getId()), it.getQuantity(), Integer::sum);
				}
			}
		}
		configManager.setConfiguration(SlayerCompanionConfig.GROUP, PREFIX + LocationService.slug(taskName), gson.toJson(setup));
		return setup;
	}

	/** What the player is not wearing / carrying compared with the saved setup. */
	public List<Difference> compare(GearSetup setup)
	{
		List<Difference> out = new ArrayList<>();
		Map<Integer, Integer> wornNow = owned.equipment();
		for (Map.Entry<Integer, Integer> e : setup.getEquipment().entrySet())
		{
			int id = e.getValue();
			if (!wornNow.containsKey(id))
			{
				out.add(new Difference("Wear " + name(id), !owned.owns(id)));
			}
		}
		Map<Integer, Integer> invNow = owned.inventory();
		for (Map.Entry<Integer, Integer> e : setup.getInventory().entrySet())
		{
			int id = e.getKey();
			int want = e.getValue();
			int have = invNow.getOrDefault(id, 0);
			if (have < want)
			{
				out.add(new Difference("Bring " + (want - have) + " x " + name(id), !owned.owns(id)));
			}
		}
		return out;
	}

	public String name(int itemId)
	{
		return itemManager.getItemComposition(itemId).getName();
	}
}
