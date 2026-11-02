package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Counts synchronous work rather than treating layoutlib timings as device frame timings. */
class R27ATabCostTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanup() = BattleShotKit.reset()
    private class Counted(c: Context) : FrameLayout(c) {
        var draws = 0; var measures = 0; var layouts = 0
        override fun dispatchDraw(canvas: Canvas) { draws++; super.dispatchDraw(canvas) }
        override fun onMeasure(w: Int, h: Int) { measures++; super.onMeasure(w, h) }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            layouts++; super.onLayout(changed, l, t, r, b)
        }
    }
    @Test fun compareRemovedSnapshotWorkAndIndicatorOnlyPreparation() {
        val c = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        val page = Counted(c).apply { addView(BattlePanelView(c).apply { renderAll() }) }
        page.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1738, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, 824, 1738)
        val measures = page.measures; val layouts = page.layouts
        // This is the same production capture routine the released prepareTab called.
        val capture = UiMotion::class.java.getDeclaredMethod("picture", View::class.java).apply { isAccessible = true }
        val old = capture.invoke(UiMotion, page) as Bitmap
        assertEquals(1, page.draws)
        assertEquals(measures, page.measures); assertEquals(layouts, page.layouts)
        assertEquals(824 * 1738 * 4, old.byteCount)
        val activity = attach(Activity(), c)
        repeat(20) {
            UiMotion.prepareTab(activity, BottomNav.Tab.STUDY, BottomNav.Tab.SETTINGS)
        }
        assertEquals("navigation preparation does not draw the page", 1, page.draws)
        assertEquals(measures, page.measures); assertEquals(layouts, page.layouts)
        val pending = getField(UiMotion, "pending")!!
        assertFalse("no bitmap retained across activities", pending.javaClass.declaredFields.any { Bitmap::class.java.isAssignableFrom(it.type) })
        File(Shots.outDir.parentFile, "r27a-tab-cost.txt").writeText(
            "824x1738 outgoing page: released capture = ${old.byteCount} bitmap bytes, 1 synchronous tree draw, 0 extra measure/layout passes.\n" +
            "20 indicator-only prepareTab calls = 0 bitmap captures, 0 page draws, 0 measure/layout passes.\n" +
            "Released slide blended outgoing bitmap + incoming page for 220ms; removed slide has no page overlay or page alpha/translation animator. Activity construction remains.\n")
        old.recycle()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        UiMotion.prepareTab(activity, BottomNav.Tab.STUDY, BottomNav.Tab.SETTINGS)
        assertNull(getField(UiMotion, "pending"))
    }
}
