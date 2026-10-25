package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropSharp
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.Stage
import com.husarp.ankigo.ui.StandPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-F (PLAN 274): the sharp drawing of [ArenaBackdrop] on real canvases (pixel checks, no PNGs): a picture that lands at or above
 * [BackdropSharp.HARD_MIN] screen px per file px has hard edges (every pixel is one of the picture's own colours), one that lands under
 * it is first drawn the old way and then replaced by the Lanczos copy (made in place here, held and run by hand for the "not yet" half),
 * the copy follows a new stage / screen scale / size, and a wrong or missing copy never breaks the drawing.
 */
class R19FBackdropSharpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        Backdrops.forget()
        BackdropSharp.runner = BackdropSharp.InPlace // the real runner needs a looper: tests never leave it behind
    }

    private val noGet: HttpGet = { _, _ -> null }
    private val left = 0xFFC80000.toInt()   // (200, 0, 0)
    private val right = 0xFF0000C8.toInt()  // (0, 0, 200)

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private fun setUp(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        BackdropSharp.runner = BackdropSharp.InPlace
        return ctx
    }

    /** A 300 x 200 picture: the left half red, the right half blue ([put] can add a one-pixel checkerboard in the middle third). */
    private fun bd(id: String) = Backdrop(
        id, BackdropSource.OWN, "png", 300, 200, StandPoint(.3f, .8f, .4f), StandPoint(.7f, .6f, .3f), crisp = true, dark = false,
    )

    private fun put(ctx: Context, bg: Backdrop, checker: Boolean = false) {
        val img = FakeSprites.Img(bg.w, bg.h)
        for (y in 0 until bg.h) for (x in 0 until bg.w) {
            img.px[y * bg.w + x] = when {
                checker && x in 100..199 -> if ((x + y) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                x < bg.w / 2 -> left
                else -> right
            }
        }
        FakeSprites.write(img, Backdrops.file(Sprites.dir(ctx), bg))
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
    }

    private fun drawn(back: ArenaBackdrop, w: Int, h: Int, scale: Float = 1f): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        back.draw(Canvas(bmp).also { it.scale(scale, scale) })
        return bmp
    }

    private fun row(bmp: Bitmap, y: Int) = IntArray(bmp.width) { bmp.getPixel(it, y) }

    /** A window of [cropW] x [cropH] picture pixels at the picture's top left (no shrink, no fade), the stage's feet are not used by the backdrop. */
    private fun stage(cropW: Float, cropH: Float, x: Float = 0f, y: Float = 0f) =
        Stage(x, y, cropW, cropH, shrunk = false, playerFeetX = 0f, playerFeetY = 0f, playerFrame = 1f, wildFeetX = 0f, wildFeetY = 0f, wildFrame = 1f)

    @Test fun aPictureAtOrAboveTheThresholdIsDrawnWithHardEdgesAndNeedsNoCopy() {
        val ctx = setUp()
        val bg = bd("r19f-hard"); put(ctx, bg)
        val back = ArenaBackdrop(ctx).apply { reduceMotion = { true }; screenScale = { 1f } }
        back.request(bg, false)
        back.setStage(stage(300f, 200f))
        back.setBounds(0, 0, 465, 310) // 1.55 screen px per file px, not a whole number
        val bmp = drawn(back, 465, 310)
        for (y in listOf(0, 100, 309)) for (p in row(bmp, y)) assertTrue("only the picture's colours (got ${Integer.toHexString(p)})", p == left || p == right)
        // the edge is where the file's is, scaled (232.5 of 465)
        assertTrue(row(bmp, 50)[200] == left && row(bmp, 50)[270] == right)
    }

    @Test fun aReductionIsFirstDrawnTheOldWayThenReplacedByTheLanczosCopy() {
        val ctx = setUp()
        val bg = bd("r19f-down"); put(ctx, bg, checker = true)
        val held = ArrayList<Runnable>()
        BackdropSharp.runner = object : BackdropSharp.Runner {
            override fun background(job: Runnable) { held += job }
            override fun main(delayMs: Long, job: Runnable) { held += job }
        }
        val back = ArenaBackdrop(ctx).apply { reduceMotion = { true }; screenScale = { 1f } }
        back.request(bg, false)
        back.setStage(stage(300f, 200f))
        back.setBounds(0, 0, 100, 66) // 0.33 screen px per file px: a reduction
        val before = drawn(back, 100, 66)
        assertNotNull(before)
        assertTrue("the copy is asked for, not yet made", held.isNotEmpty())
        // run what was asked for (the debounce, the work, the hand-back): the copy is there and is drawn
        var guard = 0
        while (held.isNotEmpty() && guard++ < 20) { val jobs = held.toList(); held.clear(); jobs.forEach { it.run() } }
        val after = drawn(back, 100, 66)
        // the middle third (file x 100..199) is a one-pixel checkerboard: a good reduction averages it to mid grey; the edge of red / blue stays hard
        for (x in 40..60) assertEquals("grey at $x", 127.5f, r(after.getPixel(x, 30)).toFloat(), 12f)
        assertTrue(r(after.getPixel(10, 30)) >= 190 && b(after.getPixel(90, 30)) >= 190)
    }

    @Test fun theCopyFollowsAnotherStageAndAnotherScreenScale() {
        val ctx = setUp()
        val bg = bd("r19f-follow"); put(ctx, bg)
        var k = 1f
        val back = ArenaBackdrop(ctx).apply { reduceMotion = { true }; screenScale = { k } }
        back.request(bg, false)
        back.setStage(stage(300f, 200f))
        back.setBounds(0, 0, 150, 100)
        var out = drawn(back, 150, 100)
        // the picture's whole width in 150 px: the red / blue edge at x = 75
        assertTrue(r(out.getPixel(60, 50)) >= 190 && b(out.getPixel(90, 50)) >= 190)
        // a stage that shows only the right third of the picture: all blue
        back.setStage(stage(100f, 66.7f, x = 200f, y = 0f))
        out = drawn(back, 150, 100)
        assertTrue("all blue after the stage moved", (0 until 150 step 7).all { b(out.getPixel(it, 50)) >= 190 && r(out.getPixel(it, 50)) <= 10 })
        // the panel is drawn at half size on the screen (as the study layouts do): the bitmap is the screen's, the picture the same
        back.setStage(stage(300f, 200f))
        k = 0.5f
        val half = drawn(back, 75, 50, scale = 0.5f)
        assertTrue(r(half.getPixel(30, 25)) >= 190 && b(half.getPixel(45, 25)) >= 190)
    }
}
