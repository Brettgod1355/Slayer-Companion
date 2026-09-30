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
import com.slayercompanion.worth.LootEstimate;
import com.slayercompanion.worth.Verdict;
import java.awt.Color;
import java.util.List;
import javax.swing.JPanel;

/**
 * The current task, in two parts of the Task view: the header (which monster, the verdict, or how
 * to get a task) above the locations and gear, and the details (how it's done, facts, what to
 * bring, Combat Achievements, strategy) below them.
 */
class TaskTab extends JPanel
{
	enum Part
	{
		HEADER, DETAILS
	}

	private final PanelActions actions;
	private final Part part;

	TaskTab(PanelActions actions, Part part)
	{
		this.actions = actions;
		this.part = part;
		setLayout(new java.awt.BorderLayout());
		setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		CurrentTask task = m.getTask();
		TaskInfo info = m.getInfo();

		if (part == Part.DETAILS && (!m.isLoggedIn() || task == null || info == null))
		{
			add(col, java.awt.BorderLayout.NORTH);
			revalidate();
			repaint();
			return;
		}
		if (!m.isLoggedIn())
		{
			col.add(Ui.wrap("Log in to see your task."));
		}
		else if (task == null)
		{
			col.add(Ui.wrap("No Slayer task. Visit a Slayer master to get one."));
			col.add(Ui.gap(4));
			col.add(mastersCard(m));
		}
		else if (info == null)
		{
			col.add(Ui.wrap("No bundled notes for \"" + task.getName() + "\" yet, so there is little below for this task.", Ui.WARN));
		}
		else if (part == Part.HEADER)
		{
			if (m.getVariants().size() > 1)
			{
				JPanel variant = Ui.card();
				variant.add(Ui.title("Which are you killing?"));
				variant.add(Ui.dropdown(m.getVariants(), m.getSelectedVariant(), v -> actions.setVariant(task.getName(), v)));
				variant.add(Ui.wrap("Locations, map markers and XP follow this choice. Remembered per task.", Ui.MUTED));
				col.add(variant);
				col.add(Ui.gap(4));
			}
			col.add(verdictCard(m, info));
		}
		else
		{
			if (info.getSummary() != null && !info.getSummary().isEmpty())
			{
				JPanel card = Ui.card();
				card.add(Ui.title("How it's done"));
				card.add(Ui.wrap(info.getSummary()));
				col.add(card);
				col.add(Ui.gap(4));
			}

			JPanel facts = Ui.card();
			facts.add(Ui.title("Task facts"));
			if (info.getRecommendedStyle() != null)
			{
				facts.add(Ui.keyValue("Style", info.getRecommendedStyle()));
			}
			if (m.getXpPerKill() != null)
			{
				facts.add(Ui.keyValue("Slayer XP / kill", Ui.num(m.getXpPerKill())));
				facts.add(Ui.keyValue("XP left in task", Ui.num((long) m.getXpPerKill() * task.getRemaining())));
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
				if (m.getSelectedVariant() != null && !m.getSelectedVariant().equalsIgnoreCase(mon.getName()))
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
					// ", " rather than "/" so a long list can wrap
					sb.append(", ").append(String.join(", ", mon.getAttackStyles()));
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
						if (m.getNonItemRequirements() != null && m.getNonItemRequirements().contains(r))
						{
							items.add(Ui.wrap("\u2022 " + r + " (required)", Color.WHITE));
						}
						else if (!m.isBankKnown() && missing)
						{
							items.add(Ui.wrap("? " + r + " (required)", Ui.MUTED));
						}
						else
						{
							items.add(Ui.wrap((missing ? "✗ " : "✓ ") + r + " (required)", missing ? Ui.BAD : Ui.GOOD));
						}
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

			JPanel achievements = achievementsCard(m, info);
			if (achievements != null)
			{
				col.add(achievements);
				col.add(Ui.gap(4));
			}

			if (info.getStrategy() != null && !info.getStrategy().isEmpty())
			{
				JPanel strat = Ui.card();
				strat.add(Ui.section("Wiki strategy notes", "Wiki strategy notes", true));
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

	/** With no task: a route to the master who pays most for the next task, and to the last master. */
	private JPanel mastersCard(PanelModel m)
	{
		JPanel card = Ui.card();
		card.add(Ui.title("Get your next task"));
		List<PanelModel.MasterRoute> routes = m.getMasterRoutes() == null ? java.util.Collections.emptyList() : m.getMasterRoutes();
		if (routes.isEmpty())
		{
			card.add(Ui.wrap("The Points tab shows which master pays best for your next task.", Ui.MUTED));
			return card;
		}
		if (!m.isShortestPathAvailable())
		{
			card.add(Ui.wrap("Install and enable the Shortest Path plugin to draw the way.", Ui.MUTED));
		}
		for (PanelModel.MasterRoute r : routes)
		{
			card.add(Ui.gap(4));
			card.add(Ui.wrap(r.getName() + " \u2014 " + r.getWhy(), Color.WHITE));
			if (r.getPlace() != null)
			{
				card.add(Ui.wrap(r.getPlace(), Ui.MUTED));
			}
			for (String tip : r.getTravel())
			{
				card.add(Ui.wrap("\u2022 " + tip, Ui.MUTED));
			}
			javax.swing.JButton route = Ui.button("Route to " + r.getName(), "Ask Shortest Path to draw the way to " + r.getName(), () -> actions.routeToMaster(r.getName()));
			route.setEnabled(m.isShortestPathAvailable());
			card.add(Ui.buttonRow(route));
		}
		return card;
	}

	/** The wiki's do / skip / block advice, what it costs, and what the kills left are worth. */
	private static JPanel verdictCard(PanelModel m, TaskInfo info)
	{
		JPanel card = Ui.card();
		card.add(Ui.title("Verdict"));
		Verdict v = m.getVerdict();
		if (v != null)
		{
			card.add(headline(v.getKind()));
			card.add(Ui.wrap(v.getWikiSays() == null ? "The wiki's Slayer training guide has no advice for this task." : "Wiki: " + v.getWikiSays(), Ui.MUTED));
			for (String note : v.getNotes())
			{
				card.add(Ui.wrap(note, Ui.MUTED));
			}
		}
		com.slayercompanion.data.TrainingSummary ts = info.getTrainingSummary();
		if (ts != null)
		{
			if (ts.getPros() != null && !ts.getPros().isEmpty())
			{
				card.add(Ui.wrap("+ " + ts.getPros(), Ui.GOOD));
			}
			if (ts.getCons() != null && !ts.getCons().isEmpty())
			{
				card.add(Ui.wrap("\u2212 " + ts.getCons(), Ui.WARN));
			}
			if (ts.getXpPerHour() != null && !ts.getXpPerHour().isEmpty())
			{
				card.add(Ui.keyValue("XP / hour", ts.getXpPerHour()));
			}
		}

		card.add(Ui.gap(4));
		LootEstimate e = m.getLootEstimate();
		if (e == null)
		{
			com.slayercompanion.data.MonsterInfo mon = info.mainMonster(m.getSelectedVariant());
			card.add(Ui.wrap("The wiki has no drop rates for " + (mon == null ? info.getTask() : mon.getName()) + ", so no loot estimate.", Ui.MUTED));
			return card;
		}
		card.add(Ui.keyValue("Loot for " + Ui.num(e.getKills()) + " left", "\u2248 " + Ui.gp(e.getTotal()), Ui.GOOD));
		card.add(Ui.keyValue("Per kill", "\u2248 " + Ui.gp(e.getPerKill())));
		for (LootEstimate.Unique u : e.getUniques())
		{
			card.add(Ui.wrap(u.getItem() + " (" + u.getRarity() + "): " + percent(u.getChanceOverKills()) + " chance before the task ends", Color.WHITE));
		}
		card.add(Ui.wrap("Average for " + e.getMonster() + " from the wiki's drop rates and today's GE prices; untradeables count as 0.", Ui.MUTED));
		return card;
	}

	private static javax.swing.JLabel headline(Verdict.Kind kind)
	{
		String text;
		Color color;
		switch (kind)
		{
			case DO:
				text = "Do it";
				color = Ui.GOOD;
				break;
			case SKIP:
				text = "Skip it";
				color = Ui.WARN;
				break;
			case BLOCK:
				text = "Block it";
				color = Ui.BAD;
				break;
			case DEPENDS:
				text = "Depends what you want";
				color = Color.WHITE;
				break;
			default:
				text = "Your call";
				color = Ui.MUTED;
				break;
		}
		javax.swing.JLabel l = Ui.wrap(text, color);
		l.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
		return l;
	}

	/** Combat Achievements for the task's monsters (only the chosen variant's when one is picked); null when none. */
	@javax.annotation.Nullable
	private static JPanel achievementsCard(PanelModel m, TaskInfo info)
	{
		com.slayercompanion.data.MonsterInfo picked = info.monster(m.getSelectedVariant());
		List<com.slayercompanion.data.CombatAchievementInfo> list = new java.util.ArrayList<>();
		for (com.slayercompanion.data.CombatAchievementInfo ca : info.combatAchievementsOrEmpty())
		{
			if (picked == null || ca.getMonster().equalsIgnoreCase(picked.getPage()) || ca.getMonster().equalsIgnoreCase(picked.getName()))
			{
				list.add(ca);
			}
		}
		if (list.isEmpty())
		{
			return null;
		}
		java.util.Set<Integer> completed = m.getCompletedAchievements() == null ? java.util.Collections.emptySet() : m.getCompletedAchievements();
		int done = 0;
		for (com.slayercompanion.data.CombatAchievementInfo ca : list)
		{
			done += completed.contains(ca.getId()) ? 1 : 0;
		}
		JPanel card = Ui.card();
		card.add(Ui.section("Combat Achievements", "Combat Achievements (" + done + "/" + list.size() + " done)", true));
		String monster = null;
		for (com.slayercompanion.data.CombatAchievementInfo ca : list)
		{
			if (!ca.getMonster().equals(monster))
			{
				monster = ca.getMonster();
				card.add(Ui.gap(3));
				card.add(Ui.wrap(monster, Color.WHITE));
			}
			boolean ok = completed.contains(ca.getId());
			card.add(Ui.wrap((ok ? "\u2713 " : "\u2022 ") + ca.getName() + " \u00b7 " + ca.getTier(), ok ? Ui.GOOD : Ui.WARN));
			card.add(Ui.wrap(ca.getTask(), Ui.MUTED));
		}
		return card;
	}

	static String percent(double p)
	{
		if (p > 0 && p < 0.01)
		{
			return "<1%";
		}
		return Math.round(p * 100) + "%";
	}
}
