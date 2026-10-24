package com.husarp.ankigo.game

import kotlin.math.max
import kotlin.math.min

/**
 * R17C-4 rule set "MovePower": power that depends on the fight (weight, HP, speed, friendship, stages, a roll), situational power
 * (Hex, Facade, Knock Off ...), moves that build up (Echoed Voice, Rollout, Triple Axel, Beat Up, Rage, Bide), special damage (Counter,
 * Endeavor, Psywave ...), the fail / timing rules (Fake Out, Sucker Punch, Focus Punch, Future Sight ...), type rules (Hidden Power,
 * Freeze-Dry ...) and the texts of all of them. Mainline formulas; every number is a named constant below.
 *
 * KEYS (private state of this set, all prefixed "mp."): in [BattleSide.volatiles] "mp.echo" "mp.fury" "mp.rollout" "mp.bide" "mp.bide.dmg"
 * "mp.rage" "mp.rand" (+ ".at", see [lockGet]); in [BattleSide.notes] "mp.rollout.move" "mp.used" "mp.lowered"; in the target side's
 * [BattleView.conds] "mp.fs" (rounds left of a Future Sight / Doom Desire), "mp.fs.dmg", "mp.fs.doom". "defensecurl" is R17C-8's key
 * (a volatile of the user): it doubles Rollout / Ice Ball.
 *
 * Moves it leaves to others: Spit Up and Stockpile (R17C-5), weather power (R17C-7). Only [ignoresBurn] is an extra hook (Facade).
 */
internal object MovePower : BattleRules {
    // ---- the numbers ----
    /** Weight (kg) used when the data has none. */
    private const val DEFAULT_WEIGHT_KG = 60.0
    /** Low Kick / Grass Knot: the target weighs LESS than the first number (kg) -> that power; at 200 kg or more [WEIGHT_TOP_POWER]. */
    private val WEIGHT_STEPS = listOf(10.0 to 20, 25.0 to 40, 50.0 to 60, 100.0 to 80, 200.0 to 100)
    private const val WEIGHT_TOP_POWER = 120
    /** Heavy Slam / Heat Crash: user weight / target weight at least the first number -> that power; below the last [RATIO_BOTTOM_POWER]. */
    private val WEIGHT_RATIO_STEPS = listOf(5.0 to 120, 4.0 to 100, 3.0 to 80, 2.0 to 60)
    private const val RATIO_BOTTOM_POWER = 40
    /** Flail / Reversal: [FLAIL_SCALE] * HP / max HP (whole number) at most the first number -> that power; above the last [FLAIL_BOTTOM_POWER]. */
    private const val FLAIL_SCALE = 48
    private val FLAIL_STEPS = listOf(1 to 200, 4 to 150, 9 to 100, 16 to 80, 32 to 40)
    private const val FLAIL_BOTTOM_POWER = 20
    /** Eruption / Water Spout / Dragon Energy, Crush Grip / Wring Out, Hard Press: power at full HP (it falls with the HP, at least 1). */
    private const val ERUPTION_POWER = 150
    private const val CRUSH_GRIP_POWER = 120
    private const val HARD_PRESS_POWER = 100
    /** Gyro Ball: [GYRO_BALL_FACTOR] * target speed / user speed + 1, at most [GYRO_BALL_MAX]. Electro Ball: user speed / target speed at least the first number -> that power; below the last [RATIO_BOTTOM_POWER]. */
    private const val GYRO_BALL_FACTOR = 25
    private const val GYRO_BALL_MAX = 150
    private val ELECTRO_BALL_STEPS = listOf(4.0 to 150, 3.0 to 120, 2.0 to 80, 1.0 to 60)
    /** Return / Frustration: power = friendship * 10 / 25 (at least 1). A wild Pokémon has no friendship in AnkiGo: it counts as this (the games' usual 70). */
    private const val FRIENDSHIP_DIVISOR = 25
    private const val FRIENDSHIP_FACTOR = 10
    private const val WILD_FRIENDSHIP = 70
    /** Stored Power / Power Trip: [STAGE_BASE] + [STAGE_PER_STAGE] per raised stage; Punishment: [PUNISHMENT_BASE] + the same, at most [PUNISHMENT_MAX]. */
    private const val STAGE_BASE = 20
    private const val STAGE_PER_STAGE = 20
    private const val PUNISHMENT_BASE = 60
    private const val PUNISHMENT_MAX = 200
    /** Magnitude: level 4..10 with its chance in percent; power by level. */
    private val MAGNITUDE_CHANCE = listOf(4 to 5, 5 to 10, 6 to 20, 7 to 30, 8 to 20, 9 to 10, 10 to 5)
    private val MAGNITUDE_POWER = mapOf(4 to 10, 5 to 30, 6 to 50, 7 to 70, 8 to 90, 9 to 110, 10 to 150)
    /** The power an ESTIMATE (move choice, details) uses for a random move: its average. */
    private const val MAGNITUDE_AVERAGE = 71
    private const val PRESENT_AVERAGE = 52
    /** Present: chance in percent of 40 / 80 / 120 power (the rest, 20 %, heals the target by [PRESENT_HEAL_PARTS]th of its max HP). */
    private val PRESENT_CHANCE = listOf(40 to 40, 80 to 30, 120 to 10)
    private const val PRESENT_HEAL = -1
    private const val PRESENT_HEAL_PARTS = 4
    /** Trump Card: AnkiGo has no PP, so it keeps the power of a move with plenty of PP left. */
    private const val TRUMP_CARD_POWER = 40
    /** Rage Fist: 50 + 50 per hit taken in this fight, at most 350. */
    private const val RAGE_FIST_BASE = 50
    private const val RAGE_FIST_PER_HIT = 50
    private const val RAGE_FIST_MAX = 350
    /** Echoed Voice: base power * (1 + uses in a row), at most [ECHO_MAX_POWER]; Fury Cutter: base * 2 ^ hits in a row, at most [FURY_MAX_POWER]. */
    private const val ECHO_MAX_POWER = 200
    private const val FURY_MAX_POWER = 160
    /** Rollout / Ice Ball: locked for this many actions, the power doubles with every hit; Defense Curl doubles it once more. */
    private const val ROLLOUT_ACTIONS = 5
    /** Bide: this many actions storing damage, then the next one deals double what was stored. */
    private const val BIDE_STORE_ACTIONS = 2
    private const val BIDE_MULTIPLIER = 2
    /** Psywave: the user's level * (this many percent .. +100); Counter / Mirror Coat give back double, Metal Burst / Comeuppance 3/2. */
    private const val PSYWAVE_MIN_PERCENT = 50
    private const val COUNTER_FACTOR = 2
    /** Beat Up: each team member hits with 5 + its base Attack / 10. */
    private const val BEAT_UP_BASE = 5
    private const val BEAT_UP_DIVISOR = 10
    /** Future Sight / Doom Desire land at the end of the round this many rounds after use (the use round counts as the first). */
    private const val FUTURE_ROUNDS = 3
    /** Freeze-Dry hits Water types for double instead of half. */
    private const val FREEZE_DRY_WATER = 4.0
    private const val FAILED = "But it failed!"

    // ---- keys ----
    private const val ECHO = "mp.echo"
    private const val FURY = "mp.fury"
    private const val ROLLOUT = "mp.rollout"
    private const val ROLLOUT_MOVE = "mp.rollout.move"
    private const val BIDE = "mp.bide"
    private const val BIDE_DAMAGE = "mp.bide.dmg"
    private const val RAGE = "mp.rage"
    private const val RAND = "mp.rand"
    private const val USED = "mp.used"
    private const val LOWERED = "mp.lowered"
    private const val FUTURE = "mp.fs"
    private const val FUTURE_DAMAGE = "mp.fs.dmg"
    private const val FUTURE_DOOM = "mp.fs.doom"

    /** Hidden Power's types by the number the IVs give (the games' order). */
    private val HIDDEN_POWER_TYPES = listOf(
        Type.FIGHTING, Type.FLYING, Type.POISON, Type.GROUND, Type.ROCK, Type.BUG, Type.GHOST, Type.STEEL,
        Type.FIRE, Type.WATER, Type.GRASS, Type.ELECTRIC, Type.PSYCHIC, Type.ICE, Type.DRAGON, Type.DARK,
    )

    // ---- the moves ----
    private val WEIGHT_MOVES = setOf("lowkick", "grassknot")
    private val WEIGHT_RATIO_MOVES = setOf("heavyslam", "heatcrash")
    private val FLAIL_MOVES = setOf("flail", "reversal")
    private val ERUPTION_MOVES = setOf("eruption", "waterspout", "dragonenergy")
    private val TARGET_HP_MOVES = mapOf("crushgrip" to CRUSH_GRIP_POWER, "wringout" to CRUSH_GRIP_POWER, "hardpress" to HARD_PRESS_POWER)
    private val FRIENDSHIP_MOVES = setOf("return", "pikapapow", "veeveevolley")
    private val STORED_POWER_MOVES = setOf("storedpower", "powertrip")
    private val ROLLOUT_MOVES = setOf("rollout", "iceball")
    private val TRIPLE_MOVES = setOf("tripleaxel", "triplekick")
    private val FUTURE_MOVES = setOf("futuresight", "doomdesire")
    private val COUNTER_MOVES = setOf("counter", "mirrorcoat", "metalburst", "comeuppance")
    private val STATUS_DOUBLE = setOf("hex", "infernalparade")
    private val POISON_DOUBLE = setOf("venoshock", "barbbarrage")
    private val FIRST_ACTION_MOVES = setOf("fakeout", "firstimpression")
    private val SNEAKY_MOVES = setOf("suckerpunch", "thunderclap")
    private val USER_FIRST_MOVES = setOf("fishiousrend", "boltbeak")
    private val STAGE_LOWERED_MOVES = setOf("lashout")
    private val PREVIOUS_FAILED_MOVES = setOf("stompingtantrum", "temperflare")
    private val FACADE_STATUSES = setOf("brn", "psn", "tox", "par")

    /**
     * The moves whose listed power is NOT the power they hit with (it depends on the fight, or they deal a special amount): the details and
     * the compare popup show no "Power" number for them ([MoveText.showsPower]). "spitup" is R17C-5's move; it is listed here so it never shows
     * the flat 60 either.
     */
    val VARIABLE_POWER: Set<String> = setOf(
        "lowkick", "grassknot", "heavyslam", "heatcrash", "flail", "reversal", "eruption", "waterspout", "dragonenergy",
        "crushgrip", "wringout", "hardpress", "gyroball", "electroball", "return", "pikapapow", "veeveevolley", "frustration",
        "storedpower", "powertrip", "punishment", "magnitude", "present", "trumpcard", "ragefist",
        "psywave", "endeavor", "finalgambit", "counter", "mirrorcoat", "metalburst", "comeuppance",
        "beatup", "bide", "spitup",
    )

    // ---- reading the fight ----

    private fun weightOf(v: BattleView, mon: Mon): Double =
        (MoveControl.transformedWeight(v, mon) ?: v.data.sp(mon.species).weightKg ?: DEFAULT_WEIGHT_KG) * AbilitiesMore.weightFactor(v, mon) // R17C-8: a transformed Pokémon weighs what its target does; R17C-9b: Light / Heavy Metal
    private fun hpPercentPower(power: Int, hp: Int, maxHp: Int) = max(1, power * hp / max(1, maxHp))

    /** [p]'s effective speed (stages, paralysis, speed rules), with or without a round. */
    private fun speedOf(v: BattleView, p: Boolean): Double {
        (v as? Round)?.let { return it.speed(p) }
        val s = v.stats(p).spe * BattleEngine.stageMult(v.side(p).boosts[4]) * rulesProduct { it.speedMod(v, p) }
        return if (v.side(p).status == "par") s * Tuning.PARA_SPEED else s
    }

    private fun friendshipOf(h: Hit): Int = (if (h.attackerIsMain) h.attacker.friendship else WILD_FRIENDSHIP).coerceIn(0, Tuning.FRIENDSHIP_MAX)

    private fun raisedStages(side: BattleSide): Int = side.boosts.sumOf { max(0, it) }

    /** A locked run of actions (Rollout, Bide): [key] counts the actions so far; it only counts while the side's NEXT action is the one it was stored for. */
    private fun lockSet(s: BattleSide, key: String, n: Int) { s.volatiles[key] = n; s.volatiles["$key.at"] = s.actions + 1 }
    private fun lockGet(s: BattleSide, key: String): Int = s.volatiles[key]?.takeIf { s.volatiles["$key.at"] == s.actions } ?: 0
    private fun lockClear(s: BattleSide, key: String) { s.volatiles.remove(key); s.volatiles.remove("$key.at") }

    /** The team of the side [p] for Beat Up: the Pokémon itself first, then the other team members with HP (the Main only; a wild one is alone). */
    private fun beatUpTeam(v: BattleView, p: Boolean): List<Mon> {
        val me = v.mon(p)
        if (!p) return listOf(me)
        return listOf(me) + v.battle?.team.orEmpty().filter { it.uid != me.uid && it.hp > 0 }
    }

    /** [p]'s side has a Future Sight / Doom Desire on its way. */
    private fun futureWaiting(v: BattleView, tp: Boolean) = FUTURE in v.conds(tp)

    private fun usedMoves(s: BattleSide): Set<String> = s.notes[USED]?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    /** Last Resort works when [p] knows another move and has used every other one in this fight. */
    private fun lastResortReady(v: BattleView, p: Boolean): Boolean {
        val others = v.moves(p).map { it.id }.filter { it != "lastresort" }
        return others.isNotEmpty() && usedMoves(v.side(p)).containsAll(others)
    }

    /** The hit [p] took from the other side this round that the counter move [move] answers, or null (none, or the wrong kind). */
    private fun hitToReturn(r: Round, p: Boolean, move: Move): BattleEngine.TakenHit? {
        val t = r.lastHitTaken(p) ?: return null
        return when {
            t.byMain == p -> null
            move.id == "counter" -> t.takeIf { it.category == Category.PHYSICAL }
            move.id == "mirrorcoat" -> t.takeIf { it.category == Category.SPECIAL }
            else -> t
        }
    }

    /** The move [move] of side [p] landed a hit on the other side in this action (it dealt damage). */
    private fun landed(r: Round, p: Boolean, move: Move): Boolean = r.lastHitTaken(!p)?.let { it.byMain == p && it.moveId == move.id } == true

    // ---- the damage formula ----

    override fun basePower(h: Hit): Int? {
        val m = h.move
        val v = h.view
        val p = h.attackerIsMain
        return when (m.id) {
            in WEIGHT_MOVES -> {
                val kg = weightOf(v, h.defender)
                WEIGHT_STEPS.firstOrNull { kg < it.first }?.second ?: WEIGHT_TOP_POWER
            }
            in WEIGHT_RATIO_MOVES -> {
                val ratio = weightOf(v, h.attacker) / max(0.1, weightOf(v, h.defender))
                WEIGHT_RATIO_STEPS.firstOrNull { ratio >= it.first }?.second ?: RATIO_BOTTOM_POWER
            }
            in FLAIL_MOVES -> {
                val x = FLAIL_SCALE * h.attacker.hp / max(1, v.maxHp(p))
                FLAIL_STEPS.firstOrNull { x <= it.first }?.second ?: FLAIL_BOTTOM_POWER
            }
            in ERUPTION_MOVES -> hpPercentPower(ERUPTION_POWER, h.attacker.hp, v.maxHp(p))
            "crushgrip", "wringout", "hardpress" -> hpPercentPower(TARGET_HP_MOVES.getValue(m.id), h.defender.hp, v.maxHp(!p))
            "gyroball" -> {
                val u = speedOf(v, p)
                if (u <= 0) GYRO_BALL_MAX else min(GYRO_BALL_MAX, (GYRO_BALL_FACTOR * speedOf(v, !p) / u).toInt() + 1)
            }
            "electroball" -> {
                val t = speedOf(v, !p)
                val ratio = if (t <= 0) Double.MAX_VALUE else speedOf(v, p) / t
                ELECTRO_BALL_STEPS.firstOrNull { ratio >= it.first }?.second ?: RATIO_BOTTOM_POWER
            }
            in FRIENDSHIP_MOVES -> max(1, friendshipOf(h) * FRIENDSHIP_FACTOR / FRIENDSHIP_DIVISOR)
            "frustration" -> max(1, (Tuning.FRIENDSHIP_MAX - friendshipOf(h)) * FRIENDSHIP_FACTOR / FRIENDSHIP_DIVISOR)
            in STORED_POWER_MOVES -> STAGE_BASE + STAGE_PER_STAGE * raisedStages(h.aSide)
            "punishment" -> min(PUNISHMENT_MAX, PUNISHMENT_BASE + STAGE_PER_STAGE * raisedStages(h.dSide))
            "magnitude" -> h.aSide.volatiles[RAND]?.let { MAGNITUDE_POWER[it] } ?: MAGNITUDE_AVERAGE
            "present" -> h.aSide.volatiles[RAND]?.takeIf { it != PRESENT_HEAL } ?: PRESENT_AVERAGE
            "trumpcard" -> TRUMP_CARD_POWER
            "ragefist" -> min(RAGE_FIST_MAX, RAGE_FIST_BASE + RAGE_FIST_PER_HIT * h.aSide.timesHit)
            "echoedvoice" -> min(ECHO_MAX_POWER, m.power * (1 + (h.aSide.volatiles[ECHO] ?: 0)))
            "furycutter" -> min(FURY_MAX_POWER, m.power shl (h.aSide.volatiles[FURY] ?: 0))
            in ROLLOUT_MOVES -> {
                val curl = if ("defensecurl" in h.aSide.volatiles) 2 else 1
                m.power * (1 shl lockGet(h.aSide, ROLLOUT)) * curl
            }
            in TRIPLE_MOVES -> m.power * (h.hitIndex + 1)
            "beatup" -> {
                val team = beatUpTeam(v, p)
                val member = team[min(h.hitIndex, team.size - 1)]
                BEAT_UP_BASE + v.data.sp(member.species).base.atk / BEAT_UP_DIVISOR
            }
            else -> null
        }
    }

    override fun powerMod(h: Hit): Double {
        val m = h.move
        val p = h.attackerIsMain
        val rd = h.round
        val twice = when (m.id) {
            in STATUS_DOUBLE -> h.dSide.status != null || AbilitiesMore.comatose(h.view, !p) // R17C-9b: Comatose counts as a status
            in POISON_DOUBLE -> h.dSide.status == "psn" || h.dSide.status == "tox"
            "facade" -> h.aSide.status in FACADE_STATUSES
            "brine" -> h.defender.hp * 2 <= h.view.maxHp(!p)
            "acrobatics" -> h.attacker.heldItem == null
            "wakeupslap" -> h.dSide.status == "slp" || AbilitiesMore.comatose(h.view, !p)
            "smellingsalts" -> h.dSide.status == "par"
            "assurance" -> rd != null && rd.damageTakenThisRound(!p) > 0
            "avalanche", "revenge" -> rd != null && rd.hitThisRound(p)
            "payback" -> rd != null && rd.movedThisRound(!p)
            in USER_FIRST_MOVES -> rd != null && !rd.movedThisRound(!p) && rd.plannedMove(!p) != null
            in STAGE_LOWERED_MOVES -> h.aSide.notes[LOWERED]?.let { it == h.view.battle?.rounds?.toString() } == true
            in PREVIOUS_FAILED_MOVES -> h.aSide.lastMoveFailed
            "round" -> rd != null && rd.movedThisRound(!p) && h.dSide.lastMove == "round"
            else -> false
        }
        return when {
            twice -> 2.0
            m.id == "knockoff" && h.defender.heldItem != null -> 1.5
            else -> 1.0
        }
    }

    /** Facade: its damage is not halved by the user's burn. */
    override fun ignoresBurn(h: Hit): Boolean = h.move.id == "facade"

    override fun fixedDamage(h: Hit): Int? {
        val m = h.move
        return when (m.id) {
            "endeavor" -> max(0, h.defender.hp - h.attacker.hp)
            "finalgambit" -> h.attacker.hp
            "psywave" -> max(1, h.attacker.level * (h.aSide.volatiles[RAND] ?: 100) / 100)
            "bide" -> if (lockGet(h.aSide, BIDE) >= BIDE_STORE_ACTIONS) BIDE_MULTIPLIER * (h.aSide.volatiles[BIDE_DAMAGE] ?: 0) else 0
            in COUNTER_MOVES -> {
                val taken = h.round?.let { hitToReturn(it, h.attackerIsMain, m) }?.damage ?: 0
                if (m.id == "counter" || m.id == "mirrorcoat") taken * COUNTER_FACTOR else taken * 3 / 2
            }
            else -> null
        }
    }

    override fun moveType(h: Hit, type: Type): Type = when (h.move.id) {
        "hiddenpower" -> hiddenPowerType(h.attacker.ivs)
        "revelationdance" -> h.aTypes.firstOrNull() ?: type
        else -> type
    }

    /** Hidden Power's type: the lowest bit of each IV (HP, Attack, Defense, Speed, Sp. Atk, Sp. Def) builds a number 0..63; * 15 / 63 picks the type. */
    internal fun hiddenPowerType(ivs: Stats): Type {
        var n = 0
        listOf(ivs.hp, ivs.atk, ivs.def, ivs.spe, ivs.spa, ivs.spd).forEachIndexed { i, iv -> n += (iv and 1) shl i }
        return HIDDEN_POWER_TYPES[n * 15 / 63]
    }

    override fun effectiveness(h: Hit, eff: Double): Double = when (h.move.id) {
        "freezedry" -> eff * Math.pow(FREEZE_DRY_WATER, h.dTypes.count { it == Type.WATER }.toDouble())
        "flyingpress" -> eff * TypeChart.eff(Type.FLYING, h.dTypes)
        "bide" -> 1.0 // typeless: it only needs a target
        "futuresight", "doomdesire" -> if (eff == 0.0) h.dTypes.fold(1.0) { x, t -> x * TypeChart.single(h.type, t).let { s -> if (s == 0.0) 1.0 else s } } else eff
        "present" -> if (h.aSide.volatiles[RAND] == PRESENT_HEAL) 1.0 else eff // the heal has no type
        else -> eff
    }

    override fun hits(r: Round, p: Boolean, move: Move): Int? = if (move.id == "beatup") beatUpTeam(r, p).size else null

    // ---- before the move: rolls, fail and timing rules ----

    override fun failReason(r: Round, p: Boolean, move: Move): String? {
        rollFor(r, p, move)
        val fails = when (move.id) {
            in FIRST_ACTION_MOVES -> !isFirstAction(r, p)
            in SNEAKY_MOVES -> r.movedThisRound(!p) || r.plannedMove(!p)?.damaging != true
            "dreameater" -> r.side(!p).status != "slp" && !AbilitiesMore.comatose(r, !p) // R17C-9b: Comatose counts as asleep
            "snore" -> r.side(p).status != "slp" && !AbilitiesMore.comatose(r, p)
            "poltergeist" -> r.mon(!p).heldItem == null
            "synchronoise" -> r.types(p).none { it in r.types(!p) }
            "lastresort" -> !lastResortReady(r, p)
            "endeavor" -> r.mon(!p).hp <= r.mon(p).hp
            in COUNTER_MOVES -> hitToReturn(r, p, move) == null
            in FUTURE_MOVES -> futureWaiting(r, !p)
            "bide" -> lockGet(r.side(p), BIDE) < BIDE_STORE_ACTIONS || (r.side(p).volatiles[BIDE_DAMAGE] ?: 0) <= 0
            else -> false
        }
        return if (fails) FAILED else null
    }

    /** It is [p]'s first action in this fight (Fake Out, First Impression; R17C-5's Mat Block asks it too). */
    internal fun isFirstAction(v: BattleView, p: Boolean): Boolean = v.side(p).actions == 0

    /** The random part of Magnitude / Present / Psywave is rolled ONCE per use, here, so that estimates (move choice, details) never draw numbers. */
    private fun rollFor(r: Round, p: Boolean, move: Move) {
        val v = r.side(p).volatiles
        when (move.id) {
            "magnitude" -> {
                var x = r.rng.nextInt(100)
                val level = MAGNITUDE_CHANCE.first { x -= it.second; x < 0 }.first
                v[RAND] = level
                r.fx(p, "Magnitude $level!")
            }
            "present" -> {
                var x = r.rng.nextInt(100)
                v[RAND] = PRESENT_CHANCE.firstOrNull { x -= it.second; x < 0 }?.first ?: PRESENT_HEAL
            }
            "psywave" -> v[RAND] = PSYWAVE_MIN_PERCENT + r.rng.nextInt(101)
        }
    }

    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        when (move.id) {
            "focuspunch" -> if (r.hitThisRound(p)) {
                r.fx(p, "${r.mon(p).name} lost its focus and couldn't move!", EffectKind.CANT_MOVE, "focuspunch")
                return false
            }
            "bide" -> {
                val s = r.side(p)
                val done = lockGet(s, BIDE)
                if (done >= BIDE_STORE_ACTIONS) return true // the action after the storing ones: it is let loose
                if (done == 0) s.volatiles[BIDE_DAMAGE] = 0
                lockSet(s, BIDE, done + 1)
                if (done == 0) r.fx(p, "${r.mon(p).name} is storing energy!", EffectKind.VOLATILE, "bide", 1, label = "Bide")
                else r.fx(p, "${r.mon(p).name} is storing energy!")
                return false
            }
        }
        return true
    }

    override fun forcedMove(v: BattleView, p: Boolean): Move? {
        val s = v.side(p)
        if (lockGet(s, BIDE) in 1..BIDE_STORE_ACTIONS) v.data.move("bide")?.let { return it }
        if (lockGet(s, ROLLOUT) in 1 until ROLLOUT_ACTIONS) s.notes[ROLLOUT_MOVE]?.let { v.data.move(it) }?.let { return it }
        return null
    }

    // ---- after the move, the hit, the damage ----

    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        val s = r.side(p)
        s.volatiles.remove(RAND)
        s.notes[USED] = (usedMoves(s) + move.id).joinToString(",")
        val hit = !failed && landed(r, p, move)

        if (move.id == "echoedvoice") s.volatiles[ECHO] = min((ECHO_MAX_POWER / max(1, move.power)) - 1, (s.volatiles[ECHO] ?: 0) + 1)
        else s.volatiles.remove(ECHO)

        if (move.id == "furycutter" && hit) s.volatiles[FURY] = min(2, (s.volatiles[FURY] ?: 0) + 1)
        else s.volatiles.remove(FURY)

        if (move.id in ROLLOUT_MOVES) {
            val n = lockGet(s, ROLLOUT) + 1
            if (!hit || n >= ROLLOUT_ACTIONS) lockClear(s, ROLLOUT) else { lockSet(s, ROLLOUT, n); s.notes[ROLLOUT_MOVE] = move.id }
        } else lockClear(s, ROLLOUT)

        lockClear(s, BIDE); s.volatiles.remove(BIDE_DAMAGE) // a used Bide is let loose; any other move ends a storing run

        if (move.id == "rage") s.volatiles[RAGE] = 1 else s.volatiles.remove(RAGE)
    }

    /** Spectral Thief: the target's raised stats go to the user before the hit (so the hit counts them). */
    override fun beforeHit(r: Round, p: Boolean, move: Move) {
        if (move.id != "spectralthief") return
        val from = r.side(!p).boosts
        if (from.none { it > 0 }) return
        val to = r.side(p).boosts
        for (i in from.indices) {
            if (from[i] <= 0) continue
            to[i] = min(r.moveChoice.stageLimit(i), to[i] + from[i])
            from[i] = 0
        }
        r.fx(p, "${r.mon(p).name} stole ${r.mon(!p).name}'s stat boosts!", EffectKind.VOLATILE, "spectralthief", 1, label = "Stole boosts")
    }

    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        val t = r.side(tp)
        if (damage <= 0 || r.mon(tp).hp <= 0) return
        if (move.id == "wakeupslap" && t.status == "slp") {
            t.status = null; t.statusTurns = 0; t.volatiles.remove(CoreRules.SLEEP_TURNS) // R17C FIX-A: Rest's fixed sleep count goes too (a later sleep is a new one)
            r.fx(tp, "${r.mon(tp).name} woke up!", EffectKind.STATUS_CURED, "slp")
        } else if (move.id == "smellingsalts" && t.status == "par") {
            t.status = null
            r.fx(tp, "${r.mon(tp).name} was cured of paralysis!", EffectKind.STATUS_CURED, "par")
        }
    }

    override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {
        val s = r.side(tp)
        // R17C FIX-A: only in a round the victim uses the move (its pick this round), not in the rounds it idles: a wild Pokémon acts rarely, and it must
        // not store every hit of all the good rounds in between (Bide released up to twice its max HP) or grow with them (Rage)
        val using = r.plannedMove(tp)?.id
        if (RAGE in s.volatiles && using == "rage" && r.mon(tp).hp > 0) {
            r.fx(tp, "${r.mon(tp).name}'s rage is building!")
            r.boost(tp, mapOf("atk" to 1), byFoe = false)
        }
        if (lockGet(s, BIDE) >= 1 && using == "bide") s.volatiles[BIDE_DAMAGE] = (s.volatiles[BIDE_DAMAGE] ?: 0) + damage
    }

    override fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) {
        if (delta < 0) r.side(tp).notes[LOWERED] = r.battle.rounds.toString()
    }

    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        val m = h.move
        if (m.id in FUTURE_MOVES) {
            val c = r.conds(tp)
            c[FUTURE] = FUTURE_ROUNDS
            c[FUTURE_DAMAGE] = damage
            if (m.id == "doomdesire") c[FUTURE_DOOM] = 1 else c.remove(FUTURE_DOOM)
            r.fx(h.attackerIsMain, "${h.attacker.name} foresaw an attack!")
            return 0
        }
        if (m.id == "present" && h.aSide.volatiles[RAND] == PRESENT_HEAL) {
            val name = r.mon(tp).name
            if (r.mon(tp).hp >= r.maxHp(tp)) r.fx(tp, "$name's HP is full!")
            else r.heal(tp, max(1, r.maxHp(tp) / PRESENT_HEAL_PARTS), "$name regained health!")
            return 0
        }
        return damage
    }

    override fun endOfRound(r: Round) {
        for (tp in listOf(true, false)) {
            val c = r.conds(tp)
            val left = (c[FUTURE] ?: continue) - 1
            if (left > 0) { c[FUTURE] = left; continue }
            val damage = c.remove(FUTURE_DAMAGE) ?: 0
            val name = if (c.remove(FUTURE_DOOM) != null) "Doom Desire" else "Future Sight"
            c.remove(FUTURE)
            if (r.mon(tp).hp <= 0) continue
            r.hurt(tp, min(damage, r.mon(tp).hp), "${r.mon(tp).name} took the $name attack!", EffectKind.RESIDUAL)
        }
        r.side(true).volatiles.remove(RAND)
        r.side(false).volatiles.remove(RAND)
    }

    // ---- the move choice ----

    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        if (!move.damaging) return null
        val works = when (move.id) {
            in FIRST_ACTION_MOVES -> isFirstAction(v, p)
            "dreameater" -> v.side(!p).status == "slp" || AbilitiesMore.comatose(v, !p)
            "snore" -> v.side(p).status == "slp" || AbilitiesMore.comatose(v, p)
            "poltergeist" -> v.mon(!p).heldItem != null
            "synchronoise" -> v.types(p).any { it in v.types(!p) }
            "lastresort" -> lastResortReady(v, p)
            "endeavor" -> v.mon(!p).hp > v.mon(p).hp
            in FUTURE_MOVES -> !futureWaiting(v, !p)
            // R17C FIX-A: they fail in a round the target does not act in (every round of good answers against a wild one)
            in COUNTER_MOVES, in SNEAKY_MOVES -> v.acts(!p)
            else -> true
        }
        return if (works) null else false
    }

    // ---- texts ----

    override fun describe(m: Move): List<String> {
        val text = when (m.id) {
            "lowkick", "grassknot" -> "Power depends on the target's weight: 20 to 120."
            "heavyslam", "heatcrash" -> "Power depends on how much heavier you are than the target: 40 to 120."
            "flail", "reversal" -> "Power depends on your HP: 20 to 200, the lower your HP the stronger."
            "eruption", "waterspout", "dragonenergy" -> "Power depends on your HP: up to $ERUPTION_POWER at full HP, less as it drops."
            "crushgrip", "wringout" -> "Power depends on the target's HP: up to $CRUSH_GRIP_POWER at full HP, less as it drops."
            "hardpress" -> "Power depends on the target's HP: up to $HARD_PRESS_POWER at full HP, less as it drops."
            "gyroball" -> "Power depends on how much slower you are than the target: up to $GYRO_BALL_MAX."
            "electroball" -> "Power depends on how much faster you are than the target: 40 to 150."
            "return", "pikapapow", "veeveevolley" -> "Power grows with your friendship, up to ${Tuning.FRIENDSHIP_MAX * FRIENDSHIP_FACTOR / FRIENDSHIP_DIVISOR}."
            "frustration" -> "Power grows as your friendship drops, up to ${Tuning.FRIENDSHIP_MAX * FRIENDSHIP_FACTOR / FRIENDSHIP_DIVISOR}."
            "storedpower", "powertrip" -> "Power is $STAGE_BASE, plus $STAGE_PER_STAGE for every stat stage you have raised."
            "punishment" -> "Power is $PUNISHMENT_BASE, plus $STAGE_PER_STAGE for every stat stage the target has raised, up to $PUNISHMENT_MAX."
            "magnitude" -> "Power is random: 10 to 150, most often 70."
            "present" -> "Power is random (40, 80 or 120), or it heals the target by a quarter of its max HP one time in five."
            "trumpcard" -> "Always has $TRUMP_CARD_POWER power: AnkiGo has no PP to change it."
            "ragefist" -> "Power is $RAGE_FIST_BASE, plus $RAGE_FIST_PER_HIT for every time you were hit in this fight, up to $RAGE_FIST_MAX."

            "hex", "infernalparade" -> "Double power against a target with a status."
            "venoshock", "barbbarrage" -> "Double power against a poisoned target."
            "facade" -> "Double power while you are burned, poisoned or paralyzed, and a burn does not halve its damage."
            "brine" -> "Double power against a target at half HP or less."
            "acrobatics" -> "Double power while you hold no item."
            "knockoff" -> "Has 1.5 times the power against a target that holds an item, and never takes the item."
            "assurance" -> "Double power if the target already took damage this round."
            "avalanche", "revenge" -> "Double power if the target hit you earlier this round."
            "payback" -> "Double power if the target moved before you this round."
            "fishiousrend", "boltbeak" -> "Double power if you move before the target this round."
            "wakeupslap" -> "Double power against a sleeping target, which it wakes up."
            "smellingsalts" -> "Double power against a paralyzed target, which it cures."
            "lashout" -> "Double power if your stats were lowered this round."
            "stompingtantrum", "temperflare" -> "Double power if your last move failed or missed."
            "lastrespects", "retaliate" -> "Its power never grows: no Pokémon of your team stays fainted in AnkiGo."
            "round" -> "Double power if the target used Round earlier this round."

            "echoedvoice" -> "Gains ${m.power} power each time you use it in a row, up to $ECHO_MAX_POWER."
            "furycutter" -> "Doubles its power each time it hits in a row, up to $FURY_MAX_POWER."
            "rollout", "iceball" -> "Locks you in for up to $ROLLOUT_ACTIONS uses with double the power after each hit, and double again after Defense Curl; a miss ends it."
            "tripleaxel", "triplekick" -> "Each hit has more power (${m.power}, ${m.power * 2}, then ${m.power * 3}) and rolls its own accuracy; the first miss ends it."
            "populationbomb" -> "Each hit rolls its own accuracy, and the first miss ends it."
            "beatup" -> "Every Pokémon of your team with HP hits once with $BEAT_UP_BASE plus its base Attack divided by $BEAT_UP_DIVISOR; a wild Pokémon hits once."
            "rage" -> "While you keep using it, every hit you take raises your Attack by 1."
            "bide" -> "Stores energy for $BIDE_STORE_ACTIONS of your moves: the damage you take in those turns is counted, then your next move deals double that. It fails if you took none."
            "spectralthief" -> "Steals the target's raised stats for itself before it hits."

            "psywave" -> "Deals your level times a random 50 to 150 percent."
            "endeavor" -> "Brings the target's HP down to yours, and fails if the target has no more HP than you."
            "finalgambit" -> "Deals damage equal to your current HP."
            "counter" -> "If a physical move hit you this round, deals double that damage back; otherwise it fails."
            "mirrorcoat" -> "If a special move hit you this round, deals double that damage back; otherwise it fails."
            "metalburst", "comeuppance" -> "If the target hit you this round, deals 1.5 times that damage back; otherwise it fails."

            "fakeout", "firstimpression" -> "Only works on your first turn in the fight."
            "suckerpunch", "thunderclap" -> "Fails unless the target has not moved yet and is about to use a damaging move."
            "focuspunch" -> "You lose your focus and can't move if you were hit earlier this round."
            "dreameater" -> "Fails unless the target is asleep."
            "snore" -> "Fails unless you are asleep."
            "poltergeist" -> "Fails unless the target holds an item. Wild Pokémon hold no items, so your Pokémon never uses it."
            "synchronoise" -> "Fails unless the target shares a type with you."
            "lastresort" -> "Fails unless you have used all your other moves in this fight."
            "futuresight", "doomdesire" -> "Hits the target 2 rounds after it is used, with the damage worked out when it is used; only one can wait for each side."

            "hiddenpower" -> "Its type depends on your IVs."
            "revelationdance" -> "Takes your first type."
            "freezedry" -> "Super effective against Water types."
            "flyingpress" -> "Its damage also uses the Flying type's effectiveness against the target."
            else -> return emptyList()
        }
        return listOf(text)
    }
}
