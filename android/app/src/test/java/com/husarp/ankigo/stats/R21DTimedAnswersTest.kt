package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import org.junit.Assert.*
import org.junit.Test

class R21DTimedAnswersTest {
    @Test fun countsOnlyValidTimedEntriesIncludingZeroAndCapsBothSources() {
        val own = timedAnswers(sequenceOf("100,1,0,3,14500,study", "101,1,0,3,0,study", "102,1,0,3,99999999,widget",
            "103,1,0,3,-1", "bad", "0,1,0,3,5", "104,1,0,3,broken"), false) { "day" }
        assertEquals(3L, own.counts["day"])
        assertEquals(614500L, own.time["day"])
        val live = timedAnswers(sequenceOf("100,14500", "101,999999", "102,0", "103,-1", "104,5,extra", "0,1"), true) { "day" }
        assertEquals(3L, live.counts["day"])
        assertEquals(74500L, live.time["day"])
    }

    @Test fun badDatesAreExcludedFromTimeAndCountTogether() {
        val got = timedAnswers(sequenceOf("100,20", "200,30"), true) { if (it == 100L) error("bad date") else "next" }
        assertEquals(mapOf("next" to 30L), got.time)
        assertEquals(mapOf("next" to 1L), got.counts)
    }

    private val today = "2026-10-30"
    private val yesterday = "2026-10-29"
    private fun snapshot() = StatsSnapshot(timeByDay = mapOf(today to 20000L, yesterday to 100000L),
        liveTimeByDay = mapOf(today to 9000L), timedAnswersByDay = mapOf(today to 2L, yesterday to 10L))

    @Test fun mixedSourcesUseTimedAnswersAndIgnoreCatchUpCount() {
        val s = ChartData.build(ChartMetric.SECONDS_PER_CARD, ChartPeriod.D7, "Some deck", snapshot(),
            mapOf(today to DayLog(reviews = 1000)), today)
        assertEquals(10.75, s.total, 1e-9)
        assertEquals(14.5, s.bars.last().values.single(), 1e-9)
        assertEquals(12.25, s.perDayAvg, 1e-9)
        assertEquals(10.0, s.best, 1e-9)
        assertFalse(s.deckApplies)
        assertTrue(s.bars.take(5).none { it.hasData })
        assertTrue(s.bars.first().detail.single().endsWith("–"))
        assertFalse(s.empty)
    }

    @Test fun weeklyAndMonthlyBucketsDivideSummedTimeBySummedAnswers() {
        for ((period, grain) in listOf(ChartPeriod.D30 to ChartGrain.WEEK, ChartPeriod.D90 to ChartGrain.MONTH)) {
            val s = ChartData.build(ChartMetric.SECONDS_PER_CARD, period, null, snapshot(), emptyMap(), today, grain)
            assertEquals(10.75, s.bars.last().values.single(), 1e-9)
            assertEquals(10.75, s.total, 1e-9)
        }
    }

    @Test fun gameDayRolloverAndThirtyDayWindowKeepTheSameDenominator() {
        val zone = java.time.ZoneId.of("UTC")
        val before = java.time.Instant.parse("2026-10-30T03:59:00Z").toEpochMilli()
        val after = java.time.Instant.parse("2026-10-30T04:00:00Z").toEpochMilli()
        val timed = timedAnswers(sequenceOf("$before,10000", "$after,20000"), true) { AnkiGoTime.dayKey(it, 4, zone) }
        assertEquals(mapOf(yesterday to 1L, today to 1L), timed.counts)
        assertEquals(mapOf(yesterday to 10000L, today to 20000L), timed.time)
        val outside = java.time.LocalDate.parse(today).minusDays(30).toString()
        val time = timed.time + (outside to 999999L)
        val counts = timed.counts + (outside to 100L)
        assertEquals(15.0, StatsText.secondsPerCard(AnkiGoTime.lastDays(time, today, 30),
            AnkiGoTime.lastDays(counts, today, 30))!!, 1e-9)
    }

    @Test fun noTimedAnswersIsMissingButZeroSecondsIsData() {
        fun build(s: StatsSnapshot) = ChartData.build(ChartMetric.SECONDS_PER_CARD, ChartPeriod.D7, null, s, emptyMap(), today)
        assertTrue(build(StatsSnapshot()).empty)
        assertTrue(build(StatsSnapshot()).bars.none { it.hasData })
        val zero = build(StatsSnapshot(timedAnswersByDay = mapOf(today to 1L)))
        assertFalse(zero.empty)
        assertTrue(zero.bars.last().hasData)
        assertEquals("0.0 s / card", StatsText.averageText(zero.total))
        assertEquals("14.5 s / card", StatsText.averageText(StatsText.secondsPerCard(29000L, 2L)))
        assertEquals("–", StatsText.averageText(StatsText.secondsPerCard(999L, 0L)))
    }
}
