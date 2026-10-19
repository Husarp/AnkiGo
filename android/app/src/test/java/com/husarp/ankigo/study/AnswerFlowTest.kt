package com.husarp.ankigo.study

import com.husarp.ankigo.Anki
import org.junit.Assert.*
import org.junit.Test

class AnswerFlowTest {
    // ---- the summary waits for answers still in flight
    @Test fun summaryRunsAtOnceWhenNothingIsInFlight() {
        val p = PendingAnswers(); var ran = 0
        p.whenIdle { ran++ }
        assertEquals(1, ran)
    }

    @Test fun summaryWaitsForTheLastAnswerInFlight() {
        val p = PendingAnswers(); var ran = 0
        val a = p.start(); val b = p.start()
        p.whenIdle { ran++ }
        assertEquals(0, ran); assertEquals(2, p.count)
        a.finish(); assertEquals(0, ran)
        b.finish(); assertEquals(1, ran); assertEquals(0, p.count)
    }

    @Test fun aTicketCountsOnlyOnceEvenIfTheTimeoutAndTheRealCallbackBothFinishIt() {
        val p = PendingAnswers(); var ran = 0
        val a = p.start(); val b = p.start()
        p.whenIdle { ran++ }
        a.finish(); a.finish(); a.finish()
        assertEquals(1, p.count); assertEquals(0, ran)
        b.finish(); assertEquals(1, ran)
        b.finish(); assertEquals(1, ran)
    }

    @Test fun aNewerWaitingActionReplacesTheOlderOne() {
        val p = PendingAnswers(); val seen = ArrayList<String>()
        val a = p.start()
        p.whenIdle { seen += "old" }; p.whenIdle { seen += "new" }
        a.finish()
        assertEquals(listOf("new"), seen)
    }

    @Test fun anAnswerStartedWhileWaitingKeepsTheSummaryWaiting() {
        val p = PendingAnswers(); var ran = 0
        val a = p.start()
        p.whenIdle { ran++ }
        val b = p.start()
        a.finish(); assertEquals(0, ran)
        b.finish(); assertEquals(1, ran)
    }

    // ---- the timing line
    @Test fun timingLineIsCompleteWhenTheGameStepAndTheShownCardAreIn() {
        val t = AnswerTiming(1000)
        assertFalse(t.put(AnswerTiming.WRITE, 120))
        assertFalse(t.put(AnswerTiming.GAME, 15))
        assertFalse(t.put(AnswerTiming.NEXT, 340))
        assertTrue(t.put(AnswerTiming.SHOWN, 700))
        assertFalse("logged once", t.put(AnswerTiming.SHOWN, 701))
    }

    @Test fun timingLineWorksInEitherOrder() {
        val t = AnswerTiming(0)
        assertFalse(t.put(AnswerTiming.SHOWN, 300))
        assertTrue(t.put(AnswerTiming.GAME, 5000))
    }

    @Test fun timingLineShowsEveryPartAndDashesForMissingOnes() {
        val t = AnswerTiming(0)
        t.put(AnswerTiming.WRITE, 120); t.put(AnswerTiming.ABSORB, 4800); t.put(AnswerTiming.GAME, 15)
        t.put(AnswerTiming.NEXT, 340); t.put(AnswerTiming.COUNTS, 45); t.put(AnswerTiming.SHOWN, 700)
        t.note = "a note"
        assertEquals("answer: save+verify 120 ms, detector wait 4800 ms, game step 15 ms, next card 340 ms " +
            "(deck list -), tap to next card shown 700 ms, deck counts read after it 45 ms; a note", t.line())
    }

    @Test fun studyLineWaitsForThePageAndTheCountsReadToo() {
        val needs = setOf(AnswerTiming.GAME, AnswerTiming.SHOWN, AnswerTiming.PAGE, AnswerTiming.COUNTS)
        val t = AnswerTiming(0, needs)
        assertFalse(t.put(AnswerTiming.SHOWN, 400))
        assertFalse(t.put(AnswerTiming.GAME, 20))
        assertFalse(t.put(AnswerTiming.PAGE, 480))
        assertTrue(t.put(AnswerTiming.COUNTS, 900))
        assertFalse("logged once", t.put(AnswerTiming.PAGE, 481))
    }

    @Test fun partsThatNeverCameShowAsDashesAndPollsAreCounted() {
        val t = AnswerTiming(0, setOf(AnswerTiming.GAME, AnswerTiming.SHOWN, AnswerTiming.PAGE, AnswerTiming.COUNTS))
        t.put(AnswerTiming.WRITE, 150); t.put(AnswerTiming.ABSORB, 2); t.put(AnswerTiming.NEXT, 300); t.put(AnswerTiming.LIST, 1200)
        t.polls = 0
        t.put(AnswerTiming.PAGE, -1); t.put(AnswerTiming.COUNTS, -1); t.put(AnswerTiming.GAME, 30)
        assertTrue(t.put(AnswerTiming.SHOWN, 1700)) // the summary: no page, no counts read
        assertEquals("answer: save+verify 150 ms, detector wait 2 ms, game step 30 ms, next card 300 ms (deck list 1200 ms), " +
            "tap to next card shown 1700 ms, page loaded -, deck counts read after it -, detector polls meanwhile 0", t.line())
    }

    // ---- read once per visit of the screen
    @Test fun screenMemoReadsOnceUntilReset() {
        var reads = 0
        val m = ScreenMemo { reads++; "{\"maxTaken\":60}" }
        assertEquals("{\"maxTaken\":60}", m.get()); m.get(); m.get()
        assertEquals(1, reads)
        m.reset()
        m.get(); assertEquals("the screen resumed: read again", 2, reads)
    }

    @Test fun screenMemoKeepsNoFailedRead() {
        var reads = 0
        val m = ScreenMemo<Pair<Boolean, Boolean>> { reads++; if (reads == 1) error("AnkiDroid busy") else true to false }
        assertTrue(runCatching { m.get() }.isFailure)
        assertEquals(true to false, m.get()); m.get()
        assertEquals(2, reads)
    }

    // ---- the counts shown until the exact ones are read (learn, review, new as in the deck list)
    @Test fun answeredCardLeavesItsOwnQueueCount() {
        val d = Anki.Deck(7, "Spanish", learn = 3, review = 20, new = 5, dyn = false)
        assertEquals(d.copy(new = 4), QueueCounts.afterAnswer(d, 0))
        assertEquals(d.copy(learn = 2), QueueCounts.afterAnswer(d, 1))
        assertEquals(d.copy(learn = 2), QueueCounts.afterAnswer(d, 3))
        assertEquals(d.copy(review = 19), QueueCounts.afterAnswer(d, 2))
        assertEquals("preview / unknown queue: unchanged", d, QueueCounts.afterAnswer(d, 4))
        assertEquals(d, QueueCounts.afterAnswer(d, null))
    }

    @Test fun standInCountsNeverGoBelowZeroAndNeedARead() {
        val empty = Anki.Deck(7, "Spanish", 0, 0, 0, false)
        assertEquals(empty, QueueCounts.afterAnswer(empty, 0))
        assertEquals(empty, QueueCounts.afterAnswer(empty, 1))
        assertEquals(empty, QueueCounts.afterAnswer(empty, 2))
        assertNull("nothing read yet: nothing to show", QueueCounts.afterAnswer(null, 2))
    }

    // ---- deck names are read once
    @Test fun deckListIsReadOnceAndAgainOnlyForAnUnknownDeck() {
        var reads = 0
        val names = DeckNames { reads++; mapOf(1L to "A", 2L to "A::B") }
        assertEquals("A::B", names.name(2)); assertEquals("A", names.name(1)); assertEquals("A::B", names.name(2))
        assertEquals(1, reads)
        assertNull(names.name(9)); assertEquals(2, reads) // a deck made meanwhile: one more look
        assertNull(names.name(9)); assertEquals("not again for the same id", 2, reads)
    }

    @Test fun rememberedListIsUsedWithoutReading() {
        var reads = 0
        val names = DeckNames { reads++; emptyMap() }
        names.remember(mapOf(5L to "Spanish"))
        assertEquals("Spanish", names.name(5)); assertEquals(0, reads)
    }

    // ---- the selected deck's counts (same JSON order as the deck list: learn, review, new)
    @Test fun selectedDeckRowGivesCountsInTheDeckListOrder() {
        val d = Anki.Deck.from(mapOf("deck_id" to "42", "deck_name" to "Spanish::Verbs", "deck_count" to "[3,20,7]", "deck_dyn" to "0"))!!
        assertEquals(42L, d.id); assertEquals("Spanish::Verbs", d.name)
        assertEquals(3, d.learn); assertEquals(20, d.review); assertEquals(7, d.new); assertFalse(d.dyn)
        assertEquals(30, d.total)
        assertTrue(Anki.Deck.from(mapOf("deck_id" to "1", "deck_count" to "[0,0,0]", "deck_dyn" to "true"))!!.dyn)
    }

    @Test fun selectedDeckRowWithoutIdOrCountsIsNotUsed() {
        assertNull(Anki.Deck.from(mapOf("deck_name" to "x", "deck_count" to "[1,2,3]")))
        assertNull(Anki.Deck.from(mapOf("deck_id" to "1", "deck_name" to "x")))
        assertNull(Anki.Deck.from(mapOf("deck_id" to "1", "deck_count" to "not json")))
    }

    // ---- R9 wave 6: the STUDY line of the background-write flow
    private val all = AnswerTiming.ALL_PARTS

    @Test fun theAnswerCallAloneIsOnTheLineWithItsTries() {
        val t = AnswerTiming(0, all)
        t.put(AnswerTiming.ANSWER, 2247); t.attempts = 3
        assertTrue(t.line(), t.line().startsWith("answer: ANSWER 2247 ms (3 tries), save+verify -, "))
        val one = AnswerTiming(0, all); one.put(AnswerTiming.ANSWER, 31)
        assertTrue(one.line(), one.line().startsWith("answer: ANSWER 31 ms, save+verify -, "))
        assertFalse("a line without the call has no ANSWER", AnswerTiming(0, all).line().contains("ANSWER"))
    }

    @Test fun theWholeLineWithEveryNewPart() {
        val t = AnswerTiming(0, all)
        t.put(AnswerTiming.ANSWER, 2247); t.put(AnswerTiming.WRITE, 2290); t.queueAtTap = 0
        t.put(AnswerTiming.ABSORB, 3); t.put(AnswerTiming.GAME, 25); t.put(AnswerTiming.NEXT, 52); t.put(AnswerTiming.LIST, 4)
        t.next = AnswerTiming.HIT; t.put(AnswerTiming.CONFIRMED, 2310); t.put(AnswerTiming.SHOWN, 12); t.put(AnswerTiming.PAGE, 80)
        t.put(AnswerTiming.COUNTS, -1); t.loadRetries = 0; t.noteMainWait(14); t.noteMainWait(6); t.polls = 0
        assertEquals("answer: ANSWER 2247 ms, save+verify 2290 ms, write queue 0 at the tap, detector wait 3 ms, game step 25 ms, " +
            "next card 52 ms (deck list 4 ms), next card: predicted hit, confirmed after 2310 ms, tap to next card shown 12 ms, " +
            "page loaded 80 ms, deck counts read after it -, load retries 0, main wait 14 ms, detector polls meanwhile 0", t.line())
    }

    @Test fun theThreeKindsOfNextCardAreNamedAndAMissIsASwap() {
        for ((kind, text) in listOf(AnswerTiming.HIT to "predicted hit", AnswerTiming.MISS to "predicted miss (swap)", AnswerTiming.NONE to "none (waited)")) {
            val t = AnswerTiming(0, all); t.next = kind
            assertTrue(t.line(), t.line().contains("next card: $text"))
        }
        assertFalse(AnswerTiming(0, all).line().contains("next card:"))
    }

    @Test fun theWriteQueueLengthAndMainWaitShowOnlyWhenKnown() {
        val t = AnswerTiming(0, all)
        assertFalse(t.line().contains("write queue")); assertFalse(t.line().contains("main wait"))
        t.queueAtTap = 2
        assertTrue(t.line().contains("write queue 2 at the tap"))
        t.noteMainWait(0)
        assertTrue("a wait of 0 ms is a measurement too", t.line().contains("main wait 0 ms"))
    }

    @Test fun theLineWaitsForEveryPartAndIsLoggedOnce() {
        val t = AnswerTiming(0, all)
        val parts = listOf(AnswerTiming.GAME, AnswerTiming.SHOWN, AnswerTiming.PAGE, AnswerTiming.COUNTS, AnswerTiming.WRITE, AnswerTiming.CONFIRMED)
        for (part in parts.dropLast(1)) assertFalse(part, t.put(part, 1))
        assertTrue(t.put(parts.last(), 1))
        assertFalse("logged once", t.put(AnswerTiming.PAGE, 2))
        assertFalse("and the screen going afterwards does not log it again", t.flush())
    }

    @Test fun theScreenGoingLogsWhatTheLineHasExactlyOnce() {
        val t = AnswerTiming(0, all)
        t.put(AnswerTiming.WRITE, 900)
        assertTrue(t.flush()); assertFalse(t.flush())
        assertFalse("a part arriving later does not log it twice", t.put(AnswerTiming.GAME, 5))
        assertTrue(t.line().contains("save+verify 900 ms"))
    }

    @Test fun anOldLineStaysTheSameWithoutTheNewParts() {
        val t = AnswerTiming(0)
        t.put(AnswerTiming.WRITE, 120); t.put(AnswerTiming.GAME, 15); t.put(AnswerTiming.NEXT, 340); t.put(AnswerTiming.SHOWN, 700)
        assertEquals("answer: save+verify 120 ms, detector wait -, game step 15 ms, next card 340 ms (deck list -), tap to next card shown 700 ms", t.line())
    }

    // ---- the deck counts read (item 3)
    @Test fun noCountsReadWhileAnAnswerIsUnresolved() {
        assertFalse(CountsPolicy.mayStart(unresolved = true, firstOfVisit = true, now = 1_000_000, lastStartedAt = null))
        assertFalse(CountsPolicy.mayStart(unresolved = true, firstOfVisit = false, now = 1_000_000, lastStartedAt = 0))
    }

    @Test fun atMostOneCountsReadPerThirtySeconds() {
        assertFalse(CountsPolicy.mayStart(false, false, now = 29_999, lastStartedAt = 0))
        assertTrue(CountsPolicy.mayStart(false, false, now = 30_000, lastStartedAt = 0))
        assertFalse(CountsPolicy.mayStart(false, false, now = 31_000, lastStartedAt = 10_000))
        assertTrue(CountsPolicy.mayStart(false, false, now = 40_000, lastStartedAt = 10_000))
    }

    @Test fun theFirstCardOfAVisitAndTheFirstEverReadAreAllowed() {
        assertTrue(CountsPolicy.mayStart(false, firstOfVisit = true, now = 5, lastStartedAt = 0))
        assertTrue(CountsPolicy.mayStart(false, firstOfVisit = false, now = 5, lastStartedAt = null))
    }

    // ---- the queue read (item 2)
    private class Reads(val results: List<Any>, val due: Int? = null) {
        var asks = 0; var dueAsks = 0; val pauses = ArrayList<Int>()
        val query: () -> List<String> = { val r = results[minOf(asks++, results.size - 1)]; if (r is Exception) throw r else @Suppress("UNCHECKED_CAST") (r as List<String>) }
        val dueCount: () -> Int? = { dueAsks++; due }
        val pause: (Int) -> Unit = { pauses += it }
    }

    @Test fun aQueueThatComesBackIsAskedOnce() {
        val r = Reads(listOf(listOf("a", "b", "c")))
        val out = QueueRead.read(r.query, r.dueCount, r.pause)
        assertEquals(listOf("a", "b", "c"), out.items); assertEquals(0, out.retries); assertEquals(1, r.asks); assertEquals(0, r.dueAsks); assertTrue(r.pauses.isEmpty())
    }

    @Test fun anEmptyQueueIsNotAskedAgainWhenTheCountsSayNothingIsDue() {
        val r = Reads(listOf(emptyList<String>()), due = 0)
        val out = QueueRead.read(r.query, r.dueCount, r.pause)
        assertTrue(out.items.isEmpty()); assertFalse(out.dueButEmpty); assertEquals(0, out.retries); assertEquals(1, r.asks); assertEquals(1, r.dueAsks)
        val unknown = Reads(listOf(emptyList<String>()), due = null)
        assertFalse(QueueRead.read(unknown.query, unknown.dueCount, unknown.pause).dueButEmpty); assertEquals(1, unknown.asks)
    }

    @Test fun anEmptyQueueIsAskedAgainWhileTheCountsSayCardsAreDueAndTheRetriesAreCounted() {
        val r = Reads(listOf(emptyList<String>(), emptyList<String>(), listOf("a")), due = 4)
        val out = QueueRead.read(r.query, r.dueCount, r.pause)
        assertEquals(listOf("a"), out.items); assertEquals(2, out.retries); assertEquals(3, r.asks); assertEquals(listOf(1, 2), r.pauses)
        assertEquals("the counts are read once", 1, r.dueAsks)
    }

    @Test fun anEmptyQueueThatStaysEmptyWhileCardsAreDueEndsAsDueButEmptyAfterThreeAsks() {
        val r = Reads(listOf(emptyList<String>()), due = 4)
        val out = QueueRead.read(r.query, r.dueCount, r.pause)
        assertTrue(out.dueButEmpty); assertEquals(3, r.asks); assertEquals(2, out.retries)
    }

    @Test fun aQueryThatThrowsIsAskedAgainWithoutReadingTheCounts() {
        val r = Reads(listOf(IllegalStateException("busy"), listOf("a")))
        val out = QueueRead.read(r.query, r.dueCount, r.pause)
        assertEquals(listOf("a"), out.items); assertEquals(1, out.retries); assertEquals(0, r.dueAsks); assertEquals(listOf(1), r.pauses)
    }

    @Test fun aQueryThatKeepsThrowingFailsWithTheLastErrorAfterThreeTries() {
        val r = Reads(listOf(IllegalStateException("one"), IllegalStateException("two"), IllegalStateException("three")))
        val e = runCatching { QueueRead.read(r.query, r.dueCount, r.pause) }.exceptionOrNull()
        assertEquals("three", e?.message); assertEquals(3, r.asks); assertEquals(listOf(1, 2), r.pauses)
    }

    // ---- what the user is told
    @Test fun everyReasonHasItsOwnText() {
        val texts = AnswerOutcome.Reason.entries.map { AnswerNotes.notWritten(it) }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(AnswerNotes.notWritten(AnswerOutcome.Reason.NOT_TOP).startsWith("AnkiDroid showed another card first"))
        assertTrue(AnswerNotes.notWritten(AnswerOutcome.Reason.CHANGED).startsWith("That card changed in AnkiDroid"))
        assertTrue(AnswerNotes.notWritten(AnswerOutcome.Reason.UNSURE).contains("Check it in AnkiDroid"))
        assertTrue("it says the answer was not saved", AnswerNotes.notWritten(AnswerOutcome.Reason.DISCARDED).contains("wasn't saved"))
    }

    @Test fun theNoticeTextsSayWhatHappenedAndWhatToDo() {
        assertTrue(AnswerNotes.RETRYING.startsWith("Your last answer isn't saved in AnkiDroid yet."))
        assertTrue(AnswerNotes.RETRYING.endsWith("AnkiGo keeps it and tries again."))
        assertTrue(AnswerNotes.GIVE_UP_ASK.contains("The card will come up again in AnkiDroid."))
        assertEquals("Answer saved", AnswerNotes.SAVED_LATE)
        assertTrue("a held tap that is dropped says the card is still due", AnswerNotes.HELD_LOST.contains("still due"))
        assertEquals(6, setOf(AnswerNotes.RETRYING, AnswerNotes.PARKED, AnswerNotes.SAVED_LATE, AnswerNotes.SAVING, AnswerNotes.WAIT, AnswerNotes.HELD_LOST).size)
    }
}
