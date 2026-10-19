package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R

/**
 * The switch under a tab's app bar. Two looks, one place, so a new look is a one-file change:
 * [Style.TABS]: the Pokémon tab's Team · Box · Pokédex (three equal tabs, each with its icon on top of its label; the
 * selected one sits in a rounded square of the accent colour, like the selected tab of the bottom bar);
 * [Style.SEGMENTED]: the Stats tab's Statistics · Achievements (the plain segmented control, the selected part raised).
 */
internal object SectionSwitch {
    class Seg(val label: String, @DrawableRes val icon: Int)

    enum class Style { TABS, SEGMENTED }

    /** Fills [box] (emptied first) with one segment per [segs], the [selected] one marked; a tap calls [onPick] with its index. */
    fun draw(ctx: Context, box: LinearLayout, segs: List<Seg>, selected: Int, style: Style = Style.SEGMENTED, onPick: (Int) -> Unit) {
        box.removeAllViews()
        box.background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), if (style == Style.TABS) 14 else 12, ctx)
        box.setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        for ((i, s) in segs.withIndex()) {
            val seg = if (style == Style.TABS) tab(ctx, s, i == selected) else segment(ctx, s, i == selected)
            seg.contentDescription = s.label
            seg.setOnClickListener { onPick(i) }
            box.addView(seg, LinearLayout.LayoutParams(0, -2, 1f).also { if (style == Style.TABS && i > 0) it.marginStart = Ui.dp(ctx, 2) })
        }
    }

    /** A tab: icon over label, 52 dp tall (46 in a short window); the selected one is on a 20 % accent rounded square (the bottom bar's look), in the accent colour. */
    private fun tab(ctx: Context, s: Seg, on: Boolean): LinearLayout {
        val accent = Ui.c(ctx, R.color.ag_mauve)
        val ink = if (on) accent else Ui.c(ctx, R.color.ag_sub0)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val short = Ui.shortWindow(ctx) // a split-screen half has no room to spare: a little lower
            minimumHeight = Ui.dp(ctx, if (short) 46 else 52)
            setPadding(0, Ui.dp(ctx, if (short) 3 else 5), 0, Ui.dp(ctx, if (short) 3 else 5))
            background = if (on) Ui.rounded((accent and 0x00FFFFFF) or 0x33000000, 11, ctx) else null
            addView(ImageView(ctx).apply {
                setImageResource(s.icon)
                setColorFilter(ink, PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20)))
            addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply {
                text = s.label
                setTextColor(ink)
                maxLines = 1
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        }
    }

    /** A part of the plain segmented control: just the label, 42 dp tall in a 48 dp control; the selected one is raised on the card colour. */
    private fun segment(ctx: Context, s: Seg, on: Boolean): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = Ui.dp(ctx, 42)
        background = if (on) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
        addView(Ui.text(ctx, 12f, 900, if (on) R.color.ag_text else R.color.ag_sub0).apply { text = s.label; maxLines = 1 })
    }
}
