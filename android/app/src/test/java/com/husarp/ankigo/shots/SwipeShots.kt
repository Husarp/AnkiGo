package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.SwipeRules.Dir
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SwipeFrame
import org.junit.Rule
import org.junit.Test

/** Swipe to answer: the hint on the answer side (4/3/2 buttons), live feedback pills mid-swipe, and the Settings row. WebView card = blank in the renderer. */
class SwipeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.SPLIT, Size.LAND, Size.TABLET)

    private fun screen(ctx: Context, buttons: Int = 4, flipped: Boolean = true, swipeOn: Boolean = true): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", if (ctx.resources.configuration.smallestScreenWidthDp >= 600) 0 else 1)
        val root = act.buildContent()
        val times = if (buttons == 2) listOf("<1m", "1d") else if (buttons == 3) listOf("<1m", "1d", "4d") else listOf("<1m", "6m", "1d", "4d")
        setField(act, "card", Anki.Review(10L, 0, buttons, times, emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        setField(act, "swipeOn", swipeOn)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return act to root
    }

    @Test fun hint() {
        for (n in listOf(4, 3, 2)) paparazzi.shootWith("swipe-hint-$n", sizes) { screen(it, buttons = n).second }
        paparazzi.shootWith("swipe-off-answer", listOf(Size.PHONE)) { screen(it, swipeOn = false).second }
        paparazzi.shootWith("swipe-question", listOf(Size.PHONE)) { screen(it, flipped = false).second }
    }

    @Test fun feedback() {
        for (dir in Dir.entries) paparazzi.shootWith("swipe-drag-${dir.name.lowercase()}", sizes) { ctx ->
            val (act, root) = screen(ctx)
            (getField(act, "swipe") as SwipeFrame).preview(dir, 1f)
            root
        }
        // the same pills part-way: faded and smaller
        for (dir in listOf(Dir.RIGHT, Dir.UP)) paparazzi.shootWith("swipe-half-${dir.name.lowercase()}", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx)
            (getField(act, "swipe") as SwipeFrame).preview(dir, .45f)
            root
        }
        paparazzi.shootWith("swipe-drag-left-3", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx, buttons = 3)
            (getField(act, "swipe") as SwipeFrame).preview(Dir.UP, 1f)
            root
        }
    }

    /** The Settings page scrolled so the Study card (with the new row) is at the top. */
    private class ToStudy(ctx: Context, page: View) : FrameLayout(ctx) {
        init { addView(page, LayoutParams(-1, -1)) }
        private fun find(v: View, pred: (View) -> Boolean): View? {
            if (pred(v)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
            return null
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val scroll = find(this) { it is ScrollView } as? ScrollView ?: return
            val title = find(scroll) { it is TextView && it.text.toString() == "STUDY" } ?: return
            var y = 0; var v: View = title
            while (v !== scroll) { y += v.top; v = v.parent as View }
            scroll.scrollTo(0, y - 4)
        }
    }

    private fun studyPage(ctx: Context): View {
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val v = pageScreen(ShotCtx(ctx), sp)
        sp.open(com.husarp.ankigo.ui.SettingsSection.STUDY) // the Study section of the Settings tab
        return v
    }

    @Test fun settingsRow() {
        val phone = Variant("phone", 412, 900); val tablet = Variant("tablet", 800, 1000)
        paparazzi.shootPage("swipe-settings", listOf(phone, tablet)) { ctx ->
            ToStudy(ctx, studyPage(ctx))
        }
    }
}
