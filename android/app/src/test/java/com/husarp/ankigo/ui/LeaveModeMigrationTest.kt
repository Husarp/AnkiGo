package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TICKET R2-08: PiP on leave is moved to off once; Pop out picks float or PiP. */
class LeaveModeMigrationTest {
    @Test fun oldPipBecomesOffOnce() {
        assertEquals(LeaveMode.OFF, LeaveModeMigration.resolve(null, "PIP"))
        assertTrue(LeaveModeMigration.needsWrite(null))
    }

    @Test fun oldFloatAndOffAreKept() {
        assertEquals(LeaveMode.FLOAT, LeaveModeMigration.resolve(null, "FLOAT"))
        assertEquals(LeaveMode.OFF, LeaveModeMigration.resolve(null, "OFF"))
    }

    @Test fun nothingStoredIsOff() {
        assertEquals(LeaveMode.OFF, LeaveModeMigration.resolve(null, null))
        assertEquals(LeaveMode.OFF, LeaveModeMigration.resolve("junk", "junk"))
    }

    @Test fun newValueSticksEvenIfPipAndIgnoresTheOldKey() {
        assertEquals(LeaveMode.PIP, LeaveModeMigration.resolve("PIP", "FLOAT"))
        assertEquals(LeaveMode.OFF, LeaveModeMigration.resolve("OFF", "PIP"))
        assertFalse(LeaveModeMigration.needsWrite("PIP"))
    }

    @Test fun popOutPicksFloatOnlyForFloatModeOrNoPip() {
        assertTrue(PopOutPlan.useFloat(LeaveMode.FLOAT, true))
        assertTrue(PopOutPlan.useFloat(LeaveMode.OFF, false))
        assertTrue(PopOutPlan.useFloat(LeaveMode.PIP, false))
        assertFalse(PopOutPlan.useFloat(LeaveMode.OFF, true))
        assertFalse(PopOutPlan.useFloat(LeaveMode.PIP, true))
    }
}
