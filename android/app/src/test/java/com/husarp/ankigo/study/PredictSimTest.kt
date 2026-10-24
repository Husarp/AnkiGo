package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * R10-X1: the reducer and [PredictRule] against a small model of AnkiDroid's v3 queue (Anki rslib `scheduler/queue`): intraday
 * learning cards due by the cutoff, then the main queue, then learning cards within the learn-ahead limit; the cutoff moves only when an
 * answer is applied (here: a random write delay after the tap, as in a lag phase); a requeued learning card goes after the next learning
 * card when the main queue is empty. Random sessions of new and review cards with learning steps, random think times.
 */
class PredictSimTest {
    private class Model(var nowMs: Long) {
        class C(val id: Long, var queue: Int, var due: Long = 0, var reps: Int = 0, var step: Int = 0)
        val main = ArrayList<C>() // new and review cards, in the order the queue was built
        val learning = ArrayList<C>() // intraday learning cards
        var cutoff = nowMs / 1000
        val ahead = 1200L

        val nowSecs get() = nowMs / 1000

        fun order(): List<C> = learning.filter { it.due <= cutoff }.sortedBy { it.due } + main +
            learning.filter { it.due > cutoff && it.due <= cutoff + ahead }.sortedBy { it.due }

        fun byKey(k: StudyLoop.Key) = (main + learning).first { it.id == k.nid }

        /** The answer applied at [nowMs]: AnkiDroid only answers its top card. */
        fun answer(c: C, ease: Int) {
            assertSame("only the top card is answered", order().first(), c)
            main.remove(c); learning.remove(c)
            c.reps++
            val steps = listOf(60L, 600L)
            val secs: Long? = when (c.queue) {
                0, 1 -> when (ease) { 1 -> { c.step = 0; steps[0] }; 2 -> steps[c.step]; 3 -> if (c.step + 1 < steps.size) steps[++c.step] else null; else -> null }
                else -> if (ease == 1) { c.step = 0; 600L } else null // a lapse relearns; else it is gone until another day
            }
            if (secs != null) {
                c.queue = 1; c.due = nowSecs + secs
                val aheadCutoff = cutoff + ahead // the old cutoff: requeue first, then move it
                val next = learning.minByOrNull { it.due }
                if (c.due <= aheadCutoff && main.isEmpty() && next != null && next.due >= c.due && next.due + 1 < aheadCutoff) c.due = next.due + 1
                learning += c
            } else c.queue = 2
            cutoff = nowSecs
        }

        fun prepared(c: C) = Prepared(
            StudyLoop.Key(c.id, 0),
            mapOf("_id" to "${c.id}", "note_id" to "${c.id}", "deck_id" to "1", "reps" to "${c.reps}", "queue" to "${c.queue}", "due" to "${c.due}"),
            "c${c.id}",
            PredictRule.LearnRead(nowMs, learning.map { PredictRule.Due(it.id, it.due) }),
        )
    }

    private class Run(val answers: Int, val predicted: Int, val swaps: Int, val waits: Int, val ahead: Int = 0, val wrongWrites: Int = 0)

    /**
     * [ahead] (R18-A, 278) > 0: the user answers the shown-ahead card before the write of the answer before it ran (taps come faster than
     * the writes, which apply one after the other, each [0, 4.5 s] after the previous). A write for a card that is not first is NOT made
     * (AnkiDroid only answers its first card; the queue's check refuses it): it is counted in [Run.wrongWrites].
     */
    private fun session(seed: Int, rule: Boolean, ahead: Int = 0): Run {
        val rng = Random(seed)
        val m = Model(1_700_000_000_000L)
        var id = 0L
        repeat(10 + rng.nextInt(20)) { m.main += Model.C(++id, if (rng.nextInt(3) == 0) 2 else 0) }
        val nc = if (rule) NextCard<String>(1, { ++id }, { m.nowMs }, maxAhead = ahead, check = PredictRule::check) else NextCard<String>(1, { ++id }, { m.nowMs }, maxAhead = ahead)
        class W(val key: StudyLoop.Key, val ease: Int, val tapAt: Long)
        val writes = ArrayDeque<W>()
        var ioFreeAt = 0L // answers ahead: the writes run one after the other; this is when the last one ended
        val io = ArrayDeque<() -> Unit>()
        var answers = 0
        var wrongWrites = 0
        fun cmds(list: List<Cmd<String>>) {
            for (c in list) when (c) {
                is Cmd.Enqueue -> { writes += W(c.answer.key, c.answer.ease, m.nowMs); answers++ }
                is Cmd.Load -> io += {
                    if (ahead == 0) m.nowMs += rng.nextLong(0, 4_500) // the write runs (a slow one in a lag phase): AnkiDroid moves its cutoff when it applies it
                    while (writes.isNotEmpty()) {
                        val w = writes.removeFirst()
                        val card = m.byKey(w.key)
                        if (ahead == 0) { m.answer(card, w.ease); continue }
                        // answers ahead: the write starts when the one before it ended (or at the tap) and ends up to 4.5 s later, whatever the user did meanwhile
                        ioFreeAt = maxOf(ioFreeAt, w.tapAt) + rng.nextLong(0, 4_500)
                        val now = m.nowMs
                        m.nowMs = ioFreeAt
                        if (m.order().first() !== card) wrongWrites++ else m.answer(card, w.ease)
                        m.nowMs = maxOf(now, ioFreeAt)
                    }
                    val q = m.order().take(maxOf(3, ahead + 2))
                    val keys = q.map { StudyLoop.Key(it.id, 0) }
                    cmds(when (val step = LoadPlan.decide(keys, c.predicted)) {
                        LoadPlan.Step.Finished -> nc.finished(c.token)
                        is LoadPlan.Step.Confirm -> nc.confirmed(c.token, step.key, keys)
                        is LoadPlan.Step.Fetch -> if (c.predicted != null) nc.swapped(c.token, m.prepared(q[0]), keys) else nc.loaded(c.token, m.prepared(q[0]), keys)
                    })
                }
                is Cmd.Prepare -> io += { nc.prepared(c.forKey, c.forToken, m.prepared(m.byKey(c.key))) }
                else -> {}
            }
        }
        cmds(nc.reload())
        var guard = 0
        while (guard++ < 4000) {
            val fast = ahead > 0 && nc.slot is NextCard.Slot.Predicted<*> && rng.nextInt(100) < 70 // the next tap comes before any write ran
            if (!fast) while (io.isNotEmpty()) io.removeFirst()()
            if (nc.slot is NextCard.Slot.Finished) break
            m.nowMs += if (fast) rng.nextLong(100, 1_500) else rng.nextLong(1_500, 40_000) // the user reads the card: learning cards come due meanwhile
            cmds(nc.tapAnswer(listOf(1, 1, 2, 3, 3, 3, 4)[rng.nextInt(7)], 1000))
        }
        assertTrue("seed $seed: the deck is finished", nc.slot is NextCard.Slot.Finished)
        return Run(answers, nc.predicted, nc.misses, nc.withheld.values.sum() + nc.noPrediction, nc.answeredAhead, wrongWrites)
    }

    @Test fun withoutTheRuleLearningCardsComingDueSwapThePredictionWithItThereIsNoSwap() {
        var before = 0; var after = 0; var answers = 0; var predicted = 0; var waits = 0
        for (seed in 1..200) {
            before += session(seed, rule = false).swaps
            val r = session(seed, rule = true)
            after += r.swaps; answers += r.answers; predicted += r.predicted; waits += r.waits
        }
        println("v3 model: swaps without the rule $before, with it $after; answers $answers, predicted $predicted, waited $waits")
        assertTrue("the model reproduces the swap: $before", before > 100)
        assertEquals("no swap with the rule", 0, after)
        assertTrue("most answers still show their next card at once: $predicted of $answers", predicted * 2 > answers)
    }

    @Test fun answeringAheadOfTheWritesKeepsTheRuleSafeNoSwapAndNoWriteToTheWrongCard() {
        var before = 0; var beforeWrong = 0; var swaps = 0; var wrong = 0; var answers = 0; var ahead = 0
        for (seed in 1..200) {
            val off = session(seed, rule = false, ahead = NextCard.AHEAD)
            before += off.swaps; beforeWrong += off.wrongWrites
            val r = session(seed, rule = true, ahead = NextCard.AHEAD)
            swaps += r.swaps; wrong += r.wrongWrites; answers += r.answers; ahead += r.ahead
        }
        println("v3 model, answers ahead: without the rule swaps $before and wrong-card writes $beforeWrong; with it swaps $swaps, wrong-card writes $wrong; answers $answers, made before the confirm $ahead")
        assertTrue("the model reproduces the wrong guesses without the rule: $beforeWrong", beforeWrong > 20)
        assertEquals("no wrong-card write with the rule", 0, wrong)
        assertEquals("no swap with the rule", 0, swaps)
        assertTrue("answers really were made ahead of the confirm: $ahead of $answers", ahead * 10 > answers)
    }
}
