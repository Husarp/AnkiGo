package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C-7 test fixtures: the field moves and the moves they change, with their real Showdown ids, types, categories, powers, accuracies and
 * flags (no real Pokémon data in the repo), and made-up species of every type the rules look at. The Main "Fastie" is very fast, the wild
 * one "Slowpoke" very slow and bulky, so the order of a round is always the same unless a test says otherwise.
 */
internal object R17C7 {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS

    private fun field(id: String, type: Type, acc: Int? = null, priority: Int = 0) =
        Move(id, id, type, ST, 0, acc, priority = priority, target = "all")

    val moves: List<Move> = listOf(
        // weather
        field("raindance", Type.WATER), field("sunnyday", Type.FIRE), field("sandstorm", Type.ROCK), field("hail", Type.ICE),
        field("snowscape", Type.ICE), field("chillyreception", Type.ICE).copy(selfSwitch = true),
        // terrain
        field("electricterrain", Type.ELECTRIC), field("grassyterrain", Type.GRASS), field("mistyterrain", Type.FAIRY), field("psychicterrain", Type.PSYCHIC),
        // rooms and sports
        field("trickroom", Type.PSYCHIC, priority = -7), field("gravity", Type.PSYCHIC), field("wonderroom", Type.PSYCHIC),
        field("mudsport", Type.GROUND), field("watersport", Type.WATER), field("iondeluge", Type.ELECTRIC, priority = 1),
        // clearing moves and Growth
        Move("defog", "Defog", Type.FLYING, ST, 0, null, flags = setOf("protect")),
        field("courtchange", N, acc = 100),
        Move("tidyup", "Tidy Up", N, ST, 0, null, target = "self"),
        Move("growth", "Growth", N, ST, 0, null, target = "self", boosts = mapOf("atk" to 1, "spa" to 1)),
        // weather and terrain power
        Move("weatherball", "Weather Ball", N, S, 50, 100), Move("terrainpulse", "Terrain Pulse", N, S, 50, 100),
        Move("solarbeam", "Solar Beam", Type.GRASS, S, 120, 100, flags = setOf("charge")),
        Move("solarblade", "Solar Blade", Type.GRASS, P, 125, 100, flags = setOf("charge", "contact")),
        Move("electroshot", "Electro Shot", Type.ELECTRIC, S, 130, 100, flags = setOf("charge")),
        Move("thunder", "Thunder", Type.ELECTRIC, S, 110, 70, secondary = Secondary(30, status = "par")),
        Move("hurricane", "Hurricane", Type.FLYING, S, 110, 70, secondary = Secondary(30, volatileStatus = "confusion")),
        Move("blizzard", "Blizzard", Type.ICE, S, 110, 70, secondary = Secondary(10, status = "frz")),
        Move("bleakwindstorm", "Bleakwind Storm", Type.FLYING, S, 100, 80),
        Move("wildboltstorm", "Wildbolt Storm", Type.ELECTRIC, S, 100, 80),
        Move("sandsearstorm", "Sandsear Storm", Type.GROUND, S, 100, 80),
        Move("springtidestorm", "Springtide Storm", Type.FAIRY, S, 100, 80),
        Move("hydrosteam", "Hydro Steam", Type.WATER, S, 80, 100),
        Move("expandingforce", "Expanding Force", Type.PSYCHIC, S, 80, 100),
        Move("risingvoltage", "Rising Voltage", Type.ELECTRIC, S, 70, 100),
        Move("mistyexplosion", "Misty Explosion", Type.FAIRY, S, 100, 100, selfDestruct = true),
        Move("grassyglide", "Grassy Glide", Type.GRASS, P, 55, 100),
        Move("steelroller", "Steel Roller", Type.STEEL, P, 130, 100),
        Move("icespinner", "Ice Spinner", Type.ICE, P, 80, 100),
        Move("earthquake", "Earthquake", Type.GROUND, P, 100, 100, target = "allAdjacent"),
        Move("bulldoze", "Bulldoze", Type.GROUND, P, 60, 100, target = "allAdjacent"),
        Move("magnitude", "Magnitude", Type.GROUND, P, 60, 100, target = "allAdjacent"),
        Move("plasmafists", "Plasma Fists", Type.ELECTRIC, P, 100, 100, pseudoWeather = "iondeluge"),
        // moves Gravity stops (the data's `gravity` flag)
        Move("fly", "Fly", Type.FLYING, P, 90, 95, flags = setOf("charge", "gravity")),
        Move("bounce", "Bounce", Type.FLYING, P, 85, 85, flags = setOf("charge", "gravity")),
        Move("highjumpkick", "High Jump Kick", Type.FIGHTING, P, 130, 90, flags = setOf("gravity"), crashDamage = true),
        Move("magnetrise", "Magnet Rise", Type.ELECTRIC, ST, 0, null, target = "self", flags = setOf("gravity")),
        // plain moves of every type the rules look at
        Move("hydropump", "Hydro Pump", Type.WATER, S, 110, 100), Move("flamethrower", "Flamethrower", Type.FIRE, S, 90, 100),
        Move("psychic", "Psychic", Type.PSYCHIC, S, 90, 100), Move("dragonpulse", "Dragon Pulse", Type.DRAGON, S, 85, 100),
        Move("thunderbolt", "Thunderbolt", Type.ELECTRIC, S, 90, 100), Move("energyball", "Energy Ball", Type.GRASS, S, 90, 100),
        Move("icebeam", "Ice Beam", Type.ICE, S, 90, 100, secondary = Secondary(100, status = "frz")),
        Move("plainspecial", "Plain Special", N, S, 80, 100), Move("plainphysical", "Plain Physical", N, P, 80, 100),
        Move("psyshockish", "Psyshockish", N, S, 80, 100, defensiveStat = "def"),
        Move("quickattack", "Quick Attack", N, P, 40, 100, priority = 1), Move("tackle", "Tackle", N, P, 40, 100),
        Move("shadowclaw", "Shadow Claw", Type.GHOST, P, 70, 100),
        Move("toxic", "Toxic", Type.POISON, ST, 0, 90, status = "tox"),
        Move("thunderwave", "Thunder Wave", Type.ELECTRIC, ST, 0, 90, status = "par"),
        Move("hypnosis", "Hypnosis", Type.PSYCHIC, ST, 0, 60, status = "slp"),
        Move("confuseray", "Confuse Ray", Type.GHOST, ST, 0, 100, volatileStatus = "confusion"),
    )

    // number, name, types, base stats (hp atk def spa spd spe)
    val species = listOf(
        TestData.species(700, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(701, "Slowpoke", listOf(N), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(702, "Rocky", listOf(Type.ROCK), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(703, "Groundy", listOf(Type.GROUND), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(704, "Steely", listOf(Type.STEEL), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(705, "Icy", listOf(Type.ICE), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(706, "Glider", listOf(N, Type.FLYING), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(707, "Sparky", listOf(Type.ELECTRIC), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(708, "Greeny", listOf(Type.GRASS), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(709, "Drako", listOf(Type.DRAGON), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(710, "Mind", listOf(Type.PSYCHIC), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(711, "Torchy", listOf(Type.FIRE), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(712, "Pondy", listOf(Type.WATER), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(713, "Slowfast", listOf(N), TestData.stats(250, 80, 80, 80, 80, 300)), // faster than Fastie
        TestData.species(714, "Boo", listOf(Type.GHOST), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(715, "Beefy", listOf(N), TestData.stats(80, 80, 20, 80, 160, 10)), // low Defense, high Sp. Def: for Wonder Room
    )

    val data: GameData = TestData.data(TestData.world().species.values.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    fun mon(sp: Int, lv: Int, vararg ids: String): Mon = TestData.mon(data, sp, lv, ids.toList())

    /** The Main: very fast, level 100 (the damage numbers are big enough to compare factors). */
    fun main(vararg ids: String): Mon = mon(700, 100, *ids)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(sp: Int = 701, vararg ids: String): Mon = mon(sp, 100, *ids)

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    /** The Main's maximum HP. */
    fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** Always the biggest random numbers: a hit chance below 100 misses, a chance is never met. */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    /** Always the smallest numbers: every chance is met and nothing misses. */
    val low: Random = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    /** A rule that scripts the moves: [main] and [wild] are queues of move ids, one entry per pick of that side. Put it FIRST so it is asked before the rule sets. */
    class Script(val main: ArrayDeque<String> = ArrayDeque(), val wild: ArrayDeque<String> = ArrayDeque()) : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? {
            val next = (if (p) main else wild).removeFirstOrNull() // R17C FIX-A: one entry per pick; the rules' own forced move (a charge hit, a lock) overrules it
            if (R17C4Fixture.yieldsToCore(v, p)) return null
            return next?.let { v.data.move(it) ?: error("no move $it") }
        }
    }

    /**
     * Plays one round for every entry: [mainIds] / [wildIds] are the moves each side uses in that round (null = that side does not act in it).
     * Returns the events of each round.
     */
    fun play(
        main: Mon, b: Battle, mainIds: List<String?>, wildIds: List<String?> = emptyList(), rng: Random = Random(1),
        choice: MoveChoice = MoveChoice.RANDOM, extra: List<BattleRules> = emptyList(),
    ): List<List<GameEvent>> {
        val script = Script(ArrayDeque(mainIds.filterNotNull()), ArrayDeque(wildIds.filterNotNull()))
        val rules = listOf<BattleRules>(script) + extra + BattleRules.ALL
        return BattleRules.using(rules) {
            (0 until maxOf(mainIds.size, wildIds.size)).map { i ->
                BattleEngine.playRound(data, b, main, 1.0, wildIds.getOrNull(i) != null, rng, mainActs = mainIds.getOrNull(i) != null, moveChoice = choice)
            }
        }
    }

    /** The damage of one hit (roll 100, no crit) with the fight's field as it is now. [byMain] = the Main attacks the wild one, else the other way round. */
    fun dmg(b: Battle, main: Mon, move: String, byMain: Boolean = true): Int {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move) ?: error("no move $move")
        return if (byMain) BattleEngine.damage(data, main, b.player, b.wild, b.enemy, m, Random(1), false, 100, v, true).damage
        else BattleEngine.damage(data, b.wild, b.enemy, main, b.player, m, Random(1), false, 100, v, false).damage
    }

    /** The damage ratio of [move] with [setup] applied to the field, against the same hit on an empty field. */
    fun ratio(main: Mon, wildSp: Int, move: String, byMain: Boolean = true, setup: (BattleField) -> Unit): Double {
        val plain = battle(wild(wildSp, "tackle"))
        val set = battle(wild(wildSp, "tackle")).also { setup(it.field) }
        return dmg(set, main, move, byMain).toDouble() / dmg(plain, main, move, byMain)
    }

    /** A [Hit] of the Main's [move] against the fight [b]'s wild one (for the hooks that take a hit). */
    fun hit(b: Battle, main: Mon, move: String, byMain: Boolean = true): Hit {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move)!!
        return if (byMain) BattleEngine.newHit(v, true, main, b.player, b.wild, b.enemy, m) else BattleEngine.newHit(v, false, b.wild, b.enemy, main, b.player, m)
    }

    /** Runs [block] with only the field rules (and the test's own script): for tests whose numbers other sets would also count down (side conditions, Future Sight). */
    fun <T> isolated(block: () -> T): T = BattleRules.using(listOf(FieldRules), block)

    /** A running round to call the helpers on. */
    fun round(main: Mon, b: Battle, rng: Random = Random(1)): BattleEngine.Round = BattleEngine.Round(data, b, main, 1.0, 1.0, rng)

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }
    fun List<GameEvent>.fields() = kinds(EffectKind.FIELD)
}
