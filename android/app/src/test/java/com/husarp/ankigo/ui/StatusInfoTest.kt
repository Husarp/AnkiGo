package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Tuning
import org.junit.Assert.*
import org.junit.Test

class StatusInfoTest {
    private val ids = listOf("par", "brn", "psn", "tox", "slp", "frz")

    @Test fun everyStatusHasALine() {
        for (id in ids) {
            val i = StatusInfo.of(id)!!
            assertTrue(i.line.isNotBlank()); assertTrue(i.name.isNotBlank())
            assertEquals(StatusPill.label(id), i.label)
        }
        assertNull(StatusInfo.of(null)); assertNull(StatusInfo.of("cnf"))
    }

    @Test fun numbersMatchEngine() {
        assertEquals(25, Math.round(Tuning.PARA_SKIP * 100).toInt())
        assertTrue(StatusInfo.of("par")!!.line.contains("${Math.round(Tuning.PARA_SKIP * 100)} %"))
        assertEquals(0.5, Tuning.PARA_SPEED, 0.0); assertTrue(StatusInfo.of("par")!!.line.contains("halved"))
        assertTrue(StatusInfo.of("slp")!!.line.contains("${Math.round(Tuning.WAKE_CHANCE * 100)} %"))
        assertTrue(StatusInfo.of("frz")!!.line.contains("${Math.round(Tuning.FREEZE_THAW * 100)} %"))
        assertTrue(StatusInfo.of("brn")!!.line.contains("1/16")); assertTrue(StatusInfo.of("psn")!!.line.contains("1/8"))
        assertTrue(StatusInfo.of("tox")!!.line.contains("15/16"))
    }
}
