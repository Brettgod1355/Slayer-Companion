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

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;

/** Game interface sprites for the panel, read once from the client's cache and kept for the session. */
final class RsSprites
{
	private final SpriteManager spriteManager;
	private final Runnable onLoaded;
	private final Map<Integer, BufferedImage> loaded = new ConcurrentHashMap<>();
	private final Set<Integer> requested = ConcurrentHashMap.newKeySet();

	/** {@code onLoaded} runs on the Swing thread whenever a sprite arrives. */
	RsSprites(SpriteManager spriteManager, Runnable onLoaded)
	{
		this.spriteManager = spriteManager;
		this.onLoaded = onLoaded;
	}

	/** The sprite, or null until the client has loaded it (then {@code onLoaded} runs). */
	@Nullable
	BufferedImage get(int spriteId)
	{
		BufferedImage img = loaded.get(spriteId);
		if (img == null && requested.add(spriteId))
		{
			spriteManager.getSpriteAsync(spriteId, 0, sprite ->
			{
				loaded.put(spriteId, sprite);
				SwingUtilities.invokeLater(onLoaded);
			});
		}
		return img;
	}
}
