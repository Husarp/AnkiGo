package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-07: the two external achievements (Study time in minutes, New cards learned) fed with Achievements.feed. Made-up species only. */
class AchievementsFeedTest {
    private val data = TestData.world()
    private val T0 = 1_791_892_800_000L

    private fun state() = TestData.state(data, 25, 20, listOf("tackle"))

    private fun feed(s: GameState, m: Metric, v: Long, now: Long = T0) = Achievements.feed(s, data, m, v, now)

    private fun reached(events: List<GameEvent>, id: String) =
        events.filterIsInstance<GameEvent.AchievementReached>().filter { it.id == id }

    @Test fun theTwoRowsExistWithTheProposalsThresholds() {
        val study = AchievementTable.row("study-time")!!
        assertEquals(Metric.STUDY_MINUTES, study.metric)
        assertEquals("Study", study.group)
        assertEquals(listOf(1L, 10L, 50L, 200L, 500L), study.tiers.map { it / 60 }) // 1 h, 10 h, 50 h, 200 h, 500 h in minutes
        assertTrue(study.tiers.all { it % 60 == 0L })
        val cards = AchievementTable.row("new-cards")!!
        assertEquals(Metric.NEW_CARDS, cards.metric)
        assertEquals(listOf(50L, 500L, 2000L, 5000L, 10000L), cards.tiers)
        for (r in listOf(study, cards)) assertTrue(r.id, r.tiers.zipWithNext().all { (a, b) -> a < b })
        // Study time sits after Days studied, in the Study group
        val ids = AchievementTable.ROWS.map { it.id }
        assertEquals(ids.indexOf("days-studied") + 1, ids.indexOf("study-time"))
    }

    @Test fun onlyTheExternalMetricsCanBeFed() {
        assertEquals(setOf(Metric.STUDY_MINUTES, Metric.NEW_CARDS), Metric.entries.filter { it.external }.toSet())
        assertThrows(IllegalArgumentException::class.java) { feed(state(), Metric.ANSWERS, 5) }
        assertThrows(IllegalArgumentException::class.java) { feed(state(), Metric.DAMAGE_DEALT, 5) }
    }

    @Test fun feedStoresTheValueAndKeepsTheMax() {
        val s = state()
        feed(s, Metric.STUDY_MINUTES, 90)
        assertEquals(90L, s.counters[Metric.STUDY_MINUTES.name])
        feed(s, Metric.STUDY_MINUTES, 40) // a smaller reading never lowers it
        assertEquals(90L, s.counters[Metric.STUDY_MINUTES.name])
        assertEquals(90L, Achievements.value(Metric.STUDY_MINUTES, s, data))
        feed(s, Metric.STUDY_MINUTES, 130)
        assertEquals(130L, Achievements.value(Metric.STUDY_MINUTES, s, data))
        feed(s, Metric.NEW_CARDS, -5)
        assertEquals(0L, s.counters[Metric.NEW_CARDS.name]) // never negative
    }

    @Test fun theFirstFeedIsSilentButDatesTheReachedTiers() {
        val s = state()
        val ev = feed(s, Metric.NEW_CARDS, 600) // already past tiers 1 and 2
        assertTrue(ev.isEmpty())
        assertEquals(listOf(T0, T0), s.achTiers["new-cards"])
        assertEquals(1, Achievements.view(s, data).first { it.id == "new-cards" }.tier)
    }

    @Test fun theFirstFeedIsSilentEvenAfterTheRowWasLookedAtEmpty() {
        val s = state()
        Achievements.check(s, data, T0 - 1000, mutableListOf()) // a normal answer looked at the row: empty list, value 0
        assertEquals(emptyList<Long>(), s.achTiers["study-time"])
        assertTrue(feed(s, Metric.STUDY_MINUTES, 700).isEmpty()) // still the metric's first feed
        assertEquals(2, s.achTiers["study-time"]!!.size)
    }

    @Test fun aLaterCrossingSendsOneEventPerTierInOrder() {
        val s = state()
        feed(s, Metric.STUDY_MINUTES, 30) // first: silent, nothing reached
        assertTrue(feed(s, Metric.STUDY_MINUTES, 59).isEmpty())
        val one = feed(s, Metric.STUDY_MINUTES, 60, T0 + 1)
        assertEquals(listOf(0), reached(one, "study-time").map { it.tier })
        assertEquals("Common", reached(one, "study-time")[0].tierName)
        assertEquals("Study time", reached(one, "study-time")[0].title)
        assertEquals(1, s.achTiers["study-time"]!!.size)
        assertTrue(feed(s, Metric.STUDY_MINUTES, 61, T0 + 2).isEmpty()) // no repeat
        val two = feed(s, Metric.STUDY_MINUTES, 3_000, T0 + 3) // jumps over tiers 1 and 2 at once
        assertEquals(listOf(1, 2), reached(two, "study-time").map { it.tier })
        assertEquals(listOf(T0 + 1, T0 + 3, T0 + 3), s.achTiers["study-time"])
    }

    @Test fun aLowerReadingNeverDropsATier() {
        val s = state()
        feed(s, Metric.NEW_CARDS, 10)
        feed(s, Metric.NEW_CARDS, 60, T0 + 1)
        assertTrue(feed(s, Metric.NEW_CARDS, 20, T0 + 2).isEmpty())
        val v = Achievements.view(s, data).first { it.id == "new-cards" }
        assertEquals(0, v.tier)
        assertEquals(60L, v.value)
        assertEquals(500L, v.next)
    }

    @Test fun hoursAndMinutesLineUp() {
        val s = state()
        feed(s, Metric.STUDY_MINUTES, 0)
        assertEquals(1, feed(s, Metric.STUDY_MINUTES, 1 * 60L).size)
        assertEquals(1, feed(s, Metric.STUDY_MINUTES, 10 * 60L).size)
        assertEquals(1, feed(s, Metric.STUDY_MINUTES, 50 * 60L).size)
        assertEquals(1, feed(s, Metric.STUDY_MINUTES, 200 * 60L).size)
        assertEquals(1, feed(s, Metric.STUDY_MINUTES, 500 * 60L).size)
        assertEquals(4, Achievements.view(s, data).first { it.id == "study-time" }.tier)
    }

    @Test fun theTwoCountersSurviveASaveRoundTrip() {
        val s = state()
        feed(s, Metric.STUDY_MINUTES, 125)
        feed(s, Metric.NEW_CARDS, 777)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(125L, back.counters[Metric.STUDY_MINUTES.name])
        assertEquals(777L, back.counters[Metric.NEW_CARDS.name])
        assertEquals(s.achTiers["new-cards"], back.achTiers["new-cards"])
        // a loaded save is not "first" again: the next crossing is loud
        assertFalse(feed(back, Metric.NEW_CARDS, 2_000, T0 + 5).isEmpty())
    }
}
