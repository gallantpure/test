/*
 * Copyright (c) 2017, Devin French <https://github.com/devinfrench>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *	list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *	this list of conditions and the following disclaimer in the documentation
 *	and/or other materials provided with the distribution.
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
package net.runelite.client.plugins.inferno;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.client.plugins.inferno.displaymodes.InfernoPrayerDisplayMode;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Singleton
public class InfernoInfoBoxOverlay extends Overlay
{
	private static final Color NOT_ACTIVATED_BACKGROUND_COLOR = new Color(150, 0, 0, 150);
	private final Client client;
	private final InfernoPlugin plugin;
	private final InfernoConfig config;
	private final SpriteManager spriteManager;
	private final PanelComponent imagePanelComponent = new PanelComponent();
	private BufferedImage prayMeleeSprite;
	private BufferedImage prayRangedSprite;
	private BufferedImage prayMagicSprite;

	@Inject
	private InfernoInfoBoxOverlay(final Client client, final InfernoPlugin plugin, final InfernoConfig config, final SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.prayerDisplayMode() != InfernoPrayerDisplayMode.BOTTOM_RIGHT
			&& config.prayerDisplayMode() != InfernoPrayerDisplayMode.BOTH)
		{
			return null;
		}

		imagePanelComponent.getChildren().clear();

		if (plugin.getClosestAttack() != null)
		{
			final BufferedImage prayerImage = getPrayerImage(plugin.getClosestAttack());

			imagePanelComponent.getChildren().add(new ImageComponent(prayerImage));
			imagePanelComponent.setBackgroundColor(client.isPrayerActive(plugin.getClosestAttack().getPrayer())
				? ComponentConstants.STANDARD_BACKGROUND_COLOR
				: NOT_ACTIVATED_BACKGROUND_COLOR);
		}
		else
		{
			imagePanelComponent.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		}

		return imagePanelComponent.render(graphics);
	}

	private BufferedImage getPrayerImage(InfernoNPC.Attack attack)
	{
		if (prayMeleeSprite == null)
		{
			prayMeleeSprite = spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MELEE, 0);
		}
		if (prayRangedSprite == null)
		{
			prayRangedSprite = spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MISSILES, 0);
		}
		if (prayMagicSprite == null)
		{
			prayMagicSprite = spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MAGIC, 0);
		}

		switch (attack)
		{
			case MELEE:
				return prayMeleeSprite;
			case RANGED:
				return prayRangedSprite;
			case MAGIC:
				return prayMagicSprite;
		}

		return prayMagicSprite;
	}
}
