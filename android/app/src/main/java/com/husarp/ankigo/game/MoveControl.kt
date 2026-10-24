package com.husarp.ankigo.game

import kotlin.math.max

/**
 * R17C rule set "MoveControl" (R17C-8, PLAN 229): moves that control what the other side can do, tricks with stats, Transform, type changes,
 * grounding and aim, and the moves that call other moves. Mainline rules; a "turn" is one battle ROUND, durations count down at the end of a
 * round ([endOfRound]) and both sides follow the same rules ([p] = the Main's side).
 * - Move control ([moveBlocked] / [forcedMove]): Taunt, Encore, Disable, Torment, Imprison, Heal Block, Throat Chop. A Pokémon whose every move
 *   is blocked uses Struggle (both sides). A duration is "the target's next N actions": it gets one more round when the target had moved already.
 * - Stat tricks: Psych Up, Heart / Power / Guard / Speed Swap, Power / Guard Split, Power Trick, Power Shift, Topsy-Turvy, Acupressure, Venom
 *   Drench, Rototiller, Flower Shield, Charge (lasts until the user's next Electric move, mainline Gen 9), Defense Curl (key "defensecurl", Rollout
 *   reads it), Minimize, Tar Shot, Parting Shot (R17C FIX-A: the stat drops; the user does not switch out), Powder (R17C FIX-A).
 * - Transform (also [transformInto] for an ability), type changes (Soak ... Roost), Electrify, Psycho Shift.
 * - Grounding and aim: Magnet Rise, Telekinesis, Smack Down / Thousand Arrows, Lock-On, Foresight, Miracle Eye.
 * - Calling moves ([Round.useMove]): Metronome, Sleep Talk, Copycat, Mirror Move, Me First, Nature Power, Snatch, Magic Coat, Mimic, Sketch.
 *
 * KEYS. [BattleSide.volatiles] (lower case Showdown ids, public so other sets can read them): "taunt" "encore" "disable" "torment" "imprison"
 * "healblock" "throatchop" (rounds left, 1 = until the fight ends for torment / imprison), "magnetrise" "telekinesis" "smackdown" "foresight"
 * "miracleeye" "charge" "defensecurl" "minimize" "tarshot" "powertrick" "powershift"; round-long: "electrify" "roost" "magiccoat" "snatch" "powder";
 * "lockon" = the user's action count at which its next action happens. [BattleSide.notes] (private, prefix "mc."): "mc.types" (the types
 * that replace the species' own), "mc.transform" (the species number copied) with "mc.tstats" "mc.tmoves" "mc.tab", "mc.mimic" "mc.sketch" (the
 * move that took the slot), "mc.o.<stat>" (a stat set by Speed Swap / a Split), "mc.last" (on the wild one's side: the last move used by anybody,
 * for Copycat); the notes "encore" and "disable" hold the move id. The Main's side is replaced on a switch, so all of it ends with the fight there.
 */
internal object MoveControl : BattleRules {
    // ---- keys of BattleSide.volatiles ----
    const val TAUNT = "taunt"
    const val ENCORE = "encore"
    const val DISABLE = "disable"
    const val TORMENT = "torment"
    const val IMPRISON = "imprison"
    const val HEAL_BLOCK = CoreRules.HEAL_BLOCK
    const val THROAT_CHOP = "throatchop"
    const val MAGNET_RISE = "magnetrise"
    const val TELEKINESIS = "telekinesis"
    const val SMACK_DOWN = "smackdown"
    const val LOCK_ON = "lockon"
    const val FORESIGHT = "foresight"
    const val MIRACLE_EYE = "miracleeye"
    const val CHARGE = "charge"
    const val DEFENSE_CURL = "defensecurl"
    const val MINIMIZE = "minimize"
    const val TAR_SHOT = "tarshot"
    const val ELECTRIFY = "electrify"
    const val ROOST = "roost"
    const val MAGIC_COAT = "magiccoat"
    const val SNATCH = "snatch"
    /** R17C FIX-A: Powder: the target's next Fire move this round fails and hurts it. */
    const val POWDER = "powder"
    const val POWER_TRICK = "powertrick"
    const val POWER_SHIFT = "powershift"
    private const val ME_FIRST = "mc.mefirst"   // while Me First uses the foe's move: its power x1.5
    private const val BOUNCING = "mc.bounce"    // while a bounced move runs: it can't be bounced again

    // ---- keys of BattleSide.notes ----
    private const val TYPES = "mc.types"
    const val TRANSFORMED = "mc.transform"
    private const val T_STATS = "mc.tstats"
    private const val T_MOVES = "mc.tmoves"
    private const val T_ABILITY = "mc.tab"
    private const val MIMIC = "mc.mimic"
    private const val SKETCH = "mc.sketch"
    private const val OVERRIDE = "mc.o."
    private const val LAST_ANY = "mc.last"

    // ---- numbers (mainline; a "turn" is a round) ----
    private const val TAUNT_TURNS = 3
    private const val ENCORE_TURNS = 3
    private const val DISABLE_TURNS = 4
    private const val HEAL_BLOCK_TURNS = 5
    private const val THROAT_CHOP_TURNS = 2
    private const val MAGNET_RISE_TURNS = 5
    private const val TELEKINESIS_TURNS = 3
    /** Charge (and Wind Power / Electromorphosis): the next Electric move of the holder is doubled and ends it. R17C FIX-A: no countdown (mainline Gen 9) - a wild Pokémon acts rarely, a countdown would end it before it could use it. */
    private const val CHARGE_POWER = 2.0
    /** Powder: the Pokémon whose Fire move it stops loses this share of its max HP. */
    private const val POWDER_DIVISOR = 4
    private const val MINIMIZE_POWER = 2.0
    private const val ME_FIRST_POWER = 1.5
    private const val ACUPRESSURE_RISE = 2
    private const val TAR_SHOT_FIRE = 2.0
    private val ROOST_HEAL = 1 to 2
    private const val FAILED = "But it failed!"
    private val CHARGE_STAGES = mapOf("spd" to 1)
    private val CURL_STAGES = mapOf("def" to 1)
    private val MINIMIZE_STAGES = mapOf("evasion" to 2)
    private val TAR_SHOT_STAGES = mapOf("spe" to -1)
    private val VENOM_STAGES = mapOf("atk" to -1, "spa" to -1, "spe" to -1)
    private val ROTOTILLER_STAGES = mapOf("atk" to 1, "spa" to 1)
    private val FLOWER_SHIELD_STAGES = mapOf("def" to 1)
    private val PARTING_SHOT_STAGES = mapOf("atk" to -1, "spa" to -1)

    // ---- move lists ----
    /** Moves that hit a Minimized target without missing, for double power. */
    private val MINIMIZE_MOVES = setOf("stomp", "bodyslam", "dragonrush", "heatcrash", "heavyslam", "flyingpress", "maliciousmoonsault", "steamroller", "supercellslam")
    private val SMACK_MOVES = setOf("smackdown", "thousandarrows")
    /** Telekinesis fails on these species (national numbers: Diglett, Dugtrio, Sandygast, Palossand). */
    private val NO_TELEKINESIS = setOf(50, 51, 769, 770)
    /** Moves that call another move: never called themselves, so a call never loops. */
    private val CALLERS = setOf("metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "assist")
    /** Mainline: Metronome can't call these (used only when the data has no `metronome` flag). */
    private val METRONOME_EXCLUDED = CALLERS + setOf(
        "afteryou", "belch", "bestow", "celebrate", "chatter", "counter", "covet", "craftyshield", "destinybond", "detect", "endure", "feint",
        "focuspunch", "followme", "freezeshock", "helpinghand", "iceburn", "mimic", "mirrorcoat", "protect", "quash", "quickguard", "ragepowder",
        "relicsong", "secretsword", "sketch", "snatch", "snore", "switcheroo", "thief", "transform", "trick", "vcreate", "wideguard", "bide", "magiccoat",
    )
    /** Mainline: Copycat can't copy these (used besides the `failcopycat` flag). */
    private val NO_COPYCAT = CALLERS + setOf(
        "banefulbunker", "bestow", "celebrate", "chatter", "counter", "covet", "destinybond", "detect", "endure", "feint", "focuspunch", "followme",
        "helpinghand", "kingsshield", "matblock", "mimic", "mirrorcoat", "protect", "ragepowder", "roar", "sketch", "snatch", "spikyshield", "spotlight",
        "struggle", "switcheroo", "thief", "transform", "trick", "whirlwind",
    )
    private val NO_ENCORE = CALLERS + setOf("encore", "transform", "mimic", "sketch", "struggle")
    private val NO_MIMIC = CALLERS + setOf("mimic", "sketch", "transform", "struggle", "chatter")
    private val NO_SKETCH = setOf("sketch", "struggle", "chatter")
    private val NO_ME_FIRST = CALLERS + setOf("chatter", "counter", "covet", "focuspunch", "metalburst", "mirrorcoat", "struggle", "thief")
    private val STATUS_NAMES = mapOf("brn" to "burn", "par" to "paralysis", "psn" to "poisoning", "tox" to "poisoning", "slp" to "sleep", "frz" to "freeze")
    private val STAT_KEYS = listOf("atk", "def", "spa", "spd", "spe")
    private val STAT_OF = mapOf("atk" to Stat.ATK, "def" to Stat.DEF, "spa" to Stat.SPA, "spd" to Stat.SPD, "spe" to Stat.SPE)

    /** Condition -> (the line when it ends). The countdown of [endOfRound] runs over exactly these. */
    private val ENDS: Map<String, String> = linkedMapOf(
        TAUNT to "%s's taunt wore off!",
        ENCORE to "%s's encore ended!",
        DISABLE to "%s is no longer disabled!",
        HEAL_BLOCK to "The Heal Block on %s ended!",
        THROAT_CHOP to "The effects of Throat Chop wore off for %s!",
        MAGNET_RISE to "%s's electromagnetism wore off!",
        TELEKINESIS to "%s was freed from the telekinesis!",
    )
    /** Conditions that last for the round they were made in only. */
    private val ROUND_LONG = listOf(MAGIC_COAT, SNATCH, ELECTRIFY, ROOST, POWDER)

    override val handles: Set<String> = setOf(
        "taunt", "encore", "disable", "torment", "imprison", "healblock",
        "psychup", "heartswap", "powerswap", "guardswap", "speedswap", "powersplit", "guardsplit", "powertrick", "powershift", "topsyturvy",
        "acupressure", "venomdrench", "rototiller", "flowershield", "charge", "defensecurl", "minimize", "tarshot",
        "transform", "soak", "magicpowder", "reflecttype", "conversion", "conversion2", "forestscurse", "trickortreat", "camouflage",
        "roost", "electrify", "psychoshift", "partingshot", "powder",
        "magnetrise", "telekinesis", "lockon", "mindreader", "foresight", "odorsleuth", "miracleeye",
        "metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "snatch", "magiccoat", "mimic", "sketch",
    )

    // ---------------------------------------------------------------- reading the fight (other sets may use these)

    /** Heal Block is on [p]: no healing move, no drain, no other heal of this fight's rules. */
    fun healBlocked(v: BattleView, p: Boolean): Boolean = (v.side(p).volatiles[HEAL_BLOCK] ?: 0) > 0

    /** [p]'s Pokémon has turned into the other one (Transform). */
    fun isTransformed(v: BattleView, p: Boolean): Boolean = TRANSFORMED in v.side(p).notes

    /** The weight (kg) of the species [mon] was transformed into, null when it did not transform: MovePower reads it for the weight moves. */
    fun transformedWeight(v: BattleView, mon: Mon): Double? {
        val p = if (mon === v.mon(true)) true else if (mon === v.mon(false)) false else return null
        val num = v.side(p).notes[TRANSFORMED]?.toIntOrNull() ?: return null
        return v.data.species[num]?.weightKg
    }

    private fun storedTypes(side: BattleSide): List<Type>? =
        side.notes[TYPES]?.let { text -> text.split(',').mapNotNull { Type.parse(it) } }

    /** [q]'s own types (its species', or the ones a move set), not counting Roost. */
    private fun ownTypes(v: BattleView, q: Boolean): List<Type> = storedTypes(v.side(q)) ?: v.baseTypes(q)

    private fun decodeStats(text: String, hp: Int): Stats? {
        val l = text.split(',').mapNotNull { it.toIntOrNull() }
        return if (l.size == 5) Stats(hp, l[0], l[1], l[2], l[3], l[4]) else null
    }

    private fun encodeStats(s: Stats) = "${s.atk},${s.def},${s.spa},${s.spd},${s.spe}"

    private fun changesStats(s: BattleSide) =
        T_STATS in s.notes || POWER_TRICK in s.volatiles || POWER_SHIFT in s.volatiles || STAT_KEYS.any { OVERRIDE + it in s.notes }

    /**
     * [q]'s stats as its own moves leave them: the copied ones after Transform, Power Trick / Power Shift swaps, then the numbers a Split or Speed
     * Swap set. Without Wonder Room's swap (the hook adds it) and with the Pokémon's own HP.
     */
    private fun ownStats(v: BattleView, q: Boolean): Stats {
        val s = v.side(q)
        val base = v.baseStats(q)
        var x = s.notes[T_STATS]?.let { decodeStats(it, base.hp) } ?: base
        if (POWER_TRICK in s.volatiles) x = x.copy(atk = x.def, def = x.atk)
        if (POWER_SHIFT in s.volatiles) x = x.copy(atk = x.def, def = x.atk, spa = x.spd, spd = x.spa)
        for (key in STAT_KEYS) s.notes[OVERRIDE + key]?.toIntOrNull()?.let { x = x.with(STAT_OF.getValue(key), it) }
        return x
    }

    /** The moves a transformed [q] can use (the target's, copied), null when it did not transform. */
    private fun transformedMoves(v: BattleView, q: Boolean): List<Move>? =
        v.side(q).notes[T_MOVES]?.let { text -> text.split(',').filter { it.isNotEmpty() }.mapNotNull { v.data.move(it) }.ifEmpty { null } }

    private fun lastOf(v: BattleView, q: Boolean): Move? = v.side(q).lastMove?.let { v.data.move(it) }

    private fun name(v: BattleView, q: Boolean) = v.mon(q).name

    /** How many rounds a condition on [tp] lasts: [n], one more when [tp] has moved already in this round (so it covers its next [n] actions). */
    private fun lasts(r: Round, tp: Boolean, n: Int) = n + if (r.movedThisRound(tp)) 1 else 0

    private fun turns(n: Int) = if (n == 1) "1 turn" else "$n turns"

    private fun note(r: Round, q: Boolean, text: String, key: String, label: String?) = r.fx(q, text, EffectKind.VOLATILE, key, 1, label = label)

    // ---------------------------------------------------------------- the effective values (types, stats, moves, ability, grounding)

    override fun types(v: BattleView, p: Boolean, t: List<Type>): List<Type> {
        val s = v.side(p)
        var x = storedTypes(s) ?: t
        // Roost: no Flying type until the end of the round (a pure Flying type is Normal meanwhile)
        if (ROOST in s.volatiles && Type.FLYING in x) x = x.filter { it != Type.FLYING }.ifEmpty { listOf(Type.NORMAL) }
        return x
    }

    override fun stats(v: BattleView, p: Boolean, s: Stats): Stats {
        if (!changesStats(v.side(p))) return s
        var x = ownStats(v, p).copy(hp = s.hp)
        if ((v.field.rooms["wonderroom"] ?: 0) > 0) x = x.copy(def = x.spd, spd = x.def) // our numbers replace Wonder Room's swap, so it is done again here
        return x
    }

    override fun moves(v: BattleView, p: Boolean, m: List<Move>): List<Move> {
        transformedMoves(v, p)?.let { return it }
        val s = v.side(p)
        val mimic = s.notes[MIMIC]?.let { v.data.move(it) }
        val sketch = s.notes[SKETCH]?.let { v.data.move(it) }
        if (mimic == null && sketch == null) return m
        return m.map { if (it.id == "mimic" && mimic != null) mimic else if (it.id == "sketch" && sketch != null) sketch else it }
    }

    override fun ability(v: BattleView, p: Boolean, a: String): String {
        val s = v.side(p)
        return if (TRANSFORMED in s.notes) s.notes[T_ABILITY] ?: a else a
    }

    /** Magnet Rise and Telekinesis lift a Pokémon (unless Gravity, Ingrain or Smack Down hold it down); Smack Down grounds it. */
    override fun grounded(v: BattleView, p: Boolean, g: Boolean): Boolean {
        val s = v.side(p).volatiles
        return when {
            SMACK_DOWN in s -> true
            FieldRules.gravityOn(v) || SideMoves.INGRAIN in s -> g
            MAGNET_RISE in s || TELEKINESIS in s -> false
            else -> g
        }
    }

    // ---------------------------------------------------------------- the damage formula and the aim

    /** Electrify: the move of the electrified Pokémon is Electric this round. */
    override fun moveType(h: Hit, type: Type): Type = if (ELECTRIFY in h.aSide.volatiles) Type.ELECTRIC else type

    override fun powerMod(h: Hit): Double {
        var m = 1.0
        val a = h.aSide.volatiles
        if (ME_FIRST in a) m *= ME_FIRST_POWER
        if (CHARGE in a && h.type == Type.ELECTRIC && h.move.id != "charge") m *= CHARGE_POWER
        if (MINIMIZE in h.dSide.volatiles && h.move.id in MINIMIZE_MOVES) m *= MINIMIZE_POWER
        return m
    }

    /** Foresight / Odor Sleuth / Miracle Eye lift an immunity; Thousand Arrows hits Flying types; Tar Shot makes the target weak to Fire. */
    override fun effectiveness(h: Hit, eff: Double): Double {
        var e = eff
        val d = h.dSide.volatiles
        val t = h.type
        if (e == 0.0) {
            val lifted = when {
                FORESIGHT in d && Type.GHOST in h.dTypes && (t == Type.NORMAL || t == Type.FIGHTING) -> Type.GHOST
                MIRACLE_EYE in d && Type.DARK in h.dTypes && t == Type.PSYCHIC -> Type.DARK
                h.move.id == "thousandarrows" && Type.FLYING in h.dTypes && t == Type.GROUND -> Type.FLYING
                else -> null
            }
            if (lifted != null) e = h.dTypes.fold(1.0) { x, ty -> x * (if (ty == lifted) 1.0 else TypeChart.single(t, ty)) }
        }
        if (TAR_SHOT in d && t == Type.FIRE) e *= TAR_SHOT_FIRE
        return e
    }

    /** A Ground move does not hit a Pokémon that Magnet Rise or Telekinesis lifted (Smack Down and Thousand Arrows do). */
    override fun immune(r: Round, h: Hit): Boolean {
        if (!h.move.damaging || h.type != Type.GROUND || h.move.ignoreImmunity || h.move.id in SMACK_MOVES) return false
        val d = h.dSide.volatiles
        if (MAGNET_RISE !in d && TELEKINESIS !in d) return false
        if (r.grounded(!h.attackerIsMain)) return false
        r.fx(h.attackerIsMain, "It doesn't affect ${h.defender.name}…")
        return true
    }

    override fun accuracy(h: Hit, acc: Double?): Double? {
        if (acc == null) return null
        val a = h.aSide
        val d = h.dSide
        if (a.volatiles[LOCK_ON] == a.actions) return null                    // Lock-On / Mind Reader: this move can't miss
        if (TELEKINESIS in d.volatiles && !FieldRules.gravityOn(h.view) && !h.move.ohko) return null // a lifted target can't dodge (a one-hit KO still can miss)
        if (MINIMIZE in d.volatiles && h.move.id in MINIMIZE_MOVES) return null
        // Foresight / Odor Sleuth / Miracle Eye: the target's raised evasion is ignored (the engine subtracts it from the accuracy stage)
        val eva = d.boosts[6]
        if (eva > 0 && !h.move.ignoreEvasion && (FORESIGHT in d.volatiles || MIRACLE_EYE in d.volatiles)) {
            val stage = a.boosts[5]
            return acc * BattleEngine.accStageMult(stage.coerceIn(-6, 6)) / BattleEngine.accStageMult((stage - eva).coerceIn(-6, 6))
        }
        return acc
    }

    // ---------------------------------------------------------------- what can't be used, what must be used

    override fun moveBlocked(v: BattleView, p: Boolean, move: Move): String? {
        if (move.id == Tuning.STRUGGLE) return null // Struggle can always be used
        val s = v.side(p)
        val vol = s.volatiles
        if ((vol[TAUNT] ?: 0) > 0 && !move.damaging) return "after the taunt"
        if ((vol[DISABLE] ?: 0) > 0 && s.notes[DISABLE] == move.id) return "because it is disabled"
        if ((vol[THROAT_CHOP] ?: 0) > 0 && "sound" in move.flags) return "after the throat chop"
        if (healBlocked(v, p) && "heal" in move.flags) return "because of Heal Block"
        if (TORMENT in vol && s.lastMove == move.id && s.charging != move.id && !locked(s, move)) return "twice in a row due to the torment"
        if (IMPRISON in v.side(!p).volatiles && v.moves(!p).any { it.id == move.id }) return "because of Imprison"
        return null
    }

    /** The move is a locked move or an Uproar that goes on: a Torment does not stop it. */
    private fun locked(s: BattleSide, move: Move): Boolean =
        ((s.volatiles[CoreRules.LOCKED] ?: 0) > 0 && s.notes[CoreRules.LOCKED] == move.id) ||
            ((s.volatiles[CoreRules.UPROAR] ?: 0) > 0 && s.notes[CoreRules.UPROAR] == move.id)

    override fun forcedMove(v: BattleView, p: Boolean): Move? {
        val s = v.side(p)
        val pool = v.moves(p).filter { it.id !in CoreRules.NEVER_PICKED }
        // Encore: repeat the last move (a blocked one leaves nothing to use: Struggle)
        if ((s.volatiles[ENCORE] ?: 0) > 0) {
            s.notes[ENCORE]?.let { v.data.move(it) }?.let { m ->
                return if (rulesFirst { it.moveBlocked(v, p, m) } == null) m else BattleEngine.struggle(v.data)
            }
        }
        if (pool.isEmpty()) return null
        fun blocked(m: Move) = rulesFirst { it.moveBlocked(v, p, m) } != null
        if (pool.all { blocked(it) }) return BattleEngine.struggle(v.data) // every move is blocked (Taunt, Disable, Torment ...): Struggle, both sides
        // Ditto (Transform) and Smeargle (Sketch) have no attack: the move that turns them into a fighter comes first
        if (pool.none { it.damaging && MoveChooser.canHurt(v, p, it) }) {
            for (id in listOf("transform", "sketch")) {
                val m = pool.firstOrNull { it.id == id } ?: continue
                if (!blocked(m) && useful(v, p, m) == true) return m
            }
        }
        return null
    }

    /** Heal Block / Burn Up / Double Shock: the move fails before it starts. */
    override fun failReason(r: Round, p: Boolean, move: Move): String? = when {
        healBlocked(r, p) && "heal" in move.flags -> "${name(r, p)} can't heal while Heal Block lasts!"
        move.id == "burnup" && Type.FIRE !in r.types(p) -> FAILED
        move.id == "doubleshock" && Type.ELECTRIC !in r.types(p) -> FAILED
        else -> null
    }

    // ---------------------------------------------------------------- Snatch and Magic Coat

    /** Magic Coat bounces a status move aimed at its user; the move then runs as the bouncer's own against the first user. */
    override fun blocks(r: Round, p: Boolean, move: Move): Boolean {
        val tp = !p
        if (move.damaging || "reflectable" !in move.flags) return false
        if (MAGIC_COAT !in r.side(tp).volatiles || BOUNCING in r.side(p).volatiles) return false
        return bounce(r, tp, move)
    }

    /** [bouncer] sends [move] back at the Pokémon that used it (Magic Coat; an ability like Magic Bounce can call it too). Always true. */
    fun bounce(r: Round, bouncer: Boolean, move: Move): Boolean {
        val s = r.side(bouncer)
        r.fx(bouncer, "${name(r, bouncer)} bounced the ${move.name} back!", EffectKind.BLOCKED, MAGIC_COAT, label = "Bounced")
        s.volatiles[BOUNCING] = 1
        try { r.useMove(bouncer, move) } finally { s.volatiles.remove(BOUNCING) }
        return true
    }

    /**
     * Snatch: the next self-targeting status move of the other side works for the Snatch user instead. R17C FIX-A: Powder: a Fire move of the
     * powdered Pokémon fails and the powder explodes (it loses a quarter of its max HP).
     */
    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        if (POWDER in r.side(p).volatiles && BattleEngine.newHit(r, p, r.mon(p), r.side(p), r.mon(!p), r.side(!p), move).type == Type.FIRE) {
            r.side(p).volatiles.remove(POWDER)
            val n = name(r, p)
            r.fx(p, "When the flame touched the powder on $n, it exploded!", EffectKind.CANT_MOVE, POWDER)
            r.hurt(p, max(1, (r.maxHp(p) + POWDER_DIVISOR / 2) / POWDER_DIVISOR), "$n was hurt by the exploding powder!", EffectKind.RESIDUAL, source = Indirect.POWDER)
            return false
        }
        val tp = !p
        if (move.damaging || "snatch" !in move.flags || r.side(tp).volatiles.remove(SNATCH) == null) return true
        r.fx(tp, "${name(r, tp)} snatched ${name(r, p)}'s ${move.name}!", EffectKind.VOLATILE, SNATCH, 1, label = "Snatched")
        r.useMove(tp, move)
        return false
    }

    // ---------------------------------------------------------------- the status moves

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        val tp = !p
        val n = name(r, p)
        val tn = name(r, tp)
        return when (move.id) {
            "taunt" -> r.addVolatile(tp, TAUNT, lasts(r, tp, TAUNT_TURNS), true, "$tn fell for the taunt!", "Taunted")
            "encore" -> encore(r, p)
            "disable" -> disable(r, p)
            "torment" -> r.addVolatile(tp, TORMENT, 1, true, "$tn was subjected to torment!", "Tormented")
            "imprison" -> imprison(r, p)
            "healblock" -> r.addVolatile(tp, HEAL_BLOCK, lasts(r, tp, HEAL_BLOCK_TURNS), true, "$tn was prevented from healing!", "Heal blocked")

            "psychup" -> { System.arraycopy(r.side(tp).boosts, 0, r.side(p).boosts, 0, 7); note(r, p, "$n copied $tn's stat changes!", "psychup", "Copied"); true }
            "heartswap" -> { swapStages(r, p, 0..6); note(r, p, "$n switched stat changes with its target!", "heartswap", "Swapped"); true }
            "powerswap" -> { swapStages(r, p, listOf(0, 2)); note(r, p, "$n switched all changes to its Attack and Sp. Atk with its target!", "powerswap", "Swapped"); true }
            "guardswap" -> { swapStages(r, p, listOf(1, 3)); note(r, p, "$n switched all changes to its Defense and Sp. Def with its target!", "guardswap", "Swapped"); true }
            "speedswap" -> speedSwap(r, p)
            "powersplit" -> split(r, p, "atk", "spa", "$n shared its power with the target!", "Power split")
            "guardsplit" -> split(r, p, "def", "spd", "$n shared its guard with the target!", "Guard split")
            "powertrick" -> toggle(r, p, POWER_TRICK, "$n switched its Attack and Defense!", "Power Trick")
            "powershift" -> toggle(r, p, POWER_SHIFT, "$n switched its offensive and defensive stats!", "Power Shift")
            "topsyturvy" -> topsyTurvy(r, p)
            "acupressure" -> acupressure(r, p)
            "venomdrench" -> (r.side(tp).status == "psn" || r.side(tp).status == "tox") && r.boost(tp, move.boosts ?: VENOM_STAGES, byFoe = true)
            "rototiller" -> grassBoost(r, p, move.selfBoosts ?: ROTOTILLER_STAGES, needGround = true)
            "flowershield" -> grassBoost(r, p, move.selfBoosts ?: FLOWER_SHIELD_STAGES, needGround = false)
            "charge" -> charge(r, p, move)
            "defensecurl" -> selfCondition(r, p, DEFENSE_CURL, move.boosts ?: CURL_STAGES)
            "minimize" -> selfCondition(r, p, MINIMIZE, move.boosts ?: MINIMIZE_STAGES)
            "tarshot" -> {
                val boosted = r.boost(tp, move.boosts ?: TAR_SHOT_STAGES, byFoe = true)
                val tarred = r.addVolatile(tp, TAR_SHOT, 1, true, "$tn became weaker to fire!", "Tar Shot")
                boosted || tarred
            }

            "transform" -> transformInto(r, p)
            "soak" -> setTypesOf(r, tp, listOf(Type.WATER), "$tn transformed into the Water type!")
            "magicpowder" -> setTypesOf(r, tp, listOf(Type.PSYCHIC), "$tn's type changed to Psychic!")
            "reflecttype" -> {
                val t = ownTypes(r, tp)
                setTypes(r, p, t, "$n's type became the same as $tn's type!")
                true
            }
            "conversion" -> conversion(r, p, move)
            "conversion2" -> conversion2(r, p)
            "forestscurse" -> addType(r, tp, Type.GRASS)
            "trickortreat" -> addType(r, tp, Type.GHOST)
            "camouflage" -> camouflage(r, p)
            "roost" -> roost(r, p, move)
            "electrify" -> !r.movedThisRound(tp) && r.addVolatile(tp, ELECTRIFY, 1, true, "$tn's moves have been electrified!", "Electrified")
            "psychoshift" -> psychoShift(r, p, move)
            "partingshot" -> r.boost(tp, PARTING_SHOT_STAGES, byFoe = true) // the drops are code in the games; the user does not switch out here
            "powder" -> r.addVolatile(tp, POWDER, 1, true, "$tn is covered in powder!", "Powdered")

            "magnetrise" -> magnetRise(r, p)
            "telekinesis" -> telekinesis(r, p)
            "lockon", "mindreader" -> {
                r.side(p).volatiles[LOCK_ON] = r.side(p).actions + 1 // the user's next action
                note(r, p, "$n took aim at $tn!", LOCK_ON, "Locked on")
                true
            }
            "foresight", "odorsleuth" -> r.addVolatile(tp, FORESIGHT, 1, true, "$n identified $tn!", "Identified")
            "miracleeye" -> r.addVolatile(tp, MIRACLE_EYE, 1, true, "$n identified $tn!", "Identified")

            "metronome" -> metronome(r, p)
            "sleeptalk" -> sleepTalk(r, p, move)
            "copycat" -> copycat(r, p)
            "mirrormove" -> mirrorMove(r, p)
            "mefirst" -> meFirst(r, p)
            "naturepower" -> naturePower(r, p)
            "snatch" -> arm(r, p, SNATCH, "$n waits for a target to make a move!", "Snatch")
            "magiccoat" -> arm(r, p, MAGIC_COAT, "$n shrouded itself with Magic Coat!", "Magic Coat")
            "mimic" -> mimic(r, p)
            "sketch" -> sketch(r, p)
            else -> null
        }
    }

    // ---- move control ----

    private fun encorable(m: Move) = m.id !in NO_ENCORE && "failencore" !in m.flags

    private fun encore(r: Round, p: Boolean): Boolean {
        val tp = !p
        val last = lastOf(r, tp) ?: return false
        if (!encorable(last)) return false
        if (!r.addVolatile(tp, ENCORE, lasts(r, tp, ENCORE_TURNS), true, "${name(r, tp)} received an encore!", "Encore")) return false
        r.side(tp).notes[ENCORE] = last.id
        return true
    }

    private fun disable(r: Round, p: Boolean): Boolean {
        val tp = !p
        val last = lastOf(r, tp)?.takeIf { it.id != Tuning.STRUGGLE } ?: return false
        if (!r.addVolatile(tp, DISABLE, lasts(r, tp, DISABLE_TURNS), true, "${name(r, tp)}'s ${last.name} was disabled!", "Disabled")) return false
        r.side(tp).notes[DISABLE] = last.id
        return true
    }

    private fun imprison(r: Round, p: Boolean): Boolean {
        val mine = r.moves(p).map { it.id }.toSet()
        if (r.moves(!p).none { it.id in mine }) return false // nothing to seal
        return r.addVolatile(p, IMPRISON, 1, false, "${name(r, p)} sealed any moves its target shares with it!", "Imprison")
    }

    /** Arms a one-round condition of the user (Snatch, Magic Coat); it fails when the other side has acted already. */
    private fun arm(r: Round, p: Boolean, key: String, text: String, label: String): Boolean {
        if (r.movedThisRound(!p)) return false
        return r.addVolatile(p, key, 1, false, text, label)
    }

    // ---- stat tricks ----

    private fun swapStages(r: Round, p: Boolean, indices: Iterable<Int>) {
        val a = r.side(p).boosts
        val b = r.side(!p).boosts
        for (i in indices) { val x = a[i]; a[i] = b[i]; b[i] = x }
    }

    private fun speedSwap(r: Round, p: Boolean): Boolean {
        val u = ownStats(r, p).spe
        val t = ownStats(r, !p).spe
        r.side(p).notes[OVERRIDE + "spe"] = t.toString()
        r.side(!p).notes[OVERRIDE + "spe"] = u.toString()
        note(r, p, "${name(r, p)} exchanged Speed stats with its target!", "speedswap", "Swapped")
        return true
    }

    private fun split(r: Round, p: Boolean, first: String, second: String, text: String, label: String): Boolean {
        val u = ownStats(r, p)
        val t = ownStats(r, !p)
        for (key in listOf(first, second)) {
            val stat = STAT_OF.getValue(key)
            val avg = ((u[stat] + t[stat]) / 2).toString()
            r.side(p).notes[OVERRIDE + key] = avg
            r.side(!p).notes[OVERRIDE + key] = avg
        }
        note(r, p, text, "split", label)
        return true
    }

    private fun toggle(r: Round, p: Boolean, key: String, text: String, label: String): Boolean {
        val v = r.side(p).volatiles
        if (v.remove(key) == null) v[key] = 1
        note(r, p, text, key, label)
        return true
    }

    private fun topsyTurvy(r: Round, p: Boolean): Boolean {
        val b = r.side(!p).boosts
        if (b.all { it == 0 }) return false
        for (i in b.indices) b[i] = -b[i]
        note(r, !p, "All of ${name(r, !p)}'s stat changes were inverted!", "topsyturvy", "Inverted")
        return true
    }

    private fun acupressure(r: Round, p: Boolean): Boolean {
        val b = r.side(p).boosts
        val open = BattleEngine.BOOST_KEYS.indices.filter { b[it] < r.moveChoice.stageLimit(it) }
        if (open.isEmpty()) return false
        return r.boost(p, mapOf(BattleEngine.BOOST_KEYS[open[r.rng.nextInt(open.size)]] to ACUPRESSURE_RISE), byFoe = false)
    }

    /** Rototiller / Flower Shield: every Grass type in the fight (on the ground when [needGround]) gets [stages]. True when a stat moved. */
    private fun grassBoost(r: Round, p: Boolean, stages: Map<String, Int>, needGround: Boolean): Boolean {
        var any = false
        for (q in listOf(p, !p)) {
            if (r.mon(q).hp <= 0 || Type.GRASS !in r.types(q) || (needGround && !r.grounded(q))) continue
            if (r.boost(q, stages, byFoe = q != p)) any = true
        }
        return any
    }

    /** Charge always works: +1 Sp. Def (when it can) and the next Electric move is doubled (it stays until that move: no countdown). */
    private fun charge(r: Round, p: Boolean, move: Move): Boolean {
        r.boost(p, move.boosts ?: CHARGE_STAGES, byFoe = false)
        r.side(p).volatiles[CHARGE] = 1
        note(r, p, "${name(r, p)} began charging power!", CHARGE, "Charged")
        return true
    }

    /** Defense Curl / Minimize: the stage change and a condition other moves read ("defensecurl" for Rollout, "minimize" for the stomping moves). */
    private fun selfCondition(r: Round, p: Boolean, key: String, stages: Map<String, Int>): Boolean {
        val boosted = r.boost(p, stages, byFoe = false)
        val started = r.addVolatile(p, key, 1, false)
        return boosted || started
    }

    // ---- Transform ----

    /**
     * [p]'s Pokémon turns into the other one for the rest of the fight: its types, stats (not HP), stat changes, moves and ability (through the
     * hooks), and the weight for the weight moves. The picture does not change. False against a Pokémon that has transformed or hides behind a
     * substitute, or when [p] has transformed already. An ability like Imposter calls this too.
     */
    fun transformInto(r: Round, p: Boolean): Boolean {
        val tp = !p
        val s = r.side(p)
        val t = r.side(tp)
        if (TRANSFORMED in s.notes || TRANSFORMED in t.notes || SideMoves.SUBSTITUTE in t.volatiles || r.mon(tp).hp <= 0) return false
        s.notes[TRANSFORMED] = r.mon(tp).species.toString()
        s.notes[T_STATS] = encodeStats(ownStats(r, tp))
        s.notes[TYPES] = ownTypes(r, tp).joinToString(",") { it.name }
        s.notes[T_MOVES] = r.moves(tp).joinToString(",") { it.id }
        s.notes[T_ABILITY] = r.ability(tp)
        for (key in STAT_KEYS) s.notes.remove(OVERRIDE + key)
        s.volatiles.remove(POWER_TRICK); s.volatiles.remove(POWER_SHIFT)
        System.arraycopy(t.boosts, 0, s.boosts, 0, 7)
        note(r, p, "${name(r, p)} transformed into ${name(r, tp)}!", "transform", "Transformed")
        return true
    }

    // ---- types ----

    private fun typeLabel(types: List<Type>) = if (types.isEmpty()) "No type" else types.joinToString("/") { it.label }

    private fun setTypes(r: Round, q: Boolean, types: List<Type>, text: String) {
        r.side(q).notes[TYPES] = types.joinToString(",") { it.name }
        note(r, q, text, "type", typeLabel(types))
    }

    /** Soak / Magic Powder: [q] becomes exactly [types]; it fails when it is already so. */
    private fun setTypesOf(r: Round, q: Boolean, types: List<Type>, text: String): Boolean {
        if (ownTypes(r, q) == types) return false
        setTypes(r, q, types, text)
        return true
    }

    /** Forest's Curse / Trick-or-Treat: [type] is added to the types of [q]; it fails when [q] has it. */
    private fun addType(r: Round, q: Boolean, type: Type): Boolean {
        val own = ownTypes(r, q)
        if (type in own) return false
        setTypes(r, q, own + type, "${type.label} type was added to ${name(r, q)}!")
        return true
    }

    /** Conversion: the user takes the type of one of its other moves that it does not have yet (mainline). */
    private fun conversion(r: Round, p: Boolean, move: Move): Boolean {
        val own = ownTypes(r, p)
        val options = r.moves(p).filter { it.id != move.id }.map { it.type }.distinct().filter { it !in own }
        if (options.isEmpty()) return false
        val t = options[r.rng.nextInt(options.size)]
        setTypes(r, p, listOf(t), "${name(r, p)} transformed into the ${t.label} type!")
        return true
    }

    /** Conversion 2: the user takes a type that resists the last move of the other side. */
    private fun conversion2(r: Round, p: Boolean): Boolean {
        val last = lastOf(r, !p)?.takeIf { it.id != Tuning.STRUGGLE } ?: return false
        val own = ownTypes(r, p)
        val options = Type.entries.filter { TypeChart.single(last.type, it) < 1.0 && it !in own }
        if (options.isEmpty()) return false
        val t = options[r.rng.nextInt(options.size)]
        setTypes(r, p, listOf(t), "${name(r, p)} transformed into the ${t.label} type!")
        return true
    }

    private fun camouflage(r: Round, p: Boolean): Boolean {
        val t = when (r.terrain()) {
            "electric" -> Type.ELECTRIC
            "grassy" -> Type.GRASS
            "misty" -> Type.FAIRY
            "psychic" -> Type.PSYCHIC
            else -> Type.NORMAL
        }
        if (ownTypes(r, p) == listOf(t)) return false
        setTypes(r, p, listOf(t), "${name(r, p)}'s type changed to ${t.label}!")
        return true
    }

    private fun roost(r: Round, p: Boolean, move: Move): Boolean {
        val (a, b) = move.heal ?: ROOST_HEAL
        if (r.mon(p).hp >= r.maxHp(p)) return false
        r.heal(p, max(1, r.maxHp(p) * a / b), "${name(r, p)} regained health!", EffectKind.HEAL)
        r.side(p).volatiles[ROOST] = 1 // no Flying type until the end of the round
        return true
    }

    /** Psycho Shift: the user's status goes to the target when it can get it, and the user is cured. */
    private fun psychoShift(r: Round, p: Boolean, move: Move): Boolean {
        val s = r.side(p)
        val status = s.status ?: return false
        if (!r.setStatus(!p, status, move.type, byFoe = true)) return false
        s.status = null; s.statusTurns = 0; s.volatiles.remove(CoreRules.SLEEP_TURNS)
        r.fx(p, "${name(r, p)}'s ${STATUS_NAMES[status] ?: status} was cured!", EffectKind.STATUS_CURED, status)
        return true
    }

    // ---- grounding ----

    private fun magnetRise(r: Round, p: Boolean): Boolean {
        val v = r.side(p).volatiles
        if (FieldRules.gravityOn(r) || SideMoves.INGRAIN in v || SMACK_DOWN in v || TELEKINESIS in v) return false
        return r.addVolatile(p, MAGNET_RISE, MAGNET_RISE_TURNS, false, "${name(r, p)} levitated with electromagnetism!", "Magnet Rise")
    }

    private fun telekinesis(r: Round, p: Boolean): Boolean {
        val tp = !p
        val v = r.side(tp).volatiles
        if (FieldRules.gravityOn(r) || SideMoves.INGRAIN in v || SMACK_DOWN in v) return false
        if (r.data.sp(r.mon(tp).species).baseNum in NO_TELEKINESIS) return false
        return r.addVolatile(tp, TELEKINESIS, lasts(r, tp, TELEKINESIS_TURNS), true, "${name(r, tp)} was hurled into the air!", "Telekinesis")
    }

    // ---- calling moves ----

    /** A move another move may call: no never-picked move, no calling move, no move with a charge turn, a rest turn or a lock (those need their own turns). */
    private fun callable(m: Move): Boolean =
        m.id != Tuning.STRUGGLE && m.id !in CoreRules.NEVER_PICKED && m.id !in CALLERS && !CoreRules.isCharge(m) && !CoreRules.isRecharge(m) &&
            m.selfVolatile != CoreRules.LOCKED && m.selfVolatile != CoreRules.UPROAR

    /** The moves Metronome can call: the data's `metronome` flag when it has one, else the mainline exclusion list; sorted by id. */
    internal fun metronomePool(data: GameData): List<Move> {
        val flagged = data.moves.values.any { "metronome" in it.flags }
        return data.moves.values.filter { callable(it) && (if (flagged) "metronome" in it.flags else it.id !in METRONOME_EXCLUDED) }.sortedBy { it.id }
    }

    private fun metronome(r: Round, p: Boolean): Boolean {
        val pool = metronomePool(r.data)
        if (pool.isEmpty()) return false
        return r.useMove(p, pool[r.rng.nextInt(pool.size)])
    }

    private fun sleepTalk(r: Round, p: Boolean, move: Move): Boolean {
        if (r.side(p).status != "slp" && !AbilitiesMore.comatose(r, p)) return false // R17C FIX-A: Comatose counts as asleep
        val pool = r.moves(p).filter { it.id != move.id && callable(it) && "nosleeptalk" !in it.flags }
        if (pool.isEmpty()) return false
        return r.useMove(p, pool[r.rng.nextInt(pool.size)])
    }

    private fun copycat(r: Round, p: Boolean): Boolean {
        val m = r.side(false).notes[LAST_ANY]?.let { r.data.move(it) } ?: return false
        if (!callable(m) || m.id in NO_COPYCAT || "failcopycat" in m.flags) return false
        return r.useMove(p, m)
    }

    private fun mirrorMove(r: Round, p: Boolean): Boolean {
        val m = lastOf(r, !p) ?: return false
        if (!callable(m) || "mirror" !in m.flags) return false
        return r.useMove(p, m)
    }

    /** Me First: the move the other side is about to use, at x1.5, when it has not moved yet and the move attacks. */
    private fun meFirst(r: Round, p: Boolean): Boolean {
        val planned = r.plannedMove(!p) ?: return false
        if (r.movedThisRound(!p) || !planned.damaging || !callable(planned) || planned.id in NO_ME_FIRST || "failmefirst" in planned.flags) return false
        val v = r.side(p).volatiles
        v[ME_FIRST] = 1
        try { return r.useMove(p, planned) } finally { v.remove(ME_FIRST) }
    }

    private fun naturePower(r: Round, p: Boolean): Boolean {
        val id = when (r.terrain()) {
            "electric" -> "thunderbolt"
            "grassy" -> "energyball"
            "misty" -> "moonblast"
            "psychic" -> "psychic"
            else -> "triattack"
        }
        val m = r.data.move(id) ?: return false
        return r.useMove(p, m)
    }

    /** Mimic / Sketch: the move in the slot becomes the other side's last move until the fight ends (Sketch does not teach it for good). */
    private fun mimic(r: Round, p: Boolean): Boolean {
        val m = lastOf(r, !p) ?: return false
        if (m.id in NO_MIMIC || "failmimic" in m.flags || r.moves(p).any { it.id == m.id }) return false
        r.side(p).notes[MIMIC] = m.id
        note(r, p, "${name(r, p)} learned ${m.name}!", "mimic", "Mimic")
        return true
    }

    private fun sketch(r: Round, p: Boolean): Boolean {
        val m = lastOf(r, !p) ?: return false
        if (m.id in NO_SKETCH || "nosketch" in m.flags || r.moves(p).any { it.id == m.id }) return false
        r.side(p).notes[SKETCH] = m.id
        note(r, p, "${name(r, p)} sketched ${m.name}!", "sketch", "Sketch")
        return true
    }

    // ---------------------------------------------------------------- after a move, a hit, a secondary, the round

    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        r.side(false).notes[LAST_ANY] = move.id
        val s = r.side(p)
        // Lock-On: this was the action it was for (or it is over: the action was lost)
        s.volatiles[LOCK_ON]?.let { at -> if (at <= s.actions) s.volatiles.remove(LOCK_ON) }
        // Charge: an Electric move uses it up
        if (CHARGE in s.volatiles && move.type == Type.ELECTRIC && move.id != "charge") s.volatiles.remove(CHARGE)
    }

    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        when {
            move.id == "burnup" -> loseType(r, p, Type.FIRE, "${name(r, p)} burned itself out!")
            move.id == "doubleshock" -> loseType(r, p, Type.ELECTRIC, "${name(r, p)} used up all its electricity!")
        }
        if ((move.id in SMACK_MOVES || move.volatileStatus == SMACK_DOWN) && r.mon(tp).hp > 0 && !r.grounded(tp)) {
            val tv = r.side(tp).volatiles
            tv.remove(MAGNET_RISE); tv.remove(TELEKINESIS)
            r.addVolatile(tp, SMACK_DOWN, 1, true, "${name(r, tp)} fell straight down!", "Grounded", quiet = true)
        }
    }

    private fun loseType(r: Round, p: Boolean, type: Type, text: String) {
        val own = ownTypes(r, p)
        if (r.mon(p).hp > 0 && type in own) setTypes(r, p, own - type, text)
    }

    /** Throat Chop: the target can't use sound moves for a while. */
    override fun secondary(r: Round, p: Boolean, move: Move, sec: Secondary) {
        if (move.id != "throatchop") return
        val tp = !p
        r.addVolatile(tp, THROAT_CHOP, lasts(r, tp, THROAT_CHOP_TURNS), true, "${name(r, tp)} can't use sound-based moves!", "Throat Chop", quiet = true)
    }

    /** The countdowns, and the conditions that last for one round only. */
    override fun endOfRound(r: Round) {
        val gravity = FieldRules.gravityOn(r)
        for (q in listOf(true, false)) {
            val s = r.side(q)
            for (key in ROUND_LONG) s.volatiles.remove(key)
            if (gravity) { s.volatiles.remove(MAGNET_RISE); s.volatiles.remove(TELEKINESIS) } // Gravity brings everything down for good
            if (r.mon(q).hp <= 0) continue
            for ((key, line) in ENDS) {
                val left = s.volatiles[key] ?: continue
                if (left - 1 > 0) { s.volatiles[key] = left - 1; continue }
                s.volatiles.remove(key)
                s.notes.remove(key)
                r.fx(q, line.format(name(r, q)), EffectKind.VOLATILE, key, -1)
            }
        }
    }

    /** R17C FIX-A: a free hit is no round: Magic Coat, Snatch, Electrify, Roost and Powder set in it end now, not in the next real round. */
    override fun afterFreeHit(r: Round) {
        for (q in listOf(true, false)) for (key in ROUND_LONG) r.side(q).volatiles.remove(key)
    }

    // ---------------------------------------------------------------- the move choice

    /** Asks the engine's own rule (null) for the moves that only change a stat; the others say when they still change something. */
    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        val tp = !p
        val me = v.side(p)
        val foe = v.side(tp)
        val foeAlive = v.mon(tp).hp > 0
        fun differ(a: Iterable<Int>) = a.any { me.boosts[it] != foe.boosts[it] }
        fun foeKnows(f: (Move) -> Boolean) = v.moves(tp).any(f)
        // R17C FIX-A: a Ground move does not hit a Pokémon that Magnet Rise / Telekinesis lifted (see [immune])
        if (move.damaging && foeAlive && (MAGNET_RISE in foe.volatiles || TELEKINESIS in foe.volatiles) && !move.ignoreImmunity && move.id !in SMACK_MOVES &&
            !v.grounded(tp) && BattleEngine.newHit(v, p, v.mon(p), me, v.mon(tp), foe, move).type == Type.GROUND) return false
        return when (move.id) {
            "burnup" -> Type.FIRE in v.types(p)
            "doubleshock" -> Type.ELECTRIC in v.types(p)
            "taunt" -> foeAlive && TAUNT !in foe.volatiles && foeKnows { !it.damaging }
            "encore" -> foeAlive && ENCORE !in foe.volatiles && lastOf(v, tp)?.let { encorable(it) } == true
            "disable" -> foeAlive && DISABLE !in foe.volatiles && lastOf(v, tp)?.let { it.id != Tuning.STRUGGLE } == true
            "torment" -> foeAlive && TORMENT !in foe.volatiles
            "imprison" -> IMPRISON !in me.volatiles && v.moves(p).map { it.id }.let { mine -> foeKnows { it.id in mine } }
            "healblock" -> foeAlive && !healBlocked(v, tp)
            "psychup" -> foe.boosts.indices.any { foe.boosts[it] > me.boosts[it] }
            "heartswap" -> differ(0..6)
            "powerswap" -> differ(listOf(0, 2))
            "guardswap" -> differ(listOf(1, 3))
            "speedswap" -> ownStats(v, p).spe < ownStats(v, tp).spe
            "powersplit" -> ownStats(v, p).let { u -> ownStats(v, tp).let { t -> u.atk + u.spa < t.atk + t.spa } }
            "guardsplit" -> ownStats(v, p).let { u -> ownStats(v, tp).let { t -> u.def + u.spd < t.def + t.spd } }
            "powertrick" -> POWER_TRICK !in me.volatiles && ownStats(v, p).let { it.def > it.atk }
            "powershift" -> POWER_SHIFT !in me.volatiles && ownStats(v, p).let { it.def + it.spd > it.atk + it.spa }
            "topsyturvy" -> foe.boosts.any { it > 0 }
            "venomdrench" -> foeAlive && (foe.status == "psn" || foe.status == "tox") && listOf(0, 2, 4).any { foe.boosts[it] > -6 }
            "rototiller" -> Type.GRASS in v.types(p) && v.grounded(p) && (me.boosts[0] < 6 || me.boosts[2] < 6)
            "flowershield" -> Type.GRASS in v.types(p) && me.boosts[1] < 6
            "charge" -> CHARGE !in me.volatiles && v.moves(p).any { it.damaging && it.type == Type.ELECTRIC }
            "defensecurl" -> DEFENSE_CURL !in me.volatiles || me.boosts[1] < 6
            "minimize" -> MINIMIZE !in me.volatiles
            "tarshot" -> foeAlive && TAR_SHOT !in foe.volatiles
            "transform" -> canTransform(v, p)
            "soak" -> foeAlive && ownTypes(v, tp) != listOf(Type.WATER)
            "magicpowder" -> foeAlive && ownTypes(v, tp) != listOf(Type.PSYCHIC) && Type.GRASS !in v.types(tp)
            "reflecttype" -> foeAlive && ownTypes(v, tp) != ownTypes(v, p)
            "conversion" -> v.moves(p).any { it.id != move.id && it.type !in ownTypes(v, p) }
            "conversion2" -> lastOf(v, tp)?.let { l -> l.id != Tuning.STRUGGLE && Type.entries.any { TypeChart.single(l.type, it) < 1.0 && it !in ownTypes(v, p) } } == true
            "forestscurse" -> foeAlive && Type.GRASS !in ownTypes(v, tp)
            "trickortreat" -> foeAlive && Type.GHOST !in ownTypes(v, tp)
            "camouflage" -> false // it only changes the user's type to something that is worth less than its own
            "roost" -> v.mon(p).hp < v.maxHp(p) && !healBlocked(v, p)
            "electrify" -> foeAlive && v.acts(tp) && ELECTRIFY !in foe.volatiles && foeKnows { it.damaging && it.type != Type.ELECTRIC }
            "partingshot" -> foeAlive && (foe.boosts[0] > -6 || foe.boosts[2] > -6)
            "powder" -> foeAlive && v.acts(tp) && POWDER !in foe.volatiles && foeKnows { it.type == Type.FIRE }
            "psychoshift" -> me.status != null && foe.status == null && foeAlive
            "magnetrise" -> MAGNET_RISE !in me.volatiles && v.grounded(p) && !FieldRules.gravityOn(v)
            "telekinesis" -> foeAlive && TELEKINESIS !in foe.volatiles && !FieldRules.gravityOn(v)
            "lockon", "mindreader" -> me.volatiles[LOCK_ON] != me.actions + 1 && v.moves(p).any { it.damaging && (it.accuracy ?: 100) < 100 }
            "foresight", "odorsleuth" -> foeAlive && FORESIGHT !in foe.volatiles && (foe.boosts[6] > 0 || Type.GHOST in v.types(tp))
            "miracleeye" -> foeAlive && MIRACLE_EYE !in foe.volatiles && (foe.boosts[6] > 0 || Type.DARK in v.types(tp))
            "metronome" -> true
            "sleeptalk" -> (me.status == "slp" || AbilitiesMore.comatose(v, p)) && v.moves(p).any { it.id != move.id && callable(it) && "nosleeptalk" !in it.flags }
            "copycat" -> v.side(false).notes[LAST_ANY] != null
            "mirrormove" -> lastOf(v, tp)?.let { callable(it) && "mirror" in it.flags } == true
            "mefirst" -> foeAlive && v.acts(tp) && foeKnows { it.damaging && callable(it) }
            "naturepower" -> true
            "snatch" -> foeAlive && v.acts(tp) && foeKnows { "snatch" in it.flags }
            "magiccoat" -> foeAlive && v.acts(tp) && foeKnows { !it.damaging && "reflectable" in it.flags }
            "mimic" -> lastOf(v, tp)?.let { it.id !in NO_MIMIC && "failmimic" !in it.flags && v.moves(p).none { m -> m.id == it.id } } == true
            "sketch" -> lastOf(v, tp)?.let { it.id !in NO_SKETCH && "nosketch" !in it.flags && v.moves(p).none { m -> m.id == it.id } } == true
            else -> null
        }
    }

    /** Transform could work now: neither side has transformed and the target has no substitute. */
    private fun canTransform(v: BattleView, p: Boolean): Boolean =
        v.mon(!p).hp > 0 && TRANSFORMED !in v.side(p).notes && TRANSFORMED !in v.side(!p).notes && SideMoves.SUBSTITUTE !in v.side(!p).volatiles

    // ---------------------------------------------------------------- the move text

    override fun describe(m: Move): List<String> {
        val text = when (m.id) {
            "taunt" -> "The target can't use status moves for $TAUNT_TURNS turns."
            "encore" -> "The target has to repeat the move it used last for $ENCORE_TURNS turns. Fails if it has not used a move yet."
            "disable" -> "The target can't use the move it used last for $DISABLE_TURNS turns. Fails if it has not used a move yet."
            "torment" -> "The target can't use the same move twice in a row."
            "imprison" -> "The target can't use any move that you also know."
            "healblock" -> "The target can't heal for $HEAL_BLOCK_TURNS turns: healing moves fail and draining restores nothing."
            "throatchop" -> "The target can't use sound moves for $THROAT_CHOP_TURNS turns."
            "psychup" -> "Copies the target's stat changes."
            "heartswap" -> "Swaps all stat changes with the target."
            "powerswap" -> "Swaps the Attack and Sp. Atk changes with the target."
            "guardswap" -> "Swaps the Defense and Sp. Def changes with the target."
            "speedswap" -> "Swaps Speed stats with the target."
            "powersplit" -> "You and the target each get the average of your Attack stats and the average of your Sp. Atk stats."
            "guardsplit" -> "You and the target each get the average of your Defense stats and the average of your Sp. Def stats."
            "powertrick" -> "Swaps your Attack and Defense stats. Using it again swaps them back."
            "powershift" -> "Swaps your Attack with your Defense and your Sp. Atk with your Sp. Def. Using it again swaps them back."
            "topsyturvy" -> "Turns all of the target's stat changes around: raises become drops and drops become raises."
            "acupressure" -> "Raises a random stat of yours by $ACUPRESSURE_RISE."
            "venomdrench" -> "Lowers the Attack, Sp. Atk and Speed of a poisoned target by 1."
            "rototiller" -> "Raises the Attack and Sp. Atk of every Grass type on the ground by 1, yours or the target's."
            "flowershield" -> "Raises the Defense of every Grass type by 1, yours or the target's."
            "charge" -> "Your next Electric move deals double damage."
            "defensecurl" -> "Rollout and Ice Ball deal double damage after you curl up."
            "minimize" -> "Stomp, Body Slam and similar moves never miss you and deal double damage to you."
            "tarshot" -> "The target takes double damage from Fire moves."
            "transform" -> "Turns you into the target until the fight ends: its types, stats (not HP), stat changes, moves and ability. Your picture does not change."
            "soak" -> "Makes the target a pure Water type."
            "magicpowder" -> "Makes the target a pure Psychic type."
            "reflecttype" -> "Your types become the same as the target's."
            "conversion" -> "Changes your type to the type of one of your other moves."
            "conversion2" -> "Changes your type to one that resists the target's last move."
            "forestscurse" -> "Adds the Grass type to the target."
            "trickortreat" -> "Adds the Ghost type to the target."
            "camouflage" -> "Changes your type to match the terrain, or to Normal without one."
            "burnup" -> "Fails unless you are a Fire type. You lose the Fire type after the hit."
            "doubleshock" -> "Fails unless you are an Electric type. You lose the Electric type after the hit."
            "roost" -> "You lose the Flying type until the end of the turn."
            "electrify" -> "The target's move this turn becomes Electric. Fails if the target has moved already."
            "psychoshift" -> "Gives your status problem to the target and cures yours."
            "partingshot" -> "Lowers the target's Attack and Sp. Atk by 1. You do not switch out."
            "powder" -> "The target's Fire move this turn fails, and it loses a quarter of its max HP."
            "magnetrise" -> "Makes you immune to Ground moves for $MAGNET_RISE_TURNS turns."
            "telekinesis" -> "Lifts the target for $TELEKINESIS_TURNS turns: Ground moves miss it and every move except a one-hit KO hits it."
            "smackdown" -> "Brings a flying or floating target down to the ground until the fight ends."
            "thousandarrows" -> "Hits Flying types too, and brings a flying or floating target down to the ground until the fight ends."
            "lockon", "mindreader" -> "Your next move can't miss."
            "foresight", "odorsleuth" -> "Normal and Fighting moves hit Ghost types, and the target's evasion is ignored."
            "miracleeye" -> "Psychic moves hit Dark types, and the target's evasion is ignored."
            "metronome" -> "Uses a random move. Some moves can't be called."
            "sleeptalk" -> "Uses a random one of your other moves while you are asleep."
            "copycat" -> "Uses the move that was used last in this fight."
            "mirrormove" -> "Uses the move the target used last."
            "mefirst" -> "Uses the move the target is about to use, with 1.5 times the power. Fails if the target has moved already or does not attack."
            "naturepower" -> "Uses Tri Attack, or another move that depends on the terrain."
            "snatch" -> "Steals the target's next self-targeting status move this turn."
            "magiccoat" -> "Bounces back status moves aimed at you this turn."
            "mimic" -> "Copies the target's last move in place of Mimic until the fight ends."
            "sketch" -> "Copies the target's last move in place of Sketch until the fight ends. Unlike in the games, it is not learned for good."
            else -> null
        }
        return listOfNotNull(text)
    }
}
