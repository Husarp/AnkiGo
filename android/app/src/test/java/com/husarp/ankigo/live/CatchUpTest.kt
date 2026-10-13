package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.GUESS
import com.husarp.ankigo.SURE
import com.husarp.ankigo.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpTest {
    private val steps = Steps(listOf(1.0, 10.0), listOf(10.0), 1, 4)
    private val noSteps: (CardState) -> Steps? = { steps }

    /** A review card (type 2) with sensible values. */
    private fun review(id: Long, reps: Int = 5, lapses: Int = 0, factor: Int = 2500, ivl: Int = 10) =
        CardState(id, id + 100, 1, 0, reps, lapses, 2, 2, 100, ivl, factor, 0, null, null, 1000L)

    private fun run(restored: Map<Long, CardState>?, base: List<CardState>, rated: Set<Long>) =
        catchUp(restored, base, rated, noSteps, 2000L)

    @Test fun codecRoundTripWithNullsAndValues() {
        val a = review(1).copy(lastRev = null)
        val b = review(2).copy(stab = 12.5, diff = 5.25, lastRev = 1234567890L, odid = 7, left = 1002)
        val map = linkedMapOf(1L to a, 2L to b)
        val back = decode(encode(map))
        assertEquals(map, back)
        assertNull(back[1L]!!.stab); assertNull(back[1L]!!.diff); assertNull(back[1L]!!.lastRev)
        assertEquals(12.5, back[2L]!!.stab!!, 0.0)
    }

    @Test fun codecEmpty() {
        assertEquals(emptyMap<Long, CardState>(), decode(encode(emptyMap())))
    }

    @Test fun grewByOneInfersButton() {
        val old = review(1, reps = 5, lapses = 0)
        val now = old.copy(reps = 6, lapses = 1, type = 3, queue = 1, left = 1001) // lapse -> Again, SURE
        val r = run(mapOf(1L to old), listOf(now), setOf(1L))
        assertEquals(listOf(LiveDetector.Event.Answer(1L, 1, SURE, true)), r)
    }

    @Test fun grewByThreeGivesThreeAnswersButtonZeroFirst() {
        val old = review(1, reps = 5)
        val r = run(mapOf(1L to old), listOf(old.copy(reps = 8)), setOf(1L))
        assertEquals(3, r.size)
        assertTrue(r.all { it.cardId == 1L && it.button == 0 && it.conf == GUESS && it.catchUp })
    }

    @Test fun unchangedGivesNothing() {
        val old = review(1)
        assertTrue(run(mapOf(1L to old), listOf(old), setOf(1L)).isEmpty())
    }

    @Test fun repsDownFromUndoGivesNothing() {
        val old = review(1, reps = 5)
        assertTrue(run(mapOf(1L to old), listOf(old.copy(reps = 4)), setOf(1L)).isEmpty())
    }

    @Test fun unknownCardWithRepsOneInfersFromFresh() {
        // new card, one step left of two (Good on step 1) -> Good, SURE
        val now = CardState(9, 109, 1, 0, 1, 0, 1, 1, 3000, 0, 0, 1001, null, null, 1990L)
        val r = run(mapOf(1L to review(1)), listOf(now), setOf(9L))
        assertEquals(listOf(LiveDetector.Event.Answer(9L, 3, SURE, true)), r)
    }

    @Test fun unknownCardWithMoreRepsIsOneAnswerButtonZero() {
        val r = run(emptyMap(), listOf(review(4, reps = 4)), setOf(4L))
        assertEquals(listOf(LiveDetector.Event.Answer(4L, 0, GUESS, true)), r)
    }

    @Test fun nullSnapshotCountsEachRatedCardOnceInBaselineOrder() {
        val base = listOf(review(3, reps = 9), review(1, reps = 7), review(2, reps = 2))
        val r = run(null, base, setOf(1L, 2L, 3L))
        assertEquals(listOf(3L, 1L, 2L), r.map { it.cardId })
        assertTrue(r.all { it.button == 0 && it.catchUp })
    }

    @Test fun cardsNotRatedTodayAreIgnored() {
        val old = review(1, reps = 5)
        val base = listOf(old.copy(reps = 7), review(2, reps = 3))
        assertTrue(run(mapOf(1L to old), base, emptySet()).isEmpty())
        assertTrue(run(null, base, setOf(99L)).isEmpty())
    }

    @Test fun ratedIdMissingFromBaselineIsIgnored() {
        assertTrue(run(null, listOf(review(1)), setOf(1L, 55L)).size == 1)
    }

    @Test fun mixedBatchKeepsCardOrder() {
        val a = review(1, reps = 5); val b = review(2, reps = 5)
        val base = listOf(a.copy(reps = 7), b.copy(reps = 6, lapses = 1, type = 3, queue = 1))
        val r = run(mapOf(1L to a, 2L to b), base, setOf(1L, 2L))
        assertEquals(listOf(1L, 1L, 2L), r.map { it.cardId })
        assertEquals(1, r[2].button)
    }
}
