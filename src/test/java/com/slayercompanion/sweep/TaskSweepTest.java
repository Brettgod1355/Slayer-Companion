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
package com.slayercompanion.sweep;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.dps.Recommendation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Every bundled task and variant: the Task view renders without cut-off text, content wider than
 * the panel, empty cards or leftover wiki markup, and "Recommend from my bank" (maxed sample
 * account) keeps the gear the task needs and never picks melee that cannot reach. Run
 * {@link TaskSweepReport} for the full report with pictures.
 */
public class TaskSweepTest
{
	/** Findings that only describe the data (no stats for Vardorvis, no vampyrebane weapon owned). */
	private static final List<String> INFORMATIONAL = Collections.singletonList("rec-unavailable");

	private static TaskSweep sweep;

	@BeforeClass
	public static void setUp()
	{
		sweep = new TaskSweep(new SlayerData(new Gson()), Collections.emptyMap());
	}

	@AfterClass
	public static void tearDown()
	{
		sweep.close();
	}

	@Test
	public void everyTaskRendersCleanlyAndRecommendsSensibly() throws Exception
	{
		List<TaskSweep.Finding> findings = Collections.synchronizedList(new ArrayList<>());
		List<TaskSweep.Case> cases = sweep.cases();
		assertTrue("expected every bundled task, got " + cases.size(), cases.size() > 300);
		for (TaskSweep.Case c : cases)
		{
			Recommendation r = sweep.recommend(c, SampleBank.MAXED);
			TaskSweep.checkRecommendation(c, SampleBank.MAXED, r, findings);
			sweep.checkLaunchers(c, SampleBank.MAXED, r, findings);
			sweep.render(sweep.model(c, r, TaskSweep.loadout(r)), c.label(), findings, false);
			sweep.render(sweep.model(c, null, null), c.label() + " (no loadout)", findings, false);
		}
		List<String> problems = findings.stream()
			.filter(f -> !INFORMATIONAL.contains(f.check))
			.map(TaskSweep.Finding::toString)
			.collect(Collectors.toList());
		assertTrue(problems.size() + " problems:\n" + String.join("\n", problems.subList(0, Math.min(40, problems.size()))),
			problems.isEmpty());
	}

	@Test
	public void recommendationsKeepRequiredGearForEveryAccount() throws Exception
	{
		List<TaskSweep.Finding> findings = new ArrayList<>();
		for (TaskSweep.Case c : sweep.cases())
		{
			if (c.info.requiredGearOrEmpty().isEmpty() && c.info.monstersOrEmpty().stream()
				.noneMatch(m -> m.getCombatStats() != null && (m.getCombatStats().getMeleeReach() != null || m.getCombatStats().is("flying"))))
			{
				continue;
			}
			for (SampleBank bank : Arrays.asList(SampleBank.LOW, SampleBank.MID))
			{
				TaskSweep.checkRecommendation(c, bank, sweep.recommend(c, bank), findings);
			}
		}
		List<String> problems = findings.stream()
			.filter(f -> f.check.equals("rec-ignores-required") || f.check.equals("rec-melee-out-of-reach"))
			.map(TaskSweep.Finding::toString)
			.collect(Collectors.toList());
		assertTrue(String.join("\n", problems), problems.isEmpty());
	}
}
