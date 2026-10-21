package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R13 Part F: "Total XP" counted XP the Pokémon never earned (the owned Pokémon's whole growth-table XP as a ratcheted floor) and
 * "Total levels" added up the owned levels. Now: XP gained / levels gained only, a one-time quiet fix of inflated saves, and the new
 * Total XP tiers. Made-up species only.
 */
class AchievementsLifetimeFixTest {
    private val data = TestData.world()
    private val NOW = 1_791_892_800_000L
    private val DAY = 24L * 60 * 60 * 1000

    private fun counter(s: GameState, m: Metric) = s.counters[m.name] ?: 0L
    private fun xpGained(events: List<GameEvent>) = events.filterIsInstance<GameEvent.XpGained>().sumOf { it.xp.toLong() }

    private fun game(): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.dailyAverage = 1000
        return Game(data, s, Random(1)) { NOW }
    }

    private fun Game.win(species: Int, level: Int, caught: Boolean): List<GameEvent> {
        if (!caught) state.caught += species else state.caught -= species
        state.battle = Battle(TestData.mon(data, species, level, listOf("tackle"), uid = "wild$species-$level").also { it.hp = 1 }, Tier.NORMAL, startedAt = NOW)
        onAnswer(Answer(1, 3, NOW))
        return onAnswer(Answer(2, 3, NOW))
    }

    @Test fun catchingALevel25AddsOnlyTheXpYourPokemonGained() {
        val g = game()
        val before = counter(g.state, Metric.TOTAL_XP)
        val ev = g.win(19, 25, caught = true)
        assertEquals(1, ev.filterIsInstance<GameEvent.Caught>().size)
        // the catch XP of the main only, never the ~15 600 XP (25^3) the caught one came with
        assertEquals(before + xpGained(ev), counter(g.state, Metric.TOTAL_XP))
        assertEquals(counter(g.state, Metric.TOTAL_XP), Achievements.value(Metric.TOTAL_XP, g.state, data))
        assertTrue(Achievements.value(Metric.TOTAL_XP, g.state, data) < 25L * 25 * 25)
        assertEquals("its levels are not gained", ev.count { it is GameEvent.LevelUp }.toLong(), Achievements.value(Metric.TOTAL_LEVELS, g.state, data))
    }

    @Test fun aDefeatThatGivesXAddsX() {
        val g = game()
        val ev = g.win(19, 12, caught = false)
        assertEquals(1, ev.filterIsInstance<GameEvent.Defeated>().size)
        val x = xpGained(ev)
        assertTrue(x > 0)
        assertEquals(x, Achievements.value(Metric.TOTAL_XP, g.state, data))
    }

    @Test fun theTotalXpTiersAreTheNewOnes() {
        assertEquals(listOf(10_000L, 100_000L, 1_000_000L, 3_000_000L, 10_000_000L), AchievementTable.row("total-xp")!!.tiers)
    }

    /** An inflated save: 1 000 000 XP ratcheted from the owned Pokémon, three "total-xp" tiers and two "total-levels" tiers reached by it. */
    private fun inflated(): GameState {
        val s = TestData.state(data, 25, 30, listOf("tackle"))
        s.starterUid = s.main!!.uid                     // the starter, Lv 5 -> 30: 25 levels really gained
        repeat(40) { s.mons += TestData.mon(data, 19, 25, uid = "c$it") }   // caught at Lv 25: 1 000 owned levels, none gained here
        s.counters[Metric.TOTAL_XP.name] = 1_000_000
        s.history["2026-10-01"] = DayLog(reviews = 100, xp = 30_000)
        s.history["2026-10-02"] = DayLog(reviews = 120, xp = 12_000)
        s.achTiers["total-xp"] = mutableListOf(NOW - 9 * DAY, NOW - 8 * DAY, NOW - 7 * DAY)
        s.achInferred += listOf("total-xp/0", "total-xp/2")
        s.achTiers["total-levels"] = mutableListOf(NOW - 9 * DAY, NOW - 8 * DAY)
        s.achLadder = 2
        return s
    }

    @Test fun anInflatedSaveIsRecomputedOnceAndItsExtraTiersAreLockedQuietly() {
        val s = inflated()
        val lines = ArrayList<String>()
        val events = mutableListOf<GameEvent>()
        Achievements.check(s, data, NOW, events, log = { lines += it })
        assertEquals(42_000L, counter(s, Metric.TOTAL_XP))          // the day history's XP
        assertEquals(25L, counter(s, Metric.TOTAL_LEVELS))          // the starter's levels after Lv 5
        assertEquals("10 000 is still reached; 100 000 and 1 000 000 are locked again", listOf(NOW - 9 * DAY), s.achTiers["total-xp"])
        assertEquals(setOf("total-xp/0"), s.achInferred.filter { it.startsWith("total-xp/") }.toSet())
        assertEquals(emptyList<Long>(), s.achTiers["total-levels"])
        assertTrue("no pop-up, no message", events.isEmpty())
        assertEquals(AchievementTable.LADDER, s.achLadder)
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("1000000 -> 42000") && lines.single().contains("locked again"))
        // once only: later XP adds on top and a second look changes nothing
        Achievements.count(s, listOf(GameEvent.XpGained("x", "X", 100, 30, 0, 1)))
        Achievements.check(s, data, NOW + DAY, events, log = { lines += it })
        assertEquals(42_100L, counter(s, Metric.TOTAL_XP))
        assertEquals(1, lines.size)
        assertTrue(events.isEmpty())
    }

    @Test fun theFixSurvivesASaveAndANewGameNeverNeedsIt() {
        val s = inflated()
        Achievements.onReady(s, data, NOW)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(42_000L, counter(back, Metric.TOTAL_XP))
        assertEquals(AchievementTable.LADDER, back.achLadder)
        assertFalse(Achievements.migrate(back))

        val fresh = GameState()
        assertEquals(AchievementTable.LADDER, fresh.achLadder)
        fresh.counters[Metric.TOTAL_XP.name] = 777
        assertFalse(Achievements.migrate(fresh))
        assertEquals(777L, counter(fresh, Metric.TOTAL_XP))
    }

    @Test fun aSaveWithNothingInflatedLosesNothing() {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.history["2026-10-01"] = DayLog(xp = 5_000)
        s.counters[Metric.TOTAL_XP.name] = 5_000
        s.achTiers["total-xp"] = mutableListOf()
        s.achLadder = 2
        val lines = ArrayList<String>()
        Achievements.check(s, data, NOW, mutableListOf(), log = { lines += it })
        assertEquals(5_000L, counter(s, Metric.TOTAL_XP))
        assertNull(s.counters[Metric.TOTAL_LEVELS.name])   // no starter known: nothing made up
        assertTrue(lines.single(), lines.single().endsWith("no tier locked again"))
    }
}
