/*
 * The NON_ATTACK animation IDs below are derived from the Attack Timer Metronome
 * plugin's AnimationData enum (https://github.com/ngraves95/attacktimer), which is
 * licensed BSD-2-Clause. The original copyright notice and disclaimer are retained
 * as required by that license:
 *
 * Copyright (c) 2021, Matsyir <https://github.com/matsyir>
 * Copyright (c) 2020, Mazhar <https://twitter.com/maz_rs>
 * Copyright (c) 2024-2026, Lexer747 <https://github.com/Lexer747>
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

import java.util.Set;

/**
 * Denylist of animation IDs that play on the player's animation channel but are
 * <em>not</em> attacks. The most important of these are the per-weapon
 * "taking-hit" / block animations: when you are struck while wielding a weapon,
 * the client plays a block animation on the same channel as your attack
 * animations. Without filtering, that block could pass {@code onAnimationChanged}'s
 * gates on a tick where you did not actually swing, producing a false "Miss!".
 *
 * <p>The IDs are taken from attacktimer's {@code AnimationData} enum (every entry
 * categorized {@code NON_ATTACK}): the per-weapon TAKING_HIT block animations plus
 * non-combat actions (eating, alching, fletching, teleports, lunar spells,
 * vengeance, thralls, etc.) that can fire mid-combat.
 *
 * <p>Note we deliberately do <em>not</em> import attacktimer's attack-speed-up /
 * variable-speed machinery: our hit/miss verdict comes from Hitpoints XP, not
 * attack cadence, so a sped-up attack is handled automatically (it still produces
 * its own animation and its own XP).
 */
final class AttackAnimations
{
	/** Animation IDs that should never be treated as the local player attacking. */
	private static final Set<Integer> NON_ATTACK = Set.of(
		// Per-weapon "taking hit" / block animations.
		397,  // 1-handed / unarmed
		410,  // 2h sword
		5866, // anchor
		8017, // blisterwood flail
		430,  // blowpipe
		7512, // bulwark
		7200, // chainmace
		3176, // chinchompa
		378,  // dagger
		4177, // defender
		388,  // fang
		7056, // godsword
		383,  // keris
		420,  // large staff
		403,  // mace
		1666, // obby maul
		435,  // scythe
		1156, // shield
		1709, // spear
		415,  // staff
		424,  // unarmed
		2063, // verac's flail
		1659, // whip

		// Non-combat actions that can fire mid-combat on the same channel.
		722,  // magic imbue
		6299, // spellbook swap
		4409, // lunar group healing
		4411, // lunar other spells
		4413, // npc contact
		8316, // vengeance
		7198, // reanimation
		8975, // demonic offering
		8979, // shadow veil
		8970, // mark of darkness
		881,  // pickpocketing
		8973, // summon thrall
		1816, // lunar teleport
		6293, // monster examine
		6294, // humidify
		7118, // geomancy
		7672, // dream
		1574, // rockslug bag of salt
		2779, // desert lizard ice cooler
		1248, // fletching knife
		5243, // fletching kebbit
		5244, // fletching chisel
		8485, // fletching dart tip
		5249, // herb tar
		5208, // setup hunter trap
		5207, // reset snare trap
		5212, // reset box trap
		3872, // desert amulet
		829,  // eat food / drink potion
		3170, // overload hit
		712,  // low alchemy
		713   // high alchemy
	);

	private AttackAnimations()
	{
	}

	/**
	 * @return {@code true} if {@code animationId} is a known non-attack animation
	 *         (block / taking-hit or a non-combat action) and should therefore not
	 *         be registered as a swing.
	 */
	static boolean isNonAttackAnimation(int animationId)
	{
		return NON_ATTACK.contains(animationId);
	}
}
