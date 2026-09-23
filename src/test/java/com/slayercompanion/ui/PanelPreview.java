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

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerData;
import com.slayercompanion.data.UnlockInfo;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.SlayerMaster;
import com.slayercompanion.unlocks.UnlockAdvice;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

/**
 * Renders a panel tab headlessly to a PNG so UI changes can be reviewed without a game client.
 * Test code only; not part of the plugin jar. Usage: PanelPreview out.png
 */
public class PanelPreview
{
	public static void main(String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		SlayerData data = new SlayerData(new Gson());
		Set<String> owned = new HashSet<>(Arrays.asList("Bigger and Badder", "Gargoyle Smasher", "Malevolent Masquerade",
			"Ring Bling", "Broader Fletching", "Like a Boss", "Seeing Red", "Hot Stuff"));
		int points = 480;
		List<UnlockAdvice> advice = new ArrayList<>();
		for (UnlockInfo b : data.unlocks())
		{
			List<String> tasks = new ArrayList<>();
			if (b.getAffectsTasks() != null)
			{
				for (String t : b.getAffectsTasks())
				{
					if (!t.startsWith("("))
					{
						tasks.add(t);
					}
				}
			}
			String effect = b.getEffect() == null ? "" : b.getEffect();
			advice.add(new UnlockAdvice(b.getName(), effect, b.getCost(), owned.contains(b.getName()), points >= b.getCost(),
				b.getPriority(), b.getCategory(), b.getRationale(), effect, tasks));
		}
		CurrentTask task = new CurrentTask("Abyssal demons", 150, 180, null, SlayerMaster.DURADEL, false, points, 57);
		PanelModel model = PanelModel.builder()
			.loggedIn(true).task(task).points(points).unlocks(advice)
			.locations(Collections.emptyList()).variants(Collections.emptyList())
			.build();

		UnlocksTab tab = new UnlocksTab();
		tab.update(model);
		JPanel frame = new JPanel(new BorderLayout());
		frame.add(tab, BorderLayout.NORTH);
		int width = PluginPanel.PANEL_WIDTH;
		frame.setSize(width, 10);
		layout(frame);
		int height = frame.getPreferredSize().height;
		frame.setSize(width, height);
		layout(frame);

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		frame.paint(g);
		g.dispose();
		ImageIO.write(img, "png", new File(args[0]));
		System.out.println("wrote " + args[0] + " " + width + "x" + height);
	}

	private static void layout(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			k.doLayout();
			for (Component child : k.getComponents())
			{
				layout(child);
			}
		}
	}
}
