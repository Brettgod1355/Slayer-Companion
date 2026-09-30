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
package com.slayercompanion.sweep;

import com.slayercompanion.dps.Spell;
import java.util.Arrays;
import java.util.List;

/** Three made-up accounts for the task sweep: what they own (by wiki item name) and their levels. */
enum SampleBank
{
	LOW("Low (about 70 combat)", 60, 60, 50, 60, 60, 43, false, false, new String[]{"Rune scimitar"},
		"Rune scimitar", "Rune full helm", "Rune platebody", "Rune platelegs", "Rune kiteshield", "Amulet of strength",
		"Amulet of power", "Climbing boots", "Combat bracelet", "Magic shortbow", "Rune arrow", "Adamant arrow",
		"Green d'hide body", "Green d'hide chaps", "Green d'hide vambraces", "Mystic robe top", "Mystic robe bottom",
		"Mystic hat", "Staff of fire", "Anti-dragon shield", "Mirror shield", "Insulated boots", "Witchwood icon",
		"Nose peg", "Earmuffs", "Facemask", "Spiny helmet", "Leaf-bladed sword", "Broad arrows", "Black mask",
		"Boots of stone", "Lit bug lantern", "Obsidian cape", "Rune boots", "Unholy book", "Ring of recoil",
		"Dragon dagger", "Granite shield", "Elemental shield", "Coif", "Snakeskin boots", "Adamant crossbow",
		"Adamant bolts", "Broad bolts"),
	MID("Mid (about 100 combat)", 80, 85, 80, 85, 85, 70, false, false, new String[]{"Abyssal whip", "Dragon defender"},
		"Abyssal whip", "Dragon defender", "Fighter torso", "Dragon platelegs", "Dragon boots", "Barrows gloves",
		"Amulet of fury", "Berserker ring (i)", "Fire cape", "Slayer helmet (i)", "Rune crossbow", "Runite bolts",
		"Broad bolts", "Black d'hide body", "Black d'hide chaps", "Ava's accumulator", "Trident of the seas",
		"Occult necklace", "Mystic robe top", "Mystic robe bottom", "Ahrim's robetop", "Ahrim's robeskirt",
		"Anti-dragon shield", "Dragonfire shield", "Mirror shield", "Insulated boots", "Witchwood icon",
		"Leaf-bladed battleaxe", "Boots of stone", "Lit bug lantern", "Salve amulet(ei)", "Dragon hunter crossbow",
		"Dragon bolts", "Arclight", "Neitiznot faceguard", "Bandos chestplate", "Bandos tassets", "Archers ring (i)",
		"Seers ring (i)", "Imbued saradomin cape", "Book of darkness", "Dragon warhammer", "Abyssal tentacle",
		"Toxic blowpipe", "Slayer's staff", "Iban's staff (u)", "Elemental shield", "Dragon crossbow",
		"Diamond dragon bolts (e)", "Ruby dragon bolts (e)", "Tome of fire", "Void knight top", "Void knight robe",
		"Void knight gloves", "Void melee helm", "Void ranger helm", "Void mage helm", "Crystal bow", "Crystal shield"),
	MAXED("Maxed", 99, 99, 99, 99, 99, 99, true, true, new String[]{"Abyssal tentacle", "Avernic defender"},
		"Scythe of vitur", "Ghrazi rapier", "Osmumten's fang", "Abyssal tentacle", "Twisted bow", "Bow of faerdhinen",
		"Dragon hunter lance", "Dragon hunter crossbow", "Tumeken's shadow", "Sanguinesti staff", "Torva full helm",
		"Torva platebody", "Torva platelegs", "Masori mask (f)", "Masori body (f)", "Masori chaps (f)", "Ancestral hat",
		"Ancestral robe top", "Ancestral robe bottom", "Ferocious gloves", "Zaryte vambraces", "Tormented bracelet",
		"Primordial boots", "Pegasian boots", "Eternal boots", "Amulet of torture", "Necklace of anguish",
		"Occult necklace", "Amulet of rancour", "Infernal cape", "Dizana's quiver", "Imbued guthix cape", "Ultor ring",
		"Venator ring", "Magus ring", "Avernic defender", "Elysian spirit shield", "Dragonfire ward", "Slayer helmet (i)",
		"Salve amulet(ei)", "Elite void top", "Elite void robe", "Void knight gloves", "Void ranger helm",
		"Void melee helm", "Void mage helm", "Crystal helm", "Crystal body", "Crystal legs", "Keris partisan",
		"Emberlight", "Dragon arrow", "Dragon bolts", "Ruby dragon bolts (e)", "Diamond dragon bolts (e)",
		"Amethyst broad bolts", "Leaf-bladed battleaxe", "Anti-dragon shield", "Mirror shield", "V's shield",
		"Insulated boots", "Witchwood icon", "Boots of stone", "Boots of brimstone", "Lit bug lantern",
		"Twisted buckler", "Elidinis' ward (f)", "Voidwaker", "Inquisitor's great helm", "Inquisitor's hauberk",
		"Inquisitor's plateskirt", "Inquisitor's mace", "Toxic blowpipe", "Zaryte crossbow", "Heavy ballista",
		"Webweaver bow", "Accursed sceptre", "Ursine chainmace", "Arkan blade", "Blood moon helm", "Oathplate helm",
		"Oathplate chest", "Oathplate legs", "Confliction gauntlets", "Avernic treads (max)", "Noxious halberd",
		"Soulreaper axe", "Harmonised nightmare staff", "Eldritch nightmare staff", "Volatile nightmare staff",
		"Kodai wand", "Staff of the dead", "Warped sceptre", "Thammaron's sceptre", "Craw's bow", "Tonalztics of ralos",
		"Atlatl dart", "Eclipse atlatl", "Dual macuahuitl", "Blue moon spear", "Burning claws", "Dragon claws",
		"Elder maul", "Zamorakian hasta", "Blade of saeldor", "Bandos godsword", "Dinh's bulwark", "Slayer's staff",
		"Trident of the swamp", "Leaf-bladed sword", "Broad bolts");

	final String label;
	final int attack;
	final int strength;
	final int defence;
	final int ranged;
	final int magic;
	final int prayer;
	final boolean rigour;
	final boolean augury;
	final int spellbook = Spell.STANDARD;
	/** Wiki names of the items the account is wearing. */
	final List<String> worn;
	/** Wiki names of everything else it owns. */
	final List<String> items;

	SampleBank(String label, int attack, int strength, int defence, int ranged, int magic, int prayer,
		boolean rigour, boolean augury, String[] worn, String... items)
	{
		this.label = label;
		this.attack = attack;
		this.strength = strength;
		this.defence = defence;
		this.ranged = ranged;
		this.magic = magic;
		this.prayer = prayer;
		this.rigour = rigour;
		this.augury = augury;
		this.worn = Arrays.asList(worn);
		this.items = Arrays.asList(items);
	}
}
