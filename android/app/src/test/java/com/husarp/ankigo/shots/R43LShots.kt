package com.husarp.ankigo.shots

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R43LShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @org.junit.After fun cleanUp() { BattleShotKit.reset() }

    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(root.context, 412), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Ui.dp(root.context, 400), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        (if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())

    @Test fun chargeTilesAndDetailsOnBothSides() {
        for (bubble in listOf<BarSide?>(null, BarSide.MINE, BarSide.THEIRS)) {
            paparazzi.shootWith("r43l-charge-${bubble?.name?.lowercase() ?: "tiles"}", listOf(Size.SPLIT)) { ctx ->
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
                val root = FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base)) }
                val bar = ActionBarView(ctx)
                val layer = FrameLayout(ctx)
                root.addView(bar, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 52)).also { it.topMargin = Ui.dp(ctx, 320) })
                root.addView(layer, FrameLayout.LayoutParams(-1, -1))
                bar.attachBubbleLayer(layer)
                for (player in listOf(true, false)) {
                    val move = Move("bounce", "Bounce", Type.FLYING, Category.PHYSICAL, 85, 85)
                    bar.observe(GameEvent.Effect(player, "${if (player) "Wailmer" else "Wailord"} sprang up!", 100, 100,
                        kind = EffectKind.CHARGE, chargeMove = move))
                }
                layout(root)
                for (side in BarSide.entries) {
                    val half = bar.halfView(side)
                    assertTrue(half.isClickable)
                    assertTrue(texts(half).contains("Bounce — charging"))
                    assertTrue(texts(half).contains("Physical"))
                    assertTrue(half.contentDescription.toString().contains("Flying type"))
                }
                if (bubble != null) {
                    bar.halfView(bubble).performClick()
                    layout(root)
                    assertTrue(bar.bubbleOpen)
                    assertTrue(texts(layer).contains("Charging"))
                    assertTrue(layer.getChildAt(0).contentDescription.toString().contains("Bounce"))
                }
                root
            }
        }
    }

    @Test fun scrollingGestureCannotBecomeADismissWhenItReachesTheTop() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
        val sheet = FrameLayout(ctx)
        val scroll = object : View(ctx) {
            var above = true
            override fun canScrollVertically(direction: Int) = direction == -1 && above
        }
        layout(sheet)
        var closed = 0
        val dragger = SheetDragger(sheet, null, scroll, 0, { closed++ })
        fun send(action: Int, y: Float, time: Long) {
            val e = MotionEvent.obtain(1000, time, action, 100f, y, 0)
            dragger.intercept(e); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 100f, 1000)
        send(MotionEvent.ACTION_MOVE, 150f, 1100)
        scroll.above = false
        send(MotionEvent.ACTION_MOVE, 300f, 1200)
        send(MotionEvent.ACTION_MOVE, 500f, 1300)
        send(MotionEvent.ACTION_UP, 500f, 1400)
        assertEquals(0, closed)
        assertEquals(0f, sheet.translationY, 0f)
        // Reversing a content scroll also keeps the same gesture in the content.
        send(MotionEvent.ACTION_DOWN, 100f, 1500)
        send(MotionEvent.ACTION_MOVE, 50f, 1600)
        send(MotionEvent.ACTION_MOVE, 400f, 1700)
        send(MotionEvent.ACTION_UP, 400f, 1800)
        assertEquals(0, closed)
        assertEquals(0f, sheet.translationY, 0f)
        // A fresh pull at the top still closes with the shorter distance.
        send(MotionEvent.ACTION_DOWN, 100f, 1900)
        send(MotionEvent.ACTION_MOVE, 140f, 2000)
        send(MotionEvent.ACTION_MOVE, 260f, 2100)
        send(MotionEvent.ACTION_UP, 260f, 2200)
        assertEquals(1, closed)
        dragger.dispose()
    }
}
