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
package com.slayercompanion;

import com.google.inject.Provides;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import com.slayercompanion.events.OwnedItemsChanged;
import com.slayercompanion.events.SessionUpdated;
import com.slayercompanion.events.TaskChanged;
import com.slayercompanion.game.LiveSlayerCatalog;
import com.slayercompanion.gear.GearAdvisor;
import com.slayercompanion.gear.GearSetup;
import com.slayercompanion.gear.ItemIndex;
import com.slayercompanion.gear.ItemNameResolver;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.SetupStore;
import com.slayercompanion.gear.SlotAdvice;
import com.slayercompanion.gear.UpgradeAdvisor;
import com.slayercompanion.location.LocationService;
import com.slayercompanion.location.MapMarkerService;
import com.slayercompanion.location.RouteService;
import com.slayercompanion.points.PointsPlanner;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import com.slayercompanion.task.TaskTracker;
import com.slayercompanion.tracker.TaskSession;
import com.slayercompanion.tracker.TaskSessionTracker;
import com.slayercompanion.ui.PanelActions;
import com.slayercompanion.ui.PanelModel;
import com.slayercompanion.ui.SlayerCompanionPanel;
import com.slayercompanion.ui.TaskOverlay;
import com.slayercompanion.unlocks.UnlockAdvisor;
import com.slayercompanion.wilderness.WildernessAdvisor;
import com.slayercompanion.wilderness.WildernessStatus;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "Slayer Companion",
	description = "Locations, routing, gear from your bank, points planning, supplies and profit, wilderness risk and unlock advice for your Slayer task",
	tags = {"slayer", "task", "gear", "location", "wilderness", "points", "konar", "supplies", "profit", "unlocks"}
)
public class SlayerCompanionPlugin extends Plugin
{
	/** Shown in the panel footer; bumped together with build.gradle and runelite-plugin.properties. */
	public static final String VERSION = "0.4.0";

	private static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";
	/** Refresh the Wilderness numbers at most this often (game ticks). */
	private static final int WILDERNESS_REFRESH_TICKS = 5;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private SlayerCompanionConfig config;

	@Inject
	private SlayerData data;
	@Inject
	private TaskTracker taskTracker;
	@Inject
	private OwnedItems ownedItems;
	@Inject
	private ItemIndex itemIndex;
	@Inject
	private ItemNameResolver itemNameResolver;
	@Inject
	private LiveSlayerCatalog catalog;
	@Inject
	private com.slayercompanion.game.AccessChecker accessChecker;
	@Inject
	private LocationService locationService;
	@Inject
	private RouteService routeService;
	@Inject
	private MapMarkerService mapMarkerService;
	@Inject
	private GearAdvisor gearAdvisor;
	@Inject
	private SetupStore setupStore;
	@Inject
	private UpgradeAdvisor upgradeAdvisor;
	@Inject
	private PointsPlanner pointsPlanner;
	@Inject
	private TaskSessionTracker sessionTracker;
	@Inject
	private WildernessAdvisor wildernessAdvisor;
	@Inject
	private UnlockAdvisor unlockAdvisor;
	@Inject
	private TaskOverlay overlay;

	private SlayerCompanionPanel panel;
	private NavigationButton navButton;
	private volatile CurrentTask overlayTask;
	private volatile TaskSession overlaySession;
	private volatile WildernessStatus overlayWilderness;
	private int tickCounter;
	private final java.util.concurrent.atomic.AtomicBoolean refreshQueued = new java.util.concurrent.atomic.AtomicBoolean();
	private boolean indexWasComplete;

	@Override
	protected void startUp()
	{
		panel = new SlayerCompanionPanel(new Actions(), data.wilderness());
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/slayercompanion/panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Companion")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlay.bind(() -> overlayTask, () -> overlaySession, () -> overlayWilderness);
		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}

		sessionTracker.setAlternativeNames(name -> data.task(name).map(TaskInfo::alternativesOrEmpty).orElse(Collections.emptyList()));
		sessionTracker.setTargetNpcIds(name -> data.task(name).map(TaskInfo::npcIds).orElse(Collections.emptySet()));
		itemIndex.startUp();
		itemIndex.want(data.allItemNames());
		ownedItems.startUp();
		sessionTracker.startUp();
		taskTracker.startUp();
		requestRefresh();
		log.debug("Slayer Companion {} started", VERSION);
	}

	@Override
	protected void shutDown()
	{
		taskTracker.shutDown();
		sessionTracker.shutDown();
		ownedItems.shutDown();
		itemIndex.shutDown();
		itemNameResolver.invalidate();
		mapMarkerService.clear();
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		catalog.reset();
		panel = null;
		overlayTask = null;
		overlaySession = null;
		overlayWilderness = null;
	}

	@Provides
	SlayerCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerCompanionConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			catalog.reset();
			requestRefresh();
		}
	}

	@Subscribe
	public void onTaskChanged(TaskChanged event)
	{
		overlayTask = event.getCurrent();
		CurrentTask task = event.getCurrent();
		if (task == null)
		{
			mapMarkerService.clear();
		}
		else if (event.isNewAssignment())
		{
			data.task(task.getName()).ifPresent(info ->
			{
				updateMarkers(info);
				if (config.autoRouteFavourite())
				{
					locationService.favouriteLocation(info).flatMap(LocationService::point).ifPresent(routeService::route);
				}
			});
		}
		requestRefresh();
	}

	@Subscribe
	public void onOwnedItemsChanged(OwnedItemsChanged event)
	{
		requestRefresh();
	}

	@Subscribe
	public void onSessionUpdated(SessionUpdated event)
	{
		overlaySession = sessionTracker.snapshot().orElse(null);
		requestRefresh();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SlayerCompanionConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showOverlay".equals(event.getKey()))
		{
			overlayManager.remove(overlay);
			if (config.showOverlay())
			{
				overlayManager.add(overlay);
			}
		}
		if ("showMapMarkers".equals(event.getKey()))
		{
			clientThread.invokeLater(() -> taskTracker.current().flatMap(t -> data.task(t.getName())).ifPresent(this::updateMarkers));
		}
		requestRefresh();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!indexWasComplete && itemIndex.isComplete())
		{
			// Names looked up before the scan finished may have missed untradeables; redo them.
			indexWasComplete = true;
			itemNameResolver.invalidate();
			requestRefresh();
		}
		if (++tickCounter % WILDERNESS_REFRESH_TICKS != 0 || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WildernessStatus w = wildernessAdvisor.status();
		WildernessStatus prev = overlayWilderness;
		overlayWilderness = w;
		if (prev == null || prev.isInWilderness() != w.isInWilderness() || prev.getRiskValue() != w.getRiskValue()
			|| prev.getItemsKept() != w.getItemsKept() || prev.getWildernessLevel() != w.getWildernessLevel())
		{
			requestRefresh();
		}
	}

	/** Build a fresh model on the client thread and push it to the panel on the Swing thread. Coalesces bursts. */
	private void requestRefresh()
	{
		if (!refreshQueued.compareAndSet(false, true))
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			refreshQueued.set(false);
			PanelModel model = buildModel();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.update(model, config);
				}
			});
		});
	}

	private PanelModel buildModel()
	{
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		CurrentTask task = taskTracker.current().orElse(null);
		TaskInfo info = task == null ? null : data.task(task.getName()).orElse(null);
		String variant = info == null ? null : locationService.variant(info);
		List<TaskLocation> locations = info == null ? Collections.emptyList() : locationService.forTask(info, variant);
		List<String> variantNames = new java.util.ArrayList<>();
		Integer xpPerKill = info == null ? null : info.xpPerKillFor(variant);
		if (info != null)
		{
			for (com.slayercompanion.data.MonsterInfo mon : info.variants())
			{
				variantNames.add(mon.getName());
			}
		}
		String favourite = info == null ? null : locationService.favourite(info.getTask());
		java.util.Map<String, com.slayercompanion.game.LockState> locks = new java.util.HashMap<>();
		if (loggedIn)
		{
			for (TaskLocation l : locations)
			{
				locks.put(l.getId(), accessChecker.check(l));
			}
		}
		int points = loggedIn ? client.getVarbitValue(VarbitID.SLAYER_POINTS) : 0;
		int shared = loggedIn ? client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED) : 0;
		int wildStreak = loggedIn ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED) : 0;
		int mortimerStreak = loggedIn ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED) : 0;

		GearSetup setup = task == null ? null : setupStore.get(task.getName()).orElse(null);
		List<SetupStore.Difference> diffs = setup == null ? Collections.emptyList() : setupStore.compare(setup);
		List<String> missingRequired = info == null ? Collections.emptyList() : gearAdvisor.missingItems(info.getRequiredItems());
		SlayerMaster master = task == null ? SlayerMaster.fromVarbit(loggedIn ? client.getVarbitValue(VarbitID.SLAYER_MASTER) : 0) : task.getMaster();
		List<UpgradeAdvisor.UpgradeSuggestion> upgrades = loggedIn && ownedItems.isBankKnown()
			? upgradeAdvisor.suggest(master, null) : Collections.emptyList();

		WildernessStatus wilderness = loggedIn ? wildernessAdvisor.status() : null;
		overlayWilderness = wilderness;
		overlayTask = task;
		overlaySession = sessionTracker.snapshot().orElse(null);

		java.util.Map<Integer, String> itemNames = new java.util.HashMap<>();
		if (overlaySession != null)
		{
			for (int id : overlaySession.getLoot().keySet())
			{
				itemNames.put(id, itemName(id));
			}
			for (int id : overlaySession.getSupplies().keySet())
			{
				itemNames.put(id, itemName(id));
			}
		}
		List<com.slayercompanion.data.GearTable> gearTables = GearAdvisor.tablesFor(info, variant, data.generalGear());
		boolean gearIsGeneral = info != null && info.gearTablesFor(variant).isEmpty() && data.generalGear().getGearTables() != null;
		List<List<SlotAdvice>> gearAdvice = new java.util.ArrayList<>();
		for (com.slayercompanion.data.GearTable table : gearTables)
		{
			gearAdvice.add(gearAdvisor.advise(table));
		}

		return PanelModel.builder()
			.loggedIn(loggedIn)
			.task(task)
			.info(info)
			.locations(locations)
			.favouriteLocationId(favourite)
			.variants(variantNames)
			.selectedVariant(variant)
			.xpPerKill(xpPerKill)
			.shortestPathAvailable(routeService.isShortestPathAvailable())
			.bankKnown(ownedItems.isBankKnown())
			.missingRequiredItems(missingRequired)
			.savedSetup(setup)
			.setupDifferences(diffs)
			.upgrades(upgrades)
			.pointsPlan(loggedIn ? pointsPlanner.plan(points, shared, task) : null)
			.sharedStreak(shared)
			.wildernessStreak(wildStreak)
			.mortimerStreak(mortimerStreak)
			.points(points)
			.session(overlaySession)
			.history(sessionTracker.history())
			.wilderness(wilderness)
			.unlocks(loggedIn ? unlockAdvisor.advise(points) : Collections.emptyList())
			.itemNames(itemNames)
			.gearTables(gearTables)
			.gearAdvice(gearAdvice)
			.gearIsGeneral(gearIsGeneral)
			.locks(locks)
			.defaultGearTable(gearAdvisor.defaultTableIndex(gearTables, info == null ? null : info.getRecommendedStyle()))
			.build();
	}

	private void updateMarkers(TaskInfo info)
	{
		if (config.showMapMarkers())
		{
			List<TaskLocation> locs = locationService.forTask(info);
			java.util.Map<String, com.slayercompanion.game.LockState> locks = new java.util.HashMap<>();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				for (TaskLocation l : locs)
				{
					locks.put(l.getId(), accessChecker.check(l));
				}
			}
			mapMarkerService.show(locs, locationService.favourite(info.getTask()), locks);
		}
		else
		{
			mapMarkerService.clear();
		}
	}

	private String itemName(int itemId)
	{
		return itemManager.getItemComposition(itemId).getName();
	}

	/** Panel callbacks; they run on the Swing thread and hop to the client thread where needed. */
	private class Actions implements PanelActions
	{
		@Override
		public void routeTo(TaskLocation location)
		{
			LocationService.point(location).ifPresent(p -> clientThread.invokeLater(() -> routeService.route(p)));
		}

		@Override
		public void clearRoute()
		{
			clientThread.invokeLater(routeService::clear);
		}

		@Override
		public void setFavourite(String taskName, @Nullable String locationId)
		{
			// Key by the bundled task name, as the readers do; the game's name can be an alternative ("Artio").
			String key = data.task(taskName).map(com.slayercompanion.data.TaskInfo::getTask).orElse(taskName);
			locationService.setFavourite(key, locationId);
			clientThread.invokeLater(() ->
			{
				data.task(key).ifPresent(SlayerCompanionPlugin.this::updateMarkers);
				requestRefresh();
			});
		}

		@Override
		public void setVariant(String taskName, @Nullable String monsterName)
		{
			// Key by the bundled task name, as the readers do; the game's name can be an alternative ("Artio").
			String key = data.task(taskName).map(com.slayercompanion.data.TaskInfo::getTask).orElse(taskName);
			locationService.setVariant(key, monsterName);
			clientThread.invokeLater(() ->
			{
				data.task(key).ifPresent(SlayerCompanionPlugin.this::updateMarkers);
				requestRefresh();
			});
		}

		@Override
		public void saveCurrentSetup(String taskName)
		{
			clientThread.invokeLater(() ->
			{
				setupStore.saveCurrent(taskName);
				requestRefresh();
			});
		}

		@Override
		public void deleteSetup(String taskName)
		{
			setupStore.delete(taskName);
			requestRefresh();
		}

		@Override
		public void resetSession()
		{
			clientThread.invokeLater(sessionTracker::resetCurrent);
		}

		@Override
		public void refresh()
		{
			requestRefresh();
		}

		@Override
		public void openWiki(String pageTitle)
		{
			LinkBrowser.browse(WIKI_BASE + pageTitle.replace(' ', '_'));
		}
	}

	/** For tests. */
	Optional<CurrentTask> currentTask()
	{
		return taskTracker.current();
	}
}
