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
 * No abilities, held-item battle effects, weather, terrain, volatile statuses, recharge moves.
 * Charge moves (Solar Beam, Fly...) only charge the first time they are picked in a fight (Tuning.CHARGE_MOVES).
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
     * secondary effects, recoil, drain). Unlike [playRound] the Main does not act, nothing counts as a round (battle.rounds stays)
     * and there is no end-of-turn burn / poison damage. A charge move that would only charge is left out of the pick while the wild
     * one has another move (see [pickSwitchHitMove]). Returns the Attack / Effect / Fainted events; resolves nothing.
     */
    fun switchHit(
        data: GameData, battle: Battle, incoming: Mon, rng: Random,
        moveChoice: MoveChoice = MoveChoice.RANDOM, power: Double = Tuning.SWITCH_HIT_POWER,
    ): List<GameEvent> {
        val r = Round(data, battle, incoming, 0.0, power.coerceAtLeast(0.0), rng, moveChoice)
        r.wildStrikes()
        return r.events
    }

    /**
     * The move of the wild one's free hit: [MoveChooser.pickWild], but among the moves that really attack now. A charge move not yet
     * charged in this fight would only spend the hit charging up, so it is left out while the wild one has any other move; with
     * nothing else (every move is such a charge move) the choice is made among all of them and the wild one charges (no damage).
     * With no charge move in play this is exactly [MoveChooser.pickWild] (same picks, same rng use).
     */
    internal fun pickSwitchHitMove(data: GameData, battle: Battle, incoming: Mon, rng: Random, choice: MoveChoice): Move {
        val known = knownMoves(data, battle.wild)
        val usable = known.filterNot { it.id in Tuning.CHARGE_MOVES && it.id !in battle.enemy.charged }
        if (usable.isEmpty() || usable.size == known.size) return MoveChooser.pickWild(choice, data, battle, incoming, rng)
        val w = battle.wild
        val reduced = Mon(w.uid, w.species, w.name, w.level, w.xp, w.ivs, w.evs, w.nature, w.gender, w.shiny, w.ability, usable.map { it.id }.toMutableList(), w.hp)
        return MoveChooser.pick(choice, data, rng, reduced, battle.enemy, battle.maxWildHp(data), incoming, battle.player, attackerIsMain = false)
    }

    /**
     * One hit's damage (before Ankimon's multiplier): Gen 5 formula
     * base = floor(floor(floor(2L/5+2) * P * A / D) / 50) + 2, then crit x1.5, random 85..100 %, STAB 1.5, type, burn 0.5 (physical).
     * A/D use stat stages (a crit ignores the attacker's negative and the defender's positive stages).
     * [forceCrit]/[forceRoll] fix the random parts for tests.
     * Fixed-damage moves (level, fixed, half HP, OHKO) skip the formula and the crit but still give 0 against an immune type.
     */
    fun damage(
        data: GameData, attacker: Mon, aSide: BattleSide, defender: Mon, dSide: BattleSide, move: Move,
        rng: Random, forceCrit: Boolean? = null, forceRoll: Int? = null,
    ): DamageResult {
        val aSp = data.sp(attacker.species)
        val dSp = data.sp(defender.species)
        val struggle = move.id == Tuning.STRUGGLE
        val eff = if (struggle) 1.0 else TypeChart.eff(move.type, dSp.types)

        val fixed = when {
            move.levelDamage -> attacker.level
            move.fixedDamage != null -> move.fixedDamage
            move.halfHp -> maxOf(1, defender.hp / 2)
            move.ohko -> defender.hp
            else -> null
        }
        if (fixed != null) return DamageResult(if (eff > 0) fixed else 0, eff, false)

        val physical = move.category == Category.PHYSICAL
        val crit = forceCrit ?: (Tuning.CRITS && (move.willCrit || rng.nextDouble() < Tuning.CRIT_CHANCE[move.critRatio.coerceIn(0, 4)]))
        val aStats = StatCalc.stats(aSp, attacker)
        val dStats = StatCalc.stats(dSp, defender)
        var aStage = aSide.boosts[if (physical) 0 else 2]
        var dStage = dSide.boosts[if (physical) 1 else 3]
        if (crit) { aStage = maxOf(aStage, 0); dStage = minOf(dStage, 0) }
        val a = maxOf(1, floor((if (physical) aStats.atk else aStats.spa) * stageMult(aStage)).toInt())
        val d = maxOf(1, floor((if (physical) dStats.def else dStats.spd) * stageMult(dStage)).toInt())

        val power = if (move.power > 0) move.power else Tuning.UNKNOWN_POWER
        var dmg = floor(floor(2.0 * attacker.level / 5 + 2).toInt() * power * a / d.toDouble()).toInt() / 50 + 2
        if (crit) dmg = floor(dmg * Tuning.CRIT_MULT).toInt()
        val roll = forceRoll ?: rng.nextInt(Tuning.ROLL_MIN, 101)
        dmg = dmg * roll / 100
        if (!struggle && move.type in aSp.types) dmg = floor(dmg * Tuning.STAB).toInt()
        dmg = floor(dmg * eff).toInt()
        if (physical && aSide.status == "brn") dmg /= 2
        if (eff > 0) dmg = maxOf(1, dmg) else dmg = 0
        return DamageResult(dmg, eff, crit)
    }

    /** Stat stage multiplier for atk/def/spa/spd/spe: (2+s)/2 or 2/(2-s). */
    fun stageMult(stage: Int): Double = if (stage >= 0) (2.0 + stage) / 2.0 else 2.0 / (2.0 - stage)

    /** Accuracy/evasion stage multiplier: (3+s)/3 or 3/(3-s). */
    fun accStageMult(stage: Int): Double = if (stage >= 0) (3.0 + stage) / 3.0 else 3.0 / (3.0 - stage)

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

    /** State of one round; keeps the two sides symmetric by addressing them with [p] = "the main's side". */
    private class Round(
        val data: GameData, val battle: Battle, val main: Mon, val mult: Double, val enemyPower: Double, val rng: Random,
        val moveChoice: MoveChoice = MoveChoice.RANDOM,
    ) {
        val wild = battle.wild
        val events = ArrayList<GameEvent>()
        private var out: MutableList<GameEvent> = events
        private val maxHp = intArrayOf(battle.maxWildHp(data), StatCalc.maxHp(data, main)) // [0] wild, [1] main
        private val fainted = BooleanArray(2)

        private fun mon(p: Boolean) = if (p) main else wild
        private fun side(p: Boolean) = if (p) battle.player else battle.enemy
        private fun maxHpOf(p: Boolean) = maxHp[if (p) 1 else 0]
        private fun types(p: Boolean) = data.sp(mon(p).species).types

        private fun fx(p: Boolean, text: String, kind: EffectKind = EffectKind.OTHER, stat: String? = null, stages: Int = 0, amount: Int = 0) {
            out += GameEvent.Effect(p, text, mon(p).hp, maxHpOf(p), side(p).status, kind, stat, stages, amount)
        }

        private fun checkFaint(p: Boolean) {
            val i = if (p) 1 else 0
            if (mon(p).hp <= 0 && !fainted[i]) { fainted[i] = true; events += GameEvent.Fainted(p, mon(p).name) }
        }

        private fun speed(p: Boolean): Double {
            val s = StatCalc.stats(data, mon(p)).spe * stageMult(side(p).boosts[4])
            return if (side(p).status == "par") s * Tuning.PARA_SPEED else s
        }

        fun play(enemyActs: Boolean, mainActs: Boolean) {
            val playerMove: Move? = if (mainActs) MoveChooser.pickMain(moveChoice, data, battle, main, rng) else null
            val enemyMove: Move? = if (enemyActs) MoveChooser.pickWild(moveChoice, data, battle, main, rng) else null

            val playerFirst = when {
                (enemyMove?.priority ?: 0) != (playerMove?.priority ?: 0) -> (playerMove?.priority ?: 0) > (enemyMove?.priority ?: 0)
                else -> speed(true) > speed(false) // a tie goes to the wild one
            }
            val order = if (playerFirst) listOf(true, false) else listOf(false, true)
            for (p in order) {
                act(p, if (p) playerMove else enemyMove)
                checkFaint(true); checkFaint(false)
            }
            for (p in order) endOfTurn(p)
        }

        /** The free hit of a switch (W10): the wild one's single action against [main]; no turn order, no Splash, no end-of-turn damage. */
        fun wildStrikes() {
            if (main.hp <= 0 || wild.hp <= 0) return // nothing to hit, or nobody left to hit with
            act(false, pickSwitchHitMove(data, battle, main, rng, moveChoice))
            checkFaint(true); checkFaint(false)
        }

        private fun act(p: Boolean, move: Move?) {
            if (move == null) { if (!p && mon(p).hp > 0 && mon(!p).hp > 0) events += GameEvent.Splash(wild.name); return } // only the wild one's idle turn is shown
            if (mon(p).hp <= 0 || mon(!p).hp <= 0) return
            if (!canAct(p, move)) return
            if (move.id in Tuning.CHARGE_MOVES && side(p).charged.add(move.id)) { fx(p, chargeText(p, move), EffectKind.CHARGE); return }
            val saved = out
            val buffer = ArrayList<GameEvent>()
            out = buffer
            val attack = if (move.damaging) damaging(p, move) else statusMove(p, move)
            out = saved
            events += attack
            events += buffer
            if (move.id in Tuning.CHARGE_CLEARS_AFTER_USE) side(p).charged.remove(move.id)
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
                "slp" -> if (rng.nextDouble() < Tuning.WAKE_CHANCE) { s.status = null; fx(p, "$n woke up!", EffectKind.STATUS_CURED, "slp") }
                         else { fx(p, "$n is fast asleep.", EffectKind.CANT_MOVE, "slp"); return false }
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
            p, mon(p).name, mon(!p).name, move.name, if (move.id == Tuning.STRUGGLE) null else move.type, move.category,
            damage, hits, eff, crit, missed, failed, mon(!p).hp, maxHpOf(!p), mon(p).hp, maxHpOf(p),
            moveId = move.id, power = move.power,
        )

        private fun accuracyHit(p: Boolean, move: Move): Boolean {
            if (move.ohko) return rng.nextDouble() * 100 < 30 + mon(p).level - mon(!p).level
            val acc = move.accuracy ?: return true
            val stage = (side(p).boosts[5] - side(!p).boosts[6]).coerceIn(-6, 6)
            return rng.nextDouble() * 100 < acc * accStageMult(stage)
        }

        private fun damaging(p: Boolean, move: Move): GameEvent {
            val u = mon(p)
            val t = mon(!p)
            if (move.ohko && t.level > u.level) { fx(p, "But it failed!"); return attackEvent(p, move, failed = true) }
            if (!accuracyHit(p, move)) return attackEvent(p, move, missed = true)

            val struggle = move.id == Tuning.STRUGGLE
            val hits = move.multihit?.let { multiHits(it) } ?: 1
            var total = 0; var done = 0; var crit = false; var eff = 1.0
            while (done < hits && t.hp > 0) {
                val r = damage(data, u, side(p), t, side(!p), move, rng)
                eff = r.effectiveness
                if (eff == 0.0) break
                crit = crit || r.crit
                var d = r.damage
                d = maxOf(1, floor(d * (if (p) mult else enemyPower)).toInt()) // R10-B22: each side's damage is scaled by its own answers-based factor
                d = minOf(d, t.hp)
                t.hp -= d; total += d; done++
            }
            val hit = eff > 0
            if (hit) {
                move.drain?.let { (a, b) -> heal(p, maxOf(1, total * a / b), "${u.name} drained HP from ${t.name}!", EffectKind.DRAIN) }
                if (struggle) hurt(p, maxOf(1, maxHpOf(p) / 4), "${u.name} is damaged by recoil!", EffectKind.RECOIL)
                else move.recoil?.let { (a, b) -> hurt(p, maxOf(1, total * a / b), "${u.name} is damaged by recoil!", EffectKind.RECOIL) }
            }
            if (move.selfDestruct) { u.hp = 0 }
            if (hit && u.hp > 0) move.selfBoosts?.let { boost(p, it) }
            if (hit && total > 0) thawByHit(!p, move)
            if (hit && t.hp > 0) move.secondary?.let { sec ->
                if (rng.nextInt(100) < sec.chance) {
                    sec.status?.let { setStatus(!p, it, move.type) }
                    sec.boosts?.let { boost(!p, it) }
                    if (u.hp > 0) sec.selfBoosts?.let { boost(p, it) }
                }
            }
            return attackEvent(p, move, total, done, eff, crit)
        }

        private fun multiHits(r: IntRange): Int {
            if (r.first == r.last) return r.first
            if (r == 2..5) { val x = rng.nextInt(100); return if (x < 35) 2 else if (x < 70) 3 else if (x < 85) 4 else 5 }
            return rng.nextInt(r.first, r.last + 1)
        }

        private fun statusMove(p: Boolean, move: Move): GameEvent {
            val self = move.target in SELF_TARGETS
            if (move.id in Tuning.POWDER_MOVES && Type.GRASS in types(!p)) {
                fx(p, "It doesn't affect ${mon(!p).name}…")
                return attackEvent(p, move, failed = true)
            }
            if (!self && move.accuracy != null && !accuracyHit(p, move)) return attackEvent(p, move, missed = true)
            val tp = if (self) p else !p
            var parts = 0; var applied = false
            move.status?.let { parts++; if (setStatus(tp, it, move.type)) applied = true }
            move.boosts?.let { parts++; if (boost(tp, it)) applied = true }
            move.selfBoosts?.let { parts++; if (boost(p, it)) applied = true }
            move.heal?.let { (a, b) ->
                parts++
                if (mon(p).hp < maxHpOf(p)) { heal(p, maxOf(1, maxHpOf(p) * a / b), "${mon(p).name} regained health!", EffectKind.HEAL); applied = true }
            }
            if (parts == 0) fx(p, "But nothing happened.")
            else if (!applied) fx(p, "But it failed!")
            return attackEvent(p, move, failed = !applied)
        }

        private fun heal(p: Boolean, amount: Int, text: String, kind: EffectKind = EffectKind.HEAL) {
            val before = mon(p).hp
            mon(p).hp = minOf(maxHpOf(p), before + amount)
            fx(p, text, kind, amount = mon(p).hp - before)
        }

        private fun hurt(p: Boolean, amount: Int, text: String, kind: EffectKind = EffectKind.RECOIL) {
            val before = mon(p).hp
            mon(p).hp = maxOf(0, before - amount)
            fx(p, text, kind, amount = before - mon(p).hp)
        }

        private fun canHave(status: String, tp: Boolean, moveType: Type): Boolean = canHaveStatus(types(tp), status, moveType)

        private fun setStatus(tp: Boolean, status: String, moveType: Type): Boolean {
            val s = side(tp)
            if (s.status != null || mon(tp).hp <= 0 || !canHave(status, tp, moveType)) return false
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
            return true
        }

        /** Applies stage changes; true when at least one stage really moved. */
        private fun boost(tp: Boolean, changes: Map<String, Int>): Boolean {
            var changed = false
            for ((key, v) in changes) {
                val i = BOOST_KEYS.indexOf(key)
                if (i < 0 || v == 0) continue
                val b = side(tp).boosts
                val label = "${mon(tp).name}'s ${BOOST_LABELS[i]}"
                val limit = moveChoice.stageLimit(i) // 6; accuracy / evasion stop at ±2 with a smart move choice (W9)
                if (v > 0 && b[i] >= limit) { fx(tp, "$label won't go any higher!", EffectKind.STAT_LIMIT, key, 1); continue }
                if (v < 0 && b[i] <= -limit) { fx(tp, "$label won't go any lower!", EffectKind.STAT_LIMIT, key, -1); continue }
                val before = b[i]
                b[i] = if (v > 0) minOf(b[i] + v, limit) else maxOf(b[i] + v, -limit)
                val applied = b[i] - before
                changed = true
                fx(tp, "$label " + when {
                    v == 1 -> "rose!"
                    v == 2 -> "rose sharply!"
                    v > 2 -> "rose drastically!"
                    v == -1 -> "fell!"
                    v == -2 -> "harshly fell!"
                    else -> "severely fell!"
                }, EffectKind.STAT_CHANGE, key, applied)
            }
            return changed
        }

        private fun endOfTurn(p: Boolean) {
            val m = mon(p)
            val s = side(p)
            if (m.hp <= 0) return
            when (s.status) {
                "brn" -> hurtEvent(p, maxOf(1, maxHpOf(p) / 16), "${m.name} is hurt by its burn!")
                "psn" -> hurtEvent(p, maxOf(1, maxHpOf(p) / 8), "${m.name} is hurt by poison!")
                "tox" -> {
                    s.statusTurns = minOf(15, s.statusTurns + 1)
                    hurtEvent(p, maxOf(1, maxHpOf(p) * s.statusTurns / 16), "${m.name} is hurt by poison!")
                }
            }
            checkFaint(p)
        }

        private fun hurtEvent(p: Boolean, amount: Int, text: String) {
            val before = mon(p).hp
            mon(p).hp = maxOf(0, before - amount)
            events += GameEvent.Effect(p, text, mon(p).hp, maxHpOf(p), side(p).status, EffectKind.STATUS_DAMAGE, amount = before - mon(p).hp)
        }
    }
}
