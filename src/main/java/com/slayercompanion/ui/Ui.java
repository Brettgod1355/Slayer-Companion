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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/** Small Swing helpers shared by the tabs. Text always wraps; names are never truncated. */
final class Ui
{
	static final int CONTENT_WIDTH = 196;
	static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color WARN = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;

	private Ui()
	{
	}

	static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setBorder(new EmptyBorder(4, 4, 4, 4));
		return p;
	}

	static JPanel card()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(6, 6, 6, 6));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	static JLabel title(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(4, 0, 2, 0));
		return l;
	}

	/** A label that wraps long text across lines without splitting words. */
	static JLabel wrap(String text)
	{
		return wrap(text, ColorScheme.TEXT_COLOR);
	}

	static JLabel wrap(String text, Color color)
	{
		JLabel l = new JLabel(html(text));
		l.setForeground(color);
		l.setFont(FontManager.getRunescapeFont());
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setVerticalAlignment(SwingConstants.TOP);
		return l;
	}

	/** Swing's HTML engine scales 'px' by 1.3; 'pt' maps 1:1 to pixels, so widths are given in pt. */
	static String html(String text)
	{
		return html(text, CONTENT_WIDTH - 24);
	}

	static String html(String text, int widthPx)
	{
		return "<html><body style='width:" + Math.max(40, widthPx) + "pt'>" + escape(text) + "</body></html>";
	}

	static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static JPanel keyValue(String key, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel k = new JLabel(key);
		k.setForeground(MUTED);
		k.setFont(FontManager.getRunescapeFont());
		int valueWidth = (CONTENT_WIDTH - 24) - k.getPreferredSize().width - 6;
		JLabel v = new JLabel(html(value, valueWidth));
		v.setForeground(valueColor);
		v.setFont(FontManager.getRunescapeFont());
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(k, BorderLayout.WEST);
		row.add(v, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	static JPanel keyValue(String key, String value)
	{
		return keyValue(key, value, Color.WHITE);
	}

	static JButton button(String text, String tooltip, Runnable onClick)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeFont());
		b.setToolTipText(tooltip);
		b.setFocusPainted(false);
		b.addActionListener(e -> onClick.run());
		return b;
	}

	static JPanel buttonRow(JButton... buttons)
	{
		JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JButton b : buttons)
		{
			row.add(b);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	static JPanel badges(String... badges)
	{
		// Two badges per row so the row never grows wider than the panel.
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel row = null;
		int inRow = 0;
		for (int i = 0; i + 1 < badges.length; i += 2)
		{
			if (row == null || inRow == 2)
			{
				row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				rows.add(row);
				inRow = 0;
			}
			JLabel b = new JLabel(badges[i]);
			b.setOpaque(true);
			b.setBackground(ColorScheme.DARK_GRAY_COLOR);
			b.setForeground(Color.decode(badges[i + 1]));
			b.setFont(FontManager.getRunescapeFont());
			b.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
			row.add(b);
			inRow++;
		}
		rows.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows.getPreferredSize().height + 2));
		return rows;
	}

	static Component gap(int px)
	{
		return Box.createVerticalStrut(px);
	}

	static String gp(long value)
	{
		return QuantityFormatter.quantityToStackSize(value) + " gp";
	}

	static String num(long value)
	{
		return QuantityFormatter.formatNumber(value);
	}

	static Font small()
	{
		return FontManager.getRunescapeFont();
	}
}
