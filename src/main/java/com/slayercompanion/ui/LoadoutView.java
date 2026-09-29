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

import com.slayercompanion.gear.LoadoutDisplay;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;

/**
 * A saved loadout drawn like the game's Worn Equipment tab above its inventory: the stone side-panel
 * background, the equipment slot tiles with their silhouettes, and the item sprites with the
 * game's quantity text. Items the player does not have on them are tinted: red when none are on
 * them, amber when only some are or they are in the wrong place. Hover a slot for where it is.
 */
class LoadoutView extends JPanel
{
	private static final int SLOT = 36;
	private static final int EQUIP_DX = 41;
	private static final int EQUIP_DY = 39;
	private static final int INV_DX = 42;
	private static final int INV_DY = 36;
	private static final int ITEM_H = 32;
	private static final int PAD = 8;
	private static final int GAP = 14;

	private static final int INV_W = 3 * INV_DX + SLOT;
	private static final int EQUIP_W = 2 * EQUIP_DX + SLOT;
	private static final int EQUIP_H = 4 * EQUIP_DY + SLOT;
	private static final int INV_H = 6 * INV_DY + ITEM_H;
	private static final int WIDTH = INV_W + 2 * PAD;
	private static final int HEIGHT = PAD + EQUIP_H + GAP + INV_H + PAD;
	private static final int EQUIP_ONLY_HEIGHT = PAD + EQUIP_H + PAD;
	private static final int EQUIP_X = (WIDTH - EQUIP_W) / 2;
	private static final int INV_X = PAD;
	private static final int INV_Y = PAD + EQUIP_H + GAP;

	/** Fallbacks until the game sprites have loaded. */
	private static final Color STONE = new Color(62, 53, 41);
	private static final Color TILE = new Color(47, 40, 31);
	private static final Color TILE_EDGE = new Color(28, 24, 18);
	private static final Color LINE = new Color(34, 29, 22);
	private static final Color MISSING = new Color(255, 0, 0, 90);
	private static final Color PARTLY = new Color(255, 176, 0, 90);

	/** Equipment slots in the Worn Equipment tab's layout: column, row and the empty-slot silhouette. */
	private static final Map<Integer, int[]> EQUIP_LAYOUT = new LinkedHashMap<>();

	static
	{
		EQUIP_LAYOUT.put(EquipmentInventorySlot.HEAD.getSlotIdx(), new int[]{1, 0, SpriteID.Wornicons.HEAD});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.CAPE.getSlotIdx(), new int[]{0, 1, SpriteID.Wornicons.CAPE});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.AMULET.getSlotIdx(), new int[]{1, 1, SpriteID.Wornicons.NECK});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.AMMO.getSlotIdx(), new int[]{2, 1, SpriteID.Wornicons.AMMUNITION});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.WEAPON.getSlotIdx(), new int[]{0, 2, SpriteID.Wornicons.WEAPON});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.BODY.getSlotIdx(), new int[]{1, 2, SpriteID.Wornicons.TORSO});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.SHIELD.getSlotIdx(), new int[]{2, 2, SpriteID.Wornicons.SHIELD});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.LEGS.getSlotIdx(), new int[]{1, 3, SpriteID.Wornicons.LEGS});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.GLOVES.getSlotIdx(), new int[]{0, 4, SpriteID.Wornicons.HANDS});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.BOOTS.getSlotIdx(), new int[]{1, 4, SpriteID.Wornicons.FEET});
		EQUIP_LAYOUT.put(EquipmentInventorySlot.RING.getSlotIdx(), new int[]{2, 4, SpriteID.Wornicons.RING});
	}

	private final LoadoutDisplay loadout;
	private final RsSprites sprites;
	private final boolean showInventory;
	private final Map<Integer, BufferedImage> equipImages = new LinkedHashMap<>();
	private final List<BufferedImage> invImages = new ArrayList<>();

	LoadoutView(LoadoutDisplay loadout, ItemManager itemManager, RsSprites sprites)
	{
		this(loadout, itemManager, sprites, true);
	}

	/** {@code showInventory} false draws the Worn Equipment part only (for recommended gear). */
	LoadoutView(LoadoutDisplay loadout, ItemManager itemManager, RsSprites sprites, boolean showInventory)
	{
		this.loadout = loadout;
		this.sprites = sprites;
		this.showInventory = showInventory;
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setToolTipText("");
		for (Map.Entry<Integer, LoadoutDisplay.Slot> e : loadout.getEquipment().entrySet())
		{
			equipImages.put(e.getKey(), image(itemManager, e.getValue()));
		}
		for (LoadoutDisplay.Slot s : loadout.getInventory())
		{
			invImages.add(s == null ? null : image(itemManager, s));
		}
	}

	private BufferedImage image(ItemManager itemManager, LoadoutDisplay.Slot s)
	{
		net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(s.getItemId(), s.getQuantity(), s.isStackable() || s.getQuantity() > 1);
		img.onLoaded(this::repaint);
		return img;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(WIDTH, showInventory ? HEIGHT : EQUIP_ONLY_HEIGHT);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			paintBackground(g);
			paintEquipment(g);
			if (showInventory)
			{
				paintInventory(g);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	private void paintBackground(Graphics2D g)
	{
		int height = showInventory ? HEIGHT : EQUIP_ONLY_HEIGHT;
		BufferedImage stone = sprites.get(SpriteID.SIDE_BACKGROUND);
		if (stone == null)
		{
			g.setColor(STONE);
			g.fillRect(0, 0, WIDTH, height);
		}
		else
		{
			for (int y = 0; y < height; y += stone.getHeight())
			{
				for (int x = 0; x < WIDTH; x += stone.getWidth())
				{
					g.drawImage(stone, x, y, null);
				}
			}
		}
		if (showInventory)
		{
			g.setColor(LINE);
			g.fillRect(PAD, PAD + EQUIP_H + GAP / 2 - 1, WIDTH - 2 * PAD, 2);
		}
	}

	private void paintEquipment(Graphics2D g)
	{
		// The lines that join the slots in the game's Worn Equipment tab.
		g.setColor(LINE);
		g.setStroke(new BasicStroke(3));
		int mid = EQUIP_X + EQUIP_DX + SLOT / 2;
		g.drawLine(mid, PAD + SLOT / 2, mid, PAD + 4 * EQUIP_DY + SLOT / 2);
		for (int row : new int[]{1, 2, 4})
		{
			int y = PAD + row * EQUIP_DY + SLOT / 2;
			g.drawLine(EQUIP_X + SLOT / 2, y, EQUIP_X + 2 * EQUIP_DX + SLOT / 2, y);
		}

		BufferedImage tile = sprites.get(SpriteID.Miscgraphics._0);
		for (Map.Entry<Integer, int[]> e : EQUIP_LAYOUT.entrySet())
		{
			Rectangle r = equipRect(e.getValue());
			if (tile == null)
			{
				g.setColor(TILE);
				g.fillRect(r.x, r.y, SLOT, SLOT);
				g.setColor(TILE_EDGE);
				g.drawRect(r.x, r.y, SLOT - 1, SLOT - 1);
			}
			else
			{
				g.drawImage(tile, r.x, r.y, null);
			}
			LoadoutDisplay.Slot item = loadout.getEquipment().get(e.getKey());
			if (item == null)
			{
				BufferedImage silhouette = sprites.get(e.getValue()[2]);
				if (silhouette != null)
				{
					g.drawImage(silhouette, r.x + (SLOT - silhouette.getWidth()) / 2, r.y + (SLOT - silhouette.getHeight()) / 2, null);
				}
				continue;
			}
			tint(g, r, item);
			g.drawImage(equipImages.get(e.getKey()), r.x, r.y + (SLOT - ITEM_H) / 2, null);
		}
	}

	private void paintInventory(Graphics2D g)
	{
		for (int i = 0; i < loadout.getInventory().size(); i++)
		{
			LoadoutDisplay.Slot item = loadout.getInventory().get(i);
			if (item == null)
			{
				continue;
			}
			Rectangle r = invRect(i);
			tint(g, r, item);
			g.drawImage(invImages.get(i), r.x, r.y, null);
		}
	}

	private static void tint(Graphics2D g, Rectangle r, LoadoutDisplay.Slot item)
	{
		switch (item.getStatus())
		{
			case ON_YOU:
				return;
			case PARTLY:
				g.setColor(PARTLY);
				break;
			default:
				g.setColor(MISSING);
				break;
		}
		g.fillRect(r.x, r.y, r.width, r.height);
	}

	private static Rectangle equipRect(int[] layout)
	{
		return new Rectangle(EQUIP_X + layout[0] * EQUIP_DX, PAD + layout[1] * EQUIP_DY, SLOT, SLOT);
	}

	private static Rectangle invRect(int index)
	{
		return new Rectangle(INV_X + (index % 4) * INV_DX, INV_Y + (index / 4) * INV_DY, SLOT, ITEM_H);
	}

	@Override
	@Nullable
	public String getToolTipText(MouseEvent event)
	{
		LoadoutDisplay.Slot hit = null;
		for (Map.Entry<Integer, int[]> e : EQUIP_LAYOUT.entrySet())
		{
			if (equipRect(e.getValue()).contains(event.getPoint()))
			{
				hit = loadout.getEquipment().get(e.getKey());
			}
		}
		for (int i = 0; showInventory && i < loadout.getInventory().size(); i++)
		{
			if (invRect(i).contains(event.getPoint()))
			{
				hit = loadout.getInventory().get(i);
			}
		}
		if (hit == null)
		{
			return null;
		}
		String qty = hit.getQuantity() > 1 ? " × " + Ui.num(hit.getQuantity()) : "";
		return "<html>" + Ui.escape(hit.getName()) + qty + "<br>" + Ui.escape(hit.getWhere()) + "</html>";
	}
}
