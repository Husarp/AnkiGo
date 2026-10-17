package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/** R3-03: the new per-day numbers (xp, cash, buttons), catches per ball, first seen and the chosen look. */
class StatsDataTest {
    private val data = TestData.world()
    private var now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private var nextCard = 1L
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun game(): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.cardsPerRound = 1
        return Game(data, s, Random(1)) { now }
    }

    private fun Game.fight(species: Int, hp: Int = 1): Mon {
        val w = TestData.mon(data, species, 12, listOf("tackle"), uid = "wild")
        w.hp = hp
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
        return w
    }

    private fun Game.answer(button: Int = 3) = onAnswer(Answer(nextCard++, button, now))

    private fun counted(button: Int) = GameEvent.Counted(button, 10, 1, 1, false)

    // ---- DayLog numbers ----

    @Test fun recordSumsXpOfEveryPokemonCashAndButtons() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(
            s,
            listOf(
                counted(3), counted(1), counted(4), counted(3), counted(0), GameEvent.Ignored("undo"),
                GameEvent.XpGained("main", "Sparkmouse", 40, 5, 10, 100), GameEvent.XpGained("share", "Birdie", 25, 3, 4, 50),
                GameEvent.Cash(12, 112), GameEvent.Cash(30, 142),
            ),
        )
        val d = s.history.getValue("2026-10-13")
        assertEquals(5, d.reviews)
        assertEquals(65, d.xp)
        assertEquals(42, d.cash)
        assertArrayEquals(intArrayOf(1, 1, 0, 2, 1), d.buttons)
        StudyStats.record(s, listOf(counted(2), GameEvent.Cash(8, 150)))
        assertEquals(50, d.cash)
        assertArrayEquals(intArrayOf(1, 1, 1, 2, 1), d.buttons)
    }

    @Test fun ignoredAnswersAddNothingToButtons() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, listOf(GameEvent.Ignored("Answer repeated after an undo")))
        assertTrue(s.history.isEmpty())
    }

    @Test fun aDayWithOnlyXpOrOnlyCashIsStillRecorded() {
        val a = GameState(day = "2026-10-13")
        StudyStats.record(a, listOf(GameEvent.XpGained("u", "Sparkmouse", 7, 5, 1, 50)))
        assertEquals(7, a.history.getValue("2026-10-13").xp)
        assertEquals(0, a.history.getValue("2026-10-13").reviews)
        val b = GameState(day = "2026-10-13")
        StudyStats.record(b, listOf(GameEvent.Cash(9, 9)))
        assertEquals(9, b.history.getValue("2026-10-13").cash)
    }

    @Test fun xpAndCashAndButtonsComeFromRealAnswers() {
        val g = game()
        val holder = TestData.mon(data, 16, 10, listOf("tackle"), uid = "holder")
        g.state.mons += holder
        g.state.settings.xpShareUid = "holder"
        g.state.caught += 19
        g.fight(19)
        val ev = g.answer(4)
        val d = g.state.history.getValue("2026-10-13")
        val xp = ev.filterIsInstance<GameEvent.XpGained>()
        assertTrue("main and holder both gained XP: $xp", xp.map { it.uid }.containsAll(listOf("main", "holder")))
        assertEquals(xp.sumOf { it.xp }, d.xp)
        assertEquals(ev.filterIsInstance<GameEvent.Cash>().sumOf { it.amount }, d.cash)
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 1), d.buttons)
        g.answer(1)
        assertArrayEquals(intArrayOf(0, 1, 0, 0, 1), g.state.history.getValue("2026-10-13").buttons)
    }

    @Test fun anAnswerIgnoredAfterAnUndoLeavesTheLogAlone() {
        val g = game()
        g.state.redo[7L] = 1
        g.onAnswer(Answer(7L, 3, now))
        assertTrue(g.state.history.isEmpty())
    }

    // ---- catches per ball ----

    @Test fun ballCatchesCountThrowCatchesPerBallAndAutoCatches() {
        val g = game()
        Economy.give(g.state, "master-ball", 3)
        g.fight(54, hp = 20)
        g.throwBall("master-ball")
        g.throwBall("master-ball")
        assertEquals(mapOf("master-ball" to 2), g.state.ballCatches)
        g.state.settings.autoMode = 1
        g.state.battle = null
        g.fight(19)
        g.answer()
        assertEquals(mapOf("master-ball" to 2, "auto" to 1), g.state.ballCatches)
    }

    @Test fun aMissedThrowAndADefeatAreNotCatches() {
        val g = game()
        g.state.settings.autoMode = 2
        g.state.caught += 19
        g.fight(19)
        g.answer() // defeated, not caught
        assertTrue(g.state.ballCatches.isEmpty())
        Economy.give(g.state, "poke-ball", 5)
        val miss = (0..1000).first { Random(it).nextInt(100) >= 8 }
        val g2 = Game(data, g.state, Random(miss)) { now }
        g2.fight(54, hp = 20)
        g2.throwBall("poke-ball")
        assertTrue(g2.state.ballCatches.isEmpty())
    }

    @Test fun aFossilRevivalIsNotACatch() {
        val d = GameData(data.species + (138 to TestData.species(138, "Spiralmon")), data.moves, data.growth, data.itemPrices)
        val s = TestData.state(d)
        Economy.give(s, "helix-fossil")
        assertTrue(ItemUse.use(d, s, "helix-fossil", null, TestData.rng(), 1234L).ok)
        assertTrue(s.mons.any { it.species == 138 })
        assertTrue(s.ballCatches.isEmpty())
    }

    // ---- first seen ----

    @Test fun seenAtIsSetOnceWhenASpeciesIsFirstMet() {
        val g = game()
        g.state.seen.clear()
        g.state.nextSeed = 5L
        now = 1_000L
        val firstMet = HashMap<Int, Long>()
        var repeats = 0
        repeat(60) {
            g.state.battle = null
            val sp = g.ensureEncounter().filterIsInstance<GameEvent.Encounter>().single().species
            if (sp in firstMet) repeats++ else firstMet[sp] = now
            now += 1_000L
        }
        assertTrue("a species was met again", repeats > 0)
        // met again later: the first date stays
        assertEquals(firstMet, g.state.seenAt)
        assertEquals(firstMet.keys, g.state.seen)
    }

    @Test fun anAlreadySeenSpeciesWithoutADateStaysUndated() {
        val s = TestData.state(data, 25, 20) // 25 is in seen but has no date
        s.markSeen(25, 5_000L)
        assertNull(s.seenAt[25])
        s.markSeen(16, 5_000L)
        assertEquals(5_000L, s.seenAt[16])
    }

    @Test fun pickStarterAndCatchesSetSeenAt() {
        val state = GameState(createdAt = 1L)
        val g = Game(data, state, Random(1)) { now }
        g.tick()
        now = 123_456L
        g.pickStarter(1)
        assertEquals(123_456L, state.seenAt[1])
        now = 777_000L
        val g2 = game()
        g2.state.seen -= 54
        g2.state.seenAt.remove(54)
        Economy.give(g2.state, "master-ball")
        g2.fight(54, hp = 20)
        g2.throwBall("master-ball")
        assertEquals(777_000L, g2.state.seenAt[54])
    }

    @Test fun fossilRevivalSetsSeenAt() {
        val d = GameData(data.species + (138 to TestData.species(138, "Spiralmon")), data.moves, data.growth, data.itemPrices)
        val s = TestData.state(d)
        Economy.give(s, "helix-fossil")
        ItemUse.use(d, s, "helix-fossil", null, TestData.rng(), 4_242L)
        assertEquals(4_242L, s.seenAt[138])
    }

    @Test fun firstSeenPrefersSeenAtThenTheEarliestCatchThenNull() {
        val s = TestData.state(data, 25, 20)
        assertNull(s.firstSeen(99)) // never met
        assertNull(s.firstSeen(25)) // main has caughtAt 0: no date
        s.mons += TestData.mon(data, 25, 5, uid = "a").also { it.caughtAt = 3_000L }
        s.mons += TestData.mon(data, 25, 6, uid = "b").also { it.caughtAt = 2_000L }
        s.mons += TestData.mon(data, 25, 7, uid = "c").also { it.caughtAt = 0L }
        assertEquals(2_000L, s.firstSeen(25))
        s.seenAt[25] = 1_500L
        assertEquals(1_500L, s.firstSeen(25))
        assertFalse(s.firstSeen(16) != null)
    }

    // ---- chosen look ----

    @Test fun choicesAreTheLineUpToTheCurrentSpeciesOldestFirst() {
        fun nums(sp: Int) = Looks.choices(data, TestData.mon(data, sp, 5)).map { it.num }
        assertEquals(listOf(1), nums(1))
        assertEquals(listOf(1, 2), nums(2))
        assertEquals(listOf(1, 2, 3), nums(3))
        assertEquals(listOf(172, 25), nums(25)) // a baby and its evolution
        assertEquals(listOf(133, 135), nums(135)) // a branching line only follows the way back
        assertEquals(listOf(16), nums(16))
    }

    @Test fun choicesFollowTheRegionalPreform() {
        val regional = TestData.species(10001, "Pre-Regional").copy(form = "Alola", baseNum = 19)
        val next = TestData.species(10002, "Reg-Final", prevo = 10001).copy(form = "Alola", baseNum = 20)
        val d = TestData.data(data.species.values.toList() + regional + next)
        assertEquals(listOf(10001, 10002), Looks.choices(d, TestData.mon(d, 10002, 5)).map { it.num })
    }

    @Test fun anEndlessPrevoLoopStillEnds() {
        val a = TestData.species(2001, "Loop-A", prevo = 2002)
        val b = TestData.species(2002, "Loop-B", prevo = 2001)
        val d = TestData.data(data.species.values.toList() + a + b)
        assertEquals(listOf(2002, 2001), Looks.choices(d, TestData.mon(d, 2001, 5)).map { it.num })
    }

    @Test fun setLookAcceptsALineSpeciesAndResets() {
        val g = game()
        val mon = TestData.mon(data, 3, 40, uid = "evo3")
        g.state.mons += mon
        val ok = g.setLook("evo3", 1)
        assertTrue(ok.ok); assertEquals("Leafking now looks like Leafling.", ok.message)
        assertEquals(1, mon.look)
        assertEquals(1, mon.lookSpecies(data).num)
        assertEquals(3, mon.species) // species, name and stats stay
        val reset = g.setLook("evo3", null)
        assertTrue(reset.ok); assertEquals("Leafking shows its own look again.", reset.message)
        assertNull(mon.look)
        assertEquals(3, mon.lookSpecies(data).num)
        // picking its own current species is the same as "current"
        g.setLook("evo3", 2)
        assertEquals(2, mon.look)
        assertTrue(g.setLook("evo3", 3).ok)
        assertNull(mon.look)
    }

    @Test fun setLookRefusesAnOutsideSpeciesOrUnknownUid() {
        val g = game()
        val mon = TestData.mon(data, 2, 40, uid = "evo2")
        g.state.mons += mon
        assertFalse(g.setLook("evo2", 3).ok) // a later stage is not a choice
        assertFalse(g.setLook("evo2", 16).ok)
        assertFalse(g.setLook("evo2", 99999).ok)
        assertNull(mon.look)
        val r = g.setLook("nope", 1)
        assertFalse(r.ok); assertEquals("Pokémon not found.", r.message)
    }

    @Test fun lookSpeciesFallsBackWhenTheLookIsNoLongerAChoice() {
        val mon = TestData.mon(data, 2, 40, uid = "x")
        mon.look = 3
        assertEquals(2, mon.lookSpecies(data).num)
        mon.look = 12345
        assertEquals(2, mon.lookSpecies(data).num)
        mon.look = 1
        assertEquals(1, mon.lookSpecies(data).num)
    }

    @Test fun aChosenLookSurvivesEvolutionAndNeverTouchesStats() {
        val mon = TestData.mon(data, 1, 10, uid = "y")
        val before = StatCalc.stats(data, mon)
        mon.look = 1
        assertEquals(before, StatCalc.stats(data, mon))
        mon.species = 2; mon.name = "Leafbud"
        assertEquals(1, mon.lookSpecies(data).num) // still the old look after evolving
        assertTrue(Looks.choices(data, mon).map { it.num }.containsAll(listOf(1, 2)))
    }
}
