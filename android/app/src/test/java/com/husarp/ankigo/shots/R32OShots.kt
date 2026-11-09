package com.husarp.ankigo.shots

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.BarMeaning
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.setMeaning
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32OShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun particlesCrossTheClippedCardInBothThemes() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(screenHeight = 320))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 24), Ui.dp(ctx, 24), Ui.dp(ctx, 24))
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            }
            root.addView(Ui.text(ctx, 16f, 700, R.color.ag_text).apply { text = "Daily goal reached" })
            val card = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val bar = BarView(ctx).apply { setMeaning(BarMeaning.GOAL, 1f, false); setGoalSparks(3) }
            inner.addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 8)))
            card.addView(inner)
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 8) })
            var checked = false
            root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    call(bar, "refreshSparks")
                    assertSame(root.rootView, getField(bar, "sparkHost"))
                    assertNotSame(card, getField(bar, "sparkHost"))
                    assertTrue(card.clipChildren && inner.clipChildren)
                    assertEquals(false, getField(bar, "sparkRunning"))
                    checked = true
                    return true
                }
            })
            paparazzi.snapshot(root, "o-564-goal-${theme.name.lowercase()}")
            assertTrue(checked)
            assertNull(getField(bar, "sparkDrawable")) // Removed on detach.
        }
    }
}
