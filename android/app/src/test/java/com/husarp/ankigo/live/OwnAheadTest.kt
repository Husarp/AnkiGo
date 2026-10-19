package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10-B23: own answers counted at the tap, kept as their predicted row until the write is settled. */
class OwnAheadTest {
    private val noSteps: (CardState) -> Steps? = { null }

    private fun review(id: Long, reps: Int) = CardState(id, id + 100, 1, 0, reps, 0, 2, 2, 100, 10, 2500, 0, null, null, 1000L)
    private fun snap(vararg c: CardState) = encode(c.associateBy { it.id })
    private fun reps(json: String?) = decode(json!!).mapValues { it.value.reps }

    @Test fun aRowIsTheCardOneRepFurtherKeptOnceUntilItLeaves() {
        val o = OwnAhead()
        val before = review(1, 5)
        assertEquals(6, o.add(before).reps)
        o.add(before)
        assertEquals("added twice = one row", 1, o.pending.size)
        assertNull("another answer's row is not there", o.remove(review(1, 6)))
        assertEquals(6, o.remove(before)!!.reps)
        assertTrue(o.isEmpty())
    }

    @Test fun theSavedMapHoldsEveryRowButNeverTakesACardBack() {
        val o = OwnAhead()
        o.add(review(1, 5)); o.add(review(2, 0))
        assertEquals(mapOf(1L to 6, 2L to 1, 3L to 4), reps(o.into(snap(review(1, 5), review(3, 4)))))
        assertEquals("the detector already has the real state, or later", mapOf(1L to 7, 2L to 1), reps(o.into(snap(review(1, 7)))))
        assertEquals(mapOf(1L to 6, 2L to 1), reps(o.into(null)))
        assertEquals("no rows: the map as it is", "[]", OwnAhead().into("[]"))
    }

    @Test fun countedMeansSomeMapHoldsTheCardAtTheRepTheAnswerMakesOrLater() {
        val before = review(1, 5)
        assertTrue(OwnAhead.counted(before, snap(review(1, 6))))
        assertTrue(OwnAhead.counted(before, snap(review(1, 9))))
        assertFalse("still at the rep before the answer", OwnAhead.counted(before, snap(review(1, 5))))
        assertFalse(OwnAhead.counted(before, snap(review(2, 6))))
        assertTrue("the detector's map", OwnAhead.counted(before, snap(review(1, 5)), snap(review(1, 6))))
        assertFalse("no map, unreadable map", OwnAhead.counted(before, null, "not json"))
    }

    /** What a restart's catch-up counts, from the map saved with the game, when AnkiDroid holds [now]. */
    private fun caughtUp(saved: String?, now: List<CardState>) =
        catchUp(saved?.let { decode(it) }, now, now.map { it.id }.toSet(), noSteps, 2000L).map { it.cardId }

    @Test fun aSaveMadeAfterTheTapNeverLetsARestartCountTheWriteAgain() {
        val o = OwnAhead()
        val before = review(1, 5)
        o.add(before)
        val saved = o.into(snap(review(2, 3)))
        assertEquals("written meanwhile: nothing to catch up", emptyList<Long>(), caughtUp(saved, listOf(review(1, 6), review(2, 3))))
        assertEquals("a save from before the tap: the catch-up counts it (the step died with the process)", listOf(1L), caughtUp(snap(review(2, 3)), listOf(review(1, 6), review(2, 3))))
    }

    @Test fun theBaselineDoesNotCountAnOwnAnswerItExpects() {
        val own = OwnAnswers()
        own.expect(1, 6, 0, pinned = true)
        val now = listOf(review(1, 6), review(2, 4))
        val restored = decode(snap(review(2, 3))) // the save holds neither the row nor card 1
        assertEquals("only card 2 (answered in AnkiDroid while AnkiGo was closed)", listOf(2L), baselineAnswers(restored, now, setOf(1L, 2L), noSteps, 2000L, own, 10).map { it.cardId })
        assertEquals("the expectation is settled", 0, own.consume(1, 5, 6, 10))
    }

    @Test fun aPollWhileTheWriteIsPendingSeesNothingAndThePinnedExpectationSwallowsTheWrite() {
        val l = CardLedger()
        l.cards[1] = review(1, 5)
        l.own.expect(1, 6, 0, pinned = true)
        val out = ArrayList<LiveDetector.Event>()
        l.compare(review(1, 5), setOf(1L), noSteps, 999_999, 2000L, out)
        assertTrue("not written yet", out.isEmpty())
        l.compare(review(1, 6), setOf(1L), noSteps, 999_999, 2000L, out)
        assertTrue("the write lands minutes later: still ours", out.isEmpty())
        l.absorb(review(1, 6), 999_999)
        assertEquals(6, l.cards[1]!!.reps)
    }
}
