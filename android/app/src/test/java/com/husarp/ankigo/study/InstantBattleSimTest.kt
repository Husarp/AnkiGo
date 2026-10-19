package com.husarp.ankigo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R10-B23: the battle happens at the tap; only the write to AnkiDroid may be late. Every scenario ends with [InstantSim.check]:
 * each written answer counted exactly once (its game step, or the catch-up when the step died unsaved), never a step twice.
 */
class InstantBattleSimTest {
    private fun sim() = InstantSim()

    @Test fun theStepRunsAtTheTapBeforeAnyWriteAndTheSavedOutcomeDoesNotCountAgain() {
        val s = sim(); s.boot(detectorFirst = false)
        val a = s.tap()!!
        assertEquals("counted on the tap, before the io thread ran at all", 1, s.p.game.steps[a.id])
        assertEquals(0, s.fake.revlog.size)
        assertTrue("the saved map already holds the card one rep further", com.husarp.ankigo.live.OwnAhead.counted(com.husarp.ankigo.CardState.from(a.before)!!, s.p.game.snapshot))
        s.settle()
        assertTrue(s.outcomes[a.id] is AnswerOutcome.Saved)
        s.p.poll()
        assertEquals(1, s.p.game.steps[a.id]); assertEquals(0, s.p.game.detected.values.sum())
        s.check()
    }

    @Test fun aSlowWriteDoesNotDelayTheStepsOfTheAnswersBeforeIt() {
        val s = sim(); s.boot(detectorFirst = false)
        s.fake.plan("answer", F.SLOW)
        val a = s.tap()!!
        assertEquals(1, s.p.game.steps[a.id])
        s.settle()
        val b = s.tap()!!
        assertEquals(1, s.p.game.steps[b.id]); assertEquals(1, s.fake.revlog.size)
        s.settle(); s.check()
    }

    @Test fun aPollThatSeesTheWriteBeforeItIsSettledCountsNothing() {
        val s = sim(); s.boot(detectorFirst = false)
        val a = s.tap()!!
        s.p.poll()
        while (s.fake.revlog.isEmpty()) s.io() // the write landed; its outcome waits for the main thread
        s.p.poll(); s.p.poll()
        assertEquals(0, s.p.game.detected.values.sum())
        s.settle(); s.p.poll()
        assertEquals(1, s.p.game.steps[a.id]); s.check()
    }

    @Test fun answersMadeInAnkiDroidStillCountThroughTheDetector() {
        val s = sim(); s.boot(detectorFirst = false)
        val a = s.tap()!!
        val other = s.fake.queueOf(s.deck).last()
        s.fake.externalAnswer(other, 3) // answered in AnkiDroid itself while our write is pending
        s.p.poll()
        assertEquals(1, s.p.game.detected[other.id])
        s.settle(); s.p.poll()
        assertEquals(1, s.p.game.steps[a.id])
        s.check(external = mapOf(other.id to 1))
    }

    @Test fun aRestartCountsAnAnswerMadeInAnkiDroidOnceButNeverTheOwnOneAgain() {
        val s = sim(); s.boot(detectorFirst = false)
        s.tap()!!; s.settle(); s.save()
        val other = s.fake.queueOf(s.deck).last()
        s.fake.externalAnswer(other, 3) // AnkiGo was closed
        s.die(); s.settle(); s.p.poll()
        assertEquals(1, s.p.game.detected[other.id])
        s.check(external = mapOf(other.id to 1))
    }

    @Test fun anAnswerThatEndsNotWrittenKeepsItsStepAndLeavesTheSavedMap() {
        val s = sim(); s.boot(detectorFirst = false)
        val a = s.tap()!!
        s.fake.moveToTop(s.fake.queueOf(s.deck).last()) // a learning card came due: NOT_TOP
        s.settle()
        assertEquals(AnswerOutcome.Reason.NOT_TOP, (s.outcomes[a.id] as AnswerOutcome.NotWritten).reason)
        assertEquals("the battle is never undone", 1, s.p.game.steps[a.id])
        val before = com.husarp.ankigo.CardState.from(a.before)!!
        assertTrue("the card is not one rep further in the saved map any more", !com.husarp.ankigo.live.OwnAhead.counted(before, s.p.game.snapshot))
        s.fake.externalAnswer(s.fake.cards[a.cardId]!!, 3) // later answered in AnkiDroid: that one counts
        s.p.poll()
        assertEquals(1, s.p.game.detected[a.cardId])
        s.check(external = mapOf(a.cardId to 1))
    }

    @Test fun notWrittenWithoutADetectorTakesTheRowBackOutOfTheSavedMap() {
        val s = sim() // no detector in this process (LiveSession's null-detector branches)
        val a = s.tap(holdSession = false)!!
        s.fake.moveToTop(s.fake.queueOf(s.deck).last())
        s.settle()
        assertTrue(!com.husarp.ankigo.live.OwnAhead.counted(com.husarp.ankigo.CardState.from(a.before)!!, s.p.game.snapshot))
        s.check()
    }

    // ---- killed after the tap: every interleaving of the game save and the write, the detector before / after the replay's decision

    private fun killed(detectorFirst: Boolean, script: (InstantSim) -> Unit): InstantSim {
        val s = sim(); s.boot(detectorFirst = false)
        script(s)
        s.settle(detectorFirst); s.save()
        s.die(detectorFirst) // one more restart: what the saves hold must still count everything once
        s.settle(detectorFirst); s.p.poll(); s.save()
        s.check()
        assertEquals("written once", 1, s.fake.revlog.size)
        return s
    }

    @Test fun killedBeforeTheSaveAndBeforeTheWrite() = both { first ->
        val s = killed(first) { it.tap(); it.die(first) }
        assertEquals("the replay gave it its step", 1, s.p.game.steps[s.made[0].id])
    }

    @Test fun killedAfterTheSaveBeforeTheWrite() = both { first ->
        val s = killed(first) { it.tap(); it.save(); it.die(first) }
        assertEquals("the save's step, not another", 1, s.p.game.steps[s.made[0].id])
    }

    @Test fun killedAfterTheWriteBeforeTheSave() = both { first ->
        killed(first) { it.fake.plan("answer", F.DIE_AFTER); it.tap() } // the step and the write, then death: the step is lost
    }

    @Test fun killedAfterTheSaveAndTheWrite() = both { first ->
        val s = killed(first) { it.fake.plan("answer", F.DIE_AFTER); it.tap(); it.save() }
        assertEquals(1, s.p.game.steps[s.made[0].id])
        assertTrue(s.outcomes[s.made[0].id] is AnswerOutcome.AlreadySaved)
    }

    @Test fun killedAfterTheJournalLetItGoBeforeAnySave() = both { first ->
        val s = killed(first) { it.tap(); while (AnswerJournal.decode(it.journal.text).answers.isNotEmpty()) it.io(); it.die(first) }
        assertEquals("the step died unsaved: the catch-up counts it", 1, s.p.game.detected[s.made[0].cardId])
    }

    @Test fun killedAfterTheSettleBeforeItsSave() = both { first ->
        val s = killed(first) { it.tap(); it.save(); it.settle(); it.die(first) }
        assertEquals(1, s.p.game.steps[s.made[0].id]); assertEquals(0, s.p.game.detected.values.sum())
    }

    @Test fun killedWhileTheReconcileOfTheReplayRuns() = both { first ->
        killed(first) { it.fake.plan("answer", F.DIE_AFTER); it.fake.plan("stats", F.NONE, F.DIE_BEFORE); it.tap() } // the replay's first read dies too
    }

    @Test fun theReplayWaitsForTheGameToLoadBeforeAnythingIsWritten() {
        val s = sim(); s.boot(detectorFirst = false)
        s.tap(); s.die(detectorFirst = false, holdRestore = true)
        while (s.p.io.tasks.size > 1) s.io()
        assertEquals(0, s.fake.count("answer"))
        assertEquals(1, s.p.io.tasks.size)
        s.p.heldRestore!!.invoke() // the game loaded and decided
        s.settle(); s.p.poll()
        assertEquals(1, s.fake.revlog.size); s.check()
    }

    private fun both(test: (Boolean) -> Unit) { test(false); test(true) }

    // ---- random: faults, deaths and saves anywhere

    @Test fun twoHundredRandomRunsWithDeathsAnywhere() {
        val seen = HashMap<String, Int>() // what the runs went through, so this test can fail for want of coverage too
        for (seed in 1..200) {
            val rng = Random(seed)
            val s = InstantSim(cards = 6)
            if (rng.nextInt(4) == 0) s.fake.againAt = rng.nextInt(3) // Again cards come back: several answers on one card
            s.boot(detectorFirst = rng.nextBoolean(), detector = rng.nextBoolean())
            var deathsLeft = 4
            s.fake.chaos = { op ->
                val r = rng.nextInt(100)
                when {
                    op == "answer" && r < 6 && deathsLeft > 0 -> { deathsLeft--; if (rng.nextBoolean()) F.DIE_BEFORE else F.DIE_AFTER }
                    op == "answer" && r < 12 -> F.THROW_AFTER
                    op == "answer" && r < 18 -> F.THROW_BEFORE
                    op == "answer" && r < 22 -> F.SWALLOW
                    op == "stats" && r < 5 -> F.NULL
                    op == "stats" && r < 7 && deathsLeft > 0 -> { deathsLeft--; F.DIE_BEFORE }
                    op == "top" && r < 4 -> F.THROW
                    else -> F.NONE
                }
            }
            try {
                var steps = 0
                while (steps++ < 400 && s.made.size < 12) {
                    val first = rng.nextBoolean()
                    when (rng.nextInt(100)) {
                        in 0..29 -> s.io(first)
                        in 30..54 -> s.p.main.poll()?.run()
                        in 55..69 -> s.tap(listOf(1, 2, 3, 3, 4)[rng.nextInt(5)])
                        in 70..79 -> s.p.poll()
                        in 80..91 -> s.save()
                        in 92..94 -> if (deathsLeft > 0) { deathsLeft--; s.die(first, detector = rng.nextBoolean()) }
                        in 95..97 -> s.p.startDetector()
                        else -> if (s.made.isNotEmpty() && rng.nextInt(3) == 0) s.fake.queueOf(s.deck).lastOrNull()?.let { s.fake.moveToTop(it) } // NOT_TOP
                    }
                }
                s.fake.chaos = null; s.fake.plans.clear()
                s.settle(rng.nextBoolean())
                s.die(rng.nextBoolean()); s.settle(); s.p.poll()
                s.check()
                for (t in s.trace) for (k in listOf("step now", "already counted", "died on the io thread")) if (t.contains(k)) seen.merge(k, 1, Int::plus)
                for (o in s.outcomes.values) seen.merge(o.javaClass.simpleName, 1, Int::plus)
                if (s.made.any { s.written(it) == 1 && (s.p.game.steps[it.id] ?: 0) == 0 }) seen.merge("caught up", 1, Int::plus)
            } catch (e: AssertionError) {
                throw AssertionError("seed $seed: ${e.message}", e)
            }
        }
        for (k in listOf("step now", "already counted", "died on the io thread", "Saved", "AlreadySaved", "NotWritten", "caught up")) assertTrue("never: $k ($seen)", (seen[k] ?: 0) > 0)
    }
}
