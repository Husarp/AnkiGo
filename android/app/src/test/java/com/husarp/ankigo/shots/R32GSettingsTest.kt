package com.husarp.ankigo.shots

import android.animation.ValueAnimator
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32GSettingsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun label(row: LinearLayout, i: Int) = (row.getChildAt(i) as LinearLayout).getChildAt(1) as TextView
    private fun layout(v: View, width: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, width, v.measuredHeight)
    }

    @Test fun allSettingsSegmentsAreEqualCenteredAndUnclipped() {
        val choices = listOf(
            ThemeMode.entries.map { it.label }, BarStyle.entries.map { it.label }, TypeStyle.entries.map { it.label },
            MatchupChips.entries.map { it.label }, listOf("Today", "A", "B"), AchievementPopup.modes,
            ShowAnswerColor.entries.map { it.label }, OpenOn.entries.map { it.label }, DotsPlace.entries.map { it.label },
            InfoBoxStyle.entries.map { it.label }
        )
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 2f)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", width, 1100).device(theme).copy(fontScale = scale))
            val ctx = ShotCtx(Shots.files(paparazzi.context))
            val page = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), 0, Ui.dp(ctx, 24), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            }
            choices.forEach { labels ->
                var picked = -1
                val row = Form.segmented(ctx, labels, 0) { picked = it }
                page.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
                layout(row, Ui.dp(ctx, width - 48))
                val cells = (0 until row.childCount).map { row.getChildAt(it) as TextView }
                assertTrue(cells.maxOf { it.width } - cells.minOf { it.width } <= 1)
                assertEquals(1, cells.map { it.height }.distinct().size)
                cells.forEach {
                    assertEquals(Gravity.CENTER, it.gravity)
                    assertTrue(it.compoundDrawables.all { drawable -> drawable == null })
                    assertTrue(it.layout.height <= it.height - it.compoundPaddingTop - it.compoundPaddingBottom)
                    for (line in 0 until it.lineCount) assertEquals(0, it.layout.getEllipsisCount(line))
                }
                cells.last().performClick()
                assertEquals(labels.lastIndex, picked)
                assertTrue(cells.last().isSelected)
                assertFalse(cells.first().isSelected)
            }
            paparazzi.snapshot(page, "g-segments-$width-font$scale-${theme.name.lowercase()}")
        }
    }

    @Test fun everyStyleMarksTheSavedChoiceAndCanChangeIt() {
        for (theme in Theme.entries) for (style in TabBarStyle.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 640).device(theme))
            val ctx = ShotCtx(Shots.files(paparazzi.context))
            TabBarStyle.put(ctx, style)
            val page = SettingsPage(ctx, NoHost)
            page.restoreSection("LOOK")
            var dialog: AppDialog? = null
            Pickers.interceptApp = { dialog = it }
            try {
                Form.rowsIn(page.root).single { Form.titleOf(it) == "Bottom tab bar" }.performClick()
                val content = dialog!!.content()
                val selected = all(content).filterIsInstance<TextView>().filter { it.text.endsWith(" — selected") }
                assertEquals(1, selected.size)
                assertTrue(selected.single().text.startsWith(style.label))
                paparazzi.snapshot(FrameLayout(ctx).apply { addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)) }, "g-picker-${style.name}-${theme.name.lowercase()}")
                val next = TabBarStyle.entries[(style.ordinal + 1) % TabBarStyle.entries.size]
                val choice = all(content).filterIsInstance<TextView>().single { it.text.startsWith(next.label) }
                var click: View = choice
                while (!click.isClickable) click = click.parent as View
                click.performClick()
                assertEquals(next, TabBarStyle.get(ctx))
            } finally { Pickers.interceptApp = null; dialog?.dismiss(); page.onGone() }
        }
    }

    @Test fun bothTabLabelsAnimateContinuouslyInBothDirectionsAndSettleWithoutMotion() {
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", width, 640).device(Theme.LIGHT).copy(fontScale = scale))
            val ctx = ShotCtx(Shots.files(paparazzi.context))
            for (style in TabBarStyle.entries) for (from in BottomNav.Tab.entries) for (to in BottomNav.Tab.entries.filter { it != from }) {
                TabBarStyle.put(ctx, style)
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                fun bar(tab: BottomNav.Tab) = BottomNav.create(attach(Activity(), ctx), tab) as LinearLayout
                val source = bar(from).getChildAt(1) as BottomNav.HighlightRow
                val target = bar(to).getChildAt(1) as BottomNav.HighlightRow
                layout(source, Ui.dp(ctx, width)); layout(target, Ui.dp(ctx, width))
                val sizes = (0..4).map { label(target, it).textSize }
                target.arriveFrom(from)
                @Suppress("UNCHECKED_CAST")
                val jobs = getField(UiMotion, "jobs") as Map<View, ValueAnimator>
                val animator = jobs[target]!!
                if (style == TabBarStyle.A3 || style == TabBarStyle.E1) assertEquals(200L, animator.duration)
                assertEquals(source.height, target.height)
                for (i in listOf(from.ordinal, to.ordinal)) {
                    assertEquals(label(source, i).top, label(target, i).top)
                    assertEquals(label(source, i).textSize, label(target, i).textSize, .001f)
                    assertEquals(label(source, i).scaleX, label(target, i).scaleX, .001f)
                    if (style == TabBarStyle.E1) {
                        assertEquals(label(source, i).alpha, label(target, i).alpha, 0f)
                        val sourceIcon = ((source.getChildAt(i) as LinearLayout).getChildAt(0) as FrameLayout).getChildAt(0)
                        val targetIcon = ((target.getChildAt(i) as LinearLayout).getChildAt(0) as FrameLayout).getChildAt(0)
                        assertEquals(sourceIcon.scaleX, targetIcon.scaleX, .001f)
                    }
                }
                var incoming = label(target, to.ordinal).scaleX
                var outgoing = label(target, from.ordinal).scaleX
                for (time in 1L until animator.duration step 10) {
                    animator.currentPlayTime = time
                    assertEquals(sizes, (0..4).map { label(target, it).textSize })
                    if (style == TabBarStyle.A3) {
                        assertTrue(label(target, to.ordinal).scaleX >= incoming)
                        assertTrue(label(target, from.ordinal).scaleX <= outgoing)
                        incoming = label(target, to.ordinal).scaleX
                        outgoing = label(target, from.ordinal).scaleX
                    }
                }
                animator.currentPlayTime = animator.duration / 2
                if (style == TabBarStyle.A3) {
                    assertTrue(label(target, to.ordinal).scaleX > 10f / 12f)
                    assertTrue(label(target, to.ordinal).scaleX < 1f)
                    assertTrue(label(target, from.ordinal).scaleX > 10f / 12f)
                    assertTrue(label(target, from.ordinal).scaleX < 1f)
                }
                if (style == TabBarStyle.E1) {
                    assertTrue(label(target, to.ordinal).alpha > 0f && label(target, to.ordinal).alpha < 1f)
                    assertTrue(label(target, from.ordinal).alpha > 0f && label(target, from.ordinal).alpha < 1f)
                }
                UiMotion.finish(target)
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                target.arriveFrom(from)
                assertNull(jobs[target])
                if (style == TabBarStyle.A3) {
                    assertEquals(1f, label(target, to.ordinal).scaleX, 0f)
                    assertEquals(10f / 12f, label(target, from.ordinal).scaleX, 0f)
                }
                if (style == TabBarStyle.E1) {
                    assertEquals(1f, label(target, to.ordinal).alpha, 0f)
                    assertEquals(0f, label(target, from.ordinal).alpha, 0f)
                }
            }
        }
    }

    @Test fun tabStyleAnimationFrames() {
        for (style in TabBarStyle.entries) for (time in listOf(0L, 50L, 100L, 200L)) {
            paparazzi.shoot("g-tabs-${style.name}-$time", listOf(Size.SMALL), offsetMillis = time) { base ->
                val ctx = ShotCtx(base)
                TabBarStyle.put(ctx, style)
                val bar = BottomNav.create(attach(Activity(), ctx), BottomNav.Tab.SETTINGS) as LinearLayout
                val row = bar.getChildAt(1) as BottomNav.HighlightRow
                row.arriveFrom(BottomNav.Tab.POKEMON)
                FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)); addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)) }
            }
        }
    }
}
