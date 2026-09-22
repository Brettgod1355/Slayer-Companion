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
	private List<String> styleNotes;
	@Nullable
	private List<String> requiredItems;
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
	@Nullable
	private List<GearTable> gearTables;
	@Nullable
	private List<ExampleSetup> exampleSetups;
	@Nullable
	private List<String> strategy;
	@Nullable
	private List<TaskLocation> locations;
	@Nullable
	private List<ShopOption> unlocks;
	@Nullable
	private String notes;

	public List<TaskLocation> locationsOrEmpty()
	{
		return locations == null ? Collections.emptyList() : locations;
	}

	public List<GearTable> gearTablesOrEmpty()
	{
		return gearTables == null ? Collections.emptyList() : gearTables;
	}

	public List<String> alternativesOrEmpty()
	{
		return alternatives == null ? Collections.emptyList() : alternatives;
	}

	public List<MonsterInfo> monstersOrEmpty()
	{
		return monsters == null ? Collections.emptyList() : monsters;
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
