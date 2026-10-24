package com.husarp.ankigo.game

import com.husarp.ankigo.game.Type.*
import kotlin.math.floor
import kotlin.random.Random

/** The standard 18-type chart. Immune = 0 (AnkiGo fix; Ankimon's engine used 1/8). */
object TypeChart {
    private val table = Array(Type.entries.size) { DoubleArray(Type.entries.size) { 1.0 } }.also { t ->
        fun row(atk: Type, twice: List<Type> = emptyList(), half: List<Type> = emptyList(), zero: List<Type> = emptyList()) {
            twice.forEach { t[atk.ordinal][it.ordinal] = 2.0 }
            half.forEach { t[atk.ordinal][it.ordinal] = 0.5 }
            zero.forEach { t[atk.ordinal][it.ordinal] = 0.0 }
        }
        run {
            row(NORMAL, half = listOf(ROCK, STEEL), zero = listOf(GHOST))
            row(FIRE, twice = listOf(GRASS, ICE, BUG, STEEL), half = listOf(FIRE, WATER, ROCK, DRAGON))
            row(WATER, twice = listOf(FIRE, GROUND, ROCK), half = listOf(WATER, GRASS, DRAGON))
            row(ELECTRIC, twice = listOf(WATER, FLYING), half = listOf(ELECTRIC, GRASS, DRAGON), zero = listOf(GROUND))
            row(GRASS, twice = listOf(WATER, GROUND, ROCK), half = listOf(FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL))
            row(ICE, twice = listOf(GRASS, GROUND, FLYING, DRAGON), half = listOf(FIRE, WATER, ICE, STEEL))
            row(FIGHTING, twice = listOf(NORMAL, ICE, ROCK, DARK, STEEL), half = listOf(POISON, FLYING, PSYCHIC, BUG, FAIRY), zero = listOf(GHOST))
            row(POISON, twice = listOf(GRASS, FAIRY), half = listOf(POISON, GROUND, ROCK, GHOST), zero = listOf(STEEL))
            row(GROUND, twice = listOf(FIRE, ELECTRIC, POISON, ROCK, STEEL), half = listOf(GRASS, BUG), zero = listOf(FLYING))
            row(FLYING, twice = listOf(GRASS, FIGHTING, BUG), half = listOf(ELECTRIC, ROCK, STEEL))
            row(PSYCHIC, twice = listOf(FIGHTING, POISON), half = listOf(PSYCHIC, STEEL), zero = listOf(DARK))
            row(BUG, twice = listOf(GRASS, PSYCHIC, DARK), half = listOf(FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL, FAIRY))
            row(ROCK, twice = listOf(FIRE, ICE, FLYING, BUG), half = listOf(FIGHTING, GROUND, STEEL))
            row(GHOST, twice = listOf(PSYCHIC, GHOST), half = listOf(DARK), zero = listOf(NORMAL))
            row(DRAGON, twice = listOf(DRAGON), half = listOf(STEEL), zero = listOf(FAIRY))
            row(DARK, twice = listOf(PSYCHIC, GHOST), half = listOf(FIGHTING, DARK, FAIRY))
            row(STEEL, twice = listOf(ICE, ROCK, FAIRY), half = listOf(FIRE, WATER, ELECTRIC, STEEL))
            row(FAIRY, twice = listOf(FIGHTING, DRAGON, DARK), half = listOf(FIRE, POISON, STEEL))
        }
    }

    /** Multiplier of an attacking type against one defending type: 0, 0.5, 1 or 2. */
    fun single(atk: Type, def: Type): Double = table[atk.ordinal][def.ordinal]

    /** Product over all of the defender's types. */
    fun eff(atk: Type, def: List<Type>): Double = def.fold(1.0) { m, t -> m * single(atk, t) }
}

/**
 * "Same feel, simpler engine": Ankimon's damage formula, 18 types, STAB, accuracy/evasion, crits, stat stages,
 * simple non-volatile statuses (par/brn/psn/tox/slp/frz), drain/recoil/heal, multi-hit, fixed damage, OHKO.
 * Charge moves (Solar Beam, Fly...) charge on their first use and hit on the user's very next action (R17C-3, CoreRules; Tuning.CHARGE_MOVES).
 *
 * R17C-1: the engine plays the FRAME of a round; every further rule (volatile conditions, weather, abilities, special moves ...) is a hook
 * of a [BattleRules] set that the engine asks at fixed places. With every hook neutral the numbers are exactly the old engine's.
 * ORDER OF AN ACTION (one side's turn, [Round.act]): both sides enter if they haven't ([BattleRules.onEnter]) -> the start of the action
 * (R17C-3: Glaive Rush's drawback ends) -> a rest turn after a recharge move -> sleep / freeze / paralysis -> `beforeMove` (CoreRules: flinch,
 * confusion, love) -> `moveBlocked` (an action stopped on the way ends a charge, a locked move and Uproar) -> the charge turn of a charge move
 * (its next action is the hit) -> the move ([Round.perform]): `failReason`, `blocks`, `immune`, hit
 * chance, then for a damaging move `hits` and per hit the damage formula + `takeDamage` + `afterDamage`, drain / recoil, self stage changes,
 * thaw, `afterHit`, every secondary (each rolled; `secondary` hook), and for a status move `statusMove` or the generic status / stage / heal
 * path -> the engine's tracking (`lastMove`, `actions`) and `afterMove`.
 * ORDER OF A ROUND: pick both moves (`forcedMove` first) -> turn order (priority + `priority` hooks, then speed with `speedMod`;
 * `slowerFirst` reverses the speed; a tie goes to the wild one) -> both actions -> the end of the round (see [BattleRules.endOfRound]).
 */
object BattleEngine {
    data class DamageResult(val damage: Int, val effectiveness: Double, val crit: Boolean)

    // Used when the game data has no such move (the real data does).
    private val STRUGGLE_MOVE = Move(Tuning.STRUGGLE, "Struggle", Type.NORMAL, Category.PHYSICAL, 50, null)
    private val TACKLE_MOVE = Move(Tuning.FALLBACK_MOVE, "Tackle", Type.NORMAL, Category.PHYSICAL, 40, 100)

    internal val SELF_TARGETS = setOf("self", "adjacentAllyOrSelf", "allies", "allySide")
    internal val BOOST_KEYS = listOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")
    private val BOOST_LABELS = listOf("Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "accuracy", "evasiveness")

    /**
     * Plays one Ankimon battle round between the main and the wild Pokémon (mutates HP and battle sides):
     * - main: a random move from its moves (Struggle when none of them can damage the wild one) if [mainActs], otherwise nothing;
     * - wild: a random move if [enemyActs], otherwise Splash (no action, emits GameEvent.Splash);
     * - order: higher priority first, then higher effective speed; a tie goes to the wild one;
     * - every damage the main deals is multiplied by [multiplier] and every damage the wild one deals by [enemyPower]
     *   (Round 10: RoundRule picks both from the answers), and is at least 1 when the move hits a non-immune target;
     * - a fainted Pokémon doesn't act; end-of-turn burn/poison damage for both.
     * Returns Attack / Effect / Splash / Fainted events in order. Does NOT resolve catch/defeat/heal.
     * An Attack's damage is what really came off the target's HP (never more than it had left).
     * Round 10 (W9): [moveChoice] is how both sides pick their move (see [MoveChooser]); RANDOM (the default here) is the old rule,
     * the other two also stop accuracy / evasion stages at ±[Tuning.SMART_ACC_EVA_LIMIT].
     */
    fun playRound(
        data: GameData, battle: Battle, main: Mon, multiplier: Double, enemyActs: Boolean, rng: Random,
        enemyPower: Double = 1.0, mainActs: Boolean = true, moveChoice: MoveChoice = MoveChoice.RANDOM,
    ): List<GameEvent> {
        val r = Round(data, battle, main, multiplier.coerceAtLeast(0.0), enemyPower.coerceAtLeast(0.0), rng, moveChoice)
        r.play(enemyActs, mainActs)
        battle.rounds++
        return r.events
    }

    /**
     * Round 10 (W10): the free hit of a switch. ONE action of the wild one against [incoming] (the Main just sent in, already on
     * fresh sides), at [power] of its normal damage (Tuning.SWITCH_HIT_POWER = one Again); it picks its move with [moveChoice] like in
     * a round and every battle rule of an action applies (order of checks, accuracy, type, sleep / freeze / paralysis may stop it,
     * secondary effects, recoil, drain). Unlike [playRound] the Main does not act, nothing counts as a round (battle.rounds stays),
     * there is no end-of-turn damage or countdown and nothing can make the Main flinch. A wild one that charged last time releases the
     * charge here (its next action is the hit); a charge move that would only charge is left out of the pick while the wild one has
     * another move (see [pickSwitchHitMove]). Returns the Attack / Effect / Fainted events; resolves nothing.
     */
    fun switchHit(
        data: GameData, battle: Battle, incoming: Mon, rng: Random,
        moveChoice: MoveChoice = MoveChoice.RANDOM, power: Double = Tuning.SWITCH_HIT_POWER,
    ): List<GameEvent> {
        val r = Round(data, battle, incoming, 0.0, power.coerceAtLeast(0.0), rng, moveChoice, freeHit = true)
        r.wildStrikes()
        return r.events
    }

    /**
     * The move of the wild one's free hit: [MoveChooser.pickWild], but among the moves that really attack now. A charge move that would
     * only charge (it is not the move the wild one is charging, and no rule skips its charge turn) would spend the hit charging up, so it
     * is left out while the wild one has any other move; with nothing else (every move is such a charge move) the choice is made among
     * all of them and the wild one charges (no damage). A move the wild one is already charging, or has to repeat, is forced by the pick
     * itself ([BattleRules.forcedMove]). With no charge move in play this is exactly [MoveChooser.pickWild] (same picks, same rng use).
     * [round] is only used to ask the rules whether a charge turn is skipped; null = a throwaway one.
     */
    internal fun pickSwitchHitMove(data: GameData, battle: Battle, incoming: Mon, rng: Random, choice: MoveChoice, round: Round? = null): Move {
        val known = knownMoves(data, battle.wild)
        val skipper by lazy { round ?: Round(data, battle, incoming, 0.0, 0.0, rng, choice, freeHit = true) }
        val usable = known.filterNot {
            CoreRules.isCharge(it) && battle.enemy.charging != it.id && !rulesAny { rule -> rule.skipsCharge(skipper, false, it) }
        }
        // R17C FIX-A: the Main does not act in a free hit (SimpleView.mainActs = false): a reactive move (Counter, Protect ...) is not picked for nothing
        if (usable.isEmpty() || usable.size == known.size) return MoveChooser.pick(choice, SimpleView.of(data, battle, incoming, mainActs = false), false, rng)
        val w = battle.wild
        val reduced = Mon(w.uid, w.species, w.name, w.level, w.xp, w.ivs, w.evs, w.nature, w.gender, w.shiny, w.ability, usable.map { it.id }.toMutableList(), w.hp)
        val view = SimpleView(data, incoming, reduced, battle.player, battle.enemy, StatCalc.maxHp(data, incoming), battle.maxWildHp(data), battle, mainActs = false)
        return MoveChooser.pick(choice, view, false, rng)
    }

    /**
     * One hit's damage (before Ankimon's multiplier): Gen 5 formula
     * base = floor(floor(floor(2L/5+2) * P * A / D) / 50) + 2, then crit x1.5, random 85..100 %, STAB 1.5, type, burn 0.5 (physical).
     * A/D use stat stages (a crit ignores the attacker's negative and the defender's positive stages).
     * [forceCrit]/[forceRoll] fix the random parts for tests.
     * Fixed-damage moves (level, fixed, half HP, OHKO) skip the formula and the crit but still give 0 against an immune type.
     *
     * R17C-1: [view] is the fight the rule hooks see (null = a [SimpleView] of just these two Pokémon and sides); [attackerIsMain] says whose
     * side the attacker is (true = the Main's), [hitIndex] counts the hits of a multi-hit move from 0. The pipeline is [computeDamage].
     */
    fun damage(
        data: GameData, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move,
        rng: Random, forceCrit: Boolean? = null, forceRoll: Int? = null,
        view: BattleView? = null, attackerIsMain: Boolean = true, hitIndex: Int = 0,
    ): DamageResult {
        val v = view ?: if (attackerIsMain) SimpleView(data, attacker, defender, aSide, dSide) else SimpleView(data, defender, attacker, dSide, aSide)
        return computeDamage(newHit(v, attackerIsMain, attacker, aSide, defender, dSide, move, hitIndex), rng, forceCrit, forceRoll)
    }

    /** A [Hit] with its type resolved ([BattleRules.moveType], chained; Struggle has none). */
    internal fun newHit(
        view: BattleView, attackerIsMain: Boolean, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move, hitIndex: Int = 0,
    ): Hit {
        val h = Hit(view, attackerIsMain, attacker, aSide, defender, dSide, move, hitIndex)
        if (move.id != Tuning.STRUGGLE) h.type = rulesChain(move.type) { r, t -> r.moveType(h, t) }
        return h
    }

    /** The index into the stat stages ([BattleSide.boosts]) of "atk" "def" "spa" "spd" "spe". */
    private fun stageIndex(key: String): Int = BOOST_KEYS.indexOf(key).also { check(it in 0..4) { "not a battle stat: $key" } }

    private fun statOf(s: Stats, key: String): Int = when (key) {
        "atk" -> s.atk; "def" -> s.def; "spa" -> s.spa; "spd" -> s.spd; else -> s.spe
    }

    /**
     * The damage pipeline of one hit, in the mainline order (Gen 5 formula as before): type (already in [Hit.type]) -> effectiveness on the
     * target's EFFECTIVE types, then the chained `effectiveness` hooks -> a fixed damage (`fixedDamage` hook, else the move's own rule)
     * ends it here -> power (`basePower` hook or the move's, x `powerMod`) -> attack stat ([Move.offensiveStat] / [Move.offensiveFromTarget]
     * choose it) x its stage x `attackMod` -> defense stat ([Move.defensiveStat]; [Move.ignoreDefensive] drops its stage) x stage x
     * `defenseMod` -> base damage -> `weatherMod` -> crit (off) -> roll -> STAB (`stab` hook or [Tuning.STAB], on the user's effective types)
     * -> effectiveness -> burn halves physical damage -> `finalMod` -> at least 1 unless immune.
     */
    internal fun computeDamage(h: Hit, rng: Random, forceCrit: Boolean? = null, forceRoll: Int? = null): DamageResult {
        val v = h.view
        val move = h.move
        val attacker = h.attacker
        val defender = h.defender
        val struggle = move.id == Tuning.STRUGGLE
        val eff = if (struggle) 1.0 else rulesChain(TypeChart.eff(h.type, h.dTypes)) { r, e -> r.effectiveness(h, e) }

        val fixed = rulesFirst { it.fixedDamage(h) } ?: when {
            move.levelDamage -> attacker.level
            move.fixedDamage != null -> move.fixedDamage
            move.halfHp -> maxOf(1, defender.hp / 2)
            move.ohko -> defender.hp
            else -> null
        }
        if (fixed != null) return DamageResult(if (eff > 0) fixed else 0, eff, false)

        val physical = h.physical
        val crit = forceCrit ?: (Tuning.CRITS && (move.willCrit || rng.nextDouble() < Tuning.CRIT_CHANCE[move.critRatio.coerceIn(0, 4)]))
        h.crit = crit
        val aStats = v.stats(h.attackerIsMain)
        val dStats = v.stats(!h.attackerIsMain)
        val offKey = move.offensiveStat ?: if (physical) "atk" else "spa"   // Body Press: the user's Defense
        val defKey = move.defensiveStat ?: if (physical) "def" else "spd"   // Psyshock: Defense although special
        val offStats = if (move.offensiveFromTarget) dStats else aStats     // Foul Play: the target's Attack
        val offSide = if (move.offensiveFromTarget) h.dSide else h.aSide
        var aStage = if (rulesAny { it.ignoresStage(h, !move.offensiveFromTarget, offKey) }) 0 else offSide.boosts[stageIndex(offKey)] // R17C-9a: Unaware
        var dStage = if (move.ignoreDefensive || rulesAny { it.ignoresStage(h, false, defKey) }) 0 else h.dSide.boosts[stageIndex(defKey)]
        if (crit) { aStage = maxOf(aStage, 0); dStage = minOf(dStage, 0) }
        val a = maxOf(1, floor(statOf(offStats, offKey) * stageMult(aStage) * rulesProduct { it.attackMod(h) }).toInt())
        val d = maxOf(1, floor(statOf(dStats, defKey) * stageMult(dStage) * rulesProduct { it.defenseMod(h) }).toInt())

        val basePower = rulesFirst { it.basePower(h) } ?: if (move.power > 0) move.power else Tuning.UNKNOWN_POWER
        val power = basePower * rulesProduct { it.powerMod(h) }
        var dmg = floor(floor(2.0 * attacker.level / 5 + 2).toInt() * power * a / d.toDouble()).toInt() / 50 + 2
        val weather = rulesProduct { it.weatherMod(h) }
        if (weather != 1.0) dmg = floor(dmg * weather).toInt()
        if (crit) dmg = floor(dmg * Tuning.CRIT_MULT).toInt()
        val roll = forceRoll ?: rng.nextInt(Tuning.ROLL_MIN, 101)
        dmg = dmg * roll / 100
        if (!struggle && h.type in h.aTypes) dmg = floor(dmg * (rulesFirst { it.stab(h) } ?: Tuning.STAB)).toInt()
        dmg = floor(dmg * eff).toInt()
        if (physical && h.aSide.status == "brn" && !rulesAny { it.ignoresBurn(h) }) dmg /= 2 // R17C-4: Facade ignores the burn
        val last = rulesProduct { it.finalMod(h) }
        if (last != 1.0) dmg = floor(dmg * last).toInt()
        if (eff > 0) dmg = maxOf(1, dmg) else dmg = 0
        return DamageResult(dmg, eff, crit)
    }

    /** Stat stage multiplier for atk/def/spa/spd/spe: (2+s)/2 or 2/(2-s). */
    fun stageMult(stage: Int): Double = if (stage >= 0) (2.0 + stage) / 2.0 else 2.0 / (2.0 - stage)

    /** Accuracy/evasion stage multiplier: (3+s)/3 or 3/(3-s). */
    fun accStageMult(stage: Int): Double = if (stage >= 0) (3.0 + stage) / 3.0 else 3.0 / (3.0 - stage)

    /** R17C-9a: the accuracy stage minus the evasion stage of one hit (-6..6); a move that ignores evasion and the rules' [BattleRules.ignoresStage] (Unaware, Keen Eye) leave a stage out. */
    internal fun accuracyStage(h: Hit): Int {
        val acc = if (rulesAny { it.ignoresStage(h, true, "accuracy") }) 0 else h.aSide.boosts[5]
        val eva = if (h.move.ignoreEvasion || rulesAny { it.ignoresStage(h, false, "evasion") }) 0 else h.dSide.boosts[6]
        return (acc - eva).coerceIn(-6, 6)
    }

    /** The moves of [mon] that the game data knows (Tackle when none). */
    internal fun knownMoves(data: GameData, mon: Mon): List<Move> =
        mon.moves.mapNotNull { data.move(it) }.ifEmpty { listOf(data.move(Tuning.FALLBACK_MOVE) ?: TACKLE_MOVE) }

    /** The move the main will use this round (random, Ankimon), Struggle when nothing can hurt the wild one. This is the RANDOM move choice. */
    fun pickPlayerMove(data: GameData, main: Mon, wild: Mon, rng: Random): Move {
        val known = knownMoves(data, main)
        val wildTypes = data.sp(wild.species).types
        if (known.none { it.damaging && TypeChart.eff(it.type, wildTypes) > 0 }) return struggle(data)
        return known[rng.nextInt(known.size)]
    }

    /**
     * The RANDOM move choice for the Main among [known] (the moves it may pick), judged against [view]'s wild one.
     * R17C FIX-A: Struggle when no move can hurt the wild one by the SAME test as the Smart choice ([MoveChooser.canHurt]: the hit's type, rule
     * immunities like Levitate / Volt Absorb / Wonder Guard, [BattleRules.useful]); with a move that can, the pick is still among ALL known moves
     * (random, like the games: it may pick a move that fails).
     */
    internal fun pickPlayerMove(view: BattleView, known: List<Move>, rng: Random): Move {
        if (known.none { it.damaging && MoveChooser.canHurt(view, true, it) && rulesUseful(view, true, it) != false }) return struggle(view.data)
        return known[rng.nextInt(known.size)]
    }

    /** R17C FIX-A: a rule skips the charge turn of [move] for [p] (Solar Beam in the sun ...), asked from a view; false when there is no fight to ask in. */
    internal fun skipsCharge(view: BattleView, p: Boolean, move: Move): Boolean {
        val round = view as? Round ?: view.battle?.let { b -> Round(view.data, b, view.mon(true), 0.0, 0.0, Random(0), freeHit = true) } ?: return false
        return rulesAny { it.skipsCharge(round, p, move) }
    }

    /** R17C FIX-A: a rule says the hit never misses (Lock-On, No Guard, Glaive Rush): the accuracy chain ends in null. A one-hit KO still asks it (and still fails against a higher level). */
    internal fun neverMisses(h: Hit): Boolean = rulesChain<Double?>(100.0) { r, a -> r.accuracy(h, a) } == null

    /** The move the wild one uses this round with the RANDOM move choice: one of its moves by chance. */
    internal fun pickWildMove(data: GameData, wild: Mon, rng: Random): Move {
        val known = knownMoves(data, wild)
        return known[rng.nextInt(known.size)]
    }

    internal fun struggle(data: GameData) = data.move(Tuning.STRUGGLE) ?: STRUGGLE_MOVE

    /** Type immunities to a status: Electric can't be paralysed (nor a Ground type by an Electric move), Fire not burned, Ice not frozen, Poison / Steel not poisoned. */
    internal fun canHaveStatus(targetTypes: List<Type>, status: String, moveType: Type): Boolean = when (status) {
        "par" -> Type.ELECTRIC !in targetTypes && !(moveType == Type.ELECTRIC && Type.GROUND in targetTypes)
        "brn" -> Type.FIRE !in targetTypes
        "frz" -> Type.ICE !in targetTypes
        "psn", "tox" -> Type.POISON !in targetTypes && Type.STEEL !in targetTypes
        else -> true
    }

    /** What the last damaging hit [Round.lastHitTaken] reports: its [category], the damage it did (what a substitute took included), who dealt it and with which move. */
    internal class TakenHit(val category: Category, val damage: Int, val byMain: Boolean, val moveId: String)

    /**
     * State of one round; keeps the two sides symmetric by addressing them with [p] = "the main's side". It is the [BattleView] the hooks
     * see, and the toolbox they write with ([fx] [heal] [hurt] [setStatus] [boost] [addVolatile] [useMove] ...).
     */
    internal class Round(
        override val data: GameData, override val battle: Battle, val main: Mon, val mult: Double, val enemyPower: Double, val rng: Random,
        val moveChoice: MoveChoice = MoveChoice.RANDOM,
        /** R17C-3: this is the free hit of a switch ([switchHit]), not a round: the Main does not act, and nothing may wait for an end of round. */
        val freeHit: Boolean = false,
    ) : BattleView {
        val wild = battle.wild
        val events = ArrayList<GameEvent>()
        private var out: MutableList<GameEvent> = events
        /** R17C-5: HP of the current hit that a [BattleRules.takeDamage] hook took on the target's behalf (Substitute); the hit's drain / recoil count it, the Attack event's damage does not. Reset after each hit. */
        internal var absorbed = 0
        private val maxHpArr = intArrayOf(battle.maxWildHp(data), StatCalc.maxHp(data, main)) // [0] wild, [1] main
        private val fainted = BooleanArray(2)
        private var reasonShown = false // a blocked status / condition / stage wrote its own line during this status move

        /** R17C-9a: the side whose MOVE is being performed right now (null outside a move: entering, the end of a round). Mold Breaker reads it. */
        var actor: Boolean? = null
            private set

        /** R17C-9b: the move being performed right now (null outside a move); Mycelium Might reads it (its status moves ignore abilities). */
        var actingMove: Move? = null
            private set

        // per-round tracking, index 0 = wild, 1 = main
        private val moved = BooleanArray(2)
        private val taken = IntArray(2)
        private val hurtBy = BooleanArray(2) // R17C FIX-A: a hit cost the side real HP in this round (a hit a substitute took does not count)
        private val lastTaken = arrayOfNulls<TakenHit>(2)
        private val planned = arrayOfNulls<Move>(2) // R17C-4: the move each side picked for this round (play)
        private val acting = booleanArrayOf(true, true) // R17C FIX-A: [wild, main] takes an action in this round (see [acts])

        override val field: BattleField get() = battle.field
        override fun mon(p: Boolean) = if (p) main else wild
        override fun side(p: Boolean) = if (p) battle.player else battle.enemy
        override fun maxHp(p: Boolean) = maxHpArr[if (p) 1 else 0]
        override fun acts(p: Boolean): Boolean = acting[idx(p)]

        private fun idx(p: Boolean) = if (p) 1 else 0

        // ---- tracking for the hooks ----

        /** [p] already took its action in this round (also one that was stopped by sleep, a flinch ...). */
        fun movedThisRound(p: Boolean): Boolean = moved[idx(p)]

        /** The HP [p] lost to damaging hits so far in this round. */
        fun damageTakenThisRound(p: Boolean): Int = taken[idx(p)]

        /** [p] took a damaging hit that cost HP in this round (a hit that only a substitute took does not count). */
        fun hitThisRound(p: Boolean): Boolean = hurtBy[idx(p)]

        /**
         * The last damaging hit [p] took in this round (its category, damage, who dealt it, the move), or null. Counter / Mirror Coat / Metal Burst read it.
         * R17C FIX-A: the damage counts what a substitute took too (the games' Counter answers a hit on the substitute).
         */
        fun lastHitTaken(p: Boolean): TakenHit? = lastTaken[idx(p)]

        /** R17C-4: the move [p] picked for this round, or null (an idle turn, a switch's free hit, a round it doesn't act in). Sucker Punch / Thunderclap read it. */
        fun plannedMove(p: Boolean): Move? = planned[idx(p)]

        /** The answers-based damage factor of [p]'s attacks in this round (the Main's multiplier, the wild one's power). */
        fun damageScale(p: Boolean): Double = if (p) mult else enemyPower

        // ---- writing events ----

        /** Writes an [GameEvent.Effect] for [p]: the line [text], its [kind] and the numbers the kind carries ([label] = the short float text). */
        fun fx(p: Boolean, text: String, kind: EffectKind = EffectKind.OTHER, stat: String? = null, stages: Int = 0, amount: Int = 0, label: String? = null) {
            out += GameEvent.Effect(p, text, mon(p).hp, maxHp(p), side(p).status, kind, stat, stages, amount, label)
        }

        /** [p] regains up to [amount] HP (never above its max); the event carries what it really gained. [label] = a short float text (an ability's name). */
        fun heal(p: Boolean, amount: Int, text: String, kind: EffectKind = EffectKind.HEAL, label: String? = null) {
            val before = mon(p).hp
            mon(p).hp = minOf(maxHp(p), before + amount)
            fx(p, text, kind, amount = mon(p).hp - before, label = label)
        }

        /**
         * [p] loses up to [amount] HP (never below 0); the event carries what it really lost. A faint is noticed by [checkFaint] / the round.
         * R17C-9a: a [source] ([Indirect]) says this is damage that is not a hit: [BattleRules.indirectDamage] may cut it down or end it (Magic
         * Guard, Rock Head); nothing is written then. No source = a cost or a hit, never asked.
         */
        fun hurt(p: Boolean, amount: Int, text: String, kind: EffectKind = EffectKind.RECOIL, label: String? = null, source: String? = null) {
            val n = if (source == null) amount else rulesChain(amount) { rule, x -> rule.indirectDamage(this, p, source, x) }
            if (n <= 0) return
            val before = mon(p).hp
            mon(p).hp = maxOf(0, before - n)
            fx(p, text, kind, amount = before - mon(p).hp, label = label)
        }

        /**
         * [to] drains [amount] HP from the other side (a drain move, Leech Seed): [BattleRules.drain] may change it (Liquid Ooze turns it into a
         * loss), what is left heals [to] with [text]. R17C-9a.
         */
        fun drain(to: Boolean, amount: Int, text: String, kind: EffectKind = EffectKind.DRAIN) {
            val n = rulesChain(amount) { rule, x -> rule.drain(this, to, x) }
            if (n > 0) heal(to, n, text, kind)
        }

        /** Emits [GameEvent.Fainted] once for [p] when its HP is 0. */
        fun checkFaint(p: Boolean) {
            val i = idx(p)
            if (mon(p).hp <= 0 && !fainted[i]) { fainted[i] = true; events += GameEvent.Fainted(p, mon(p).name) }
        }

        fun speed(p: Boolean): Double {
            val s = stats(p).spe * stageMult(side(p).boosts[4]) * rulesProduct { it.speedMod(this, p) }
            return if (side(p).status == "par") s * Tuning.PARA_SPEED else s
        }

        // ---- the round ----

        fun play(enemyActs: Boolean, mainActs: Boolean) {
            acting[idx(false)] = enemyActs; acting[idx(true)] = mainActs // known before the picks: a reactive move is not picked for an action that won't come
            val playerMove: Move? = if (mainActs) MoveChooser.pick(moveChoice, this, true, rng) else null
            val enemyMove: Move? = if (enemyActs) MoveChooser.pick(moveChoice, this, false, rng) else null
            planned[idx(true)] = playerMove; planned[idx(false)] = enemyMove

            val playerPriority = playerMove?.let { m -> m.priority + rulesSum { it.priority(this, true, m) } } ?: 0
            val enemyPriority = enemyMove?.let { m -> m.priority + rulesSum { it.priority(this, false, m) } } ?: 0
            val playerBracket = playerMove?.let { m -> rulesSum { it.bracket(this, true, m) } } ?: 0  // R17C-9b: Stall, Quick Draw ... (within one priority)
            val enemyBracket = enemyMove?.let { m -> rulesSum { it.bracket(this, false, m) } } ?: 0
            val playerFirst = when {
                enemyPriority != playerPriority -> playerPriority > enemyPriority
                enemyBracket != playerBracket -> playerBracket > enemyBracket
                rulesAny { it.slowerFirst(this) } -> speed(true) < speed(false) // Trick Room; a tie still goes to the wild one
                else -> speed(true) > speed(false) // a tie goes to the wild one
            }
            val order = if (playerFirst) listOf(true, false) else listOf(false, true)
            for (p in order) {
                act(p, if (p) playerMove else enemyMove)
                checkFaint(true); checkFaint(false)
            }
            // the end of the round: the rule sets first, then burn / poison damage (the engine's own)
            rulesEach { it.endOfRound(this) }
            checkFaint(true); checkFaint(false)
            for (p in order) endOfTurn(p)
        }

        /** The free hit of a switch (W10): the wild one's single action against [main]; no turn order, no Splash, no end-of-turn damage. */
        fun wildStrikes() {
            if (main.hp <= 0 || wild.hp <= 0) return // nothing to hit, or nobody left to hit with
            acting[idx(true)] = false // the Main does not act in a free hit
            act(false, pickSwitchHitMove(data, battle, main, rng, moveChoice, this))
            checkFaint(true); checkFaint(false)
            rulesEach { it.afterFreeHit(this) } // R17C FIX-A: a free hit is no round: what lasts "until the end of the round" ends here, not in the next real round
        }

        /** Both sides that have not entered the fight yet do now, the faster first ([BattleRules.onEnter]). */
        private fun enterBoth() {
            if (battle.player.entered && battle.enemy.entered) return
            val order = if (speed(true) > speed(false)) listOf(true, false) else listOf(false, true)
            for (q in order) {
                if (side(q).entered) continue
                side(q).entered = true
                rulesEach { it.onEnter(this, q) }
                checkFaint(q)
            }
        }

        private fun act(p: Boolean, picked: Move?) {
            if (picked == null) { if (!p && mon(p).hp > 0 && mon(!p).hp > 0) events += GameEvent.Splash(wild.name); return } // only the wild one's idle turn is shown
            if (mon(p).hp <= 0 || mon(!p).hp <= 0) return
            enterBoth()
            if (mon(p).hp <= 0 || mon(!p).hp <= 0) return
            takeTurn(p, picked)
            side(p).actions++
            moved[idx(p)] = true
        }

        private fun takeTurn(p: Boolean, move: Move) {
            val s = side(p)
            CoreRules.startOfAction(this, p)
            if (CoreRules.recharge(this, p)) return // "X must recharge!": the rest turn after a recharge move
            if (!canAct(p, move) || !rulesAll { it.beforeMove(this, p, move) }) { CoreRules.interrupted(this, p); return }
            rulesFirst { it.moveBlocked(this, p, move) }?.let { why ->
                CoreRules.interrupted(this, p)
                events += attackEvent(p, move, failed = true)
                fx(p, "${mon(p).name} can't use ${move.name}${if (why.isBlank()) "" else " $why"}!")
                return
            }
            // R17C-3 (Adam's rule): a charge move charges on its first use; the user's very next action is the hit (CoreRules.forcedMove gives it),
            // and the charge is over after that action whatever it did
            val hitTurn = s.charging == move.id
            s.charging = null
            if (!hitTurn && CoreRules.isCharge(move)) {
                val skip = rulesAny { it.skipsCharge(this, p, move) } // Solar Beam in the sun ...: no charge turn, the move goes on at once
                if (!skip) { s.charging = move.id; fx(p, chargeText(p, move), EffectKind.CHARGE) }
                CoreRules.chargeRise(this, p, move) // Skull Bash / Meteor Beam / Electro Shot raise a stat on their first turn, charged or not
                if (!skip) return
            }
            val failed = perform(p, move)
            s.lastMove = move.id
            s.lastMoveFailed = failed
            rulesEach { it.afterMove(this, p, move, failed) }
        }

        /**
         * Runs [move] for [p] as an action NOW, like a move that was picked, but without a pick and without the checks that come before the
         * move (sleep / freeze / paralysis, `beforeMove`, `moveBlocked`, the charge turn) and without `afterMove` / the tracking: for moves that call
         * another move (Metronome, Sleep Talk, Copycat ...). Its Attack event and effects are written after the caller's own Attack event.
         * R17C FIX-A: the move's own clean-up runs through [BattleRules.afterCalledMove] (a called Spit Up ends the stockpile).
         * Returns true when it worked (it did not miss or fail).
         */
        fun useMove(p: Boolean, move: Move): Boolean {
            if (mon(p).hp <= 0 || mon(!p).hp <= 0) return false
            val failed = perform(p, move)
            rulesEach { it.afterCalledMove(this, p, move) } // R17C FIX-A: the clean-up of the move itself (a called Spit Up ends the stockpile)
            return !failed
        }

        /** The move itself: its Attack event first, the effects it caused after it. True = it failed or missed. */
        private fun perform(p: Boolean, move: Move): Boolean {
            val saved = out
            val savedActor = actor
            val savedMove = actingMove
            val buffer = ArrayList<GameEvent>()
            out = buffer
            actor = p
            actingMove = move
            val attack = if (move.damaging) damaging(p, move) else statusMove(p, move)
            actor = savedActor
            actingMove = savedMove
            out = saved
            out += attack
            out += buffer
            return attack.failed || attack.missed
        }

        /** The games' line for the charge turn of [move]. */
        private fun chargeText(p: Boolean, move: Move): String {
            val n = mon(p).name
            return when (move.id) {
                "solarbeam", "solarblade" -> "$n absorbed light!"
                "fly" -> "$n flew up high!"
                "bounce" -> "$n sprang up!"
                "dig" -> "$n burrowed its way under the ground!"
                "dive" -> "$n hid underwater!"
                "phantomforce", "shadowforce" -> "$n vanished instantly!"
                "skullbash" -> "$n tucked in its head!"
                "skyattack" -> "$n became cloaked in a harsh light!"
                "razorwind" -> "$n whipped up a whirlwind!"
                "freezeshock" -> "$n became cloaked in a freezing light!"
                "iceburn" -> "$n became cloaked in freezing air!"
                "meteorbeam" -> "$n is overflowing with space power!"
                "electroshot" -> "$n absorbed electricity!"
                "geomancy" -> "$n is absorbing power!"
                "skydrop" -> "$n took ${mon(!p).name} into the sky!"
                else -> "$n is charging up!"
            }
        }

        /**
         * Sleep / freeze / paralysis checks (poke-engine): a sleeper wakes with [Tuning.WAKE_CHANCE] each time it tries
         * to move and then acts at once; a frozen one thaws with [Tuning.FREEZE_THAW], or surely when it uses a thaw move.
         */
        private fun canAct(p: Boolean, move: Move): Boolean {
            val s = side(p)
            val n = mon(p).name
            when (s.status) {
                "slp" -> {
                    // R17C-3: a sleeper with a fixed count (Rest: volatiles["sleepTurns"]) sleeps through that many attempts and wakes at the next
                    val fixed = s.volatiles[CoreRules.SLEEP_TURNS]
                    val faster = rulesProduct { it.wakeMod(this, p) } // R17C-9a: Early Bird sleeps half as long
                    if (fixed != null && fixed > 0) s.volatiles[CoreRules.SLEEP_TURNS] = maxOf(0, fixed - maxOf(1, faster.toInt()))
                    val wakes = if (fixed != null) fixed <= 0 else rng.nextDouble() < Tuning.WAKE_CHANCE * faster
                    if (wakes) { s.status = null; s.volatiles.remove(CoreRules.SLEEP_TURNS); fx(p, "$n woke up!", EffectKind.STATUS_CURED, "slp") }
                    else if (move.sleepUsable) { /* Sleep Talk, Snore: usable while asleep (the sleep still counts down) */ }
                    else { fx(p, "$n is fast asleep.", EffectKind.CANT_MOVE, "slp"); return false }
                }
                "frz" -> if (move.id in Tuning.THAW_IF_USES || rng.nextDouble() < Tuning.FREEZE_THAW) { s.status = null; fx(p, "$n thawed out!", EffectKind.STATUS_CURED, "frz") }
                         else { fx(p, "$n is frozen solid!", EffectKind.CANT_MOVE, "frz"); return false }
                "par" -> if (rng.nextDouble() < Tuning.PARA_SKIP) { fx(p, "$n is fully paralyzed!", EffectKind.CANT_MOVE, "par"); return false }
            }
            return true
        }

        /** A damaging Fire move (or Scald and friends) that hit a frozen target thaws it. */
        private fun thawByHit(tp: Boolean, move: Move) {
            val s = side(tp)
            if (s.status != "frz" || mon(tp).hp <= 0) return
            if (move.type != Type.FIRE && move.id !in Tuning.THAW_IF_HIT_BY) return
            s.status = null
            fx(tp, "${mon(tp).name} thawed out!", EffectKind.STATUS_CURED, "frz")
        }

        private fun attackEvent(
            p: Boolean, move: Move, damage: Int = 0, hits: Int = 0, eff: Double = 1.0, crit: Boolean = false,
            missed: Boolean = false, failed: Boolean = false,
        ) = GameEvent.Attack(
            p, mon(p).name, mon(!p).name, move.name, if (move.id == Tuning.STRUGGLE) null else effectiveType(p, move), move.category,
            damage, hits, eff, crit, missed, failed, mon(!p).hp, maxHp(!p), mon(p).hp, maxHp(p),
            moveId = move.id, power = move.power,
        )

        /** R17C FIX-A: the type the move has in this hit (-ate abilities, Normalize, Weather Ball, Electrify ...): the Attack event animates it, not the move's own. */
        private fun effectiveType(p: Boolean, move: Move): Type = newHit(this, p, mon(p), side(p), mon(!p), side(!p), move).type

        /** A move is "aimed at the other side" unless it targets the user or the user's own side. */
        private fun aimedAtFoe(move: Move) = move.target !in SELF_TARGETS

        /** The checks every move has before its hit chance: `failReason`, `blocks`, `immune`. True = the move was stopped here (the hooks wrote the line). */
        private fun stoppedBefore(p: Boolean, move: Move): Boolean {
            rulesFirst { it.failReason(this, p, move) }?.let { fx(p, it); return true }
            if (!aimedAtFoe(move)) return false
            if (rulesAny { it.blocks(this, p, move) }) return true
            return rulesAny { it.immune(this, newHit(this, p, mon(p), side(p), mon(!p), side(!p), move)) }
        }

        private fun accuracyHit(p: Boolean, move: Move, hitIndex: Int = 0): Boolean {
            val h = newHit(this, p, mon(p), side(p), mon(!p), side(!p), move, hitIndex)
            if (move.ohko) { // the stages and the other accuracy changes don't count, but Lock-On / No Guard make it sure (the level rule is checked before)
                if (neverMisses(h)) return true
                return rng.nextDouble() * 100 < 30 + mon(p).level - mon(!p).level
            }
            val acc = rulesChain(move.accuracy?.toDouble()) { r, a -> r.accuracy(h, a) } ?: return true
            return rng.nextDouble() * 100 < acc * accStageMult(accuracyStage(h)) // R17C-9a: Unaware, Keen Eye leave a stage out
        }

        private fun damaging(p: Boolean, move: Move): GameEvent.Attack {
            val u = mon(p)
            val t = mon(!p)
            if (stoppedBefore(p, move)) return attackEvent(p, move, failed = true)
            if (move.ohko && t.level > u.level) { fx(p, "But it failed!"); return attackEvent(p, move, failed = true) }
            if (!accuracyHit(p, move)) return attackEvent(p, move, missed = true)
            rulesEach { it.beforeHit(this, p, move) } // R17C FIX-A: Spectral Thief takes the target's stat rises first

            val struggle = move.id == Tuning.STRUGGLE
            val hits = rulesFirst { it.hits(this, p, move) } ?: move.multihit?.let { multiHits(it) } ?: 1
            var total = 0; var dealt = 0; var done = 0; var crit = false; var eff = 1.0 // R17C-5: dealt = total + what a substitute took (drain / recoil)
            while (done < hits && t.hp > 0 && u.hp > 0) { // R17C-9a: a user that fainted from Rough Skin hits no more
                if (done > 0 && move.multiAccuracy && !accuracyHit(p, move, done)) break // R17C-4: Triple Axel, Population Bomb: every hit rolls its accuracy, the first miss ends the move
                val h = newHit(this, p, u, side(p), t, side(!p), move, done)
                val r = computeDamage(h, rng)
                eff = r.effectiveness
                if (eff == 0.0) break
                crit = crit || r.crit
                var d = r.damage
                d = maxOf(1, floor(d * damageScale(p)).toInt()) // R10-B22: each side's damage is scaled by its own answers-based factor
                d = rulesChain(d) { rule, x -> rule.takeDamage(this, !p, h, x) }
                d = d.coerceIn(0, t.hp)
                val sub = absorbed
                t.hp -= d; total += d; dealt += d + sub; absorbed = 0; done++
                if (d + sub > 0) lastTaken[idx(!p)] = TakenHit(h.category, d + sub, p, move.id) // R17C FIX-A: a substitute's share counts for Counter & co
                if (d > 0) {
                    taken[idx(!p)] += d
                    hurtBy[idx(!p)] = true
                    side(!p).timesHit++
                    rulesEach { rule -> rule.afterDamage(this, !p, h, d) }
                }
            }
            val hit = eff > 0
            if (hit && u.hp > 0) { // R17C-9a: a user that fainted from Rough Skin drains and takes recoil no more
                if (!MoveControl.healBlocked(this, p)) move.drain?.let { (a, b) -> drain(p, maxOf(1, dealt * a / b), "${u.name} drained HP from ${t.name}!") } // R17C-8: Heal Block stops the drain
                if (struggle) hurt(p, maxOf(1, maxHp(p) / 4), "${u.name} is damaged by recoil!", EffectKind.RECOIL, source = Indirect.STRUGGLE)
                else move.recoil?.let { (a, b) -> hurt(p, maxOf(1, dealt * a / b), "${u.name} is damaged by recoil!", EffectKind.RECOIL, source = Indirect.RECOIL) }
            }
            if (move.selfDestruct && (hit || !move.selfDestructIfHit)) { u.hp = 0 } // R17C FIX-A: Final Gambit ("ifHit") needs a hit: an immune target does not take the user down
            if (hit && u.hp > 0) move.selfBoosts?.let { boost(p, it, byFoe = false) }
            if (hit && total > 0) thawByHit(!p, move)
            if (hit) rulesEach { it.afterHit(this, p, move, total) }
            if (hit && t.hp > 0) for (sec in move.allSecondaries) {
                val chance = rulesChain(sec.chance) { rule, c -> rule.secondaryChance(newHit(this, p, u, side(p), t, side(!p), move), sec, c) } // R17C-9a: Serene Grace, Sheer Force, Shield Dust
                if (rng.nextInt(100) < chance) {
                    sec.status?.let { setStatus(!p, it, move.type, byFoe = true, quiet = true) }
                    sec.boosts?.let { boost(!p, it, byFoe = true) }
                    if (u.hp > 0) sec.selfBoosts?.let { boost(p, it, byFoe = false) }
                    rulesEach { it.secondary(this, p, move, sec) }
                }
            }
            return attackEvent(p, move, total, done, eff, crit)
        }

        private fun multiHits(r: IntRange): Int {
            if (r.first == r.last) return r.first
            if (r == 2..5) { val x = rng.nextInt(100); return if (x < 35) 2 else if (x < 70) 3 else if (x < 85) 4 else 5 }
            return rng.nextInt(r.first, r.last + 1)
        }

        private fun statusMove(p: Boolean, move: Move): GameEvent.Attack {
            val self = move.target in SELF_TARGETS
            if (stoppedBefore(p, move)) return attackEvent(p, move, failed = true)
            if (move.id in Tuning.POWDER_MOVES && Type.GRASS in types(!p)) {
                fx(p, "It doesn't affect ${mon(!p).name}…")
                return attackEvent(p, move, failed = true)
            }
            if (!self && move.accuracy != null && !accuracyHit(p, move)) return attackEvent(p, move, missed = true)
            val before = out.size
            val handled = rulesFirst { it.statusMove(this, p, move) }
            if (handled != null) {
                if (!handled && out.size == before) fx(p, "But it failed!")
                return attackEvent(p, move, failed = !handled)
            }
            val tp = if (self) p else !p
            reasonShown = false
            var parts = 0; var applied = false
            move.status?.let { parts++; if (setStatus(tp, it, move.type, byFoe = tp != p)) applied = true }
            move.boosts?.let { parts++; if (boost(tp, it, byFoe = tp != p)) applied = true }
            move.selfBoosts?.let { parts++; if (boost(p, it, byFoe = false)) applied = true }
            move.heal?.let { (a, b) ->
                parts++
                if (mon(p).hp < maxHp(p)) { heal(p, maxOf(1, maxHp(p) * a / b), "${mon(p).name} regained health!", EffectKind.HEAL); applied = true }
            }
            if (parts == 0) fx(p, "But nothing happened.")
            else if (!applied && !reasonShown) fx(p, "But it failed!")
            return attackEvent(p, move, failed = !applied)
        }

        /** R17C-9b: the type rules ([canHaveStatus]); Corrosion lets the other side poison a Steel or Poison type ([AbilitiesMore.corrodes]). */
        private fun canHave(status: String, tp: Boolean, moveType: Type, byFoe: Boolean): Boolean =
            canHaveStatus(types(tp), status, moveType) || (byFoe && AbilitiesMore.corrodes(this, !tp, status))

        /**
         * Gives [tp] the non-volatile [status] when it can have it: it has none, is alive, its types allow it and no rule set blocks it
         * ([BattleRules.blockStatus], asked with [byFoe]). A blocked status MOVE shows the reason; a [quiet] attempt (a secondary chance) fails
         * without a word. Writes the "was burned!" line, then asks [BattleRules.afterStatus]. True = it was given.
         */
        fun setStatus(tp: Boolean, status: String, moveType: Type, byFoe: Boolean = true, quiet: Boolean = false): Boolean {
            val s = side(tp)
            if (s.status != null || mon(tp).hp <= 0 || !canHave(status, tp, moveType, byFoe)) return false
            val why = rulesFirst { it.blockStatus(this, tp, status, byFoe) }
            if (why != null) { showReason(tp, why, status, quiet); return false }
            s.status = status
            s.statusTurns = 0
            val n = mon(tp).name
            fx(tp, when (status) {
                "par" -> "$n was paralyzed!"
                "brn" -> "$n was burned!"
                "psn" -> "$n was poisoned!"
                "tox" -> "$n was badly poisoned!"
                "slp" -> "$n fell asleep!"
                else -> "$n was frozen solid!"
            }, EffectKind.STATUS_APPLIED)
            rulesEach { it.afterStatus(this, tp, status, byFoe) }
            return true
        }

        /** A hook blocked something: [why] is the line to show ("" = the hook wrote its own); [quiet] shows nothing. */
        private fun showReason(tp: Boolean, why: String, key: String, quiet: Boolean) {
            if (quiet) return
            reasonShown = true
            if (why.isNotEmpty()) fx(tp, why, EffectKind.BLOCKED, key, label = "Blocked")
        }

        /**
         * Starts the lasting condition [key] on [tp] for [turns] (stored in [BattleSide.volatiles]; what the number means is the rule's business)
         * unless it is already there, [tp] has fainted or a rule set blocks it ([BattleRules.blockVolatile]). A non-null [text] is written as a
         * [EffectKind.VOLATILE] line (stages +1) with the float [label]. True = it started.
         */
        fun addVolatile(tp: Boolean, key: String, turns: Int, byFoe: Boolean, text: String? = null, label: String? = null, quiet: Boolean = false): Boolean {
            val s = side(tp)
            if (mon(tp).hp <= 0 || key in s.volatiles) return false
            val why = rulesFirst { it.blockVolatile(this, tp, key, byFoe) }
            if (why != null) { showReason(tp, why, key, quiet); return false }
            s.volatiles[key] = turns
            if (text != null) fx(tp, text, EffectKind.VOLATILE, key, 1, label = label)
            return true
        }

        /** Ends the lasting condition [key] of [tp]; a non-null [text] is written as a [EffectKind.VOLATILE] line (stages -1, no float). True = it was there. */
        fun removeVolatile(tp: Boolean, key: String, text: String? = null): Boolean {
            if (side(tp).volatiles.remove(key) == null) return false
            if (text != null) fx(tp, text, EffectKind.VOLATILE, key, -1)
            return true
        }

        /**
         * Applies stage changes to [tp] (`atk def spa spd spe accuracy evasion` -> signed amount); [byFoe] = the other side causes them (so
         * Clear Body / Mist / Defiant can tell). Each change passes the chained [BattleRules.stageChange] (0 = blocked, the hook wrote its
         * line); a stage that moved is announced and passed to [BattleRules.afterStageChange]. True = at least one stage really moved.
         */
        fun boost(tp: Boolean, changes: Map<String, Int>, byFoe: Boolean): Boolean {
            var changed = false
            for ((key, v) in changes) {
                val i = BOOST_KEYS.indexOf(key)
                if (i < 0 || v == 0) continue
                val delta = rulesChain(v) { r, d -> r.stageChange(this, tp, key, d, byFoe) }
                if (delta == 0) { reasonShown = true; continue }
                val b = side(tp).boosts
                val label = "${mon(tp).name}'s ${BOOST_LABELS[i]}"
                val limit = moveChoice.stageLimit(i) // 6; accuracy / evasion stop at ±2 with a smart move choice (W9)
                if (delta > 0 && b[i] >= limit) { fx(tp, "$label won't go any higher!", EffectKind.STAT_LIMIT, key, 1); continue }
                if (delta < 0 && b[i] <= -limit) { fx(tp, "$label won't go any lower!", EffectKind.STAT_LIMIT, key, -1); continue }
                val before = b[i]
                b[i] = if (delta > 0) minOf(b[i] + delta, limit) else maxOf(b[i] + delta, -limit)
                val applied = b[i] - before
                changed = true
                fx(tp, "$label " + when {
                    delta == 1 -> "rose!"
                    delta == 2 -> "rose sharply!"
                    delta > 2 -> "rose drastically!"
                    delta == -1 -> "fell!"
                    delta == -2 -> "harshly fell!"
                    else -> "severely fell!"
                }, EffectKind.STAT_CHANGE, key, applied)
                rulesEach { it.afterStageChange(this, tp, key, applied, byFoe) }
            }
            return changed
        }

        private fun endOfTurn(p: Boolean) {
            val m = mon(p)
            val s = side(p)
            if (m.hp <= 0) return
            when (s.status) {
                "brn" -> hurtEvent(p, maxOf(1, maxHp(p) / 16), "${m.name} is hurt by its burn!", Indirect.BURN)
                "psn" -> hurtEvent(p, maxOf(1, maxHp(p) / 8), "${m.name} is hurt by poison!", Indirect.POISON)
                "tox" -> {
                    s.statusTurns = minOf(15, s.statusTurns + 1)
                    hurtEvent(p, maxOf(1, maxHp(p) * s.statusTurns / 16), "${m.name} is hurt by poison!", Indirect.POISON)
                }
            }
            checkFaint(p)
        }

        private fun hurtEvent(p: Boolean, amount: Int, text: String, source: String) {
            val n = rulesChain(amount) { rule, x -> rule.indirectDamage(this, p, source, x) } // R17C-9a: Magic Guard, Heatproof
            if (n <= 0) return
            val before = mon(p).hp
            mon(p).hp = maxOf(0, before - n)
            events += GameEvent.Effect(p, text, mon(p).hp, maxHp(p), side(p).status, EffectKind.STATUS_DAMAGE, amount = before - mon(p).hp)
        }
    }
}
