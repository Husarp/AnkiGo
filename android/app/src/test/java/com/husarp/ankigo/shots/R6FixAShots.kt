package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/* R6-FIX A3: a custom view that scrolls by itself (the picker's ListView, a Bits.scroll) sits directly in the dialog card with the remaining height. */
class R6FixAShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.FLOATBATTLE)

    private fun find(v: View, cls: Class<*>): View? {
        if (cls.isInstance(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), cls)?.let { return it }
        return null
    }

    @Test fun pickerWithTwentyRowsShowsManyRows() {
        paparazzi.shoot("r6fix-picker-20", sizes) { ctx ->
            val gd = GameRepo.data!!
            val mons = GameRepo.state!!.mons.let { m -> (0 until 20).map { m[it % m.size] } }
            val d = AppDialogShots.capture { Pickers.pickMon(ctx, "Teach TM Body Slam to…", gd, mons, { "Lv ${it.level}" }) {} }!!
            val scene = AppDialogShots.scene(ctx, d)
            val dm = ctx.resources.displayMetrics
            scene.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
            scene.layout(0, 0, dm.widthPixels, dm.heightPixels)
            val list = find(scene, ListView::class.java)!!
            // a tall window shows many rows; the 300 dp floating card, with title and buttons around it, at least two
            val rows = if (dm.heightPixels / dm.density >= 600) 8 else 2
            assertTrue("list is only ${list.measuredHeight} px tall", list.measuredHeight >= Ui.dp(ctx, 48) * rows)
            assertTrue("list is not inside the dialog's own ScrollView", list.parent !is ScrollView && (list.parent as View).parent !is ScrollView)
            scene
        }
    }

    @Test fun scrollInfoViewKeepsItsLook() {
        paparazzi.shoot("r6fix-info-scroll", sizes) { ctx ->
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), 0, Ui.dp(ctx, 24), 0)
                for (i in 1..30) addView(Ui.text(ctx, 14f, 700, com.husarp.ankigo.R.color.ag_text).apply { text = "Line $i of the entry text." })
            }
            val d = AppDialogShots.capture { Pickers.info(ctx, "Entry", Bits.scroll(ctx, col)) }!!
            AppDialogShots.scene(ctx, d)
        }
    }
}
