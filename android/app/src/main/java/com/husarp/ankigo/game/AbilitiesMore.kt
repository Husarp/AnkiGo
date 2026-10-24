package com.husarp.ankigo.game

import kotlin.math.max

/**
 * R17C rule set "More abilities" (R17C-9b, PLAN 229): the rarer abilities that mean something in a 1-vs-1 fight, 115 of them (the rows of [AbilityText]
 * from Aftermath on), next to the 102 common ones of [Abilities]. Same rules and conventions as there (mainline Gen 9, a "turn" is one battle ROUND, both
 * sides follow the same rules, `v.ability(p)` is the effective ability, Mold Breaker ignores the `breakable` ones through [Abilities.dAb]); it is asked
 * after [Abilities], so the helpers of that object ([Abilities.say] [Abilities.dAb] [Abilities.contacts] ...) are used here.
 *
 * WHERE THINGS SIT: an immunity or a move that fails against the target in [immune] (and [useful] for the Smart move choice); a damage factor in
 * [powerMod] / [attackMod] / [defenseMod] / [finalMod] / [moveType] / [accuracy]; stat and status blocks in [stageChange] / [blockStatus] / [blockVolatile];
 * what a hit sets off in [afterDamage] / [afterHit]; the turn in [priority] / [bracket] (the new hook: Stall, Quick Draw) / [speedMod] / [beforeMove] /
 * [forcedMove] / [endOfRound]; the Pokémon's values in [stats] / [types] / [suppressWeather] / [ability]. Entering abilities are [enter] (asked by
 * [Abilities.enter], which also starts an ability that a move gave). The seven ability-changing moves are [AbilityMoves].
 *
 * NEUTRALIZING GAS: the holder's side carries `volatiles["ab.gas"]` from entering; while the holder still has the ability, [ability] makes every other
 * ability "" (except the ones that cannot be suppressed). SLOW START and STAKEOUT count rounds since entering: `volatiles["ab.turns"]`, +1 at the end of
 * each round (a switch's free hit is not one, but it is the incoming Main's first turn), and only while one of them is in the fight.
 *
 * KEYS. [BattleSide.volatiles] (private, prefix "ab."): "ab.turns" "ab.gas" "ab.truant" (it loafs in its next action) "ab.busted" (Disguise is
 * used up) "ab.bounce" (a stat drop is being bounced to this side; Mirror Armor does not bounce it again) "ab.copy" (Opportunist is copying a rise).
 * [BattleSide.notes]: "ab.lock" (Gorilla Tactics: the move it is locked into), "ab.color" (Color Change: the type, "|", the types before it).
 */
internal object AbilitiesMore : BattleRules {
    // ---- the numbers (mainline; each is used by the sentence in AbilityText) ----
    /** Sand Force (Rock / Ground / Steel in a sandstorm), Punk Rock (its sound moves) and Transistor (Electric). */
    const val SAND_FORCE = 1.3
    const val PUNK_ROCK = 1.3
    const val TRANSISTOR = 1.3
    /** Sharpness, Toxic Boost, Flare Boost, Solar Power, Gorilla Tactics, Dragon's Maw, Rocky Payload, Steelworker, Steely Spirit. */
    const val SHARPNESS = 1.5
    const val STATUS_BOOST = 1.5
    const val SOLAR_POWER = 1.5
    const val GORILLA_TACTICS = 1.5
    const val TYPE_BOOST = 1.5
    /** Normalize and the -ate abilities: their moves. */
    const val CONVERTED = 1.2
    /** Orichalcum Pulse (Attack in the sun) and Hadron Engine (Sp. Atk in Electric Terrain). */
    const val ENERGY = 1.33
    /** Protosynthesis and Quark Drive: the highest stat, and Speed if that is the highest. */
    const val ENERGY_STAT = 1.3
    const val ENERGY_SPEED = 1.5
    /** Fairy Aura / Dark Aura, and the same moves with an Aura Break around. */
    const val AURA = 1.33
    const val AURA_BREAK = 0.75
    /** Ruin abilities: the other Pokémon's stat. */
    const val RUIN = 0.75
    const val STAKEOUT = 2.0
    const val ANALYTIC = 1.3
    const val WATER_BUBBLE_ATTACK = 2.0
    const val WATER_BUBBLE_DEFENSE = 0.5
    const val PURIFYING_SALT_GHOST = 0.5
    const val FUR_COAT = 2.0
    const val MARVEL_SCALE = 1.5
    const val FLUFFY_CONTACT = 0.5
    const val FLUFFY_FIRE = 2.0
    /** Ice Scales (special), Multiscale and Shadow Shield (full HP), Punk Rock (sound against it). */
    const val HALVED = 0.5
    const val PRISM_ARMOR = 0.75
    const val SLOW_START_FACTOR = 0.5
    const val SLOW_START_TURNS = 5
    const val SPEED_DOUBLE = 2.0           // Slush Rush, Surge Surfer
    const val VICTORY_STAR = 1.1
    const val WONDER_SKIN_ACCURACY = 50.0
    const val QUICK_DRAW_CHANCE = 30
    const val TOXIC_CHAIN_CHANCE = 30
    const val TRIAGE_PRIORITY = 3
    const val STEAM_ENGINE_STAGES = 6
    const val WATER_COMPACTION_STAGES = 2
    const val WELL_BAKED_STAGES = 2
    const val LIGHT_METAL = 0.5
    const val HEAVY_METAL = 2.0
    /** Heal and damage shares: 1/this of the max HP. */
    const val AFTERMATH_DIVISOR = 4
    const val EARTH_EATER_DIVISOR = 4
    const val RAIN_DISH_DIVISOR = 16
    const val POISON_HEAL_DIVISOR = 8
    const val SOLAR_POWER_DIVISOR = 8
    const val BAD_DREAMS_DIVISOR = 8
    const val DISGUISE_DIVISOR = 8
    private const val MOODY_RISE = 2
    private const val MOODY_DROP = -1

    // ---- keys ----
    private const val TURNS = "ab.turns"
    private const val GAS_ON = "ab.gas"
    private const val TRUANT = "ab.truant"
    private const val BUSTED = "ab.busted"
    private const val BOUNCE = "ab.bounce"
    private const val COPYING = "ab.copy"
    private const val LOCK = "ab.lock"
    private const val COLOR = "ab.color"
    private const val GAS = "neutralizinggas"

    // ---- tables ----
    private val FOE_TARGETS = setOf("normal", "any", "adjacentFoe", "allAdjacentFoes", "allAdjacent", "randomNormal", "scripted")
    private val ALL_STATUSES = setOf("brn", "par", "psn", "tox", "slp", "frz")
    /** The statuses an ability stops (Comatose and Purifying Salt: all of them). */
    private val STATUS_PROOF: Map<String, Set<String>> = mapOf(
        "sweetveil" to setOf("slp"), "magmaarmor" to setOf("frz"), "pastelveil" to setOf("psn", "tox"), "immunity" to setOf("psn", "tox"),
        "waterbubble" to setOf("brn"), "thermalexchange" to setOf("brn"), "purifyingsalt" to ALL_STATUSES, "comatose" to ALL_STATUSES,
    )
    /** Moves whose type no ability changes (Showdown's list for Normalize and the -ate abilities). */
    private val NO_TYPE_CHANGE = setOf("hiddenpower", "judgment", "multiattack", "naturalgift", "revelationdance", "struggle", "technoblast", "terrainpulse", "weatherball")
    private val ATE_TYPES = mapOf("refrigerate" to Type.ICE, "pixilate" to Type.FAIRY, "aerilate" to Type.FLYING, "galvanize" to Type.ELECTRIC)
    private val SAND_FORCE_TYPES = setOf(Type.ROCK, Type.GROUND, Type.STEEL)
    private val WEATHER_BLOCKERS = setOf("cloudnine", "airlock")
    private val RUIN_STAT = mapOf("tabletsofruin" to "atk", "swordofruin" to "def", "vesselofruin" to "spa", "beadsofruin" to "spd")
    private val STAT_ORDER = listOf("atk", "def", "spa", "spd", "spe")
    private val PRIORITY_BLOCKERS = setOf("dazzling", "queenlymajesty", "armortail")
    private val SCREENS = listOf(SideMoves.REFLECT to "Reflect", SideMoves.LIGHT_SCREEN to "Light Screen", SideMoves.AURORA_VEIL to "Aurora Veil")
    /** Aroma Veil keeps these lasting conditions away, with the word the line uses. */
    private val VEIL_KEYS: Map<String, String> = mapOf(
        CoreRules.ATTRACT to "infatuation", MoveControl.DISABLE to "disabling", MoveControl.ENCORE to "encores",
        MoveControl.HEAL_BLOCK to "Heal Block", MoveControl.TAUNT to "taunts", MoveControl.TORMENT to "torment",
    )
    private val TERRAIN_TYPES = mapOf("electric" to Type.ELECTRIC, "grassy" to Type.GRASS, "misty" to Type.FAIRY, "psychic" to Type.PSYCHIC)

    override val handles: Set<String> = AbilityMoves.IDS

    // ---------------------------------------------------------------- reading

    /** Rounds [q]'s Pokémon has finished in the fight (0 = it is new: its first round). The free hit of a switch counts as the incoming Main's first turn ([onEnter]). */
    fun turns(v: BattleView, q: Boolean): Int = v.side(q).volatiles[TURNS] ?: 0

    /**
     * The rounds since entering are only counted (written to the sides) while Slow Start or Stakeout is in the fight on either side, so a fight without
     * them keeps its sides as they were.
     */
    private fun counting(v: BattleView): Boolean = listOf(true, false).any { v.ability(it).let { a -> a == "slowstart" || a == "stakeout" } }

    /** [q]'s Pokémon has just come into the fight: it has not finished a turn yet, or it is the Main taking the free hit of its switch (Stakeout). */
    private fun justIn(v: BattleView, q: Boolean): Boolean = turns(v, q) == 0 || (v is Round && v.freeHit && q)

    /** [q]'s Pokémon has Comatose: it counts as asleep (Dream Eater, Wake-Up Slap, Snore) and as having a status (Hex), but never really sleeps. */
    fun comatose(v: BattleView, q: Boolean): Boolean = v.mon(q).hp > 0 && v.ability(q) == "comatose"

    /** The factor of the weight of [mon] for the weight moves (Light Metal, Heavy Metal); [mon] is one of the two Pokémon of [v]. */
    fun weightFactor(v: BattleView, mon: Mon): Double {
        val p = if (mon === v.mon(true)) true else if (mon === v.mon(false)) false else return 1.0
        return when (v.ability(p)) {
            "lightmetal" -> LIGHT_METAL
            "heavymetal" -> HEAVY_METAL
            else -> 1.0
        }
    }

    /** The status [status] given by [p] (a move or an ability of it) ignores the types of the target: Corrosion poisons Steel and Poison types. */
    fun corrodes(r: Round, p: Boolean, status: String): Boolean = (status == "psn" || status == "tox") && r.ability(p) == "corrosion"

    /** True when the ability [ab] stops [status] (not counting Leaf Guard and the like of [Abilities.stopsStatus]). */
    fun stopsStatus(ab: String, status: String): Boolean = STATUS_PROOF[ab]?.contains(status) == true

    private fun asleep(v: BattleView, q: Boolean): Boolean = v.side(q).status == "slp" || comatose(v, q)

    /** The attacker's move comes last this round (the other Pokémon has moved or does not move): Analytic. Without a round (an estimate) it does not. */
    private fun movesLast(h: Hit): Boolean {
        val r = h.round ?: return false
        val o = !h.attackerIsMain
        return r.movedThisRound(o) || r.plannedMove(o) == null
    }

    private fun slowStart(v: BattleView, q: Boolean): Boolean = v.ability(q) == "slowstart" && turns(v, q) < SLOW_START_TURNS

    private fun aura(v: BattleView, id: String): Boolean = v.ability(true) == id || v.ability(false) == id

    private fun statValue(s: Stats, key: String): Int = when (key) { "atk" -> s.atk; "def" -> s.def; "spa" -> s.spa; "spd" -> s.spd; else -> s.spe }

    // ---------------------------------------------------------------- entering

    /** The abilities that act when their Pokémon enters the fight (or gets the ability from a move); asked through [Abilities.enter]. */
    internal fun enter(r: Round, p: Boolean, ab: String) {
        val n = r.mon(p).name
        when (ab) {
            "screencleaner" -> screenCleaner(r, p)
            "supersweetsyrup" -> once(r, p, ab) { syrup(r, p) }
            "electricsurge" -> terrain(r, p, ab, "electric")
            "psychicsurge" -> terrain(r, p, ab, "psychic")
            "grassysurge" -> terrain(r, p, ab, "grassy")
            "mistysurge" -> terrain(r, p, ab, "misty")
            "hadronengine" -> terrain(r, p, ab, "electric")
            "orichalcumpulse" -> if (r.field.weather != "sun") { Abilities.say(r, p, ab, "!"); FieldRules.setWeather(r, "sun", FieldRules.WEATHER_TURNS, p) }
            "intrepidsword" -> once(r, p, ab) { raise(r, p, ab, "atk") }
            "dauntlessshield" -> once(r, p, ab) { raise(r, p, ab, "def") }
            "windrider" -> if ((r.conds(p)[SideMoves.TAILWIND] ?: 0) > 0) raise(r, p, ab, "atk")
            "tabletsofruin" -> Abilities.announce(r, p, ab, "$n's Tablets of Ruin weakened the Attack of all surrounding Pokémon!")
            "swordofruin" -> Abilities.announce(r, p, ab, "$n's Sword of Ruin weakened the Defense of all surrounding Pokémon!")
            "vesselofruin" -> Abilities.announce(r, p, ab, "$n's Vessel of Ruin weakened the Sp. Atk of all surrounding Pokémon!")
            "beadsofruin" -> Abilities.announce(r, p, ab, "$n's Beads of Ruin weakened the Sp. Def of all surrounding Pokémon!")
            "neutralizinggas" -> { r.side(p).volatiles[GAS_ON] = 1; Abilities.announce(r, p, ab, "Neutralizing gas filled the area!") }
            "cloudnine", "airlock" -> Abilities.announce(r, p, ab, "The effects of the weather disappeared.")
            "slowstart" -> Abilities.announce(r, p, ab, "$n can't get it going!")
            "comatose" -> Abilities.announce(r, p, ab, "$n is drowsing!")
            "fairyaura" -> Abilities.announce(r, p, ab, "$n is radiating a fairy aura!")
            "darkaura" -> Abilities.announce(r, p, ab, "$n is radiating a dark aura!")
            "aurabreak" -> Abilities.announce(r, p, ab, "$n reversed all other Pokémon's auras!")
        }
    }

    /**
     * R17C FIX-A: [block] runs only the first time this Pokémon enters in this fight (Supersweet Syrup, Intrepid Sword, Dauntless Shield: once per
     * battle). The memory is a side condition ([BattleView.conds]) because the Main's own side is replaced when it is switched out and in again.
     */
    private fun once(r: Round, p: Boolean, ab: String, block: () -> Unit) {
        val key = "ab.once.${r.mon(p).uid}.$ab"
        if (r.conds(p).containsKey(key)) return
        r.conds(p)[key] = 1
        block()
    }

    private fun raise(r: Round, p: Boolean, ab: String, stat: String) {
        Abilities.say(r, p, ab, "!")
        r.boost(p, mapOf(stat to 1), byFoe = false)
    }

    private fun terrain(r: Round, p: Boolean, ab: String, key: String) {
        if (r.field.terrain == key) return
        Abilities.say(r, p, ab, "!")
        FieldRules.setTerrain(r, key, FieldRules.TERRAIN_TURNS, p)
    }

    /** Screen Cleaner: Reflect, Light Screen and Aurora Veil end on both sides. */
    private fun screenCleaner(r: Round, p: Boolean) {
        val up = listOf(p, !p).flatMap { q -> SCREENS.filter { (key, _) -> (r.conds(q)[key] ?: 0) > 0 }.map { q to it } }
        if (up.isEmpty()) return
        Abilities.say(r, p, "screencleaner", "!")
        for ((q, screen) in up) {
            r.conds(q).remove(screen.first)
            r.fx(q, "${r.mon(q).name}'s ${screen.second} wore off!", EffectKind.FIELD, screen.first, -1)
        }
    }

    /** Supersweet Syrup: the other Pokémon's evasiveness falls by 1 (a substitute stops it). */
    private fun syrup(r: Round, p: Boolean) {
        val tp = !p
        if (r.mon(tp).hp <= 0) return
        Abilities.say(r, p, "supersweetsyrup", "!")
        if (SideMoves.SUBSTITUTE in r.side(tp).volatiles) r.fx(tp, "It doesn't affect ${r.mon(tp).name}…")
        else r.boost(tp, mapOf("evasion" to -1), byFoe = true)
    }

    /** The free hit of a switch is the incoming Main's first turn: Slow Start counts it, Stakeout doubles on it, and the next round is its second. */
    override fun onEnter(r: Round, p: Boolean) {
        if (r.freeHit && p && counting(r)) r.side(p).volatiles[TURNS] = 1
    }

    // ---------------------------------------------------------------- the ability itself, the weather

    /** Neutralizing Gas: while its holder has it, no other ability works (the ones that cannot be suppressed and the gas itself do). */
    override fun ability(v: BattleView, p: Boolean, a: String): String {
        if (a.isEmpty() || a == GAS || a in AbilityMoves.CANT_SUPPRESS) return a
        val o = v.side(!p)
        // read only the note and the Pokémon's own ability, never the effective one (that would ask this hook again)
        return if (GAS_ON in o.volatiles && (o.notes[Abilities.OVERRIDE] ?: v.baseAbility(!p)) == GAS) "" else a
    }

    /** Cloud Nine and Air Lock. */
    override fun suppressWeather(v: BattleView): Boolean = v.ability(true) in WEATHER_BLOCKERS || v.ability(false) in WEATHER_BLOCKERS

    // ---------------------------------------------------------------- immunities of the target

    /**
     * The ability of the target that makes [h]'s move fail or do nothing (a type or a kind of move it takes in, Good as Gold, Wonder Guard, a move that
     * goes first against Dazzling), or null. Pure: [immune] acts on it, [useful] only asks.
     */
    private fun immunity(h: Hit): String? {
        val ab = Abilities.dAb(h)
        val move = h.move
        if (ab.isEmpty() || (!move.damaging && (move.target !in FOE_TARGETS || SideMoves.isSelfCurse(h.view, h.attackerIsMain, move)))) return null // R17C FIX-A: a non-Ghost Curse is aimed at its user
        return when (ab) {
            "bulletproof" -> ab.takeIf { "bullet" in move.flags }
            "windrider" -> ab.takeIf { "wind" in move.flags }
            "eartheater" -> ab.takeIf { h.type == Type.GROUND }
            "wellbakedbody" -> ab.takeIf { h.type == Type.FIRE }
            "goodasgold" -> ab.takeIf { !move.damaging }
            "wonderguard" -> ab.takeIf { move.damaging && move.id != Tuning.STRUGGLE && Abilities.effOf(h) <= 1.0 }
            in PRIORITY_BLOCKERS -> ab.takeIf { move.target in FOE_TARGETS && move.priority + rulesSum { it.priority(h.view, h.attackerIsMain, move) } > 0 }
            else -> null
        }
    }

    override fun immune(r: Round, h: Hit): Boolean {
        val ab = immunity(h) ?: return false
        val tp = !h.attackerIsMain
        val nm = Abilities.name(ab)
        when (ab) {
            "windrider" -> Abilities.absorbAndRaise(r, tp, ab, "atk")
            "wellbakedbody" -> Abilities.absorbAndRaise(r, tp, ab, "def", WELL_BAKED_STAGES)
            "eartheater" ->
                if (r.mon(tp).hp < r.maxHp(tp) && !SideMoves.healBlocked(r, tp)) r.heal(tp, max(1, r.maxHp(tp) / EARTH_EATER_DIVISOR), "${r.mon(tp).name}'s $nm restored its HP!", label = nm)
                else Abilities.say(r, tp, ab, ": it isn't affected.")
            "bulletproof" -> Abilities.say(r, tp, ab, ": bullet moves don't affect it.")
            "goodasgold" -> Abilities.say(r, tp, ab, ": status moves don't affect it.")
            "wonderguard" -> Abilities.say(r, tp, ab, ": only super effective moves hit it.")
            else -> Abilities.say(r, tp, ab, ": moves that go first can't be used against it.")
        }
        return true
    }

    // ---------------------------------------------------------------- the damage formula

    override fun powerMod(h: Hit): Double {
        val move = h.move
        val v = h.view
        var x = 1.0
        when (val ab = Abilities.aAb(h)) {
            "sandforce" -> if (v.weather() == "sand" && h.type in SAND_FORCE_TYPES) x *= SAND_FORCE
            "sharpness" -> if ("slicing" in move.flags) x *= SHARPNESS
            "toxicboost" -> if (h.physical && (h.aSide.status == "psn" || h.aSide.status == "tox")) x *= STATUS_BOOST
            "flareboost" -> if (h.category == Category.SPECIAL && h.aSide.status == "brn") x *= STATUS_BOOST
            "punkrock" -> if ("sound" in move.flags) x *= PUNK_ROCK
            "waterbubble" -> if (h.type == Type.WATER) x *= WATER_BUBBLE_ATTACK
            "steelyspirit" -> if (h.type == Type.STEEL) x *= TYPE_BOOST
            "analytic" -> if (movesLast(h)) x *= ANALYTIC
            "normalize" -> if (move.id !in NO_TYPE_CHANGE && h.type == Type.NORMAL) x *= CONVERTED
            else -> ATE_TYPES[ab]?.let { to -> if (move.id !in NO_TYPE_CHANGE && move.type == Type.NORMAL && h.type == to) x *= CONVERTED }
        }
        when (Abilities.dAb(h)) {
            "purifyingsalt" -> if (h.type == Type.GHOST) x *= PURIFYING_SALT_GHOST
            "waterbubble" -> if (h.type == Type.FIRE) x *= WATER_BUBBLE_DEFENSE
        }
        val fairy = h.type == Type.FAIRY && aura(v, "fairyaura")
        val dark = h.type == Type.DARK && aura(v, "darkaura")
        if (fairy || dark) x *= if (aura(v, "aurabreak")) AURA_BREAK else AURA
        return x
    }

    /** The attack stat in use: Solar Power, Defeatist, Gorilla Tactics, the type boosts, Stakeout, the energy abilities, Slow Start - and the other side's Ruin abilities. */
    override fun attackMod(h: Hit): Double {
        val v = h.view
        val key = h.move.offensiveStat ?: if (h.physical) "atk" else "spa"
        val raw = key == "atk" || key == "spa"
        var x = 1.0
        if (!h.move.offensiveFromTarget) {
            val p = h.attackerIsMain
            when (Abilities.aAb(h)) {
                "solarpower" -> if (key == "spa" && v.weather() == "sun") x *= SOLAR_POWER
                "defeatist" -> if (raw && h.attacker.hp * 2 <= v.maxHp(p)) x *= HALVED
                "gorillatactics" -> if (key == "atk") x *= GORILLA_TACTICS
                "transistor" -> if (raw && h.type == Type.ELECTRIC) x *= TRANSISTOR
                "dragonsmaw" -> if (raw && h.type == Type.DRAGON) x *= TYPE_BOOST
                "rockypayload" -> if (raw && h.type == Type.ROCK) x *= TYPE_BOOST
                "steelworker" -> if (raw && h.type == Type.STEEL) x *= TYPE_BOOST
                "stakeout" -> if (raw && justIn(v, !p)) x *= STAKEOUT
                "orichalcumpulse" -> if (key == "atk" && v.weather() == "sun") x *= ENERGY
                "hadronengine" -> if (key == "spa" && v.terrain() == "electric") x *= ENERGY
                "slowstart" -> if (key == "atk" && slowStart(v, p)) x *= SLOW_START_FACTOR
            }
        }
        // the stat's owner is the target for Foul Play; the Ruin ability is on the other side
        val owner = if (h.move.offensiveFromTarget) !h.attackerIsMain else h.attackerIsMain
        val ruin = RUIN_STAT[v.ability(!owner)]
        if (ruin != null && ruin == key) x *= RUIN
        return x
    }

    /** The defense stat in use: Fur Coat, Marvel Scale of the target, and the Ruin abilities of the attacker. */
    override fun defenseMod(h: Hit): Double {
        val key = h.move.defensiveStat ?: if (h.physical) "def" else "spd"
        var x = 1.0
        when (Abilities.dAb(h)) {
            "furcoat" -> if (key == "def") x *= FUR_COAT
            "marvelscale" -> if (key == "def" && h.dSide.status != null) x *= MARVEL_SCALE
        }
        val ruin = RUIN_STAT[h.view.ability(h.attackerIsMain)]
        if (ruin != null && ruin == key) x *= RUIN
        return x
    }

    /** The target's damage cutters: Fluffy, Ice Scales, Multiscale, Shadow Shield, Prism Armor, Punk Rock. */
    override fun finalMod(h: Hit): Double {
        val move = h.move
        val ab = Abilities.dAb(h)
        var x = 1.0
        when (ab) {
            "fluffy" -> {
                if (Abilities.contacts(h.view, h.attackerIsMain, move)) x *= FLUFFY_CONTACT
                if (h.type == Type.FIRE) x *= FLUFFY_FIRE
            }
            "icescales" -> if (h.category == Category.SPECIAL) x *= HALVED
            "multiscale", "shadowshield" -> if (h.defender.hp >= h.view.maxHp(!h.attackerIsMain)) x *= HALVED
            "prismarmor" -> if (Abilities.effOf(h) > 1.0) x *= PRISM_ARMOR
            "punkrock" -> if ("sound" in move.flags) x *= HALVED
        }
        return x
    }

    /** Normalize makes its damaging moves Normal; the -ate abilities make its Normal moves Ice, Fairy, Flying or Electric (the boost is in [powerMod]). */
    override fun moveType(h: Hit, type: Type): Type {
        if (!h.move.damaging || h.move.id in NO_TYPE_CHANGE) return type
        val ab = h.view.ability(h.attackerIsMain)
        if (ab == "normalize") return Type.NORMAL
        val to = ATE_TYPES[ab] ?: return type
        return if (type == Type.NORMAL) to else type
    }

    /** Victory Star (x1.1) and Wonder Skin (status moves against it: 50 %). */
    override fun accuracy(h: Hit, acc: Double?): Double? {
        var x = acc ?: return null
        if (Abilities.aAb(h) == "victorystar") x *= VICTORY_STAR
        if (!h.move.damaging && Abilities.dAb(h) == "wonderskin") x = WONDER_SKIN_ACCURACY
        return x
    }

    /** Illuminate ignores the target's evasion like Keen Eye. */
    override fun ignoresStage(h: Hit, onAttacker: Boolean, key: String): Boolean = !onAttacker && key == "evasion" && Abilities.aAb(h) == "illuminate"

    // ---------------------------------------------------------------- stats, statuses and conditions

    /** What the target's ability stops when the other side causes it: Full Metal Body and Flower Veil (stat drops), Illuminate (accuracy), Mirror Armor (bounces them back). */
    override fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean): Int {
        if (!byFoe || delta >= 0) return delta
        val ab = r.ability(tp)
        when {
            ab == "fullmetalbody" -> { Abilities.say(r, tp, ab, " prevents stat loss!"); return 0 }
            ab == "illuminate" && key == "accuracy" -> { Abilities.say(r, tp, ab, " prevents accuracy loss!"); return 0 }
            ab == "flowerveil" && Type.GRASS in r.types(tp) -> { Abilities.say(r, tp, ab, " prevents stat loss!"); return 0 }
            ab == "mirrorarmor" -> {
                val i = BattleEngine.BOOST_KEYS.indexOf(key)
                if (i < 0 || BOUNCE in r.side(tp).volatiles || r.mon(!tp).hp <= 0 || r.side(tp).boosts[i] <= -r.moveChoice.stageLimit(i)) return delta
                Abilities.say(r, tp, ab, "!")
                val other = r.side(!tp).volatiles
                other[BOUNCE] = 1 // the bounced drop is not bounced again
                try { r.boost(!tp, mapOf(key to delta), byFoe = true) } finally { other.remove(BOUNCE) }
                return 0
            }
        }
        return delta
    }

    /** Opportunist: a stat of the other Pokémon rose, so does its own (not when the rise was a copy). */
    override fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) {
        val o = !tp
        if (delta <= 0 || r.mon(o).hp <= 0 || r.ability(o) != "opportunist" || COPYING in r.side(tp).volatiles || COPYING in r.side(o).volatiles) return
        val mine = r.side(o).volatiles
        mine[COPYING] = 1
        try {
            Abilities.say(r, o, "opportunist", "!")
            r.boost(o, mapOf(key to delta), byFoe = false)
        } finally { mine.remove(COPYING) }
    }

    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? {
        val ab = r.ability(tp)
        val n = r.mon(tp).name
        if (stopsStatus(ab, status)) return when (ab) {
            "sweetveil" -> "$n's Sweet Veil keeps it awake!"
            "magmaarmor" -> "$n's Magma Armor prevents freezing!"
            "pastelveil", "immunity" -> "$n's ${Abilities.name(ab)} prevents poisoning!"
            "waterbubble", "thermalexchange" -> "$n's ${Abilities.name(ab)} prevents burns!"
            else -> "$n's ${Abilities.name(ab)} protects it from status problems!"
        }
        if (ab == "flowerveil" && byFoe && Type.GRASS in r.types(tp)) return "$n's Flower Veil protects it from status problems!"
        return null
    }

    override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? {
        val ab = r.ability(tp)
        val n = r.mon(tp).name
        if (key == SideMoves.YAWN) {
            if (ab == "sweetveil") return "$n's Sweet Veil keeps it awake!"
            if (ab == "purifyingsalt") return "$n's Purifying Salt protects it!"
            if (ab == "flowerveil" && byFoe && Type.GRASS in r.types(tp)) return "$n's Flower Veil protects it!"
        }
        if (ab == "aromaveil" && byFoe) VEIL_KEYS[key]?.let { return "$n's Aroma Veil prevents $it!" }
        return null
    }

    /** Poison Puppeteer: a Pokémon its move poisons also becomes confused. */
    override fun afterStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean) {
        val holder = !tp
        if ((status != "psn" && status != "tox") || !byFoe || r.actor != holder || r.ability(holder) != "poisonpuppeteer") return
        if (r.mon(tp).hp <= 0 || CoreRules.CONFUSION in r.side(tp).volatiles) return
        Abilities.say(r, holder, "poisonpuppeteer", "!")
        CoreRules.confuse(r, tp, byFoe = true, quiet = true)
    }

    // ---------------------------------------------------------------- what a hit sets off

    /** The target's abilities that answer a hit; asked after each hit that cost HP (the target may have fainted). */
    override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {
        val a = !tp
        val move = h.move
        val ab = r.ability(tp)
        val n = r.mon(tp).name
        val alive = r.mon(tp).hp > 0
        val attackerAlive = r.mon(a).hp > 0
        val contact = Abilities.contacts(r, a, move)
        when (ab) {
            "aftermath" -> if (!alive && attackerAlive && contact) aftermath(r, tp, a)
            "innardsout" -> if (!alive && attackerAlive) {
                r.hurt(a, damage, "${r.mon(a).name} was hurt by $n's Innards Out!", EffectKind.RECOIL, label = Abilities.name(ab), source = Indirect.ABILITY)
            }
            "stamina" -> if (alive) { Abilities.say(r, tp, ab, "!"); r.boost(tp, mapOf("def" to 1), byFoe = false) }
            "steamengine" -> if (alive && (h.type == Type.FIRE || h.type == Type.WATER)) { Abilities.say(r, tp, ab, "!"); r.boost(tp, mapOf("spe" to STEAM_ENGINE_STAGES), byFoe = false) }
            "thermalexchange" -> if (alive && h.type == Type.FIRE) { Abilities.say(r, tp, ab, "!"); r.boost(tp, mapOf("atk" to 1), byFoe = false) }
            "watercompaction" -> if (alive && h.type == Type.WATER) { Abilities.say(r, tp, ab, "!"); r.boost(tp, mapOf("def" to WATER_COMPACTION_STAGES), byFoe = false) }
            "windpower" -> if (alive && "wind" in move.flags) charged(r, tp, ab, move)
            "electromorphosis" -> if (alive) charged(r, tp, ab, move)
            "colorchange" -> if (alive && h.type !in r.types(tp)) colorChange(r, tp, h.type)
            "mummy", "lingeringaroma" -> if (alive && attackerAlive && contact) mummy(r, tp, a, ab)
            "wanderingspirit" -> if (alive && attackerAlive && contact) wander(r, tp, a)
            "gooey", "tanglinghair" -> if (alive && attackerAlive && contact) { Abilities.say(r, tp, ab, "!"); r.boost(a, mapOf("spe" to -1), byFoe = true) }
            "cottondown" -> if (alive && attackerAlive) { Abilities.say(r, tp, ab, "!"); r.boost(a, mapOf("spe" to -1), byFoe = true) }
            "sandspit" -> if (alive && r.field.weather != "sand") { Abilities.say(r, tp, ab, "!"); FieldRules.setWeather(r, "sand", FieldRules.WEATHER_TURNS, tp) }
            "seedsower" -> if (alive && r.field.terrain != "grassy") { Abilities.say(r, tp, ab, "!"); FieldRules.setTerrain(r, "grassy", FieldRules.TERRAIN_TURNS, tp) }
            "berserk" -> if (crossedHalf(r, tp, damage)) { Abilities.say(r, tp, ab, "!"); r.boost(tp, mapOf("spa" to 1), byFoe = false) }
            "angershell" -> if (crossedHalf(r, tp, damage)) {
                Abilities.say(r, tp, ab, "!")
                r.boost(tp, mapOf("atk" to 1, "spa" to 1, "spe" to 1, "def" to -1, "spd" to -1), byFoe = false)
            }
        }
    }

    /** The hit took the target from above half of its max HP to half or less, and it is still up (Berserk, Anger Shell). */
    private fun crossedHalf(r: Round, tp: Boolean, damage: Int): Boolean {
        val hp = r.mon(tp).hp
        val max = r.maxHp(tp)
        return hp > 0 && hp * 2 <= max && (hp + damage) * 2 > max
    }

    /** Aftermath: the attacker that knocked the holder out with a move that makes contact loses a quarter of its max HP (a Damp attacker is spared). */
    private fun aftermath(r: Round, tp: Boolean, a: Boolean) {
        if (r.ability(a) == "damp") return
        r.hurt(a, max(1, r.maxHp(a) / AFTERMATH_DIVISOR), "${r.mon(a).name} was hurt by ${r.mon(tp).name}'s Aftermath!", EffectKind.RECOIL, label = Abilities.name("aftermath"), source = Indirect.ABILITY)
    }

    /** Wind Power and Electromorphosis: the next Electric move of the holder is doubled (the Charge of [MoveControl]). */
    private fun charged(r: Round, tp: Boolean, ab: String, move: Move) {
        r.addVolatile(tp, MoveControl.CHARGE, 1, byFoe = false, text = "Being hit by ${move.name} charged ${r.mon(tp).name} with power!", label = Abilities.name(ab))
    }

    /** Color Change: the holder takes the type of the move that hit it. Remembers the types before, so a later type change of a move (Soak ...) is not undone. */
    private fun colorChange(r: Round, tp: Boolean, type: Type) {
        r.side(tp).notes[COLOR] = type.name + "|" + typesBeforeMe(r, tp).joinToString(",") { it.name }
        r.fx(tp, "${r.mon(tp).name}'s Color Change made it the ${type.label} type!", EffectKind.OTHER, label = Abilities.name("colorchange"))
    }

    /** [q]'s types as the rule sets before this one leave them (the species', or what a move set). */
    private fun typesBeforeMe(v: BattleView, q: Boolean): List<Type> {
        var t = v.baseTypes(q)
        for (rule in BattleRules.ALL) {
            if (rule === this) break
            t = rule.types(v, q, t)
        }
        return t
    }

    /** Mummy and Lingering Aroma: the attacker that made contact gets the holder's ability (not an ability that can't be suppressed, or the same one). */
    private fun mummy(r: Round, tp: Boolean, a: Boolean, ab: String) {
        val old = AbilityMoves.current(r, a)
        if (old in AbilityMoves.CANT_SUPPRESS || old == ab) return
        AbilityMoves.set(r, a, ab, "${r.mon(a).name}'s Ability became ${Abilities.name(ab)}!")
    }

    /** Wandering Spirit: the attacker that made contact and the holder swap abilities (unless one of them can't be swapped). */
    private fun wander(r: Round, tp: Boolean, a: Boolean) {
        val theirs = AbilityMoves.current(r, a)
        if (theirs in AbilityMoves.FAIL_SKILLSWAP || theirs == "wanderingspirit") return
        AbilityMoves.swap(r, tp, a, "${r.mon(tp).name} swapped Abilities with ${r.mon(a).name}!")
    }

    /** Toxic Chain: its damaging moves badly poison the target 30 % of the time (Shield Dust stops it). */
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        val tp = !p
        if (damage <= 0 || r.mon(tp).hp <= 0 || r.mon(p).hp <= 0 || r.ability(p) != "toxicchain") return
        if (Abilities.abVs(r, p) != "shielddust" && Abilities.roll(r, TOXIC_CHAIN_CHANCE)) Abilities.inflict(r, p, tp, "toxicchain", "tox")
    }

    /** Disguise: the first hit does nothing, then the Pokémon loses an eighth of its max HP; Magic Guard keeps that. */
    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        if (damage <= 0 || Abilities.dAb(h) != "disguise" || BUSTED in r.side(tp).volatiles) return damage
        r.side(tp).volatiles[BUSTED] = 1
        Abilities.say(r, tp, "disguise", ": its disguise served it as a decoy!")
        r.hurt(tp, max(1, r.maxHp(tp) / DISGUISE_DIVISOR), "${r.mon(tp).name}'s disguise was busted!", EffectKind.RESIDUAL, label = Abilities.name("disguise"), source = Indirect.ABILITY)
        return 0
    }

    /** Poison Heal: the poison does not hurt (the heal is in [endOfRound]). */
    override fun indirectDamage(r: Round, p: Boolean, source: String, amount: Int): Int =
        if (source == Indirect.POISON && r.ability(p) == "poisonheal") 0 else amount

    // ---------------------------------------------------------------- the turn

    /** Triage: heal moves go first; Gale Wings: Flying moves at full HP. */
    override fun priority(v: BattleView, p: Boolean, move: Move): Int = when (v.ability(p)) {
        "triage" -> if ("heal" in move.flags) TRIAGE_PRIORITY else 0
        "galewings" -> if (move.type == Type.FLYING && v.mon(p).hp >= v.maxHp(p)) 1 else 0
        else -> 0
    }

    /** Stall and Mycelium Might's status moves are last of their priority; Quick Draw's damaging moves are first 30 % of the time. */
    override fun bracket(v: BattleView, p: Boolean, move: Move): Int = when (v.ability(p)) {
        "stall" -> -1
        "myceliummight" -> if (move.damaging) 0 else -1
        "quickdraw" -> if (move.damaging && v is Round && Abilities.roll(v, QUICK_DRAW_CHANCE)) {
            Abilities.say(v, p, "quickdraw", ": it can act faster than normal!")
            1
        } else 0
        else -> 0
    }

    override fun speedMod(v: BattleView, p: Boolean): Double = when (v.ability(p)) {
        "slushrush" -> if (v.weather() == "hail" || v.weather() == "snow") SPEED_DOUBLE else 1.0
        "surgesurfer" -> if (v.terrain() == "electric") SPEED_DOUBLE else 1.0
        "slowstart" -> if (slowStart(v, p)) SLOW_START_FACTOR else 1.0
        else -> 1.0
    }

    /** Protosynthesis (sun) and Quark Drive (Electric Terrain): the highest of Attack, Defense, Sp. Atk, Sp. Def and Speed is higher (the first of them on a tie). */
    override fun stats(v: BattleView, p: Boolean, s: Stats): Stats {
        val ab = v.ability(p)
        val on = (ab == "protosynthesis" && v.weather() == "sun") || (ab == "quarkdrive" && v.terrain() == "electric")
        if (!on) return s
        val best = STAT_ORDER.maxByOrNull { statValue(s, it) } ?: return s
        val factor = if (best == "spe") ENERGY_SPEED else ENERGY_STAT
        val raised = (statValue(s, best) * factor).toInt()
        return when (best) {
            "atk" -> s.copy(atk = raised)
            "def" -> s.copy(def = raised)
            "spa" -> s.copy(spa = raised)
            "spd" -> s.copy(spd = raised)
            else -> s.copy(spe = raised)
        }
    }

    /** Mimicry: the type of the terrain; Color Change: the type of the last move that hit it (while nothing else changed its types). */
    override fun types(v: BattleView, p: Boolean, t: List<Type>): List<Type> {
        v.side(p).notes[COLOR]?.split('|')?.let { parts ->
            val type = Type.parse(parts[0])
            if (type != null && parts.getOrNull(1) == t.joinToString(",") { it.name }) return listOf(type)
        }
        if (v.ability(p) == "mimicry") v.terrain()?.let { key -> TERRAIN_TYPES[key] }?.let { return listOf(it) }
        return t
    }

    /** Truant: it loafs in every second action. */
    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        if (r.ability(p) != "truant") return true
        val s = r.side(p).volatiles
        if (s.remove(TRUANT) != null) {
            r.fx(p, "${r.mon(p).name} is loafing around!", EffectKind.CANT_MOVE, "truant")
            return false
        }
        s[TRUANT] = 1
        return true
    }

    /** Gorilla Tactics: it uses the first move it used again and again (Struggle when that move is blocked). */
    override fun forcedMove(v: BattleView, p: Boolean): Move? {
        if (v.ability(p) != "gorillatactics") return null
        val id = v.side(p).notes[LOCK] ?: return null
        val m = v.moves(p).firstOrNull { it.id == id } ?: return null
        return if (rulesFirst { it.moveBlocked(v, p, m) } == null) m else BattleEngine.struggle(v.data)
    }

    /** Gorilla Tactics locks onto its first move; Wind Rider rises when its own Tailwind starts. */
    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        if (r.mon(p).hp <= 0) return
        when (r.ability(p)) {
            "gorillatactics" -> if (move.id != Tuning.STRUGGLE) r.side(p).notes.putIfAbsent(LOCK, move.id)
            "windrider" -> if (move.id == "tailwind" && !failed) raise(r, p, "windrider", "atk")
        }
    }

    // ---------------------------------------------------------------- the end of a round

    /** Rain Dish, Solar Power, Poison Heal, Bad Dreams, Moody, the end of Slow Start; then every side has finished one more round. */
    override fun endOfRound(r: Round) {
        val order = if (r.speed(true) > r.speed(false)) listOf(true, false) else listOf(false, true)
        val weather = r.weather()
        for (q in order) {
            val m = r.mon(q)
            if (m.hp <= 0) continue
            val ab = r.ability(q)
            val hurt = m.hp < r.maxHp(q) && !SideMoves.healBlocked(r, q)
            when (ab) {
                "raindish" -> if (weather == "rain" && hurt) r.heal(q, max(1, r.maxHp(q) / RAIN_DISH_DIVISOR), "${m.name}'s Rain Dish restored its HP!", label = Abilities.name(ab))
                "solarpower" -> if (weather == "sun") {
                    r.hurt(q, max(1, r.maxHp(q) / SOLAR_POWER_DIVISOR), "${m.name} was hurt by its Solar Power!", EffectKind.RESIDUAL, label = Abilities.name(ab), source = Indirect.WEATHER)
                }
                "poisonheal" -> if ((r.side(q).status == "psn" || r.side(q).status == "tox") && hurt) {
                    r.heal(q, max(1, r.maxHp(q) / POISON_HEAL_DIVISOR), "${m.name}'s Poison Heal restored its HP!", label = Abilities.name(ab))
                }
                "baddreams" -> if (r.mon(!q).hp > 0 && asleep(r, !q)) {
                    r.hurt(!q, max(1, r.maxHp(!q) / BAD_DREAMS_DIVISOR), "${r.mon(!q).name} is tormented by ${m.name}'s Bad Dreams!", EffectKind.RESIDUAL, label = Abilities.name(ab), source = Indirect.ABILITY)
                }
                "moody" -> moody(r, q)
            }
        }
        if (!counting(r)) return
        for (q in listOf(true, false)) {
            val s = r.side(q).volatiles
            val done = (s[TURNS] ?: 0) + 1
            s[TURNS] = done
            if (done == SLOW_START_TURNS && r.mon(q).hp > 0 && r.ability(q) == "slowstart") {
                Abilities.announce(r, q, "slowstart", "${r.mon(q).name} finally got its act together!")
            }
        }
    }

    /** Moody: one stat rises by 2 and another falls by 1 (Attack ... Speed, not the ones that are at their limit). */
    private fun moody(r: Round, q: Boolean) {
        val b = r.side(q).boosts
        fun at(key: String) = b[BattleEngine.BOOST_KEYS.indexOf(key)]
        val up = STAT_ORDER.filter { at(it) < 6 }.randomOrNull(r.rng)
        val down = STAT_ORDER.filter { it != up && at(it) > -6 }.randomOrNull(r.rng)
        if (up == null && down == null) return
        Abilities.say(r, q, "moody", "!")
        up?.let { r.boost(q, mapOf(it to MOODY_RISE), byFoe = false) }
        down?.let { r.boost(q, mapOf(it to MOODY_DROP), byFoe = false) }
    }

    // ---------------------------------------------------------------- the ability-changing moves

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? = if (move.id in AbilityMoves.IDS) AbilityMoves.use(r, p, move) else null

    // ---------------------------------------------------------------- the move choice and the move texts

    /**
     * A Smart side does not pick a move that will fail against the target's ability (Bulletproof, Good as Gold, Wonder Guard, Dazzling ...), a status
     * move that the target's ability stops (Magma Armor and Will-O-Wisp ...), nor an ability-changing move that would change nothing; a poisoning move
     * is worth it against a Steel or Poison type for a Corrosion user.
     */
    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        if (move.id in AbilityMoves.IDS) return AbilityMoves.works(v, p, move.id)
        if (v.mon(!p).hp <= 0) return null
        if (immunity(BattleEngine.newHit(v, p, v.mon(p), v.side(p), v.mon(!p), v.side(!p), move)) != null) return false
        if (!move.damaging && move.target in FOE_TARGETS) {
            // R17C FIX-A: Aroma Veil stops its conditions (Taunt, Torment ...), and a Yawn that Sweet Veil / Comatose / Purifying Salt would stop
            move.volatileStatus?.let { key ->
                val ab = Abilities.abVs(v, p)
                if ((ab == "aromaveil" && key in VEIL_KEYS) || (key == SideMoves.YAWN && stopsStatus(ab, "slp"))) return false
            }
            val status = move.status ?: return null
            if (stopsStatus(Abilities.abVs(v, p), status)) return false
            if ((status == "psn" || status == "tox") && v.ability(p) == "corrosion") return v.side(!p).status == null
        }
        return null
    }

    override fun describe(m: Move): List<String> = listOfNotNull(AbilityMoves.TEXT[m.id])
}
