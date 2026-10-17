package com.husarp.ankigo.shots

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.LogSide
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R6-12: the battle text bar flips between your last action and the wild one's (tap or swipe), with a small chevron at the end
 * you can flip towards. PNGs: r612-yours (chevron right) and r612-wild (chevron left) at PHONE / SPLIT / FLOATB, plus the study strip.
 */
class R612LogShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val mine = "Sparkmouse used {{Thunderbolt}}. It's super effective!"
    private val wild = "Wild Gnarlpup used {{Bite}}."

    private fun panel(ctx: Context, small: Boolean, legacy: Boolean = false): BattlePanelView {
        val p = BattlePanelView(ctx)
        if (small) p.setCompact(true)
        if (legacy) p.setStudyLayout(true, false)
        p.renderAll()
        p.setLog(LogKind.NEUTRAL, "WILD", "A wild Gnarlpup appeared!", false, LogSide.WILD, true)
        p.setLog(LogKind.GOOD, "GOOD", mine, false, LogSide.YOURS)
        return p
    }

    private val sizes = listOf(Size.PHONE, Size.SPLIT, Size.FLOATB)

    @Test fun yoursShown() = paparazzi.shootWith("r612-yours", sizes) { ctx -> panel(ctx, small = ctx.resources.configuration.screenHeightDp < 330) }

    @Test fun wildShown() = paparazzi.shootWith("r612-wild", sizes) { ctx ->
        panel(ctx, small = ctx.resources.configuration.screenHeightDp < 330).also { it.setLog(LogKind.AGAIN, "WILD", wild, false, LogSide.WILD) }
    }

    @Test fun strip() {
        paparazzi.shootWith("r612-strip-yours", listOf(Size.PHONE)) { ctx -> panel(ctx, small = false, legacy = true) }
        paparazzi.shootWith("r612-strip-wild", listOf(Size.PHONE)) { ctx ->
            panel(ctx, small = false, legacy = true).also { it.setLog(LogKind.AGAIN, "WILD", wild, false, LogSide.WILD) }
        }
    }

    // ---------------------------------------------------------------- touches

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun bar(p: BattlePanelView): View = BattlePanelView::class.java.getDeclaredField("logBox").apply { isAccessible = true }.get(p) as View
    private fun barText(p: BattlePanelView): String =
        (BattlePanelView::class.java.getDeclaredField("logText").apply { isAccessible = true }.get(p) as android.widget.TextView).text.toString()

    private fun touch(v: View, action: Int, x: Float, y: Float, t: Long = SystemClock.uptimeMillis()): Boolean =
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))

    private fun tap(v: View) { touch(v, MotionEvent.ACTION_DOWN, 20f, 10f); touch(v, MotionEvent.ACTION_UP, 20f, 10f) }
    private fun swipe(v: View, dx: Float) {
        touch(v, MotionEvent.ACTION_DOWN, 100f, 10f)
        touch(v, MotionEvent.ACTION_MOVE, 100f + dx / 2, 10f)
        touch(v, MotionEvent.ACTION_MOVE, 100f + dx, 10f)
        touch(v, MotionEvent.ACTION_UP, 100f + dx, 10f)
    }

    private fun ready(): BattlePanelView {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = panel(c, small = false)
        measure(p, 412, 869)
        return p
    }

    @Test fun aTapFlipsToTheOtherSideAndBack() {
        val p = ready()
        assertTrue(barText(p).startsWith("Sparkmouse used"))
        tap(bar(p))
        assertEquals("Wild one's line", true, barText(p).startsWith("A wild Gnarlpup"))
        tap(bar(p))
        assertTrue(barText(p).startsWith("Sparkmouse used"))
    }

    @Test fun aSwipeEitherWayFlipsOnceAndIsNotATap() {
        val p = ready()
        swipe(bar(p), -200f)
        assertTrue("one flip for a swipe, not two", barText(p).startsWith("A wild Gnarlpup"))
        swipe(bar(p), 200f)
        assertTrue(barText(p).startsWith("Sparkmouse used"))
    }

    @Test fun aNewEventShowsAtOnceAndForgetsTheFlip() {
        val p = ready()
        tap(bar(p))
        p.setLog(LogKind.AGAIN, "WILD", wild, false, LogSide.WILD)
        assertEquals(wild.replace("{{", "").replace("}}", ""), barText(p))
    }

    @Test fun withNoOtherLineTheBarDoesNotTakeTheTouch() {
        val p = ready()
        p.setLog(LogKind.SYS, "NEW", "A wild Rocklet appeared!", false, LogSide.WILD, true)
        val bar = bar(p)
        assertEquals(false, touch(bar, MotionEvent.ACTION_DOWN, 20f, 10f))
        assertTrue(barText(p).startsWith("A wild Rocklet"))
    }
}
