package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.SpriteZoomView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 213 (R15-G): a 120 x 110 stand-in "3D animation" (a 1-px red / blue checkerboard, the worst case for smoothing) drawn by the
 * arena's [SpriteView] and the full-screen viewer's [SpriteZoomView]: every drawn pixel is one of the two pure colours or transparent
 * (never a blend), in light and dark. PNGs: r15g-<where>-<case>-<theme>.
 */
class R15GCrispShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val red = 0xFFD03030.toInt()
    private val blue = 0xFF3050D0.toInt()

    private fun checker(w: Int, h: Int): SpriteImage {
        val px = IntArray(w * h) { i -> if ((i % w + i / w) % 2 == 0) red else blue }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return SpriteImage(listOf(bmp), intArrayOf(0))
    }

    private fun render(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    /** Pixels drawn that are neither of the two pure colours nor fully transparent. */
    private fun blended(b: Bitmap): Int {
        var n = 0
        val row = IntArray(b.width)
        for (y in 0 until b.height) {
            b.getPixels(row, 0, b.width, 0, y, b.width, 1)
            for (c in row) if (c != red && c != blue && (c ushr 24) != 0) n++
        }
        return n
    }

    private fun sprite(side: Int): SpriteView {
        val ctx = paparazzi.context
        return SpriteView(ctx).apply {
            byFrame = true
            setImage(checker(120, 110))
            measure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY))
            layout(0, 0, side, side)
        }
    }

    @Test fun arenaSpriteIsNeverBlended() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            // frame 264 px: fit 2.2 -> whole 2 ; frame 300 px: fit 2.5 stays fractional (still nearest neighbour)
            for (side in listOf(264, 300)) {
                val v = sprite(side)
                paparazzi.snapshot(v, "r15g-arena-$side-${theme.name.lowercase()}", 1000)
                val bmp = render(v)
                assertEquals("side $side ${theme.name}", 0, blended(bmp))
                var drawn = 0
                for (y in 0 until bmp.height) for (x in 0 until bmp.width) if ((bmp.getPixel(x, y) ushr 24) != 0) drawn++
                assertTrue("something is drawn at $side", drawn > 20000)
            }
        }
    }

    @Test fun viewerSpriteIsNeverBlended() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val v = SpriteZoomView(paparazzi.context).apply {
                setImage(checker(120, 110))
                measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
                layout(0, 0, 824, 1200)
            }
            paparazzi.snapshot(v, "r15g-viewer-${theme.name.lowercase()}", 1000)
            assertEquals(theme.name, 0, blended(render(v)))
        }
    }
}
