package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R3-X2 item 5: AnkiDroid ignores a DO_SYNC intent within 2 minutes of its last sync, so AnkiGo does not send it twice that fast. */
class AnkiSyncRulesTest {
    private val gap = AnkiSyncRules.MIN_GAP_MS

    @Test fun theGapIsAnkiDroidsTwoMinutes() = assertEquals(120_000L, gap)

    @Test fun neverAskedOrClockWentBackMeansAskNow() {
        assertEquals(0, AnkiSyncRules.waitSeconds(1_000_000, 0))
        assertEquals(0, AnkiSyncRules.waitSeconds(1_000, 5_000)) // asked "in the future": ignore it
    }

    @Test fun insideTheGapItCountsTheSecondsLeftRoundedUp() {
        assertEquals(120, AnkiSyncRules.waitSeconds(10_000, 10_000))
        assertEquals(60, AnkiSyncRules.waitSeconds(70_000, 10_000))
        assertEquals(1, AnkiSyncRules.waitSeconds(10_000 + gap - 1, 10_000))
    }

    @Test fun atOrAfterTheGapAskNow() {
        assertEquals(0, AnkiSyncRules.waitSeconds(10_000 + gap, 10_000))
        assertEquals(0, AnkiSyncRules.waitSeconds(10_000 + gap + 5, 10_000))
    }
}
