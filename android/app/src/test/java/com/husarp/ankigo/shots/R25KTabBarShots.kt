package com.husarp.ankigo.shots

import android.animation.ValueAnimator
import android.app.Activity
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.TabBarStyle
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.UiMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25KTabBarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun styles() {
        for (style in TabBarStyle.entries) {
            paparazzi.shootPage("r25k-tabbar-${style.label}", listOf(Variant("w360", 360, 400))) { base ->
                val ctx = ShotCtx(base)
                TabBarStyle.put(ctx, style)
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    for (tab in BottomNav.Tab.entries) {
                        val bar = BottomNav.create(attach(Activity(), ctx), tab)
                        addView(bar, LinearLayout.LayoutParams(-1, -2))
                        val row = (bar as LinearLayout).getChildAt(1) as LinearLayout
                        assertEquals(5, row.childCount)
                        assertTrue(row.getChildAt(tab.ordinal).isSelected)
                    }
                }
            }
        }
    }

    @Test fun defaultAndPersistence() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        assertEquals(TabBarStyle.C1, TabBarStyle.get(ctx))
        for (style in TabBarStyle.entries) {
            val generation = com.husarp.ankigo.ui.AppLook.generation
            com.husarp.ankigo.ui.AppLook.setTabBarStyle(ctx, style)
            assertTrue(com.husarp.ankigo.ui.AppLook.generation > generation)
            assertEquals(style, TabBarStyle.get(ctx))
            assertEquals(style.name, ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).getString("tabBarStyle", null))
        }
        ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).edit().putString("tabBarStyle", "future").apply()
        assertEquals(TabBarStyle.C1, TabBarStyle.get(ctx))
    }

    @Test fun animationEndsAtDestination() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        val row = BottomNav.HighlightRow(ctx, BottomNav.Tab.STUDY, TabBarStyle.C1)
        assertEquals(0f, row.position, 0f)
        assertEquals(Ui.c(ctx, R.color.ag_blue), row.colour)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        var completed = false
        row.moveTo(BottomNav.Tab.SETTINGS) { completed = true }
        assertTrue("navigation commits on the tap", completed)
        // The source bar stays still. The destination owns the faster indicator slide.
        assertEquals(0f, row.position, 0f)
        val restored = BottomNav.HighlightRow(ctx, BottomNav.Tab.SETTINGS, TabBarStyle.C1)
        restored.arriveFrom(BottomNav.Tab.STUDY)
        @Suppress("UNCHECKED_CAST")
        val jobs = getField(UiMotion, "jobs") as Map<android.view.View, ValueAnimator>
        val animator = jobs[restored]!!
        assertEquals(140L, animator.duration)
        animator.currentPlayTime = 75
        assertTrue(restored.position > 0f && restored.position < 4f)
        assertNotEquals(Ui.c(ctx, R.color.ag_blue), restored.colour)
        assertNotEquals(Ui.c(ctx, R.color.ag_teal), restored.colour)
        UiMotion.finish(restored)
        assertEquals(4f, restored.position, 0f)
        assertEquals(Ui.c(ctx, R.color.ag_teal), restored.colour)
        assertNull(jobs[restored])
        row.onWindowFocusChanged(true)
        assertEquals(0f, row.position, 0f)

    }
}
