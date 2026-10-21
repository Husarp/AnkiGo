package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.Prepared
import com.husarp.ankigo.study.StudyLoop
import com.husarp.ankigo.study.StudyRules
import com.husarp.ankigo.study.TtsRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 175: leaving the app (a screenshot, another app) and coming back reloads the card; the answer side that was up shows again
 * when AnkiDroid still has the same card unanswered on top, and the question shows when it was answered meanwhile or another card is
 * on top. Drives the same showCard path a resume's load ends in. Made-up card texts only. PNG: r13-answer-kept.
 */
class R13AnswerKeepShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun row(id: Long, reps: Int) = mapOf("_id" to "$id", "note_id" to "$id", "deck_id" to "2", "reps" to "$reps", "queue" to "2", "due" to "5", "last_review_time_secs" to "1")

    private fun card(id: Long, q: String, reps: Int = 2) = Prepared(
        StudyLoop.Key(id, 0), row(id, reps),
        com.husarp.ankigo.StudyCard(
            Anki.Review(id, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()), "<div>$q</div>", "<div>Canberra</div>", "Card 1",
            "Basic", "", emptyMap(), "", emptyList(), emptyList(), false, TtsRules.NONE, JSONObject(), "Geography", true, 0,
        ),
    )

    /** The study screen with card 10's ANSWER side up (a confirmed card), as before the user left the app. */
    private fun screen(ctx: Context): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "stats", row(10, 2))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", true); setField(act, "locked", false); setField(act, "predictedNow", false)
        setField(act, "typedShown", "Canbera")
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return act to root
    }

    private fun buttons(act: StudyActivity): List<View> {
        val row = getField(act, "buttons") as ViewGroup
        val out = ArrayList<View>()
        fun walk(v: View) { if (v is ViewGroup && v.isClickable) out += v else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(row)
        return out
    }

    /** What the resume does: remember the answer side, let go of the card (the load), then the load shows [next]. */
    private fun resume(act: StudyActivity, next: Prepared<com.husarp.ankigo.StudyCard>) {
        val keep = call(act, "answerKeep") as StudyRules.AnswerKeep?
        setField(act, "keepAnswer", keep)
        call(act, "resetForLoading", true)
        call(act, "showCard", next, false)
    }

    @Test fun theSameUnansweredCardComesBackOnItsAnswerSide() {
        paparazzi.shootWith("r13-answer-kept", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            val keep = call(act, "answerKeep") as StudyRules.AnswerKeep
            assertEquals("Canbera", keep.typed)
            resume(act, card(10, "Capital of Australia?"))
            assertEquals("the answer side is up again", true, getField(act, "flipped"))
            assertEquals(false, getField(act, "locked"))
            assertNull("used once", getField(act, "keepAnswer"))
            val b = buttons(act)
            assertEquals("the four answer buttons are back", 4, b.size)
            assertTrue(b.all { it.isEnabled })
            measure(root, Size.PHONE, ctx)
            root
        }
    }

    @Test fun aCardAnsweredMeanwhileOrAnotherCardShowsTheQuestion() {
        paparazzi.shootWith("r13-answer-not-kept", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            resume(act, card(10, "Capital of Australia?", reps = 3))   // answered in AnkiDroid, came back on top (Again)
            assertEquals(false, getField(act, "flipped"))
            assertEquals("Show answer", buttons(act).single().contentDescription.toString().substringBefore(","))
            val (act2, _) = screen(ctx)
            resume(act2, card(11, "Capital of Canada?"))   // another card is on top now
            assertEquals(false, getField(act2, "flipped"))
            val (act3, _) = screen(ctx)
            setField(act3, "flipped", false)   // the question side was up: nothing to keep
            assertNull(call(act3, "answerKeep"))
            val (act4, _) = screen(ctx)
            setField(act4, "finished", true)   // the summary: nothing to keep
            assertNull(call(act4, "answerKeep"))
            measure(root, Size.PHONE, ctx)
            root
        }
    }
}
