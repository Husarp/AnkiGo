package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R9-B6: the moves section of the details: no "—" cells, one move open with its plain-words text, a status move without
 * power and accuracy. PNGs: r9movetext-<open move>-<size>-<theme>.
 */
class R9MoveTextShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val ids = listOf("thunderbolt", "thunderwave", "swordsdance", "seismictoss", "fissure", "splash", "quickattack", "doubleedge", "recover", "absorb")

    /** The section card as the details show it, [open] = the id whose row is tapped open (null = none). */
    private fun view(ctx: Context, open: String?, compact: Boolean): View {
        val sv = com.husarp.ankigo.ui.TapWatchScroll(ctx)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
        }
        card.addView(DetailSections.moves(ctx, GameRepo.data!!, ids, compact))
        sv.addView(card, ViewGroup.LayoutParams(-1, -2))
        val root = LinearLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
            addView(sv, LinearLayout.LayoutParams(-1, -1))
        }
        if (open != null) {
            // a real tap on the row: click the clickable row whose description starts with the move's name
            val name = GameRepo.data!!.move(open)!!.name
            rowOf(card, name)?.performClick()
        }
        return root
    }

    private fun rowOf(v: View, name: String): View? {
        if (v.isClickable && v.contentDescription?.startsWith("$name,") == true) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) rowOf(v.getChildAt(i), name)?.let { return it }
        return null
    }

    @Test fun closed() = paparazzi.shoot("r9movetext-closed", listOf(Size.PHONE, Size.FLOAT)) { view(it, null, false) }
    @Test fun openDamage() = paparazzi.shoot("r9movetext-open-damage", listOf(Size.PHONE, Size.FLOAT)) { view(it, "thunderbolt", false) }
    @Test fun openStatusNoNumbers() = paparazzi.shoot("r9movetext-open-status", listOf(Size.PHONE, Size.FLOAT)) { view(it, "swordsdance", true) }
    @Test fun openRule() = paparazzi.shoot("r9movetext-open-rule", listOf(Size.FLOAT)) { view(it, "fissure", true) }

    /** A plain tap on the details (not on a row) closes the open text; a tap on the open row closes it; a tap on another row switches. */
    @Test fun tapAnywhereCloses() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val root = view(ctx, "thunderbolt", false)
        val frame = android.widget.FrameLayout(ctx).apply { addView(root, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r9movetext-tap-probe") // lays everything out
        val sv = findScroll(root)!!
        fun open() = texts(root).count { it.visibility == View.VISIBLE && it.text.toString().endsWith("(10 %).") }
        assertEquals(1, open())
        fun tap(x: Float, y: Float) {
            val t = android.os.SystemClock.uptimeMillis()
            sv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
            sv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 40, android.view.MotionEvent.ACTION_UP, x, y, 0))
        }
        // on the card's bottom padding / legend: not a row
        tap(sv.width / 2f, sv.getChildAt(0).bottom - 4f)
        assertEquals(0, open())
        // open it again through its row, then the same row closes it
        rowOf(root, "Thunderbolt")!!.performClick()
        assertEquals(1, open())
        rowOf(root, "Thunderbolt")!!.performClick()
        assertEquals(0, open())
        // another row switches: still one open
        rowOf(root, "Thunderbolt")!!.performClick()
        rowOf(root, "Thunder Wave")!!.performClick()
        assertEquals(1, texts(root).count { it.visibility == View.VISIBLE && it.text.toString().startsWith("Paralyzes") })
        assertEquals(0, open())
    }

    /**
     * Real touch events through the TapWatchScroll for the race of Codex finding 2. The row's own click is delivered like Android does it:
     * queued (after ACTION_UP, `queued = true`, the usual case: View posts PerformClick) or synchronous (before the scroll sees ACTION_UP).
     * A test tap at (x, y) is on a row when it hits the row's bounds (centre of the row).
     */
    @Test fun rowTapRaceWithOutsideTap() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val root = view(ctx, null, false)
        val frame = android.widget.FrameLayout(ctx).apply { addView(root, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r9movetext-race-probe")
        val sv = findScroll(root)!!
        fun openTexts() = texts(root).filter { it.visibility == View.VISIBLE && (it.text.endsWith("%).") || it.text.startsWith("Paralyzes")) }.map { it.text.toString() }
        fun centre(v: View): Pair<Float, Float> {
            var x = v.width / 2f; var y = v.height / 2f; var p: View = v
            while (p !== sv) { x += p.left; y += p.top; p = p.parent as View }
            return x to (y - sv.scrollY)
        }
        fun touch(action: Int, t: Long, x: Float, y: Float) = sv.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, action, x, y, 0))
        /** a tap on [target] (a row, or null = the bottom padding, not a row); [clickRow] = the row's click, run [queued] or inside the gesture. */
        fun tap(target: View?, queued: Boolean) {
            val (x, y) = if (target != null) centre(target) else (sv.width / 2f) to (sv.getChildAt(0).bottom - 4f - sv.scrollY)
            val t = android.os.SystemClock.uptimeMillis()
            touch(android.view.MotionEvent.ACTION_DOWN, t, x, y)
            if (target != null && !queued) target.performClick()
            touch(android.view.MotionEvent.ACTION_UP, t + 40, x, y)
            if (target != null && queued) target.performClick()
        }
        for (queued in listOf(true, false)) {
            val tb = rowOf(root, "Thunderbolt")!!
            val tw = rowOf(root, "Thunder Wave")!!
            assertEquals(0, openTexts().size)
            tap(tb, queued); assertEquals("open row, queued=$queued", 1, openTexts().size)
            tap(tb, queued); assertEquals("same row closes and stays closed, queued=$queued", 0, openTexts().size)
            tap(tb, queued); assertEquals(1, openTexts().size)
            tap(tw, queued)
            assertEquals("another row switches, queued=$queued", listOf(true), openTexts().map { it.startsWith("Paralyzes") })
            tap(null, queued); assertEquals("outside tap closes, queued=$queued", 0, openTexts().size)
            tap(tb, queued); tap(null, queued); tap(tb, queued)
            assertEquals("outside close then the row opens it again, queued=$queued", 1, openTexts().size)
            tap(tb, queued)
            assertEquals(0, openTexts().size)
        }
        // a scroll gesture (moved past the slop) leaves it open, and the next plain tap on a row is not confused by it
        val tb = rowOf(root, "Thunderbolt")!!
        tap(tb, true); assertEquals(1, openTexts().size)
        val t = android.os.SystemClock.uptimeMillis(); val x = sv.width / 2f
        touch(android.view.MotionEvent.ACTION_DOWN, t, x, 300f)
        touch(android.view.MotionEvent.ACTION_MOVE, t + 20, x, 200f)
        touch(android.view.MotionEvent.ACTION_UP, t + 40, x, 200f)
        assertEquals("a scroll keeps it open", 1, openTexts().size)
        tap(tb, true); assertEquals("the row after a scroll closes it", 0, openTexts().size)
    }

    private fun findScroll(v: View): android.widget.ScrollView? {
        if (v is android.widget.ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun texts(v: View, out: MutableList<android.widget.TextView> = ArrayList()): List<android.widget.TextView> {
        if (v is android.widget.TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }
}
