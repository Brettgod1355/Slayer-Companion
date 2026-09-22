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
package com.slayercompanion.tracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.events.SessionUpdated;
import com.slayercompanion.events.TaskChanged;
import com.slayercompanion.task.CurrentTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

/**
 * Tracks loot received from task targets and supplies consumed while on a task, valued at Grand
 * Exchange prices, so the panel can show profit for the current assignment.
 * <p>
 * Supplies are detected as decreases of the inventory while the bank is closed for items that
 * are food ("Eat"), potions ("Drink"), or stackable ammunition and runes. Trading, dropping and
 * shop use also register as consumption; that is an accepted limitation of a passive tracker.
 * The session is persisted per RuneScape profile so it survives a relog.
 */
@Slf4j
@Singleton
public class TaskSessionTracker
{
	private static final String SESSION_KEY = "session";
	private static final String HISTORY_KEY = "sessionHistory";
	private static final int HISTORY_LIMIT = 30;
	/** Item names (lowercase) that count as supplies when their stack shrinks. */
	private static final List<String> STACKABLE_SUPPLY_SUFFIXES = Arrays.asList(
		"rune", "arrow", "bolt", "bolts (e)", "dart", "knife", "javelin", "thrownaxe", "chinchompa",
		"scales", "cannonball", "bolt rack", "brutal arrow", "atlatl dart");

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final EventBus eventBus;
	private final Gson gson;
	private final SlayerCompanionConfig config;

	@Nullable
	private TaskSession session;
	private final List<Pattern> targetNames = new ArrayList<>();
	private final Map<Integer, Integer> lastInventory = new HashMap<>();
	private boolean bankOpen;
	private int lastSlayerXp = -1;
	private int dirtyWrites;
	private Function<String, List<String>> alternativeNames = name -> Collections.emptyList();
	private Function<String, java.util.Set<Integer>> targetNpcIds = name -> Collections.emptySet();
	private java.util.Set<Integer> npcIds = Collections.emptySet();

	@Inject
	TaskSessionTracker(Client client, ItemManager itemManager, ConfigManager configManager, EventBus eventBus,
		Gson gson, SlayerCompanionConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.eventBus = eventBus;
		this.gson = gson;
		this.config = config;
	}

	public void startUp()
	{
		eventBus.register(this);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			load();
		}
	}

	public void shutDown()
	{
		persist();
		eventBus.unregister(this);
		session = null;
		targetNames.clear();
		lastInventory.clear();
		lastSlayerXp = -1;
	}

	public Optional<TaskSession> current()
	{
		return Optional.ofNullable(session);
	}

	public List<TaskSession> history()
	{
		String json = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, HISTORY_KEY);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}
		try
		{
			List<TaskSession> list = gson.fromJson(json, new TypeToken<List<TaskSession>>()
			{
			}.getType());
			return list == null ? Collections.emptyList() : list;
		}
		catch (RuntimeException e)
		{
			return Collections.emptyList();
		}
	}

	/** Discard the current session's numbers and start counting again. */
	public void resetCurrent()
	{
		if (session == null)
		{
			return;
		}
		TaskSession fresh = new TaskSession();
		fresh.setTaskName(session.getTaskName());
		fresh.setMasterName(session.getMasterName());
		fresh.setInitialAmount(session.getInitialAmount());
		fresh.setStartedAtEpochMs(System.currentTimeMillis());
		fresh.setUpdatedAtEpochMs(fresh.getStartedAtEpochMs());
		session = fresh;
		persist();
		eventBus.post(new SessionUpdated(session));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			load();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			persist();
			lastInventory.clear();
			lastSlayerXp = -1;
		}
	}

	@Subscribe
	public void onTaskChanged(TaskChanged event)
	{
		CurrentTask task = event.getCurrent();
		if (event.isCompleted() || (task == null && session != null))
		{
			if (session != null)
			{
				session.setCompleted(true);
				session.setUpdatedAtEpochMs(System.currentTimeMillis());
				archive(session);
			}
			session = null;
			targetNames.clear();
			persist();
			eventBus.post(new SessionUpdated(null));
			return;
		}
		if (task == null)
		{
			return;
		}
		if (event.isNewAssignment() || session == null || !task.getName().equalsIgnoreCase(session.getTaskName()))
		{
			if (session != null && !session.isCompleted())
			{
				archive(session);
			}
			session = new TaskSession();
			session.setTaskName(task.getName());
			session.setMasterName(task.getMaster() == null ? null : task.getMaster().getDisplayName());
			session.setInitialAmount(task.getInitialAmount());
			session.setStartedAtEpochMs(System.currentTimeMillis());
			session.setUpdatedAtEpochMs(session.getStartedAtEpochMs());
			rebuildTargetNames(task.getName());
		}
		session.setKills(task.getKills());
		session.setUpdatedAtEpochMs(System.currentTimeMillis());
		persistLazily();
		eventBus.post(new SessionUpdated(session));
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (session == null || !isTarget(event.getComposition()))
		{
			return;
		}
		long added = 0;
		for (ItemStack stack : event.getItems())
		{
			int id = itemManager.canonicalize(stack.getId());
			session.getLoot().merge(id, stack.getQuantity(), Integer::sum);
			added += (long) itemManager.getItemPrice(id) * stack.getQuantity();
		}
		session.setLootValue(session.getLootValue() + added);
		session.setUpdatedAtEpochMs(System.currentTimeMillis());
		persistLazily();
		eventBus.post(new SessionUpdated(session));
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = false;
			// Re-baseline so withdrawals are not counted as consumption.
			ItemContainer inv = client.getItemContainer(InventoryID.INV);
			if (inv != null)
			{
				snapshot(inv, lastInventory);
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || event.getItemContainer() == null)
		{
			return;
		}
		Map<Integer, Integer> now = new HashMap<>();
		snapshot(event.getItemContainer(), now);
		if (session != null && config.trackSupplies() && !bankOpen && !lastInventory.isEmpty())
		{
			long used = 0;
			for (Map.Entry<Integer, Integer> e : lastInventory.entrySet())
			{
				int id = e.getKey();
				int before = e.getValue();
				int after = now.getOrDefault(id, 0);
				if (after >= before || !isSupply(id))
				{
					continue;
				}
				int consumed = before - after;
				session.getSupplies().merge(id, consumed, Integer::sum);
				used += (long) itemManager.getItemPrice(id) * consumed;
			}
			if (used > 0)
			{
				session.setSuppliesValue(session.getSuppliesValue() + used);
				session.setUpdatedAtEpochMs(System.currentTimeMillis());
				persistLazily();
				eventBus.post(new SessionUpdated(session));
			}
		}
		lastInventory.clear();
		lastInventory.putAll(now);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.SLAYER)
		{
			return;
		}
		int xp = event.getXp();
		if (lastSlayerXp >= 0 && session != null && xp > lastSlayerXp)
		{
			session.setSlayerXpGained(session.getSlayerXpGained() + (xp - lastSlayerXp));
			persistLazily();
			eventBus.post(new SessionUpdated(session));
		}
		lastSlayerXp = xp;
	}

	private boolean isSupply(int canonicalId)
	{
		ItemComposition comp = itemManager.getItemComposition(canonicalId);
		String[] actions = comp.getInventoryActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if ("Eat".equals(a) || "Drink".equals(a))
				{
					return true;
				}
			}
		}
		String name = comp.getName().toLowerCase();
		for (String extra : Text.fromCSV(config.supplyItemNames()))
		{
			if (!extra.isEmpty() && name.equals(extra.trim().toLowerCase()))
			{
				return true;
			}
		}
		if (comp.isStackable())
		{
			for (String suffix : STACKABLE_SUPPLY_SUFFIXES)
			{
				if (name.endsWith(suffix) || name.endsWith(suffix + "s"))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void snapshot(ItemContainer container, Map<Integer, Integer> into)
	{
		into.clear();
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			into.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
		}
	}

	private void rebuildTargetNames(String taskName)
	{
		targetNames.clear();
		npcIds = targetNpcIds.apply(taskName);
		targetNames.add(targetNamePattern(taskName));
		targetNames.add(targetNamePattern(taskName.replaceAll("s$", "")));
		for (String alt : alternativeNames.apply(taskName))
		{
			targetNames.add(targetNamePattern(alt));
		}
	}

	/** Supplies the wiki's NPC ids for a task, matched before the name patterns. Set by the plugin. */
	public void setTargetNpcIds(Function<String, java.util.Set<Integer>> provider)
	{
		this.targetNpcIds = provider == null ? name -> Collections.emptySet() : provider;
	}

	/** Supplies extra NPC names that count for a task (superiors, boss variants). Set by the plugin. */
	public void setAlternativeNames(Function<String, List<String>> provider)
	{
		this.alternativeNames = provider == null ? name -> Collections.emptyList() : provider;
	}

	private static Pattern targetNamePattern(String targetName)
	{
		return Pattern.compile("(?:\\s|^)" + Pattern.quote(targetName) + "(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	}

	private boolean isTarget(NPCComposition composition)
	{
		if (composition == null)
		{
			return false;
		}
		if (npcIds.contains(composition.getId()))
		{
			return true;
		}
		if (composition.getName() == null)
		{
			return false;
		}
		String name = Text.removeTags(composition.getName()).replace(' ', ' ').toLowerCase();
		for (Pattern p : targetNames)
		{
			if (p.matcher(name).find())
			{
				return true;
			}
		}
		return false;
	}

	private void archive(TaskSession done)
	{
		List<TaskSession> history = new ArrayList<>(history());
		history.add(0, done);
		while (history.size() > HISTORY_LIMIT)
		{
			history.remove(history.size() - 1);
		}
		configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, HISTORY_KEY, gson.toJson(history));
	}

	private void persistLazily()
	{
		if (++dirtyWrites >= 5)
		{
			persist();
		}
	}

	private void persist()
	{
		dirtyWrites = 0;
		if (session == null)
		{
			configManager.unsetRSProfileConfiguration(SlayerCompanionConfig.GROUP, SESSION_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, SESSION_KEY, gson.toJson(session));
		}
	}

	private void load()
	{
		String json = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP, SESSION_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			TaskSession saved = gson.fromJson(json, TaskSession.class);
			if (saved != null && saved.getTaskName() != null)
			{
				session = saved;
				rebuildTargetNames(saved.getTaskName());
				eventBus.post(new SessionUpdated(session));
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read saved task session", e);
		}
	}
}
