package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.study.AnswerHost
import com.husarp.ankigo.study.AnswerNotes
import com.husarp.ankigo.study.AnswerOutcome
import com.husarp.ankigo.study.PendingAnswers
import com.husarp.ankigo.study.QueuedAnswer
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9 wave 6 FIX (Codex 1 and 5), the Study screen's answer handling without a phone: an answer another screen made (or one restored from
 * the journal) is only TOLD to this screen (its toast), never counted in this session; the user hears once per visit that the backup copy
 * of an answer could not be kept. The screen is attached without onCreate (no host, no AnkiDroid); made-up cards only.
 */
class R9W6FixShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val before = mapOf("_id" to "7", "note_id" to "70", "deck_id" to "3", "reps" to "3", "due" to "12", "queue" to "2", "last_review_time_secs" to "100")
    private val after = before + mapOf("reps" to "4", "due" to "19")
    private fun answer(seq: Long, ease: Int = 3, ms: Long = 4_000) = QueuedAnswer(seq, 3, 70, 0, 7, before, ease, ms, 1L, buttons = 4)
    private fun saved(seq: Long, ease: Int = 3, ms: Long = 4_000) = AnswerOutcome.Saved(answer(seq, ease, ms), after, 1, 120)
    private fun notWritten(seq: Long) = AnswerOutcome.NotWritten(answer(seq), AnswerOutcome.Reason.NOT_TOP, "not first")
    private val rewards = listOf(GameEvent.XpGained("u1", "Pip", 50, 3, 10, 100), GameEvent.Cash(7, 7))

    private fun screen(ctx: Context): StudyActivity {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 3L); setField(act, "deckName", "Geography::Capitals")
        return act
    }

    @Suppress("UNCHECKED_CAST")
    private fun tickets(act: StudyActivity) = getField(act, "tickets") as HashMap<Long, PendingAnswers.Ticket>

    private fun blank(ctx: Context): View = View(ctx)

    @Test fun anAnswerOfAnotherScreenIsNotCreditedToThisSession() {
        paparazzi.shootWith("r9w6fix-credit", listOf(Size.PHONE)) { ctx ->
            val act = screen(ctx)
            val counts = getField(act, "counts") as IntArray
            val pending = getField(act, "pending") as PendingAnswers

            // deck A's answer resolves after deck B's screen registered: B is told, but it is not B's answer
            val other = saved(5)
            call(act, "answerEnded", other, AnswerHost.Info(false, 300, 0L))
            call(act, "gameFinished", other.answer, rewards, 5L, 6L, false)
            assertEquals("no button count", listOf(0, 0, 0, 0), counts.toList())
            assertEquals("no time", 0L, getField(act, "totalTime"))
            assertEquals("no xp in the summary", 0, getField(act, "xp")); assertEquals("no coins in the summary", 0, getField(act, "coins"))
            assertEquals("lastStudied is not written for it", false, getField(act, "studiedWritten"))
            assertTrue("the summary does not wait for its game step", tickets(act).isEmpty()); assertEquals(0, pending.count)

            // the same answer made on this screen counts
            val own = saved(6)
            tickets(act)[6L] = pending.start() // what answerMade does at the tap
            call(act, "answerEnded", own, AnswerHost.Info(true, 300, 0L))
            call(act, "gameFinished", own.answer, rewards, 5L, 6L, true)
            assertEquals(listOf(0, 0, 1, 0), counts.toList())
            assertEquals(4_000L, getField(act, "totalTime"))
            assertEquals(50, getField(act, "xp")); assertEquals(7, getField(act, "coins"))
            assertEquals("the first saved answer of the visit writes lastStudied", true, getField(act, "studiedWritten"))
            assertTrue(tickets(act).isEmpty()); assertEquals("its game step released the ticket", 0, pending.count)
            blank(ctx)
        }
    }

    @Test fun aNotWrittenOutcomeOfAnotherScreenOrARestoredAnswerGivesOnlyItsToast() {
        paparazzi.shootWith("r9w6fix-notwritten", listOf(Size.PHONE)) { ctx ->
            val act = screen(ctx)
            val pending = getField(act, "pending") as PendingAnswers
            setField(act, "countsFirst", false)
            tickets(act)[9L] = pending.start() // a ticket this screen must leave alone (it is not seq 9's screen)
            Ui.lastToast = null
            call(act, "answerEnded", notWritten(9), AnswerHost.Info(false, -1L, 0L))
            assertEquals("the user is told, whoever's answer it was", AnswerNotes.notWritten(AnswerOutcome.Reason.NOT_TOP), Ui.lastToast)
            assertEquals("the stand-in counts are not this session's business", false, getField(act, "countsFirst"))
            assertEquals("no ticket of this screen was touched", 1, pending.count)

            // its own not-written answer also resets the counts and frees its ticket
            Ui.lastToast = null
            call(act, "answerEnded", notWritten(9), AnswerHost.Info(true, -1L, 0L))
            assertEquals(AnswerNotes.notWritten(AnswerOutcome.Reason.NOT_TOP), Ui.lastToast)
            assertEquals(true, getField(act, "countsFirst"))
            assertEquals(0, pending.count)
            blank(ctx)
        }
    }

    @Test fun theUserHearsOncePerVisitThatTheBackupCopyOfAnAnswerCouldNotBeKept() {
        paparazzi.shootWith("r9w6fix-journal", listOf(Size.PHONE)) { ctx ->
            val act = screen(ctx)
            Ui.lastToast = null
            act.journalWarn()
            assertEquals(AnswerNotes.JOURNAL_FAILED, Ui.lastToast)
            assertTrue(AnswerNotes.JOURNAL_FAILED.contains("still being saved to AnkiDroid"))
            Ui.lastToast = null
            act.journalWarn() // the next answer of the same visit
            assertNull("only once per visit", Ui.lastToast)
            blank(ctx)
        }
    }
}
