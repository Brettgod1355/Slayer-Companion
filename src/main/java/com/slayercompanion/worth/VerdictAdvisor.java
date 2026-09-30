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
package com.slayercompanion.worth;

import com.slayercompanion.data.MasterInfo;
import com.slayercompanion.data.TaskInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Turns the wiki's "Slayer training" recommendation for a task into do / skip / block and adds
 * what that costs at your master. The advice is the wiki's; this only reads it and does the sums.
 */
public final class VerdictAdvisor
{
	private VerdictAdvisor()
	{
	}

	public static Verdict verdict(@Nullable TaskInfo task, @Nullable MasterInfo master, int points)
	{
		String wiki = task == null || task.getTrainingSummary() == null ? null : task.getTrainingSummary().getRecommendation();
		Verdict.Kind kind = kind(wiki);
		List<String> notes = new ArrayList<>();
		if (master != null && (kind == Verdict.Kind.SKIP || kind == Verdict.Kind.DEPENDS || kind == Verdict.Kind.BLOCK))
		{
			int skip = master.getSkipCost();
			notes.add(points >= skip
				? "Skipping costs " + skip + " points; you have " + points + "."
				: "Skipping costs " + skip + " points; you have " + points + ", not enough.");
			if (kind == Verdict.Kind.BLOCK && master.getBlockCost() > 0)
			{
				int block = master.getBlockCost();
				notes.add(points >= block
					? "Blocking costs " + block + " points and stops " + blockList(master) + " assigning it again."
					: "Blocking costs " + block + " points; you have " + points + ", not enough.");
				if (master.getBlockSlots() != null)
				{
					notes.add(master.getName() + " has only " + master.getBlockSlots() + " block slots.");
				}
			}
		}
		return new Verdict(kind, wiki == null || wiki.trim().isEmpty() ? null : wiki.trim(), notes);
	}

	/** Who a block applies to: each master keeps a block list, except that Turael (and Aya) share Spria's. */
	private static String blockList(MasterInfo master)
	{
		return "turael".equals(master.getId()) || "spria".equals(master.getId()) ? "Turael and Spria" : master.getName();
	}

	/**
	 * The first word decides ("Skip unless you are an ironman ..." is a skip, "Block with the unique
	 * unlock" a block); "Do for profit; skip for XP" and "Do if ..." depend on you. Advice about
	 * unlocking the task ("Should not be unlocked") says nothing about a task you already have.
	 */
	static Verdict.Kind kind(@Nullable String wiki)
	{
		if (wiki == null || wiki.trim().isEmpty())
		{
			return Verdict.Kind.NONE;
		}
		String w = wiki.trim().toLowerCase(Locale.ROOT);
		if (w.startsWith("should") || w.startsWith("can be unlocked") || w.startsWith("don't unlock"))
		{
			return Verdict.Kind.NONE;
		}
		if (w.startsWith("block"))
		{
			return Verdict.Kind.BLOCK;
		}
		if (w.startsWith("skip"))
		{
			return Verdict.Kind.SKIP;
		}
		if (w.startsWith("do"))
		{
			return w.startsWith("do if") || w.contains("skip") || w.contains("block") ? Verdict.Kind.DEPENDS : Verdict.Kind.DO;
		}
		return Verdict.Kind.DEPENDS;
	}
}
