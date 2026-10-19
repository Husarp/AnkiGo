package com.husarp.ankigo.shots

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.AnswerHost
import com.husarp.ankigo.study.AnswerNotes
import com.husarp.ankigo.study.Prepared
import com.husarp.ankigo.study.StudyLoop
import com.husarp.ankigo.study.TtsRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9 wave 6 (item 7), the Study screen while an answer is still being saved in AnkiDroid: a predicted next card with a held tap (the
 * pressed button stays marked, the others dim), the saving dot, the "not saved yet" notice with its buttons, and a swap that lands on
 * the question side. Made-up card texts only.
 * PNGs (phone + split, light + dark): r9w6-held, r9w6-dot, r9w6-retrying, r9w6-parked, r9w6-swap.
 */
class R9W6StudyShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val both = listOf(Size.PHONE, Size.SPLIT)

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /** The study screen on a made-up card, answer side showing (nothing loaded; the views only). */
    private fun screen(ctx: Context, flipped: Boolean = true): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped); setField(act, "locked", false)
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

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView && v.text.isNotEmpty()) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun unresolved(retrying: Boolean, parked: Boolean = false, ageMs: Long = 2_000) =
        AnswerHost.Unresolved(1, SystemClock.elapsedRealtime() - ageMs, if (retrying) 3 else 0, if (retrying) "AnkiDroid is busy" else null, parked, 7)

    @Test fun aTapOnAPredictedCardIsHeldAndTheButtonStaysMarked() {
        for (size in both) paparazzi.shootWith("r9w6-held", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            setField(act, "predictedNow", true); setField(act, "heldEase", 3); setField(act, "locked", true)
            call(act, "showButtons")
            measure(root, size, ctx)
            val b = buttons(act)
            assertEquals(4, b.size)
            assertTrue("nothing takes another tap", b.none { it.isEnabled })
            val marked = b.filter { it.alpha == 1f && it.scaleX < 1f }
            assertEquals("exactly the pressed button is marked", 1, marked.size)
            assertTrue("it is the third one (Good)", marked.single() === b[2])
            assertTrue("and carries the outline", marked.single().foreground != null)
            assertTrue("the others dim", b.filter { it !== marked.single() }.all { it.alpha < .5f })
            assertTrue(marked.single().contentDescription.toString().endsWith("saving"))
            root
        }
    }

    @Test fun theSavingDotShowsAfterThreeHundredMillisecondsAndGoesWhenResolved() {
        for (size in both) paparazzi.shootWith("r9w6-dot", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            val dot = getField(act, "savingDot") as View
            assertEquals("Saving your answer to AnkiDroid", dot.contentDescription)
            assertEquals(View.GONE, dot.visibility)
            call(act, "applyUnresolved", unresolved(false, ageMs = 10))
            assertEquals("not for the first 300 ms", View.GONE, dot.visibility)
            call(act, "applyUnresolved", unresolved(false, ageMs = 2_000))
            assertEquals(View.VISIBLE, dot.visibility)
            assertEquals("no notice while nothing failed", View.GONE, (getField(act, "saveNotice") as View).visibility)
            measure(root, size, ctx)
            assertTrue("the dot has a size", dot.width > 0 && dot.height > 0)
            call(act, "applyUnresolved", null)
            assertEquals(View.GONE, dot.visibility)
            call(act, "applyUnresolved", unresolved(false, ageMs = 2_000))
            measure(root, size, ctx)
            root
        }
    }

    @Test fun aFailingWriteShowsTheNoticeWithRetryNowAndOpenInAnkiDroid() {
        for (size in both) paparazzi.shootWith("r9w6-retrying", listOf(size)) { ctx ->
            val (act, root) = screen(ctx, flipped = false)
            setField(act, "predictedNow", true)
            call(act, "applyUnresolved", unresolved(true))
            measure(root, size, ctx)
            val notice = getField(act, "saveNotice") as ViewGroup
            assertEquals(View.VISIBLE, notice.visibility)
            val t = texts(notice)
            assertEquals(AnswerNotes.RETRYING, t[0])
            assertEquals(listOf("Retry now", "Open in AnkiDroid"), t.drop(1))
            assertTrue("it is on screen", notice.height > 0 && notice.width > 0)
            assertEquals("the dot shows too", View.VISIBLE, (getField(act, "savingDot") as View).visibility)
            call(act, "applyUnresolved", null)
            assertEquals("the notice is gone when the answer is saved", View.GONE, notice.visibility)
            call(act, "applyUnresolved", unresolved(true))
            measure(root, size, ctx)
            root
        }
    }

    @Test fun aParkedAnswerOffersRetryNowAndGiveUp() {
        for (size in both) paparazzi.shootWith("r9w6-parked", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            call(act, "applyUnresolved", unresolved(true, parked = true))
            measure(root, size, ctx)
            val t = texts(getField(act, "saveNotice") as View)
            assertEquals(AnswerNotes.PARKED, t[0])
            assertTrue(t.toString(), t.containsAll(listOf("Retry now", "Give up on this answer", "Open in AnkiDroid")))
            root
        }
    }

    @Test fun aSwapLandsOnTheQuestionSideAndDropsTheHeldTap() {
        for (size in both) paparazzi.shootWith("r9w6-swap", listOf(size)) { ctx ->
            val (act, root) = screen(ctx)
            setField(act, "predictedNow", true); setField(act, "heldEase", 3); setField(act, "locked", true)
            call(act, "showButtons")
            val real = Prepared(
                StudyLoop.Key(11, 0), mapOf("_id" to "11", "note_id" to "11", "deck_id" to "2", "reps" to "2", "queue" to "2", "due" to "5", "last_review_time_secs" to "1"),
                com.husarp.ankigo.StudyCard(
                    Anki.Review(11L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()), "<div>Capital of Canada?</div>", "<div>Ottawa</div>", "Card 1",
                    "Basic", "", emptyMap(), "", emptyList(), emptyList(), false, TtsRules.NONE, JSONObject(), "Geography", true, 0,
                ),
            )
            call(act, "showCard", real, false)   // what a swap does
            measure(root, size, ctx)
            assertEquals("the question side", false, getField(act, "flipped"))
            assertEquals("the held tap is gone", 0, getField(act, "heldEase"))
            assertEquals(false, getField(act, "predictedNow"))
            assertEquals(false, getField(act, "locked"))
            assertEquals("Capital of Canada?", (getField(act, "question") as String).replace(Regex("<[^>]*>"), ""))
            val b = buttons(act)
            assertEquals("Show answer", b.single().contentDescription.toString().substringBefore(","))
            assertTrue(b.single().isEnabled)
            assertNull(getField(act, "answerTiming"))
            assertFalse((getField(act, "saveNotice") as View).visibility == View.VISIBLE)
            root
        }
    }
}
