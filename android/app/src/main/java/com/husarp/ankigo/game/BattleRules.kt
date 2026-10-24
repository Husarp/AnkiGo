package com.husarp.ankigo.game

/** R17C: the name rule files use for [BattleEngine]'s state of one round (a round, or the free hit of a switch). */
internal typealias Round = BattleEngine.Round

/**
 * R17C: one set of battle rules. The battle engine ([BattleEngine]) only plays the frame of a round; every rule that goes beyond it
 * (charge / recharge, confusion, Protect, weather, abilities ...) lives in a set that implements this interface and is listed in
 * [ALL]. Every hook has a neutral default, so a set overrides only what it needs and an empty set changes nothing.
 *
 * HOW HOOKS COMBINE (the engine asks ALL sets, in [ALL] order; the helpers are `rulesFirst` ... `rulesSum` below):
 * - "first non-null wins": [statusMove] [moveBlocked] [forcedMove] [failReason] [hits] [basePower] [fixedDamage] [stab] [blockStatus]
 *   [blockVolatile];
 * - [useful] (R17C FIX-A): any set that says false wins, else the first non-null (an early "true" never hides a later veto), see [rulesUseful];
 * - "any true": [blocks] [immune] [suppressWeather] [slowerFirst] [skipsCharge] [ignoresBurn] [ignoresStage]; "all must be true": [beforeMove] (stops at the first false);
 * - "chained" (each set gets the previous set's answer): [moveType] [effectiveness] [accuracy] [takeDamage] [stageChange] [types] [stats]
 *   [moves] [grounded] [ability] [secondaryChance] [indirectDamage] [drain];
 * - "multiplied": [powerMod] [attackMod] [defenseMod] [weatherMod] [finalMod] [speedMod] [wakeMod]; "added": [priority] [bracket];
 * - "all run": [beforeHit] [afterHit] [afterMove] [afterCalledMove] [afterDamage] [afterStatus] [afterStageChange] [secondary] [onEnter] [endOfRound]
 *   [afterFreeHit] [onLeave].
 *
 * WHO IS WHO: `p = true` is the Main's side, `false` the wild one's; a set must treat both the same. `tp` = the side a thing happens to.
 * `r` = the running [Round] (it is also a [BattleView]); `v` = a read-only [BattleView] (also used by the move choice, where there is
 * no round: such hooks must not write events). A hook that changes the fight writes its battle line with [Round.fx] and an
 * [EffectKind]; the engine never writes a line for a hook that handled something (see each hook).
 *
 * The effective-value hooks ([types] [stats] [moves] [grounded] [ability]) are asked through [BattleView]: inside them read the BASE
 * values ([BattleView.baseTypes] ...) or the side's saved state, never the effective value of the same kind (that would ask itself).
 *
 * The sets are internal on purpose (they know the round); tests inject their own list with [BattleRules.using].
 */
internal interface BattleRules {
    // ---- moves ----

    /**
     * Handles a STATUS move completely. Asked after the move's own checks (`failReason`, `blocks`, `immune`, the powder rule, the hit chance);
     * the engine's generic stat / status / heal path comes after this: true = it worked, false = it failed (the engine writes "But it failed!"
     * unless the hook already wrote a line), null = not this set's move. Damaging moves never ask.
     */
    fun statusMove(r: Round, p: Boolean, move: Move): Boolean? = null

    /** Before [move] is tried, after sleep / freeze / paralysis: false = [p] can't move this turn (the hook wrote the line, e.g. Truant, flinch). */
    fun beforeMove(r: Round, p: Boolean, move: Move): Boolean = true

    /**
     * Why [p] can't use [move] now (Taunt, Disable, Torment ...), null = it can. The text is a short phrase for the line
     * "X can't use Y <phrase>!" ("after the taunt"; "" = no phrase). The move choice leaves such a move out; a side with nothing else
     * still tries one and the engine writes the line and fails the move.
     */
    fun moveBlocked(v: BattleView, p: Boolean, move: Move): String? = null

    /** A move [p] MUST use now instead of picking one (Encore, a locked move, a recharge turn ...); asked before every pick, also in a switch's free hit. */
    fun forcedMove(v: BattleView, p: Boolean): Move? = null

    /** The line to show ("But it failed!" ...) when [move] fails before it is used (Fake Out later in the fight, Sucker Punch ...); null = it can be used. */
    fun failReason(r: Round, p: Boolean, move: Move): String? = null

    /** The target's side stops [move] (Protect ...): the hook writes the line (an [EffectKind.BLOCKED] float). Asked only for moves aimed at the other side. */
    fun blocks(r: Round, p: Boolean, move: Move): Boolean = false

    /** The target is immune to the hit for a reason other than its type (Levitate, Volt Absorb ...): the hook writes the line. Asked for damaging and status moves aimed at the other side. */
    fun immune(r: Round, h: Hit): Boolean = false

    /** R17C FIX-A: [p]'s damaging [move] is about to hit (it was not stopped and did not miss), before the first hit's damage: Spectral Thief takes the target's stat rises here. */
    fun beforeHit(r: Round, p: Boolean, move: Move) {}

    /** After a damaging move of [p] hit (all its hits done, drain / recoil / thaw done), before its secondaries; [damage] = the HP the target lost. */
    fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {}

    /**
     * A secondary of [move] rolled true (its status and stat changes were already applied): fill in what the data cannot say: its
     * `volatileStatus` (flinch, confusion ...) and code-only secondaries ([Move.secondaryOnHit], e.g. Tri Attack). R17C-1 added this hook.
     */
    fun secondary(r: Round, p: Boolean, move: Move, sec: Secondary) {}

    /**
     * The chance in percent that the secondary [sec] of the hit's move happens, chained: [chance] is the answer so far (the data's to start).
     * R17C-9a: Serene Grace doubles it, Sheer Force and Shield Dust make it 0 (Shield Dust leaves a secondary that only changes the user alone).
     * Asked for every secondary of every move that hit, before the roll; a chance of 100 or more always happens.
     */
    fun secondaryChance(h: Hit, sec: Secondary, chance: Int): Int = chance

    /**
     * After [p] tried [move] (also a miss or a failure; not when it couldn't move, the move was blocked, it only charged, or it was called
     * by another move through [Round.useMove]): tracking, locks. [failed] = it failed or missed. The engine has already set
     * [BattleSide.lastMove] / [BattleSide.lastMoveFailed] (and counts [BattleSide.actions] after this action).
     */
    fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {}

    /**
     * R17C FIX-A: [move] was called by another move through [Round.useMove] (Metronome, Sleep Talk, Copycat ...) and has been performed. Only the
     * clean-up that belongs to the move itself goes here (a called Spit Up ends the stockpile); [afterMove] is NOT asked for a called move (its
     * tracking, locks, charge and rest turns are the caller's).
     */
    fun afterCalledMove(r: Round, p: Boolean, move: Move) {}

    /** Added to [move]'s own priority ([p]'s turn order); also asked for the move choice's order. */
    fun priority(v: BattleView, p: Boolean, move: Move): Int = 0

    /**
     * Added, once per side and round, to the order WITHIN one priority (it comes before the speed, also in Trick Room): -1 = last of that priority
     * (Stall, Mycelium Might's status moves), +1 = first (Quick Draw, which rolls its chance here). Asked by [BattleEngine.Round.play] only, for a
     * side that picked a move; a hook that writes a line checks `v is Round`. R17C-9b.
     */
    fun bracket(v: BattleView, p: Boolean, move: Move): Int = 0

    /** The number of hits instead of the move's multi-hit range; the first non-null wins. */
    fun hits(r: Round, p: Boolean, move: Move): Int? = null

    // ---- the damage formula (BattleEngine.damage / computeDamage) ----

    /** The move's base power instead of [Move.power] (Return, Grass Knot ...); first non-null wins. */
    fun basePower(h: Hit): Int? = null

    /** A fixed amount of damage instead of the formula (Counter, Endeavor ...); first non-null wins, asked before the move's own fixed / level / half-HP / one-hit-KO rule. A type immunity still gives 0. */
    fun fixedDamage(h: Hit): Int? = null

    /** The move's type for this hit (Weather Ball, Normalize ...), chained; [Hit.type] already holds the answer so far. Struggle has no type and is never asked. */
    fun moveType(h: Hit, type: Type): Type = type

    /** Multiplies the move's power (Technician, Facade ...). */
    fun powerMod(h: Hit): Double = 1.0

    /** Multiplies the attack stat in use, after its stage (Huge Power, Choice items ...). */
    fun attackMod(h: Hit): Double = 1.0

    /** Multiplies the defense stat in use, after its stage (Sandstorm for Rock types, Eviolite ...). */
    fun defenseMod(h: Hit): Double = 1.0

    /** Multiplies the base damage for the weather / terrain (rain boosts Water ...). */
    fun weatherMod(h: Hit): Double = 1.0

    /** Replaces [Tuning.STAB] when the move has STAB (Adaptability); first non-null wins. */
    fun stab(h: Hit): Double? = null

    /** Multiplies the final damage (screens, Multiscale, Life Orb ...). */
    fun finalMod(h: Hit): Double = 1.0

    /** Any true = a burn of the attacker does not halve this physical hit's damage (Facade, Guts). R17C-4 added this hook. */
    fun ignoresBurn(h: Hit): Boolean = false

    /**
     * Any true = the stage [key] (atk def spa spd accuracy evasion) of the ATTACKER's side ([onAttacker] = true) or of the target's side is not
     * counted in this hit: Unaware, Keen Eye (the target's evasion) ... R17C-9a added this hook; the damage formula and the hit chance ask it.
     */
    fun ignoresStage(h: Hit, onAttacker: Boolean, key: String): Boolean = false

    /** The type effectiveness of the hit (0, 0.25 ... 4), chained: Wonder Guard, Scrappy, Ring Target ... [eff] is the answer so far. */
    fun effectiveness(h: Hit, eff: Double): Double = eff

    /** The move's accuracy in percent BEFORE the stage multipliers, chained; null = it never misses (No Guard, Gravity ...). A one-hit KO ignores this. */
    fun accuracy(h: Hit, acc: Double?): Double? = acc

    /**
     * The HP [tp] loses from this hit, chained (Substitute takes it, Sturdy leaves 1 HP ...): [damage] is the amount so far, already scaled
     * by the answers and at least 1. Return what really comes off the HP (0 when something else absorbed it).
     */
    fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int = damage

    /** [tp] lost [damage] HP (above 0) to the other side's hit; after each hit of a multi-hit move. */
    fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {}

    /**
     * The HP [p] loses to damage that is not a hit, chained: [source] is one of the [Indirect] keys, [amount] the HP so far (at least 1). Return
     * what really comes off (0 = none, nothing is written): Magic Guard 0, Rock Head 0 for recoil and crash, Heatproof half of a burn.
     * Asked by [BattleEngine.Round.hurt] when the caller names a source, and for the burn / poison damage of the engine itself. R17C-9a.
     */
    fun indirectDamage(r: Round, p: Boolean, source: String, amount: Int): Int = amount

    /**
     * The HP [to] gains by draining the other side (drain moves, Leech Seed), chained: [amount] so far. Return what [to] really heals; a hook that
     * turns it into a loss (Liquid Ooze) hurts [to] itself, writes the line and returns 0. R17C-9a.
     */
    fun drain(r: Round, to: Boolean, amount: Int): Int = amount

    // ---- statuses, conditions, stages ----

    /**
     * Why [tp] can't get [status] (Limber, Safeguard, Misty Terrain ...), null = it can. [byFoe] = the other side causes it. A status MOVE shows the
     * text as a line; a secondary chance fails quietly. "" = blocked and the hook already wrote its own line / float.
     */
    fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? = null

    /** Why [tp] can't get the lasting condition [key] (confusion, flinch, taunt ...), null = it can; same text rules as [blockStatus]. */
    fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? = null

    /** The change of stage [key] (atk def spa spd spe accuracy evasion) before it is applied, chained: Contrary flips it, Clear Body / Mist return 0 (blocked: the hook writes the line), Simple doubles it. */
    fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean): Int = delta

    /** A stage of [tp] really moved by [delta] (never 0): Defiant, Competitive ... */
    fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) {}

    /** [tp] got [status] just now. */
    fun afterStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean) {}

    // ---- the Pokemon itself (read-only; also used by the move choice and the details) ----

    /** [p]'s types, chained (Soak, Forest's Curse, Roost, Burn Up ...). Read [BattleView.baseTypes], not [BattleView.types]. */
    fun types(v: BattleView, p: Boolean, t: List<Type>): List<Type> = t

    /** [p]'s stats, chained (Transform, Power Trick, Wonder Room ...); HP is not used in a fight (max HP is fixed). */
    fun stats(v: BattleView, p: Boolean, s: Stats): Stats = s

    /** The moves [p] can pick from, chained (Transform, Mimic). */
    fun moves(v: BattleView, p: Boolean, m: List<Move>): List<Move> = m

    /** Whether [p] is on the ground, chained (the base: it is not a Flying type): Levitate, Magnet Rise, Gravity, Air Balloon ... */
    fun grounded(v: BattleView, p: Boolean, g: Boolean): Boolean = g

    /** [p]'s ability, chained: a normalized id (lower case letters and digits); "" = none or suppressed (Gastro Acid, Neutralizing Gas). */
    fun ability(v: BattleView, p: Boolean, a: String): String = a

    /** Any true = the weather has no effect (Cloud Nine, Air Lock). */
    fun suppressWeather(v: BattleView): Boolean = false

    /** [move] skips its charge turn for [p] (Solar Beam in the sun, Power Herb ...). */
    fun skipsCharge(r: Round, p: Boolean, move: Move): Boolean = false

    // ---- the turn ----

    /** Multiplies [p]'s speed in the turn order (Swift Swim, Tailwind ...). */
    fun speedMod(v: BattleView, p: Boolean): Double = 1.0

    /** Multiplies how fast [p]'s sleep wears off (Early Bird = 2): the chance to wake, or the sleep turns that count down in one try. R17C-9a. */
    fun wakeMod(v: BattleView, p: Boolean): Double = 1.0

    /** Any true = the slower Pokémon moves first (Trick Room). */
    fun slowerFirst(v: BattleView): Boolean = false

    /** [p]'s Pokémon starts acting in this fight (its first action or the first hit on it, after a switch too): Intimidate, weather setters ... Asked once per [BattleSide.entered]. */
    fun onEnter(r: Round, p: Boolean) {}

    /**
     * Once per round, after both sides acted (not for a switch's free hit): residual damage, heals, countdowns of conditions.
     * ORDER OF THE END OF A ROUND: (1) the sets' `endOfRound` in [ALL] order, (2) faint check, (3) burn / poison damage of the sides in
     * turn order (the engine's own), (4) faint check. Check `r.mon(p).hp > 0` before touching a side.
     */
    fun endOfRound(r: Round) {}

    /**
     * R17C FIX-A: the free hit of a switch is over ([BattleEngine.switchHit]; it is not a round, so [endOfRound] did not run). Remove what only lasts until
     * the end of a round and the free hit set up (Protect, Endure, Magic Coat, Snatch, Electrify, Roost, Ion Deluge ...), with the set's own end-of-round
     * removals; countdowns and damage do not happen. Never called for a round.
     */
    fun afterFreeHit(r: Round) {}

    /** [p]'s Pokémon [mon] is switched out of the fight ([BattleSide] is replaced right after; [Game.changed], the Ankimon import). Natural Cure, Regenerator ... */
    fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) {}

    // ---- texts and the move choice ----

    /** Extra sentences for [MoveText.describe] (each ends with a full stop); appended after the engine's own lines. */
    fun describe(m: Move): List<String> = emptyList()

    /**
     * The Smart move choice: could this move still change something / work now? null = no opinion (ask the engine's own rule). The sets' answers
     * combine as "any false wins, else the first non-null" ([rulesUseful]). Damaging moves are asked too (false = it would surely fail now).
     * Use [BattleView.acts] for a move that only works against an action of the other side.
     */
    fun useful(v: BattleView, p: Boolean, move: Move): Boolean? = null

    /** The ids of the status moves this set implements (the coverage test checks that every move is handled or said to have no effect). */
    val handles: Set<String> get() = emptySet()

    companion object {
        private val DEFAULT: List<BattleRules> by lazy { listOf(CoreRules, MovePower, SideMoves, FieldRules, MoveControl, Abilities, AbilitiesMore) }

        @Volatile private var injected: List<BattleRules>? = null

        /** Every rule set, in the order the engine asks them: core flow, move power, side moves, field, move control, abilities, more abilities (R17C-9b). */
        val ALL: List<BattleRules> get() = injected ?: DEFAULT

        /** Runs [block] with [rules] as [ALL] (tests only), then puts the real list back. */
        fun <T> using(rules: List<BattleRules>, block: () -> T): T {
            val saved = injected
            injected = rules
            try { return block() } finally { injected = saved }
        }
    }
}

// ---- combining the sets ----

internal inline fun rulesEach(f: (BattleRules) -> Unit) { for (r in BattleRules.ALL) f(r) }

/**
 * R17C FIX-A: the answers of [BattleRules.useful] for [move] of side [p]: false when any set says false, else the first non-null answer, else null.
 * (Before, the first non-null answer won, so a set that said "true" hid a later set's veto: Leech Seed against a Good as Gold target ...)
 */
internal fun rulesUseful(v: BattleView, p: Boolean, move: Move): Boolean? {
    var answer: Boolean? = null
    for (r in BattleRules.ALL) {
        val x = r.useful(v, p, move) ?: continue
        if (!x) return false
        if (answer == null) answer = true
    }
    return answer
}

/** The first non-null answer in [BattleRules.ALL] order, else null. */
internal inline fun <T : Any> rulesFirst(f: (BattleRules) -> T?): T? {
    for (r in BattleRules.ALL) { val x = f(r); if (x != null) return x }
    return null
}

internal inline fun rulesAny(f: (BattleRules) -> Boolean): Boolean {
    for (r in BattleRules.ALL) if (f(r)) return true
    return false
}

/** True when every set says true; stops at the first false. */
internal inline fun rulesAll(f: (BattleRules) -> Boolean): Boolean {
    for (r in BattleRules.ALL) if (!f(r)) return false
    return true
}

/** Each set gets the answer so far and returns the new one. */
internal inline fun <T> rulesChain(start: T, f: (BattleRules, T) -> T): T {
    var x = start
    for (r in BattleRules.ALL) x = f(r, x)
    return x
}

internal inline fun rulesProduct(f: (BattleRules) -> Double): Double {
    var x = 1.0
    for (r in BattleRules.ALL) x *= f(r)
    return x
}

internal inline fun rulesSum(f: (BattleRules) -> Int): Int {
    var x = 0
    for (r in BattleRules.ALL) x += f(r)
    return x
}

/**
 * R17C: one hit of a damaging move (or the checks of a move aimed at the other side), as the rule hooks see it. [type], [category] and
 * [crit] are filled as the damage formula goes: [type] is already the final type when any other hook is asked ([BattleRules.moveType] sees the
 * type so far), [crit] after the crit roll. [hitIndex] counts the hits of a multi-hit move from 0.
 */
internal class Hit(
    val view: BattleView,
    val attackerIsMain: Boolean,
    val attacker: Mon, val aSide: BattleSide,
    val defender: Mon, val dSide: BattleSide,
    val move: Move,
    val hitIndex: Int = 0,
) {
    var type: Type = move.type
    var category: Category = move.category
    var crit: Boolean = false

    val physical: Boolean get() = category == Category.PHYSICAL

    /** The attacker's / the target's effective types. */
    val aTypes: List<Type> get() = view.types(attackerIsMain)
    val dTypes: List<Type> get() = view.types(!attackerIsMain)

    /** The running round, or null when the hit is only being estimated (move choice, details). */
    val round: Round? get() = view as? Round
}

/**
 * R17C: the Main left the fight ([oldMon] is the Pokémon that was out; null = not known): every set's [BattleRules.onLeave] runs, then the
 * Main's side is replaced by a fresh one (stat stages, status and lasting conditions go; the field's side conditions stay).
 */
internal fun Battle.switchOutMain(data: GameData, oldMon: Mon?) {
    if (oldMon != null) rulesEach { it.onLeave(this, data, true, oldMon) }
    player = BattleSide()
}

/**
 * R17C-9a: the sources of [BattleRules.indirectDamage] (damage that is not a hit; costs the user pays for its own move, a confusion self-hit
 * and the attacks of Future Sight name no source and are never asked).
 */
internal object Indirect {
    const val RECOIL = "recoil"          // a move's recoil
    const val STRUGGLE = "struggle"      // Struggle's recoil
    const val CRASH = "crash"            // High Jump Kick ... missed
    const val MIND_BLOWN = "mindblown"   // the HP Mind Blown / Steel Beam / Chloroblast cost
    const val BURN = "brn"
    const val POISON = "psn"             // poison and bad poison
    const val WEATHER = "weather"        // sandstorm, hail, Dry Skin in the sun
    const val SEED = "leechseed"
    const val BOUND = "bound"            // Wrap, Fire Spin ...
    const val CURSE = "curse"
    const val NIGHTMARE = "nightmare"
    const val SALT_CURE = "saltcure"
    const val ABILITY = "ability"        // Rough Skin, Iron Barbs
    const val SPIKY = "spikyshield"      // Spiky Shield
    const val POWDER = "powder"          // R17C FIX-A: a powdered Pokémon's Fire move
}
