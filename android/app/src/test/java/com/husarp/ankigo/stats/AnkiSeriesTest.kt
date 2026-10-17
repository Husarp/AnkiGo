package com.husarp.ankigo.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// TICKET R3-04: the on-demand AnkiDroid reads (deck filter) and their cache.
class AnkiSeriesTest {
    private val today = "2026-10-14"

    private class Fake(val answers: (String) -> Int = { 0 }) {
        val asked = ArrayList<String>()
        fun count(q: String): Int { asked += q; return answers(q) }
        fun reader() = AnkiSeriesReader { count(it) }
    }

    @Test fun deckNamesAreQuotedAndEscaped() {
        assertEquals("deck:\"Spanish\" prop:rated=-2", AnkiSeriesQueries.reviewsOn("Spanish", 2))
        assertEquals("deck:\"Lang::Spanish\" introduced:5", AnkiSeriesQueries.introducedIn("Lang::Spanish", 5))
        assertEquals("deck:\"A\\\\B \\\"x\\\" \\*\\_\" prop:due=3", AnkiSeriesQueries.dueIn("A\\B \"x\" *_", 3))
        assertEquals("prop:rated=0", AnkiSeriesQueries.reviewsOn(null, 0))
        assertEquals("prop:rated=0", AnkiSeriesQueries.reviewsOn("  ", 0))
        assertEquals("rated:7 -introduced:7", AnkiSeriesQueries.retentionAnswered(7))
        assertEquals("rated:365:1 -introduced:365", AnkiSeriesQueries.retentionAgain(365))
        assertEquals(AnkiQueries.RETENTION_ANSWERED, AnkiSeriesQueries.retentionAnswered(30))
        assertEquals(AnkiQueries.RETENTION_AGAIN, AnkiSeriesQueries.retentionAgain(30))
    }

    @Test fun reviewsReadsOneSearchPerDay() {
        val f = Fake { q -> if (q == "deck:\"D\" prop:rated=-1") 9 else 1 }
        val d = f.reader().read(SeriesKey("D", ChartMetric.REVIEWS, 7, today), 5L)
        assertEquals(7, d.perDay.size)
        assertEquals(9, d.perDay["2026-10-13"]); assertEquals(1, d.perDay["2026-10-14"]); assertEquals(1, d.perDay["2026-10-08"])
        assertEquals(7, f.asked.size); assertEquals(7, f.asked.toSet().size)
        assertEquals(5L, d.fetchedAt)
    }

    @Test fun newCardsAreTheDifferenceOfTheCumulativeCounts() {
        // introduced:j = cumulative; day 0 (today) 3, yesterday 2, two days ago 0 (a day of no new cards), then 5
        val cum = mapOf(1 to 3, 2 to 5, 3 to 5, 4 to 10)
        val f = Fake { q -> cum[q.removePrefix("introduced:").toInt()] ?: 10 }
        val d = f.reader().read(SeriesKey(null, ChartMetric.NEW_CARDS, 4, today), 0)
        assertEquals(mapOf("2026-10-14" to 3, "2026-10-13" to 2, "2026-10-12" to 0, "2026-10-11" to 5), d.perDay)
        assertEquals(listOf("introduced:1", "introduced:2", "introduced:3", "introduced:4"), f.asked)
    }

    @Test fun newCardsNeverGoBelowZeroAndKeepTheDeckFilter() {
        val f = Fake { q -> if (q.endsWith("introduced:1")) 5 else 3 } // a sync made the cumulative count drop
        val d = f.reader().read(SeriesKey("X", ChartMetric.NEW_CARDS, 2, today), 0)
        assertEquals(mapOf("2026-10-14" to 5, "2026-10-13" to 0), d.perDay)
        assertTrue(f.asked.all { it.startsWith("deck:\"X\" ") })
    }

    @Test fun dueReadsTomorrowOnwards() {
        val f = Fake { q -> q.substringAfterLast('=').toInt() * 10 }
        val d = f.reader().read(SeriesKey("D", ChartMetric.DUE, 3, today), 0)
        assertEquals(mapOf("2026-10-15" to 10, "2026-10-16" to 20, "2026-10-17" to 30), d.perDay)
        assertEquals("deck:\"D\" prop:due=1", f.asked[0])
    }

    @Test fun retentionWindowReadsTwoSearches() {
        val f = Fake { q -> if (q.contains(":1 ")) 4 else 40 }
        val d = f.reader().read(SeriesKey(null, ChartMetric.RETENTION, 7, today), 0)
        assertEquals(mapOf(AnkiSeriesReader.ANSWERED to 40, AnkiSeriesReader.AGAIN to 4), d.perDay)
        assertEquals(listOf("rated:7 -introduced:7", "rated:7:1 -introduced:7"), f.asked)
    }

    @Test fun gameMetricsReadNothing() {
        val f = Fake()
        assertTrue(f.reader().read(SeriesKey(null, ChartMetric.XP, 7, today), 0).perDay.isEmpty())
        assertTrue(f.asked.isEmpty())
    }

    @Test fun aFailureGoesToTheCaller() {
        try {
            AnkiSeriesReader { throw IllegalArgumentException("Invalid Anki search") }.read(SeriesKey("D", ChartMetric.REVIEWS, 7, today), 0)
            org.junit.Assert.fail()
        } catch (e: IllegalArgumentException) { assertEquals("Invalid Anki search", e.message) }
    }

    // ---- cache ----

    @Test fun cacheReusesTheSameKeyForAMinuteOnly() {
        val key = SeriesKey("D", ChartMetric.REVIEWS, 30, today)
        val c = AnkiSeriesCache.put(emptyMap(), key, SeriesData(emptyMap(), 1_000))
        assertTrue(AnkiSeriesCache.fresh(c, key, 1_000 + AnkiSeriesCache.MAX_AGE_MS))
        assertFalse(AnkiSeriesCache.fresh(c, key, 1_001 + AnkiSeriesCache.MAX_AGE_MS))
        assertFalse(AnkiSeriesCache.fresh(c, key, 999)) // the clock went back: read again
        assertFalse(AnkiSeriesCache.fresh(c, key.copy(deck = "E"), 1_000))
        assertFalse(AnkiSeriesCache.fresh(c, key.copy(metric = ChartMetric.DUE), 1_000))
        assertFalse(AnkiSeriesCache.fresh(c, key.copy(days = 7), 1_000))
        assertNull(c[key.copy(today = "2026-10-15")])
    }

    @Test fun cacheDropsReadsOfOtherDays() {
        val a = SeriesKey("D", ChartMetric.REVIEWS, 7, today)
        val b = SeriesKey("D", ChartMetric.DUE, 7, today)
        val next = a.copy(today = "2026-10-15")
        var c = AnkiSeriesCache.put(emptyMap(), a, SeriesData(emptyMap(), 1))
        c = AnkiSeriesCache.put(c, b, SeriesData(emptyMap(), 2))
        assertEquals(setOf(a, b), c.keys)
        c = AnkiSeriesCache.put(c, next, SeriesData(emptyMap(), 3))
        assertEquals(setOf(next), c.keys)
    }
}
