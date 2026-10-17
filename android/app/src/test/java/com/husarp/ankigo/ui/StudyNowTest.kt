package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyNowTest {
    @Test fun badgeText() {
        assertNull(StudyNow.badge(0))
        assertNull(StudyNow.badge(null))
        assertEquals("1", StudyNow.badge(1))
        assertEquals("87", StudyNow.badge(87))
        assertEquals("999", StudyNow.badge(999))
        assertEquals("999+", StudyNow.badge(1000))
        assertEquals("999+", StudyNow.badge(1234))
    }

    @Test fun stateChoice() {
        assertEquals(StudyNow.State.DUE, StudyNow.state(87))
        assertEquals(StudyNow.State.DONE, StudyNow.state(0))
        assertEquals(StudyNow.State.UNKNOWN, StudyNow.state(null))
    }

    @Test fun labels() {
        assertEquals("Study", StudyNow.label(StudyNow.State.DUE))
        assertEquals("Study", StudyNow.label(StudyNow.State.UNKNOWN))
        assertEquals("All done", StudyNow.label(StudyNow.State.DONE))
    }

    @Test fun spokenDescription() {
        assertEquals("Study now, 87 cards due", StudyNow.description(87))
        assertEquals("Study now, 1 card due", StudyNow.description(1))
        assertEquals("Study now, all done", StudyNow.description(0))
        assertEquals("Study now", StudyNow.description(null))
    }

    @Test fun buddySpokenDescription() {
        assertEquals("Mimi, 32 of 50 today, 48 cards left, 12-day streak", BuddyText.description("Mimi", 32, 50, 48, 12))
        assertEquals("Mimi, 32 of 50 today", BuddyText.description("Mimi", 32, 50, null, 0))
        assertEquals("Buddy, 5 today, 1 card left", BuddyText.description(null, 5, 0, 1, 0))
    }
}
