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

import com.slayercompanion.data.GearTable;
import com.slayercompanion.data.TaskInfo;
import com.slayercompanion.gear.SetupStore;
import com.slayercompanion.gear.SlotAdvice;
import com.slayercompanion.gear.UpgradeAdvisor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Wiki gear per slot next to the best item the player owns, saved setups and upgrade ideas. */
class GearTab extends JPanel
{
	private final PanelActions actions;
	private int selectedTable;
	private String selectedTaskName;

	GearTab(PanelActions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		TaskInfo info = m.getInfo();
		if (m.getTask() == null)
		{
			col.add(Ui.wrap("No task."));
		}
		else
		{
			String taskName = m.getTask().getName();
			if (!taskName.equals(selectedTaskName))
			{
				selectedTaskName = taskName;
				selectedTable = -1;
			}
			if (!m.isBankKnown())
			{
				col.add(Ui.wrap("Open your bank once this session so the plugin can see what you own.", Ui.WARN));
				col.add(Ui.gap(4));
			}

			List<GearTable> tables = m.getGearTables();
			if (tables.isEmpty())
			{
				col.add(Ui.wrap("No wiki gear table bundled for this task."));
			}
			else
			{
				if (selectedTable < 0 || selectedTable >= tables.size())
				{
					selectedTable = info == null ? 0 : defaultIndex(tables, info);
				}
				if (m.isGearIsGeneral())
				{
					col.add(Ui.wrap("The wiki has no gear table for this task, so these are its general Slayer setups by style.", Ui.MUTED));
					col.add(Ui.gap(4));
				}
				JComboBox<String> combo = new JComboBox<>();
				for (GearTable t : tables)
				{
					combo.addItem(label(t));
				}
				combo.setSelectedIndex(selectedTable);
				combo.setFont(Ui.small());
				combo.setAlignmentX(Component.LEFT_ALIGNMENT);
				combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
				combo.addActionListener(e ->
				{
					selectedTable = combo.getSelectedIndex();
					actions.refresh();
				});
				col.add(combo);
				col.add(Ui.gap(4));

				GearTable table = tables.get(selectedTable);
				JPanel card = Ui.card();
				card.add(Ui.title("Slot: yours / wiki best"));
				List<SlotAdvice> advice = selectedTable < m.getGearAdvice().size() ? m.getGearAdvice().get(selectedTable) : java.util.Collections.emptyList();
				for (SlotAdvice a : advice)
				{
					card.add(slotRow(a));
				}
				card.add(Ui.gap(2));
				card.add(Ui.wrap("Green = equipped, white = in bank, orange = a lower tier than the wiki's best, red = none of the listed items owned.", Ui.MUTED));
				col.add(card);
				col.add(Ui.gap(4));
			}

			JPanel setup = Ui.card();
			setup.add(Ui.title("Your setup for " + taskName));
			if (m.getSavedSetup() == null)
			{
				setup.add(Ui.wrap("Nothing saved. Gear up, then save what you are wearing and carrying.", Ui.MUTED));
				setup.add(Ui.gap(3));
				setup.add(Ui.buttonRow(Ui.button("Save current", "Save worn items and inventory as this task's setup",
					() -> actions.saveCurrentSetup(taskName))));
			}
			else
			{
				List<SetupStore.Difference> diffs = m.getSetupDifferences();
				if (diffs.isEmpty())
				{
					setup.add(Ui.wrap("✓ You match your saved setup.", Ui.GOOD));
				}
				else
				{
					for (SetupStore.Difference d : diffs)
					{
						setup.add(Ui.wrap((d.isMissing() ? "✗ " : "• ") + d.getDescription() + (d.isMissing() ? " (not owned)" : ""),
							d.isMissing() ? Ui.BAD : Ui.WARN));
					}
				}
				setup.add(Ui.gap(3));
				setup.add(Ui.buttonRow(
					Ui.button("Overwrite", "Replace the saved setup with what you have now", () -> actions.saveCurrentSetup(taskName)),
					Ui.button("Delete", "Forget the saved setup", () -> actions.deleteSetup(taskName))));
			}
			col.add(setup);
			col.add(Ui.gap(4));

			if (!m.getUpgrades().isEmpty())
			{
				JPanel up = Ui.card();
				up.add(Ui.title("Worth saving for"));
				up.add(Ui.wrap("Top-tier wiki picks you do not own, weighted by how often your master assigns the tasks they help with.", Ui.MUTED));
				for (UpgradeAdvisor.UpgradeSuggestion s : m.getUpgrades())
				{
					String price = s.getPrice() > 0 ? Ui.gp(s.getPrice()) : "untradeable / no price";
					JLabel l = Ui.wrap(s.getItemName() + " (" + s.getSlot() + ") — " + price, Color.WHITE);
					l.setToolTipText(Ui.html("Helps with: " + String.join(", ", s.getTasksHelped())));
					up.add(l);
				}
				col.add(up);
			}
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private static JPanel slotRow(SlotAdvice a)
	{
		Color color;
		String yours;
		if (a.getOwnedBest() == null)
		{
			color = Ui.BAD;
			yours = "none";
		}
		else
		{
			yours = a.getOwnedBest();
			color = a.isEquipped() ? Ui.GOOD : (a.getOwnedTier() == 1 ? Color.WHITE : Ui.WARN);
		}
		JPanel row = Ui.keyValue(a.getSlot(), yours + "  /  " + String.join(" / ", a.getWikiBest()), color);
		StringBuilder tip = new StringBuilder();
		if (!a.getMissingBetter().isEmpty())
		{
			tip.append("Better: ").append(String.join(", ", a.getMissingBetter()));
		}
		if (a.getNote() != null && !a.getNote().isEmpty())
		{
			tip.append(tip.length() > 0 ? "<br>" : "").append(Ui.escape(a.getNote()));
		}
		if (tip.length() > 0)
		{
			row.setToolTipText("<html>" + tip + "</html>");
		}
		return row;
	}

	private static String label(GearTable t)
	{
		if (t.getLabel() != null && !t.getLabel().isEmpty())
		{
			return t.getLabel();
		}
		return t.getStyle() == null ? "Gear" : t.getStyle();
	}

	private static int defaultIndex(List<GearTable> tables, TaskInfo info)
	{
		String preferred = info.getRecommendedStyle();
		if (preferred != null)
		{
			for (int i = 0; i < tables.size(); i++)
			{
				if (preferred.equalsIgnoreCase(tables.get(i).getStyle()))
				{
					return i;
				}
			}
		}
		return 0;
	}
}
