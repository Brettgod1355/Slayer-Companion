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

import static org.junit.Assert.assertEquals;

import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

public class BankLayoutTest
{
	@Test
	public void equipmentInTheWornShapeAndInventoryOnTheRight()
	{
		Loadout loadout = new Loadout();
		loadout.getEquipment().put(EquipmentInventorySlot.HEAD.getSlotIdx(), new Loadout.Slot(11865, 1));
		loadout.getEquipment().put(EquipmentInventorySlot.CAPE.getSlotIdx(), new Loadout.Slot(6570, 1));
		loadout.getEquipment().put(EquipmentInventorySlot.WEAPON.getSlotIdx(), new Loadout.Slot(12006, 1));
		loadout.getEquipment().put(EquipmentInventorySlot.LEGS.getSlotIdx(), new Loadout.Slot(11834, 1));
		loadout.getEquipment().put(EquipmentInventorySlot.RING.getSlotIdx(), new Loadout.Slot(11773, 1));
		for (int i = 0; i < Loadout.INVENTORY_SIZE; i++)
		{
			loadout.getInventory().add(new Loadout.Slot(-1, 0));
		}
		loadout.getInventory().set(0, new Loadout.Slot(2434, 1));
		loadout.getInventory().set(5, new Loadout.Slot(386, 1));   // noted shark
		loadout.getInventory().set(27, new Loadout.Slot(385, 1));

		int[] bank = BankLayout.positions(loadout, id -> id == 386 ? 385 : id);
		assertEquals(56, bank.length);
		// Row 0: head in the middle column; row 1 starts with the cape; row 2 the weapon; row 3 the legs; row 4 ends with the ring.
		assertEquals(11865, bank[1]);
		assertEquals(6570, bank[8]);
		assertEquals(12006, bank[16]);
		assertEquals(11834, bank[25]);
		assertEquals(11773, bank[34]);
		// Inventory slot n goes to column 4 + n % 4 of row n / 4; noted items show as the item.
		assertEquals(2434, bank[4]);
		assertEquals(385, bank[13]);
		assertEquals(385, bank[6 * 8 + 7]);
		assertEquals(-1, bank[0]);
		assertEquals(-1, bank[3]);
	}
}
