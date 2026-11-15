package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.StatsChartMath
import com.husarp.ankigo.ui.StatsChartView
import com.husarp.ankigo.ui.Ui
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R35KStatsShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun chart(period: ChartPeriod, grain: ChartGrain) {
        val today = LocalDate.of(2026, 11, 15)
        // Whole missing weeks/months prove the line lifts across gaps; individual absent days stay blank too.
        val history = (0 until 1000).filter { it !in 200..280 && it % 23 != 0 }.associate { i ->
            today.minusDays(i.toLong()).toString() to DayLog(buttons = intArrayOf(0, 4 + i % 4, 8 + i % 7,
                (65 + 35 * kotlin.math.sin(i / 43.0) + i % 31).toInt(), 10 + i % 6))
        }
        val start = System.nanoTime()
        val series = ChartData.build(ChartMetric.BUTTONS, period, null, StatsSnapshot(), history, today.toString(), grain)
        val buildMs = (System.nanoTime() - start) / 1_000_000.0
        val drawMs = mutableListOf<Double>()
        paparazzi.shootPage("k-${period.days}-${grain.name.lowercase()}", variants = listOf(Variant("p412", 412, 460))) { ctx ->
            val tones = listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue).map { Ui.c(ctx, it) }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(Ui.text(ctx, 24f, 900, R.color.ag_text).apply { text = "Stats" })
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "Answer buttons" })
                addView(Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = "Last ${period.days} days · ${grain.label}" })
                addView(Ui.text(ctx, 22f, 900, R.color.ag_text).apply { text = "${StatsChartMath.num(series.total)} answers" })
                addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "${StatsChartMath.num(series.perDayAvg)} per calendar day" })
                addView(Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply {
                    text = if (grain == ChartGrain.DAY) "Answers per day" else "Average answers per recorded day"
                })
                val frame = object : FrameLayout(ctx) {
                    override fun dispatchDraw(canvas: Canvas) {
                        val before = System.nanoTime()
                        super.dispatchDraw(canvas)
                        if (canvas.width > 0) drawMs += (System.nanoTime() - before) / 1_000_000.0
                    }
                }
                frame.addView(StatsChartView(ctx).apply { set(series, tones) }, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 200)))
                addView(frame, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 200)))
                addView(Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply { text = "Again · Hard · Good · Easy" })
                addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply {
                    text = "${series.bars.first().startDay} – ${series.bars.last().endDay}\nDays without records stay blank."
                })
            }
            col
        }
        assertTrue(drawMs.isNotEmpty())
        val mean = drawMs.average()
        println("R35-k ${period.days}-day ${grain.name}: aggregation=%.3f ms; draw mean=%.3f ms, max=%.3f ms, frames=%d".format(java.util.Locale.US, buildMs, mean, drawMs.max(), drawMs.size))
        assertTrue("chart draw mean $mean ms", mean < 250.0)
    }

    @Test fun hundredDaily() = chart(ChartPeriod.D100, ChartGrain.DAY)
    @Test fun hundredWeekly() = chart(ChartPeriod.D100, ChartGrain.WEEK)
    @Test fun thousandDaily() = chart(ChartPeriod.D1000, ChartGrain.DAY)
    @Test fun thousandMonthly() = chart(ChartPeriod.D1000, ChartGrain.MONTH)
}
