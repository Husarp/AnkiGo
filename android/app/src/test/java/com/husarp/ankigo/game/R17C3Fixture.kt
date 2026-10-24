package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C-3 test fixtures: made-up species and the moves of CoreRules (no real Pokémon data in the repo). The Main is "Fastie" (very fast, level 50),
 * the wild one "Slowpoke" (very slow and very bulky, level 100) unless a test says otherwise, so the order of a round and the survival over
 * several rounds are the same in every test. The moves are as the game data gives them (flags, volatile conditions, secondaries).
 */
internal object R17C3 {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS

    val moves: List<Move> = listOf(
        // recharge
        Move("hyperbeam", "Hyper Beam", N, S, 150, 90, flags = setOf("recharge"), selfVolatile = "mustrecharge"),
        Move("gigaimpact", "Giga Impact", N, P, 150, 100, selfVolatile = "mustrecharge"),
        // charge moves
        Move("skullbash", "Skull Bash", N, P, 130, 100, flags = setOf("charge")),
        Move("meteorbeam", "Meteor Beam", Type.ROCK, S, 120, 100, flags = setOf("charge")),
        Move("electroshot", "Electro Shot", Type.ELECTRIC, S, 130, 100, flags = setOf("charge")),
        Move("geomancy", "Geomancy", Type.FAIRY, ST, 0, null, target = "self", boosts = mapOf("spa" to 2, "spd" to 2, "spe" to 2), flags = setOf("charge")),
        Move("dig", "Dig", Type.GROUND, P, 80, 100, flags = setOf("charge")),
        Move("bounce", "Bounce", Type.FLYING, P, 85, 5, flags = setOf("charge")),
        // flinch
        Move("bite", "Bite", Type.DARK, P, 60, 100, secondary = Secondary(100, flinch = true, volatileStatus = "flinch")),
        Move("firefang", "Fire Fang", Type.FIRE, P, 65, 100, secondary = Secondary(100, status = "brn"),
            moreSecondaries = listOf(Secondary(100, flinch = true, volatileStatus = "flinch"))),
        // confusion and love
        Move("confuseray", "Confuse Ray", Type.GHOST, ST, 0, 100, volatileStatus = "confusion"),
        Move("swagger", "Swagger", N, ST, 0, 100, boosts = mapOf("atk" to 2), volatileStatus = "confusion"),
        Move("flatter", "Flatter", Type.DARK, ST, 0, 100, boosts = mapOf("spa" to 1), volatileStatus = "confusion"),
        Move("attract", "Attract", N, ST, 0, 100, volatileStatus = "attract"),
        Move("waterpulse", "Water Pulse", Type.WATER, S, 60, 100, secondary = Secondary(100, volatileStatus = "confusion")),
        // locks
        Move("outrage", "Outrage", Type.DRAGON, P, 120, 100, selfVolatile = "lockedmove"),
        Move("uproar", "Uproar", N, S, 90, 100, selfVolatile = "uproar"),
        Move("snore", "Snore", N, S, 50, 100, sleepUsable = true),
        // binding and the lasting secondaries
        Move("wrap", "Wrap", N, P, 15, 100, volatileStatus = "partiallytrapped"),
        Move("firespin", "Fire Spin", Type.FIRE, S, 35, 100, volatileStatus = "partiallytrapped"),
        Move("saltcure", "Salt Cure", Type.ROCK, P, 40, 100, secondary = Secondary(100, volatileStatus = "saltcure")),
        Move("syrupbomb", "Syrup Bomb", Type.GRASS, S, 60, 100, secondary = Secondary(100, volatileStatus = "syrupbomb")),
        Move("sparklingaria", "Sparkling Aria", Type.WATER, S, 90, 100, secondary = Secondary(100, volatileStatus = "sparklingaria")),
        Move("psychicnoise", "Psychic Noise", Type.PSYCHIC, S, 75, 100, secondary = Secondary(100, volatileStatus = "healblock")),
        Move("glaiverush", "Glaive Rush", Type.DRAGON, P, 120, 100, selfVolatile = "glaiverush"),
        // code-only secondaries (chance 100 here so a test does not depend on the roll)
        Move("triattack", "Tri Attack", N, S, 80, 100, secondary = Secondary(100), secondaryOnHit = true),
        Move("direclaw", "Dire Claw", Type.POISON, P, 80, 100, secondary = Secondary(100), secondaryOnHit = true),
        Move("alluringvoice", "Alluring Voice", Type.FAIRY, S, 80, 100, secondary = Secondary(100), secondaryOnHit = true),
        Move("burningjealousy", "Burning Jealousy", Type.FIRE, S, 70, 100, secondary = Secondary(100), secondaryOnHit = true),
        // crash and HP cost
        Move("highjumpkick", "High Jump Kick", Type.FIGHTING, P, 130, 90, crashDamage = true),
        Move("steelbeam", "Steel Beam", Type.STEEL, S, 140, 95, mindBlownRecoil = true),
        Move("chloroblast", "Chloroblast", Type.GRASS, S, 150, 95),
        Move("wildswing", "Wild Swing", N, P, 40, 1),
        // never picked
        Move("fling", "Fling", Type.DARK, P, 60, 100),
        Move("roar", "Roar", N, ST, 0, null, target = "normal"),
        Move("helpinghand", "Helping Hand", N, ST, 0, null, target = "adjacentAlly", priority = 5),
        Move("focusenergy", "Focus Energy", N, ST, 0, null, target = "self"),
        Move("spite", "Spite", Type.GHOST, ST, 0, 100),
        Move("perishsong", "Perish Song", N, ST, 0, null, target = "all"),
        Move("celebrate", "Celebrate", N, ST, 0, null, target = "self"),
    )

    val species = listOf(
        TestData.species(300, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(301, "Slowpoke", listOf(N), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(302, "Gloom", listOf(Type.GRASS), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(303, "Splashy", listOf(Type.WATER), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(304, "Ironie", listOf(Type.STEEL), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(305, "Boo", listOf(Type.GHOST), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(306, "Torchy", listOf(Type.FIRE), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(307, "Slowfast", listOf(N), TestData.stats(80, 80, 80, 80, 80, 1)),
    )

    val data: GameData = TestData.data(TestData.world().species.values.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    fun mon(sp: Int, lv: Int, vararg ids: String, gender: Char = 'M'): Mon =
        TestData.mon(data, sp, lv, ids.toList()).also { it.gender = gender }

    /** The Main: very fast, level 50. */
    fun main(vararg ids: String, gender: Char = 'M'): Mon = mon(300, 50, *ids, gender = gender)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(vararg ids: String, sp: Int = 301, gender: Char = 'F'): Mon = mon(sp, 100, *ids, gender = gender)

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    fun round(main: Mon, b: Battle, rng: Random, enemyActs: Boolean = true, mainActs: Boolean = true, choice: MoveChoice = MoveChoice.RANDOM, multiplier: Double = 1.0): List<GameEvent> =
        BattleEngine.playRound(data, b, main, multiplier, enemyActs, rng, mainActs = mainActs, moveChoice = choice)

    /**
     * Always the BIGGEST random numbers: a hit chance roll misses, a sleeper does not wake, a chance (nextDouble) is never met. A Random.nextInt(n)
     * with n not a power of two is 0 here (so a secondary's `nextInt(100) < chance` is met); a power of two gives n - 1 (the top damage roll, the longest duration).
     */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    /** Always the smallest numbers: every chance is met (a miss never happens, a sleeper wakes, a paralysed one is stuck, a confused one hurts itself ...). */
    val low: Random = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }
    fun List<GameEvent>.cantMove(stat: String) = effects().filter { it.kind == EffectKind.CANT_MOVE && it.stat == stat }

    /**
     * A test rule set that makes the Main (`p = true`) use [id] on every pick. Needed for a Main with only status moves: the move choice would
     * send it to Struggle (nothing that hurts), and a status move is only one of several picks of a Smart Main.
     */
    fun forcing(id: String): BattleRules = object : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p) v.data.move(id) else null
    }

    /** Runs [block] with the real rule sets plus [extra] after them (a test's own hooks come last, so the real ones answer first). */
    fun <T> withRules(vararg extra: BattleRules, block: () -> T): T = BattleRules.using(BattleRules.ALL + extra.toList(), block)
}
