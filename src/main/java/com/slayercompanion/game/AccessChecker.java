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
package com.slayercompanion.game;

import com.slayercompanion.data.Access;
import com.slayercompanion.data.AccessGroup;
import com.slayercompanion.data.AccessRule;
import com.slayercompanion.data.TaskLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;

/**
 * Evaluates a location's structured requirements against the player's quest states, levels,
 * diaries, membership and slayer unlocks. Everything is read through the RuneLite API on the
 * client thread; quest states come from the game's own quest script.
 * <p>
 * Unknown rule types or names never lock a location: a wrong lock is worse than a missing one.
 */
@Slf4j
@Singleton
public class AccessChecker
{
	/** Diary completion varbits by their RuneLite constant name (the bundled data refers to them by name). */
	private static final Map<String, Integer> DIARY_VARBITS = new HashMap<>();

	static
	{
		DIARY_VARBITS.put("ARDOUGNE_DIARY_EASY_COMPLETE", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("ARDOUGNE_DIARY_MEDIUM_COMPLETE", VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("ARDOUGNE_DIARY_HARD_COMPLETE", VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("ARDOUGNE_DIARY_ELITE_COMPLETE", VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("FALADOR_DIARY_EASY_COMPLETE", VarbitID.FALADOR_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("FALADOR_DIARY_MEDIUM_COMPLETE", VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("FALADOR_DIARY_HARD_COMPLETE", VarbitID.FALADOR_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("FALADOR_DIARY_ELITE_COMPLETE", VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("WILDERNESS_DIARY_EASY_COMPLETE", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("WILDERNESS_DIARY_MEDIUM_COMPLETE", VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("WILDERNESS_DIARY_HARD_COMPLETE", VarbitID.WILDERNESS_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("WILDERNESS_DIARY_ELITE_COMPLETE", VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("WESTERN_DIARY_EASY_COMPLETE", VarbitID.WESTERN_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("WESTERN_DIARY_MEDIUM_COMPLETE", VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("WESTERN_DIARY_HARD_COMPLETE", VarbitID.WESTERN_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("WESTERN_DIARY_ELITE_COMPLETE", VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("KANDARIN_DIARY_EASY_COMPLETE", VarbitID.KANDARIN_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("KANDARIN_DIARY_MEDIUM_COMPLETE", VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("KANDARIN_DIARY_HARD_COMPLETE", VarbitID.KANDARIN_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("KANDARIN_DIARY_ELITE_COMPLETE", VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("VARROCK_DIARY_EASY_COMPLETE", VarbitID.VARROCK_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("VARROCK_DIARY_MEDIUM_COMPLETE", VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("VARROCK_DIARY_HARD_COMPLETE", VarbitID.VARROCK_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("VARROCK_DIARY_ELITE_COMPLETE", VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("DESERT_DIARY_EASY_COMPLETE", VarbitID.DESERT_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("DESERT_DIARY_MEDIUM_COMPLETE", VarbitID.DESERT_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("DESERT_DIARY_HARD_COMPLETE", VarbitID.DESERT_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("DESERT_DIARY_ELITE_COMPLETE", VarbitID.DESERT_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("MORYTANIA_DIARY_EASY_COMPLETE", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("MORYTANIA_DIARY_MEDIUM_COMPLETE", VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("MORYTANIA_DIARY_HARD_COMPLETE", VarbitID.MORYTANIA_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("MORYTANIA_DIARY_ELITE_COMPLETE", VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("FREMENNIK_DIARY_EASY_COMPLETE", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("FREMENNIK_DIARY_MEDIUM_COMPLETE", VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("FREMENNIK_DIARY_HARD_COMPLETE", VarbitID.FREMENNIK_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("FREMENNIK_DIARY_ELITE_COMPLETE", VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("LUMBRIDGE_DIARY_EASY_COMPLETE", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("LUMBRIDGE_DIARY_MEDIUM_COMPLETE", VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("LUMBRIDGE_DIARY_HARD_COMPLETE", VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("LUMBRIDGE_DIARY_ELITE_COMPLETE", VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("KARAMJA_DIARY_ELITE_COMPLETE", VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);
		DIARY_VARBITS.put("KOUREND_DIARY_EASY_COMPLETE", VarbitID.KOUREND_DIARY_EASY_COMPLETE);
		DIARY_VARBITS.put("KOUREND_DIARY_MEDIUM_COMPLETE", VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE);
		DIARY_VARBITS.put("KOUREND_DIARY_HARD_COMPLETE", VarbitID.KOUREND_DIARY_HARD_COMPLETE);
		DIARY_VARBITS.put("KOUREND_DIARY_ELITE_COMPLETE", VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
	}

	private final Client client;
	private final LiveSlayerCatalog catalog;

	@Inject
	AccessChecker(Client client, LiveSlayerCatalog catalog)
	{
		this.client = client;
		this.catalog = catalog;
	}

	/** Client thread only. */
	public LockState check(TaskLocation location)
	{
		Access access = location.getAccess();
		List<String> reasons = new ArrayList<>();
		List<String> manual = new ArrayList<>();
		boolean checkedAnything = false;
		for (AccessGroup group : access == null ? Collections.<AccessGroup>emptyList() : access.groupsOrEmpty())
		{
			if ("assignment requirement".equals(group.getNote()))
			{
				// Gates whether a master hands the task out, not whether the player can go there.
				continue;
			}
			List<AccessRule> rules = group.getAny() == null ? Collections.<AccessRule>emptyList() : group.getAny();
			if (rules.isEmpty())
			{
				if (group.getText() != null)
				{
					manual.add(group.getText());
				}
				continue;
			}
			Boolean satisfied = null;
			boolean unevaluated = false;
			for (AccessRule rule : rules)
			{
				Boolean r = evaluate(rule);
				if (r == null)
				{
					unevaluated = true;
					continue;
				}
				satisfied = satisfied == null ? r : (satisfied || r);
			}
			if (satisfied == null || (!satisfied && unevaluated))
			{
				// Nothing in this group could be evaluated, or the only alternatives that might hold cannot be.
				if (group.getText() != null)
				{
					manual.add(group.getText());
				}
				continue;
			}
			checkedAnything = true;
			if (!satisfied)
			{
				reasons.add(describe(group));
			}
			else if (group.isManual() && group.getText() != null)
			{
				// Partly checkable: the checkable part holds, the rest is on the player.
				manual.add(group.getText());
			}
		}
		LockState.Kind kind = !reasons.isEmpty() ? LockState.Kind.LOCKED : (checkedAnything ? LockState.Kind.OPEN : LockState.Kind.UNKNOWN);
		return new LockState(kind, Collections.unmodifiableList(reasons), Collections.unmodifiableList(manual));
	}

	/** True/false when the rule can be evaluated, null when it cannot. */
	@Nullable
	Boolean evaluate(AccessRule rule)
	{
		if (rule.getType() == null)
		{
			return null;
		}
		try
		{
			switch (rule.getType())
			{
				case "quest":
				{
					Quest quest = questByName(rule.getQuest());
					if (quest == null)
					{
						return null;
					}
					QuestState state = quest.getState(client);
					if ("IN_PROGRESS".equalsIgnoreCase(rule.getState()))
					{
						return state != QuestState.NOT_STARTED;
					}
					return state == QuestState.FINISHED;
				}
				case "skill":
				{
					Skill skill = skillByName(rule.getSkill());
					if (skill == null || rule.getLevel() == null)
					{
						return null;
					}
					return client.getRealSkillLevel(skill) >= rule.getLevel();
				}
				case "combat":
					if (rule.getLevel() == null || client.getLocalPlayer() == null)
					{
						return null;
					}
					return client.getLocalPlayer().getCombatLevel() >= rule.getLevel();
				case "diary":
				{
					Integer varbit = rule.getVarbit() == null ? null : DIARY_VARBITS.get(rule.getVarbit());
					if (varbit == null)
					{
						return null;
					}
					return client.getVarbitValue(varbit) == 1;
				}
				case "members":
					return client.getWorldType().contains(WorldType.MEMBERS);
				case "unlock":
				{
					if (rule.getName() == null)
					{
						return null;
					}
					for (LiveSlayerCatalog.Unlock u : catalog.unlocks())
					{
						if (u.getName().equalsIgnoreCase(rule.getName()))
						{
							return catalog.isUnlocked(u);
						}
					}
					return null;
				}
				default:
					return null;
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Could not evaluate access rule {}", rule, e);
			return null;
		}
	}

	@Nullable
	private static Quest questByName(@Nullable String enumName)
	{
		if (enumName == null)
		{
			return null;
		}
		for (Quest q : Quest.values())
		{
			if (q.name().equals(enumName))
			{
				return q;
			}
		}
		return null;
	}

	@Nullable
	private static Skill skillByName(@Nullable String enumName)
	{
		if (enumName == null)
		{
			return null;
		}
		for (Skill s : Skill.values())
		{
			if (s.name().equals(enumName))
			{
				return s;
			}
		}
		return null;
	}

	private static String describe(AccessGroup group)
	{
		if (group.getText() != null && !group.getText().isEmpty())
		{
			return group.getText();
		}
		List<String> parts = new ArrayList<>();
		for (AccessRule r : group.getAny() == null ? Collections.<AccessRule>emptyList() : group.getAny())
		{
			parts.add(r.getName() != null ? r.getName() : r.getType());
		}
		return String.join(" or ", parts);
	}
}
