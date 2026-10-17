package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tackle and the physical template on a recording stage: timings, poses, reaction, direction. Pure JVM. */
class TackleFxTest {
    private val eps = 1e-2f
    private val unit = 2f

    private fun run(look: MoveLook = MoveLook.TACKLE, kit: FxKit = FxKit.NORMAL, reverse: Boolean = false, react: Boolean = true,
                    k: Float = 1f, seed: Long = 11L): Triple<RecStage, FxScript, FxTimes> {
        val stage = RecStage(unit, reverse)
        val p = plan(look, kit, k)
        val s = MoveFx.script(stage, p, react, seed)!!
        val t = MoveFx.build(s, look)
        return Triple(stage, s, t)
    }

    private fun frames(s: FxScript, to: Float, step: Float = 1000f / 60f) {
        var ms = 0f
        while (ms < to) { s.advance(ms); ms += step }
        s.advance(to)
    }

    @Test fun timings() {
        val (_, _, t) = run()
        assertEquals(1100f, t.total, 0f); assertEquals(310f, t.impact, 0f); assertEquals(690f, t.done, 0f)
        assertEquals(610f, run(react = false).third.done, 0f)             // no reaction: only the lunge moves anything
        val h = run(MoveLook.HIT_TEMPLATE, FxKit.FIRE).third
        assertEquals(1100f, h.total, 0f); assertEquals(310f, h.impact, 0f)
    }

    @Test fun lungeWindsBackThenStrikesThenReturns() {
        val (stage, s, _) = run()
        s.advance(160f)                                                    // end of the wind-up: 22 units back, 6 down
        var a = stage.last(true)!!
        assertEquals(-22f * unit, a.dx, eps); assertEquals(6f * unit, a.dy, eps)
        s.advance(310f)                                                    // contact: (210 - 20) x .86 toward the target, (-85) x .86
        a = stage.last(true)!!
        assertEquals((210f - 20f) * .86f * unit, a.dx, .5f); assertEquals(-85f * .86f * unit, a.dy, .5f)
        s.advance(700f)                                                    // home again
        a = stage.last(true)!!
        assertEquals(0f, a.dx, eps); assertEquals(0f, a.dy, eps)
        assertEquals(1f, a.sx, 0f); assertEquals(1f, a.sy, 0f)
    }

    @Test fun lungeIsContinuousAndNeverPastTheTarget() {
        val (stage, s, _) = run()
        var prevX = 0f
        val xs = ArrayList<Float>()
        var ms = 0f
        while (ms <= 650f) { s.advance(ms); stage.last(true)?.let { xs += it.dx }; ms += 5f }
        for (x in xs) assertTrue(x <= (210f - 20f) * .86f * unit + 1f)
        for (i in 1 until xs.size) assertTrue("jump at $i: ${xs[i - 1]} -> ${xs[i]}", Math.abs(xs[i] - xs[i - 1]) < 40f)
        prevX = xs.max()
        assertTrue(prevX > 100f)
    }

    @Test fun lungeMirrorsWhenTheWildOneAttacks() {
        val (stage, s, _) = run(reverse = true)
        s.advance(160f)
        var a = stage.last(true)!!
        assertEquals(22f * unit, a.dx, eps)                                // winds back to the right, away from the target
        assertEquals(6f * unit, a.dy, eps)                                 // "down" is still down
        s.advance(310f)
        a = stage.last(true)!!
        assertEquals(-(210f - 20f) * .86f * unit, a.dx, .5f)               // strikes left
        assertEquals(85f * .86f * unit, a.dy, .5f)                         // and down toward us
    }

    @Test fun targetIsKnockedBackFromImpactOnAndSettles() {
        val (stage, s, _) = run()
        s.advance(300f); assertEquals(0, stage.count(false))               // nothing before the hit
        s.advance(310f); assertTrue(stage.count(false) > 0)
        var peak = 0f
        var ms = 310f
        while (ms <= 690f) { s.advance(ms); peak = maxOf(peak, stage.last(false)!!.dx); ms += 4f }
        assertTrue("pushed away from the attacker: $peak", peak > 4f * unit)
        s.advance(700f)
        val l = stage.last(false)!!
        assertEquals(0f, l.dx, 0.1f); assertEquals(0f, l.dy, eps)
        assertTrue(l.matrix == null)
    }

    @Test fun knockBackPointsAwayFromTheAttackerInBothDirections() {
        val (fs, f, _) = run(reverse = false); val (rs, r, _) = run(reverse = true)
        var fMax = 0f; var rMin = 0f; var ms = 310f
        while (ms <= 690f) { f.advance(ms); r.advance(ms); fMax = maxOf(fMax, fs.last(false)!!.dx); rMin = minOf(rMin, rs.last(false)!!.dx); ms += 4f }
        assertTrue(fMax > 4f * unit); assertTrue(rMin < -4f * unit)
    }

    @Test fun noReactionLeavesTheTargetUnposed() {
        val (stage, s, _) = run(react = false)
        frames(s, 1100f)
        assertEquals(0, stage.count(false))
        assertTrue(stage.count(true) > 0)                                  // the lunge still plays
        assertEquals(0, stage.poses.count { it.matrix != null })
        // and the particles still play
        val (st2, s2, _) = run(MoveLook.HIT_TEMPLATE, FxKit.WATER, react = false)
        frames(s2, 450f)
        assertEquals(0, st2.count(false)); assertTrue(s2.particles.isNotEmpty())
    }

    @Test fun burstAtImpactIsAtTheHitSpot() {
        val (_, s, _) = run()
        frames(s, 320f)
        assertTrue(s.particles.size >= 15)                                 // star + ring + 5 sparks + 8 stars
        val spot = s.space.fromTarget(-22f, 4f)
        val first = s.particles.first()
        assertEquals(spot.x, first.x0, eps); assertEquals(spot.y, first.y0, eps)
        assertEquals(310f, first.t0, 0f)
        assertEquals(spot.x, 350f * unit - 22f * unit, eps)                // in front of the target, toward the attacker
        assertEquals(.35f, s.flashAlpha, 0f)
        s.advance(400f); assertEquals(0f, s.flashAlpha, 0f)
        val (_, rs, _) = run(reverse = true)
        frames(rs, 320f)
        assertEquals(140f * unit + 22f * unit, rs.particles.first().x0, eps)
    }

    @Test fun nothingLeftAfterTheTotal() {
        val (stage, s, t) = run()
        frames(s, t.total + 50f)
        assertTrue(s.particles.isEmpty()); assertTrue(s.procs.isEmpty())
        assertEquals(0f, s.flashAlpha, 0f); assertEquals(0f, s.dim, 0f)
        assertEquals(0f, stage.last(true)!!.dx, eps)
    }

    @Test fun everyKitBurstsAtImpactAndEndsInsideTheTotal() {
        for (kit in FxKit.entries) for (seed in longArrayOf(1L, 2L, 3L)) for (reverse in listOf(false, true)) {
            val (stage, s, t) = run(MoveLook.HIT_TEMPLATE, kit, reverse = reverse, k = 1.3f, seed = seed)
            var lastEnd = 0f
            var ms = 0f
            var seen = false
            while (ms <= t.total) {
                s.advance(ms)
                if (ms < 305f) assertTrue("$kit has particles before the hit", s.particles.isEmpty())
                if (ms > 330f && s.particles.isNotEmpty()) seen = true
                for (p in s.particles) lastEnd = maxOf(lastEnd, p.t0 + p.life)
                ms += 1000f / 60f
            }
            assertTrue("$kit shows particles after the hit", seen)
            assertTrue("$kit particles end by ${t.total}: $lastEnd", lastEnd <= t.total + 1f)
            assertTrue(stage.count(true) > 0 && stage.count(false) > 0)
        }
    }

    @Test fun kitsUseTheirOwnParticles() {
        fun keys(kit: FxKit): Set<String?> {
            val (_, s, _) = run(MoveLook.HIT_TEMPLATE, kit)
            val out = HashSet<String?>()
            var ms = 0f
            while (ms <= 700f) { s.advance(ms); s.particles.forEach { out += it.img }; ms += 1000f / 60f }
            return out
        }
        assertTrue("star_06" in keys(FxKit.NORMAL))
        assertTrue("drop" in keys(FxKit.WATER) && "circle_02" in keys(FxKit.WATER))
        assertTrue("spark_05" in keys(FxKit.ELECTRIC) || "spark_01" in keys(FxKit.ELECTRIC))
        assertTrue("star_04" in keys(FxKit.ICE))
        assertTrue("twirl_01" in keys(FxKit.PSYCHIC) && "circle_02" in keys(FxKit.PSYCHIC))
        assertTrue("smoke_08" in keys(FxKit.GHOST) && "star_04" in keys(FxKit.GHOST))
        assertTrue("dirt_01" in keys(FxKit.GROUND) || "dirt_02" in keys(FxKit.GROUND))
        assertTrue("star_06" in keys(FxKit.PLAIN))
    }

    @Test fun plainKitIsTintedWithThePalette() {
        val (_, s, _) = run(MoveLook.HIT_TEMPLATE, FxKit.PLAIN)
        s.advance(320f)
        val tints = s.particles.map { it.tint }.toSet()
        assertTrue(TEST_PALETTE.deep in tints && TEST_PALETTE.glow in tints && TEST_PALETTE.spark in tints)
    }

    @Test fun bigMovesScaleTheBurst() {
        fun biggest(k: Float): Float {
            val (_, s, _) = run(k = k)
            s.advance(310f)
            return s.particles.maxOf { it.s1 }
        }
        assertEquals(biggest(1f) * 1.6f, biggest(1.6f), 1f)
    }

    @Test fun everyLookHasABuilderAndFinishesAfterItsImpact() {
        for (look in MoveLook.entries) {
            val (_, _, t) = run(look, FxKit.NORMAL)
            assertTrue("$look", t.impact in 0f..t.total)
            assertTrue("$look", t.done in t.impact..t.total)
        }
    }

    @Test fun playerTimesDivideBySpeed() {
        val (stage, s, t) = run()
        val player = MoveFxPlayer(stage, s, t, 2f) { error("no canvas in a JVM test") }
        assertEquals(155L, player.impactMs); assertEquals(345L, player.doneMs); assertEquals(550L, player.totalMs)
        assertFalse(stage.restores > 0)
        player.end(); player.end()
        assertEquals(1, stage.restores)                                    // once, however often it is ended
    }
}
