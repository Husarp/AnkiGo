package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class BattleMomentsTest {
    @Test fun throwReachesContactBeforeAbsorbingAndBouncesOntoTheGround() {
        assertEquals(120f, BattleMoments.THROW, 0f)
        assertEquals(190f, BattleMoments.ballX(120f, 190f, 350f), .001f)
        assertEquals(350f, BattleMoments.ballX(620f, 190f, 350f), .001f)
        assertEquals(97f, BattleMoments.ballY(620f, 150f, 97f, 139f, 1f), .001f)
        assertTrue(BattleMoments.ballY(370f, 150f, 97f, 139f, 1f) < 60f)
        assertEquals(139f, BattleMoments.ballY(1150f, 150f, 97f, 139f, 1f), .001f)
        assertTrue(BattleMoments.ballY(1100f, 150f, 97f, 139f, 1f) < 139f)
        assertEquals(0f, BattleMoments.absorbed(619f), 0f)
        assertEquals(.25f, BattleMoments.absorbed(760f), .001f)
        assertEquals(1f, BattleMoments.absorbed(900f), 0f)
    }
    @Test fun failedThrowGetsTwoShakesAndCaughtGetsThree() {
        assertEquals(1950f, BattleMoments.catchEnd(false), 0f)
        assertEquals(2350f, BattleMoments.catchEnd(true), 0f)
        for (start in listOf(1150f, 1550f)) {
            assertTrue(abs(BattleMoments.wobble(start + 75f, false)) > 15f)
            assertEquals(0f, BattleMoments.wobble(start + 300f, false), .001f)
        }
        assertEquals(0f, BattleMoments.wobble(2025f, false), .001f)
        assertTrue(abs(BattleMoments.wobble(2025f, true)) > 15f)
        assertEquals(0f, BattleMoments.wobble(2350f, true), .001f)
    }
    @Test fun evolutionChangesAtPeakLightAndEndsCleanly() {
        assertEquals(900f, BattleMoments.EVOLVE_SWAP, 0f)
        assertEquals(1f, BattleMoments.evolutionWhite(900f), 0f)
        assertEquals(1f, BattleMoments.evolutionWhite(1000f), 0f)
        assertEquals(0f, BattleMoments.evolutionWhite(1700f), 0f)
        assertEquals(0f, BattleMoments.evolutionWhite(2200f), 0f)
        for (t in 0..2200) assertTrue(BattleMoments.evolutionWhite(t.toFloat()) in 0f..1f)
    }
    @Test fun glowCoversBothSmallAndOversizedVisibleBoxesWithoutACap() {
        for (half in listOf(5f, 46f, 120f, 350f, 800f)) {
            val radius = BattleMoments.coverRadius(half)
            assertTrue("solid plate covers $half", radius * .62f > half * 1.4142f)
        }
        assertTrue(BattleMoments.coverRadius(350f) > 210f)
    }
    @Test fun ribbonsCrossBothDepthLayersWhileRisingAndFalling() {
        for (up in listOf(false, true)) for (i in 0 until 4) {
            val sides = (0..100).map { kotlin.math.sin(BattleMoments.ribbonAngle(i, it / 100f, up)) > 0f }.toSet()
            assertEquals(setOf(false, true), sides)
        }
        assertEquals(48f, BattleMoments.ribbonY(0f, true), .001f)
        assertEquals(-62f, BattleMoments.ribbonY(1f, true), .001f)
        assertEquals(-62f, BattleMoments.ribbonY(0f, false), .001f)
        assertEquals(48f, BattleMoments.ribbonY(1f, false), .001f)
    }
    @Test fun approvedBallColorsStayDistinctAndOtherBallsKeepTheirShellFamilies() {
        assertEquals(0xFFFF4646.toInt(), BallTrail.colors("poke-ball").main)
        assertEquals(0xFF4696FF.toInt(), BallTrail.colors("great-ball").main)
        assertEquals(0xFFFFD732.toInt(), BallTrail.colors("ultra-ball").main)
        assertEquals(0xFFB95AFF.toInt(), BallTrail.colors("master-ball").main)
        assertEquals(BallTrail.colors("nest-ball").main, BallTrail.colors("friend-ball").main)
        assertEquals(BallTrail.colors("love-ball").main, BallTrail.colors("heal-ball").main)
    }
    @Test fun sixInFourteenXpHeadsHaveSmallShapesAndTrailsStayOrbs() {
        assertEquals(6, (0 until 14).count { XpParticles.headKind(it) != XpParticles.HEAD })
        for (kind in XpParticles.SMALL_STAR..XpParticles.CROSS) assertEquals(2, (0 until 14).count { XpParticles.headKind(it) == kind })
        val p = XpParticles.plan(381)
        val box = XpParticles.Box(0f, 0f, 100f, 100f)
        for (i in p.sparks.indices) {
            val spark = p.sparks[i]
            val marks = XpParticles.at(p, spark.t0 + spark.dur / 2f, box, box, 1f)
            assertTrue(marks.any { it.kind == XpParticles.headKind(i) })
            assertTrue(marks.filter { it.kind >= XpParticles.SMALL_STAR }.all { it.r <= XpParticles.SPARK_UNITS * 1.25f })
            assertTrue(marks.any { it.kind == XpParticles.TRAIL_MARK })
        }
    }
}
