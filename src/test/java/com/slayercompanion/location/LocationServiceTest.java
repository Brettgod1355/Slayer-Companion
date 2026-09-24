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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.MonsterInfo;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

public class LocationServiceTest
{
	private final Map<String, String> config = new HashMap<>();
	private LocationService service;

	@Before
	public void setUp()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(SlayerCompanionConfig.GROUP), anyString()))
			.thenAnswer(inv -> config.get(inv.<String>getArgument(1)));
		doAnswer(inv -> config.put(inv.getArgument(1), inv.getArgument(2)))
			.when(configManager).setConfiguration(eq(SlayerCompanionConfig.GROUP), anyString(), anyString());
		doAnswer(inv -> config.remove(inv.<String>getArgument(1)))
			.when(configManager).unsetConfiguration(eq(SlayerCompanionConfig.GROUP), anyString());
		service = new LocationService(configManager);
	}

	private static TaskLocation loc(String id, int rank, String... monsters)
	{
		TaskLocation l = new TaskLocation();
		l.setId(id);
		l.setName(id);
		l.setRank(rank);
		l.setMonsters(Arrays.asList(monsters));
		return l;
	}

	private static MonsterInfo monster(String name, Integer xp)
	{
		MonsterInfo m = new MonsterInfo();
		m.setName(name);
		m.setSlayerXp(xp);
		return m;
	}

	/** Abyssal demons: the regular demon in three places, the Sire in its own lair, the superior nowhere. */
	private static TaskInfo demons()
	{
		TaskInfo t = new TaskInfo();
		t.setTask("Abyssal demons");
		t.setXpPerKill(150);
		t.setSuperior("Greater abyssal demon");
		t.setMonsters(Arrays.asList(monster("Abyssal demon", 150), monster("Abyssal Sire", 478),
			monster("Greater abyssal demon", 4200), monster("Unplaced demon", 1)));
		t.setLocations(Arrays.asList(
			loc("catacombs", 2, "Abyssal demon"),
			loc("sire-lair", 1, "Abyssal Sire"),
			loc("slayer-tower", 1, "Abyssal demon"),
			loc("wildy-cave", 0, "Abyssal demon", "Greater abyssal demon")));
		return t;
	}

	private static List<String> ids(List<TaskLocation> list)
	{
		List<String> out = new ArrayList<>();
		for (TaskLocation l : list)
		{
			out.add(l.getId());
		}
		return out;
	}

	// --- variants ---

	@Test
	public void variantsArePlacedMonstersWithoutTheSuperior()
	{
		List<String> names = new ArrayList<>();
		for (MonsterInfo m : demons().variants())
		{
			names.add(m.getName());
		}
		assertEquals(Arrays.asList("Abyssal demon", "Abyssal Sire"), names);
	}

	@Test
	public void selectedVariantFiltersLocations()
	{
		TaskInfo t = demons();
		assertEquals(Arrays.asList("slayer-tower", "catacombs", "wildy-cave"), ids(service.forTask(t, "Abyssal demon")));
		assertEquals(Arrays.asList("sire-lair"), ids(service.forTask(t, "Abyssal Sire")));
	}

	@Test
	public void variantWithoutSpotsOrNoVariantShowsEverything()
	{
		TaskInfo t = demons();
		// Ranked spots first (ties by name), unranked last.
		List<String> all = Arrays.asList("sire-lair", "slayer-tower", "catacombs", "wildy-cave");
		assertEquals(all, ids(service.forTask(t, null)));
		assertEquals(all, ids(service.forTask(t, "Unplaced demon")));
	}

	@Test
	public void savedVariantIsUsedAndDefaultsToTheFirst()
	{
		TaskInfo t = demons();
		assertEquals("Abyssal demon", service.variant(t));
		service.setVariant("Abyssal demons", "Abyssal Sire");
		assertEquals("Abyssal Sire", service.variant(t));
		assertEquals(Arrays.asList("sire-lair"), ids(service.forTask(t)));
		service.setVariant("Abyssal demons", null);
		assertEquals("Abyssal demon", service.variant(t));
	}

	@Test
	public void staleSavedVariantFallsBackToTheFirst()
	{
		service.setVariant("Abyssal demons", "Removed monster");
		assertEquals("Abyssal demon", service.variant(demons()));
	}

	@Test
	public void taskWithoutVariantsHasNone()
	{
		TaskInfo t = new TaskInfo();
		t.setTask("Cows");
		assertNull(service.variant(t));
		assertTrue(service.forTask(t).isEmpty());
	}

	@Test
	public void selectedVariantSetsTheXpPerKill()
	{
		TaskInfo t = demons();
		assertEquals(Integer.valueOf(478), t.xpPerKillFor("Abyssal Sire"));
		assertEquals(Integer.valueOf(150), t.xpPerKillFor("abyssal demon"));
		assertEquals("no variant: the task's figure", Integer.valueOf(150), t.xpPerKillFor(null));
		assertEquals("unknown variant: the task's figure", Integer.valueOf(150), t.xpPerKillFor("Nobody"));
		TaskInfo noXp = demons();
		noXp.getMonsters().get(1).setSlayerXp(null);
		assertEquals("variant without XP: the task's figure", Integer.valueOf(150), noXp.xpPerKillFor("Abyssal Sire"));
	}

	@Test
	public void everyBundledVariantNarrowsToItsOwnSpots()
	{
		SlayerData data = new SlayerData(new Gson());
		int checked = 0;
		for (TaskInfo t : data.tasks())
		{
			for (MonsterInfo m : t.variants())
			{
				List<TaskLocation> spots = service.forTask(t, m.getName());
				assertFalse(t.getTask() + " / " + m.getName() + " has no spots", spots.isEmpty());
				for (TaskLocation l : spots)
				{
					boolean own = l.getMonsters().contains(m.getName());
					boolean shared = t.variants().stream().noneMatch(v -> l.getMonsters().contains(v.getName()));
					assertTrue(t.getTask() + ": " + l.getId() + " belongs to another variant, not " + m.getName(), own || shared);
				}
				Integer xp = t.xpPerKillFor(m.getName());
				assertEquals(m.getSlayerXp() != null ? m.getSlayerXp() : t.getXpPerKill(), xp);
				checked++;
			}
		}
		assertTrue(checked > 0);
	}

	@Test
	public void everyBundledSpotShowsForSomeVariant()
	{
		SlayerData data = new SlayerData(new Gson());
		for (TaskInfo t : data.tasks())
		{
			if (t.variants().isEmpty())
			{
				continue;
			}
			java.util.Set<String> shown = new java.util.HashSet<>();
			for (MonsterInfo m : t.variants())
			{
				for (TaskLocation l : service.forTask(t, m.getName()))
				{
					shown.add(l.getId());
				}
			}
			for (TaskLocation l : t.locationsOrEmpty())
			{
				assertTrue(t.getTask() + ": " + l.getId() + " is hidden whichever variant is chosen", shown.contains(l.getId()));
			}
		}
	}

	@Test
	public void spotWithoutAnyVariantShowsForEveryVariant()
	{
		TaskInfo t = demons();
		List<TaskLocation> locs = new ArrayList<>(t.getLocations());
		locs.add(loc("task-wide", 3));
		t.setLocations(locs);
		for (MonsterInfo m : t.variants())
		{
			assertTrue(m.getName(), service.forTask(t, m.getName()).stream().anyMatch(l -> l.getId().equals("task-wide")));
		}
	}

	// --- favourites ---

	@Test
	public void favouriteComesFirst()
	{
		TaskInfo t = demons();
		service.setFavourite("Abyssal demons", "wildy-cave");
		assertEquals("wildy-cave", service.forTask(t, "Abyssal demon").get(0).getId());
		assertEquals("wildy-cave", service.favouriteLocation(t).get().getId());
		service.setFavourite("Abyssal demons", null);
		assertNull(service.favourite("Abyssal demons"));
		assertFalse(service.favouriteLocation(t).isPresent());
	}

	@Test
	public void favouriteIsKeyedByTheNormalisedTaskName()
	{
		// The game's name and the wiki's differ in case, "The " and punctuation; both must reach the same entry.
		service.setFavourite("ABYSSAL DEMONS", "catacombs");
		assertEquals("catacombs", service.favourite("Abyssal demons"));
		service.setFavourite("The Abyssal Sire", "sire-lair");
		assertEquals("sire-lair", service.favourite("Abyssal Sire"));
		service.setFavourite("Kree'arra", "gwd");
		assertEquals("gwd", service.favourite("KREEARRA"));
		assertEquals(SlayerData.normalise("K'ril Tsutsaroth"), LocationService.slug("k'ril tsutsaroth"));
		assertTrue(config.containsKey("fav.abyssaldemons"));
	}

	@Test
	public void favouriteThatNoLongerExistsIsIgnored()
	{
		service.setFavourite("Abyssal demons", "removed-spot");
		assertFalse(service.favouriteLocation(demons()).isPresent());
		assertEquals(Arrays.asList("sire-lair", "slayer-tower", "catacombs", "wildy-cave"), ids(service.forTask(demons(), null)));
	}

	@Test
	public void variantIsKeyedByTheNormalisedTaskNameToo()
	{
		service.setVariant("ABYSSAL DEMONS", "Abyssal Sire");
		assertEquals("Abyssal Sire", service.variant(demons()));
	}

	// --- map points ---

	@Test
	public void pointNeedsCoordinates()
	{
		TaskLocation l = loc("x", 1);
		assertFalse(LocationService.point(l).isPresent());
		l.setX(3200);
		l.setY(3200);
		assertEquals(new WorldPoint(3200, 3200, 0), LocationService.point(l).get());
		l.setPlane(1);
		assertEquals(1, LocationService.point(l).get().getPlane());
		l.setCoordsMissing(true);
		assertFalse(LocationService.point(l).isPresent());
	}
}
