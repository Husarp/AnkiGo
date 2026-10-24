package com.husarp.ankigo.game

import kotlin.math.floor

/**
 * R17C rule set "CoreRules" (R17C-3, PLAN 229 / 267): the core move effects the data only hints at.
 * - Charge moves (Adam's rule): turn 1 charges, the user's VERY NEXT action is the hit, by itself ([forcedMove]); recharge moves rest after a hit.
 * - Flinch (only when the user moves first), confusion (own-hit), infatuation, locked moves (Outrage ... then fatigue), Uproar, binding
 *   (Wrap, Fire Spin ...), Salt Cure, Syrup Bomb, Sparkling Aria, Glaive Rush, Tri Attack / Dire Claw / Alluring Voice / Burning Jealousy,
 *   crash damage (High Jump Kick), the HP cost of Mind Blown / Steel Beam / Chloroblast.
 * - [NEVER_PICKED]: moves that mean nothing in a 1-vs-1 wild fight (the move choice leaves them out; used anyway they do nothing).
 *
 * The turn FLOW (what is checked in which order) is in [BattleEngine.Round.takeTurn]; it calls [startOfAction], [recharge], [interrupted] and
 * [chargeRise] of this object directly. Keys of [BattleSide.volatiles] / [BattleSide.notes] are the lower case Showdown ids ("confusion",
 * "flinch", "attract", "mustrecharge", "lockedmove", "uproar", "partiallytrapped", "saltcure", "syrupbomb", "glaiverush", "healblock") so
 * other sets can read them; the number is explained at each key below. Durations count in battle ROUNDS (a switch's free hit is not one).
 */
internal object CoreRules : BattleRules {
    // ---- the condition keys (BattleSide.volatiles) ----
    /** Turns left in own action attempts (2..5 at the start, see [Tuning.CONFUSION_TURNS]). */
    const val CONFUSION = "confusion"
    /** 1 until the end of the round: the target loses its action if it has not acted yet. */
    const val FLINCH = "flinch"
    /** 1 while the Pokémon is infatuated with the other one. */
    const val ATTRACT = "attract"
    /** 1 = the next action is a rest turn. */
    const val MUSTRECHARGE = "mustrecharge"
    /** Forced repeats left of a locked move (the move id is in `notes["lockedmove"]`). */
    const val LOCKED = "lockedmove"
    /** Forced repeats left of Uproar (move id in `notes["uproar"]`); 0 = its last turn, removed at the end of that round. */
    const val UPROAR = "uproar"
    /** Rounds left of the binding (move id in `notes["partiallytrapped"]`). */
    const val BOUND = "partiallytrapped"
    /** 1 until the fight ends. */
    const val SALT_CURE = "saltcure"
    /** Rounds left of Syrup Bomb. */
    const val SYRUP_BOMB = "syrupbomb"
    /** 1 from a Glaive Rush hit until the user's next action: moves against it never miss and deal double. */
    const val GLAIVE_RUSH = "glaiverush"
    /** Rounds left of Heal Block (R17C-8 decides what it blocks and counts it down); this set only starts Psychic Noise's. */
    const val HEAL_BLOCK = "healblock"
    /** A sleeper with this number sleeps through exactly that many action attempts and wakes at the next (Rest sets it); without it the sleeper wakes by chance ([Tuning.WAKE_CHANCE]). */
    const val SLEEP_TURNS = "sleepTurns"
    /** Set (in `notes`) when a stat of the Pokémon rose in this round; Alluring Voice / Burning Jealousy read it; cleared at the end of the round. */
    private const val RAISED = "cr.raised"

    /** The rest turn after a recharge move, as a "move" so the turn order and the pick see an ordinary priority-0 action; never executed as a move. */
    val RECHARGE = Move("recharge", "Recharge", Type.NORMAL, Category.STATUS, 0, null, target = "self")

    /** The moves whose damage the `chloroblastRecoil` data flag (not parsed as mindBlownRecoil) belongs to. */
    private val EXTRA_HP_COST_MOVES = setOf("chloroblast")

    /** True for a move with a charge turn: the data's `charge` flag, or [Tuning.CHARGE_MOVES] for a move without flags (test fixtures). */
    fun isCharge(m: Move): Boolean = "charge" in m.flags || m.id in Tuning.CHARGE_MOVES

    /** True for a move the user must rest after: the `recharge` flag or the `mustrecharge` self condition. */
    fun isRecharge(m: Move): Boolean = "recharge" in m.flags || m.selfVolatile == MUSTRECHARGE

    private fun hpCost(m: Move): Boolean = m.mindBlownRecoil || m.id in EXTRA_HP_COST_MOVES

    // ---------------------------------------------------------------- never-picked moves (PLAN 229 part 2)

    private fun group(reason: String, ids: String): Map<String, String> = ids.split(' ').filter { it.isNotEmpty() }.associateWith { reason }

    /**
     * Moves that can't mean anything in a 1-vs-1 wild fight: move id (Showdown id) -> why (a key of [reasonText]). The move choice (ALL modes,
     * Random too) never picks them while the Pokémon has another move; a Pokémon with only such moves is a Main that uses Struggle or a wild
     * one that uses one of them ("But nothing happened.", the Magikarp feel). Used anyway (forced, called): "But nothing happened.", no effect.
     */
    val NEVER_PICKED: Map<String, String> =
        group("partner", "helpinghand followme ragepowder spotlight afteryou quash allyswitch aromaticmist coaching decorate healpulse floralhealing " +
            "purify instruct holdhands gearup magneticflux assist") +
        group("switching", "roar whirlwind teleport batonpass shedtail healingwish lunardance revivalblessing memento spikes toxicspikes stealthrock " +
            "stickyweb meanlook block spiderweb fairylock octolock") +
        group("items", "fling trick switcheroo bestow recycle embargo magicroom naturalgift belch stuffcheeks teatime corrosivegas") +
        group("pp", "spite grudge") +
        group("crits", "focusenergy laserfocus dragoncheer luckychant") +
        group("fight", "perishsong") +
        group("nothing", "splash celebrate happyhour")

    /**
     * R17C FIX-A: moves that can never work for the MAIN (the wild one may use them): the move choice leaves them out for the Main in every mode,
     * like [NEVER_PICKED]. Poltergeist needs a target that holds an item, and wild Pokémon hold none.
     */
    val MAIN_NEVER_PICKED: Set<String> = setOf("poltergeist")

    /** The sentence of each reason in [NEVER_PICKED]. */
    val reasonText: Map<String, String> = mapOf(
        "partner" to "Never used in AnkiGo battles: it needs a partner Pokémon.",
        "switching" to "Never used in AnkiGo battles: it only works when Pokémon switch in or out.",
        "items" to "Never used in AnkiGo battles: it needs held items.",
        "pp" to "Never used in AnkiGo battles: AnkiGo has no PP.",
        "crits" to "Never used in AnkiGo battles: AnkiGo has no critical hits.",
        "fight" to "Never used in AnkiGo battles: it would end every fight on its own.",
        "nothing" to "It does nothing, so it is never used.",
    )

    /** The move text of a never-picked move ([MoveText.describe]), null for any other move. */
    fun neverPickedText(id: String): String? = NEVER_PICKED[id]?.let { reasonText[it] }

    // ---------------------------------------------------------------- helpers other sets can use

    /**
     * Confuses [tp]: 2..5 of its own action attempts ([Tuning.CONFUSION_TURNS]). [byFoe] = the other side causes it (so Own Tempo, Misty Terrain,
     * Safeguard can tell, through [BattleRules.blockVolatile]). [text] defaults to "X became confused!". [quiet] = a blocked attempt says
     * nothing (a secondary chance). True = it started (false: already confused, fainted or blocked).
     */
    fun confuse(r: Round, tp: Boolean, byFoe: Boolean, text: String? = null, quiet: Boolean = false): Boolean {
        val turns = r.rng.nextInt(Tuning.CONFUSION_TURNS.first, Tuning.CONFUSION_TURNS.last + 1)
        return r.addVolatile(tp, CONFUSION, turns, byFoe, text ?: "${r.mon(tp).name} became confused!", "Confused", quiet)
    }

    /** Infatuates [tp] with the other Pokémon ("X fell in love!"); only a male with a female. True = it started. */
    fun infatuate(r: Round, tp: Boolean, byFoe: Boolean, quiet: Boolean = false): Boolean {
        if (!genderFit(r.mon(true), r.mon(false))) return false
        return r.addVolatile(tp, ATTRACT, 1, byFoe, "${r.mon(tp).name} fell in love!", "In love", quiet)
    }

    /** The target [tp] flinches when [p] (the user) moved before it in this round: it will lose its action. */
    fun flinch(r: Round, p: Boolean, tp: Boolean) {
        if (r.freeHit || r.movedThisRound(tp)) return
        r.addVolatile(tp, FLINCH, 1, byFoe = true, quiet = true)
    }

    /** One male and one female (a genderless Pokémon or two of the same gender can't fall in love). */
    fun genderFit(a: Mon, b: Mon): Boolean = (a.gender == 'M' && b.gender == 'F') || (a.gender == 'F' && b.gender == 'M')

    // ---------------------------------------------------------------- called by the engine's turn flow (BattleEngine.Round.takeTurn)

    /** The very start of [p]'s action, before anything can stop it: Glaive Rush's drawback ends. */
    fun startOfAction(r: Round, p: Boolean) {
        r.side(p).volatiles.remove(GLAIVE_RUSH)
    }

    /** True (and the line is written) when [p] must rest this action: "X must recharge!". Comes before sleep and the rest. */
    fun recharge(r: Round, p: Boolean): Boolean {
        if (r.side(p).volatiles.remove(MUSTRECHARGE) == null) return false
        r.fx(p, "${r.mon(p).name} must recharge!", EffectKind.CANT_MOVE, "recharge")
        return true
    }

    /** [p] could not use its move in this action (asleep, flinched, blocked ...): a charge in progress, a locked move and Uproar are over. */
    fun interrupted(r: Round, p: Boolean) {
        val s = r.side(p)
        s.charging = null
        for (key in listOf(LOCKED, UPROAR)) { s.volatiles.remove(key); s.notes.remove(key) }
    }

    /** What the charge turn of [move] raises on the user (Skull Bash, Meteor Beam, Electro Shot), after the charge line. */
    fun chargeRise(r: Round, p: Boolean, move: Move) {
        Tuning.CHARGE_TURN_RISE[move.id]?.let { r.boost(p, it, byFoe = false) }
    }

    // ---------------------------------------------------------------- hooks

    override val handles: Set<String> =
        setOf("confuseray", "supersonic", "sweetkiss", "teeterdance", "swagger", "flatter", "attract")

    /** What [p] must do now instead of picking: rest, finish a charge, repeat a locked move or Uproar. */
    override fun forcedMove(v: BattleView, p: Boolean): Move? {
        val s = v.side(p)
        if (s.volatiles.containsKey(MUSTRECHARGE)) return RECHARGE
        s.charging?.let { id -> v.data.move(id)?.let { return it } }
        for (key in listOf(LOCKED, UPROAR)) {
            if ((s.volatiles[key] ?: 0) > 0) s.notes[key]?.let { id -> v.data.move(id)?.let { return it } }
        }
        return null
    }

    /** A never-picked move that is used anyway (forced, called by another move) does nothing. */
    override fun failReason(r: Round, p: Boolean, move: Move): String? =
        if (move.id in NEVER_PICKED) "But nothing happened." else null

    /** Confusion moves (Confuse Ray ... Swagger, Flatter: their stat part first) and Attract. */
    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        val tp = !p
        when (move.volatileStatus) {
            CONFUSION -> {
                var worked = false
                move.boosts?.let { if (r.boost(tp, it, byFoe = true)) worked = true }
                if (r.side(tp).volatiles.containsKey(CONFUSION)) r.fx(tp, "${r.mon(tp).name} is already confused!")
                else if (confuse(r, tp, byFoe = true)) worked = true
                return worked
            }
            ATTRACT -> {
                if (!genderFit(r.mon(p), r.mon(tp))) return false
                if (r.side(tp).volatiles.containsKey(ATTRACT)) { r.fx(tp, "${r.mon(tp).name} is already in love!"); return false }
                return infatuate(r, tp, byFoe = true)
            }
        }
        return null
    }

    /** Flinch, confusion and love stop an action (in the games' order); the line is written here. */
    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        val s = r.side(p)
        val n = r.mon(p).name
        if (s.volatiles.remove(FLINCH) != null) {
            r.fx(p, "$n flinched and couldn't move!", EffectKind.CANT_MOVE, "flinch")
            Abilities.flinchStopped(r, p) // R17C FIX-A: Steadfast raises the Speed when the flinch really takes the action
            return false
        }
        s.volatiles[CONFUSION]?.let { left ->
            if (left - 1 <= 0) r.removeVolatile(p, CONFUSION, "$n snapped out of its confusion!")
            else {
                s.volatiles[CONFUSION] = left - 1
                r.fx(p, "$n is confused!", EffectKind.VOLATILE, CONFUSION)
                if (r.rng.nextDouble() < Tuning.CONFUSION_SELF_HIT) { hurtByConfusion(r, p); return false }
            }
        }
        if (s.volatiles.containsKey(ATTRACT)) {
            r.fx(p, "$n is in love with ${r.mon(!p).name}!", EffectKind.VOLATILE, ATTRACT)
            if (r.rng.nextDouble() < Tuning.LOVE_STOPS) {
                r.fx(p, "$n is immobilized by love!", EffectKind.CANT_MOVE, ATTRACT)
                return false
            }
        }
        return true
    }

    /** The confusion self-hit: a [Tuning.CONFUSION_POWER] typeless physical hit with the Pokémon's own Attack / Defense and stages; no STAB, type, crit, burn or answers factor. */
    private fun hurtByConfusion(r: Round, p: Boolean) {
        val m = r.mon(p)
        val s = r.side(p)
        val st = r.stats(p)
        val a = maxOf(1, floor(st.atk * BattleEngine.stageMult(s.boosts[0])).toInt())
        val d = maxOf(1, floor(st.def * BattleEngine.stageMult(s.boosts[1])).toInt())
        var dmg = floor(floor(2.0 * m.level / 5 + 2).toInt() * Tuning.CONFUSION_POWER.toDouble() * a / d).toInt() / 50 + 2
        dmg = maxOf(1, dmg * r.rng.nextInt(Tuning.ROLL_MIN, 101) / 100)
        r.fx(p, "It hurt itself in its confusion!", EffectKind.CANT_MOVE, CONFUSION)
        val lost = minOf(dmg, m.hp)
        r.hurt(p, dmg, "${m.name} lost $lost HP.", EffectKind.RESIDUAL)
    }

    /** After a hit: the target is bound (Wrap ...), the move's rest turn (Hyper Beam ...) and Glaive Rush's drawback. */
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        if (move.volatileStatus == BOUND && r.mon(tp).hp > 0) {
            val turns = r.rng.nextInt(Tuning.BIND_TURNS.first, Tuning.BIND_TURNS.last + 1)
            if (r.addVolatile(tp, BOUND, turns, byFoe = true, text = bindText(move, r.mon(p).name, r.mon(tp).name), label = "Trapped", quiet = true)) {
                r.side(tp).notes[BOUND] = move.id
            }
        }
        if (move.selfVolatile == GLAIVE_RUSH && r.mon(p).hp > 0) r.side(p).volatiles[GLAIVE_RUSH] = 1
    }

    private fun bindText(m: Move, user: String, target: String): String = when (m.id) {
        "bind" -> "$target was squeezed by $user!"
        "wrap" -> "$target was wrapped by $user!"
        "clamp" -> "$user clamped $target!"
        "firespin", "whirlpool" -> "$target was trapped in the vortex!"
        "sandtomb" -> "$target was trapped by Sand Tomb!"
        "magmastorm" -> "$target became trapped by swirling magma!"
        "infestation" -> "$target has been afflicted with an infestation by $user!"
        "snaptrap" -> "$target was caught in a snap trap!"
        "thundercage" -> "$user trapped $target!"
        else -> "$target was trapped by ${m.name}!"
    }

    /** The lasting parts of a rolled secondary that the data cannot say (flinch, confusion, Salt Cure ...), and the code-only secondaries (Tri Attack ...). */
    override fun secondary(r: Round, p: Boolean, move: Move, sec: Secondary) {
        val tp = !p
        val tn = r.mon(tp).name
        when {
            sec.flinch || sec.volatileStatus == "flinch" -> flinch(r, p, tp)
            sec.volatileStatus == CONFUSION -> confuse(r, tp, byFoe = true, quiet = true)
            sec.volatileStatus == HEAL_BLOCK ->
                r.addVolatile(tp, HEAL_BLOCK, Tuning.PSYCHIC_NOISE_HEAL_BLOCK, byFoe = true, text = "$tn was prevented from healing!", label = "Heal blocked", quiet = true)
            sec.volatileStatus == SALT_CURE ->
                r.addVolatile(tp, SALT_CURE, 1, byFoe = true, text = "$tn is being salt cured!", label = "Salt cured", quiet = true)
            sec.volatileStatus == SYRUP_BOMB ->
                r.addVolatile(tp, SYRUP_BOMB, Tuning.SYRUP_TURNS, byFoe = true, text = "$tn got covered in sticky syrup!", label = "Syrup", quiet = true)
            sec.volatileStatus == "sparklingaria" -> {
                val s = r.side(tp)
                if (s.status == "brn" && r.mon(tp).hp > 0) {
                    s.status = null; s.statusTurns = 0
                    r.fx(tp, "$tn's burn was cured!", EffectKind.STATUS_CURED, "brn")
                }
            }
            sec.status == null && sec.boosts == null && sec.selfBoosts == null && sec.volatileStatus == null -> codeSecondary(r, p, move)
        }
    }

    /** The secondaries whose effect is code in the games (the data only gives a chance): [Move.secondaryOnHit]. */
    private fun codeSecondary(r: Round, p: Boolean, move: Move) {
        val tp = !p
        val raised = r.side(tp).notes.containsKey(RAISED)
        when (move.id) {
            "triattack" -> r.setStatus(tp, Tuning.TRI_ATTACK_STATUSES[r.rng.nextInt(Tuning.TRI_ATTACK_STATUSES.size)], move.type, byFoe = true, quiet = true)
            "direclaw" -> r.setStatus(tp, Tuning.DIRE_CLAW_STATUSES[r.rng.nextInt(Tuning.DIRE_CLAW_STATUSES.size)], move.type, byFoe = true, quiet = true)
            "alluringvoice" -> if (raised) confuse(r, tp, byFoe = true, quiet = true)
            "burningjealousy" -> if (raised) r.setStatus(tp, "brn", move.type, byFoe = true, quiet = true)
        }
    }

    /** After the move was tried: recharge, locks and Uproar, crash damage, the HP cost of Mind Blown. [failed] = it missed or failed. */
    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        val s = r.side(p)
        val n = r.mon(p).name
        val alive = r.mon(p).hp > 0
        val connected = !failed && !hasNoEffect(r, p, move)

        if (connected && alive && isRecharge(move)) s.volatiles[MUSTRECHARGE] = 1

        // a locked move: 2-3 uses in all, then fatigue; a miss, a fail or no effect ends it early without confusion
        val locked = s.volatiles[LOCKED]
        if (locked != null) {
            s.volatiles.remove(LOCKED)
            val id = s.notes.remove(LOCKED)
            if (connected) {
                if (locked > 1 && id != null) { s.volatiles[LOCKED] = locked - 1; s.notes[LOCKED] = id }
                else if (alive) confuse(r, p, byFoe = false, text = "$n became confused due to fatigue!", quiet = true)
            }
        } else if (connected && alive && move.selfVolatile == LOCKED) {
            s.volatiles[LOCKED] = r.rng.nextInt(Tuning.LOCK_TURNS.first, Tuning.LOCK_TURNS.last + 1) - 1
            s.notes[LOCKED] = move.id
        }

        // Uproar: 3 uses in all; removed at the end of the round of its last use (endOfRound)
        val up = s.volatiles[UPROAR]
        if (up != null) {
            if (connected) s.volatiles[UPROAR] = maxOf(0, up - 1) else { s.volatiles.remove(UPROAR); s.notes.remove(UPROAR) }
        } else if (connected && alive && move.selfVolatile == UPROAR) {
            s.volatiles[UPROAR] = Tuning.UPROAR_TURNS - 1
            s.notes[UPROAR] = move.id
        }

        if (move.crashDamage && !connected && alive) {
            r.hurt(p, maxOf(1, r.maxHp(p) / Tuning.CRASH_DIVISOR), "$n kept going and crashed!", EffectKind.RECOIL, source = Indirect.CRASH)
        }
        if (hpCost(move) && alive && !(failed && Abilities.dampStops(r, p, move))) { // R17C FIX-A: Damp stopped the move: no HP is paid
            r.hurt(p, (r.maxHp(p) + Tuning.MIND_BLOWN_DIVISOR - 1) / Tuning.MIND_BLOWN_DIVISOR, "$n lost half of its max HP!", EffectKind.RECOIL, source = Indirect.MIND_BLOWN)
        }
    }

    /** A damaging move whose type the target is immune to (the engine reports it as a hit with effectiveness 0, not as a failure). */
    private fun hasNoEffect(r: Round, p: Boolean, move: Move): Boolean {
        if (!move.damaging || move.id == Tuning.STRUGGLE) return false
        val h = BattleEngine.newHit(r, p, r.mon(p), r.side(p), r.mon(!p), r.side(!p), move)
        return rulesChain(TypeChart.eff(h.type, r.types(!p))) { rule, e -> rule.effectiveness(h, e) } == 0.0
    }

    /** Glaive Rush: moves against its user never miss. */
    override fun accuracy(h: Hit, acc: Double?): Double? = if (GLAIVE_RUSH in h.dSide.volatiles) null else acc

    /** Glaive Rush: moves against its user deal double. */
    override fun finalMod(h: Hit): Double = if (GLAIVE_RUSH in h.dSide.volatiles) 2.0 else 1.0

    /** Nobody falls asleep while an Uproar lasts. */
    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? =
        if (status == "slp" && uproar(r)) "But the uproar kept ${r.mon(tp).name} awake!" else null

    private fun uproar(v: BattleView): Boolean = v.side(true).volatiles.containsKey(UPROAR) || v.side(false).volatiles.containsKey(UPROAR)

    override fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) {
        if (delta > 0) r.side(tp).notes[RAISED] = "1"
    }

    /** The end of a round: flinch is over, an uproar wakes sleepers, binding / Salt Cure hurt, Syrup Bomb slows. */
    override fun endOfRound(r: Round) {
        val awake = uproar(r)
        val order = if (r.speed(true) > r.speed(false)) listOf(true, false) else listOf(false, true)
        for (q in order) {
            val s = r.side(q)
            val m = r.mon(q)
            s.volatiles.remove(FLINCH); s.notes.remove(RAISED)
            if (m.hp <= 0) continue
            if (awake && s.status == "slp") {
                s.status = null; s.statusTurns = 0; s.volatiles.remove(SLEEP_TURNS)
                r.fx(q, "${m.name} woke up in the uproar!", EffectKind.STATUS_CURED, "slp")
            }
            s.volatiles[BOUND]?.let { left ->
                val name = s.notes[BOUND]?.let { id -> r.data.move(id)?.name } ?: "the binding"
                r.hurt(q, maxOf(1, r.maxHp(q) / Tuning.BIND_DIVISOR), "${m.name} is hurt by $name!", EffectKind.RESIDUAL, source = Indirect.BOUND)
                if (left - 1 <= 0) {
                    s.volatiles.remove(BOUND); s.notes.remove(BOUND)
                    r.fx(q, "${m.name} was freed from $name!", EffectKind.VOLATILE, BOUND, -1)
                } else s.volatiles[BOUND] = left - 1
            }
            if (m.hp > 0 && s.volatiles.containsKey(SALT_CURE)) {
                val weak = Type.WATER in r.types(q) || Type.STEEL in r.types(q)
                r.hurt(q, maxOf(1, r.maxHp(q) / (if (weak) Tuning.SALT_CURE_WEAK_DIVISOR else Tuning.SALT_CURE_DIVISOR)), "${m.name} is hurt by Salt Cure!", EffectKind.RESIDUAL, source = Indirect.SALT_CURE)
            }
            if (m.hp > 0) s.volatiles[SYRUP_BOMB]?.let { left ->
                r.boost(q, mapOf("spe" to -1), byFoe = true)
                if (left - 1 <= 0) r.removeVolatile(q, SYRUP_BOMB, "${m.name} is no longer covered in sticky syrup.")
                else s.volatiles[SYRUP_BOMB] = left - 1
            }
        }
        for (q in listOf(true, false)) {
            val s = r.side(q)
            if (s.volatiles[UPROAR] == 0) { s.volatiles.remove(UPROAR); s.notes.remove(UPROAR) }
        }
    }

    /** R17C FIX-A: the free hit of a switch is no round: nothing "raised in this round" stays for the next one. */
    override fun afterFreeHit(r: Round) {
        for (q in listOf(true, false)) r.side(q).notes.remove(RAISED)
    }

    /** The Main leaves: what it did to the wild one ends with it (love, binding, Syrup Bomb). Its own side is replaced by the caller. */
    override fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) {
        if (!p) return
        val wild = battle.enemy
        wild.volatiles.remove(ATTRACT)
        wild.volatiles.remove(BOUND); wild.notes.remove(BOUND)
        wild.volatiles.remove(SYRUP_BOMB)
    }

    // ---------------------------------------------------------------- the move choice

    /** The Smart choice: a confusion / love move only while it can still work; a never-picked move never. Everything else: ask the engine's own rule. */
    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        if (move.id in NEVER_PICKED) return false
        return when (move.volatileStatus) {
            CONFUSION -> if (move.damaging) null else canConfuse(v, !p)
            ATTRACT -> canInfatuate(v, p)
            else -> null
        }
    }

    /** [tp] could still get confused: alive, not confused, and not protected by Misty Terrain, Safeguard or Own Tempo (read by their keys). */
    private fun canConfuse(v: BattleView, tp: Boolean): Boolean {
        if (v.mon(tp).hp <= 0 || CONFUSION in v.side(tp).volatiles) return false
        if (v.terrain() == "misty" && v.grounded(tp)) return false
        if ((v.conds(tp)["safeguard"] ?: 0) > 0) return false
        return v.ability(tp) != "owntempo"
    }

    /** The user [p] could still make the other one fall in love: genders fit, not in love already, not Oblivious. */
    private fun canInfatuate(v: BattleView, p: Boolean): Boolean =
        v.mon(!p).hp > 0 && genderFit(v.mon(p), v.mon(!p)) && ATTRACT !in v.side(!p).volatiles && v.ability(!p) != "oblivious"

    // ---------------------------------------------------------------- the move text

    override fun describe(m: Move): List<String> {
        val out = ArrayList<String>()
        if (isRecharge(m)) out += "After a hit, your next move is a rest turn to recharge."
        for (sec in m.allSecondaries) secondaryText(m, sec)?.let { out += it }
        when (m.volatileStatus) {
            CONFUSION -> if (!m.damaging) out += "Confuses the target."
            ATTRACT -> out += "Makes the target fall in love if one of you is male and the other female. A Pokémon in love can't move half of the time."
            BOUND -> out += "Holds the target for ${Tuning.BIND_TURNS.first}–${Tuning.BIND_TURNS.last} turns: it loses " +
                "${MoveText.part(1, Tuning.BIND_DIVISOR)} its max HP at the end of each turn."
        }
        when (m.selfVolatile) {
            // R17C FIX-A: these count the user's own uses (a wild Pokémon that does not act in a round uses none), so they say "moves", not "turns"
            LOCKED -> out += "Locks you into using it for ${Tuning.LOCK_TURNS.first}–${Tuning.LOCK_TURNS.last} moves in a row, then you become confused."
            UPROAR -> out += "Locks you into using it for ${Tuning.UPROAR_TURNS} moves in a row. While it lasts nobody can fall asleep, and sleepers wake up."
            GLAIVE_RUSH -> out += "After it hits, moves against you never miss and deal double damage until your next move."
        }
        if (m.crashDamage) out += "If it misses or has no effect, you lose half of your max HP."
        if (hpCost(m)) out += "You lose half of your max HP after using it."
        return out
    }

    /** The sentence for one secondary whose effect lives in this set, null for every other kind (MoveText says status / stat parts itself). */
    private fun secondaryText(m: Move, s: Secondary): String? {
        if (s.chance <= 0) return null
        val sure = s.chance >= 100
        val tag = if (sure) "" else " (${s.chance} %)"
        return when {
            s.flinch || s.volatileStatus == "flinch" ->
                if (sure) "Makes the target flinch if you move first." else "May make the target flinch$tag if you move first."
            s.volatileStatus == CONFUSION -> if (sure) "Confuses the target." else "May confuse the target$tag."
            s.volatileStatus == HEAL_BLOCK -> "Stops the target from healing for ${Tuning.PSYCHIC_NOISE_HEAL_BLOCK} turns."
            s.volatileStatus == SALT_CURE ->
                "Hurts the target by ${MoveText.part(1, Tuning.SALT_CURE_DIVISOR)} its max HP at the end of each turn until the fight ends " +
                    "(${MoveText.part(1, Tuning.SALT_CURE_WEAK_DIVISOR).removeSuffix(" of")} for Water and Steel types)."
            s.volatileStatus == SYRUP_BOMB -> "Lowers the target's Speed by 1 at the end of each of the next ${Tuning.SYRUP_TURNS} turns."
            s.volatileStatus == "sparklingaria" -> "Cures the target's burn."
            s.status == null && s.boosts == null && s.selfBoosts == null && s.volatileStatus == null -> when (m.id) {
                "triattack" -> "May burn, paralyze or freeze the target$tag."
                "direclaw" -> "May poison, paralyze or put the target to sleep$tag."
                "alluringvoice" -> "Confuses the target if its stats were raised this turn."
                "burningjealousy" -> "Burns the target if its stats were raised this turn."
                else -> null
            }
            else -> null
        }
    }
}
