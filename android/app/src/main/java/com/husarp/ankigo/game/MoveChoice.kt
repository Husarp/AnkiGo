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
 *    R17C-1: the fight is a [BattleView]: a move a rule says [BattleRules.moveBlocked] is not picked (a side with nothing else still tries
 *    one and fails at the action), a move a rule says [BattleRules.forcedMove] is used without a pick, types and stats are the effective
 *    ones, and a status move first asks [BattleRules.useful]. R17C-3: a move in [CoreRules.NEVER_PICKED] is left out in every mode (Random too)
 *    while the Pokémon has another move; with only such moves the Main uses Struggle and the wild one one of them (it does nothing).
 * 4. SMARTEST picks the attack by [expectedDamage]: the strongest with [Tuning.SMARTEST_BEST_SHARE], else the second strongest
 *    (a tie is broken by chance; a single attack is simply that one). A move that makes its user faint (Explosion) is left out
 *    unless it is the only attack that hurts. R17C FIX-A: a charge or recharge move needs two actions per hit, so its expected damage is halved
 *    (not a charge move whose charge turn a rule skips, like Solar Beam in the sun).
 * R17C FIX-A: the answers of [BattleRules.useful] combine as "any false wins" ([rulesUseful]); RANDOM's Struggle fallback uses the same "can hurt"
 * test as SMART (RANDOM itself still picks among all moves, also one that fails); a move in [CoreRules.MAIN_NEVER_PICKED] (Poltergeist: wild
 * Pokémon hold no item) is left out for the Main in every mode, like [CoreRules.NEVER_PICKED].
 */
object MoveChooser {
    /** The Main's move against the battle's wild one ([battle].player is the Main's side). */
    fun pickMain(choice: MoveChoice, data: GameData, battle: Battle, main: Mon, rng: Random): Move =
        pick(choice, SimpleView.of(data, battle, main), true, rng)

    /** The wild one's move against [main] (the Main on the field). */
    fun pickWild(choice: MoveChoice, data: GameData, battle: Battle, main: Mon, rng: Random): Move =
        pick(choice, SimpleView.of(data, battle, main), false, rng)

    /**
     * [attacker] with its battle side [aSide] and full HP [aMaxHp] picks a move against [defender] / [dSide];
     * [attackerIsMain] only matters when nothing can hurt the defender (Struggle for the Main, a random move for the wild one).
     * The fight is just these two Pokémon and sides (a [SimpleView] without a battle); use the [BattleView] overload inside a fight.
     */
    fun pick(
        choice: MoveChoice, data: GameData, rng: Random,
        attacker: Mon, aSide: BattleSide, aMaxHp: Int, defender: Mon, dSide: BattleSide, attackerIsMain: Boolean,
    ): Move {
        val view = if (attackerIsMain) SimpleView(data, attacker, defender, aSide, dSide, mainMaxHp = aMaxHp)
        else SimpleView(data, defender, attacker, dSide, aSide, wildMaxHp = aMaxHp)
        return pick(choice, view, attackerIsMain, rng)
    }

    /**
     * The move the side [attackerIsMain] (true = the Main) uses in the fight [view]. A rule's [BattleRules.forcedMove] comes first; the
     * moves it may pick from are its effective [BattleView.moves] without the ones a rule says [BattleRules.moveBlocked].
     */
    fun pick(choice: MoveChoice, view: BattleView, attackerIsMain: Boolean, rng: Random): Move {
        val p = attackerIsMain
        rulesFirst { it.forcedMove(view, p) }?.let { return it }
        val everything = view.moves(p)
        // R17C-3: moves that mean nothing in a 1-vs-1 wild fight are never picked (CoreRules.NEVER_PICKED). With only such moves the Main
        // uses Struggle and the wild one uses one of them ("But nothing happened.")
        val all = everything.filter { it.id !in CoreRules.NEVER_PICKED && !(p && it.id in CoreRules.MAIN_NEVER_PICKED) }
        if (all.isEmpty()) return if (p) BattleEngine.struggle(view.data) else everything[rng.nextInt(everything.size)]
        val known = all.filter { m -> rulesFirst { it.moveBlocked(view, p, m) } == null }
        if (choice == MoveChoice.RANDOM) {
            return if (p) BattleEngine.pickPlayerMove(view, known, rng) else (known.ifEmpty { all }).let { it[rng.nextInt(it.size)] }
        }
        // R17C-4: a damaging move a rule says would surely fail now (Dream Eater on an awake target ...) is not "hurting"
        val hurting = known.filter { it.damaging && canHurt(view, p, it) && rulesUseful(view, p, it) != false }
        if (hurting.isEmpty() && p) return BattleEngine.struggle(view.data)
        val useful = known.filter { !it.damaging && statusUseful(choice, it, view, p) }
        if (hurting.isEmpty()) {
            val pool = useful.ifEmpty { known }.ifEmpty { all }
            return pool[rng.nextInt(pool.size)]
        }
        if (useful.isNotEmpty() && rng.nextDouble() >= Tuning.SMART_DAMAGE_SHARE) return useful[rng.nextInt(useful.size)]
        return if (choice == MoveChoice.SMARTEST) strongest(view, p, rng, hurting) else hurting[rng.nextInt(hurting.size)]
    }

    /** True when [move] could do damage to [defender] (not immune; a one-hit KO needs a target that is not above the user). */
    fun canHurt(move: Move, attacker: Mon, defender: Mon, defenderTypes: List<Type>): Boolean =
        TypeChart.eff(move.type, defenderTypes) > 0 && !(move.ohko && defender.level > attacker.level)

    /** [canHurt] in a fight: the move's type for this hit against the target's effective types. */
    fun canHurt(view: BattleView, p: Boolean, move: Move): Boolean {
        val h = BattleEngine.newHit(view, p, view.mon(p), view.side(p), view.mon(!p), view.side(!p), move)
        val eff = rulesChain(TypeChart.eff(h.type, view.types(!p))) { r, e -> r.effectiveness(h, e) }
        return eff > 0 && !(move.ohko && view.mon(!p).level > view.mon(p).level)
    }

    /** True when the status move [move] used by [attacker] could still change something (see the class text). */
    fun statusUseful(
        choice: MoveChoice, move: Move, data: GameData,
        attacker: Mon, aSide: BattleSide, aMaxHp: Int, defender: Mon, dSide: BattleSide,
    ): Boolean = statusUseful(choice, move, SimpleView(data, attacker, defender, aSide, dSide, mainMaxHp = aMaxHp), true)

    /**
     * [statusUseful] in a fight: the side [p] is the user. The rules' [BattleRules.useful] answers first ([rulesUseful]: any false wins, else the
     * first non-null); without one the engine's own rule from the class text decides.
     */
    fun statusUseful(choice: MoveChoice, move: Move, view: BattleView, p: Boolean): Boolean {
        val self = move.target in BattleEngine.SELF_TARGETS
        // R17C FIX-A: a substitute stops the other side's status moves (sound moves and moves that bypass it go through)
        if (SideMoves.aimedAtFoe(view, p, move) && SideMoves.SUBSTITUTE in view.side(!p).volatiles &&
            !SideMoves.passesSubstitute(move) && !Abilities.infiltrates(view, p)) return false
        rulesUseful(view, p, move)?.let { return it }
        val attacker = view.mon(p)
        val aSide = view.side(p)
        if (move.id in Tuning.POWDER_MOVES && Type.GRASS in view.types(!p)) return false
        val tp = if (self) p else !p
        val target = view.mon(tp)
        val tSide = view.side(tp)
        move.status?.let { s ->
            if (tSide.status == null && target.hp > 0 && BattleEngine.canHaveStatus(view.types(tp), s, move.type)) return true
        }
        if (move.boosts?.let { canMoveStage(choice, tSide, it) } == true) return true
        if (move.selfBoosts?.let { canMoveStage(choice, aSide, it) } == true) return true
        if (move.heal != null && attacker.hp < view.maxHp(p)) return true
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
    private fun strongest(view: BattleView, p: Boolean, rng: Random, attacks: List<Move>): Move {
        val pool = attacks.filter { !it.selfDestruct }.ifEmpty { attacks }
        if (pool.size == 1) return pool[0]
        val ranked = pool.shuffled(rng).map { it to expectedDamage(view, p, it) }.sortedByDescending { it.second }
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
    fun expectedDamage(data: GameData, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move): Double =
        expectedDamage(SimpleView(data, attacker, defender, aSide, dSide), true, move)

    /** [expectedDamage] in a fight: the side [p] attacks the other one with [move]. */
    fun expectedDamage(view: BattleView, p: Boolean, move: Move): Double {
        val hit = BattleEngine.damage(
            view.data, view.mon(p), view.side(p), view.mon(!p), view.side(!p), move, UNUSED_RNG,
            forceCrit = false, forceRoll = AVERAGE_ROLL, view = view, attackerIsMain = p,
        )
        return hit.damage * averageHits(move.multihit) * hitChance(view, p, move) * actionsFactor(view, p, move)
    }

    /**
     * R17C FIX-A: a move with a charge turn (unless a rule skips it) or a rest turn after the hit needs two actions per hit, so it deals half per
     * action; every second action of a SMARTEST side that prefers it would otherwise deal nothing.
     */
    private fun actionsFactor(view: BattleView, p: Boolean, move: Move): Double = when {
        CoreRules.isRecharge(move) -> 0.5
        CoreRules.isCharge(move) && view.side(p).charging != move.id && !BattleEngine.skipsCharge(view, p, move) -> 0.5
        else -> 1.0
    }

    /** The engine's multi-hit counts: 2..5 hits as 35 / 35 / 15 / 15 %, any other range evenly. */
    private fun averageHits(r: IntRange?): Double = when {
        r == null -> 1.0
        r == 2..5 -> 3.1
        else -> (r.first + r.last) / 2.0
    }

    private fun hitChance(view: BattleView, p: Boolean, move: Move): Double {
        val attacker = view.mon(p)
        val defender = view.mon(!p)
        val h = BattleEngine.newHit(view, p, attacker, view.side(p), defender, view.side(!p), move)
        if (move.ohko) return if (BattleEngine.neverMisses(h)) 1.0 else (30 + attacker.level - defender.level).coerceIn(0, 100) / 100.0
        val acc = rulesChain(move.accuracy?.toDouble()) { r, a -> r.accuracy(h, a) } ?: return 1.0
        return (acc * BattleEngine.accStageMult(BattleEngine.accuracyStage(h))).coerceIn(0.0, 100.0) / 100.0 // R17C-9a: Unaware, Keen Eye
    }
}
