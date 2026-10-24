package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.study.Cmd
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 129: after the deck's last answer the Study screen stays on the battle panel, the answer row holds one "Finish deck" button and
 * only a tap on it opens the finished screen (once). Drives the real [StudyActivity] views by reflection (no onCreate), like R609FlowTest.
 * PNGs: r14c-finish-waiting, r14c-finish-held (a result holds), r14c-finish-summary.
 */
class R14CFinishDeckShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val decks = listOf(Anki.Deck(2L, "Geography::Capitals", 0, 0, 0, false), Anki.Deck(3L, "Geography::Rivers", 0, 4, 0, false))
    private val args = Triple(decks, 0, 0)

    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun defeatBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /** The study screen with a question card up (reduced motion, full panel). */
    private fun screen(ctx: Context): Pair<StudyActivity, View> {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "flipped", false); setField(act, "locked", false)
        call(act, "layout")
        (getField(act, "panel") as BattlePanelView).setStudyLayout(false, false)
        measure(root, Size.PHONE, ctx)
        call(act, "showButtons")
        return act to root
    }

    private fun row(act: StudyActivity) = getField(act, "buttons") as ViewGroup
    private fun rowText(act: StudyActivity) = row(act).getChildAt(0)?.contentDescription?.toString() ?: ""
    private fun events(act: StudyActivity) = getField(act, "events") as EventPlayer
    private fun panel(act: StudyActivity) = getField(act, "panel") as BattlePanelView
    private fun finished(act: StudyActivity) = getField(act, "finished") as Boolean

    /** The last answer was made and the empty queue came back: what the load does (answer flag, ShowFinished, the wait). */
    private fun lastAnswerIn(act: StudyActivity) {
        setField(act, "answeredSinceCard", true)
        call(act, "perform", listOf(Cmd.ShowFinished))
        call(act, "waitForFinish", args)
    }

    private fun flush(c: Context) {
        paparazzi.snapshot(android.widget.FrameLayout(c), "r14c-flush", 300)
    }

    @Test fun theLastAnswerLeavesThePanelAndOneFinishButton() {
        paparazzi.shootWith("r14c-finish-waiting", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            lastAnswerIn(act)
            assertFalse("the summary is not shown", finished(act))
            assertNull(getField(act, "lastSummary"))
            assertEquals("one button, in the place of Show answer", 1, row(act).childCount)
            assertEquals("Finish deck, see the summary", rowText(act))
            assertTrue(row(act).getChildAt(0).isEnabled)
            assertEquals("the battle panel stays", View.VISIBLE, panel(act).visibility)
            val line = getField(act, "summaryView") as ViewGroup
            assertEquals("Deck finished · tap Finish deck", (line.getChildAt(0) as TextView).text.toString())
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    @Test fun tappingFinishDeckShowsTheSummaryOnce() {
        paparazzi.shootWith("r14c-finish-summary", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            lastAnswerIn(act)
            row(act).getChildAt(0).performClick()
            assertTrue("the summary is on", finished(act))
            assertNotNull(getField(act, "lastSummary"))
            assertNull("nothing waits any more", getField(act, "finishArgs"))
            assertEquals("the summary hides the panel as today", View.GONE, panel(act).visibility)
            val summary = getField(act, "summaryView")
            val buttons = row(act).childCount
            call(act, "finishDeck")   // a second tap / a late call
            assertTrue(finished(act)); assertTrue("the same summary, not a second one", summary === getField(act, "summaryView"))
            assertEquals(buttons, row(act).childCount)
            assertFalse("Finish deck is gone", (0 until row(act).childCount).any { row(act).getChildAt(it).contentDescription?.toString()?.startsWith("Finish deck") == true })
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    @Test fun aHeldResultIsReplacedByFinishDeckAndEndedByItsTap() {
        paparazzi.shootWith("r14c-finish-held", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            events(act).enqueue(defeatBatch())
            flush(ctx)
            assertTrue("a result holds", events(act).holding)
            // R18 FIX-1: the row swap is the hold notice posted on the main looper (R609FlowTest tests that); under a heavy machine load it is not
            // always played by the flush, so this test runs the same step itself and no longer depends on the timing
            call(act, "showButtons")
            assertEquals("Continue, next battle", rowText(act))
            lastAnswerIn(act)
            assertEquals("Finish deck, see the summary", rowText(act))
            assertEquals(1, row(act).childCount)
            assertTrue("the hold stays until Finish deck", events(act).holding)
            measure(root, Size.PHONE, ctx)
            row(act).getChildAt(0).performClick()
            assertFalse("Finish deck ended the hold", events(act).holding)
            assertTrue(finished(act))
            root
        }
    }

    @Test fun keysSwipesAndTheAutoTimerAnswerNothingWhileWaiting() {
        paparazzi.shootWith("r14c-finish-keys", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            events(act).enqueue(defeatBatch())
            flush(ctx)
            lastAnswerIn(act)
            call(act, "flip"); call(act, "answer", 3); call(act, "fireAuto")
            assertTrue("a key does not even continue the battle: only the button acts", events(act).holding)
            assertFalse(finished(act))
            assertEquals(false, getField(act, "flipped"))
            assertEquals(0, (getField(act, "counts") as IntArray).sum())
            assertEquals("Finish deck, see the summary", rowText(act))
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    @Test fun aScreenOpenedOnAFinishedDeckHasNoWaitingState() {
        paparazzi.shootWith("r14c-finish-reopen", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            assertEquals("no own answer since the last card: the load shows the summary at once", false, getField(act, "answeredSinceCard"))
            call(act, "summary", decks, 0, 0)
            assertTrue(finished(act)); assertNull(getField(act, "finishArgs"))
            assertEquals(false, getField(act, "answeredSinceCard"))
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    /** What the load does when AnkiDroid's queue comes back empty: a fresh load token, then the screen's empty-deck step. */
    private fun emptyLoad(act: StudyActivity) {
        val nc = (getField(act, "nc\$delegate") as Lazy<*>).value as com.husarp.ankigo.study.NextCard<*>
        val token = (nc.reload().single() as Cmd.Load).token
        setField(act, "generation", token)
        call(act, "deckEmpty", token, args, {})
    }

    /**
     * R14 review: the wait lived only in the screen's fields, so a recreated screen (a theme or font change, the app reopened after
     * Android closed it) loaded the empty deck and opened the summary at once. Saved and restored, it waits for Finish deck again; a
     * new visit (no saved state) still opens the summary at once.
     */
    @Test fun aRecreatedScreenWaitsForFinishDeckAgain() {
        paparazzi.shootWith("r14c-finish-recreated", listOf(Size.PHONE)) { ctx ->
            val (before, _) = screen(ctx)
            lastAnswerIn(before)
            val saved = android.os.Bundle()
            call(before, "saveFinishWait", saved)
            val (after, root) = screen(ctx)   // the recreated screen: fields start over, only the saved state comes back
            call(after, "restoreFinishWait", saved)
            emptyLoad(after)
            assertFalse("not straight to the summary", finished(after))
            assertNotNull(getField(after, "finishArgs"))
            assertEquals(1, row(after).childCount)
            assertEquals("Finish deck, see the summary", rowText(after))
            row(after).getChildAt(0).performClick()
            assertTrue("the tap still opens it", finished(after))

            val (shown, _) = screen(ctx)   // the summary was on screen already: recreated, it is the summary again
            lastAnswerIn(shown); row(shown).getChildAt(0).performClick()
            val savedShown = android.os.Bundle()
            call(shown, "saveFinishWait", savedShown)
            val (again, _) = screen(ctx)
            call(again, "restoreFinishWait", savedShown)
            emptyLoad(again)
            assertTrue(finished(again)); assertNull(getField(again, "finishArgs"))

            val (fresh, _) = screen(ctx)   // a new visit to a finished deck: no saved state
            emptyLoad(fresh)
            assertTrue("a new visit shows the summary at once", finished(fresh))
            assertNull(getField(fresh, "finishArgs"))
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    @Test fun aNewCardEndsTheWaitAndClearsTheAnswerFlag() {
        paparazzi.shootWith("r14c-finish-newcard", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            lastAnswerIn(act)
            call(act, "resetForLoading", true)   // a reload (e.g. a learning card came back): the wait goes, the load decides again
            assertNull(getField(act, "finishArgs"))
            assertNull("the calm line went", getField(act, "summaryView"))
            assertTrue("the answer flag stays until a card is confirmed", getField(act, "answeredSinceCard") as Boolean)
            measure(root, Size.PHONE, ctx)
            root
        }
    }
}
