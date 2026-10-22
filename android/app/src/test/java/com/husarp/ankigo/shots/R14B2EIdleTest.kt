package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.ui.MonPreview
import com.husarp.ankigo.ui.SpriteViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 205 (R14 B2-E): the idle float on the details picture (MonPreview.show idle = true) and on the full-screen viewer. A still
 * picture gets the motion when shown and loses it when detached; reduced motion, a silhouette, a view reused without the flag and an
 * animated picture get none. The snapshot host attaches the view for the render and detaches it afterwards (like R14HIdleMotionTest).
 */
class R14B2EIdleTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun ctx(reduce: Boolean): android.content.Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduce).commit()
        return c
    }

    private fun shot(v: View, name: String) {
        val f = FrameLayout(v.context).apply { addView(v, FrameLayout.LayoutParams(288, 288)) }
        paparazzi.snapshot(f, name, 1000)
    }

    // ---- pure ----
    @Test fun theOffsetMatchesTheBattleArenasFloat() {
        assertEquals(0f, MonPreview.idleOffsetPx(0f, 2f), 1e-6f)
        assertEquals(-6f, MonPreview.idleOffsetPx(0.5f, 2f), 1e-6f)   // 3 dp at density 2, up = negative
        assertEquals(-3f, MonPreview.idleOffsetPx(0.25f, 2f), 1e-6f)
        assertEquals(0f, MonPreview.idleOffsetPx(1f, 2f), 1e-6f)
    }

    // ---- details picture ----
    @Test fun aStillDetailPictureGetsTheMotionWhenShownAndLosesItWhenDetached() {
        val c = ctx(reduce = false)
        val mon = GameRepo.state!!.mon("t1")!!
        val img = ImageView(c)
        MonPreview.show(c, mon.species, mon.shiny, img, idle = true)
        assertFalse("not attached yet", MonPreview.idleRunning(img))
        shot(img, "r14b2e-detail-still")
        assertEquals("started once while shown", 1, MonPreview.idleStarts(img))
        assertFalse("stopped when detached", MonPreview.idleRunning(img))
        assertFalse("no window listener is left after detach", MonPreview.idleListening(img))
        assertEquals("rest pose after detach", 0f, img.translationY, 0f)
        assertEquals(1f, img.scaleY, 0f)
    }

    /** Calls the hidden ViewTreeObserver dispatchers the way ViewRootImpl does (the snapshot host has no real window). */
    private fun dispatch(v: View, name: String, vararg arg: Any) {
        val o = v.viewTreeObserver
        val m = o.javaClass.declaredMethods.first { it.name == name }
        m.isAccessible = true
        m.invoke(o, *arg)
    }

    @Test fun theFloatStopsWhenTheWindowOrTheViewIsHiddenAndStartsAgainWhenVisible() {
        val c = ctx(reduce = false)
        val mon = GameRepo.state!!.mon("t1")!!
        val img = ImageView(c)
        MonPreview.show(c, mon.species, mon.shiny, img, idle = true)
        val seen = mutableListOf<String>()
        // added after the float's own listener, so it runs right after the float started on attach
        img.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                seen += "attached running=${MonPreview.idleRunning(img)} listening=${MonPreview.idleListening(img)}"
                dispatch(img, "dispatchOnWindowVisibilityChange", View.GONE)
                seen += "window gone running=${MonPreview.idleRunning(img)}"
                dispatch(img, "dispatchOnWindowVisibilityChange", View.VISIBLE)
                seen += "window visible running=${MonPreview.idleRunning(img)}"
                img.visibility = View.INVISIBLE
                dispatch(img, "dispatchOnPreDraw")
                seen += "view hidden running=${MonPreview.idleRunning(img)}"
                img.visibility = View.VISIBLE
                dispatch(img, "dispatchOnPreDraw")
                seen += "view shown running=${MonPreview.idleRunning(img)}"
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        shot(img, "r14b2e-detail-visibility")
        assertEquals(listOf(
            "attached running=true listening=true",
            "window gone running=false",
            "window visible running=true",
            "view hidden running=false",
            "view shown running=true",
        ), seen)
        assertEquals("one start per visible spell: attach, window back, view back", 3, MonPreview.idleStarts(img))
        assertFalse(MonPreview.idleRunning(img))
        assertFalse(MonPreview.idleListening(img))
    }

    @Test fun noMotionInReducedMotionWithoutTheFlagOrForASilhouette() {
        val reduced = ctx(reduce = true)
        val mon = GameRepo.state!!.mon("t1")!!
        val a = ImageView(reduced)
        MonPreview.show(reduced, mon.species, mon.shiny, a, idle = true)
        shot(a, "r14b2e-detail-reduced")
        assertEquals(0, MonPreview.idleStarts(a))
        val c = ctx(reduce = false)
        val b = ImageView(c)
        MonPreview.show(c, mon.species, mon.shiny, b) // grids, lists, pickers: the default stays still
        shot(b, "r14b2e-detail-noflag")
        assertEquals(0, MonPreview.idleStarts(b))
        val s = ImageView(c)
        MonPreview.show(c, mon.species, mon.shiny, s, silhouette = true, idle = true)
        shot(s, "r14b2e-detail-silhouette")
        assertEquals(0, MonPreview.idleStarts(s))
    }

    @Test fun aViewReusedForAnotherPokemonStartsOver() {
        val c = ctx(reduce = false)
        val mon = GameRepo.state!!.mon("t1")!!
        val img = ImageView(c)
        MonPreview.show(c, mon.species, mon.shiny, img, idle = true)
        MonPreview.show(c, mon.species, mon.shiny, img) // reused without the flag: the float is gone
        shot(img, "r14b2e-detail-reused")
        assertEquals(0, MonPreview.idleStarts(img))
        assertEquals(0f, img.translationY, 0f)
    }

    // ---- full-screen viewer ----
    private fun block(): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
        for (y in 30..80) for (x in 20..70) b.setPixel(x, y, 0xFF3366CC.toInt())
    }

    private fun viewer(c: android.content.Context, setup: (SpriteViewer.Content) -> Unit = {}): SpriteViewer.Content {
        val mon = GameRepo.state!!.mon("t1")!!
        return SpriteViewer.build(c, mon.species, mon.shiny) {}.also(setup)
    }

    @Test fun aStillViewerPictureFloatsAndAnAnimatedOrReducedOneDoesNot() {
        val still = viewer(ctx(reduce = false))
        shot(still.root, "r14b2e-viewer-still")
        assertEquals("a still picture started the float once", 1, still.zoom.idleStarts)
        assertFalse("and it stops when detached", still.zoom.idleRunning)

        val c = ctx(reduce = false)
        val animated = viewer(c) { it.zoom.setImage(SpriteImage(listOf(block(), block()), intArrayOf(100, 100))) }
        shot(animated.root, "r14b2e-viewer-animated")
        assertEquals(0, animated.zoom.idleStarts)

        val reduced = viewer(ctx(reduce = true))
        shot(reduced.root, "r14b2e-viewer-reduced")
        assertEquals(0, reduced.zoom.idleStarts)
    }
}
