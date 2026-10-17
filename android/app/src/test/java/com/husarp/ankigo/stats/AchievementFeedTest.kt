package com.husarp.ankigo.stats

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-07: the totals behind the Study time and New cards learned achievements, on made-up csv lines. */
class AchievementFeedTest {
    private val data = TestData.world()
    private val T0 = 1_791_892_800_000L

    // reviews.csv: epochMillis,nid,ord,ease,ms,source
    private fun rev(at: Long, ms: Long) = "$at,1,0,3,$ms,study"
    // live-time.csv: epochMillis,ms
    private fun live(at: Long, ms: Long) = "$at,$ms"

    @Test fun minutesAddBothFilesOverAllDays() {
        val reviews = sequenceOf(rev(T0, 30_000), rev(T0 + 86_400_000L * 3, 90_000)) // two different days
        val lives = sequenceOf(live(T0, 45_000), live(T0 + 86_400_000L * 9, 45_000))
        // 30 + 90 + 45 + 45 s = 210 s = 3 full minutes
        assertEquals(3L, AchievementFeedMath.studyMinutes(reviews, lives))
    }

    @Test fun oneAnswerCountsAtMostTheCap() {
        val cap = AnkiGoTime.MAX_ANSWER_MS
        val reviews = sequenceOf(rev(T0, cap * 10))
        val lives = sequenceOf(live(T0, LIVE_ANSWER_CAP_MS * 10))
        assertEquals((cap + LIVE_ANSWER_CAP_MS) / 60_000L, AchievementFeedMath.studyMinutes(reviews, lives))
    }

    @Test fun unreadableLinesAreSkipped() {
        val reviews = sequenceOf("", "garbage", "abc,1,0,3,5000,x", "$T0,1,0,3", rev(T0, 120_000), "$T0,1,0,3,12") // half-written last line
        val lives = sequenceOf("1,2,3", "x,5", rev(T0, 1).substringBefore(',') + ",-5", live(T0, 60_000))
        assertEquals(3L, AchievementFeedMath.studyMinutes(reviews, lives)) // 120 s + 60 s + 12 ms = 180 s
    }

    @Test fun noFilesMeansZeroMinutes() {
        assertEquals(0L, AchievementFeedMath.studyMinutes(emptySequence(), emptySequence()))
    }

    @Test fun newCardsUseOneLongWindowSearch() {
        val asked = ArrayList<String>()
        val n = AchievementFeedMath.newCardsAllTime { q -> asked += q; 1234 }
        assertEquals(1234L, n)
        assertEquals(listOf("introduced:36500"), asked)
    }

    @Test fun aFailingAnkiReadReachesTheCaller() {
        var thrown = false
        try { AchievementFeedMath.newCardsAllTime { throw SecurityException("no permission") } } catch (e: SecurityException) { thrown = true }
        assertTrue(thrown)
    }

    @Test fun newsOnlyWhenAReadingIsNewOrHigher() {
        val s = TestData.state(data)
        assertTrue(AchievementFeedMath.hasNews(s, 0, null)) // never fed
        AchievementFeedMath.feed(s, data, 90, 400, T0)
        assertFalse(AchievementFeedMath.hasNews(s, 90, 400))
        assertFalse(AchievementFeedMath.hasNews(s, 10, 100)) // lower
        assertFalse(AchievementFeedMath.hasNews(s, 90, null)) // AnkiDroid not read
        assertTrue(AchievementFeedMath.hasNews(s, 91, 400))
        assertTrue(AchievementFeedMath.hasNews(s, 90, 401))
    }

    @Test fun withoutAnkiDroidOnlyTheStudyTimeIsFed() {
        val s = TestData.state(data)
        val ev = AchievementFeedMath.feed(s, data, 75, null, T0)
        assertTrue(ev.isEmpty()) // first feed: silent
        assertEquals(75L, s.counters[Metric.STUDY_MINUTES.name])
        assertFalse(Metric.NEW_CARDS.name in s.counters)
        // the cards come later: still their first feed -> silent; then loud for later crossings
        assertTrue(AchievementFeedMath.feed(s, data, 75, 100, T0 + 1).isEmpty())
        val later = AchievementFeedMath.feed(s, data, 80, 600, T0 + 2)
        assertEquals(listOf("new-cards"), later.filterIsInstance<GameEvent.AchievementReached>().map { it.id })
    }

    @Test fun toastTextNamesTheTiers() {
        assertNull(AchievementFeedMath.toastText(emptyList()))
        val t = AchievementFeedMath.toastText(listOf(
            GameEvent.AchievementReached("study-time", 0, "Study time", "Common"),
            GameEvent.AchievementReached("new-cards", 1, "New cards learned", "Rare"),
        ))
        assertEquals("Achievement: Study time — Common, New cards learned — Rare", t)
    }
}
