package com.husarp.ankigo.study

import com.husarp.ankigo.study.StudyTiming.Act
import com.husarp.ankigo.study.StudyTiming.Options
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyTimingTest {
    private fun o(json: String) = StudyTiming.parse(JSONObject(json))

    // ---- options
    @Test fun missingKeysAreAnkiDefaults() {
        assertEquals(Options(), StudyTiming.parse(null))
        val d = o("{}")
        assertEquals(60L, d.maxTakenSecs); assertFalse(d.showTimer); assertFalse(d.stopTimerOnAnswer)
        assertEquals(0.0, d.questionSecs, 0.0); assertEquals(0.0, d.answerSecs, 0.0)
        assertEquals(0, d.questionAction); assertEquals(0, d.answerAction); assertTrue(d.waitForAudio)
    }
    @Test fun readsEveryKey() {
        val d = o("""{"maxTaken":30,"timer":1,"stopTimerOnAnswer":true,"secondsToShowQuestion":7.5,"secondsToShowAnswer":4,"questionAction":1,"answerAction":3,"waitForAudio":false}""")
        assertEquals(30L, d.maxTakenSecs); assertTrue(d.showTimer); assertTrue(d.stopTimerOnAnswer)
        assertEquals(7.5, d.questionSecs, 0.0); assertEquals(4.0, d.answerSecs, 0.0)
        assertEquals(1, d.questionAction); assertEquals(3, d.answerAction); assertFalse(d.waitForAudio)
    }
    @Test fun stringsAndBoolsAreAccepted() {
        val d = o("""{"maxTaken":"45","timer":true,"stopTimerOnAnswer":"true","secondsToShowQuestion":"3.5","waitForAudio":"false","answerAction":"2"}""")
        assertEquals(45L, d.maxTakenSecs); assertTrue(d.showTimer); assertTrue(d.stopTimerOnAnswer)
        assertEquals(3.5, d.questionSecs, 0.0); assertFalse(d.waitForAudio); assertEquals(2, d.answerAction)
    }
    @Test fun nonsenseFallsBack() {
        val d = o("""{"maxTaken":"soon","timer":"maybe","secondsToShowQuestion":-4,"secondsToShowAnswer":null,"waitForAudio":"x"}""")
        assertEquals(60L, d.maxTakenSecs); assertFalse(d.showTimer)
        assertEquals(0.0, d.questionSecs, 0.0); assertEquals(0.0, d.answerSecs, 0.0); assertTrue(d.waitForAudio)
        assertEquals(1L, o("""{"maxTaken":0}""").maxTakenSecs)
        assertEquals(1000L, o("""{"maxTaken":0}""").maxMs)
    }

    // ---- visible timer
    @Test fun timerVisibilityPerMode() {
        assertTrue(StudyTiming.timerShown(StudyTiming.AS_DECK, true)); assertFalse(StudyTiming.timerShown(StudyTiming.AS_DECK, false))
        assertTrue(StudyTiming.timerShown(StudyTiming.ALWAYS, false)); assertFalse(StudyTiming.timerShown(StudyTiming.OFF, true))
        assertTrue(StudyTiming.timerShown(null, true)); assertFalse(StudyTiming.timerShown("???", false))
    }
    @Test fun clockFormat() {
        assertEquals("0:00", StudyTiming.clock(0)); assertEquals("0:07", StudyTiming.clock(7_999)); assertEquals("1:05", StudyTiming.clock(65_000))
        assertEquals("12:00", StudyTiming.clock(720_000)); assertEquals("0:00", StudyTiming.clock(-5))
        assertEquals("Answer time 0:07", StudyTiming.timerDescription(7_000))
    }
    @Test fun takenIsBankedPlusRunningAndCapped() {
        assertEquals(5_000L, StudyTiming.taken(5_000, 0, 99_000, 60_000))            // paused: only what is banked
        assertEquals(8_000L, StudyTiming.taken(5_000, 10_000, 13_000, 60_000))       // running
        assertEquals(60_000L, StudyTiming.taken(50_000, 10_000, 40_000, 60_000))     // capped at the deck's max
        assertEquals(5_000L, StudyTiming.taken(5_000, 10_000, 9_000, 60_000))        // clock going backwards never subtracts
        assertTrue(StudyTiming.atMax(60_000, 60_000)); assertFalse(StudyTiming.atMax(59_999, 60_000))
    }

    // ---- auto-advance
    @Test fun actionMappingFromTheDeck() {
        assertEquals(Act.SHOW_ANSWER, StudyTiming.questionAct(0)); assertEquals(Act.REMINDER, StudyTiming.questionAct(1)); assertEquals(Act.SHOW_ANSWER, StudyTiming.questionAct(9))
        assertEquals(listOf(Act.BURY, Act.AGAIN, Act.GOOD, Act.HARD, Act.REMINDER, Act.BURY), (0..5).map { StudyTiming.answerAct(it) })
    }
    @Test fun easeForEachButtonCount() {
        for (n in 2..4) assertEquals(1, StudyTiming.easeFor(Act.AGAIN, n))
        assertEquals(2, StudyTiming.easeFor(Act.GOOD, 2)); assertEquals(2, StudyTiming.easeFor(Act.GOOD, 3)); assertEquals(3, StudyTiming.easeFor(Act.GOOD, 4))
        assertEquals(2, StudyTiming.easeFor(Act.HARD, 4)); assertNull(StudyTiming.easeFor(Act.HARD, 3)); assertNull(StudyTiming.easeFor(Act.HARD, 2))
        assertNull(StudyTiming.easeFor(Act.BURY, 4))
    }
    @Test fun secondsAreTheDecksElseOwn() {
        val deck = Options(questionSecs = 6.5, answerSecs = 0.0)
        assertEquals(6_500L, StudyTiming.plan(deck, false, true, 10, 5, 4)!!.millis)
        assertEquals(5_000L, StudyTiming.plan(deck, true, true, 10, 5, 4)!!.millis)
        assertEquals(10_000L, StudyTiming.plan(Options(), false, true, 10, 5, 4)!!.millis)
    }
    @Test fun offMeansNoPlan() {
        assertNull(StudyTiming.plan(Options(questionSecs = 5.0), false, false, 10, 5, 4))
        assertNull(StudyTiming.plan(Options(), false, true, 0, 0, 4))
    }
    @Test fun planActions() {
        assertEquals(Act.SHOW_ANSWER, StudyTiming.plan(Options(), false, true, 10, 5, 4)!!.act)
        assertEquals(Act.REMINDER, StudyTiming.plan(Options(questionAction = 1), false, true, 10, 5, 4)!!.act)
        assertEquals(Act.BURY, StudyTiming.plan(Options(), true, true, 10, 5, 4)!!.act)
        val good4 = StudyTiming.plan(Options(answerAction = 2), true, true, 10, 5, 4)!!; assertEquals(Act.GOOD, good4.act); assertEquals(3, good4.ease)
        val good3 = StudyTiming.plan(Options(answerAction = 2), true, true, 10, 5, 3)!!; assertEquals(2, good3.ease)
        val again = StudyTiming.plan(Options(answerAction = 1), true, true, 10, 5, 2)!!; assertEquals(Act.AGAIN, again.act); assertEquals(1, again.ease)
        val hard = StudyTiming.plan(Options(answerAction = 3), true, true, 10, 5, 4)!!; assertEquals(Act.HARD, hard.act); assertEquals(2, hard.ease)
        val hard3 = StudyTiming.plan(Options(answerAction = 3), true, true, 10, 5, 3)!!; assertEquals(Act.REMINDER, hard3.act); assertEquals(0, hard3.ease)
        assertEquals(Act.REMINDER, StudyTiming.plan(Options(answerAction = 4), true, true, 10, 5, 4)!!.act)
    }
    @Test fun countdownLeft() {
        assertEquals(1f, StudyTiming.left(4_000, 1_000, 1_000), 0f); assertEquals(.5f, StudyTiming.left(4_000, 1_000, 3_000), 0f)
        assertEquals(0f, StudyTiming.left(4_000, 1_000, 9_000), 0f); assertEquals(1f, StudyTiming.left(4_000, 1_000, 500), 0f); assertEquals(0f, StudyTiming.left(0, 0, 0), 0f)
    }
    @Test fun descriptionsAndReminders() {
        assertEquals("Auto-advance: Good in 4 seconds", StudyTiming.describe(StudyTiming.Plan(Act.GOOD, 4_000, 3)))
        assertEquals("Auto-advance: show answer in 1 second", StudyTiming.describe(StudyTiming.Plan(Act.SHOW_ANSWER, 1_000)))
        assertEquals("Auto-advance: bury card in 5 seconds", StudyTiming.describe(StudyTiming.Plan(Act.BURY, 4_600)))
        assertEquals("Time's up — tap the card for the answer", StudyTiming.reminder(false)); assertEquals("Time's up — pick an answer", StudyTiming.reminder(true))
    }
    @Test fun pauseReasons() {
        fun run(page: Boolean = true, fg: Boolean = true, focus: Boolean = true, locked: Boolean = false, media: Boolean = false, dialog: Boolean = false, audio: Boolean = false, wait: Boolean = true) =
            StudyTiming.mayRun(page, fg, focus, locked, media, dialog, audio, wait)
        assertTrue(run())
        assertFalse(run(page = false)); assertFalse(run(fg = false)); assertFalse(run(focus = false)); assertFalse(run(locked = true))
        assertFalse(run(media = true)); assertFalse(run(dialog = true))
        assertFalse(run(audio = true)); assertTrue(run(audio = true, wait = false)); assertTrue(run(wait = true, audio = false))
    }
}
