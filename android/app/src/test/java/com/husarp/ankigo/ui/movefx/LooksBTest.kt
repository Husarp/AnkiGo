package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7-08: Thunderbolt, Psychic + aura template, Shadow Ball + orb template, Earthquake on a recording stage. Pure JVM. */
class LooksBTest {
    private val eps = 1e-2f
    private val unit = 2f
    private val frame = 1000f / 60f

    /** A stage that also remembers the jolt values. */
    private class JoltStage(unitPx: Float, reverse: Boolean) : MoveFxStage {
        val rec = RecStage(unitPx, reverse)
        val jolts = ArrayList<Float>()
        override val unit get() = rec.unit
        override val attackerX get() = rec.attackerX
        override val attackerY get() = rec.attackerY
        override val targetX get() = rec.targetX
        override val targetY get() = rec.targetY
        override fun pose(attacker: Boolean, dx: Float, dy: Float, sx: Float, sy: Float, matrix: FloatArray?) = rec.pose(attacker, dx, dy, sx, sy, matrix?.copyOf())
        override fun jolt(dy: Float) { jolts += dy }
        override fun restore() = rec.restore()
    }

    private class Run(val stage: JoltStage, val s: FxScript, val t: FxTimes)

    private fun run(look: MoveLook, kit: FxKit = FxKit.NORMAL, reverse: Boolean = false, react: Boolean = true, k: Float = 1f, seed: Long = 11L, palette: FxPalette = TEST_PALETTE): Run {
        val stage = JoltStage(unit, reverse)
        val p = MoveFxPlan(look, k, kit, palette)
        val s = MoveFx.script(stage, p, react, seed)!!
        return Run(stage, s, MoveFx.build(s, look))
    }

    private inline fun frames(r: Run, to: Float, each: (Float) -> Unit = {}) {
        var ms = 0f
        while (ms < to) { r.s.advance(ms); each(ms); ms += frame }
        r.s.advance(to); each(to)
    }

    private val allLooks = listOf(MoveLook.THUNDERBOLT, MoveLook.PSYCHIC, MoveLook.SHADOW_BALL, MoveLook.ORB_TEMPLATE, MoveLook.AURA_TEMPLATE, MoveLook.EARTHQUAKE)

    @Test fun timings() {
        run(MoveLook.THUNDERBOLT).t.let { assertEquals(1300f, it.total, 0f); assertEquals(220f, it.impact, 0f); assertEquals(920f, it.done, 0f) }
        run(MoveLook.PSYCHIC).t.let { assertEquals(1500f, it.total, 0f); assertEquals(100f, it.impact, 0f); assertEquals(1100f, it.done, 0f) }
        run(MoveLook.SHADOW_BALL).t.let { assertEquals(1700f, it.total, 0f); assertEquals(900f, it.impact, 0f); assertEquals(1500f, it.done, 0f) }
        run(MoveLook.ORB_TEMPLATE).t.let { assertEquals(1700f, it.total, 0f); assertEquals(900f, it.impact, 0f) }
        run(MoveLook.EARTHQUAKE).t.let { assertEquals(2200f, it.total, 0f); assertEquals(75f, it.impact, 0f); assertEquals(1500f, it.done, 0f) }   // PLAN 157: longer
        run(MoveLook.AURA_TEMPLATE).t.let { assertEquals(1000f, it.total, 0f); assertTrue(it.done <= it.total) }
        for (look in allLooks) run(look, react = false).t.let { assertTrue("$look", it.impact <= it.done && it.done <= it.total) }
    }

    @Test fun everyLookEndsInsideItsTotalAndLeavesNothingBehind() {
        for (look in allLooks) for (kit in FxKit.entries) for (reverse in listOf(false, true)) for (seed in longArrayOf(1L, 2L)) {
            val r = run(look, kit, reverse, k = 1.3f, seed = seed)
            var lastEnd = 0f
            frames(r, r.t.total + 50f) { for (p in r.s.particles) lastEnd = maxOf(lastEnd, p.t0 + p.life) }
            assertTrue("$look/$kit ends by ${r.t.total}: $lastEnd", lastEnd <= r.t.total + 1f)
            assertTrue("$look/$kit particles", r.s.particles.isEmpty()); assertTrue("$look/$kit procs", r.s.procs.isEmpty())
            assertEquals(0f, r.s.dim, 0f); assertEquals(0f, r.s.flashAlpha, 0f)
            val a = r.stage.rec.last(true)
            if (a != null) { assertEquals(0f, a.dx, eps); assertEquals(0f, a.dy, eps); assertEquals(1f, a.sy, eps); assertNull("$look attacker filter", a.matrix) }
        }
    }

    @Test fun particleCapIsRespected() {
        for (look in allLooks) {
            val r = run(look, FxKit.WATER, k = 1.6f)
            frames(r, r.t.total) { assertTrue(r.s.particles.size <= FxScript.MAX_PARTICLES) }
        }
    }

    // ---- Thunderbolt ----
    @Test fun thunderboltDimsFlashesAndFlickersTheTarget() {
        val r = run(MoveLook.THUNDERBOLT)
        var maxDim = 0f; var flashAt = -1f
        frames(r, 1300f) { ms ->
            maxDim = maxOf(maxDim, r.s.dim)
            if (r.s.flashAlpha > 0f && flashAt < 0f) flashAt = ms
            if (ms < 200f) assertEquals(0, r.stage.rec.count(false))
        }
        assertEquals(.5f, maxDim, .02f)
        assertTrue("flash at the impact: $flashAt", flashAt in 215f..240f)
        assertTrue(r.stage.rec.count(false) > 20)
    }

    @Test fun thunderboltFlickerIsBrightAndEndsAtRest() {
        val r = run(MoveLook.THUNDERBOLT)
        val m = ArrayList<FloatArray>()
        frames(r, 950f) { if (it in 230f..900f) r.stage.rec.last(false)?.matrix?.let { x -> m += x } }
        assertTrue(m.isNotEmpty())
        // brightness 1.2..2.4 with sepia + saturate: the red row is clearly above identity
        assertTrue(m.any { it[0] + it[1] + it[2] > 1.8f })
        val l = r.stage.rec.last(false)!!
        assertNull(l.matrix); assertEquals(0f, l.dx, eps)
    }

    @Test fun thunderboltImpactIsAtTheTargetInBothDirections() {
        for (reverse in listOf(false, true)) {
            val r = run(MoveLook.THUNDERBOLT, reverse = reverse)
            frames(r, 230f)
            val star = r.s.particles.first { it.img == "star_06" }
            assertEquals(r.stage.targetX, star.x0, eps); assertEquals(r.stage.targetY, star.y0, eps)
            assertEquals(220f, star.t0, 0f)
        }
    }

    @Test fun thunderboltBigMovesGrowTheStar() {
        fun s1(k: Float) = run(MoveLook.THUNDERBOLT, k = k).also { frames(it, 230f) }.s.particles.first { it.img == "star_06" }.s1
        assertEquals(s1(1f) * 1.5f, s1(1.5f), 1f)
    }

    @Test fun thunderboltNoReactionNeverPosesTheTarget() {
        val r = run(MoveLook.THUNDERBOLT, react = false)
        frames(r, 1300f)
        assertEquals(0, r.stage.rec.count(false))
    }

    // ---- Psychic ----
    @Test fun psychicFloatsTheAttackerGlowingAndWobblesTheTarget() {
        val r = run(MoveLook.PSYCHIC)
        var minY = 0f; var maxY = 0f; var tinted = false; var hue = false; var maxX = 0f
        frames(r, 1500f) { ms ->
            if (ms in 50f..1000f) r.stage.rec.last(true)?.let { minY = minOf(minY, it.dy); maxY = maxOf(maxY, it.dy); if (it.matrix != null) tinted = true }
            if (ms in 150f..1000f) r.stage.rec.last(false)?.let { if (it.matrix != null) hue = true; maxX = maxOf(maxX, Math.abs(it.dx)) }
        }
        assertTrue(minY < -2f * unit && maxY > 2f * unit)                       // bobs +-3 units
        assertTrue(tinted); assertTrue(hue); assertTrue(maxX > 1.5f * unit)
        val a = r.stage.rec.last(true)!!; val t = r.stage.rec.last(false)!!
        assertNull(a.matrix); assertNull(t.matrix); assertEquals(0f, t.dx, eps); assertEquals(1f, t.sx, eps); assertEquals(1f, t.sy, eps)
        assertTrue(r.stage.rec.poses.none { it.attacker && it.dx != 0f })       // floats only, never sideways
    }

    @Test fun psychicHasThreeWavesClosingOnTheTarget() {
        for (reverse in listOf(false, true)) {
            val r = run(MoveLook.PSYCHIC, reverse = reverse)
            val rings = HashSet<Float>()
            frames(r, 800f) { for (p in r.s.particles) if (p.img == "circle_02") rings += p.t0 }
            assertEquals(setOf(100f, 360f, 620f), rings)
            frames(r, 150f)
            val ring = r.s.particles.first { it.img == "circle_02" }
            assertEquals(r.stage.targetX, ring.x0, eps); assertEquals(r.stage.targetY, ring.y0, eps)
            assertTrue(ring.s0 > ring.s1)                                         // closing in
        }
    }

    @Test fun psychicNoReactionLeavesTheTargetAlone() {
        val r = run(MoveLook.PSYCHIC, react = false)
        frames(r, 1500f)
        assertEquals(0, r.stage.rec.count(false)); assertTrue(r.stage.rec.count(true) > 0)
    }

    @Test fun psychicSparklesStayNearTheTarget() {
        val r = run(MoveLook.PSYCHIC)
        var n = 0
        frames(r, 1100f) { for (p in r.s.particles) if (p.img == "magic_05") { n++; assertTrue(Math.abs(p.x0 - r.stage.targetX) <= 60f * unit + 1f) } }
        assertTrue(n > 100)
    }

    // ---- Aura template ----
    @Test fun auraNeverPosesOrFiltersTheTarget() {
        for (react in listOf(true, false)) for (kit in FxKit.entries) for (reverse in listOf(false, true)) {
            val r = run(MoveLook.AURA_TEMPLATE, kit, reverse, react)
            frames(r, 1000f)
            assertEquals("$kit", 0, r.stage.rec.count(false))
            assertTrue(r.stage.rec.count(true) > 0)
        }
    }

    @Test fun auraIsAroundTheUserInThePalette() {
        for (reverse in listOf(false, true)) {
            val r = run(MoveLook.AURA_TEMPLATE, FxKit.PLAIN, reverse)
            val tints = HashSet<Int>()
            frames(r, 700f) { for (p in r.s.particles) { tints += p.tint; assertTrue(Math.abs(p.x0 - r.stage.attackerX) <= 60f * unit + 1f) } }
            assertTrue(tints.all { it == TEST_PALETTE.glow || it == TEST_PALETTE.spark })
            assertTrue(TEST_PALETTE.glow in tints && TEST_PALETTE.spark in tints)
            assertTrue(TEST_PALETTE.deep !in tints)
            frames(r, 40f)
            val ring = r.s.particles.first { it.img == "circle_02" }
            assertEquals(r.stage.attackerX, ring.x0, eps); assertEquals(r.stage.attackerY, ring.y0, eps)
        }
    }

    @Test fun auraMixesInTheKitsOwnParticles() {
        fun imgs(kit: FxKit): Set<String?> { val r = run(MoveLook.AURA_TEMPLATE, kit, seed = 5L); val o = HashSet<String?>(); frames(r, 1000f) { r.s.particles.forEach { p -> o += p.img } }; return o }
        val plain = imgs(FxKit.PLAIN)
        assertEquals(setOf<String?>("circle_02", "light_02", "twirl_01", "magic_05"), plain)
        assertTrue("star_04" in imgs(FxKit.NORMAL))
        assertTrue("drop" in imgs(FxKit.WATER))
        assertTrue("spark_05" in imgs(FxKit.ELECTRIC))
        assertTrue("star_04" in imgs(FxKit.ICE))
        assertTrue("ps:purplewisp" in imgs(FxKit.GHOST))
        assertTrue(imgs(FxKit.GROUND).any { it?.startsWith("dirt") == true })
        assertTrue(null in imgs(FxKit.FIRE)); assertTrue(null in imgs(FxKit.GRASS))   // hand-drawn pixel art
    }

    @Test fun auraKitParticlesRise() {
        val r = run(MoveLook.AURA_TEMPLATE, FxKit.NORMAL, seed = 3L)
        val rising = ArrayList<FxParticle>()
        frames(r, 700f) { for (p in r.s.particles) if (p.img == "star_04" && p !in rising) rising += p }
        assertTrue(rising.isNotEmpty()); assertTrue(rising.all { it.vy < 0f })
    }

    // ---- Shadow Ball and the orb template ----
    @Test fun shadowBallDimsLightlyAndBurstsAtTheTarget() {
        for (reverse in listOf(false, true)) {
            val r = run(MoveLook.SHADOW_BALL, FxKit.GHOST, reverse)
            var maxDim = 0f
            frames(r, 905f) { maxDim = maxOf(maxDim, r.s.dim); if (it < 890f) assertTrue(r.s.particles.isEmpty()) }
            assertEquals(.12f, maxDim, .005f)
            val ring = r.s.particles.first { it.img == "circle_05" }
            assertEquals(900f, ring.t0, 0f)
            val spot = r.s.space.fromTarget(-6f, 4f)
            assertEquals(spot.x, ring.x0, eps); assertEquals(spot.y, ring.y0, eps)
            assertEquals(TEST_PALETTE.deep, ring.tint)
            assertTrue(r.s.particles.count { it.img == "smoke_08" } == 8 && r.s.particles.count { it.img == "star_04" } == 8)
        }
    }

    @Test fun shadowBallTargetFlinchesFromImpactOnAndDarkens() {
        val r = run(MoveLook.SHADOW_BALL, FxKit.GHOST)
        var dark = false; var peak = 0f
        frames(r, 1550f) { ms ->
            if (ms < 895f) assertEquals(0, r.stage.rec.count(false))
            if (ms > 900f) r.stage.rec.last(false)?.let { peak = maxOf(peak, it.dx); it.matrix?.let { m -> if (m[0] < 0.9f) dark = true } }
        }
        assertTrue(dark); assertTrue(peak > 3f * unit)
        val t = r.stage.rec.last(false)!!
        assertNull(t.matrix); assertEquals(0f, t.dx, eps)
    }

    @Test fun shadowBallNoReactionNeverPosesTheTarget() {
        val r = run(MoveLook.SHADOW_BALL, FxKit.GHOST, react = false)
        frames(r, 1700f); assertEquals(0, r.stage.rec.count(false)); assertTrue(r.s.particles.isEmpty())
    }

    @Test fun orbTemplateBurstsInThePalette() {
        val pal = FxPalette(0xFF3F4757.toInt(), 0xFF9EABC2.toInt(), 0xFF6F7889.toInt(), 0xFFFFFFFF.toInt())
        val r = run(MoveLook.ORB_TEMPLATE, FxKit.PLAIN, palette = pal)
        frames(r, 905f)
        val by = r.s.particles.groupBy { it.img }.mapValues { e -> e.value.map { it.tint }.toSet() }
        assertEquals(setOf(pal.deep, pal.glow), by["circle_05"]); assertEquals(setOf(pal.smoke), by["smoke_08"]); assertEquals(setOf(pal.spark), by["star_04"])
        assertTrue(r.s.particles.any { it.img == "circle_05" && it.tint == pal.glow && it.add })
    }

    @Test fun orbTemplateAddsTheKitsParticlesOnlyWithAKit() {
        fun imgs(kit: FxKit): Set<String?> { val r = run(MoveLook.ORB_TEMPLATE, kit, seed = 4L); val o = HashSet<String?>(); frames(r, 1700f) { r.s.particles.forEach { p -> o += p.img } }; return o }
        val plain = imgs(FxKit.PLAIN)
        assertEquals(setOf<String?>("circle_05", "smoke_08", "star_04"), plain)
        assertTrue("circle_02" in imgs(FxKit.WATER) && "drop" in imgs(FxKit.WATER))
        assertTrue("spark_05" in imgs(FxKit.ELECTRIC) || "spark_01" in imgs(FxKit.ELECTRIC))
        assertTrue(null in imgs(FxKit.FIRE))
        assertTrue("twirl_01" in imgs(FxKit.PSYCHIC))
    }

    @Test fun orbSizeFollowsK() {
        fun big(k: Float) = run(MoveLook.ORB_TEMPLATE, FxKit.PLAIN, k = k).also { frames(it, 905f) }.s.particles.first { it.img == "circle_05" }.s1
        assertEquals(big(1f) * 1.6f, big(1.6f), 1f)
    }

    // ---- Earthquake ----
    @Test fun earthquakeJoltsTheBackgroundAndEndsAtZero() {
        val r = run(MoveLook.EARTHQUAKE, FxKit.GROUND, k = 1f)
        frames(r, 2200f)
        val j = r.stage.jolts
        assertTrue(j.isNotEmpty())
        assertEquals(0f, j.last(), 0f)
        val q = run(MoveLook.EARTHQUAKE); q.s.advance(140f)
        assertEquals(-24f * unit, q.stage.jolts.last(), eps)                    // PLAN 157: the deepest keyframe (140 ms): -24 x unit (was -7.5)
        assertTrue("it jolts up and down", j.any { it < -20f * unit } && j.any { it > 5f * unit })
        assertTrue(j.all { it >= -24f * unit - eps && it <= 8f * unit + eps })
        val a = r.stage.rec.last(true)!!; val t = r.stage.rec.last(false)!!
        assertEquals(0f, a.dy, eps); assertEquals(1f, a.sy, eps); assertEquals(0f, t.dy, eps)
        assertEquals("the sideways shake is over", 0f, a.dx, eps); assertEquals(0f, t.dx, eps)
    }

    @Test fun earthquakeShakesBothMonsSidewaysAndItFadesOut() {
        val r = run(MoveLook.EARTHQUAKE)
        val early = ArrayList<Float>(); val late = ArrayList<Float>()
        frames(r, 1500f) { ms -> r.stage.rec.last(true)?.let { if (ms < 300f) early += Math.abs(it.dx) else if (ms > 1300f) late += Math.abs(it.dx) } }
        assertTrue("a clear shake at the start", early.max() > EarthquakeFx.SIDE * unit * .7f)
        assertTrue("it fades out", late.max() < EarthquakeFx.SIDE * unit * .2f)
        assertTrue(r.stage.rec.poses.any { !it.attacker && it.dx != 0f })
    }

    @Test fun earthquakeSquashesTheAttackerOnTheSecondBeatAndScalesWithK() {
        fun deepest(k: Float): Float { val r = run(MoveLook.EARTHQUAKE, k = k); frames(r, 1100f); return r.stage.jolts.min() }
        assertEquals(deepest(1f) * 1.5f, deepest(1.5f), .5f)
        val r = run(MoveLook.EARTHQUAKE)
        var minSy = 1f; var at = 0f
        frames(r, 1100f) { ms -> r.stage.rec.last(true)?.let { if (it.sy < minSy) { minSy = it.sy; at = ms } } }
        assertEquals(.85f, minSy, .02f); assertEquals(175f, at, 20f)   // 60 fps frames land either side of the 175 ms key
    }

    @Test fun earthquakeBothMonsBounceAndAnEarthquakeWithoutReactionOnlyShakesTheAttacker() {
        val r = run(MoveLook.EARTHQUAKE)
        frames(r, 1100f)
        assertTrue(r.stage.rec.poses.any { !it.attacker && it.dy != 0f })
        val n = run(MoveLook.EARTHQUAKE, react = false)
        frames(n, 1800f)
        assertEquals(0, n.stage.rec.count(false)); assertTrue(n.stage.jolts.isNotEmpty())
    }

    @Test fun earthquakeDirtFliesFromBothFeetAndTheMiddleWithoutCracks() {
        for (reverse in listOf(false, true)) {
            val r = run(MoveLook.EARTHQUAKE, reverse = reverse)
            val seen = HashSet<FxParticle>()
            val xs = ArrayList<Float>()
            val imgs = HashSet<String?>()
            frames(r, 1400f) { r.s.particles.forEach { p -> imgs += p.img; if (p.img?.startsWith("dirt") == true && seen.add(p)) xs += p.x0 } }
            assertTrue(imgs.all { it == "dirt_01" || it == "dirt_02" || it == "smoke_08" })   // nothing else: no cracks
            val ax = r.stage.attackerX; val tx = r.stage.targetX; val mid = (ax + tx) / 2f
            val tol = 90f * unit + 1f
            assertTrue(xs.any { Math.abs(it - ax) <= tol }); assertTrue(xs.any { Math.abs(it - tx) <= tol }); assertTrue(xs.any { Math.abs(it - mid) <= tol })
        }
    }

    @Test fun earthquakeDustPuffsAreThreeWavesAtBothFeetAndTheMiddle() {
        val r = run(MoveLook.EARTHQUAKE)
        val smoke = ArrayList<FxParticle>()
        frames(r, 1000f) { r.s.particles.forEach { p -> if (p.img == "smoke_08" && p !in smoke) smoke += p } }
        assertEquals(9, smoke.size)
        assertEquals(setOf(150f, 550f, 950f), smoke.map { it.t0 }.toSet())
    }

    @Test fun earthquakeThrowsMoreAndBiggerDirtWithRockChips() {
        val r = run(MoveLook.EARTHQUAKE)
        val dirt = LinkedHashSet<FxParticle>()
        frames(r, 1400f) { r.s.particles.forEach { p -> if (p.img?.startsWith("dirt") == true) dirt += p } }
        assertTrue("about 3 x the R7-08 look's ~20 clods: ${dirt.size}", dirt.size >= 45)
        assertTrue("bigger clods", dirt.maxOf { it.s0 } > 34f * unit)
        assertTrue("grey rock chips among the dirt", dirt.any { it.tint == 0xFF7A7068.toInt() } && dirt.any { it.tint == 0xFF9A7448.toInt() })
    }

    @Test fun deterministicForASeed() {
        for (look in allLooks) {
            fun sig(): String { val r = run(look, FxKit.ELECTRIC, seed = 99L); val sb = StringBuilder(); frames(r, 900f) { for (p in r.s.particles) sb.append(p.x0).append(p.life) }; return sb.toString() }
            assertEquals("$look", sig(), sig())
        }
    }

    @Test fun everyBuilderIsRegistered() {
        for (look in allLooks) assertNotNull(run(look))
        assertFalse(allLooks.isEmpty())
    }
}
