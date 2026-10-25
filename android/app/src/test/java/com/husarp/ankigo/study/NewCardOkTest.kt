package com.husarp.ankigo.study

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 155 (R14-B2-F): "New cards: one OK button", the pure parts: the row rule, the one decision, the journal. */
class NewCardOkTest {

    // ---- the row ----

    @Test fun theRowIsTheUsualOneUnlessOkAppliesAndThenOkReplacesAgainAndHard() {
        fun labels(buttons: Int, ok: Boolean) = NewCardOk.row(buttons, ok).map { it.label to it.ease }
        assertEquals(listOf("Again" to 1, "Hard" to 2, "Good" to 3, "Easy" to 4), labels(4, false))
        assertEquals(listOf("Again" to 1, "Good" to 2, "Easy" to 3), labels(3, false))
        assertEquals(listOf("Again" to 1, "Good" to 2), labels(2, false))
        assertEquals(listOf("OK" to 1, "Good" to 3, "Easy" to 4), labels(4, true))   // no Hard on the OK row
        assertEquals(listOf("OK" to 1, "Good" to 2, "Easy" to 3), labels(3, true))   // OK replaces Again alone
        assertEquals(listOf("OK" to 1, "Good" to 2), labels(2, true))
        assertTrue(NewCardOk.row(4, true).first().ok)
        assertTrue(NewCardOk.row(4, true).drop(1).none { it.ok })
    }

    @Test fun theRowAppliesOnlyWithTheSettingOnAndANewCard() {
        assertFalse("setting off", NewCardOk.rowApplies(false, 0))
        assertFalse("setting off, gentle", NewCardOk.rowApplies(false, 1))
        assertTrue("new card", NewCardOk.rowApplies(true, 0))
        assertTrue("new card that is also gentle", NewCardOk.rowApplies(true, 0))
        assertFalse("old gentle card in learning", NewCardOk.rowApplies(true, 1))
        assertFalse("old gentle card in day learning", NewCardOk.rowApplies(true, 3))
        assertFalse("learning card, not gentle", NewCardOk.rowApplies(true, 1))
        assertFalse("review card", NewCardOk.rowApplies(true, 2))
        assertFalse("a gentle entry on a card that is a review by now", NewCardOk.rowApplies(true, 2))
        assertFalse("unknown queue", NewCardOk.rowApplies(true, null))
        assertFalse("suspended", NewCardOk.rowApplies(true, -1))
    }

    // ---- the one decision ----

    @Test fun anEaseTheOkReplacesBecomesOkAndAgain() {
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.resolve(true, 4, 1))
        assertEquals("Hard (a key or a swipe) counts as OK too", NewCardOk.Resolved(1, true), NewCardOk.resolve(true, 4, 2))
        assertEquals(NewCardOk.Resolved(3, false), NewCardOk.resolve(true, 4, 3))
        assertEquals(NewCardOk.Resolved(4, false), NewCardOk.resolve(true, 4, 4))
        assertEquals("3 buttons: only Again is replaced", NewCardOk.Resolved(1, true), NewCardOk.resolve(true, 3, 1))
        assertEquals(NewCardOk.Resolved(2, false), NewCardOk.resolve(true, 3, 2))
        assertEquals(NewCardOk.Resolved(3, false), NewCardOk.resolve(true, 3, 3))
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.resolve(true, 2, 1))
        assertEquals(NewCardOk.Resolved(2, false), NewCardOk.resolve(true, 2, 2))
        for (b in 2..4) for (e in 1..b) assertEquals("no OK row: nothing changes", NewCardOk.Resolved(e, false), NewCardOk.resolve(false, b, e))
    }

    @Test fun settingOffChangesNothing() {
        for (b in 2..4) for (e in 1..b)
            assertEquals(NewCardOk.Resolved(e, false), NewCardOk.settle(false, 0, b, e))
    }

    @Test fun theFirstLearningShowingAndEveryLaterShowingAreNormal() {
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.settle(true, 0, 4, 1))
        for (queue in listOf(1, 3, 2)) for (ease in 1..4) {
            assertFalse(NewCardOk.rowApplies(true, queue))
            assertEquals(NewCardOk.Resolved(ease, false), NewCardOk.settle(true, queue, 4, ease))
        }
    }

    // ---- companion mode ----

    @Test fun companionNeutralIsAgainOrHardOnACardThatWasNewWithTheSettingOn() {
        assertTrue(NewCardOk.companionNeutral(true, true, 1))
        assertTrue(NewCardOk.companionNeutral(true, true, 2))
        assertFalse("Good on a new card", NewCardOk.companionNeutral(true, true, 3))
        assertFalse("Easy on a new card", NewCardOk.companionNeutral(true, true, 4))
        assertFalse("unknown button", NewCardOk.companionNeutral(true, true, 0))
        assertFalse("Again on a card that was not new", NewCardOk.companionNeutral(true, false, 1))
        assertFalse("setting off", NewCardOk.companionNeutral(false, true, 1))
    }

    // ---- the journal ----

    private val before = mapOf("_id" to "7", "note_id" to "70", "deck_id" to "3", "reps" to "0", "due" to "12", "queue" to "0", "last_review_time_secs" to null)
    private fun answer(seq: Long, neutral: Boolean = false, ease: Int = 1) =
        QueuedAnswer(seq, 3, 70, 0, 7, before, ease, 900, 1_700_000_000_000L + seq, neutral = neutral)

    @Test fun theJournalKeepsNeutralAndOldJournalsReadAsFalse() {
        val d = AnswerJournal.decode(AnswerJournal.encode(listOf(answer(1, neutral = true), answer(2, ease = 3))))
        assertNull(d.problem)
        assertEquals(listOf(true, false), d.answers.map { it.neutral })
        // a journal written before this setting existed has no such field
        val old = JSONObject(AnswerJournal.encode(listOf(answer(5)))).toString()
        assertFalse(old.contains("neutral"))
        assertFalse(AnswerJournal.decode(old).answers.single().neutral)
        // and a plain answer is written exactly as before (the field appears only when true)
        assertTrue(AnswerJournal.encode(listOf(answer(6, neutral = true))).contains("\"neutral\":true"))
    }
}
