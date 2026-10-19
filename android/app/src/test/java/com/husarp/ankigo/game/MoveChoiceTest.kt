package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.help.GameGuide
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * Round 10, W9: the move choice (RANDOM / SMART / SMARTEST) of both sides. Made-up species and moves only.
 * Every rule of [MoveChooser] has a test here; the balance numbers are in MoveChoiceBalanceTest.
 */
class MoveChoiceTest {
    private val base = TestData.world()

    private val extraMoves = listOf(
        Move("sandattack", "Sand Attack", Type.NORMAL, Category.STATUS, 0, 100, boosts = mapOf("accuracy" to -1)),
        Move("sweetscent", "Sweet Scent", Type.NORMAL, Category.STATUS, 0, 100, boosts = mapOf("evasion" to -2)),
        Move("doubleteam", "Double Team", Type.NORMAL, Category.STATUS, 0, null, target = "self", boosts = mapOf("evasion" to 1)),
        Move("honeclaws", "Hone Claws", Type.DARK, Category.STATUS, 0, null, target = "self", boosts = mapOf("atk" to 1, "accuracy" to 1)),
        Move("agility", "Agility", Type.PSYCHIC, Category.STATUS, 0, null, target = "self", boosts = mapOf("spe" to 2)),
        Move("willowisp", "Will-O-Wisp", Type.FIRE, Category.STATUS, 0, 85, status = "brn"),
        Move("sleeppowder", "Sleep Powder", Type.GRASS, Category.STATUS, 0, 75, status = "slp"),
        Move("raindance", "Rain Dance", Type.WATER, Category.STATUS, 0, null, target = "all"),
        Move("bigmiss", "Big Miss", Type.NORMAL, Category.PHYSICAL, 120, 50),
        Move("scratch", "Scratch", Type.NORMAL, Category.PHYSICAL, 40, 100),
        Move("zap", "Zap", Type.ELECTRIC, Category.PHYSICAL, 40, 100),
        Move("explosion", "Explosion", Type.NORMAL, Category.PHYSICAL, 250, 100, selfDestruct = true),
        Move("blast", "Blast", Type.NORMAL, Category.PHYSICAL, 250, 100),
        Move("doublehit", "Double Hit", Type.NORMAL, Category.PHYSICAL, 40, 100, multihit = 2..2),
        Move("onehit", "One Hit", Type.NORMAL, Category.PHYSICAL, 40, 100),
    )

    private val data = GameData(
        base.species + listOf(
            TestData.species(901, "Dustling", listOf(Type.GROUND)),
            TestData.species(903, "Cinderling", listOf(Type.FIRE)),
            TestData.species(905, "Steelbit", listOf(Type.STEEL)),
            TestData.species(906, "Frostling", listOf(Type.ICE)),
            TestData.species(907, "Plainling", listOf(Type.NORMAL)),
        ).associateBy { it.num },
        TestData.MOVES + extraMoves.associateBy { it.id }, base.growth, base.itemPrices,
    )

    private fun mon(species: Int, level: Int, vararg moves: String, uid: String = "m$species") =
        TestData.mon(data, species, level, moves.toList(), uid)

    private val WATER = 54
    private val GRASS_POISON = 1
    private val ELECTRIC = 25
    private val GHOST = 92
    private val GROUND = 901
    private val FIRE = 903
    private val STEEL = 905
    private val PLAIN = 907

    /** How often each move id is picked in [n] picks by one attacker against one defender (the sides are left as given). */
    private fun picks(
        choice: MoveChoice, attacker: Mon, defender: Mon, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide(),
        isMain: Boolean = true, n: Int = 3000, seed: Int = 1, aMaxHp: Int = StatCalc.maxHp(data, attacker),
    ): Map<String, Int> {
        val r = Random(seed)
        return (1..n).map { MoveChooser.pick(choice, data, r, attacker, aSide, aMaxHp, defender, dSide, isMain).id }
            .groupingBy { it }.eachCount()
    }

    private val smart = listOf(MoveChoice.SMART, MoveChoice.SMARTEST)

    // ---------------- the setting ----------------

    @Test fun theDefaultIsSmartAndNamesParseBack() {
        assertEquals(MoveChoice.SMART, Settings().moveChoice)
        for (c in MoveChoice.entries) assertEquals(c, MoveChoice.parse(c.name))
        assertEquals(MoveChoice.SMART, MoveChoice.parse(null))
        assertEquals(MoveChoice.SMART, MoveChoice.parse("nonsense"))
        assertEquals(MoveChoice.SMART, MoveChoice.parse("smart")) // names are the saved enum names, nothing else
        assertEquals(listOf("Random", "Smart", "Smartest"), MoveChoice.entries.map { it.label })
    }

    // ---------------- RANDOM = exactly today ----------------

    /** The Main's pick before W9, copied. */
    private fun oldMainPick(main: Mon, wild: Mon, rng: Random): Move {
        val known = main.moves.mapNotNull { data.move(it) }.ifEmpty { listOf(data.move(Tuning.FALLBACK_MOVE)!!) }
        val wildTypes = data.sp(wild.species).types
        if (known.none { it.damaging && TypeChart.eff(it.type, wildTypes) > 0 }) return data.move(Tuning.STRUGGLE)!!
        return known[rng.nextInt(known.size)]
    }

    /** The wild one's pick before W9, copied. */
    private fun oldWildPick(wild: Mon, rng: Random): Move {
        val known = wild.moves.mapNotNull { data.move(it) }.ifEmpty { listOf(data.move(Tuning.FALLBACK_MOVE)!!) }
        return known[rng.nextInt(known.size)]
    }

    @Test fun randomMakesTheOldPicksWithTheOldRngUse() {
        val sets = listOf(
            arrayOf("tackle", "thunderbolt", "growl", "recover"), arrayOf("tackle"), arrayOf("growl", "swordsdance"),
            arrayOf("sandattack", "fissure", "tackle", "lick"), arrayOf("nonsense"), arrayOf(),
        )
        for (defenderSp in listOf(WATER, GHOST, GROUND, ELECTRIC)) for ((i, moves) in sets.withIndex()) {
            val a = mon(ELECTRIC, 30, *moves)
            val d = mon(defenderSp, 30, *sets[(i + 1) % sets.size])
            val battle = Battle(d, Tier.NORMAL)
            for (seed in 1..120) {
                val oldMain = Random(seed); val newMain = Random(seed)
                assertEquals(oldMainPick(a, d, oldMain).id, MoveChooser.pickMain(MoveChoice.RANDOM, data, battle, a, newMain).id)
                assertEquals("rng use (main)", oldMain.nextInt(1_000_000), newMain.nextInt(1_000_000))
                val oldWild = Random(seed); val newWild = Random(seed)
                assertEquals(oldWildPick(d, oldWild).id, MoveChooser.pickWild(MoveChoice.RANDOM, data, battle, a, newWild).id)
                assertEquals("rng use (wild)", oldWild.nextInt(1_000_000), newWild.nextInt(1_000_000))
            }
        }
    }

    @Test fun pickPlayerMoveStillWorksForItsCallers() {
        val a = mon(ELECTRIC, 30, "tackle", "growl", "thunderbolt")
        val d = mon(WATER, 30, "tackle")
        for (seed in 1..60) assertEquals(oldMainPick(a, d, Random(seed)).id, BattleEngine.pickPlayerMove(data, a, d, Random(seed)).id)
    }

    @Test fun playRoundWithoutAChoiceIsRandomAndPlaysTheSameRoundsAsBefore() {
        for (seed in 1..80) {
            fun fight(choice: MoveChoice?): Pair<List<GameEvent>, List<Int>> {
                val main = mon(ELECTRIC, 30, "tackle", "growl", "thunderbolt", "recover", uid = "main")
                val wild = mon(WATER, 30, "tackle", "sandattack", "watergun", "toxic", uid = "wild")
                val b = Battle(wild, Tier.NORMAL)
                val ev = ArrayList<GameEvent>()
                repeat(6) {
                    ev += if (choice == null) BattleEngine.playRound(data, b, main, 1.0, true, Random(seed + it), 1.0, true)
                    else BattleEngine.playRound(data, b, main, 1.0, true, Random(seed + it), 1.0, true, choice)
                }
                return ev to listOf(main.hp, wild.hp, b.rounds)
            }
            assertEquals(fight(null), fight(MoveChoice.RANDOM))
        }
    }

    @Test fun randomKeepsAccuracyAndEvasionAtPlusMinusSix() {
        val main = mon(ELECTRIC, 30, "tackle", uid = "main")
        val b = Battle(mon(WATER, 30, "sandattack", uid = "wild"), Tier.NORMAL)
        repeat(8) { BattleEngine.playRound(data, b, main, 1.0, true, Random(it), 1.0, false) }
        assertEquals(-6, b.player.boosts[5])
        val b2 = Battle(mon(WATER, 30, "doubleteam", uid = "wild2"), Tier.NORMAL)
        repeat(8) { BattleEngine.playRound(data, b2, main, 1.0, true, Random(it), 1.0, false, MoveChoice.RANDOM) }
        assertEquals(6, b2.enemy.boosts[6])
    }

    // ---------------- SMART: damaging 80 % ----------------

    @Test fun smartAttacksAboutEightyPercentWhenAUsefulStatusMoveExists() {
        for (choice in smart) {
            val p = picks(choice, mon(ELECTRIC, 30, "tackle", "thunderbolt", "growl"), mon(WATER, 30, "tackle"), n = 5000)
            val damaging = (p["tackle"] ?: 0) + (p["thunderbolt"] ?: 0)
            assertEquals("$choice damaging share", 0.8, damaging / 5000.0, 0.03)
            assertTrue("$choice uses the status move too", (p["growl"] ?: 0) > 600)
        }
    }

    @Test fun smartSplitsItsAttacksEvenly() {
        val p = picks(MoveChoice.SMART, mon(ELECTRIC, 30, "tackle", "thunderbolt", "vinewhip"), mon(WATER, 30, "tackle"), n = 6000)
        for (id in listOf("tackle", "thunderbolt", "vinewhip")) assertEquals(id, 1 / 3.0, p.getValue(id) / 6000.0, 0.03)
    }

    @Test fun smartAlwaysAttacksWhenItHasNoUsefulStatusMove() {
        for (choice in smart) {
            val atkAtFloor = BattleSide().also { it.boosts[0] = -6 }
            val p = picks(choice, mon(ELECTRIC, 30, "tackle", "thunderbolt", "growl"), mon(WATER, 30, "tackle"), dSide = atkAtFloor)
            assertEquals("$choice", 0, p["growl"] ?: 0)
            assertEquals(3000, (p["tackle"] ?: 0) + (p["thunderbolt"] ?: 0))
            // a side with only attacks, too
            assertEquals(setOf("tackle", "thunderbolt"), picks(choice, mon(ELECTRIC, 30, "tackle", "thunderbolt"), mon(WATER, 30, "tackle")).keys)
        }
    }

    // ---------------- SMART: never a useless status move ----------------

    /** How often the status move [id] is picked next to Tackle with the sides as given. */
    private fun used(
        choice: MoveChoice, id: String, defenderSp: Int = WATER, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide(),
        isMain: Boolean = true, hp: Int? = null, n: Int = 2000,
    ): Int {
        val a = mon(ELECTRIC, 30, "tackle", id)
        if (hp != null) a.hp = hp
        return picks(choice, a, mon(defenderSp, 30, "tackle"), aSide, dSide, isMain, n).getOrDefault(id, 0)
    }

    private fun side(vararg stages: Pair<Int, Int>, status: String? = null) =
        BattleSide(status = status).also { s -> for ((i, v) in stages) s.boosts[i] = v }

    private fun assertUseless(what: String, id: String, defenderSp: Int = WATER, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide()) {
        for (c in smart) assertEquals("$what ($c, Main)", 0, used(c, id, defenderSp, aSide, dSide))
        for (c in smart) assertEquals("$what ($c, wild)", 0, picksOfWildWith(c, id, defenderSp, aSide, dSide))
    }

    private fun assertUseful(what: String, id: String, defenderSp: Int = WATER, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide()) {
        for (c in smart) assertTrue("$what ($c, Main)", used(c, id, defenderSp, aSide, dSide) > 100)
        for (c in smart) assertTrue("$what ($c, wild)", picksOfWildWith(c, id, defenderSp, aSide, dSide) > 100)
    }

    /** The wild side's view of [used]: a wild Pokémon with Tackle + [id] against a Main of species [defenderSp]. */
    private fun picksOfWildWith(choice: MoveChoice, id: String, defenderSp: Int, aSide: BattleSide, dSide: BattleSide): Int {
        val wild = mon(ELECTRIC, 30, "tackle", id)
        return picks(choice, wild, mon(defenderSp, 30, "tackle"), aSide, dSide, isMain = false).getOrDefault(id, 0)
    }

    @Test fun aStatDropOnTheFoeIsUselessAtItsFloorAndUsefulAboveIt() {
        assertUseless("Growl at -6", "growl", dSide = side(0 to -6))
        assertUseful("Growl at -5", "growl", dSide = side(0 to -5))
        assertUseful("Growl at +6 (a drop still moves it)", "growl", dSide = side(0 to 6))
        assertUseful("Growl at 0", "growl")
    }

    @Test fun aSelfBoostIsUselessAtItsCeilingAndUsefulBelowIt() {
        assertUseless("Swords Dance at +6", "swordsdance", aSide = side(0 to 6))
        assertUseful("Swords Dance at +5 (one stage still moves)", "swordsdance", aSide = side(0 to 5))
        assertUseful("Swords Dance at -6 (a rise still moves it)", "swordsdance", aSide = side(0 to -6))
        assertUseless("Agility at +6", "agility", aSide = side(4 to 6))
        assertUseful("Agility at +4", "agility", aSide = side(4 to 4))
    }

    @Test fun accuracyAndEvasionStopAtTwo() {
        assertUseless("Sand Attack at -2", "sandattack", dSide = side(5 to -2))
        assertUseful("Sand Attack at -1", "sandattack", dSide = side(5 to -1))
        assertUseless("Sweet Scent at -2", "sweetscent", dSide = side(6 to -2))
        assertUseful("Sweet Scent at -1", "sweetscent", dSide = side(6 to -1))
        assertUseless("Double Team at +2", "doubleteam", aSide = side(6 to 2))
        assertUseful("Double Team at +1", "doubleteam", aSide = side(6 to 1))
        assertUseful("Double Team at -3 (from an older fight)", "doubleteam", aSide = side(6 to -3))
    }

    @Test fun randomStillAllowsTheStageBeyondTwo() {
        // the chooser's usefulness is a SMART rule, RANDOM keeps its blind pick
        assertTrue(used(MoveChoice.RANDOM, "sandattack", dSide = side(5 to -2)) > 500)
        assertTrue(used(MoveChoice.RANDOM, "doubleteam", aSide = side(6 to 4)) > 500)
    }

    @Test fun aMoveWithSeveralStageChangesIsUselessOnlyWhenAllOfThemAreAtTheirLimit() {
        assertUseless("Hone Claws with attack +6 and accuracy +2", "honeclaws", aSide = side(0 to 6, 5 to 2))
        assertUseful("Hone Claws with attack +6 and accuracy +1", "honeclaws", aSide = side(0 to 6, 5 to 1))
        assertUseful("Hone Claws with attack +5 and accuracy +2", "honeclaws", aSide = side(0 to 5, 5 to 2))
    }

    @Test fun aStatusMoveIsUselessOnATargetThatAlreadyHasAStatus() {
        assertUseful("Thunder Wave on a healthy target", "thunderwave")
        for (st in listOf("par", "brn", "psn", "tox", "slp", "frz")) {
            assertUseless("Thunder Wave on a $st target", "thunderwave", dSide = side(status = st))
            assertUseless("Hypnosis on a $st target", "hypnosis", dSide = side(status = st))
        }
        // the statused side is the target's, not the user's
        assertUseful("Thunder Wave while the user is statused", "thunderwave", aSide = side(status = "par"))
    }

    @Test fun aStatusMoveIsUselessOnATargetThatCannotHaveIt() {
        assertUseless("Thunder Wave on an Electric type", "thunderwave", defenderSp = ELECTRIC)
        assertUseless("Thunder Wave on a Ground type", "thunderwave", defenderSp = GROUND)
        assertUseful("Thunder Wave on a Water type", "thunderwave", defenderSp = WATER)
        assertUseless("Toxic on a Poison type", "toxic", defenderSp = GRASS_POISON)
        assertUseless("Toxic on a Steel type", "toxic", defenderSp = STEEL)
        assertUseful("Toxic on a Water type", "toxic", defenderSp = WATER)
        assertUseless("Will-O-Wisp on a Fire type", "willowisp", defenderSp = FIRE)
        assertUseful("Will-O-Wisp on a Water type", "willowisp", defenderSp = WATER)
        assertUseful("Hypnosis on an Electric type (sleep has no type immunity)", "hypnosis", defenderSp = ELECTRIC)
        assertUseless("Sleep Powder on a Grass type", "sleeppowder", defenderSp = GRASS_POISON)
        assertUseful("Sleep Powder on a Water type", "sleeppowder", defenderSp = WATER)
    }

    @Test fun aHealIsUselessAtFullHpAndUsefulBelowIt() {
        val full = StatCalc.maxHp(data, mon(ELECTRIC, 30))
        for (c in smart) {
            assertEquals("Recover at full HP ($c)", 0, used(c, "recover", hp = full))
            assertTrue("Recover one HP short ($c)", used(c, "recover", hp = full - 1) > 100)
            assertTrue("Recover at 1 HP ($c)", used(c, "recover", hp = 1) > 100)
        }
        // the wild one: its full HP can be an encounter value (a faster-start fight), not the species' maximum
        val wild = mon(ELECTRIC, 30, "tackle", "recover", uid = "wild")
        wild.hp = 20
        for (c in smart) {
            assertEquals("wild at its own full HP ($c)", 0, picks(c, wild, mon(WATER, 30, "tackle"), isMain = false, aMaxHp = 20).getOrDefault("recover", 0))
            assertTrue("wild below it ($c)", picks(c, wild, mon(WATER, 30, "tackle"), isMain = false, aMaxHp = 21).getOrDefault("recover", 0) > 100)
        }
    }

    @Test fun aMoveTheEngineDoesNotModelIsNeverUseful() {
        assertUseless("Rain Dance", "raindance")
        assertUseless("Splash", "splash")
        assertUseless("Mimic", "mimic")
    }

    // ---------------- SMART: immune targets, Struggle, wild fallback ----------------

    @Test fun smartNeverAttacksWithAMoveTheTargetIsImmuneToWhenAnotherIsLeft() {
        for (c in smart) {
            val p = picks(c, mon(ELECTRIC, 30, "tackle", "lick", "growl"), mon(GHOST, 30, "tackle"))
            assertEquals("$c Tackle against a Ghost", 0, p["tackle"] ?: 0)
            assertTrue("$c Lick", p.getValue("lick") > 2000)
        }
        // RANDOM is blind to it
        assertTrue(picks(MoveChoice.RANDOM, mon(ELECTRIC, 30, "tackle", "lick", "growl"), mon(GHOST, 30, "tackle")).getValue("tackle") > 500)
        // two of them immune (Ground against a Flying type, Normal against Ghost)
        val d = mon(16, 30, "tackle") // Normal / Flying: Earthquake does nothing to it
        for (c in smart) assertEquals(setOf("tackle"), picks(c, mon(ELECTRIC, 30, "tackle", "earthquake"), d).keys)
    }

    @Test fun theMainUsesStruggleInEveryModeWhenNothingCanHurtTheWildOne() {
        for (c in MoveChoice.entries) {
            assertEquals("$c: only an immune attack", setOf("struggle"), picks(c, mon(ELECTRIC, 30, "tackle", "growl"), mon(GHOST, 30, "tackle")).keys)
            assertEquals("$c: only status moves", setOf("struggle"), picks(c, mon(ELECTRIC, 30, "growl", "swordsdance"), mon(WATER, 30, "tackle")).keys)
        }
    }

    @Test fun aWildOneUsesAUsefulStatusMoveWhenItsAttacksAreAllImmune() {
        for (c in smart) {
            val p = picks(c, mon(ELECTRIC, 30, "tackle", "growl"), mon(GHOST, 30, "tackle"), isMain = false)
            assertEquals("$c", setOf("growl"), p.keys)
        }
    }

    @Test fun aWildOneWithNothingUsefulUsesARandomMoveOfAll() {
        for (c in smart) {
            // an immune attack and a status move that does nothing: both are "nothing left", so any of the two
            val p = picks(c, mon(ELECTRIC, 30, "tackle", "raindance"), mon(GHOST, 30, "tackle"), isMain = false)
            assertEquals("$c", setOf("tackle", "raindance"), p.keys)
            assertEquals("$c", 0.5, p.getValue("tackle") / 3000.0, 0.05)
            // a lone immune attack is used (nothing else is left)
            assertEquals(setOf("tackle"), picks(c, mon(ELECTRIC, 30, "tackle"), mon(GHOST, 30, "tackle"), isMain = false).keys)
            // all useless: growl at the floor and tackle against a ghost
            val floor = side(0 to -6)
            assertEquals(setOf("tackle", "growl"), picks(c, mon(ELECTRIC, 30, "tackle", "growl"), mon(GHOST, 30, "tackle"), dSide = floor, isMain = false).keys)
        }
    }

    @Test fun aWildOneWithOnlyStatusMovesUsesTheUsefulOnes() {
        for (c in smart) {
            val p = picks(c, mon(ELECTRIC, 30, "growl", "splash", "raindance"), mon(WATER, 30, "tackle"), isMain = false)
            assertEquals("$c", setOf("growl"), p.keys)
            // nothing useful at all (a Magikarp-like): random among its moves, as before
            assertEquals(setOf("splash"), picks(c, mon(ELECTRIC, 30, "splash"), mon(WATER, 30, "tackle"), isMain = false).keys)
        }
    }

    @Test fun anOneHitKoThatMustFailIsNotChosenWhileAnotherMoveIsLeft() {
        val target = mon(WATER, 40, "tackle")
        for (c in smart) {
            assertEquals("$c vs a higher level", setOf("tackle"), picks(c, mon(ELECTRIC, 30, "fissure", "tackle"), target).keys)
            assertEquals("$c alone: Struggle", setOf("struggle"), picks(c, mon(ELECTRIC, 30, "fissure", "growl"), target).keys)
            val lower = picks(c, mon(ELECTRIC, 30, "fissure", "tackle"), mon(WATER, 30, "tackle"))
            assertTrue("$c vs an equal level", lower.getOrDefault("fissure", 0) > 500)
        }
        assertTrue(picks(MoveChoice.RANDOM, mon(ELECTRIC, 30, "fissure", "tackle"), target).getValue("fissure") > 500)
    }

    @Test fun chargeMovesCountAsDamaging() {
        for (c in smart) {
            val p = picks(c, mon(ELECTRIC, 30, "solarbeam", "growl"), mon(WATER, 30, "tackle"), n = 5000)
            assertEquals("$c", 0.8, p.getValue("solarbeam") / 5000.0, 0.03)
        }
    }

    @Test fun anUnknownMoveListFallsBackToTackleInEveryMode() {
        for (c in MoveChoice.entries) assertEquals(setOf("tackle"), picks(c, mon(ELECTRIC, 30, "nonsense"), mon(WATER, 30, "tackle"), n = 50).keys)
    }

    // ---------------- both sides run it inside a round ----------------

    private data class Played(val moves: List<String>, val main: Mon, val wild: Mon, val battle: Battle)

    private fun wildRounds(choice: MoveChoice, wildMoves: List<String>, setup: (Battle) -> Unit = {}, rounds: Int = 150): Played {
        val used = ArrayList<String>()
        var last: Played? = null
        for (seed in 1..rounds) {
            val main = mon(ELECTRIC, 40, "tackle", uid = "main")
            val wild = mon(WATER, 20, *wildMoves.toTypedArray(), uid = "wild")
            val b = Battle(wild, Tier.NORMAL)
            setup(b)
            val ev = BattleEngine.playRound(data, b, main, 1.0, true, Random(seed), 1.0, false, choice)
            used += ev.filterIsInstance<GameEvent.Attack>().filter { !it.byPlayer }.map { it.moveId }
            last = Played(used, main, wild, b)
        }
        return last!!
    }

    @Test fun theWildOneUsesTheMoveChoiceToo() {
        val atkAtFloor = { b: Battle -> b.player.boosts[0] = -6 }
        assertTrue("RANDOM uses Growl", wildRounds(MoveChoice.RANDOM, listOf("growl", "tackle"), atkAtFloor).moves.contains("growl"))
        for (c in smart) {
            val moves = wildRounds(c, listOf("growl", "tackle"), atkAtFloor).moves
            assertEquals(150, moves.size)
            assertFalse("$c wild Growl against a Main at -6", moves.contains("growl"))
        }
    }

    @Test fun theMainUsesTheMoveChoiceInARound() {
        var tackle = 0
        for (seed in 1..120) {
            val main = mon(ELECTRIC, 40, "tackle", "lick", "growl", uid = "main")
            val b = Battle(mon(GHOST, 20, "tackle", uid = "wild"), Tier.NORMAL)
            val ev = BattleEngine.playRound(data, b, main, 1.0, false, Random(seed), 1.0, true, MoveChoice.SMART)
            tackle += ev.filterIsInstance<GameEvent.Attack>().count { it.byPlayer && it.moveId == "tackle" }
        }
        assertEquals(0, tackle)
    }

    @Test fun accuracyAndEvasionOfEitherSideStopAtMinusTwoAndTheMessageSaysSo() {
        for (c in smart) {
            val main = mon(ELECTRIC, 40, "tackle", uid = "main")
            val wild = mon(WATER, 20, "sandattack", uid = "wild")
            val b = Battle(wild, Tier.NORMAL)
            val ev = ArrayList<GameEvent>()
            repeat(8) { ev += BattleEngine.playRound(data, b, main, 1.0, true, Random(it), 1.0, false, c) }
            assertEquals("$c", -2, b.player.boosts[5])
            val limits = ev.filterIsInstance<GameEvent.Effect>().filter { it.kind == EffectKind.STAT_LIMIT }
            assertEquals("$c: the 3rd to 8th use are stopped", 6, limits.size)
            assertTrue(limits.all { it.text.endsWith("accuracy won't go any lower!") && it.stages == -1 })
            // evasion raised by the wild one itself
            val wild2 = mon(WATER, 20, "doubleteam", uid = "wild2")
            val b2 = Battle(wild2, Tier.NORMAL)
            repeat(8) { BattleEngine.playRound(data, b2, mon(ELECTRIC, 40, "tackle", uid = "main2"), 1.0, true, Random(it), 1.0, false, c) }
            assertEquals("$c", 2, b2.enemy.boosts[6])
        }
    }

    @Test fun otherStagesStillGoToSixWhateverTheChoice() {
        for (c in MoveChoice.entries) {
            val wild = mon(WATER, 20, "swordsdance", uid = "wild")
            val b = Battle(wild, Tier.NORMAL)
            val ev = ArrayList<GameEvent>()
            repeat(5) { ev += BattleEngine.playRound(data, b, mon(ELECTRIC, 40, "tackle", uid = "main"), 1.0, true, Random(it), 1.0, false, c) }
            assertEquals("$c", 6, b.enemy.boosts[0])
            assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.kind == EffectKind.STAT_LIMIT && it.text.endsWith("Attack won't go any higher!") })
        }
    }

    @Test fun aStageAboveTheCapIsNotPulledDownByAChangeInTheOtherDirection() {
        // an accuracy stage of -4 left over from a RANDOM fight: a rise goes to -3, not to the cap
        val main = mon(ELECTRIC, 40, "tackle", uid = "main")
        val wild = mon(WATER, 20, "honeclaws", uid = "wild")
        val b = Battle(wild, Tier.NORMAL)
        b.enemy.boosts[5] = -4
        BattleEngine.playRound(data, b, main, 1.0, true, Random(1), 1.0, false, MoveChoice.SMART)
        assertEquals(-3, b.enemy.boosts[5])
        // and a stage of +5 hit by a drop goes down by the drop only
        val b2 = Battle(mon(WATER, 20, "sandattack", uid = "w2"), Tier.NORMAL)
        b2.player.boosts[5] = 5
        BattleEngine.playRound(data, b2, main, 1.0, true, Random(1), 1.0, false, MoveChoice.SMART)
        assertEquals(4, b2.player.boosts[5])
    }

    // ---------------- SMARTEST ----------------

    private fun share(p: Map<String, Int>, id: String, n: Int) = p.getOrDefault(id, 0) / n.toDouble()

    @Test fun smartestPicksOnlyTheTopTwoAttacksWeightedSeventyThirty() {
        val n = 6000
        val p = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "thunderbolt", "vinewhip", "watergun"), mon(WATER, 30, "tackle"), n = n)
        assertEquals(setOf("thunderbolt", "vinewhip"), p.keys) // 90 STAB x2 and 45 x2; Tackle is 40, Water Gun resisted
        assertEquals(0.7, share(p, "thunderbolt", n), 0.03)
        assertEquals(0.3, share(p, "vinewhip", n), 0.03)
    }

    @Test fun smartestFollowsTheTypeMatchup() {
        val n = 6000
        val moves = arrayOf("tackle", "thunderbolt", "vinewhip", "watergun")
        // a Fire type: Thunderbolt (STAB) neutral, Water Gun super effective; Vine Whip resisted
        val fire = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, *moves), mon(FIRE, 30, "tackle"), n = n)
        assertEquals(setOf("thunderbolt", "watergun"), fire.keys)
        assertEquals(0.7, share(fire, "thunderbolt", n), 0.03)
        // a Ground type: Thunderbolt does nothing, so it is never picked; Vine Whip and Water Gun are super effective
        val ground = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, *moves), mon(GROUND, 30, "tackle"), n = n)
        assertEquals(setOf("vinewhip", "watergun"), ground.keys)
        assertFalse(ground.containsKey("thunderbolt"))
    }

    @Test fun smartestWithOneAttackUsesIt() {
        val only = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "thunderbolt"), mon(WATER, 30, "tackle"), n = 200)
        assertEquals(mapOf("thunderbolt" to 200), only)
        val withStatus = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "thunderbolt", "growl"), mon(WATER, 30, "tackle"), n = 5000)
        assertEquals(setOf("thunderbolt", "growl"), withStatus.keys)
        assertEquals(0.8, share(withStatus, "thunderbolt", 5000), 0.03)
    }

    @Test fun smartestKeepsTheStatusRulesOfSmart() {
        val atkAtFloor = side(0 to -6)
        val p = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "thunderbolt", "growl"), mon(WATER, 30, "tackle"), dSide = atkAtFloor)
        assertEquals(setOf("thunderbolt", "tackle"), p.keys)
        assertEquals(setOf("struggle"), picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "growl"), mon(GHOST, 30, "tackle")).keys)
        val wild = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "growl"), mon(GHOST, 30, "tackle"), isMain = false)
        assertEquals(setOf("growl"), wild.keys)
    }

    @Test fun smartestBreaksATieByChance() {
        val n = 4000
        val p = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "scratch"), mon(WATER, 30, "tackle"), n = n)
        assertEquals(0.5, share(p, "tackle", n), 0.04)
        assertEquals(0.5, share(p, "scratch", n), 0.04)
    }

    @Test fun smartestWeighsPowerTimesAccuracy() {
        // Big Miss: 120 power but 50 % accurate is still worth about 1.4 times a sure 40; Tackle and Scratch are equal and share the second place
        val n = 4000
        val p = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "tackle", "bigmiss", "scratch"), mon(WATER, 30, "tackle"), n = n)
        assertEquals(0.7, share(p, "bigmiss", n), 0.04)
        assertEquals(0.15, share(p, "tackle", n), 0.04)
        assertEquals(0.15, share(p, "scratch", n), 0.04)
    }

    // ---------------- the damage estimate ----------------

    private fun expected(move: String, attacker: Mon = mon(ELECTRIC, 30, move), defender: Mon = mon(PLAIN, 30, "tackle"),
                         a: BattleSide = BattleSide(), d: BattleSide = BattleSide()) =
        MoveChooser.expectedDamage(data, attacker, a, defender, d, data.move(move)!!)

    @Test fun theEstimateGivesStabAndTheTypeChartTheirFactors() {
        val tackle = expected("tackle"); val zap = expected("zap") // same power, the Electric one is the attacker's own type
        assertEquals(1.5, zap / tackle, 0.12)
        val wet = expected("watergun", defender = mon(FIRE, 30)) // Water vs Fire x2, no STAB
        val neutral = expected("watergun")
        assertEquals(2.0, wet / neutral, 0.2)
        assertEquals(0.0, expected("tackle", defender = mon(GHOST, 30)), 0.0)
    }

    @Test fun theEstimateCountsAccuracyAndStages() {
        val sure = expected("scratch"); val half = expected("bigmiss")
        assertTrue("120 power at 50 % is about 1.4 times a sure 40", half > sure * 1.2 && half < sure * 2.0)
        // the accuracy / evasion stages move the chance, both ways
        val missy = expected("bigmiss", a = side(5 to -2))
        assertTrue(missy < half)
        assertTrue(expected("bigmiss", a = side(5 to 6)) > half)
        assertTrue(expected("bigmiss", d = side(6 to 2)) < half)
    }

    @Test fun theEstimateGivesFixedDamageMovesTheirEngineValue() {
        val a = mon(ELECTRIC, 30, "seismictoss")
        assertEquals(30.0, expected("seismictoss", attacker = a), 0.0)                // the user's level
        assertEquals(40.0, expected("dragonrage", attacker = a), 0.0)                 // the fixed number
        val target = mon(PLAIN, 30, "tackle")
        assertEquals(target.hp / 2 * 0.9, expected("superfang", attacker = a, defender = target), 1e-9) // half its HP at 90 %
        assertEquals(target.hp * 0.30, expected("fissure", attacker = a, defender = target), 1e-9)      // OHKO: its HP at 30 % at equal level
        val lower = mon(PLAIN, 20)
        assertEquals(lower.hp * 0.40, expected("fissure", attacker = a, defender = lower), 1e-9) // 10 levels below = +10 %
        assertEquals(0.0, expected("seismictoss", attacker = a, defender = mon(GHOST, 30)), 0.0)    // Fighting vs Ghost: immune
    }

    @Test fun theEstimateCountsMultiHit() {
        val one = expected("onehit")
        assertEquals(2.0, expected("doublehit") / one, 0.01)
        val bullet = expected("bulletseed", defender = mon(PLAIN, 30, "tackle"))
        val single = MoveChooser.expectedDamage(data, mon(ELECTRIC, 30), BattleSide(), mon(PLAIN, 30), BattleSide(), data.move("bulletseed")!!.copy(multihit = null))
        assertEquals(3.1, bullet / single, 0.01)
        assertEquals(expected("blast"), expected("explosion"), 0.0) // the estimate is the damage; Smartest leaves the move out itself
    }

    @Test fun smartestLeavesOutAMoveThatKnocksOutItsUserWhileAnotherAttackIsLeft() {
        val p = picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "explosion", "thunderbolt", "tackle"), mon(WATER, 30, "tackle"), n = 4000)
        assertEquals(setOf("thunderbolt", "tackle"), p.keys)
        // when it is the only attack that hurts it is used (80 % of the time, with a useful Growl next to it)
        assertEquals(setOf("explosion", "growl"), picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "explosion", "growl"), mon(WATER, 30, "tackle"), n = 400).keys)
        assertEquals(setOf("explosion"), picks(MoveChoice.SMARTEST, mon(ELECTRIC, 30, "explosion"), mon(WATER, 30, "tackle"), n = 100).keys)
        // SMART only avoids useless moves, it does not look at what a move costs its user
        assertTrue(picks(MoveChoice.SMART, mon(ELECTRIC, 30, "explosion", "tackle"), mon(WATER, 30, "tackle")).getValue("explosion") > 1000)
    }

    @Test fun theEstimateDrawsNoRandomNumbers() {
        val r = Random(5); val twin = Random(5)
        MoveChooser.expectedDamage(data, mon(ELECTRIC, 30, "thunderbolt"), BattleSide(), mon(WATER, 30), BattleSide(), data.move("thunderbolt")!!)
        assertEquals(twin.nextInt(), r.nextInt())
    }

    // ---------------- saved with the game ----------------

    private fun state(choice: MoveChoice?): GameState {
        val s = TestData.state(TestData.world())
        if (choice != null) s.settings.moveChoice = choice
        return s
    }

    @Test fun eachChoiceRoundTripsThroughTheSave() {
        for (c in MoveChoice.entries) {
            val back = SaveCodec.decode(SaveCodec.encode(state(c)))
            assertEquals(c, back.settings.moveChoice)
            assertEquals(SaveCodec.encode(state(c)), SaveCodec.encode(back))
        }
    }

    @Test fun anOldSaveWithoutTheKeyLoadsAsSmartAndOtherwiseUnchanged() {
        val st = state(MoveChoice.RANDOM)
        st.settings.autoMode = 1; st.settings.trainerName = "Old Hand"; st.settings.dayStartHour = 7
        val json = SaveCodec.encode(st)
        assertTrue(json.contains("\"moveChoice\":\"RANDOM\""))
        val old = json.replace("\"moveChoice\":\"RANDOM\",", "")
        assertFalse(old.contains("moveChoice"))
        val back = SaveCodec.decode(old)
        assertEquals(MoveChoice.SMART, back.settings.moveChoice)
        assertEquals(1, back.settings.autoMode); assertEquals("Old Hand", back.settings.trainerName); assertEquals(7, back.settings.dayStartHour)
        assertEquals(st.mons.map { it.uid }, back.mons.map { it.uid })
        // and the same save with the setting changed back
        assertEquals(MoveChoice.RANDOM, SaveCodec.decode(json).settings.moveChoice)
    }

    @Test fun anUnknownSavedValueLoadsAsSmart() {
        val json = SaveCodec.encode(state(MoveChoice.SMARTEST)).replace("\"moveChoice\":\"SMARTEST\"", "\"moveChoice\":\"CLEVERER\"")
        assertEquals(MoveChoice.SMART, SaveCodec.decode(json).settings.moveChoice)
    }

    // ---------------- Game passes the setting to the round ----------------

    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** The Main's first move (by id) in the first round of a fresh fight against a Ghost, for 100 seeds. */
    private fun gameMoves(choice: MoveChoice): List<String> {
        val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val used = ArrayList<String>()
        for (seed in 1..100) {
            val st = TestData.state(data, 25, 20, listOf("tackle", "lick"))
            st.settings.moveChoice = choice
            val g = Game(data, st, Random(seed)) { now }
            st.battle = Battle(mon(GHOST, 15, "lick", uid = "wild"), Tier.NORMAL, startedAt = now)
            var card = 1000L + seed * 10
            val events = g.onAnswer(Answer(card++, 3, now)) + g.onAnswer(Answer(card++, 3, now))
            used += events.filterIsInstance<GameEvent.Attack>().filter { it.byPlayer }.map { it.moveId }
        }
        return used
    }

    @Test fun theGameRoundUsesTheSettingsMoveChoice() {
        val smartMoves = gameMoves(MoveChoice.SMART)
        assertEquals(100, smartMoves.size)
        assertEquals(setOf("lick"), smartMoves.toSet()) // never Tackle against a Ghost
        assertTrue(gameMoves(MoveChoice.RANDOM).contains("tackle"))
    }

    // ---------------- help ----------------

    @Test fun theBattleChapterExplainsTheMoveChoice() {
        val ch = GameGuide.chapter(GameGuide.BATTLES)!!
        val text = (listOf(ch.intro) + ch.points).joinToString(" ")
        assertTrue(text.contains("Move choice"))
        assertTrue(text.contains("yours and the wild one alike"))
        assertTrue(text.contains("${(Tuning.SMART_DAMAGE_SHARE * 100).toInt()}%"))
        assertTrue(text.contains("±${Tuning.SMART_ACC_EVA_LIMIT}"))
        assertTrue(text.contains("Random") && text.contains("Smartest"))
    }
}
