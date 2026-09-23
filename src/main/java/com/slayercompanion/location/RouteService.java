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
package com.slayercompanion.location;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Hands a destination to the Shortest Path plugin over the RuneLite event bus.
 * <p>
 * Shortest Path draws the route on the map and the ground; this plugin never moves the player,
 * clicks or touches the camera. If Shortest Path is not installed or is disabled the message is
 * simply not consumed.
 */
@Slf4j
@Singleton
public class RouteService
{
	/** Constants from Shortest Path's {@code ShortestPathPlugin}. */
	static final String SHORTEST_PATH_NAMESPACE = "shortestpath";
	static final String MESSAGE_PATH = "path";
	static final String MESSAGE_CLEAR = "clear";
	static final String KEY_TARGET = "target";
	static final String SHORTEST_PATH_PLUGIN_NAME = "Shortest Path";

	private final EventBus eventBus;
	private final PluginManager pluginManager;

	@Inject
	RouteService(EventBus eventBus, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
	}

	/** Ask Shortest Path to draw a route from the player to {@code target}. */
	public void route(WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(KEY_TARGET, target);
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, MESSAGE_PATH, data));
		log.debug("Requested route to {}", target);
	}

	/** Ask Shortest Path to clear its current route. */
	public void clear()
	{
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, MESSAGE_CLEAR));
	}

	/** True when the Shortest Path plugin is installed and enabled. */
	public boolean isShortestPathAvailable()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (SHORTEST_PATH_PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}
}
