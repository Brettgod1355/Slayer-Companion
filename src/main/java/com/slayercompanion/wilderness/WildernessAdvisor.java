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
package com.slayercompanion.wilderness;

import com.slayercompanion.gear.OwnedItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SkullIcon;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Computes the player's own death risk from what they carry, using the Items Kept on Death rules:
 * three items kept, one more with Protect Item, none when skulled (one with Protect Item). Items
 * are ranked by Grand Exchange value, which is how the game orders kept items for tradeables.
 * <p>
 * This is purely informational. It does not notify, flash, or highlight anything, and it looks
 * only at the local player. Client thread only.
 */
@Singleton
public class WildernessAdvisor
{
	private static final Pattern WILDERNESS_LEVEL = Pattern.compile("^Level: (\\d+)$");
	private static final int BASE_KEPT = 3;

	private final Client client;
	private final ItemManager itemManager;
	private final OwnedItems ownedItems;

	@Inject
	WildernessAdvisor(Client client, ItemManager itemManager, OwnedItems ownedItems)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.ownedItems = ownedItems;
	}

	public WildernessStatus status()
	{
		Player local = client.getLocalPlayer();
		boolean skulled = local != null && local.getSkullIcon() != SkullIcon.NONE;
		boolean protect = client.isPrayerActive(Prayer.PROTECT_ITEM);
		int level = wildernessLevel();

		int kept = (skulled ? 0 : BASE_KEPT) + (protect ? 1 : 0);

		List<WildernessStatus.CarriedItem> carried = new ArrayList<>();
		addAll(carried, ownedItems.inventory());
		addAll(carried, ownedItems.equipment());

		List<WildernessStatus.CarriedItem> untradeables = new ArrayList<>();
		long carriedValue = 0;
		for (WildernessStatus.CarriedItem item : carried)
		{
			if (item.isTradeable())
			{
				carriedValue += item.getValue();
			}
			else
			{
				untradeables.add(item);
			}
		}
		// Highest unit value first, the order the game keeps items in. Untradeables rank by their
		// alchemy value and take up kept slots too, but are not counted as gold at risk.
		carried.sort((a, b) -> Long.compare(unitValue(b), unitValue(a)));

		List<WildernessStatus.CarriedItem> keptItems = new ArrayList<>();
		List<WildernessStatus.CarriedItem> lostItems = new ArrayList<>();
		int slotsLeft = kept;
		long risk = 0;
		for (WildernessStatus.CarriedItem item : carried)
		{
			// Each kept slot protects one unit of a stack.
			int keepQty = Math.min(slotsLeft, item.getQuantity());
			slotsLeft -= keepQty;
			int loseQty = item.getQuantity() - keepQty;
			long unit = unitValue(item);
			if (keepQty > 0)
			{
				keptItems.add(new WildernessStatus.CarriedItem(item.getItemId(), item.getName(), keepQty, unit * keepQty, item.isTradeable()));
			}
			if (loseQty > 0 && item.isTradeable())
			{
				lostItems.add(new WildernessStatus.CarriedItem(item.getItemId(), item.getName(), loseQty, unit * loseQty, true));
				risk += unit * loseQty;
			}
		}

		return new WildernessStatus(level > 0, level, skulled, protect, kept,
			Collections.unmodifiableList(keptItems),
			Collections.unmodifiableList(lostItems),
			Collections.unmodifiableList(untradeables),
			risk, carriedValue);
	}

	/** Wilderness level from the game's own indicator widget, 0 outside the Wilderness. */
	public int wildernessLevel()
	{
		Widget w = client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL);
		if (w == null || w.isHidden() || w.getText() == null)
		{
			return 0;
		}
		Matcher m = WILDERNESS_LEVEL.matcher(w.getText());
		return m.matches() ? Integer.parseInt(m.group(1)) : 0;
	}

	private void addAll(List<WildernessStatus.CarriedItem> into, Map<Integer, Integer> items)
	{
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			int id = e.getKey();
			int qty = e.getValue();
			ItemComposition comp = itemManager.getItemComposition(id);
			boolean tradeable = comp.isTradeable();
			int unit = tradeable ? itemManager.getItemPrice(id) : 0;
			if (unit <= 0)
			{
				unit = comp.getHaPrice();
			}
			long value = (long) Math.max(0, unit) * qty;
			into.add(new WildernessStatus.CarriedItem(id, comp.getName(), qty, value, tradeable));
		}
	}

	private static long unitValue(WildernessStatus.CarriedItem item)
	{
		return item.getQuantity() == 0 ? 0 : item.getValue() / item.getQuantity();
	}
}
