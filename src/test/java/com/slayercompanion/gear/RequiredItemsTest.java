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

import static com.slayercompanion.gear.GearFixture.ARDOUGNE_CLOAK_4;
import static com.slayercompanion.gear.GearFixture.DRAGON_DEFENDER_T;
import static com.slayercompanion.gear.GearFixture.FIRE_CAPE;
import static com.slayercompanion.gear.GearFixture.NOSE_PEG;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET_I;
import static com.slayercompanion.gear.GearFixture.TENTACLE;
import static com.slayercompanion.gear.GearFixture.TRIDENT_SWAMP_E;
import static com.slayercompanion.gear.GearFixture.UNCHARGED_TOXIC_TRIDENT;
import static com.slayercompanion.gear.GearFixture.WHIP_OR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** The Bring list's owned/missing check, including how wiki names match item variants. */
public class RequiredItemsTest
{
	private final GearFixture world = new GearFixture();

	private RequiredItems required()
	{
		return new RequiredItems(world.resolver, world.owned);
	}

	/** Which of these names the fixture player owns nowhere. */
	private List<String> missing(String... names)
	{
		return required().missing(Arrays.asList(names));
	}

	private static List<String> none()
	{
		return Collections.emptyList();
	}

	@Test
	public void anyAlternativeCounts()
	{
		world.index(Arrays.asList("Nose peg", "Slayer helmet", "Fire cape")).bank(SLAYER_HELMET_I);
		RequiredItems required = required();
		assertEquals(Collections.singletonList("Fire cape"),
			required.missing(Arrays.asList("Nose peg or Slayer helmet", "Fire cape")));
		world.bank(NOSE_PEG, FIRE_CAPE);
		assertTrue(required.missing(Arrays.asList("Nose peg or Slayer helmet", "Fire cape")).isEmpty());
		assertTrue(required.missing(null).isEmpty());
	}

	@Test
	public void wornAndCarriedItemsCountToo()
	{
		world.index(Arrays.asList("Nose peg", "Fire cape")).worn(NOSE_PEG).inventory(FIRE_CAPE);
		assertEquals(none(), missing("Nose peg", "Fire cape"));
	}

	@Test
	public void ornamentVariantCountsAsOwned()
	{
		// The (or) whip is untradeable, so only the client's item index knows it.
		world.index(Collections.singletonList("Abyssal whip")).bank(WHIP_OR);
		assertEquals(none(), missing("Abyssal whip"));
	}

	@Test
	public void trimmedVariantCountsAsOwned()
	{
		world.index(Collections.singletonList("Dragon defender")).bank(DRAGON_DEFENDER_T);
		assertEquals(none(), missing("Dragon defender"));
	}

	@Test
	public void chargedVariantCountsAsOwned()
	{
		world.index(Collections.singletonList("Trident of the swamp")).bank(TRIDENT_SWAMP_E);
		assertEquals(none(), missing("Trident of the swamp"));
	}

	@Test
	public void imbuedVariantCountsForThePlainName()
	{
		world.index(Collections.singletonList("Slayer helmet")).bank(SLAYER_HELMET_I);
		assertEquals(none(), missing("Slayer helmet"));
	}

	@Test
	public void plainItemDoesNotCountForTheImbuedName()
	{
		world.index(Arrays.asList("Slayer helmet (i)", "Slayer helmet")).bank(SLAYER_HELMET);
		assertEquals(Collections.singletonList("Slayer helmet (i)"), missing("Slayer helmet (i)"));
	}

	@Test
	public void differentItemsWithASharedPrefixDoNotCount()
	{
		world.index(Arrays.asList("Trident of the swamp", "Abyssal whip")).bank(UNCHARGED_TOXIC_TRIDENT, TENTACLE);
		assertEquals(Arrays.asList("Trident of the swamp", "Abyssal whip"), missing("Trident of the swamp", "Abyssal whip"));
	}

	@Test
	public void prefixSearchIsTheLastResort()
	{
		world.index(Collections.singletonList("Ardougne cloak")).bank(ARDOUGNE_CLOAK_4);
		assertEquals(none(), missing("Ardougne cloak"));
	}
}
