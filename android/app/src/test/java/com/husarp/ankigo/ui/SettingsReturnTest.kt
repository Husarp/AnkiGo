package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsReturnTest {
    @Test fun strictlyLessThanOneMinute() {
        assertTrue(SettingsReturn.withinMinute(100, 100))
        assertTrue(SettingsReturn.withinMinute(100, 60099))
        assertFalse(SettingsReturn.withinMinute(100, 60100))
        assertFalse(SettingsReturn.withinMinute(100, 90000))
    }
    @Test fun missingOrRebootedClockExpires() {
        assertFalse(SettingsReturn.withinMinute(-1, 100))
        assertFalse(SettingsReturn.withinMinute(500, 100))
    }
}
