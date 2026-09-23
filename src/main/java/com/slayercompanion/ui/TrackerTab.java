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
import java.util.List;
import java.util.Map;
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
		}
		else
		{
			JPanel card = Ui.card();
			card.add(Ui.title(s.getTaskName()));
			double h = Math.max(s.getDurationMs(), 60000L) / 3600000.0;
			card.add(Ui.keyValue("Kills", Ui.num(s.getKills()) + " / " + Ui.num(s.getInitialAmount())));
			card.add(Ui.keyValue("Time", duration(s.getDurationMs())));
			card.add(Ui.keyValue("Kills / hour", Ui.num(Math.round(s.getKills() / h))));
			card.add(Ui.keyValue("Slayer XP", Ui.num(s.getSlayerXpGained())));
			card.add(Ui.keyValue("Loot", Ui.gp(s.getLootValue()), Ui.GOOD));
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
				for (Map.Entry<Integer, Integer> e : top(s.getLoot(), 12))
				{
					loot.add(Ui.wrap(itemNames.getOrDefault(e.getKey(), "Item " + e.getKey()) + " x " + Ui.num(e.getValue()), Color.WHITE));
				}
				col.add(loot);
				col.add(Ui.gap(4));
			}
			if (!s.getSupplies().isEmpty())
			{
				JPanel sup = Ui.card();
				sup.add(Ui.title("Supplies used"));
				for (Map.Entry<Integer, Integer> e : top(s.getSupplies(), 12))
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
				hist.add(Ui.keyValue(t.getTaskName(), Ui.gp(t.getProfit()) + " in " + duration(t.getDurationMs()),
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

	private static List<Map.Entry<Integer, Integer>> top(Map<Integer, Integer> map, int n)
	{
		List<Map.Entry<Integer, Integer>> list = new ArrayList<>(map.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list.size() > n ? list.subList(0, n) : list;
	}

	static String duration(long ms)
	{
		long mins = ms / 60000L;
		return mins < 60 ? mins + " min" : (mins / 60) + " h " + (mins % 60) + " min";
	}
}
