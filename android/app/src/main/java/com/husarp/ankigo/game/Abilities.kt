package com.husarp.ankigo.game

import kotlin.math.max

/**
 * R17C rule set "Abilities" (R17C-9a, PLAN 229): the ability framework and the common abilities (the first 102 rows of [AbilityText]; the rarer ones are
 * [AbilitiesMore], R17C-9b, which uses the helpers of this object: [dAb] [aAb] [say] [announce] [roll] [inflict] [cure] [enter] [contacts]).
 * Mainline Gen 9 rules, a "turn" is one battle ROUND, both sides follow the same rules ([p] = the Main's side). An ability is matched by its
 * normalized id ("Keen Eye" -> "keeneye", [BattleView.normalizeAbility]); `v.ability(p)` is the EFFECTIVE one: the species' own, then Transform
 * (R17C-8), then [OVERRIDE] (set by Trace here; [AbilityMoves] - Role Play, Skill Swap, Gastro Acid ... - and Mummy write it too, "" = no ability).
 *
 * MOLD BREAKER (and Teravolt, Turboblaze): while its user performs a MOVE, the target's ability is read as "" if it is one of [BREAKABLE]
 * (Sturdy, Levitate, Clear Body ...). Inside a round this is the `ability` hook itself (it reads [BattleEngine.Round.actor]); the hooks that get a
 * [Hit] also work without a round (the move choice's estimates) through [dAb]. Abilities that act on entering (Intimidate ...) are not moves
 * and are never ignored. R17C-9b: Mycelium Might does the same for its STATUS moves ([breaksMold]).
 *
 * WHERE THINGS SIT: a damage factor in [powerMod] / [attackMod] / [finalMod] / [stab]; an immunity (absorbing a type, Levitate, Soundproof ...) in
 * [immune] (the move choice asks the same rule in [useful], so a Smart side does not pick a move that will be absorbed); what a hit sets off in
 * [afterDamage] (Static, Rough Skin ...) or [afterHit] (Poison Touch, Stench); stat rules in [stageChange] / [afterStageChange]; statuses and
 * lasting conditions in [blockStatus] / [blockVolatile] / [afterStatus]; the turn in [speedMod] / [priority] / [onEnter] / [endOfRound] / [onLeave];
 * the hooks R17C-9a added to [BattleRules]: [secondaryChance], [indirectDamage], [drain], [ignoresStage], [wakeMod]. Other sets call
 * [infiltrates] (SideMoves) directly.
 *
 * ON SCREEN: an ability that acts writes a line "<name>'s <Ability>..." with the ability's name as the float label (kind OTHER with a label, or
 * a HEAL / RECOIL / RESIDUAL with one); a result that has its own line (a status, a stat change) follows it.
 *
 * KEYS. [BattleSide.notes]: [OVERRIDE]. [BattleSide.volatiles] (private, prefix "ab."): "ab.flashfire" (Flash Fire is lit), "ab.rounds" (end of
 * rounds seen, for Speed Boost).
 */
internal object Abilities : BattleRules {
    // ---- the numbers (mainline; each is used by the sentence in AbilityText) ----
    /** Static, Poison Point, Flame Body, Cute Charm, Effect Spore, Poison Touch: the chance (percent) of the effect. */
    const val CONTACT_CHANCE = 30
    const val SHED_SKIN_CHANCE = 33
    const val STENCH_CHANCE = 10
    /** Cursed Body: the chance (percent) that the move that hit it is disabled, and for how many turns. */
    const val CURSED_BODY_CHANCE = 30
    const val CURSED_BODY_TURNS = 4
    /** Technician: moves up to this power are boosted. */
    const val TECHNICIAN_MAX_POWER = 60
    /** Overgrow, Blaze, Torrent, Swarm: active at this share of the max HP or less (1/3), and the boost. */
    private const val PINCH_DIVISOR = 3
    private const val PINCH_BOOST = 1.5
    private const val TECHNICIAN_BOOST = 1.5
    private const val IRON_FIST_BOOST = 1.2
    private const val STRONG_JAW_BOOST = 1.5
    private const val MEGA_LAUNCHER_BOOST = 1.5
    private const val TOUGH_CLAWS_BOOST = 1.3
    private const val RECKLESS_BOOST = 1.2
    private const val SHEER_FORCE_BOOST = 1.3
    private const val RIVALRY_SAME = 1.25
    private const val RIVALRY_OTHER = 0.75
    private const val FLASH_FIRE_BOOST = 1.5
    private const val THICK_FAT_FACTOR = 0.5
    private const val HEATPROOF_FACTOR = 0.5
    private const val DRY_SKIN_FIRE = 1.25
    private const val SOLID_ROCK_FACTOR = 0.75
    private const val TINTED_LENS_FACTOR = 2.0
    private const val ADAPTABILITY_STAB = 2.0
    private const val ATTACK_STAT_FACTOR = 2.0       // Huge Power, Pure Power
    private const val STATUS_ATTACK_FACTOR = 1.5     // Guts
    private const val HUSTLE_ATTACK = 1.5
    private const val HUSTLE_ACCURACY = 0.8
    private const val COMPOUND_EYES = 1.3
    private const val VEIL_ACCURACY = 0.8            // Sand Veil, Snow Cloak: evasion x1.25 = their hit chance x0.8
    private const val TANGLED_FEET_ACCURACY = 0.5    // evasion x2 while confused
    private const val WEATHER_SPEED = 2.0            // Swift Swim, Chlorophyll, Sand Rush
    private const val QUICK_FEET_SPEED = 1.5
    private const val EARLY_BIRD = 2.0
    private const val SKILL_LINK_HITS = 5
    /** Water Absorb, Volt Absorb, Dry Skin heal 1/4 of the max HP; Dry Skin's rain heal and sun loss are 1/8; Ice Body heals 1/16; Rough Skin / Iron Barbs take 1/8; Regenerator heals 1/3. */
    private const val ABSORB_DIVISOR = 4
    private const val DRY_SKIN_WEATHER_DIVISOR = 8
    private const val ICE_BODY_DIVISOR = 16
    private const val ROUGH_SKIN_DIVISOR = 8
    private const val REGENERATOR_DIVISOR = 3
    private const val WEAK_ARMOR_DEFENSE = -1
    private const val WEAK_ARMOR_SPEED = 2
    private const val DEFIANT_RISE = 2

    // ---- keys ----
    /** [BattleSide.notes]: the ability this side has instead of its own (Trace, 9b's Role Play ...); "" = none (Gastro Acid). */
    const val OVERRIDE = "ab.set"
    private const val FLASH_FIRE = "ab.flashfire"
    private const val ROUNDS = "ab.rounds"

    // ---- tables ----
    /** The abilities Mold Breaker ignores (Showdown's `breakable` flag). */
    val BREAKABLE: Set<String> = setOf(
        "armortail", "aromaveil", "aurabreak", "auraguard", "battlearmor", "bigpecks", "bulletproof", "clearbody", "contrary", "damp", "dazzling",
        "disguise", "dryskin", "eartheater", "eelevate", "filter", "flashfire", "flowergift", "flowerveil", "fluffy", "friendguard", "furcoat",
        "goodasgold", "grasspelt", "guarddog", "heatproof", "heavymetal", "hypercutter", "iceface", "icescales", "illuminate", "immunity", "innerfocus",
        "insomnia", "keeneye", "leafguard", "levitate", "lightmetal", "lightningrod", "limber", "magicbounce", "magmaarmor", "marvelscale", "mindseye",
        "mirrorarmor", "motordrive", "mountaineer", "multiscale", "oblivious", "overcoat", "owntempo", "pastelveil", "punkrock", "purifyingsalt",
        "queenlymajesty", "rebound", "sandveil", "sapsipper", "shellarmor", "shielddust", "simple", "snowcloak", "solidrock", "soundproof", "stickyhold",
        "stormdrain", "sturdy", "suctioncups", "sweetveil", "tangledfeet", "telepathy", "terashell", "thermalexchange", "thickfat", "unaware",
        "vitalspirit", "voltabsorb", "waterabsorb", "waterbubble", "waterveil", "wellbakedbody", "whitesmoke", "windrider", "wonderguard", "wonderskin",
    )
    val MOLD_BREAKERS: Set<String> = setOf("moldbreaker", "teravolt", "turboblaze")
    /** Abilities Trace can't copy (Showdown's `notrace` flag). */
    private val NO_TRACE: Set<String> = setOf(
        "asoneglastrier", "asonespectrier", "battlebond", "comatose", "commander", "disguise", "embodyaspectcornerstone", "embodyaspecthearthflame",
        "embodyaspectteal", "embodyaspectwellspring", "flowergift", "forecast", "hungerswitch", "iceface", "illusion", "imposter", "multitype",
        "neutralizinggas", "poisonpuppeteer", "powerconstruct", "powerofalchemy", "protosynthesis", "quarkdrive", "receiver", "rkssystem", "schooling",
        "shieldsdown", "stancechange", "teraformzero", "terashell", "terashift", "trace", "zenmode", "zerotohero",
    )
    /** The targets that mean "a move aimed at the other Pokémon". */
    private val FOE_TARGETS = setOf("normal", "any", "adjacentFoe", "allAdjacentFoes", "allAdjacent", "randomNormal", "scripted")
    /** Damp stops these (Showdown's list). */
    private val DAMP_MOVES = setOf("explosion", "selfdestruct", "mindblown", "mistyexplosion")
    /** Intimidate does not work on these (Gen 8+). */
    private val INTIMIDATE_PROOF = setOf("innerfocus", "owntempo", "oblivious", "scrappy")
    private val SYNC_STATUSES = setOf("brn", "psn", "tox", "par")
    private val STATUS_NAMES = mapOf("brn" to "burn", "par" to "paralysis", "psn" to "poison", "tox" to "poison", "slp" to "sleep", "frz" to "freeze")

    /** The ids of every ability this set builds. */
    val BUILT: Set<String> get() = AbilityText.ids

    /** The display name of an ability id ("keeneye" -> "Keen Eye"); an unknown one gets its id capitalized. */
    fun name(id: String): String = AbilityText.name(id) ?: id.replaceFirstChar { it.uppercase() }

    // ---------------------------------------------------------------- reading abilities

    /** The effective ability of the attacker / of the target of [h] ("" = none; Mold Breaker already counted). */
    internal fun aAb(h: Hit): String = h.view.ability(h.attackerIsMain)
    internal fun dAb(h: Hit): String = abVs(h.view, h.attackerIsMain)

    /** The ability of the side opposite to [attacker] as a move of [attacker] meets it: "" when [attacker] breaks the mold and it is breakable. */
    internal fun abVs(v: BattleView, attacker: Boolean): String {
        val a = v.ability(!attacker)
        return if (a in BREAKABLE && v.ability(attacker) in MOLD_BREAKERS) "" else a
    }

    /** R17C-9b: a move of [attacker] makes contact: it has the flag and its user is not Long Reach (its moves never make contact). */
    fun contacts(v: BattleView, attacker: Boolean, move: Move): Boolean = "contact" in move.flags && v.ability(attacker) != "longreach"

    /** R17C-9b: Unseen Fist - a move of [attacker] that makes contact goes through Protect and the like (SideMoves asks). */
    fun breaksProtection(v: BattleView, attacker: Boolean, move: Move): Boolean = v.ability(attacker) == "unseenfist" && contacts(v, attacker, move)

    /** True when the moves of [attacker] go through Reflect, Light Screen, Aurora Veil, Substitute, Safeguard and Mist (SideMoves asks). */
    fun infiltrates(v: BattleView, attacker: Boolean): Boolean = v.ability(attacker) == "infiltrator"

    /** The share of [h]'s attacker's max HP that Overgrow, Blaze, Torrent and Swarm look at. */
    private fun pinch(h: Hit): Boolean = h.attacker.hp <= h.view.maxHp(h.attackerIsMain) / PINCH_DIVISOR

    /** The move's base power as the damage formula will use it (Technician). */
    private fun power(h: Hit): Int = rulesFirst { it.basePower(h) } ?: if (h.move.power > 0) h.move.power else Tuning.UNKNOWN_POWER

    /** The effectiveness of [h] as the damage formula gets it (Tinted Lens, Solid Rock). */
    internal fun effOf(h: Hit): Double = rulesChain(TypeChart.eff(h.type, h.dTypes)) { r, e -> r.effectiveness(h, e) }

    /** True when [ab] stops [status] (Insomnia, Limber ...) for a Pokémon in [v]. */
    internal fun stopsStatus(v: BattleView, ab: String, status: String): Boolean = when (ab) {
        "insomnia", "vitalspirit" -> status == "slp"
        "limber" -> status == "par"
        "waterveil" -> status == "brn"
        "leafguard" -> v.weather() == "sun"
        else -> false
    }

    // ---------------------------------------------------------------- writing

    /** One line of an ability at work: the whole [text], the ability's name as the float label. */
    internal fun announce(r: Round, p: Boolean, id: String, text: String) = r.fx(p, text, EffectKind.OTHER, label = name(id))

    /** "<Pokémon>'s <Ability><tail>": `say(r, p, "static", "!")` -> "Pikachu's Static!". */
    internal fun say(r: Round, p: Boolean, id: String, tail: String) = announce(r, p, id, "${r.mon(p).name}'s ${name(id)}$tail")

    internal fun roll(r: Round, percent: Int): Boolean = r.rng.nextInt(100) < percent

    // ---------------------------------------------------------------- the ability itself

    /** Mold Breaker (see the class text) and [OVERRIDE]. */
    override fun ability(v: BattleView, p: Boolean, a: String): String {
        val x = v.side(p).notes[OVERRIDE] ?: a
        if (x in BREAKABLE && v is Round && v.actor == !p && breaksMold(v, !p)) return ""
        return x
    }

    /** R17C-9b: the move [q] performs now ignores the target's ability: Mold Breaker and its kin, or Mycelium Might with a status move. */
    private fun breaksMold(v: Round, q: Boolean): Boolean {
        val a = v.ability(q)
        return a in MOLD_BREAKERS || (a == "myceliummight" && v.actingMove?.damaging == false)
    }

    /** Levitate: not on the ground (Gravity, Ingrain and Smack Down still hold it down). */
    override fun grounded(v: BattleView, p: Boolean, g: Boolean): Boolean {
        if (!g || v.ability(p) != "levitate" || FieldRules.gravityOn(v)) return g
        val held = v.side(p).volatiles
        return SideMoves.INGRAIN in held || MoveControl.SMACK_DOWN in held // it floats unless something holds it down
    }

    // ---------------------------------------------------------------- entering and leaving

    /** The abilities that act when their Pokémon enters the fight: Intimidate, Download, Trace, the weather makers, Mold Breaker's line. */
    override fun onEnter(r: Round, p: Boolean) {
        if (r.mon(p).hp > 0) enter(r, p, r.ability(p))
    }

    /** The effect of the ability [ab] starting on [p]: when it enters the fight, and when a move or Trace gives it (R17C-9b: the rarer ones are [AbilitiesMore.enter]). */
    internal fun enter(r: Round, p: Boolean, ab: String) {
        when (ab) {
            "intimidate" -> intimidate(r, p)
            "download" -> download(r, p)
            "trace" -> trace(r, p)
            "drizzle" -> summon(r, p, ab, "rain")
            "drought" -> summon(r, p, ab, "sun")
            "sandstream" -> summon(r, p, ab, "sand")
            "snowwarning" -> summon(r, p, ab, "snow")
            "moldbreaker" -> announce(r, p, ab, "${r.mon(p).name} breaks the mold!")
            "teravolt" -> announce(r, p, ab, "${r.mon(p).name} is radiating a bursting aura!")
            "turboblaze" -> announce(r, p, ab, "${r.mon(p).name} is radiating a blazing aura!")
            else -> AbilitiesMore.enter(r, p, ab)
        }
    }

    private fun intimidate(r: Round, p: Boolean) {
        val tp = !p
        if (r.mon(tp).hp <= 0 || SideMoves.SUBSTITUTE in r.side(tp).volatiles) return
        say(r, p, "intimidate", "!")
        val foe = r.ability(tp)
        if (foe in INTIMIDATE_PROOF) { say(r, tp, foe, ": Intimidate doesn't work on it."); return }
        if (foe == "guarddog") { say(r, tp, foe, "!"); r.boost(tp, mapOf("atk" to 1), byFoe = false); return } // R17C-9b: Guard Dog is pleased
        val before = r.side(tp).boosts[0]
        r.boost(tp, mapOf("atk" to -1), byFoe = true)
        if (foe == "rattled" && r.side(tp).boosts[0] < before && r.mon(tp).hp > 0) { say(r, tp, foe, "!"); r.boost(tp, mapOf("spe" to 1), byFoe = false) }
    }

    /** Download: the foe's lower defence decides: Sp. Atk when its Sp. Def is lower than its Defense, else Attack. */
    private fun download(r: Round, p: Boolean) {
        val tp = !p
        if (r.mon(tp).hp <= 0) return
        val def = r.stats(tp).def * BattleEngine.stageMult(r.side(tp).boosts[1])
        val spd = r.stats(tp).spd * BattleEngine.stageMult(r.side(tp).boosts[3])
        say(r, p, "download", "!")
        r.boost(p, mapOf((if (spd < def) "spa" else "atk") to 1), byFoe = false)
    }

    private fun trace(r: Round, p: Boolean) {
        val foe = r.ability(!p)
        if (foe.isEmpty() || foe in NO_TRACE || r.mon(!p).hp <= 0) return
        r.side(p).notes[OVERRIDE] = foe
        announce(r, p, "trace", "${r.mon(p).name} traced ${r.mon(!p).name}'s ${name(foe)}!")
        enter(r, p, foe)
    }

    private fun summon(r: Round, p: Boolean, id: String, weather: String) {
        if (r.field.weather == weather) return
        say(r, p, id, "!")
        FieldRules.setWeather(r, weather, FieldRules.WEATHER_TURNS, p)
    }

    /** Regenerator: the Main that is switched out heals a third of its max HP (a Pokémon that fainted does not). */
    override fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) {
        if (mon.hp <= 0) return
        val ab = battle.player.notes[OVERRIDE] ?: BattleView.normalizeAbility(mon.ability)
        if (!p || ab != "regenerator") return
        val max = StatCalc.maxHp(data, mon)
        mon.hp = minOf(max, mon.hp + max(1, max / REGENERATOR_DIVISOR))
    }

    // ---------------------------------------------------------------- the turn: speed, priority, sleep

    override fun speedMod(v: BattleView, p: Boolean): Double {
        val weather = v.weather()
        return when (v.ability(p)) {
            "swiftswim" -> if (weather == "rain") WEATHER_SPEED else 1.0
            "chlorophyll" -> if (weather == "sun") WEATHER_SPEED else 1.0
            "sandrush" -> if (weather == "sand") WEATHER_SPEED else 1.0
            // paralysis halves the speed after this factor: take that back, so it does not slow Quick Feet
            "quickfeet" -> when (v.side(p).status) { null -> 1.0; "par" -> QUICK_FEET_SPEED / Tuning.PARA_SPEED; else -> QUICK_FEET_SPEED }
            else -> 1.0
        }
    }

    /** Prankster: status moves go first. */
    override fun priority(v: BattleView, p: Boolean, move: Move): Int = if (!move.damaging && v.ability(p) == "prankster") 1 else 0

    /** Early Bird: the sleep wears off twice as fast. */
    override fun wakeMod(v: BattleView, p: Boolean): Double = if (v.ability(p) == "earlybird") EARLY_BIRD else 1.0

    // ---------------------------------------------------------------- the damage formula

    override fun powerMod(h: Hit): Double {
        val move = h.move
        var x = 1.0
        when (aAb(h)) {
            "overgrow" -> if (h.type == Type.GRASS && pinch(h)) x *= PINCH_BOOST
            "blaze" -> if (h.type == Type.FIRE && pinch(h)) x *= PINCH_BOOST
            "torrent" -> if (h.type == Type.WATER && pinch(h)) x *= PINCH_BOOST
            "swarm" -> if (h.type == Type.BUG && pinch(h)) x *= PINCH_BOOST
            "technician" -> if (move.id != Tuning.STRUGGLE && power(h) <= TECHNICIAN_MAX_POWER) x *= TECHNICIAN_BOOST
            "ironfist" -> if ("punch" in move.flags) x *= IRON_FIST_BOOST
            "strongjaw" -> if ("bite" in move.flags) x *= STRONG_JAW_BOOST
            "megalauncher" -> if ("pulse" in move.flags) x *= MEGA_LAUNCHER_BOOST
            "toughclaws" -> if ("contact" in move.flags) x *= TOUGH_CLAWS_BOOST
            "reckless" -> if (move.recoil != null || move.crashDamage) x *= RECKLESS_BOOST
            "sheerforce" -> if (move.allSecondaries.isNotEmpty()) x *= SHEER_FORCE_BOOST
            "flashfire" -> if (h.type == Type.FIRE && FLASH_FIRE in h.aSide.volatiles) x *= FLASH_FIRE_BOOST
            "rivalry" -> x *= rivalry(h.attacker.gender, h.defender.gender)
        }
        when (dAb(h)) {
            "thickfat" -> if (h.type == Type.FIRE || h.type == Type.ICE) x *= THICK_FAT_FACTOR
            "heatproof" -> if (h.type == Type.FIRE) x *= HEATPROOF_FACTOR
            "dryskin" -> if (h.type == Type.FIRE) x *= DRY_SKIN_FIRE
        }
        return x
    }

    private fun rivalry(a: Char, d: Char): Double = when {
        a == 'N' || d == 'N' -> 1.0
        a == d -> RIVALRY_SAME
        else -> RIVALRY_OTHER
    }

    /** Huge Power, Pure Power, Guts, Hustle: the Attack stat in use (a special move or Body Press is not helped). */
    override fun attackMod(h: Hit): Double {
        if ((h.move.offensiveStat ?: if (h.physical) "atk" else "spa") != "atk" || h.move.offensiveFromTarget) return 1.0
        return when (aAb(h)) {
            "hugepower", "purepower" -> ATTACK_STAT_FACTOR
            "guts" -> if (h.aSide.status != null) STATUS_ATTACK_FACTOR else 1.0
            "hustle" -> HUSTLE_ATTACK
            else -> 1.0
        }
    }

    /** Guts: a burn does not halve the damage. */
    override fun ignoresBurn(h: Hit): Boolean = aAb(h) == "guts"

    /** Adaptability: STAB is 2. */
    override fun stab(h: Hit): Double? = if (aAb(h) == "adaptability") ADAPTABILITY_STAB else null

    /** Solid Rock, Filter (super effective x0.75) and Tinted Lens (not very effective x2). */
    override fun finalMod(h: Hit): Double {
        val ab = dAb(h)
        val tinted = aAb(h) == "tintedlens"
        if (ab != "solidrock" && ab != "filter" && !tinted) return 1.0
        val eff = effOf(h)
        var x = 1.0
        if ((ab == "solidrock" || ab == "filter") && eff > 1.0) x *= SOLID_ROCK_FACTOR
        if (tinted && eff > 0.0 && eff < 1.0) x *= TINTED_LENS_FACTOR
        return x
    }

    /** Scrappy: Normal and Fighting moves hit Ghost types. */
    override fun effectiveness(h: Hit, eff: Double): Double {
        if (eff != 0.0 || aAb(h) != "scrappy" || (h.type != Type.NORMAL && h.type != Type.FIGHTING) || Type.GHOST !in h.dTypes) return eff
        return h.dTypes.filter { it != Type.GHOST }.fold(1.0) { x, t -> x * TypeChart.single(h.type, t) }
    }

    /** Hustle, Compound Eyes, Sand Veil, Snow Cloak, Tangled Feet, No Guard, Skill Link (the later hits of a multi-accuracy move). */
    override fun accuracy(h: Hit, acc: Double?): Double? {
        var x = acc ?: return null
        val att = aAb(h)
        val def = dAb(h)
        if (att == "noguard" || def == "noguard") return null
        if (att == "skilllink" && h.hitIndex > 0 && h.move.multiAccuracy) return null
        when (att) {
            "compoundeyes" -> x *= COMPOUND_EYES
            "hustle" -> if (h.physical) x *= HUSTLE_ACCURACY
        }
        val weather = h.view.weather()
        when (def) {
            "sandveil" -> if (weather == "sand") x *= VEIL_ACCURACY
            "snowcloak" -> if (weather == "hail" || weather == "snow") x *= VEIL_ACCURACY
            "tangledfeet" -> if (CoreRules.CONFUSION in h.dSide.volatiles) x *= TANGLED_FEET_ACCURACY
        }
        return x
    }

    /** Unaware ignores the other side's stages; Keen Eye ignores the target's evasion. */
    override fun ignoresStage(h: Hit, onAttacker: Boolean, key: String): Boolean {
        if (onAttacker) return dAb(h) == "unaware"
        val att = aAb(h)
        return att == "unaware" || (att == "keeneye" && key == "evasion")
    }

    /** Skill Link: 2-5 hit moves hit 5 times. */
    override fun hits(r: Round, p: Boolean, move: Move): Int? =
        if (r.ability(p) == "skilllink" && move.multihit == 2..5) SKILL_LINK_HITS else null

    /** Serene Grace doubles, Sheer Force ends and Shield Dust ends (unless it only changes the user) the extra effects of a move. */
    override fun secondaryChance(h: Hit, sec: Secondary, chance: Int): Int {
        val att = aAb(h)
        if (att == "sheerforce") return 0
        if (dAb(h) == "shielddust" && !(sec.selfBoosts != null && sec.status == null && sec.boosts == null && !sec.flinch && sec.volatileStatus == null)) return 0
        return if (att == "serenegrace") chance * 2 else chance
    }

    // ---------------------------------------------------------------- immunities of the target

    /**
     * The ability of the target that makes [h]'s move useless (a type it absorbs, Levitate, Soundproof, Overcoat, Sturdy against a one-hit KO), or
     * null. Pure: [immune] acts on it, [useful] only asks.
     */
    private fun immunity(h: Hit): String? {
        val ab = dAb(h)
        val move = h.move
        if (ab.isEmpty() || (!move.damaging && (move.target !in FOE_TARGETS || SideMoves.isSelfCurse(h.view, h.attackerIsMain, move)))) return null
        return when (ab) {
            "voltabsorb", "lightningrod", "motordrive" -> ab.takeIf { h.type == Type.ELECTRIC }
            "waterabsorb", "stormdrain", "dryskin" -> ab.takeIf { h.type == Type.WATER }
            "flashfire" -> ab.takeIf { h.type == Type.FIRE }
            "sapsipper" -> ab.takeIf { h.type == Type.GRASS }
            "levitate" -> ab.takeIf { h.type == Type.GROUND && move.damaging && !move.ignoreImmunity && !h.view.grounded(!h.attackerIsMain) }
            "soundproof" -> ab.takeIf { "sound" in move.flags }
            "overcoat" -> ab.takeIf { "powder" in move.flags }
            "sturdy" -> ab.takeIf { move.ohko }
            else -> null
        }
    }

    override fun immune(r: Round, h: Hit): Boolean {
        val ab = immunity(h) ?: return false
        val tp = !h.attackerIsMain
        val n = r.mon(tp).name
        val nm = name(ab)
        when (ab) {
            "voltabsorb", "waterabsorb", "dryskin" -> {
                if (r.mon(tp).hp < r.maxHp(tp) && !SideMoves.healBlocked(r, tp)) r.heal(tp, max(1, r.maxHp(tp) / ABSORB_DIVISOR), "$n's $nm restored its HP!", label = nm)
                else say(r, tp, ab, ": it isn't affected.")
            }
            "lightningrod", "stormdrain" -> absorbAndRaise(r, tp, ab, "spa")
            "motordrive" -> absorbAndRaise(r, tp, ab, "spe")
            "sapsipper" -> absorbAndRaise(r, tp, ab, "atk")
            "flashfire" -> {
                if (FLASH_FIRE in r.side(tp).volatiles) say(r, tp, ab, ": it isn't affected.")
                else { r.side(tp).volatiles[FLASH_FIRE] = 1; say(r, tp, ab, ": its Fire moves are stronger now!") }
            }
            "levitate" -> say(r, tp, ab, ": Ground moves don't affect it.")
            "soundproof" -> say(r, tp, ab, ": sound moves don't affect it.")
            "overcoat" -> say(r, tp, ab, ": powder moves don't affect it.")
            "sturdy" -> say(r, tp, ab, ": one-hit KO moves don't affect it.")
        }
        return true
    }

    internal fun absorbAndRaise(r: Round, tp: Boolean, ab: String, stat: String, stages: Int = 1) {
        say(r, tp, ab, ": it absorbed the move!")
        r.boost(tp, mapOf(stat to stages), byFoe = false)
    }

    /** Damp stops the self-destructing moves; a Prankster status move fails against a Dark target. */
    override fun failReason(r: Round, p: Boolean, move: Move): String? {
        if (move.id in DAMP_MOVES) {
            val holder = listOf(!p, p).firstOrNull { r.ability(it) == "damp" }
            if (holder != null) return "${r.mon(holder).name}'s Damp prevents ${r.mon(p).name} from using ${move.name}!"
        }
        if (!move.damaging && move.target in FOE_TARGETS && !SideMoves.isSelfCurse(r, p, move) && r.ability(p) == "prankster" && Type.DARK in r.types(!p)) return "It doesn't affect ${r.mon(!p).name}…"
        return null
    }

    /** R17C FIX-A: Damp ([DAMP_MOVES]) stops [move] of [p]: a Damp holder is in the fight. The HP cost of Mind Blown is not paid then. */
    fun dampStops(v: BattleView, p: Boolean, move: Move): Boolean = move.id in DAMP_MOVES && (v.ability(!p) == "damp" || v.ability(p) == "damp")

    /** Sturdy: from full HP a hit that would knock the Pokémon out leaves it with 1 HP. */
    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        val m = r.mon(tp)
        if (damage > 0 && damage >= m.hp && m.hp > 1 && m.hp >= r.maxHp(tp) && dAb(h) == "sturdy") {
            say(r, tp, "sturdy", ": it endured the hit!")
            return m.hp - 1
        }
        return damage
    }

    // ---------------------------------------------------------------- damage that is not a hit

    /** Magic Guard takes all of it, Rock Head the recoil and the crash, Heatproof half of a burn. */
    override fun indirectDamage(r: Round, p: Boolean, source: String, amount: Int): Int = when (r.ability(p)) {
        "magicguard" -> 0
        "rockhead" -> if (source == Indirect.RECOIL || source == Indirect.CRASH) 0 else amount
        "heatproof" -> if (source == Indirect.BURN) max(1, amount / 2) else amount
        else -> amount
    }

    /** Liquid Ooze: what the drainer would gain, it loses. */
    override fun drain(r: Round, to: Boolean, amount: Int): Int {
        if (r.ability(!to) != "liquidooze") return amount
        r.hurt(to, amount, "${r.mon(to).name} sucked up the liquid ooze!", EffectKind.RECOIL, label = name("liquidooze"))
        return 0
    }

    // ---------------------------------------------------------------- statuses, conditions, stages

    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? {
        val ab = r.ability(tp)
        if (!stopsStatus(r, ab, status)) return null
        val n = r.mon(tp).name
        return when (ab) {
            "insomnia", "vitalspirit" -> "$n's ${name(ab)} keeps it awake!"
            "limber" -> "$n's Limber prevents paralysis!"
            "waterveil" -> "$n's Water Veil prevents burns!"
            else -> "$n's Leaf Guard prevents status problems in the sun!"
        }
    }

    /** The lasting conditions an ability stops: (ability, condition key) -> what the line says it prevents. */
    private val VOLATILE_STOPPERS: Map<Pair<String, String>, String> = mapOf(
        ("owntempo" to CoreRules.CONFUSION) to "Own Tempo prevents confusion",
        ("innerfocus" to CoreRules.FLINCH) to "Inner Focus prevents flinching",
        ("oblivious" to CoreRules.ATTRACT) to "Oblivious prevents infatuation",
        ("oblivious" to MoveControl.TAUNT) to "Oblivious prevents taunting",
    )

    /** R17C FIX-A: the ability [ab] stops the lasting condition [key] (the Smart choice asks it through [useful]; [blockVolatile] writes the line). */
    internal fun stopsVolatile(ab: String, key: String): Boolean = (ab to key) in VOLATILE_STOPPERS

    override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? {
        val n = r.mon(tp).name
        return VOLATILE_STOPPERS[r.ability(tp) to key]?.let { "$n's $it!" }
    }

    /** Clear Body, White Smoke, Hyper Cutter, Big Pecks, Keen Eye stop the other side's drops; then Simple doubles and Contrary reverses. */
    override fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean): Int {
        val ab = r.ability(tp)
        if (byFoe && delta < 0) {
            val what = when {
                ab == "clearbody" || ab == "whitesmoke" -> "stat loss"
                ab == "hypercutter" && key == "atk" -> "Attack loss"
                ab == "bigpecks" && key == "def" -> "Defense loss"
                ab == "keeneye" && key == "accuracy" -> "accuracy loss"
                else -> null
            }
            if (what != null) { say(r, tp, ab, " prevents $what!"); return 0 }
        }
        return when (ab) {
            "simple" -> delta * 2
            "contrary" -> -delta
            else -> delta
        }
    }

    /** Defiant and Competitive: a drop made by the other side raises Attack / Sp. Atk by 2. */
    override fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) {
        if (!byFoe || delta >= 0 || r.mon(tp).hp <= 0) return
        val ab = r.ability(tp)
        if (ab == "defiant") { say(r, tp, ab, "!"); r.boost(tp, mapOf("atk" to DEFIANT_RISE), byFoe = false) }
        else if (ab == "competitive") { say(r, tp, ab, "!"); r.boost(tp, mapOf("spa" to DEFIANT_RISE), byFoe = false) }
    }

    /** Synchronize: the status comes back to the Pokémon that caused it. */
    override fun afterStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean) {
        if (status !in SYNC_STATUSES || r.ability(tp) != "synchronize") return
        val from = !tp
        if (r.mon(from).hp <= 0 || r.side(from).status != null || !BattleEngine.canHaveStatus(r.types(from), status, Type.NORMAL)) return
        say(r, tp, "synchronize", "!")
        r.setStatus(from, status, Type.NORMAL, byFoe = true, quiet = true)
    }

    // ---------------------------------------------------------------- what a hit sets off

    /** The target's abilities: contact effects, Rough Skin, Weak Armor, Justified, Rattled, Cursed Body. Asked after each hit that cost HP. */
    override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {
        val a = !tp
        val move = h.move
        val contact = contacts(r, a, move) // R17C-9b: Long Reach never touches
        val alive = r.mon(tp).hp > 0 && r.mon(a).hp > 0
        when (val ab = r.ability(tp)) {
            "static" -> if (contact && alive && roll(r, CONTACT_CHANCE)) inflict(r, tp, a, ab, "par")
            "poisonpoint" -> if (contact && alive && roll(r, CONTACT_CHANCE)) inflict(r, tp, a, ab, "psn")
            "flamebody" -> if (contact && alive && roll(r, CONTACT_CHANCE)) inflict(r, tp, a, ab, "brn")
            "effectspore" -> if (contact && alive && Type.GRASS !in r.types(a) && r.ability(a) != "overcoat" && roll(r, CONTACT_CHANCE)) {
                inflict(r, tp, a, ab, listOf("psn", "par", "slp")[r.rng.nextInt(3)])
            }
            "cutecharm" -> if (contact && alive && CoreRules.genderFit(r.mon(true), r.mon(false)) && CoreRules.ATTRACT !in r.side(a).volatiles && roll(r, CONTACT_CHANCE)) {
                say(r, tp, ab, "!")
                CoreRules.infatuate(r, a, byFoe = true, quiet = true)
            }
            "roughskin", "ironbarbs" -> if (contact && r.mon(a).hp > 0) {
                val nm = name(ab)
                r.hurt(a, max(1, r.maxHp(a) / ROUGH_SKIN_DIVISOR), "${r.mon(a).name} was hurt by ${r.mon(tp).name}'s $nm!", EffectKind.RECOIL, label = nm, source = Indirect.ABILITY)
            }
            "weakarmor" -> if (h.physical && r.mon(tp).hp > 0) {
                say(r, tp, ab, "!")
                r.boost(tp, mapOf("def" to WEAK_ARMOR_DEFENSE, "spe" to WEAK_ARMOR_SPEED), byFoe = false)
            }
            "justified" -> if (h.type == Type.DARK && r.mon(tp).hp > 0) { say(r, tp, ab, "!"); r.boost(tp, mapOf("atk" to 1), byFoe = false) }
            "rattled" -> if ((h.type == Type.BUG || h.type == Type.GHOST || h.type == Type.DARK) && r.mon(tp).hp > 0) { say(r, tp, ab, "!"); r.boost(tp, mapOf("spe" to 1), byFoe = false) }
            "cursedbody" -> if (alive && roll(r, CURSED_BODY_CHANCE)) cursedBody(r, tp, a, move)
        }
    }

    /** [holder] gives [to] the status [status] with its ability [id]; nothing happens (and nothing is said) when [to] can't have it. */
    internal fun inflict(r: Round, holder: Boolean, to: Boolean, id: String, status: String) {
        if (r.mon(to).hp <= 0 || r.side(to).status != null || !BattleEngine.canHaveStatus(r.types(to), status, Type.NORMAL)) return
        say(r, holder, id, "!")
        r.setStatus(to, status, Type.NORMAL, byFoe = true, quiet = true)
    }

    /**
     * Cursed Body: the move that hit [holder] is disabled for [a] (MoveControl's Disable: key and note). [a] is in the middle of its action, so the
     * countdown of this very round is one of the turns: one more, as MoveControl does for a target that has moved already.
     */
    private fun cursedBody(r: Round, holder: Boolean, a: Boolean, move: Move) {
        if (move.id == Tuning.STRUGGLE || MoveControl.DISABLE in r.side(a).volatiles) return
        if (!r.addVolatile(a, MoveControl.DISABLE, CURSED_BODY_TURNS + 1, byFoe = true, text = "${r.mon(a).name}'s ${move.name} was disabled by ${r.mon(holder).name}'s Cursed Body!", label = name("cursedbody"))) return
        r.side(a).notes[MoveControl.DISABLE] = move.id
    }

    /** Poison Touch and Stench: the attacker's moves. Asked after a damaging move hit (not after a substitute took it). */
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        if (damage <= 0 || r.mon(tp).hp <= 0 || r.mon(p).hp <= 0) return
        when (val ab = r.ability(p)) {
            "poisontouch" -> if ("contact" in move.flags && abVs(r, p) != "shielddust" && roll(r, CONTACT_CHANCE)) inflict(r, p, tp, ab, "psn")
            "stench" -> if (move.allSecondaries.none { it.flinch || it.volatileStatus == "flinch" } && !r.movedThisRound(tp) && !r.freeHit &&
                CoreRules.FLINCH !in r.side(tp).volatiles && roll(r, STENCH_CHANCE)
            ) {
                say(r, p, ab, "!")
                CoreRules.flinch(r, p, tp)
            }
        }
    }

    /**
     * Steadfast: the flinch really took [p]'s action (CoreRules.beforeMove calls this after its "flinched" line): the Speed rises. R17C FIX-A: before,
     * it fired when the flinch was set, also for a Pokémon that then lost its action to sleep, a faint or a flinch that never came.
     */
    fun flinchStopped(r: Round, p: Boolean) {
        if (r.mon(p).hp <= 0 || r.ability(p) != "steadfast") return
        say(r, p, "steadfast", "!")
        r.boost(p, mapOf("spe" to 1), byFoe = false)
    }

    // ---------------------------------------------------------------- the end of a round

    /** Shed Skin and Hydration cure, Speed Boost, Ice Body and Dry Skin (rain heals, sun hurts). Faster side first. */
    override fun endOfRound(r: Round) {
        val order = if (r.speed(true) > r.speed(false)) listOf(true, false) else listOf(false, true)
        val weather = r.weather()
        for (q in order) {
            val s = r.side(q)
            val m = r.mon(q)
            if (m.hp <= 0) continue
            val ab = r.ability(q)
            when (ab) {
                "shedskin" -> if (s.status != null && roll(r, SHED_SKIN_CHANCE)) cure(r, q, ab)
                "hydration" -> if (s.status != null && weather == "rain") cure(r, q, ab)
                "speedboost" -> {
                    val seen = s.volatiles[ROUNDS] ?: 0
                    s.volatiles[ROUNDS] = seen + 1
                    if (seen > 0 && s.boosts[4] < r.moveChoice.stageLimit(4)) { say(r, q, ab, "!"); r.boost(q, mapOf("spe" to 1), byFoe = false) }
                }
                "icebody" -> if ((weather == "hail" || weather == "snow") && m.hp < r.maxHp(q)) {
                    r.heal(q, max(1, r.maxHp(q) / ICE_BODY_DIVISOR), "${m.name}'s Ice Body restored its HP!", label = name(ab))
                }
                "dryskin" -> when (weather) {
                    "rain" -> if (m.hp < r.maxHp(q)) r.heal(q, max(1, r.maxHp(q) / DRY_SKIN_WEATHER_DIVISOR), "${m.name}'s Dry Skin restored its HP!", label = name(ab))
                    "sun" -> r.hurt(q, max(1, r.maxHp(q) / DRY_SKIN_WEATHER_DIVISOR), "${m.name} was hurt by its Dry Skin!", EffectKind.RESIDUAL, label = name(ab), source = Indirect.WEATHER)
                }
            }
        }
    }

    internal fun cure(r: Round, q: Boolean, id: String) {
        val s = r.side(q)
        val status = s.status ?: return
        s.status = null; s.statusTurns = 0; s.volatiles.remove(CoreRules.SLEEP_TURNS)
        r.fx(q, "${r.mon(q).name}'s ${name(id)} cured its ${STATUS_NAMES[status] ?: status}!", EffectKind.STATUS_CURED, status, label = name(id))
    }

    // ---------------------------------------------------------------- the move choice

    /** A Smart side does not pick a move that will be absorbed or fail because of the target's ability (Volt Absorb vs an Electric move, Limber vs Thunder Wave ...). */
    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        if (v.mon(!p).hp <= 0) return null
        if (immunity(BattleEngine.newHit(v, p, v.mon(p), v.side(p), v.mon(!p), v.side(!p), move)) != null) return false
        if (move.id in DAMP_MOVES && (v.ability(p) == "damp" || abVs(v, p) == "damp")) return false
        if (!move.damaging && move.target in FOE_TARGETS && !SideMoves.isSelfCurse(v, p, move)) {
            if (v.ability(p) == "prankster" && Type.DARK in v.types(!p)) return false
            move.status?.let { if (stopsStatus(v, abVs(v, p), it)) return false }
            // R17C FIX-A: a lasting condition the target's ability stops (Taunt vs Oblivious ...); Yawn's sleep vs Insomnia and Vital Spirit
            move.volatileStatus?.let { key ->
                if (stopsVolatile(abVs(v, p), key) || (key == SideMoves.YAWN && stopsStatus(v, abVs(v, p), "slp"))) return false
            }
        }
        return null
    }
}
