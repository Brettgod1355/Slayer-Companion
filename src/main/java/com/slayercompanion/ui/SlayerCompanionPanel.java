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

import com.slayercompanion.SlayerCompanionConfig;
import com.slayercompanion.SlayerCompanionPlugin;
import com.slayercompanion.data.WildernessInfo;
import com.slayercompanion.task.CurrentTask;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/** The side panel: task header plus tabs. All updates arrive on the Swing thread as a {@link PanelModel}. */
public class SlayerCompanionPanel extends PluginPanel
{
	private final JLabel taskLabel = new JLabel();
	private final JLabel subLabel = new JLabel();
	private final ProgressBar progress = new ProgressBar();
	private final JPanel display = new JPanel(new BorderLayout());
	private final MaterialTabGroup tabs = new MaterialTabGroup(display);

	private final TaskTab taskTab;
	private final LocationsTab locationsTab;
	private final GearTab gearTab;
	private final PointsTab pointsTab;
	private final TrackerTab trackerTab;
	private final WildernessTab wildernessTab;
	private final UnlocksTab unlocksTab;
	private final MaterialTab wildernessMaterialTab;

	public SlayerCompanionPanel(PanelActions actions, WildernessInfo wildernessInfo)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(8, 8, 6, 8));
		taskLabel.setFont(FontManager.getRunescapeBoldFont());
		taskLabel.setForeground(Color.WHITE);
		subLabel.setFont(FontManager.getRunescapeFont());
		subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		progress.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(taskLabel);
		header.add(subLabel);
		header.add(Ui.gap(4));
		header.add(progress);

		taskTab = new TaskTab(actions);
		locationsTab = new LocationsTab(actions);
		gearTab = new GearTab(actions);
		pointsTab = new PointsTab();
		trackerTab = new TrackerTab(actions);
		wildernessTab = new WildernessTab(wildernessInfo);
		unlocksTab = new UnlocksTab();

		tabs.setLayout(new java.awt.GridLayout(0, 3, 2, 2));
		tabs.setBorder(new EmptyBorder(4, 4, 0, 4));
		addTab("Task", taskTab);
		addTab("Where", locationsTab);
		addTab("Gear", gearTab);
		addTab("Points", pointsTab);
		addTab("Loot", trackerTab);
		wildernessMaterialTab = addTab("Wild", wildernessTab);
		addTab("Unlocks", unlocksTab);
		tabs.select(tabs.getTab(0));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(header, BorderLayout.NORTH);
		top.add(tabs, BorderLayout.CENTER);

		JPanel displayNorth = new JPanel(new BorderLayout());
		displayNorth.setBackground(ColorScheme.DARK_GRAY_COLOR);
		displayNorth.add(display, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(displayNorth);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(24);
		scroll.setWheelScrollingEnabled(true);
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(top, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		JLabel version = new JLabel("Slayer Companion " + SlayerCompanionPlugin.VERSION, SwingConstants.CENTER);
		version.setFont(FontManager.getRunescapeSmallFont());
		version.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		version.setBorder(new EmptyBorder(2, 0, 2, 0));
		add(version, BorderLayout.SOUTH);
	}

	private MaterialTab addTab(String name, JPanel content)
	{
		// The content goes straight into the shared scroll pane; nested scroll panes swallow the mouse wheel.
		MaterialTab tab = new MaterialTab(name, tabs, content);
		tab.setFont(FontManager.getRunescapeSmallFont());
		tabs.addTab(tab);
		return tab;
	}

	/** Swing thread only. */
	public void update(PanelModel m, SlayerCompanionConfig config)
	{
		CurrentTask t = m.getTask();
		if (!m.isLoggedIn())
		{
			taskLabel.setText("Not logged in");
			subLabel.setText(" ");
			progress.setMaximumValue(1);
			progress.setValue(0);
		}
		else if (t == null)
		{
			taskLabel.setText("No task");
			subLabel.setText(Ui.num(m.getPoints()) + " points • streak " + Ui.num(m.getSharedStreak()));
			progress.setMaximumValue(1);
			progress.setValue(0);
		}
		else
		{
			taskLabel.setText(Ui.html(t.getName() + " — " + t.getRemaining() + " left"));
			String master = t.getMaster() == null ? "unknown master" : t.getMaster().getDisplayName();
			subLabel.setText(Ui.html(master + " • " + Ui.num(t.getPoints()) + " pts • streak " + Ui.num(t.getStreak())
				+ (t.getAreaName() != null ? " • " + t.getAreaName() : "")));
			progress.setMaximumValue(Math.max(1, t.getInitialAmount()));
			progress.setValue(Math.max(0, t.getInitialAmount() - t.getRemaining()));
		}
		wildernessMaterialTab.setVisible(config.showWildernessTab());
		if (!config.showWildernessTab() && wildernessMaterialTab.isSelected())
		{
			tabs.select(tabs.getTab(0));
		}

		taskTab.update(m);
		locationsTab.update(m);
		gearTab.update(m);
		pointsTab.update(m);
		trackerTab.update(m);
		wildernessTab.update(m);
		unlocksTab.update(m);
		revalidate();
		repaint();
	}
}
