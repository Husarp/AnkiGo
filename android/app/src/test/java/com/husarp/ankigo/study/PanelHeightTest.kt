package com.husarp.ankigo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelHeightTest {
    @Test fun presetsMatchThePlan() {
        assertEquals(listOf(220, 180, 34), (0..2).map { PanelHeight.preset(false, it) })
        assertEquals(listOf(400, 240, 34), (0..2).map { PanelHeight.preset(true, it) })
        val plan = StudyLayout.plan(412, 412, false, false)
        assertEquals(listOf(220, 180, 34), (0..2).map { plan.panelDp(it) })
    }

    @Test fun modeFollowsTheHeight() {
        assertEquals(2, PanelHeight.mode(34, 220))
        assertEquals(2, PanelHeight.mode(99, 220))
        assertEquals(1, PanelHeight.mode(100, 220))
        assertEquals(1, PanelHeight.mode(219, 220))
        assertEquals(0, PanelHeight.mode(220, 220))
        assertEquals(0, PanelHeight.mode(300, 220))
        assertEquals(1, PanelHeight.mode(399, 400))
    }

    @Test fun snapsToTheNearestPresetWithinReach() {
        assertEquals(220, PanelHeight.snap(210, false))
        assertEquals(180, PanelHeight.snap(190, false))      // 10 from 180, 30 from 220
        assertEquals(220, PanelHeight.snap(204, false))      // 16 from 220, 24 from 180
        assertEquals(34, PanelHeight.snap(60, false))
        assertEquals(34, PanelHeight.snap(99, false))
        assertEquals(34, PanelHeight.snap(34, false))
    }

    @Test fun keepsAFreeHeightOutOfReach() {
        assertEquals(130, PanelHeight.snap(130, false))
        assertEquals(260, PanelHeight.snap(260, false))
        assertEquals(100, PanelHeight.snap(100, false))
        assertEquals(300, PanelHeight.snap(300, true))       // tablet: 240 and 400 are both far
        assertEquals(240, PanelHeight.snap(250, true))
    }

    @Test fun clampKeepsCardAndMini() {
        assertEquals(34, PanelHeight.clamp(10, 300))
        assertEquals(300, PanelHeight.clamp(900, 300))
        assertEquals(180, PanelHeight.clamp(180, 300))
        assertEquals(34, PanelHeight.clamp(180, 10))         // a tiny window still gets the Mini line
        // 869 dp phone: body ~ 869 - 24; panel max = body - top bar - bar - buttons - 120
        assertEquals(845 - 46 - 24 - 64 - 120, PanelHeight.maxDp(845, PanelHeight.buttonsDp(1)))
    }

    @Test fun tapCyclesTheThreePresets() {
        assertEquals(180, PanelHeight.tap(220, false))
        assertEquals(34, PanelHeight.tap(180, false))
        assertEquals(220, PanelHeight.tap(34, false))
        assertEquals(220, PanelHeight.tap(260, false))       // above Full: back to Full
        assertEquals(180, PanelHeight.tap(200, false))       // free height: the next preset below
        assertEquals(34, PanelHeight.tap(120, false))
        assertEquals(240, PanelHeight.tap(400, true))
    }

    @Test fun restoreAndMigrateTheOldPref() {
        assertEquals(150, PanelHeight.restore(150, 0, false))   // the new key wins
        assertEquals(220, PanelHeight.restore(-1, 0, false))
        assertEquals(180, PanelHeight.restore(-1, 1, false))
        assertEquals(34, PanelHeight.restore(-1, 2, false))
        assertEquals(240, PanelHeight.restore(-1, 1, true))
        assertEquals(180, PanelHeight.restore(-1, -1, false))   // nothing saved: phone Strip
        assertEquals(400, PanelHeight.restore(-1, -1, true))    // tablet Full
        assertEquals(180, PanelHeight.restore(0, 7, false))     // junk old value: the default
    }

    @Test fun labels() {
        assertEquals("FULL", PanelHeight.label(220, false))
        assertEquals("STRIP", PanelHeight.label(180, false))
        assertEquals("MINI", PanelHeight.label(34, false))
        assertEquals("STRIP · 140", PanelHeight.label(140, false))
        assertEquals("FULL · 260", PanelHeight.label(260, false))
        assertEquals("MINI · 60", PanelHeight.label(60, false))
    }

    @Test fun answerRowHasOneHeightPerLayout() {
        assertEquals(64, PanelHeight.buttonsDp(1))
        assertEquals(102, PanelHeight.buttonsDp(2))
        assertTrue(PanelHeight.buttonsDp(2) > PanelHeight.buttonsDp(1))
    }
}
