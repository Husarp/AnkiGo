package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class StudyReturnTest {
    private fun started() = StudyReturn().apply { requested(); ankiStarting() }

    @Test fun backFromAnkiDroidWithoutPipLandsOnStudy() {
        val r = started()
        assertTrue(r.resumed(false))                // first resume after AnkiDroid: go to Study
        assertFalse(r.fromStudy)
        assertFalse(r.resumed(false))                // only once
        assertFalse(r.back())                        // later Back is normal
    }
    @Test fun expandingThePipWindowShowsTheBattleAndBackGoesToStudy() {
        val r = started()
        r.pipEntered()
        assertFalse(r.resumed(true))                 // still in the small window
        assertFalse(r.resumed(false))                // expanded: battle stays
        assertTrue(r.back())                         // Back -> Study
        assertFalse(r.back())
    }
    @Test fun firstResumeBeforeAnkiStartedDoesNothing() {
        val r = StudyReturn().apply { requested() }
        assertFalse(r.resumed(false))
        assertTrue(r.back())
    }
    @Test fun failedLaunchStaysOnBattle() {
        val r = started(); r.ankiFailed()
        assertFalse(r.resumed(false))
    }
    @Test fun ownScreenResetsTheReturn() {
        val r = started(); r.ownScreenOpened()
        assertFalse(r.resumed(false)); assertFalse(r.back())
    }
    @Test fun plainBattleStartHasNoStudyReturn() {
        val r = StudyReturn()
        assertFalse(r.resumed(false)); assertFalse(r.back()); r.pipEntered(); assertFalse(r.resumed(false))
    }
    @Test fun stateSurvivesARecreate() {
        val r = started(); r.pipEntered()
        val copy = StudyReturn(); copy.restore(r.toInts())
        assertEquals(r.toInts().toList(), copy.toInts().toList())
        assertFalse(copy.resumed(false)) // pipped: the battle is fine
        val b = started(); val c = StudyReturn(); c.restore(b.toInts())
        assertTrue(c.resumed(false))
        StudyReturn().restore(null); StudyReturn().restore(intArrayOf(1))
    }
}
