package com.husarp.ankigo.deck

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Ui

/** Small view pieces shared by the deck and card management dialogs, the move sheet and the note editor (the app's own look: `ag_*` colours, Nunito). */
internal object DeckViews {
    fun dark(ctx: Context) = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** The text colour on a filled accent / red button: white in the light palette, the darkest base in the dark one (as the answer buttons). */
    fun ink(ctx: Context) = if (dark(ctx)) Ui.c(ctx, R.color.ag_crust) else Color.WHITE

    fun ripple(ctx: Context, shape: android.graphics.drawable.GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf((Ui.c(ctx, R.color.ag_ov) and 0x00ffffff) or 0x33000000), shape, null)

    /** A full-width 48 dp button: filled with [fill] (ink on it) or, when [fill] is 0, the quiet `ag_mantle` look with normal text. */
    fun button(ctx: Context, label: String, fill: Int = 0, onClick: () -> Unit): TextView =
        Ui.text(ctx, 14f, 900, R.color.ag_text).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = Ui.dp(ctx, 48)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
            if (fill != 0) setTextColor(ink(ctx))
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, if (fill == 0) R.color.ag_mantle else fill), 12, ctx))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }

    /** A dimmed, untouchable button (the action is not possible yet). */
    fun enable(v: View, on: Boolean) { v.isEnabled = on; v.alpha = if (on) 1f else .45f }

    /** A dialog's padded column: title (18/900), then what you add. */
    fun box(ctx: Context, title: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 18), Ui.dp(ctx, 20), Ui.dp(ctx, 16))
        addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = title; setLineSpacing(0f, 1.1f) })
    }

    fun message(ctx: Context, text: CharSequence): TextView =
        Ui.text(ctx, 14f, 700, R.color.ag_sub1).apply { this.text = text; setLineSpacing(0f, 1.2f) }

    /** Two equal buttons side by side (the left one is the way out). */
    fun buttons(ctx: Context, left: View, right: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        addView(right, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) })
    }

    /** The text of [parts] with the middle part heavy and in the main text colour (a plain bold span would not change the app's variable font). */
    fun withBold(ctx: Context, parts: DeckTexts.Parts): CharSequence = SpannableString(parts.toString()).apply {
        setSpan(Heavy(Ui.font(ctx, 900), Ui.c(ctx, R.color.ag_text)), parts.before.length, parts.before.length + parts.bold.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private class Heavy(private val face: Typeface, private val color: Int) : MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface = face; p.color = color }
        override fun updateMeasureState(p: TextPaint) { p.typeface = face }
    }

    /** A thin progress bar: [fraction] 0..1 of the way, accent on `ag_mantle`. */
    fun bar(ctx: Context, fraction: Float): View = LinearLayout(ctx).apply {
        val f = fraction.coerceIn(0f, 1f)
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 4, ctx)
        addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 4, ctx) }, LinearLayout.LayoutParams(0, -1, f))
        addView(View(ctx), LinearLayout.LayoutParams(0, -1, 1f - f))
    }
}
