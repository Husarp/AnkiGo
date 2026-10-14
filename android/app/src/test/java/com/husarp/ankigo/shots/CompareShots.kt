package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.Compare
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* U-10: the Compare dialog. Owned and Pokédex columns, Now / Fully evolved, scrolled sideways and down, phone / split / float. */
class CompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val float = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val three = listOf(Variants.PHONE, Variants.SPLIT, float)

    private fun find(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (pred(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred, out)
        return out
    }

    /** Opens Compare for [picks] and shoots it on the scrim; [after] may click or scroll once the dialog exists. */
    private fun shoot(name: String, picks: List<ComparePick>, variants: List<Variant> = three, after: (View) -> Unit = {}) {
        paparazzi.shootPage(name, variants) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val d = DialogShots.capture { Compare.open(page, picks) } ?: error("no dialog was shown")
            val w = ctx.resources.configuration.screenWidthDp - 16
            val scene = DialogShots.scene(ctx, d, widthDp = minOf(w, 640))
            after(scene)
            scene
        }
    }

    private fun clickFinal(scene: View) {
        (find(scene, { it is TextView && it.text == "Fully evolved" }).first() as TextView).performClick()
    }

    /** Scrolls the table once it has been laid out (the renderer lays out once, then draws). */
    private fun scrollAfterLayout(scene: View, x: Int, y: Int) {
        val h = find(scene, { it is HorizontalScrollView }).first() as HorizontalScrollView
        val v = find(scene, { it is ScrollView }).first() as ScrollView
        h.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(view: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                h.removeOnLayoutChangeListener(this)
                h.scrollTo(x, 0); v.scrollTo(0, y)
            }
        })
    }

    private val ownedTwo = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"))
    private val mixedFour = listOf(ComparePick.Owned("t1"), ComparePick.Dex(150), ComparePick.Owned("t6"), ComparePick.Dex(3))

    @Test fun ownedTwo() = shoot("compare-owned2", ownedTwo)

    @Test fun mixedFour() = shoot("compare-mixed4", mixedFour)

    @Test fun fullyEvolved() = shoot("compare-evolved", mixedFour) { clickFinal(it) }

    @Test fun threeOwned() = shoot("compare-owned3", listOf(ComparePick.Owned("t3"), ComparePick.Owned("t4"), ComparePick.Owned("t5")),
        listOf(Variants.PHONE))

    @Test fun scrolled() {
        shoot("compare-scrolled", mixedFour, listOf(Variants.PHONE, float)) { scrollAfterLayout(it, 420, 520) }
    }

    @Test fun dexOnly() = shoot("compare-dex", listOf(ComparePick.Dex(25), ComparePick.Dex(133)), listOf(Variants.PHONE)) { clickFinal(it) }
}
