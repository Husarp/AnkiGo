package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R9-C7: "Options" is pinned under the scrolling details (AppDialog footer). PNGs: r9moreopt-s<0|1>-<size>-<theme>.
 * The button's rect (in the scene) is recorded at draw time per scroll position and size: it is inside the dialog, below the
 * details' scroll view, 48 dp high, and at the same place at the top and in the middle.
 */
class R9MoreOptionsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE, Variants.SPLIT, Variant("land", 869, 412))
    private val rects = HashMap<String, IntArray>() // "<size>-<frac>" -> top, bottom, scrollBottom, scrollRange, sceneH, density*100

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    private fun findText(v: View, s: String): TextView? {
        if (v is TextView && v.text.toString() == s) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), s)?.let { return it }
        return null
    }

    private fun top(v: View, root: View): Int { var y = 0; var p = v; while (p !== root) { y += p.top - ((p.parent as? ScrollView)?.scrollY ?: 0); p = p.parent as View }; return y }

    @Test fun pinned() {
        listOf(0f, 0.5f).forEachIndexed { k, frac ->
            variants.forEach { var_ ->
                paparazzi.shootPage("r9moreopt-s$k", listOf(var_)) { ctx ->
                    val st = GameRepo.state!!
                    val mon = st.mon("t1")!!
                    // long content: all the moves, sections open come from the saved memory (default closed), so many moves + a long nickname
                    val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, mon) } ?: error("no dialog was shown")
                    val scene = DialogShots.scene(ctx, d)
                    object : FrameLayout(ctx) {
                        override fun dispatchDraw(canvas: android.graphics.Canvas) {
                            val sc = scrolls(scene).maxByOrNull { it.getChildAt(0)?.height ?: 0 }!!
                            val range = (sc.getChildAt(0).height - sc.height).coerceAtLeast(0)
                            sc.scrollTo(0, (range * frac).toInt())
                            val more = findText(scene, "Options")!!
                            val t = top(more, scene)
                            val sTop = top(sc, scene)
                            rects["${var_.label}-$k"] = intArrayOf(t, t + more.height, sTop + sc.height, range, scene.height, (ctx.resources.displayMetrics.density * 100).toInt())
                            super.dispatchDraw(canvas)
                        }
                    }.apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }
                }
            }
        }
        for (v in variants) {
            val a = rects["${v.label}-0"]!!; val b = rects["${v.label}-1"]!!
            assertEquals("button moved with the scroll (${v.label})", a[0], b[0])
            assertTrue("button below the scroll view (${v.label})", a[0] >= a[2])
            assertTrue("button inside the scene (${v.label})", a[1] <= a[4] && a[0] >= 0)
            assertTrue("48 dp high (${v.label})", a[1] - a[0] >= 48 * a[5] / 100)
        }
    }
}
