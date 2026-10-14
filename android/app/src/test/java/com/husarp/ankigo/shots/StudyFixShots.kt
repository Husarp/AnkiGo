package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** U-01: the answer row that never jumps, and the battle panel's drag bar at free heights. */
class StudyFixShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private enum class St { ANSWER, TAPPED, LOADING, NEXT }

    /** Top of [v] in the root's coordinates (px). */
    private fun topIn(root: View, v: View): Int {
        var t = 0; var x: View = v
        while (x !== root) { t += x.top; x = x.parent as View }
        return t
    }

    private fun measure(root: View, size: Size, ctx: Context) {
        val d = ctx.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /** Positions that must not move between the card states: [panel top, panel height, buttons top, buttons height, handle top]. */
    private fun spots(act: StudyActivity, root: View): List<Int> {
        val panel = getField(act, "panel") as View; val buttons = getField(act, "buttons") as View; val handle = getField(act, "handleBar") as View
        return listOf(topIn(root, panel), panel.height, topIn(root, buttons), buttons.height, topIn(root, handle))
    }

    private fun screen(ctx: Context, state: St, panelDp: Int = -1, buttons: Int = 4): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", 1); setField(act, "panelDp", panelDp)
        val root = act.buildContent()
        val times = listOf("<1m", "6m", "1d", "4d").take(buttons)
        setField(act, "card", Anki.Review(10L, 0, buttons, times, emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>"); setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", state != St.NEXT); setField(act, "locked", false)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        when (state) {
            St.ANSWER, St.NEXT -> {}
            // answer(): locked, buttons disabled but still there
            St.TAPPED -> { setField(act, "locked", true); call(act, "showButtons") }
            // loadNext(): the old buttons stay disabled, no card yet
            St.LOADING -> {
                setField(act, "locked", true); call(act, "showButtons")
                call(act, "disableButtons", getField(act, "buttons"))
                setField(act, "card", null); call(act, "layout")
            }
        }
        if (state == St.NEXT) call(act, "showButtons")
        return act to root
    }

    /** Answer side -> tapped -> loading -> next question: nothing above the answer row may move. */
    @Test fun answerRowNeverJumps() {
        val seen = HashMap<String, MutableMap<St, List<Int>>>()
        for (st in St.entries) paparazzi.shootWith("studyfix-seq-${st.name.lowercase()}", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx, st)
            measure(root, Size.PHONE, ctx)
            val theme = if (ctx.resources.configuration.uiMode and 0x30 == 0x20) "dark" else "light"
            seen.getOrPut(theme) { HashMap() }[st] = spots(act, root)
            root
        }
        for ((theme, byState) in seen) {
            val first = byState.getValue(St.ANSWER)
            assertTrue("panel has a size", first[1] > 0 && first[3] > 0)
            for (st in St.entries) assertEquals("$theme $st: panel top/height, buttons top/height, handle top", first, byState.getValue(st))
        }
    }

    /** The 2-button and 3-button answer rows keep the same container too. */
    @Test fun answerRowSameForEveryButtonCount() {
        val seen = ArrayList<List<Int>>()
        for (n in listOf(2, 3, 4)) paparazzi.shootWith("studyfix-buttons$n", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx, St.ANSWER, buttons = n)
            measure(root, Size.PHONE, ctx); seen += spots(act, root); root
        }
        assertEquals(1, seen.toSet().size)
    }

    @Test fun draggedHeights() {
        for (dp in listOf(34, 120, 260)) paparazzi.shootWith("studyfix-drag-$dp", listOf(Size.PHONE)) { ctx ->
            val (act, root) = screen(ctx, St.NEXT, panelDp = dp)
            call(act, "setPanelDp", dp, false)
            measure(root, Size.PHONE, ctx)
            val panel = getField(act, "panel") as View
            assertEquals(dp, Math.round(panel.height / ctx.resources.displayMetrics.density))
            root
        }
    }

    /** Saved height is clamped to the window, remembered, and a tap cycles Full > Strip > Mini > Full. */
    @Test fun heightIsRememberedAndTapCycles() {
        paparazzi.shootWith("studyfix-tap", listOf(Size.PHONE), withSprites = false) { ctx ->
            val (act, root) = screen(ctx, St.NEXT, panelDp = 220)
            call(act, "layout")
            val prefs = act.getSharedPreferences("study", 0)
            val bar = getField(act, "handleBar") as View
            val seq = ArrayList<Int>()
            repeat(4) { bar.performClick(); seq += (getField(act, "panelDp") as Int) }
            assertEquals(listOf(180, 34, 220, 180), seq)
            assertEquals(180, prefs.getInt("panelDp", -1))
            call(act, "setPanelDp", 9000, true)
            assertTrue("clamped to leave the card area", prefs.getInt("panelDp", -1) < 700)
            assertTrue(prefs.getInt("panelDp", -1) > 400)
            root
        }
    }

    @Test fun tablet() {
        paparazzi.shootWith("studyfix-tablet", listOf(Size.TABLET)) { ctx -> screen(ctx, St.NEXT, panelDp = 400).second }
    }
}
