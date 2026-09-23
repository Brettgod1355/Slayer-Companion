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
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.tracker.TaskSession;
import com.slayercompanion.wilderness.WildernessStatus;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

/** Compact on-screen summary: task, kills left, session profit and (in the Wilderness) own risk. */
public class TaskOverlay extends OverlayPanel
{
	private final SlayerCompanionConfig config;
	private Supplier<CurrentTask> task = () -> null;
	private Supplier<TaskSession> session = () -> null;
	private Supplier<WildernessStatus> wilderness = () -> null;

	@Inject
	TaskOverlay(SlayerCompanionConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void bind(Supplier<CurrentTask> task, Supplier<TaskSession> session, Supplier<WildernessStatus> wilderness)
	{
		this.task = task;
		this.session = session;
		this.wilderness = wilderness;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		CurrentTask t = task.get();
		if (t == null)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text(t.getName()).color(Color.WHITE).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Left").right(String.valueOf(t.getRemaining())).build());
		TaskSession s = session.get();
		if (config.overlayShowProfit() && s != null)
		{
			long profit = s.getProfit();
			panelComponent.getChildren().add(LineComponent.builder().left("Profit")
				.right(QuantityFormatter.quantityToStackSize(profit))
				.rightColor(profit >= 0 ? Color.GREEN : Color.RED).build());
		}
		WildernessStatus w = wilderness.get();
		if (config.overlayShowRisk() && w != null && w.isInWilderness())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Risk")
				.right(QuantityFormatter.quantityToStackSize(w.getRiskValue()))
				.rightColor(Color.ORANGE).build());
		}
		return super.render(graphics);
	}
}
