package com.husarp.ankigo.study

import com.husarp.ankigo.study.PredictRule.Why
import org.junit.Assert.*
import org.junit.Test

/** R10-X1: when the prepared next card may show at once after an answer (each cause), and the learning read it decides with. */
class PredictRuleTest {
    private val now = 1_700_000_000_000L // epoch ms (a whole second)
    private val nowSecs = now / 1000

    private fun card(id: Long, queue: Int = 2, due: Long = 900, nid: Long = 100 + id, learning: PredictRule.LearnRead? = null) = Prepared(
        StudyLoop.Key(nid, 0), mapOf("_id" to "$id", "note_id" to "$nid", "queue" to "$queue", "due" to "$due", "reps" to "3"), "c$id", learning,
    )

    /** Learning cards (id to due in epoch seconds) read 30 s before the tap. */
    private fun read(vararg due: Pair<Long, Long>, at: Long = now - 30_000) = PredictRule.LearnRead(at, due.map { PredictRule.Due(it.first, it.second) })

    private fun check(next: Prepared<String>, answered: Prepared<String> = card(1)) = PredictRule.check(answered, next, now)

    // ---- the cause: a learning card that comes due while a card is on screen jumps to the front when the answer is applied

    @Test fun aMainQueueCardWithNoLearningCardDueIsSafe() {
        assertEquals(Why.SAFE, check(card(2, learning = read())))
        assertEquals("learning cards due later are fine", Why.SAFE, check(card(2, learning = read(9L to nowSecs + 600, 8L to nowSecs + 60))))
    }

    @Test fun aLearningCardThatCameDueWhileTheCardWasShownMakesTheAnswerWait() {
        assertEquals(Why.LEARNING_DUE, check(card(2, learning = read(9L to nowSecs - 20))))
        assertEquals("a review card read second goes behind it too", Why.LEARNING_DUE, check(card(2, queue = 2, learning = read(9L to nowSecs))))
        assertEquals("an interday learning card (main queue) too", Why.LEARNING_DUE, check(card(2, queue = 3, due = 19_000, learning = read(9L to nowSecs))))
    }

    @Test fun aLearningCardDueWithinTheSlackOfTheWriteWaitsOneDueAfterItIsSafe() {
        val edge = (now + PredictRule.SLACK_MS) / 1000
        assertEquals(Why.LEARNING_DUE, check(card(2, learning = read(9L to edge))))
        assertEquals(Why.SAFE, check(card(2, learning = read(9L to edge + 1))))
    }

    // ---- what is NOT a cause in AnkiDroid's v3 queue (so these answers stay instant)

    @Test fun theAnsweredCardComingDueSoonNeverMakesItWait() {
        // Again on a learning card: it is due again in a minute (or even now), but goes behind the main queue / after the next learning card
        assertEquals(Why.SAFE, check(card(2, learning = read(1L to nowSecs - 5)), answered = card(1, queue = 1, due = nowSecs - 60)))
        assertEquals(Why.SAFE, check(card(2, learning = read(1L to nowSecs + 60)), answered = card(1, queue = 0)))
    }

    @Test fun aLearningCardReadSecondKeepsItsPlaceBeforeCardsDueAfterIt() {
        val next = { l: PredictRule.LearnRead -> card(2, queue = 1, due = nowSecs - 100, learning = l) }
        assertEquals(Why.SAFE, check(next(read(9L to nowSecs - 10))))
        assertEquals("one due before it would sort first", Why.LEARNING_DUE, check(next(read(9L to nowSecs - 200))))
        assertEquals("the predicted card itself is not counted", Why.SAFE, check(next(read(2L to nowSecs - 100))))
        assertEquals("a filtered deck's preview repeat (queue 4) is ordered the same way", Why.SAFE, check(card(2, queue = 4, due = nowSecs - 100, learning = read(9L to nowSecs - 10))))
    }

    @Test fun aSiblingReadSecondIsStillPredicted() {
        // the queue is gathered with the same bury rule the answer applies: a sibling read second is not buried by the answer
        assertEquals(Why.SAFE, check(card(2, nid = 101, learning = read()), answered = card(1, nid = 101)))
    }

    // ---- reads that cannot be trusted

    @Test fun learningCardsThatCouldNotBeReadMakeTheAnswerWait() {
        assertEquals(Why.UNREAD, check(card(2, learning = null)))
        assertEquals(Why.UNREAD, check(card(2, learning = PredictRule.LearnRead(now - 1000, null))))
    }

    @Test fun aReadOlderThanTheLimitMakesTheAnswerWait() {
        assertEquals(Why.SAFE, check(card(2, learning = read(at = now - PredictRule.MAX_AGE_MS))))
        assertEquals(Why.OLD, check(card(2, learning = read(at = now - PredictRule.MAX_AGE_MS - 1))))
        assertEquals("a clock that went back", Why.OLD, check(card(2, learning = read(at = now + 60_000))))
    }

    // ---- the learning read

    @Test fun theLearningReadKeepsOnlyThisDecksIntradayCards() {
        val deck = 7L
        fun row(id: String?, queue: String?, due: String?, did: String? = "7", odid: String? = "0") =
            mapOf("_id" to id, "queue" to queue, "due" to due, "deck_id" to did, "original_deck_id" to odid)
        val rows = listOf(
            row("1", "1", "1700000100"),
            row("2", "3", "19650"), // interday learning: a day number, part of the main queue
            row("3", "2", "19650"),
            row("4", "4", "1700000200"), // preview repeat
            row("5", "1", "1700000300", did = "99", odid = "7"), // this deck's card sitting in a filtered deck
            row("6", "1", "1700000400", did = "7", odid = "3"), // studying the filtered deck itself
            row(null, "1", "1700000500"), row("8", "1", null), row("9", "1", "1700000600", odid = null),
        )
        val r = PredictRule.learnRead(rows, deck, 42L)
        assertEquals(42L, r.at)
        assertEquals(listOf(1L to 1700000100L, 4L to 1700000200L, 6L to 1700000400L, 9L to 1700000600L), r.due!!.map { it.cardId to it.dueSecs })
        assertNull("a failed read", PredictRule.learnRead(null, deck, 42L).due)
        assertEquals(emptyList<PredictRule.Due>(), PredictRule.learnRead(emptyList(), deck, 1L).due)
    }

    @Test fun theLearningQueryIsTheStudiedDeckWithItsSubdecks() {
        assertEquals("deck:\"Lang::Words\" is:learn", PredictRule.learnQuery("Lang::Words"))
        assertEquals("deck:\"Odd \\\"name\\\"\" is:learn", PredictRule.learnQuery("Odd \"name\""))
        assertEquals("deck:\"a\\_b\\*c\\\\d\" is:learn", PredictRule.learnQuery("a_b*c\\d"))
    }

    // ---- the swap's cause (STUDY line)

    @Test fun aSwapIsNamedByWhatCameFirst() {
        val a = StudyLoop.Key(101, 0); val b = StudyLoop.Key(102, 0)
        assertEquals("the answered card came back", PredictRule.swapCause(a, b, card(1), listOf(a, b)))
        assertEquals("a learning card came due", PredictRule.swapCause(a, b, card(9, queue = 1), listOf(StudyLoop.Key(109, 0), b)))
        assertEquals("the predicted card left the queue", PredictRule.swapCause(a, b, card(3), listOf(StudyLoop.Key(103, 0))))
        assertEquals("AnkiDroid changed the order", PredictRule.swapCause(a, b, card(3), listOf(StudyLoop.Key(103, 0), b)))
    }
}
