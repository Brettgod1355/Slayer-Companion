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
package com.slayercompanion.gear;

import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET_I;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static com.slayercompanion.gear.GearFixture.WHIP_OR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import net.runelite.api.events.GameTick;
import org.junit.Test;

public class ItemIndexTest
{
	private final GearFixture world = new GearFixture();

	@Test
	public void indexesOnlyWantedNamesIgnoringCaseAndNotes()
	{
		world.index(Collections.singletonList("ABYSSAL WHIP"));
		// The noted whip shares the name but is not a separate item.
		assertEquals(Collections.singletonList(WHIP), world.index.ids("abyssal whip"));
		assertEquals(Collections.singletonList(WHIP_OR), world.index.ids("Abyssal whip (or)"));
		assertTrue(world.index.ids("Abyssal tentacle").isEmpty());
	}

	@Test
	public void variantsOfWantedNamesAreIndexedToo()
	{
		world.index(Collections.singletonList("Slayer helmet"));
		assertEquals(Collections.singletonList(SLAYER_HELMET), world.index.ids("Slayer helmet"));
		assertEquals(Collections.singletonList(SLAYER_HELMET_I), world.index.ids("Slayer helmet (i)"));
	}

	@Test
	public void baseNameDropsOnlyATrailingParenthetical()
	{
		assertEquals("slayer helmet", ItemIndex.baseName("slayer helmet (i)"));
		assertEquals("dragon defender", ItemIndex.baseName("dragon defender (t)"));
		assertEquals("salve amulet(ei)", ItemIndex.baseName("salve amulet(ei)"));
		assertEquals("abyssal whip", ItemIndex.baseName("abyssal whip"));
		assertEquals("(i)", ItemIndex.baseName("(i)"));
	}

	@Test
	public void scanRunsInSlicesAcrossTicks()
	{
		when(world.client.getItemCount()).thenReturn(6000);
		world.index.want(Collections.singletonList("Abyssal whip"));
		world.index.onGameTick(new GameTick());
		assertFalse(world.index.isComplete());
		world.index.onGameTick(new GameTick());
		world.index.onGameTick(new GameTick());
		assertTrue(world.index.isComplete());
		assertEquals(Collections.singletonList(WHIP), world.index.ids("Abyssal whip"));
	}

	@Test
	public void newNamesRestartTheScan()
	{
		world.index(Collections.singletonList("Abyssal whip"));
		assertTrue(world.index.isComplete());
		world.index.want(Collections.singletonList("abyssal whip"));
		assertTrue("an already wanted name changes nothing", world.index.isComplete());
		world.index.want(Collections.singletonList("Fire cape"));
		assertFalse(world.index.isComplete());
		world.index(Collections.<String>emptyList());
		assertEquals(Collections.singletonList(GearFixture.FIRE_CAPE), world.index.ids("fire cape"));
		assertEquals(Collections.singletonList(WHIP), world.index.ids("abyssal whip"));
	}

	@Test
	public void nothingWantedMeansNoScan()
	{
		world.index.onGameTick(new GameTick());
		assertFalse(world.index.isComplete());
		assertTrue(world.index.ids("Abyssal whip").isEmpty());
	}
}
