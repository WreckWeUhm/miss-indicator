# CLAUDE.md — Miss Indicator (RuneLite plugin)

Guidance for Claude when working in this repo. Read this fully before making changes.

## What this plugin does

Shows a **"Miss!"** indicator when the **local player's own attack deals 0 damage** (a miss/splash)
against an NPC or another player. It is NOT about enemy attacks missing the player — that was the
original boilerplate behavior and was explicitly reworked away.

Built from the runelite example-plugin boilerplate. Package: `com.missindicator`.

## Build environment (Windows, this machine)

- **JDK 25** at `C:\Program Files\Java\jdk-25`. Gradle runs on it.
- **Gradle 9.3** via the wrapper (`gradlew.bat`).
- Three fixes were applied to `build.gradle` over the stock boilerplate — **do not regress these**:
  1. **Lombok `1.18.40`** (was `1.18.30`). 1.18.30 crashes on JDK 25 with
     `NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN`.
  2. **`tasks.named('test') { failOnNoDiscoveredTests = false }`** — the only test class is a
     RuneLite launcher (`MissIndicatorPluginTest`, has `main()`), not a JUnit test; Gradle 9 fails
     the build on "no tests discovered" without this.
  3. **`run` task has `jvmArgs '-ea'`** — RuneLite's `ExternalPluginManager.loadBuiltin()` throws
     unless assertions are enabled.

### How to build / run / verify

```
# compile only (fastest signal)
gradlew.bat compileJava --no-daemon --console=plain

# build the fat jar
gradlew.bat shadowJar --no-daemon --console=plain

# launch RuneLite dev client with the plugin (assertions on)
"C:\Program Files\Java\jdk-25\bin\java.exe" -ea -jar build\libs\miss-indicator-1.0-SNAPSHOT.jar --developer-mode
```

There is a `run` Gradle task too, but launching the jar directly is the reliable path here.
**Claude can confirm the plugin compiles and loads, but cannot drive in-game combat** — actual
miss-display behavior must be tested by the user.

### Tooling gotcha (important)

In this environment the Bash/PowerShell **stdout channel intermittently buffers** — a command can
return empty inline, then its output flushes on a *later* turn. Mitigations that work:
- Redirect command output to a file and `Read` the file, rather than trusting inline output.
- Don't fire many parallel shell calls; if one errors, siblings get cancelled (cascade of empty
  results). Prefer one command per call for anything that matters.
- Don't add `ScheduleWakeup`/sleeps to "wait for flush" — it just adds noise.

## Detection model (the core design)

This is the heart of the plugin. Two independent questions:

1. **Did an attack happen?** → driven by the player's **attack animation** (`AnimationChanged`).
2. **Did it deal damage (hit) or 0 (miss)?** → driven by **Hitpoints XP**, NOT hitsplats.

### Why Hitpoints XP specifically

Mirrors how *Customizable XP Drops* infers damage (`XpDropDamageCalculator` reads only HP XP:
`hpXpDiff * 0.75 / modifier`). HP XP is granted in proportion to damage for every style
(melee/ranged ≈ dmg×1.33, magic = dmg×2). Crucially, a **magic splash still awards base cast XP to
the MAGIC skill but ZERO Hitpoints XP** — so reading HP XP alone correctly classifies a magic
splash as a miss. (An earlier version counted all combat skills and wrongly treated every magic
cast as a hit — do not regress to that.)

### Timing

- XP for a hit is delivered on the **attack tick** for all styles, and `StatChanged` is processed
  **before** `GameTick` within a tick. So a swing can be judged on the same tick it happened —
  matching XP Drops' on-tick timing.
- A **"Safety tick" config toggle** (`attackSafetyTick`, default OFF) optionally delays the verdict
  by 1 tick for cases where XP lands a tick late. Applies to all styles.
- Earlier code had a projectile-flight subsystem (`onProjectileMoved`) and a hardcoded
  `MELEE_RESOLVE_DELAY` — **both were removed**. They caused a permanent 1-tick lag and made the
  safety toggle a no-op. Don't reintroduce projectile timing; it's unnecessary because the XP
  verdict (not cadence) decides hit/miss, and sped-up attacks still produce their own XP.

## Current architecture (files in `src/main/java/com/missindicator/`)

- **`MissIndicatorPlugin.java`** — detection. Subscribes to:
  - `onAnimationChanged`: gates (is local player? not idle anim `-1`? interacting with attackable
    target? one-per-tick guard) → registers a `PendingAttack` snapshotting `totalDamageXp` and a
    `resolveTick` (= attackTick + safety).
  - `onStatChanged` / `onFakeXpDrop`: accumulate **Hitpoints** XP into `totalDamageXp`.
  - `onGameTick`: when `resolveTick` reached, if `totalDamageXp` did not increase → it was a miss →
    add a `MissIndicatorEntry`. Also expires old entries.
- **`PendingAttack.java`** — immutable record of one swing (target, attackTick, resolveTick,
  xpAtAttack). `resolveTick` is final; no projectile/`ranged` fields anymore.
- **`MissIndicatorEntry.java`** — immutable snapshot of a miss for rendering. Captures config at
  creation AND `spawnMillis = System.currentTimeMillis()`.
- **`MissIndicatorOverlay.java`** — renders entries. **Animation progress is driven by wall-clock
  milliseconds** (`spawnMillis`, `GAME_TICK_MILLIS = 600`), NOT game ticks. Driving it off ticks
  made the float/fade animation step at ~1.67 fps (choppy) — do not regress to tick-based progress.
- **`MissIndicatorConfig.java`** — config interface. Sections: Display, Text Style, Colors,
  Animation, Sound, Detection. Display anchor is `MissDisplayMode` (ABOVE_TARGET / ABOVE_PLAYER /
  SCREEN_CENTER). `attackSafetyTick` lives in the Detection section (closedByDefault).
- Enums: `MissDisplayMode`, `MissBackgroundStyle`, `MissFloatDirection`, `MissFontStyle`.

## NEXT TASK (planned, agreed with user — implement this)

**Add a block/non-attack animation denylist so taking-hit animations don't register as attacks.**

Problem: `onAnimationChanged` currently accepts any non-idle animation while interacting with an
attackable target. But the **block/taking-hit animation** uses the same animation channel and would
pass the gates → a tick where you're hit but didn't attack can produce a false "Miss!".

Chosen approach = **Option B** (decided after reviewing `ngraves95/attacktimer`):
- Create `AttackAnimations.java`: a `Set<Integer>` of the ~30 `NON_ATTACK` animation IDs taken from
  attacktimer's `AnimationData` enum (the `TAKING_HIT_*` weapon animations are the core; plus
  eating/alching/fletching/teleport/lunar/vengeance/thrall/etc.), and `isNonAttackAnimation(int)`.
- Wire into `onAnimationChanged`: after the existing gates, `if (isNonAttackAnimation(animId)) return;`.
- Optionally also add the target cross-check attacktimer uses (interacting NPC's
  `getComposition().getActions()` contains "Attack") for extra robustness.

Do NOT copy attacktimer's whole 400-line `AnimationData` enum, and do NOT copy its `VariableSpeed/`
subsystem (Yama Shadow Crash, Doom/Tormented Demons, etc.). That machinery only matters for
*predicting* the next attack tick (cadence). Our hit/miss verdict comes from HP XP, so sped-up
attacks are handled automatically — a faster attack still produces its own animation and its own XP.

**Licensing:** `AnimationData.java` is BSD-2-Clause, © Matsyir / Mazhar / Lexer747. BSD-2 requires
retaining the copyright notice + disclaimer in source redistribution. Put an attribution header on
`AttackAnimations.java` crediting those authors and noting the IDs derive from the attacktimer
project. (The example-plugin boilerplate is also BSD-2; consistent.)

### Key NON_ATTACK animation IDs (taking-hit, from attacktimer AnimationData)

```
397 (1h/unarmed), 410 (2h sword), 5866 (anchor), 8017 (blisterwood flail), 430 (blowpipe),
7512 (bulwark), 7200 (chainmace), 3176 (chinchompa), 378 (dagger), 4177 (defender), 388 (fang),
7056 (godsword), 383 (keris), 420 (large staff), 403 (mace), 1666 (obby maul), 435 (scythe),
1156 (shield), 1709 (spear), 415 (staff), 424 (unarmed), 2063 (verac's flail), 1659 (whip)
```
Plus non-combat actions also tagged NON_ATTACK in their enum: 722 imbue, 829 eat food/potion,
712 low alch, 713 high alch, 1816 lunar teleport, 8316 vengeance, 8973 summon thrall, etc.
(Re-fetch the authoritative list from the repo when implementing rather than trusting this excerpt.)

## Conventions

- Java 11 bytecode (`options.release.set(11)`), even though building on JDK 25.
- Lombok is available (`@Getter`, `@Slf4j`, etc.).
- Keep the BSD-2 license header on source files.
- After any change, run `compileJava` to confirm it builds. A harmless deprecation warning in
  `MissIndicatorOverlay.java` exists; not blocking.

## Reference plugins (for study, not dependencies)

- **Customizable XP Drops** — `github.com/l2-/template-plugin` (package `com.xpdrops`). Source of the
  HP-XP-as-damage model. `XpDropDamageCalculator.calculateHit` = `hpXpDiff * 0.75 / modifier`.
- **Attack Timer Metronome** — `github.com/ngraves95/attacktimer`. Source of the block-list approach
  (`AnimationData.isBlockListAnimation`) and a `VariableSpeed/` system for attack-speed-up mechanics
  (only relevant if doing cadence prediction, which this plugin does not).
