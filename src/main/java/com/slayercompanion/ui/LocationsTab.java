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

import com.slayercompanion.data.TaskLocation;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Where to fight the task, with multi/cannon/wilderness flags, favourite and routing. */
class LocationsTab extends JPanel
{
	private final PanelActions actions;

	LocationsTab(PanelActions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		if (m.getTask() == null)
		{
			col.add(Ui.wrap("No task."));
		}
		else if (m.getLocations().isEmpty())
		{
			col.add(Ui.wrap("No locations bundled for " + m.getTask().getName() + "."));
		}
		else
		{
			if (!m.isShortestPathAvailable())
			{
				col.add(Ui.wrap("Install and enable the Shortest Path plugin to draw routes to a location.", Ui.MUTED));
				col.add(Ui.gap(4));
			}
			if (m.getTask().getAreaName() != null)
			{
				col.add(Ui.wrap("Your master assigned: " + m.getTask().getAreaName() + ". Kills elsewhere do not count.", Ui.WARN));
				col.add(Ui.gap(4));
			}
			String taskName = m.getTask().getName();
			for (TaskLocation l : m.getLocations())
			{
				col.add(card(l, taskName, l.getId().equals(m.getFavouriteLocationId()), m.isShortestPathAvailable()));
				col.add(Ui.gap(4));
			}
			col.add(Ui.buttonRow(Ui.button("Clear route", "Remove the drawn route", actions::clearRoute)));
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private JPanel card(TaskLocation l, String taskName, boolean favourite, boolean routing)
	{
		JPanel card = Ui.card();
		card.add(Ui.title((favourite ? "★ " : "") + l.label()));

		List<String> badges = new ArrayList<>();
		switch (String.valueOf(l.getMulti()).toLowerCase())
		{
			case "true":
				badges.add("Multi");
				badges.add("#37f046");
				break;
			case "false":
				badges.add("Single");
				badges.add("#a5a5a5");
				break;
			case "partial":
				badges.add("Partly multi");
				badges.add("#e6961e");
				break;
			default:
				badges.add("Multi ?");
				badges.add("#777777");
		}
		switch (String.valueOf(l.getCannon()).toLowerCase())
		{
			case "true":
				badges.add("Cannon");
				badges.add("#37f046");
				break;
			case "false":
				badges.add("No cannon");
				badges.add("#a5a5a5");
				break;
			default:
				badges.add("Cannon ?");
				badges.add("#777777");
		}
		if (l.isWilderness())
		{
			String lvl = l.getWildernessLevelMin() == null ? "Wilderness"
				: "Wilderness " + l.getWildernessLevelMin() + (l.getWildernessLevelMax() == null || l.getWildernessLevelMax().equals(l.getWildernessLevelMin()) ? "" : "-" + l.getWildernessLevelMax());
			badges.add(lvl);
			badges.add("#e6321e");
		}
		if ("true".equalsIgnoreCase(l.getKonarAssignable()))
		{
			badges.add("Konar");
			badges.add("#6ee16e");
		}
		card.add(Ui.badges(badges.toArray(new String[0])));

		if (l.getRequirements() != null && !l.getRequirements().isEmpty())
		{
			card.add(Ui.wrap("Needs: " + String.join("; ", l.getRequirements()), Ui.WARN));
		}
		if (l.getNotes() != null && !l.getNotes().isEmpty())
		{
			card.add(Ui.wrap(l.getNotes()));
		}
		if (!l.hasCoords())
		{
			card.add(Ui.wrap("No map coordinates for this spot yet.", Ui.MUTED));
		}

		JButton fav = Ui.button(favourite ? "Unfavourite" : "Favourite",
			"Favourite locations are listed first and used for auto-routing",
			() -> actions.setFavourite(taskName, favourite ? null : l.getId()));
		JButton route = Ui.button("Route", routing ? "Ask Shortest Path to draw the way there" : "Needs the Shortest Path plugin",
			() -> actions.routeTo(l));
		route.setEnabled(routing && l.hasCoords());
		card.add(Ui.gap(3));
		card.add(Ui.buttonRow(fav, route));
		return card;
	}
}
