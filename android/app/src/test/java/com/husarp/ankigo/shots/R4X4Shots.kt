package com.husarp.ankigo.shots

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.PlayerTurnSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R4-X4 (PLAN 34): swipe across our Pokémon, it turns to its front view. PNG: r4x4-turned (front view shown). */
class R4X4Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    /** The battle with our main turned around: the front picture (the fake back picture is drawn differently). */
    @Test fun turned() = paparazzi.shootWith("r4x4-turned", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val src = b.arena!!.turnSource!!
        assertTrue("front picture available", src.hasFront())
        src.showFront()
        b.root
    }

    private class Fake(val front: Boolean = true) : PlayerTurnSource {
        var fronts = 0; var backs = 0
        override fun hasFront() = front
        override fun showFront() { fronts++ }
        override fun showBack() { backs++ }
    }

    private fun holder(a: ArenaView): View =
        (0 until a.childCount).map { a.getChildAt(it) }.first { it.contentDescription?.startsWith("Your Pokémon") == true }

    private fun touch(v: View, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun swipe(v: View, dx: Float) {
        touch(v, MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(v, MotionEvent.ACTION_MOVE, 20f + dx / 2, 21f)
        touch(v, MotionEvent.ACTION_MOVE, 20f + dx, 22f)
        touch(v, MotionEvent.ACTION_UP, 20f + dx, 22f)
    }

    private fun arena(ctx: android.content.Context): ArenaView {
        return ArenaView(ctx).also { it.instantTurn = true }   // swap at once: the renderer has no animation clock
    }

    @Test fun swipeTurnsAndDoesNotTap() {
        val a = arena(paparazzi.context)
        val src = Fake(); a.turnSource = src
        swipe(holder(a), 120f)
        assertEquals(1, src.fronts)
        swipe(holder(a), -120f)
        assertEquals("a second swipe only extends the turn", 1, src.fronts)
    }

    @Test fun tapDoesNotTurn() {
        val a = arena(paparazzi.context)
        val src = Fake(); a.turnSource = src
        val h = holder(a)
        touch(h, MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(h, MotionEvent.ACTION_UP, 21f, 20f)
        assertEquals(0, src.fronts)   // (the click itself is queued until the view is attached; PlayerTapShots covers it)
    }

    @Test fun busyOrNoFrontIgnoresTheSwipe() {
        val a = arena(paparazzi.context)
        val src = Fake(); a.turnSource = src
        a.canTurn = { false }
        swipe(holder(a), 120f)
        assertEquals(0, src.fronts)
        a.canTurn = { true }
        val none = Fake(front = false); a.turnSource = none
        swipe(holder(a), 120f)
        assertEquals(0, none.fronts)
    }

    /** The 1.5 s timer's end (posted, so run by hand here). */
    private fun endTurn(a: ArenaView) = ArenaView::class.java.getDeclaredMethod("endTurn").apply { isAccessible = true }.invoke(a)

    @Test fun turnsBackOnlyOverItsOwnFrontPicture() {
        val a = arena(paparazzi.context)
        val src = Fake(); a.turnSource = src
        swipe(holder(a), 120f)
        endTurn(a)
        assertEquals("back to the normal picture", 1, src.backs)
        swipe(holder(a), 120f)
        assertEquals(2, src.fronts)
        a.playerSprite.setTag(R.id.ag_tag_sprite, Any())   // a battle event loaded another picture (e.g. the next main sent out)
        endTurn(a)
        assertEquals("the event's picture stays", 1, src.backs)
        assertEquals(1f, a.playerSprite.scaleX)
    }

    @Test fun aSwipeAfterAnEventReplacedThePictureTurnsAgain() {
        val a = arena(paparazzi.context)
        val src = Fake(); a.turnSource = src
        swipe(holder(a), 120f)
        a.playerSprite.setTag(R.id.ag_tag_sprite, Any())
        swipe(holder(a), -120f)
        assertEquals(2, src.fronts)
    }

    @Test fun aSwipeLeavesNoPressedLook() {
        val a = arena(paparazzi.context)
        a.turnSource = Fake()
        val h = holder(a)
        swipe(h, 120f)
        assertFalse(h.isPressed)
    }
}
