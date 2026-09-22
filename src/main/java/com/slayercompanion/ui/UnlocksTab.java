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

import com.slayercompanion.unlocks.UnlockAdvice;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** Slayer reward shop, ordered by the plugin's recommendation, with live costs and owned state. */
class UnlocksTab extends JPanel
{
	UnlocksTab()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void update(PanelModel m)
	{
		removeAll();
		JPanel col = Ui.column();
		if (m.getUnlocks().isEmpty())
		{
			col.add(Ui.wrap("Log in to see the reward shop."));
		}
		else
		{
			col.add(Ui.wrap("You have " + Ui.num(m.getPoints()) + " points. Ordered by suggested priority; owned unlocks are at the bottom.", Ui.MUTED));
			col.add(Ui.gap(4));
			JPanel card = Ui.card();
			for (UnlockAdvice u : m.getUnlocks())
			{
				boolean owned = Boolean.TRUE.equals(u.getUnlocked());
				String prefix = owned ? "✓ " : (u.isAffordable() ? "● " : "○ ");
				String pri = u.getPriority() >= 9 ? "" : "  [P" + u.getPriority() + "]";
				Color c = owned ? Ui.MUTED : (u.isAffordable() ? Ui.GOOD : Color.WHITE);
				JLabel l = Ui.wrap(prefix + u.getName() + " — " + Ui.num(u.getCost()) + " pts" + pri, c);
				String tip = Ui.escape(u.getDescription());
				if (u.getRationale() != null)
				{
					tip += "<br><br>" + Ui.escape(u.getRationale());
				}
				l.setToolTipText("<html><body style='width:220px'>" + tip + "</body></html>");
				card.add(l);
			}
			card.add(Ui.gap(3));
			card.add(Ui.wrap("P1 = get first … P5 = situational. Hover an entry for what it does and why.", Ui.MUTED));
			col.add(card);
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}
}
