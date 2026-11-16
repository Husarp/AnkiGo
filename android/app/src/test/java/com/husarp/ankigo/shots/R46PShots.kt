package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R46PShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun burnUsesTheAchievementFlameEverywhere() {
        assertEquals(AchStyle.icon("flame"), ConditionIcons.of("brn"))
        paparazzi.shootWith("p-burn-icons", listOf(Size.SPLIT)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(Ui.text(ctx, 18f, 800, R.color.ag_text).apply { text = "Burned · Achievements flame" })
                for (size in listOf(13, 24, 48)) {
                    val badge = StatusBadge(ctx, "brn")
                    val px = Ui.dp(ctx, size)
                    badge.layout(0, 0, px, px)
                    val actual = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    badge.draw(Canvas(actual))
                    val expected = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    Canvas(expected).apply expectedCanvas@{
                        scale(px / 24f, px / 24f)
                        val fill = StatusBadge.fillColor(ctx, "brn")
                        drawRoundRect(1f, 1f, 23f, 23f, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
                        ctx.getDrawable(ConditionIcons.of("brn")!!)!!.mutate().apply {
                            setTint(StatusBadge.inkColor(fill)); setBounds(3, 3, 21, 21); draw(this@expectedCanvas)
                        }
                    }
                    assertTrue("$size dp badge uses the achievement artwork", actual.sameAs(expected))
                    addView(badge, LinearLayout.LayoutParams(px, px))
                    addView(ImageView(ctx).apply { setImageResource(ConditionIcons.of("brn")!!); setColorFilter(Ui.c(ctx, R.color.ag_peach)) }, LinearLayout.LayoutParams(px, px))
                }
            }
        }
    }

    @Test fun battleAlertsCoverTheGoalGlow() {
        paparazzi.shootWith("p-alert-over-goal", listOf(Size.SPLIT, Size.PHONE)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            GameRepo.state!!.battle!!.enemy.status = "brn"
            val panel = BattlePanelView(ctx).apply { renderAll(); setGoal(230, 100, false) }
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    val bar = getField(panel, "goalBar") as BarView
                    if (bar.width > 0) {
                        val column = getField(panel, "column") as View
                        assertSame("glow stays below all panel alerts", column, getField(bar, "sparkHost"))
                        val scaler = column.parent as FrameLayout
                        assertTrue(scaler.indexOfChild(column) < scaler.indexOfChild(panel.toastLayer))
                        assertTrue(scaler.indexOfChild(column) < scaler.indexOfChild(getField(panel, "overlay") as View))
                        val toast = getChildAt(1) as TextView
                        val at = Rect(0, 0, bar.width, bar.height)
                        offsetDescendantRectToMyCoords(bar, at)
                        // Exercise the screenshot's exact overlap, including the halo above the bar.
                        toast.layout((width - toast.measuredWidth) / 2, at.centerY() - toast.measuredHeight / 2,
                            (width + toast.measuredWidth) / 2, at.centerY() + toast.measuredHeight / 2)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply {
                addView(panel, FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, "Resists: 4 matchups not yet seen in battle")
            }
        }
    }

    @Test fun studySummaryGlowStaysInsideItsContent() {
        paparazzi.shootWith("p-study-summary-goal", listOf(Size.PHONE)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            GameRepo.state!!.day = GameRepo.game!!.dayKey()
            GameRepo.state!!.reviewsToday = 230
            GameRepo.state!!.settings.dailyAverage = 100
            val activity = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
            setField(activity, "deckId", 2L)
            setField(activity, "deckName", "Geography::Capitals")
            val content = activity.buildContent()
            call(activity, "summary", StudyDeckKit.decks, 0, 3)
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    fun bars(v: View): List<BarView> = when (v) {
                        is BarView -> listOf(v)
                        is android.view.ViewGroup -> (0 until v.childCount).flatMap { bars(v.getChildAt(it)) }
                        else -> emptyList()
                    }
                    val goal = bars(getField(activity, "summaryView") as View).single()
                    assertSame("summary glow follows content below window alerts",
                        (goal.parent as View).parent, getField(goal, "sparkHost"))
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(content, FrameLayout.LayoutParams(-1, -1)) }
        }
    }
}
