package com.husarp.ankigo.ui

import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.TestData
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun at(day: String, hour: Int) = ZonedDateTime.of(
        day.substring(0, 4).toInt(), day.substring(5, 7).toInt(), day.substring(8, 10).toInt(), hour, 30, 0, 0, zone,
    ).toInstant().toEpochMilli()

    private val data = TestData.world()

    @Test fun dayStartsAtTheConfiguredHour() {
        assertEquals("2026-10-12", WidgetLogic.dayKey(at("2026-10-13", 3), 4, zone))
        assertEquals("2026-10-13", WidgetLogic.dayKey(at("2026-10-13", 4), 4, zone))
        assertEquals("2026-10-13", WidgetLogic.dayKey(at("2026-10-13", 23), 4, zone))
    }

    @Test fun reviewsFromAnEarlierDayCountAsZero() {
        val s = TestData.state(data).apply { day = "2026-10-12"; reviewsToday = 60 }
        assertEquals(0, WidgetLogic.summarize(s, at("2026-10-13", 10), zone).today)
        s.day = "2026-10-13"
        assertEquals(60, WidgetLogic.summarize(s, at("2026-10-13", 10), zone).today)
    }

    @Test fun answersLeftAndGoalDone() {
        val s = TestData.state(data).apply { day = "2026-10-13"; reviewsToday = 64; settings.dailyAverage = 100 }
        val a = WidgetLogic.summarize(s, at("2026-10-13", 10), zone)
        assertEquals(36, a.left)
        assertFalse(a.goalDone)
        assertEquals(0.64f, a.progress, 0.001f)
        s.reviewsToday = 130
        val b = WidgetLogic.summarize(s, at("2026-10-13", 10), zone)
        assertTrue(b.goalDone)
        assertEquals(0, b.left)
        assertEquals(1f, b.progress, 0f)
    }

    @Test fun streakAtRiskOnlyAfterEightPmWithGoalOpenAndAStreak() {
        val s = TestData.state(data).apply {
            day = "2026-10-13"; reviewsToday = 10; streakDays = 5; lastGoalDay = "2026-10-12"
        }
        assertFalse(WidgetLogic.summarize(s, at("2026-10-13", 19), zone).atRisk)
        assertTrue(WidgetLogic.summarize(s, at("2026-10-13", 20), zone).atRisk)
        s.reviewsToday = 100
        assertFalse(WidgetLogic.summarize(s, at("2026-10-13", 21), zone).atRisk)
        s.reviewsToday = 10; s.streakDays = 0
        assertFalse(WidgetLogic.summarize(s, at("2026-10-13", 21), zone).atRisk)
    }

    @Test fun aLapsedStreakShowsZero() {
        val s = TestData.state(data).apply { day = "2026-10-10"; streakDays = 7; lastGoalDay = "2026-10-10" }
        val snap = WidgetLogic.summarize(s, at("2026-10-13", 10), zone)
        assertEquals(0, snap.streak)
        assertFalse(snap.atRisk)
        s.lastGoalDay = "2026-10-12"
        assertEquals(7, WidgetLogic.summarize(s, at("2026-10-13", 10), zone).streak)
    }

    @Test fun ballsCoinsAndTeamHp() {
        val s = TestData.state(data)
        val main = s.main!!
        val max = StatCalc.maxHp(data, main)
        main.hp = max / 2
        s.cash = 1240
        s.bag["poke-ball"] = 10; s.bag["great-ball"] = 4; s.bag["potion"] = 3
        val snap = WidgetLogic.summarize(s, at("2026-10-13", 10), zone) { StatCalc.maxHp(data, it) }
        assertEquals(14, snap.balls)
        assertEquals(1240, snap.cash)
        assertTrue(snap.hasStarter)
        assertEquals(main.hp.toFloat() / max, snap.team[0].hpFrac, 0.001f)
        assertEquals(main.name, snap.main!!.name)
    }

    @Test fun hpFractionEdgeCases() {
        assertEquals(0f, WidgetLogic.hpFrac(0, 50), 0f)
        assertEquals(1f, WidgetLogic.hpFrac(10, null), 0f)
        assertEquals(1f, WidgetLogic.hpFrac(80, 50), 0f)
        assertEquals(0.5f, WidgetLogic.hpFrac(25, 50), 0f)
    }

    @Test fun noStarterMeansNoMain() {
        val s = TestData.state(data).apply { team.clear() }
        assertFalse(WidgetLogic.summarize(s, at("2026-10-13", 10), zone).hasStarter)
    }

    @Test fun teamIsCappedAtSix() {
        val s = TestData.state(data)
        repeat(8) { i -> TestData.mon(data, 25, 5, uid = "x$i").also { s.mons += it; s.team += it.uid } }
        assertEquals(6, WidgetLogic.summarize(s, at("2026-10-13", 10), zone).team.size)
    }

    @Test fun wideGeometryFitsTheMinimumSizeAndTheTallSize() {
        val small = WidgetLogic.wideGeometry(250f, 110f, 6)
        assertTrue(small.tile >= 40f)
        assertFalse(small.chipsRow)
        // tile + team region must fit in the height
        assertTrue(WidgetLogic.PAD_Y + small.tile + WidgetLogic.teamRegionHeight(small.sprite) + WidgetLogic.PAD_Y <= 110f + 0.01f)
        val tall = WidgetLogic.wideGeometry(320f, 200f, 6)
        assertTrue(tall.chipsRow)
        assertEquals(96f, tall.tile, 0f)
        assertFalse(tall.chipsInColumn)
        assertTrue(WidgetLogic.wideGeometry(320f, 150f, 6).chipsInColumn)
    }

    @Test fun bitmapStaysUnderTheCap() {
        for ((w, h, d) in listOf(Triple(330f, 140f, 3f), Triple(110f, 110f, 3.5f), Triple(700f, 400f, 4f), Triple(250f, 110f, 2f))) {
            val k = WidgetLogic.pixelsPerDp(w, h, d)
            assertTrue(k <= d)
            assertTrue(w * k * h * k * 4 <= WidgetLogic.MAX_BITMAP_BYTES * 1.02f)
        }
        assertEquals(2f, WidgetLogic.pixelsPerDp(250f, 110f, 2f), 0f)
    }
}
