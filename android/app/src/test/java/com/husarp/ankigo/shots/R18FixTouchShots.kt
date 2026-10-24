package com.husarp.ankigo.shots

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R18 RV-8: the ball chip's 48 dp touch area reaches above and below the chip row (the delegate sits on the footer, not on the 21 dp row). */
class R18FixTouchShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun find(v: View, desc: String): View? {
        if (v.contentDescription == desc) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), desc)?.let { return it }
        return null
    }

    private fun absTop(v: View, root: View): Float { var t = 0f; var c: View? = v; while (c != null && c !== root) { t += c.top; c = c.parent as? View }; return t }
    private fun absLeft(v: View, root: View): Float { var t = 0f; var c: View? = v; while (c != null && c !== root) { t += c.left; c = c.parent as? View }; return t }

    @Test fun theChipTakesTouchesTwelveDpAboveAndBelowItsRow() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val panel = BattlePanelView(ctx)
        val d = ctx.resources.displayMetrics.density
        panel.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        panel.renderAll()
        panel.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val chip = find(panel, "Poké Balls")!!
        assertTrue("the chip is shown and clickable", chip.visibility == View.VISIBLE && chip.isClickable && chip.width > 0)
        assertEquals("the panel is not scaled in this window", 1f, (panel.getChildAt(0) as View).scaleX, 0.001f)
        var downs = 0
        chip.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) downs++; true }
        val cx = absLeft(chip, panel) + chip.width / 2f
        val top = absTop(chip, panel)
        val bottom = top + chip.height

        fun tap(y: Float): Int {
            val before = downs
            val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, cx, y, 0)
            panel.dispatchTouchEvent(down); down.recycle()
            val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, cx, y, 0)
            panel.dispatchTouchEvent(up); up.recycle()
            return downs - before
        }
        val footer = chip.parent.parent as ViewGroup
        assertTrue("the footer holds the delegate", footer.touchDelegate != null)
        assertEquals("on the chip", 1, tap(top + chip.height / 2f))
        // the chip row is only 23 dp tall: the touch area reaches into the 4 dp gap above it and the footer's 9 dp padding below it
        // (up to the footer's edges; the goal row's own buttons keep their touches above)
        assertEquals("3 dp above the row", 1, tap(top - 3 * d))
        assertEquals("6 dp below the row", 1, tap(bottom + 6 * d))
        assertEquals("well above the touch area", 0, tap(top - 30 * d))
    }
}
