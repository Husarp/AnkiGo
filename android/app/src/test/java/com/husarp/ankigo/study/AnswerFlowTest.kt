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
}
