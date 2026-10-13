package com.husarp.ankigo.game

import kotlin.random.Random

/**
 * Shared test fixtures: made-up species and moves (no real Pokémon data in the repo).
 * Species numbers matter for rule lists (wishlist 25/133, starters, ultra beasts...), so fixtures can pick any num.
 * Moves use real-looking Showdown ids so engine rules keyed by id (struggle, splash, tackle) work.
 */
object TestData {
    fun stats(hp: Int, atk: Int, def: Int, spa: Int, spd: Int, spe: Int) = Stats(hp, atk, def, spa, spd, spe)

    fun species(
        num: Int,
        name: String = "Testmon$num",
        types: List<Type> = listOf(Type.NORMAL),
        base: Stats = stats(50, 50, 50, 50, 50, 50),
        gen: Int = genOf(num),
        levelMoves: List<LevelMove> = listOf(LevelMove(1, "tackle"), LevelMove(5, "growl"), LevelMove(9, "bodyslam")),
        tmMoves: Set<String> = setOf("bodyslam"),
        evolutions: List<Evolution> = emptyList(),
        prevo: Int? = null,
        evoLevel: Int? = null,
        evoType: String? = null,
        baseExp: Int = 64,
        growthRate: Int = 2,
        evYield: Stats = Stats.ZERO.copy(hp = 1),
        maleRatio: Double? = 0.5,
        isBaby: Boolean = false,
        isLegendary: Boolean = false,
        isMythical: Boolean = false,
    ) = Species(
        num = num, id = name.lowercase(), name = name, gen = gen, types = types, base = base, maleRatio = maleRatio,
        abilities = listOf("Testability"), prevo = prevo, evolutions = evolutions, baseExp = baseExp,
        growthRate = growthRate, evYield = evYield, isBaby = isBaby, isLegendary = isLegendary, isMythical = isMythical,
        levelMoves = levelMoves, tmMoves = tmMoves, evoLevel = evoLevel, evoType = evoType,
    )

    fun genOf(num: Int) = when {
        num <= 151 -> 1; num <= 251 -> 2; num <= 386 -> 3; num <= 493 -> 4; num <= 649 -> 5
        num <= 721 -> 6; num <= 809 -> 7; num <= 905 -> 8; else -> 9
    }

    val MOVES: Map<String, Move> = listOf(
        Move("tackle", "Tackle", Type.NORMAL, Category.PHYSICAL, 40, 100),
        Move("bodyslam", "Body Slam", Type.NORMAL, Category.PHYSICAL, 85, 100, secondary = Secondary(30, status = "par")),
        Move("growl", "Growl", Type.NORMAL, Category.STATUS, 0, 100, target = "allAdjacentFoes", boosts = mapOf("atk" to -1)),
        Move("swordsdance", "Swords Dance", Type.NORMAL, Category.STATUS, 0, null, target = "self", boosts = mapOf("atk" to 2)),
        Move("thunderbolt", "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 90, 100, secondary = Secondary(10, status = "par")),
        Move("thunderwave", "Thunder Wave", Type.ELECTRIC, Category.STATUS, 0, 90, status = "par"),
        Move("watergun", "Water Gun", Type.WATER, Category.SPECIAL, 40, 100),
        Move("ember", "Ember", Type.FIRE, Category.SPECIAL, 40, 100, secondary = Secondary(10, status = "brn")),
        Move("vinewhip", "Vine Whip", Type.GRASS, Category.PHYSICAL, 45, 100),
        Move("earthquake", "Earthquake", Type.GROUND, Category.PHYSICAL, 100, 100, target = "allAdjacent"),
        Move("shadowball", "Shadow Ball", Type.GHOST, Category.SPECIAL, 80, 100),
        Move("lick", "Lick", Type.GHOST, Category.PHYSICAL, 30, 100),
        Move("quickattack", "Quick Attack", Type.NORMAL, Category.PHYSICAL, 40, 100, priority = 1),
        Move("slash", "Slash", Type.NORMAL, Category.PHYSICAL, 70, 100, critRatio = 2),
        Move("absorb", "Absorb", Type.GRASS, Category.SPECIAL, 20, 100, drain = 1 to 2),
        Move("doubleedge", "Double-Edge", Type.NORMAL, Category.PHYSICAL, 120, 100, recoil = 33 to 100),
        Move("recover", "Recover", Type.NORMAL, Category.STATUS, 0, null, target = "self", heal = 1 to 2),
        Move("bulletseed", "Bullet Seed", Type.GRASS, Category.PHYSICAL, 25, 100, multihit = 2..5),
        Move("seismictoss", "Seismic Toss", Type.FIGHTING, Category.PHYSICAL, 0, 100, levelDamage = true),
        Move("dragonrage", "Dragon Rage", Type.DRAGON, Category.SPECIAL, 0, 100, fixedDamage = 40),
        Move("superfang", "Super Fang", Type.NORMAL, Category.PHYSICAL, 0, 90, halfHp = true),
        Move("fissure", "Fissure", Type.GROUND, Category.PHYSICAL, 0, 30, ohko = true),
        Move("splash", "Splash", Type.NORMAL, Category.STATUS, 0, null, target = "self"),
        Move("teleport", "Teleport", Type.PSYCHIC, Category.STATUS, 0, null, target = "self"),
        Move("struggle", "Struggle", Type.NORMAL, Category.PHYSICAL, 50, null),
        Move("mimic", "Mimic", Type.NORMAL, Category.STATUS, 0, null),
        Move("moonblast", "Moonblast", Type.FAIRY, Category.SPECIAL, 95, 100),
        Move("hypnosis", "Hypnosis", Type.PSYCHIC, Category.STATUS, 0, 60, status = "slp"),
        Move("toxic", "Toxic", Type.POISON, Category.STATUS, 0, 90, status = "tox"),
    ).associateBy { it.id }

    /** Medium growth (n^3) for rate 2, fast (0.8 n^3) for rate 3, slow (1.25 n^3) for rate 1. */
    val GROWTH: Map<Int, IntArray> = mapOf(
        1 to IntArray(101) { if (it <= 1) 0 else (1.25 * it * it * it).toInt() },
        2 to IntArray(101) { if (it <= 1) 0 else it * it * it },
        3 to IntArray(101) { if (it <= 1) 0 else (0.8 * it * it * it).toInt() },
    )

    val PRICES = mapOf(
        "poke-ball" to 200, "great-ball" to 600, "ultra-ball" to 800, "master-ball" to 0, "net-ball" to 1000,
        "potion" to 200, "super-potion" to 700, "max-potion" to 2500, "thunder-stone" to 3000, "moon-stone" to 3000,
        "linking-cord" to 8000, "lucky-egg" to 10000, "everstone" to 3000, "amulet-coin" to 10000, "helix-fossil" to 7000,
        "poke-doll" to 300, "kings-rock" to 5000, "rare-candy" to 10000,
    )

    fun data(species: List<Species>, moves: Map<String, Move> = MOVES) =
        GameData(species.associateBy { it.num }, moves, GROWTH, PRICES)

    /** A small world: a basic line 1->2->3 (level 16/32), an electric line 172(baby)->25->26(stone),
     *  common 16, 19, a ghost 92, a water 54, an eevee-like 133 with two stone evolutions, a legendary 150, a mythical 151. */
    fun world(): GameData = data(
        listOf(
            species(1, "Leafling", listOf(Type.GRASS, Type.POISON), stats(45, 49, 49, 65, 65, 45),
                levelMoves = listOf(LevelMove(1, "tackle"), LevelMove(3, "growl"), LevelMove(7, "vinewhip"), LevelMove(13, "absorb")),
                evolutions = listOf(Evolution(2, EvoKind.LEVEL, level = 16))),
            species(2, "Leafbud", listOf(Type.GRASS, Type.POISON), stats(60, 62, 63, 80, 80, 60), prevo = 1, evoLevel = 16,
                levelMoves = listOf(LevelMove(0, "bulletseed"), LevelMove(1, "tackle"), LevelMove(20, "doubleedge")),
                evolutions = listOf(Evolution(3, EvoKind.LEVEL, level = 32))),
            species(3, "Leafking", listOf(Type.GRASS, Type.POISON), stats(80, 82, 83, 100, 100, 80), prevo = 2, evoLevel = 32),
            species(16, "Birdie", listOf(Type.NORMAL, Type.FLYING), stats(40, 45, 40, 35, 35, 56), baseExp = 50,
                levelMoves = listOf(LevelMove(1, "tackle"), LevelMove(5, "quickattack"))),
            species(19, "Ratty", listOf(Type.NORMAL), stats(30, 56, 35, 25, 35, 72), baseExp = 51,
                levelMoves = listOf(LevelMove(1, "tackle"), LevelMove(4, "quickattack"), LevelMove(10, "superfang"))),
            species(172, "Sparklet", listOf(Type.ELECTRIC), stats(20, 40, 15, 35, 35, 60), isBaby = true,
                levelMoves = listOf(LevelMove(1, "thunderwave"), LevelMove(1, "tackle")),
                evolutions = listOf(Evolution(25, EvoKind.FRIENDSHIP, minFriendship = 160))),
            species(25, "Sparkmouse", listOf(Type.ELECTRIC), stats(35, 55, 40, 50, 50, 90), prevo = 172, evoType = "levelFriendship",
                levelMoves = listOf(LevelMove(1, "tackle"), LevelMove(1, "thunderwave"), LevelMove(26, "thunderbolt")),
                evolutions = listOf(Evolution(26, EvoKind.ITEM, item = "thunder-stone"))),
            species(26, "Sparkrat", listOf(Type.ELECTRIC), stats(60, 90, 55, 90, 80, 110), prevo = 25, evoType = "useItem"),
            species(54, "Duckling", listOf(Type.WATER), stats(50, 52, 48, 65, 50, 55),
                levelMoves = listOf(LevelMove(1, "watergun"), LevelMove(1, "tackle"))),
            species(92, "Spooky", listOf(Type.GHOST, Type.POISON), stats(30, 35, 30, 100, 35, 80),
                levelMoves = listOf(LevelMove(1, "lick"), LevelMove(1, "hypnosis"), LevelMove(12, "shadowball"))),
            species(133, "Fluffy", listOf(Type.NORMAL), stats(55, 55, 50, 45, 65, 55),
                levelMoves = listOf(LevelMove(1, "tackle"), LevelMove(1, "growl")),
                evolutions = listOf(Evolution(134, EvoKind.ITEM, item = "water-stone"), Evolution(135, EvoKind.ITEM, item = "thunder-stone"),
                    Evolution(700, EvoKind.FRIENDSHIP, moveType = Type.FAIRY, minFriendship = 160))),
            species(134, "Fluffwave", listOf(Type.WATER), prevo = 133, evoType = "useItem"),
            species(135, "Fluffbolt", listOf(Type.ELECTRIC), prevo = 133, evoType = "useItem"),
            species(700, "Fluffcharm", listOf(Type.FAIRY), prevo = 133, evoType = "levelExtra"),
            species(129, "Flopfish", listOf(Type.WATER), stats(20, 10, 55, 15, 20, 80),
                levelMoves = listOf(LevelMove(1, "splash"), LevelMove(15, "tackle")),
                evolutions = listOf(Evolution(130, EvoKind.LEVEL, level = 20))),
            species(130, "Flopdragon", listOf(Type.WATER, Type.FLYING), stats(95, 125, 79, 60, 100, 81), prevo = 129, evoLevel = 20),
            species(63, "Blinky", listOf(Type.PSYCHIC), stats(25, 20, 15, 105, 55, 90), levelMoves = listOf(LevelMove(1, "teleport")),
                evolutions = listOf(Evolution(64, EvoKind.LEVEL, level = 16))),
            species(64, "Blinkier", listOf(Type.PSYCHIC), prevo = 63, evoLevel = 16, evolutions = listOf(Evolution(65, EvoKind.TRADE))),
            species(65, "Blinkiest", listOf(Type.PSYCHIC), prevo = 64, evoType = "trade"),
            species(150, "Mindlord", listOf(Type.PSYCHIC), stats(106, 110, 90, 154, 90, 130), isLegendary = true, baseExp = 340, growthRate = 1),
            species(151, "Tinymyth", listOf(Type.PSYCHIC), stats(100, 100, 100, 100, 100, 100), isMythical = true, baseExp = 300, growthRate = 1),
        ),
    )

    /** A Mon with fixed IVs 15, neutral nature, no EVs, full HP. */
    fun mon(data: GameData, species: Int, level: Int, moves: List<String> = listOf("tackle"), uid: String = "m$species-$level"): Mon {
        val sp = data.sp(species)
        val m = Mon(
            uid = uid, species = species, name = sp.name, level = level, xp = 0,
            ivs = Stats(15, 15, 15, 15, 15, 15), evs = Stats.ZERO, nature = Nature.HARDY, gender = 'M', shiny = false,
            ability = "Testability", moves = moves.toMutableList(), hp = 1,
        )
        m.hp = StatCalc.maxHp(data, m)
        return m
    }

    /** A fresh game state with one main of [species] at [level]. */
    fun state(data: GameData, species: Int = 25, level: Int = 20, moves: List<String> = listOf("tackle", "thunderbolt")): GameState {
        val s = GameState(createdAt = 1L, day = "2026-10-13", starterChosen = true)
        val m = mon(data, species, level, moves, uid = "main")
        s.mons += m; s.team += m.uid; s.seen += species; s.caught += species
        return s
    }

    fun rng(seed: Int = 1) = Random(seed)
}
