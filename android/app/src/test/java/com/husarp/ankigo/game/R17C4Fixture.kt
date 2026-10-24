package com.husarp.ankigo.game

import kotlin.random.Random

/**
 * R17C-4 test fixtures: the moves of the ticket with their real Showdown ids, types, categories, powers and priorities (a move whose real
 * base power is 0 gets the flat 60 the parser gives, so a test proves that [MovePower] does not leave it at 60), a few made-up species
 * with chosen weights and speeds, and a rule that scripts the move each side uses.
 */
internal object R17C4Fixture {
    private fun mv(id: String, type: Type, cat: Category, power: Int, acc: Int? = 100, priority: Int = 0, target: String = "normal", edit: (Move) -> Move = { it }) =
        edit(Move(id, id, type, cat, power, acc, priority = priority, target = target))

    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL

    val MOVES: List<Move> = listOf(
        // group 1: power from the fight
        mv("lowkick", Type.FIGHTING, P, 60), mv("grassknot", Type.GRASS, S, 60), mv("heavyslam", Type.STEEL, P, 60), mv("heatcrash", Type.FIRE, P, 60),
        mv("flail", N, P, 60), mv("reversal", Type.FIGHTING, P, 60),
        mv("eruption", Type.FIRE, S, 150, target = "allAdjacentFoes"), mv("waterspout", Type.WATER, S, 150, target = "allAdjacentFoes"),
        mv("dragonenergy", Type.DRAGON, S, 150, target = "allAdjacentFoes"),
        mv("crushgrip", N, P, 60), mv("wringout", N, S, 60), mv("hardpress", Type.STEEL, P, 60),
        mv("gyroball", Type.STEEL, P, 60), mv("electroball", Type.ELECTRIC, S, 60),
        mv("return", N, P, 60), mv("pikapapow", Type.ELECTRIC, S, 60, null), mv("veeveevolley", N, P, 60, null), mv("frustration", N, P, 60),
        mv("storedpower", Type.PSYCHIC, S, 20), mv("powertrip", Type.DARK, P, 20), mv("punishment", Type.DARK, P, 60),
        mv("magnitude", Type.GROUND, P, 60, target = "allAdjacent"), mv("present", N, P, 60, 90),
        mv("trumpcard", N, S, 60, null), mv("ragefist", Type.GHOST, P, 50),
        // group 2: situational power
        mv("hex", Type.GHOST, S, 65), mv("infernalparade", Type.GHOST, S, 60), mv("venoshock", Type.POISON, S, 65), mv("barbbarrage", Type.POISON, P, 60),
        mv("facade", N, P, 70), mv("brine", Type.WATER, S, 65), mv("acrobatics", Type.FLYING, P, 55, target = "any"), mv("knockoff", Type.DARK, P, 65),
        mv("assurance", Type.DARK, P, 60), mv("avalanche", Type.ICE, P, 60, priority = -4), mv("revenge", Type.FIGHTING, P, 60, priority = -4),
        mv("payback", Type.DARK, P, 50), mv("fishiousrend", Type.WATER, P, 85), mv("boltbeak", Type.ELECTRIC, P, 85),
        mv("wakeupslap", Type.FIGHTING, P, 70), mv("smellingsalts", N, P, 70), mv("lashout", Type.DARK, P, 75),
        mv("stompingtantrum", Type.GROUND, P, 75), mv("temperflare", Type.FIRE, P, 75),
        mv("lastrespects", Type.GHOST, P, 50), mv("retaliate", N, P, 70), mv("round", N, S, 60),
        // group 3: build-ups
        mv("echoedvoice", N, S, 40), mv("furycutter", Type.BUG, P, 40, 95), mv("rollout", Type.ROCK, P, 30, 90), mv("iceball", Type.ICE, P, 30, 90),
        mv("tripleaxel", Type.ICE, P, 20, 90) { it.copy(multihit = 3..3, multiAccuracy = true) },
        mv("triplekick", Type.FIGHTING, P, 10, 90) { it.copy(multihit = 3..3, multiAccuracy = true) },
        mv("populationbomb", N, P, 20, 90) { it.copy(multihit = 10..10, multiAccuracy = true) },
        mv("beatup", Type.DARK, P, 60), mv("rage", N, P, 20),
        mv("bide", N, P, 60, null, priority = 1, target = "self"),
        // group 4: special damage
        mv("psywave", Type.PSYCHIC, S, 60), mv("endeavor", N, P, 60),
        mv("finalgambit", Type.FIGHTING, S, 60) { it.copy(selfDestruct = true) },
        mv("counter", Type.FIGHTING, P, 60, priority = -5), mv("mirrorcoat", Type.PSYCHIC, S, 60, priority = -5),
        mv("metalburst", Type.STEEL, P, 60), mv("comeuppance", Type.DARK, P, 60),
        // group 5: fail and timing rules
        mv("fakeout", N, P, 40, priority = 3), mv("firstimpression", Type.BUG, P, 90, priority = 2),
        mv("suckerpunch", Type.DARK, P, 70, priority = 1), mv("thunderclap", Type.ELECTRIC, S, 70, priority = 1),
        mv("focuspunch", Type.FIGHTING, P, 150, priority = -3),
        mv("dreameater", Type.PSYCHIC, S, 100) { it.copy(drain = 1 to 2) },
        mv("snore", N, S, 50) { it.copy(sleepUsable = true) }, mv("poltergeist", Type.GHOST, P, 110, 90),
        mv("synchronoise", Type.PSYCHIC, S, 120, target = "allAdjacent"), mv("lastresort", N, P, 140),
        mv("futuresight", Type.PSYCHIC, S, 120), mv("doomdesire", Type.STEEL, S, 140),
        // group 6: type rules
        mv("hiddenpower", N, S, 60), mv("revelationdance", N, S, 90), mv("freezedry", Type.ICE, S, 70), mv("flyingpress", Type.FIGHTING, P, 100, 95),
        // helpers
        mv("shadowball2", Type.GHOST, S, 80),
    )

    /** Species: a big sturdy Normal "Hitter" (num 400, speed 100), a Water "Tank" (num 401, speed 50), a Ghost (402), a Dark (403), a Fire (404), a Fast Normal (405, speed 140). */
    val BASE = Stats(120, 100, 100, 100, 100, 100)
    private fun sp(num: Int, name: String, types: List<Type>, spe: Int, kg: Double? = 60.0) =
        TestData.species(num, name, types, BASE.copy(spe = spe)).copy(weightKg = kg)

    val HITTER = sp(400, "Hitter", listOf(Type.NORMAL), 100)
    val TANK = sp(401, "Tank", listOf(Type.WATER), 50)
    val GHOST = sp(402, "Spook", listOf(Type.GHOST), 50)
    val DARK = sp(403, "Shade", listOf(Type.DARK), 50)
    val FIRE = sp(404, "Ember", listOf(Type.FIRE), 50)
    val FAST = sp(405, "Zippy", listOf(Type.NORMAL), 140)
    val PSY = sp(406, "Mind", listOf(Type.PSYCHIC), 50)
    val FLYER = sp(407, "Glider", listOf(Type.NORMAL, Type.FLYING), 50)
    val WATERGRASS = sp(408, "Pondweed", listOf(Type.WATER, Type.GRASS), 50)

    /** Species with chosen weights for the weight moves: numbers 450 + index. */
    val WEIGHTS: List<Double?> = listOf(5.0, 10.0, 24.9, 25.0, 49.9, 50.0, 99.9, 100.0, 199.9, 200.0, null)
    val WEIGHT_SPECIES: List<Species> = WEIGHTS.mapIndexed { i, kg -> sp(450 + i, "Weight$i", listOf(Type.NORMAL), 50, kg) }

    fun data(extraSpecies: List<Species> = emptyList(), extraMoves: List<Move> = emptyList()): GameData =
        TestData.data(
            TestData.world().species.values.toList() + listOf(HITTER, TANK, GHOST, DARK, FIRE, FAST, PSY, FLYER, WATERGRASS) + WEIGHT_SPECIES + extraSpecies,
            TestData.MOVES + MOVES.associateBy { it.id } + extraMoves.associateBy { it.id },
        )

    /**
     * A rule that scripts the moves: [main] and [wild] are queues of move ids (null = no scripted move: the normal pick), one entry per
     * pick of that side. Put it FIRST in the list of a test so it is asked before the rule sets.
     */
    class Script(val main: ArrayDeque<String?> = ArrayDeque(), val wild: ArrayDeque<String?> = ArrayDeque()) : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? {
            val next = (if (p) main else wild).removeFirstOrNull() // one entry per pick; an entry the rules overrule is dropped
            if (yieldsToCore(v, p)) return null
            return next?.let { v.data.move(it) ?: error("no move $it") }
        }
    }

    /**
     * R17C FIX-A (review item 18): a script put FIRST must not override the rules' own forced move: a charge hit, a locked move (Outrage, Uproar),
     * a rest turn after a recharge move. True when [CoreRules] is among the sets in play and has one for [p]; the scripts of every R17C fixture ask it.
     */
    fun yieldsToCore(v: BattleView, p: Boolean): Boolean = CoreRules in BattleRules.ALL && CoreRules.forcedMove(v, p) != null

    /** Random numbers from a script: [doubles] feed nextDouble() (hit chances), everything else is the lowest number (rolls 85, chance 0). */
    class ScriptedRandom(vararg doubles: Double) : Random() {
        private val queue = ArrayDeque(doubles.toList())
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = queue.removeFirstOrNull() ?: 0.0
    }
}
