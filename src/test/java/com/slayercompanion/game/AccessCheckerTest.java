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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slayercompanion.data.Access;
import com.slayercompanion.data.AccessGroup;
import com.slayercompanion.data.AccessRule;
import com.slayercompanion.data.TaskLocation;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

public class AccessCheckerTest
{
	private Client client;
	private AccessChecker checker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		LiveSlayerCatalog catalog = mock(LiveSlayerCatalog.class);
		when(catalog.unlocks()).thenReturn(Collections.emptyList());
		Player p = mock(Player.class);
		when(p.getCombatLevel()).thenReturn(90);
		when(client.getLocalPlayer()).thenReturn(p);
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(65);
		when(client.getVarbitValue(VarbitID.MORYTANIA_DIARY_HARD_COMPLETE)).thenReturn(1);
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
}
