package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C-9b test fixtures: the moves of [R17C9] plus the ones the rarer abilities look at (wind, bullet, slicing, heal, aura and ability-changing moves with
 * their real Showdown ids, types, categories, powers, accuracies and flags) and made-up species of the types they need. The Main "Fastie" (800) is
 * very fast, the wild "Slowpoke" (801) very slow and bulky, so a round always has the same order unless a test says otherwise. Level 100 so the damage
 * numbers can be compared. The ability of a Mon is set per test ([main] / [wild]).
 */
internal object R17C9b {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS
    private val prot = setOf("protect")
    private val contact = setOf("contact", "protect")

    /** The moves of the 9a fixture, with these on top (a move here replaces the one of the same id there). */
    val moves: List<Move> = R17C9.moves + listOf(
        Move("gust", "Gust", Type.FLYING, S, 40, 100, flags = setOf("protect", "wind")),
        Move("hurricane", "Hurricane", Type.FLYING, S, 110, 70, flags = setOf("protect", "wind"), secondary = Secondary(30, volatileStatus = "confusion")),
        Move("icywind", "Icy Wind", Type.ICE, S, 55, 95, target = "allAdjacentFoes", flags = setOf("protect", "wind"), secondary = Secondary(100, boosts = mapOf("spe" to -1))),
        Move("aircutter", "Air Cutter", Type.FLYING, S, 60, 95, target = "allAdjacentFoes", flags = setOf("protect", "slicing", "wind")),
        Move("aerialace", "Aerial Ace", Type.FLYING, P, 60, null, flags = setOf("contact", "protect", "slicing")),
        Move("slash", "Slash", N, P, 70, 100, flags = setOf("contact", "protect", "slicing")),
        Move("shadowball", "Shadow Ball", Type.GHOST, S, 80, 100, flags = setOf("protect", "bullet")),
        Move("sludgebomb", "Sludge Bomb", Type.POISON, S, 90, 100, flags = setOf("protect", "bullet")),
        Move("moonblast", "Moonblast", Type.FAIRY, S, 95, 100, flags = prot),
        Move("dragonclaw", "Dragon Claw", Type.DRAGON, P, 80, 100, flags = contact),
        Move("rockslide", "Rock Slide", Type.ROCK, P, 75, 90, target = "allAdjacentFoes", flags = prot),
        Move("ironhead", "Iron Head", Type.STEEL, P, 80, 100, flags = contact),
        Move("psyshock", "Psyshock", Type.PSYCHIC, S, 80, 100, flags = prot, defensiveStat = "def"),
        Move("surf", "Surf", Type.WATER, S, 90, 100, target = "allAdjacent", flags = prot),
        Move("poisonjab", "Poison Jab", Type.POISON, P, 80, 100, flags = contact),
        Move("recover", "Recover", N, ST, 0, null, target = "self", heal = 1 to 2, flags = setOf("heal")),
        Move("tailwind", "Tailwind", Type.FLYING, ST, 0, null, target = "allySide", sideCondition = "tailwind", flags = setOf("wind")),
        Move("raindance", "Rain Dance", Type.WATER, ST, 0, null, target = "all"),
        Move("sunnyday", "Sunny Day", Type.FIRE, ST, 0, null, target = "all"),
        Move("hail", "Hail", Type.ICE, ST, 0, null, target = "all"),
        Move("electricterrain", "Electric Terrain", Type.ELECTRIC, ST, 0, null, target = "all"),
        Move("lightscreen", "Light Screen", Type.PSYCHIC, ST, 0, null, target = "allySide"),
        Move("protect", "Protect", N, ST, 0, null, target = "self", priority = 4),
        Move("torment", "Torment", Type.DARK, ST, 0, 100, volatileStatus = "torment", flags = prot),
        Move("willowisp", "Will-O-Wisp", Type.FIRE, ST, 0, 85, status = "brn", flags = prot),
        Move("icebeam", "Ice Beam", Type.ICE, S, 90, 100, flags = prot, secondary = Secondary(10, status = "frz")),
        Move("yawn", "Yawn", N, ST, 0, null, volatileStatus = "yawn", flags = prot),
        Move("poisongas", "Poison Gas", Type.POISON, ST, 0, 90, target = "allAdjacentFoes", status = "psn", flags = prot),
        Move("dreameater", "Dream Eater", Type.PSYCHIC, S, 100, 100, drain = 1 to 2, flags = setOf("protect", "heal")),
        Move("hex", "Hex", Type.GHOST, S, 65, 100, flags = prot),
        Move("lowkick", "Low Kick", Type.FIGHTING, P, 0, 100, flags = contact),
        Move("snore", "Snore", N, S, 50, 100, sleepUsable = true, flags = setOf("protect", "sound")),
        // the ability-changing moves
        Move("entrainment", "Entrainment", N, ST, 0, 100, flags = setOf("protect", "reflectable")),
        Move("gastroacid", "Gastro Acid", Type.POISON, ST, 0, 100, flags = setOf("protect", "reflectable")),
        Move("roleplay", "Role Play", Type.PSYCHIC, ST, 0, null, flags = setOf("bypasssub")),
        Move("simplebeam", "Simple Beam", N, ST, 0, 100, flags = setOf("protect", "reflectable")),
        Move("skillswap", "Skill Swap", Type.PSYCHIC, ST, 0, null, flags = setOf("protect", "bypasssub")),
        Move("worryseed", "Worry Seed", Type.GRASS, ST, 0, 100, flags = setOf("protect", "reflectable")),
        Move("doodle", "Doodle", N, ST, 0, 100, flags = prot),
    )

    // number, name, types, base stats (hp atk def spa spd spe)
    val species: List<Species> = R17C9.species + listOf(
        TestData.species(812, "Steely", listOf(Type.STEEL), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(813, "Birdy", listOf(Type.FLYING), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(814, "Fairy", listOf(Type.FAIRY), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(815, "Grounder", listOf(Type.GROUND), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(816, "Wetty", listOf(Type.WATER), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(817, "Icy", listOf(Type.ICE), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(818, "Dragy", listOf(Type.DRAGON), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(819, "Fighty", listOf(Type.FIGHTING), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(820, "Shedy", listOf(Type.BUG, Type.GHOST), TestData.stats(250, 80, 80, 80, 80, 10)), // weak to Dark / Ghost / Flying / Rock / Fire only
        TestData.species(821, "Psycho", listOf(Type.PSYCHIC), TestData.stats(250, 80, 80, 80, 80, 10)),
    )

    val data: GameData = TestData.data(
        TestData.world().species.values.toList() + species,
        TestData.MOVES + moves.associateBy { it.id },
    )

    /** A Pokémon with the ability [ability] ("" = none), level [lv], [gender], and the moves [ids]. */
    fun mon(sp: Int, lv: Int, ability: String, vararg ids: String, gender: Char = 'M'): Mon =
        TestData.mon(data, sp, lv, ids.toList()).also { it.ability = ability; it.gender = gender }

    /** The Main: very fast, level 100. */
    fun main(ability: String = "", vararg ids: String, gender: Char = 'M'): Mon = mon(800, 100, ability, *ids, gender = gender)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(ability: String = "", sp: Int = 801, vararg ids: String, gender: Char = 'F', lv: Int = 100): Mon = mon(sp, lv, ability, *ids, gender = gender)

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    val high: Random = R17C9.high
    val low: Random = R17C9.low
    fun fixed(percent: Int, fraction: Double = 0.0): Random = R17C9.fixed(percent, fraction)

    /** Plays one round for every entry: [mainIds] / [wildIds] are the moves each side uses in that round (null = that side does not act in it). */
    fun play(
        main: Mon, b: Battle, mainIds: List<String?>, wildIds: List<String?> = emptyList(), rng: Random = low,
        choice: MoveChoice = MoveChoice.RANDOM, extra: List<BattleRules> = emptyList(),
    ): List<List<GameEvent>> {
        val script = R17C9.Script(ArrayDeque(mainIds.filterNotNull()), ArrayDeque(wildIds.filterNotNull()))
        val rules = listOf<BattleRules>(script) + extra + BattleRules.ALL
        return BattleRules.using(rules) {
            (0 until maxOf(mainIds.size, wildIds.size)).map { i ->
                BattleEngine.playRound(data, b, main, 1.0, wildIds.getOrNull(i) != null, rng, mainActs = mainIds.getOrNull(i) != null, moveChoice = choice)
            }
        }
    }

    /** One round: the Main uses [mainMove] (null = it does not act), the wild one [wildMove]. */
    fun round(main: Mon, b: Battle, mainMove: String?, wildMove: String? = null, rng: Random = low): List<GameEvent> =
        play(main, b, listOf(mainMove), listOf(wildMove), rng).single()

    /** The free hit of a switch: the wild one uses [wildMove] on the incoming [main]. */
    fun freeHit(main: Mon, b: Battle, wildMove: String, rng: Random = low): List<GameEvent> {
        val script = R17C9.Script(ArrayDeque(), ArrayDeque(listOf(wildMove)))
        return BattleRules.using(listOf<BattleRules>(script) + BattleRules.ALL) { BattleEngine.switchHit(data, b, main, rng, MoveChoice.RANDOM, 1.0) }
    }

    /** The damage of one hit (roll 100, no crit) with the fight as it is now. [byMain] = the Main attacks the wild one, else the other way round. */
    fun dmg(b: Battle, main: Mon, move: String, byMain: Boolean = true): Int {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move) ?: error("no move $move")
        return if (byMain) BattleEngine.damage(data, main, b.player, b.wild, b.enemy, m, Random(1), false, 100, v, true).damage
        else BattleEngine.damage(data, b.wild, b.enemy, main, b.player, m, Random(1), false, 100, v, false).damage
    }

    /** The accuracy percent the chain gives for [move] (starting from the move's own), null = never misses. */
    fun accuracy(b: Battle, main: Mon, move: String, byMain: Boolean = true): Double? {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move)!!
        val h = if (byMain) BattleEngine.newHit(v, true, main, b.player, b.wild, b.enemy, m) else BattleEngine.newHit(v, false, b.wild, b.enemy, main, b.player, m)
        return rulesChain(m.accuracy?.toDouble()) { r, a -> r.accuracy(h, a) }
    }

    /** The ratio of [move]'s damage (Main -> wild) with the abilities as given to the damage with neither side having one. */
    fun ratio(mainAbility: String, wildAbility: String, move: String, wildSp: Int = 801, mainMoves: List<String> = listOf("tackle"), setup: (Battle, Mon) -> Unit = { _, _ -> }): Double {
        val plain = battle(wild("", wildSp, "tackle")).let { b -> val m = main("", *mainMoves.toTypedArray()); setup(b, m); dmg(b, m, move) }
        val withAb = battle(wild(wildAbility, wildSp, "tackle")).let { b -> val m = main(mainAbility, *mainMoves.toTypedArray()); setup(b, m); dmg(b, m, move) }
        return withAb.toDouble() / plain
    }

    /** The ratio of the WILD one's damage to the Main (the wild one has [wildAbility], the Main [mainAbility]). */
    fun ratioFromWild(wildAbility: String, mainAbility: String, move: String, wildSp: Int = 801, setup: (Battle, Mon) -> Unit = { _, _ -> }): Double {
        val plain = battle(wild("", wildSp, "tackle")).let { b -> val m = main("", "tackle"); setup(b, m); dmg(b, m, move, byMain = false) }
        val withAb = battle(wild(wildAbility, wildSp, "tackle")).let { b -> val m = main(mainAbility, "tackle"); setup(b, m); dmg(b, m, move, byMain = false) }
        return withAb.toDouble() / plain
    }

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }
}
