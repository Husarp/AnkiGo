package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TICKET R2-08: PiP on leave is moved to off once; Pop out picks float or PiP (R4-X5: its own choice). */
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

    @Test fun popOutDefaultFollowsLeaveModeUntilChosen() {
        assertEquals(PopOutMode.FLOAT, PopOutPlan.resolve(null, LeaveMode.FLOAT))
        assertEquals(PopOutMode.PIP, PopOutPlan.resolve(null, LeaveMode.OFF))
        assertEquals(PopOutMode.PIP, PopOutPlan.resolve(null, LeaveMode.PIP))
        assertEquals(PopOutMode.PIP, PopOutPlan.resolve("junk", LeaveMode.OFF))
    }

    @Test fun popOutChoiceSticksWhateverTheLeaveMode() {
        assertEquals(PopOutMode.FLOAT, PopOutPlan.resolve("FLOAT", LeaveMode.OFF))
        assertEquals(PopOutMode.PIP, PopOutPlan.resolve("PIP", LeaveMode.FLOAT))
    }

    @Test fun popOutPicksFloatOnlyWhenChosenOrNoPip() {
        assertTrue(PopOutPlan.useFloat(PopOutMode.FLOAT, true))
        assertFalse(PopOutPlan.useFloat(PopOutMode.PIP, true))
    }

    @Test fun noPipAlwaysFloat() {
        assertTrue(PopOutPlan.useFloat(PopOutMode.PIP, false))
        assertTrue(PopOutPlan.useFloat(PopOutMode.FLOAT, false))
    }
}
