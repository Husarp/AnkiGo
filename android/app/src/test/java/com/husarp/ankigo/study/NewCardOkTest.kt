package com.husarp.ankigo.study

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 155 (R14-B2-F): "New cards: one OK button", the pure parts: the row rule, the one decision, the gentle set, the journal. */
class NewCardOkTest {
    private fun gentle(raw: String? = null, now: () -> Long = { 1_000L }) = GentleCards(raw, {}, now)

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

    @Test fun theRowAppliesOnlyWithTheSettingOnAndANewOrGentleCard() {
        assertFalse("setting off", NewCardOk.rowApplies(false, 0, false))
        assertFalse("setting off, gentle", NewCardOk.rowApplies(false, 1, true))
        assertTrue("new card", NewCardOk.rowApplies(true, 0, false))
        assertTrue("new card that is also gentle", NewCardOk.rowApplies(true, 0, true))
        assertTrue("gentle card in learning", NewCardOk.rowApplies(true, 1, true))
        assertTrue("gentle card in day learning", NewCardOk.rowApplies(true, 3, true))
        assertFalse("learning card, not gentle", NewCardOk.rowApplies(true, 1, false))
        assertFalse("review card", NewCardOk.rowApplies(true, 2, false))
        assertFalse("a gentle entry on a card that is a review by now", NewCardOk.rowApplies(true, 2, true))
        assertFalse("unknown queue", NewCardOk.rowApplies(true, null, false))
        assertFalse("suspended", NewCardOk.rowApplies(true, -1, true))
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

    @Test fun settingOffChangesNothingAndTouchesNothing() {
        val g = gentle("5:0:900")
        for (b in 2..4) for (e in 1..b) assertEquals(NewCardOk.Resolved(e, false), NewCardOk.settle(false, 0, 5, 0, b, e, g))
        assertEquals("5:0:900", g.text())
        assertEquals(1, g.size)
    }

    @Test fun anOkOnANewCardMakesItGentleOnceAndGoodDoesNot() {
        val g = gentle()
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.settle(true, 0, 11, 0, 4, 1, g))
        assertTrue(g.has(11, 0))
        assertFalse("another card, or the other side of the note, is not gentle", g.has(11, 1) || g.has(12, 0))
        // Good on a new card: no OK, nothing remembered
        assertEquals(NewCardOk.Resolved(3, false), NewCardOk.settle(true, 0, 12, 0, 4, 3, g))
        assertFalse(g.has(12, 0))
    }

    @Test fun theGentleShowingGetsTheOkRowOnceThenAgainIsNormal() {
        val g = gentle()
        NewCardOk.settle(true, 0, 11, 0, 4, 1, g)                       // OK on the new card
        // its next showing (queue 1): the OK row applies, and an OK there is neutral again but is not remembered a second time
        assertTrue(NewCardOk.rowApplies(true, 1, g.has(11, 0)))
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.settle(true, 1, 11, 0, 4, 1, g))
        assertFalse("used up", g.has(11, 0))
        // after that Again is an ordinary Again
        assertFalse(NewCardOk.rowApplies(true, 1, g.has(11, 0)))
        assertEquals(NewCardOk.Resolved(1, false), NewCardOk.settle(true, 1, 11, 0, 4, 1, g))
    }

    @Test fun aGoodOnTheGentleShowingUsesItUpAsWell() {
        val g = gentle()
        NewCardOk.settle(true, 0, 11, 0, 4, 1, g)
        assertEquals(NewCardOk.Resolved(3, false), NewCardOk.settle(true, 1, 11, 0, 4, 3, g))
        assertFalse(g.has(11, 0))
    }

    @Test fun aReviewCardIsNeverOk() {
        val g = gentle()
        assertEquals(NewCardOk.Resolved(1, false), NewCardOk.settle(true, 2, 11, 0, 4, 1, g))
        assertEquals(NewCardOk.Resolved(2, false), NewCardOk.settle(true, 2, 11, 0, 4, 2, g))
        assertFalse(g.has(11, 0))
    }

    // ---- the gentle set ----

    @Test fun gentleEntriesAreDroppedAfterADay() {
        var t = 1_000L
        val g = gentle(null) { t }
        g.add(1, 0)
        t += GentleCards.DAY_MS
        assertTrue("exactly a day: still there", g.has(1, 0))
        t += 1
        assertFalse(g.has(1, 0))
        assertEquals("", g.text())
    }

    @Test fun theSetStaysSmallAndTheOldestGoFirst() {
        var t = 0L
        val g = gentle(null) { t++ }
        for (n in 1..GentleCards.MAX + 5) g.add(n.toLong(), 0)
        assertEquals(GentleCards.MAX, g.size)
        assertFalse(g.has(1, 0)); assertFalse(g.has(5, 0))
        assertTrue(g.has(6, 0)); assertTrue(g.has((GentleCards.MAX + 5).toLong(), 0))
    }

    @Test fun theSetSurvivesARestartThroughItsText() {
        var saved = ""
        val a = GentleCards(null, { saved = it }) { 5_000L }
        a.add(10, 0); a.add(10, 1); a.add(20, 2)
        a.remove(10, 0)
        val b = GentleCards(saved, {}) { 5_100L }   // the next process reads what was saved
        assertFalse(b.has(10, 0)); assertTrue(b.has(10, 1)); assertTrue(b.has(20, 2))
        assertEquals(2, b.size)
    }

    @Test fun anUnreadableSavedTextIsJustEmptyAndAClockThatWentBackDropsEntries() {
        assertEquals(0, gentle("garbage,1:x:3,::,:::,4:5").size)
        assertEquals(1, gentle("garbage,7:1:900").size)
        val future = gentle("9:0:${1_000L + GentleCards.DAY_MS + 5}")   // saved "tomorrow": the phone's clock moved back
        assertFalse(future.has(9, 0))
    }

    @Test fun addingACardAgainRefreshesItsTime() {
        var t = 0L
        val g = gentle(null) { t }
        g.add(1, 0)
        t = GentleCards.DAY_MS - 10
        g.add(1, 0)
        t = GentleCards.DAY_MS + 100
        assertTrue("the second add started a new day", g.has(1, 0))
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
