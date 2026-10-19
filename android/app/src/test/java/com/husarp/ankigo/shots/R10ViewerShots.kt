package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.SpriteViewer
import com.husarp.ankigo.ui.ViewerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/*
 * TICKET R10-B (B1): the full-screen sprite viewer's content, rendered in a frame at PHONE, LAND, FLOAT (340x480) and SPLIT, light + dark:
 * front, back, mirrored, zoomed 2.5x panned into a corner, and the back-missing fallback (the front turned round).
 * PNGs: r10viewer-<state>-<size>-<theme>. Generated sprites only (FakeGame). Asserted at draw time: the picture inside the screen at fit,
 * its edges not further in than the screen's when zoomed, and the three buttons >= 48 dp and inside the screen.
 */
class R10ViewerShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class Seen(val w: Int, val h: Int, val density: Float, val sprite: FloatArray, val zoomed: Boolean, val buttons: List<IntArray>)
    private val seen = LinkedHashMap<String, Seen>() // the last draw per render (the renderer draws a frame more than once)

    private fun abs(v: View, root: View): IntArray {
        var x = 0; var y = 0; var p: View = v
        while (p !== root) { x += p.left; y += p.top; p = p.parent as View }
        return intArrayOf(x, y, x + v.width, y + v.height)
    }

    private fun state(name: String, sizes: List<Size>, noBack: Boolean = false, setup: (SpriteViewer.Content) -> Unit = {}, pose: ViewerMath.Pose? = null) {
        for (size in sizes) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx: Context = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val mon = GameRepo.state!!.mon("t1")!!
            if (noBack) {
                File(Sprites.dir(ctx), "back/${mon.species}.png").delete()
                ctx.getSharedPreferences("firstrun", 0).edit().putBoolean("skipSprites", true).apply() // no download in a test
            }
            val c = SpriteViewer.build(ctx, mon.species, mon.shiny) {}
            setup(c)
            val frame = object : FrameLayout(ctx) {
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                    super.onLayout(changed, l, t, r, b)
                    if (pose != null) c.zoom.setPose(pose)
                }
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    seen["$name-${size.name.lowercase()}-${theme.name.lowercase()}"] = Seen(
                        c.root.width, c.root.height, ctx.resources.displayMetrics.density, c.zoom.spriteRect(), ViewerMath.isZoomed(c.zoom.pose),
                        listOf(abs(c.close, c.root), abs(c.turn, c.root), abs(c.mirror, c.root)))
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(c.root, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r10viewer-$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    private val sizes = listOf(Size.PHONE, Size.LAND, Size.FLOAT, Size.SPLIT)

    private fun check(prefix: String, expectCount: Int) {
        val mine = seen.filterKeys { it.startsWith(prefix) }.toList()
        assertEquals("rendered $prefix", expectCount, mine.size)
        for ((key, s) in mine) {
            val r = s.sprite
            if (!s.zoomed) {
                assertTrue("$key sprite inside the screen", r[0] >= -0.5f && r[1] >= -0.5f && r[2] <= s.w + 0.5f && r[3] <= s.h + 0.5f)
            } else {
                // per axis: bigger than the screen -> its edge is not further in than the screen's; smaller -> centred
                if (r[2] - r[0] > s.w + 0.5f) assertTrue("$key zoomed sprite edge not further in than the screen edge (x)", r[0] <= 0.5f && r[2] >= s.w - 0.5f)
                else assertEquals("$key zoomed sprite centred (x)", s.w / 2f, (r[0] + r[2]) / 2f, 1f)
                if (r[3] - r[1] > s.h + 0.5f) assertTrue("$key zoomed sprite edge not further in than the screen edge (y)", r[1] <= 0.5f && r[3] >= s.h - 0.5f)
                else assertEquals("$key zoomed sprite centred (y)", s.h / 2f, (r[1] + r[3]) / 2f, 1f)
            }
            val min = 48 * s.density - 1f
            for ((i, b) in s.buttons.withIndex()) {
                assertTrue("$key button $i >= 48 dp (${b[2] - b[0]} x ${b[3] - b[1]})", b[2] - b[0] >= min && b[3] - b[1] >= min)
                assertTrue("$key button $i inside the screen", b[0] >= 0 && b[1] >= 0 && b[2] <= s.w && b[3] <= s.h)
            }
        }
    }

    @Test fun front() { state("front", sizes); check("front", 8) }

    @Test fun back() { state("back", sizes, setup = { it.turn.performClick() }); check("back", 8) }

    @Test fun mirrored() { state("mirror", sizes, setup = { it.mirror.performClick() }); check("mirror", 8) }

    @Test fun zoomed() { state("zoom", sizes, pose = ViewerMath.Pose(2.5f, 99999f, 99999f)); check("zoom", 8) }

    @Test fun noBackPictureShowsTheFrontTurned() {
        state("noback", listOf(Size.PHONE), noBack = true, setup = { it.turn.performClick() }); check("noback", 2)
    }

    // R10-FIX (Codex 2): closing the viewer must stop its pending loads: no toast, no new load, nothing drawn into the closed view
    @Test fun closingTheViewerDisposesPendingLoads() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx: Context = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val mon = GameRepo.state!!.mon("t1")!!
        File(Sprites.dir(ctx), "back/${mon.species}.png").delete()
        File(Sprites.dir(ctx), "back/${mon.species}.gif").delete()
        val oldBack = SpriteViewer.fetchBack
        val oldStatic = SpriteViewer.fetchStatic
        try {
            var pendingBack: ((File?) -> Unit)? = null
            var staticCalls = 0
            SpriteViewer.fetchBack = { _, _, _, cb -> pendingBack = cb }
            SpriteViewer.fetchStatic = { _, _, _, _ -> staticCalls++ }
            // control: an open viewer does react to the failed back fetch (toast + the front loads again)
            val open = SpriteViewer.build(ctx, mon.species, mon.shiny) {}
            open.turn.performClick()
            val before = staticCalls
            com.husarp.ankigo.ui.Ui.lastToast = null
            pendingBack!!(null)
            assertTrue("an open viewer toasts", com.husarp.ankigo.ui.Ui.lastToast != null)
            assertTrue("and loads the front again (or has it on disk)", staticCalls >= before)
            open.dispose()
            // closed while the fetch is pending: nothing happens when it comes back
            val c = SpriteViewer.build(ctx, mon.species, mon.shiny) {}
            c.turn.performClick()
            val calls = staticCalls
            val cb = pendingBack!!
            c.dispose()
            com.husarp.ankigo.ui.Ui.lastToast = null
            cb(null)
            assertEquals("no toast after close", null, com.husarp.ankigo.ui.Ui.lastToast)
            assertEquals("no further load after close", calls, staticCalls)
            // a second close and a late click are harmless
            c.dispose(); c.turn.performClick()
            assertEquals("no load from a button after close", calls, staticCalls)
        } finally {
            SpriteViewer.fetchBack = oldBack
            SpriteViewer.fetchStatic = oldStatic
        }
    }
}
