package com.husarp.ankigo.study

import com.husarp.ankigo.study.AnswerOutcome.Reason
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** The whole flow (reducer + io FIFO + write queue + pretend AnkiDroid), with the invariants checked after every scenario. */
class StudySimTest {
    private val deck = 7L
    private val other = 8L

    private fun sim(notes: Int, ords: Int = 1, configure: (FakeAnki) -> Unit = {}): StudySim {
        val fake = StudySim.deck(deck, notes, ords)
        fake.selected = 99
        configure(fake)
        return StudySim(fake, deck).also { it.boot(); it.pump() }
    }

    private fun StudySim.top(i: Int = 0) = fake.queueOf(deck)[i]
    private fun StudySim.outcomeOf(m: QueuedAnswer) = terminal(m)
    private fun StudySim.name(c: FakeAnki.Card) = "c${c.nid}/${c.ord}"
    private fun StudySim.shownName() = shows.last().substring(2)

    // ---- normal runs

    @Test fun aFastRunOfTwentyAnswersEveryPredictionHits() {
        val s = sim(20)
        val order = s.fake.queueOf(deck).map { it.id }
        repeat(20) { i ->
            assertTrue("card ${i + 1} is shown", s.displayed != null && !s.displayed!!.predicted)
            s.tap(3)
            if (i < 19) {
                assertTrue("the next card is on screen at once, before any provider call", s.displayed!!.predicted)
                assertEquals(s.name(s.fake.cards.values.toList()[i + 1]), s.shownName())
                assertEquals("nothing but the tap happened yet", i, s.fake.revlog.size)
            }
            s.pump()
        }
        assertTrue(s.finished); assertEquals(order, s.fake.revlog.map { it.cardId })
        assertEquals(19, s.p.nc.hits); assertEquals(0, s.p.nc.misses); assertEquals("only the last card has nothing to predict", 1, s.p.nc.noPrediction)
        assertTrue(s.outcomes.values.all { it.single() is AnswerOutcome.Saved })
        assertEquals(20, s.gameSteps.values.sum()); s.check()
    }

    @Test fun slowWritesWithTheUserFlippingAndTappingDuringTheWriteActOnlyAfterTheConfirm() {
        val s = sim(6)
        for (i in 1..6) s.fake.plan("answer", F.SLOW)
        s.fake.duringWrite = {
            val d = s.displayed
            if (d != null) { assertTrue("while a write runs the shown card is only predicted", d.predicted); s.flip(); s.tap(4) }
        }
        s.tap(3); s.pump() // each write runs slow; the tap held on the next card acts as soon as AnkiDroid confirmed that card
        assertEquals("every card answered once, in order, none ahead of its confirm", s.fake.cards.values.map { it.id }, s.fake.revlog.map { it.cardId })
        assertEquals(listOf(3, 4, 4, 4, 4, 4), s.made.map { it.ease })
        assertTrue(s.finished); assertEquals(5, s.heldShown)
        assertTrue("no answer ever acted on a predicted card", s.violations.isEmpty())
        assertEquals(6, s.timings.size); assertTrue(s.timings.all { it >= 900 })
        // the held tap's own next card was shown at once too (it was prepared while the tap was held)
        assertEquals("a predicted card after the first tap and after each held tap answer except the last card", 5, s.shows.count { it.startsWith("P:") })
        s.check()
    }

    @Test fun aHeldTapWritesNothingUntilTheCardIsConfirmed() {
        val s = sim(4)
        s.fake.plan("answer", F.SLOW)
        var duringHeld: List<Int>? = null
        s.fake.duringWrite = {
            s.tap(4) // held on card 2
            duringHeld = s.fake.revlog.map { it.ease }
            s.fake.duringWrite = null
        }
        s.tap(3); s.pump()
        assertEquals("only the first answer was in AnkiDroid while the write ran", emptyList<Int>(), duringHeld)
        assertEquals(listOf(3, 4), s.fake.revlog.map { it.ease })
        s.check()
    }

    // ---- predictions that miss

    @Test fun anAgainCardComingBackFirstSwapsToItAndDropsTheHeldTapWithNothingWrittenToThePredictedCard() {
        val s = sim(4) { it.againAt = 0 }
        val c1 = s.top(0); val c2 = s.top(1)
        s.fake.plan("answer", F.SLOW)
        s.fake.duringWrite = { s.flip(); s.tap(4); s.fake.duringWrite = null } // held on the predicted card 2
        s.tap(1); s.pump()
        assertEquals(listOf(c1.id), s.fake.revlog.map { it.cardId }); assertEquals(3, c2.reps)
        assertEquals(1, s.p.nc.misses); assertEquals(1, s.p.nc.heldDropped)
        assertEquals("the swap lands on the question side", "question", s.side)
        assertEquals(s.name(c1), s.shownName()); assertFalse(s.displayed!!.predicted)
        assertEquals(c1.key, s.displayed!!.key)
        s.tap(3); s.pump() // and the card can be answered again on its own
        assertEquals(listOf(c1.id, c1.id), s.fake.revlog.map { it.cardId })
        s.check()
    }

    @Test fun aSiblingBuriedByTheAnswerSwapsToTheRealNextCard() {
        val s = sim(3, ords = 2) { it.buryOnAnswer = true }
        val n1a = s.top(0); val n1b = s.top(1); val n2a = s.top(2)
        s.tap(3)
        assertEquals("the sibling is what was predicted", s.name(n1b), s.shownName())
        s.pump()
        assertEquals(n2a.key, s.displayed!!.key); assertFalse(s.displayed!!.predicted)
        assertEquals(1, s.p.nc.misses); assertEquals(-3, n1b.queue)
        assertEquals(listOf(n1a.id), s.fake.revlog.map { it.cardId })
        s.check()
    }

    @Test fun aLearningCardComingDueBeforeTheWriteSwapsToIt() {
        val s = sim(5) { f -> f.afterApply = { _, _ -> f.moveToTop(f.queueOf(deck).last()) } }
        val last = s.top(4)
        s.tap(3); s.pump()
        assertEquals(last.key, s.displayed!!.key); assertEquals(1, s.p.nc.misses)
        s.check()
    }

    @Test fun theLastCardOfTheDeckHasNoPredictionThenWaitsThenFinishes() {
        val s = sim(1)
        s.tap(3)
        assertNull("waiting, no card shown", s.displayed); assertTrue(s.shows.none { it.startsWith("P:") })
        assertEquals(1, s.p.nc.noPrediction)
        s.pump()
        assertTrue(s.finished); assertEquals(1, s.fake.revlog.size); s.check()
    }

    @Test fun anAnswerBeforeThePredictionIsPreparedWaitsForTheRealCardInstead() {
        val fake = StudySim.deck(deck, 3); fake.selected = 99
        val s = StudySim(fake, deck); s.boot()
        s.runOneIo(); s.pumpMain() // the first card is shown; the prepare of the second has not run yet
        assertNotNull(s.displayed)
        s.tap(3)
        assertNull(s.displayed); assertEquals(1, s.p.nc.noPrediction)
        s.pump()
        assertEquals(s.name(s.fake.queueOf(deck)[0]), s.shownName()); s.check()
    }

    // ---- write faults

    @Test fun aWriteThatThrowsBeforeApplyingThenWorksIsWrittenOnceAndTheNextCardOnlyConfirmsAfterIt() {
        val s = sim(3)
        s.fake.plan("answer", F.THROW_BEFORE)
        s.tap(3); s.pump()
        val m = s.made[0]
        assertEquals(2, (s.outcomeOf(m) as AnswerOutcome.Saved).attempts)
        assertEquals(listOf(1_000L), s.waiter.waits)
        assertTrue(s.outcomes[m.id]!!.first() is AnswerOutcome.Retrying)
        assertEquals(1, s.fake.revlog.size); assertEquals(1, s.gameSteps[m.id]); s.check()
        val t = s.timeline
        assertTrue("the next card is confirmed only after the write ended: $t", t.indexOf("end:${m.id}") in 0 until t.indexOf("confirm:c102/0*"))
    }

    @Test fun aWriteThatThrowsAfterApplyingIsReconciledAndWrittenOnce() {
        val s = sim(3)
        s.fake.plan("answer", F.THROW_AFTER)
        s.tap(3); s.pump()
        assertEquals(1, s.fake.count("answer")); assertEquals(1, s.fake.revlog.size)
        assertEquals(1, s.gameSteps[s.made[0].id]); s.check()
    }

    @Test fun aSwallowedWriteIsRetriedAtOnceTheSecondTimeWithoutAnyWait() {
        val s = sim(3)
        s.fake.plan("answer", F.SWALLOW)
        s.tap(3); s.pump()
        assertEquals(emptyList<Long>(), s.waiter.waits); assertEquals(2, s.fake.count("answer")); assertEquals(1, s.fake.revlog.size)
        s.check()
    }

    @Test fun aWriteSwallowedAlwaysBacksOffParksAndAnswersHeldTapsOnlyAfterRetryNowWorks() {
        val s = sim(4)
        for (i in 1..30) s.fake.plan("answer", F.SWALLOW)
        var parked = false
        s.waiter.script = { ms ->
            if (ms < 0 && !parked) {
                parked = true
                assertTrue("parked on the predicted card", s.displayed!!.predicted)
                s.flip(); s.tap(2) // held; nothing may be written
                assertEquals(0, s.fake.revlog.size); assertEquals(1, s.p.queue.size)
                s.fake.plans.clear() // AnkiDroid works again
                s.p.queue.retryNow()
                true
            } else false
        }
        s.tap(3); s.pump()
        assertTrue(parked)
        assertEquals(-1L, s.waiter.waits.last()); assertEquals(30_000L, s.waiter.waits[s.waiter.waits.size - 2])
        assertEquals(listOf(3, 2), s.made.take(2).map { it.ease }); assertEquals(s.made.take(2).map { it.cardId }, s.fake.revlog.take(2).map { it.cardId })
        s.check()
    }

    @Test fun aWriteThatNeverWorksIsNotLostItStaysQueuedAndJournaledWhileParked() {
        val s = sim(3)
        for (i in 1..200) s.fake.plan("answer", F.SWALLOW)
        var seenJournal: List<Long> = emptyList()
        s.waiter.script = { ms ->
            if (ms < 0) { seenJournal = AnswerJournal.decode(s.store.text).answers.map { it.seq }; s.p.queue.discard(s.made[0].seq) }
            ms < 0
        }
        s.tap(3); s.pump()
        assertEquals(listOf(s.made[0].seq), seenJournal)
        val o = s.outcomeOf(s.made[0]) as AnswerOutcome.NotWritten
        assertEquals(Reason.DISCARDED, o.reason); assertEquals(1, s.cancels.values.sum())
        assertEquals("the screen carries on after the user gives up", s.fake.queueOf(deck)[0].key, s.displayed!!.key)
        s.check()
    }

    @Test fun statsUnreadableRightAfterTheWriteIsReconciledNotRewritten() {
        val s = sim(3)
        s.fake.duringWrite = { s.fake.plan("stats", F.NULL); s.fake.duringWrite = null }
        s.tap(3); s.pump()
        assertEquals(1, s.fake.count("answer")); assertEquals(1, s.fake.revlog.size); s.check()
    }

    @Test fun selectOrTopFailingBeforeTheWriteRetriesThenWritesOnce() {
        val s = sim(3)
        s.fake.plan("select", F.THROW); s.fake.plan("top", F.THROW)
        s.tap(3); s.pump()
        assertEquals(1, s.fake.revlog.size); s.check()
    }

    // ---- not written, and told

    @Test fun aCardEditedElsewhereWhileQueuedIsChangedNotWrittenAndShownAgain() {
        val s = sim(4)
        val c1 = s.top(0)
        s.tap(3)
        s.fake.reschedule(c1)
        s.pump()
        val o = s.outcomeOf(s.made[0]) as AnswerOutcome.NotWritten
        assertEquals(Reason.CHANGED, o.reason); assertEquals(0, s.fake.revlog.size)
        assertEquals("the card comes back on the question side with its new state", c1.key, s.displayed!!.key)
        assertEquals("question", s.side); assertFalse(s.displayed!!.predicted)
        assertEquals(1, s.p.nc.misses)
        s.check()
    }

    @Test fun aCardAnsweredInAnkiDroidItselfWhileQueuedIsNotWrittenAgain() {
        val s = sim(4)
        val c1 = s.top(0); val c2 = s.top(1)
        s.tap(3)
        s.fake.externalAnswer(c1, 4)
        s.pump()
        assertEquals(Reason.NOT_TOP, (s.outcomeOf(s.made[0]) as AnswerOutcome.NotWritten).reason)
        assertEquals("the predicted card is the real top card after all", c2.key, s.displayed!!.key)
        assertEquals(1, s.fake.revlog.size); assertTrue(s.fake.revlog[0].external); assertEquals(1, s.p.nc.hits)
        s.check()
    }

    @Test fun aCardThatIsNotOnTopAnyMoreIsNeverWrittenAndTheRightCardIsShown() {
        val s = sim(4)
        val c1 = s.top(0); val c4 = s.top(3)
        s.tap(3)
        s.fake.moveToTop(c4) // a learning card came due while the user read card 1
        s.pump()
        assertEquals(Reason.NOT_TOP, (s.outcomeOf(s.made[0]) as AnswerOutcome.NotWritten).reason)
        assertEquals(0, s.fake.revlog.size); assertEquals(0, c1.reps - 3)
        assertEquals(c4.key, s.displayed!!.key)
        assertEquals(0, s.gameSteps.values.sum())
        s.check()
    }

    @Test fun repsMovedByTwoIsUnsureNeverRewrittenAndTold() {
        val s = sim(3) { f -> f.afterApply = { c, _ -> c.reps++; f.afterApply = null } }
        s.tap(3); s.pump()
        assertEquals(Reason.UNSURE, (s.outcomeOf(s.made[0]) as AnswerOutcome.NotWritten).reason)
        assertEquals(1, s.fake.count("answer")); s.check()
    }

    // ---- the process dies

    @Test fun diesBeforeTheWriteAppliedTheNewProcessWritesItOnceWithItsGameStep() {
        val s = sim(3)
        s.fake.plan("answer", F.DIE_BEFORE)
        s.tap(3); s.pump()
        assertEquals(1, s.deaths)
        assertEquals(1, s.fake.revlog.size); assertEquals("the dead process's call and the new one's", 2, s.fake.count("answer"))
        assertTrue(s.outcomeOf(s.made[0]) is AnswerOutcome.Saved); assertEquals(1, s.gameSteps[s.made[0].id])
        assertNotNull(s.displayed); assertFalse(s.displayed!!.predicted)
        s.check()
    }

    @Test fun diesAfterTheWriteAppliedTheNewProcessFindsItAlreadySavedAndDoesNotWriteOrCountItAgain() {
        val s = sim(3)
        s.fake.plan("answer", F.DIE_AFTER)
        s.tap(3); s.pump()
        assertEquals(1, s.deaths); assertEquals(1, s.fake.count("answer")); assertEquals(1, s.fake.revlog.size)
        assertTrue(s.outcomeOf(s.made[0]) is AnswerOutcome.AlreadySaved)
        assertNull("no game step: the new detector counted it", s.gameSteps[s.made[0].id]); assertEquals(1, s.detected[s.made[0].id])
        s.check()
    }

    @Test fun diesDuringTheBackOffWithTheEntryInTheJournal() {
        val s = sim(3)
        s.fake.plan("answer", F.THROW_BEFORE)
        var died = false
        s.waiter.script = { if (!died) { died = true; throw Die() }; false }
        s.tap(3); s.pump()
        assertEquals(1, s.deaths); assertEquals(1, s.fake.revlog.size); assertTrue(s.restoreProblems.isEmpty())
        assertEquals(1, s.gameSteps[s.made[0].id]); s.check()
    }

    @Test fun diesRightAfterTheTapBeforeTheWriteTaskRan() {
        val s = sim(3)
        s.tap(3)
        assertEquals(1, AnswerJournal.decode(s.store.text).answers.size)
        s.die(); s.pump()
        assertEquals(1, s.fake.revlog.size); assertEquals(1, s.gameSteps[s.made[0].id]); s.check()
    }

    @Test fun diesAgainWhileTheNewProcessReconcilesStillWritesItOnceAndNeverCountsItInProcess() {
        val s = sim(3)
        s.fake.plan("answer", F.DIE_AFTER)
        s.fake.plan("stats", F.NONE, F.DIE_BEFORE) // step 3 of the first process; the new process's first read dies too
        s.tap(3); s.pump()
        assertEquals(2, s.deaths); assertEquals(1, s.fake.count("answer")); assertEquals(1, s.fake.revlog.size)
        assertTrue(s.outcomeOf(s.made[0]) is AnswerOutcome.AlreadySaved); assertNull(s.gameSteps[s.made[0].id])
        s.check()
    }

    @Test fun twoRestoredEntriesAreWrittenOldestFirstBeforeTheFirstLoad() {
        val fake = StudySim.deck(deck, 4); fake.selected = 99
        val cs = fake.queueOf(deck).toList()
        val store = MemStore()
        fun entry(c: FakeAnki.Card, seq: Long) = QueuedAnswer(seq, deck, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 100 + seq, 1)
        store.text = AnswerJournal.encode(listOf(entry(cs[1], 8), entry(cs[0], 4)))
        val s = StudySim(fake, deck, store); s.boot(); s.pump()
        assertEquals(listOf(cs[0].id, cs[1].id), fake.revlog.map { it.cardId })
        assertEquals(cs[2].key, s.displayed!!.key); assertFalse(s.displayed!!.predicted)
        assertTrue(s.outcomes.values.all { it.last() is AnswerOutcome.Saved })
    }

    @Test fun aRestoredEntryForADeletedCardParksAndTheUserCanDiscardItWithoutBlockingForever() {
        val fake = StudySim.deck(deck, 3); fake.selected = 99
        val cs = fake.queueOf(deck).toList()
        val store = MemStore()
        store.text = AnswerJournal.encode(listOf(QueuedAnswer(3, deck, cs[0].nid, cs[0].ord, cs[0].id, fake.stats(cs[0].key)!!, 3, 100, 1)))
        fake.delete(cs[0])
        val s = StudySim(fake, deck, store)
        s.waiter.script = { ms -> if (ms < 0) s.p.queue.discard(3); ms < 0 }
        s.boot(); s.pump()
        val o = s.outcomes[100L]!!.last() as AnswerOutcome.NotWritten
        assertEquals(Reason.DISCARDED, o.reason)
        assertEquals("then the screen carries on with the first real card", cs[1].key, s.displayed!!.key)
        assertEquals(0, fake.revlog.size); assertEquals(0, AnswerJournal.decode(store.text).answers.size)
    }

    // ---- bad journals

    @Test fun emptyCorruptOldVersionAndPartlyBadJournalsNeverCrashAndStudyContinues() {
        for (bad in listOf("", "{{{{", "{\"v\":0,\"answers\":[{\"seq\":1}]}", "{\"v\":1,\"answers\":[{\"seq\":1,\"ease\":3}]}", "null")) {
            val fake = StudySim.deck(deck, 3); fake.selected = 99
            val store = MemStore().also { it.text = bad }
            val s = StudySim(fake, deck, store); s.boot(); s.pump()
            assertEquals(bad, 1, s.restoreProblems.size)
            assertNotNull(s.displayed); s.tap(3); s.pump()
            assertEquals(bad, 1, fake.revlog.size); s.check()
        }
    }

    // ---- the random mix

    private fun chaos(rng: Random, deathsLeft: IntArray): (String) -> F = { op ->
        val r = rng.nextInt(1000)
        fun die(f: F) = if (deathsLeft[0] > 0) { deathsLeft[0]--; f } else F.NONE
        when (op) {
            "answer" -> when {
                r < 200 -> F.SLOW; r < 250 -> F.THROW_BEFORE; r < 300 -> F.THROW_AFTER; r < 350 -> F.SWALLOW
                r < 365 -> die(F.DIE_BEFORE); r < 380 -> die(F.DIE_AFTER); else -> F.NONE
            }
            "stats" -> when { r < 40 -> F.NULL; r < 60 -> F.THROW; r < 66 -> die(F.DIE_BEFORE); r < 72 -> die(F.DIE_AFTER); r < 150 -> F.SLOW; else -> F.NONE }
            "top" -> when { r < 30 -> F.THROW; r < 35 -> die(F.DIE_AFTER); else -> F.NONE }
            else -> when { r < 30 -> F.THROW; r < 35 -> die(F.DIE_BEFORE); else -> F.NONE }
        }
    }

    private fun randomRun(seed: Int): StudySim {
        val rng = Random(seed)
        val ords = 1 + rng.nextInt(2)
        val fake = StudySim.deck(deck, 5 + rng.nextInt(10), ords)
        for (i in 1..3) fake.add(other, 900L + i)
        fake.selected = 99
        fake.againAt = listOf(null, 0, 1, 3)[rng.nextInt(4)]
        fake.buryOnAnswer = ords > 1 && rng.nextBoolean()
        fake.answerAnyTop = rng.nextBoolean()
        fake.topNeedsSelection = rng.nextBoolean()
        val learning = rng.nextInt(4) == 0
        fake.afterApply = { _, _ -> if (learning && rng.nextInt(4) == 0) fake.queueOf(deck).randomOrNull(rng)?.let { fake.moveToTop(it) } }
        fake.chaos = chaos(rng, intArrayOf(2))
        val s = StudySim(fake, deck)
        s.policy = StudySim.randomUser(rng)
        s.meddle = { sim ->
            if (rng.nextInt(100) < 4) {
                val busy = sim.p.queue.pending().map { it.cardId }.toSet()
                val free = fake.queueOf(deck).filter { it.id !in busy }
                when (rng.nextInt(4)) {
                    0 -> free.randomOrNull(rng)?.let { fake.reschedule(it) }
                    1 -> free.randomOrNull(rng)?.let { fake.externalAnswer(it, 3) }
                    2 -> fake.queueOf(deck).randomOrNull(rng)?.let { fake.moveToTop(it) }
                    else -> { fake.selected = other; sim.p.queue.invalidateSelection() } // another place selected a deck (never faulty: it is not the code under test)
                }
            }
        }
        s.boot(); s.run()
        return s
    }

    @Test fun twoHundredRandomSeededRunsMixingEveryFaultKeepEveryInvariant() {
        var deaths = 0; var answers = 0; var notWritten = 0; var misses = 0; var retries = 0
        for (seed in 1..200) {
            val s = try { randomRun(seed).also { it.check() } } catch (e: AssertionError) { throw AssertionError("seed $seed: ${e.message}", e) }
            assertTrue("seed $seed ended with the deck unfinished", s.finished && s.fake.queueOf(deck).isEmpty())
            assertEquals("seed $seed: nothing left queued", 0, s.p.queue.size)
            deaths += s.deaths; answers += s.made.size
            notWritten += s.outcomes.values.count { it.any { o -> o is AnswerOutcome.NotWritten } }
            misses += s.p.nc.misses; retries += s.outcomes.values.sumOf { it.count { o -> o is AnswerOutcome.Retrying } }
        }
        println("random runs: deaths=$deaths answers=$answers notWrittenAnswers=$notWritten predictionMisses=$misses retryReports=$retries")
        // the mix really exercised the hard paths (so a test that never fails cannot hide here)
        assertTrue("deaths $deaths", deaths >= 20); assertTrue("not written $notWritten", notWritten >= 15)
        assertTrue("misses $misses", misses >= 40); assertTrue("retries $retries", retries >= 100); assertTrue("answers $answers", answers >= 2000)
    }
}
