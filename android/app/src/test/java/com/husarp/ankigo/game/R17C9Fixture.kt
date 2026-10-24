package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C-9a test fixtures: moves with their real Showdown ids, types, categories, powers, accuracies and flags (no real Pokémon data in the repo)
 * and made-up species of the types the abilities look at. The Main "Fastie" (800) is very fast, the wild "Slowpoke" (801) very slow and bulky, so
 * a round always has the same order unless a test says otherwise. Both are Normal types; level 100 so the damage numbers can be compared.
 * The ability of a Mon is set per test ([main] / [wild] take it).
 */
internal object R17C9 {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS

    private val contact = setOf("contact", "protect")
    private val prot = setOf("protect")

    val moves: List<Move> = listOf(
        // plain attacks of every type the abilities look at
        Move("tackle", "Tackle", N, P, 40, 100, flags = contact),
        Move("quickattack", "Quick Attack", N, P, 40, 100, priority = 1, flags = contact),
        Move("bodyslam", "Body Slam", N, P, 85, 100, flags = contact),
        Move("hyperbeam", "Hyper Beam", N, S, 150, 90, flags = prot),
        Move("watergun", "Water Gun", Type.WATER, S, 40, 100, flags = prot),
        Move("hydropump", "Hydro Pump", Type.WATER, S, 110, 80, flags = prot),
        Move("ember", "Ember", Type.FIRE, S, 40, 100, flags = prot),
        Move("flamethrower", "Flamethrower", Type.FIRE, S, 90, 100, flags = prot),
        Move("thundershock", "Thunder Shock", Type.ELECTRIC, S, 40, 100, flags = prot),
        Move("vinewhip", "Vine Whip", Type.GRASS, P, 45, 100, flags = contact),
        Move("razorleaf", "Razor Leaf", Type.GRASS, P, 55, 95, flags = prot),
        Move("earthquake", "Earthquake", Type.GROUND, P, 100, 100, target = "allAdjacent", flags = prot),
        Move("bugbite", "Bug Bite", Type.BUG, P, 60, 100, flags = setOf("bite", "contact", "protect")),
        Move("shadowball", "Shadow Ball", Type.GHOST, S, 80, 100, flags = prot),
        Move("gust", "Gust", Type.FLYING, S, 40, 100, flags = prot),
        Move("karatechop", "Karate Chop", Type.FIGHTING, P, 50, 100, flags = contact),
        Move("machpunch", "Mach Punch", Type.FIGHTING, P, 40, 100, priority = 1, flags = setOf("contact", "protect", "punch")),
        Move("aurasphere", "Aura Sphere", Type.FIGHTING, S, 80, null, flags = setOf("protect", "pulse")),
        Move("darkpulse", "Dark Pulse", Type.DARK, S, 80, 100, flags = setOf("protect", "pulse")),
        Move("crunch", "Crunch", Type.DARK, P, 80, 100, flags = setOf("bite", "contact", "protect")),
        Move("firepunch", "Fire Punch", Type.FIRE, P, 75, 100, flags = setOf("contact", "protect", "punch"), secondary = Secondary(10, status = "brn")),
        Move("bite", "Bite", Type.DARK, P, 60, 100, flags = setOf("bite", "contact", "protect"), secondary = Secondary(30, flinch = true, volatileStatus = "flinch")),
        Move("headbutt", "Headbutt", N, P, 70, 100, flags = contact, secondary = Secondary(30, flinch = true, volatileStatus = "flinch")),
        Move("hypervoice", "Hyper Voice", N, S, 90, 100, target = "allAdjacentFoes", flags = setOf("protect", "sound")),
        Move("doubleedge", "Double-Edge", N, P, 120, 100, flags = contact, recoil = 33 to 100),
        Move("highjumpkick", "High Jump Kick", Type.FIGHTING, P, 130, 90, flags = contact, crashDamage = true),
        Move("explosion", "Explosion", N, P, 250, 100, target = "allAdjacent", selfDestruct = true, flags = prot),
        Move("fissure", "Fissure", Type.GROUND, P, 0, 30, ohko = true, flags = prot),
        Move("bulletseed", "Bullet Seed", Type.GRASS, P, 25, 100, multihit = 2..5, flags = prot),
        Move("doublekick", "Double Kick", Type.FIGHTING, P, 30, 100, multihit = 2..2, flags = contact),
        Move("tripleaxel", "Triple Axel", Type.ICE, P, 20, 90, multihit = 3..3, multiAccuracy = true, flags = contact),
        Move("psychic", "Psychic", Type.PSYCHIC, S, 90, 100, flags = prot, secondary = Secondary(100, boosts = mapOf("spd" to -1))),
        Move("flamecharge", "Flame Charge", Type.FIRE, P, 50, 100, flags = contact, secondary = Secondary(100, selfBoosts = mapOf("spe" to 1))),
        Move("gigadrain", "Giga Drain", Type.GRASS, S, 75, 100, drain = 1 to 2, flags = setOf("protect", "heal")),
        Move("icebeam", "Ice Beam", Type.ICE, S, 90, 100, flags = prot),
        // status moves
        Move("growl", "Growl", N, ST, 0, 100, target = "allAdjacentFoes", boosts = mapOf("atk" to -1), flags = setOf("protect", "sound")),
        Move("leer", "Leer", N, ST, 0, 100, target = "allAdjacentFoes", boosts = mapOf("def" to -1), flags = prot),
        Move("sandattack", "Sand Attack", Type.GROUND, ST, 0, 100, boosts = mapOf("accuracy" to -1), flags = prot),
        Move("swordsdance", "Swords Dance", N, ST, 0, null, target = "self", boosts = mapOf("atk" to 2)),
        Move("sleeppowder", "Sleep Powder", Type.GRASS, ST, 0, 100, status = "slp", flags = setOf("protect", "powder")),
        Move("thunderwave", "Thunder Wave", Type.ELECTRIC, ST, 0, 100, status = "par", flags = prot),
        Move("willowisp", "Will-O-Wisp", Type.FIRE, ST, 0, 100, status = "brn", flags = prot),
        Move("hypnosis", "Hypnosis", Type.PSYCHIC, ST, 0, 100, status = "slp", flags = prot),
        Move("toxic", "Toxic", Type.POISON, ST, 0, 100, status = "tox", flags = prot),
        Move("confuseray", "Confuse Ray", Type.GHOST, ST, 0, 100, volatileStatus = "confusion", flags = prot),
        Move("attract", "Attract", N, ST, 0, 100, volatileStatus = "attract", flags = prot),
        Move("taunt", "Taunt", Type.DARK, ST, 0, 100, volatileStatus = "taunt", flags = prot),
        Move("soak", "Soak", Type.WATER, ST, 0, 100, flags = prot),
        Move("leechseed", "Leech Seed", Type.GRASS, ST, 0, 100, volatileStatus = "leechseed", flags = prot),
        Move("sandstorm", "Sandstorm", Type.ROCK, ST, 0, null, target = "all"),
        Move("splash", "Splash", N, ST, 0, null, target = "self"),
        Move("bellydrum", "Belly Drum", N, ST, 0, null, target = "self"),
        Move("substitute", "Substitute", N, ST, 0, null, target = "self"),
        Move("reflect", "Reflect", Type.PSYCHIC, ST, 0, null, target = "allySide"),
    )

    // number, name, types, base stats (hp atk def spa spd spe)
    val species = listOf(
        TestData.species(800, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(801, "Slowpoke", listOf(N), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(802, "Leafy", listOf(Type.GRASS), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(803, "Darky", listOf(Type.DARK), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(804, "Boo", listOf(Type.GHOST), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(805, "Sparky", listOf(Type.ELECTRIC), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(806, "Rocky", listOf(Type.ROCK), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(807, "Torchy", listOf(Type.FIRE), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(808, "Slowfast", listOf(N), TestData.stats(250, 80, 80, 80, 80, 300)), // faster than Fastie
        TestData.species(809, "Tanky", listOf(N), TestData.stats(250, 80, 160, 80, 20, 10)),   // high Defense, low Sp. Def: Download picks Sp. Atk
        TestData.species(810, "Walley", listOf(N), TestData.stats(250, 80, 20, 80, 160, 10)),  // low Defense, high Sp. Def: Download picks Attack
        TestData.species(811, "Poisony", listOf(Type.POISON), TestData.stats(250, 80, 80, 80, 80, 10)),
    )

    val data: GameData = TestData.data(TestData.world().species.values.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    /** A Pokémon with the ability [ability] ("" = none), level [lv], [gender], and the moves [ids]. */
    fun mon(sp: Int, lv: Int, ability: String, vararg ids: String, gender: Char = 'M'): Mon =
        TestData.mon(data, sp, lv, ids.toList()).also { it.ability = ability; it.gender = gender }

    /** The Main: very fast, level 100. */
    fun main(ability: String = "", vararg ids: String, gender: Char = 'M'): Mon = mon(800, 100, ability, *ids, gender = gender)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(ability: String = "", sp: Int = 801, vararg ids: String, gender: Char = 'F', lv: Int = 100): Mon = mon(sp, lv, ability, *ids, gender = gender)

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** Always the biggest random numbers: a hit chance below 100 misses, a chance is never met. */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    /** Always the smallest numbers: every chance is met and nothing misses. */
    val low: Random = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    /** Every percent roll (`nextInt(100)`) gives [percent], every chance roll (`nextDouble`) gives [fraction], damage rolls are the lowest and hits never miss. */
    fun fixed(percent: Int, fraction: Double = 0.0): Random = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = minOf(percent, until - 1)
        override fun nextDouble() = fraction
    }

    /** A rule that scripts the moves: [main] and [wild] are queues of move ids, one entry per pick of that side. Put it FIRST so it is asked before the rule sets. */
    class Script(val main: ArrayDeque<String> = ArrayDeque(), val wild: ArrayDeque<String> = ArrayDeque()) : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? {
            val next = (if (p) main else wild).removeFirstOrNull() // R17C FIX-A: one entry per pick; the rules' own forced move (a charge hit, a lock) overrules it
            if (R17C4Fixture.yieldsToCore(v, p)) return null
            return next?.let { v.data.move(it) ?: error("no move $it") }
        }
    }

    /** Plays one round for every entry: [mainIds] / [wildIds] are the moves each side uses in that round (null = that side does not act in it). */
    fun play(
        main: Mon, b: Battle, mainIds: List<String?>, wildIds: List<String?> = emptyList(), rng: Random = low,
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

    /** One round: the Main uses [mainMove] (null = it does not act), the wild one [wildMove]. */
    fun round(main: Mon, b: Battle, mainMove: String?, wildMove: String? = null, rng: Random = low): List<GameEvent> =
        play(main, b, listOf(mainMove), listOf(wildMove), rng).single()

    /** The damage of one hit (roll 100, no crit) with the fight as it is now. [byMain] = the Main attacks the wild one, else the other way round. */
    fun dmg(b: Battle, main: Mon, move: String, byMain: Boolean = true): Int {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move) ?: error("no move $move")
        return if (byMain) BattleEngine.damage(data, main, b.player, b.wild, b.enemy, m, Random(1), false, 100, v, true).damage
        else BattleEngine.damage(data, b.wild, b.enemy, main, b.player, m, Random(1), false, 100, v, false).damage
    }

    /** A [Hit] of the Main's [move] against the wild one (or the other way round with [byMain] = false). */
    fun hit(b: Battle, main: Mon, move: String, byMain: Boolean = true, hitIndex: Int = 0): Hit {
        val v = SimpleView.of(data, b, main)
        val m = data.move(move)!!
        return if (byMain) BattleEngine.newHit(v, true, main, b.player, b.wild, b.enemy, m, hitIndex) else BattleEngine.newHit(v, false, b.wild, b.enemy, main, b.player, m, hitIndex)
    }

    /** The accuracy percent the chain gives for [move] (starting from the move's own), null = never misses. */
    fun accuracy(b: Battle, main: Mon, move: String, byMain: Boolean = true, hitIndex: Int = 0): Double? {
        val h = hit(b, main, move, byMain, hitIndex)
        return rulesChain(data.move(move)!!.accuracy?.toDouble()) { r, a -> r.accuracy(h, a) }
    }

    /** The ratio of [move]'s damage (Main -> wild) with the abilities as given to the damage with neither side having one. */
    fun ratio(mainAbility: String, wildAbility: String, move: String, wildSp: Int = 801, setup: (Battle, Mon) -> Unit = { _, _ -> }): Double {
        val plain = battle(wild("", wildSp, "tackle")).let { b -> val m = main("", "tackle"); setup(b, m); dmg(b, m, move) }
        val withAb = battle(wild(wildAbility, wildSp, "tackle")).let { b -> val m = main(mainAbility, "tackle"); setup(b, m); dmg(b, m, move) }
        return withAb.toDouble() / plain
    }

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }
}
