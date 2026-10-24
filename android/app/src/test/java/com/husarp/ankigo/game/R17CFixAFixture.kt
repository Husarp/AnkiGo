package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C FIX-A test fixtures: the moves of the review items with their real Showdown ids, types, targets, priorities and flags (no real Pokémon
 * data in the repo) and a few made-up species. The Main "Fastie" (900, very fast) acts before the wild "Slowpoke" (901, bulky and slow) unless a
 * priority says otherwise; "Zippy" (903) is a wild one that is faster than the Main. Picks are scripted with [R17C9.Script] (it yields to the
 * rules' own forced move), and every fight runs under the real [BattleRules.ALL] unless a test says otherwise.
 */
internal object R17CFixA {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS
    private val CONTACT = setOf("contact", "protect", "mirror")
    private val PROT = setOf("protect", "mirror")

    private fun atk(id: String, type: Type, cat: Category, power: Int, acc: Int? = 100, priority: Int = 0, target: String = "normal", flags: Set<String> = PROT, edit: (Move) -> Move = { it }) =
        edit(Move(id, id, type, cat, power, acc, priority = priority, target = target, flags = flags))

    private fun st(id: String, type: Type, target: String = "normal", acc: Int? = 100, priority: Int = 0, flags: Set<String> = PROT, edit: (Move) -> Move = { it }) =
        edit(Move(id, id, type, ST, 0, acc, priority = priority, target = target, flags = flags))

    val moves: List<Move> = listOf(
        // attacks
        atk("tackle", N, P, 40, flags = CONTACT), atk("quickattack", N, P, 40, priority = 1, flags = CONTACT),
        atk("thunderbolt", Type.ELECTRIC, S, 90), atk("shadowball", Type.GHOST, S, 80), atk("flamethrower", Type.FIRE, S, 90),
        atk("earthquake", Type.GROUND, P, 100, target = "allAdjacent"), atk("hyperbeam", N, S, 150, 90, flags = PROT + "recharge") { it.copy(selfVolatile = "mustrecharge") },
        atk("solarbeam", Type.GRASS, S, 120, flags = PROT + "charge"), atk("fly", Type.FLYING, P, 90, 95, flags = CONTACT + "charge" + "gravity"),
        atk("fissure", Type.GROUND, P, 0, 30) { it.copy(ohko = true) }, atk("sheercold", Type.ICE, S, 0, 30) { it.copy(ohko = true) },
        atk("mindblown", Type.FIRE, S, 150, target = "allAdjacent") { it.copy(mindBlownRecoil = true) },
        atk("explosion", N, P, 250, target = "allAdjacent") { it.copy(selfDestruct = true) },
        atk("finalgambit", Type.FIGHTING, S, 60) { it.copy(selfDestruct = true, selfDestructIfHit = true) },
        atk("poltergeist", Type.GHOST, P, 110, 90), atk("wakeupslap", Type.FIGHTING, P, 70, flags = CONTACT),
        atk("bide", N, P, 60, null, priority = 1, target = "self"), atk("rage", N, P, 20, flags = CONTACT),
        atk("counter", Type.FIGHTING, P, 60, priority = -5, flags = CONTACT), atk("mirrorcoat", Type.PSYCHIC, S, 60, priority = -5),
        atk("metalburst", Type.STEEL, P, 60), atk("comeuppance", Type.DARK, P, 60),
        atk("suckerpunch", Type.DARK, P, 70, priority = 1, flags = CONTACT), atk("thunderclap", Type.ELECTRIC, S, 70, priority = 1),
        atk("spitup", N, S, 100), atk("spectralthief", Type.GHOST, P, 90, flags = CONTACT + "bypasssub"),
        atk("outrage", Type.DRAGON, P, 120, flags = CONTACT) { it.copy(selfVolatile = "lockedmove") },
        atk("uproar", N, S, 90, flags = setOf("protect", "mirror", "sound", "bypasssub")) { it.copy(selfVolatile = "uproar") },
        atk("hypervoice", N, S, 90, target = "allAdjacentFoes", flags = setOf("protect", "mirror", "sound", "bypasssub")),
        // status moves
        st("protect", N, "self", null, 4), st("detect", Type.FIGHTING, "self", null, 4), st("endure", N, "self", null, 4),
        st("quickguard", Type.FIGHTING, "allySide", null, 3), st("wideguard", Type.ROCK, "allySide", null, 3), st("craftyshield", Type.FAIRY, "allySide", null, 3),
        st("destinybond", Type.GHOST, "self", null, flags = setOf("bypasssub")),
        st("mefirst", N, "adjacentFoe", null, flags = setOf("mirror")), st("snatch", Type.DARK, "self", null, 4, flags = setOf("bypasssub")),
        st("magiccoat", Type.PSYCHIC, "self", null, 4), st("electrify", Type.ELECTRIC, "normal", null),
        st("curse", Type.GHOST, "normal", null, flags = setOf("bypasssub")) { it.copy(volatileStatus = "curse") },
        st("leechseed", Type.GRASS, "normal", 90) { it.copy(volatileStatus = "leechseed") },
        st("thunderwave", Type.ELECTRIC, "normal", 90, flags = PROT + "reflectable") { it.copy(status = "par") },
        st("spore", Type.GRASS, "normal", 100, flags = PROT + "powder") { it.copy(status = "slp") },
        st("willowisp", Type.FIRE, "normal", 85, flags = PROT + "reflectable") { it.copy(status = "brn") },
        st("taunt", Type.DARK, "normal", 100, flags = PROT + "reflectable") { it.copy(volatileStatus = "taunt") },
        st("yawn", N, "normal", null) { it.copy(volatileStatus = "yawn") },
        st("nightmare", Type.GHOST, "normal", 100) { it.copy(volatileStatus = "nightmare") },
        st("supersonic", N, "normal", 55, flags = PROT + "sound" + "bypasssub" + "reflectable") { it.copy(volatileStatus = "confusion") },
        st("confuseray", Type.GHOST, "normal", 100, flags = PROT + "reflectable") { it.copy(volatileStatus = "confusion") },
        st("growl", N, "allAdjacentFoes", 100, flags = setOf("protect", "mirror", "sound", "bypasssub")) { it.copy(boosts = mapOf("atk" to -1)) },
        st("sleeptalk", N, "self", null, flags = setOf("nosleeptalk")) { it.copy(sleepUsable = true) },
        st("metronome", N, "self", null), st("stockpile", N, "self", null) { it.copy(volatileStatus = "stockpile") },
        st("charge", Type.ELECTRIC, "self", null, flags = setOf("snatch")) { it.copy(volatileStatus = "charge", boosts = mapOf("spd" to 1)) },
        st("lockon", N, "normal", null),
        st("substitute", N, "self", null) { it.copy(volatileStatus = "substitute") },
        st("magnetrise", Type.ELECTRIC, "self", null) { it.copy(volatileStatus = "magnetrise") },
        st("partingshot", Type.DARK, "normal", 100, flags = PROT + "reflectable" + "sound" + "bypasssub") { it.copy(selfSwitch = true) },
        st("powder", Type.BUG, "normal", 100, priority = 1, flags = PROT + "reflectable" + "bypasssub" + "powder") { it.copy(volatileStatus = "powder") },
        st("roost", Type.FLYING, "self", null, flags = setOf("heal", "snatch")) { it.copy(heal = 1 to 2, selfVolatile = "roost") },
        st("iondeluge", Type.ELECTRIC, "all", null, priority = 1) { it.copy(pseudoWeather = "iondeluge") },
        st("swordsdance", N, "self", null, flags = setOf("snatch")) { it.copy(boosts = mapOf("atk" to 2)) },
        st("sandstorm", Type.ROCK, "all", null) { it.copy(weather = "sandstorm") },
        st("splash", N, "self", null),
    )

    // number, name, types, base stats (hp atk def spa spd spe)
    val species = listOf(
        TestData.species(900, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(901, "Slowpoke", listOf(N), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(902, "Ghosty", listOf(Type.GHOST), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(903, "Zippy", listOf(N), TestData.stats(250, 80, 80, 80, 80, 300)),
        TestData.species(904, "Sparky", listOf(Type.ELECTRIC), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(905, "Darky", listOf(Type.DARK), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(906, "Glider", listOf(N, Type.FLYING), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(907, "Leafy", listOf(Type.GRASS), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(908, "Fiery", listOf(Type.FIRE), TestData.stats(250, 80, 80, 80, 80, 10)),
        TestData.species(909, "Zippy Ghost", listOf(Type.GHOST), TestData.stats(250, 80, 80, 80, 80, 300)),
    )

    val data: GameData = TestData.data(TestData.world().species.values.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    fun mon(sp: Int, lv: Int, ability: String, vararg ids: String, gender: Char = 'M'): Mon =
        TestData.mon(data, sp, lv, ids.toList()).also { it.ability = ability; it.gender = gender }

    /** The Main: very fast, level 50. */
    fun main(vararg ids: String, ability: String = "", sp: Int = 900, lv: Int = 50): Mon = mon(sp, lv, ability, *ids)

    /** The wild one: slow and bulky, level 100. */
    fun wild(vararg ids: String, ability: String = "", sp: Int = 901, lv: Int = 100): Mon = mon(sp, lv, ability, *ids, gender = 'F')

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** Always the smallest numbers: every chance is met and nothing misses. */
    val low: Random = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    /** Always the biggest numbers: a hit chance below 100 misses, a chance is never met. */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    /**
     * Plays one round for every entry: [mainIds] / [wildIds] are the moves each side uses in that round (null = that side does not act in it).
     * The rule sets are [rules] (the real ones unless given), with the script first.
     */
    fun play(
        main: Mon, b: Battle, mainIds: List<String?>, wildIds: List<String?> = emptyList(), rng: Random = low,
        choice: MoveChoice = MoveChoice.RANDOM, rules: List<BattleRules> = BattleRules.ALL, extra: List<BattleRules> = emptyList(),
    ): List<List<GameEvent>> {
        val script = R17C9.Script(ArrayDeque(mainIds.filterNotNull()), ArrayDeque(wildIds.filterNotNull()))
        return BattleRules.using(listOf<BattleRules>(script) + extra + rules) {
            (0 until maxOf(mainIds.size, wildIds.size)).map { i ->
                BattleEngine.playRound(data, b, main, 1.0, wildIds.getOrNull(i) != null, rng, mainActs = mainIds.getOrNull(i) != null, moveChoice = choice)
            }
        }
    }

    fun round(main: Mon, b: Battle, mainMove: String?, wildMove: String? = null, rng: Random = low): List<GameEvent> =
        play(main, b, listOf(mainMove), listOf(wildMove), rng).single()

    /** The free hit of a switch: the wild one uses [wildMove] (a scripted pick; null = its own pick) on the incoming [main]. */
    fun freeHit(main: Mon, b: Battle, wildMove: String?, rng: Random = low, choice: MoveChoice = MoveChoice.RANDOM): List<GameEvent> {
        val script = R17C9.Script(ArrayDeque(), ArrayDeque(listOfNotNull(wildMove)))
        return BattleRules.using(listOf<BattleRules>(script) + BattleRules.ALL) { BattleEngine.switchHit(data, b, main, rng, choice, 1.0) }
    }

    /** The view the move choice sees: the Main on the field of [b]; [mainActs] false = the free hit of a switch. */
    fun view(main: Mon, b: Battle, mainActs: Boolean = true): SimpleView = SimpleView.of(data, b, main, mainActs)

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
}
