package com.husarp.ankigo.game

import kotlin.math.max

/**
 * R17C rule set "FieldRules" (R17C-7, PLAN 229): weather, terrain, rooms and sports, the moves that start them, and the weather / terrain
 * power, type and accuracy rules. Mainline Gen 9; a "turn" is one battle ROUND (the round that starts a field effect is its first turn, so a
 * 5-turn weather affects the rest of that round and the next 4). Both sides follow the same rules.
 *
 * STATE (all in [Battle.field], saved with the fight; nothing else is stored):
 * - `field.weather` in "rain" "sun" "sand" "hail" "snow" + `weatherTurns` (rounds left; 0 = until it is replaced);
 * - `field.terrain` in "electric" "grassy" "misty" "psychic" + `terrainTurns`;
 * - `field.rooms` (key -> rounds left): "trickroom" "gravity" "wonderroom" "mudsport" "watersport" "iondeluge" (this round only).
 * Read the weather with [BattleView.weather] (it respects Cloud Nine / Air Lock) and the terrain with [BattleView.terrain].
 *
 * PUBLIC API for other rule sets (abilities, Weather-setting abilities of R17C-9):
 * - [setWeather] / [setTerrain]: start one for N rounds on behalf of a side (the line + the FIELD float are written, the old one is replaced,
 *   false = it was already so, nothing written);
 * - [clearTerrain]: end the terrain with its line (Defog, Steel Roller, Ice Spinner use it);
 * - [gravityOn]: Gravity is up. The `grounded` hook is chained and this set answers BEFORE move control and abilities, so a hook that says "not
 *   grounded" (Magnet Rise, Telekinesis, Levitate, Air Balloon) must answer `g && !gravityOn(v)`.
 * Weather abilities pass [WEATHER_TURNS] (Drizzle, Drought, Sand Stream, Snow Warning); terrain abilities [TERRAIN_TURNS].
 * Sand / hail damage already skips the abilities that stop it (Magic Guard, Overcoat, Sand Veil / Rush / Force, Ice Body, Snow Cloak; read
 * by id from [BattleView.ability]): the abilities set does not need to handle those.
 *
 * END OF A ROUND (this set's [endOfRound], in this order): (1) sandstorm / hail damage, 1/16 of the max HP, faster side first (not in the
 * last round of the weather, as in the games); (2) Grassy Terrain heals 1/16 of the max HP of every grounded side; (3) the countdowns
 * (weather, terrain, rooms), each ending with its line. Ion Deluge is a room of 1 round, so it ends here too.
 *
 * WHERE THE RULES SIT: Terrain Pulse / Weather Ball / Ion Deluge change the type in [moveType]; power changes (terrain boosts, sports, Weather Ball,
 * Solar Beam) are in [powerMod]; the rain / sun damage in [weatherMod]; Rock / Ice defense boosts in [defenseMod]; Thunder & co. and Gravity in [accuracy].
 */
internal object FieldRules : BattleRules {
    // ---- the numbers ----
    /** Rounds a weather, a terrain or a room lasts (the round it starts counts). */
    const val WEATHER_TURNS = 5
    const val TERRAIN_TURNS = 5
    private const val ROOM_TURNS = 5
    /** Ion Deluge lasts for the round it is used in. */
    private const val ION_DELUGE_TURNS = 1
    /** Sandstorm / hail damage and the Grassy Terrain heal: 1/this of the max HP. */
    private const val RESIDUAL_DIVISOR = 16
    /** Rain boosts Water, sun boosts Fire; the opposite element is halved. */
    private const val WEATHER_BOOST = 1.5
    private const val WEATHER_WEAKEN = 0.5
    /** Solar Beam / Solar Blade in rain, sand, hail and snow. */
    private const val SOLAR_WEAKEN = 0.5
    /** Rock types' Sp. Def in a sandstorm, Ice types' Defense in snow. */
    private const val DEFENSE_BOOST = 1.5
    /** The accuracy Thunder and friends have in harsh sunlight. */
    private const val SUN_ACCURACY = 50.0
    /** Growth's rise in harsh sunlight (1 otherwise). */
    private const val GROWTH_IN_SUN = 2
    /** Electric / Grass / Psychic Terrain boost the move type for a grounded user. */
    private const val TERRAIN_BOOST = 1.3
    /** Grassy Terrain weakens Earthquake & co. and Misty Terrain weakens Dragon moves (against a grounded target). */
    private const val TERRAIN_WEAKEN = 0.5
    /** Expanding Force (Psychic Terrain) and Misty Explosion (Misty Terrain). */
    private const val TERRAIN_MOVE_BOOST = 1.5
    /** Terrain Pulse with a terrain, Rising Voltage against a grounded target in Electric Terrain. */
    private const val DOUBLE = 2.0
    /** Mud Sport and Water Sport. */
    private const val SPORT_FACTOR = 1.0 / 3.0
    /** Gravity: every accuracy times 5/3. */
    private const val GRAVITY_ACCURACY = 5.0 / 3.0

    // ---- keys ----
    private const val TRICK_ROOM = "trickroom"
    private const val GRAVITY = "gravity"
    private const val WONDER_ROOM = "wonderroom"
    private const val MUD_SPORT = "mudsport"
    private const val WATER_SPORT = "watersport"
    private const val ION_DELUGE = "iondeluge"
    private const val SUBSTITUTE = "substitute"

    // ---- texts and tables ----

    /** A weather or a terrain: its key, the line and the float label when it starts, the line when it ends. */
    private class Field(val key: String, val start: String, val label: String, val end: String)

    private val WEATHERS: Map<String, Field> = listOf(
        Field("rain", "It started to rain!", "Rain", "The rain stopped."),
        Field("sun", "The sunlight turned harsh!", "Harsh sun", "The sunlight faded."),
        Field("sand", "A sandstorm kicked up!", "Sandstorm", "The sandstorm subsided."),
        Field("hail", "It started to hail!", "Hail", "The hail stopped."),
        Field("snow", "It started to snow!", "Snow", "The snow stopped."),
    ).associateBy { it.key }

    private val TERRAINS: Map<String, Field> = listOf(
        Field("electric", "An electric current runs across the battlefield!", "Electric Terrain", "The electricity disappeared from the battlefield."),
        Field("grassy", "Grass grew to cover the battlefield!", "Grassy Terrain", "The grass disappeared from the battlefield."),
        Field("misty", "Mist swirled about the battlefield!", "Misty Terrain", "The mist disappeared from the battlefield."),
        Field("psychic", "The battlefield got weird!", "Psychic Terrain", "The weirdness disappeared from the battlefield."),
    ).associateBy { it.key }

    /** A room or sport: [start] gets the user's name; [end] null = nothing is said when it ends. */
    private class Room(val key: String, val start: (String) -> String, val label: String, val end: String?)

    private val ROOMS: Map<String, Room> = listOf(
        Room(TRICK_ROOM, { "$it twisted the dimensions!" }, "Trick Room", "The twisted dimensions returned to normal!"),
        Room(GRAVITY, { "Gravity intensified!" }, "Gravity", "Gravity returned to normal!"),
        Room(WONDER_ROOM, { "It created a bizarre area in which the Defense and Sp. Def stats are swapped!" }, "Wonder Room",
            "Wonder Room wore off, and the Defense and Sp. Def stats returned to normal!"),
        Room(MUD_SPORT, { "Electricity's power was weakened!" }, "Mud Sport", "The effects of Mud Sport have faded."),
        Room(WATER_SPORT, { "Fire's power was weakened!" }, "Water Sport", "The effects of Water Sport have faded."),
        Room(ION_DELUGE, { "A deluge of ions showers the battlefield!" }, "Ion Deluge", null),
    ).associateBy { it.key }

    /** Showdown move id -> the weather key it starts (Chilly Reception's switch part does nothing here). */
    private val WEATHER_MOVES = mapOf("raindance" to "rain", "sunnyday" to "sun", "sandstorm" to "sand", "hail" to "hail", "snowscape" to "snow", "chillyreception" to "snow")
    private val TERRAIN_MOVES = mapOf("electricterrain" to "electric", "grassyterrain" to "grassy", "mistyterrain" to "misty", "psychicterrain" to "psychic")
    /** The rooms that are switched off by using the move again; the other rooms fail when they are already up. */
    private val TOGGLE_ROOMS = setOf(TRICK_ROOM, WONDER_ROOM)
    private val ROOM_MOVES = ROOMS.keys

    /** Never misses in rain, 50 % accurate in harsh sunlight. */
    private val RAIN_SURE_MOVES = setOf("thunder", "hurricane", "bleakwindstorm", "wildboltstorm", "sandsearstorm")
    private val SOLAR_MOVES = setOf("solarbeam", "solarblade")
    private val QUAKE_MOVES = setOf("earthquake", "bulldoze", "magnitude")
    /** Moves Gravity stops: the data's `gravity` flag, and these ids for a move without flags. */
    private val GRAVITY_MOVES = setOf("fly", "bounce", "skydrop", "highjumpkick", "jumpkick", "magnetrise", "telekinesis", "splash", "flyingpress")

    /** The data's targets that mean "a move aimed at the foe's Pokémon" (the engine counts every non-self target, also "all", as aimed at the foe). */
    private val FOE_TARGETS = setOf("normal", "any", "adjacentFoe", "allAdjacentFoes", "allAdjacent", "randomNormal", "scripted")

    /** What Defog ends on the target's side, in the order the lines are written: condition key -> (name, how its end reads). */
    private val DEFOG_ENDS: Map<String, Pair<String, String>> = linkedMapOf(
        "reflect" to ("Reflect" to "wore off"), "lightscreen" to ("Light Screen" to "wore off"), "auroraveil" to ("Aurora Veil" to "wore off"),
        "safeguard" to ("Safeguard" to "wore off"), "mist" to ("Mist" to "faded"),
    )
    /** What Court Change swaps between the two sides (the side conditions that exist in AnkiGo). */
    private val COURT_CHANGE_KEYS = listOf("reflect", "lightscreen", "auroraveil", "safeguard", "mist", "tailwind")

    /** Types and abilities that take no sandstorm / hail damage. */
    private val SAND_PROOF_TYPES = setOf(Type.ROCK, Type.GROUND, Type.STEEL)
    private val HAIL_PROOF_TYPES = setOf(Type.ICE)
    private val SAND_PROOF_ABILITIES = setOf("magicguard", "overcoat", "sandveil", "sandrush", "sandforce")
    private val HAIL_PROOF_ABILITIES = setOf("magicguard", "overcoat", "icebody", "snowcloak")

    override val handles: Set<String> = WEATHER_MOVES.keys + TERRAIN_MOVES.keys + ROOM_MOVES + setOf("defog", "courtchange", "tidyup", "growth")

    // ---------------------------------------------------------------- reading the field

    private fun room(v: BattleView, key: String): Boolean = (v.field.rooms[key] ?: 0) > 0

    /** R17C FIX-A: [key] is a weather this set knows (a save may hold anything: SaveCodec drops the rest). */
    fun isWeather(key: String): Boolean = key in WEATHERS

    /** R17C FIX-A: [key] is a terrain this set knows. */
    fun isTerrain(key: String): Boolean = key in TERRAINS

    /** Gravity is up (everyone is on the ground, see the class text). */
    fun gravityOn(v: BattleView): Boolean = room(v, GRAVITY)

    private fun name(v: BattleView, p: Boolean): String = v.mon(p).name

    /** The move's priority with the rules' changes (Grassy Glide ...), as the turn order counts it. */
    private fun effectivePriority(v: BattleView, p: Boolean, move: Move): Int = move.priority + rulesSum { it.priority(v, p, move) }

    private fun gravityBlocks(move: Move): Boolean = "gravity" in move.flags || move.id in GRAVITY_MOVES

    /** [p]'s effective speed (stages, paralysis, speed rules), with or without a round. */
    private fun speedOf(v: BattleView, p: Boolean): Double {
        (v as? Round)?.let { return it.speed(p) }
        val s = v.stats(p).spe * BattleEngine.stageMult(v.side(p).boosts[4]) * rulesProduct { it.speedMod(v, p) }
        return if (v.side(p).status == "par") s * Tuning.PARA_SPEED else s
    }

    /** [p] would act first among moves of the same priority now: the faster one, or the slower one in Trick Room; a tie goes to the wild one. */
    private fun actsFirst(v: BattleView, p: Boolean): Boolean {
        val mainFirst = if (room(v, TRICK_ROOM)) speedOf(v, true) < speedOf(v, false) else speedOf(v, true) > speedOf(v, false)
        return mainFirst == p
    }

    // ---------------------------------------------------------------- starting and ending (public for other sets)

    /**
     * Starts the weather [key] ("rain" "sun" "sand" "hail" "snow") for [turns] rounds ([WEATHER_TURNS] for a move or an ability; 0 = until it is
     * replaced) on behalf of the side [byP] (`true` = the Main): writes the start line and the FIELD float on that side. A weather already up
     * is replaced; the same weather again is not restarted: false, nothing written (a move then says "But it failed!").
     */
    fun setWeather(r: Round, key: String, turns: Int = WEATHER_TURNS, byP: Boolean): Boolean {
        val info = WEATHERS[key] ?: error("not a weather: $key")
        val f = r.field
        if (f.weather == key) return false
        f.weather = key
        f.weatherTurns = max(0, turns)
        r.fx(byP, info.start, EffectKind.FIELD, key, 1, label = info.label)
        return true
    }

    /** Same as [setWeather] for the terrain [key] ("electric" "grassy" "misty" "psychic"), [TERRAIN_TURNS] rounds. */
    fun setTerrain(r: Round, key: String, turns: Int = TERRAIN_TURNS, byP: Boolean): Boolean {
        val info = TERRAINS[key] ?: error("not a terrain: $key")
        val f = r.field
        if (f.terrain == key) return false
        f.terrain = key
        f.terrainTurns = max(0, turns)
        r.fx(byP, info.start, EffectKind.FIELD, key, 1, label = info.label)
        return true
    }

    /** Ends the terrain, if there is one, with its end line (shown on the side [byP]). True = there was one. */
    fun clearTerrain(r: Round, byP: Boolean): Boolean {
        val key = r.field.terrain ?: return false
        r.field.terrain = null
        r.field.terrainTurns = 0
        TERRAINS[key]?.let { r.fx(byP, it.end, EffectKind.FIELD, key, -1) } // R17C FIX-A: an unknown key (a damaged save) just ends
        return true
    }

    private fun startRoom(r: Round, p: Boolean, key: String): Boolean {
        if (room(r, key)) return false
        val info = ROOMS.getValue(key)
        r.field.rooms[key] = if (key == ION_DELUGE) ION_DELUGE_TURNS else ROOM_TURNS
        r.fx(p, info.start(name(r, p)), EffectKind.FIELD, key, 1, label = info.label)
        return true
    }

    private fun endRoom(r: Round, p: Boolean, key: String) {
        r.field.rooms.remove(key)
        ROOMS.getValue(key).end?.let { r.fx(p, it, EffectKind.FIELD, key, -1) }
    }

    // ---------------------------------------------------------------- status moves

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        WEATHER_MOVES[move.id]?.let { return setWeather(r, it, WEATHER_TURNS, p) }
        TERRAIN_MOVES[move.id]?.let { return setTerrain(r, it, TERRAIN_TURNS, p) }
        return when (move.id) {
            in TOGGLE_ROOMS -> if (room(r, move.id)) { endRoom(r, p, move.id); true } else startRoom(r, p, move.id)
            GRAVITY, MUD_SPORT, WATER_SPORT, ION_DELUGE -> startRoom(r, p, move.id)
            "defog" -> defog(r, p)
            "courtchange" -> courtChange(r, p)
            "tidyup" -> tidyUp(r, p)
            "growth" -> r.boost(p, growthStages(sun = r.weather() == "sun"), byFoe = false)
            else -> null
        }
    }

    /** Growth raises Attack and Sp. Atk by 1, by 2 in harsh sunlight. */
    private fun growthStages(sun: Boolean): Map<String, Int> {
        val n = if (sun) GROWTH_IN_SUN else 1
        return mapOf("atk" to n, "spa" to n)
    }

    /** Defog: the target's evasion falls by 1; Reflect, Light Screen, Aurora Veil, Safeguard and Mist on the TARGET's side end (as in the games), and so does the terrain. */
    private fun defog(r: Round, p: Boolean): Boolean {
        val tp = !p
        var worked = r.boost(tp, mapOf("evasion" to -1), byFoe = true)
        val conds = r.conds(tp)
        for ((key, info) in DEFOG_ENDS) {
            if (conds.remove(key) == null) continue
            r.fx(tp, "${name(r, tp)}'s ${info.first} ${info.second}!", EffectKind.FIELD, key, -1)
            worked = true
        }
        if (clearTerrain(r, p)) worked = true
        return worked
    }

    /** Court Change: the side conditions of the two sides trade places (with their rounds left); fails when neither side has one. */
    private fun courtChange(r: Round, p: Boolean): Boolean {
        val mine = r.conds(p)
        val theirs = r.conds(!p)
        var swapped = false
        for (key in COURT_CHANGE_KEYS) {
            val a = mine[key]
            val b = theirs[key]
            if (a == null && b == null) continue
            if (b != null) mine[key] = b else mine.remove(key)
            if (a != null) theirs[key] = a else theirs.remove(key)
            swapped = true
        }
        if (swapped) r.fx(p, "${name(r, p)} swapped the battle effects affecting each side!", EffectKind.FIELD, "courtchange", 1, label = "Court Change")
        return swapped
    }

    /** Tidy Up: every substitute on the field goes, then the user's Attack and Speed rise by 1. */
    private fun tidyUp(r: Round, p: Boolean): Boolean {
        var removed = false
        for (q in listOf(p, !p)) if (r.side(q).volatiles.remove(SUBSTITUTE) != null) removed = true
        if (removed) r.fx(p, "Tidying up complete!", EffectKind.VOLATILE, SUBSTITUTE, -1)
        val raised = r.boost(p, mapOf("atk" to 1, "spe" to 1), byFoe = false)
        return removed || raised
    }

    // ---------------------------------------------------------------- the damage formula

    /** Weather Ball and Terrain Pulse change type; Ion Deluge turns Normal into Electric (after them). */
    override fun moveType(h: Hit, type: Type): Type {
        var t = type
        when (h.move.id) {
            "weatherball" -> t = when (h.view.weather()) {
                "rain" -> Type.WATER; "sun" -> Type.FIRE; "sand" -> Type.ROCK; "hail", "snow" -> Type.ICE; else -> t
            }
            "terrainpulse" -> if (h.view.grounded(h.attackerIsMain)) t = when (h.view.terrain()) {
                "electric" -> Type.ELECTRIC; "grassy" -> Type.GRASS; "misty" -> Type.FAIRY; "psychic" -> Type.PSYCHIC; else -> t
            }
        }
        if (t == Type.NORMAL && room(h.view, ION_DELUGE)) t = Type.ELECTRIC
        return t
    }

    /** Terrain boosts and weakening, the sports, Weather Ball, the terrain moves and Solar Beam in bad weather (all are changes of the move's power). */
    override fun powerMod(h: Hit): Double {
        val v = h.view
        val m = h.move
        val up = h.attackerIsMain
        var x = 1.0
        if (h.type == Type.ELECTRIC && room(v, MUD_SPORT)) x *= SPORT_FACTOR
        if (h.type == Type.FIRE && room(v, WATER_SPORT)) x *= SPORT_FACTOR
        val terrain = v.terrain()
        if (terrain != null) {
            if (v.grounded(up)) {
                val boosted = (terrain == "electric" && h.type == Type.ELECTRIC) || (terrain == "grassy" && h.type == Type.GRASS) ||
                    (terrain == "psychic" && h.type == Type.PSYCHIC)
                if (boosted) x *= TERRAIN_BOOST
                when (m.id) {
                    "terrainpulse" -> x *= DOUBLE
                    "expandingforce" -> if (terrain == "psychic") x *= TERRAIN_MOVE_BOOST
                    "mistyexplosion" -> if (terrain == "misty") x *= TERRAIN_MOVE_BOOST
                }
            }
            if (v.grounded(!up)) {
                if (terrain == "grassy" && m.id in QUAKE_MOVES) x *= TERRAIN_WEAKEN
                if (terrain == "misty" && h.type == Type.DRAGON) x *= TERRAIN_WEAKEN
                if (terrain == "electric" && m.id == "risingvoltage") x *= DOUBLE
            }
        }
        val weather = v.weather()
        if (weather != null) {
            if (m.id == "weatherball") x *= DOUBLE
            if (m.id in SOLAR_MOVES && weather != "sun") x *= SOLAR_WEAKEN
        }
        return x
    }

    /** Rain / sun: Water and Fire (Hydro Steam is boosted in the sun, not weakened). */
    override fun weatherMod(h: Hit): Double = when (h.view.weather()) {
        "rain" -> when (h.type) { Type.WATER -> WEATHER_BOOST; Type.FIRE -> WEATHER_WEAKEN; else -> 1.0 }
        "sun" -> when {
            h.move.id == "hydrosteam" -> WEATHER_BOOST
            h.type == Type.FIRE -> WEATHER_BOOST
            h.type == Type.WATER -> WEATHER_WEAKEN
            else -> 1.0
        }
        else -> 1.0
    }

    /** Sandstorm: Rock types' Sp. Def; snow: Ice types' Defense (the stat the hit really uses, so Psyshock reads Defense). */
    override fun defenseMod(h: Hit): Double {
        val key = h.move.defensiveStat ?: if (h.physical) "def" else "spd"
        return when (h.view.weather()) {
            "sand" -> if (key == "spd" && Type.ROCK in h.dTypes) DEFENSE_BOOST else 1.0
            "snow" -> if (key == "def" && Type.ICE in h.dTypes) DEFENSE_BOOST else 1.0
            else -> 1.0
        }
    }

    /** Thunder & co. in rain / sun, Blizzard in hail / snow, then Gravity. Null stays null (a move that never misses); a move aimed at the whole field never misses. */
    override fun accuracy(h: Hit, acc: Double?): Double? {
        if (h.move.target == "all") return null // weather, terrain, rooms, Court Change ...: nothing to hit
        var a = acc ?: return null
        val weather = h.view.weather()
        val id = h.move.id
        if (id in RAIN_SURE_MOVES) when (weather) { "rain" -> return null; "sun" -> a = SUN_ACCURACY }
        if (id == "blizzard" && (weather == "hail" || weather == "snow")) return null
        if (room(h.view, GRAVITY)) a *= GRAVITY_ACCURACY
        return a
    }

    /** A grounded Flying type (Gravity ...) is hit by Ground moves: its Flying half no longer makes it immune. */
    override fun effectiveness(h: Hit, eff: Double): Double {
        if (eff != 0.0 || h.type != Type.GROUND || Type.FLYING !in h.dTypes || !h.view.grounded(!h.attackerIsMain)) return eff
        return h.dTypes.filter { it != Type.FLYING }.fold(1.0) { x, t -> x * TypeChart.single(Type.GROUND, t) }
    }

    // ---------------------------------------------------------------- the Pokémon and the turn

    /** Grassy Glide goes first in Grassy Terrain (for a grounded user). */
    override fun priority(v: BattleView, p: Boolean, move: Move): Int =
        if (move.id == "grassyglide" && v.terrain() == "grassy" && v.grounded(p)) 1 else 0

    /** Gravity: everyone is on the ground. */
    override fun grounded(v: BattleView, p: Boolean, g: Boolean): Boolean = g || room(v, GRAVITY)

    /** Wonder Room: Defense and Sp. Def trade places. */
    override fun stats(v: BattleView, p: Boolean, s: Stats): Stats = if (room(v, WONDER_ROOM)) s.copy(def = s.spd, spd = s.def) else s

    override fun slowerFirst(v: BattleView): Boolean = room(v, TRICK_ROOM)

    /** Solar Beam / Solar Blade in harsh sunlight and Electro Shot in rain hit at once. */
    override fun skipsCharge(r: Round, p: Boolean, move: Move): Boolean = when {
        move.id in SOLAR_MOVES -> r.weather() == "sun"
        move.id == "electroshot" -> r.weather() == "rain"
        else -> false
    }

    /** Gravity stops Fly, Bounce, High Jump Kick, Splash ... */
    override fun moveBlocked(v: BattleView, p: Boolean, move: Move): String? =
        if (room(v, GRAVITY) && gravityBlocks(move)) "because of gravity" else null

    /** Steel Roller fails without a terrain. */
    override fun failReason(r: Round, p: Boolean, move: Move): String? =
        if (move.id == "steelroller" && r.field.terrain == null) "But it failed!" else null

    /** Psychic Terrain: a move that goes first does not reach a grounded Pokémon. */
    override fun blocks(r: Round, p: Boolean, move: Move): Boolean {
        val tp = !p
        if (r.terrain() != "psychic" || move.target !in FOE_TARGETS || !r.grounded(tp) || effectivePriority(r, p, move) <= 0) return false
        r.fx(tp, "${name(r, tp)} surrounds itself with psychic terrain!", EffectKind.BLOCKED, "psychicterrain", label = "Blocked")
        return true
    }

    /** Harsh sunlight: nobody is frozen. Electric Terrain: no sleep, Misty Terrain: no status at all (grounded Pokémon only). */
    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? {
        val n = name(r, tp)
        if (status == "frz" && r.weather() == "sun") return "$n can't be frozen in the harsh sunlight."
        if (!r.grounded(tp)) return null
        return when (r.terrain()) {
            "electric" -> if (status == "slp") "$n surrounds itself with electrified terrain!" else null
            "misty" -> "$n surrounds itself with a protective mist!"
            else -> null
        }
    }

    /** Misty Terrain: no confusion; Electric Terrain: no Yawn (grounded Pokémon only). */
    override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? {
        if (!r.grounded(tp)) return null
        val n = name(r, tp)
        return when {
            key == CoreRules.CONFUSION && r.terrain() == "misty" -> "$n surrounds itself with a protective mist!"
            key == "yawn" && r.terrain() == "electric" -> "$n surrounds itself with electrified terrain!"
            else -> null
        }
    }

    /** Steel Roller and Ice Spinner end the terrain after the hit; Plasma Fists starts Ion Deluge. */
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        when (move.id) {
            "steelroller", "icespinner" -> if (r.mon(p).hp > 0) clearTerrain(r, p)
            "plasmafists" -> startRoom(r, p, ION_DELUGE)
        }
    }

    // ---------------------------------------------------------------- the end of a round

    override fun endOfRound(r: Round) {
        val f = r.field
        val order = if (r.speed(true) > r.speed(false)) listOf(true, false) else listOf(false, true)

        // (1) sandstorm / hail damage; the last round of the weather has none (it ends in step 3)
        val weather = r.weather()
        if ((weather == "sand" || weather == "hail") && f.weatherTurns != 1) {
            for (q in order) {
                val m = r.mon(q)
                if (m.hp <= 0 || weatherProof(r, q, weather)) continue
                r.hurt(q, max(1, r.maxHp(q) / RESIDUAL_DIVISOR), "${m.name} is buffeted by the ${if (weather == "sand") "sandstorm" else "hail"}!", EffectKind.RESIDUAL)
            }
        }

        // (2) Grassy Terrain heals every grounded Pokémon that is hurt
        if (f.terrain == "grassy") {
            for (q in order) {
                val m = r.mon(q)
                if (m.hp <= 0 || m.hp >= r.maxHp(q) || !r.grounded(q) || MoveControl.healBlocked(r, q)) continue // R17C-8: Heal Block stops it
                r.heal(q, max(1, r.maxHp(q) / RESIDUAL_DIVISOR), "${m.name} is healed by the grassy terrain!")
            }
        }

        // (3) the countdowns
        f.weather?.let { w ->
            if (f.weatherTurns > 0 && --f.weatherTurns == 0) {
                f.weather = null
                WEATHERS[w]?.let { r.fx(true, it.end, EffectKind.FIELD, w, -1) } // R17C FIX-A: an unknown key (a damaged save) just ends
            }
        }
        if (f.terrain != null && f.terrainTurns > 0 && --f.terrainTurns == 0) clearTerrain(r, true)
        for (key in f.rooms.keys.toList()) {
            if (key !in ROOMS) continue // not ours
            val left = f.rooms.getValue(key) - 1
            if (left > 0) f.rooms[key] = left else endRoom(r, true, key)
        }
    }

    /** R17C FIX-A: a free hit is no round: Ion Deluge ("the rest of this turn") ends now, not in the next real round. */
    override fun afterFreeHit(r: Round) {
        r.field.rooms.remove(ION_DELUGE)
    }

    /** [q] takes no sandstorm / hail damage: its types or its ability stop it. */
    private fun weatherProof(r: Round, q: Boolean, weather: String): Boolean {
        val types = r.types(q)
        val ability = r.ability(q)
        return if (weather == "sand") types.any { it in SAND_PROOF_TYPES } || ability in SAND_PROOF_ABILITIES
        else types.any { it in HAIL_PROOF_TYPES } || ability in HAIL_PROOF_ABILITIES
    }

    // ---------------------------------------------------------------- the move choice

    /** The Smart choice: a weather, terrain or room move only while it still changes something; a terrain-bound attack only where it works. */
    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        if (move.damaging) {
            if (move.id == "steelroller" && v.field.terrain == null) return false
            if (v.terrain() == "psychic" && move.target in FOE_TARGETS && v.grounded(!p) && effectivePriority(v, p, move) > 0) return false
            return null
        }
        // R17C FIX-A: a status or a condition the terrain (or the sun, for freezing) stops now is not worth a move
        if (move.target in FOE_TARGETS && !SideMoves.isSelfCurse(v, p, move) && stoppedByTheField(v, !p, move)) return false
        WEATHER_MOVES[move.id]?.let { return v.field.weather != it }
        TERRAIN_MOVES[move.id]?.let { return v.field.terrain != it }
        return when (move.id) {
            TRICK_ROOM -> speedOf(v, p) != speedOf(v, !p) && !actsFirst(v, p) // only when it turns a slower move order around in your favour
            WONDER_ROOM, GRAVITY, MUD_SPORT, WATER_SPORT, ION_DELUGE -> !room(v, move.id)
            "defog" -> {
                val foe = v.conds(!p)
                v.field.terrain != null || DEFOG_ENDS.keys.any { (foe[it] ?: 0) > 0 } || v.side(!p).boosts[6] > -Tuning.SMART_ACC_EVA_LIMIT
            }
            "courtchange" -> {
                // worth it when the other side has a condition you don't, and you have none the other side lacks
                val mine = v.conds(p)
                val theirs = v.conds(!p)
                COURT_CHANGE_KEYS.any { (theirs[it] ?: 0) > 0 && (mine[it] ?: 0) <= 0 } && COURT_CHANGE_KEYS.none { (mine[it] ?: 0) > 0 && (theirs[it] ?: 0) <= 0 }
            }
            "tidyup" -> v.side(p).boosts[0] < 6 || v.side(p).boosts[4] < 6
            else -> null
        }
    }

    /** The same rules as [blockStatus] / [blockVolatile], asked without a round: the field stops what [move] would give [tp]. */
    private fun stoppedByTheField(v: BattleView, tp: Boolean, move: Move): Boolean {
        val status = move.status
        if (status == "frz" && v.weather() == "sun") return true
        if (!v.grounded(tp)) return false
        return when (v.terrain()) {
            "electric" -> status == "slp" || move.volatileStatus == "yawn"
            "misty" -> status != null || move.volatileStatus == CoreRules.CONFUSION
            else -> false
        }
    }

    // ---------------------------------------------------------------- the move text

    override fun describe(m: Move): List<String> {
        val text = when (m.id) {
            "raindance" -> "Makes it rain for $WEATHER_TURNS turns. Water moves deal 1.5 times the damage and Fire moves half."
            "sunnyday" -> "Makes the sun shine harshly for $WEATHER_TURNS turns. Fire moves deal 1.5 times the damage and Water moves half. Nobody can be frozen."
            "sandstorm" -> "Whips up a sandstorm for $WEATHER_TURNS turns. At the end of each turn everyone but Rock, Ground and Steel types loses a sixteenth of its max HP. " +
                "Rock types get 1.5 times their Sp. Def."
            "hail" -> "Makes it hail for $WEATHER_TURNS turns. At the end of each turn everyone but Ice types loses a sixteenth of its max HP."
            "snowscape" -> "Makes it snow for $WEATHER_TURNS turns. Ice types get 1.5 times their Defense."
            "chillyreception" -> "Makes it snow for $WEATHER_TURNS turns, like Snowscape. You do not switch out."
            "electricterrain" -> "Charges the ground for $TERRAIN_TURNS turns. Pokémon on the ground deal 1.3 times the damage with Electric moves and can't fall asleep."
            "grassyterrain" -> "Grows grass for $TERRAIN_TURNS turns. Pokémon on the ground deal 1.3 times the damage with Grass moves and heal a sixteenth of their max HP " +
                "at the end of each turn. Earthquake, Bulldoze and Magnitude deal half damage to them."
            "mistyterrain" -> "Fills the air with mist for $TERRAIN_TURNS turns. Pokémon on the ground can't get a status or become confused, and Dragon moves deal half damage to them."
            "psychicterrain" -> "Twists the ground for $TERRAIN_TURNS turns. Pokémon on the ground deal 1.3 times the damage with Psychic moves and can't be hit by moves that go first."
            TRICK_ROOM -> "For $ROOM_TURNS turns the slower Pokémon moves first. Using it again ends it."
            GRAVITY -> "For $ROOM_TURNS turns every Pokémon is on the ground and every move is two thirds more accurate. Fly, Bounce, Sky Drop, High Jump Kick, " +
                "Jump Kick, Flying Press, Magnet Rise, Telekinesis and Splash can't be used."
            WONDER_ROOM -> "For $ROOM_TURNS turns the Defense and Sp. Def of every Pokémon are swapped. Using it again ends it."
            MUD_SPORT -> "For $ROOM_TURNS turns Electric moves deal a third of their damage."
            WATER_SPORT -> "For $ROOM_TURNS turns Fire moves deal a third of their damage."
            ION_DELUGE -> "For the rest of this turn Normal moves become Electric moves."
            "plasmafists" -> "After it hits, Normal moves become Electric moves for the rest of the turn."
            "defog" -> "Lowers the target's evasiveness by 1. Ends Reflect, Light Screen, Aurora Veil, Safeguard and Mist on the target's side, and the terrain."
            "courtchange" -> "Swaps Reflect, Light Screen, Aurora Veil, Safeguard, Mist and Tailwind between the two sides. Fails if there is nothing to swap."
            "tidyup" -> "Raises your Attack and Speed by 1 and removes all substitutes."
            "growth" -> "In harsh sunlight it raises them by 2 instead."
            "weatherball" -> "Its type depends on the weather (Fire in harsh sunlight, Water in rain, Rock in a sandstorm, Ice in hail or snow) and it has double power in any weather."
            "terrainpulse" -> "If you are on the ground, its type depends on the terrain (Electric, Grass, Fairy or Psychic) and it has double power in any terrain."
            "solarbeam", "solarblade" -> "In harsh sunlight it hits at once. In rain, a sandstorm, hail or snow its power is halved."
            "electroshot" -> "In rain it hits at once."
            in RAIN_SURE_MOVES -> "Never misses in rain, but is only 50 % accurate in harsh sunlight."
            "blizzard" -> "Never misses in hail or snow."
            "hydrosteam" -> "In harsh sunlight it deals 1.5 times the damage instead of half."
            "expandingforce" -> "Deals 1.5 times the damage in Psychic Terrain if you are on the ground."
            "risingvoltage" -> "Deals double damage to a target on the ground in Electric Terrain."
            "mistyexplosion" -> "Deals 1.5 times the damage in Misty Terrain if you are on the ground."
            "grassyglide" -> "Goes first in Grassy Terrain if you are on the ground."
            "steelroller" -> "Fails unless there is a terrain, and ends the terrain."
            "icespinner" -> "Ends the terrain."
            in QUAKE_MOVES -> "Deals half damage to a target on the ground in Grassy Terrain."
            else -> return emptyList()
        }
        return listOf(text)
    }
}
