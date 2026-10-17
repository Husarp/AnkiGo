package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R7-04: Ember, Water Gun jet, Vine Whip and Ice Beam on a recording stage: timings, counts, mirroring, reactions, pure geometry. Pure JVM. */
class LooksATest {
    private val eps = 1e-2f
    private val unit = 2f

    private fun run(look: MoveLook, k: Float = 1f, unit: Float = this.unit, reverse: Boolean = false, react: Boolean = true,
                    kit: FxKit = FxKit.NORMAL, seed: Long = 11L): Triple<RecStage, FxScript, FxTimes> {
        val stage = RecStage(unit, reverse)
        val s = MoveFx.script(stage, plan(look, kit, k), react, seed)!!
        val t = MoveFx.build(s, look)
        return Triple(stage, s, t)
    }

    /** Runs the script on from where it is (its clock) to [to], frame by frame. */
    private fun frames(s: FxScript, to: Float, step: Float = 1000f / 60f) {
        var ms = s.clock
        while (ms < to) { s.advance(ms); ms += step }
        s.advance(to)
    }

    private val looks = listOf(MoveLook.EMBER, MoveLook.WATER_JET, MoveLook.VINE_WHIP, MoveLook.ICE_BEAM)

    // ---- all four ----
    @Test fun noCrashAnySizeDirectionReaction() {
        for (look in looks) for (u in floatArrayOf(.05f, 1f, 4f)) for (rev in listOf(false, true)) for (react in listOf(false, true)) for (k in floatArrayOf(.8f, 1f, 1.6f)) {
            val (stage, s, t) = run(look, k, u, rev, react)
            assertTrue("$look times", t.impact > 0f && t.impact <= t.done && t.done <= t.total)
            var ms = 0f
            var peak = 0
            while (ms <= t.total + 50f) { s.advance(ms); peak = maxOf(peak, s.particles.size); ms += 1000f / 30f }
            assertTrue("$look cap", peak <= FxScript.MAX_PARTICLES)
            for (p in stage.poses) assertTrue("$look pose finite", p.dx.isFinite() && p.dy.isFinite() && p.sx.isFinite() && p.sy.isFinite())
            assertEquals("$look restore is the player's job", 0, stage.restores)
            if (!react) assertEquals("$look must not touch the target", 0, stage.count(false))
        }
    }

    @Test fun registryUsesTheOwnLooks() {
        val e = run(MoveLook.EMBER).second
        val w = run(MoveLook.WATER_JET).second
        val v = run(MoveLook.VINE_WHIP).second
        val i = run(MoveLook.ICE_BEAM).second
        assertEquals(10, e.procs.size)                                  // one proc per fireball
        assertEquals(1, w.procs.size)                                   // the jet
        assertEquals(1, v.procs.size)                                   // the vine buffer
        assertEquals(10, i.procs.size)                                  // 4 shards + 2 iceballs + 3 wisps + the cube
    }

    // ---- Ember ----
    @Test fun emberFiresTenTimesKFireballs() {
        assertEquals(10, EmberFx.count(1f)); assertEquals(16, EmberFx.count(1.6f)); assertEquals(8, EmberFx.count(.8f)); assertEquals(12, EmberFx.count(1.2f))
        for (k in floatArrayOf(.8f, 1f, 1.25f, 1.6f)) assertEquals(EmberFx.count(k), run(MoveLook.EMBER, k).second.procs.size)
    }

    @Test fun emberStreamIsUnevenWithinTheJsBounds() {
        var pairs = 0
        val gaps = HashSet<Int>()
        for (seed in 1..300) {
            val n = 10
            val st = EmberFx.stream(Random(seed), n)
            assertEquals(n, st.times.size); assertEquals(n, st.offsets.size)
            assertEquals(90f, st.times[0], 0f)
            var i = 0
            var prevStart = -1f
            while (i < n) {
                val start = st.times[i]
                if (prevStart >= 0f) {                                  // between two slots: .35 .. 1.9 x the base gap
                    val gap = start - prevStart
                    assertTrue("gap $gap", gap >= .35f * 55f - eps && gap <= 1.9f * 55f + eps)
                    gaps += (gap / 4f).toInt()
                }
                if (st.offsets[i] == -9f) {                             // a pair: the second within 12 ms, 9 units apart each way
                    assertTrue("pair at the very end", i < n - 1)
                    assertEquals(9f, st.offsets[i + 1], 0f)
                    val d = st.times[i + 1] - start
                    assertTrue("pair gap $d", d >= 0f && d <= 12f)
                    pairs++
                    prevStart = start
                    i += 2
                } else {
                    assertEquals(0f, st.offsets[i], 0f)
                    prevStart = start
                    i++
                }
            }
        }
        assertTrue("pairs happen ($pairs)", pairs in 150..900)          // 30 % of the slots, about 2.4 pairs per 10 balls
        assertTrue("gaps vary (${gaps.size} buckets)", gaps.size > 12)
    }

    @Test fun emberTimingsAndStreamLength() {
        for (seed in 1L..20L) {
            val (_, _, t) = run(MoveLook.EMBER, seed = seed)
            assertTrue("first lands ~ 305 ms: ${t.impact}", t.impact in 290f..322f)
            val last = t.total - 700f
            assertEquals(last + 360f, t.done, eps)                       // the shake runs from 260 ms for (last + 100)
            assertTrue("stream ends at $last", last in 350f..1300f)
        }
        val (_, _, noReact) = run(MoveLook.EMBER, react = false)
        assertEquals(noReact.impact, noReact.done, 0f)
    }

    @Test fun emberTargetFlickersSepiaThenSettles() {
        val (stage, s, t) = run(MoveLook.EMBER)
        frames(s, 250f); assertEquals(0, stage.count(false))
        frames(s, 600f)
        assertTrue(stage.count(false) > 0)
        val m = stage.poses.last { !it.attacker && it.matrix != null }.matrix!!
        assertTrue("not the identity", Math.abs(m[0] - 1f) > .01f || Math.abs(m[1]) > .01f)
        frames(s, t.total)
        val end = stage.last(false)!!
        assertNull(end.matrix); assertEquals(0f, end.dx, eps); assertEquals(0f, end.dy, eps)
    }

    @Test fun emberAttackerRecoilsAwayAndMirrors() {
        val (a, sa, _) = run(MoveLook.EMBER)
        sa.advance(100f)
        assertTrue(a.last(true)!!.dx < -5f * unit)                       // 10 units back, away from the target on the right
        val (b, sb, _) = run(MoveLook.EMBER, reverse = true)
        sb.advance(100f)
        assertTrue(b.last(true)!!.dx > 5f * unit)                        // and away from the target on the left
        sb.advance(250f); assertEquals(0f, b.last(true)!!.dx, .1f)
    }

    @Test fun emberBurstsAtTheTargetEmbersAtTheLanding() {
        val (_, s, t) = run(MoveLook.EMBER)
        frames(s, t.impact + 20f)
        // the landing's glow ring + 7 pixel embers (+ some trail embers)
        assertTrue(s.particles.count { it.pix != null } >= 7)
        assertTrue(s.particles.any { it.img == "circle_05" && it.add })
    }

    // ---- Water jet ----
    @Test fun waterJetTimings() {
        val (_, _, t) = run(MoveLook.WATER_JET)
        assertEquals(290f, t.impact, 0f)
        assertEquals(120f + 170f + 420f + 170f + 650f, t.total, 0f)
        assertEquals(290f + 420f + 140f, t.done, 0f)
        val big = run(MoveLook.WATER_JET, k = 1.6f).third
        assertEquals(290f, big.impact, 0f)
        assertEquals(t.total + 420f * .6f, big.total, eps)               // the stream time grows with k
        assertEquals(t.done + 420f * .6f, big.done, eps)
        assertEquals(290f, run(MoveLook.WATER_JET, react = false).third.done, 0f)
    }

    @Test fun waterJetShapeGrowsHoldsAndDrains() {
        val shape = WaterJetShape()
        val g = WaterJetFx.G
        fun fill(ms: Float, k: Float = 1f, dir: Float = 1f, ax: Float = 100f, bx: Float = 400f) =
            shape.fill(ax, 300f, bx, 100f, 2f, dir, k, WaterJetFx.hold(k), ms, ms + WaterJetFx.S)
        fill(0f)
        for (i in 0..shape.n) { assertEquals(100f, shape.pts[i * 2], 1e-3f); assertEquals(300f, shape.pts[i * 2 + 1], 1e-3f) }
        fill(g)                                                         // the head is at the target, the root at the attacker
        assertEquals(100f, shape.pts[0], 1e-3f); assertEquals(300f, shape.pts[1], 1e-3f)
        assertEquals(400f, shape.pts[shape.n * 2], 3.5f * 2f + 1f); assertEquals(100f, shape.pts[shape.n * 2 + 1], 3.5f * 2f + 1f)
        val wide = shape.ws.copyOf()
        assertTrue(wide[shape.n] > wide[0])                              // wider toward the target
        fill(g, k = 1.5f)
        for (i in 0..shape.n) assertEquals(wide[i] * 1.5f, shape.ws[i], wide[i] * .001f)   // the width scales with k
        // draining: at k = 1 the root has left the attacker 50 ms into the drain, at k = 1.6 the stream is still held
        val ms = g + WaterJetFx.hold(1f) + 50f
        fill(ms, k = 1f); assertTrue(shape.pts[0] > 110f)
        fill(ms, k = 1.6f); assertEquals(100f, shape.pts[0], 1e-3f)
        fill(g + WaterJetFx.hold(1f) + WaterJetFx.OUT); assertEquals(shape.pts[0], shape.pts[shape.n * 2], 3.5f * 2f + 1f)
    }

    @Test fun waterJetShapeIsFiniteEverywhere() {
        val shape = WaterJetShape()
        for (dir in floatArrayOf(1f, -1f)) for (ms in floatArrayOf(0f, 100f, 170f, 400f, 700f, 800f)) {
            shape.fill(300f, 120f, 40f, 40f, 1f, dir, 1.2f, 500f, ms, ms)     // wild one attacks to the left (dir = -1), tiny jet
            for (v in shape.pts) assertTrue(v.isFinite())
            for (v in shape.ws) assertTrue(v.isFinite() && v >= 0f)
        }
        shape.fill(10f, 10f, 10f, 10f, 1f, 1f, 1f, 420f, 100f, 100f)         // zero length
        for (v in shape.pts) assertTrue(v.isFinite())
    }

    @Test fun waterJetSplashesFromTheLanding() {
        val (_, s, _) = run(MoveLook.WATER_JET)
        frames(s, 600f)
        assertTrue(s.particles.count { it.img == "drop" } > 10)
        assertTrue(s.particles.size <= FxScript.MAX_PARTICLES)
        val (_, s2, _) = run(MoveLook.WATER_JET)
        frames(s2, 250f)                                                // before the head arrives: nothing splashes
        assertEquals(0, s2.particles.count { it.img == "drop" })
    }

    @Test fun waterJetMirrorsRecoilAndKnockBack() {
        val (a, sa, t) = run(MoveLook.WATER_JET, reverse = true)
        sa.advance(75f); assertTrue(a.last(true)!!.dx > 0f)             // away from a target on the left
        frames(sa, t.done)
        val left = a.poses.filter { !it.attacker }.map { it.dx }
        assertTrue("pushed away from the attacker (to the left): ${left.average()}", left.average() < -5.0)
        val (b, sb, _) = run(MoveLook.WATER_JET)
        frames(sb, t.done)
        assertTrue(b.poses.filter { !it.attacker }.map { it.dx }.average() > 5.0)
    }

    // ---- Vine Whip ----
    @Test fun vineWhipHasThreeVinesFourForBigMoves() {
        assertEquals(3, VineWhipFx.vinesFor(.8f).size); assertEquals(3, VineWhipFx.vinesFor(1f).size); assertEquals(3, VineWhipFx.vinesFor(1.34f).size)
        assertEquals(4, VineWhipFx.vinesFor(1.35f).size); assertEquals(4, VineWhipFx.vinesFor(1.6f).size)
        // the JS three keep their places; the fourth leaves last
        val three = VineWhipFx.vinesFor(1f)
        assertEquals(-38f, three[0].gx, 0f); assertEquals(30f, three[1].gx, 0f); assertEquals(-2f, three[2].gx, 0f)
        assertEquals(0f, three[0].t0, 0f); assertEquals(210f, three[1].t0, 0f); assertEquals(400f, three[2].t0, 0f)
        assertTrue(VineWhipFx.vinesFor(1.6f)[3].t0 > three[2].t0)
    }

    @Test fun vineWhipTimingsAndDirt() {
        val (_, s, t) = run(MoveLook.VINE_WHIP)
        assertEquals(409f, t.impact, eps)                                // first flick: 220 + 70 + 0.85 x 140
        assertEquals(400f + 930f + 450f, t.total, 0f)
        assertEquals(400f + 409f + 320f, t.done, eps)
        assertEquals(409f, run(MoveLook.VINE_WHIP, react = false).third.done, eps)
        val big = run(MoveLook.VINE_WHIP, k = 1.6f).third
        assertEquals(580f + 930f + 450f, big.total, 0f)
        assertEquals(409f, big.impact, eps)
        s.advance(1f)
        assertEquals(6, s.particles.count { it.pix != null })           // the first vine breaks the ground: 6 dirt bits
    }

    @Test fun vineWhipTargetIsPushedByEachFlick() {
        val (stage, s, t) = run(MoveLook.VINE_WHIP)
        frames(s, 400f); assertEquals(0, stage.count(false))
        frames(s, 520f); assertTrue(stage.count(false) > 0)
        frames(s, t.total)
        assertEquals(0f, stage.last(false)!!.dx, eps)
        assertEquals(0, stage.count(true))                               // the attacker stays still
    }

    private fun lean(r: VineRaster, bx: Float, by: Float): Float {      // sum of the horizontal offsets of the vine pixels above the base
        var sum = 0f
        for (y in 0 until by.toInt() - 1) for (x in 0 until r.bw) if (r.pix[y * r.bw + x] != 0) sum += x + .5f - bx
        return sum
    }

    @Test fun vineRasterLeansAwayThenLashesTowardItsSide() {
        for (dir in floatArrayOf(1f, -1f)) {
            val r = VineRaster(120, 80, 1f)
            r.clear(); r.vine(28f, 60f, 60f, dir, 0f)
            assertEquals("nothing at the very start", 0, r.pix.count { it != 0 })
            r.clear(); r.vine(28f, 60f, 60f, dir, 150f)
            assertTrue("a small vine grows", r.pix.count { it != 0 } > 30)
            r.clear(); r.vine(28f, 60f, 60f, dir, VineWhipFx.G + VineWhipFx.WAIT - 1f)
            val rest = lean(r, 60f, 60f)
            assertTrue("rests leaning away: $rest", rest * dir < 0f)
            r.clear(); r.vine(28f, 60f, 60f, dir, VineWhipFx.G + VineWhipFx.WAIT + VineWhipFx.F)
            val lash = lean(r, 60f, 60f)
            assertTrue("lashes toward its side: $lash", lash * dir > 0f)
            r.clear(); r.vine(28f, 60f, 60f, dir, VineWhipFx.TOT - 1f)
            assertTrue("sunk again", r.pix.count { it != 0 } < 40)
        }
    }

    @Test fun vineRasterMirrorsRoughly() {
        val a = VineRaster(120, 80, 1f); val b = VineRaster(120, 80, 1f)
        val ms = VineWhipFx.G + VineWhipFx.WAIT + VineWhipFx.F
        a.vine(28f, 60f, 60f, 1f, ms); b.vine(28f, 60f, 60f, -1f, ms)
        val na = a.pix.count { it != 0 }; val nb = b.pix.count { it != 0 }
        assertTrue("$na vs $nb", Math.abs(na - nb) < na * .1f)
    }

    @Test fun vineRasterGrowsWithKAndStaysInItsBuffer() {
        val small = VineRaster(200, 120, 1f); val big = VineRaster(200, 120, 1.6f)
        val ms = VineWhipFx.G + VineWhipFx.WAIT + 10f
        small.vine(28f, 100f, 100f, 1f, ms); big.vine(28f, 100f, 100f, 1f, ms)
        assertTrue(big.pix.count { it != 0 } > small.pix.count { it != 0 } * 1.5f)
        // base far outside / tiny buffer / past the end of life: nothing throws
        val tiny = VineRaster(1, 1, 1.6f)
        for (ms2 in floatArrayOf(-50f, 0f, 100f, 300f, 450f, 700f, 929f, 5000f)) { tiny.clear(); tiny.vine(28f, 40f, -30f, 1f, ms2); tiny.vine(28f, .5f, .5f, -1f, ms2) }
        val edge = VineRaster(30, 20, 1f)
        for (ms2 in 0..930 step 31) { edge.clear(); edge.vine(28f, 2f, 18f, -1f, ms2.toFloat()); edge.vine(28f, 28f, 18f, 1f, ms2.toFloat()) }
    }

    @Test fun vineGrowthAndWhipCurvesAreContinuous() {
        var prev = VineWhipFx.grow(0f)
        assertEquals(0f, prev, eps)
        assertEquals(1f, VineWhipFx.grow(VineWhipFx.G), eps)
        assertEquals(1f, VineWhipFx.grow(VineWhipFx.TOT - VineWhipFx.S), eps)
        assertEquals(0f, VineWhipFx.grow(VineWhipFx.TOT), eps)
        var w = VineWhipFx.whipAt(0f)
        assertEquals(0f, w, 0f)
        var ms = 0f
        while (ms <= VineWhipFx.TOT) {
            val nw = VineWhipFx.whipAt(ms); val g = VineWhipFx.grow(ms)
            assertTrue("whip jump at $ms", Math.abs(nw - w) < .12f)
            assertTrue("grow jump at $ms", Math.abs(g - prev) < .1f)
            assertTrue(nw in 0f..1.1001f)
            w = nw; prev = g; ms += 5f
        }
        assertEquals(1.1f, VineWhipFx.whipAt(VineWhipFx.G + VineWhipFx.WAIT + VineWhipFx.F), eps)
    }

    // ---- Ice Beam ----
    @Test fun iceBeamTimings() {
        val (_, _, t) = run(MoveLook.ICE_BEAM)
        assertEquals(150f, t.impact, 0f); assertEquals(1610f, t.done, 0f); assertEquals(2140f, t.total, 0f)
        assertEquals(150f, run(MoveLook.ICE_BEAM, react = false).third.done, 0f)
        assertEquals(2140f, run(MoveLook.ICE_BEAM, k = 1.6f).third.total, 0f)
    }

    @Test fun iceBeamTargetTurnsColdShiversThenIsKnockedBack() {
        val (stage, s, t) = run(MoveLook.ICE_BEAM)
        frames(s, 420f); assertEquals(0, stage.count(false))
        frames(s, 800f)
        val cold = stage.last(false)!!.matrix
        assertNotNull(cold)
        val want = FxColor.chain(FxColor.saturate(.5f), FxColor.brightness(1.18f), FxColor.hueRotate(-8f))
        for (i in want.indices) assertEquals(want[i], cold!![i], 1e-4f)
        frames(s, 1250f)                                                 // shivering in the last 15 % of the freeze
        val shiver = stage.poses.filter { !it.attacker }.takeLast(20).map { it.dx }
        assertTrue(shiver.any { Math.abs(it) > .1f } && shiver.all { Math.abs(it) <= 1.5f * unit + eps })
        frames(s, 1500f)                                                 // after the shatter: pushed 6 units, no filter any more
        assertTrue(stage.poses.filter { !it.attacker }.takeLast(5).all { it.matrix == null })
        frames(s, t.total)
        assertEquals(0f, stage.last(false)!!.dx, eps); assertNull(stage.last(false)!!.matrix)
        assertEquals(0, stage.count(true))
    }

    @Test fun iceBeamShattersIntoEighteenShards() {
        val (_, s, _) = run(MoveLook.ICE_BEAM)
        frames(s, 1280f); assertEquals(0, s.particles.count { it.draw != null })
        s.advance(1300f)
        assertEquals(18, s.particles.count { it.draw != null })
        assertTrue(s.particles.count { it.img == "star_04" } >= 10)
        assertTrue(s.particles.size <= FxScript.MAX_PARTICLES)
    }

    @Test fun iceCubeGrowsWithK() {
        val s1 = MoveFx.script(RecStage(unit), plan(MoveLook.ICE_BEAM, k = 1f), true, 3L)!!
        val s2 = MoveFx.script(RecStage(unit), plan(MoveLook.ICE_BEAM, k = 1.5f), true, 3L)!!
        val a = IceCube(s1, 1f); val b = IceCube(s2, 1.5f)
        assertEquals(100f, a.w, 0f); assertEquals(96f, a.h, 0f)
        assertEquals(150f, b.w, 0f); assertEquals(144f, b.h, 0f)
    }

    @Test fun iceCubeCurvesFormShineCrack() {
        assertEquals(.7f, IceBeamFx.scaleAt(IceBeamFx.F0), eps)
        assertEquals(1.06f, IceBeamFx.scaleAt(IceBeamFx.F0 + IceBeamFx.FORM), eps)
        assertEquals(1f, IceBeamFx.scaleAt(IceBeamFx.F0 + IceBeamFx.FORM + 200f), eps)
        assertEquals(0f, IceBeamFx.alphaAt(IceBeamFx.F0), eps); assertEquals(1f, IceBeamFx.alphaAt(IceBeamFx.F0 + IceBeamFx.FORM), eps)
        assertEquals(0f, IceBeamFx.crackAt(IceBeamFx.CRACK - 1f), 0f); assertEquals(1f, IceBeamFx.crackAt(IceBeamFx.BREAK), eps)
        assertFalse(IceBeamFx.crackAt(1200f) <= 0f)
    }
}
