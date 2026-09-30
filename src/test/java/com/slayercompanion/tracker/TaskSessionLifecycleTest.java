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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.events.TaskChanged;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

/** Supplies, kill counts and time across doses, resets, logouts and tasks finished elsewhere. */
public class TaskSessionLifecycleTest
{
	// Super combat potion(4..1) and the empty vial; a cake and its leftovers.
	private static final int SCP4 = 12695;
	private static final int SCP3 = 12697;
	private static final int SCP2 = 12699;
	private static final int SCP1 = 12701;
	private static final int VIAL = 229;
	private static final int CAKE = 1891;
	private static final int CAKE_2_3 = 1893;
	private static final int CAKE_SLICE = 1895;

	private final Map<String, String> profile = new HashMap<>();
	private final Map<Integer, Long> prices = new HashMap<>();
	private Client client;
	private TaskSessionTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
		ConfigManager configManager = mock(ConfigManager.class);
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(i ->
		{
			((Runnable) i.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(itemManager.canonicalize(anyInt())).thenAnswer(i -> i.getArgument(0));
		when(itemManager.getItemPrice(anyInt())).thenAnswer(i -> prices.getOrDefault((Integer) i.getArgument(0), 0L));
		prices.put(SCP4, 10000L);
		prices.put(SCP3, 7500L);
		prices.put(SCP2, 5000L);
		prices.put(SCP1, 2500L);
		prices.put(VIAL, 5L);
		prices.put(CAKE, 300L);
		prices.put(CAKE_2_3, 200L);
		prices.put(CAKE_SLICE, 100L);
		when(itemManager.getItemComposition(anyInt())).thenAnswer(i ->
		{
			int id = i.getArgument(0);
			ItemComposition c = mock(ItemComposition.class);
			when(c.getName()).thenReturn(id == VIAL ? "Vial" : "Supply " + id);
			when(c.getInventoryActions()).thenReturn(id == VIAL ? new String[5]
				: new String[]{id >= CAKE && id <= CAKE_SLICE ? "Eat" : "Drink", null, null, null, null});
			return c;
		});
		when(configManager.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(i -> profile.get((String) i.getArgument(1)));
		doAnswer(i -> profile.put(i.getArgument(1), String.valueOf((Object) i.getArgument(2))))
			.when(configManager).setRSProfileConfiguration(anyString(), anyString(), any());
		doAnswer(i -> profile.remove((String) i.getArgument(1))).when(configManager).unsetRSProfileConfiguration(anyString(), anyString());
		SlayerCompanionConfig config = new SlayerCompanionConfig()
		{
		};
		tracker = new TaskSessionTracker(client, clientThread, itemManager, configManager, new EventBus(), new Gson(), config);
		tracker.startUp();
	}

	private static CurrentTask task(int remaining, int initial)
	{
		return new CurrentTask("Abyssal demons", remaining, initial, null, SlayerMaster.DURADEL, false, 0, 0);
	}

	private void inventory(int... idQty)
	{
		Item[] items = new Item[idQty.length / 2];
		for (int i = 0; i < items.length; i++)
		{
			items[i] = new Item(idQty[2 * i], idQty[2 * i + 1]);
		}
		ItemContainer c = mock(ItemContainer.class);
		when(c.getItems()).thenReturn(items);
		tracker.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, c));
	}

	private void state(GameState s)
	{
		GameStateChanged e = new GameStateChanged();
		e.setGameState(s);
		tracker.onGameStateChanged(e);
	}

	private TaskSession session()
	{
		return tracker.current().orElseThrow(AssertionError::new);
	}

	@Test
	public void aFourDosePotionCostsItsPriceOnce()
	{
		tracker.onTaskChanged(new TaskChanged(null, task(150, 150)));
		inventory(SCP4, 1);
		inventory(SCP3, 1);
		assertEquals("one dose", 2500, session().getSuppliesValue());
		inventory(SCP2, 1);
		inventory(SCP1, 1);
		inventory(VIAL, 1);
		assertEquals(10000, session().getSuppliesValue());
		assertEquals("the list shows the potion that was used up", 1, (int) session().getSupplies().get(SCP4));
		assertEquals(1, TrackerTabAccess.positive(session().getSupplies()));
	}

	@Test
	public void aPickedUpSupplyIsNotACreditAndACakeIsEatenOnce()
	{
		tracker.onTaskChanged(new TaskChanged(null, task(150, 150)));
		inventory(SCP4, 1);
		inventory(SCP4, 1, SCP3, 1); // picked up
		assertEquals(0, session().getSuppliesValue());
		inventory(SCP4, 1, SCP2, 1); // drank the picked-up one
		assertEquals(2500, session().getSuppliesValue());
		inventory(SCP4, 1, SCP2, 1, CAKE, 1);
		inventory(SCP4, 1, SCP2, 1, CAKE_2_3, 1);
		inventory(SCP4, 1, SCP2, 1, CAKE_SLICE, 1);
		inventory(SCP4, 1, SCP2, 1);
		assertEquals(2800, session().getSuppliesValue());
	}

	@Test
	public void killsBeforeTrackingOrAResetAreNotTracked()
	{
		// Enabled with 100 of 150 done.
		tracker.onTaskChanged(new TaskChanged(null, task(50, 150)));
		assertEquals(100, session().getKills());
		assertEquals(0, session().getTrackedKills());
		tracker.onTaskChanged(new TaskChanged(task(50, 150), task(40, 150)));
		assertEquals(10, session().getTrackedKills());
		tracker.resetCurrent();
		assertEquals(110, session().getKills());
		assertEquals(0, session().getTrackedKills());
		tracker.onTaskChanged(new TaskChanged(task(40, 150), task(35, 150)));
		assertEquals(115, session().getKills());
		assertEquals(5, session().getTrackedKills());
	}

	@Test
	public void timeLoggedOutIsNotPartOfTheDuration()
	{
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(40);
		tracker.onTaskChanged(new TaskChanged(null, task(50, 150)));
		long hour = 3_600_000L;
		long now = System.currentTimeMillis();
		session().setStartedAtEpochMs(now - hour);
		state(GameState.LOGIN_SCREEN);
		assertFalse(tracker.current().isPresent());

		// Pretend the logout was eight hours ago.
		Gson gson = new Gson();
		TaskSession saved = gson.fromJson(profile.get("session"), TaskSession.class);
		long away = 8 * hour;
		saved.setStartedAtEpochMs(saved.getStartedAtEpochMs() - away);
		saved.setUpdatedAtEpochMs(saved.getUpdatedAtEpochMs() - away);
		saved.setPausedAtEpochMs(saved.getPausedAtEpochMs() - away);
		profile.put("session", gson.toJson(saved));

		state(GameState.LOGGED_IN);
		long minutes = session().getDurationMs() / 60000L;
		assertTrue("about an hour, was " + minutes + " min", minutes >= 59 && minutes <= 61);
		tracker.onTaskChanged(new TaskChanged(null, task(40, 150)));
		minutes = session().getDurationMs() / 60000L;
		assertTrue("still about an hour after the next kill, was " + minutes + " min", minutes >= 59 && minutes <= 61);
	}

	@Test
	public void aRestoredSessionWithNoTaskInHandIsPutInTheHistory()
	{
		tracker.onTaskChanged(new TaskChanged(null, task(50, 150)));
		state(GameState.LOGIN_SCREEN);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		state(GameState.LOGGED_IN);
		assertTrue(tracker.current().isPresent());
		for (int i = 0; i < TaskSessionTracker.RESTORED_CHECK_TICKS; i++)
		{
			tracker.onGameTick(new GameTick());
		}
		assertFalse(tracker.current().isPresent());
		assertEquals(1, tracker.history().size());
		assertFalse("not known to be completed", tracker.history().get(0).isCompleted());
	}

	@Test
	public void aRestoredSessionWhoseTaskIsStillOnStays()
	{
		tracker.onTaskChanged(new TaskChanged(null, task(50, 150)));
		state(GameState.LOGIN_SCREEN);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(50);
		state(GameState.LOGGED_IN);
		for (int i = 0; i < 2 * TaskSessionTracker.RESTORED_CHECK_TICKS; i++)
		{
			tracker.onGameTick(new GameTick());
		}
		assertTrue(tracker.current().isPresent());
		assertTrue(tracker.history().isEmpty());
	}

	@Test
	public void aBankLeftOpenAtLogoutDoesNotStopSupplyTracking()
	{
		WidgetLoaded bank = new WidgetLoaded();
		bank.setGroupId(InterfaceID.BANKMAIN);
		tracker.onWidgetLoaded(bank);
		state(GameState.LOGIN_SCREEN);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(150);
		state(GameState.LOGGED_IN);
		tracker.onTaskChanged(new TaskChanged(null, task(150, 150)));
		inventory(SCP4, 1);
		inventory(SCP3, 1);
		assertEquals(2500, session().getSuppliesValue());
	}

	@Test
	public void shutDownSavesTheSessionOnTheClientThread()
	{
		tracker.onTaskChanged(new TaskChanged(null, task(150, 150)));
		tracker.shutDown();
		assertFalse(tracker.current().isPresent());
		assertTrue(profile.get("session").contains("Abyssal demons"));
	}

	/** What the Loot tab lists. */
	static final class TrackerTabAccess
	{
		static int positive(Map<Integer, Integer> map)
		{
			return com.slayercompanion.ui.TrackerTabTestAccess.top(map).size();
		}
	}
}
