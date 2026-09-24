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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.data.Access;
import com.slayercompanion.data.AccessGroup;
import com.slayercompanion.data.AccessRule;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.data.TaskLocation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

public class AccessCheckerTest
{
	/** Values the game's quest status script leaves on the int stack. */
	private static final int NOT_STARTED = 1;
	private static final int FINISHED = 2;
	private static final int IN_PROGRESS = 0;

	private Client client;
	private LiveSlayerCatalog catalog;
	private AccessChecker checker;
	/** Quest id -> script result; quests not listed are NOT_STARTED. */
	private final Map<Integer, Integer> questStates = new HashMap<>();
	private int lastQuestId;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		catalog = mock(LiveSlayerCatalog.class);
		when(catalog.unlocks()).thenReturn(Collections.emptyList());
		Player p = mock(Player.class);
		when(p.getCombatLevel()).thenReturn(90);
		when(client.getLocalPlayer()).thenReturn(p);
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(65);
		when(client.getVarbitValue(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE)).thenReturn(1);
		// Quest.getState runs the quest status script with the quest id, then reads the int stack.
		doAnswer(inv ->
		{
			lastQuestId = (Integer) inv.getArguments()[1];
			return null;
		}).when(client).runScript(any());
		when(client.getIntStack()).thenAnswer(inv -> new int[]{questStates.getOrDefault(lastQuestId, NOT_STARTED)});
		checker = new AccessChecker(client, catalog);
	}

	private static AccessRule rule(String type, String skill, Integer level, String varbit)
	{
		AccessRule r = new AccessRule();
		r.setType(type);
		r.setSkill(skill);
		r.setLevel(level);
		r.setVarbit(varbit);
		return r;
	}

	private static AccessRule quest(String enumName, String state)
	{
		AccessRule r = new AccessRule();
		r.setType("quest");
		r.setQuest(enumName);
		r.setName(enumName);
		r.setState(state);
		return r;
	}

	private static AccessRule unlock(String name)
	{
		AccessRule r = new AccessRule();
		r.setType("unlock");
		r.setName(name);
		return r;
	}

	private static TaskLocation location(AccessGroup... groups)
	{
		TaskLocation l = new TaskLocation();
		Access a = new Access();
		a.setGroups(Arrays.asList(groups));
		l.setAccess(a);
		return l;
	}

	private static AccessGroup group(String text, boolean manual, AccessRule... rules)
	{
		AccessGroup g = new AccessGroup();
		g.setText(text);
		g.setManual(manual);
		g.setAny(Arrays.asList(rules));
		return g;
	}

	private static AccessGroup note(String note, AccessGroup g)
	{
		g.setNote(note);
		return g;
	}

	@Test
	public void unmetSkillLocks()
	{
		LockState s = checker.check(location(group("70 Agility", false, rule("skill", "AGILITY", 70, null))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals("70 Agility", s.getReasons().get(0));
	}

	@Test
	public void anyAlternativeSatisfiesTheGroup()
	{
		LockState s = checker.check(location(group("Dusty key or 70 Agility", true,
			rule("skill", "AGILITY", 70, null), rule("combat", null, 50, null))));
		assertEquals(LockState.Kind.OPEN, s.getKind());
		// The manual part (the key) is still reported.
		assertEquals(1, s.getManual().size());
	}

	@Test
	public void diaryAndMembersAreChecked()
	{
		LockState s = checker.check(location(
			group("Morytania Hard diary", false, rule("diary", null, null, "MORYTANIA_DIARY_HARD_COMPLETE")),
			group("Members", false, rule("members", null, null, null))));
		assertEquals(LockState.Kind.OPEN, s.getKind());
	}

	@Test
	public void onlyManualOrUnknownRulesGiveUnknown()
	{
		LockState s = checker.check(location(group("Light source", true), group("Weird", false, rule("mystery", null, null, null))));
		assertEquals(LockState.Kind.UNKNOWN, s.getKind());
		assertEquals(2, s.getManual().size());
	}

	@Test
	public void noAccessDataGivesUnknown()
	{
		assertEquals(LockState.Kind.UNKNOWN, checker.check(new TaskLocation()).getKind());
	}

	// --- AND across groups, OR within a group ---

	@Test
	public void everyGroupMustHold()
	{
		LockState s = checker.check(location(
			group("60 Agility", false, rule("skill", "AGILITY", 60, null)),
			group("Combat 100", false, rule("combat", null, 100, null))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		// Only the unmet group is a reason.
		assertEquals(Collections.singletonList("Combat 100"), s.getReasons());
	}

	@Test
	public void allGroupsMetIsOpen()
	{
		LockState s = checker.check(location(
			group("60 Agility", false, rule("skill", "AGILITY", 60, null)),
			group("Combat 90", false, rule("combat", null, 90, null))));
		assertEquals(LockState.Kind.OPEN, s.getKind());
		assertTrue(s.getReasons().isEmpty());
		assertTrue(s.getManual().isEmpty());
	}

	@Test
	public void groupLocksOnlyWhenEveryAlternativeFails()
	{
		TaskLocation l = location(group("70 Agility or 70 Strength", false,
			rule("skill", "AGILITY", 70, null), rule("skill", "STRENGTH", 70, null)));
		LockState s = checker.check(l);
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals(Collections.singletonList("70 Agility or 70 Strength"), s.getReasons());

		when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(70);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
	}

	@Test
	public void reasonWithoutTextIsBuiltFromRuleNames()
	{
		AccessRule a = quest("DRAGON_SLAYER_II", "FINISHED");
		a.setName("Dragon Slayer II");
		AccessRule b = quest("SONG_OF_THE_ELVES", "FINISHED");
		b.setName("Song of the Elves");
		LockState s = checker.check(location(group(null, false, a, b)));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals("Dragon Slayer II or Song of the Elves", s.getReasons().get(0));
	}

	@Test
	public void unmetManualGroupWithRulesStillLocks()
	{
		// The checkable part failing is enough; the manual detail does not soften it.
		LockState s = checker.check(location(group("66 Slayer (82 for Ancient Wyverns)", true, rule("skill", "SLAYER", 66, null))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertTrue(s.getManual().isEmpty());
	}

	// --- quests ---

	@Test
	public void finishedQuestRequirement()
	{
		TaskLocation l = location(group("Dragon Slayer II", false, quest("DRAGON_SLAYER_II", "FINISHED")));
		assertEquals(LockState.Kind.LOCKED, checker.check(l).getKind());
		questStates.put(Quest.DRAGON_SLAYER_II.getId(), IN_PROGRESS);
		assertEquals("started is not finished", LockState.Kind.LOCKED, checker.check(l).getKind());
		questStates.put(Quest.DRAGON_SLAYER_II.getId(), FINISHED);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
	}

	@Test
	public void inProgressQuestRequirementMeansAtLeastStarted()
	{
		TaskLocation l = location(group("Regicide (partial)", false, quest("REGICIDE", "IN_PROGRESS")));
		assertEquals(LockState.Kind.LOCKED, checker.check(l).getKind());
		questStates.put(Quest.REGICIDE.getId(), IN_PROGRESS);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
		questStates.put(Quest.REGICIDE.getId(), FINISHED);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
	}

	@Test
	public void unknownQuestNameNeverLocks()
	{
		LockState s = checker.check(location(group("Some future quest", false, quest("NOT_A_REAL_QUEST", "FINISHED"))));
		assertEquals(LockState.Kind.UNKNOWN, s.getKind());
		assertTrue(s.getReasons().isEmpty());
		assertEquals(Collections.singletonList("Some future quest"), s.getManual());
	}

	@Test
	public void unknownAlternativeKeepsAFailedGroupFromLocking()
	{
		// "A or B" where A cannot be checked and B fails: A might hold, so this must not be a lock.
		LockState s = checker.check(location(group("Some future quest or 70 Agility", false,
			quest("NOT_A_REAL_QUEST", "FINISHED"), rule("skill", "AGILITY", 70, null))));
		assertEquals(LockState.Kind.UNKNOWN, s.getKind());
		assertTrue(s.getReasons().isEmpty());
		assertEquals(Collections.singletonList("Some future quest or 70 Agility"), s.getManual());
	}

	@Test
	public void unknownAlternativeDoesNotHideAMetOne()
	{
		LockState s = checker.check(location(group("Some future quest or 60 Agility", false,
			quest("NOT_A_REAL_QUEST", "FINISHED"), rule("skill", "AGILITY", 60, null))));
		assertEquals(LockState.Kind.OPEN, s.getKind());
	}

	@Test
	public void unknownGroupDoesNotHideALockElsewhere()
	{
		LockState s = checker.check(location(
			group("Some future quest", false, quest("NOT_A_REAL_QUEST", "FINISHED")),
			group("70 Agility", false, rule("skill", "AGILITY", 70, null))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals(Collections.singletonList("70 Agility"), s.getReasons());
		assertEquals(Collections.singletonList("Some future quest"), s.getManual());
	}

	// --- diaries, skills, combat, members ---

	@Test
	public void unmetDiaryLocks()
	{
		LockState s = checker.check(location(group("Wilderness Medium diary", false,
			rule("diary", null, null, "WILDERNESS_DIARY_MEDIUM_COMPLETE"))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
	}

	@Test
	public void unknownDiaryNameNeverLocks()
	{
		// Karamja easy/medium/hard have no completion varbit.
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Karamja Hard diary", false,
			rule("diary", null, null, "KARAMJA_DIARY_HARD_COMPLETE")))).getKind());
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Diary", false,
			rule("diary", null, null, null)))).getKind());
	}

	@Test
	public void skillThresholdIsInclusiveAndUsesRealLevel()
	{
		TaskLocation l = location(group("70 Agility", false, rule("skill", "AGILITY", 70, null)));
		when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(69);
		when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(75);
		assertEquals("a boost does not count", LockState.Kind.LOCKED, checker.check(l).getKind());
		when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(70);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
		when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(71);
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
	}

	@Test
	public void unknownSkillOrMissingLevelNeverLocks()
	{
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("70 Dungeoneering", false,
			rule("skill", "DUNGEONEERING", 70, null)))).getKind());
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Agility", false,
			rule("skill", "AGILITY", null, null)))).getKind());
	}

	@Test
	public void combatThresholdIsInclusive()
	{
		assertEquals(LockState.Kind.OPEN, checker.check(location(group("Combat 90", false, rule("combat", null, 90, null)))).getKind());
		assertEquals(LockState.Kind.LOCKED, checker.check(location(group("Combat 91", false, rule("combat", null, 91, null)))).getKind());
	}

	@Test
	public void combatWithoutLocalPlayerIsUnknown()
	{
		when(client.getLocalPlayer()).thenReturn(null);
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Combat 91", false, rule("combat", null, 91, null)))).getKind());
	}

	@Test
	public void membersOnlyLocksOnAFreeWorld()
	{
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		LockState s = checker.check(location(group("Members", false, rule("members", null, null, null))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals(Collections.singletonList("Members"), s.getReasons());
	}

	@Test
	public void ruleThatThrowsIsUnknown()
	{
		when(client.getWorldType()).thenThrow(new IllegalStateException("not ready"));
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Members", false, rule("members", null, null, null)))).getKind());
	}

	// --- slayer unlocks ---

	private void liveUnlock(String name, Boolean owned)
	{
		LiveSlayerCatalog.Unlock u = new LiveSlayerCatalog.Unlock(name, "", 200, 1234, 1);
		List<LiveSlayerCatalog.Unlock> all = new ArrayList<>(catalog.unlocks());
		all.add(u);
		when(catalog.unlocks()).thenReturn(all);
		when(catalog.isUnlocked(u)).thenReturn(owned);
	}

	@Test
	public void ownedUnlockOpens()
	{
		liveUnlock("Like a Boss", true);
		assertEquals(LockState.Kind.OPEN, checker.check(location(group("Like a Boss", false, unlock("Like a Boss")))).getKind());
	}

	@Test
	public void missingUnlockLocks()
	{
		liveUnlock("Like a Boss", false);
		LockState s = checker.check(location(group("Like a boss unlock (200 Slayer reward points)", false, unlock("Like a Boss"))));
		assertEquals(LockState.Kind.LOCKED, s.getKind());
		assertEquals("Like a boss unlock (200 Slayer reward points)", s.getReasons().get(0));
	}

	@Test
	public void unlockNamesMatchIgnoringCase()
	{
		liveUnlock("LIKE A BOSS", true);
		assertEquals(LockState.Kind.OPEN, checker.check(location(group("Like a Boss", false, unlock("Like a Boss")))).getKind());
	}

	@Test
	public void unverifiedUnlockBitsNeverLock()
	{
		// LiveSlayerCatalog answers null when COL_BIT could not be proven to be a varbit id.
		liveUnlock("Like a Boss", null);
		LockState s = checker.check(location(group("Like a Boss", false, unlock("Like a Boss"))));
		assertEquals(LockState.Kind.UNKNOWN, s.getKind());
		assertTrue(s.getReasons().isEmpty());
	}

	@Test
	public void unlockMissingFromTheLiveListNeverLocks()
	{
		liveUnlock("Bigger and Badder", false);
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Like a Boss", false, unlock("Like a Boss")))).getKind());
		assertEquals(LockState.Kind.UNKNOWN, checker.check(location(group("Unlock", false, unlock(null)))).getKind());
	}

	// --- master assignment requirements ---

	@Test
	public void assignmentRequirementsNeverLockOrShow()
	{
		LockState s = checker.check(location(
			note("assignment requirement", group("Combat 100 (to be assigned)", false, rule("combat", null, 100, null))),
			note("assignment requirement", group("Like a Boss unlock to receive boss tasks", false, unlock("Like a Boss"))),
			note("assignment requirement", group("Death Plateau (to receive GWD tasks)", true))));
		assertEquals(LockState.Kind.UNKNOWN, s.getKind());
		assertTrue(s.getReasons().isEmpty());
		assertTrue(s.getManual().isEmpty());
	}

	@Test
	public void assignmentRequirementBesideARealRequirement()
	{
		TaskLocation l = location(
			group("60 Agility", false, rule("skill", "AGILITY", 60, null)),
			note("assignment requirement", group("Combat 100 (to be assigned)", false, rule("combat", null, 100, null))));
		assertEquals(LockState.Kind.OPEN, checker.check(l).getKind());
	}

	// --- bundled data ---

	private static List<TaskLocation> bundledLocations()
	{
		List<TaskLocation> out = new ArrayList<>();
		for (TaskInfo t : new SlayerData(new Gson()).tasks())
		{
			out.addAll(t.locationsOrEmpty());
		}
		assertFalse(out.isEmpty());
		return out;
	}

	private static List<AccessGroup> groups(TaskLocation l)
	{
		return l.getAccess() == null ? Collections.emptyList() : l.getAccess().groupsOrEmpty();
	}

	@Test
	public void maxedAccountFindsNoBundledLocationLocked()
	{
		List<TaskLocation> locations = bundledLocations();
		List<LiveSlayerCatalog.Unlock> unlocks = new ArrayList<>();
		for (TaskLocation l : locations)
		{
			for (AccessGroup g : groups(l))
			{
				for (AccessRule r : g.getAny() == null ? Collections.<AccessRule>emptyList() : g.getAny())
				{
					if ("unlock".equals(r.getType()) && r.getName() != null)
					{
						unlocks.add(new LiveSlayerCatalog.Unlock(r.getName(), "", 1, 1, 1));
					}
				}
			}
		}
		when(catalog.unlocks()).thenReturn(unlocks);
		when(catalog.isUnlocked(any())).thenReturn(true);
		for (Skill skill : Skill.values())
		{
			when(client.getRealSkillLevel(skill)).thenReturn(99);
		}
		Player maxed = mock(Player.class);
		when(maxed.getCombatLevel()).thenReturn(126);
		when(client.getLocalPlayer()).thenReturn(maxed);
		when(client.getVarbitValue(anyInt())).thenReturn(1);
		when(client.getIntStack()).thenReturn(new int[]{FINISHED});

		for (TaskLocation l : locations)
		{
			LockState s = checker.check(l);
			assertTrue(l.getName() + " is locked for a maxed account: " + s.getReasons(), s.getKind() != LockState.Kind.LOCKED);
		}
	}

	@Test
	public void freshAccountIsNeverLockedByAssignmentRequirements()
	{
		for (Skill skill : Skill.values())
		{
			when(client.getRealSkillLevel(skill)).thenReturn(1);
		}
		Player fresh = mock(Player.class);
		when(fresh.getCombatLevel()).thenReturn(3);
		when(client.getLocalPlayer()).thenReturn(fresh);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(client.getVarbitValue(anyInt())).thenReturn(0);
		for (TaskLocation l : bundledLocations())
		{
			LockState s = checker.check(l);
			assertNotNull(s);
			for (AccessGroup g : groups(l))
			{
				if ("assignment requirement".equals(g.getNote()))
				{
					assertFalse(l.getName() + " locked by an assignment requirement", s.getReasons().contains(g.getText()));
					assertFalse(l.getName() + " lists an assignment requirement as a manual one", s.getManual().contains(g.getText()));
				}
			}
		}
	}
}
