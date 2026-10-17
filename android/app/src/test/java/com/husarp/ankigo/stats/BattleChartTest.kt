package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// TICKET R4-V11: the four battle metrics (damage dealt / taken / blocked, misses) as charts, fed from the day log. Made-up data only.
class BattleChartTest {
    private val today = "2026-10-14" // a Wednesday
    private val t = LocalDate.parse(today)
    private fun date(k: Int) = t.minusDays(k.toLong()).toString()

    private fun log(dealt: Int = 0, taken: Int = 0, blocked: Int = 0, misses: Int = 0, reviews: Int = 0) =
        DayLog(reviews = reviews, dmgDealt = dealt, dmgTaken = taken, dmgBlocked = blocked, misses = misses)

    private fun build(m: ChartMetric, p: ChartPeriod, history: Map<String, DayLog> = emptyMap(), deck: String? = null, grain: ChartGrain? = null) =
        ChartData.build(m, p, deck, StatsSnapshot(), history, today, grain)

    private val history = mapOf(
        date(0) to log(dealt = 120, taken = 45, blocked = 12, misses = 3),
        date(2) to log(dealt = 80, taken = 0, blocked = 0, misses = 1),
        date(9) to log(dealt = 1000, taken = 300, blocked = 50, misses = 0),
    )

    @Test fun theFourMetricsAreInTheGameGroupWithTheirUnitsAndIcons() {
        val m = listOf(ChartMetric.DMG_DEALT, ChartMetric.DMG_TAKEN, ChartMetric.DMG_BLOCKED, ChartMetric.MISSES)
        assertEquals(listOf("Damage dealt", "Damage taken", "Damage blocked", "Misses"), m.map { it.label })
        assertEquals(listOf("HP", "HP", "HP", "misses"), m.map { it.unit })
        assertTrue(m.all { it.group == ChartGroup.GAME && it.kind == ChartKind.TIME && it.listed && !it.fromAnkiDroid && it.usesPeriod })
        assertEquals(4, m.map { it.icon }.toSet().size)
    }

    @Test fun eachMetricReadsItsOwnDayLogField() {
        val dealt = build(ChartMetric.DMG_DEALT, ChartPeriod.D7, history)
        assertEquals(200.0, dealt.total, 0.0); assertEquals(120.0, dealt.best, 0.0); assertEquals(today, dealt.bestDay)
        assertEquals(listOf(120.0), dealt.bars.last().values); assertEquals(listOf(80.0), dealt.bars[4].values)
        assertEquals(7, dealt.bars.size)
        assertEquals(45.0, build(ChartMetric.DMG_TAKEN, ChartPeriod.D7, history).total, 0.0)
        assertEquals(12.0, build(ChartMetric.DMG_BLOCKED, ChartPeriod.D7, history).total, 0.0)
        val misses = build(ChartMetric.MISSES, ChartPeriod.D7, history)
        assertEquals(4.0, misses.total, 0.0); assertEquals(3.0, misses.best, 0.0)
        assertEquals("misses", misses.unit)
        assertEquals(listOf("Misses"), misses.seriesNames)
    }

    @Test fun periodsAndGrainsWork() {
        val d30 = build(ChartMetric.DMG_DEALT, ChartPeriod.D30, history)
        assertEquals(30, d30.bars.size); assertEquals(1200.0, d30.total, 0.0); assertEquals(1200.0 / 30, d30.perDayAvg, 1e-9)
        assertEquals(1000.0, d30.best, 0.0); assertEquals(date(9), d30.bestDay)
        val weeks = build(ChartMetric.DMG_DEALT, ChartPeriod.D30, history, grain = ChartGrain.WEEK)
        assertEquals(ChartGrain.WEEK, weeks.grain)
        assertEquals(1200.0, weeks.bars.sumOf { it.values[0] }, 0.0)
        val months = build(ChartMetric.MISSES, ChartPeriod.D365, history, grain = ChartGrain.MONTH)
        assertEquals(4.0, months.bars.sumOf { it.values[0] }, 0.0)
    }

    @Test fun daysWithoutTheNumbersShowZeroAndTheChartIsEmptyWithoutAnyBattle() {
        val old = mapOf(date(1) to log(reviews = 60)) // a day logged before the update: reviews, no battle numbers
        for (m in listOf(ChartMetric.DMG_DEALT, ChartMetric.DMG_TAKEN, ChartMetric.DMG_BLOCKED, ChartMetric.MISSES)) {
            val s = build(m, ChartPeriod.D7, old)
            assertTrue("$m empty", s.empty); assertEquals(0.0, s.total, 0.0); assertTrue(s.bars.all { it.values == listOf(0.0) })
            assertTrue(build(m, ChartPeriod.D30).empty)
        }
    }

    @Test fun theCaptionSaysSinceZeroPointThirteenAndNoDeckFilterApplies() {
        for (m in listOf(ChartMetric.DMG_DEALT, ChartMetric.DMG_TAKEN, ChartMetric.DMG_BLOCKED, ChartMetric.MISSES)) {
            val s = build(m, ChartPeriod.D7, history, deck = "Some deck")
            assertTrue(s.source, s.source.contains("since 0.13")); assertFalse(s.deckApplies)
            assertFalse(s.loading)
        }
    }

    @Test fun tapTextNamesTheNumberInWords() {
        assertEquals(listOf("Wed 14 Oct · 120 HP dealt"), build(ChartMetric.DMG_DEALT, ChartPeriod.D7, history).bars.last().detail)
        assertEquals(listOf("Wed 14 Oct · 45 HP taken"), build(ChartMetric.DMG_TAKEN, ChartPeriod.D7, history).bars.last().detail)
        assertEquals(listOf("Wed 14 Oct · 12 HP blocked"), build(ChartMetric.DMG_BLOCKED, ChartPeriod.D7, history).bars.last().detail)
        assertEquals(listOf("Wed 14 Oct · 3 misses"), build(ChartMetric.MISSES, ChartPeriod.D7, history).bars.last().detail)
        assertEquals(listOf("Mon 12 Oct · 1 miss"), build(ChartMetric.MISSES, ChartPeriod.D7, history).bars[4].detail)
    }

    @Test fun seriesKeysAskAnkiDroidForNothing() {
        for (m in listOf(ChartMetric.DMG_DEALT, ChartMetric.DMG_TAKEN, ChartMetric.DMG_BLOCKED, ChartMetric.MISSES)) {
            assertTrue(ChartData.seriesKeys(m, ChartPeriod.D30, "Some deck", today).isEmpty())
        }
    }
}
