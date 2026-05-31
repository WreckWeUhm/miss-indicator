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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("missindicator")
public interface MissIndicatorConfig extends Config
{
	// ── Section: Display Settings ─────────────────────────────────────────────
	// Mirrors the "Xp drop settings" section in Customizable XP Drops

	@ConfigSection(
		name = "Display Settings",
		description = "Controls what is shown and where",
		position = 0
	)
	String displaySection = "displaySection";

	@ConfigItem(
		keyName = "missText",
		name = "Miss text",
		description = "Text shown when your attack misses (deals 0 damage)",
		section = displaySection,
		position = 0
	)
	default String missText()
	{
		return "Miss!";
	}

	@ConfigItem(
		keyName = "showOnNpcs",
		name = "Show on NPCs",
		description = "Show the indicator when you miss an attack on an NPC",
		section = displaySection,
		position = 1
	)
	default boolean showOnNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOnPlayers",
		name = "Show on players (PvP)",
		description = "Show the indicator when you miss an attack on another player",
		section = displaySection,
		position = 2
	)
	default boolean showOnPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayMode",
		name = "Attach to",
		description = "Where the 'Miss!' text is anchored: above your target, above yourself, or screen centre",
		section = displaySection,
		position = 3
	)
	default MissDisplayMode displayMode()
	{
		return MissDisplayMode.ABOVE_TARGET;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "displayDurationTicks",
		name = "Duration (ticks)",
		description = "How many game ticks the indicator stays visible",
		section = displaySection,
		position = 4
	)
	default int displayDurationTicks()
	{
		return 3;
	}

	@ConfigItem(
			keyName = "displayOffsetY",
			name = "Offset Y",
			description = "Offset Y",
			section = displaySection,
			position = 5
	)
	default int displayOffsetY()
	{
		return 0;
	}

	// ── Section: Text Settings ───────────────────────────────────────────────────
	// Mirrors the font/style knobs in Customizable XP Drops

	@ConfigSection(
		name = "Text Settings",
		description = "Font, style, size and text color options",
		position = 1
	)
	String textSettingsSection = "textStyleSection";

	@ConfigItem(
		keyName = "fontName",
		name = "Font",
		description = "Font name for the miss text. Leave blank to use the RuneScape Bold font.",
		section = textSettingsSection,
		position = 0
	)
	default String fontName()
	{
		return "";
	}

	@Range(min = 8, max = 40)
	@ConfigItem(
			keyName = "fontSize",
			name = "Font size",
			description = "Size of the miss text in points",
			section = textSettingsSection,
			position = 1
	)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "fontStyle",
		name = "Font style",
		description = "Style applied to the font",
		section = textSettingsSection,
		position = 2
	)
	default MissFontStyle fontStyle()
	{
		return MissFontStyle.BOLD;
	}

	@Alpha
	@ConfigItem(
		keyName = "missColor",
		name = "Miss text color",
		description = "Color of the miss indicator text",
		section = textSettingsSection,
		position = 3
	)
	default Color missColor()
	{
		return new Color(255, 80, 80, 255);
	}

	@ConfigItem(
			keyName = "backgroundStyle",
			name = "Background",
			description = "Outline or shadow drawn behind the text",
			section = textSettingsSection,
			position = 4
	)
	default MissBackgroundStyle backgroundStyle()
	{
		return MissBackgroundStyle.SHADOW;
	}

	@Alpha
	@ConfigItem(
		keyName = "backgroundColor",
		name = "Background color",
		description = "Color of the background effect (set alpha to 0 to disable)",
		section = textSettingsSection,
		position = 5
	)
	default Color backgroundColor()
	{
		return new Color(0, 0, 0, 200);
	}

	// ── Section: Animation ────────────────────────────────────────────────────
	// Mirrors the XP drop speed and direction options

	@ConfigSection(
		name = "Animation",
		description = "Controls how the indicator moves and fades",
		position = 3
	)
	String animationSection = "animationSection";

	@ConfigItem(
		keyName = "floatDirection",
		name = "Float direction",
		description = "Direction the text drifts after appearing",
		section = animationSection,
		position = 0
	)
	default MissFloatDirection floatDirection()
	{
		return MissFloatDirection.UP;
	}

	@Range(min = 10, max = 120)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "floatDistance",
		name = "Float distance",
		description = "Pixels the text travels over its full lifetime",
		section = animationSection,
		position = 1
	)
	default int floatDistance()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "fadeOut",
		name = "Fade out",
		description = "Fade the text out during the second half of its lifetime",
		section = animationSection,
		position = 2
	)
	default boolean fadeOut()
	{
		return true;
	}

	// ── Section: Detection ────────────────────────────────────────────────────
	// Fine-tuning for how ranged/magic misses are judged.

	@ConfigSection(
		name = "Detection",
		description = "Advanced timing options for ranged and magic miss detection",
		position = 5,
		closedByDefault = true
	)
	String detectionSection = "detectionSection";

	@ConfigItem(
		keyName = "attackSafetyTick",
		name = "Safety tick",
		description = "Wait one extra game tick before judging a miss. OFF shows the miss on the same"
			+ " tick the XP drop would have appeared (snappiest). ON waits one tick longer, which avoids"
			+ " a hit briefly flashing 'Miss!' in cases where the damage XP arrives a tick late (e.g."
			+ " long-range ranged/magic). Applies to all attack styles.",
		section = detectionSection,
		position = 0
	)
	default boolean attackSafetyTick()
	{
		return false;
	}
}
