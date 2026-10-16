package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import org.junit.Rule
import org.junit.Test

/** R2-11: Pokédex entry dialog with the Lv 100 stat ranges, Options / Details for owned ones, the copy picker, the Options menu. */
class R211Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", 340, 480)
    private val variants = listOf(Variants.PHONE, FLOAT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private fun find(v: View, text: String): TextView? {
        if (v is TextView && v.text.toString() == text) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    private fun scrolled(ctx: Context, scene: View, frac: Float): View = object : FrameLayout(ctx) {
        override fun dispatchDraw(canvas: android.graphics.Canvas) {
            scrolls(scene).maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { sc ->
                sc.scrollTo(0, ((sc.getChildAt(0).height - sc.height).coerceAtLeast(0) * frac).toInt())
            }
            super.dispatchDraw(canvas)
        }
    }.apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }

    private fun open(ctx: Context, num: Int) = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), gd.sp(num)) } ?: error("no dialog")

    private fun twoCopies() {
        val a = TestData.mon(gd, 25, 31, uid = "x1").also { it.nickname = "Zappy" }
        val b = TestData.mon(gd, 25, 7, uid = "x2").also { it.shiny = true }
        st.mons += a; st.mons += b
    }

    @Test fun notOwnedAndOwned() {
        for ((k, f) in listOf(0f, 1f).withIndex()) {
            paparazzi.shootPage("r211-notowned-s$k", variants) { ctx -> scrolled(ctx, DialogShots.scene(ctx, open(ctx, 150)), f) }
            paparazzi.shootPage("r211-owned-s$k", variants) { ctx -> scrolled(ctx, DialogShots.scene(ctx, open(ctx, 25)), f) }
        }
    }

    @Test fun copyPicker() {
        paparazzi.shootPage("r211-picker", variants) { ctx ->
            twoCopies()
            val scene = DialogShots.scene(ctx, open(ctx, 25))
            val picker = DialogShots.capture { find(scene, "Options")!!.performClick() } ?: error("no picker")
            DialogShots.scene(ctx, picker)
        }
    }

    @Test fun optionsMenu() {
        paparazzi.shootPage("r211-options", variants) { ctx ->
            val scene = DialogShots.scene(ctx, open(ctx, 25))
            MenuKit.scene(ctx, MenuKit.capture { find(scene, "Options")!!.performClick() })
        }
    }
}
