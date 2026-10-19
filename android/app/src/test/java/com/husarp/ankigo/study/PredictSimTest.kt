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

    private class Run(val answers: Int, val predicted: Int, val swaps: Int, val waits: Int)

    private fun session(seed: Int, rule: Boolean): Run {
        val rng = Random(seed)
        val m = Model(1_700_000_000_000L)
        var id = 0L
        repeat(10 + rng.nextInt(20)) { m.main += Model.C(++id, if (rng.nextInt(3) == 0) 2 else 0) }
        val nc = if (rule) NextCard<String>(1, { ++id }, { m.nowMs }, PredictRule::check) else NextCard<String>(1, { ++id }, { m.nowMs })
        val writes = ArrayDeque<Pair<StudyLoop.Key, Int>>()
        val io = ArrayDeque<() -> Unit>()
        var answers = 0
        fun cmds(list: List<Cmd<String>>) {
            for (c in list) when (c) {
                is Cmd.Enqueue -> { writes += c.answer.key to c.answer.ease; answers++ }
                is Cmd.Load -> io += {
                    m.nowMs += rng.nextLong(0, 4_500) // the write runs (a slow one in a lag phase): AnkiDroid moves its cutoff when it applies it
                    while (writes.isNotEmpty()) { val (k, e) = writes.removeFirst(); m.answer(m.byKey(k), e) }
                    val q = m.order().take(3)
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
        while (guard++ < 2000) {
            while (io.isNotEmpty()) io.removeFirst()()
            if (nc.slot is NextCard.Slot.Finished) break
            m.nowMs += rng.nextLong(1_500, 40_000) // the user reads the card: learning cards come due meanwhile
            cmds(nc.tapAnswer(listOf(1, 1, 2, 3, 3, 3, 4)[rng.nextInt(7)], 1000))
        }
        assertTrue("seed $seed: the deck is finished", nc.slot is NextCard.Slot.Finished)
        return Run(answers, nc.predicted, nc.misses, nc.withheld.values.sum() + nc.noPrediction)
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
}
