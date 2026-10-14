package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.Ui
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Study screen on tablets (two columns, full battle panel) plus phone / split / landscape regression renders. WebView card = blank in the renderer. */
class TabletShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val tablets = listOf(Size.TABLET, Size.TABLET_LAND)
    private val regress = listOf(Size.PHONE, Size.SPLIT, Size.LAND)

    private fun screen(
        ctx: Context, flipped: Boolean = false, buttons: Int = 4,
        mediaNotice: Triple<Int, Int, Boolean>? = null, blocked: Boolean = false, heightMode: Int = 1,
    ): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", heightMode)
        val root = act.buildContent()
        val times = if (buttons == 2) listOf("<1m", "1d") else listOf("<1m", "6m", "1d", "4d")
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

    @Test fun question() { paparazzi.shootWith("tablet-question", tablets + regress) { screen(it, heightMode = if (it.resources.configuration.smallestScreenWidthDp >= 600) 0 else 1).second } }

    /** Portrait tablet: the three heights of the stacked battle panel (0 full, 1 strip, 2 mini), question and answer. */
    @Test fun portraitHeights() {
        for (h in 0..2) {
            paparazzi.shootWith("tablet-h$h-question", listOf(Size.TABLET)) { screen(it, heightMode = h).second }
            paparazzi.shootWith("tablet-h$h-answer", listOf(Size.TABLET)) { screen(it, flipped = true, heightMode = h).second }
        }
    }

    @Test fun answer() {
        paparazzi.shootWith("tablet-answer", tablets + regress) { screen(it, flipped = true, heightMode = if (it.resources.configuration.smallestScreenWidthDp >= 600) 0 else 1).second }
        paparazzi.shootWith("tablet-answer-2buttons", tablets) { screen(it, flipped = true, buttons = 2).second }
    }

    @Test fun notices() {
        paparazzi.shootWith("tablet-media-picture", tablets) { screen(it, mediaNotice = Triple(1, 0, false), blocked = true).second }
        paparazzi.shootWith("tablet-media-sound", tablets) { screen(it, mediaNotice = Triple(0, 2, false)).second }
        paparazzi.shootWith("tablet-error", tablets) {
            val (act, root) = screen(it)
            call(act, "showError", "AnkiDroid hasn't opened its study queue. Open AnkiDroid once, then retry.")
            root
        }
    }

    @Test fun actionsSheet() {
        paparazzi.shootWith("tablet-actions", tablets) { ctx ->
            val (act, root) = screen(ctx)
            val sheet = act.actionSheetContent { }
            val w = minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
            FrameLayout(ctx).apply {
                addView(root, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(Color.argb((.48f * 255).toInt(), 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                addView(sheet, FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            }
        }
    }

    private fun summaryScreen(ctx: Context): View {
        val (act, root) = screen(ctx)
        setField(act, "counts", intArrayOf(9, 6, 60, 11))
        setField(act, "totalTime", 18 * 60_000L)
        setField(act, "coins", 120); setField(act, "xp", 340); setField(act, "met", 4); setField(act, "defeated", 3)
        @Suppress("UNCHECKED_CAST") (getField(act, "caught") as MutableList<String>).add("Sparkmouse")
        @Suppress("UNCHECKED_CAST") (getField(act, "foundItems") as MutableList<String>).addAll(listOf("rare-candy", "potion"))
        @Suppress("UNCHECKED_CAST") (getField(act, "rewardMons") as MutableList<JSONObject>).apply {
            add(JSONObject().put("species", 54).put("name", "Splashduck").put("caught", false))
            add(JSONObject().put("species", 25).put("name", "Sparkmouse").put("shiny", false).put("caught", true))
            add(JSONObject().put("species", 16).put("name", "Pidgeon").put("caught", false))
        }
        call(act, "summary", StudyDeckKit.decks, 0, 3)
        return root
    }

    @Test fun summary() { paparazzi.shootWith("tablet-summary", tablets + Size.LAND) { summaryScreen(it) } }

    @Test fun deckPicker() {
        paparazzi.shootWith("tablet-decks", tablets) { StudyDeckKit.build(it) }
    }
}
