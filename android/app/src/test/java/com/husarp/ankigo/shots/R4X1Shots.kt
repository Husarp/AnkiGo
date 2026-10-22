package com.husarp.ankigo.shots

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareState
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.DownloadsDone
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R4-X1 (0.12 feedback): Compare zoom (3..6 columns), the red-to-green value fill, a column lifted mid-drag and dropped,
 * and the green "Downloaded ✓" Settings state. PNGs: r4x1-<what>-<size>-<theme>.
 */
class R4X1Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val five = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150), ComparePick.Owned("t6"), ComparePick.Dex(3))
    private val nine = listOf(
        ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Owned("t4"), ComparePick.Owned("t5"),
        ComparePick.Owned("t6"), ComparePick.Owned("b3"), ComparePick.Dex(150), ComparePick.Dex(3),
    )

    private fun find(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (pred(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred, out)
        return out
    }

    private fun page(ctx: Context, picks: List<ComparePick>, mode: CompareMode, fit: Int, seed: (CompareState) -> Unit = {}): ComparePage {
        CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, RowChoice()); CompareStore.saveFit(ctx, fit)
        return ComparePage(ctx, NoHost, picks).also { it.onNewState = seed; it.fit = fit } // R9 C1: the screen starts on Auto, the shot sets what it shows
    }

    private fun resetFit() = CompareStore.saveFit(Shots.files(paparazzi.context), CompareFit.AUTO)

    @Test fun zoom() {
        try {
            for (fit in CompareFit.OPTIONS) paparazzi.shootPage("r4x1-zoom$fit", listOf(Variants.PHONE)) { ctx -> pageScreen(ctx, page(ctx, nine, CompareMode.BEST_OF_ALL, fit)) }
            paparazzi.shootPage("r4x1-zoom-vs4", listOf(Variants.PHONE)) { ctx -> pageScreen(ctx, page(ctx, nine, CompareMode.VS_REFERENCE, 4)) }
        } finally { resetFit() }
    }

    @Test fun bestOfAllScale() {
        paparazzi.shootPage("r4x1-best5", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> pageScreen(ctx, page(ctx, five, CompareMode.BEST_OF_ALL, CompareFit.AUTO)) }
    }

    @Test fun vsNoPlusMinusZero() {
        paparazzi.shootPage("r4x1-vs5", listOf(Variants.PHONE)) { ctx -> pageScreen(ctx, page(ctx, five, CompareMode.VS_REFERENCE, CompareFit.AUTO)) }
    }

    private fun touch(v: View, action: Int, rawX: Float, downTime: Long) {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, rawX, 20f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    /**
     * Long-press the head of column [pos], drag it [dxDp] to the right and shoot it lifted; with [drop] lift the finger too (the
     * order then changes). The page is drawn once by hand (no message-loop render afterwards, which would throw the lifted state away).
     */
    private fun drag(name: String, mode: CompareMode, pos: Int, dxDp: Float, drop: Boolean, variants: List<Variant> = listOf(Variants.PHONE)) {
        paparazzi.shootPage(name, variants) { ctx ->
            var st: CompareState? = null
            val p = page(ctx, nine, mode, CompareFit.AUTO) { st = it }
            val root = p.root
            val doRender = com.husarp.ankigo.ui.GamePage::class.java.getDeclaredMethod("doRender").apply { isAccessible = true }
            doRender.invoke(p)
            val screen = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(com.husarp.ankigo.ui.Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
                addView(root, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
            }
            val heads = find(root, { it.contentDescription?.contains("Tap for options.") == true })
            assertEquals(9, heads.size)
            val head = heads[pos]
            val table = { com.husarp.ankigo.ui.CompareTable.build(com.husarp.ankigo.GameRepo.data!!, com.husarp.ankigo.GameRepo.state!!, nine, false) }
            val before = st!!.display(table())
            val t0 = SystemClock.uptimeMillis()
            val x0 = 300f
            touch(head, MotionEvent.ACTION_DOWN, x0, t0)
            assertTrue("long press starts the drag", head.performLongClick())
            val dx = dxDp * ctx.resources.displayMetrics.density
            touch(head, MotionEvent.ACTION_MOVE, x0 + dx, t0)
            assertTrue("the head follows the finger: ${head.translationX}", head.translationX > 0f || drop)
            if (drop) {
                touch(head, MotionEvent.ACTION_UP, x0 + dx, t0)
                val after = st!!.display(table())
                assertTrue("the order changed: $before -> $after", before != after)
                doRender.invoke(p) // the page redraws itself after a drop: show the new order
            }
            // a render queued by the page would rebuild the table (and drop the lifted column) before the picture is taken
            setField(p, "alive", false)
            screen
        }
    }

    @Test fun dragLifted() = drag("r4x1-drag-lifted", CompareMode.BEST_OF_ALL, 1, 140f, drop = false)
    @Test fun dragDropped() = drag("r4x1-drag-dropped", CompareMode.BEST_OF_ALL, 1, 140f, drop = true)
    @Test fun dragVsLifted() = drag("r4x1-drag-vs", CompareMode.VS_REFERENCE, 2, 130f, drop = false)

    @Test fun downloadsDone() {
        paparazzi.shootPage("r4x1-downloads-done", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            DownloadsDone.mark(c, DownloadsDone.PICTURES, Tuning.DEFAULT_GENS)
            DownloadsDone.mark(c, DownloadsDone.EVERYTHING, Tuning.DEFAULT_GENS)
            try {
                val pg = SettingsPage(c, NoHost)
                val v = pageScreen(c, pg)
                pg.open(SettingsSection.DOWNLOADS)
                v
            } finally { DownloadsDone.clear(c) }
        }
    }
}
