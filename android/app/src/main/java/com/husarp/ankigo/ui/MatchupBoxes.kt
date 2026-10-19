package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type

/**
 * R9-W3-A (D8-V1): one matchup group as a neutral box with a 3 dp bar on its left edge in the group colour: a header (the label in
 * the group colour + the multiplier pill), then the type chips flowing. Used by the details (label + pill, the label ellipsizes) and by
 * Compare's cells (the pill only: the row label carries the words). A tap on the box says what the group means (a real button
 * for TalkBack).
 */
internal object MatchupBoxes {
    @ColorRes
    fun colorRes(g: MatchupGroup): Int = when (g) {
        MatchupGroup.VERY_WEAK -> R.color.ag_mu_x4
        MatchupGroup.WEAK -> R.color.ag_mu_x2
        MatchupGroup.RESISTS -> R.color.ag_mu_half
        MatchupGroup.STRONGLY_RESISTS -> R.color.ag_mu_quarter
        MatchupGroup.IMMUNE -> R.color.ag_mu_zero
        MatchupGroup.STRONG_AGAINST -> R.color.ag_mu_strong
    }

    /** The box: rounded fill with the bar on its left edge (clipped to the rounded corners). */
    private class BarBox(private val fill: Int, private val bar: Int, private val radius: Float, private val barW: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val clip = Path()

        override fun draw(canvas: Canvas) {
            rect.set(bounds)
            paint.color = fill
            canvas.drawRoundRect(rect, radius, radius, paint)
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.color = bar
            canvas.drawRect(rect.left, rect.top, rect.left + barW, rect.bottom, paint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /**
     * The box of [group] with [types]. [roomPx] = the width the box may take (null = the parent decides: the details); [compact]
     * = Compare's cells (smaller text and padding, pill only, no label). [chipFit] gets every chip and the width left
     * for it (Compare shrinks the text there). [name] = the Pokémon's display name, for the tap message.
     */
    fun box(
        ctx: Context, group: MatchupGroup, types: List<Type>, name: String,
        compact: Boolean, roomPx: Int? = null, textSp: Float = 12.5f, chipFit: (TextView, Int) -> Unit = { _, _ -> },
    ): View {
        fun dp(v: Float) = Ui.dp(ctx, v).toInt()
        val dark = TypeChips.isDark(ctx)
        val col = Ui.c(ctx, colorRes(group))
        val fill = MatchupGroups.boxColor(Ui.c(ctx, R.color.ag_card), Ui.c(ctx, R.color.ag_mantle), dark)
        val barW = dp(3f)
        val padStart = barW + dp(if (compact) 3f else 8f)
        val padEnd = dp(if (compact) 3f else 8f)
        val message = MatchupGroups.toast(group, types, name)

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = BarBox(fill, col, Ui.dp(ctx, 10f), barW.toFloat())
            setPadding(padStart, dp(if (compact) 4f else 6f), padEnd, dp(if (compact) 5f else 7f))
            minimumHeight = dp(48f) // a real touch target even where the box is short
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            contentDescription = message
            setOnClickListener { Ui.toast(ctx, message) }
            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            })
        }

        val label = Ui.text(ctx, textSp, 900, if (group == MatchupGroup.IMMUNE) R.color.ag_sub1 else colorRes(group)).apply { text = group.label; maxLines = 1 }
        val pill = Ui.text(ctx, textSp + 0.5f, 900, R.color.ag_text).apply {
            text = group.pill
            setTextColor(MatchupGroups.pillInk(col))
            background = Ui.rounded(col, 6, ctx)
            setPadding(dp(6f), 0, dp(6f), 0)
            includeFontPadding = false
        }
        val innerW = roomPx?.let { it - padStart - padEnd }
        val gap = dp(6f)
        // Compare: the row label already says the words, so a cell shows the pill only. Details: the label takes the rest of the
        // line and ellipsizes, the pill (measured first, no weight) always shows in full.
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (!compact) {
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            head.addView(label, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginEnd = gap })
        }
        head.addView(pill, LinearLayout.LayoutParams(-2, -2))
        box.addView(head, LinearLayout.LayoutParams(if (compact) -2 else -1, -2))

        val outline = MatchupGroups.outline(col, dark)
        val flow = FlowRow(ctx, dp(5f), centered = false, lineGapPx = dp(4f))
        for (t in types) {
            val chip = Ui.typeChip(ctx, t)
            (chip.background as? GradientDrawable)?.setStroke(dp(1f).coerceAtLeast(1), outline)
            chip.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (innerW != null) chipFit(chip, innerW)
            flow.addView(chip)
        }
        box.addView(flow, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(5f) })
        return box
    }
}
