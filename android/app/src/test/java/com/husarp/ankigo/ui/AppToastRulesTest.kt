package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppToastRulesTest {
    @Test fun shortTextStaysTwoPointTwoSeconds() {
        assertEquals(2200L, AppToastRules.showMs(""))
        assertEquals(2200L, AppToastRules.showMs("Saved"))
        assertEquals(2200L, AppToastRules.showMs("x".repeat(40)))
    }

    @Test fun longerTextStaysLongerButNotForever() {
        assertEquals(2230L, AppToastRules.showMs("x".repeat(41)))
        assertTrue(AppToastRules.showMs("x".repeat(80)) > AppToastRules.showMs("x".repeat(60)))
        assertEquals(5000L, AppToastRules.showMs("x".repeat(500)))
    }

    @Test fun widthKeepsSideMarginsAndCaps() {
        assertEquals(360, AppToastRules.widthDp(412))
        assertEquals(360, AppToastRules.widthDp(800))
        assertEquals(328, AppToastRules.widthDp(360))
        assertEquals(288, AppToastRules.widthDp(320))
        assertEquals(0, AppToastRules.widthDp(10))
    }

    @Test fun bottomIs96InATallWindowAndLessInAShortOne() {
        assertEquals(96, AppToastRules.bottomDp(869))
        assertEquals(96, AppToastRules.bottomDp(600))
        assertEquals(65, AppToastRules.bottomDp(599))
        assertEquals(33, AppToastRules.bottomDp(300))
        assertEquals(16, AppToastRules.bottomDp(100))
    }

    @Test fun keyboardLiftsItOnlyWhenHigher() {
        assertEquals(96, AppToastRules.bottomDp(869, imeDp = 0))
        assertEquals(96, AppToastRules.bottomDp(869, imeDp = 40))
        assertEquals(316, AppToastRules.bottomDp(869, imeDp = 300))
        assertEquals(316, AppToastRules.bottomDp(400, imeDp = 300))
    }

    @Test fun stillWhenAnimatorsAreOffOrReduceMotionIsOn() {
        assertFalse(AppToastRules.still(animatorsEnabled = true, reduceMotionPref = false))
        assertTrue(AppToastRules.still(animatorsEnabled = false, reduceMotionPref = false))
        assertTrue(AppToastRules.still(animatorsEnabled = true, reduceMotionPref = true))
    }

    @Test fun aLiveCallerKeepsItsMessage() {
        assertEquals(AppToastRules.Place.CALLER, AppToastRules.place(hasActivity = true, callerFinishing = false, otherActivity = true, floating = true))
    }

    @Test fun aFinishingCallerTriesTheLiveHostsFirst() {
        assertEquals(AppToastRules.Place.OTHER_ACTIVITY, AppToastRules.place(true, true, otherActivity = true, floating = true))
        assertEquals(AppToastRules.Place.FLOAT, AppToastRules.place(true, true, otherActivity = false, floating = true))
    }

    @Test fun aFinishingCallerWithNoHostWaitsForTheNextScreen() {
        assertEquals(AppToastRules.Place.WAIT, AppToastRules.place(true, true, otherActivity = false, floating = false))
    }

    @Test fun noActivityAndNoFloatingWindowIsLeftToTheCaller() {
        assertEquals(AppToastRules.Place.NONE, AppToastRules.place(false, false, false, false))
        assertEquals(AppToastRules.Place.FLOAT, AppToastRules.place(false, false, false, true))
    }

    @Test fun aWaitingMessageIsOverAfterThreeSeconds() {
        assertFalse(AppToastRules.waitOver(1000L, 4000L))
        assertTrue(AppToastRules.waitOver(1000L, 4001L))
    }
}
