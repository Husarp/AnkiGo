package com.husarp.ankigo.study

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.OwnAnswers
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The process-wide study io with a REAL executor: ordering behind a write, the journal replay, routing to screens, the game step (at the tap, R10-B23). */
class AnswerHostTest {
    private val deck = 7L

    private class Env : AnswerHost.Env {
        val posted = LinkedBlockingQueue<Runnable>()   // the main thread's queue
        val log = CopyOnWriteArrayList<String>()
        val expects = AtomicInteger(); val cancels = AtomicInteger()
        val pins = CopyOnWriteArrayList<Boolean>()   // the `pinned` flag of every expect, in order
        val games = CopyOnWriteArrayList<Pair<QueuedAnswer, String?>>()   // game steps, in order
        val settles = CopyOnWriteArrayList<Triple<QueuedAnswer, Map<String, String?>, Boolean>>()   // (answer, after, saved by this process)
        val drops = CopyOnWriteArrayList<QueuedAnswer>()
        val restoredAsked = CopyOnWriteArrayList<List<QueuedAnswer>>()
        @Volatile var onExpect: ((Map<String, String?>, Boolean) -> Unit)? = null   // a pretend detector's ledger
        @Volatile var onCancel: ((Map<String, String?>) -> Unit)? = null
        @Volatile var onGame: ((QueuedAnswer) -> Unit)? = null
        @Volatile var onSettle: ((QueuedAnswer) -> Unit)? = null
        /** What the game decides for replayed answers; default: none of them is in the save yet, so each gets its game step now. */
        @Volatile var onRestored: ((List<QueuedAnswer>, () -> Unit) -> Unit)? = null
        override fun post(r: Runnable) { posted += r }
        override fun elapsed() = System.nanoTime() / 1_000_000L
        override fun expect(before: Map<String, String?>, pinned: Boolean) { expects.incrementAndGet(); pins += pinned; onExpect?.invoke(before, pinned) }
        override fun cancel(before: Map<String, String?>) { cancels.incrementAndGet(); onCancel?.invoke(before) }
        override fun gameStep(answer: QueuedAnswer, front: String?, done: (List<GameEvent>, Long) -> Unit) {
            games += answer to front; onGame?.invoke(answer); done(emptyList(), 2)
        }
        override fun restored(answers: List<QueuedAnswer>, done: () -> Unit) {
            restoredAsked += answers
            val decide = onRestored
            if (decide != null) decide(answers, done) else { for (a in answers) games += a to null; done() }
        }
        override fun settled(answer: QueuedAnswer, after: Map<String, String?>, saved: Boolean) { settles += Triple(answer, after, saved); onSettle?.invoke(answer) }
        override fun dropped(answer: QueuedAnswer) { drops += answer }
        override fun log(text: String) { log += text }

        /** Runs what the io thread posted, like the main looper does. */
        fun pump() { while (true) (posted.poll() ?: return).run() }
        fun pumpUntil(ms: Long = 10_000, cond: () -> Boolean) {
            val end = System.nanoTime() + ms * 1_000_000
            while (!cond()) { pump(); if (System.nanoTime() > end) fail("timed out"); Thread.sleep(5) }
        }
    }

    private class Screen : StudyListener {
        val states = ArrayList<AnswerHost.Unresolved?>()
        val outcomes = ArrayList<Pair<AnswerOutcome, AnswerHost.Info>>()
        val games = ArrayList<Long>()
        val gameMine = ArrayList<Boolean>()   // for each game step told to this screen: did it make that answer?
        override fun unresolved(u: AnswerHost.Unresolved?) { states += u }
        override fun outcome(o: AnswerOutcome, info: AnswerHost.Info) { outcomes += o to info }
        override fun gameDone(answer: QueuedAnswer, events: List<GameEvent>, absorbMs: Long, gameMs: Long, mine: Boolean) { games += answer.seq; gameMine += mine }
    }

    /** The write waits on [release] (a slow AnkiDroid). */
    private class Gate(val fake: FakeAnki) : AnswerProvider by fake {
        val inWrite = CountDownLatch(1); val release = CountDownLatch(1)
        override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) {
            inWrite.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            fake.answer(key, ease, ms)
        }
    }

    private fun newFake(n: Int = 3) = FakeAnki().also { f -> for (i in 1..n) f.add(deck, 100L + i); f.selected = 99 }
    private fun entry(host: AnswerHost, fake: FakeAnki, c: FakeAnki.Card = fake.queueOf(deck).first(), ease: Int = 3) =
        QueuedAnswer(host.nextSeq(), deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, ease, 500, 1_700_000_000_000L, buttons = 4)

    private class Rig(val fake: FakeAnki = FakeAnki().also { f -> for (i in 1..3) f.add(7, 100L + i); f.selected = 99 }, provider: AnswerProvider? = null, backoff: (Int) -> Long = { 60_000L }) {
        val env = Env()
        val store = MemStore()
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "test-study-io").apply { isDaemon = true } }
        val host = AnswerHost(provider ?: fake, store, env, io, LockWaiter(), backoff = backoff)
        fun close() { io.shutdownNow() }
    }

    /**
     * An answer that APPLIED in AnkiDroid but cannot be read back, so it stays unresolved (after three tries it is parked, waiting for
     * the user), with a pretend detector ledger ([own], the real [OwnAnswers]) that the host's expect / cancel calls feed. The
     * waiter is fake: a parked wait is "the user is away for [AWAY_MS]" and runs [whileAway] (a detector poll, a give-up) then wakes.
     */
    private class Away(val deck: Long, val absorb: Boolean = false) {
        val fake = FakeAnki().also { f -> for (i in 1..3) f.add(deck, 100L + i); f.selected = 99 }
        val own = OwnAnswers()
        val env = Env()
        val store = MemStore()
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "test-study-io").apply { isDaemon = true } }
        val card = fake.queueOf(deck).first()
        val repsBefore = card.reps
        var live = 0 // answers the pretend detector counted on its own (it is on the io thread: only touched there)
        var whileAway: () -> Unit = {}
        private var polled = repsBefore
        private fun id(b: Map<String, String?>) = b["_id"]!!.toLong()
        private fun rep(b: Map<String, String?>) = b["reps"]!!.toInt() + 1 // the rep the answer makes
        val waiter = object : Waiter {
            override fun await(ms: Long): Boolean {
                if (ms >= 0) { fake.clock.ms += ms; return false }
                fake.clock.ms += AWAY_MS
                whileAway()
                return true // the Retry button
            }
            override fun wake() {}
            override fun clear() {}
        }
        val host = AnswerHost(fake, store, env, io, waiter, clock = { fake.clock.ms }, backoff = { 10L }, parkAfter = 3)
        val answer = QueuedAnswer(host.nextSeq(), deck, card.nid, card.ord, card.id, fake.stats(card.key)!!, 3, 500, 1_700_000_000_000L, buttons = 4)

        init {
            env.onExpect = { b, pinned -> own.expect(id(b), rep(b), fake.clock.ms, pinned) }
            env.onCancel = { b -> own.cancel(id(b), rep(b)) }
            env.onGame = { a -> own.expect(id(a.before), rep(a.before), fake.clock.ms, pinned = true) } // LiveSession.ownAnswer pins it at the tap
            if (absorb) env.onSettle = { a -> own.consume(id(a.before), rep(a.before) - 1, rep(a.before), fake.clock.ms) } // the detector settles it
        }

        /** The first try writes and throws; the next two reads fail: parked, with the card already answered in AnkiDroid. */
        fun appliedButUnreadable() { fake.plan("answer", F.THROW_AFTER); fake.plan("stats", F.NONE, F.NULL, F.NULL) }

        /** A detector poll: the card's rep went up since the last one; what the ledger does not recognise as ours it counts as a live answer. */
        fun poll() {
            val c = fake.card(card.key)!!
            if (c.reps > polled) { live += (c.reps - polled) - own.consume(c.id, polled, c.reps, fake.clock.ms); polled = c.reps }
        }

        /** What the ledger still expects of this answer at [now] (1 = an expectation that a poll would swallow). */
        fun expectedAt(now: Long) = own.consume(card.id, repsBefore, repsBefore + 1, now)

        fun close() { io.shutdownNow() }
    }

    @Test fun aLoadQueuedBehindASlowWriteRunsOnlyAfterTheAnswerIsResolved() {
        val fake = newFake(); val gate = Gate(fake)
        val r = Rig(fake, gate)
        try {
            r.host.start()
            val screen = Screen(); r.host.register(screen)
            val a = entry(r.host, fake)
            r.host.enqueue(a, "front", screen)
            val order = CopyOnWriteArrayList<String>()
            val postedWhenLoadRan = AtomicInteger(-1)
            r.host.submit(null, Runnable { postedWhenLoadRan.set(r.env.posted.size); order += "load" })
            assertTrue(gate.inWrite.await(10, TimeUnit.SECONDS))
            Thread.sleep(300)
            assertTrue("the load must not run while the write is in flight", order.isEmpty())
            assertTrue(r.host.unresolvedNow())
            gate.release.countDown()
            r.env.pumpUntil { order.isNotEmpty() }
            assertEquals(listOf("load"), order)
            assertEquals("the answer was already resolved (and its outcome posted) when the load ran", 1, postedWhenLoadRan.get())
            assertEquals(1, fake.revlog.size)
            r.env.pump()
            assertTrue(screen.outcomes.single().first is AnswerOutcome.Saved)
            assertFalse(r.host.unresolvedNow())
        } finally { gate.release.countDown(); r.close() }
    }

    @Test fun tasksRunInTheOrderTheyWereSubmitted() {
        val r = Rig()
        try {
            val order = CopyOnWriteArrayList<Int>()
            val done = CountDownLatch(50)
            for (i in 1..50) r.host.submit(null, Runnable { order += i; done.countDown() })
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals((1..50).toList(), order.toList())
        } finally { r.close() }
    }

    @Test fun anAnswersWriteIsQueuedBeforeALoadSubmittedRightAfterIt() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val a = entry(r.host, fake)
            val seen = AtomicInteger(-1)
            r.host.enqueue(a, null, null)
            r.host.submit(null, Runnable { seen.set(fake.revlog.size) })   // main thread order: enqueue, then the load
            r.env.pumpUntil { seen.get() >= 0 }
            assertEquals("the load saw the card already written", 1, seen.get())
        } finally { r.close() }
    }

    @Test fun theJournalIsReplayedBeforeAnyOtherTask() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val c = fake.queueOf(deck).first()
            val old = QueuedAnswer(41, deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 500, 1)
            r.store.write(AnswerJournal.encode(listOf(old)))   // the journal of a process that died
            val seen = AtomicInteger(-1)
            r.host.start()
            r.host.submit(null, Runnable { seen.set(fake.revlog.size) })
            r.env.pumpUntil { seen.get() >= 0 }
            assertEquals("the replay ran before the first load", 1, seen.get())
            r.env.pump()
            assertEquals("the save did not hold it: the replay gave it its game step, once", 1, r.env.games.size)
            assertNull("a replayed answer has no card front", r.env.games.single().second)
            assertEquals("and the write only settled it", 1, r.env.settles.size)
            assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
        } finally { r.close() }
    }

    @Test fun anAnswerFoundAlreadyWrittenAfterARestartIsOnlySettled() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val c = fake.queueOf(deck).first()
            val before = fake.stats(c.key)!!
            r.store.write(AnswerJournal.encode(listOf(QueuedAnswer(5, deck, c.nid, c.ord, c.id, before, 3, 500, 1))))
            fake.externalAnswer(c, 3)   // the dead process's write had applied
            r.env.onRestored = { _, done -> done() }   // and its save held the game step
            val screen = Screen()
            r.host.start(); r.host.register(screen)
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertTrue(screen.outcomes.single().first is AnswerOutcome.AlreadySaved)
            assertEquals("no game step", 0, r.env.games.size)
            val (a, after, saved) = r.env.settles.single()
            assertEquals(5L, a.seq); assertFalse("an earlier process wrote it", saved); assertEquals("${before["reps"]!!.toInt() + 1}", after["reps"])
            assertEquals("only the plain expectation that ends the replay's pin", listOf(false), r.env.pins.toList())
            assertEquals("not written again", 1, fake.revlog.size)
        } finally { r.close() }
    }

    @Test fun theAnswerIsSettledEvenWhenNoScreenIsLeft() {
        val fake = newFake(); val gate = Gate(fake); val r = Rig(fake, gate)
        try {
            r.host.start()
            val screen = Screen(); r.host.register(screen)
            r.host.enqueue(entry(r.host, fake), "What is 2+2?", screen)
            assertEquals("the game step ran at the tap", "What is 2+2?", r.env.games.single().second)
            assertTrue(gate.inWrite.await(10, TimeUnit.SECONDS))
            r.host.unregister(screen)   // the screen is closed while the write is slow
            gate.release.countDown()
            r.env.pumpUntil { r.env.settles.isNotEmpty() }
            assertEquals(1, fake.revlog.size); assertEquals("no second step", 1, r.env.games.size)
            assertTrue("the end was told to nobody, nobody needed it", screen.outcomes.isEmpty())
        } finally { gate.release.countDown(); r.close() }
    }

    @Test fun theOutcomeGoesToTheScreenThatMadeTheAnswer() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val mine = Screen(); val other = Screen()
            r.host.register(mine); r.host.register(other)
            r.host.enqueue(entry(r.host, fake), "f", mine)
            r.env.pumpUntil { mine.outcomes.isNotEmpty() }
            assertTrue(mine.outcomes.single().second.mine)
            assertEquals("its game step is its own too", listOf(true), mine.gameMine)
            assertTrue(other.outcomes.isEmpty()); assertTrue(other.games.isEmpty())
        } finally { r.close() }
    }

    @Test fun aScreenThatIsGoneHandsItsAnswerToTheNewestScreen() {
        val fake = newFake(); val gate = Gate(fake); val r = Rig(fake, gate)
        try {
            val old = Screen(); r.host.register(old)
            r.host.enqueue(entry(r.host, fake), "f", old)
            assertTrue(gate.inWrite.await(10, TimeUnit.SECONDS))
            r.host.unregister(old)
            val fresh = Screen(); r.host.register(fresh)   // the screen was recreated while the write ran
            gate.release.countDown()
            r.env.pumpUntil { fresh.outcomes.isNotEmpty() }
            val (o, info) = fresh.outcomes.single()
            assertTrue(o is AnswerOutcome.Saved); assertFalse(info.mine)
            assertEquals("the game step was the old screen's, at its tap", listOf(true), old.gameMine)
            assertTrue("the new screen never counts it in its summary", fresh.games.isEmpty())
            assertTrue(old.outcomes.isEmpty())
        } finally { gate.release.countDown(); r.close() }
    }

    @Test fun aNotWrittenAnswerNobodyCouldBeToldAboutWaitsForTheNextScreen() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val c = fake.queueOf(deck).first()
            val a = entry(r.host, fake, c)
            fake.moveToTop(fake.queueOf(deck).last())   // another card is first now: this answer must not be written
            r.host.enqueue(a, "f", null)
            r.env.pumpUntil { r.env.cancels.get() == 1 && r.host.unresolved() == null }
            assertEquals(0, fake.revlog.size)
            val next = Screen(); r.host.register(next)
            val (o, info) = next.outcomes.single()
            assertEquals(AnswerOutcome.Reason.NOT_TOP, (o as AnswerOutcome.NotWritten).reason)
            assertFalse(info.mine)
            val later = Screen(); r.host.register(later)
            assertTrue("told once", later.outcomes.isEmpty())
        } finally { r.close() }
    }

    @Test fun theStateTellsWhatIsUnresolvedAndASlowOrFailingWriteShowsAsRetrying() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val screen = Screen(); r.host.register(screen)
            assertEquals(listOf<AnswerHost.Unresolved?>(null), screen.states.toList())   // told on register
            fake.plan("answer", F.THROW_BEFORE)
            val a = entry(r.host, fake)
            r.host.enqueue(a, "f", screen)
            val first = screen.states.last()!!
            assertEquals(1, first.count); assertEquals(0, first.attempt); assertFalse(first.retrying); assertEquals(a.seq, first.seq)
            r.env.pumpUntil { screen.states.last()?.retrying == true }
            val retrying = screen.states.last()!!
            assertEquals(1, retrying.attempt); assertFalse(retrying.parked); assertEquals(first.since, retrying.since)
            assertNotNull(retrying.reason)
            r.host.retryNow()   // the Retry button wakes the 60 s back-off at once
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            val saved = screen.outcomes.single().first as AnswerOutcome.Saved
            assertEquals(2, saved.attempts)
            assertNull(screen.states.last()); assertNull(r.host.unresolved())
            assertEquals(1, fake.revlog.size)
        } finally { r.close() }
    }

    @Test fun aNewScreenWakesAnAnswerThatIsWaitingToTryAgain() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val first = Screen(); r.host.register(first)
            fake.plan("answer", F.THROW_BEFORE)
            r.host.enqueue(entry(r.host, fake), "f", first)
            r.env.pumpUntil { first.states.last()?.retrying == true }
            val second = Screen(); r.host.register(second)   // registering = a new study screen: retry now
            assertTrue("the new screen hears the state it joined", second.states.single()!!.retrying)
            r.env.pumpUntil { first.outcomes.isNotEmpty() }
            assertEquals(1, fake.revlog.size)
        } finally { r.close() }
    }

    @Test fun discardingAParkedAnswerEndsItNotWrittenAndFreesTheQueue() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val screen = Screen(); r.host.register(screen)
            for (i in 1..10) fake.plan("answer", F.THROW_BEFORE)
            val a = entry(r.host, fake)
            val parked = AnswerHost(fake, r.store, r.env, r.io, LockWaiter(), backoff = { 1L }, parkAfter = 3)
            parked.register(screen)
            parked.enqueue(a, "f", screen)
            r.env.pumpUntil { screen.states.lastOrNull()?.parked == true }
            val seq = screen.states.last()!!.seq
            val ran = AtomicInteger()
            parked.submit(null, Runnable { ran.incrementAndGet() })
            Thread.sleep(150)
            assertEquals("a load waits behind a parked answer", 0, ran.get())
            assertTrue(parked.discard(seq))
            r.env.pumpUntil { screen.outcomes.isNotEmpty() && ran.get() == 1 }
            val o = screen.outcomes.single().first as AnswerOutcome.NotWritten
            assertEquals(AnswerOutcome.Reason.DISCARDED, o.reason)
            assertEquals(0, fake.revlog.size); assertEquals("cancelOwn ran for it", 1, r.env.cancels.get())
        } finally { r.close() }
    }

    @Test fun whatAClosedScreenQueuedAndTheIoThreadHasNotStartedNeverRuns() {
        val r = Rig()
        try {
            val started = CountDownLatch(1); val hold = CountDownLatch(1)
            r.host.submit(null, Runnable { started.countDown(); hold.await(10, TimeUnit.SECONDS) })   // the io thread is busy
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val screen = Any()
            val ran = CopyOnWriteArrayList<String>()
            r.host.submit(screen, Runnable { ran += "screen load" })
            r.host.submit(null, Runnable { ran += "write of a bury" })   // not owned by a screen: must still happen
            r.host.cancelOwner(screen)
            hold.countDown()
            val end = System.nanoTime() + 5_000_000_000L
            while (ran.isEmpty() && System.nanoTime() < end) Thread.sleep(5)
            Thread.sleep(100)
            assertEquals(listOf("write of a bury"), ran.toList())
        } finally { r.close() }
    }

    @Test fun aFailingTaskDoesNotStopTheNextOnes() {
        val r = Rig()
        try {
            val ran = CountDownLatch(1)
            r.host.submit(null, Runnable { error("boom") })
            r.host.submit(null, Runnable { ran.countDown() })
            assertTrue(ran.await(10, TimeUnit.SECONDS))
            assertTrue(r.env.log.any { it.contains("boom") })
        } finally { r.close() }
    }

    @Test fun theJournalHoldsTheAnswerFromTheMomentItIsQueued() {
        val fake = newFake(); val gate = Gate(fake); val r = Rig(fake, gate)
        try {
            val a = entry(r.host, fake)
            assertTrue(r.host.enqueue(a, "f", null))
            assertEquals("the journal was written before enqueue returned", listOf(a.seq), AnswerJournal.decode(r.store.text).answers.map { it.seq })
            assertTrue(gate.inWrite.await(10, TimeUnit.SECONDS))
            assertEquals("still there while the write is in flight", 1, AnswerJournal.decode(r.store.text).answers.size)
            gate.release.countDown()
            r.env.pumpUntil { r.host.unresolved() == null }
            assertEquals(0, AnswerJournal.decode(r.store.text).answers.size)
        } finally { gate.release.countDown(); r.close() }
    }

    @Test fun aJournalThatCannotBeWrittenStillQueuesTheAnswer() {
        val fake = newFake(); val r = Rig(fake)
        try {
            r.store.failWrites = true
            assertFalse(r.host.enqueue(entry(r.host, fake), "f", null))
            r.env.pumpUntil { fake.revlog.size == 1 }
            assertTrue(r.env.log.any { it.contains("journal could not be written") })
        } finally { r.close() }
    }

    // ---- R9 wave 6 FIX, Codex 3: the own-answer expectation is pinned while an answer is unresolved

    @Test fun everyAttemptPinsTheExpectationAndTheSavedAnswerHandsOverAPlainOne() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val screen = Screen(); r.host.register(screen)
            val a = entry(r.host, fake)
            fake.plan("answer", F.THROW_BEFORE)
            r.host.enqueue(a, "f", screen)
            r.env.pumpUntil { screen.states.last()?.retrying == true }
            r.host.retryNow()
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            val pins = r.env.pins.toList()
            assertTrue(pins.toString(), pins.size >= 3)
            assertTrue("while the answer is unresolved every expectation is pinned: $pins", pins.dropLast(1).all { it })
            assertFalse("saved: the pin is released, the game step runs under a plain 30 s one", pins.last())
        } finally { r.close() }
    }

    @Test fun aLongWaitForTheUserNeverLetsADetectorPollCountAnOwnAnswerTwice() {
        val w = Away(deck, absorb = true)
        try {
            val screen = Screen(); w.host.start(); w.host.register(screen)
            w.appliedButUnreadable()
            w.whileAway = { w.poll() } // five minutes later the screen was left and the detector polls: the card moved one rep
            w.host.enqueue(w.answer, "front", screen)
            w.env.pumpUntil { screen.outcomes.isNotEmpty() }
            val saved = screen.outcomes.single().first as AnswerOutcome.Saved
            assertEquals(4, saved.attempts)
            assertEquals("written once", 1, w.fake.revlog.size)
            assertEquals("the poll found the rep change expected, so the detector counted nothing itself", 0, w.live)
            assertEquals("the saved answer's game step is the one count", 1, w.env.games.size)
            assertEquals("and nothing stays expected", 0, w.expectedAt(w.fake.clock.ms))
        } finally { w.close() }
    }

    @Test fun savingReleasesThePinSoNothingStaysExpectedForEver() {
        val w = Away(deck) // no poll, and no detector absorb either: only the plain expectation of the handoff is left
        try {
            val screen = Screen(); w.host.start(); w.host.register(screen)
            w.appliedButUnreadable()
            w.host.enqueue(w.answer, "front", screen)
            w.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertTrue(screen.outcomes.single().first is AnswerOutcome.Saved)
            assertEquals("thirty seconds on, nothing is expected any more", 0, w.expectedAt(w.fake.clock.ms + 31_000))
        } finally { w.close() }
    }

    @Test fun aGivenUpAnswerLeavesNoPinnedExpectationBehind() {
        val w = Away(deck)
        try {
            val screen = Screen(); w.host.start(); w.host.register(screen)
            for (i in 1..3) w.fake.plan("answer", F.THROW_BEFORE) // never applied
            w.whileAway = { w.host.discard(w.answer.seq) }
            w.host.enqueue(w.answer, "front", screen)
            w.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertEquals(AnswerOutcome.Reason.DISCARDED, (screen.outcomes.single().first as AnswerOutcome.NotWritten).reason)
            assertEquals(0, w.fake.revlog.size)
            assertEquals("cancelOwn removed the pinned expectation", 0, w.expectedAt(w.fake.clock.ms))
            assertTrue(w.env.pins.all { it }); assertEquals(1, w.env.cancels.get())
            assertEquals("the game keeps the step it made at the tap", 1, w.env.games.size); assertEquals(1, w.env.drops.size)
        } finally { w.close() }
    }

    // ---- R9 wave 6 FIX, Codex 5: another screen's answer is only told, never counted

    @Test fun aNotWrittenAnswerOfAnotherScreenStillReachesTheNewScreenToTellTheUser() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val a = Screen(); r.host.register(a)
            val c = fake.queueOf(deck).first()
            val answer = entry(r.host, fake, c)
            fake.moveToTop(fake.queueOf(deck).last())
            r.host.unregister(a) // A was closed before the answer was even tried
            val b = Screen(); r.host.register(b)
            r.host.enqueue(answer, "f", a)
            r.env.pumpUntil { b.outcomes.isNotEmpty() }
            val (o, info) = b.outcomes.single()
            assertEquals(AnswerOutcome.Reason.NOT_TOP, (o as AnswerOutcome.NotWritten).reason)
            assertFalse("told, but not B's answer", info.mine)
        } finally { r.close() }
    }

    // ---- R10-B23: the game step runs at the tap; the write's end only settles it

    @Test fun theGameStepRunsAtTheTapWhileTheWriteIsStillBlocked() {
        val fake = newFake(); val gate = Gate(fake); val r = Rig(fake, gate)
        try {
            r.host.start()
            val screen = Screen(); r.host.register(screen)
            val a = entry(r.host, fake)
            r.host.enqueue(a, "front", screen)
            assertEquals("the step ran inside enqueue, on the tap", listOf(a.seq), r.env.games.map { it.first.seq })
            assertEquals(listOf(a.seq), screen.games); assertEquals("its own answer: the summary counts it", listOf(true), screen.gameMine)
            assertTrue(gate.inWrite.await(10, TimeUnit.SECONDS))
            Thread.sleep(100)
            assertEquals("AnkiDroid has nothing yet", 0, fake.revlog.size)
            assertTrue("nothing settled while the write hangs", r.env.settles.isEmpty() && screen.outcomes.isEmpty())
            gate.release.countDown()
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertTrue(screen.outcomes.single().first is AnswerOutcome.Saved)
            assertEquals("the Saved outcome does not count it again", 1, r.env.games.size); assertEquals(listOf(a.seq), screen.games)
            val (settled, after, saved) = r.env.settles.single()
            assertEquals(a.seq, settled.seq); assertTrue(saved); assertEquals("${a.before["reps"]!!.toInt() + 1}", after["reps"])
            assertEquals(1, fake.revlog.size)
        } finally { gate.release.countDown(); r.close() }
    }

    @Test fun anAnswerThatEndsNotWrittenKeepsItsGameStep() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val screen = Screen(); r.host.register(screen)
            val a = entry(r.host, fake)
            fake.moveToTop(fake.queueOf(deck).last())   // AnkiDroid shows another card first: NOT_TOP
            r.host.enqueue(a, "f", screen)
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertEquals(AnswerOutcome.Reason.NOT_TOP, (screen.outcomes.single().first as AnswerOutcome.NotWritten).reason)
            assertEquals("the battle is never undone", listOf(a.seq), r.env.games.map { it.first.seq })
            assertEquals(listOf(a.seq), r.env.drops.map { it.seq }); assertTrue(r.env.settles.isEmpty())
            assertEquals(1, r.env.cancels.get()); assertEquals(0, fake.revlog.size)
        } finally { r.close() }
    }

    @Test fun replayedAnswersReachAnkiDroidOnlyAfterTheGameDecidedWhichItCounted() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val c = fake.queueOf(deck).first()
            r.store.write(AnswerJournal.encode(listOf(QueuedAnswer(9, deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 500, 1))))
            val decide = LinkedBlockingQueue<() -> Unit>()
            r.env.onRestored = { _, done -> decide += done }   // the game is still loading
            val loadRan = AtomicInteger(-1)
            val calls = fake.count("answer") + fake.count("stats")
            r.host.start()
            r.host.submit(null, Runnable { loadRan.set(fake.revlog.size) })
            Thread.sleep(300)
            assertEquals("nothing reached AnkiDroid before the decision", calls, fake.count("answer") + fake.count("stats"))
            assertEquals("and the first load waits behind the replay", -1, loadRan.get())
            assertEquals(listOf(9L), r.env.restoredAsked.single().map { it.seq })
            assertTrue(r.env.restoredAsked.single().single().restored)
            decide.take().invoke()
            r.env.pumpUntil { loadRan.get() >= 0 && r.env.settles.isNotEmpty() }
            assertEquals("written before the load ran", 1, loadRan.get())
            assertEquals("the game decided: no step from the host", 0, r.env.games.size)
        } finally { r.close() }
    }

    @Test fun aDetectorPollThatSeesTheWriteLandNeverCountsTheAnswerTheTapCounted() {
        val fake = newFake(); val r = Rig(fake)
        try {
            val own = OwnAnswers()
            val a = entry(r.host, fake)
            val id = a.cardId; val rep = a.before["reps"]!!.toInt() + 1
            r.env.onGame = { own.expect(id, rep, 0, pinned = true) }   // LiveSession.ownAnswer at the tap
            r.env.onExpect = { _, pinned -> own.expect(id, rep, 0, pinned) }
            r.env.onSettle = { own.consume(id, rep - 1, rep, 0) }       // the settle's absorb
            val screen = Screen(); r.host.register(screen)
            r.host.enqueue(a, "f", screen)
            val end = System.nanoTime() + 10_000_000_000L
            while (fake.revlog.isEmpty() && System.nanoTime() < end) Thread.sleep(5)
            assertEquals("a poll right after the write (before the outcome reached the main thread) finds it expected", 1, own.consume(id, rep - 1, rep, 1_000_000))
            r.env.pumpUntil { screen.outcomes.isNotEmpty() }
            assertEquals("so the detector counted nothing; the tap's step is the one count", 1, r.env.games.size)
            assertEquals("nothing stays expected after the settle", 0, own.consume(id, rep - 1, rep, 1_000_000))
        } finally { r.close() }
    }

    private companion object { const val AWAY_MS = 300_000L }
}
