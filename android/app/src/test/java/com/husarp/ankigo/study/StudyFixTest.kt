package com.husarp.ankigo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4 fix batch B1 (the deck counts' read must not hold up answers; its result is shown only while still current) and B11 (the card's real name). */
class StudyFixTest {
    // ---- B1 ----

    @Test fun aReadIsAcceptedWhileItsCardIsOnScreenAndNoAnswerWasStarted() {
        val gate = CountsGate()
        val t = gate.ticket(7)
        assertTrue(gate.accept(t, 7))
    }

    @Test fun aReadThatBeganBeforeAnAnswerIsDropped() {
        val gate = CountsGate()
        val t = gate.ticket(7)   // the counts of card 7 are being read ...
        gate.answerStarted()     // ... and the answer is tapped: the read may predate its save
        assertFalse(gate.accept(t, 7))
    }

    @Test fun aReadForAnOlderCardIsDropped() {
        val gate = CountsGate()
        val t = gate.ticket(7)
        assertFalse(gate.accept(t, 8)) // the next card replaced it
    }

    @Test fun theNextCardsReadIsAcceptedAfterTheAnswer() {
        val gate = CountsGate()
        val first = gate.ticket(7)
        gate.answerStarted()
        val second = gate.ticket(8) // taken when the next card started loading, after the answer
        assertFalse(gate.accept(first, 8))
        assertTrue(gate.accept(second, 8))
        gate.answerStarted()        // another answer: that one is stale again
        assertFalse(gate.accept(second, 8))
    }

    @Test fun manyAnswersEachInvalidateEarlierReadsOnly() {
        val gate = CountsGate()
        val tickets = (1..5).map { n -> gate.ticket(n).also { gate.answerStarted() } }
        val last = gate.ticket(6)
        assertTrue(tickets.indices.none { gate.accept(tickets[it], it + 1) })
        assertTrue(gate.accept(last, 6))
    }

    // ---- B11 ----

    @Test fun theCardNameComesFromTheCardQuery() {
        assertEquals("Reverse", StudyRules.cardName(mapOf("card_name" to "Reverse", "question" to "q"), mapOf("queue" to "2"), 1))
    }

    @Test fun theStateQueryIsTheSecondChoice() {
        assertEquals("Forward", StudyRules.cardName(mapOf("card_name" to null), mapOf("card_name" to "Forward"), 0))
        assertEquals("Forward", StudyRules.cardName(mapOf("card_name" to "  "), mapOf("card_name" to "Forward"), 0))
    }

    @Test fun cardNFallbackOnlyWhenNeitherHasAName() {
        assertEquals("Card 1", StudyRules.cardName(emptyMap(), emptyMap(), 0))
        assertEquals("Card 3", StudyRules.cardName(null, null, 2))
        assertEquals("Card 2", StudyRules.cardName(mapOf("card_name" to "null"), mapOf("card_name" to ""), 1))
    }
}
