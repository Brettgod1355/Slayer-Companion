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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * One entry in a wiki gear tier. {@code name} is the linked page, {@code pic} the pictured item
 * (often the concrete variant, e.g. "Imbued Saradomin cape" for "God capes") and {@code txt} the
 * displayed label.
 */
@Data
public class GearItem
{
	private String name;
	@Nullable
	private String pic;
	@Nullable
	private String txt;

	/** What to show the player. */
	public String label()
	{
		return txt != null && !txt.isEmpty() ? txt : name;
	}

	/** Item names to try when resolving to an item id, most specific first. */
	public List<String> candidates()
	{
		List<String> out = new ArrayList<>(3);
		if (pic != null && !pic.isEmpty())
		{
			out.add(pic);
		}
		if (name != null && !name.isEmpty() && !out.contains(name))
		{
			out.add(name);
		}
		if (txt != null && !txt.isEmpty() && !out.contains(txt))
		{
			out.add(txt);
		}
		return out;
	}
}
