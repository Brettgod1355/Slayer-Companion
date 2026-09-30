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
import com.slayercompanion.dps.LoadoutOptimizer;
import com.slayercompanion.dps.LoadoutRecommender;
import com.slayercompanion.dps.Recommendation;
import com.slayercompanion.events.OwnedItemsChanged;
import com.slayercompanion.events.SessionUpdated;
import com.slayercompanion.events.TaskChanged;
import com.slayercompanion.game.LiveSlayerCatalog;
import com.slayercompanion.gear.BankLayout;
import com.slayercompanion.gear.InventorySetupsLink;
import com.slayercompanion.gear.ItemIndex;
import com.slayercompanion.gear.ItemNameResolver;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.LoadoutDisplay;
import com.slayercompanion.gear.LoadoutStore;
import com.slayercompanion.gear.RequiredItems;
import com.slayercompanion.location.LocationService;
import com.slayercompanion.location.MapMarkerService;
import com.slayercompanion.location.RouteService;
import com.slayercompanion.points.PointsPlanner;
import com.slayercompanion.task.AssignmentMemory;
import com.slayercompanion.task.CurrentTask;
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
import com.slayercompanion.worth.CombatAchievements;
import com.slayercompanion.worth.LootEstimate;
import com.slayercompanion.worth.LootEstimator;
import com.slayercompanion.worth.VerdictAdvisor;
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
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "Slayer Companion",
	description = "A do/skip/block verdict with what the task is worth, locations, routing, the wiki's recommended gear and strategy, your own loadout per task, points planning, supplies and profit, wilderness risk and unlock advice for your Slayer task",
	tags = {"slayer", "task", "verdict", "loot", "achievements", "gear", "loadout", "dps", "inventory", "setups", "location", "wilderness", "points", "konar", "supplies", "profit", "unlocks"}
)
@PluginDependency(BankTagsPlugin.class)
public class SlayerCompanionPlugin extends Plugin
{
	/** Shown in the panel footer; bumped together with build.gradle and runelite-plugin.properties. */
	public static final String VERSION = "0.16.0";

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
	private RequiredItems requiredItems;
	@Inject
	private LoadoutStore loadoutStore;
	@Inject
	private LootEstimator lootEstimator;
	@Inject
	private LoadoutRecommender recommender;
	@Inject
	private BankLayout bankLayout;
	@Inject
	private InventorySetupsLink inventorySetupsLink;
	@Inject
	private SpriteManager spriteManager;
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
	@Inject
	private AssignmentMemory assignmentMemory;

	private SlayerCompanionPanel panel;
	private NavigationButton navButton;
	private volatile CurrentTask overlayTask;
	private volatile TaskSession overlaySession;
	private volatile WildernessStatus overlayWilderness;
	private int tickCounter;
	private final java.util.concurrent.atomic.AtomicBoolean refreshQueued = new java.util.concurrent.atomic.AtomicBoolean();
	private boolean indexWasComplete;
	/** The last best-in-bank result; shown only while its task and variant are current. */
	private volatile Recommendation recommendation;
	private volatile boolean recommending;
	/** Which option of {@link #recommendation} is in the loadout. */
	private volatile int recommendationIndex;
	/** The loadout before the recommendation filled it (null: there was none), for Undo. */
	private volatile Loadout undoLoadout;
	private volatile String undoKey;
	/** Bumped on logout and shutdown so a search still running then is thrown away when it answers. */
	private final java.util.concurrent.atomic.AtomicInteger recommendGeneration = new java.util.concurrent.atomic.AtomicInteger();

	@Override
	protected void startUp()
	{
		panel = new SlayerCompanionPanel(new Actions(), data.wilderness(), itemManager, spriteManager);
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
		itemIndex.want(data.requiredItemNames());
		ownedItems.startUp();
		sessionTracker.startUp();
		taskTracker.startUp();
		clientThread.invoke(bankLayout::startUp);
		inventorySetupsLink.refresh();
		requestRefresh();
		// The TaskChanged for a task already in hand can arrive before this plugin is on the event bus.
		clientThread.invokeLater(() -> taskTracker.current().flatMap(t -> data.task(t.getName())).ifPresent(this::updateMarkers));
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
		inventorySetupsLink.reset();
		clientThread.invoke(bankLayout::shutDown);
		resetRecommendation();
		indexWasComplete = false;
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
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// The next login may be another account: nothing of this one's may carry over.
			resetRecommendation();
			mapMarkerService.clear();
		}
		if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			catalog.reset();
			inventorySetupsLink.refresh();
			requestRefresh();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			requestRefresh();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			// Next time the bank opens it is the normal bank again.
			bankLayout.close();
			requestRefresh();
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (inventorySetupsLink.onPluginMessage(message))
		{
			requestRefresh();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		// Inventory Setups may have been turned on or off.
		inventorySetupsLink.refresh();
		requestRefresh();
	}

	@Subscribe
	public void onTaskChanged(TaskChanged event)
	{
		overlayTask = event.getCurrent();
		CurrentTask task = event.getCurrent();
		if (task == null)
		{
			mapMarkerService.clear();
			if (event.isCompleted())
			{
				assignmentMemory.forget();
			}
		}
		else if (event.isNewAssignment())
		{
			// A login, hop or restart reports the task in hand again: redraw the markers, but open the
			// setup and route only once per assignment.
			boolean firstSighting = assignmentMemory.firstSighting(task);
			if (firstSighting && config.openLinkedSetup())
			{
				loadoutStore.linkedSetup(bundledName(task.getName())).ifPresent(inventorySetupsLink::open);
			}
			data.task(task.getName()).ifPresent(info ->
			{
				updateMarkers(info);
				if (firstSighting && config.autoRouteFavourite())
				{
					locationService.favouriteLocation(info)
						.filter(l -> LocationService.suitsAssignment(l, task))
						.flatMap(LocationService::point).ifPresent(routeService::route);
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
			clientThread.invokeLater(() ->
			{
				Optional<TaskInfo> info = taskTracker.current().flatMap(t -> data.task(t.getName()));
				if (info.isPresent())
				{
					updateMarkers(info.get());
				}
				else
				{
					mapMarkerService.clear();
				}
			});
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

		List<String> missingRequired = info == null ? Collections.emptyList() : requiredItems.missing(info.getRequiredItems());
		String taskKey = task == null ? null : info == null ? task.getName() : info.getTask();
		LoadoutDisplay loadout = loggedIn && taskKey != null ? loadoutStore.get(taskKey).map(loadoutStore::display).orElse(null) : null;
		String linkedSetup = loggedIn && taskKey != null ? loadoutStore.linkedSetup(taskKey).orElse(null) : null;

		WildernessStatus wilderness = loggedIn ? wildernessAdvisor.status() : null;
		overlayWilderness = wilderness;
		overlayTask = task;
		overlaySession = sessionTracker.snapshot().orElse(null);

		java.util.Map<Integer, String> itemNames = new java.util.HashMap<>();
		java.util.Map<Integer, Long> itemPrices = new java.util.HashMap<>();
		if (overlaySession != null)
		{
			java.util.Set<Integer> ids = new java.util.HashSet<>(overlaySession.getLoot().keySet());
			ids.addAll(overlaySession.getSupplies().keySet());
			for (int id : ids)
			{
				itemNames.put(id, itemName(id));
				itemPrices.put(id, (long) itemManager.getItemPrice(id));
			}
		}

		com.slayercompanion.data.MasterInfo masterInfo = task == null || task.getMaster() == null ? null : data.master(task.getMaster()).orElse(null);
		LootEstimate estimate = loggedIn && info != null ? lootEstimator.estimate(info, variant, task.getRemaining()).orElse(null) : null;
		Long sessionExpected = null;
		if (loggedIn && overlaySession != null)
		{
			TaskInfo sessionInfo = data.task(overlaySession.getTaskName()).orElse(null);
			String sessionVariant = sessionInfo == info ? variant : null;
			sessionExpected = sessionInfo == null ? null
				: lootEstimator.estimate(sessionInfo, sessionVariant, overlaySession.getTrackedKills()).map(LootEstimate::getTotal).orElse(null);
		}
		List<TaskSession> history = sessionTracker.history();
		List<Long> historyExpected = new java.util.ArrayList<>();
		for (int i = 0; loggedIn && i < Math.min(history.size(), 8); i++)
		{
			TaskSession h = history.get(i);
			historyExpected.add(data.task(h.getTaskName()).flatMap(t -> lootEstimator.estimate(t, null, h.getTrackedKills()))
				.map(LootEstimate::getTotal).orElse(null));
		}
		java.util.Set<Integer> completedAchievements = new java.util.HashSet<>();
		if (loggedIn && info != null)
		{
			for (com.slayercompanion.data.CombatAchievementInfo ca : info.combatAchievementsOrEmpty())
			{
				if (ca.getId() != null && Boolean.TRUE.equals(CombatAchievements.isDone(client, ca.getId())))
				{
					completedAchievements.add(ca.getId());
				}
			}
		}

		String recommendationKey = info == null ? null : LoadoutRecommender.key(info.getTask(), variant);
		Recommendation rec = recommendation;
		if (rec != null && !rec.getKey().equals(recommendationKey))
		{
			rec = null;
		}
		String taskKeyForUndo = info == null ? (task == null ? null : task.getName()) : info.getTask();
		boolean canUndo = undoKey != null && undoKey.equals(taskKeyForUndo);
		boolean bankOpen = loggedIn && client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;

		com.slayercompanion.points.PointsPlan pointsPlan = loggedIn ? pointsPlanner.plan(points, shared, task,
			client.getLocalPlayer() == null ? Integer.MAX_VALUE : client.getLocalPlayer().getCombatLevel(),
			client.getRealSkillLevel(net.runelite.api.Skill.SLAYER)) : null;
		List<PanelModel.MasterRoute> masterRoutes = new java.util.ArrayList<>();
		if (loggedIn && task == null)
		{
			if (pointsPlan != null && pointsPlan.getRecommended() != null)
			{
				masterRoute(pointsPlan.getRecommended().getMasterName(), "pays most for your next task").ifPresent(masterRoutes::add);
			}
			com.slayercompanion.task.SlayerMaster last = com.slayercompanion.task.SlayerMaster.fromVarbit(client.getVarbitValue(VarbitID.SLAYER_MASTER));
			if (last != null && masterRoutes.stream().noneMatch(r -> r.getName().equalsIgnoreCase(last.getDisplayName())))
			{
				masterRoute(last.getDisplayName(), "your last master").ifPresent(masterRoutes::add);
			}
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
			.nonItemRequirements(info == null ? Collections.emptyList() : requiredItems.notItems(info.getRequiredItems()))
			.loadout(loadout)
			.inventorySetups(inventorySetupsLink.setups())
			.linkedSetup(linkedSetup)
			.recommendation(rec)
			.recommendationIndex(rec == null ? 0 : Math.min(recommendationIndex, Math.max(0, rec.getOptions().size() - 1)))
			.recommending(recommending)
			.canUndoRecommendation(canUndo)
			.bankOpen(bankOpen)
			.bankLayoutShowing(bankOpen && bankLayout.isShowing())
			.pointsPlan(pointsPlan)
			.masterRoutes(masterRoutes)
			.verdict(task == null ? null : VerdictAdvisor.verdict(info, masterInfo, points))
			.lootEstimate(estimate)
			.sessionExpected(sessionExpected)
			.historyExpected(historyExpected)
			.completedAchievements(completedAchievements)
			.sharedStreak(shared)
			.wildernessStreak(wildStreak)
			.mortimerStreak(mortimerStreak)
			.points(points)
			.session(overlaySession)
			.history(history)
			.wilderness(wilderness)
			.unlocks(loggedIn ? unlockAdvisor.advise(points) : Collections.emptyList())
			.itemNames(itemNames)
			.itemPrices(itemPrices)
			.locks(locks)
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

	/** Put recommendation option {@code index} into the task's loadout. Client thread only. */
	private void applyRecommendation(String key, Recommendation r, int index)
	{
		loadoutStore.saveEquipment(key, LoadoutOptimizer.itemIds(r.getOptions().get(index).getGear(), r.getOwned()));
		recommendationIndex = index;
		refreshBankLayout(key);
	}

	private void forgetRecommendation()
	{
		recommendation = null;
		recommendationIndex = 0;
		undoKey = null;
		undoLoadout = null;
	}

	/** Forget the recommendation and Undo, and drop the answer of any search still running. */
	private void resetRecommendation()
	{
		recommendGeneration.incrementAndGet();
		recommending = false;
		forgetRecommendation();
	}

	/** When the bank is showing the loadout, show the loadout as it is now. Client thread only. */
	private void refreshBankLayout(String key)
	{
		if (bankLayout.isShowing())
		{
			Optional<Loadout> loadout = loadoutStore.get(key);
			if (loadout.isPresent())
			{
				bankLayout.show(loadout.get());
			}
			else
			{
				bankLayout.close();
			}
		}
	}

	/** A master by display name, id or alias. */
	private Optional<com.slayercompanion.data.MasterInfo> masterInfo(String name)
	{
		for (com.slayercompanion.data.MasterInfo mi : data.masters())
		{
			// The alias is "Aya (replaces Turael during/after While Guthix Sleeps)": match the name before the remark.
			String alias = mi.getAlias() == null ? null : mi.getAlias().replaceFirst("\\s*\\(.*$", "");
			if (name.equalsIgnoreCase(mi.getName()) || name.equalsIgnoreCase(mi.getId()) || name.equalsIgnoreCase(alias))
			{
				return Optional.of(mi);
			}
		}
		return Optional.empty();
	}

	private Optional<PanelModel.MasterRoute> masterRoute(String name, String why)
	{
		return masterInfo(name).filter(mi -> mi.getLocation() != null && mi.getLocation().getX() != null)
			.map(mi -> new PanelModel.MasterRoute(mi.getName(), why, mi.getLocation().getPlace(),
				mi.getTravel() == null ? Collections.emptyList() : mi.getTravel()));
	}

	/** The bundled task name, as favourites and loadouts are keyed; the game's name can be an alternative ("Artio"). */
	private String bundledName(String taskName)
	{
		return data.task(taskName).map(TaskInfo::getTask).orElse(taskName);
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
		public void routeToMaster(String masterName)
		{
			masterInfo(masterName).map(com.slayercompanion.data.MasterInfo::getLocation).filter(l -> l.getX() != null && l.getY() != null)
				.ifPresent(l -> clientThread.invokeLater(() -> routeService.route(
					new net.runelite.api.coords.WorldPoint(l.getX(), l.getY(), l.getPlane() == null ? 0 : l.getPlane()))));
		}

		@Override
		public void setFavourite(String taskName, @Nullable String locationId)
		{
			String key = bundledName(taskName);
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
			String key = bundledName(taskName);
			locationService.setVariant(key, monsterName);
			clientThread.invokeLater(() ->
			{
				data.task(key).ifPresent(SlayerCompanionPlugin.this::updateMarkers);
				requestRefresh();
			});
		}

		@Override
		public void saveLoadout(String taskName)
		{
			String key = bundledName(taskName);
			clientThread.invokeLater(() ->
			{
				loadoutStore.saveCurrent(key);
				forgetRecommendation();
				refreshBankLayout(key);
				requestRefresh();
			});
		}

		@Override
		public void deleteLoadout(String taskName)
		{
			String key = bundledName(taskName);
			clientThread.invokeLater(() ->
			{
				loadoutStore.delete(key);
				forgetRecommendation();
				refreshBankLayout(key);
				requestRefresh();
			});
		}

		@Override
		public void undoRecommendation(String taskName)
		{
			String key = bundledName(taskName);
			clientThread.invokeLater(() ->
			{
				if (key.equals(undoKey))
				{
					loadoutStore.put(key, undoLoadout);
					forgetRecommendation();
					refreshBankLayout(key);
				}
				requestRefresh();
			});
		}

		@Override
		public void showLoadoutInBank(String taskName)
		{
			String key = bundledName(taskName);
			clientThread.invokeLater(() ->
			{
				if (client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null)
				{
					loadoutStore.get(key).ifPresent(bankLayout::show);
				}
				requestRefresh();
			});
		}

		@Override
		public void closeBankLayout()
		{
			clientThread.invokeLater(() ->
			{
				bankLayout.close();
				requestRefresh();
			});
		}

		@Override
		public void linkInventorySetup(String taskName, @Nullable String setupName)
		{
			loadoutStore.link(bundledName(taskName), setupName);
			requestRefresh();
		}

		@Override
		public void openInventorySetup(String setupName)
		{
			// Same thread as the automatic open on a new task.
			clientThread.invokeLater(() -> inventorySetupsLink.open(setupName));
		}

		@Override
		public void recommendLoadout(String taskName)
		{
			clientThread.invokeLater(() ->
			{
				TaskInfo info = data.task(taskName).orElse(null);
				if (info == null)
				{
					return;
				}
				recommending = true;
				requestRefresh();
				int generation = recommendGeneration.get();
				recommender.recommend(info, locationService.variant(info), r -> clientThread.invokeLater(() ->
				{
					if (generation != recommendGeneration.get())
					{
						return; // logged out or shut down meanwhile
					}
					recommendation = r;
					recommending = false;
					if (!r.getOptions().isEmpty())
					{
						// Fill the loadout with the best option. Undo goes back to the loadout from before the
						// first recommendation, however many times the button is pressed.
						if (!info.getTask().equals(undoKey))
						{
							undoLoadout = loadoutStore.get(info.getTask()).orElse(null);
							undoKey = info.getTask();
						}
						applyRecommendation(info.getTask(), r, 0);
					}
					requestRefresh();
				}));
			});
		}

		@Override
		public void useRecommendation(String taskName, int index)
		{
			Recommendation r = recommendation;
			if (r == null || index < 0 || index >= r.getOptions().size())
			{
				return;
			}
			String key = bundledName(taskName);
			clientThread.invokeLater(() ->
			{
				applyRecommendation(key, r, index);
				requestRefresh();
			});
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
