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

import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

/** A place where a task's monsters can be fought. Part of the bundled tasks.json. */
@Data
public class TaskLocation
{
	private String id;
	private String name;
	@Nullable
	private String displayName;
	private int rank;
	/** "true", "false", "partial" or "unknown". */
	private String multi;
	/** "true", "false" or "unknown". */
	private String cannon;
	private boolean wilderness;
	@Nullable
	private Integer wildernessLevelMin;
	@Nullable
	private Integer wildernessLevelMax;
	/** "true", "false" or "unknown". */
	@Nullable
	private String konarAssignable;
	private List<String> requirements;
	@Nullable
	private String notes;
	@Nullable
	private Integer x;
	@Nullable
	private Integer y;
	@Nullable
	private Integer plane;
	/** Individual spawn tiles as [x, y] pairs. */
	@Nullable
	private List<List<Integer>> spawns;
	private boolean coordsMissing;

	public String label()
	{
		return displayName == null || displayName.isEmpty() ? name : displayName;
	}

	public boolean hasCoords()
	{
		return x != null && y != null && !coordsMissing;
	}

	public boolean isMulti()
	{
		return "true".equalsIgnoreCase(multi);
	}

	public boolean isCannon()
	{
		return "true".equalsIgnoreCase(cannon);
	}
}
