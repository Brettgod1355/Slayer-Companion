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
package com.slayercompanion.location;

import com.slayercompanion.data.TaskLocation;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

/** Puts one marker per task location on the world map. */
@Singleton
public class MapMarkerService
{
	private final WorldMapPointManager worldMapPointManager;
	private final List<WorldMapPoint> points = new ArrayList<>();
	private final BufferedImage icon;
	private final BufferedImage favouriteIcon;

	@Inject
	MapMarkerService(WorldMapPointManager worldMapPointManager)
	{
		this.worldMapPointManager = worldMapPointManager;
		this.icon = ImageUtil.loadImageResource(MapMarkerService.class, "/com/slayercompanion/map_marker.png");
		this.favouriteIcon = ImageUtil.loadImageResource(MapMarkerService.class, "/com/slayercompanion/map_marker_fav.png");
	}

	public void show(List<TaskLocation> locations, String favouriteId, java.util.Map<String, com.slayercompanion.game.LockState> locks)
	{
		clear();
		for (TaskLocation l : locations)
		{
			LocationService.point(l).ifPresent(wp ->
			{
				boolean fav = favouriteId != null && favouriteId.equals(l.getId());
				WorldMapPoint p = WorldMapPoint.builder()
					.worldPoint(wp)
					.image(fav ? favouriteIcon : icon)
					.name(l.label())
					.tooltip(tooltip(l, locks.get(l.getId())))
					.jumpOnClick(true)
					.snapToEdge(true)
					.build();
				points.add(p);
				worldMapPointManager.add(p);
			});
		}
	}

	public void clear()
	{
		for (WorldMapPoint p : points)
		{
			worldMapPointManager.remove(p);
		}
		points.clear();
	}

	private static String tooltip(TaskLocation l, @javax.annotation.Nullable com.slayercompanion.game.LockState lock)
	{
		StringBuilder sb = new StringBuilder(l.label());
		if (lock != null && lock.getKind() == com.slayercompanion.game.LockState.Kind.LOCKED)
		{
			sb.append("</br>Locked: ").append(String.join("; ", lock.getReasons()));
		}
		if (l.isMulti())
		{
			sb.append("</br>Multi-combat");
		}
		if (l.isCannon())
		{
			sb.append("</br>Cannon allowed");
		}
		if (l.isWilderness())
		{
			sb.append("</br>Wilderness");
		}
		return sb.toString();
	}

	/** Centre of the world map on this point via a temporary jump marker. */
	public static WorldPoint centre(TaskLocation l)
	{
		return LocationService.point(l).orElse(null);
	}
}
