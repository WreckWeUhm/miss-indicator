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

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Miss Indicator",
	description = "Shows a 'Miss!' indicator when your own attack fails to deal damage",
	tags = {"miss", "combat", "indicator", "splash", "zero", "damage", "pvp", "xp"}
)
public class MissIndicatorPlugin extends Plugin
{
	/** Animation value reported by {@link Actor#getAnimation()} when an actor is idle. */
	private static final int IDLE_ANIMATION = -1;

	/**
	 * The skill we read to detect damage. Hitpoints XP is granted in proportion to
	 * damage dealt for every combat style (melee/ranged = damage * 1.33, magic =
	 * damage * 2), and — crucially — it is the ONLY combat XP that a magic cast does
	 * not award on a splash. A spell that misses still grants its base cast XP to the
	 * Magic skill, but zero Hitpoints XP, so reading Hitpoints alone lets us tell a
	 * magic miss (splash) apart from a magic hit. (XP formulas per the OSRS wiki, the
	 * same ones the l2-/template-plugin predicted-hit calculator uses.)
	 */
	private static final Skill DAMAGE_SKILL = Skill.HITPOINTS;

	@Inject
	private Client client;

	@Inject
	private MissIndicatorConfig config;

	@Inject
	private MissIndicatorOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	/** Miss indicators currently being rendered. Read by the overlay thread. */
	@Getter
	private final Deque<MissIndicatorEntry> activeMisses = new ArrayDeque<>();

	/** Swings awaiting an XP verdict. */
	private final List<PendingAttack> pendingAttacks = new CopyOnWriteArrayList<>();

	/** Last-known Hitpoints XP, used to compute per-event deltas. -1 = not yet seeded. */
	private int prevHitpointsXp = -1;

	/** Monotonic running total of damage-derived (Hitpoints) XP gained; only increases. */
	private long totalDamageXp;

	/** Guards against registering more than one swing per tick. */
	private int lastRegisteredTick = -1;

	@Provides
	MissIndicatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MissIndicatorConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		reset();
		prevHitpointsXp = client.getSkillExperience(DAMAGE_SKILL);
		log.debug("Miss Indicator started!");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		reset();
		log.debug("Miss Indicator stopped!");
	}

	// ── attack detection ───────────────────────────────────────────────────────

	/**
	 * Registers a swing when the local player begins an attack animation while
	 * interacting with a valid target. We can't yet know if it hit — that's
	 * decided later in {@link #onGameTick} once damage XP has (or hasn't) arrived.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local)
		{
			return;
		}

		int animId = local.getAnimation();
		if (animId == IDLE_ANIMATION)
		{
			return;
		}

		// Block / taking-hit animations (and other non-combat actions) play on the
		// same animation channel as attacks. Filter them out so a tick where we were
		// hit but didn't swing can't masquerade as a missed attack.
		if (AttackAnimations.isNonAttackAnimation(animId))
		{
			return;
		}

		Actor target = local.getInteracting();
		if (!isAttackableTarget(target))
		{
			return;
		}

		int tick = client.getTickCount();
		if (tick == lastRegisteredTick)
		{
			// One swing per tick; ignore repeat animation events in the same tick.
			return;
		}
		lastRegisteredTick = tick;

		// A hit's Hitpoints XP is awarded on the attack tick for every style, and
		// StatChanged is processed before GameTick within that tick — so a swing can
		// be judged on the same tick it happened (matching Customizable XP Drops'
		// timing). The optional safety tick waits one extra tick in case the XP lands
		// a tick later (e.g. long-range projectiles), trading snappiness for accuracy.
		int safety = config.attackSafetyTick() ? 1 : 0;
		pendingAttacks.add(new PendingAttack(
			target, tick, tick + safety, totalDamageXp));
	}

	// ── damage (Hitpoints XP) tracking ───────────────────────────────────────────

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != DAMAGE_SKILL)
		{
			return;
		}

		int xp = event.getXp();
		if (prevHitpointsXp < 0)
		{
			// First reading — seed only, don't count as a gain.
			prevHitpointsXp = xp;
			return;
		}

		int delta = xp - prevHitpointsXp;
		prevHitpointsXp = xp;
		if (delta > 0)
		{
			totalDamageXp += delta;
		}
	}

	/**
	 * Fake XP drops (max-level / 200m skills) don't move real XP, so count Hitpoints
	 * fake drops as proof of damage too.
	 */
	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		if (event.getSkill() == DAMAGE_SKILL)
		{
			totalDamageXp += event.getXp();
		}
	}

	// ── resolution / lifecycle ───────────────────────────────────────────────────

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();

		// Resolve swings whose verdict tick has arrived.
		Iterator<PendingAttack> it = pendingAttacks.iterator();
		while (it.hasNext())
		{
			PendingAttack pending = it.next();
			if (tick < pending.getResolveTick())
			{
				continue;
			}

			boolean hit = totalDamageXp > pending.getXpAtAttack();
			if (!hit)
			{
				// No damage XP since the swing started => the attack dealt 0 => miss.
				activeMisses.addLast(new MissIndicatorEntry(pending.getTarget(), tick, config));
			}
			pendingAttacks.remove(pending);
		}

		// Expire indicators that have outlived their configured duration.
		activeMisses.removeIf(e -> (tick - e.getSpawnTick()) >= e.getDurationTicks());
	}

	// ── helpers ──────────────────────────────────────────────────────────────────

	/** A target is attackable if it's a combat NPC, or a player (PvP), subject to config. */
	private boolean isAttackableTarget(Actor target)
	{
		if (target instanceof NPC)
		{
			// Combat level 0 filters out non-combat NPCs (fishing spots, bankers, etc.)
			return config.showOnNpcs() && target.getCombatLevel() > 0;
		}

		if (target instanceof Player)
		{
			return config.showOnPlayers();
		}

		return false;
	}

	private void reset()
	{
		activeMisses.clear();
		pendingAttacks.clear();
		prevHitpointsXp = -1;
		totalDamageXp = 0;
		lastRegisteredTick = -1;
	}
}
