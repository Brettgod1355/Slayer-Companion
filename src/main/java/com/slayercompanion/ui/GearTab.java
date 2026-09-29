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

import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.gear.LoadoutDisplay;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;

/**
 * The wiki's recommended gear and strategy for the task or chosen variant, and the player's own
 * loadout for it drawn like the game's equipment and inventory, with an optional Inventory Setups link.
 */
class GearTab extends JPanel
{
	private static final String NO_SETUP = "(none)";

	private final PanelActions actions;
	private final ItemManager itemManager;
	private final RsSprites sprites;
	/** Which recommendation option is shown, per recommendation. */
	private String shownKey;
	private int shownOption;

	GearTab(PanelActions actions, ItemManager itemManager, SpriteManager spriteManager)
	{
		this.actions = actions;
		this.itemManager = itemManager;
		this.sprites = new RsSprites(spriteManager, this::repaint);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		TaskInfo info = m.getInfo();
		if (m.getTask() != null && m.isLoggedIn())
		{
			if (info == null)
			{
				col.add(Ui.wrap("No bundled wiki page for \"" + m.getTask().getName() + "\" yet.", Ui.WARN));
			}
			else
			{
				col.add(wikiCard(m, info));
			}
			col.add(Ui.gap(4));
			col.add(loadoutCard(m, info == null ? m.getTask().getName() : info.getTask()));
			if (info != null && m.isLoggedIn())
			{
				col.add(Ui.gap(4));
				col.add(bestCard(m, info));
			}
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private JPanel wikiCard(PanelModel m, TaskInfo info)
	{
		String variant = m.getSelectedVariant();
		String page = info.gearPageFor(variant);
		JPanel card = Ui.card();
		card.add(Ui.title("Recommended gear & strategy"));
		if (page != null)
		{
			boolean forVariant = variant != null && !page.equals(info.getGearPage());
			card.add(Ui.wrap("The wiki's recommended gear and strategy for " + (forVariant ? variant : info.getTask()) + "."));
		}
		else
		{
			page = info.getWikiTaskPage() == null ? info.getTask() : info.getWikiTaskPage();
			card.add(Ui.wrap("The wiki has no recommended gear table for this task. Its page may still have strategy notes.", Ui.MUTED));
		}
		card.add(Ui.gap(4));
		card.add(Ui.wrap(page, Ui.MUTED));
		card.add(Ui.gap(4));
		String target = page;
		card.add(Ui.buttonRow(Ui.button("Open on the wiki", "Open " + target + " in your browser", () -> actions.openWiki(target))));
		return card;
	}

	private JPanel loadoutCard(PanelModel m, String taskName)
	{
		JPanel card = Ui.card();
		card.add(Ui.title("Your loadout"));
		LoadoutDisplay loadout = m.getLoadout();
		if (!m.isLoggedIn())
		{
			card.add(Ui.wrap("Log in to see your loadout.", Ui.MUTED));
		}
		else if (loadout == null)
		{
			card.add(Ui.wrap("Nothing saved for " + taskName + " yet. Gear up, then save what you are wearing and carrying.", Ui.MUTED));
			card.add(Ui.gap(4));
			card.add(Ui.buttonRow(Ui.button("Save what I have on", "Save your worn equipment and inventory as the loadout for " + taskName,
				() -> actions.saveLoadout(taskName))));
		}
		else
		{
			card.add(new LoadoutView(loadout, itemManager, sprites));
			card.add(Ui.gap(4));
			card.add(summary(loadout));
			card.add(Ui.gap(4));
			card.add(Ui.buttonRow(
				Ui.button("Replace", "Save what you have on now instead", () ->
				{
					if (confirm("Replace your saved loadout for " + taskName + " with what you have on now?"))
					{
						actions.saveLoadout(taskName);
					}
				}),
				Ui.button("Delete", "Forget the saved loadout", () ->
				{
					if (confirm("Delete your saved loadout for " + taskName + "?"))
					{
						actions.deleteLoadout(taskName);
					}
				})));
		}
		addInventorySetups(card, m, taskName);
		return card;
	}

	/** The best-DPS gear from the bank, drawn like the Worn Equipment tab, with the numbers behind it. */
	private JPanel bestCard(PanelModel m, TaskInfo info)
	{
		String taskName = info.getTask();
		com.slayercompanion.data.MonsterInfo monster = info.mainMonster(m.getSelectedVariant());
		String target = monster == null ? taskName : monster.getName();
		JPanel card = Ui.card();
		card.add(Ui.title("Best DPS from your bank"));
		com.slayercompanion.dps.Recommendation r = m.getRecommendation();
		if (m.isRecommending())
		{
			card.add(Ui.wrap("Working it out\u2026", Ui.MUTED));
			return card;
		}
		if (r == null)
		{
			card.add(Ui.wrap("Finds the highest-DPS gear you own for " + target + " from your levels and the wiki's item and monster stats.", Ui.MUTED));
			if (!m.isBankKnown())
			{
				card.add(Ui.wrap("Open your bank once first so the plugin knows what you own.", Ui.WARN));
			}
			card.add(Ui.gap(4));
			card.add(Ui.buttonRow(Ui.button("Recommend from my bank", "Work out the best-DPS gear you own for " + target,
				() -> actions.recommendLoadout(taskName))));
			return card;
		}
		if (r.getUnavailable() != null)
		{
			card.add(Ui.wrap(r.getUnavailable(), Ui.WARN));
			card.add(Ui.gap(4));
			card.add(Ui.buttonRow(Ui.button("Try again", "Work it out again", () -> actions.recommendLoadout(taskName))));
			return card;
		}
		if (!r.getKey().equals(shownKey))
		{
			shownKey = r.getKey();
			shownOption = 0;
		}
		List<String> labels = new ArrayList<>();
		for (com.slayercompanion.dps.LoadoutOptimizer.Option o : r.getOptions())
		{
			labels.add(kind(o.getKind()) + " \u2014 " + dps(o.getResult().getDps()) + " DPS");
		}
		int index = Math.min(shownOption, labels.size() - 1);
		if (labels.size() > 1)
		{
			card.add(Ui.dropdown(labels, labels.get(index), v ->
			{
				shownOption = Math.max(0, labels.indexOf(v));
				actions.refresh();
			}));
			card.add(Ui.gap(4));
		}
		com.slayercompanion.dps.LoadoutOptimizer.Option o = r.getOptions().get(index);
		if (m.getRecommendationDisplays() != null && index < m.getRecommendationDisplays().size())
		{
			card.add(new LoadoutView(m.getRecommendationDisplays().get(index), itemManager, sprites, false));
			card.add(Ui.gap(4));
		}
		com.slayercompanion.dps.AttackStyle style = o.getStyle();
		card.add(Ui.keyValue("Style", style.getStance() == com.slayercompanion.dps.AttackStyle.Stance.AUTOCAST ? style.getName() : style.toString()));
		card.add(Ui.keyValue("Max hit", String.valueOf(o.getResult().getMaxHit())));
		card.add(Ui.keyValue("Accuracy", Math.round(o.getResult().getHitChance() * 100) + "%"));
		card.add(Ui.keyValue("DPS against " + r.getMonster(), dps(o.getResult().getDps()), Ui.GOOD));
		if (r.getCurrent() != null)
		{
			card.add(Ui.keyValue("Your worn gear now", dps(r.getCurrent().getResult().getDps()) + " DPS"));
		}
		card.add(Ui.gap(3));
		card.add(Ui.wrap("Red slots are not on you (hover for where). Empty slots make no DPS difference: wear what you like there.", Ui.MUTED));
		for (String a : r.getAssumptions())
		{
			card.add(Ui.wrap(a, Ui.MUTED));
		}
		card.add(Ui.gap(4));
		int chosen = index;
		card.add(Ui.buttonRow(Ui.button("Use as my loadout", "Save this as your worn gear for " + taskName + " (your saved inventory stays)", () ->
		{
			if (m.getLoadout() == null || confirm("Replace the worn gear of your saved loadout for " + taskName + " with this?"))
			{
				actions.useRecommendation(taskName, chosen);
			}
		})));
		card.add(Ui.gap(3));
		card.add(Ui.buttonRow(Ui.button("Work out again", "Recalculate with your bank as it is now", () -> actions.recommendLoadout(taskName))));
		return card;
	}

	private static String kind(com.slayercompanion.dps.LoadoutOptimizer.Kind kind)
	{
		switch (kind)
		{
			case MELEE:
				return "Melee";
			case RANGED:
				return "Ranged";
			default:
				return "Magic";
		}
	}

	private static String dps(double dps)
	{
		return String.format(java.util.Locale.ROOT, "%.2f", dps);
	}

	private static JLabel summary(LoadoutDisplay loadout)
	{
		if (loadout.isComplete())
		{
			return Ui.wrap("✓ You have everything on you.", Ui.GOOD);
		}
		List<String> parts = new ArrayList<>();
		add(parts, loadout.count(LoadoutDisplay.Status.IN_BANK), "in your bank");
		add(parts, loadout.count(LoadoutDisplay.Status.NOT_OWNED), "not owned");
		add(parts, loadout.count(LoadoutDisplay.Status.UNKNOWN), "not on you (bank not seen yet)");
		add(parts, loadout.count(LoadoutDisplay.Status.PARTLY), "short or in the wrong place");
		return Ui.wrap("Still to get: " + String.join(", ", parts) + ". Hover a slot for details.", Ui.WARN);
	}

	private static void add(List<String> parts, int count, String what)
	{
		if (count > 0)
		{
			parts.add(count + " " + what);
		}
	}

	private void addInventorySetups(JPanel card, PanelModel m, String taskName)
	{
		List<String> setups = m.getInventorySetups() == null ? Collections.emptyList() : m.getInventorySetups();
		String linked = m.getLinkedSetup();
		if (!m.isLoggedIn() || (setups.isEmpty() && linked == null))
		{
			return;
		}
		card.add(Ui.gap(8));
		card.add(Ui.wrap("Inventory Setups", Color.WHITE));
		List<String> options = new ArrayList<>();
		options.add(NO_SETUP);
		options.addAll(setups);
		if (linked != null && !setups.contains(linked))
		{
			options.add(linked);
		}
		card.add(Ui.dropdown(options, linked == null ? NO_SETUP : linked,
			v -> actions.linkInventorySetup(taskName, NO_SETUP.equals(v) ? null : v)));
		if (linked == null)
		{
			card.add(Ui.wrap("Link one of your setups to this task to open it from here. It also opens by itself when you get the task (see settings).", Ui.MUTED));
		}
		else
		{
			if (!setups.contains(linked))
			{
				card.add(Ui.wrap("Inventory Setups has no setup called \"" + linked + "\" right now.", Ui.WARN));
			}
			card.add(Ui.gap(3));
			card.add(Ui.buttonRow(Ui.button("Open in Inventory Setups", "Show " + linked + " in Inventory Setups; it filters your bank to it",
				() -> actions.openInventorySetup(linked))));
		}
	}

	private boolean confirm(String question)
	{
		return JOptionPane.showConfirmDialog(this, question, "Slayer Companion", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}
}
