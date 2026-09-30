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
package com.slayercompanion.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

public class AssignmentMemoryTest
{
	private final Map<String, String> profile = new HashMap<>();
	private AssignmentMemory memory;

	@Before
	public void setUp()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(i -> profile.get((String) i.getArgument(1)));
		doAnswer(i -> profile.put(i.getArgument(1), String.valueOf((Object) i.getArgument(2))))
			.when(config).setRSProfileConfiguration(anyString(), anyString(), any());
		doAnswer(i -> profile.remove((String) i.getArgument(1))).when(config).unsetRSProfileConfiguration(anyString(), anyString());
		memory = new AssignmentMemory(config);
	}

	private static CurrentTask task(String name, SlayerMaster master, String area, int remaining)
	{
		return new CurrentTask(name, remaining, 150, area, master, false, 0, 0);
	}

	@Test
	public void theSameAssignmentSeenAgainAfterALoginIsNotNew()
	{
		assertTrue(memory.firstSighting(task("Abyssal demons", SlayerMaster.KONAR, "Catacombs of Kourend", 150)));
		// Relog, hop or plugin restart: reported again, further along.
		assertFalse(memory.firstSighting(task("Abyssal demons", SlayerMaster.KONAR, "Catacombs of Kourend", 90)));
	}

	@Test
	public void anotherTaskMasterOrAreaIsNew()
	{
		assertTrue(memory.firstSighting(task("Abyssal demons", SlayerMaster.KONAR, "Catacombs of Kourend", 150)));
		assertTrue(memory.firstSighting(task("Abyssal demons", SlayerMaster.KONAR, "The Abyss", 150)));
		assertTrue(memory.firstSighting(task("Abyssal demons", SlayerMaster.DURADEL, null, 150)));
		assertTrue(memory.firstSighting(task("Hydras", SlayerMaster.DURADEL, null, 150)));
	}

	@Test
	public void afterCompletionTheSameTaskAgainIsNew()
	{
		assertTrue(memory.firstSighting(task("Hydras", SlayerMaster.DURADEL, null, 150)));
		memory.forget();
		assertTrue(memory.firstSighting(task("Hydras", SlayerMaster.DURADEL, null, 150)));
	}
}
