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
package com.slayercompanion.dps;

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.ItemStats;
import com.slayercompanion.data.MonsterInfo;
import com.slayercompanion.data.RequiredGear;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.gear.OwnedItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/**
 * Works out the best-DPS gear from what the player owns (bank, inventory and worn) for the task's
 * monster. The inputs are read on the client thread; the search runs on the client's executor so
 * the game never waits for it.
 */
@Slf4j
@Singleton
public class LoadoutRecommender
{
	private final Client client;
	private final SlayerData data;
	private final OwnedItems owned;
	private final SlayerCompanionConfig config;
	private final ScheduledExecutorService executor;

	@Inject
	public LoadoutRecommender(Client client, SlayerData data, OwnedItems owned, SlayerCompanionConfig config, ScheduledExecutorService executor)
	{
		this.client = client;
		this.data = data;
		this.owned = owned;
		this.config = config;
		this.executor = executor;
	}

	public static String key(String task, @Nullable String variant)
	{
		return task + "|" + (variant == null ? "" : variant);
	}

	/** Client thread only; {@code done} is called on the executor thread. */
	public void recommend(TaskInfo task, @Nullable String variant, Consumer<Recommendation> done)
	{
		String key = key(task.getTask(), variant);
		MonsterInfo monster = task.mainMonster(variant);
		String name = monster == null ? task.getTask() : monster.getName();
		if (monster == null || monster.getCombatStats() == null || monster.getCombatStats().getDefenceLevel() == null)
		{
			done.accept(unavailable(key, name, "The wiki has no combat stats for " + name + "."));
			return;
		}
		if (!owned.isBankKnown())
		{
			done.accept(unavailable(key, name, "Open your bank once so the plugin knows what you own."));
			return;
		}
		PlayerStats player = PlayerStats.builder()
			.attack(client.getRealSkillLevel(Skill.ATTACK))
			.strength(client.getRealSkillLevel(Skill.STRENGTH))
			.defence(client.getRealSkillLevel(Skill.DEFENCE))
			.ranged(client.getRealSkillLevel(Skill.RANGED))
			.magic(client.getRealSkillLevel(Skill.MAGIC))
			.prayer(client.getRealSkillLevel(Skill.PRAYER))
			.potions(config.dpsAssumePotions())
			.prayers(config.dpsAssumePrayers())
			.rigourUnlocked(client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) != 0)
			.auguryUnlocked(client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) != 0)
			.deadeyeUnlocked(client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0)
			.mysticVigourUnlocked(client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0)
			.spellbook(client.getVarbitValue(VarbitID.SPELLBOOK))
			.build();
		Set<Integer> ids = new HashSet<>();
		ids.addAll(owned.bank().keySet());
		ids.addAll(owned.inventory().keySet());
		ids.addAll(owned.equipment().keySet());
		Map<Integer, ItemStats> stats = data.itemStats();
		Set<ItemStats> items = new LinkedHashSet<>();
		for (int id : ids)
		{
			ItemStats s = stats.get(id);
			if (s != null)
			{
				items.add(s);
			}
		}
		Map<Integer, Predicate<ItemStats>> required = new HashMap<>();
		List<String> requirements = requirements(task, variant == null ? name : variant, items, required);
		Map<Integer, ItemStats> worn = new HashMap<>();
		for (int id : owned.equipment().keySet())
		{
			ItemStats s = stats.get(id);
			if (s != null && Gear.slotOf(s.getSlot()) >= 0)
			{
				worn.put(Gear.slotOf(s.getSlot()), s);
			}
		}
		try
		{
			executor.execute(() ->
			{
				Recommendation result = null;
				try
				{
					List<LoadoutOptimizer.Option> options = LoadoutOptimizer.best(player, items, monster.getCombatStats(), required);
					LoadoutOptimizer.Option current = LoadoutOptimizer.evaluate(player, worn, monster.getCombatStats());
					result = new Recommendation(key, name, options, current, requirements, assumptions(player, options),
						options.isEmpty() ? "Nothing you own can hurt " + name + " (as far as the plugin can tell)." : null, ids);
				}
				catch (RuntimeException e)
				{
					log.warn("Loadout recommendation failed", e);
				}
				finally
				{
					// Always answer, even after an Error, so the panel never stays on "Working out...".
					done.accept(result != null ? result : unavailable(key, name, "Something went wrong working this out."));
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			done.accept(unavailable(key, name, "Something went wrong working this out."));
		}
	}

	private static Recommendation unavailable(String key, String name, String why)
	{
		return new Recommendation(key, name, Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList(), why,
			Collections.emptySet());
	}

	/**
	 * The equipment the task needs for this variant, as slot rules for the optimizer, and one line
	 * each saying what the recommendation keeps there or that the player owns none of it.
	 */
	private List<String> requirements(TaskInfo task, String variant, Set<ItemStats> owned, Map<Integer, Predicate<ItemStats>> into)
	{
		List<String> lines = new ArrayList<>();
		for (RequiredGear rule : task.requiredGearOrEmpty())
		{
			int slot = Gear.slotOf(rule.getSlot());
			if (slot < 0 || !rule.appliesTo(variant, config.eliteKourendDiary()))
			{
				continue;
			}
			Predicate<ItemStats> accepts = s -> rule.accepts(s.getName());
			into.merge(slot, accepts, Predicate::and);
			boolean ownsOne = owned.stream().anyMatch(s -> Gear.slotOf(s.getSlot()) == slot && rule.accepts(s.getName()));
			String items = describe(rule.itemsOrEmpty());
			lines.add(ownsOne
				? "Keeps " + items + " on: needed " + rule.getReason() + "."
				: "Needs " + items + " " + rule.getReason() + ". You own none, so that slot is left empty.");
		}
		return lines;
	}

	/** "Mirror shield or V's shield"; a "*slayer helmet" pattern reads "Slayer helmet". */
	static String describe(List<String> patterns)
	{
		List<String> names = new ArrayList<>();
		for (String p : patterns)
		{
			String n = p.startsWith("*") ? p.substring(1).trim() : p;
			names.add(Character.toUpperCase(n.charAt(0)) + n.substring(1));
		}
		if (names.size() == 1)
		{
			return names.get(0);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
	}

	private static List<String> assumptions(PlayerStats p, List<LoadoutOptimizer.Option> options)
	{
		List<String> out = new ArrayList<>();
		out.add("On task, so a Slayer helmet or black mask counts.");
		if (p.isPotions())
		{
			out.add("Super combat potion for melee, ranging potion for ranged, magic potion for magic.");
		}
		if (p.isPrayers())
		{
			List<String> prayers = new ArrayList<>();
			for (LoadoutOptimizer.Option o : options)
			{
				String name = DpsCalculator.prayerName(p, o.getStyle().getType());
				if (name != null && !prayers.contains(name))
				{
					prayers.add(name);
				}
			}
			if (!prayers.isEmpty())
			{
				out.add("Prayer: " + String.join(" / ", prayers) + " (by your levels; Rigour, Augury, Deadeye and Mystic Vigour only if unlocked).");
			}
		}
		out.add("Does not check level requirements, ammo tiers, runes, charges or blowpipe darts, and uses no special attacks."
			+ " Enchanted diamond and ruby bolts count their effects, without the hard Kandarin diary's bonus.");
		return out;
	}
}
