package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class StudyLayoutTest {
    private fun plan(w: Int, sw: Int, land: Boolean, finished: Boolean = false) = StudyLayout.plan(w, sw, land, finished)

    @Test fun phonePortraitStacksWithHandle() {
        val p = plan(412, 412, false)
        assertFalse(p.columns); assertNull(p.mode); assertTrue(p.handle); assertEquals(1, p.answerRows); assertEquals(0, p.summaryMaxDp)
    }
    @Test fun phoneLandscapeKeepsStripAndTwoRows() {
        val p = plan(869, 412, true)
        assertTrue(p.columns); assertEquals(1, p.mode); assertEquals(.58f, p.cardShare, 0f); assertFalse(p.narrow); assertEquals(2, p.answerRows); assertFalse(p.handle)
    }
    @Test fun splitScreenIsFiftyFiftyCompact() {
        val p = plan(412, 400, true)
        assertTrue(p.columns); assertTrue(p.narrow); assertEquals(.5f, p.cardShare, 0f); assertEquals(1, p.mode)
    }
    @Test fun tabletPortraitStacksWithHandleAndTallerPanel() {
        val p = plan(800, 800, false)
        assertFalse(p.columns); assertNull(p.mode); assertTrue(p.handle); assertEquals(1, p.answerRows)
        assertEquals(400, p.panelDp(0)); assertEquals(240, p.panelDp(1)); assertEquals(34, p.panelDp(2))
    }
    @Test fun phonePanelHeightsUnchanged() {
        val p = plan(412, 412, false)
        assertEquals(220, p.panelDp(0)); assertEquals(180, p.panelDp(1)); assertEquals(34, p.panelDp(2))
    }
    @Test fun tabletLandscapeKeepsFortyTwoPercent() {
        val p = plan(1280, 800, true)
        assertTrue(p.columns); assertEquals(0, p.mode); assertEquals(.58f, p.cardShare, .001f); assertEquals(1, p.answerRows); assertFalse(p.handle)
    }
    @Test fun summaryIsOneColumnAndCappedOnTablets() {
        val t = plan(1280, 800, true, finished = true)
        assertFalse(t.columns); assertEquals(640, t.summaryMaxDp)
        val ph = plan(869, 412, true, finished = true)
        assertFalse(ph.columns); assertEquals(0, ph.summaryMaxDp)
    }
}
