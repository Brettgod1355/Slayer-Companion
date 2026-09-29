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
package com.slayercompanion.gear;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

/**
 * Shows a loadout in the open bank through RuneLite's Bank Tags plugin: a hidden tag that holds just
 * the loadout's items, laid out like the side panel's view — the equipment in the Worn Equipment
 * shape on the left, the 28 inventory slots on the right. Items not in the bank show as Bank Tags'
 * usual placeholders. Only the bank's display changes; nothing is withdrawn or clicked.
 */
@Singleton
public class BankLayout
{
	static final String TAG = "slayer companion loadout";
	static final int ROW = 8;
	static final int INVENTORY_COLUMN = 4;

	/** Bank position of each equipment slot: column + row * 8, same shape as the Worn Equipment tab. */
	private static final Map<Integer, Integer> EQUIPMENT_POSITIONS = new java.util.HashMap<>();

	static
	{
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.HEAD.getSlotIdx(), 1);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.CAPE.getSlotIdx(), ROW);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.AMULET.getSlotIdx(), ROW + 1);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.AMMO.getSlotIdx(), ROW + 2);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.WEAPON.getSlotIdx(), 2 * ROW);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.BODY.getSlotIdx(), 2 * ROW + 1);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.SHIELD.getSlotIdx(), 2 * ROW + 2);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.LEGS.getSlotIdx(), 3 * ROW + 1);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.GLOVES.getSlotIdx(), 4 * ROW);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.BOOTS.getSlotIdx(), 4 * ROW + 1);
		EQUIPMENT_POSITIONS.put(EquipmentInventorySlot.RING.getSlotIdx(), 4 * ROW + 2);
	}

	private final BankTagsService bankTags;
	private final TagManager tagManager;
	private final LayoutManager layoutManager;
	private final ItemManager itemManager;
	private volatile Set<Integer> items = Collections.emptySet();

	@Inject
	BankLayout(BankTagsService bankTags, TagManager tagManager, LayoutManager layoutManager, ItemManager itemManager)
	{
		this.bankTags = bankTags;
		this.tagManager = tagManager;
		this.layoutManager = layoutManager;
		this.itemManager = itemManager;
	}

	public void startUp()
	{
		tagManager.registerTag(TAG, id -> items.contains(id));
	}

	public void shutDown()
	{
		close();
		tagManager.unregisterTag(TAG);
		layoutManager.removeLayout(TAG);
		items = Collections.emptySet();
	}

	/** Filter the open bank to the loadout, laid out like the panel. Client thread only. */
	public void show(Loadout loadout)
	{
		int[] layout = positions(loadout, itemManager::canonicalize);
		Set<Integer> ids = new HashSet<>();
		for (int id : layout)
		{
			if (id > 0)
			{
				ids.add(id);
			}
		}
		items = ids;
		layoutManager.saveLayout(new Layout(TAG, layout));
		bankTags.openBankTag(TAG, BankTagsService.OPTION_HIDE_TAG_NAME);
	}

	public boolean isShowing()
	{
		return TAG.equals(bankTags.getActiveTag());
	}

	/** Back to the normal bank. Client thread only. */
	public void close()
	{
		if (isShowing())
		{
			bankTags.closeBankTag();
		}
	}

	/**
	 * Item id per bank position (8 per row; -1 = empty): equipment in columns 0-2 as on the Worn
	 * Equipment tab, the inventory in columns 4-7, one row per four slots. Ids are canonicalised
	 * (noted to unnoted) because the bank holds items unnoted.
	 */
	static int[] positions(Loadout loadout, IntUnaryOperator canonical)
	{
		int[] out = new int[ROW * (Loadout.INVENTORY_SIZE / 4)];
		java.util.Arrays.fill(out, -1);
		for (Map.Entry<Integer, Loadout.Slot> e : loadout.getEquipment().entrySet())
		{
			Integer pos = EQUIPMENT_POSITIONS.get(e.getKey());
			if (pos != null && e.getValue() != null && e.getValue().getId() > 0)
			{
				out[pos] = canonical.applyAsInt(e.getValue().getId());
			}
		}
		for (int i = 0; i < Math.min(Loadout.INVENTORY_SIZE, loadout.getInventory().size()); i++)
		{
			Loadout.Slot s = loadout.getInventory().get(i);
			if (s != null && s.getId() > 0)
			{
				out[(i / 4) * ROW + INVENTORY_COLUMN + i % 4] = canonical.applyAsInt(s.getId());
			}
		}
		return out;
	}
}
