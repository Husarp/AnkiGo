package com.husarp.ankigo.game

import kotlin.random.Random

/**
 * Round 10 (W9): how a Pokémon picks its move in a round. One setting for BOTH sides (your Main and the wild one run the
 * same [MoveChooser]); saved in the game (Settings.moveChoice).
 * - RANDOM: the old rule, one random move of the four (Struggle for the Main when none of its moves can hurt the wild one).
 * - SMART (the default, also for saves from before this setting): mostly attacks, status moves only while they still change something.
 * - SMARTEST: SMART, and among the attacks it prefers the stronger ones.
 * Pure Kotlin.
 */
enum class MoveChoice(val label: String) {
    RANDOM("Random"), SMART("Smart"), SMARTEST("Smartest");

    /** How far a stage may go: attack, defense, ... speed ±6 always; accuracy and evasion ±6 in RANDOM, [Tuning.SMART_ACC_EVA_LIMIT] in SMART / SMARTEST. */
    fun stageLimit(boostIndex: Int): Int = if (boostIndex >= 5 && this != RANDOM) Tuning.SMART_ACC_EVA_LIMIT else 6

    companion object {
        /** A saved name; missing or unknown = SMART. */
        fun parse(name: String?): MoveChoice = entries.firstOrNull { it.name == name } ?: SMART
    }
}

/**
 * Picks the move one side uses this round. RANDOM calls the old code (same moves, same rng use). SMART and SMARTEST:
 * 1. A damaging move can "hurt" when the target is not immune to its type (effectiveness above 0) and, for a one-hit KO,
 *    when the target is not above the user's level (it would just fail). Charge moves count as damaging.
 * 2. A status move is "useful" when it can still change something: a status the target can take and does not have
 *    (type immunities as in the fight), a stage that is not at its limit yet (accuracy / evasion: ±[Tuning.SMART_ACC_EVA_LIMIT]; any
 *    one of a move's stage changes is enough), a heal below full HP. A move with none of these effects (weather, Splash,
 *    anything the engine does not model) is never useful; a powder move is not useful against a Grass type.
 * 3. The Main with nothing that can hurt: Struggle (all modes). Otherwise it uses a move that hurts [Tuning.SMART_DAMAGE_SHARE]
 *    of the time when it also has a useful status move (always when it has none), else one useful status move.
 *    The wild one with no move that hurts uses a useful status move, and with nothing useful a random move of all its moves.
 * 4. SMARTEST picks the attack by [expectedDamage]: the strongest with [Tuning.SMARTEST_BEST_SHARE], else the second strongest
 *    (a tie is broken by chance; a single attack is simply that one). A move that makes its user faint (Explosion) is left out
 *    unless it is the only attack that hurts.
 */
object MoveChooser {
    /** The Main's move against the battle's wild one ([battle].player is the Main's side). */
    fun pickMain(choice: MoveChoice, data: GameData, battle: Battle, main: Mon, rng: Random): Move =
        pick(choice, data, rng, main, battle.player, StatCalc.maxHp(data, main), battle.wild, battle.enemy, attackerIsMain = true)

    /** The wild one's move against [main] (the Main on the field). */
    fun pickWild(choice: MoveChoice, data: GameData, battle: Battle, main: Mon, rng: Random): Move =
        pick(choice, data, rng, battle.wild, battle.enemy, battle.maxWildHp(data), main, battle.player, attackerIsMain = false)

    /**
     * [attacker] with its battle side [aSide] and full HP [aMaxHp] picks a move against [defender] / [dSide];
     * [attackerIsMain] only matters when nothing can hurt the defender (Struggle for the Main, a random move for the wild one).
     */
    fun pick(
        choice: MoveChoice, data: GameData, rng: Random,
        attacker: Mon, aSide: BattleSide, aMaxHp: Int, defender: Mon, dSide: BattleSide, attackerIsMain: Boolean,
    ): Move {
        if (choice == MoveChoice.RANDOM) {
            return if (attackerIsMain) BattleEngine.pickPlayerMove(data, attacker, defender, rng) else BattleEngine.pickWildMove(data, attacker, rng)
        }
        val known = BattleEngine.knownMoves(data, attacker)
        val dTypes = data.sp(defender.species).types
        val hurting = known.filter { it.damaging && canHurt(it, attacker, defender, dTypes) }
        if (hurting.isEmpty() && attackerIsMain) return BattleEngine.struggle(data)
        val useful = known.filter { !it.damaging && statusUseful(choice, it, data, attacker, aSide, aMaxHp, defender, dSide) }
        if (hurting.isEmpty()) {
            val pool = useful.ifEmpty { known }
            return pool[rng.nextInt(pool.size)]
        }
        if (useful.isNotEmpty() && rng.nextDouble() >= Tuning.SMART_DAMAGE_SHARE) return useful[rng.nextInt(useful.size)]
        return if (choice == MoveChoice.SMARTEST) strongest(data, rng, hurting, attacker, aSide, defender, dSide)
        else hurting[rng.nextInt(hurting.size)]
    }

    /** True when [move] could do damage to [defender] (not immune; a one-hit KO needs a target that is not above the user). */
    fun canHurt(move: Move, attacker: Mon, defender: Mon, defenderTypes: List<Type>): Boolean =
        TypeChart.eff(move.type, defenderTypes) > 0 && !(move.ohko && defender.level > attacker.level)

    /** True when the status move [move] used by [attacker] could still change something (see the class text). */
    fun statusUseful(
        choice: MoveChoice, move: Move, data: GameData,
        attacker: Mon, aSide: BattleSide, aMaxHp: Int, defender: Mon, dSide: BattleSide,
    ): Boolean {
        val self = move.target in BattleEngine.SELF_TARGETS
        val dTypes = data.sp(defender.species).types
        if (move.id in Tuning.POWDER_MOVES && Type.GRASS in dTypes) return false
        val target = if (self) attacker else defender
        val tSide = if (self) aSide else dSide
        move.status?.let { s ->
            if (tSide.status == null && target.hp > 0 && BattleEngine.canHaveStatus(data.sp(target.species).types, s, move.type)) return true
        }
        if (move.boosts?.let { canMoveStage(choice, tSide, it) } == true) return true
        if (move.selfBoosts?.let { canMoveStage(choice, aSide, it) } == true) return true
        if (move.heal != null && attacker.hp < aMaxHp) return true
        return false
    }

    /** True when at least one of the stage [changes] would really move a stage of [side]. */
    private fun canMoveStage(choice: MoveChoice, side: BattleSide, changes: Map<String, Int>): Boolean = changes.any { (key, v) ->
        val i = BattleEngine.BOOST_KEYS.indexOf(key)
        when {
            i < 0 || v == 0 -> false
            v > 0 -> side.boosts[i] < choice.stageLimit(i)
            else -> side.boosts[i] > -choice.stageLimit(i)
        }
    }

    /** SMARTEST: the strongest of [attacks] with [Tuning.SMARTEST_BEST_SHARE], else the second strongest; no self-destruct move while another attack is left. */
    private fun strongest(data: GameData, rng: Random, attacks: List<Move>, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide): Move {
        val pool = attacks.filter { !it.selfDestruct }.ifEmpty { attacks }
        if (pool.size == 1) return pool[0]
        val ranked = pool.shuffled(rng).map { it to expectedDamage(data, attacker, aSide, defender, dSide, it) }.sortedByDescending { it.second }
        return (if (rng.nextDouble() < Tuning.SMARTEST_BEST_SHARE) ranked[0] else ranked[1]).first
    }

    private val UNUSED_RNG = Random(0) // BattleEngine.damage draws nothing when the crit and the roll are given
    private const val AVERAGE_ROLL = (Tuning.ROLL_MIN + 100) / 2

    /**
     * How much HP [move] is expected to take off [defender] on average: the engine's own damage (type chart, STAB, the real
     * attack and defense with their stages, burn) at the average damage roll and no crit, times the average number of hits,
     * times the chance to hit (accuracy with the accuracy / evasion stages). Fixed-damage moves are what the engine gives
     * them: the user's level (Seismic Toss), the fixed number, half the target's HP (Super Fang); a one-hit KO is the target's
     * HP times its chance to hit (30 % plus the level difference).
     */
    fun expectedDamage(data: GameData, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move): Double {
        val hit = BattleEngine.damage(data, attacker, aSide, defender, dSide, move, UNUSED_RNG, forceCrit = false, forceRoll = AVERAGE_ROLL)
        return hit.damage * averageHits(move.multihit) * hitChance(attacker, aSide, defender, dSide, move)
    }

    /** The engine's multi-hit counts: 2..5 hits as 35 / 35 / 15 / 15 %, any other range evenly. */
    private fun averageHits(r: IntRange?): Double = when {
        r == null -> 1.0
        r == 2..5 -> 3.1
        else -> (r.first + r.last) / 2.0
    }

    private fun hitChance(attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move): Double {
        if (move.ohko) return (30 + attacker.level - defender.level).coerceIn(0, 100) / 100.0
        val acc = move.accuracy ?: return 1.0
        val stage = (aSide.boosts[5] - dSide.boosts[6]).coerceIn(-6, 6)
        return (acc * BattleEngine.accStageMult(stage)).coerceIn(0.0, 100.0) / 100.0
    }
}
