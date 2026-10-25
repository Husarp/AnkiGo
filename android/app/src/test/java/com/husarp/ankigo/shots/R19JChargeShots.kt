package com.husarp.ankigo.shots

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** PLAN 266B / 287: real charge halves and wrapping bubble prose, light + dark; dismissal leaves the host usable. */
class R19JChargeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @org.junit.After fun cleanUp() { BattleShotKit.reset() }

    private fun ready(mine: Boolean): Pair<FrameLayout, ActionBarView> {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        return host(ctx, mine, true)
    }

    private fun measure(v: View, w: Int = 412, h: Int = 400) {
        v.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(v.context, w), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Ui.dp(v.context, h), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun host(ctx: Context, mine: Boolean, bubble: Boolean): Pair<FrameLayout, ActionBarView> {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
        val root = FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base)) }
        val bar = ActionBarView(ctx)
        val layer = FrameLayout(ctx)
        root.addView(bar, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 58)).also { it.topMargin = Ui.dp(ctx, 330) })
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        bar.attachBubbleLayer(layer)
        bar.observe(GameEvent.Effect(mine, if (mine) "Bulbasaur absorbed light!" else "Orthworm burrowed its way under the ground!",
            30, 30, kind = EffectKind.CHARGE))
        measure(root)
        if (bubble) { bar.openBubble(if (mine) BarSide.MINE else BarSide.THEIRS); measure(root) }
        return root to bar
    }

    @Test fun wildDigHalf() = paparazzi.shootWith("r19j-wild-dig-half", listOf(Size.SPLIT)) { ctx -> host(ctx, false, false).first }
    @Test fun wildDigBubble() = paparazzi.shootWith("r19j-wild-dig-bubble", listOf(Size.SPLIT)) { ctx -> host(ctx, false, true).first }
    @Test fun yourSolarBeam() = paparazzi.shootWith("r19j-your-solar-beam", listOf(Size.SPLIT)) { ctx -> host(ctx, true, true).first }

    @Test fun outsideThePanelClosesAndTheTapCanContinue() {
        val (root, bar) = ready(false)
        val bubble = (root.getChildAt(1) as FrameLayout).getChildAt(0) as MoveBubble
        var clicks = 0
        root.setOnClickListener { clicks++ }
        root.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
            true
        }
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 2f, 2f, 0)
        bubble.outsideDown(event)
        root.dispatchTouchEvent(event)
        event.recycle()
        assertFalse(bar.bubbleOpen)
        assertEquals(0, clicks)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 2f, 2f, 0)
        root.dispatchTouchEvent(up)
        up.recycle()
        assertEquals(1, clicks)
    }

    @Test fun backClosesOnlyTheBubbleAndItCanReopen() {
        val (root, bar) = ready(true)
        val bubble = (root.getChildAt(1) as FrameLayout).getChildAt(0) as MoveBubble
        assertFalse(bubble.backKey(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_A)))
        assertTrue(bubble.backKey(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK)))
        assertTrue(bar.bubbleOpen)
        assertTrue(bubble.backKey(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK)))
        assertFalse(bar.bubbleOpen)
        assertEquals(2, root.childCount)
        bar.halfView(BarSide.MINE).performClick()
        assertTrue(bar.bubbleOpen)
    }
}
