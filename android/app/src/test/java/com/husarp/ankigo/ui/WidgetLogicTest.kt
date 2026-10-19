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

    /** R9-W5-A: the team signature changes with everything a Team / Box action can change of what the widgets draw, and with nothing an answer changes. */
    @Test fun theTeamSignatureFollowsTheTeamAndNotTheAnswers() {
        val s = TestData.state(data)
        s.mons += TestData.mon(data, 2, 10, uid = "b")
        val base = WidgetLogic.teamSignature(s)
        // an answer: HP, XP, cash, the day's numbers
        s.mons.first().hp -= 1; s.mons.first().xp += 7; s.cash += 5; s.reviewsToday += 1
        assertEquals(base, WidgetLogic.teamSignature(s))
        // a box Pokémon changes: not on a widget
        s.mon("b")!!.nickname = "Zip"; s.mon("b")!!.level = 11
        assertEquals(base, WidgetLogic.teamSignature(s))
        // who is in the team and in which order
        s.team += "b"
        val two = WidgetLogic.teamSignature(s)
        assertTrue(two != base)
        s.team.reverse()
        val flipped = WidgetLogic.teamSignature(s)
        assertTrue(flipped != two)
        s.team.reverse()
        assertEquals(two, WidgetLogic.teamSignature(s))
        // what each one shows
        val first = s.mons.first()
        for (change in listOf<() -> Unit>({ first.look = 172 }, { first.nickname = "Zippy" }, { first.level += 1 }, { first.species = 26 }, { first.shiny = !first.shiny })) {
            val before = WidgetLogic.teamSignature(s)
            change()
            assertTrue(before != WidgetLogic.teamSignature(s))
        }
    }
    /** R9-W5-FIX: a Pokémon a widget shows by pick (even one in the box) counts in the signature: its picture / name / level / release, not its HP. */
    @Test fun aPickedBoxedPokemonCountsInTheSignature() {
        val s = TestData.state(data)
        s.mons += TestData.mon(data, 2, 10, uid = "boxed")
        val none = WidgetLogic.teamSignature(s)
        val picks = setOf("boxed")
        val base = WidgetLogic.teamSignature(s, picks)
        assertTrue(base != none)
        // without picks nothing changes for a boxed one; with the pick it does
        val boxed = s.mon("boxed")!!
        boxed.hp -= 1; boxed.xp += 5
        assertEquals(base, WidgetLogic.teamSignature(s, picks))
        for (change in listOf<() -> Unit>({ boxed.look = 172 }, { boxed.nickname = "Zippy" }, { boxed.level += 1 }, { boxed.species = 26 }, { boxed.shiny = !boxed.shiny })) {
            val before = WidgetLogic.teamSignature(s, picks)
            val teamBefore = WidgetLogic.teamSignature(s)
            change()
            assertTrue(before != WidgetLogic.teamSignature(s, picks))
            assertEquals(teamBefore, WidgetLogic.teamSignature(s))
        }
        // released: the pick is gone, which is a change too (the widget falls back to the main), and it does not crash
        val before = WidgetLogic.teamSignature(s, picks)
        s.mons.remove(boxed)
        assertTrue(before != WidgetLogic.teamSignature(s, picks))
        assertEquals(WidgetLogic.teamSignature(s, picks), WidgetLogic.teamSignature(s, picks))
    }
}
