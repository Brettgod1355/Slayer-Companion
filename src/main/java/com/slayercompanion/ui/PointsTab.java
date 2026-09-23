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

import com.slayercompanion.points.PointsPlan;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Points, streaks and which master pays best next. Advice only; nothing acts on the game. */
class PointsTab extends JPanel
{
	PointsTab()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		if (!m.isLoggedIn())
		{
			col.add(Ui.wrap("Log in to see your points."));
		}
		else
		{
			JPanel status = Ui.card();
			status.add(Ui.title("Now"));
			status.add(Ui.keyValue("Points", Ui.num(m.getPoints())));
			status.add(Ui.keyValue("Streak", Ui.num(m.getSharedStreak())));
			status.add(Ui.keyValue("Wilderness streak", Ui.num(m.getWildernessStreak())));
			status.add(Ui.keyValue("Mortimer streak", Ui.num(m.getMortimerStreak())));
			col.add(status);
			col.add(Ui.gap(4));

			PointsPlan plan = m.getPointsPlan();
			if (plan != null)
			{
				JPanel next = Ui.card();
				next.add(Ui.title("Next task (#" + plan.getNextTaskNumber() + ")"));
				next.add(Ui.wrap(plan.getSummary(), plan.getNextMilestoneInterval() > 0 ? Ui.GOOD : Color.WHITE));
				next.add(Ui.gap(3));
				for (PointsPlan.MasterOption o : plan.getOptions())
				{
					boolean best = plan.getRecommended() != null && o.getMasterName().equals(plan.getRecommended().getMasterName());
					next.add(Ui.keyValue(o.getMasterName(), o.getPointsForNextTask() + " pts", best ? Ui.GOOD : Color.WHITE));
				}
				next.add(Ui.gap(3));
				next.add(Ui.wrap("Krystilia (25 base) and Mortimer (modifier-based) use their own streaks and are not part of this list. Skipping costs 30 points at most masters (100 at Mortimer); a Turael task resets the shared streak.", Ui.MUTED));
				col.add(next);
				col.add(Ui.gap(4));
			}

			JPanel rules = Ui.card();
			rules.add(Ui.title("How milestones work"));
			rules.add(Ui.wrap("Bonus points are paid on every 10th, 50th, 100th, 250th and 1,000th consecutive task. When several apply, the plugin assumes the largest one is paid. Points start on your 5th task in a row."));
			col.add(rules);
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}
}
