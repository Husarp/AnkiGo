package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class XpParticlesTest {
    private val plan = XpParticles.plan(XpParticles.MOCK_SEED)
    private val from = XpParticles.Box(200f, 20f, 110f, 100f)   // the defeated one's box (top right)
    private val to = XpParticles.Box(20f, 110f, 140f, 128f)     // ours (bottom left)
    private val unit = 2f

    @Test fun sixteenSparks() = assertEquals(16, plan.sparks.size)

    @Test fun samePictureForTheSameSeed() {
        val a = XpParticles.plan(77)
        val b = XpParticles.plan(77)
        for (i in 0 until 16) {
            val x = a.sparks[i]; val y = b.sparks[i]
            assertEquals(x.t0, y.t0, 0f); assertEquals(x.dur, y.dur, 0f); assertEquals(x.a1, y.a1, 0f); assertEquals(x.r2, y.r2, 0f)
            assertEquals(x.ez, y.ez, 0f); assertEquals(x.sx, y.sx, 0f); assertEquals(x.ey, y.ey, 0f)
        }
        assertEquals(XpParticles.at(a, 1300f, from, to, unit), XpParticles.at(b, 1300f, from, to, unit))
        assertNotEquals(XpParticles.plan(78).sparks[3].a1, a.sparks[3].a1)
    }

    @Test fun launchWindowAndFlightTime() {
        for ((i, s) in plan.sparks.withIndex()) {
            // launch t0 = 520 + (i + rand * 0.8) / 16 * 1000
            assertTrue("launch $i ${s.t0}", s.t0 >= 520f + i / 16f * 1000f - 0.01f && s.t0 <= 520f + (i + 0.8f) / 16f * 1000f + 0.01f)
            assertTrue("flight $i ${s.dur}", s.dur in 750f..1100f)
            assertTrue("size $i ${s.size}", s.size in 0.75f..1.25f)
            assertTrue("ease $i ${s.ez}", s.ez in 0.75f..1.45f)
            assertTrue("radius 1 $i ${s.r1}", s.r1 in 26f..96f)
            assertTrue("radius 2 $i ${s.r2}", s.r2 in 26f..106f)
        }
        assertEquals(plan.sparks.minOf { it.t0 }, plan.firstLaunch, 0f)
        assertEquals(plan.sparks.maxOf { it.t0 + it.dur }, plan.lastLanding, 0f)
        assertTrue(plan.firstLaunch >= 520f && plan.lastLaunch < 1520f)
    }

    @Test fun everySparkStartsInTheDefeatedBoxAndEndsInOurs() {
        for (seed in 1L..40L) for (s in XpParticles.plan(seed).sparks) {
            val (sx, sy) = XpParticles.start(s, from)
            assertTrue("start x $sx", sx >= from.left + from.w * 0.25f - 0.01f && sx <= from.left + from.w * 0.75f + 0.01f)    // the middle half
            assertTrue("start y $sy", sy >= from.top + from.h * 0.35f - 0.01f && sy <= from.top + from.h * 0.75f + 0.01f)       // 55 % down, +-20 %
            val (ex, ey) = XpParticles.landing(s, to)
            assertTrue("end x $ex", ex >= to.left + to.w * 0.325f - 0.01f && ex <= to.left + to.w * 0.675f + 0.01f)            // the middle third
            assertTrue("end y $ey", ey >= to.top + to.h * 0.4f - 0.01f && ey <= to.top + to.h * 0.7f + 0.01f)
        }
    }

    @Test fun aSparkFliesFromItsStartToItsLandingSpot() {
        val s = plan.sparks[5]
        val one = XpParticles.Plan(listOf(s), 1f)
        val (sx, sy) = XpParticles.start(s, from)
        val (ex, ey) = XpParticles.landing(s, to)
        val early = XpParticles.at(one, s.t0 + 1f, from, to, unit).last { it.kind == XpParticles.HEAD }
        assertTrue(hypot(early.x - sx, early.y - sy) < 3f * unit)
        val late = XpParticles.at(one, s.t0 + s.dur - 1f, from, to, unit).last { it.kind == XpParticles.HEAD }
        assertTrue(hypot(late.x - ex, late.y - ey) < 4f * unit)
        // after the flight a small star sits exactly on the landing spot and fades
        val star = XpParticles.at(one, s.land + 110f, from, to, unit).single()
        assertEquals(XpParticles.LAND, star.kind)
        assertEquals(ex, star.x, 0.001f); assertEquals(ey, star.y, 0.001f)
        assertEquals(0.5f, star.alpha, 0.001f)
        assertTrue(XpParticles.at(one, s.land + 220f, from, to, unit).isEmpty() || XpParticles.at(one, s.land + 220f, from, to, unit).all { it.alpha <= 0.001f })
        assertTrue(XpParticles.at(one, s.t0 - 1f, from, to, unit).isEmpty())
    }

    @Test fun eachSparkBendsItsOwnWay() {
        // distance of the half-way point from the straight line start -> landing: both sides, different amounts
        val sides = HashSet<Int>()
        val amounts = HashSet<Int>()
        for (s in plan.sparks) {
            val one = XpParticles.Plan(listOf(s), 1f)
            val (sx, sy) = XpParticles.start(s, from)
            val (ex, ey) = XpParticles.landing(s, to)
            // the ease makes u = 0.5 half-way along the curve's parameter whatever the exponent is not; use the head nearest the time of u = 0.5
            val m = XpParticles.at(one, s.t0 + s.dur * 0.5f, from, to, unit).last { it.kind == XpParticles.HEAD }
            val dx = ex - sx; val dy = ey - sy
            val side = (dx * (m.y - sy) - dy * (m.x - sx)) / hypot(dx, dy)
            sides += if (side > 0) 1 else -1
            amounts += (side / 8f).toInt()
        }
        assertEquals(setOf(-1, 1), sides)
        assertTrue("curves too alike: $amounts", amounts.size >= 5)
    }

    @Test fun nothingBeforeTheFirstLaunchOrAfterTheEnd() {
        assertTrue(XpParticles.at(plan, 0f, from, to, unit).isEmpty())
        assertTrue(XpParticles.at(plan, plan.firstLaunch - 1f, from, to, unit).isEmpty())
        assertTrue(XpParticles.at(plan, plan.lastLanding + 221f, from, to, unit).isEmpty())
        assertTrue(plan.end >= plan.lastLanding + 650f - 0.01f)
    }

    @Test fun numberCountsUpMonotonicallyToTheTotal() {
        val total = 48
        var prev = -1
        var t = 0f
        while (t <= plan.end) {
            val c = XpParticles.count(plan, t, total)
            assertTrue("t=$t $c < $prev", c >= prev)
            assertTrue(c in 0..total)
            prev = c
            t += 10f
        }
        assertEquals(0, XpParticles.count(plan, plan.firstLaunch, total))
        assertEquals(total, XpParticles.count(plan, plan.countTo, total))
        assertEquals(total, XpParticles.count(plan, plan.end, total))
        // it is still counting while the sparks fly (not done at the first launch + a bit, done before the last landing)
        assertTrue(XpParticles.count(plan, plan.firstLaunch + 200f, total) in 1 until total)
        assertTrue(plan.countTo < plan.lastLanding)
    }

    @Test fun numberStaysOnTheSameSpot() {
        // the anchor is one fixed point of the defeated one's box: 18 % down, centred; no time in it
        val (x, y) = XpParticles.numberSpot(from, 412f, 40f, 12f, 2f)
        assertEquals(from.cx, x, 0.001f)
        assertEquals(from.top + from.h * 0.18f, y, 0.001f)
        assertEquals(XpParticles.numberSpot(from, 412f, 40f, 12f, 2f), x to y)
    }

    @Test fun numberStaysInsideTheArena() {
        val high = XpParticles.Box(300f, -30f, 110f, 100f)
        val (x, y) = XpParticles.numberSpot(high, 330f, 40f, 12f, 2f)
        assertEquals(330f - 40f - 2f, x, 0.001f)      // pushed back in from the right edge
        assertEquals(14f, y, 0.001f)                  // and down from the top edge
        val narrow = XpParticles.numberSpot(from, 60f, 40f, 12f, 2f)
        assertEquals(30f, narrow.first, 0.001f)       // no room at all: centred
    }

    @Test fun numberAlphaTimeline() {
        assertEquals(0f, XpParticles.numberAlpha(plan, 0f), 0f)
        assertEquals(0f, XpParticles.numberAlpha(plan, 380f), 0.001f)
        assertEquals(0.5f, XpParticles.numberAlpha(plan, 510f), 0.001f)
        assertEquals(1f, XpParticles.numberAlpha(plan, 640f), 0.001f)
        assertEquals(1f, XpParticles.numberAlpha(plan, plan.lastLanding + 100f), 0.001f)
        assertEquals(1f, XpParticles.numberAlpha(plan, plan.fadeFrom), 0.001f)
        var prev = 1f
        var t = plan.fadeFrom
        while (t <= plan.fadeTo) {
            val a = XpParticles.numberAlpha(plan, t)
            assertTrue(a <= prev + 1e-6f)
            prev = a
            t += 20f
        }
        assertEquals(0f, XpParticles.numberAlpha(plan, plan.fadeTo), 0.001f)
        assertEquals(0f, XpParticles.numberAlpha(plan, plan.end + 50f), 0f)
        // it fades only after the last spark landed, and is gone by the effect's end
        assertEquals(plan.lastLanding + 180f, plan.fadeFrom, 0.01f)
        assertEquals(plan.lastLanding + 650f, plan.fadeTo, 0.01f)
    }

    @Test fun numberPopsOnceAndSettlesAtOne() {
        assertEquals(1f, XpParticles.numberScale(plan, 100f), 0.0001f)
        assertEquals(1f, XpParticles.numberScale(plan, 380f), 0.0001f)
        assertEquals(1.16f, XpParticles.numberScale(plan, 510f), 0.0001f)
        assertEquals(1f, XpParticles.numberScale(plan, 640f), 0.0001f)
        assertEquals(1f, XpParticles.numberScale(plan, 2000f), 0.0001f)
    }

    @Test fun halfPaceHalvesEveryTime() {
        val half = XpParticles.plan(XpParticles.MOCK_SEED, 0.5f)
        for (i in 0 until 16) {
            assertEquals(plan.sparks[i].t0 / 2f, half.sparks[i].t0, 0.01f)
            assertEquals(plan.sparks[i].dur / 2f, half.sparks[i].dur, 0.01f)
        }
        assertEquals(plan.firstLanding / 2f, half.firstLanding, 0.01f)
        assertEquals(plan.lastLanding / 2f, half.lastLanding, 0.01f)
        assertEquals(plan.fadeTo / 2f, half.fadeTo, 0.01f)
        assertEquals(plan.end / 2f, half.end, 0.01f)
        assertEquals(plan.popFrom / 2f, half.popFrom, 0.01f)
        assertEquals(plan.countTo / 2f, half.countTo, 0.01f)
        // the same picture, only faster
        val a = XpParticles.at(plan, 1400f, from, to, unit)
        val b = XpParticles.at(half, 700f, from, to, unit)
        assertEquals(a.size, b.size)
        for (i in a.indices) { assertEquals(a[i].x, b[i].x, 0.05f); assertEquals(a[i].y, b[i].y, 0.05f) }
    }

    @Test fun totalIsAboutThreeAndAHalfSeconds() {
        assertTrue("end ${plan.end}", plan.end in 3000f..4200f)
        assertTrue("first landing ${plan.firstLanding}", plan.firstLanding in 1250f..2000f)
    }

    @Test fun flashStartsAtTheFirstLandingAndEndsExactlyAtZero() {
        assertEquals(0f, XpParticles.flash(plan, 0f), 0f)
        assertEquals(0f, XpParticles.flash(plan, plan.firstLanding - 1f), 0f)
        assertTrue(XpParticles.flash(plan, plan.firstLanding + 5f) > 0.5f)
        var t = 0f
        var peak = 0f
        while (t <= plan.end + 300f) {
            val f = XpParticles.flash(plan, t)
            assertTrue("flash $f at $t", f in 0f..1f)
            peak = maxOf(peak, f)
            t += 5f
        }
        assertEquals(1f, peak, 0.0001f)   // landings overlap: capped at 1
        assertEquals(0f, XpParticles.flash(plan, plan.lastLanding + 130f), 0f)
        assertEquals(0f, XpParticles.flash(plan, plan.end), 0f)
        assertEquals(0f, XpParticles.flash(plan, plan.lastLanding + 5000f), 0f)
    }

    @Test fun flashOfOneLandingDiesAwayInThirteenTenthsOfASecond() {
        val one = XpParticles.Plan(listOf(plan.sparks[0]), 1f)
        val l = plan.sparks[0].land
        assertEquals(XpParticles.FLASH_GAIN, XpParticles.flash(one, l), 0.0001f)
        assertEquals(XpParticles.FLASH_GAIN * 0.5f, XpParticles.flash(one, l + 65f), 0.0001f)
        assertEquals(0f, XpParticles.flash(one, l + 130f), 0f)
    }

    @Test fun unitFollowsTheArenaWidth() {
        assertEquals(2f * 0.7f, XpParticles.unit(240f * 2f, 2f), 0.0001f)     // narrow windows: floor 0.7
        assertEquals(2f * 1.25f, XpParticles.unit(1200f * 2f, 2f), 0.0001f)   // wide: ceiling 1.25
        assertEquals(2f * (412f / 360f), XpParticles.unit(412f * 2f, 2f), 0.0001f)
        assertTrue(abs(XpParticles.unit(0f, 0f)) >= 0f)
    }
}
