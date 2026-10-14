package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.Ui
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Study deck picker (StudyDecksActivity) and the Study screen (StudyActivity) in every state. The card itself is a WebView: layoutlib draws it blank. */
class StudyShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    // ---------------------------------------------------------------- deck picker

    @Test fun deckPicker() {
        val both = listOf(Size.PHONE, Size.SPLIT)
        paparazzi.shootWith("studydecks-list", both) { StudyDeckKit.build(it) }
        paparazzi.shootWith("studydecks-folded", both) { StudyDeckKit.build(it, folded = setOf("Languages")) }
        paparazzi.shootWith("studydecks-nogame", both, FakeGame.Scenario.NO_DATA) { StudyDeckKit.build(it) }
        paparazzi.shootWith("studydecks-nodecks", both) { StudyDeckKit.build(it, decks = emptyList(), lastDeck = 0) }
        paparazzi.shootWith("studydecks-ankinotinstalled", both) { StudyDeckKit.build(it, decks = emptyList(), ankiVersion = null, lastDeck = 0) }
        paparazzi.shootWith("studydecks-nopermission", both) { StudyDeckKit.build(it, problem = "Allow AnkiGo to use AnkiDroid first.", granted = false) }
    }

    // ---------------------------------------------------------------- study screen

    private fun screen(
        ctx: Context, flipped: Boolean = false, buttons: Int = 4, ankiVersion: String = "2.24.0",
        mediaNotice: Triple<Int, Int, Boolean>? = null, blocked: Boolean = false, heightMode: Int = 1,
    ): Pair<StudyActivity, View> {
        val ss = ShotCtx(ctx, ankiVersion = ankiVersion)
        val act = attach(StudyActivity(), ss)
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", heightMode)
        val root = act.buildContent()
        val times = listOf("<1m", "6m", "1d", "4d").take(buttons).let { if (buttons == 2) listOf("<1m", "1d") else if (buttons == 3) listOf("<1m", "1d", "4d") else it }
        setField(act, "card", Anki.Review(10L, 0, buttons, times, emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        setField(act, "mediaBlocked", blocked)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        if (mediaNotice != null) call(act, "mediaNotice", mediaNotice.first, mediaNotice.second, mediaNotice.third)
        return act to root
    }

    @Test fun question() {
        paparazzi.shootWith("study-question", listOf(Size.PHONE, Size.SPLIT)) { screen(it).second }
        paparazzi.shootWith("study-question-nogame", listOf(Size.PHONE), FakeGame.Scenario.NO_DATA) { screen(it).second }
    }

    @Test fun answer() {
        paparazzi.shootWith("study-answer", listOf(Size.PHONE, Size.SPLIT)) { screen(it, flipped = true).second }
        paparazzi.shootWith("study-answer-2buttons", listOf(Size.PHONE)) { screen(it, flipped = true, buttons = 2).second }
    }

    @Test fun landscape() {
        paparazzi.shootWith("study-land-question", listOf(Size.LAND)) { screen(it).second }
        paparazzi.shootWith("study-land-answer", listOf(Size.LAND)) { screen(it, flipped = true).second }
    }

    @Test fun mediaMissing() {
        paparazzi.shootWith("study-media-picture", listOf(Size.PHONE, Size.SPLIT)) { screen(it, mediaNotice = Triple(1, 0, false), blocked = true).second }
        paparazzi.shootWith("study-media-needsanki", listOf(Size.PHONE)) { screen(it, mediaNotice = Triple(0, 0, true), blocked = true).second }
        paparazzi.shootWith("study-media-sound", listOf(Size.PHONE)) { screen(it, mediaNotice = Triple(0, 2, false)).second }
    }

    @Test fun error() {
        paparazzi.shootWith("study-error", listOf(Size.PHONE)) {
            val (act, root) = screen(it)
            call(act, "showError", "AnkiDroid hasn't opened its study queue. Open AnkiDroid once, then retry.")
            root
        }
    }

    /** The card-actions bottom sheet: its content over the question screen with the dialog's dim layer (.48) and bottom gravity. */
    @Test fun actionsSheet() {
        for (flags in listOf("2.25.0" to "", "2.24.0" to "-noflags")) {
            paparazzi.shootWith("study-actions$${flags.second}".replace("$", ""), listOf(Size.PHONE, Size.SPLIT)) { ctx ->
                val (act, root) = screen(ctx, ankiVersion = flags.first)
                val sheet = act.actionSheetContent { }
                val w = minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
                FrameLayout(ctx).apply {
                    addView(root, FrameLayout.LayoutParams(-1, -1))
                    addView(View(ctx).apply { setBackgroundColor(Color.argb((.48f * 255).toInt(), 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                    addView(sheet, FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
                }
            }
        }
    }

    private fun summaryScreen(ctx: Context, busy: Boolean = true): View {
        val (act, root) = screen(ctx)
        if (busy) {
            setField(act, "counts", intArrayOf(9, 6, 60, 11))
            setField(act, "totalTime", 18 * 60_000L)
            setField(act, "coins", 120); setField(act, "xp", 340); setField(act, "met", 4); setField(act, "defeated", 3)
            (getField(act, "caught") as MutableList<String>).add("Sparkmouse")
            (getField(act, "foundItems") as MutableList<String>).addAll(listOf("rare-candy", "potion"))
            (getField(act, "rewardMons") as MutableList<JSONObject>).apply {
                add(JSONObject().put("species", 54).put("name", "Splashduck").put("caught", false))
                add(JSONObject().put("species", 25).put("name", "Sparkmouse").put("shiny", false).put("caught", true))
                add(JSONObject().put("species", 16).put("name", "Pidgeon").put("caught", false))
            }
            @Suppress("UNCHECKED_CAST")
            (getField(act, "teamProgress") as MutableMap<String, JSONObject>).apply {
                put("t1", JSONObject().put("start", 26).put("name", "Sparkmouse").put("level", 27).put("species", 25).put("shiny", false))
                put("t2", JSONObject().put("start", 24).put("fromName", "Leafling").put("name", "Leafwing").put("level", 24).put("species", 3).put("evolved", true))
            }
        }
        call(act, "summary", StudyDeckKit.decks, 0, 3)
        return root
    }

    @Test fun summary() {
        paparazzi.shootWith("study-summary", listOf(Size.PHONE, Size.SPLIT)) { summaryScreen(it) }
        paparazzi.shootWith("study-summary-quiet", listOf(Size.PHONE)) { summaryScreen(it, busy = false) }
    }
}
