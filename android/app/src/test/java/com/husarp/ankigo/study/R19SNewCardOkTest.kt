package com.husarp.ankigo.study

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule

/** PLAN 283 / R19: the shared answer gate has no remembered gentle showing. */
class R19SNewCardOkTest {
    @get:Rule val paparazzi = Paparazzi()
    private fun card(id: Int, queue: Int) = Prepared(
        StudyLoop.Key(id.toLong(), 0),
        mapOf("_id" to "$id", "note_id" to "$id", "deck_id" to "1", "reps" to "0",
            "due" to "5", "queue" to "$queue", "last_review_time_secs" to "9"), "card$id",
    )

    private fun decision(a: QueuedAnswer) = NewCardOk.settle(true, a.before["queue"]?.toIntOrNull(), 4, a.ease)

    @Test fun allSharedGateInputsUseOkOnlyWhileNew() {
        // Buttons, keys, swipes, timer and JavaScript all reach answerMade via NextCard.tapAnswer.
        for (ease in 1..4) {
            val nc = NextCard<String>(1, { 1L }, { 4242L })
            nc.reload()
            val new = card(1, 0)
            nc.loaded(1, new, listOf(new.key))
            val first = nc.tapAnswer(ease, 100).filterIsInstance<Cmd.Enqueue>().single().answer
            assertTrue(NewCardOk.rowApplies(true, 0))
            assertEquals(NewCardOk.resolve(true, 4, ease), decision(first))
            for (queue in listOf(1, 3, 1, 2)) {
                val learning = card(1, queue)
                val load = nc.reload().filterIsInstance<Cmd.Load>().single()
                nc.loaded(load.token, learning, listOf(learning.key))
                val next = nc.tapAnswer(ease, 100).filterIsInstance<Cmd.Enqueue>().single().answer
                assertFalse(NewCardOk.rowApplies(true, queue))
                assertEquals(NewCardOk.Resolved(ease, false), decision(next))
            }
        }
    }

    @Test fun predictedLearningHeldUntilConfirmUsesNormalAnswer() {
        val nc = NextCard<String>(1, { 1L }, { 4242L }, maxAhead = 0)
        val first = card(1, 0); val learning = card(2, 1)
        nc.reload(); nc.loaded(1, first, listOf(first.key, learning.key))
        nc.prepared(first.key, 1, learning)
        assertTrue(nc.tapAnswer(1, 100).any { it is Cmd.Show && it.predicted })
        assertEquals(listOf(Cmd.ShowHeld(1)), nc.tapAnswer(1, 100))
        val answer = nc.confirmed(2, learning.key, listOf(learning.key))
            .filterIsInstance<Cmd.Enqueue>().single().answer
        assertEquals(NewCardOk.Resolved(1, false), decision(answer))
    }

    @Test fun predictedNewCardAnsweredAheadStillGetsItsFirstOk() {
        val nc = NextCard<String>(1, { 1L }, { 4242L }, maxAhead = 2)
        val first = card(1, 2); val new = card(2, 0); val third = card(3, 2)
        nc.reload(); nc.loaded(1, first, listOf(first.key, new.key, third.key))
        nc.prepared(first.key, 1, new); nc.prepared(new.key, 1, third)
        nc.tapAnswer(3, 100)
        val answer = nc.tapAnswer(2, 100).filterIsInstance<Cmd.Enqueue>().single().answer
        assertEquals(NewCardOk.Resolved(1, true), decision(answer))
    }

    @Test fun dueLearningPredictionUsesNormalRowAfterItsEarlierNewAnswer() {
        val now = 1_700_000_000_000L
        val first = card(1, 2)
        val learning = card(3, 1).let { it.copy(before = it.before + ("due" to "${now / 1000 - 10}")) }
        val main = card(2, 2).copy(learning = PredictRule.LearnRead(now,
            listOf(PredictRule.Due(3, now / 1000 - 10, learning.key))))
        val nc = NextCard<String>(1, { 1L }, { now }, check = PredictRule::check)
        assertEquals(NewCardOk.Resolved(1, true), NewCardOk.settle(true, 0, 4, 1))
        nc.reload(); nc.loaded(1, first, listOf(first.key, main.key))
        nc.prepared(first.key, 1, main); nc.preparedLearning(first.key, 1, learning)
        val shown = nc.tapAnswer(3, 100).filterIsInstance<Cmd.Show<String>>().single()
        assertEquals(learning, shown.card)
        assertTrue(shown.predicted)
        assertFalse(NewCardOk.rowApplies(true, shown.card.before["queue"]?.toIntOrNull()))
        nc.tapAnswer(2, 100)
        val answer = nc.confirmed(2, learning.key, listOf(learning.key))
            .filterIsInstance<Cmd.Enqueue>().single().answer
        assertEquals(NewCardOk.Resolved(2, false), decision(answer))
    }

    @Test fun restartIgnoresOldGentlePreference() {
        val context = Shots.FilesCtx(paparazzi.context)
        context.getSharedPreferences("study", 0).edit().putBoolean(NewCardOk.PREF, true)
            .putString("newCardOkGentle", "1:0:${System.currentTimeMillis()}").commit()
        val reopened = context.getSharedPreferences("study", 0)
        for (queue in listOf(1, 3, 2)) {
            assertFalse(NewCardOk.rowApplies(reopened.getBoolean(NewCardOk.PREF, false), queue))
            assertEquals(NewCardOk.Resolved(1, false), NewCardOk.settle(true, queue, 4, 1))
        }
    }

    @Test fun theOldGentlePreferenceIsRemovedOnceAndNothingElseIsTouched() {   // R19-FIX NICE-8
        val context = Shots.FilesCtx(paparazzi.context)
        val prefs = context.getSharedPreferences("study", 0)
        prefs.edit().putBoolean(NewCardOk.PREF, true).putString(NewCardOk.OLD_GENTLE_PREF, "1:0:${System.currentTimeMillis()}").commit()
        assertTrue(NewCardOk.dropOldGentle(prefs))
        assertFalse(prefs.contains("newCardOkGentle"))
        assertTrue("the setting itself stays", prefs.getBoolean(NewCardOk.PREF, false))
        assertFalse("the second time there is nothing to remove", NewCardOk.dropOldGentle(prefs))
    }

    @Test fun undoRestoresOkWhenAnkiDroidRestoresNewQueueAndRedoRemovesIt() {
        for (queue in listOf(0, 1, 0, 1)) {
            assertEquals(queue == 0, NewCardOk.rowApplies(true, queue))
            assertEquals(NewCardOk.Resolved(1, queue == 0), NewCardOk.settle(true, queue, 4, 1))
        }
    }
}
