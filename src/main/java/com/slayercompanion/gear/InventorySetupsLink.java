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
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Talks to the Inventory Setups plugin through its PluginMessage API (namespace
 * "inventory-setups", API version 1): lists the player's setups and asks it to open one, which
 * filters the bank the way its own menu does. Nothing happens when the plugin is not installed.
 */
@Singleton
public class InventorySetupsLink
{
	/** Constants from Inventory Setups' {@code InventorySetupsPluginMessageHandler}. */
	static final String NAMESPACE = "inventory-setups";
	static final String MESSAGE_GET_SETUPS = "get-setups";
	static final String MESSAGE_SETUPS_CHANGED = "setups-changed";
	static final String MESSAGE_VIEW = "view";
	static final String KEY_SETUPS = "setups";
	static final String KEY_SETUP = "setup";

	private final EventBus eventBus;
	private volatile List<String> setups = Collections.emptyList();

	@Inject
	InventorySetupsLink(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/** Setup names, empty when Inventory Setups is not installed or has none. */
	public List<String> setups()
	{
		return setups;
	}

	/** Ask Inventory Setups for its setup names; it fills the list before post() returns. */
	public void refresh()
	{
		List<String> names = new ArrayList<>();
		Map<String, Object> data = new HashMap<>();
		data.put(KEY_SETUPS, names);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_GET_SETUPS, data));
		setups = copy(names);
	}

	/** Take in a "setups-changed" broadcast. Returns true when the setup list changed. */
	public boolean onPluginMessage(PluginMessage message)
	{
		if (!NAMESPACE.equals(message.getNamespace()) || !MESSAGE_SETUPS_CHANGED.equals(message.getName()))
		{
			return false;
		}
		Object names = message.getData().get(KEY_SETUPS);
		if (!(names instanceof Collection))
		{
			return false;
		}
		List<String> next = copy((Collection<?>) names);
		boolean changed = !next.equals(setups);
		setups = next;
		return changed;
	}

	/** Open the setup in Inventory Setups (its panel shows it and the bank is filtered to it). */
	public void open(String setupName)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(KEY_SETUP, setupName);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_VIEW, data));
	}

	public void reset()
	{
		setups = Collections.emptyList();
	}

	private static List<String> copy(Collection<?> names)
	{
		List<String> out = new ArrayList<>();
		for (Object o : names)
		{
			if (o instanceof String)
			{
				out.add((String) o);
			}
		}
		return Collections.unmodifiableList(out);
	}
}
