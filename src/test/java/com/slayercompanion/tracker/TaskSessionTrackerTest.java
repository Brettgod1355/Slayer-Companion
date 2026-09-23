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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.events.TaskChanged;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;

public class TaskSessionTrackerTest
{
	private Client client;
	private ItemManager itemManager;
	private ConfigManager configManager;
	private TaskSessionTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		itemManager = mock(ItemManager.class);
		configManager = mock(ConfigManager.class);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
		when(itemManager.getItemPrice(anyInt())).thenReturn(100);
		when(configManager.getRSProfileConfiguration(anyString(), anyString())).thenReturn(null);
		SlayerCompanionConfig config = new SlayerCompanionConfig()
		{
		};
		tracker = new TaskSessionTracker(client, itemManager, configManager, new EventBus(), new Gson(), config);
		tracker.startUp();
	}

	private static CurrentTask task(String name, int remaining, int initial)
	{
		return new CurrentTask(name, remaining, initial, null, SlayerMaster.DURADEL, false, 0, 0);
	}

	@Test
	public void newAssignmentStartsSessionAndLootFromTargetsCounts()
	{
		tracker.onTaskChanged(new TaskChanged(null, task("Abyssal demons", 150, 150)));
		assertTrue(tracker.current().isPresent());
		assertEquals("Abyssal demons", tracker.current().get().getTaskName());

		NPCComposition demon = mock(NPCComposition.class);
		when(demon.getId()).thenReturn(415);
		when(demon.getName()).thenReturn("Abyssal demon");
		tracker.onServerNpcLoot(new ServerNpcLoot(demon, Arrays.asList(new ItemStack(4151, 1), new ItemStack(995, 300))));
		assertEquals(30100, tracker.current().get().getLootValue());

		NPCComposition cow = mock(NPCComposition.class);
		when(cow.getId()).thenReturn(2);
		when(cow.getName()).thenReturn("Cow");
		tracker.onServerNpcLoot(new ServerNpcLoot(cow, Collections.singletonList(new ItemStack(1739, 1))));
		assertEquals("loot from non-targets is ignored", 30100, tracker.current().get().getLootValue());
	}

	@Test
	public void progressOnSameTaskKeepsSessionButNewTaskOfSameMonsterResets()
	{
		tracker.onTaskChanged(new TaskChanged(null, task("Bloodveld", 200, 200)));
		NPCComposition veld = mock(NPCComposition.class);
		when(veld.getId()).thenReturn(484);
		when(veld.getName()).thenReturn("Bloodveld");
		tracker.onServerNpcLoot(new ServerNpcLoot(veld, Collections.singletonList(new ItemStack(526, 1))));
		tracker.onTaskChanged(new TaskChanged(task("Bloodveld", 200, 200), task("Bloodveld", 150, 200)));
		assertEquals(100, tracker.current().get().getLootValue());
		assertEquals(50, tracker.current().get().getKills());

		// Same monster assigned again: kills go back to 0, so a fresh session starts.
		tracker.onTaskChanged(new TaskChanged(task("Bloodveld", 150, 200), task("Bloodveld", 180, 180)));
		assertEquals(0, tracker.current().get().getLootValue());
	}

	@Test
	public void completionArchivesAndClears()
	{
		tracker.onTaskChanged(new TaskChanged(null, task("Dust devils", 100, 100)));
		tracker.onTaskChanged(new TaskChanged(task("Dust devils", 1, 100), null));
		assertFalse(tracker.current().isPresent());
	}

	@Test
	public void npcIdsFromBundledDataMatchEvenWhenNameDiffers()
	{
		tracker.setTargetNpcIds(name -> Collections.singleton(9999));
		tracker.onTaskChanged(new TaskChanged(null, task("Kalphites", 100, 100)));
		NPCComposition queen = mock(NPCComposition.class);
		when(queen.getId()).thenReturn(9999);
		when(queen.getName()).thenReturn("Something else entirely");
		tracker.onServerNpcLoot(new ServerNpcLoot(queen, Collections.singletonList(new ItemStack(1, 2))));
		assertEquals(200, tracker.current().get().getLootValue());
	}
}
