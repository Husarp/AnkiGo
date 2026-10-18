package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropFadeTest {
    @Test fun lastsFourHundredMillisecondsOrNothingWithReducedMotion() {
        assertEquals(400L, BackdropFade.duration(false))
        assertEquals(0L, BackdropFade.duration(true))
    }

    @Test fun opacityRisesFromZeroToOne() {
        assertEquals(0f, BackdropFade.alpha(0, false), 0f)
        assertEquals(0f, BackdropFade.alpha(-50, false), 0f)
        assertEquals(0.25f, BackdropFade.alpha(100, false), 1e-6f)
        assertEquals(0.5f, BackdropFade.alpha(200, false), 1e-6f)
        assertEquals(0.75f, BackdropFade.alpha(300, false), 1e-6f)
        assertEquals(1f, BackdropFade.alpha(400, false), 0f)
        assertEquals(1f, BackdropFade.alpha(9000, false), 0f)
        var prev = -1f
        for (t in 0L..400L step 20) { val a = BackdropFade.alpha(t, false); assertTrue(a >= prev); prev = a }
    }

    @Test fun reducedMotionIsAtOnceFromTheFirstInstant() {
        for (t in listOf(-5L, 0L, 1L, 200L, 400L)) assertEquals(1f, BackdropFade.alpha(t, true), 0f)
        assertFalse(BackdropFade.running(0, true))
    }

    @Test fun runsUntilFourHundredAndNotAfter() {
        assertTrue(BackdropFade.running(0, false))
        assertTrue(BackdropFade.running(399, false))
        assertFalse(BackdropFade.running(400, false))
        assertFalse(BackdropFade.running(5000, false))
    }

    @Test fun layerAlphaIsTheOpacityInBytes() {
        assertEquals(0, BackdropFade.alpha255(0, false))
        assertEquals(128, BackdropFade.alpha255(200, false))
        assertEquals(255, BackdropFade.alpha255(400, false))
        assertEquals(255, BackdropFade.alpha255(0, true))
    }

    @Test fun aPictureAppliedDuringAFadeFreezesTheBlendOnlyWhilePartWay() {
        assertEquals(BackdropFade.Outgoing.KEEP, BackdropFade.outgoingAt(false, 0))
        assertEquals(BackdropFade.Outgoing.KEEP, BackdropFade.outgoingAt(false, 300))
        assertEquals(BackdropFade.Outgoing.KEEP, BackdropFade.outgoingAt(true, 0))
        assertEquals(BackdropFade.Outgoing.SNAPSHOT, BackdropFade.outgoingAt(true, 1))
        assertEquals(BackdropFade.Outgoing.SNAPSHOT, BackdropFade.outgoingAt(true, 100))
        assertEquals(BackdropFade.Outgoing.SNAPSHOT, BackdropFade.outgoingAt(true, 399))
        assertEquals(BackdropFade.Outgoing.INCOMING, BackdropFade.outgoingAt(true, 400))
        assertEquals(BackdropFade.Outgoing.INCOMING, BackdropFade.outgoingAt(true, 5000))
    }
}
