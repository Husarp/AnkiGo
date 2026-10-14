package com.husarp.ankigo.game

// The game's shared data model. Pure Kotlin: no android.* imports anywhere under game/ (unit-tested on the JVM).
// Rules follow Ankimon (docs/ankimon-rules.md); every tunable number lives in Tuning.kt.
// Pokémon data itself is never in the repo: GameData is built at runtime from downloaded files.

enum class Type {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON,
    DARK, STEEL, FAIRY;

    val label get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        fun parse(s: String?): Type? = s?.trim()?.let { t -> entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }
    }
}

enum class Category { PHYSICAL, SPECIAL, STATUS }

enum class Stat { HP, ATK, DEF, SPA, SPD, SPE }

/** Six stats in the order hp, atk, def, spa, spd, spe. */
data class Stats(val hp: Int, val atk: Int, val def: Int, val spa: Int, val spd: Int, val spe: Int) {
    operator fun get(s: Stat): Int = when (s) {
        Stat.HP -> hp; Stat.ATK -> atk; Stat.DEF -> def; Stat.SPA -> spa; Stat.SPD -> spd; Stat.SPE -> spe
    }
    fun toList() = listOf(hp, atk, def, spa, spd, spe)
    val total get() = hp + atk + def + spa + spd + spe
    fun with(s: Stat, v: Int) = when (s) {
        Stat.HP -> copy(hp = v); Stat.ATK -> copy(atk = v); Stat.DEF -> copy(def = v)
        Stat.SPA -> copy(spa = v); Stat.SPD -> copy(spd = v); Stat.SPE -> copy(spe = v)
    }

    companion object {
        val ZERO = Stats(0, 0, 0, 0, 0, 0)
        fun of(l: List<Int>) = Stats(l[0], l[1], l[2], l[3], l[4], l[5])
    }
}

/** The 25 natures: +10% to [plus], −10% to [minus] (neutral when both are null). */
enum class Nature(val plus: Stat?, val minus: Stat?) {
    HARDY(null, null), LONELY(Stat.ATK, Stat.DEF), BRAVE(Stat.ATK, Stat.SPE), ADAMANT(Stat.ATK, Stat.SPA), NAUGHTY(Stat.ATK, Stat.SPD),
    BOLD(Stat.DEF, Stat.ATK), DOCILE(null, null), RELAXED(Stat.DEF, Stat.SPE), IMPISH(Stat.DEF, Stat.SPA), LAX(Stat.DEF, Stat.SPD),
    TIMID(Stat.SPE, Stat.ATK), HASTY(Stat.SPE, Stat.DEF), SERIOUS(null, null), JOLLY(Stat.SPE, Stat.SPA), NAIVE(Stat.SPE, Stat.SPD),
    MODEST(Stat.SPA, Stat.ATK), MILD(Stat.SPA, Stat.DEF), QUIET(Stat.SPA, Stat.SPE), BASHFUL(null, null), RASH(Stat.SPA, Stat.SPD),
    CALM(Stat.SPD, Stat.ATK), GENTLE(Stat.SPD, Stat.DEF), SASSY(Stat.SPD, Stat.SPE), CAREFUL(Stat.SPD, Stat.SPA), QUIRKY(null, null);

    fun mult(s: Stat): Double = when (s) { plus -> 1.1; minus -> 0.9; else -> 1.0 }
    val label get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** How a species evolves into [to]. Built from Showdown's evoType/evoLevel/evoItem/evoMove/evoCondition + PokeAPI min happiness. */
enum class EvoKind {
    LEVEL,       // reach [Evolution.level] (optionally [time]/[gender]/[condition])
    FRIENDSHIP,  // friendship >= minFriendship after a defeat (optionally [time], [moveType] for Sylveon)
    ITEM,        // use [item] from the bag (stones, apples, pots; also Showdown "levelHold" items like Oval Stone)
    TRADE,       // Ankimon turns trades into item use: [item] if the trade needs a held item, else Linking Cord
    LEVEL_MOVE,  // level up while knowing [move]
    OTHER,       // odd rules (Showdown "other" / "levelExtra" except Sylveon): AnkiGo lets Linking Cord do it
}

data class Evolution(
    val to: Int,
    val kind: EvoKind,
    val level: Int? = null,
    val item: String? = null,       // PokeAPI-style item id, e.g. "thunder-stone", "kings-rock", "up-grade"
    val move: String? = null,       // Showdown move id, e.g. "mimic"
    val moveType: Type? = null,
    val time: String? = null,       // "day" | "night" | null
    val gender: Char? = null,       // 'M' | 'F' | null: only a Pokémon of this gender evolves (evolved species is single-gender)
    val minFriendship: Int? = null,
    val condition: String? = null,  // raw Showdown evoCondition text (display / special cases)
)

/** A level-up move. level 0 = learned on evolution (Showdown "L0"); "R" relearn codes count as level 1. */
data class LevelMove(val level: Int, val move: String)

data class Species(
    val num: Int,
    val id: String,                 // Showdown id = Showdown sprite id, e.g. "mrmime", "nidoranf", "tapukoko"
    val name: String,
    val gen: Int,
    val types: List<Type>,
    val base: Stats,
    val maleRatio: Double?,         // chance to be male; null = genderless
    val abilities: List<String>,    // normal abilities only (no hidden ones; Ankimon never gives wild hidden abilities)
    val prevo: Int?,                // pre-evolution, null if none (a regional form where the line is regional: Perrserker.prevo = Meowth-Galar)
    val evolutions: List<Evolution>,
    val baseExp: Int,               // PokeAPI pokemon.base_experience (min 1)
    val growthRate: Int,            // PokeAPI growth_rate_id (1..6), key into GameData.growth
    val evYield: Stats,             // PokeAPI pokemon_stats.effort
    val isBaby: Boolean,
    val isLegendary: Boolean,
    val isMythical: Boolean,
    val levelMoves: List<LevelMove>, // newest generation that has level-up data (Ankimon: gen 9, else 8, 7, ...), sorted by level
    val tmMoves: Set<String>,        // TM-learnable moves ("<gen>M" codes) for the same generation
    val evoLevel: Int?,              // Showdown evoLevel of THIS species (for Ankimon's wild minimum level)
    val evoType: String?,            // Showdown evoType of THIS species ("useItem", "trade", "levelFriendship", ...)
    // Regional forms (Alolan/Galarian/Hisuian/Paldean): num = PokeAPI pokemon id (> 10000), these three tell it from a base species.
    val form: String? = null,        // Showdown "forme" ("Alola", "Paldea-Combat"); null for a base species
    val baseNum: Int = num,          // national Pokédex number (= num for a base species)
    val spriteId: String = id,       // Showdown sprite file name ("raticate-alola"); = id for a base species
)

data class Secondary(
    val chance: Int,                     // percent
    val status: String? = null,          // "par" "brn" "psn" "tox" "slp" "frz"
    val boosts: Map<String, Int>? = null,     // to the target
    val selfBoosts: Map<String, Int>? = null, // to the user
    val flinch: Boolean = false,
)

/** A move from Showdown's moves.json. Boost keys use Showdown names: atk def spa spd spe accuracy evasion. */
data class Move(
    val id: String,
    val name: String,
    val type: Type,
    val category: Category,
    val power: Int,                  // basePower; damaging moves with basePower 0 and no special rule get Tuning.UNKNOWN_POWER
    val accuracy: Int?,              // null = never misses (Showdown accuracy: true)
    val priority: Int = 0,
    val critRatio: Int = 1,
    val target: String = "normal",  // Showdown target; "self" / "adjacentAllyOrSelf" / "allies" mean the user
    val boosts: Map<String, Int>? = null,     // status-move stat changes, to the target (or the user when target is self)
    val selfBoosts: Map<String, Int>? = null, // Showdown "self": {"boosts": ...}
    val status: String? = null,
    val secondary: Secondary? = null,
    val drain: Pair<Int, Int>? = null,   // heal user by damage * a/b
    val recoil: Pair<Int, Int>? = null,  // user takes damage * a/b
    val heal: Pair<Int, Int>? = null,    // heal user by maxHp * a/b
    val multihit: IntRange? = null,
    val fixedDamage: Int? = null,        // Showdown damage: number (Dragon Rage 40, Sonic Boom 20)
    val levelDamage: Boolean = false,    // Showdown damage: "level" (Seismic Toss, Night Shade)
    val halfHp: Boolean = false,         // Super Fang, Nature's Madness, Ruination: half the target's current HP
    val ohko: Boolean = false,
    val selfDestruct: Boolean = false,
    val willCrit: Boolean = false,
) {
    val damaging get() = category != Category.STATUS
}

class GameData(
    val species: Map<Int, Species>,           // base forms 1..1025 + regional forms keyed by PokeAPI pokemon id (> 10000)
    val moves: Map<String, Move>,             // Showdown id -> move (Max/Z/CAP moves left out)
    val growth: Map<Int, IntArray>,           // growth rate id -> total XP to reach level L at index L (1..100; index 0 = 0)
    val itemPrices: Map<String, Int>,         // PokeAPI item id -> official price (0 = never sold)
    val builtAt: Long = 0,
) {
    fun sp(num: Int): Species = species.getValue(num)
    fun move(id: String): Move? = moves[id]
    fun price(item: String): Int = itemPrices[item] ?: 0
}

/** Rarity tiers (Ankimon legacy encounter system). MEGA and GMAX only exist as rolls; AnkiGo has no such forms, so they fall back to NORMAL. */
enum class Tier(val label: String) {
    NORMAL("Normal"), BABY("Baby"), ULTRA("Ultra Beast"), LEGENDARY("Legendary"), MYTHICAL("Mythical"),
    STARTER("Starter"), MEGA("Mega"), GMAX("G-Max"),
}

/** One Pokémon: owned (team/box) or the wild one in a battle. Mutable on purpose; only the main thread changes it. */
class Mon(
    var uid: String,
    var species: Int,
    var name: String,               // species display name, kept in sync on evolution (widgets read it without GameData)
    var level: Int,
    var xp: Int,                    // XP progress inside the current level (Ankimon)
    val ivs: Stats,
    var evs: Stats,
    val nature: Nature,
    var gender: Char,               // 'M' 'F' 'N'
    var shiny: Boolean,
    var ability: String,
    val moves: MutableList<String>, // Showdown move ids, max 4
    var hp: Int,                    // current HP
    var friendship: Int = 0,
    var heldItem: String? = null,
    var caughtAt: Long = 0,
    var defeats: Int = 0,           // wild Pokémon defeated while this one was the main
    var tier: Tier = Tier.NORMAL,   // tier it was met in
    var nickname: String? = null,   // player-given name (Ankimon import); null = none
    var ankimonId: String? = null,  // Ankimon's individual_id when imported from an Ankimon save (re-import matches on it)
)

/** The name to show for an owned Pokémon: its nickname when it has one, else the species name. */
val Mon.shownName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name

/** Per-battle state of one side (resets for every new wild Pokémon and when someone faints). */
class BattleSide(
    val boosts: IntArray = IntArray(7), // atk def spa spd spe accuracy evasion, each -6..+6
    var status: String? = null,         // "par" "brn" "psn" "tox" "slp" "frz"
    var statusTurns: Int = 0,           // sleep turns left / toxic counter
    val charged: MutableSet<String> = mutableSetOf(), // charge moves (ids) already charged in this fight
)

enum class Outcome { CATCH, DEFEAT }

class Battle(
    val wild: Mon,
    var tier: Tier,
    var player: BattleSide = BattleSide(),
    var enemy: BattleSide = BattleSide(),
    var tally: Int = 0,             // answer points collected in the current round (Again 0, Hard 5, Good 10, Easy 20)
    var answers: Int = 0,           // answers in the current round
    var override: Outcome? = null,  // one-off catch/defeat choice for this fight (optional tap)
    var rounds: Int = 0,
    var startedAt: Long = 0,
    var wildMaxHp: Int? = null,     // encounter-only pace override; never carried into a caught Pokémon
) {
    fun maxWildHp(data: GameData): Int = wildMaxHp?.coerceAtLeast(1) ?: StatCalc.maxHp(data, wild)
}

class Settings(
    val gens: MutableSet<Int> = Tuning.DEFAULT_GENS.toMutableSet(),
    var cardsPerRound: Int = Tuning.CARDS_PER_ROUND,
    var autoMode: Int = Tuning.DEFAULT_AUTO_MODE,       // Ankimon automatic_battle: 1 always catch, 2 defeat (catch shiny/protected), 3 catch new species
    var protectLegendary: Boolean = true,
    var protectMythical: Boolean = true,
    var protectUltra: Boolean = true,
    var protectStarter: Boolean = true,
    var protectRegional: Boolean = true,                // Ankimon battle.auto_catch_regional: regional forms are always caught
    val wishlist: MutableSet<Int> = Tuning.DEFAULT_WISHLIST.toMutableSet(),
    var dailyAverage: Int = Tuning.DAILY_AVERAGE,       // Ankimon "daily average": the daily goal, also drives rarity
    var dayStartHour: Int = Tuning.DAY_START_HOUR,
    var xpShareUid: String? = null,                     // Ankimon "classic" Exp. Share holder (null = off)
    var trainerName: String = "Trainer",
    var fasterStart: Boolean = true, // new games only; old saves decode missing key as false
)

/** One game day of study for the Stats screen (AnkiGo's own log; see Stats.kt). */
class DayLog(var reviews: Int = 0, var won: Int = 0, var caught: Int = 0, var shinies: Int = 0)

class GameState(
    var schema: Int = SCHEMA,
    var seq: Long = 0,                   // grows with every save; the loader picks the highest valid one
    var savedAt: Long = 0,
    var createdAt: Long = 0,
    val mons: MutableList<Mon> = mutableListOf(),      // everything caught (team + box)
    val team: MutableList<String> = mutableListOf(),   // uids, max 6; team[0] is the main (the one that fights)
    val bag: MutableMap<String, Int> = mutableMapOf(), // item id -> count
    val tms: MutableSet<String> = mutableSetOf(),      // owned TMs (Showdown move ids); never used up
    var cash: Int = 0,
    var trainerLevel: Int = 1,
    var trainerXp: Int = 0,              // progress inside the trainer level
    val seen: MutableSet<Int> = mutableSetOf(),
    val caught: MutableSet<Int> = mutableSetOf(),
    var battle: Battle? = null,
    var nextSpecies: Int? = null,        // pre-rolled next encounter (widget silhouette, sprite prefetch)
    var nextTier: Tier = Tier.NORMAL,
    var nextShiny: Boolean = false,
    var day: String = "",                // current game day "yyyy-MM-dd" (a day starts at settings.dayStartHour)
    var reviewsToday: Int = 0,
    var cashToday: Int = 0,
    var itemCountdown: Int = 0,          // answers until the next random item (0 = not rolled yet)
    val buttonsToday: IntArray = IntArray(5), // index = button (0 unknown, 1 Again .. 4 Easy)
    var cardStreak: Int = 0,             // correct answers in a row (shown as the combo chip; no battle effect, as Ankimon)
    var bestCardStreak: Int = 0,
    var streakDays: Int = 0,             // days in a row with the daily goal reached, ending at lastGoalDay
    var lastGoalDay: String? = null,
    var bestStreakDays: Int = 0,         // longest goal streak ever
    val history: MutableMap<String, DayLog> = mutableMapOf(), // game day "yyyy-MM-dd" -> that day's log (kept 400 days)
    var totalReviews: Long = 0,
    val badges: MutableSet<Int> = mutableSetOf(),
    var shopDay: String? = null,
    val shopItems: MutableList<String> = mutableListOf(),
    val shopTms: MutableList<String> = mutableListOf(),
    val redo: MutableMap<Long, Int> = mutableMapOf(), // card id -> answers to ignore after an AnkiDroid undo
    var liveDay: String? = null,         // day of liveSnapshot
    var liveSnapshot: String? = null,    // LiveDetector's card map (opaque JSON), saved atomically with the game
    var starterChosen: Boolean = false,
    var catches: Int = 0,
    var defeats: Int = 0,
    var evolutions: Int = 0,
    val settings: Settings = Settings(),
) {
    fun mon(uid: String?): Mon? = mons.firstOrNull { it.uid == uid }
    val main: Mon? get() = mon(team.firstOrNull())
    fun teamMons(): List<Mon> = team.mapNotNull { mon(it) }
    fun boxMons(): List<Mon> = mons.filter { it.uid !in team }
    fun count(item: String) = bag[item] ?: 0

    companion object { const val SCHEMA = 1 }
}

/** One answer seen in AnkiDroid. button: 0 unknown (counts as Good), 1 Again, 2 Hard, 3 Good, 4 Easy. */
data class Answer(val cardId: Long, val button: Int, val at: Long, val catchUp: Boolean = false)
