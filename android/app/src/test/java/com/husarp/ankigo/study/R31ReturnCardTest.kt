package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

/** Return-only refreshes use the same scheduler/state checks with fast and deliberately slow fake providers. */
class R31ReturnCardTest {
    private fun prepared(fake: FakeAnki, key: StudyLoop.Key) = Prepared(key, fake.stats(key)!!, "c${key.nid}/${key.ord}")
    private fun read(fake: FakeAnki): SchedulerCardRead.Result<StudyLoop.Key, String> {
        fun queue(): List<StudyLoop.Key> {
            val due = fake.cards.values.filter { it.queue in 1..4 }.map {
                mapOf("note_id" to "${it.nid}", "ord" to "${it.ord}", "queue" to "${it.queue}")
            }
            return DueOrder.prioritize(fake.top(1, 4), due, { fake.top(1, 100) }) { it }
        }
        return SchedulerCardRead.confirmed(queue(), ::queue, { it }, { prepared(fake, it) }, { p, _ -> p },
            valid = { it.before == fake.stats(it.key) })
    }
    private fun newSession(slow: Boolean = false): StudySim {
        val fake = FakeAnki()
        fake.add(1, 101).apply { queue = 0; reps = 0; last = null }
        return StudySim(fake, 1, maxAhead = NextCard.AHEAD, tryNotTop = true).also {
            it.boot(); it.pump()
            if (slow) fake.chaos = { op -> if (op == "top" || op == "stats") F.SLOW else F.NONE }
        }
    }
    private fun apply(sim: StudySim, ticket: NextCard.ReturnCheck, read: SchedulerCardRead.Result<StudyLoop.Key, String>, revealed: Boolean = false): List<Cmd<String>> =
        sim.p.nc.returned(ticket, read.card, read.queue, revealed).also { sim.cmds(it) }

    private fun dueFirst(slow: Boolean) {
        for (queue in 1..4) {
            val sim = newSession(slow)
            val nc = sim.p.nc
            val new = sim.fake.card(StudyLoop.Key(101, 0))!!
            val before = sim.fake.stats(new.key)
            val original = nc.slot
            val ticket = nc.returnCheck(false)!!
            val due = sim.fake.add(1, 202, atTop = true).apply { this.queue = queue }
            sim.fake.ticker = { _, _ -> assertEquals("read keeps the NEW question usable", original, nc.slot) }
            val at = sim.fake.clock.ms
            val read = read(sim.fake)
            sim.fake.ticker = null
            if (slow) assertTrue(sim.fake.clock.ms - at >= 4 * sim.fake.slowMs)
            val commands = apply(sim, ticket, read)
            assertEquals(due.key, commands.filterIsInstance<Cmd.Show<String>>().single().card.key)
            assertTrue(commands.none { it is Cmd.Enqueue || it is Cmd.ShowHeld || it is Cmd.Load || it == Cmd.ShowWaiting })
            assertEquals(before, sim.fake.stats(new.key))
            assertEquals(listOf(due.key, new.key), sim.fake.top(1, 4)) // no provider queue mutation
            assertEquals(0, sim.fake.count("answer"))
            assertTrue(sim.store.history.isEmpty()); assertTrue(sim.made.isEmpty()); assertTrue(sim.gameSteps.isEmpty())
            sim.pump() // prepare the original new card behind the due card
            sim.tap(3); sim.pump()
            assertEquals(new.key, sim.displayed!!.key)
            assertEquals(before, sim.fake.stats(new.key))
            assertEquals(due.id, sim.fake.revlog.single().cardId)
            assertEquals(1, sim.gameSteps.values.sum())
            sim.check()
        }
    }

    @Test fun fastProviderShowsEveryDueQueueKindBeforeNewWithoutAnsweringNew() = dueFirst(false)
    @Test fun slowProviderShowsEveryDueQueueKindBeforeNewWithoutAnsweringNew() = dueFirst(true)

    @Test fun anotherNewCardOrAnEmptyQueueKeepsTheOriginalQuestion() {
        for (empty in listOf(false, true)) {
            val sim = newSession()
            val nc = sim.p.nc; val original = nc.slot; val ticket = nc.returnCheck(false)!!
            sim.fake.queues[1]!!.clear()
            if (!empty) sim.fake.add(1, 202).queue = 0
            assertTrue(apply(sim, ticket, read(sim.fake)).isEmpty())
            assertEquals(original, nc.slot); assertTrue(sim.made.isEmpty())
        }
    }

    @Test fun aReturnNeverChecksLearningReviewOrOtherNonNewQueues() {
        for (queue in listOf(-3, -2, -1, 1, 2, 3, 4)) {
            val sim = newSession()
            val key = sim.displayed!!.key
            sim.fake.card(key)!!.queue = queue
            sim.reload(); sim.pump()
            assertNull("queue $queue", sim.p.nc.returnCheck(false))
        }
    }

    @Test fun revealedNewCardIsRetainedIncludingWhenItsAnswerWasHiddenAgain() {
        val sim = newSession(); val original = sim.p.nc.slot
        sim.flip()
        assertNull(sim.p.nc.returnCheck(true))
        sim.side = "question" // tap-to-hide does not erase the fact that the answer was seen
        assertNull(sim.p.nc.returnCheck(true))
        assertEquals(original, sim.p.nc.slot)
    }

    @Test fun revealingDuringASlowReturnReadRejectsTheResult() {
        val sim = newSession(slow = true); val nc = sim.p.nc; val original = nc.slot
        val ticket = nc.returnCheck(false)!!
        sim.fake.add(1, 202)
        sim.fake.ticker = { _, _ -> sim.flip() }
        val read = read(sim.fake)
        assertEquals("answer", sim.side)
        assertTrue(apply(sim, ticket, read, revealed = true).isEmpty())
        assertEquals(original, nc.slot); assertTrue(sim.made.isEmpty())
    }

    @Test fun answeringDuringASlowReturnReadRejectsTheResultAndKeepsTheExactTap() {
        val sim = newSession(slow = true); val nc = sim.p.nc; val ticket = nc.returnCheck(false)!!
        sim.fake.add(1, 202)
        var tapped = false
        sim.fake.ticker = { _, _ -> if (!tapped) { tapped = true; sim.tap(4) } }
        val read = read(sim.fake)
        assertTrue(apply(sim, ticket, read).isEmpty())
        assertEquals(101L, sim.made.single().nid); assertEquals(4, sim.made.single().ease)
        sim.fake.ticker = null; sim.pump(); sim.check()
    }

    @Test fun leavingOrReloadingDuringASlowReadInvalidatesItsCardToken() {
        for (reload in listOf(false, true)) {
            val sim = newSession(slow = true); val nc = sim.p.nc; val ticket = nc.returnCheck(false)!!
            sim.fake.add(1, 202)
            var left = false
            sim.fake.ticker = { _, _ -> if (!left) { left = true; if (reload) nc.reload() else nc.leave() } }
            assertTrue(apply(sim, ticket, read(sim.fake)).isEmpty())
            assertTrue(nc.slot is NextCard.Slot.Loading); assertTrue(sim.made.isEmpty())
        }
    }

    @Test fun successfulReturnInvalidatesOldLoadsPreparationsAndRepeatedResults() {
        val sim = newSession(); val nc = sim.p.nc; val ticket = nc.returnCheck(false)!!
        val due = sim.fake.add(1, 202)
        val read = read(sim.fake)
        apply(sim, ticket, read)
        val shown = nc.slot
        assertTrue(nc.loaded(ticket.token, prepared(sim.fake, ticket.key), listOf(ticket.key)).isEmpty())
        assertTrue(nc.confirmed(ticket.token, ticket.key, listOf(ticket.key)).isEmpty())
        nc.prepared(due.key, ticket.token, prepared(sim.fake, ticket.key))
        assertTrue(apply(sim, ticket, read).isEmpty())
        assertEquals(shown, nc.slot)
    }

    @Test fun predictedNewCardCanYieldAfterThePriorWriteWithoutWritingNewOrChangingItsGame() {
        val fake = FakeAnki()
        val previous = fake.add(1, 100)
        val new = fake.add(1, 101).apply { queue = 0; reps = 0 }
        val sim = StudySim(fake, 1, maxAhead = NextCard.AHEAD, tryNotTop = true)
        sim.boot(); sim.pump(); sim.tap(3)
        val ticket = sim.p.nc.returnCheck(false)!!
        assertTrue(sim.p.nc.slot is NextCard.Slot.Predicted)
        fake.afterApply = { card, _ -> if (card == previous) fake.add(1, 202).queue = 1 }
        // The return check runs on the same FIFO after the prior answer and its original confirmation.
        sim.p.io += { val read = read(fake); sim.p.main += { apply(sim, ticket, read) } }
        sim.pump()
        assertEquals(202L, sim.displayed!!.key.nid)
        assertEquals(previous.id, fake.revlog.single().cardId)
        assertEquals(1, sim.gameSteps.values.sum()); assertEquals(1, sim.made.size)
        assertEquals(0, new.reps); assertTrue(fake.queueOf(1).contains(new))
        sim.check()
    }

    @Test fun heldAnswerOnPredictedNewIsNeverEligibleForReturnReplacement() {
        val fake = FakeAnki(); fake.add(1, 100); fake.add(1, 101).queue = 0
        val sim = StudySim(fake, 1, maxAhead = NextCard.AHEAD)
        sim.boot(); sim.pump(); sim.tap(3); sim.tap(4) // no prepared card after new: held
        assertNotNull((sim.p.nc.slot as NextCard.Slot.Predicted).held)
        assertNull(sim.p.nc.returnCheck(false))
    }

    @Test fun dueCardArrivingWhileVisibleKeepsNewUntilAnExplicitReturnCheck() {
        val fake = FakeAnki(); val previous = fake.add(1, 100)
        val new = fake.add(1, 101).apply { queue = 0; reps = 0 }
        val sim = StudySim(fake, 1, maxAhead = NextCard.AHEAD, tryNotTop = true)
        sim.boot(); sim.pump(); sim.tap(3)
        fake.afterApply = { card, _ -> if (card == previous) fake.add(1, 202, atTop = true).queue = 1 }
        sim.pump() // a late ordinary queue read sees due first, while the screen has stayed visible
        assertEquals(new.key, sim.displayed!!.key)
        assertFalse((sim.p.nc.slot as NextCard.Slot.Confirmed).onTop)
        assertEquals(0, new.reps)
        val ticket = sim.p.nc.returnCheck(false)!!
        apply(sim, ticket, read(fake))
        assertEquals(202L, sim.displayed!!.key.nid)
        assertEquals(previous.id, fake.revlog.single().cardId)
        assertEquals(1, sim.gameSteps.values.sum())
        sim.check()
    }

    @Test fun unconfirmedOrUndueCandidateCannotReplaceNew() {
        val sim = newSession(); val nc = sim.p.nc; val ticket = nc.returnCheck(false)!!
        val due = sim.fake.add(1, 202)
        val p = prepared(sim.fake, due.key)
        for (queue in listOf(-3, -1, 0)) assertTrue(nc.returned(ticket, p.copy(before = p.before + ("queue" to "$queue")), listOf(due.key), false).isEmpty())
        assertTrue(nc.returned(ticket, p, listOf(ticket.key, due.key), false).isEmpty())
        assertTrue(nc.returned(ticket, null, emptyList(), false).isEmpty())
        assertEquals(ticket.key, sim.displayed!!.key)
    }

    @Test fun dueCardBecomingUnscheduledDuringSlowPreparationIsNeverShown() {
        val sim = newSession(slow = true); val nc = sim.p.nc; val ticket = nc.returnCheck(false)!!
        val due = sim.fake.add(1, 202)
        sim.fake.chaos = { op ->
            if (op == "stats") sim.fake.queues[1]!!.remove(due)
            F.SLOW
        }
        assertTrue(apply(sim, ticket, read(sim.fake)).isEmpty())
        assertEquals(ticket.key, sim.displayed!!.key)
    }

    @Test fun failedReturnReadKeepsTheQuestionAndDoesNotQueueAnyAnswer() {
        val sim = newSession(); val nc = sim.p.nc; val original = nc.slot; val ticket = nc.returnCheck(false)!!
        sim.fake.add(1, 202)
        sim.fake.plan("top", F.THROW)
        val read = runCatching { read(sim.fake) }.getOrNull()
        assertNull(read)
        assertTrue(nc.returned(ticket, read?.card, read?.queue.orEmpty(), false).isEmpty())
        assertEquals(original, nc.slot)
        assertTrue(sim.made.isEmpty()); assertEquals(0, sim.fake.count("answer"))
    }
}
