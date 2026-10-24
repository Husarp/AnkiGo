package com.husarp.ankigo.game

import kotlin.math.max
import kotlin.math.min

/**
 * R17C rule set "SideMoves" (R17C-5, PLAN 229): the status moves whose effect is code in the games and was missing here.
 * - Protect family (Protect, Detect, King's Shield, Spiky Shield, Baneful Bunker, Burning Bulwark, Silk Trap, Obstruct, Endure, Quick Guard,
 *   Wide Guard, Crafty Shield, Mat Block): one round long, success 1/3, 1/9 ... when used again in a row, `breaksProtect` moves lift them.
 * - Substitute: its HP is `volatiles["substitute"]`; it takes the hits (`takeDamage`), stops the foe's status moves and secondaries.
 * - Side conditions in `conds(p)` (they survive a switch): Reflect, Light Screen, Aurora Veil, Safeguard, Mist, Tailwind, Wish.
 * - Lasting effects: Leech Seed, Curse (both kinds), Nightmare, Yawn, Destiny Bond, Aqua Ring, Ingrain.
 * - Heals and cures written as code: Moonlight / Morning Sun / Synthesis / Shore Up (weather), Rest, Wish, Stockpile / Swallow / Spit Up,
 *   Strength Sap, Pain Split, Jungle Healing, Lunar Blessing, Heal Bell, Aromatherapy, Refresh, Take Heart.
 * - Stat moves with an HP cost: Belly Drum, Fillet Away, Clangorous Soul; Haze.
 *
 * Keys are lower case Showdown ids so other sets can read them (see the constants). A "turn" is one battle round: durations count down at the
 * end of a round ([endOfRound]); a switch's free hit is not a round. Both sides follow the same rules ([p] = the Main's side). Hooks other
 * sets may want: [protecting], [passesSubstitute] (an ability like Infiltrator would extend it) and [healBlocked].
 */
internal object SideMoves : BattleRules {
    // ---- keys of BattleSide.volatiles ----
    /** The substitute's HP. */
    const val SUBSTITUTE = "substitute"
    const val LEECH_SEED = "leechseed"
    const val CURSE = "curse"
    const val NIGHTMARE = "nightmare"
    /** Rounds left until the yawning one falls asleep (2 at the start, so it happens at the end of the next round). */
    const val YAWN = "yawn"
    /** 1 from Destiny Bond until the user's next action begins. */
    const val DESTINY_BOND = "destinybond"
    const val AQUA_RING = "aquaring"
    const val INGRAIN = "ingrain"
    /** Layers (1..[MAX_STOCKPILE]) of Stockpile. */
    const val STOCKPILE = "stockpile"
    /** The Pokémon survives every hit of this round with 1 HP. */
    const val ENDURE = "endure"
    /** The protecting moves, as the volatile each puts on its user for the rest of the round (Detect uses "protect"). */
    val PROTECTIONS = listOf(
        "protect", "kingsshield", "spikyshield", "banefulbunker", "burningbulwark", "silktrap", "obstruct",
        "quickguard", "wideguard", "craftyshield", "matblock",
    )
    private const val STOCK_DEF = "sm.stockdef"   // the Defense / Sp. Def stages the stockpile really gave (taken back at the end)
    private const val STOCK_SPD = "sm.stockspd"
    private const val STALL = "sm.stall"         // successful uses of a protecting move in a row
    private const val STALLED = "sm.stalled"     // it used one in this round
    private const val SUB_HIT = "sm.subhit"      // the last hit it took went into its substitute: the move's secondaries don't reach the Pokémon

    // ---- keys of the side conditions ([BattleView.conds]): rounds left ----
    const val REFLECT = "reflect"
    const val LIGHT_SCREEN = "lightscreen"
    const val AURORA_VEIL = "auroraveil"
    const val SAFEGUARD = "safeguard"
    const val MIST = "mist"
    const val TAILWIND = "tailwind"
    /** Rounds until the wish comes true (2 at the start); the HP it heals is in [WISH_HP]. */
    const val WISH = "wish"
    private const val WISH_HP = "sm.wishhp"

    // ---- numbers (mainline; a "turn" is a round) ----
    /** A protecting move works with chance 1 in [STALL_BASE]^n after n uses in a row; it stops growing at [STALL_BASE]^[STALL_MAX_N] (mainline 729). */
    private const val STALL_BASE = 3
    private const val STALL_MAX_N = 6
    /** Spiky Shield: a contact attacker loses 1/8 of its max HP. */
    private const val SPIKY_DIVISOR = 8
    /** Substitute costs 1/4 of the max HP and has that much HP. */
    const val SUBSTITUTE_DIVISOR = 4   // R17C-10: not private, the guide reads it
    const val SCREEN_TURNS = 5   // R17C-10: not private, the guide reads it
    private const val SAFEGUARD_TURNS = 5
    private const val MIST_TURNS = 5
    private const val TAILWIND_TURNS = 4
    private const val SCREEN_MULT = 0.5
    private const val TAILWIND_MULT = 2.0
    /** Leech Seed and Aqua Ring / Ingrain: 1/8 and 1/16 of the max HP at the end of each round. */
    private const val SEED_DIVISOR = 8
    private const val RING_DIVISOR = 16
    /** Curse by a Ghost type: the user loses 1/2 of its max HP, the target 1/4 at the end of each round. Nightmare: 1/4. */
    private const val CURSE_COST_DIVISOR = 2
    private const val CURSE_DIVISOR = 4
    private const val NIGHTMARE_DIVISOR = 4
    private const val YAWN_ROUNDS = 2
    private const val WISH_ROUNDS = 2
    private const val WISH_DIVISOR = 2
    private const val MAX_STOCKPILE = 3
    private const val SPIT_UP_POWER = 100
    private val SWALLOW_HEALS = listOf(1 to 4, 1 to 2, 1 to 1)
    /** Rest: asleep for exactly this many turns (R17C-3's `sleepTurns` rule: this many attempts, the next one wakes). */
    const val REST_SLEEP = 2   // R17C-10: not private, StatusInfo reads it
    private val MOON_HEAL = 1 to 2
    private val SUN_HEAL = 2 to 3
    private val DULL_WEATHER_HEAL = 1 to 4     // rain, sand, hail, snow
    private val SAND_SHORE_HEAL = 2 to 3       // Shore Up in a sandstorm
    private val JUNGLE_HEAL = 1 to 4
    private const val BELLY_DRUM_DIVISOR = 2
    private const val FILLET_DIVISOR = 2
    private const val CLANGOROUS_DIVISOR = 3

    // ---- move lists ----
    /** Each stalling move -> the volatile it puts on its user. */
    private val STALLING: Map<String, String> = mapOf(
        "protect" to "protect", "detect" to "protect", "kingsshield" to "kingsshield", "spikyshield" to "spikyshield",
        "banefulbunker" to "banefulbunker", "burningbulwark" to "burningbulwark", "silktrap" to "silktrap", "obstruct" to "obstruct",
        "endure" to ENDURE, "quickguard" to "quickguard", "wideguard" to "wideguard", "craftyshield" to "craftyshield", "matblock" to "matblock",
    )
    private val SCREEN_BREAKERS = setOf("brickbreak", "psychicfangs", "ragingbull")
    private val SPINNERS = setOf("rapidspin", "mortalspin")
    /** Moves that heal and so are stopped by Heal Block. */
    private val HEAL_MOVES = setOf("moonlight", "morningsun", "synthesis", "shoreup", "rest", "wish", "swallow", "strengthsap", "junglehealing", "lunarblessing")
    /** The targets that mean "aimed at the foe" in the data (the engine treats every non-self target as aimed at the foe, also "all" and "allyTeam"). */
    private val FOE_TARGETS = setOf("normal", "any", "adjacentFoe", "allAdjacentFoes", "allAdjacent", "randomNormal", "scripted")
    private val WIDE_TARGETS = setOf("allAdjacent", "allAdjacentFoes")
    /** Side condition -> (name, the line when it ends). */
    private val SIDE_ENDS: Map<String, Pair<String, String>> = linkedMapOf(
        REFLECT to ("Reflect" to "wore off"), LIGHT_SCREEN to ("Light Screen" to "wore off"), AURORA_VEIL to ("Aurora Veil" to "wore off"),
        SAFEGUARD to ("Safeguard" to "wore off"), MIST to ("Mist" to "faded"), TAILWIND to ("Tailwind" to "petered out"),
    )
    private val STATUS_NAMES = mapOf("brn" to "burn", "par" to "paralysis", "psn" to "poisoning", "tox" to "poisoning", "slp" to "sleep", "frz" to "freeze")

    override val handles: Set<String> = STALLING.keys + setOf(
        "substitute", "reflect", "lightscreen", "auroraveil", "safeguard", "mist", "tailwind",
        "leechseed", "curse", "nightmare", "yawn", "destinybond",
        "moonlight", "morningsun", "synthesis", "shoreup", "rest", "wish", "stockpile", "swallow", "strengthsap", "painsplit",
        "aquaring", "ingrain", "junglehealing", "lunarblessing", "healbell", "aromatherapy", "refresh", "takeheart",
        "bellydrum", "filletaway", "clangoroussoul", "haze",
    )

    // ---------------------------------------------------------------- reading the fight (other sets may use these)

    /** The volatile key of the protecting move that is up on [tp] (one of [PROTECTIONS]), or null. */
    fun protecting(v: BattleView, tp: Boolean): String? = PROTECTIONS.firstOrNull { it in v.side(tp).volatiles }

    /** A move gets through a substitute: a sound move or one with the `bypasssub` flag (an ability like Infiltrator would add to this). */
    fun passesSubstitute(move: Move): Boolean = "bypasssub" in move.flags || "sound" in move.flags

    /**
     * R17C FIX-A: Curse of a user that is not a Ghost type is a move on the user itself (+1 Attack / Defense, -1 Speed) although the data's target says
     * "normal": Good as Gold, Prankster against a Dark type and Crafty Shield must not treat it as a move aimed at the other Pokémon.
     */
    fun isSelfCurse(v: BattleView, p: Boolean, move: Move): Boolean = move.id == "curse" && Type.GHOST !in v.types(p)

    /** R17C FIX-A: a status [move] of [p] is aimed at the other Pokémon (not a field move, not a move on the user's own side, not a non-Ghost Curse). */
    fun aimedAtFoe(v: BattleView, p: Boolean, move: Move): Boolean = !move.damaging && move.target in FOE_TARGETS && !isSelfCurse(v, p, move)

    /** Heal Block (R17C-8 counts it down; Psychic Noise starts it) is on [p]: it gets none of the heals of this set. */
    fun healBlocked(v: BattleView, p: Boolean): Boolean = (v.side(p).volatiles[CoreRules.HEAL_BLOCK] ?: 0) > 0

    private fun up(v: BattleView, p: Boolean, key: String): Boolean = (v.conds(p)[key] ?: 0) > 0

    private fun stalls(v: BattleView, p: Boolean): Int = v.side(p).volatiles[STALL] ?: 0

    private fun layers(v: BattleView, p: Boolean): Int = v.side(p).volatiles[STOCKPILE] ?: 0

    private fun pow(base: Int, n: Int): Int { var x = 1; repeat(n) { x *= base }; return x }

    // ---------------------------------------------------------------- status moves

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        if (move.id in HEAL_MOVES && healBlocked(r, p)) {
            r.fx(p, "${r.mon(p).name} can't heal while Heal Block lasts!", EffectKind.BLOCKED, CoreRules.HEAL_BLOCK, label = "Heal blocked")
            return false
        }
        return when (move.id) {
            in STALLING -> stallMove(r, p, move)
            "substitute" -> substitute(r, p)
            "reflect", "lightscreen", "auroraveil" -> screen(r, p, move)
            "safeguard" -> sideCondition(r, p, SAFEGUARD, SAFEGUARD_TURNS, "${r.mon(p).name}'s team became cloaked in a mystical veil!", "Safeguard")
            "mist" -> sideCondition(r, p, MIST, MIST_TURNS, "${r.mon(p).name}'s team became shrouded in mist!", "Mist")
            "tailwind" -> sideCondition(r, p, TAILWIND, TAILWIND_TURNS, "The tailwind blew from behind ${r.mon(p).name}'s team!", "Tailwind")
            "leechseed" -> leechSeed(r, p)
            "curse" -> curse(r, p)
            "nightmare" -> nightmare(r, p)
            "yawn" -> yawn(r, p)
            "destinybond" -> destinyBond(r, p)
            "moonlight", "morningsun", "synthesis", "shoreup" -> weatherHeal(r, p, move)
            "rest" -> rest(r, p)
            "wish" -> wish(r, p)
            "stockpile" -> stockpile(r, p)
            "swallow" -> swallow(r, p)
            "strengthsap" -> strengthSap(r, p)
            "painsplit" -> painSplit(r, p)
            "aquaring" -> lasting(r, p, AQUA_RING, "${r.mon(p).name} surrounded itself with a veil of water!", "Aqua Ring")
            "ingrain" -> lasting(r, p, INGRAIN, "${r.mon(p).name} planted its roots!", "Ingrain")
            "junglehealing", "lunarblessing" -> healAndCure(r, p)
            "healbell" -> cureAll(r, p, "A bell chimed.", null)
            "aromatherapy" -> cureAll(r, p, "A soothing aroma wafted through the air!", null)
            "refresh" -> cureAll(r, p, null, setOf("brn", "par", "psn", "tox"))
            "takeheart" -> takeHeart(r, p)
            "bellydrum" -> bellyDrum(r, p)
            "filletaway" -> payThenBoost(r, p, move, FILLET_DIVISOR)
            "clangoroussoul" -> payThenBoost(r, p, move, CLANGOROUS_DIVISOR)
            "haze" -> haze(r, p)
            else -> null
        }
    }

    // ---- Protect family ----

    private fun stallMove(r: Round, p: Boolean, move: Move): Boolean {
        val s = r.side(p)
        val n = s.volatiles[STALL] ?: 0
        if (move.id == "matblock" && !MovePower.isFirstAction(r, p)) return false // only on its first action (it does not count as a use)
        s.volatiles[STALLED] = 1
        if (n > 0 && r.rng.nextInt(pow(STALL_BASE, min(n, STALL_MAX_N))) != 0) { s.volatiles.remove(STALL); return false }
        s.volatiles[STALL] = n + 1
        val key = STALLING.getValue(move.id)
        s.volatiles[key] = 1
        val me = r.mon(p).name
        r.fx(p, when (move.id) {
            "kingsshield" -> "$me braced behind its king's shield."
            "spikyshield" -> "$me put up a spiky shield."
            "banefulbunker" -> "$me took cover in a baneful bunker."
            "burningbulwark" -> "$me took cover behind a burning bulwark."
            "silktrap" -> "$me spun a silk trap around itself."
            "obstruct" -> "$me put up an obstruction."
            "endure" -> "$me braced itself!"
            "quickguard" -> "Quick Guard protects $me's team from priority moves."
            "wideguard" -> "Wide Guard protects $me's team from wide-ranging attacks."
            "craftyshield" -> "Crafty Shield protects $me's team from status moves."
            "matblock" -> "$me flipped up a mat to block damaging moves."
            else -> "$me is protecting itself."
        }, EffectKind.VOLATILE, key, 1)
        return true
    }

    /** True when the protection [key] on the target's side stops [move] of [p]. */
    private fun stops(r: Round, p: Boolean, key: String, move: Move): Boolean {
        val flagged = "protect" in move.flags && !Abilities.breaksProtection(r, p, move) // R17C-9b: Unseen Fist
        return when (key) {
            "protect", "spikyshield", "banefulbunker", "burningbulwark" -> flagged
            "kingsshield", "silktrap", "obstruct", "matblock" -> flagged && move.damaging
            "quickguard" -> flagged && move.priority + rulesSum { it.priority(r, p, move) } > 0
            "wideguard" -> flagged && move.target in WIDE_TARGETS
            "craftyshield" -> !move.damaging && move.target in FOE_TARGETS && !isSelfCurse(r, p, move)
            else -> false
        }
    }

    override fun blocks(r: Round, p: Boolean, move: Move): Boolean {
        val tp = !p
        val ts = r.side(tp)
        if (move.breaksProtect) breakProtection(r, p, move)
        else {
            val key = PROTECTIONS.firstOrNull { it in ts.volatiles && stops(r, p, it, move) }
            if (key != null) { protect(r, p, key, move); return true }
        }
        // a substitute stops the foe's status moves (sound moves and `bypasssub` ones go through)
        if (SUBSTITUTE in ts.volatiles && !move.damaging && move.target in FOE_TARGETS && !isSelfCurse(r, p, move) && !passesSubstitute(move) && !Abilities.infiltrates(r, p)) { // R17C-9a: Infiltrator
            r.fx(p, "But it failed!")
            return true
        }
        return false
    }

    /** The target's protection stopped [move]: the line, then what a contact attacker gets for it. */
    private fun protect(r: Round, p: Boolean, key: String, move: Move) {
        val tp = !p
        val tn = r.mon(tp).name
        r.fx(tp, when (key) {
            "quickguard" -> "Quick Guard protected $tn's team!"
            "wideguard" -> "Wide Guard protected $tn's team!"
            "craftyshield" -> "Crafty Shield protected $tn's team!"
            "matblock" -> "${move.name} was blocked by the mat!"
            else -> "$tn protected itself!"
        }, EffectKind.BLOCKED, key, label = "Protected")
        if (!Abilities.contacts(r, p, move)) return // R17C-9b: Long Reach
        val n = r.mon(p).name
        when (key) {
            "kingsshield" -> r.boost(p, mapOf("atk" to -1), byFoe = true)
            "spikyshield" -> r.hurt(p, max(1, r.maxHp(p) / SPIKY_DIVISOR), "$n was hurt!", EffectKind.RECOIL, source = Indirect.SPIKY)
            "banefulbunker" -> r.setStatus(p, "psn", Type.POISON, byFoe = true, quiet = true)
            "burningbulwark" -> r.setStatus(p, "brn", Type.FIRE, byFoe = true, quiet = true)
            "silktrap" -> r.boost(p, mapOf("spe" to -1), byFoe = true)
            "obstruct" -> r.boost(p, mapOf("def" to -2), byFoe = true)
        }
    }

    /** Feint, Phantom Force ...: the move goes through and lifts the protection of the target. */
    private fun breakProtection(r: Round, p: Boolean, move: Move) {
        val tp = !p
        val v = r.side(tp).volatiles
        var broke = false
        for (key in PROTECTIONS) if (v.remove(key) != null) broke = true
        if (!broke) return
        val tn = r.mon(tp).name
        r.fx(tp, if (move.id == "feint") "$tn fell for the feint!" else "${r.mon(p).name} broke through $tn's protection!", EffectKind.VOLATILE, "protect", -1)
    }

    // ---- Substitute ----

    private fun substitute(r: Round, p: Boolean): Boolean {
        val s = r.side(p)
        val n = r.mon(p).name
        val cost = r.maxHp(p) / SUBSTITUTE_DIVISOR
        if (SUBSTITUTE in s.volatiles) { r.fx(p, "$n already has a substitute!"); return false }
        // R17C FIX (manager): a Pokémon with 1 max HP (Shedinja) can't pay a quarter of it: it fails, as in the games
        if (r.mon(p).hp <= cost || r.maxHp(p) == 1) { r.fx(p, "But it does not have enough HP left to make a substitute!"); return false }
        r.hurt(p, cost, "$n lost $cost HP.", EffectKind.RESIDUAL)
        s.volatiles[SUBSTITUTE] = max(1, cost)
        r.fx(p, "$n put in a substitute!", EffectKind.VOLATILE, SUBSTITUTE, 1, label = "Substitute")
        return true
    }

    // ---- screens and the other side conditions ----

    private fun screen(r: Round, p: Boolean, move: Move): Boolean {
        val n = r.mon(p).name
        val c = r.conds(p)
        if ((c[move.id] ?: 0) > 0) return false
        if (move.id == "auroraveil" && r.weather() != "hail" && r.weather() != "snow") return false
        c[move.id] = SCREEN_TURNS
        val (name, what) = when (move.id) {
            "reflect" -> "Reflect" to "physical"
            "lightscreen" -> "Light Screen" to "special"
            else -> "Aurora Veil" to "physical and special"
        }
        r.fx(p, "$name made $n's team stronger against $what moves!", EffectKind.FIELD, move.id, 1, label = name)
        return true
    }

    private fun sideCondition(r: Round, p: Boolean, key: String, turns: Int, text: String, label: String): Boolean {
        val c = r.conds(p)
        if ((c[key] ?: 0) > 0) return false
        c[key] = turns
        r.fx(p, text, EffectKind.FIELD, key, 1, label = label)
        return true
    }

    // ---- lasting effects ----

    private fun leechSeed(r: Round, p: Boolean): Boolean {
        val tp = !p
        return r.addVolatile(tp, LEECH_SEED, 1, byFoe = true, text = "${r.mon(tp).name} was seeded!", label = "Seeded")
    }

    private fun curse(r: Round, p: Boolean): Boolean {
        val n = r.mon(p).name
        if (Type.GHOST !in r.types(p)) return r.boost(p, mapOf("atk" to 1, "def" to 1, "spe" to -1), byFoe = false)
        val tp = !p
        if (CURSE in r.side(tp).volatiles) return false
        r.hurt(p, max(1, r.maxHp(p) / CURSE_COST_DIVISOR), "$n cut its own HP!", EffectKind.RECOIL) // R17C FIX (manager): at least 1 HP, as in the games
        return r.addVolatile(tp, CURSE, 1, byFoe = true, text = "${r.mon(tp).name} was cursed!", label = "Cursed")
    }

    private fun nightmare(r: Round, p: Boolean): Boolean {
        val tp = !p
        if (r.side(tp).status != "slp" && !AbilitiesMore.comatose(r, tp)) return false // R17C FIX-A: Comatose counts as asleep
        return r.addVolatile(tp, NIGHTMARE, 1, byFoe = true, text = "${r.mon(tp).name} began having a nightmare!", label = "Nightmare")
    }

    private fun yawn(r: Round, p: Boolean): Boolean {
        val tp = !p
        val ts = r.side(tp)
        if (ts.status != null || YAWN in ts.volatiles) return false
        // a Pokémon that could not fall asleep now (an uproar, a terrain, an ability) is not made drowsy
        val why = rulesFirst { it.blockStatus(r, tp, "slp", false) }
        if (why != null) { if (why.isNotEmpty()) r.fx(tp, why, EffectKind.BLOCKED, "slp", label = "Blocked"); return false }
        return r.addVolatile(tp, YAWN, YAWN_ROUNDS, byFoe = true, text = "${r.mon(tp).name} grew drowsy!", label = "Drowsy")
    }

    private fun destinyBond(r: Round, p: Boolean): Boolean {
        val s = r.side(p)
        if (s.volatiles.remove(DESTINY_BOND) != null) return false // used twice in a row: it fails (and is gone)
        s.volatiles[DESTINY_BOND] = 1
        r.fx(p, "${r.mon(p).name} is trying to take its foe down with it!", EffectKind.VOLATILE, DESTINY_BOND, 1, label = "Destiny Bond")
        return true
    }

    /** Aqua Ring, Ingrain: a condition of the user that heals it at the end of each round. */
    private fun lasting(r: Round, p: Boolean, key: String, text: String, label: String): Boolean {
        val s = r.side(p)
        if (key in s.volatiles) return false
        s.volatiles[key] = 1
        r.fx(p, text, EffectKind.VOLATILE, key, 1, label = label)
        return true
    }

    // ---- heals and cures ----

    private fun healBy(r: Round, p: Boolean, frac: Pair<Int, Int>): Boolean {
        if (r.mon(p).hp >= r.maxHp(p)) return false
        r.heal(p, max(1, r.maxHp(p) * frac.first / frac.second), "${r.mon(p).name} regained health!")
        return true
    }

    private fun weatherHeal(r: Round, p: Boolean, move: Move): Boolean {
        val w = r.weather()
        val frac = if (move.id == "shoreup") { if (w == "sand") SAND_SHORE_HEAL else MOON_HEAL }
        else when (w) { "sun" -> SUN_HEAL; "rain", "sand", "hail", "snow" -> DULL_WEATHER_HEAL; else -> MOON_HEAL }
        return healBy(r, p, frac)
    }

    /** Cures [p]'s status (only one of [only] when given) with its line. True = it had one. */
    private fun cure(r: Round, p: Boolean, only: Set<String>? = null): Boolean {
        val s = r.side(p)
        val st = s.status ?: return false
        if (only != null && st !in only) return false
        s.status = null; s.statusTurns = 0; s.volatiles.remove(CoreRules.SLEEP_TURNS)
        val n = r.mon(p).name
        r.fx(p, when (st) {
            "slp" -> "$n woke up!"
            "frz" -> "$n thawed out!"
            else -> "$n was cured of its ${STATUS_NAMES[st] ?: st}!"
        }, EffectKind.STATUS_CURED, st)
        return true
    }

    private fun cureAll(r: Round, p: Boolean, line: String?, only: Set<String>?): Boolean {
        if (r.side(p).status == null || (only != null && r.side(p).status !in only)) return false
        if (line != null) r.fx(p, line)
        return cure(r, p, only)
    }

    private fun healAndCure(r: Round, p: Boolean): Boolean {
        val healed = healBy(r, p, JUNGLE_HEAL)
        return cure(r, p) || healed
    }

    private fun takeHeart(r: Round, p: Boolean): Boolean {
        val rose = r.boost(p, mapOf("spa" to 1, "spd" to 1), byFoe = false)
        return cure(r, p) || rose
    }

    private fun rest(r: Round, p: Boolean): Boolean {
        val s = r.side(p)
        if (s.status == "slp" || r.mon(p).hp >= r.maxHp(p)) return false
        val why = rulesFirst { it.blockStatus(r, p, "slp", false) }
        if (why != null) { if (why.isNotEmpty()) r.fx(p, why, EffectKind.BLOCKED, "slp", label = "Blocked"); return false }
        val oldStatus = s.status; val oldTurns = s.statusTurns
        s.status = null; s.statusTurns = 0
        if (!r.setStatus(p, "slp", Type.NORMAL, byFoe = false)) { s.status = oldStatus; s.statusTurns = oldTurns; return false }
        s.volatiles[CoreRules.SLEEP_TURNS] = REST_SLEEP // AFTER setStatus (R17C-3)
        r.heal(p, r.maxHp(p) - r.mon(p).hp, "${r.mon(p).name} regained health!")
        return true
    }

    private fun wish(r: Round, p: Boolean): Boolean {
        val c = r.conds(p)
        if ((c[WISH] ?: 0) > 0) return false
        c[WISH] = WISH_ROUNDS
        c[WISH_HP] = max(1, r.maxHp(p) / WISH_DIVISOR)
        r.fx(p, "${r.mon(p).name} made a wish!", EffectKind.FIELD, WISH, 1, label = "Wish")
        return true
    }

    private fun stockpile(r: Round, p: Boolean): Boolean {
        val s = r.side(p)
        val have = layers(r, p)
        if (have >= MAX_STOCKPILE) return false
        s.volatiles[STOCKPILE] = have + 1
        r.fx(p, "${r.mon(p).name} stockpiled ${have + 1}!", EffectKind.VOLATILE, STOCKPILE, 1, label = "Stockpile ${have + 1}")
        val def0 = s.boosts[1]; val spd0 = s.boosts[3]
        r.boost(p, mapOf("def" to 1, "spd" to 1), byFoe = false)
        s.volatiles[STOCK_DEF] = (s.volatiles[STOCK_DEF] ?: 0) + (s.boosts[1] - def0)
        s.volatiles[STOCK_SPD] = (s.volatiles[STOCK_SPD] ?: 0) + (s.boosts[3] - spd0)
        return true
    }

    /** Ends the stockpile of [p] and takes back the Defense / Sp. Def stages it gave. */
    private fun endStockpile(r: Round, p: Boolean) {
        val s = r.side(p)
        if (s.volatiles.remove(STOCKPILE) == null) return
        val def = s.volatiles.remove(STOCK_DEF) ?: 0
        val spd = s.volatiles.remove(STOCK_SPD) ?: 0
        r.fx(p, "${r.mon(p).name}'s stockpile effect wore off!", EffectKind.VOLATILE, STOCKPILE, -1)
        val back = HashMap<String, Int>()
        if (def > 0) back["def"] = -def
        if (spd > 0) back["spd"] = -spd
        if (back.isNotEmpty()) r.boost(p, back, byFoe = false)
    }

    private fun swallow(r: Round, p: Boolean): Boolean {
        val have = layers(r, p)
        if (have <= 0) return false
        val healed = healBy(r, p, SWALLOW_HEALS[min(have, SWALLOW_HEALS.size) - 1])
        endStockpile(r, p) // used up even when nothing could be healed
        return healed
    }

    private fun strengthSap(r: Round, p: Boolean): Boolean {
        val tp = !p
        val ts = r.side(tp)
        if (ts.boosts[0] <= -6) return false
        val atk = max(1, (r.stats(tp).atk * BattleEngine.stageMult(ts.boosts[0])).toInt())
        val healed = r.mon(p).hp < r.maxHp(p)
        if (healed) r.heal(p, atk, "${r.mon(p).name} sapped strength and regained health!")
        val lowered = r.boost(tp, mapOf("atk" to -1), byFoe = true)
        return healed || lowered
    }

    private fun painSplit(r: Round, p: Boolean): Boolean {
        val tp = !p
        val avg = max(1, (r.mon(p).hp + r.mon(tp).hp) / 2)
        r.fx(p, "The battlers shared their pain!")
        for (q in listOf(p, tp)) {
            val m = r.mon(q)
            val goal = min(avg, r.maxHp(q))
            if (goal > m.hp) r.heal(q, goal - m.hp, "${m.name} regained ${goal - m.hp} HP.")
            else if (goal < m.hp) r.hurt(q, m.hp - goal, "${m.name} lost ${m.hp - goal} HP.", EffectKind.RESIDUAL)
        }
        return true
    }

    // ---- stat moves with a cost ----

    private fun bellyDrum(r: Round, p: Boolean): Boolean {
        val s = r.side(p)
        if (r.mon(p).hp <= r.maxHp(p) / BELLY_DRUM_DIVISOR || s.boosts[0] >= 6) return false
        r.hurt(p, r.maxHp(p) / BELLY_DRUM_DIVISOR, "${r.mon(p).name} cut its own HP!", EffectKind.RECOIL)
        r.boost(p, mapOf("atk" to 12), byFoe = false)
        return true
    }

    /** Fillet Away, Clangorous Soul: the HP cost first, then the rises of the move's data; they fail when the HP is too low or nothing could rise. */
    private fun payThenBoost(r: Round, p: Boolean, move: Move, divisor: Int): Boolean {
        val rises = move.boosts ?: return false
        val s = r.side(p)
        if (r.mon(p).hp <= r.maxHp(p) / divisor) return false
        val canRise = rises.any { (key, v) ->
            val i = BattleEngine.BOOST_KEYS.indexOf(key)
            i >= 0 && v > 0 && s.boosts[i] < r.moveChoice.stageLimit(i)
        }
        if (!canRise) return false
        r.hurt(p, r.maxHp(p) / divisor, "${r.mon(p).name} cut its own HP!", EffectKind.RECOIL)
        r.boost(p, rises, byFoe = false)
        return true
    }

    private fun haze(r: Round, p: Boolean): Boolean {
        for (q in listOf(p, !p)) java.util.Arrays.fill(r.side(q).boosts, 0)
        r.fx(p, "All stat changes were eliminated!", EffectKind.FIELD, "haze", 0, label = "Haze")
        return true
    }

    // ---------------------------------------------------------------- Spit Up (a damaging move)

    override fun failReason(r: Round, p: Boolean, move: Move): String? =
        if (move.id == "spitup" && layers(r, p) <= 0) "But it failed!" else null

    override fun basePower(h: Hit): Int? =
        if (h.move.id == "spitup") SPIT_UP_POWER * max(1, layers(h.view, h.attackerIsMain)) else null

    // ---------------------------------------------------------------- hits taken: substitute, Endure

    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        val s = r.side(tp)
        val n = r.mon(tp).name
        s.volatiles.remove(SUB_HIT) // it reflects the last hit only
        val sub = s.volatiles[SUBSTITUTE]
        if (sub != null && damage > 0 && !passesSubstitute(h.move) && !Abilities.infiltrates(r, h.attackerIsMain)) { // R17C-9a: Infiltrator
            val taken = min(damage, sub)
            r.absorbed = taken
            s.volatiles[SUB_HIT] = 1
            if (taken >= sub) {
                s.volatiles.remove(SUBSTITUTE)
                r.fx(tp, "$n's substitute faded!", EffectKind.VOLATILE, SUBSTITUTE, 0, label = "Substitute broke")
            } else {
                s.volatiles[SUBSTITUTE] = sub - taken
                r.fx(tp, "The substitute took damage for $n!", EffectKind.VOLATILE, SUBSTITUTE, 0)
            }
            return 0
        }
        if (ENDURE in s.volatiles && damage >= r.mon(tp).hp) {
            r.fx(tp, "$n endured the hit!", EffectKind.VOLATILE, ENDURE, 0, label = "Endured")
            return max(0, r.mon(tp).hp - 1)
        }
        return damage
    }

    /** Reflect, Light Screen and Aurora Veil halve the hit (not for the moves that break them); one halving at most. */
    override fun finalMod(h: Hit): Double {
        if (h.move.id in SCREEN_BREAKERS || Abilities.infiltrates(h.view, h.attackerIsMain)) return 1.0 // R17C-9a: Infiltrator goes through the screens
        val tp = !h.attackerIsMain
        val v = h.view
        return if (up(v, tp, AURORA_VEIL) || (h.physical && up(v, tp, REFLECT)) || (!h.physical && up(v, tp, LIGHT_SCREEN))) SCREEN_MULT else 1.0
    }

    // ---------------------------------------------------------------- statuses, stages, immunity

    /** The foe's secondaries don't reach a Pokémon whose substitute took the hit; Safeguard stops a status from the foe. */
    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? {
        if (!byFoe) return null
        if (SUB_HIT in r.side(tp).volatiles) return ""
        if (up(r, tp, SAFEGUARD) && !Abilities.infiltrates(r, !tp)) return "${r.mon(tp).name}'s team is protected by Safeguard!" // R17C-9a: Infiltrator
        return null
    }

    /** Safeguard also stops confusion and Yawn from the foe. */
    override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? {
        if (!byFoe) return null
        if (SUB_HIT in r.side(tp).volatiles) return ""
        if ((key == CoreRules.CONFUSION || key == YAWN) && up(r, tp, SAFEGUARD) && !Abilities.infiltrates(r, !tp)) return "${r.mon(tp).name}'s team is protected by Safeguard!"
        return null
    }

    /** Mist: the foe can't lower the stats; a substitute that took the hit keeps its Pokémon's stats safe too. */
    override fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean): Int {
        if (!byFoe || delta >= 0) return delta
        if (SUB_HIT in r.side(tp).volatiles) return 0
        if (up(r, tp, MIST) && !Abilities.infiltrates(r, !tp)) { // R17C-9a: Infiltrator
            r.fx(tp, "${r.mon(tp).name} is protected by the mist!", EffectKind.BLOCKED, key, label = "Blocked")
            return 0
        }
        return delta
    }

    /** Leech Seed does not work on a Grass type. */
    override fun immune(r: Round, h: Hit): Boolean {
        if (h.move.id == "leechseed" && Type.GRASS in h.dTypes) {
            r.fx(h.attackerIsMain, "It doesn't affect ${h.defender.name}…")
            return true
        }
        return false
    }

    /** Ingrain roots the user to the ground. */
    override fun grounded(v: BattleView, p: Boolean, g: Boolean): Boolean = g || INGRAIN in v.side(p).volatiles

    /** Tailwind doubles the Speed of the team. */
    override fun speedMod(v: BattleView, p: Boolean): Double = if (up(v, p, TAILWIND)) TAILWIND_MULT else 1.0

    // ---------------------------------------------------------------- around the actions

    /** Destiny Bond ends when its user begins another action (one that could not move keeps it). */
    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        if (move.id != "destinybond") r.side(p).volatiles.remove(DESTINY_BOND)
        return true
    }

    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        when (move.id) {
            in SCREEN_BREAKERS -> shatterScreens(r, tp)
            in SPINNERS -> if (r.mon(p).hp > 0) freeFromBinding(r, p)
        }
        if (damage > 0 && r.mon(tp).hp <= 0 && DESTINY_BOND in r.side(tp).volatiles && r.mon(p).hp > 0) {
            r.fx(tp, "${r.mon(tp).name} took its attacker down with it!", EffectKind.VOLATILE, DESTINY_BOND, 0, label = "Destiny Bond")
            r.mon(p).hp = 0
        }
    }

    private fun shatterScreens(r: Round, tp: Boolean) {
        val c = r.conds(tp)
        for (key in listOf(REFLECT, LIGHT_SCREEN, AURORA_VEIL)) {
            if (c.remove(key) == null) continue
            r.fx(tp, "${r.mon(tp).name}'s ${SIDE_ENDS.getValue(key).first} was shattered!", EffectKind.FIELD, key, -1)
        }
    }

    /** Rapid Spin, Mortal Spin: the user is free of Leech Seed and of a binding move. */
    private fun freeFromBinding(r: Round, p: Boolean) {
        val s = r.side(p)
        val n = r.mon(p).name
        if (s.volatiles.remove(LEECH_SEED) != null) r.fx(p, "$n shed Leech Seed!", EffectKind.VOLATILE, LEECH_SEED, -1)
        if (s.volatiles.remove(CoreRules.BOUND) != null) {
            val name = s.notes.remove(CoreRules.BOUND)?.let { id -> r.data.move(id)?.name } ?: "the binding"
            r.fx(p, "$n got free of $name!", EffectKind.VOLATILE, CoreRules.BOUND, -1)
        }
    }

    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        r.side(!p).volatiles.remove(SUB_HIT) // the move is over: what it hit no longer shields the Pokémon from later effects
        if (move.id == "spitup") endStockpile(r, p)
    }

    /** R17C FIX-A: a Spit Up that another move called (Metronome ...) ends the stockpile too; what it hit no longer shields the target. */
    override fun afterCalledMove(r: Round, p: Boolean, move: Move) {
        r.side(!p).volatiles.remove(SUB_HIT)
        if (move.id == "spitup") endStockpile(r, p)
    }

    /** R17C FIX-A: a free hit is no round: the guards, Endure and the stall mark set in it end now (the chain of uses in a row keeps its count). */
    override fun afterFreeHit(r: Round) {
        for (q in listOf(true, false)) {
            val v = r.side(q).volatiles
            for (key in PROTECTIONS) v.remove(key)
            v.remove(ENDURE); v.remove(SUB_HIT); v.remove(STALLED)
        }
    }

    // ---------------------------------------------------------------- the end of a round

    override fun endOfRound(r: Round) {
        val order = if (r.speed(true) > r.speed(false)) listOf(true, false) else listOf(false, true)
        for (q in order) wishComesTrue(r, q)
        for (q in order) rootsAndRings(r, q)
        for (q in order) seedSaps(r, q)
        for (q in order) nightmareHurts(r, q)
        for (q in order) curseHurts(r, q)
        for (q in order) yawnTakesEffect(r, q)
        for (q in listOf(true, false)) countDown(r, q)
        for (q in listOf(true, false)) {
            val v = r.side(q).volatiles
            for (key in PROTECTIONS) v.remove(key)
            v.remove(ENDURE); v.remove(SUB_HIT)
            if (v.remove(STALLED) == null) v.remove(STALL) // a round without a protecting move ends the chain
        }
    }

    private fun wishComesTrue(r: Round, q: Boolean) {
        val c = r.conds(q)
        val left = c[WISH] ?: return
        if (left > 1) { c[WISH] = left - 1; return }
        val amount = c.remove(WISH_HP) ?: 0
        c.remove(WISH)
        val m = r.mon(q)
        if (m.hp > 0 && m.hp < r.maxHp(q) && !healBlocked(r, q)) r.heal(q, amount, "${m.name}'s wish came true!")
    }

    private fun rootsAndRings(r: Round, q: Boolean) {
        val v = r.side(q).volatiles
        val m = r.mon(q)
        for ((key, text) in listOf(AQUA_RING to "${m.name}'s Aqua Ring restored its HP.", INGRAIN to "${m.name} absorbed nutrients with its roots!")) {
            if (key !in v || m.hp <= 0 || m.hp >= r.maxHp(q) || healBlocked(r, q)) continue
            r.heal(q, max(1, r.maxHp(q) / RING_DIVISOR), text)
        }
    }

    private fun seedSaps(r: Round, q: Boolean) {
        val m = r.mon(q)
        if (LEECH_SEED !in r.side(q).volatiles || m.hp <= 0) return
        val before = m.hp
        r.hurt(q, max(1, r.maxHp(q) / SEED_DIVISOR), "${m.name}'s health is sapped by Leech Seed!", EffectKind.RESIDUAL, source = Indirect.SEED)
        val lost = before - m.hp
        val other = r.mon(!q)
        if (lost > 0 && other.hp > 0 && other.hp < r.maxHp(!q) && !healBlocked(r, !q)) r.drain(!q, lost, "${other.name} regained health!", EffectKind.HEAL)
    }

    private fun nightmareHurts(r: Round, q: Boolean) {
        val s = r.side(q)
        val m = r.mon(q)
        if (NIGHTMARE !in s.volatiles || m.hp <= 0) return
        if (s.status != "slp" && !AbilitiesMore.comatose(r, q)) { s.volatiles.remove(NIGHTMARE); return } // it woke up: the nightmare is over
        r.hurt(q, max(1, r.maxHp(q) / NIGHTMARE_DIVISOR), "${m.name} is locked in a nightmare!", EffectKind.RESIDUAL, source = Indirect.NIGHTMARE)
    }

    private fun curseHurts(r: Round, q: Boolean) {
        val m = r.mon(q)
        if (CURSE !in r.side(q).volatiles || m.hp <= 0) return
        r.hurt(q, max(1, r.maxHp(q) / CURSE_DIVISOR), "${m.name} is afflicted by the curse!", EffectKind.RESIDUAL, source = Indirect.CURSE)
    }

    private fun yawnTakesEffect(r: Round, q: Boolean) {
        val s = r.side(q)
        val left = s.volatiles[YAWN] ?: return
        if (left > 1) { s.volatiles[YAWN] = left - 1; return }
        s.volatiles.remove(YAWN)
        // the sleep is not "from the foe": Safeguard does not stop it (mainline), a terrain or an uproar still do
        if (r.mon(q).hp > 0 && s.status == null) r.setStatus(q, "slp", Type.NORMAL, byFoe = false)
    }

    private fun countDown(r: Round, q: Boolean) {
        val c = r.conds(q)
        for ((key, info) in SIDE_ENDS) {
            val left = c[key] ?: continue
            if (left > 1) { c[key] = left - 1; continue }
            c.remove(key)
            r.fx(q, "${r.mon(q).name}'s ${info.first} ${info.second}!", EffectKind.FIELD, key, -1)
        }
    }

    // ---------------------------------------------------------------- the move choice

    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        val me = v.mon(p)
        val s = v.side(p)
        val tp = !p
        val foe = v.mon(tp)
        val fs = v.side(tp)
        val maxHp = v.maxHp(p)
        val hurt = me.hp < maxHp && !healBlocked(v, p)
        return when (move.id) {
            // a guard that only stops one kind of move is only worth it against a foe that has such a move
            // R17C FIX-A: all of them need an action of the other side this round (v.acts): a guard in a round it idles is a wasted answer
            "quickguard" -> v.acts(tp) && stalls(v, p) == 0 && v.moves(tp).any { it.priority > 0 && "protect" in it.flags }
            "wideguard" -> v.acts(tp) && stalls(v, p) == 0 && v.moves(tp).any { it.target in WIDE_TARGETS && "protect" in it.flags }
            "craftyshield" -> v.acts(tp) && stalls(v, p) == 0 && v.moves(tp).any { !it.damaging && it.target in FOE_TARGETS && !isSelfCurse(v, tp, it) }
            "matblock" -> v.acts(tp) && stalls(v, p) == 0 && MovePower.isFirstAction(v, p) && v.moves(tp).any { it.damaging }
            in STALLING -> v.acts(tp) && stalls(v, p) == 0
            "substitute" -> SUBSTITUTE !in s.volatiles && me.hp > maxHp / SUBSTITUTE_DIVISOR
            "reflect", "lightscreen" -> !up(v, p, move.id)
            "auroraveil" -> !up(v, p, AURORA_VEIL) && (v.weather() == "hail" || v.weather() == "snow")
            "safeguard", "mist", "tailwind" -> !up(v, p, move.id)
            "leechseed" -> foe.hp > 0 && LEECH_SEED !in fs.volatiles && Type.GRASS !in v.types(tp) && SUBSTITUTE !in fs.volatiles
            "curse" -> if (Type.GHOST in v.types(p)) CURSE !in fs.volatiles && me.hp > maxHp / CURSE_COST_DIVISOR else s.boosts[0] < 6 || s.boosts[1] < 6
            "nightmare" -> (fs.status == "slp" || AbilitiesMore.comatose(v, tp)) && NIGHTMARE !in fs.volatiles && SUBSTITUTE !in fs.volatiles
            "yawn" -> foe.hp > 0 && fs.status == null && YAWN !in fs.volatiles && SUBSTITUTE !in fs.volatiles && !up(v, tp, SAFEGUARD)
            "destinybond" -> v.acts(tp) && DESTINY_BOND !in s.volatiles
            "moonlight", "morningsun", "synthesis", "shoreup" -> hurt
            "painsplit" -> foe.hp > me.hp && SUBSTITUTE !in fs.volatiles
            "rest" -> hurt && s.status != "slp"
            "wish" -> !up(v, p, WISH) && hurt
            "stockpile" -> layers(v, p) < MAX_STOCKPILE
            "swallow" -> layers(v, p) > 0 && hurt
            "spitup" -> layers(v, p) > 0
            "strengthsap" -> fs.boosts[0] > -6 && SUBSTITUTE !in fs.volatiles
            "aquaring" -> AQUA_RING !in s.volatiles
            "ingrain" -> INGRAIN !in s.volatiles
            "junglehealing", "lunarblessing" -> hurt || s.status != null
            "healbell", "aromatherapy" -> s.status != null
            "refresh" -> s.status in setOf("brn", "par", "psn", "tox")
            "takeheart" -> s.status != null || s.boosts[2] < 6 || s.boosts[3] < 6
            "bellydrum" -> me.hp > maxHp / BELLY_DRUM_DIVISOR && s.boosts[0] < 6
            "filletaway", "clangoroussoul" -> {
                val divisor = if (move.id == "filletaway") FILLET_DIVISOR else CLANGOROUS_DIVISOR
                me.hp > maxHp / divisor && (move.boosts ?: emptyMap()).any { (key, d) ->
                    val i = BattleEngine.BOOST_KEYS.indexOf(key)
                    i >= 0 && d > 0 && s.boosts[i] < 6
                }
            }
            "haze" -> fs.boosts.any { it > 0 } || s.boosts.any { it < 0 }
            else -> null
        }
    }

    // ---------------------------------------------------------------- the move text

    private fun stallText(): String {
        val first = STALL_BASE; val second = STALL_BASE * STALL_BASE
        return "It only helps in a turn in which the other Pokémon also attacks, and it works 1 time in $first when used again right after, then 1 in $second."
    }

    private fun fraction(f: Pair<Int, Int>) = if (f == 1 to 16) "a sixteenth of" else MoveText.part(f.first, f.second)

    /** [fraction] without its "of": "two thirds", "a quarter". */
    private fun bare(f: Pair<Int, Int>) = fraction(f).removeSuffix(" of")

    /** "half of your max HP" (the share [d] stands for as 1/[d]) for the "fails at ... or less" sentences. */
    private fun orLess(d: Int) = "Fails when your HP is ${bare(1 to d)} of your max HP or less."

    override fun describe(m: Move): List<String> {
        val text = when (m.id) {
            "protect", "detect" -> "Blocks every move aimed at you for the rest of the turn. ${stallText()}"
            "kingsshield" -> "Blocks damaging moves for the rest of the turn; a Pokémon that makes contact with you has its Attack lowered by 1. ${stallText()}"
            "spikyshield" -> "Blocks every move aimed at you for the rest of the turn; a Pokémon that makes contact with you loses ${fraction(1 to SPIKY_DIVISOR)} its max HP. ${stallText()}"
            "banefulbunker" -> "Blocks every move aimed at you for the rest of the turn; a Pokémon that makes contact with you is poisoned. ${stallText()}"
            "burningbulwark" -> "Blocks every move aimed at you for the rest of the turn; a Pokémon that makes contact with you is burned. ${stallText()}"
            "silktrap" -> "Blocks damaging moves for the rest of the turn; a Pokémon that makes contact with you has its Speed lowered by 1. ${stallText()}"
            "obstruct" -> "Blocks damaging moves for the rest of the turn; a Pokémon that makes contact with you has its Defense lowered by 2. ${stallText()}"
            "endure" -> "You survive every hit for the rest of the turn with at least 1 HP. ${stallText()}"
            "quickguard" -> "Blocks moves that go first because of their priority for the rest of the turn. ${stallText()}"
            "wideguard" -> "Blocks moves that hit several Pokémon, like Earthquake, for the rest of the turn. ${stallText()}"
            "craftyshield" -> "Blocks status moves aimed at you for the rest of the turn. ${stallText()}"
            "matblock" -> "Blocks damaging moves for the rest of the turn, but only works on your first turn in the fight. ${stallText()}"
            "substitute" -> "Pay ${fraction(1 to SUBSTITUTE_DIVISOR)} your max HP for a substitute with that much HP: it takes the hits meant for you and stops the other Pokémon's status moves, but not sound moves. " +
                "It fails if you have one already or your HP is ${bare(1 to SUBSTITUTE_DIVISOR)} of your max HP or less."
            "reflect" -> "For $SCREEN_TURNS turns, physical moves hurt your team half as much."
            "lightscreen" -> "For $SCREEN_TURNS turns, special moves hurt your team half as much."
            "auroraveil" -> "For $SCREEN_TURNS turns, physical and special moves hurt your team half as much. It fails unless it is hailing or snowing."
            "safeguard" -> "For $SAFEGUARD_TURNS turns, the other Pokémon can't give your team a status or confusion."
            "mist" -> "For $MIST_TURNS turns, the other Pokémon can't lower your stats."
            "tailwind" -> "For $TAILWIND_TURNS turns, your Speed is doubled."
            "leechseed" -> "Seeds the target: at the end of each turn it loses ${fraction(1 to SEED_DIVISOR)} its max HP and you heal that much. Fails against Grass types and seeded targets."
            "curse" -> "A Ghost type loses ${fraction(1 to CURSE_COST_DIVISOR)} its max HP to curse the target, which then loses ${fraction(1 to CURSE_DIVISOR)} its max HP at the end of each turn. " +
                "Any other type raises its Attack and Defense by 1 and lowers its Speed by 1."
            "nightmare" -> "A sleeping target loses ${fraction(1 to NIGHTMARE_DIVISOR)} its max HP at the end of each turn while it sleeps. Fails against a target that is awake."
            "yawn" -> "The target falls asleep at the end of the next turn if it can. Fails against a target with a status."
            "destinybond" -> "Until your next move, a Pokémon that makes you faint with a move faints too. Fails if used twice in a row."
            "moonlight", "morningsun", "synthesis" -> "Heals you by ${fraction(MOON_HEAL)} your max HP: ${bare(SUN_HEAL)} in the sun, ${bare(DULL_WEATHER_HEAL)} in rain, a sandstorm, hail or snow."
            "shoreup" -> "Heals you by ${fraction(MOON_HEAL)} your max HP: ${bare(SAND_SHORE_HEAL)} in a sandstorm."
            "rest" -> "Heals you fully, cures your status and puts you to sleep: you sleep through exactly $REST_SLEEP tries to move and wake up on the next one. Fails at full HP or if you are asleep or can't sleep."
            "wish" -> "At the end of the next turn, your team's Pokémon is healed by ${fraction(1 to WISH_DIVISOR)} your max HP."
            "stockpile" -> "Raises your Defense and Sp. Def by 1, up to $MAX_STOCKPILE times. Swallow and Spit Up use the stockpile up and take the rises back."
            "swallow" -> "Heals you by ${fraction(SWALLOW_HEALS[0])} your max HP with 1 stockpile, ${fraction(SWALLOW_HEALS[1])} with 2 and all of it with 3, then ends the stockpile. Fails with none."
            "spitup" -> "Its power is $SPIT_UP_POWER times your stockpile, which it then ends. Fails with none."
            "strengthsap" -> "Heals you by the target's Attack stat, then lowers its Attack by 1. Fails if its Attack is already at -6."
            "painsplit" -> "Your HP and the target's HP both become the average of the two."
            "aquaring" -> "You heal ${fraction(1 to RING_DIVISOR)} your max HP at the end of each turn."
            "ingrain" -> "You heal ${fraction(1 to RING_DIVISOR)} your max HP at the end of each turn, and nothing can lift you off the ground."
            "junglehealing", "lunarblessing" -> "Heals you by ${fraction(JUNGLE_HEAL)} your max HP and cures your status."
            "healbell", "aromatherapy" -> "Cures your status."
            "refresh" -> "Cures your burn, poison or paralysis."
            "takeheart" -> "Raises your Sp. Atk and Sp. Def by 1 and cures your status."
            "bellydrum" -> "Pay ${fraction(1 to BELLY_DRUM_DIVISOR)} your max HP to raise your Attack to the maximum. ${orLess(BELLY_DRUM_DIVISOR)}"
            "filletaway" -> "Pay ${fraction(1 to FILLET_DIVISOR)} your max HP first. ${orLess(FILLET_DIVISOR)}"
            "clangoroussoul" -> "Pay ${fraction(1 to CLANGOROUS_DIVISOR)} your max HP first. ${orLess(CLANGOROUS_DIVISOR)}"
            "haze" -> "Sets every stat change of both Pokémon back to 0."
            "brickbreak", "psychicfangs", "ragingbull" -> "Breaks the target's Reflect, Light Screen and Aurora Veil first."
            "rapidspin", "mortalspin" -> "Frees you from Leech Seed and from binding moves."
            else -> if (m.breaksProtect) "Goes through Protect and similar moves, and ends them." else return emptyList()
        }
        return listOf(text)
    }
}
