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
package com.slayercompanion.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.task.SlayerMaster;
import org.junit.Test;

public class SlayerDataTest
{
	private final SlayerData data = new SlayerData(new Gson());

	@Test
	public void normaliseIgnoresCaseArticlesAndPunctuation()
	{
		assertEquals("abyssalsire", SlayerData.normalise("The Abyssal Sire"));
		assertEquals("kreearra", SlayerData.normalise("Kree'arra"));
		assertEquals("tzhaar", SlayerData.normalise("TzHaar"));
	}

	@Test
	public void bundledMastersLoadForEveryEnumValue()
	{
		assertFalse(data.masters().isEmpty());
		for (SlayerMaster m : SlayerMaster.values())
		{
			assertTrue("masters.json is missing " + m.getDataId(), data.master(m).isPresent());
		}
		assertEquals(18, data.master(SlayerMaster.KONAR).get().getPointsPerTask());
		assertEquals(25, data.master(SlayerMaster.KRYSTILIA).get().getPointsPerTask());
	}

	@Test
	public void bundledUnlocksAndWildernessLoad()
	{
		assertFalse(data.unlocks().isEmpty());
		assertFalse(data.wilderness().getExplainers().isEmpty());
		assertFalse(data.areaRules().getCannonProhibited().isEmpty());
	}

	@Test
	public void bundledTasksLoadAndMatchAlternatives()
	{
		assertFalse("tasks.json should be bundled", data.tasks().isEmpty());
		assertTrue(data.task("Abyssal demons").isPresent());
		assertTrue(data.task("ABYSSAL DEMONS").isPresent());
	}

	@Test
	public void gearPageFollowsTheChosenVariant()
	{
		TaskInfo abyssal = data.task("Abyssal demons").get();
		assertEquals("Slayer task/Abyssal demons", abyssal.gearPageFor(null));
		assertEquals("Abyssal Sire/Strategies", abyssal.gearPageFor("abyssal sire"));
		assertEquals("Slayer task/Abyssal demons", abyssal.gearPageFor("Abyssal demon"));
		assertEquals("Abyssal Sire/Strategies", data.task("The Abyssal Sire").get().gearPageFor(null));

		// Only Vorkath has a gear table on the wiki; the plugin falls back to the task page.
		TaskInfo blue = data.task("Blue dragons").get();
		assertEquals("Vorkath/Strategies", blue.gearPageFor("Vorkath"));
		assertNull(blue.gearPageFor(null));
	}

	@Test
	public void everyVariantGearPageBelongsToASelectableVariant()
	{
		for (TaskInfo t : data.tasks())
		{
			if (t.getVariantGearPages() == null)
			{
				continue;
			}
			for (String variant : t.getVariantGearPages().keySet())
			{
				boolean selectable = false;
				for (MonsterInfo m : t.variants())
				{
					selectable |= variant.equalsIgnoreCase(m.getName());
				}
				assertTrue(t.getTask() + ": " + variant + " cannot be picked", selectable);
			}
		}
	}

	@Test
	public void requiredItemNamesSplitAlternativesAndVariants()
	{
		java.util.Set<String> names = data.requiredItemNames();
		assertTrue(names.contains("Nose peg"));
		assertTrue(names.contains("Slayer helmet"));
		assertTrue(names.contains("Spade"));
	}
}
