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

import static com.slayercompanion.gear.GearFixture.DRAGON_DEFENDER;
import static com.slayercompanion.gear.GearFixture.RUNE_SCIMITAR;
import static com.slayercompanion.gear.GearFixture.SLAYER_HELMET_I;
import static com.slayercompanion.gear.GearFixture.TENTACLE;
import static com.slayercompanion.gear.GearFixture.WHIP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.dps.Gear;
import com.slayercompanion.dps.LoadoutOptimizer;
import com.slayercompanion.dps.LoadoutRecommender;
import com.slayercompanion.dps.Recommendation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Skill;
import org.junit.After;
import org.junit.Test;

public class LoadoutRecommenderTest
{
	private final GearFixture world = new GearFixture();
	private final SlayerData data = new SlayerData(new Gson());
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@After
	public void tearDown()
	{
		executor.shutdownNow();
	}

	private Recommendation recommend(TaskInfo task) throws Exception
	{
		for (Skill s : Skill.values())
		{
			when(world.client.getRealSkillLevel(s)).thenReturn(80);
		}
		SlayerCompanionConfig config = mock(SlayerCompanionConfig.class);
		LoadoutRecommender recommender = new LoadoutRecommender(world.client, data, world.owned, config, executor);
		CompletableFuture<Recommendation> result = new CompletableFuture<>();
		recommender.recommend(task, null, result::complete);
		return result.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void aStoppedExecutorStillGetsAnAnswer() throws Exception
	{
		world.bank(WHIP);
		executor.shutdownNow();
		// The panel would otherwise stay on "Working out..." for good.
		Recommendation r = recommend(data.task("Abyssal demons").get());
		assertTrue(r.getOptions().isEmpty());
		assertEquals("Something went wrong working this out.", r.getUnavailable());
	}

	@Test
	public void needsTheBankFirst() throws Exception
	{
		Recommendation r = recommend(data.task("Abyssal demons").get());
		assertTrue(r.getOptions().isEmpty());
		assertTrue(r.getUnavailable().contains("Open your bank"));
	}

	@Test
	public void keepsTheShieldADragonTaskNeedsAndSaysSo() throws Exception
	{
		world.bank(WHIP, DRAGON_DEFENDER, GearFixture.ANTI_DRAGON_SHIELD);
		Recommendation r = recommend(data.task("Black dragons").get());
		LoadoutOptimizer.Option best = r.getOptions().get(0);
		assertEquals("Anti-dragon shield", best.getGear().get(Gear.SHIELD).getName());
		assertTrue(r.getRequirements().get(0), r.getRequirements().get(0).startsWith("Keeps Anti-dragon shield"));
	}

	@Test
	public void saysWhenTheNeededShieldIsNotOwned() throws Exception
	{
		world.bank(WHIP, DRAGON_DEFENDER);
		Recommendation r = recommend(data.task("Black dragons").get());
		assertNull(r.getOptions().get(0).getGear().get(Gear.SHIELD));
		assertTrue(r.getRequirements().get(0), r.getRequirements().get(0).startsWith("Needs Anti-dragon shield"));
	}

	@Test
	public void bestMeleeFromTheBankAndHowTheWornGearCompares() throws Exception
	{
		world.bank(WHIP, TENTACLE, SLAYER_HELMET_I, DRAGON_DEFENDER).worn(RUNE_SCIMITAR);
		Recommendation r = recommend(data.task("Abyssal demons").get());
		assertNull(r.getUnavailable());
		assertEquals("Abyssal demon", r.getMonster());
		LoadoutOptimizer.Option best = r.getOptions().get(0);
		assertEquals(LoadoutOptimizer.Kind.MELEE, best.getKind());
		assertEquals("Abyssal tentacle", best.getGear().get(Gear.WEAPON).getName());
		assertNotNull(r.getCurrent());
		assertTrue(best.getResult().getDps() > r.getCurrent().getResult().getDps());
		assertEquals(Integer.valueOf(TENTACLE), LoadoutOptimizer.itemIds(best.getGear(), r.getOwned()).get(Gear.WEAPON));
	}
}
