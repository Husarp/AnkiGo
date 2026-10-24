package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

private fun key(n: Int) = StudyLoop.Key(100L + n, 0)
private fun card(n: Int, learning: PredictRule.LearnRead? = null) = Prepared(
    key(n), mapOf("_id" to "$n", "note_id" to "${100 + n}", "deck_id" to "1", "reps" to "3", "due" to "5", "queue" to "2", "last_review_time_secs" to "9"), "c$n", learning,
)

/**
 * R18-A (278): the reducer lets the user flip and answer a PREDICTED card while the write of the answer before it runs. The cards after
 * the confirmed one are prepared ahead (queue order); a tap on a predicted card is answered at once, behind the earlier write, when the card
 * after it is prepared and safe to show and fewer than [NextCard.maxAhead] answers are ahead; else it is held, as before.
 */
class R18AAheadTest {
    private fun queue(vararg n: Int) = n.map { key(it) }

    private class Rig(val ahead: Int = NextCard.AHEAD, why: (Prepared<String>, Prepared<String>, Long) -> PredictRule.Why = { _, _, _ -> PredictRule.Why.SAFE }) {
        var seq = 10L
        var clock = 4242L
        val asked = ArrayList<Triple<Int, Int, Long>>()
        val nc = NextCard<String>(1, { ++seq }, { clock }, maxAhead = ahead) { a, n, now -> asked += Triple(a.key.nid.toInt() - 100, n.key.nid.toInt() - 100, now); why(a, n, now) }
        fun start(vararg more: Int) = nc.reload().let { nc.loaded(1, card(1), listOf(key(1)) + more.map { key(it) }) }
        /** card 1 confirmed (token 1), cards 2, 3, 4 prepared behind it. */
        fun ready(prepare: Int = 3): Rig { start(2, 3, 4, 5); for (n in 2..1 + prepare) nc.prepared(key(n - 1), 1, card(n)); return this }
    }

    // ---- what is prepared

    @Test fun aConfirmedCardAsksForTheCardsAfterItInQueueOrder() {
        val r = Rig(); r.nc.reload()
        val cmds = r.nc.loaded(1, card(1), queue(1, 2, 3, 4))
        assertEquals(listOf<Cmd<String>>(Cmd.Show(card(1), false), Cmd.Prepare(key(2), key(1), 1), Cmd.Prepare(key(3), key(2), 1), Cmd.Prepare(key(4), key(3), 1)), cmds)
        val short = Rig(); short.nc.reload()
        assertEquals("only as far as the queue reads", 2, short.nc.loaded(1, card(1), queue(1, 2)).size)
        val off = Rig(ahead = 0); off.nc.reload()
        assertEquals("ahead 0 is the old behaviour: one card", listOf<Cmd<String>>(Cmd.Show(card(1), false), Cmd.Prepare(key(2), key(1), 1)), off.nc.loaded(1, card(1), queue(1, 2, 3, 4)))
    }

    @Test fun preparedCardsAreKeptInOrderOnlyForTheConfirmedCardAndAreBounded() {
        val r = Rig(); r.start(2, 3, 4, 5)
        r.nc.prepared(key(2), 1, card(3)) // not the card after the confirmed one yet
        r.nc.prepared(key(1), 1, card(2)); r.nc.prepared(key(2), 1, card(3)); r.nc.prepared(key(3), 1, card(4))
        r.nc.prepared(key(4), 1, card(5)) // one too many (ahead 2 + 1)
        r.nc.prepared(key(3), 9, card(4)) // for another load
        val t = r.nc.tapAnswer(3, 1); val u = r.nc.tapAnswer(3, 1); val v = r.nc.tapAnswer(3, 1)
        assertEquals(Cmd.Show(card(2), true), t[1]); assertEquals(Cmd.Show(card(3), true), u[1]); assertEquals(Cmd.Show(card(4), true), v[1])
        assertEquals("the fourth tap has no card to show: held", listOf<Cmd<String>>(Cmd.ShowHeld(3)), r.nc.tapAnswer(3, 1))
    }

    @Test fun aGapInThePreparedCardsEndsTheChain() {
        val r = Rig(); r.start(2, 3, 4)
        r.nc.prepared(key(1), 1, card(2)); r.nc.prepared(key(3), 1, card(4)) // card 3 failed to prepare
        r.nc.tapAnswer(3, 1)
        assertEquals("card 3 is not prepared, so the tap waits", listOf<Cmd<String>>(Cmd.ShowHeld(3)), r.nc.tapAnswer(3, 1))
    }

    // ---- answering ahead

    @Test fun aTapOnAPredictedCardIsAnsweredAtOnceBehindTheEarlierWrite() {
        val r = Rig().ready()
        val first = r.nc.tapAnswer(3, 100)
        assertEquals(Cmd.Show(card(2), true), first[1]); assertEquals(Cmd.Load(2, key(2)), first[2])
        val second = r.nc.tapAnswer(4, 700)
        val e = second[0] as Cmd.Enqueue
        assertEquals(12L, e.answer.seq); assertEquals(102L, e.answer.nid); assertEquals(2L, e.answer.cardId); assertEquals(4, e.answer.ease); assertEquals(700L, e.answer.tookMs)
        assertEquals("the card after it shows at once", Cmd.Show(card(3), true), second[1])
        assertEquals("only the last answer's load confirms", Cmd.Load(3, key(3)), second[2])
        assertEquals(NextCard.Slot.Predicted(card(3), 3, 12L), r.nc.slot)
        assertEquals(1, r.nc.answeredAhead); assertEquals(0, r.nc.heldDropped)
        assertTrue("the earlier load's result is stale now", r.nc.confirmed(2, key(2), queue(2, 3, 4)).isEmpty())
        assertEquals(NextCard.Slot.Predicted(card(3), 3, 12L), r.nc.slot)
    }

    @Test fun twoAnswersAheadThenTheThirdTapIsHeldUntilTheConfirm() {
        val r = Rig().ready()
        r.nc.tapAnswer(3, 1)                       // on the confirmed card 1
        val a = r.nc.tapAnswer(3, 1)               // ahead 1, on card 2
        val b = r.nc.tapAnswer(3, 1)               // ahead 2, on card 3
        assertTrue(a[0] is Cmd.Enqueue && b[0] is Cmd.Enqueue)
        assertEquals(Cmd.Show(card(4), true), b[1])
        val held = r.nc.tapAnswer(2, 55)           // on card 4: nothing may go ahead of 2 answers
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(2)), held)
        assertEquals(2, r.nc.answeredAhead)
        val done = r.nc.confirmed(4, key(4), queue(4, 5, 6, 7), refresh = { "$it*" })
        assertEquals(Cmd.Confirm(card(4).copy(view = "c4*")), done[0])
        val enq = done[1] as Cmd.Enqueue
        assertEquals("the held tap acts once, on the confirmed card", 4L, enq.answer.cardId); assertEquals(2, enq.answer.ease)
        assertEquals("numbered after the three before it", 14L, enq.answer.seq)
    }

    @Test fun anAnswerAheadIsOnlyMadeWhenTheCardAfterItIsSafeToShow() {
        var why = PredictRule.Why.SAFE
        val r = Rig(why = { _, _, _ -> why }).ready()
        r.nc.tapAnswer(3, 1)
        why = PredictRule.Why.LEARNING_DUE // a learning card would come first after the next answer
        val seqBefore = r.seq
        val held = r.nc.tapAnswer(3, 1)
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(3)), held)
        assertEquals("nothing was made: no sequence number used, no answer counted", seqBefore, r.seq)
        assertEquals(0, r.nc.answeredAhead); assertTrue(r.nc.withheld.isEmpty())
    }

    @Test fun aLearningCardIsNotShownAheadAfterAnAnswerMadeAheadButIsAfterAnAnswerOnAConfirmedCard() {
        // end of the deck: with no main-queue card left the cards answered before come back right after the next learning card, so the card read
        // behind a learning card may not be where the guess says. Only a main-queue card is shown ahead of an answer that was itself ahead.
        fun learning(n: Int) = card(n).let { it.copy(before = it.before + ("queue" to "1") + ("due" to "9")) }
        val r = Rig(); r.start(2, 3, 4, 5)
        r.nc.prepared(key(1), 1, card(2)); r.nc.prepared(key(2), 1, learning(3)); r.nc.prepared(key(3), 1, card(4))
        val first = r.nc.tapAnswer(3, 1)
        assertEquals("on the confirmed card: card 2 (main queue) shows ahead", Cmd.Show(card(2), true), first[1])
        assertEquals("on card 2, answered ahead: card 3 is a learning card, so the tap waits for the confirm", listOf<Cmd<String>>(Cmd.ShowHeld(3)), r.nc.tapAnswer(3, 1))
        assertEquals(0, r.nc.answeredAhead)
        // a learning card read right after a CONFIRMED card still shows ahead (R10-X1 proved it for one answer)
        val r2 = Rig(); r2.start(2, 3)
        r2.nc.prepared(key(1), 1, learning(2))
        assertEquals(Cmd.Show(learning(2), true), r2.nc.tapAnswer(3, 1)[1])
        assertTrue(PredictRule.aheadOk(card(5))); assertFalse(PredictRule.aheadOk(learning(5)))
        assertFalse("a filtered deck's preview repeat is intraday too", PredictRule.aheadOk(card(5).let { it.copy(before = it.before + ("queue" to "4")) }))
        assertTrue("an interday learning card is part of the main queue", PredictRule.aheadOk(card(5).let { it.copy(before = it.before + ("queue" to "3")) }))
    }

    @Test fun theRuleSeesALaterTimeForEachWriteStillAhead() {
        val r = Rig().ready()
        r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1)
        assertEquals(listOf(Triple(1, 2, 4242L), Triple(2, 3, 4242L + PredictRule.SLACK_MS), Triple(3, 4, 4242L + 2 * PredictRule.SLACK_MS)), r.asked)
    }

    @Test fun cardsAnsweredEarlierInTheRunAreNotLearningCardsThatAreDue() {
        // card 1 was a learning card due 10 s ago when the cards were read; answering it moved it. Answering card 2 next must not wait for it.
        val read = PredictRule.LearnRead(4_242_000L - 3_000L, listOf(PredictRule.Due(1, 4_242L - 10)))
        val rule: (Prepared<String>, Prepared<String>, Long) -> PredictRule.Why = { a, n, now -> PredictRule.check(a, n, now) }
        val r = Rig(why = rule); r.clock = 4_242_000L
        r.start(2, 3, 4)
        r.nc.prepared(key(1), 1, card(2, read)); r.nc.prepared(key(2), 1, card(3, read)); r.nc.prepared(key(3), 1, card(4, read))
        r.nc.tapAnswer(3, 1)
        val second = r.nc.tapAnswer(3, 1)
        assertEquals("card 1 is answered: it is not what comes first", Cmd.Show(card(3, read), true), second[1])
        // without the filter the rule alone would hold it back
        assertEquals(PredictRule.Why.LEARNING_DUE, PredictRule.check(card(2, read), card(3, read), r.clock))
    }

    @Test fun aSwapAfterAnswersAheadShowsTheRealCardsQuestionAndNothingIsHeld() {
        val r = Rig().ready()
        r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1)         // card 3 shown ahead, token 3
        val cmds = r.nc.swapped(3, card(8), queue(8, 3, 4))  // a learning card came due: AnkiDroid's first card is 8
        assertEquals(Cmd.Show(card(8), predicted = false), cmds[0])
        assertEquals(NextCard.Slot.Confirmed(card(8), 3), r.nc.slot)
        assertEquals(1, r.nc.misses); assertEquals(0, r.nc.hits)
        assertEquals("a card after a swap is prepared from the fresh read", listOf<Cmd<String>>(Cmd.Prepare(key(3), key(8), 3), Cmd.Prepare(key(4), key(3), 3)), cmds.drop(1))
    }

    @Test fun theConfirmOfTheLastAnswerAheadFreshensTheChain() {
        val r = Rig().ready()
        r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1)
        val cmds = r.nc.confirmed(3, key(3), queue(3, 4, 5, 6), refresh = { "$it*" })
        assertEquals(Cmd.Confirm(card(3).copy(view = "c3*")), cmds[0])
        assertEquals("the cards after it are read again, not trusted from before", listOf<Cmd<String>>(Cmd.Prepare(key(4), key(3), 3), Cmd.Prepare(key(5), key(4), 3), Cmd.Prepare(key(6), key(5), 3)), cmds.drop(1))
        assertEquals(1, r.nc.hits); assertTrue(r.nc.actionCard() != null)
        // and the run of answers starts again from nothing
        assertEquals(Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
    }

    @Test fun reloadOrLeavingForgetsTheChainAndTheCount() {
        val r = Rig().ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1)
        r.nc.reload()
        r.nc.loaded(4, card(7), queue(7, 8, 9))
        r.nc.prepared(key(7), 4, card(8)); r.nc.prepared(key(8), 4, card(9))
        val a = r.nc.tapAnswer(3, 1); val b = r.nc.tapAnswer(3, 1); val c = r.nc.tapAnswer(3, 1)
        assertTrue(a[0] is Cmd.Enqueue && b[0] is Cmd.Enqueue)
        assertEquals("a fresh count: the third tap has no card prepared", listOf<Cmd<String>>(Cmd.ShowHeld(3)), c)
        val t = r.nc.leave()
        assertTrue(r.nc.tapAnswer(3, 1).isEmpty()); assertTrue(r.nc.confirmed(t - 1, key(9), queue(9)).isEmpty())
    }

    @Test fun theTallyNamesTheAnswersMadeBeforeTheConfirm() {
        val r = Rig().ready(); r.nc.tapAnswer(3, 1); r.nc.tapAnswer(3, 1)
        assertTrue(r.nc.tally(), r.nc.tally().contains("answered before the confirm 1"))
        assertFalse(Rig().ready().nc.tally().contains("before the confirm"))
    }

    // ---- fuzz: whatever order the results arrive in, an answer acts on the card on screen, once, in order, and never more than the cap ahead

    @Test fun randomEventOrdersNeverAnswerTheWrongCardOrMoreThanTheCapAhead() {
        for (seed in 1..400) {
            val rng = Random(seed)
            var seq = 0L
            val nc = NextCard<String>(1, { ++seq }, maxAhead = 2) { _, _, _ -> if (rng.nextInt(5) == 0) PredictRule.Why.LEARNING_DUE else PredictRule.Why.SAFE }
            var shown: Pair<StudyLoop.Key, Boolean>? = null // key, confirmed
            var tokenAsked = 0
            var run = 0 // answers since a confirmed card was on screen
            var lastSeq = 0L
            fun go(cmds: List<Cmd<String>>) {
                for (c in cmds) when (c) {
                    is Cmd.Show -> { shown = c.card.key to !c.predicted; if (!c.predicted) run = 0 }
                    is Cmd.Confirm -> { shown = c.card.key to true; run = 0 }
                    Cmd.ShowWaiting, Cmd.ShowFinished, is Cmd.ShowError -> shown = null
                    is Cmd.Load -> tokenAsked = c.token
                    is Cmd.Enqueue -> {
                        val s = shown
                        assertTrue("seed $seed: answer acted on $s", s != null && s.first == c.answer.key)
                        if (!s!!.second) assertTrue("seed $seed: ${run} answers ahead of the confirm", run <= 2)
                        assertTrue("seed $seed: sequence numbers rise", c.answer.seq > lastSeq); lastSeq = c.answer.seq
                        run++
                    }
                    else -> {}
                }
            }
            go(nc.reload())
            repeat(150) {
                val t = (tokenAsked - 1 + rng.nextInt(3)).coerceAtLeast(0)
                val n = 1 + rng.nextInt(7)
                val q = (0 until 1 + rng.nextInt(5)).map { key(n + it) }
                when (rng.nextInt(10)) {
                    0 -> go(nc.loaded(t, card(n), q))
                    1 -> go(nc.swapped(t, card(n), q))
                    2 -> go(nc.confirmed(t, key(1 + rng.nextInt(7)), q))
                    3 -> go(nc.finished(t))
                    4 -> go(nc.failed(t, "x"))
                    5, 6 -> { val k = 1 + rng.nextInt(7); nc.prepared(key(k), t, card(k + 1 + rng.nextInt(2))) }
                    7, 8 -> go(nc.tapAnswer(1 + rng.nextInt(4), 10))
                    9 -> if (rng.nextInt(4) == 0) go(nc.reload())
                }
            }
        }
    }
}
