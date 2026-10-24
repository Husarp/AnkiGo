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
import com.husarp.ankigo.study.TtsRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R18-A (278, replaces R10-X1's held "Show answer"): a card AnkiDroid has not confirmed yet (it is shown ahead of the slow write of the
 * answer before it) is an ordinary card for the user. "Show answer" is a normal button, and the confirm that comes later keeps the side the
 * user is on and only rebuilds the answer row when its buttons really changed. Made-up card texts only.
 * PNGs (phone + split, light + dark): r18a-predicted-show-answer.
 */
class R18APredictedFlipShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun row(id: Long) = mapOf("_id" to "$id", "note_id" to "$id", "deck_id" to "2", "reps" to "2", "queue" to "2", "due" to "5", "last_review_time_secs" to "1")

    private fun review(id: Long, times: List<String> = listOf("<1m", "6m", "1d", "4d")) = Anki.Review(id, 0, 4, times, emptyList())

    /** The study screen on a made-up PREDICTED card (nothing loaded; the views only), on its question side or, with [flipped], its answer side. */
    private fun screen(ctx: Context, flipped: Boolean = false): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", review(10L))
        setField(act, "stats", row(10))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped); setField(act, "locked", false); setField(act, "predictedNow", true)
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

    private fun card(id: Long, times: List<String> = listOf("<1m", "6m", "1d", "4d")) = Prepared(
        StudyLoop.Key(id, 0), row(id),
        com.husarp.ankigo.StudyCard(
            review(id, times), "<div>Capital of Australia?</div>", "<div>Canberra</div>", "Card 1",
            "Basic", "", emptyMap(), "", emptyList(), emptyList(), false, TtsRules.NONE, JSONObject(), "Geography", true, 0,
        ),
    )

    @Test fun showAnswerOnAPredictedCardIsAnOrdinaryButton() {
        for (size in listOf(Size.PHONE, Size.SPLIT)) paparazzi.shootWith("r18a-predicted-show-answer", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            measure(root, size, ctx)
            val b = buttons(act).single()
            assertEquals("Show answer", b.contentDescription.toString().substringBefore(","))
            assertTrue("it takes a tap", b.isEnabled)
            assertTrue("not marked as pressed or saving", b.alpha == 1f && b.scaleX == 1f && b.foreground == null)
            assertFalse(b.contentDescription.toString().endsWith("saving"))
            root
        }
    }

    @Test fun theConfirmKeepsTheAnswerSideAndLeavesTheButtonsAloneWhenTheyDidNotChange() {
        paparazzi.shootWith("r18a-predicted-confirm", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx, flipped = true)
            val before = buttons(act)
            assertEquals("the four answer buttons", 4, before.size)
            call(act, "cardConfirmed", card(10))
            assertEquals(true, getField(act, "flipped")); assertEquals(false, getField(act, "predictedNow")); assertEquals(false, getField(act, "locked"))
            val after = buttons(act)
            assertEquals(4, after.size)
            assertTrue("a finger on a button is not taken away: the row was not rebuilt", before.indices.all { before[it] === after[it] })
            call(act, "cardConfirmed", card(10, listOf("<1m", "7m", "2d", "5d")))
            val changed = buttons(act)
            assertEquals("new times, new row", 4, changed.size)
            assertNotSame(before[0], changed[0])
            assertTrue(changed.all { it.isEnabled })
            assertEquals(true, getField(act, "flipped"))
            measure(root, Size.PHONE, ctx)
            root
        }
    }
}
