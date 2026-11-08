package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Bond
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Bundled, real filling Love Ball artwork. All levels at 320/360/412 dp, light/dark, font 1.3; also default-font Level 3. */
class R29BondShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View): List<TextView> = all(v).filterIsInstance<TextView>()
    private fun layout(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }
    private fun noClipping(root: View) {
        texts(root).filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }.forEach { text ->
            val l = text.layout ?: return@forEach
            assertTrue("${text.text}: text height", l.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            for (line in 0 until l.lineCount) {
                assertEquals("${text.text}: ellipsis", 0, l.getEllipsisCount(line))
                // getLineMax excludes invisible trailing spaces left at a wrap boundary.
                val available = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
                assertTrue("${text.text}: line $line visible width ${l.getLineMax(line)}, available $available",
                    l.getLineMax(line) <= available + 1)
            }
        }
    }

    @Test fun bondAtEveryLevelAndRequestedWidthAndFont() {
        for (width in listOf(320, 360, 412)) for (theme in Theme.entries) for (scale in listOf(1f, 1.3f)) {
            val levels = if (scale == 1f) listOf(3) else (0..5).toList()
            for (level in levels) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Variant("w$width", width, 1000).device(theme).copy(fontScale = scale))
                val ctx = Shots.files(paparazzi.context)
                val mon = TestData.state(TestData.world()).main!!.apply { bondPoints = if (level == 3) 370 else Bond.thresholds[level] }
                val memory = DetailOpen(object : DetailOpenStore {
                    override fun get(key: String): Boolean = true
                    override fun set(key: String, open: Boolean) {}
                })
                val card = DetailSections.section(ctx, memory, DetailSection.BOND, BondSection.summary(mon)) { BondSection.body(ctx, mon) }
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 12), Ui.dp(ctx, 20), Ui.dp(ctx, 12))
                    addView(card)
                    background = Ui.rounded(Ui.c(ctx, R.color.ag_base), 16, ctx)
                }
                val frame = FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_crust))
                    addView(col, FrameLayout.LayoutParams(Ui.dp(ctx, AppDialogRules.widthDp(width)), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
                }
                layout(ctx, frame)
                paparazzi.snapshot(frame, "r29e-bond-$level-w$width-font${scale.toString().replace('.', '_')}-${theme.name.lowercase()}")
                noClipping(frame)
                val ball = all(card).filterIsInstance<ImageView>().single { it.layoutParams.width == Ui.dp(ctx, 72) }
                assertNotNull(ball.drawable)
                assertEquals(ImageView.ScaleType.FIT_CENTER, ball.scaleType)
                assertEquals(Ui.dp(ctx, 72), ball.height)
                assertTrue(ball.left >= 0 && ball.top >= 0)
                assertTrue(ball.right <= (ball.parent as View).width)
                assertTrue(ball.bottom <= (ball.parent as View).height)
                assertFalse((ball.drawable as android.graphics.drawable.BitmapDrawable).isFilterBitmap)
                assertTrue(card.bottom <= col.height - col.paddingBottom)
                val content = texts(card).joinToString("\n") { it.text }
                assertTrue(content.contains("Level $level")); assertFalse(content.contains("No decay"))
                assertEquals(level >= 2, content.contains("All species matchups revealed"))
                if (level == 3) assertTrue(content.contains("120 / 250 points to Level 4"))
                if (level == 5) { assertTrue(content.contains("Max Bond")); assertFalse(content.contains("Next ·")) }
            }
        }
    }

    @Test fun fillingBallHasAlignedBoundsAndBottomUpColourAtEveryLevel() {
        val ctx = Shots.files(paparazzi.context)
        val ids = listOf(R.drawable.bond_0, R.drawable.bond_1, R.drawable.bond_2,
            R.drawable.bond_3, R.drawable.bond_4, R.drawable.bond_5)
        val images = ids.map { android.graphics.BitmapFactory.decodeResource(ctx.resources, it,
            android.graphics.BitmapFactory.Options().apply { inScaled = false })!! }
        val depths = listOf(0, 4, 7, 11, 14, 18)
        val full = images.last()
        for ((level, image) in images.withIndex()) {
            assertEquals(144, image.width); assertEquals(144, image.height)
            var coloured = 0
            for (y in 0 until 144) for (x in 0 until 144) {
                val pixel = image.getPixel(x, y)
                val original = full.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                assertEquals(android.graphics.Color.alpha(original), alpha)
                assertTrue(alpha == 0 || alpha == 255)
                if (alpha == 0) continue
                assertTrue(x in 18 until 126 && y in 18 until 126)
                val grey = android.graphics.Color.red(pixel) == android.graphics.Color.green(pixel) &&
                    android.graphics.Color.green(pixel) == android.graphics.Color.blue(pixel)
                if (y < 126 - depths[level] * 6) assertTrue("level $level row $y", grey)
                else assertEquals(original, pixel)
                if (!grey) coloured++
            }
            if (level == 0) assertEquals(0, coloured) else assertTrue(coloured > 0)
            if (level > 0) assertFalse(image.sameAs(images[level - 1]))
        }
    }

    @Test fun orderEditorArrowsDragHandlesResetAndSavedOrder() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant("w320", 320, 1000).device(Theme.LIGHT).copy(fontScale = 1.3f))
        val ctx = Shots.files(paparazzi.context)
        AppPrefs.setDetailOrder(ctx, DetailOrder.default)
        val editor = DetailOrderSettings.Editor(ctx, AppPrefs.detailOrder(ctx))
        texts(editor).single { it.contentDescription == "Move Bond up" }.performClick()
        assertEquals(DetailOrder.default.indexOf(DetailSection.BOND) - 1, editor.order.indexOf(DetailSection.BOND))
        assertEquals(DetailOrder.default, AppPrefs.detailOrder(ctx)) // draft only
        assertTrue(texts(editor).single { it.contentDescription == "Drag Bond" }.isLongClickable)
        editor.move(DetailSection.BOND, 0) // the drop handler uses this same path
        AppPrefs.setDetailOrder(ctx, editor.order)
        assertEquals(DetailSection.BOND, AppPrefs.detailOrder(ctx).first())
        texts(editor).single { it.text.toString() == "Reset to default" }.performClick()
        assertEquals(DetailOrder.default, editor.order)
        AppPrefs.setDetailOrder(ctx, editor.order)
        assertEquals(DetailOrder.default, AppPrefs.detailOrder(ctx))
        val frame = FrameLayout(ctx).apply { addView(editor, FrameLayout.LayoutParams(-1, -2)) }
        layout(ctx, frame); noClipping(frame)
        paparazzi.snapshot(frame, "r29e-details-order-w320-font1_3")
    }

    @Test fun orderAppliesToOwnedWildAndSpeciesSectionsWithoutMovingHeaderOrFooter() {
        val ctx = Shots.files(paparazzi.context)
        val wanted = DetailOrder.resolve(listOf("info", "bond", "moves", "evolution", "stats"))
        AppPrefs.setDetailOrder(ctx, wanted)
        for (available in listOf(DetailOrder.default, listOf(DetailSection.EVOLUTION, DetailSection.STATS, DetailSection.MOVES, DetailSection.MATCHUPS, DetailSection.INFO))) {
            val col = LinearLayout(ctx)
            val header = View(ctx); val footer = View(ctx)
            col.addView(header)
            available.forEach { s -> col.addView(View(ctx).apply { tag = s }) }
            col.addView(footer)
            DetailSections.applyOrder(col)
            assertSame(header, col.getChildAt(0)); assertSame(footer, col.getChildAt(col.childCount - 1))
            assertEquals(wanted.filter { it in available }, (1 until col.childCount - 1).map { col.getChildAt(it).tag })
        }
    }
}
