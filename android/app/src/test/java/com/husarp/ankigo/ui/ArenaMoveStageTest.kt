package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R7-05: the pure parts of the move wiring: the pixel unit, the sprite centre, and how long the player waits after an attack. */
class ArenaMoveStageTest {
    private val g = ArenaMoveStage.Geo

    @Test fun theMockupsOwnDistanceIsUnitOne() {
        // attacker (140, 190), target (350, 105), target sprite 92 high: exactly the mockup
        assertEquals(1f, g.unit(140f, 190f, 350f, 105f, 92f), 0.01f)
    }

    @Test fun aVeryWideArenaStopsAtOnePointFourOfTheSpriteSize() {
        // distance 2260 = unit 10, but the target is 92 px high: ref 1, so at most 1.4
        assertEquals(1.4f, g.unit(0f, 0f, 2260f, 0f, 92f), 0.001f)
    }

    @Test fun aVeryShortDistanceStopsAtZeroPointSeven() {
        assertEquals(0.7f, g.unit(0f, 0f, 22.6f, 0f, 92f), 0.001f)
    }

    @Test fun theLimitsFollowTheTargetsDrawnHeight() {
        // a 184 px high target: ref 2, so the unit stays within 1.4 .. 2.8
        assertEquals(1.4f, g.unit(0f, 0f, 226f, 0f, 184f), 0.001f)
        assertEquals(2.8f, g.unit(0f, 0f, 22600f, 0f, 184f), 0.001f)
        assertEquals(2f, g.unit(0f, 0f, 452f, 0f, 184f), 0.001f)
    }

    @Test fun noDrawnHeightKeepsTheRawUnit() {
        assertEquals(2f, g.unit(0f, 0f, 452f, 0f, 0f), 0.001f)
    }

    @Test fun theCentreIsOfTheDrawnImageSittingOnTheBottom() {
        // a 100 x 80 view at (30, 50), image 60 high: bottom-centred, so its middle is 30 above the bottom edge
        val (x, y) = g.centre(30f, 50f, 100f, 80f, 60f)
        assertEquals(80f, x, 0f)
        assertEquals(100f, y, 0f)
    }

    @Test fun theWaitCoversTheLooksEndAndTheHitsAfterTime() {
        assertEquals(1000L, g.waitMs(1000, 400, 1.0))      // the look is longer than impact + 500
        assertEquals(900L, g.waitMs(500, 400, 1.0))        // impact + 500
        assertEquals(650L, g.waitMs(500, 400, 0.5))        // the fast pace: impact + 250
        assertEquals(401L, g.waitMs(0, 400, 0.0))          // never zero time after the hit
    }
}
