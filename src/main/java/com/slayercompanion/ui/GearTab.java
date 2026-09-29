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
import java.awt.BorderLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** A link to the wiki page with the recommended gear and strategy for the task or chosen variant. */
class GearTab extends JPanel
{
	private final PanelActions actions;

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
		else if (info == null)
		{
			col.add(Ui.wrap("No bundled wiki page for \"" + m.getTask().getName() + "\" yet.", Ui.WARN));
		}
		else
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
			col.add(card);
		}
		add(col, BorderLayout.NORTH);
		revalidate();
		repaint();
	}
}
