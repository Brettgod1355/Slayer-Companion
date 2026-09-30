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

import com.slayercompanion.tracker.TaskSession;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Loot, supplies and profit for the current task and recent history. */
class TrackerTab extends JPanel
{
	private final PanelActions actions;
	private Map<Integer, String> itemNames = java.util.Collections.emptyMap();

	TrackerTab(PanelActions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		itemNames = m.getItemNames();
		JPanel col = Ui.column();
		TaskSession s = m.getSession();
		if (s == null)
		{
			col.add(Ui.wrap("No task session. Tracking starts when a task is assigned."));
			List<TaskSession> history = m.getHistory();
			if (!history.isEmpty() && history.get(0).isCompleted())
			{
				col.add(Ui.gap(4));
				col.add(summaryCard(history.get(0), expected(m, 0)));
			}
		}
		else
		{
			JPanel card = Ui.card();
			card.add(Ui.title(s.getTaskName()));
			double h = Math.max(s.getDurationMs(), 60000L) / 3600000.0;
			card.add(Ui.keyValue("Kills", Ui.num(s.getKills()) + " / " + Ui.num(s.getInitialAmount())));
			if (s.getKillsAtStart() > 0)
			{
				// Tracking began mid-task or was reset: the numbers below are for these kills only.
				card.add(Ui.keyValue("Tracked", Ui.num(s.getTrackedKills()) + " kills", Ui.MUTED));
			}
			card.add(Ui.keyValue("Time", duration(s.getDurationMs())));
			card.add(Ui.keyValue("Kills / hour", Ui.num(Math.round(s.getTrackedKills() / h))));
			card.add(Ui.keyValue("Slayer XP", Ui.num(s.getSlayerXpGained())));
			card.add(Ui.keyValue("Loot", Ui.gp(s.getLootValue()), Ui.GOOD));
			addLuck(card, s, m.getSessionExpected());
			card.add(Ui.keyValue("Supplies", Ui.gp(s.getSuppliesValue()), Ui.WARN));
			long profit = s.getProfit();
			card.add(Ui.keyValue("Profit", Ui.gp(profit), profit >= 0 ? Ui.GOOD : Ui.BAD));
			card.add(Ui.keyValue("Profit / hour", Ui.gp(Math.round(profit / h)), profit >= 0 ? Ui.GOOD : Ui.BAD));
			card.add(Ui.gap(3));
			card.add(Ui.buttonRow(Ui.button("Reset", "Start this task's numbers again from zero", actions::resetSession)));
			col.add(card);
			col.add(Ui.gap(4));

			if (!s.getLoot().isEmpty())
			{
				JPanel loot = Ui.card();
				loot.add(Ui.title("Loot"));
				for (Map.Entry<Integer, Integer> e : top(s.getLoot(), m.getItemPrices(), 12))
				{
					loot.add(Ui.wrap(itemNames.getOrDefault(e.getKey(), "Item " + e.getKey()) + " x " + Ui.num(e.getValue()), Color.WHITE));
				}
				col.add(loot);
				col.add(Ui.gap(4));
			}
			if (s.getSupplies().values().stream().anyMatch(q -> q > 0))
			{
				JPanel sup = Ui.card();
				sup.add(Ui.title("Supplies used"));
				for (Map.Entry<Integer, Integer> e : top(s.getSupplies(), m.getItemPrices(), 12))
				{
					sup.add(Ui.wrap(itemNames.getOrDefault(e.getKey(), "Item " + e.getKey()) + " x " + Ui.num(e.getValue()), Color.WHITE));
				}
				col.add(sup);
				col.add(Ui.gap(4));
			}
		}

		if (!m.getHistory().isEmpty())
		{
			JPanel hist = Ui.card();
			hist.add(Ui.title("Recent tasks"));
			int shown = 0;
			for (TaskSession t : m.getHistory())
			{
				Long exp = expected(m, shown);
				String luck = exp == null || exp <= 0 ? "" : ", luck " + luck(t.getLootValue(), exp);
				hist.add(Ui.keyValue(t.getTaskName(), Ui.gp(t.getProfit()) + " in " + duration(t.getDurationMs()) + luck,
					t.getProfit() >= 0 ? Ui.GOOD : Ui.BAD));
				if (++shown >= 8)
				{
					break;
				}
			}
			col.add(hist);
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	/** The last finished task at a glance. */
	private static JPanel summaryCard(TaskSession t, Long expected)
	{
		JPanel card = Ui.card();
		card.add(Ui.title("Last task: " + t.getTaskName()));
		card.add(Ui.keyValue("Kills", Ui.num(t.getTrackedKills())));
		card.add(Ui.keyValue("Time", duration(t.getDurationMs())));
		card.add(Ui.keyValue("Slayer XP", Ui.num(t.getSlayerXpGained())));
		card.add(Ui.keyValue("Loot", Ui.gp(t.getLootValue()), Ui.GOOD));
		addLuck(card, t, expected);
		card.add(Ui.keyValue("Profit", Ui.gp(t.getProfit()), t.getProfit() >= 0 ? Ui.GOOD : Ui.BAD));
		return card;
	}

	/** Loot against the wiki average for the same number of kills. */
	private static void addLuck(JPanel card, TaskSession s, Long expected)
	{
		if (expected == null || expected <= 0 || s.getTrackedKills() == 0)
		{
			return;
		}
		long diff = s.getLootValue() - expected;
		JPanel row = Ui.keyValue("Luck", luck(s.getLootValue(), expected), diff >= 0 ? Ui.GOOD : Ui.WARN);
		row.setToolTipText("Average loot for " + Ui.num(s.getTrackedKills()) + " kills is about " + Ui.gp(expected)
			+ " (wiki drop rates, today's GE prices).");
		card.add(row);
	}

	static String luck(long loot, long expected)
	{
		long pct = Math.round((loot - expected) * 100.0 / expected);
		return (pct >= 0 ? "+" : "\u2212") + Math.abs(pct) + "%";
	}

	private static Long expected(PanelModel m, int index)
	{
		List<Long> list = m.getHistoryExpected();
		return list == null || index >= list.size() ? null : list.get(index);
	}

	/** The {@code n} entries worth most (quantity x price; quantity when unpriced), positive quantities only. */
	static List<Map.Entry<Integer, Integer>> top(Map<Integer, Integer> map, @Nullable Map<Integer, Long> prices, int n)
	{
		List<Map.Entry<Integer, Integer>> list = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : map.entrySet())
		{
			if (e.getValue() > 0)
			{
				list.add(e);
			}
		}
		java.util.function.ToLongFunction<Map.Entry<Integer, Integer>> worth = e ->
		{
			Long price = prices == null ? null : prices.get(e.getKey());
			return price == null || price <= 0 ? e.getValue() : price * e.getValue();
		};
		list.sort(Comparator.comparingLong(worth).reversed());
		return list.size() > n ? list.subList(0, n) : list;
	}

	static String duration(long ms)
	{
		long mins = ms / 60000L;
		return mins < 60 ? mins + " min" : (mins / 60) + " h " + (mins % 60) + " min";
	}
}
