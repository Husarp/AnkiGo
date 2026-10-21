package com.husarp.ankigo.study

import com.husarp.ankigo.study.AnswerOutcome.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * PLAN 195: an answer whose card is no longer first in AnkiDroid's queue. With the try on, the write is still made for that exact
 * card (unchanged since shown, read back); refused, it waits for its card (journal) and is written once when that card is first
 * again the same day, without the card being shown; a day over, a changed card, a second refusal or a wrong-card write end it.
 * Never twice, never the wrong card, never lost. The pretend AnkiDroid ([FakeAnki]) refuses a non-top write unless [FakeAnki.answerAnyKey].
 */
class NotTopAnswerTest {
    private val deck = 7L
    private val DAY = 24L * 60 * 60 * 1000
    private val NOON = 1_792_152_000_000L // 2026-10-15 12:00 UTC

    private class Rig(val fake: FakeAnki, val store: MemStore = MemStore(), var on: Boolean = true, var wall: Long) {
        val outcomes = ArrayList<AnswerOutcome>()
        val notes = ArrayList<String>()
        val log = ArrayList<String>()
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) { log += "expect" }
            override fun cancel(before: Map<String, String?>) { log += "cancel" }
            override fun outcome(o: AnswerOutcome) { outcomes += o; log += "outcome:${o.javaClass.simpleName}" }
            override fun note(text: String) { notes += text }
        }
        val waiter = object : Waiter {
            override fun await(ms: Long): Boolean { fake.clock.ms += maxOf(ms, 0); return false }
            override fun wake() {}
            override fun clear() {}
        }
        fun queue() = AnswerQueue(
            fake, store, hooks, waiter, { fake.clock.ms }, parkAfter = 3, tryNotTop = { on },
            dayOf = { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }, wallNow = { wall },
        )
        var q = queue()

        fun entry(c: FakeAnki.Card, ease: Int = 3): QueuedAnswer =
            QueuedAnswer(q.nextSeq(), 7L, c.nid, c.ord, c.id, fake.stats(c.key)!!, ease, 500, wall)

        fun journal() = AnswerJournal.decode(store.text).answers
        fun own() = fake.revlog.filter { !it.external }
    }

    private fun rig(on: Boolean = true): Rig {
        val f = FakeAnki()
        for (i in 1..4) f.add(deck, 100L + i)
        f.selected = 99
        return Rig(f, on = on, wall = NOON)
    }

    /** Answers the first card, but a learning card came due first meanwhile: the answer's card is second now. */
    private fun answeredThenPassed(r: Rig): FakeAnki.Card {
        val c = r.fake.queueOf(deck).first()
        r.q.enqueue(r.entry(c))
        r.fake.moveToTop(r.fake.queueOf(deck).last())
        return c
    }

    private fun waitedFor(r: Rig): FakeAnki.Card {
        val c = answeredThenPassed(r)
        val w = r.q.drain().single() as AnswerOutcome.Waiting
        assertEquals(c.id, w.answer.cardId)
        return c
    }

    // ---- 1: the write for that exact card

    @Test fun aNonTopWriteThatAnkiDroidTakesIsSaved() {
        val r = rig(); r.fake.answerAnyKey = true
        val c = answeredThenPassed(r)
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(c.id, s.answer.cardId)
        assertEquals(listOf(c.id), r.own().map { it.cardId })
        assertEquals(4, c.reps)
        assertEquals(listOf("expect", "outcome:Saved"), r.log)
        assertTrue(r.notes.toString(), r.notes.any { it.startsWith("NOT_TOP try") } && r.notes.any { it.contains("saved") })
        assertTrue(r.journal().isEmpty())
    }

    @Test fun withTheSwitchOffItIsTodaysNotice() {
        val r = rig(on = false); r.fake.answerAnyKey = true
        answeredThenPassed(r)
        assertEquals(Reason.NOT_TOP, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer"))
        assertTrue(r.own().isEmpty() && r.journal().isEmpty())
    }

    @Test fun aCardChangedBeforeTheTryIsChangedAndNotWritten() {
        val r = rig(); r.fake.answerAnyKey = true
        val c = answeredThenPassed(r)
        r.fake.reschedule(c)
        assertEquals(Reason.CHANGED, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer"))
    }

    // ---- 2: refused, it waits for its card

    @Test fun refusedItWaitsInTheJournalAndIsWrittenOnceWhenItsCardIsFirstWithoutBeingShown() {
        val r = rig()
        val c = waitedFor(r)
        assertTrue(r.own().isEmpty())
        assertEquals(0, r.q.size)   // nothing holds up the next answers or loads
        assertEquals(listOf(c.id), r.q.waitingAnswers().map { it.cardId })
        assertEquals("the journal keeps it, flagged", listOf(true), r.journal().map { it.waiting })
        assertFalse("not cancelled: its expectation stays pinned", r.log.contains("cancel"))
        // other cards are studied meanwhile; then its card is first again
        val other = r.fake.queueOf(deck).first()
        r.q.enqueue(r.entry(other)); assertTrue(r.q.drain().single() is AnswerOutcome.Saved)
        r.fake.moveToTop(r.fake.queueOf(deck)[1])
        assertFalse("another card on top: nothing happens", r.q.writeWaiting(deck, r.fake.queueOf(deck).first().key))
        assertEquals(1, r.q.waitingAnswers().size)
        r.fake.moveToTop(c)
        assertTrue(r.q.writeWaiting(deck, c.key))
        val saved = r.outcomes.last() as AnswerOutcome.Saved
        assertEquals(c.id, saved.answer.cardId)
        assertEquals("written once", 1, r.own().count { it.cardId == c.id })
        assertTrue("the load reads again: the card is no longer first, so it is never shown", r.fake.queueOf(deck).firstOrNull() != c)
        assertTrue(r.q.waitingAnswers().isEmpty() && r.journal().isEmpty())
        assertTrue(r.notes.toString(), r.notes.any { it.startsWith("saved late") })
        assertFalse("nothing left to write", r.q.writeWaiting(deck, c.key))
        assertEquals(1, r.own().count { it.cardId == c.id })
    }

    @Test fun aDayOverEndsItWithTodaysNotice() {
        val r = rig()
        val c = waitedFor(r)
        r.wall += DAY
        r.fake.moveToTop(c)
        assertFalse("a new day: it is not written late", r.q.writeWaiting(deck, c.key))
        val o = r.outcomes.last() as AnswerOutcome.NotWritten
        assertEquals(Reason.NOT_TOP, o.reason)
        assertTrue(r.own().isEmpty())
        assertTrue(r.log.contains("cancel"))
        assertTrue(r.journal().isEmpty() && r.q.waitingAnswers().isEmpty())
        assertTrue(r.notes.toString(), r.notes.any { it.startsWith("gave up") })
    }

    @Test fun theNextDrainEndsAnOldWaitingAnswerToo() {
        val r = rig()
        waitedFor(r)
        r.wall += DAY
        val out = r.q.drain()
        assertEquals(Reason.NOT_TOP, (out.single() as AnswerOutcome.NotWritten).reason)
    }

    @Test fun aCardChangedWhileItWaitedIsChangedNotWritten() {
        val r = rig()
        val c = waitedFor(r)
        r.fake.externalAnswer(c, 4)   // answered in AnkiDroid itself: it LOOKS saved, but no write of ours applied
        r.fake.moveToTop(c)
        assertTrue(r.q.writeWaiting(deck, c.key))
        assertEquals(Reason.CHANGED, (r.outcomes.last() as AnswerOutcome.NotWritten).reason)
        assertTrue(r.own().isEmpty())
        assertEquals("only the refused try went to a card that was not first", 1, r.fake.notTopWrites)
    }

    @Test fun aLateWriteThatFindsAnotherCardFirstEndsNotTopAndNeverWaitsTwice() {
        val r = rig()
        val c = waitedFor(r)
        // the load saw its card first, but by the write another one is (a race): refused again, it ends
        assertTrue(r.q.writeWaiting(deck, c.key))
        assertEquals(Reason.NOT_TOP, (r.outcomes.last() as AnswerOutcome.NotWritten).reason)
        assertTrue(r.q.waitingAnswers().isEmpty() && r.journal().isEmpty())
        assertTrue(r.own().isEmpty())
        assertEquals(1, r.outcomes.count { it is AnswerOutcome.Waiting })
    }

    @Test fun aLateWriteOnTheFirstCardFollowsTheNormalRuleAndIsWrittenOnce() {
        val r = rig()
        val c = waitedFor(r)
        r.fake.moveToTop(c)
        r.fake.plan("answer", F.SWALLOW)   // AnkiDroid drops the first call: attempt 2 at once, starting with R
        assertTrue(r.q.writeWaiting(deck, c.key))
        assertTrue(r.outcomes.last() is AnswerOutcome.Saved)
        assertEquals(1, r.own().count { it.cardId == c.id })
    }

    // ---- restart

    @Test fun aWaitingAnswerSurvivesARestartAndIsWrittenOnce() {
        val r = rig()
        val c = waitedFor(r)
        r.q = r.queue()   // the app was killed and started again
        val back = r.q.restore()
        assertEquals(0, back.count); assertEquals(1, back.waiting)
        assertTrue(r.q.pending().isEmpty())
        assertTrue(r.q.drain().isEmpty())
        val w = r.q.waitingAnswers().single()
        assertTrue(w.restored && w.waiting)
        r.fake.moveToTop(c)
        assertTrue(r.q.writeWaiting(deck, c.key))
        assertTrue(r.outcomes.last() is AnswerOutcome.Saved)
        assertEquals(1, r.own().count { it.cardId == c.id })
        assertTrue(r.journal().isEmpty())
    }

    @Test fun aRestoredWaitingAnswerWhoseCardWasAnsweredElsewhereIsChangedNotAlreadySaved() {
        val r = rig()
        val c = waitedFor(r)
        r.q = r.queue(); r.q.restore()
        r.fake.externalAnswer(c, 3); r.fake.moveToTop(c)
        assertTrue(r.q.writeWaiting(deck, c.key))
        assertEquals(Reason.CHANGED, (r.outcomes.last() as AnswerOutcome.NotWritten).reason)
        assertTrue(r.own().isEmpty())
    }

    @Test fun aKillDuringTheLateWriteIsFoundAlreadySavedNotWrittenTwice() {
        val r = rig()
        val c = waitedFor(r)
        r.fake.moveToTop(c)
        r.fake.plan("answer", F.DIE_AFTER)   // the write applied, then the process died
        try { r.q.writeWaiting(deck, c.key); fail("the process died") } catch (e: Die) {}
        assertEquals("back in the queue before the write", listOf(false), r.journal().map { it.waiting })
        r.q = r.queue()
        assertEquals(1, r.q.restore().count)
        assertTrue(r.q.drain().single() is AnswerOutcome.AlreadySaved)
        assertEquals("never twice", 1, r.own().count { it.cardId == c.id })
    }

    @Test fun aRestoredWaitingAnswerFromYesterdayEndsAtTheFirstDrain() {
        val r = rig()
        waitedFor(r)
        r.wall += DAY
        r.q = r.queue(); r.q.restore()
        assertEquals(Reason.NOT_TOP, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertTrue(r.journal().isEmpty())
    }

    // ---- never the wrong card

    @Test fun anAnkiDroidThatAnswersTheFirstCardInsteadTurnsTheTryOff() {
        val r = rig(); r.fake.answerAnyTop = true   // the worst case: ANSWER hits whatever is first
        val c = answeredThenPassed(r)
        val first = r.fake.queueOf(deck).first()
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.UNSURE, o.reason)
        assertTrue(r.notes.toString(), r.notes.any { it.contains("FIRST card") })
        assertEquals(listOf(first.id), r.own().map { it.cardId })
        // the try is off now: the next card that is not first is today's notice, with no write
        val d = r.fake.queueOf(deck).first()
        r.q.enqueue(r.entry(d)); r.fake.moveToTop(r.fake.queueOf(deck).last())
        val writes = r.fake.count("answer")
        assertEquals(Reason.NOT_TOP, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(writes, r.fake.count("answer"))
        assertNull(r.q.waitingAnswers().firstOrNull { it.cardId == c.id })
    }

    @Test fun aWriteThatCannotBeReadBackIsRetriedFromStepRNotWrittenTwice() {
        val r = rig(); r.fake.answerAnyKey = true
        val c = answeredThenPassed(r)
        r.fake.plan("stats", F.NONE, F.NONE, F.NULL)   // step 3, the first card, then the read back fails
        val out = r.q.drain()
        assertTrue(out.single() is AnswerOutcome.Saved)   // the retry's R finds our write
        assertEquals(1, r.own().count { it.cardId == c.id })
        assertTrue(r.outcomes.any { it is AnswerOutcome.Retrying })
    }
}
