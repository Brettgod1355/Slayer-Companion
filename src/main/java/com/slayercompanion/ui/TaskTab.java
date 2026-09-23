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

import com.slayercompanion.data.MasterAssignmentInfo;
import com.slayercompanion.data.MonsterInfo;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.task.CurrentTask;
import java.awt.Color;
import java.util.List;
import javax.swing.JPanel;

/** Overview of the current task: how to do it, what to bring, what counts. */
class TaskTab extends JPanel
{
	private final PanelActions actions;

	TaskTab(PanelActions actions)
	{
		this.actions = actions;
		setLayout(new java.awt.BorderLayout());
		setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		CurrentTask task = m.getTask();
		TaskInfo info = m.getInfo();

		if (!m.isLoggedIn())
		{
			col.add(Ui.wrap("Log in to see your task."));
		}
		else if (task == null)
		{
			col.add(Ui.wrap("No Slayer task. Visit a Slayer master to get one."));
			col.add(Ui.gap(6));
			col.add(Ui.wrap("The Points tab shows which master pays best for your next task.", Ui.MUTED));
		}
		else if (info == null)
		{
			col.add(Ui.wrap("No bundled notes for \"" + task.getName() + "\" yet. The Locations and Gear tabs will be empty for this task.", Ui.WARN));
		}
		else
		{
			if (info.getSummary() != null && !info.getSummary().isEmpty())
			{
				JPanel card = Ui.card();
				card.add(Ui.title("How it's done"));
				card.add(Ui.wrap(info.getSummary()));
				if (info.getStyleNotes() != null && !info.getStyleNotes().isEmpty())
				{
					card.add(Ui.gap(4));
					card.add(Ui.wrap(String.join(" \u2022 ", info.getStyleNotes()), Ui.MUTED));
				}
				col.add(card);
				col.add(Ui.gap(4));
			}

			JPanel facts = Ui.card();
			facts.add(Ui.title("Task facts"));
			if (info.getRecommendedStyle() != null)
			{
				facts.add(Ui.keyValue("Style", info.getRecommendedStyle()));
			}
			if (info.getXpPerKill() != null)
			{
				facts.add(Ui.keyValue("Slayer XP / kill", Ui.num(info.getXpPerKill())));
				facts.add(Ui.keyValue("XP left in task", Ui.num((long) info.getXpPerKill() * task.getRemaining())));
			}
			if (info.getSlayerLevel() != null)
			{
				facts.add(Ui.keyValue("Slayer level", String.valueOf(info.getSlayerLevel())));
			}
			if (info.getRequirements() != null && info.getRequirements().getOther() != null
				&& !info.getRequirements().getOther().isEmpty())
			{
				facts.add(Ui.keyValue("Requires", info.getRequirements().getOther()));
			}
			for (MonsterInfo mon : info.monstersOrEmpty())
			{
				if (mon.getCombat() == null && mon.getMaxHit() == null)
				{
					continue;
				}
				StringBuilder sb = new StringBuilder();
				if (mon.getCombat() != null)
				{
					sb.append("lvl ").append(mon.getCombat());
				}
				if (mon.getHitpoints() != null)
				{
					sb.append(", ").append(mon.getHitpoints()).append(" hp");
				}
				if (mon.getAttackStyles() != null && !mon.getAttackStyles().isEmpty())
				{
					sb.append(", ").append(String.join("/", mon.getAttackStyles()));
				}
				if (mon.getMaxHit() != null)
				{
					sb.append(", max ").append(mon.getMaxHit());
				}
				if ("Yes".equalsIgnoreCase(mon.getImmuneCannon()))
				{
					sb.append(", cannon-immune");
				}
				if (mon.getWeakness() != null && !mon.getWeakness().isEmpty())
				{
					sb.append(", weak: ").append(mon.getWeakness());
				}
				facts.add(Ui.keyValue(mon.getName(), sb.toString()));
				break;
			}
			if (info.getSuperior() != null && !info.getSuperior().isEmpty())
			{
				facts.add(Ui.keyValue("Superior", info.getSuperior()));
			}
			List<String> alts = info.alternativesOrEmpty();
			if (!alts.isEmpty())
			{
				facts.add(Ui.keyValue("Also counts", String.join(", ", alts)));
			}
			if (task.getMaster() != null && info.getMasters() != null)
			{
				MasterAssignmentInfo a = info.getMasters().get(task.getMaster().getDataId());
				if (a != null && a.getMin() != null && a.getMax() != null)
				{
					String range = a.getMin() + "-" + a.getMax();
					if (a.getExtMin() != null && a.getExtMax() != null)
					{
						range += " (" + a.getExtMin() + "-" + a.getExtMax() + " extended)";
					}
					facts.add(Ui.keyValue(task.getMaster().getDisplayName() + " assigns", range));
				}
			}
			if (task.getAreaName() != null)
			{
				facts.add(Ui.keyValue("Assigned area", task.getAreaName(), Ui.WARN));
			}
			col.add(facts);
			col.add(Ui.gap(4));

			List<String> required = info.getRequiredItems();
			List<String> useful = info.getUsefulItems();
			if ((required != null && !required.isEmpty()) || (useful != null && !useful.isEmpty()))
			{
				JPanel items = Ui.card();
				items.add(Ui.title("Bring"));
				if (required != null)
				{
					for (String r : required)
					{
						boolean missing = m.getMissingRequiredItems().contains(r);
						items.add(Ui.wrap((missing ? "✗ " : "✓ ") + r + " (required)", missing ? Ui.BAD : Ui.GOOD));
					}
				}
				if (useful != null)
				{
					for (String u : useful)
					{
						items.add(Ui.wrap("• " + u, Color.WHITE));
					}
				}
				if (!m.isBankKnown())
				{
					items.add(Ui.gap(2));
					items.add(Ui.wrap("Open your bank once so the plugin knows what you own.", Ui.MUTED));
				}
				col.add(items);
				col.add(Ui.gap(4));
			}

			if (info.getTrainingSummary() != null)
			{
				com.slayercompanion.data.TrainingSummary ts = info.getTrainingSummary();
				JPanel sum = Ui.card();
				sum.add(Ui.title("Worth doing?"));
				if (ts.getRecommendation() != null && !ts.getRecommendation().isEmpty())
				{
					sum.add(Ui.wrap(ts.getRecommendation(), Color.WHITE));
				}
				if (ts.getPros() != null && !ts.getPros().isEmpty())
				{
					sum.add(Ui.wrap("+ " + ts.getPros(), Ui.GOOD));
				}
				if (ts.getCons() != null && !ts.getCons().isEmpty())
				{
					sum.add(Ui.wrap("\u2212 " + ts.getCons(), Ui.WARN));
				}
				if (ts.getXpPerHour() != null && !ts.getXpPerHour().isEmpty())
				{
					sum.add(Ui.keyValue("XP / hour", ts.getXpPerHour()));
				}
				col.add(sum);
				col.add(Ui.gap(4));
			}

			if (info.getStrategy() != null && !info.getStrategy().isEmpty())
			{
				JPanel strat = Ui.card();
				strat.add(Ui.title("Wiki strategy notes"));
				int shown = 0;
				for (String p : info.getStrategy())
				{
					if (p == null || p.trim().isEmpty())
					{
						continue;
					}
					strat.add(Ui.wrap(p));
					strat.add(Ui.gap(3));
					if (++shown >= 6)
					{
						break;
					}
				}
				col.add(strat);
				col.add(Ui.gap(4));
			}

			String page = info.getWikiTaskPage() == null ? info.getTask() : info.getWikiTaskPage();
			col.add(Ui.buttonRow(Ui.button("Open wiki page", "Open " + page + " in your browser", () -> actions.openWiki(page))));
		}
		add(col, java.awt.BorderLayout.NORTH);
		revalidate();
		repaint();
	}
}
