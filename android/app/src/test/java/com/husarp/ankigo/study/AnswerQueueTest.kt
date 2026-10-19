package com.husarp.ankigo.study

import com.husarp.ankigo.study.AnswerOutcome.Reason
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The write queue on its own, against the pretend AnkiDroid: every step of the state machine, with call counts. */
class AnswerQueueTest {
    private val deck = 7L

    private class Rig(val fake: FakeAnki, parkAfter: Int = 20, alwaysSelect: Boolean = false) {
        val store = MemStore()
        val log = ArrayList<String>()
        val outcomes = ArrayList<AnswerOutcome>()
        val waits = ArrayList<Long>()
        var onWait: (Long) -> Boolean = { false }
        var woken = false
        var throwInHooks = false
        var journalFailed = 0
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) { log += "expect"; if (throwInHooks) error("hook") }
            override fun cancel(before: Map<String, String?>) { log += "cancel"; if (throwInHooks) error("hook") }
            override fun answerTimed(answer: QueuedAnswer, attempt: Int, ms: Long) { log += "timed:$attempt:$ms" }
            override fun outcome(o: AnswerOutcome) { outcomes += o; log += "outcome:${o.javaClass.simpleName}"; if (throwInHooks) error("hook") }
            override fun journalFailed(reason: String) { journalFailed++ }
        }
        val waiter = object : Waiter {
            override fun await(ms: Long): Boolean {
                waits += ms
                if (woken) { woken = false; return true }
                fake.clock.ms += maxOf(ms, 0)
                return onWait(ms)
            }
            override fun wake() { woken = true }
            override fun clear() { woken = false }
        }
        val deckId = 7L
        var q = AnswerQueue(fake, store, hooks, waiter, { fake.clock.ms }, parkAfter = parkAfter, alwaysSelect = alwaysSelect)

        fun entry(c: FakeAnki.Card, ease: Int = 3, seq: Long = q.nextSeq(), restored: Boolean = false): QueuedAnswer =
            QueuedAnswer(seq, deckId, c.nid, c.ord, c.id, fake.stats(c.key)!!, ease, 500, 1_700_000_000_000L + seq, restored)

        fun terminal() = outcomes.filter { it.isTerminal }
        fun retrying() = outcomes.filterIsInstance<AnswerOutcome.Retrying>()
    }

    private fun newFake(n: Int = 3): FakeAnki = FakeAnki().also { f -> for (i in 1..n) f.add(deck, 100L + i); f.selected = 99 }

    private fun rigWith(n: Int = 3) = Rig(newFake(n))

    private fun first(f: FakeAnki) = f.queueOf(deck).first()

    // ---- the happy path and its bookkeeping

    @Test fun savedOnTheFirstAttemptWithTheExpectedCalls() {
        val r = rigWith(); val c = first(r.fake)
        r.q.enqueue(r.entry(c)); r.fake.calls.clear()
        val out = r.q.drain()
        val s = out.single() as AnswerOutcome.Saved
        assertEquals(1, s.attempts); assertEquals(4, s.after["reps"]!!.toInt())
        assertEquals(1, r.fake.count("select")); assertEquals(1, r.fake.count("top")); assertEquals(2, r.fake.count("stats")); assertEquals(1, r.fake.count("answer"))
        assertEquals(listOf("expect", "timed:1:20", "outcome:Saved"), r.log)
        assertEquals(1, r.fake.revlog.size); assertEquals(0, r.q.size)
    }

    @Test fun journalHoldsTheEntryBeforeAnyProviderCallAndNothingAfter() {
        val r = rigWith(); val c = first(r.fake)
        val a = r.entry(c)
        var seenAtFirstCall: String? = null
        val spy = object : AnswerProvider by r.fake {
            override fun select(deckId: Long) { if (seenAtFirstCall == null) seenAtFirstCall = r.store.text; r.fake.select(deckId) }
        }
        val q = AnswerQueue(spy, r.store, r.hooks, r.waiter, { r.fake.clock.ms })
        q.enqueue(a)
        assertTrue(AnswerJournal.decode(r.store.text).answers.any { it.seq == a.seq })
        q.drain()
        assertTrue("journal had the entry when the first provider call ran", seenAtFirstCall!!.contains("\"seq\":${a.seq}"))
        assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
    }

    @Test fun theWriteIsTimedOnItsOwn() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.SLOW)
        r.q.enqueue(r.entry(c)); r.q.drain()
        assertTrue(r.log.contains("timed:1:900"))
        assertEquals(900L, (r.terminal().single() as AnswerOutcome.Saved).answerMs)
    }

    @Test fun theKeyAndTheTimeAreWhatWasAnswered() {
        val r = rigWith(); val c = first(r.fake)
        var got: Triple<StudyLoop.Key, Int, Long>? = null
        val spy = object : AnswerProvider by r.fake {
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { got = Triple(key, ease, ms); r.fake.answer(key, ease, ms) }
        }
        val q = AnswerQueue(spy, r.store, r.hooks, r.waiter, { r.fake.clock.ms })
        q.enqueue(r.entry(c, ease = 2).copy(tookMs = 99_999_999)); q.drain()
        assertEquals(Triple(c.key, 2, 3_600_000L), got)
    }

    // ---- not written: the card is not the one to write

    @Test fun notOnTopIsNeverWrittenAndTheUserIsToldWhy() {
        val r = rigWith(); val c = first(r.fake)
        r.q.enqueue(r.entry(c))
        r.fake.moveToTop(r.fake.queueOf(deck).last())
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.NOT_TOP, o.reason)
        assertEquals(0, r.fake.count("answer")); assertEquals(0, r.fake.revlog.size)
        assertEquals(listOf("cancel", "outcome:NotWritten"), r.log)
        assertEquals("the journal no longer holds a told answer", 0, AnswerJournal.decode(r.store.text).answers.size)
    }

    @Test fun anEmptyQueueIsNotTopToo() {
        val r = rigWith(1); val c = first(r.fake)
        r.q.enqueue(r.entry(c)); r.fake.queues[deck]!!.clear()
        assertEquals(Reason.NOT_TOP, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer"))
    }

    @Test fun answeredElsewhereBeforeTheWriteIsChangedNotWritten() {
        val r = rigWith(); val c = first(r.fake)
        r.q.enqueue(r.entry(c))
        r.fake.externalAnswer(c, 4)
        // the externally answered card left the queue, so the next card is on top: the card state check is what stops it
        r.fake.moveToTop(c)
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.CHANGED, o.reason)
        assertEquals(0, r.fake.count("answer")); assertEquals(1, r.fake.revlog.size) // only the external one
        assertTrue(r.log.contains("cancel"))
    }

    @Test fun editedElsewhereIsChanged() {
        val r = rigWith(); val c = first(r.fake)
        r.q.enqueue(r.entry(c)); r.fake.reschedule(c)
        assertEquals(Reason.CHANGED, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer"))
    }

    // ---- failures and the reconcile that follows them

    @Test fun throwsBeforeApplyThenWorksWritesOnce() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.THROW_BEFORE)
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(2, s.attempts)
        assertEquals(listOf(1_000L), r.waits)
        assertEquals(1, r.retrying().size); assertEquals(1, r.retrying()[0].attempt); assertEquals(1_000L, r.retrying()[0].waitMs)
        assertEquals(1, r.fake.revlog.size); assertEquals(2, r.fake.count("answer"))
        assertEquals("a retry selects the deck again", 2, r.fake.count("select"))
    }

    @Test fun throwsAfterApplyIsReconciledNotWrittenTwice() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.THROW_AFTER)
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals("a write of ours exists, so the game step runs", 2, s.attempts)
        assertEquals(1, r.fake.count("answer")); assertEquals(1, r.fake.revlog.size)
        assertEquals("one before the write, one that refreshes it before the reconcile read", 2, r.log.count { it == "expect" })
    }

    @Test fun anAttemptAfterAnUncertainWriteRenewsTheExpectationBeforeItReadsTheCard() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.THROW_AFTER)
        val order = ArrayList<String>()
        val spy = object : AnswerProvider by r.fake {
            override fun stats(key: StudyLoop.Key): Map<String, String?>? { order += "stats"; return r.fake.stats(key) }
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { order += "answer"; r.fake.answer(key, ease, ms) }
        }
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) { order += "expect" }
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) { order += o.javaClass.simpleName }
        }
        val q = AnswerQueue(spy, r.store, hooks, r.waiter, { r.fake.clock.ms })
        q.enqueue(r.entry(c))
        q.drain()
        // 1st attempt: stats(pre-check), expect, answer (applied, then threw), told Retrying; 2nd attempt: expect FIRST, then the reconcile read
        assertEquals(listOf("stats", "expect", "answer", "Retrying", "expect", "stats", "Saved"), order)
    }

    @Test fun aFirstAttemptAndARestoredEntryDoNotRenewAnythingTheyNeverStarted() {
        val r = rigWith(); val c = first(r.fake)
        val e = r.entry(c, restored = true)
        r.q.enqueue(e)
        r.q.drain()
        assertEquals("the restored entry reconciles without an expectation of this process, then writes once", 1, r.log.count { it == "expect" })
    }

    @Test fun swallowedOnceIsRetriedAtOnceWithoutWaiting() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.SWALLOW)
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(2, s.attempts); assertEquals(emptyList<Long>(), r.waits); assertEquals(0, r.retrying().size)
        assertEquals(2, r.fake.count("answer")); assertEquals(1, r.fake.revlog.size)
    }

    @Test fun swallowedAlwaysBacksOffThenParksThenRetryNowWorks() {
        val r = rigWith(); val c = first(r.fake)
        for (i in 1..30) r.fake.plan("answer", F.SWALLOW)
        r.onWait = { ms -> if (ms < 0) { r.fake.plans.clear(); true } else false } // parked: the user presses Retry
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(21, s.attempts)
        // attempt 1 retries at once; attempts 2..19 back off 2 s, 5 s, 10 s, 30 s, 30 s...; attempt 20 parks
        val expected = listOf(2_000L, 5_000L, 10_000L) + List(15) { 30_000L } + listOf(-1L)
        assertEquals(expected, r.waits)
        assertEquals(-1L, r.retrying().last().waitMs); assertEquals(20, r.retrying().last().attempt)
        assertEquals(1, r.fake.revlog.size)
        assertEquals(19, r.retrying().size) // attempts 2..20 each reported once
    }

    @Test fun backOffScheduleIsOneTwoFiveTenThirtyThirty() {
        assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 30_000L, 30_000L), (1..7).map { Backoff.ms(it) })
    }

    @Test fun statsUnreadableAfterTheWriteIsReconciledNotBlindlyRewritten() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.duringWrite = { r.fake.plan("stats", F.NULL); r.fake.duringWrite = null } // the read right after the write fails
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(2, s.attempts); assertEquals(1, r.fake.count("answer")); assertEquals(1, r.fake.revlog.size)
    }

    @Test fun statsThrowingAfterTheWriteIsReconciledToo() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.duringWrite = { r.fake.plan("stats", F.THROW); r.fake.duringWrite = null }
        r.q.enqueue(r.entry(c))
        assertEquals(2, (r.q.drain().single() as AnswerOutcome.Saved).attempts)
        assertEquals(1, r.fake.count("answer"))
    }

    @Test fun statsUnreadableBeforeTheWriteRetriesAndWritesOnce() {
        val r = rigWith(); val c = first(r.fake)
        val e = r.entry(c)
        r.fake.plan("stats", F.NULL)
        r.q.enqueue(e)
        assertEquals(2, (r.q.drain().single() as AnswerOutcome.Saved).attempts)
        assertEquals(1, r.fake.count("answer"))
    }

    @Test fun topThrowingAndSelectThrowingRetry() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("select", F.THROW); r.fake.plan("top", F.THROW)
        r.q.enqueue(r.entry(c))
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(3, s.attempts); assertEquals(listOf(1_000L, 2_000L), r.waits)
        assertEquals(1, r.fake.revlog.size)
    }

    @Test fun repsMovedByMoreThanOneIsUnsureAndNeverWrittenAgain() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.afterApply = { card, _ -> card.reps++ } // someone else answered at the same moment
        r.q.enqueue(r.entry(c))
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.UNSURE, o.reason)
        assertEquals(1, r.fake.count("answer")); assertEquals(listOf("expect", "timed:1:20", "cancel", "outcome:NotWritten"), r.log)
    }

    @Test fun repsPlusOneWithNothingElseChangedIsUnsureToo() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.afterApply = { card, ease -> card.due -= 7L * ease; card.queue = 2; card.last = card.last!! - 60 - ease } // only reps moved
        r.q.enqueue(r.entry(c))
        assertEquals(Reason.UNSURE, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
    }

    @Test fun anExceptionFromTheCardReadAtReconcileKeepsRetrying() {
        val r = rigWith(); val c = first(r.fake)
        val e = r.entry(c)
        r.fake.plan("answer", F.THROW_AFTER)
        r.fake.plan("stats", F.NONE, F.THROW) // attempt 1 step 3 reads fine; the reconcile read of attempt 2 throws
        r.q.enqueue(e)
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(3, s.attempts); assertEquals(1, r.fake.revlog.size); assertEquals(1, r.fake.count("answer"))
    }

    @Test fun aCardAnsweredElsewhereThatLooksSavedIsNotOursWhenNoWriteOfOursCouldExist() {
        val r = rigWith(); val c = first(r.fake)
        val e = r.entry(c)
        r.fake.plan("top", F.THROW) // attempt 1 fails before it can write anything
        r.onWait = { r.fake.externalAnswer(c, 3); false } // and the user answers the card in AnkiDroid itself during the back-off
        r.q.enqueue(e)
        val o = r.q.drain().single()
        assertEquals(Reason.CHANGED, (o as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer")); assertEquals(0, r.log.count { it == "expect" })
    }

    @Test fun aCardThatLooksSavedAfterOurOwnFailedAttemptIsTreatedAsOurs() {
        val r = rigWith(); val c = first(r.fake)
        val e = r.entry(c)
        r.fake.plan("answer", F.THROW_BEFORE) // reached the write call: a write of ours may exist
        r.onWait = { r.fake.externalAnswer(c, 3); false }
        r.q.enqueue(e)
        val o = r.q.drain().single()
        assertTrue(o.toString(), o is AnswerOutcome.Saved) // cannot be told apart; the card is reviewed once either way
    }

    // ---- restore

    @Test fun restoredEntryNotYetWrittenIsWrittenOnceWithTheGameStep() {
        val r = rigWith(); val c = first(r.fake)
        val a = r.entry(c); r.store.text = AnswerJournal.encode(listOf(a)); r.fake.calls.clear()
        val rs = r.q.restore()
        assertEquals(1, rs.count); assertNull(rs.problem); assertTrue(r.q.pending().single().restored)
        val s = r.q.drain().single() as AnswerOutcome.Saved
        assertEquals(1, s.attempts); assertEquals(1, r.fake.revlog.size)
        assertEquals("the first look at a restored entry is a read, then the usual calls", 3, r.fake.count("stats"))
    }

    @Test fun restoredEntryAlreadyWrittenByTheDeadProcessIsAlreadySavedWithNoWriteAndNoGameStep() {
        val r = rigWith(); val c = first(r.fake)
        val a = r.entry(c); r.store.text = AnswerJournal.encode(listOf(a))
        r.fake.externalAnswer(c, 3) // stands for the dead process's write: reps + 1, due / queue / last changed
        r.q.restore()
        val o = r.q.drain().single()
        assertTrue(o.toString(), o is AnswerOutcome.AlreadySaved)
        assertEquals(0, r.fake.count("answer")); assertEquals(0, r.log.count { it == "expect" })
        assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
    }

    @Test fun restoredEntryWhoseCardChangedIsToldNotWritten() {
        val r = rigWith(); val c = first(r.fake)
        val a = r.entry(c); r.store.text = AnswerJournal.encode(listOf(a))
        r.fake.reschedule(c)
        r.q.restore()
        assertEquals(Reason.CHANGED, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
        assertEquals(0, r.fake.count("answer"))
    }

    @Test fun restoredEntryWhoseCardWasAnsweredTwiceElsewhereIsChanged() {
        val r = rigWith(); val c = first(r.fake)
        val a = r.entry(c); r.store.text = AnswerJournal.encode(listOf(a))
        r.fake.externalAnswer(c, 3); r.fake.externalAnswer(c, 3)
        r.q.restore()
        assertEquals(Reason.CHANGED, (r.q.drain().single() as AnswerOutcome.NotWritten).reason)
    }

    @Test fun severalRestoredEntriesAreWrittenOldestFirstBeforeNewOnes() {
        val r = rigWith(4); val cs = r.fake.queueOf(deck).toList()
        val old = listOf(r.entry(cs[0], seq = 5), r.entry(cs[1], seq = 9)).reversed() // written to the file in the wrong order
        r.store.text = AnswerJournal.encode(old)
        assertEquals(2, r.q.restore().count)
        val fresh = r.entry(cs[2])
        assertTrue("new sequence numbers continue after the restored ones", fresh.seq > 9)
        r.q.enqueue(fresh)
        assertEquals(listOf(5L, 9L, fresh.seq), r.q.pending().map { it.seq })
        r.q.drain()
        assertEquals(listOf(cs[0].id, cs[1].id, cs[2].id), r.fake.revlog.map { it.cardId })
    }

    @Test fun restoreRunsOnlyOncePerProcess() {
        val r = rigWith(); val c = first(r.fake)
        r.store.text = AnswerJournal.encode(listOf(r.entry(c)))
        assertEquals(1, r.q.restore().count)
        assertEquals(0, r.q.restore().count)
        assertEquals(1, r.q.size)
    }

    @Test fun aBrokenJournalIsAProblemNotACrash() {
        for (bad in listOf("", "   ", "not json", "{\"v\":99,\"answers\":[]}", "{\"v\":1}", "[1,2]")) {
            val r = rigWith(); r.store.text = bad
            val rs = r.q.restore()
            assertEquals(bad, 0, rs.count); assertNotNull(bad, rs.problem)
            val c = first(r.fake); r.q.enqueue(r.entry(c)) // and a new answer still works, overwriting the bad file
            assertTrue(r.q.drain().single() is AnswerOutcome.Saved)
        }
    }

    // ---- who selects, who writes

    @Test fun theDeckIsSelectedOnceWhileItIsKnownSelectedAndAgainWhenInvalidatedOrOld() {
        val r = rigWith(5); val cs = r.fake.queueOf(deck).toList()
        r.q.enqueue(r.entry(cs[0])); r.q.drain()
        r.q.enqueue(r.entry(cs[1])); r.q.drain()
        assertEquals("second answer trusts the selection", 1, r.fake.count("select"))
        r.q.invalidateSelection()
        r.q.enqueue(r.entry(cs[2])); r.q.drain()
        assertEquals(2, r.fake.count("select"))
        r.fake.clock.ms += AnswerQueue.SELECT_TRUST_MS + 1
        r.q.enqueue(r.entry(cs[3])); r.q.drain()
        assertEquals("an old selection is not trusted", 3, r.fake.count("select"))
        assertEquals(4, r.fake.revlog.size)
    }

    @Test fun aLoadsSelectCountsAndEnsureSelectedSkipsTheRest() {
        val r = rigWith(); var n = 0
        r.q.ensureSelected(deck) { n++ }; r.q.ensureSelected(deck) { n++ }
        assertEquals(1, n)
        r.q.invalidateSelection(); r.q.ensureSelected(deck) { n++ }
        assertEquals(2, n)
        r.q.ensureSelected(8) { n++ }
        assertEquals(3, n)
    }

    @Test fun alwaysSelectSelectsBeforeEveryWrite() {
        val r = Rig(newFake(5), alwaysSelect = true); val cs = r.fake.queueOf(deck).toList()
        r.q.enqueue(r.entry(cs[0])); r.q.drain(); r.q.enqueue(r.entry(cs[1])); r.q.drain()
        assertEquals(2, r.fake.count("select"))
    }

    @Test fun aStaleSelectionIsLookedAtAgainBeforeCallingItNotOnTop() {
        // AnkiDroid #18270: while another deck is selected the schedule query shows THAT deck's queue
        val r = rigWith(5); val cs = r.fake.queueOf(deck).toList()
        r.fake.topNeedsSelection = true; r.fake.answerAnyTop = true
        r.fake.add(8, 900); r.q.enqueue(r.entry(cs[0])); r.q.drain()
        r.fake.select(8) // someone selected another deck and nobody told the queue
        r.q.enqueue(r.entry(cs[1]))
        val s = r.q.drain().single()
        assertTrue(s.toString(), s is AnswerOutcome.Saved)
        assertEquals("exactly the two own writes, none on the other deck's card", listOf(cs[0].id, cs[1].id), r.fake.revlog.map { it.cardId })
        assertEquals(0, r.fake.notTopWrites)
    }

    @Test fun afterAFailureTheNextAttemptAlwaysSelectsEvenWhenTrusted() {
        val r = rigWith(); val c = first(r.fake)
        r.q.ensureSelected(deck) { r.fake.select(deck) }; r.fake.calls.clear()
        r.fake.plan("answer", F.THROW_BEFORE)
        r.q.enqueue(r.entry(c)); r.q.drain()
        assertEquals("attempt 1 trusts the selection, attempt 2 selects", 1, r.fake.count("select"))
    }

    // ---- discard, hooks, journal trouble, dying

    @Test fun discardEndsAParkedAnswerNotWritten() {
        val r = Rig(newFake(), parkAfter = 2); val c = first(r.fake)
        for (i in 1..9) r.fake.plan("answer", F.SWALLOW)
        val a = r.entry(c); r.q.enqueue(a)
        r.onWait = { ms -> assertTrue("parks after 2 attempts", ms < 0); assertTrue(r.q.discard(a.seq)); r.woken = false; true }
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.DISCARDED, o.reason)
        assertEquals(0, r.fake.revlog.size); assertTrue(r.log.contains("cancel"))
        assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
        assertFalse(r.q.discard(a.seq))
    }

    @Test fun discardOfAnAnswerThatWasInFactWrittenReportsItSaved() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.THROW_AFTER)
        val a = r.entry(c); r.q.enqueue(a)
        r.onWait = { r.q.discard(a.seq); r.woken = false; true }
        val o = r.q.drain().single()
        assertTrue(o.toString(), o is AnswerOutcome.Saved) // the last look found it written
        assertEquals(1, r.fake.revlog.size)
    }

    @Test fun discardOfAQueuedBehindAnswerNeverTouchesProvider() {
        val r = rigWith(); val cs = r.fake.queueOf(deck).toList()
        val a = r.entry(cs[0]); val b = r.entry(cs[1])
        r.q.enqueue(a); r.q.enqueue(b); assertTrue(r.q.discard(b.seq))
        val out = r.q.drain()
        assertTrue(out[0] is AnswerOutcome.Saved); assertEquals(Reason.DISCARDED, (out[1] as AnswerOutcome.NotWritten).reason)
        assertEquals(listOf(cs[0].id), r.fake.revlog.map { it.cardId })
    }

    @Test fun aHookThatThrowsDoesNotBreakTheQueue() {
        val r = rigWith(); val cs = r.fake.queueOf(deck).toList()
        r.throwInHooks = true
        val e0 = r.entry(cs[0]); r.fake.moveToTop(cs[1]); val e1 = r.entry(cs[1])
        r.q.enqueue(e0); r.q.enqueue(e1)
        val out = r.q.drain()
        assertEquals(Reason.NOT_TOP, (out[0] as AnswerOutcome.NotWritten).reason) // its cancel hook threw
        assertTrue(out[1] is AnswerOutcome.Saved) // its expect and outcome hooks threw
        assertEquals(listOf(cs[1].id), r.fake.revlog.map { it.cardId }); assertEquals(0, r.q.size)
    }

    @Test fun aJournalThatCannotBeWrittenStillQueuesTheAnswerAndSaysSo() {
        val r = rigWith(); val c = first(r.fake)
        r.store.failWrites = true
        assertFalse(r.q.enqueue(r.entry(c)))
        assertEquals(1, r.q.size); assertTrue(r.journalFailed >= 1)
        assertTrue(r.q.drain().single() is AnswerOutcome.Saved)
    }

    @Test fun everyAnswerEndsExactlyOnceAndInTheOrderItWasMade() {
        val r = rigWith(4); val cs = r.fake.queueOf(deck).toList()
        val es = cs.map { r.entry(it) }
        es.forEach { r.q.enqueue(it) }
        r.q.drain()
        assertEquals(es.map { it.seq }, r.terminal().map { it.answer.seq })
        assertEquals(cs.map { it.id }, r.fake.revlog.map { it.cardId })
    }

    @Test fun anErrorIsNotCaughtAndTheEntryStaysJournaled() {
        val r = rigWith(); val c = first(r.fake)
        r.fake.plan("answer", F.DIE_AFTER)
        val a = r.entry(c); r.q.enqueue(a)
        try { r.q.drain(); fail("the process should have died") } catch (e: Die) { /* the process ends here */ }
        assertEquals(listOf(a.seq), AnswerJournal.decode(r.store.text).answers.map { it.seq })
        // a new process
        val q2 = AnswerQueue(r.fake, r.store, r.hooks, r.waiter, { r.fake.clock.ms })
        assertEquals(1, q2.restore().count)
        val o = q2.drain().single()
        assertTrue(o.toString(), o is AnswerOutcome.AlreadySaved)
        assertEquals("written once by the dead process only", 1, r.fake.revlog.size); assertEquals(1, r.fake.count("answer"))
    }

    @Test fun aThrowingExpectHookStillLetsTheWriteHappen() {
        val r = rigWith(); val c = first(r.fake)
        r.q.enqueue(r.entry(c))
        r.throwInHooks = true
        r.q.drain()
        assertEquals(1, r.fake.revlog.size)
    }

    @Test fun unusableAnswersAreRefusedAtTheDoor() {
        val r = rigWith(); val c = first(r.fake)
        val ok = r.entry(c)
        assertThrows(IllegalArgumentException::class.java) { r.q.enqueue(ok.copy(ease = 5)) }
        assertThrows(IllegalArgumentException::class.java) { r.q.enqueue(ok.copy(before = ok.before - "reps")) }
        assertEquals(0, r.q.size)
    }

    // ---- real threads

    @Test fun aLaterTaskCannotRunBeforeTheSlowWriteIsResolved() {
        val fake = newFake(); val c = fake.queueOf(deck).first()
        val gate = CountDownLatch(1); val inWrite = CountDownLatch(1)
        val slow = object : AnswerProvider by fake {
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { inWrite.countDown(); assertTrue(gate.await(10, TimeUnit.SECONDS)); fake.answer(key, ease, ms) }
        }
        val events = java.util.Collections.synchronizedList(ArrayList<String>())
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) { events += "outcome:${o.javaClass.simpleName}" }
        }
        val q = AnswerQueue(slow, MemStore(), hooks, LockWaiter())
        val a = QueuedAnswer(q.nextSeq(), deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 100, 1)
        val io = Executors.newSingleThreadExecutor()
        try {
            q.enqueue(a)
            io.execute { q.drain() }
            val confirmRan = CountDownLatch(1)
            io.execute { events += "confirm"; confirmRan.countDown() } // the confirm / load of the next card, queued behind the write
            assertTrue(inWrite.await(10, TimeUnit.SECONDS))
            assertFalse("the next task ran while the write was still going", confirmRan.await(300, TimeUnit.MILLISECONDS))
            assertEquals(1, q.size) // main thread asks while the io thread is blocked: no deadlock
            gate.countDown()
            assertTrue(confirmRan.await(10, TimeUnit.SECONDS))
            assertEquals(listOf("outcome:Saved", "confirm"), events.toList())
        } finally { gate.countDown(); io.shutdownNow() }
    }

    @Test fun theRealWaiterTimesOutWakesAndRemembersAWakeNobodyWaitedFor() {
        val w = LockWaiter()
        assertFalse(w.await(20))
        w.wake(); assertTrue(w.await(5_000)); assertFalse("a wake is used up once", w.await(10))
        w.wake(); w.clear(); assertFalse(w.await(10))
        val t = Thread { Thread.sleep(50); w.wake() }
        t.start()
        assertTrue("a wait without a time limit ends on the wake", w.await(-1)); t.join()
    }

    @Test fun retryNowWakesABackOffAndDiscardWakesAParkedWait() {
        val fake = newFake(); val c = fake.queueOf(deck).first()
        for (i in 1..4) fake.plan("answer", F.THROW_BEFORE)
        val retrying = java.util.concurrent.LinkedBlockingQueue<AnswerOutcome>()
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) { retrying.add(o) }
        }
        val q = AnswerQueue(fake, MemStore(), hooks, LockWaiter(), backoff = { 60_000 }, parkAfter = 3)
        val a = QueuedAnswer(q.nextSeq(), deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 100, 1)
        val io = Executors.newSingleThreadExecutor()
        try {
            q.enqueue(a); io.execute { q.drain() }
            val t0 = System.nanoTime()
            var r = retrying.poll(10, TimeUnit.SECONDS) as AnswerOutcome.Retrying
            assertEquals(1, r.attempt); assertEquals(60_000L, r.waitMs)
            q.retryNow() // would wait a minute otherwise
            r = retrying.poll(10, TimeUnit.SECONDS) as AnswerOutcome.Retrying
            assertEquals(2, r.attempt)
            q.retryNow()
            r = retrying.poll(10, TimeUnit.SECONDS) as AnswerOutcome.Retrying
            assertEquals(3, r.attempt); assertEquals("parked", -1L, r.waitMs)
            assertTrue("two wakes took no back-off time", System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(30))
            assertTrue(q.discard(a.seq)) // wakes the parked wait; the answer ends
            val end = retrying.poll(10, TimeUnit.SECONDS)
            assertTrue(end.toString(), end is AnswerOutcome.NotWritten && end.reason == Reason.DISCARDED)
            assertEquals(0, fake.revlog.size)
        } finally { io.shutdownNow() }
    }

    @Test fun enqueueFromAnotherThreadWhileTheIoThreadIsInTheWriteDoesNotBlock() {
        val fake = newFake(); val cs = fake.queueOf(deck).toList()
        val gate = CountDownLatch(1); val inWrite = CountDownLatch(1)
        val slow = object : AnswerProvider by fake {
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { inWrite.countDown(); gate.await(10, TimeUnit.SECONDS); fake.answer(key, ease, ms) }
        }
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) {}
        }
        val store = MemStore()
        val q = AnswerQueue(slow, store, hooks, LockWaiter())
        val mk = { c: FakeAnki.Card -> QueuedAnswer(q.nextSeq(), deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 100, 1) }
        val io = Executors.newSingleThreadExecutor()
        try {
            q.enqueue(mk(cs[0])); io.execute { q.drain() }
            assertTrue(inWrite.await(10, TimeUnit.SECONDS))
            val done = AtomicInteger()
            val t = Thread { q.enqueue(mk(cs[1])); done.incrementAndGet() }
            t.start(); t.join(3_000)
            assertEquals("the main thread's enqueue must not wait for the slow write", 1, done.get())
            assertEquals(2, AnswerJournal.decode(store.text).answers.size)
            gate.countDown()
            io.execute { q.drain() }
            io.shutdown(); assertTrue(io.awaitTermination(10, TimeUnit.SECONDS))
            assertEquals(listOf(cs[0].id, cs[1].id), fake.revlog.map { it.cardId })
        } finally { gate.countDown(); io.shutdownNow() }
    }

    // ---- a journal that cannot be rewritten (R9 wave 6 FIX, Codex 2)

    @Test fun aToldAnswerLeavesTheJournalEvenWhenTheDiskIsFull() {
        val r = rigWith(); val cs = r.fake.queueOf(deck).toList()
        r.q.enqueue(r.entry(cs[0]))
        r.store.failWrites = true // the disk fills up after the entry was journaled
        r.fake.moveToTop(cs[2]) // another card is first: the user is told, nothing is written
        val o = r.q.drain().single() as AnswerOutcome.NotWritten
        assertEquals(Reason.NOT_TOP, o.reason)
        assertNull("the file is deleted, which needs no free space", r.store.text)
        val q2 = AnswerQueue(r.fake, r.store, r.hooks, r.waiter, { r.fake.clock.ms })
        assertEquals("a new process has nothing to replay, so the told answer can never be written later", 0, q2.restore().count)
    }

    @Test fun aRewriteThatFailedIsTriedAgainBeforeTheNextAnswerStarts() {
        val r = rigWith(); val cs = r.fake.queueOf(deck).toList()
        val x = r.entry(cs[0]); val y = r.entry(cs[1])
        // the disk recovers right after x ended (its rewrite had failed): y must find a journal that no longer holds x
        val h = object : AnswerHooks by r.hooks {
            override fun outcome(o: AnswerOutcome) { r.hooks.outcome(o); if (o.answer.seq == x.seq) r.store.failWrites = false }
        }
        val q = AnswerQueue(r.fake, r.store, h, r.waiter, { r.fake.clock.ms })
        q.enqueue(x); q.enqueue(y)
        r.fake.moveToTop(cs[1]) // x is not first any more (NOT_TOP); y is
        r.store.failWrites = true
        var seen: List<Long>? = null
        r.fake.duringWrite = { seen = AnswerJournal.decode(r.store.text).answers.map { it.seq } }
        val out = q.drain()
        assertEquals(Reason.NOT_TOP, (out[0] as AnswerOutcome.NotWritten).reason)
        assertTrue(out[1] is AnswerOutcome.Saved)
        assertEquals("when y was written, the journal held only y", listOf(y.seq), seen)
        assertNull(r.store.text)
    }

    @Test fun aFullDiskNeverStopsTheAnswersAndTheLastOneStillClearsTheFile() {
        val r = rigWith(); val cs = r.fake.queueOf(deck).toList()
        val x = r.entry(cs[0]); val y = r.entry(cs[1])
        r.q.enqueue(x); r.q.enqueue(y)
        r.fake.moveToTop(cs[1]); r.store.failWrites = true
        val out = r.q.drain()
        assertEquals(Reason.NOT_TOP, (out[0] as AnswerOutcome.NotWritten).reason)
        assertTrue(out[1] is AnswerOutcome.Saved)
        assertEquals(listOf(cs[1].id), r.fake.revlog.map { it.cardId })
        assertNull("once nothing is left the file goes, whatever the disk", r.store.text)
        assertTrue(r.journalFailed >= 1)
        assertEquals(0, r.q.size)
    }

    @Test fun ifTheFileCannotBeDeletedAnEmptyJournalIsWrittenInstead() {
        val r = rigWith()
        r.store.failClears = true
        r.q.enqueue(r.entry(first(r.fake)))
        assertTrue(r.q.drain().single() is AnswerOutcome.Saved)
        assertNotNull("the fallback wrote an empty list", r.store.text)
        assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
        assertNull(AnswerJournal.decode(r.store.text).problem)
    }

    @Test fun aJournalThatCanNeitherBeWrittenNorDeletedStillLetsTheAnswerRun() {
        val r = rigWith()
        r.store.failWrites = true; r.store.failClears = true
        assertFalse(r.q.enqueue(r.entry(first(r.fake))))
        assertTrue(r.q.drain().single() is AnswerOutcome.Saved)
        assertEquals(1, r.fake.revlog.size)
        assertTrue("both the enqueue and the end told the host", r.journalFailed >= 2)
    }
}
