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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R10-X1: "Show answer" on a predicted card (AnkiDroid has not confirmed it yet) is held: the button stays marked as pressed until
 * the confirm opens the answer; a swap drops it and the new card shows its question. Made-up card texts only.
 * PNGs (phone + split, light + dark): r10x1-held-flip.
 */
class R10X1HeldFlipShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /** The study screen on a made-up PREDICTED card, question side (nothing loaded; the views only). */
    private fun screen(ctx: Context): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", false); setField(act, "locked", false); setField(act, "predictedNow", true)
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

    private fun card(id: Long, q: String) = Prepared(
        StudyLoop.Key(id, 0), mapOf("_id" to "$id", "note_id" to "$id", "deck_id" to "2", "reps" to "2", "queue" to "2", "due" to "5", "last_review_time_secs" to "1"),
        com.husarp.ankigo.StudyCard(
            Anki.Review(id, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()), "<div>$q</div>", "<div>-</div>", "Card 1",
            "Basic", "", emptyMap(), "", emptyList(), emptyList(), false, TtsRules.NONE, JSONObject(), "Geography", true, 0,
        ),
    )

    @Test fun showAnswerOnAPredictedCardIsHeldAndTheButtonStaysMarked() {
        for (size in listOf(Size.PHONE, Size.SPLIT)) paparazzi.shootWith("r10x1-held-flip", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            call(act, "showHeldFlip")
            measure(root, size, ctx)
            val b = buttons(act).single()
            assertEquals("Show answer", b.contentDescription.toString().substringBefore(","))
            assertFalse("nothing takes another tap", b.isEnabled)
            assertTrue("marked as pressed", b.alpha == 1f && b.scaleX < 1f && b.foreground != null)
            assertTrue(b.contentDescription.toString().endsWith("saving"))
            assertEquals(false, getField(act, "flipped"))
            root
        }
    }

    @Test fun theConfirmOpensTheHeldAnswerAndASwapDropsIt() {
        paparazzi.shootWith("r10x1-held-flip-confirm", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            call(act, "showHeldFlip")
            // layoutlib has no WebView.evaluateJavascript: the real flip stops there, which proves the confirm went on to open the answer
            val reached = try { call(act, "cardConfirmed", card(10, "Capital of Australia?"), true); false } catch (e: NoSuchMethodError) { e.message.orEmpty().contains("evaluateJavascript") }
            assertTrue("the confirm opened the held answer", reached)
            assertEquals(false, getField(act, "flipHeld")); assertEquals(false, getField(act, "predictedNow"))
            assertEquals("the answer side is on its way (the page loads it)", true, getField(act, "locked"))
            val (act2, _) = screen(ctx)
            call(act2, "showHeldFlip")
            call(act2, "showCard", card(11, "Capital of Canada?"), false) // what a swap does
            assertEquals(false, getField(act2, "flipHeld")); assertEquals(false, getField(act2, "flipped")); assertEquals(false, getField(act2, "locked"))
            val b = buttons(act2).single()
            assertTrue("the new card's Show answer works", b.isEnabled)
            assertFalse(b.contentDescription.toString().endsWith("saving"))
            measure(root, Size.PHONE, ctx)
            root
        }
    }
}
