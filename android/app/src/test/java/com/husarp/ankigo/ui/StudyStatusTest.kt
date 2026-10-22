package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 202: the Study screen warns only when something is wrong. */
class StudyStatusTest {
    @Test fun allFineGivesNoWarning() = assertNull(StudyStatus.warning(true, "2.25.1", true, true))

    @Test fun notInstalledOffersTheStore() {
        val w = StudyStatus.warning(false, null, false, false)!!
        assertEquals(StudyStatus.Action.GET_ANKIDROID, w.action)
        assertEquals("Get AnkiDroid", w.button)
    }

    @Test fun tooOldSaysTheVersionAndOpensAnkiDroid() {
        val w = StudyStatus.warning(true, "2.22.4", false, true)!!
        assertEquals(StudyStatus.Action.OPEN_ANKIDROID, w.action)
        assertTrue(w.message, "2.22.4" in w.message && "2.24" in w.message)
    }

    @Test fun newEnoughButNoProviderIsAnotherMessage() {
        val w = StudyStatus.warning(true, "2.25.1", false, true)!!
        assertTrue(w.message, "2.25.1" !in w.message)
    }

    @Test fun missingPermissionOffersAllow() {
        val w = StudyStatus.warning(true, "2.25.1", true, false)!!
        assertEquals(StudyStatus.Action.ALLOW_ACCESS, w.action)
        assertNotNull(w.message)
    }

    @Test fun oldBeatsMissingPermission() =
        assertEquals(StudyStatus.Action.OPEN_ANKIDROID, StudyStatus.warning(true, "2.20", false, false)!!.action)
}
