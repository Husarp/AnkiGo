package com.husarp.ankigo.shots

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33ATest {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun label(row: LinearLayout, i: Int) = (row.getChildAt(i) as LinearLayout).getChildAt(1) as TextView
    private fun icon(row: LinearLayout, i: Int) = ((row.getChildAt(i) as LinearLayout).getChildAt(0) as FrameLayout).getChildAt(0) as ImageView
    private fun ink(row: LinearLayout, i: Int) = (icon(row, i).colorFilter as PorterDuffColorFilter).color

    @Test fun everyChoiceAppliesAndDismissesIncludingTheCurrentColour() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        val under = object : StackWindow {
            override val stackContext = ctx
            override fun closeFromStack() = Unit
            override fun stackChanged(stacked: Boolean) = Unit
        }
        WindowStack.add(under)
        try {
            for (accent in Accent.entries) repeat(2) { repeat ->
                var changes = 0
                val before = AppPrefs.accent(ctx)
                val dialog = AccentPicker.dialog(ctx) { changes++ }
                dialog.joinStack()
                assertTrue(WindowStack.stacked(ctx))
                all(dialog.content()).single { it.isClickable && it.contentDescription?.toString()?.substringBefore(",") == accent.label }.performClick()
                assertEquals(accent, AppPrefs.accent(ctx))
                assertEquals(if (before == accent) 0 else 1, changes)
                assertFalse("Picker left the window stack ($accent, $repeat)", WindowStack.stacked(ctx))
            }
        } finally { WindowStack.remove(under); AppLook.setAccent(ctx, Accent.GREEN) }
    }

    @Test fun rainbowIsSmoothAndCircularInBothThemes() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = ShotCtx(Shots.files(paparazzi.context))
            AppLook.setAccent(ctx, Accent.GREEN)
            val dialog = AccentPicker.dialog(ctx) {}
            val choice = all(dialog.content()).single { it.contentDescription == "Multicolor" } as LinearLayout
            val swatch = choice.getChildAt(0).background
            swatch.setBounds(0, 0, 100, 100)
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            swatch.draw(Canvas(bitmap))
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            val pixels = (12..87).map { bitmap.getPixel(it, 50) }
            assertTrue("Continuous hue blend", pixels.toSet().size > 65)
            for ((a, b) in pixels.zipWithNext()) {
                assertTrue(kotlin.math.abs(Color.red(a) - Color.red(b)) < 25)
                assertTrue(kotlin.math.abs(Color.green(a) - Color.green(b)) < 25)
                assertTrue(kotlin.math.abs(Color.blue(a) - Color.blue(b)) < 25)
            }
            paparazzi.snapshot(AppDialogShots.scene(ctx, dialog), "a-picker-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun allStylesInterpolateInkWithStableViewsAndOneAnimator() {
        for (theme in Theme.entries) for (accent in listOf(Accent.MULTICOLOR, Accent.GREEN)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = ShotCtx(Shots.files(paparazzi.context))
            AppLook.setAccent(ctx, accent)
            try {
                for (style in TabBarStyle.entries) {
                    TabBarStyle.put(ctx, style)
                    ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                    fun bar(tab: BottomNav.Tab) = BottomNav.create(attach(Activity(), ctx), tab) as LinearLayout
                    val source = bar(BottomNav.Tab.STUDY).getChildAt(1) as BottomNav.HighlightRow
                    val bar = bar(BottomNav.Tab.SETTINGS)
                    val row = bar.getChildAt(1) as BottomNav.HighlightRow
                    val labels = (0..4).map { label(row, it) }
                    val texts = labels.map { it.text }
                    val sizes = labels.map { it.textSize }
                    val endColours = labels.map { it.currentTextColor }
                    val endIcons = (0..4).map { ink(row, it) }
                    for (i in 0..3) {
                        assertEquals(Ui.c(ctx, R.color.ag_sub0), endColours[i])
                        assertEquals(Ui.c(ctx, R.color.ag_sub0), endIcons[i])
                    }
                    row.arriveFrom(BottomNav.Tab.STUDY)
                    @Suppress("UNCHECKED_CAST")
                    val jobs = getField(UiMotion, "jobs") as Map<View, ValueAnimator>
                    val animator = jobs[row]!!
                    assertEquals(200L, animator.duration)
                    for (i in 0..4) {
                        assertEquals(label(source, i).currentTextColor, label(row, i).currentTextColor)
                        assertEquals(ink(source, i), ink(row, i))
                        assertEquals(label(source, i).translationY, label(row, i).translationY, 0f)
                    }
                    animator.currentPlayTime = 50
                    assertNotEquals(endColours[4], label(row, 4).currentTextColor)
                    assertNotEquals(endColours[0], label(row, 0).currentTextColor)
                    for (time in 60L..190L step 10) {
                        animator.currentPlayTime = time
                        for (i in 0..4) {
                            assertSame(labels[i], label(row, i))
                            assertSame(texts[i], label(row, i).text)
                            assertEquals(sizes[i], label(row, i).textSize, 0f)
                        }
                        assertSame(animator, jobs[row])
                    }
                    UiMotion.finish(row)
                    assertEquals(endColours, labels.map { it.currentTextColor })
                    assertEquals(endIcons, (0..4).map { ink(row, it) })
                    assertNull(jobs[row])
                    if (accent == Accent.MULTICOLOR) {
                        paparazzi.snapshot(bar, "a-tabs-${style.name}-${theme.name.lowercase()}", 1000)
                    }
                    ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                    row.arriveFrom(BottomNav.Tab.STUDY)
                    assertNull(jobs[row])
                    assertEquals(endColours, labels.map { it.currentTextColor })
                }
            } finally { AppLook.setAccent(ctx, Accent.GREEN) }
        }
    }
}
