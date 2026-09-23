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
import com.slayercompanion.data.WildernessInfo;
import com.slayercompanion.wilderness.WildernessStatus;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Informational view of the player's own death risk and the Wilderness rules. It never warns,
 * flashes or mentions other players.
 */
class WildernessTab extends JPanel
{
	private final WildernessInfo info;

	WildernessTab(WildernessInfo info)
	{
		this.info = info;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		WildernessStatus w = m.getWilderness();
		if (w == null)
		{
			col.add(Ui.wrap("Log in to see what you are risking."));
		}
		else
		{
			JPanel risk = Ui.card();
			risk.add(Ui.title(w.isInWilderness() ? "In the Wilderness, level " + w.getWildernessLevel() : "Not in the Wilderness"));
			risk.add(Ui.keyValue("Protect Item", w.isProtectItemActive() ? "on" : "off"));
			risk.add(Ui.keyValue("Items kept", String.valueOf(w.getItemsKept())));
			risk.add(Ui.keyValue("Carried value", Ui.gp(w.getCarriedValue())));
			risk.add(Ui.keyValue("Lost on death", Ui.gp(w.getRiskValue()), w.getRiskValue() > 0 ? Ui.WARN : Ui.GOOD));
			if (w.isInWilderness() && w.getWildernessLevel() > 20)
			{
				risk.add(Ui.wrap("Above level 20 most teleports do not work; above 30 none do.", Ui.MUTED));
			}
			col.add(risk);
			col.add(Ui.gap(4));

			if (!w.getKept().isEmpty())
			{
				JPanel kept = Ui.card();
				kept.add(Ui.title("Kept"));
				for (WildernessStatus.CarriedItem i : w.getKept())
				{
					kept.add(Ui.wrap(i.getName() + (i.getQuantity() > 1 ? " x " + i.getQuantity() : "") + " — " + Ui.gp(i.getValue()), Ui.GOOD));
				}
				col.add(kept);
				col.add(Ui.gap(4));
			}
			if (!w.getLost().isEmpty())
			{
				JPanel lost = Ui.card();
				lost.add(Ui.title("Lost (most valuable first)"));
				int shown = 0;
				for (WildernessStatus.CarriedItem i : w.getLost())
				{
					lost.add(Ui.wrap(i.getName() + (i.getQuantity() > 1 ? " x " + i.getQuantity() : "") + " — " + Ui.gp(i.getValue()), Color.WHITE));
					if (++shown >= 15)
					{
						lost.add(Ui.wrap("… and " + (w.getLost().size() - shown) + " more", Ui.MUTED));
						break;
					}
				}
				col.add(lost);
				col.add(Ui.gap(4));
			}
			if (!w.getUntradeables().isEmpty())
			{
				JPanel un = Ui.card();
				un.add(Ui.title("Untradeables carried"));
				un.add(Ui.wrap("These follow the game's own rules (some are kept, some become coins for the killer, some are lost). Check Items Kept on Death in game.", Ui.MUTED));
				for (WildernessStatus.CarriedItem i : w.getUntradeables())
				{
					un.add(Ui.wrap(i.getName() + (i.getQuantity() > 1 ? " x " + i.getQuantity() : ""), Color.WHITE));
				}
				col.add(un);
				col.add(Ui.gap(4));
			}
		}

		if (m.getTask() != null)
		{
			boolean any = false;
			JPanel locs = Ui.card();
			locs.add(Ui.title("Wilderness spots for this task"));
			for (TaskLocation l : m.getLocations())
			{
				if (!l.isWilderness())
				{
					continue;
				}
				any = true;
				String lvl = l.getWildernessLevelMin() == null ? "level unknown"
					: "level " + l.getWildernessLevelMin() + (l.getWildernessLevelMax() == null ? "" : "-" + l.getWildernessLevelMax());
				locs.add(Ui.wrap(l.label() + " — " + lvl + (l.isMulti() ? ", multi" : "false".equalsIgnoreCase(l.getMulti()) ? ", single" : ""), Color.WHITE));
			}
			if (any)
			{
				col.add(locs);
				col.add(Ui.gap(4));
			}
		}

		Map<String, String> ex = info.getExplainers();
		if (ex != null && !ex.isEmpty())
		{
			JPanel rules = Ui.card();
			rules.add(Ui.title("The rules, briefly"));
			for (Map.Entry<String, String> e : ex.entrySet())
			{
				rules.add(Ui.wrap(pretty(e.getKey()), Color.WHITE));
				rules.add(Ui.wrap(e.getValue(), Ui.MUTED));
				rules.add(Ui.gap(3));
			}
			col.add(rules);
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private static String pretty(String key)
	{
		String s = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
