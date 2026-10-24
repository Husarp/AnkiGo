package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.random.Random

/**
 * R17C-8 test fixtures: made-up species and the moves of [MoveControl] as the game data gives them (ids, targets, flags, priorities; no real
 * Pokémon data in the repo). The Main "Fastie" (level 50) is faster than the wild "Slowpoke" (level 100, very bulky), so the Main acts first
 * unless a test swaps them or a priority says otherwise. The wild one's damage is scaled down in [round] so the Main lives through many rounds.
 */
internal object R17C8 {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS
    private val ATTACK = setOf("protect", "mirror", "metronome")
    private val CONTACT = setOf("contact", "protect", "mirror", "metronome")
    private val REFLECT = setOf("protect", "reflectable", "mirror", "bypasssub", "metronome")
    private val SELF = setOf("snatch", "metronome")
    private val CALLING = setOf("failencore", "nosleeptalk", "noassist", "failcopycat", "failmimic", "failinstruct")

    private fun st(id: String, name: String, type: Type = N, target: String = "normal", acc: Int? = 100, flags: Set<String> = REFLECT, priority: Int = 0, edit: (Move) -> Move = { it }) =
        edit(Move(id, name, type, ST, 0, acc, priority = priority, target = target, flags = flags))

    val moves: List<Move> = ArrayList<Move>().apply {
        // ---- attacks ----
        add(Move("tackle", "Tackle", N, P, 40, 100, flags = CONTACT))
        add(Move("ember", "Ember", Type.FIRE, S, 40, 100, flags = ATTACK))
        add(Move("flamethrower", "Flamethrower", Type.FIRE, S, 90, 100, flags = ATTACK))
        add(Move("watergun", "Water Gun", Type.WATER, S, 40, 100, flags = ATTACK))
        add(Move("thunderbolt", "Thunderbolt", Type.ELECTRIC, S, 90, 100, flags = ATTACK))
        add(Move("psychic", "Psychic", Type.PSYCHIC, S, 90, 100, flags = ATTACK))
        add(Move("energyball", "Energy Ball", Type.GRASS, S, 90, 100, flags = ATTACK))
        add(Move("moonblast", "Moonblast", Type.FAIRY, S, 95, 100, flags = ATTACK))
        add(Move("triattack", "Tri Attack", N, S, 80, 100, flags = ATTACK))
        add(Move("earthquake", "Earthquake", Type.GROUND, P, 100, 100, target = "allAdjacent", flags = ATTACK))
        add(Move("karatechop", "Karate Chop", Type.FIGHTING, P, 50, 100, flags = CONTACT))
        add(Move("shadowball", "Shadow Ball", Type.GHOST, S, 80, 100, flags = ATTACK))
        add(Move("wildswing", "Wild Swing", N, P, 40, 1, flags = CONTACT))
        add(Move("stomp", "Stomp", N, P, 65, 50, flags = CONTACT))
        add(Move("bodyslam", "Body Slam", N, P, 85, 50, flags = CONTACT))
        add(Move("rollout", "Rollout", Type.ROCK, P, 30, 90, flags = CONTACT))
        add(Move("hypervoice", "Hyper Voice", N, S, 90, 100, target = "allAdjacentFoes", flags = setOf("protect", "mirror", "sound", "bypasssub", "metronome")))
        add(Move("lowkick", "Low Kick", Type.FIGHTING, P, 0, 100, flags = CONTACT))
        add(Move("mirrorless", "Mirrorless", N, P, 40, 100, flags = setOf("protect")))
        add(Move("leechlife", "Leech Life", Type.BUG, P, 80, 100, drain = 1 to 2, flags = CONTACT))
        add(Move("absorb", "Absorb", Type.GRASS, S, 20, 100, drain = 1 to 2, flags = setOf("protect", "mirror", "heal", "metronome")))
        add(Move("psychicnoise", "Psychic Noise", Type.PSYCHIC, S, 75, 100, secondary = Secondary(100, volatileStatus = "healblock"), flags = setOf("protect", "mirror", "sound")))
        add(Move("throatchop", "Throat Chop", Type.DARK, P, 80, 100, secondary = Secondary(100), flags = CONTACT))
        add(Move("burnup", "Burn Up", Type.FIRE, S, 130, 100, flags = setOf("protect", "mirror", "defrost", "metronome")))
        add(Move("doubleshock", "Double Shock", Type.ELECTRIC, P, 120, 100, flags = setOf("contact", "protect", "mirror")))
        add(Move("smackdown", "Smack Down", Type.ROCK, P, 50, 100, volatileStatus = "smackdown", flags = setOf("protect", "mirror", "nonsky", "metronome")))
        add(Move("thousandarrows", "Thousand Arrows", Type.GROUND, P, 90, 100, volatileStatus = "smackdown", ignoreImmunity = true, target = "allAdjacentFoes", flags = setOf("protect", "mirror", "nonsky")))
        add(Move("fly", "Fly", Type.FLYING, P, 90, 95, flags = setOf("charge", "gravity", "contact", "protect", "mirror", "metronome")))
        add(Move("hyperbeam", "Hyper Beam", N, S, 150, 90, flags = setOf("recharge", "protect", "mirror", "metronome"), selfVolatile = "mustrecharge"))
        add(Move("outrage", "Outrage", Type.DRAGON, P, 120, 100, selfVolatile = "lockedmove", flags = CONTACT))
        add(Move("splash", "Splash", N, ST, 0, null, target = "self", flags = setOf("gravity", "metronome")))
        add(Move("struggle", "Struggle", N, P, 50, null))
        // ---- move control ----
        add(st("taunt", "Taunt", Type.DARK))
        add(st("encore", "Encore", N, flags = REFLECT + "failencore"))
        add(st("disable", "Disable", N))
        add(st("torment", "Torment", Type.DARK))
        add(st("imprison", "Imprison", Type.PSYCHIC, "self", null, setOf("snatch", "bypasssub", "metronome")))
        add(st("healblock", "Heal Block", Type.PSYCHIC, "allAdjacentFoes", 100, setOf("protect", "reflectable", "mirror", "metronome")))
        add(st("recover", "Recover", N, "self", null, setOf("snatch", "heal", "metronome")) { it.copy(heal = 1 to 2) })
        add(st("moonlight", "Moonlight", Type.FAIRY, "self", null, setOf("snatch", "heal", "metronome")) { it.copy(heal = 1 to 2) })
        add(st("growl", "Growl", N, flags = setOf("protect", "reflectable", "mirror", "sound", "bypasssub", "metronome")) { it.copy(boosts = mapOf("atk" to -1)) })
        add(st("swordsdance", "Swords Dance", N, "self", null, SELF) { it.copy(boosts = mapOf("atk" to 2)) })
        add(st("toxic", "Toxic", Type.POISON, "normal", 100, REFLECT) { it.copy(status = "tox") })
        add(st("willowisp", "Will-O-Wisp", Type.FIRE, "normal", 100, REFLECT) { it.copy(status = "brn") })
        add(st("substitute", "Substitute", N, "self", null, setOf("nonsky", "metronome")) { it.copy(volatileStatus = "substitute") })
        // ---- stat tricks ----
        add(st("psychup", "Psych Up", N, "normal", null, setOf("bypasssub", "allyanim", "metronome")))
        add(st("heartswap", "Heart Swap", Type.PSYCHIC, "normal", null, setOf("protect", "mirror", "bypasssub", "metronome")))
        add(st("powerswap", "Power Swap", Type.PSYCHIC, "normal", null, setOf("protect", "mirror", "bypasssub", "metronome")))
        add(st("guardswap", "Guard Swap", Type.PSYCHIC, "normal", null, setOf("protect", "mirror", "bypasssub", "metronome")))
        add(st("speedswap", "Speed Swap", Type.PSYCHIC, "normal", null, setOf("protect", "mirror", "bypasssub", "metronome")))
        add(st("powersplit", "Power Split", Type.PSYCHIC, "normal", null, setOf("protect", "metronome")))
        add(st("guardsplit", "Guard Split", Type.PSYCHIC, "normal", null, setOf("protect", "metronome")))
        add(st("powertrick", "Power Trick", Type.PSYCHIC, "self", null, SELF))
        add(st("powershift", "Power Shift", N, "self", null, setOf("snatch")))
        add(st("topsyturvy", "Topsy-Turvy", Type.DARK, "normal", null, setOf("protect", "reflectable", "mirror", "metronome")))
        add(st("acupressure", "Acupressure", N, "adjacentAllyOrSelf", null, setOf("metronome")))
        add(st("venomdrench", "Venom Drench", Type.POISON, "allAdjacentFoes", 100, setOf("protect", "reflectable", "mirror", "metronome")))
        add(st("rototiller", "Rototiller", Type.GROUND, "all", null, setOf("distance", "nonsky", "metronome")))
        add(st("flowershield", "Flower Shield", Type.FAIRY, "all", null, setOf("distance", "metronome")))
        add(st("charge", "Charge", Type.ELECTRIC, "self", null, SELF) { it.copy(boosts = mapOf("spd" to 1), volatileStatus = "charge") })
        add(st("defensecurl", "Defense Curl", N, "self", null, SELF) { it.copy(boosts = mapOf("def" to 1), volatileStatus = "defensecurl") })
        add(st("minimize", "Minimize", N, "self", null, SELF) { it.copy(boosts = mapOf("evasion" to 2), volatileStatus = "minimize") })
        add(st("tarshot", "Tar Shot", Type.ROCK, "normal", 100, REFLECT) { it.copy(boosts = mapOf("spe" to -1), volatileStatus = "tarshot") })
        // ---- Transform and types ----
        add(st("transform", "Transform", N, "normal", null, setOf("allyanim", "failencore", "noassist", "failcopycat", "failmimic", "failinstruct")))
        add(st("soak", "Soak", Type.WATER, "normal", 100, setOf("protect", "reflectable", "mirror", "allyanim", "metronome")))
        add(st("magicpowder", "Magic Powder", Type.PSYCHIC, "normal", 100, setOf("protect", "reflectable", "mirror", "allyanim", "powder", "metronome")))
        add(st("reflecttype", "Reflect Type", N, "normal", null, setOf("protect", "bypasssub", "allyanim", "metronome")))
        add(st("conversion", "Conversion", N, "self", null, SELF))
        add(st("conversion2", "Conversion 2", N, "normal", null, setOf("bypasssub", "metronome")))
        add(st("forestscurse", "Forest's Curse", Type.GRASS, "normal", 100, setOf("protect", "reflectable", "mirror", "allyanim", "metronome")))
        add(st("trickortreat", "Trick-or-Treat", Type.GHOST, "normal", 100, setOf("protect", "reflectable", "mirror", "allyanim", "metronome")))
        add(st("camouflage", "Camouflage", N, "self", null, SELF))
        add(st("roost", "Roost", Type.FLYING, "self", null, setOf("snatch", "heal", "metronome")) { it.copy(heal = 1 to 2, selfVolatile = "roost") })
        add(st("electrify", "Electrify", Type.ELECTRIC, "normal", null, setOf("protect", "mirror", "allyanim", "metronome")))
        add(st("psychoshift", "Psycho Shift", Type.PSYCHIC, "normal", 100, setOf("protect", "mirror", "metronome")))
        // ---- grounding and aim ----
        add(st("magnetrise", "Magnet Rise", Type.ELECTRIC, "self", null, setOf("snatch", "gravity", "metronome")))
        add(st("telekinesis", "Telekinesis", Type.PSYCHIC, "normal", null, setOf("protect", "reflectable", "mirror", "gravity", "allyanim", "metronome")))
        add(st("lockon", "Lock-On", N, "normal", null, setOf("protect", "mirror", "metronome")))
        add(st("mindreader", "Mind Reader", N, "normal", null, setOf("protect", "mirror", "metronome")))
        add(st("foresight", "Foresight", N, "normal", null, setOf("protect", "reflectable", "mirror", "bypasssub", "metronome")))
        add(st("odorsleuth", "Odor Sleuth", N, "normal", null, setOf("protect", "reflectable", "mirror", "bypasssub", "allyanim", "metronome")))
        add(st("miracleeye", "Miracle Eye", Type.PSYCHIC, "normal", null, setOf("protect", "reflectable", "mirror", "bypasssub", "metronome")))
        // ---- calling moves ----
        add(st("metronome", "Metronome", N, "self", null, CALLING))
        add(st("sleeptalk", "Sleep Talk", N, "self", null, CALLING) { it.copy(sleepUsable = true) })
        add(st("copycat", "Copycat", N, "self", null, CALLING))
        add(st("mirrormove", "Mirror Move", Type.FLYING, "normal", null, CALLING))
        add(st("mefirst", "Me First", N, "adjacentFoe", null, CALLING + setOf("protect", "bypasssub", "failmefirst")))
        add(st("naturepower", "Nature Power", N, "normal", null, CALLING))
        add(st("snatch", "Snatch", Type.DARK, "self", null, setOf("bypasssub", "mustpressure", "noassist", "failcopycat"), 4))
        add(st("magiccoat", "Magic Coat", Type.PSYCHIC, "self", null, setOf("metronome"), 4))
        add(st("mimic", "Mimic", N, "normal", null, setOf("protect", "bypasssub", "allyanim", "failencore", "nosleeptalk", "noassist", "failcopycat", "failmimic", "failinstruct")))
        add(st("sketch", "Sketch", N, "normal", null, setOf("bypasssub", "allyanim", "failencore", "nosleeptalk", "noassist", "failcopycat", "failmimic", "failinstruct", "nosketch")))
    }

    val species = listOf(
        TestData.species(800, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)).copy(weightKg = 40.0),
        TestData.species(801, "Slowpoke", listOf(N), TestData.stats(250, 40, 40, 40, 40, 10)).copy(weightKg = 30.0),
        TestData.species(802, "Torchy", listOf(Type.FIRE), TestData.stats(250, 120, 30, 110, 25, 10)).copy(weightKg = 250.0),
        TestData.species(803, "Gloom", listOf(Type.GRASS), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(804, "Boo", listOf(Type.GHOST), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(805, "Darky", listOf(Type.DARK), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(806, "Birdy", listOf(Type.FLYING), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(807, "Blobby", listOf(N), TestData.stats(48, 48, 48, 48, 48, 48)).copy(weightKg = 4.0),
        TestData.species(808, "Zappy", listOf(Type.ELECTRIC), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(809, "Splashy", listOf(Type.WATER), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(810, "Windy", listOf(N, Type.FLYING), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(811, "Digger", listOf(Type.GROUND), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(812, "Grassfast", listOf(Type.GRASS), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(813, "Leafbird", listOf(Type.GRASS, Type.FLYING), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(814, "Digfast", listOf(Type.GROUND), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(50, "Diglett", listOf(Type.GROUND), TestData.stats(250, 40, 40, 40, 40, 10)),
    )

    val data: GameData = TestData.data(TestData.world().species.values.filter { it.num != 50 }.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    fun mon(sp: Int, lv: Int, vararg ids: String, gender: Char = 'M'): Mon = TestData.mon(data, sp, lv, ids.toList()).also { it.gender = gender }

    /** The Main: very fast, level 50. */
    fun main(vararg ids: String): Mon = mon(800, 50, *ids)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(vararg ids: String, sp: Int = 801): Mon = mon(sp, 100, *ids)

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    /** One round. The wild one's damage is [enemyPower] of its normal damage (the Main must survive many rounds). */
    fun round(
        main: Mon, b: Battle, rng: Random = Random(1), enemyActs: Boolean = true, mainActs: Boolean = true,
        choice: MoveChoice = MoveChoice.RANDOM, enemyPower: Double = 0.2,
    ): List<GameEvent> = BattleEngine.playRound(data, b, main, 1.0, enemyActs, rng, enemyPower = enemyPower, mainActs = mainActs, moveChoice = choice)

    /** Always the BIGGEST random numbers: a hit chance roll below 100 % misses, a sleeper does not wake. */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }

    /** A test rule set that makes the Main (`p = true`) use [id] on every pick. */
    fun forcing(id: String): BattleRules = object : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p) v.data.move(id) else null
    }

    /** The same for the wild one. */
    fun forcingWild(id: String): BattleRules = object : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? = if (!p) v.data.move(id) else null
    }

    /** Runs [block] with the real rule sets plus [extra] after them (a test's own hooks come last, so the real ones answer first). */
    fun <T> withRules(vararg extra: BattleRules, block: () -> T): T = BattleRules.using(BattleRules.ALL + extra.toList(), block)

    /** The view of the fight, for reading the effective types, stats and moves. */
    fun view(main: Mon, b: Battle): SimpleView = SimpleView.of(data, b, main)
}
