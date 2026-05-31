/*
 * Copyright (c) 2024, Miss Indicator Plugin
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
package com.missindicator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.util.Deque;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MissIndicatorOverlay extends Overlay
{
	/** Extra height (pixels) added above the actor's tile so the text clears their model. */
	private static final int ACTOR_HEAD_OFFSET = 30;

	/** Duration of one OSRS game tick, in milliseconds. */
	private static final int GAME_TICK_MILLIS = 600;

	private final Client client;
	private final MissIndicatorPlugin plugin;
	private final MissIndicatorConfig config;

	@Inject
	MissIndicatorOverlay(Client client, MissIndicatorPlugin plugin, MissIndicatorConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Deque<MissIndicatorEntry> misses = plugin.getActiveMisses();
		if (misses.isEmpty())
		{
			return null;
		}

		long now = System.currentTimeMillis();
		for (MissIndicatorEntry entry : misses)
		{
			renderEntry(graphics, entry, now);
		}

		return null;
	}

	// ── private helpers ───────────────────────────────────────────────────────

	private void renderEntry(Graphics2D graphics, MissIndicatorEntry entry, long now)
	{
		// 0.0 = just spawned, 1.0 = about to expire. Driven by wall-clock time
		// (not game ticks) so the float/fade animation is smooth at the render
		// framerate instead of stepping once per 600ms tick.
		long lifetimeMillis = (long) entry.getDurationTicks() * GAME_TICK_MILLIS;
		float progress = lifetimeMillis <= 0 ? 1f
			: Math.min(1f, (now - entry.getSpawnMillis()) / (float) lifetimeMillis);

		java.awt.Point base = resolveBasePoint(entry);
		if (base == null)
		{
			return;
		}

		// Apply directional float offset
		int floated = (int)(entry.getFloatDistance() * progress);
		int dx = 0, dy = 0;
		switch (entry.getFloatDirection())
		{
			case UP:    dy = -floated; break;
			case DOWN:  dy = +floated; break;
			case LEFT:  dx = -floated; break;
			case RIGHT: dx = +floated; break;
			default:    break;
		}

		int drawX = base.x + dx;
		int drawY = base.y + dy;

		// Build font
		Font font = resolveFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		String text = entry.getDisplayText();
		int textX = drawX - fm.stringWidth(text) / 2; // horizontally centred
		int textY = drawY;

		// Fade alpha: begins fading at 50% of lifetime
		int alpha = 255;
		if (entry.isFadeOut() && progress > 0.5f)
		{
			alpha = Math.max(0, (int)(255 * (1f - (progress - 0.5f) * 2f)));
		}

		Color textColor   = withAlpha(config.missColor(),    alpha);
		Color bgColor     = withAlpha(config.backgroundColor(), alpha);

		drawBackground(graphics, font, text, textX, textY, bgColor, fm);

		graphics.setColor(textColor);
		graphics.drawString(text, textX, textY);
	}

	/** Draws the configured background effect (shadow, outline, box, or nothing). */
	private void drawBackground(Graphics2D graphics, Font font, String text,
								int textX, int textY, Color bgColor, FontMetrics fm)
	{
		if (bgColor.getAlpha() == 0)
		{
			return;
		}

		switch (config.backgroundStyle())
		{
			case SHADOW:
				graphics.setColor(bgColor);
				graphics.drawString(text, textX + 1, textY + 1);
				break;

			case OUTLINE:
			{
				GlyphVector gv = font.createGlyphVector(graphics.getFontRenderContext(), text);
				Shape outline = gv.getOutline(textX, textY);
				graphics.setColor(bgColor);
				graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				graphics.draw(outline);
				break;
			}

			case BOX:
			{
				int pad = 2;
				graphics.setColor(bgColor);
				graphics.fillRect(
					textX - pad,
					textY - fm.getAscent() - pad / 2,
					fm.stringWidth(text) + pad * 2,
					fm.getAscent() + pad
				);
				break;
			}

			default:
				break;
		}
	}

	/**
	 * Resolves the on-screen origin for a miss entry based on the configured display mode.
	 */
	private java.awt.Point resolveBasePoint(MissIndicatorEntry entry)
	{
		switch (entry.getDisplayMode())
		{
			case ABOVE_TARGET:
				return toScreenPoint(entry.getActor());

			case ABOVE_PLAYER:
			{
				Player local = client.getLocalPlayer();
				return local != null ? toScreenPoint(local) : null;
			}

			case SCREEN_CENTER:
				return new java.awt.Point(
					client.getCanvasWidth() / 2,
					client.getCanvasHeight() / 3
				);

			default:
				return null;
		}
	}

	/**
	 * Converts an actor's world position to a 2-D canvas coordinate slightly above
	 * their logical height — the same approach used by Customizable XP Drops when
	 * attaching predicted-hit text to an NPC.
	 */
	private java.awt.Point toScreenPoint(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}

		LocalPoint lp = actor.getLocalLocation();
		if (lp == null)
		{
			return null;
		}

		Point p = Perspective.localToCanvas(
			client,
			lp,
			client.getPlane(),
			actor.getLogicalHeight() + ACTOR_HEAD_OFFSET
		);

		if (p == null) return null;

		int y = p.getY() + (-config.displayOffsetY());

		return new java.awt.Point(p.getX(), y);
	}

	/** Resolves the display font from config. Falls back to RuneScape Bold when no name is set. */
	private Font resolveFont()
	{
		String name  = config.fontName();
		int    style = config.fontStyle().getAwtStyle();
		float  size  = config.fontSize();

		if (name == null || name.trim().isEmpty())
		{
			return FontManager.getRunescapeBoldFont().deriveFont(style, size);
		}

		return new Font(name, style, (int) size);
	}

	/** Returns a copy of {@code base} with its alpha channel replaced by {@code alpha}. */
	private static Color withAlpha(Color base, int alpha)
	{
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
	}
}
