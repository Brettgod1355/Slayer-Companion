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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Name-to-id index built from the client's own item definitions, so untradeable gear (Slayer
 * helmets, capes, Void, Barrows gloves...) resolves as well as tradeables. The scan runs after
 * login in slices of a few thousand items per game tick to avoid a stutter, and only remembers
 * names the bundled data asks for.
 */
@Slf4j
@Singleton
public class ItemIndex
{
	private static final int ITEMS_PER_TICK = 2500;

	private final Client client;
	private final EventBus eventBus;

	private final Set<String> wanted = new HashSet<>();
	private final Map<String, List<Integer>> byName = new HashMap<>();
	private int nextId;
	private int total;
	@Getter
	private volatile boolean complete;

	@Inject
	ItemIndex(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		synchronized (this)
		{
			byName.clear();
			wanted.clear();
			nextId = 0;
			total = 0;
			complete = false;
		}
	}

	/** Names (any case) the index should remember. Restarts the scan when new names arrive. */
	public synchronized void want(Collection<String> names)
	{
		boolean added = false;
		for (String n : names)
		{
			if (n != null && !n.isEmpty())
			{
				added |= wanted.add(n.trim().toLowerCase());
			}
		}
		if (added)
		{
			nextId = 0;
			complete = false;
		}
	}

	/** Canonical item ids whose name equals {@code name} (case-insensitive); empty when unknown or not yet scanned. */
	public synchronized List<Integer> ids(String name)
	{
		List<Integer> l = byName.get(name.trim().toLowerCase());
		return l == null ? Collections.emptyList() : l;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && total == 0)
		{
			total = client.getItemCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (complete || wanted.isEmpty())
		{
			return;
		}
		if (total == 0)
		{
			total = client.getItemCount();
			if (total == 0)
			{
				return;
			}
		}
		int end = Math.min(total, nextId + ITEMS_PER_TICK);
		synchronized (this)
		{
			for (int id = nextId; id < end; id++)
			{
				ItemComposition c;
				try
				{
					c = client.getItemDefinition(id);
				}
				catch (RuntimeException e)
				{
					continue;
				}
				if (c == null || c.getNote() != -1 || c.getPlaceholderTemplateId() != -1)
				{
					continue;
				}
				String name = c.getName();
				if (name == null || name.isEmpty() || "null".equals(name))
				{
					continue;
				}
				String key = name.toLowerCase();
				if (wanted.contains(key))
				{
					byName.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
				}
			}
			nextId = end;
			if (nextId >= total)
			{
				complete = true;
				log.debug("Item index complete: {} of {} wanted names found", byName.size(), wanted.size());
			}
		}
	}
}
