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
package com.slayercompanion.data;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** A monster's defensive stats and attributes (wiki infobox, default version), for DPS estimates. */
@Data
public class MonsterCombatStats
{
	@Nullable
	private Integer defenceLevel;
	@Nullable
	private Integer magicLevel;
	@Nullable
	private Integer hitpoints;
	@Nullable
	private Integer size;
	@Nullable
	private Integer stab;
	@Nullable
	private Integer slash;
	@Nullable
	private Integer crush;
	@Nullable
	private Integer magic;
	/** Older single ranged defence; newer infoboxes split it into light/standard/heavy. */
	@Nullable
	private Integer ranged;
	@Nullable
	private Integer rangedLight;
	@Nullable
	private Integer rangedStandard;
	@Nullable
	private Integer rangedHeavy;
	@Nullable
	private Integer flatArmour;
	/** Elemental weakness ("fire", "water", ...) and its percentage. */
	@Nullable
	private String weakness;
	@Nullable
	private Integer weaknessPercent;
	/** "demon", "dragon", "undead", "kalphite", "leafy", ... */
	@Nullable
	private List<String> attributes;
	@Nullable
	private String version;

	public List<String> attributesOrEmpty()
	{
		return attributes == null ? Collections.emptyList() : attributes;
	}

	public boolean is(String attribute)
	{
		return attributesOrEmpty().contains(attribute);
	}
}
