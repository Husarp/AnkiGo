package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.Steps
import com.husarp.ankigo.study.NewCardOk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 155 (R14-B2-F), companion mode: the ledger says whether the card was NEW before an answer seen in AnkiDroid, and with the
 * setting on an Again or Hard on such a card is neutral (the rule is NewCardOk.companionNeutral, applied by LiveSession).
 */
class NewCardOkLedgerTest {
    private val steps = Steps(listOf(1.0, 10.0), listOf(10.0), 1, 4)
    private val withSteps: (CardState) -> Steps? = { steps }

    private fun fresh(id: Long) = CardState(id, id + 100, 1, 0, 0, 0, 0, 0, 5, 0, 0, 0, null, null, null)
    /** The new card after one answer on the first step: Again (back to step 1, due in 1 min), Good (one step left, 10 min), Hard (5.5 min). */
    private fun afterAgain(id: Long, reps: Int = 1) = CardState(id, id + 100, 1, 0, reps, 0, 1, 1, 2060, 0, 0, 2002, null, null, 2000L)
    private fun afterGood(id: Long) = CardState(id, id + 100, 1, 0, 1, 0, 1, 1, 2600, 0, 0, 1001, null, null, 2000L)
    private fun afterHard(id: Long) = CardState(id, id + 100, 1, 0, 1, 0, 1, 1, 2330, 0, 0, 2002, null, null, 2000L)
    private fun review(id: Long, reps: Int) = CardState(id, id + 100, 1, 0, reps, 0, 2, 2, 100, 10, 2500, 0, null, null, 1000L)
    private fun lapsed(id: Long, reps: Int) = CardState(id, id + 100, 1, 0, reps, 1, 3, 1, 2060, 5, 2300, 1001, null, null, 2000L)

    private fun CardLedger.poll(n: CardState): List<LiveDetector.Event.Answer> {
        val out = ArrayList<LiveDetector.Event>()
        compare(n, setOf(n.id), withSteps, 10, 2000L, out)
        return out.filterIsInstance<LiveDetector.Event.Answer>()
    }

    private fun neutral(on: Boolean, e: LiveDetector.Event.Answer) = NewCardOk.companionNeutral(on, e.wasNew, e.button)

    @Test fun againOnACardTheLedgerKnewAsNewIsNeutralWithTheSettingOn() {
        val l = CardLedger(); l.cards[1L] = fresh(1)
        val e = l.poll(afterAgain(1)).single()
        assertEquals(1, e.button)
        assertTrue(e.wasNew)
        assertTrue(neutral(true, e))
        assertFalse("setting off", neutral(false, e))
    }

    @Test fun hardOnANewCardIsNeutralToo() {
        val l = CardLedger(); l.cards[1L] = fresh(1)
        val e = l.poll(afterHard(1)).single()
        assertEquals(2, e.button)
        assertTrue(neutral(true, e))
    }

    @Test fun goodOnANewCardIsNotNeutral() {
        val l = CardLedger(); l.cards[1L] = fresh(1)
        val e = l.poll(afterGood(1)).single()
        assertEquals(3, e.button)
        assertTrue("it was new", e.wasNew)
        assertFalse(neutral(true, e))
    }

    @Test fun aCardFirstSeenAfterItsFirstAnswerWasNew() {
        val l = CardLedger()   // nothing known: reps == 1 means it was new before (CardState.fresh)
        val e = l.poll(afterAgain(2)).single()
        assertTrue(e.wasNew)
        assertTrue(neutral(true, e))
    }

    @Test fun anAgainOnAReviewCardIsNotNeutral() {
        val l = CardLedger(); l.cards[3L] = review(3, 5)
        val e = l.poll(lapsed(3, 6)).single()
        assertEquals(1, e.button)
        assertFalse(e.wasNew)
        assertFalse(neutral(true, e))
    }

    @Test fun aLearningCardAnsweredAgainIsNotNew() {
        val l = CardLedger(); l.cards[4L] = afterAgain(4, reps = 1)
        val e = l.poll(afterAgain(4, reps = 2).copy(lastRev = 2000L)).single()
        assertFalse(e.wasNew)
        assertFalse(neutral(true, e))
    }

    @Test fun onlyTheFirstOfSeveralAnswersBetweenTwoPollsCanHaveBeenOnANewCard() {
        val l = CardLedger(); l.cards[5L] = fresh(5)
        val events = l.poll(afterAgain(5, reps = 3))
        assertEquals(3, events.size)
        assertEquals(listOf(true, false, false), events.map { it.wasNew })
    }

    @Test fun aNewCardAnsweredInTheSamePollAsAnOwnAnswerIsNotReportedAsNew() {
        val l = CardLedger(); l.cards[6L] = fresh(6); l.own.expect(6, 1, 0)   // the study screen answered it ...
        val events = l.poll(afterAgain(6, reps = 2))                          // ... and AnkiDroid once more before the poll
        assertEquals(1, events.size)
        assertFalse("the reported answer came after the own one", events.single().wasNew)
    }

    @Test fun theCatchUpAfterAClosedAppKnowsAFreshCardToo() {
        val r = catchUp(null, listOf(afterAgain(8)), setOf(8L), withSteps, 2000L).single()
        assertTrue(r.catchUp); assertTrue(r.wasNew); assertEquals(1, r.button)
        val old = catchUp(mapOf(9L to review(9, 5)), listOf(lapsed(9, 6)), setOf(9L), withSteps, 2000L).single()
        assertFalse(old.wasNew)
        val many = catchUp(mapOf(10L to fresh(10)), listOf(afterAgain(10, reps = 3)), setOf(10L), withSteps, 2000L)
        assertTrue("several answers: the buttons are unknown, so never neutral", many.none { neutral(true, it) })
    }
}
