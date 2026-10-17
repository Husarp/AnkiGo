package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An own answer (made on the study screen) is counted by the game exactly once: the detector must neither report it
 * again nor swallow a real answer, whether a poll sees the rep change before or after the answer is absorbed.
 */
class CardLedgerTest {
    private val noSteps: (CardState) -> Steps? = { null }

    private fun review(id: Long, reps: Int) =
        CardState(id, id + 100, 1, 0, reps, 0, 2, 2, 100, 10, 2500, 0, null, null, 1000L)

    /** A ledger that knows card 1 at 5 reps and expects the own answer that makes it 6. */
    private fun ledgerWithOwnAnswer(): CardLedger = CardLedger().also {
        it.cards[1L] = review(1, 5)
        it.own.expect(1, 6, 0)
    }

    private fun CardLedger.poll(n: CardState, nowMs: Long = 10): List<LiveDetector.Event> {
        val out = ArrayList<LiveDetector.Event>()
        compare(n, setOf(n.id), noSteps, nowMs, 2000L, out)
        return out
    }

    @Test fun pollSeesTheRepChangeBeforeTheAbsorb() {
        val l = ledgerWithOwnAnswer()
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 6)))
        l.absorb(review(1, 6), 20)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 6)))
        assertEquals(6, l.cards[1L]!!.reps)
    }

    @Test fun absorbComesBeforeThePoll() {
        val l = ledgerWithOwnAnswer()
        l.absorb(review(1, 6), 10)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 6)))
        assertEquals(6, l.cards[1L]!!.reps)
    }

    @Test fun aPollThatReadTheCardBeforeTheWriteDoesNotCountAnything() {
        val l = ledgerWithOwnAnswer()
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 5))) // a slow poll: it read the card before the answer
        l.absorb(review(1, 6), 20)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 6)))
    }

    @Test fun theExpectationIsSettledOnceEitherWay() {
        val a = ledgerWithOwnAnswer(); a.poll(review(1, 6)); a.absorb(review(1, 6), 20)
        val b = ledgerWithOwnAnswer(); b.absorb(review(1, 6), 10); b.poll(review(1, 6))
        for (l in listOf(a, b)) {
            // Nothing is left over that could swallow a later real answer ...
            assertEquals(0, l.own.consume(1, 6, 7, 30))
            // ... which is reported once.
            val out = l.poll(review(1, 7))
            assertEquals(1, out.size)
            assertTrue(out[0] is LiveDetector.Event.Answer && (out[0] as LiveDetector.Event.Answer).cardId == 1L)
        }
    }

    @Test fun anAnswerFromAnkiDroidInTheSamePollIsStillCounted() {
        val l = ledgerWithOwnAnswer()
        val out = l.poll(review(1, 7)) // the own answer (6) and one made in AnkiDroid (7) arrive together
        assertEquals(1, out.size) // one for AnkiDroid's, the own one is not reported
        l.absorb(review(1, 6), 20)
        assertEquals("the absorb never takes a card back", 7, l.cards[1L]!!.reps)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 7)))
    }

    @Test fun twoOwnAnswersOfOneCardAreBothSuppressedInAnyOrder() {
        val l = ledgerWithOwnAnswer(); l.own.expect(1, 7, 0)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 7)))
        l.absorb(review(1, 6), 20); l.absorb(review(1, 7), 21)
        assertEquals(7, l.cards[1L]!!.reps)
        val m = ledgerWithOwnAnswer(); m.own.expect(1, 7, 0)
        m.absorb(review(1, 6), 10); m.absorb(review(1, 7), 11)
        assertEquals(emptyList<LiveDetector.Event>(), m.poll(review(1, 7)))
    }

    @Test fun aCanceledOwnAnswerIsNotSuppressed() {
        val l = ledgerWithOwnAnswer()
        l.own.cancel(1, 6)
        assertEquals(1, l.poll(review(1, 6)).size)
    }

    @Test fun anExpiredExpectationDoesNotSuppress() {
        val l = ledgerWithOwnAnswer()
        assertEquals("the game step must run well inside 30 s", 1, l.poll(review(1, 6), nowMs = 30_001).size)
    }

    @Test fun repsGoingDownIsAnUndo() {
        val l = ledgerWithOwnAnswer()
        val out = l.poll(review(1, 4))
        assertEquals(listOf<LiveDetector.Event>(LiveDetector.Event.Undo(1, 1)), out)
    }

    @Test fun theSnapshotHasTheAnsweredCardOnceItIsAbsorbed() {
        val l = ledgerWithOwnAnswer()
        l.absorb(review(1, 6), 10)
        val saved = decode(l.snapshot(setOf(1L)))
        assertEquals(6, saved[1L]!!.reps)
        assertFalse(l.dirty)
    }

    @Test fun aCardNeverSeenBeforeIsKnownAfterTheAbsorb() {
        val l = CardLedger()
        l.own.expect(2, 1, 0)
        l.absorb(review(2, 1), 10)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(2, 1)))
        assertEquals(1, decode(l.snapshot(setOf(2L)))[2L]!!.reps)
    }

    // ---- polls paused while the study screen is in front (LiveSession.hush): own answers are absorbed meanwhile
    @Test fun ownAnswersAbsorbedDuringAPauseAreNotCountedByThePollAfterIt() {
        val l = ledgerWithOwnAnswer()
        l.absorb(review(1, 6), 10)
        l.own.expect(1, 7, 20); l.absorb(review(1, 7), 30) // two own answers, no poll in between
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 7), nowMs = 60_000))
        assertEquals(7, l.cards[1L]!!.reps)
    }

    @Test fun anAnswerMadeInAnkiDroidDuringThePauseIsCountedOnceByThePollAfterIt() {
        val l = ledgerWithOwnAnswer()
        l.absorb(review(1, 6), 10) // own answer on the study screen
        val events = l.poll(review(1, 7), nowMs = 60_000) // one more answer in AnkiDroid while polls were paused
        assertEquals(1, events.size)
        assertTrue(events.single() is LiveDetector.Event.Answer)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(review(1, 7), nowMs = 61_000))
    }
}
