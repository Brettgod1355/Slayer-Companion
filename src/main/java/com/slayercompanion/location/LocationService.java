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

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/**
 * Orders a task's locations (favourite first, then wiki rank) and stores the player's favourite
 * location per task in the plugin config.
 */
@Singleton
public class LocationService
{
	private static final String FAVOURITE_PREFIX = "fav.";
	private static final String VARIANT_PREFIX = "variant.";

	private final ConfigManager configManager;

	@Inject
	LocationService(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public List<TaskLocation> forTask(TaskInfo task)
	{
		return forTask(task, variant(task));
	}

	/**
	 * Locations for a task, narrowed to the chosen variant when that variant has any spots.
	 * Spots that name none of the task's variants (a curated spot for the task as a whole) are
	 * kept for every variant.
	 */
	public List<TaskLocation> forTask(TaskInfo task, @Nullable String variant)
	{
		String fav = favourite(task.getTask());
		List<TaskLocation> out = new ArrayList<>();
		if (variant != null)
		{
			java.util.Set<String> variantNames = new java.util.HashSet<>();
			for (com.slayercompanion.data.MonsterInfo m : task.variants())
			{
				variantNames.add(m.getName());
			}
			List<TaskLocation> shared = new ArrayList<>();
			for (TaskLocation l : task.locationsOrEmpty())
			{
				List<String> monsters = l.getMonsters() == null ? java.util.Collections.emptyList() : l.getMonsters();
				if (monsters.contains(variant))
				{
					out.add(l);
				}
				else if (monsters.stream().noneMatch(variantNames::contains))
				{
					shared.add(l);
				}
			}
			if (!out.isEmpty())
			{
				out.addAll(shared);
			}
		}
		if (out.isEmpty())
		{
			out.addAll(task.locationsOrEmpty());
		}
		out.sort(Comparator
			.comparing((TaskLocation l) -> fav == null || !fav.equals(l.getId()))
			.thenComparingInt(l -> l.getRank() <= 0 ? Integer.MAX_VALUE : l.getRank())
			.thenComparing(TaskLocation::label));
		return out;
	}

	/** Favourite location id for a task, or null. */
	@Nullable
	public String favourite(String taskName)
	{
		return configManager.getConfiguration(SlayerCompanionConfig.GROUP, FAVOURITE_PREFIX + slug(taskName));
	}

	public Optional<TaskLocation> favouriteLocation(TaskInfo task)
	{
		String fav = favourite(task.getTask());
		if (fav == null)
		{
			return Optional.empty();
		}
		for (TaskLocation l : task.locationsOrEmpty())
		{
			if (fav.equals(l.getId()))
			{
				return Optional.of(l);
			}
		}
		return Optional.empty();
	}

	/** The variant chosen for a task, or the task's first variant when none is saved. */
	@Nullable
	public String variant(TaskInfo task)
	{
		String saved = configManager.getConfiguration(SlayerCompanionConfig.GROUP, VARIANT_PREFIX + slug(task.getTask()));
		if (saved != null && task.monster(saved) != null)
		{
			return saved;
		}
		List<com.slayercompanion.data.MonsterInfo> variants = task.variants();
		return variants.isEmpty() ? null : variants.get(0).getName();
	}

	public void setVariant(String taskName, @Nullable String monsterName)
	{
		String key = VARIANT_PREFIX + slug(taskName);
		if (monsterName == null)
		{
			configManager.unsetConfiguration(SlayerCompanionConfig.GROUP, key);
		}
		else
		{
			configManager.setConfiguration(SlayerCompanionConfig.GROUP, key, monsterName);
		}
	}

	public void setFavourite(String taskName, @Nullable String locationId)
	{
		String key = FAVOURITE_PREFIX + slug(taskName);
		if (locationId == null)
		{
			configManager.unsetConfiguration(SlayerCompanionConfig.GROUP, key);
		}
		else
		{
			configManager.setConfiguration(SlayerCompanionConfig.GROUP, key, locationId);
		}
	}

	public static Optional<WorldPoint> point(TaskLocation l)
	{
		if (!l.hasCoords())
		{
			return Optional.empty();
		}
		return Optional.of(new WorldPoint(l.getX(), l.getY(), l.getPlane() == null ? 0 : l.getPlane()));
	}

	/** Config key fragment; the same for the game's task name and the wiki's (case, "The ", punctuation ignored). */
	public static String slug(String taskName)
	{
		return com.slayercompanion.data.SlayerData.normalise(taskName);
	}
}
