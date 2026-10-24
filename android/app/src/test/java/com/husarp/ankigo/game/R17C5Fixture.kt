package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import kotlin.math.min
import kotlin.random.Random

/**
 * R17C-5 test fixtures: made-up species and the moves of [SideMoves] as the game data gives them (ids, targets, flags, priorities). The Main
 * is "Fastie" (very fast, level 50), the wild one "Slowpoke" (very slow and very bulky, level 100), so the Main acts first unless a priority
 * says otherwise (Protect is +4) and the wild one survives many rounds. Moves are picked by a scripted rule ([script]), one id per round.
 */
internal object R17C5 {
    private val N = Type.NORMAL
    private val P = Category.PHYSICAL
    private val S = Category.SPECIAL
    private val ST = Category.STATUS
    private val CONTACT = setOf("contact", "protect", "mirror")
    private val BLOCKABLE = setOf("protect", "mirror")

    private fun status(id: String, type: Type, target: String = "self", acc: Int? = null, priority: Int = 0, edit: (Move) -> Move = { it }) =
        edit(Move(id, id, type, ST, 0, acc, priority = priority, target = target))

    val moves: List<Move> = listOf(
        // ---- damaging moves ----
        Move("tackle", "Tackle", N, P, 40, 100, flags = CONTACT),
        Move("watergun", "Water Gun", Type.WATER, S, 40, 100, flags = BLOCKABLE),
        Move("quickattack", "Quick Attack", N, P, 40, 100, priority = 1, flags = CONTACT),
        Move("fakeout", "Fake Out", N, P, 40, 100, priority = 3, flags = CONTACT),
        Move("earthquake", "Earthquake", Type.GROUND, P, 100, 100, target = "allAdjacent", flags = BLOCKABLE),
        Move("unblockable", "Unblockable", N, P, 40, 100, flags = setOf("contact")),
        Move("feint", "Feint", N, P, 30, 100, priority = 2, breaksProtect = true),
        Move("hyperspacehole", "Hyperspace Hole", Type.PSYCHIC, S, 80, null, breaksProtect = true, flags = setOf("mirror", "bypasssub")),
        Move("absorb", "Absorb", Type.GRASS, S, 20, 100, drain = 1 to 2, flags = BLOCKABLE),
        Move("doubleedge", "Double-Edge", N, P, 120, 100, recoil = 33 to 100, flags = CONTACT),
        Move("hypervoice", "Hyper Voice", N, S, 90, 100, target = "allAdjacentFoes", flags = setOf("protect", "mirror", "sound", "bypasssub")),
        Move("bulletseed", "Bullet Seed", Type.GRASS, P, 25, 100, multihit = 4..4, flags = BLOCKABLE),
        Move("ember", "Ember", Type.FIRE, S, 40, 100, flags = BLOCKABLE, secondary = Secondary(100, status = "brn")),
        Move("psychic", "Psychic", Type.PSYCHIC, S, 90, 100, flags = BLOCKABLE, secondary = Secondary(100, boosts = mapOf("spd" to -1))),
        Move("bite", "Bite", Type.DARK, P, 60, 100, flags = CONTACT, secondary = Secondary(100, flinch = true, volatileStatus = "flinch")),
        Move("waterpulse", "Water Pulse", Type.WATER, S, 60, 100, flags = BLOCKABLE, secondary = Secondary(100, volatileStatus = "confusion")),
        Move("seismictoss", "Seismic Toss", Type.FIGHTING, P, 0, 100, levelDamage = true, flags = CONTACT),
        Move("brickbreak", "Brick Break", Type.FIGHTING, P, 75, 100, flags = CONTACT),
        Move("psychicfangs", "Psychic Fangs", Type.PSYCHIC, P, 85, 100, flags = CONTACT),
        Move("ragingbull", "Raging Bull", N, P, 90, 100, flags = CONTACT),
        Move("rapidspin", "Rapid Spin", N, P, 50, 100, flags = CONTACT, secondary = Secondary(100, selfBoosts = mapOf("spe" to 1))),
        Move("mortalspin", "Mortal Spin", Type.POISON, P, 30, 100, target = "allAdjacentFoes", flags = CONTACT, secondary = Secondary(100, status = "psn")),
        Move("wrap", "Wrap", N, P, 15, 100, flags = CONTACT, volatileStatus = "partiallytrapped"),
        Move("spitup", "Spit Up", N, S, 0, 100, flags = BLOCKABLE),
        // ---- the Protect family ----
        status("protect", N, priority = 4), status("detect", Type.FIGHTING, priority = 4), status("kingsshield", Type.STEEL, priority = 4),
        status("spikyshield", Type.GRASS, priority = 4), status("banefulbunker", Type.POISON, priority = 4),
        status("burningbulwark", Type.FIRE, priority = 4), status("silktrap", Type.BUG, priority = 4), status("obstruct", Type.DARK, acc = 100, priority = 4),
        status("endure", N, priority = 4), status("quickguard", Type.FIGHTING, "allySide", priority = 3),
        status("wideguard", Type.ROCK, "allySide", priority = 3), status("craftyshield", Type.FAIRY, "allySide", priority = 3),
        status("matblock", Type.FIGHTING, "allySide"),
        // ---- substitute, screens, side conditions ----
        status("substitute", N) { it.copy(volatileStatus = "substitute") },
        status("reflect", Type.PSYCHIC, "allySide") { it.copy(sideCondition = "reflect") },
        status("lightscreen", Type.PSYCHIC, "allySide") { it.copy(sideCondition = "lightscreen") },
        status("auroraveil", Type.ICE, "allySide") { it.copy(sideCondition = "auroraveil") },
        status("safeguard", N, "allySide") { it.copy(sideCondition = "safeguard") },
        status("mist", Type.ICE, "allySide") { it.copy(sideCondition = "mist") },
        status("tailwind", Type.FLYING, "allySide") { it.copy(sideCondition = "tailwind") },
        // ---- lasting effects ----
        status("leechseed", Type.GRASS, "normal", 100) { it.copy(volatileStatus = "leechseed", flags = BLOCKABLE) },
        status("curse", Type.GHOST, "normal") { it.copy(volatileStatus = "curse", flags = setOf("bypasssub")) },
        status("nightmare", Type.GHOST, "normal", 100) { it.copy(volatileStatus = "nightmare", flags = BLOCKABLE) },
        status("yawn", N, "normal") { it.copy(volatileStatus = "yawn", flags = BLOCKABLE) },
        status("destinybond", Type.GHOST) { it.copy(flags = setOf("bypasssub")) },
        status("aquaring", Type.WATER) { it.copy(volatileStatus = "aquaring") },
        status("ingrain", Type.GRASS) { it.copy(volatileStatus = "ingrain") },
        // ---- heals ----
        status("moonlight", Type.FAIRY) { it.copy(flags = setOf("heal")) }, status("morningsun", N) { it.copy(flags = setOf("heal")) },
        status("synthesis", Type.GRASS) { it.copy(flags = setOf("heal")) }, status("shoreup", Type.GROUND) { it.copy(flags = setOf("heal")) },
        status("rest", Type.PSYCHIC) { it.copy(flags = setOf("heal")) },
        status("wish", N) { it.copy(slotCondition = "wish", flags = setOf("heal")) },
        status("stockpile", N) { it.copy(volatileStatus = "stockpile") },
        status("swallow", N) { it.copy(flags = setOf("heal")) },
        status("strengthsap", Type.GRASS, "normal", 100) { it.copy(flags = BLOCKABLE + "heal") },
        status("painsplit", N, "normal") { it.copy(flags = BLOCKABLE) },
        status("junglehealing", Type.GRASS, "allies") { it.copy(flags = setOf("heal")) },
        status("lunarblessing", Type.PSYCHIC, "allies") { it.copy(flags = setOf("heal")) },
        status("healbell", N, "allyTeam") { it.copy(flags = setOf("sound", "bypasssub")) },
        status("aromatherapy", Type.GRASS, "allyTeam"),
        status("refresh", N), status("takeheart", Type.PSYCHIC),
        // ---- stat moves with a cost, Haze ----
        status("bellydrum", N), status("haze", Type.ICE, "all") { it.copy(flags = setOf("bypasssub")) },
        status("filletaway", N) { it.copy(boosts = mapOf("atk" to 2, "spa" to 2, "spe" to 2)) },
        status("clangoroussoul", Type.DRAGON) { it.copy(boosts = mapOf("atk" to 1, "def" to 1, "spa" to 1, "spd" to 1, "spe" to 1)) },
        // ---- helpers ----
        status("thunderwave", Type.ELECTRIC, "normal", 100) { it.copy(status = "par", flags = BLOCKABLE) },
        status("toxic", Type.POISON, "normal", 100) { it.copy(status = "tox", flags = BLOCKABLE) },
        status("hypnosis", Type.PSYCHIC, "normal", 100) { it.copy(status = "slp", flags = BLOCKABLE) },
        status("growl", N, "allAdjacentFoes", 100) { it.copy(boosts = mapOf("atk" to -1), flags = setOf("protect", "sound", "bypasssub")) },
        status("screech", N, "normal", 100) { it.copy(boosts = mapOf("def" to -2), flags = setOf("protect", "sound", "bypasssub")) },
        status("leer", N, "allAdjacentFoes", 100) { it.copy(boosts = mapOf("def" to -1), flags = BLOCKABLE) },
        status("confuseray", Type.GHOST, "normal", 100) { it.copy(volatileStatus = "confusion", flags = BLOCKABLE) },
        status("swordsdance", N) { it.copy(boosts = mapOf("atk" to 2)) },
        status("calmmind", Type.PSYCHIC) { it.copy(boosts = mapOf("spa" to 1, "spd" to 1)) },
        status("splash", N),
    )

    val species = listOf(
        TestData.species(300, "Fastie", listOf(N), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(301, "Slowpoke", listOf(N), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(302, "Gloom", listOf(Type.GRASS), TestData.stats(250, 40, 40, 40, 40, 10)),
        TestData.species(303, "Boo", listOf(Type.GHOST), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(304, "Glider", listOf(N, Type.FLYING), TestData.stats(80, 80, 80, 80, 80, 200)),
        TestData.species(305, "Zippy", listOf(N), TestData.stats(250, 40, 40, 40, 40, 250)), // a very fast wild one
        TestData.species(306, "Stoneling", listOf(Type.ROCK), TestData.stats(250, 40, 40, 40, 40, 10)),
    )

    val data: GameData = TestData.data(TestData.world().species.values.toList() + species, TestData.MOVES + moves.associateBy { it.id })

    fun mon(sp: Int, lv: Int, vararg ids: String, gender: Char = 'M'): Mon =
        TestData.mon(data, sp, lv, ids.toList()).also { it.gender = gender }

    /** The Main: very fast, level 50. */
    fun main(vararg ids: String, sp: Int = 300): Mon = mon(sp, 50, *ids)

    /** The wild one: very slow and bulky, level 100. */
    fun wild(vararg ids: String, sp: Int = 301): Mon = mon(sp, 100, *ids, gender = 'F')

    fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)

    fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** One round: the Main acts, the wild one acts when [wildActs]; [wildPower] scales what the wild one deals (0.25 = a light hit). */
    fun round(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = true, wildPower: Double = 1.0): List<GameEvent> =
        BattleEngine.playRound(data, b, main, 1.0, wildActs, rng, enemyPower = wildPower, moveChoice = MoveChoice.RANDOM)

    /** Always the biggest random numbers (a chance is never met, a sub-100 hit chance misses). */
    val high: Random = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }

    /** Always the smallest numbers (every chance is met, a stall chance of 1 in n succeeds). */
    val low: Random = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    /** Like [low], but a "1 in n" stall roll (a power of 3) fails. */
    val stallFails: Random = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int): Int = if (until in setOf(3, 9, 27, 81, 243, 729)) 1 else super.nextInt(until)
    }

    /** Both sides use these moves, one id per round (the last one repeats); an empty list leaves that side to the normal pick. */
    fun script(main: List<String> = emptyList(), wild: List<String> = emptyList()): BattleRules = object : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? {
            val ids = if (p) main else wild
            if (ids.isEmpty() || R17C4Fixture.yieldsToCore(v, p)) return null // R17C FIX-A: the rules' own forced move (a charge hit, a lock) overrules the script
            return v.data.move(ids[min(v.battle?.rounds ?: 0, ids.size - 1)])
        }
    }

    /** Runs [block] with the real rule sets plus the scripted picks. */
    fun <T> playing(main: List<String> = emptyList(), wild: List<String> = emptyList(), block: () -> T): T =
        BattleRules.using(BattleRules.ALL + script(main, wild), block)

    /**
     * Like [playing], but only with the core, move-power and side-move sets: for rules that read the weather, where another set's weather damage
     * (sandstorm, hail) would move the HP numbers that are being measured.
     */
    fun <T> playingAlone(main: List<String> = emptyList(), wild: List<String> = emptyList(), extra: List<BattleRules> = emptyList(), block: () -> T): T =
        BattleRules.using(listOf(CoreRules, MovePower, SideMoves) + extra + script(main, wild), block)

    /** Runs [block] with the real rule sets plus a test's own hooks (they come last, so the real ones answer first). */
    fun <T> withExtra(vararg extra: BattleRules, block: () -> T): T = BattleRules.using(BattleRules.ALL + extra.toList(), block)

    fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    fun List<GameEvent>.lines() = effects().map { it.text }
    fun List<GameEvent>.kinds(k: EffectKind) = effects().filter { it.kind == k }

    /** The wild one's damage to the Main in one hit at full power, rolled at 100 (no randomness), against the given sides. */
    fun hitOn(b: Battle, main: Mon, move: String, wildHits: Boolean = true): Int {
        val m = data.move(move)!!
        val view = SimpleView.of(data, b, main)
        return if (wildHits) BattleEngine.damage(data, b.wild, b.enemy, main, b.player, m, Random(1), false, 100, view, attackerIsMain = false).damage
        else BattleEngine.damage(data, main, b.player, b.wild, b.enemy, m, Random(1), false, 100, view, attackerIsMain = true).damage
    }
}
