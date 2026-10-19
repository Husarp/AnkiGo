package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AchStyle
import com.husarp.ankigo.ui.StatusBadge
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R9 D4-A / D6-C2: square status markers (tox = two drops) and the tier icons (Legendary sparkle, Mythical straight star). */
class R9IconShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val statuses = listOf("psn", "tox", "brn", "par", "slp", "frz")

    private fun column(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
    }

    private fun row(ctx: Context, build: (LinearLayout) -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        build(this)
    }

    @Test fun statusMarkers() = paparazzi.shoot("r9-status", listOf(Size.SPLIT)) { ctx ->
        val col = column(ctx)
        for (dp in listOf(30, 14, 12)) {
            col.addView(row(ctx) { r -> for (s in statuses) r.addView(StatusBadge(ctx, s), LinearLayout.LayoutParams(Ui.dp(ctx, dp), Ui.dp(ctx, dp)).also { it.marginEnd = Ui.dp(ctx, 10) }) },
                LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(ctx, 12) })
        }
        col
    }

    @Test fun tierIcons() = paparazzi.shoot("r9-tiers", listOf(Size.SPLIT)) { ctx ->
        val col = column(ctx)
        for (dp in listOf(40, 16, 11, 10)) {
            col.addView(row(ctx) { r ->
                for (t in 0..4) r.addView(ImageView(ctx).apply { setImageDrawable(AchStyle.shape(t, AchStyle.tierColor(ctx, t))) },
                    LinearLayout.LayoutParams(Ui.dp(ctx, dp), Ui.dp(ctx, dp)).also { it.marginEnd = Ui.dp(ctx, 12) })
            }, LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(ctx, 12) })
        }
        col
    }

    private fun bounds(p: android.graphics.Path) = RectF().also { p.computeBounds(it, true) }

    @Test fun toxIsNotPsnAndTiersDiffer() {
        val ctx = paparazzi.context
        val psn = StatusBadge(ctx, "psn"); val tox = StatusBadge(ctx, "tox")
        fun glyph(v: StatusBadge) = StatusBadge::class.java.getDeclaredField("glyph").apply { isAccessible = true }.get(v) as android.graphics.Path
        val bp = bounds(glyph(psn)); val bt = bounds(glyph(tox))
        assertNotEquals(bp.toString(), bt.toString())
        assertTrue("two drops are wider than one", bt.width() > bp.width() - 0.01f || bt.height() > bp.height() - 0.01f)
        assertEquals(StatusBadge.fillColor(ctx, "psn"), StatusBadge.fillColor(ctx, "tox"))
        // tier 3 is the sparkle (cubic, 15.2 wide), tier 4 the straight star (<= 14.8 wide); neither is the crown's 12.4 x 12.4 box
        val b3 = bounds(AchStyle.shapePath(3)); val b4 = bounds(AchStyle.shapePath(4))
        assertEquals(0.8f, b3.left, 0.01f); assertEquals(15.2f, b3.right, 0.01f)
        assertEquals(0.6f, b4.left, 0.05f)
        assertNotEquals(b3.toString(), b4.toString())
    }
}
