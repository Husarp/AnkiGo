package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class NextCardTest {
    private fun key(n: Int) = StudyLoop.Key(100L + n, 0)
    private fun card(n: Int, view: String = "c$n") = Prepared(
        key(n),
        mapOf("_id" to "$n", "note_id" to "${100 + n}", "deck_id" to "1", "reps" to "3", "due" to "5", "queue" to "2", "last_review_time_secs" to "9"),
        view,
    )
    private fun queue(vararg n: Int) = n.map { key(it) }

    private class Rig {
        var seq = 10L
        val nc = NextCard<String>(1, { ++seq }, { 4242L })
        fun start(first: Int = 1, vararg more: Int): List<Cmd<String>> { nc.reload(); return nc.loaded(1, cardOf(first), listOf(keyOf(first)) + more.map { keyOf(it) }) }
        fun keyOf(n: Int) = StudyLoop.Key(100L + n, 0)
        fun cardOf(n: Int) = Prepared(keyOf(n), mapOf("_id" to "$n", "note_id" to "${100 + n}", "deck_id" to "1", "reps" to "3", "due" to "5", "queue" to "2", "last_review_time_secs" to "9"), "c$n")
    }

    /** card 1 confirmed with card 2 prepared for it, token 1. */
    private fun ready(): Rig = Rig().also { it.start(1, 2, 3); it.nc.prepared(it.keyOf(1), 1, it.cardOf(2)) }

    // ---- loading

    @Test fun reloadAsksForTheTopCardWithNoPrediction() {
        val nc = NextCard<String>(1, { 1L })
        assertEquals(listOf<Cmd<String>>(Cmd.Load(1, null)), nc.reload())
        assertEquals(NextCard.Slot.Loading(1), nc.slot)
    }

    @Test fun loadedConfirmsTheCardAndAsksForTheOneAfterIt() {
        val r = Rig(); r.nc.reload()
        val cmds = r.nc.loaded(1, card(1), queue(1, 2, 3))
        assertEquals(listOf<Cmd<String>>(Cmd.Show(card(1), predicted = false), Cmd.Prepare(key(2), key(1), 1)), cmds)
        assertEquals(NextCard.Slot.Confirmed(card(1), 1), r.nc.slot)
        assertEquals(card(1), r.nc.actionCard())
    }

    @Test fun nothingToPrepareWhenTheQueueHasOneCardOrDisagrees() {
        val r = Rig(); r.nc.reload()
        assertEquals(listOf<Cmd<String>>(Cmd.Show(card(1), false)), r.nc.loaded(1, card(1), queue(1)))
        val r2 = Rig(); r2.nc.reload()
        assertEquals("a queue that does not start with the card is not trusted", listOf<Cmd<String>>(Cmd.Show(card(1), false)), r2.nc.loaded(1, card(1), queue(5, 2)))
    }

    @Test fun aResultForAnOldTokenChangesNothing() {
        val r = ready()
        val before = r.nc.slot
        assertTrue(r.nc.loaded(0, card(9), queue(9, 8)).isEmpty())
        assertTrue(r.nc.loaded(2, card(9), queue(9, 8)).isEmpty())
        assertTrue(r.nc.swapped(7, card(9), queue(9)).isEmpty())
        assertTrue(r.nc.confirmed(0, key(2), queue(2)).isEmpty())
        assertTrue(r.nc.finished(0).isEmpty()); assertTrue(r.nc.failed(0, "x").isEmpty())
        assertEquals(before, r.nc.slot)
    }

    @Test fun anOldResultAfterAnAnswerChangesNothing() {
        val r = ready(); r.nc.tapAnswer(3, 100) // token 2, PREDICTED(2)
        assertTrue(r.nc.loaded(1, card(7), queue(7)).isEmpty())
        assertTrue(r.nc.finished(1).isEmpty())
        assertTrue(r.nc.slot is NextCard.Slot.Predicted<*>)
    }

    @Test fun finishedAndFailedEndTheLoad() {
        val r = Rig(); r.nc.reload()
        assertEquals(listOf<Cmd<String>>(Cmd.ShowFinished), r.nc.finished(1)); assertEquals(NextCard.Slot.Finished, r.nc.slot)
        val r2 = Rig(); r2.nc.reload()
        assertEquals(listOf<Cmd<String>>(Cmd.ShowError("boom")), r2.nc.failed(1, "boom")); assertEquals(NextCard.Slot.Error("boom"), r2.nc.slot)
        assertEquals("a later reload recovers", listOf<Cmd<String>>(Cmd.Load(2, null)), r2.nc.reload())
    }

    @Test fun aFinishedOrFailedResultCannotReviveAConfirmedCard() {
        val r = ready()
        assertTrue(r.nc.finished(1).isEmpty()); assertTrue(r.nc.failed(1, "x").isEmpty())
        assertEquals(NextCard.Slot.Confirmed(card(1), 1), r.nc.slot)
    }

    // ---- the prediction cache

    @Test fun aPreparedCardIsKeptOnlyForItsConfirmedCardAndToken() {
        val r = Rig(); r.start(1, 2, 3)
        r.nc.prepared(key(5), 1, card(2)) // for another card
        r.nc.prepared(key(1), 9, card(2)) // for another load
        r.nc.prepared(key(1), 1, card(1)) // the card itself is no prediction
        assertEquals("nothing predicted: the answer waits", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
    }

    @Test fun aPreparedCardThatArrivesAfterTheAnswerIsDropped() {
        val r = Rig(); r.start(1, 2, 3)
        r.nc.tapAnswer(3, 1) // no prediction yet: LOADING(2)
        r.nc.prepared(key(1), 1, card(2)) // late
        assertEquals(NextCard.Slot.Loading(2), r.nc.slot)
        val r2 = Rig(); r2.nc.reload() // never confirmed
        r2.nc.prepared(key(1), 1, card(2))
        assertEquals(NextCard.Slot.Loading(1), r2.nc.slot)
    }

    @Test fun aPredictionIsUsedOnlyRightAfterTheCardItWasMadeFor() {
        val r = ready()
        r.nc.tapAnswer(3, 1) // uses it
        r.nc.confirmed(2, key(2), queue(2, 3)) // Y confirmed, Prepare(3) asked
        // no prepared() for 3 yet: answering Y has no prediction, the old one must not come back
        val cmds = r.nc.tapAnswer(3, 1)
        assertEquals(Cmd.ShowWaiting, cmds[1]); assertEquals(Cmd.Load(3, null), cmds[2])
    }

    @Test fun reloadForgetsThePrediction() {
        val r = ready(); r.nc.reload()
        r.nc.loaded(2, card(1), queue(1, 2)) // same card again, new token
        assertEquals("the old prediction was for token 1", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
    }

    // ---- answering

    @Test fun tapOnAConfirmedCardQueuesTheAnswerAndShowsThePredictedNextCardAtOnce() {
        val r = ready()
        val cmds = r.nc.tapAnswer(2, 777)
        val e = cmds[0] as Cmd.Enqueue
        assertEquals(11L, e.answer.seq); assertEquals(1L, e.answer.deckId); assertEquals(101L, e.answer.nid); assertEquals(0, e.answer.ord)
        assertEquals(1L, e.answer.cardId); assertEquals(card(1).before, e.answer.before); assertEquals(2, e.answer.ease)
        assertEquals(777L, e.answer.tookMs); assertEquals(4242L, e.answer.madeAt); assertFalse(e.answer.restored)
        assertEquals(Cmd.Show(card(2), predicted = true), cmds[1])
        assertEquals("the confirm is asked for after the write: token 2, predicted card 2", Cmd.Load(2, key(2)), cmds[2])
        assertEquals(NextCard.Slot.Predicted(card(2), 2, 11L), r.nc.slot)
        assertNull("no card action on a predicted card", r.nc.actionCard()); assertEquals(11L, r.nc.waitingFor())
    }

    @Test fun tapWithNothingPredictedShowsWaitingAndAsksForTheTopCard() {
        val r = Rig(); r.start(1, 2)
        val cmds = r.nc.tapAnswer(3, 5)
        assertTrue(cmds[0] is Cmd.Enqueue); assertEquals(Cmd.ShowWaiting, cmds[1]); assertEquals(Cmd.Load(2, null), cmds[2])
        assertEquals(NextCard.Slot.Loading(2), r.nc.slot); assertEquals(1, r.nc.noPrediction)
    }

    @Test fun anAnswerOnAPredictedCardIsOnlyHeldNeverQueued() {
        val r = ready(); r.nc.tapAnswer(3, 1)
        val held = r.nc.tapAnswer(4, 900)
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(4)), held)
        assertTrue("no second answer queued", held.none { it is Cmd.Enqueue })
        assertEquals(NextCard.Held(4, 900), (r.nc.slot as NextCard.Slot.Predicted<String>).held)
        assertTrue("a second tap while one is held is ignored", r.nc.tapAnswer(1, 1).isEmpty())
        assertEquals(4, ((r.nc.slot as NextCard.Slot.Predicted<String>).held)!!.ease)
    }

    @Test fun confirmedRunsTheHeldTapAsIfItWereTappedNowOnTheConfirmedCard() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        val cmds = r.nc.confirmed(2, key(2), queue(2, 3, 4), refresh = { "$it*" })
        assertEquals(Cmd.Confirm(card(2, "c2*")), cmds[0])
        val e = cmds[1] as Cmd.Enqueue
        assertEquals(12L, e.answer.seq); assertEquals(2L, e.answer.cardId); assertEquals(4, e.answer.ease); assertEquals(900L, e.answer.tookMs)
        assertEquals(card(2).before, e.answer.before)
        assertEquals(Cmd.ShowWaiting, cmds[2]); assertEquals(Cmd.Load(3, null), cmds[3])
        assertTrue(cmds.none { it is Cmd.Prepare })
        assertEquals(NextCard.Slot.Loading(3), r.nc.slot); assertEquals(1, r.nc.hits)
    }

    @Test fun confirmedWithoutAHeldTapRefreshesTheCardAndPreparesTheNext() {
        val r = ready(); r.nc.tapAnswer(3, 1)
        val cmds = r.nc.confirmed(2, key(2), queue(2, 3, 4), refresh = { "$it*" })
        assertEquals(listOf<Cmd<String>>(Cmd.Confirm(card(2, "c2*")), Cmd.Prepare(key(3), key(2), 2)), cmds)
        assertEquals(NextCard.Slot.Confirmed(card(2, "c2*"), 2), r.nc.slot); assertEquals(card(2, "c2*"), r.nc.actionCard())
        assertEquals(1, r.nc.hits); assertEquals(0, r.nc.misses)
    }

    @Test fun aNextCardPreparedWithTheConfirmBecomesThePredictionAtOnce() {
        val r = ready(); r.nc.tapAnswer(3, 1)
        val cmds = r.nc.confirmed(2, key(2), queue(2, 3, 4), next = card(3))
        assertEquals("no Prepare needed", listOf<Cmd<String>>(Cmd.Confirm(card(2))), cmds)
        val tap = r.nc.tapAnswer(3, 1)
        assertEquals(Cmd.Show(card(3), predicted = true), tap[1]); assertEquals(Cmd.Load(3, key(3)), tap[2])
    }

    @Test fun aHeldTapActingOnConfirmShowsItsOwnNextCardAtOnceWhenOneCameWithTheConfirm() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        val cmds = r.nc.confirmed(2, key(2), queue(2, 3, 4), next = card(3))
        assertTrue(cmds[1] is Cmd.Enqueue); assertEquals(Cmd.Show(card(3), predicted = true), cmds[2]); assertEquals(Cmd.Load(3, key(3)), cmds[3])
        assertEquals(NextCard.Slot.Predicted(card(3), 3, 12L), r.nc.slot)
    }

    @Test fun aNextCardThatIsNotTheOneAfterTheConfirmedCardIsIgnored() {
        val r = ready(); r.nc.tapAnswer(3, 1)
        val cmds = r.nc.confirmed(2, key(2), queue(2, 3, 4), next = card(9))
        assertEquals(listOf<Cmd<String>>(Cmd.Confirm(card(2)), Cmd.Prepare(key(3), key(2), 2)), cmds)
        assertEquals("and nothing is predicted", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
    }

    @Test fun aSwapLandsOnTheQuestionSideAndDropsTheHeldTapQuietly() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900) // held on card 2
        val cmds = r.nc.swapped(2, card(1), queue(1, 5, 6)) // Again: card 1 came back first
        assertEquals(Cmd.Show(card(1), predicted = false), cmds[0])
        assertEquals(Cmd.Prepare(key(5), key(1), 2), cmds[1])
        assertEquals(NextCard.Slot.Confirmed(card(1), 2), r.nc.slot)
        assertTrue(cmds.none { it is Cmd.Enqueue })
        assertEquals(1, r.nc.misses); assertEquals(1, r.nc.heldDropped); assertEquals(0, r.nc.hits)
    }

    @Test fun loadedWhileAPredictionShowsCountsAsASwapToo() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        r.nc.loaded(2, card(2), queue(2, 3)) // even the same card: no held tap may survive a swap
        assertEquals(NextCard.Slot.Confirmed(card(2), 2), r.nc.slot)
        assertEquals(1, r.nc.heldDropped)
    }

    @Test fun aConfirmForTheWrongCardNeverActsItReloads() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        val cmds = r.nc.confirmed(2, key(9), queue(9, 2))
        assertEquals(listOf<Cmd<String>>(Cmd.Load(3, null)), cmds)
        assertEquals(NextCard.Slot.Loading(3), r.nc.slot); assertEquals(1, r.nc.heldDropped)
    }

    @Test fun finishedOrFailedWhilePredictedDropsTheHeldTap() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        assertEquals(listOf<Cmd<String>>(Cmd.ShowFinished), r.nc.finished(2)); assertEquals(1, r.nc.heldDropped)
        val r2 = ready(); r2.nc.tapAnswer(3, 1); r2.nc.tapAnswer(4, 900)
        r2.nc.failed(2, "x"); assertEquals(1, r2.nc.heldDropped); assertNull(r2.nc.actionCard())
    }

    @Test fun reloadWhilePredictedDropsTheHeldTapAndTheNextConfirmIsIgnored() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900)
        assertEquals(listOf<Cmd<String>>(Cmd.Load(3, null)), r.nc.reload())
        assertEquals(1, r.nc.heldDropped)
        assertTrue("the confirm of the old load comes back late", r.nc.confirmed(2, key(2), queue(2)).isEmpty())
        assertEquals(NextCard.Slot.Loading(3), r.nc.slot)
    }

    // ---- the screen is closing (R9 wave 6 FIX, Codex 4)

    @Test fun leaveDropsTheHeldTapAndALateConfirmationQueuesNothing() {
        val r = ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(4, 900) // card 2 predicted (token 2), a tap held on it
        assertEquals(3, r.nc.leave())
        assertEquals(1, r.nc.heldDropped); assertNull(r.nc.actionCard())
        assertEquals(NextCard.Slot.Loading(3), r.nc.slot)
        val late = r.nc.confirmed(2, key(2), queue(2, 3), next = card(3)) // the confirmation was posted to the main thread before the user left
        assertTrue("no answer is queued after the user left", late.isEmpty())
        assertEquals(NextCard.Slot.Loading(3), r.nc.slot)
    }

    @Test fun afterLeaveEveryResultOfAnEarlierLoadChangesNothing() {
        val r = ready(); r.nc.tapAnswer(3, 1)
        val t = r.nc.leave()
        for (old in 0 until t) {
            assertTrue(r.nc.loaded(old, card(9), queue(9, 8)).isEmpty())
            assertTrue(r.nc.swapped(old, card(9), queue(9)).isEmpty())
            assertTrue(r.nc.confirmed(old, key(2), queue(2, 3)).isEmpty())
            assertTrue(r.nc.finished(old).isEmpty()); assertTrue(r.nc.failed(old, "x").isEmpty())
            r.nc.prepared(key(1), old, card(2))
        }
        assertTrue(r.nc.tapAnswer(3, 1).isEmpty())
        assertEquals(NextCard.Slot.Loading(t), r.nc.slot)
    }

    @Test fun leaveFromAConfirmedCardForgetsThePredictionToo() {
        val r = ready() // card 1 confirmed with card 2 prepared for it
        val t = r.nc.leave()
        assertNull(r.nc.actionCard())
        assertTrue("nothing is shown, so nothing can be answered", r.nc.tapAnswer(3, 1).isEmpty())
        assertTrue(r.nc.loaded(t - 1, card(1), queue(1, 2)).isEmpty())
        assertEquals(0, r.nc.heldDropped)
    }

    @Test fun tapsAreIgnoredWhereNoCardIsShown() {
        val r = Rig(); r.nc.reload()
        assertTrue(r.nc.tapAnswer(3, 1).isEmpty())
        r.nc.finished(1); assertTrue(r.nc.tapAnswer(3, 1).isEmpty())
        val r2 = Rig(); r2.nc.reload(); r2.nc.failed(1, "x"); assertTrue(r2.nc.tapAnswer(3, 1).isEmpty())
        assertTrue(ready().nc.tapAnswer(0, 1).isEmpty()); assertTrue(ready().nc.tapAnswer(5, 1).isEmpty())
    }

    @Test fun cardActionsOnlyOnAConfirmedCard() {
        val r = Rig(); r.nc.reload(); assertNull(r.nc.actionCard())
        r.nc.loaded(1, card(1), queue(1, 2)); assertNotNull(r.nc.actionCard())
        r.nc.tapAnswer(3, 1); assertNull(r.nc.actionCard()) // loading
        r.nc.finished(2); assertNull(r.nc.actionCard())
    }

    @Test fun aCardWhoseStateCannotBeReadIsRefusedNotQueued() {
        val r = Rig(); r.nc.reload()
        r.nc.loaded(1, Prepared(key(1), mapOf("_id" to "1"), "c1"), queue(1, 2)) // no reps
        val cmds = r.nc.tapAnswer(3, 1)
        assertEquals(1, cmds.size); assertTrue(cmds[0] is Cmd.Refuse)
        assertTrue("and the card stays as it was", r.nc.slot is NextCard.Slot.Confirmed<*>)
        assertEquals(10L, r.seq)
    }

    @Test fun sequenceNumbersCountUpAcrossAnswers() {
        val r = Rig(); r.start(1, 2, 3)
        val a1 = (r.nc.tapAnswer(3, 1)[0] as Cmd.Enqueue).answer.seq
        r.nc.loaded(2, card(2), queue(2, 3))
        val a2 = (r.nc.tapAnswer(3, 1)[0] as Cmd.Enqueue).answer.seq
        assertEquals(listOf(11L, 12L), listOf(a1, a2))
    }

    // ---- the io side's decision

    @Test fun loadPlanFinishedConfirmOrFetch() {
        assertEquals(LoadPlan.Step.Finished, LoadPlan.decide(emptyList(), key(1)))
        assertEquals(LoadPlan.Step.Finished, LoadPlan.decide(emptyList(), null))
        assertEquals(LoadPlan.Step.Confirm(key(2)), LoadPlan.decide(queue(2, 3), key(2)))
        assertEquals("the top card is not the predicted one", LoadPlan.Step.Fetch(key(1)), LoadPlan.decide(queue(1, 2), key(2)))
        assertEquals("the predicted card is only second", LoadPlan.Step.Fetch(key(1)), LoadPlan.decide(queue(1, 2, 3), key(2)))
        assertEquals(LoadPlan.Step.Fetch(key(4)), LoadPlan.decide(queue(4), null))
    }

    // ---- fuzz: whatever order events arrive in, no answer acts on an unconfirmed card or the wrong one

    @Test fun randomEventOrdersNeverAnswerAnUnconfirmedOrWrongCard() {
        for (seed in 1..300) {
            val rng = Random(seed)
            var seq = 0L
            val nc = NextCard<String>(1, { ++seq })
            var shown: Pair<StudyLoop.Key, Boolean>? = null // key, confirmed
            var tokenAsked = 0
            fun run(cmds: List<Cmd<String>>) {
                for (c in cmds) when (c) {
                    is Cmd.Show -> shown = c.card.key to !c.predicted
                    is Cmd.Confirm -> shown = c.card.key to true
                    Cmd.ShowWaiting, Cmd.ShowFinished, is Cmd.ShowError -> shown = null
                    is Cmd.Load -> tokenAsked = c.token
                    is Cmd.Enqueue -> {
                        val s = shown
                        assertTrue("seed $seed: answer acted on ${s}", s != null && s.second && s.first == c.answer.key)
                    }
                    else -> {}
                }
            }
            run(nc.reload())
            repeat(120) {
                val t = (tokenAsked - 1 + rng.nextInt(3)).coerceAtLeast(0) // mostly current, sometimes old or future
                val n = 1 + rng.nextInt(6)
                val q = (0 until 1 + rng.nextInt(3)).map { key(n + it) }
                when (rng.nextInt(9)) {
                    0 -> run(nc.loaded(t, card(n), q))
                    1 -> run(nc.swapped(t, card(n), q))
                    2 -> run(nc.confirmed(t, key(1 + rng.nextInt(6)), q))
                    3 -> run(nc.finished(t))
                    4 -> run(nc.failed(t, "x"))
                    5 -> nc.prepared(key(1 + rng.nextInt(6)), t, card(n))
                    6, 7 -> run(nc.tapAnswer(1 + rng.nextInt(4), 10))
                    8 -> if (rng.nextInt(4) == 0) run(nc.reload())
                }
            }
        }
    }
}
