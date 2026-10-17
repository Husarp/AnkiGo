package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's timeline, particle and wash maths, and its fixed-step spawning. Pure JVM. */
class FxScriptTest {
    private val eps = 1e-3f

    private fun script(unit: Float = 2f, reverse: Boolean = false, react: Boolean = true, seed: Long = 7L): FxScript {
        val stage = RecStage(unit, reverse)
        return MoveFx.script(stage, plan(), react, seed)!!
    }

    // ---- timeline ----
    @Test fun atRunsOnceAtItsTimeInOrder() {
        val s = script()
        val log = ArrayList<String>()
        s.at(100f) { log += "b" }
        s.at(50f) { log += "a" }
        s.at(300f) { log += "c" }
        s.advance(10f); assertTrue(log.isEmpty())
        s.advance(120f); assertEquals(listOf("b", "a"), log)       // the JS order: insertion, not time
        s.advance(130f); assertEquals(2, log.size)
        s.advance(400f); assertEquals(listOf("b", "a", "c"), log)
        s.advance(500f); assertEquals(3, log.size)
    }

    @Test fun tweenGetsProgressAndAlwaysEndsAtOne() {
        val s = script()
        val seen = ArrayList<Float>()
        s.tween(100f, 200f) { p, _ -> seen += p }
        s.advance(50f); assertTrue(seen.isEmpty())
        s.advance(100f); assertEquals(0f, seen.last(), eps)
        s.advance(200f); assertEquals(.5f, seen.last(), eps)
        s.advance(5000f)                                           // skipped over the end: the last call is still p = 1
        assertEquals(1f, seen.last(), 0f)
        val n = seen.size
        s.advance(6000f); assertEquals(n, seen.size)               // and then it is done
    }

    @Test fun tweenEaseAndMsParameter() {
        val s = script()
        var p = -1f; var ms = -1f
        s.tween(0f, 100f, FxEase.IN2) { q, t -> p = q; ms = t }
        s.advance(50f)
        assertEquals(.25f, p, eps); assertEquals(50f, ms, eps)
    }

    @Test fun zeroLengthTweenRunsOnceAtOne() {
        val s = script()
        var n = 0; var last = -1f
        s.tween(10f, 0f) { p, _ -> n++; last = p }
        s.advance(10f); s.advance(20f)
        assertEquals(1, n); assertEquals(1f, last, 0f)
    }

    @Test fun ticksRunOnAFixedStepAndCatchUp() {
        fun count(frames: List<Float>): Int {
            val s = script()
            var n = 0
            s.ticks(0f, 99f) { _, _ -> n++ }
            frames.forEach { s.advance(it) }
            return n
        }
        val fine = (0..40).map { it * 4f }                          // 250 fps
        val coarse = listOf(0f, 100f, 200f)                         // 10 fps
        val jump = listOf(500f)                                     // one big jump (a still frame)
        assertEquals(6, count(fine)); assertEquals(6, count(coarse)); assertEquals(6, count(jump))
    }

    @Test fun ticksProgressRunsFromZeroToOne() {
        val s = script()
        val ps = ArrayList<Float>()
        s.ticks(100f, 100f, 25f) { p, _ -> ps += p }
        s.advance(1000f)
        assertEquals(listOf(0f, .25f, .5f, .75f, 1f), ps)
    }

    @Test fun sameSpawnCountWhateverTheFrameRate() {
        fun spawn(frames: List<Float>): Int {
            val s = script(seed = 3L)
            s.ticks(0f, 600f) { _, ms -> if (s.chance(.5f)) s.particle("star_04", 0, 10f, 10f, life = 5000f, s0 = 4f) }
            frames.forEach { s.advance(it) }
            return s.particles.size
        }
        val a = spawn((0..60).map { it * 10f })
        val b = spawn((0..6).map { it * 100f })
        val c = spawn(listOf(700f))
        assertEquals(a, b); assertEquals(a, c)
        assertTrue(a in 5..30)
    }

    // ---- particles ----
    @Test fun particlesAreStampedWithTheItemsOwnTime() {
        val s = script()
        s.at(310f) { s.particle("circle_02", 0, 1f, 2f, life = 800f) }
        s.advance(700f)                                             // a late frame
        val p = s.particles.single()
        assertEquals(310f, p.t0, 0f)
        assertEquals(1f, p.x0, 0f)
        s.advance(1200f)                                            // 310 + 800 = 1110: gone
        assertTrue(s.particles.isEmpty())
    }

    @Test fun particleMotionMapsStageUnitsAndDirection() {
        val fwd = script(unit = 2f)
        val p = fwd.particle("a", 0, 100f, 50f, vx = 30f, vy = -80f, ax = 10f, ay = 400f, life = 1000f, s0 = 20f, s1 = 5f, a0 = 1f, a1 = 0f)!!
        // velocities x unit, x flipped with the direction (forward: not flipped), sizes x unit
        assertEquals(60f, p.vx, eps); assertEquals(-160f, p.vy, eps); assertEquals(20f, p.ax, eps); assertEquals(800f, p.ay, eps)
        assertEquals(40f, p.s0, eps); assertEquals(10f, p.s1, eps)
        // position after 0.5 s: x0 + v t + a t^2 / 2
        assertEquals(100f + 60f * .5f + .5f * 20f * .25f, p.x(500f), eps)
        assertEquals(50f - 160f * .5f + .5f * 800f * .25f, p.y(500f), eps)
        assertEquals(25f, p.size(500f), eps)                        // 40 -> 10 halfway
        assertEquals(.5f, p.alpha(500f), eps)
        val rev = script(unit = 2f, reverse = true)
        val q = rev.particle("a", 0, 100f, 50f, vx = 30f, vy = -80f, ax = 10f, ay = 400f, life = 1000f)!!
        assertEquals(-60f, q.vx, eps); assertEquals(-20f, q.ax, eps)  // x mirrored
        assertEquals(-160f, q.vy, eps); assertEquals(800f, q.ay, eps) // gravity and "up" are not
    }

    @Test fun followRotatesAlongTheVelocityOthersSpin() {
        val s = script()
        val f = s.particle("a", 0, 0f, 0f, vx = 100f, vy = 100f, follow = true)!!
        assertEquals(Math.PI.toFloat() / 4f, f.angle(0f), 1e-4f)
        val r = s.particle("a", 0, 0f, 0f, rot = .5f, vrot = 2f, life = 1000f)!!
        assertEquals(.5f + 2f * .25f, r.angle(250f), 1e-5f)
    }

    @Test fun alphaIsClampedAndProgressIsOverLife() {
        val s = script()
        val p = s.particle("a", 0, 0f, 0f, life = 400f, a0 = 2f, a1 = -1f)!!
        assertEquals(1f, p.alpha(0f), 0f)
        assertEquals(0f, p.alpha(399f), .01f)
        assertEquals(.25f, p.progress(100f), eps)
        assertTrue(p.alive(399f)); assertFalse(p.alive(400f))
    }

    @Test fun pixelBitSizesAreWholeDevicePixels() {
        val s = script(unit = 2.2f)
        val px = s.artPx(1f)
        assertEquals(3f, px * s.u, 1e-4f)
        val p = s.particle(x = 0f, y = 0f, s0 = px, pix = intArrayOf(0xFFFF0000.toInt()))!!
        assertEquals(3f, p.s0, 1e-4f)
    }

    @Test fun particleCapReturnsNull() {
        val s = script()
        repeat(FxScript.MAX_PARTICLES) { assertNotNull(s.particle("a", 0, 0f, 0f)) }
        assertNull(s.particle("a", 0, 0f, 0f))
        s.burst("a", 0, FxPt(0f, 0f), 10, 100f, 100f, 10f, 5f)
        assertEquals(FxScript.MAX_PARTICLES, s.particles.size)
    }

    @Test fun burstSpreadsAndScales() {
        val s = script(unit = 2f)
        s.at(0f) { s.burst("star_04", 0xFFFFFFFF.toInt(), FxPt(50f, 60f), 8, 220f, 380f, 36f, 6f, true) }
        s.advance(0f)
        assertEquals(8, s.particles.size)
        for (p in s.particles) {
            assertEquals(50f, p.x0, 0f); assertEquals(60f, p.y0, 0f)
            assertTrue(p.add)
            val v = Math.hypot(p.vx.toDouble(), p.vy.toDouble()).toFloat()
            assertTrue("speed $v", v >= 220f * .4f * 2f - eps && v <= 220f * 2f + eps)
            assertTrue(p.life in 380f * .7f..380f * 1.2f)
            assertTrue(p.s0 in 36f * .7f * 2f..36f * 1.2f * 2f)
            assertEquals(12f, p.s1, eps)
        }
    }

    // ---- seeds ----
    @Test fun sameSeedSameAnimation() {
        fun run(seed: Long): List<Float> {
            val s = script(seed = seed)
            TackleFx.build(s)
            s.advance(400f)
            return s.particles.flatMap { listOf(it.x0, it.y0, it.vx, it.vy, it.life, it.s0) }
        }
        assertEquals(run(5L), run(5L))
        assertTrue(run(5L) != run(6L))
    }

    // ---- washes ----
    @Test fun flashOnceLastsItsTimeFromWhenItFired() {
        val s = script()
        s.at(310f) { s.flashOnce(.35f, 60f) }
        s.advance(300f); assertEquals(0f, s.flashAlpha, 0f)
        s.advance(330f); assertEquals(.35f, s.flashAlpha, 0f)
        s.advance(380f); assertEquals(0f, s.flashAlpha, 0f)
        val j = script()
        j.at(310f) { j.flashOnce(.35f, 60f) }
        j.advance(700f)                                             // a jump over the whole flash: no flash left
        assertEquals(0f, j.flashAlpha, 0f)
    }

    @Test fun dimIsWhatTheLookSetsAndRestIsAtZero() {
        val s = script()
        assertEquals(0f, s.dim, 0f)
        s.tween(0f, 100f) { p, _ -> s.dim = .5f * p }
        s.advance(50f); assertEquals(.25f, s.dim, eps)
    }

    // ---- procs and Showdown-style effects ----
    @Test fun procsAreKeptUntilTheirEnd() {
        val s = script()
        s.proc(100f, 300f) { _, _, _ -> }
        s.advance(0f); assertEquals(1, s.procs.size)
        s.advance(299f); assertEquals(1, s.procs.size)
        s.advance(300f); assertTrue(s.procs.isEmpty())
    }

    @Test fun psFxProcSpanIncludesItsTail() {
        val s = script()
        val spr = FxSprite("ps:wisp")
        s.psFx(spr, PsKey(time = 200f), PsEnd(opacity = 0f, time = 600f), PsEase.LINEAR, PsAfter.FADE)
        s.psFx(spr, PsKey(time = 0f), PsEnd(time = 400f), PsEase.ACCEL, PsAfter.EXPLODE)
        s.psFx(spr, PsKey(time = 40f), PsEnd(), PsEase.LINEAR, PsAfter.NONE)
        assertEquals(3, s.procs.size)
        assertEquals(200f, s.procs[0].s, 0f); assertEquals(700f, s.procs[0].e, 0f)
        assertEquals(0f, s.procs[1].s, 0f); assertEquals(600f, s.procs[1].e, 0f)
        assertEquals(40f, s.procs[2].s, 0f); assertEquals(540f, s.procs[2].e, 0f)    // default end = start + 500
    }

    @Test fun psScaleFollowsShowdownsDepth() {
        val s = script()
        assertEquals(2f * .6f, s.psScale(0f), 1e-5f)
        assertEquals(1f * .6f, s.psScale(200f), 1e-5f)
    }

    // ---- shapes ----
    @Test fun jagKeepsItsEndsAndLength() {
        val s = script()
        val j = s.jag(10f, 0f, 30f, 100f, 9, 22f)
        assertEquals(20, j.size)
        assertEquals(10f, j[0], 0f); assertEquals(0f, j[1], 0f)
        assertEquals(30f, j[18], 0f); assertEquals(100f, j[19], 0f)
        for (i in 1 until 9) {
            val p = i / 9f
            assertTrue(Math.abs(j[i * 2] - (10f + 20f * p)) <= 22f + eps)
            assertTrue(Math.abs(j[i * 2 + 1] - 100f * p) <= 22f * .3f + eps)
        }
    }

    @Test fun cubicAndKeyframesAndEase() {
        val c = FxCubic(0f, 0f, 10f, 20f, 20f, 20f, 30f, 0f)
        assertEquals(0f, c.x(0f), 0f); assertEquals(30f, c.x(1f), eps); assertEquals(0f, c.y(1f), eps)
        assertEquals(15f, c.x(.5f), eps); assertEquals(15f, c.y(.5f), eps)    // 3/8 of 20 twice
        val keys = FxMath.keys(3, 0f, 0f, 1f, 100f, 10f, .5f, 200f, -10f, 1f)
        assertEquals(0f, FxMath.kf(keys, -5f), 0f)
        assertEquals(5f, FxMath.kf(keys, 50f), eps)
        assertEquals(.75f, FxMath.kf(keys, 50f, 1), eps)
        assertEquals(0f, FxMath.kf(keys, 150f), eps)
        assertEquals(-10f, FxMath.kf(keys, 900f), 0f)
        assertEquals(.25f, FxEase.IN2.at(.5f), eps); assertEquals(.75f, FxEase.OUT2.at(.5f), eps)
        assertEquals(.5f, FxEase.INOUT.at(.5f), eps); assertEquals(.125f, FxEase.INOUT.at(.25f), eps); assertEquals(1f, FxEase.INOUT.at(1f), eps)
    }

    @Test fun noScriptWithoutASize() {
        assertNull(MoveFx.script(RecStage(unitPx = 0f), plan(), true, 1L))
        assertNotNull(MoveFx.script(RecStage(unitPx = 1f), plan(), true, 1L))
    }
}
