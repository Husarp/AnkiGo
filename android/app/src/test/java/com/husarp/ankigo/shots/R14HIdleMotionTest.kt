package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.gfx.SpriteView
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** PLAN 205 (R14 H): a SpriteView with a still picture and idleMotion starts its idle animator when shown, and only then. */
class R14HIdleMotionTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun block(): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
        for (y in 30..80) for (x in 20..70) b.setPixel(x, y, 0xFF3366CC.toInt())
    }
    private val still get() = SpriteImage(listOf(block()), intArrayOf(100))
    private val animated get() = SpriteImage(listOf(block(), block()), intArrayOf(100, 100))

    private fun ctx(reduce: Boolean): android.content.Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduce).commit()
        return c
    }

    private fun show(v: SpriteView, name: String): FrameLayout {
        val f = FrameLayout(v.context).apply { addView(v, FrameLayout.LayoutParams(288, 288)) }
        paparazzi.snapshot(f, name, 1000)
        return f
    }

    @Test fun aStillPictureStartsTheIdleMotionWhenShownAndStopsWhenDetached() {
        val c = ctx(reduce = false)
        val v = SpriteView(c).apply { idleMotion = true; setImage(still) }
        assertFalse("not attached yet", v.idleRunning)
        show(v, "r14h-idle-still")
        // Paparazzi attaches the view for the snapshot and detaches it afterwards: it started once, and detaching stopped it
        assertTrue("started while shown", v.idleStarts == 1)
        assertFalse("stopped when detached", v.idleRunning)
    }

    @Test fun itDoesNotRunWithReducedMotionOrWithoutTheFlagOrForAnAnimatedPicture() {
        val reduced = SpriteView(ctx(reduce = true)).apply { idleMotion = true; setImage(still) }
        show(reduced, "r14h-idle-reduced")
        assertTrue(reduced.idleStarts == 0)
        val c = ctx(reduce = false)
        val off = SpriteView(c).apply { setImage(still) }
        show(off, "r14h-idle-off")
        assertTrue(off.idleStarts == 0)
        val anim = SpriteView(c).apply { idleMotion = true; setImage(animated) }
        show(anim, "r14h-idle-animated")
        assertTrue(anim.idleStarts == 0)
    }

    @Test fun theRestPoseDrawsExactlyWhatAViewWithoutTheFlagDraws() {
        val c = ctx(reduce = false)
        fun px(v: SpriteView): IntArray {
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(288, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(288, android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 288, 288)
            val b = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            return IntArray(288 * 288).also { b.getPixels(it, 0, 288, 0, 0, 288, 288) }
        }
        val plain = SpriteView(c).apply { anchorFeet = true; setImage(still) }
        val idle = SpriteView(c).apply { anchorFeet = true; idleMotion = true; setImage(still) }
        // not attached / no animator tick yet: both draw the rest pose
        assertArrayEquals(px(plain), px(idle))
    }
}
