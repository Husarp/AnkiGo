package com.husarp.ankigo.stats

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

// TICKET L2-07: the searches, the QUICK and HISTORY passes with a fake count function, retention, the cache JSON.
class AnkiStatsTest {
    private val today = "2026-10-14"
    private val now = ZonedDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val day = 24 * 3_600_000L

    /** A fake `count`: records every search and answers from [answers] (0 for the rest). */
    private class Fake(val answers: (String) -> Int = { 0 }) {
        val asked = ArrayList<String>()
        fun count(q: String): Int { asked += q; return answers(q) }
        fun reader() = AnkiStatsReader { count(it) }
    }

    private fun date(k: Int, from: String = today) = LocalDate.parse(from).minusDays(k.toLong()).toString()

    /** A finished, up-to-date result: all 365 days known (day k studied k+1 cards), history checked at [checkedAt]. */
    private fun full(checkedAt: Long = now - day, pkg: String = "com.ichi2.anki", on: String = today): AnkiStats =
        AnkiStatsReader { 0 }.quick(null, pkg, on, now).copy(
            perDay = (0 until 365).associate { date(it, on) to it + 1 },
            historyCheckedAt = checkedAt,
        )

    // ---- the searches ----

    @Test fun searchesAreSpelledAsInTheAnkiManual() {
        assertEquals("prop:rated=0", AnkiQueries.ratedOn(0))
        assertEquals("prop:rated=-1", AnkiQueries.ratedOn(1))
        assertEquals("prop:rated=-364", AnkiQueries.ratedOn(364))
        assertEquals("rated:30", AnkiQueries.rated(30))
        assertEquals("rated:7:3", AnkiQueries.rated(7, 3))
        assertEquals("introduced:365", AnkiQueries.introduced(365))
        assertEquals("prop:due=1", AnkiQueries.due(1))
        assertEquals("is:due", AnkiQueries.DUE_NOW)
        assertEquals("is:new -is:suspended -is:buried", AnkiQueries.NEW)
        assertEquals("is:learn -is:review -is:suspended -is:buried", AnkiQueries.LEARNING)
        assertEquals("is:learn is:review -is:suspended -is:buried", AnkiQueries.RELEARNING)
        assertEquals("is:review -is:learn prop:ivl<21 -is:suspended -is:buried", AnkiQueries.YOUNG)
        assertEquals("is:review -is:learn prop:ivl>=21 -is:suspended -is:buried", AnkiQueries.MATURE)
        assertEquals("is:suspended", AnkiQueries.SUSPENDED)
        assertEquals("is:buried -is:suspended", AnkiQueries.BURIED)
        assertEquals("rated:30 -introduced:30", AnkiQueries.RETENTION_ANSWERED)
        assertEquals("rated:30:1 -introduced:30", AnkiQueries.RETENTION_AGAIN)
    }

    // ---- QUICK pass ----

    @Test fun quickPassAsksExactlyTheseSearchesOnce() {
        val f = Fake()
        f.reader().quick(null, "com.ichi2.anki", today, now)
        val expected = buildList {
            for (k in 0..6) add(if (k == 0) "prop:rated=0" else "prop:rated=-$k")
            add("is:new -is:suspended -is:buried"); add("is:learn -is:review -is:suspended -is:buried")
            add("is:learn is:review -is:suspended -is:buried"); add("is:review -is:learn prop:ivl<21 -is:suspended -is:buried")
            add("is:review -is:learn prop:ivl>=21 -is:suspended -is:buried"); add("is:suspended"); add("is:buried -is:suspended")
            add("is:due")
            for (d in listOf(1, 7, 30, 365)) for (b in 1..4) add("rated:$d:$b")
            for (d in listOf(1, 7, 30, 365)) add("introduced:$d")
            add("rated:30 -introduced:30"); add("rated:30:1 -introduced:30")
            for (k in 1..30) add("prop:due=$k")
        }
        assertEquals(expected.sorted(), f.asked.sorted())
        assertEquals(f.asked.size, f.asked.toSet().size) // nothing is asked twice
        assertEquals(67, f.asked.size)
    }

    @Test fun quickPassReadsTheNumbersIntoTheRightPlaces() {
        val answers = mapOf(
            "prop:rated=0" to 86, "prop:rated=-1" to 120, "prop:rated=-6" to 7,
            "is:new -is:suspended -is:buried" to 900, "is:learn -is:review -is:suspended -is:buried" to 4,
            "is:learn is:review -is:suspended -is:buried" to 2, "is:review -is:learn prop:ivl<21 -is:suspended -is:buried" to 300,
            "is:review -is:learn prop:ivl>=21 -is:suspended -is:buried" to 2500, "is:suspended" to 15, "is:buried -is:suspended" to 3,
            "is:due" to 61, "rated:30:1" to 40, "rated:30:2" to 90, "rated:30:3" to 700, "rated:30:4" to 120, "rated:1:3" to 50,
            "introduced:1" to 12, "introduced:7" to 70, "introduced:30" to 250, "introduced:365" to 900,
            "rated:30 -introduced:30" to 800, "rated:30:1 -introduced:30" to 80, "prop:due=1" to 55, "prop:due=2" to 45, "prop:due=30" to 9,
        )
        val s = Fake { answers[it] ?: 0 }.reader().quick(null, "com.ichi2.anki", today, now)
        assertEquals(86, s.studied(today)); assertEquals(120, s.studied("2026-10-13")); assertEquals(7, s.studied("2026-10-08"))
        assertEquals(Counts(900, 4, 2, 300, 2500, 15, 3), s.counts)
        assertEquals(61, s.dueNow)
        assertEquals(listOf(40, 90, 700, 120), s.buttonsIn(30)); assertEquals(listOf(0, 0, 50, 0), s.buttonsIn(1))
        assertEquals(12, s.newIn(1)); assertEquals(900, s.newIn(365))
        assertEquals(30, s.forecast.size); assertEquals(55, s.dueTomorrow); assertEquals(9, s.forecast[29])
        assertEquals(55 + 45, s.dueNext7)
        assertEquals(800, s.retentionAnswered); assertEquals(80, s.retentionAgain); assertEquals(90, s.retention30)
        assertEquals(now, s.fetchedAt); assertEquals(today, s.today); assertEquals(0L, s.historyCheckedAt)
    }

    @Test fun quickPassMapsPropRatedDaysToTheDatesBeforeTheGameDay() {
        val s = Fake { if (it.startsWith("prop:rated=")) 100 + it.substringAfter('=').toInt() else 0 }
            .reader().quick(null, "p", "2026-03-01", now) // a month boundary on purpose
        assertEquals(setOf("2026-03-01", "2026-02-28", "2026-02-27", "2026-02-26", "2026-02-25", "2026-02-24", "2026-02-23"), s.perDay.keys)
        assertEquals(100, s.studied("2026-03-01")); assertEquals(99, s.studied("2026-02-28")); assertEquals(94, s.studied("2026-02-23"))
    }

    @Test fun quickPassKeepsTheHistoryOfTheSamePackageAndRefreshesTheLastSevenDays() {
        val prev = full()
        val s = Fake { if (it == "prop:rated=-1") 777 else 0 }.reader().quick(prev, "com.ichi2.anki", today, now)
        assertEquals(365, s.perDay.size)
        assertEquals(777, s.studied(date(1)))               // re-read (a sync may have added reviews)
        assertEquals(0, s.studied(date(0)))                 // re-read
        assertEquals(8, s.studied(date(7)))                 // kept
        assertEquals(365, s.studied(date(364)))             // kept
        assertEquals(prev.historyCheckedAt, s.historyCheckedAt)
    }

    @Test fun quickPassTrimsDaysOlderThan365AndFutureDays() {
        val prev = full().copy(perDay = full().perDay + mapOf(date(365) to 5, date(400) to 6, "2026-10-20" to 9))
        val s = Fake().reader().quick(prev, "com.ichi2.anki", today, now)
        assertEquals(365, s.perDay.size)
        assertTrue(date(364) in s.perDay)
        assertFalse(date(365) in s.perDay); assertFalse(date(400) in s.perDay); assertFalse("2026-10-20" in s.perDay)
    }

    @Test fun quickPassDropsHistoryFromAnotherAnkiDroidPackage() {
        val s = Fake().reader().quick(full(pkg = "com.ichi2.anki"), "com.ichi2.anki.A", today, now)
        assertEquals(7, s.perDay.size)
        assertEquals("com.ichi2.anki.A", s.pkg)
        assertEquals(0L, s.historyCheckedAt)
    }

    // ---- HISTORY pass ----

    @Test fun firstHistoryPassReadsDays7To364RecentFirstAndStampsTheEnd() {
        val f = Fake { if (it.startsWith("prop:rated=")) 5 else 0 }
        val reader = f.reader()
        val quick = reader.quick(null, "p", today, now)
        assertTrue(reader.historyNeeded(quick, now))
        f.asked.clear()
        val progress = ArrayList<AnkiStats>()
        val done = reader.history(quick, now + 1000) { progress += it }
        assertEquals((7..364).map { if (it == 0) "prop:rated=0" else "prop:rated=-$it" }, f.asked)
        assertEquals(358, f.asked.size)
        assertEquals(365, done.perDay.size)
        assertEquals(now + 1000, done.historyCheckedAt)
        assertEquals(8, progress.size)                       // after 40, 80 ... 320 days; the end is the return value
        assertTrue(progress.all { it.historyCheckedAt == 0L })
        assertEquals(7 + 40, progress[0].perDay.size)        // the recent days arrive first
        assertTrue(date(46) in progress[0].perDay); assertFalse(date(47) in progress[0].perDay)
        assertEquals(7 + 320, progress.last().perDay.size)
    }

    @Test fun anInterruptedHistoryPassResumesWithTheMissingDaysOnly() {
        val reader = Fake().reader()
        val partial = reader.quick(null, "p", today, now).let { q -> q.copy(perDay = q.perDay + (7 until 100).associate { date(it) to 3 }) }
        assertTrue(reader.historyNeeded(partial, now)) // days 100..364 are missing
        val f = Fake(); val done = f.reader().history(partial, now)
        assertEquals((100..364).map { "prop:rated=-$it" }, f.asked)
        assertEquals(365, done.perDay.size)
    }

    @Test fun historyIsNotNeededWhenEverythingIsKnownAndRecent() {
        val reader = Fake().reader()
        assertFalse(reader.historyNeeded(full(checkedAt = now - day), now))
        assertFalse(reader.historyNeeded(full(checkedAt = now - 7 * day), now))   // exactly a week is not over a week
        assertTrue(reader.historyNeeded(full(checkedAt = now - 7 * day - 1), now))
        assertTrue(reader.historyNeeded(full(checkedAt = 0L), now))               // never stamped: finish it
    }

    @Test fun incrementalHistoryReadsOnlyTheMissingDays() {
        val prev = full()
        val gaps = prev.copy(perDay = prev.perDay - date(10) - date(200))
        val f = Fake { 42 }
        val reader = f.reader()
        assertTrue(reader.historyNeeded(gaps, now))
        val done = reader.history(gaps, now)
        assertEquals(listOf("prop:rated=-10", "prop:rated=-200"), f.asked)
        assertEquals(42, done.studied(date(10))); assertEquals(8, done.studied(date(7)))
    }

    @Test fun aNewDayNeedsNoHistoryBecauseTheQuickPassAlreadyReadItsNeighbours() {
        val tomorrow = "2026-10-15"
        val reader = Fake().reader()
        val q = reader.quick(full(), "com.ichi2.anki", tomorrow, now + day)
        assertEquals(365, q.perDay.size)
        assertFalse(reader.historyNeeded(q, now + day))
        assertTrue("2025-10-16" in q.perDay)                          // the new oldest day
        assertFalse("2025-10-15" in q.perDay)                         // yesterday's oldest day fell out
    }

    @Test fun afterAWeekTheWholeHistoryIsReadAgainAndTheValuesReplaced() {
        val old = full(checkedAt = now - 8 * day)
        val f = Fake { 1 }
        val reader = f.reader()
        assertTrue(reader.historyNeeded(old, now))
        val done = reader.history(old, now)
        assertEquals(358, f.asked.size)
        assertEquals(1, done.studied(date(100)))
        assertEquals(now, done.historyCheckedAt)
    }

    @Test fun aChangedPackageReadsEverythingAgain() {
        val f = Fake { 2 }
        val reader = f.reader()
        val q = reader.quick(full(pkg = "com.ichi2.anki"), "com.ichi2.anki.debug", today, now)
        f.asked.clear()
        val done = reader.history(q, now)
        assertEquals(358, f.asked.size)
        assertEquals(365, done.perDay.size)
    }

    // ---- the whole refresh ----

    @Test fun refreshPublishesQuickThenProgressThenTheFinalResult() {
        val got = ArrayList<Pair<AnkiStats, Boolean>>()
        val result = Fake { 1 }.reader().refresh(null, "p", today, { now }) { s, running -> got += s to running }
        assertEquals(1 + 8 + 1, got.size)
        assertEquals(7, got[0].first.perDay.size); assertTrue(got[0].second)       // quick: history will run
        assertTrue(got.subList(1, 9).all { it.second && it.first.historyCheckedAt == 0L })
        assertFalse(got.last().second); assertEquals(now, got.last().first.historyCheckedAt)
        assertEquals(got.last().first, result)
    }

    @Test fun refreshWithNothingToCatchUpPublishesOnlyTheQuickResult() {
        val f = Fake()
        val got = ArrayList<Pair<AnkiStats, Boolean>>()
        f.reader().refresh(full(), "com.ichi2.anki", today, { now }) { s, running -> got += s to running }
        assertEquals(1, got.size); assertFalse(got[0].second)
        assertEquals(67, f.asked.size)
    }

    @Test fun aFailingSearchInTheHistoryPassKeepsTheQuickResultPublished() {
        var calls = 0
        val got = ArrayList<AnkiStats>()
        try {
            AnkiStatsReader { if (++calls > 67 + 10) throw IllegalArgumentException("Invalid Anki search") else 1 }
                .refresh(null, "p", today, { now }) { s, _ -> got += s }
            fail("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals(1, got.size)
            assertEquals(7, got[0].perDay.size)
        }
    }

    // ---- retention ----

    @Test fun retentionIsTheShareOfAnsweredCardsThatWereNotAgain() {
        assertEquals(90, AnkiStats.retentionPercent(200, 20))
        assertEquals(100, AnkiStats.retentionPercent(50, 0))
        assertEquals(0, AnkiStats.retentionPercent(10, 10))
        assertEquals(67, AnkiStats.retentionPercent(3, 1))     // 66.67 rounds up
        assertEquals(0, AnkiStats.retentionPercent(10, 99))    // more Again than answered can't happen; clamped
        assertEquals(100, AnkiStats.retentionPercent(10, -5))
    }

    @Test fun retentionWithNoAnsweredCardsIsNullNotADivisionByZero() {
        assertNull(AnkiStats.retentionPercent(0, 0))
        assertNull(AnkiStats.retentionPercent(0, 5))
        assertNull(AnkiStats.retentionPercent(-1, 0))
        assertNull(Fake().reader().quick(null, "p", today, now).retention30)
    }

    // ---- cache JSON ----

    @Test fun cacheJsonRoundTrips() {
        val s = Fake { it.length % 17 }.reader().quick(null, "com.ichi2.anki.A", today, now)
            .copy(historyCheckedAt = now - 5, perDay = full().perDay)
        val text = AnkiStatsCodec.encode(s)
        assertEquals(s, AnkiStatsCodec.decode(text))
        assertEquals(text, AnkiStatsCodec.encode(AnkiStatsCodec.decode(text)!!)) // deterministic
        assertTrue(text.length < 20_000)
    }

    @Test fun aBadOrOldCacheIsNull() {
        val good = AnkiStatsCodec.encode(full())
        fun changed(f: JSONObject.() -> Unit) = JSONObject(good).apply(f).toString()
        assertNotNull(AnkiStatsCodec.decode(good))
        assertNull(AnkiStatsCodec.decode(""))
        assertNull(AnkiStatsCodec.decode("not json"))
        assertNull(AnkiStatsCodec.decode("{}"))
        assertNull(AnkiStatsCodec.decode("[1,2]"))
        assertNull(AnkiStatsCodec.decode(good.take(good.length / 2)))                       // cut off
        assertNull(AnkiStatsCodec.decode(changed { put("v", 2) }))                          // another version
        assertNull(AnkiStatsCodec.decode(changed { remove("v") }))
        assertNull(AnkiStatsCodec.decode(changed { remove("pkg") }))
        assertNull(AnkiStatsCodec.decode(changed { put("today", "oops") }))
        assertNull(AnkiStatsCodec.decode(changed { getJSONObject("perDay").put("yesterday", 3) })) // a key that is no date
        assertNull(AnkiStatsCodec.decode(changed { put("dueNow", "many") }))
        assertNull(AnkiStatsCodec.decode(changed { put("forecast", JSONArray(listOf(1, 2, 3))) })) // not 30 days
        assertNull(AnkiStatsCodec.decode(changed { put("counts", JSONArray(listOf(1))) }))
        assertNull(AnkiStatsCodec.decode(changed { getJSONObject("buttons").put("7", JSONArray(listOf(1, 2))) }))
        assertNull(AnkiStatsCodec.decode(changed { getJSONObject("buttons").put("week", JSONArray(listOf(1, 2, 3, 4))) }))
    }

    // ---- time in AnkiGo ----

    private fun dayOf(ms: Long) = AnkiGoTime.dayKey(ms, 4, ZoneOffset.UTC)
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun timeInAnkiGoSumsTheAnswerTimePerGameDay() {
        val lines = sequenceOf(
            "${at(13, 9)},1,0,3,4000,app",
            "${at(13, 9, 5)},2,0,1,6500,app",
            "${at(14, 10)},3,1,4,2000,widget",
        )
        val t = timeInAnkiGo(lines, ::dayOf)
        assertEquals(mapOf("2026-10-13" to 10_500L, "2026-10-14" to 2000L), t)
    }

    @Test fun theGameDayChangesAtFourInTheMorning() {
        val lines = sequenceOf(
            "${at(14, 3, 59)},1,0,3,1000,app",   // still the 13th
            "${at(14, 4, 0)},2,0,3,2000,app",    // the 14th starts
            "${at(15, 3, 30)},3,0,3,4000,app",   // still the 14th
        )
        assertEquals(mapOf("2026-10-13" to 1000L, "2026-10-14" to 6000L), timeInAnkiGo(lines, ::dayOf))
        assertEquals("2026-10-13", AnkiGoTime.dayKey(at(14, 6), 8, ZoneOffset.UTC)) // a later day start moves the boundary
    }

    @Test fun timeInAnkiGoSkipsBadLinesAndCapsOneAnswer() {
        val lines = sequenceOf(
            "", "garbage", "1,2,3", "x,1,0,3,500,app", "${at(14, 9)},1,0,3,abc,app", "${at(14, 9)},1,0,3,-5,app",
            "0,1,0,3,500,app", "${at(14, 9)},1,0,3,1500,app", " ${at(14, 9)} , 1 , 0 , 3 , 500 ,app",
            "${at(14, 9)},1,0,3",                      // too short: a half-written line
            "${at(14, 9)},1,0,3,${3 * 3_600_000L},widget", // a widget card left open for 3 hours
        )
        assertEquals(mapOf("2026-10-14" to 1500L + 500L + AnkiGoTime.MAX_ANSWER_MS), timeInAnkiGo(lines, ::dayOf))
        assertEquals(emptyMap<String, Long>(), timeInAnkiGo(emptySequence(), ::dayOf))
        assertEquals(emptyMap<String, Long>(), timeInAnkiGo(sequenceOf("${at(14, 9)},1,0,3,10,app")) { throw IllegalStateException() })
    }

    @Test fun lastDaysAddsTheGameDaysEndingToday() {
        val m = mapOf("2026-10-14" to 5L, "2026-10-13" to 10L, "2026-10-08" to 100L, "2026-10-07" to 1000L, "2026-10-15" to 7L)
        assertEquals(5L, AnkiGoTime.lastDays(m, today, 1))
        assertEquals(115L, AnkiGoTime.lastDays(m, today, 7))   // 14th back to the 8th, not the 7th or the 15th
        assertEquals(0L, AnkiGoTime.lastDays(m, "nope", 7))
    }

    // ---- access and texts ----

    @Test fun accessReasonsComeInTheMostUsefulOrder() {
        assertEquals(AnkiAccess.NOT_INSTALLED, AnkiAccess.decide(installed = false, supported = false, permission = false))
        assertEquals(AnkiAccess.ANKIDROID_TOO_OLD, AnkiAccess.decide(installed = true, supported = false, permission = false))
        assertEquals(AnkiAccess.NO_PERMISSION, AnkiAccess.decide(installed = true, supported = true, permission = false))
        assertEquals(AnkiAccess.OK, AnkiAccess.decide(installed = true, supported = true, permission = true))
    }

    @Test fun reasonsSayWhatToDo() {
        assertEquals("Allow AnkiGo to read AnkiDroid to see its stats", StatsText.reason(AnkiAccess.NO_PERMISSION))
        assertTrue(StatsText.reason(AnkiAccess.ANKIDROID_TOO_OLD).contains("2.24"))
        assertTrue(StatsText.reason(AnkiAccess.NOT_INSTALLED).contains("Install AnkiDroid"))
        assertEquals("Couldn't read AnkiDroid's stats (boom)", StatsText.reason(AnkiAccess.ERROR, "boom"))
        assertEquals("Couldn't read AnkiDroid's stats", StatsText.reason(AnkiAccess.ERROR, " "))
        assertEquals("", StatsText.reason(AnkiAccess.OK))
    }

    @Test fun updatedTimeIsRelativeThenAClockTimeThenADate() {
        val z = ZoneOffset.UTC
        assertEquals("just now", StatsText.updated(now - 30_000, now, z))
        assertEquals("just now", StatsText.updated(now + 5_000, now, z))     // a clock a little ahead
        assertEquals("5 min ago", StatsText.updated(now - 5 * 60_000, now, z))
        assertEquals("59 min ago", StatsText.updated(now - 59 * 60_000 - 30_000, now, z))
        assertEquals("09:30", StatsText.updated(at(14, 9, 30), now, z))
        assertEquals("yesterday 23:05", StatsText.updated(at(13, 23, 5), now, z))
        assertEquals("Oct 9", StatsText.updated(at(9, 8), now, z))
    }

    @Test fun durationsFitATile() {
        assertEquals("0 min", StatsText.duration(0)); assertEquals("0 min", StatsText.duration(-5))
        assertEquals("<1 min", StatsText.duration(20_000)); assertEquals("1 min", StatsText.duration(30_000))
        assertEquals("14 min", StatsText.duration(14 * 60_000L)); assertEquals("59 min", StatsText.duration(59 * 60_000L))
        assertEquals("1h 00m", StatsText.duration(60 * 60_000L)); assertEquals("1h 05m", StatsText.duration(65 * 60_000L))
        assertEquals("12h 30m", StatsText.duration(750 * 60_000L))
    }

    @Test fun heatmapNoteSaysWhereTheNumbersComeFrom() {
        val z = ZoneOffset.UTC
        val st = full().copy(fetchedAt = now - 5 * 60_000)
        assertEquals("From AnkiDroid · updated 5 min ago", StatsText.heatmapNote(StatsSnapshot(st, AnkiAccess.OK), now, z))
        // first history pass still running
        val first = st.copy(historyCheckedAt = 0L)
        assertEquals("Reading AnkiDroid's history…", StatsText.heatmapNote(StatsSnapshot(first, AnkiAccess.OK, historyRunning = true), now, z))
        // a later history pass is quiet
        assertEquals("From AnkiDroid · updated 5 min ago", StatsText.heatmapNote(StatsSnapshot(st, AnkiAccess.OK, historyRunning = true), now, z))
        // cached numbers, but the refresh failed
        assertEquals("From AnkiDroid · updated 5 min ago · Allow AnkiGo to read AnkiDroid to see its stats",
            StatsText.heatmapNote(StatsSnapshot(st, AnkiAccess.NO_PERMISSION), now, z))
        // no numbers at all
        assertEquals("Reading AnkiDroid's stats… Meanwhile this is AnkiGo's own count.", StatsText.heatmapNote(StatsSnapshot(), now, z))
        assertEquals("Allow AnkiGo to read AnkiDroid to see its stats. This is AnkiGo's own count, kept from version 0.5.0 on.",
            StatsText.heatmapNote(StatsSnapshot(access = AnkiAccess.NO_PERMISSION), now, z))
        assertTrue(StatsText.heatmapNote(StatsSnapshot(access = AnkiAccess.ERROR, message = "x"), now, z).startsWith("Couldn't read AnkiDroid's stats (x)."))
    }
}
