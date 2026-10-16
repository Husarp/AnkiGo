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
        t.note = "counts from the selected deck"
        assertEquals("answer: save+verify 120 ms, detector wait 4800 ms, game step 15 ms, next card 340 ms " +
            "(deck counts 45 ms, deck list -), tap to next card shown 700 ms; counts from the selected deck", t.line())
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
