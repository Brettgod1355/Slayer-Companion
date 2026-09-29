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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

public class InventorySetupsLinkTest
{
	/** Answers like Inventory Setups' InventorySetupsPluginMessageHandler (API version 1). */
	public static class FakeInventorySetups
	{
		final List<String> setups = new ArrayList<>(Arrays.asList("Vorkath ranged", "Barrage"));
		final List<Object> opened = new ArrayList<>();

		@Subscribe
		@SuppressWarnings("unchecked")
		public void onPluginMessage(PluginMessage m)
		{
			if (!"inventory-setups".equals(m.getNamespace()))
			{
				return;
			}
			if ("get-setups".equals(m.getName()))
			{
				((Collection<String>) m.getData().get("setups")).addAll(setups);
			}
			else if ("view".equals(m.getName()))
			{
				opened.add(m.getData().get("setup"));
			}
		}
	}

	private final EventBus eventBus = new EventBus();
	private final InventorySetupsLink link = new InventorySetupsLink(eventBus);

	@Test
	public void emptyWithoutInventorySetups()
	{
		link.refresh();
		assertTrue(link.setups().isEmpty());
	}

	@Test
	public void listsAndOpensSetups()
	{
		FakeInventorySetups plugin = new FakeInventorySetups();
		eventBus.register(plugin);
		link.refresh();
		assertEquals(Arrays.asList("Vorkath ranged", "Barrage"), link.setups());

		link.open("Barrage");
		assertEquals(Collections.singletonList("Barrage"), plugin.opened);
	}

	@Test
	public void followsSetupsChangedBroadcasts()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("setups", Arrays.asList("Cerberus", "Hydra"));
		data.put("version", 1);
		assertTrue(link.onPluginMessage(new PluginMessage("inventory-setups", "setups-changed", data)));
		assertEquals(Arrays.asList("Cerberus", "Hydra"), link.setups());
		assertFalse("same list again", link.onPluginMessage(new PluginMessage("inventory-setups", "setups-changed", data)));
		assertFalse(link.onPluginMessage(new PluginMessage("shortestpath", "setups-changed", data)));
		assertFalse(link.onPluginMessage(new PluginMessage("inventory-setups", "active-setup-changed", data)));
	}
}
