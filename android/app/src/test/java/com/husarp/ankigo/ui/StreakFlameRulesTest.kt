package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.StreakFlameRules.Picture
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakFlameRulesTest {
    @Test fun animatedOnlyWhenEverythingAllows() {
        assertEquals(Picture.ANIMATED, StreakFlameRules.picture(28, false, false, true))
        assertEquals(Picture.ANIMATED, StreakFlameRules.picture(34, false, false, true))
    }

    @Test fun oldAndroidGetsStill() {
        assertEquals(Picture.STILL, StreakFlameRules.picture(26, false, false, true))
        assertEquals(Picture.STILL, StreakFlameRules.picture(27, false, false, true))
    }

    @Test fun reducedMotionRendererDeadAndFailureGetStill() {
        assertEquals(Picture.STILL, StreakFlameRules.picture(34, true, false, true))
        assertEquals(Picture.STILL, StreakFlameRules.picture(34, false, true, true))
        assertEquals(Picture.STILL, StreakFlameRules.picture(34, false, false, false))
        assertEquals(Picture.STILL, StreakFlameRules.picture(34, false, false, true, decodeFailed = true))
    }

    @Test fun runsOnlyWhileVisible() {
        assertEquals(true, StreakFlameRules.shouldRun(Picture.ANIMATED, true, true, true))
        assertEquals(false, StreakFlameRules.shouldRun(Picture.ANIMATED, false, true, true))
        assertEquals(false, StreakFlameRules.shouldRun(Picture.ANIMATED, true, false, true))
        assertEquals(false, StreakFlameRules.shouldRun(Picture.ANIMATED, true, true, false))
        assertEquals(false, StreakFlameRules.shouldRun(Picture.STILL, true, true, true))
    }

    @Test fun deadFlameIsFaded() {
        assertEquals(255, StreakFlameRules.alpha(true))
        assertEquals(115, StreakFlameRules.alpha(false))
    }
}
