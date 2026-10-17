package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-X4 (PLAN 34): swipe vs tap, and when the turn is ignored. */
class SwipeTurnTest {
    private val slop = 8f

    @Test fun aShortWobbleIsATap() {
        assertFalse(SwipeTurn.isSwipe(5f, 3f, slop))
        assertFalse(SwipeTurn.isSwipe(-23f, 0f, slop))   // just under three slops
    }

    @Test fun aClearHorizontalDragIsASwipeEitherWay() {
        assertTrue(SwipeTurn.isSwipe(24f, 4f, slop))
        assertTrue(SwipeTurn.isSwipe(-60f, 10f, slop))
    }

    @Test fun aMostlyVerticalDragIsNotASwipe() {
        assertFalse(SwipeTurn.isSwipe(30f, 30f, slop))
        assertFalse(SwipeTurn.isSwipe(30f, -25f, slop))   // not 1.5 times wider than tall
        assertFalse(SwipeTurn.isSwipe(2f, 80f, slop))
    }

    @Test fun trackerFiresOnceAndThenSwallowsTheTouch() {
        val t = SwipeTracker(slop)
        t.down(100f, 100f)
        assertFalse(t.move(110f, 101f))
        assertTrue(t.move(130f, 102f))
        assertFalse("only once per touch", t.move(160f, 100f))
        assertTrue(t.swiped)
        assertTrue("the UP of a swipe must not click", t.end())
        assertFalse(t.swiped)
    }

    @Test fun aTapEndsWithoutASwipe() {
        val t = SwipeTracker(slop)
        t.down(10f, 10f)
        t.move(13f, 12f)
        assertFalse(t.end())
    }

    @Test fun nextTouchStartsFresh() {
        val t = SwipeTracker(slop)
        t.down(0f, 0f); t.move(50f, 0f); t.end()
        t.down(50f, 0f)
        assertFalse(t.move(55f, 0f))
        assertTrue(t.move(80f, 0f))
    }

    @Test fun ignoredWhileBusyOrWithoutAFrontPicture() {
        assertTrue(SwipeTurn.mayTurn(allowed = true) { true })
        assertFalse("battle animation / PiP", SwipeTurn.mayTurn(allowed = false) { true })
        assertFalse("no front sprite", SwipeTurn.mayTurn(allowed = true) { false })
    }

    @Test fun noFileChecksWhileBusy() {
        var asked = 0
        assertFalse(SwipeTurn.mayTurn(allowed = false) { asked++; true })
        assertEquals(0, asked)
    }

    @Test fun aSidewaysDragIsClaimedFromScrollParentsEarly() {
        assertTrue(SwipeTurn.isClaim(8f, 2f, slop))
        assertTrue(SwipeTurn.isClaim(-12f, 7f, slop))
        assertFalse("under one slop", SwipeTurn.isClaim(7f, 0f, slop))
        assertFalse("too steep: the parent may scroll", SwipeTurn.isClaim(10f, 8f, slop))
        val t = SwipeTracker(slop)
        t.down(0f, 0f)
        assertTrue(t.claims(10f, 1f))
        assertFalse("not yet a swipe", t.move(10f, 1f))
        assertTrue(t.move(30f, 2f))
        assertFalse("a swipe already owns the touch", t.claims(40f, 2f))
    }

    @Test fun turnLastsAboutOneAndAHalfSeconds() {
        assertEquals(1500L, SwipeTurn.TURN_MS)
    }
}
