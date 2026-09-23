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
package com.slayercompanion.ui;

import com.slayercompanion.data.SlayerData;
import com.slayercompanion.unlocks.UnlockAdvice;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Slayer reward shop, reduced to what matters: the next few things worth buying, anything that
 * helps the current task, and the full shop tucked into collapsed sections without owned entries.
 */
class UnlocksTab extends JPanel
{
	private static final int BUY_NEXT = 5;
	/** Priorities (1 = buy first) that qualify for the "Buy next" card. */
	private static final int BUY_NEXT_MAX_PRIORITY = 3;
	/** Shop sections in display order: bundled category -> heading. */
	private static final Map<String, String> SECTIONS = new LinkedHashMap<>();

	static
	{
		SECTIONS.put("unlock", "Unlocks");
		SECTIONS.put("extend", "Task extensions");
		SECTIONS.put("buy", "Items");
		SECTIONS.put("cosmetic", "Cosmetics");
		SECTIONS.put("other", "Other");
	}

	UnlocksTab()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		List<UnlockAdvice> shop = new ArrayList<>();
		for (UnlockAdvice u : m.getUnlocks())
		{
			// Store, swap, skip and block are actions, not purchases; they get one line at the end.
			if (!"block".equals(u.getCategory()))
			{
				shop.add(u);
			}
		}

		if (shop.isEmpty())
		{
			col.add(Ui.wrap("Log in to see the reward shop."));
		}
		else
		{
			col.add(Ui.wrap(Ui.num(m.getPoints()) + " points to spend", Color.WHITE));
			col.add(Ui.gap(4));

			List<UnlockAdvice> next = new ArrayList<>();
			for (UnlockAdvice u : shop)
			{
				// General purchases only: extensions depend on the task and show under "For your task";
				// free toggles are settings, not purchases.
				if (!owned(u) && u.getCost() > 0 && u.getPriority() <= BUY_NEXT_MAX_PRIORITY
					&& ("unlock".equals(u.getCategory()) || "buy".equals(u.getCategory())))
				{
					next.add(u);
				}
			}
			next.sort(Comparator.comparingInt(UnlockAdvice::getPriority).thenComparingInt(UnlockAdvice::getCost));
			if (!next.isEmpty())
			{
				JPanel card = Ui.card();
				card.add(Ui.section("unlocks.next", "Buy next", false));
				for (UnlockAdvice u : next.subList(0, Math.min(BUY_NEXT, next.size())))
				{
					addDetailed(card, u);
				}
				col.add(card);
				col.add(Ui.gap(4));
			}

			if (m.getTask() != null)
			{
				String task = SlayerData.normalise(m.getTask().getName());
				List<UnlockAdvice> forTask = new ArrayList<>();
				for (UnlockAdvice u : shop)
				{
					if (!owned(u) && u.getCost() > 0 && u.getAffectsTasks() != null
						&& u.getAffectsTasks().stream().anyMatch(t -> SlayerData.normalise(t).equals(task)))
					{
						forTask.add(u);
					}
				}
				if (!forTask.isEmpty())
				{
					JPanel card = Ui.card();
					card.add(Ui.section("unlocks.task", "For your " + m.getTask().getName() + " task", false));
					forTask.sort(Comparator.comparingInt(UnlockAdvice::getCost));
					for (UnlockAdvice u : forTask)
					{
						addDetailed(card, u);
					}
					col.add(card);
					col.add(Ui.gap(4));
				}
			}

			for (Map.Entry<String, String> section : SECTIONS.entrySet())
			{
				List<UnlockAdvice> rows = new ArrayList<>();
				int ownedCount = 0;
				boolean ownedKnown = false;
				for (UnlockAdvice u : shop)
				{
					String cat = u.getCategory() == null || !SECTIONS.containsKey(u.getCategory()) ? "other" : u.getCategory();
					if (!cat.equals(section.getKey()))
					{
						continue;
					}
					ownedKnown |= u.getUnlocked() != null;
					if (owned(u))
					{
						ownedCount++;
					}
					else
					{
						rows.add(u);
					}
				}
				if (rows.isEmpty() && ownedCount == 0)
				{
					continue;
				}
				rows.sort(Comparator.comparingInt(UnlockAdvice::getCost).thenComparing(UnlockAdvice::getName));
				int total = rows.size() + ownedCount;
				String title = section.getValue() + (ownedKnown ? " (" + ownedCount + "/" + total + " owned)" : " (" + total + ")");
				JPanel card = Ui.card();
				card.add(Ui.section("unlocks." + section.getKey(), title, true));
				if (rows.isEmpty())
				{
					card.add(Ui.wrap("You own all of these.", Ui.MUTED));
				}
				for (UnlockAdvice u : rows)
				{
					JPanel row = Ui.priceRow(u.getName(), cost(u), Color.WHITE, u.isAffordable() ? Ui.GOOD : Ui.MUTED);
					if (u.getEffect() != null && !u.getEffect().isEmpty())
					{
						row.setToolTipText(Ui.html(u.getEffect()));
					}
					card.add(row);
					if (u.getElsewhere() != null)
					{
						card.add(Ui.wrap("Cheaper: " + u.getElsewhere(), Ui.WARN));
					}
				}
				col.add(card);
				col.add(Ui.gap(4));
			}

			col.add(Ui.wrap("Skipping a task costs 30 points (100 at Mortimer), blocking 40–120 depending on the master. Storing and swapping tasks is free.", Ui.MUTED));
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private static void addDetailed(JPanel card, UnlockAdvice u)
	{
		card.add(Ui.gap(3));
		card.add(Ui.priceRow(u.getName(), cost(u), Color.WHITE, u.isAffordable() ? Ui.GOOD : Ui.MUTED));
		if (u.getEffect() != null && !u.getEffect().isEmpty())
		{
			card.add(Ui.wrap(u.getEffect(), Ui.MUTED));
		}
		if (u.getElsewhere() != null)
		{
			card.add(Ui.wrap("Cheaper: " + u.getElsewhere(), Ui.WARN));
		}
	}

	private static String cost(UnlockAdvice u)
	{
		return u.getCost() == 0 ? "free" : Ui.num(u.getCost());
	}

	private static boolean owned(UnlockAdvice u)
	{
		return Boolean.TRUE.equals(u.getUnlocked());
	}
}
