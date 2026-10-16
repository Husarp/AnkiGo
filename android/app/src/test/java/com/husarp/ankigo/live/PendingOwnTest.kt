package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.Steps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Own answers whose game step has not run yet: the saved snapshot leaves them out (withoutPendingOwn), a detector that
 * starts meanwhile gets them as known (withPendingOwn). Checked through what a restart (catch-up) would count.
 */
class PendingOwnTest {
    private val noSteps: (CardState) -> Steps? = { null }

    private fun review(id: Long, reps: Int) =
        CardState(id, id + 100, 1, 0, reps, 0, 2, 2, 100, 10, 2500, 0, null, null, 1000L)

    private fun snap(vararg c: CardState) = encode(c.associateBy { it.id })
    private fun reps(json: String) = decode(json).mapValues { it.value.reps }

    /** The cards a restart would catch up, given what was saved and what AnkiDroid holds now. */
    private fun caughtUp(saved: String, now: List<CardState>) =
        catchUp(decode(saved), now, now.map { it.id }.toSet(), noSteps, 2000L).map { it.cardId }.sorted()

    private fun CardLedger.poll(n: CardState): List<LiveDetector.Event> {
        val out = ArrayList<LiveDetector.Event>()
        compare(n, setOf(n.id), noSteps, 10, 2000L, out)
        return out
    }

    // ---- the two helpers ----

    @Test fun nothingPendingChangesNothing() {
        val s = snap(review(1, 5))
        assertSame(s, withoutPendingOwn(s, emptyList()))
        assertSame(s, withPendingOwn(s, emptyList()))
        assertEquals(null, withPendingOwn(null, emptyList()))
    }

    @Test fun pendingRowsAreAddedWhereTheSnapshotLacksThemAndNeverMoveACardBack() {
        val a = review(1, 6); val newCard = review(2, 1); val ahead = review(3, 8)
        val patched = withPendingOwn(snap(review(1, 5), review(3, 9)), listOf(a, newCard, ahead))!!
        assertEquals(mapOf(1L to 6, 3L to 9, 2L to 1), reps(patched))
        assertEquals(mapOf(1L to 6), reps(withPendingOwn(null, listOf(a))!!))
        assertEquals(mapOf(1L to 6), reps(withPendingOwn("not json", listOf(a))!!))
    }

    @Test fun heldBackCardsGoOneRepBackAndANewCardLeavesTheMap() {
        val s = snap(review(1, 6), review(2, 1), review(3, 4))
        assertEquals(mapOf(1L to 5, 3L to 4), reps(withoutPendingOwn(s, listOf(review(1, 6), review(2, 1)))))
    }

    @Test fun twoPendingAnswersOfOneCardGoTwoRepsBackAndAnAnswerTheSnapshotHasNotReachedChangesNothing() {
        assertEquals(mapOf(1L to 5), reps(withoutPendingOwn(snap(review(1, 7)), listOf(review(1, 6), review(1, 7)))))
        assertEquals(mapOf(1L to 5), reps(withoutPendingOwn(snap(review(1, 6)), listOf(review(1, 6), review(1, 7)))))
        assertEquals(mapOf(1L to 5), reps(withoutPendingOwn(snap(review(1, 5)), listOf(review(1, 6)))))
    }

    @Test fun anAnswerFromAnkiDroidOnTheSameCardStaysCounted() {
        // own answer made it 7, an external one (already given to the game) 8: only the own one is taken back
        assertEquals(mapOf(1L to 7), reps(withoutPendingOwn(snap(review(1, 8)), listOf(review(1, 7)))))
    }

    @Test fun anUnreadableSnapshotIsHandedBackAsItIs() {
        assertEquals("not json", withoutPendingOwn("not json", listOf(review(1, 6))))
    }

    // ---- orderings, seen through a restart ----

    @Test fun twoOwnAnswersAbsorbedBeforeTheFirstGameStepRuns() {
        val l = CardLedger()
        l.cards[1L] = review(1, 5); l.cards[2L] = review(2, 5)
        l.own.expect(1, 6, 0); l.own.expect(2, 6, 0)
        val a = review(1, 6); val b = review(2, 6)
        l.absorb(a, 10); l.absorb(b, 10) // the worker is ahead of the main thread
        val newest = l.snapshot(setOf(1L, 2L))
        // main: A's step starts, B is still pending. If the app dies now, only B is missing from the game.
        val savedByA = withoutPendingOwn(newest, listOf(b))
        val now = listOf(a, b)
        assertEquals(listOf(2L), caughtUp(savedByA, now))
        // B's step: nothing pending any more, both counted.
        assertEquals(emptyList<Long>(), caughtUp(withoutPendingOwn(newest, emptyList()), now))
    }

    @Test fun twoOwnAnswersOfOneCardAbsorbedBeforeTheFirstGameStepRuns() {
        val l = CardLedger()
        l.cards[1L] = review(1, 5)
        l.own.expect(1, 6, 0); l.own.expect(1, 7, 0)
        val a = review(1, 6); val b = review(1, 7)
        l.absorb(a, 10); l.absorb(b, 10)
        val newest = l.snapshot(setOf(1L))
        assertEquals(listOf(1L), caughtUp(withoutPendingOwn(newest, listOf(b)), listOf(b)))
        assertEquals(emptyList<Long>(), caughtUp(withoutPendingOwn(newest, emptyList()), listOf(b)))
    }

    @Test fun aPollSettlesTheOwnAnswerFirstAndAnExternalAnswerIsDeliveredBeforeTheAbsorb() {
        val l = CardLedger()
        l.cards[1L] = review(1, 5); l.cards[2L] = review(2, 5)
        l.own.expect(1, 6, 0)
        val a = review(1, 6); val x = review(2, 6)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(a)) // own answer: suppressed, the game step still waits for the absorb
        val events = l.poll(x) // the external answer is given to the game together with this snapshot
        assertEquals(1, events.size)
        val atPost = l.snapshot(setOf(1L, 2L))
        // The game saves while counting X and not yet A: a restart must count A (once) and not X.
        assertEquals(listOf(1L), caughtUp(withoutPendingOwn(atPost, listOf(a)), listOf(a, x)))
        // The old behaviour (the snapshot as it was) would have lost A.
        assertEquals(emptyList<Long>(), caughtUp(atPost, listOf(a, x)))
        // The absorb comes later: it settles nothing twice and the next save contains A.
        l.absorb(a, 20)
        assertEquals(emptyList<Long>(), caughtUp(withoutPendingOwn(l.snapshot(setOf(1L, 2L)), emptyList()), listOf(a, x)))
    }

    @Test fun aRestartWhileTheOwnAnswerWaitsNeitherCountsItInTheBaselineNorLosesIt() {
        val a = review(1, 6)
        val saved = withoutPendingOwn(snap(a), listOf(a)) // what was saved with the game: card 1 not in it
        val now = listOf(a)
        // Without the patch the new baseline would catch the answer up and the absorb would count it again.
        assertEquals(listOf(1L), caughtUp(saved, now))
        // With it the baseline knows the answer; the absorb that is still queued counts it, once.
        assertEquals(emptyList<Long>(), caughtUp(withPendingOwn(saved, listOf(a))!!, now))
        // The new session's ledger absorbs it without reporting anything, and the save after its step contains it.
        val l = CardLedger(); l.cards[1L] = a; l.own.expect(1, 6, 0)
        l.absorb(a, 10)
        assertEquals(emptyList<LiveDetector.Event>(), l.poll(a))
        assertEquals(mapOf(1L to 6), reps(withoutPendingOwn(l.snapshot(setOf(1L)), emptyList())))
    }

    @Test fun aRestartWhileTheOwnAnswerWaitsAndAnotherAnswerWasMadeInAnkiDroid() {
        val a = review(1, 6)
        val saved = withoutPendingOwn(snap(review(1, 6), review(2, 5)), listOf(a))
        val now = listOf(a, review(2, 6)) // card 2 was answered in AnkiDroid meanwhile
        assertEquals(listOf(2L), caughtUp(withPendingOwn(saved, listOf(a))!!, now))
    }
}
