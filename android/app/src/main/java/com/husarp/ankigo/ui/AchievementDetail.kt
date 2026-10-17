package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.AchStep
import com.husarp.ankigo.game.AchView
import com.husarp.ankigo.game.AchievementTable
import java.time.ZoneId

// R4-V2 (PLAN 79, picks 2A / opt-noid11): the sheet that opens when an achievement is tapped: big medal, title, what it counts, the
// value, every tier with its threshold (reached with its date, the one in progress with a bar, the rest locked) and where the count starts.

internal object AchievementDetail {
    /** A ScrollView that stops short of the window's top (a strip of scrim stays visible) and scrolls when its content is taller. */
    private class CappedScroll(ctx: Context) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) Ui.dp(context, 560) else (size - Ui.dp(context, 40)).coerceAtLeast(Ui.dp(context, 96))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /**
     * The whole sheet layer to put over the page: scrim + the sheet at the bottom (inside the window; it scrolls when taller).
     * A tap on the scrim calls [onClose]. [achSince] dates "before"; [zone] is the time zone the dates are shown in.
     */
    fun layer(ctx: Context, v: AchView, achSince: Long, zone: ZoneId, onClose: () -> Unit): View {
        val layer = FrameLayout(ctx)
        val scrim = View(ctx).apply { setBackgroundColor(0x66000000); isClickable = true; setOnClickListener { onClose() } }
        layer.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val sheet = sheet(ctx, v, achSince, zone)
        // R6-07 (item 18): the handle really drags: the sheet follows the finger and closes when let go past 30 % (or on a fling down)
        (sheet as DragSheet).dragger = SheetDragger(sheet, scrim, sheet.getChildAt(1) as? ScrollView, Ui.dp(ctx, 28), onClose)
        layer.addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        return layer
    }

    fun sheet(ctx: Context, v: AchView, achSince: Long, zone: ZoneId): View {
        val sheet = object : DragSheet(ctx) {
            // wide windows (landscape, tablet): the sheet keeps a phone-like width in the middle
            override fun onMeasure(w: Int, h: Int) {
                val cap = Ui.dp(ctx, 520)
                super.onMeasure(if (MeasureSpec.getSize(w) > cap) MeasureSpec.makeMeasureSpec(cap, MeasureSpec.EXACTLY) else w, h)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            val r = Ui.dp(ctx, 22f)
            background = GradientDrawable().apply { setColor(Ui.c(ctx, R.color.ag_card)); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 10))
            isClickable = true // a tap on the sheet itself doesn't reach the scrim
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 10), Ui.dp(ctx, 16), Ui.dp(ctx, 4)) }
        body.addView(hero(ctx, v), LinearLayout.LayoutParams(-1, -2))
        val row = AchievementTable.row(v.id)
        if (v.oneOff) body.addView(oneOffLine(ctx, v, achSince, zone), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        else for (s in AchPageMath.ladder(v)) body.addView(step(ctx, v, s, achSince, zone), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        if (row != null) {
            val since = if (achSince > 0) AchPageMath.date(achSince, zone) else "the update"
            body.addView(note(ctx, AchPageMath.sourceNote(row, since)), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 12) })
        }
        sheet.addView(CappedScroll(ctx).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, -2))
        return sheet
    }

    private fun hero(ctx: Context, v: AchView): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val short = Ui.shortWindow(ctx) || ctx.resources.configuration.screenHeightDp in 1..459
        col.addView(AchMedal(ctx, if (short) 56 else 76).apply { set(AchStyle.icon(v.icon), v.tier, special = v.oneOff && v.tier >= 0) })
        col.addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = v.title; gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        col.addView(Ui.text(ctx, 12.5f, 700, R.color.ag_sub0).apply { text = v.desc; gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 3) })
        if (!v.oneOff) col.addView(Ui.text(ctx, 28f, 900, R.color.ag_text).apply { text = AchPageMath.fmtValue(v); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        return col
    }

    private fun glyphBox(ctx: Context, tier: Int, color: Int, on: Boolean): View = FrameLayout(ctx).apply {
        background = Ui.rounded(if (on) AchStyle.mix(color, Ui.c(ctx, R.color.ag_card), 0.16f) else Ui.c(ctx, R.color.ag_mantle), 10, ctx)
        addView(ImageView(ctx).apply { setImageDrawable(AchStyle.shape(tier, if (on) color else AchStyle.mix(color, Ui.c(ctx, R.color.ag_card), 0.35f))) },
            FrameLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Gravity.CENTER))
    }

    private fun step(ctx: Context, v: AchView, s: AchStep, achSince: Long, zone: ZoneId): View {
        val color = AchStyle.tierColor(ctx, s.tier)
        val locked = s.state == AchStep.State.LOCKED
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(glyphBox(ctx, s.tier, color, !locked), LinearLayout.LayoutParams(Ui.dp(ctx, 34), Ui.dp(ctx, 34)))
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val metric = AchievementTable.row(v.id)?.metric
        body.addView(Ui.text(ctx, 14f, 900, if (locked) R.color.ag_sub0 else R.color.ag_text).apply { text = "${s.name} · ${AchPageMath.fmt(metric, s.threshold)}" })
        when (s.state) {
            AchStep.State.REACHED -> body.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = AchPageMath.reachedText(s.date, achSince, zone, s.inferred) },
                LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
            AchStep.State.CURRENT -> {
                body.addView(BarView(ctx).apply { set(v.progress, color, false) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 6)).also { it.topMargin = Ui.dp(ctx, 5) })
                body.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = "${AchPageMath.fmtValue(v)} / ${AchPageMath.fmt(metric, s.threshold)} · ${AchPageMath.toGo(v)}" },
                    LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
            }
            AchStep.State.LOCKED -> body.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = s.lockedText },
                LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        }
        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12) })
        when (s.state) {
            AchStep.State.REACHED -> row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_check); setColorFilter(Ui.c(ctx, R.color.ag_green)) }, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)))
            AchStep.State.LOCKED -> row.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_ach_lock); setColorFilter(Ui.c(ctx, R.color.ag_sub0)); alpha = 0.7f }, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)))
            else -> {}
        }
        if (locked) row.alpha = 0.75f
        return row
    }

    private fun oneOffLine(ctx: Context, v: AchView, achSince: Long, zone: ZoneId): View {
        val earned = v.tier >= 0
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Ui.text(ctx, 14f, 900, if (earned) R.color.ag_green else R.color.ag_sub0).apply {
            text = if (earned) AchPageMath.reachedText(v.dates.firstOrNull(), achSince, zone, 0 in v.inferred).replace("Reached", "Earned") else "Not earned yet"
        })
        return row
    }

    private fun note(ctx: Context, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_info); setColorFilter(Ui.c(ctx, R.color.ag_sub0)) }, LinearLayout.LayoutParams(Ui.dp(ctx, 14), Ui.dp(ctx, 14)))
        addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { this.text = text }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8) })
    }
}
