package com.husarp.ankigo.shots

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R26MotionTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val testContext by lazy { Shots.files(paparazzi.context) }
    private fun ctx() = testContext
    private fun reduced(on: Boolean) { ctx().getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", on).commit() }

    @Test fun tabsNeverWaitAndLatestTapWins() {
        reduced(false)
        val row = BottomNav.HighlightRow(ctx(), BottomNav.Tab.STUDY, TabBarStyle.C1)
        val taps = arrayListOf<BottomNav.Tab>()
        row.moveTo(BottomNav.Tab.POKEMON) { taps += BottomNav.Tab.POKEMON }
        row.moveTo(BottomNav.Tab.SETTINGS) { taps += BottomNav.Tab.SETTINGS }
        assertEquals(listOf(BottomNav.Tab.POKEMON, BottomNav.Tab.SETTINGS), taps)
    }
    @Test fun reducedMotionCommitsFinalFramesImmediately() {
        reduced(true)
        val v = View(ctx())
        var done = 0
        UiMotion.run(v, 650, { v.alpha = it }, { done++ })
        assertEquals(1f, v.alpha, 0f); assertEquals(1, done)
        UiMotion.press(v); assertEquals(1f, v.scaleX, 0f)
        UiMotion.pop(v, true); assertEquals(1f, v.scaleY, 0f)
        assertEquals(0, UiMotion.windowStyle(ctx()))
        assertEquals(0, UiMotion.windowStyle(ctx(), true))
    }
    @Test fun finishingEffectCleansUpOnceAndRestoresFinalValue() {
        reduced(false)
        val v = View(ctx()); var ends = 0
        UiMotion.run(v, 650, { v.alpha = it }, { ends++ })
        UiMotion.finish(v); UiMotion.finish(v)
        assertEquals(1, ends); assertEquals(1f, v.alpha, 0f)
    }
    @Test fun rapidEffectReplacementFinishesPrevious() {
        reduced(false)
        val v = View(ctx()); var ends = 0
        UiMotion.run(v, 650, {}, { ends++ })
        UiMotion.run(v, 180, {}, { ends++ })
        UiMotion.finish(v)
        assertEquals(2, ends)
    }
    @Test fun axisCommitsDestinationAndLeavesTapReady() {
        reduced(true)
        val host = FrameLayout(ctx()); val page = TextView(ctx())
        host.addView(page)
        var taps = 0
        page.setOnClickListener { taps++ }
        UiMotion.axis(page, true) { page.text = "Game" }
        page.performClick()
        assertEquals("Game", page.text.toString()); assertEquals(1, taps)
        UiMotion.axis(page, false) { page.text = "Settings" }
        assertEquals("Settings", page.text.toString())
    }
    @Test fun animatedAxisKeepsDestinationInteractiveAndCleansItsPicture() {
        reduced(false)
        val c = ctx()
        val host = FrameLayout(c)
        val page = Ui.text(c, 22f, 900, R.color.ag_text).apply { text = "Settings" }
        host.addView(page, FrameLayout.LayoutParams(-1, -1))
        val w = Ui.dp(c, 320); val h = Ui.dp(c, 300)
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        var taps = 0
        UiMotion.axis(page, true) { page.text = "Game"; page.setOnClickListener { taps++ } }
        page.performClick()
        assertEquals(1, taps)
        paparazzi.snapshot(host, "r26a-axis-mid", 90)
        UiMotion.finish(page)
        assertEquals("Game", page.text.toString())
        assertEquals(0f, page.translationX, 0f); assertEquals(1f, page.alpha, 0f)
        paparazzi.snapshot(host, "r26a-axis-final", 300)
    }
    @Test fun rewardFinalValuesPreserveUnitsAndContinue() {
        reduced(true)
        val label = TextView(ctx()); var taps = 0
        label.setOnClickListener { taps++ }
        UiMotion.count(label, "+120 XP")
        label.performClick()
        assertEquals("+120 XP", label.text.toString()); assertEquals(1, taps)
        UiMotion.count(label, "+10 coins")
        assertEquals("+10 coins", label.text.toString())
    }
    @Test fun resultRebindDoesNotRestartButNextResultCanCelebrate() {
        reduced(false)
        val tiles = CatchTilesView(ctx())
        val summary = CatchSummary(false, "Pikachu", null, null,
            listOf(CatchSummary.XpLine("main", "Squirtle", 120, 8, false)), 10, CatchSummary.NEXT_TEXT)
        fun values(v: View): List<String> = when (v) {
            is TextView -> listOf(v.text.toString())
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { values(v.getChildAt(it)) }
            else -> emptyList()
        }
        tiles.bindGrid(summary, true)
        assertTrue(values(tiles).contains("+0 XP"))
        tiles.bindGrid(summary, true)
        assertTrue(values(tiles).contains("+120 XP"))
        var checked = false
        val host = FrameLayout(ctx()).apply { addView(tiles) }
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                host.removeOnAttachStateChangeListener(this)
                host.post {
                    tiles.visibility = View.GONE; tiles.visibility = View.VISIBLE
                    tiles.bindGrid(summary, true)
                    assertTrue(values(tiles).contains("+0 XP"))
                    checked = true
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        paparazzi.snapshot(host, "r26a-result-entrance", 1000)
        assertTrue("checked on an attached result view", checked)
        fun finish(v: View) {
            UiMotion.finish(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) finish(v.getChildAt(i))
        }
        finish(host)
        paparazzi.snapshot(host, "r26a-result-final", 2000)
    }
    @Test fun counterUsesTheDesignsCubicEaseOutAndRounding() {
        reduced(false)
        val label = TextView(ctx())
        UiMotion.count(label, "+120 XP")
        @Suppress("UNCHECKED_CAST")
        val jobs = getField(UiMotion, "jobs") as Map<View, android.animation.ValueAnimator>
        jobs[label]!!.currentPlayTime = 325
        assertEquals("+105 XP", label.text.toString())
        UiMotion.finish(label)
        assertEquals("+120 XP", label.text.toString())
    }
    @Test fun noticesFinishAtFullSizeAndHideImmediately() {
        reduced(true)
        val dot = View(ctx()).apply { visibility = View.GONE }
        UiMotion.notice(dot, true)
        assertEquals(View.VISIBLE, dot.visibility); assertEquals(1f, dot.scaleX, 0f)
        UiMotion.notice(dot, false); assertEquals(View.GONE, dot.visibility)
    }
    @Test fun approvedWindowTimingsAndReducedMotionStyles() {
        reduced(false)
        assertEquals(R.style.MotionDialog, UiMotion.windowStyle(ctx()))
        assertEquals(R.style.MotionSheet, UiMotion.windowStyle(ctx(), true))
        for (res in listOf(R.anim.motion_dialog_in, R.anim.motion_dialog_out))
            assertEquals(180L, android.view.animation.AnimationUtils.loadAnimation(ctx(), res).duration)
        for (res in listOf(R.anim.motion_sheet_in, R.anim.motion_sheet_out))
            assertEquals(240L, android.view.animation.AnimationUtils.loadAnimation(ctx(), res).duration)
    }
    @Test fun detachFinishesAndRemovesDecoration() {
        reduced(false)
        val host = FrameLayout(ctx()); val page = View(ctx())
        host.addView(page)
        // Removal from an attached render tree exercises the production detach listener.
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                page.post {
                    UiMotion.run(page, 650, { page.alpha = it })
                    host.removeView(page)
                    assertEquals(1f, page.alpha, 0f)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        paparazzi.snapshot(host, "r26a-detach-cleanup", 1000)
    }
    @Test fun dialogAndSmallTouchesInBothThemes() {
        reduced(true)
        paparazzi.shoot("r26a-dialog-controls", listOf(Size.PHONE, Size.FLOATBATTLE)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val controls = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(Form.primary(ctx, "Continue") {})
                addView(Form.switchRow(ctx, "Show types", null, true) {})
                addView(Form.toggleChip(ctx, "Owned", true) {})
            }
            AppDialogShots.scene(ctx, AppDialog.Builder(ctx).setTitle("Animations")
                .setMessage("Ready for your next tap.").setView(controls).setNegativeButton("Close", null).create())
        }
    }
    @Test fun fasterIndicatorFramesAndFinalSelection() {
        reduced(false)
        for (time in listOf(0L, 75L, 150L)) {
            paparazzi.shoot("r26a-c1-$time", listOf(Size.FLOATBATTLE), offsetMillis = time) { ctx ->
                ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).edit().putString("tabBarStyle", "C1").commit()
                val activity = attach(android.app.Activity(), ctx)
                val bar = BottomNav.create(activity, BottomNav.Tab.SETTINGS) as android.view.ViewGroup
                val row = bar.getChildAt(1) as BottomNav.HighlightRow
                row.arriveFrom(BottomNav.Tab.STUDY)
                FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    addView(bar, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
                }
            }
        }
        val row = BottomNav.HighlightRow(ctx(), BottomNav.Tab.SETTINGS, TabBarStyle.C1)
        row.arriveFrom(BottomNav.Tab.STUDY)
        UiMotion.finish(row)
        assertEquals(4f, row.position, 0f)
    }
    @Test fun badgeAndButtonAnimationFrames() {
        reduced(false)
        for (time in listOf(0L, 90L, 450L)) {
            paparazzi.shoot("r26a-badge-$time", listOf(Size.FLOATBATTLE), offsetMillis = time) { ctx ->
                FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    val medal = AchMedal(ctx, 92).apply { set(R.drawable.ic_ach_cards, 2) }
                    addView(medal, FrameLayout.LayoutParams(Ui.dp(ctx, 92), Ui.dp(ctx, 92), android.view.Gravity.CENTER))
                    UiMotion.pop(medal, true)
                }
            }
        }
    }
}
