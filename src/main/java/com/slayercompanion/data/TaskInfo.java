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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

/** Everything bundled about one Slayer assignment. */
@Data
public class TaskInfo
{
	@Data
	public static class ShopOption
	{
		private String name;
		@Nullable
		private Integer cost;
		@Nullable
		private String note;
	}

	private String task;
	@Nullable
	private String wikiTaskPage;
	@Nullable
	private List<String> monsterPages;
	private boolean bossTask;
	@Nullable
	private String summary;
	@Nullable
	private String recommendedStyle;
	@Nullable
	private List<String> requiredItems;
	/** Equipment the loadout recommendation keeps in its slot (from requiredItems, hand-checked). */
	@Nullable
	private List<RequiredGear> requiredGear;
	@Nullable
	private List<String> usefulItems;
	@Nullable
	private String superior;
	@Nullable
	private List<String> alternatives;
	@Nullable
	private Integer xpPerKill;
	@Nullable
	private TaskRequirements requirements;
	@Nullable
	private List<MonsterInfo> monsters;
	@Nullable
	private Map<String, MasterAssignmentInfo> masters;
	/** Wiki page with the task's own recommended gear; null when the wiki has none. */
	@Nullable
	private String gearPage;
	/** Variant name to the wiki page with that variant's own recommended gear (Vorkath on Blue dragons). */
	@Nullable
	private Map<String, String> variantGearPages;
	@Nullable
	private List<String> strategy;
	@Nullable
	private List<TaskLocation> locations;
	@Nullable
	private List<ShopOption> unlocks;
	@Nullable
	private String notes;
	@Nullable
	private TrainingSummary trainingSummary;
	@Nullable
	private String recommendedStyleSource;
	@Nullable
	private List<CombatAchievementInfo> combatAchievements;

	public List<TaskLocation> locationsOrEmpty()
	{
		return locations == null ? Collections.emptyList() : locations;
	}

	/**
	 * Wiki page with the recommended gear and strategy for the chosen variant: the variant's own
	 * page when it has one (e.g. Demonic gorilla on a Black demons task), else the task's; null
	 * when the wiki has neither.
	 */
	@Nullable
	public String gearPageFor(@Nullable String variant)
	{
		if (variant != null && variantGearPages != null)
		{
			for (Map.Entry<String, String> e : variantGearPages.entrySet())
			{
				if (variant.equalsIgnoreCase(e.getKey()))
				{
					return e.getValue();
				}
			}
		}
		return gearPage;
	}

	public List<RequiredGear> requiredGearOrEmpty()
	{
		return requiredGear == null ? Collections.emptyList() : requiredGear;
	}

	public List<String> alternativesOrEmpty()
	{
		return alternatives == null ? Collections.emptyList() : alternatives;
	}

	public List<MonsterInfo> monstersOrEmpty()
	{
		return monsters == null ? Collections.emptyList() : monsters;
	}

	/** Monsters the player can choose between: those the wiki places somewhere, superiors excluded. */
	public List<MonsterInfo> variants()
	{
		List<MonsterInfo> out = new java.util.ArrayList<>();
		for (MonsterInfo m : monstersOrEmpty())
		{
			if (m.getName() == null || (superior != null && superior.equalsIgnoreCase(m.getName())))
			{
				continue;
			}
			boolean placed = false;
			for (TaskLocation l : locationsOrEmpty())
			{
				if (l.getMonsters() != null && l.getMonsters().contains(m.getName()))
				{
					placed = true;
					break;
				}
			}
			if (placed)
			{
				out.add(m);
			}
		}
		return out;
	}

	@Nullable
	public MonsterInfo monster(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}
		for (MonsterInfo m : monstersOrEmpty())
		{
			if (name.equalsIgnoreCase(m.getName()))
			{
				return m;
			}
		}
		return null;
	}

	/** The chosen variant, else the task's main monster (the first one the wiki places), else the first monster. */
	@Nullable
	public MonsterInfo mainMonster(@Nullable String variant)
	{
		MonsterInfo chosen = monster(variant);
		if (chosen != null)
		{
			return chosen;
		}
		List<MonsterInfo> placed = variants();
		if (!placed.isEmpty())
		{
			return placed.get(0);
		}
		return monstersOrEmpty().isEmpty() ? null : monstersOrEmpty().get(0);
	}

	public List<CombatAchievementInfo> combatAchievementsOrEmpty()
	{
		return combatAchievements == null ? Collections.emptyList() : combatAchievements;
	}

	/** Slayer XP per kill of the chosen variant when the wiki gives one, else the task's own figure. */
	@Nullable
	public Integer xpPerKillFor(@Nullable String variant)
	{
		MonsterInfo chosen = monster(variant);
		return chosen != null && chosen.getSlayerXp() != null ? chosen.getSlayerXp() : xpPerKill;
	}

	/** All NPC ids the wiki lists for this task's monsters. */
	public java.util.Set<Integer> npcIds()
	{
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (MonsterInfo m : monstersOrEmpty())
		{
			if (m.getNpcIds() != null)
			{
				ids.addAll(m.getNpcIds());
			}
		}
		return ids;
	}

	@Nullable
	public Integer getSlayerLevel()
	{
		return requirements == null ? null : requirements.getSlayer();
	}

	@Nullable
	public Integer getCombatLevel()
	{
		return requirements == null ? null : requirements.getCombat();
	}
}
