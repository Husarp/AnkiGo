package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.stats.ChartGroup
import com.husarp.ankigo.stats.ChartMetric

// TICKET R3-V1 (pick 1, opt-1A-list): the "Show" sheet that lists every chart metric in its group (Study / Game / Collection).
// It is a layer the Stats page puts over its own body (scrim + sheet at the bottom), so it always stays inside the window.

internal object StatsSheets {
    /** The drawable of a [ChartMetric.icon] id. */
    @DrawableRes fun icon(id: String): Int = when (id) {
        "bars" -> R.drawable.ic_stats
        "clock" -> R.drawable.ic_clock
        "check" -> R.drawable.ic_check
        "plus" -> R.drawable.ic_plus
        "gamepad" -> R.drawable.ic_game
        "book" -> R.drawable.ic_book
        "calendar" -> R.drawable.ic_calendar
        "flame" -> R.drawable.ic_flame
        "coin" -> R.drawable.ic_coin
        "ball" -> R.drawable.ic_ball
        "swords" -> R.drawable.ic_swords
        "up" -> R.drawable.ic_up
        "dex" -> R.drawable.ic_dex
        "types" -> R.drawable.ic_palette
        "star" -> R.drawable.ic_star
        else -> R.drawable.ic_stats
    }

    /** The colour of a metric group: Study mauve (the accent), Game peach, Collection teal. */
    fun groupTone(g: ChartGroup): Int = when (g) {
        ChartGroup.STUDY -> R.color.ag_mauve
        ChartGroup.GAME -> R.color.ag_peach
        ChartGroup.COLLECTION -> R.color.ag_teal
    }

    /** The sheet: handle, "Show · one chart at a time", then every group's metrics as chips. [onPick] gets the tapped metric. */
    fun metricSheet(ctx: Context, current: ChartMetric, onPick: (ChartMetric) -> Unit): View {
        val sheet = DragSheet(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val r = Ui.dp(ctx, 20f)
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 10))
            isClickable = true // a tap on the sheet itself doesn't reach the scrim
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = "Show" }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = "one chart at a time" })
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10) })

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for ((group, metrics) in ChartMetric.menu) {
            body.addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply { text = group.label.uppercase(); letterSpacing = 0.06f },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10); it.bottomMargin = Ui.dp(ctx, 2) })
            val flow = FlowLayout(ctx)
            for (m in metrics) flow.addView(chip(ctx, m, m == current, onPick))
            body.addView(flow, LinearLayout.LayoutParams(-1, -2))
        }
        sheet.addView(CappedScroll(ctx).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, -2))
        return sheet
    }

    private fun chip(ctx: Context, m: ChartMetric, on: Boolean, onPick: (ChartMetric) -> Unit): TextView {
        val ink = Ui.c(ctx, if (on) R.color.ag_crust else R.color.ag_sub1)
        return Ui.text(ctx, 12.5f, 800, if (on) R.color.ag_crust else R.color.ag_sub1).apply {
            text = m.label
            maxLines = 1
            Ui.touchPill(this, Ui.rounded(Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_mantle), 14, ctx), 34, 12)
            ctx.getDrawable(icon(m.icon))?.mutate()?.let { d ->
                d.setTint(ink)
                val s = Ui.dp(ctx, 14)
                d.setBounds(0, 0, s, s)
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = Ui.dp(ctx, 6)
            }
            contentDescription = if (on) "${m.label}, shown" else m.label
            setOnClickListener { onPick(m) }
        }
    }

    /** A ScrollView that stops short of the window's top (a strip of scrim stays visible) and scrolls when its content is taller. */
    private class CappedScroll(ctx: Context) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) Ui.dp(context, 520) else (size - Ui.dp(context, 40)).coerceAtLeast(Ui.dp(context, 96))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** Children left to right, wrapping to a new line when the next one doesn't fit. */
    class FlowLayout(ctx: Context, private val hGapDp: Int = 6) : ViewGroup(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val gap = Ui.dp(context, hGapDp)
            var x = 0; var y = 0; var lineH = 0; var widest = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (x > 0 && x + c.measuredWidth > maxW) { y += lineH; x = 0; lineH = 0 }
                x += c.measuredWidth + gap
                lineH = maxOf(lineH, c.measuredHeight)
                widest = maxOf(widest, x - gap)
            }
            setMeasuredDimension(resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec), y + lineH + paddingTop + paddingBottom)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val maxW = r - l - paddingLeft - paddingRight
            val gap = Ui.dp(context, hGapDp)
            var x = 0; var y = 0; var lineH = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                if (x > 0 && x + c.measuredWidth > maxW) { y += lineH; x = 0; lineH = 0 }
                c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
                x += c.measuredWidth + gap
                lineH = maxOf(lineH, c.measuredHeight)
            }
        }
    }
}
