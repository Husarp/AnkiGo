package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R

/** Geometry and typography from study-mockup.html, shared by its deck, reviewer and summary views. */
internal object StudyUi {
    fun lp(w: Int = -1, h: Int = -2, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    fun spaced(ctx: Context, top: Int, w: Int = -1) = lp(w).also { it.topMargin = Ui.dp(ctx, top) }
    /** Width in px for a one-column page: capped on tablets (so it is not stretched), MATCH_PARENT on phones. Centre it with gravity. */
    fun pageWidth(ctx: Context): Int = com.husarp.ankigo.study.StudyLayout.contentMaxDp(ctx.resources.configuration.smallestScreenWidthDp)
        .let { if (it > 0) minOf(Ui.dp(ctx, it), ctx.resources.displayMetrics.widthPixels) else -1 }
    fun column(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun row(ctx: Context) = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    fun label(ctx: Context, text: String, size: Number = 13, tone: Int = R.color.ag_sub1, weight: Int = 700) =
        Ui.text(ctx, size.toFloat(), weight, tone).apply { this.text = text; setLineSpacing(0f, 1.15f) }

    fun blend(a: Int, b: Int, fraction: Float): Int = Color.rgb(
        (Color.red(a) * fraction + Color.red(b) * (1 - fraction)).toInt(),
        (Color.green(a) * fraction + Color.green(b) * (1 - fraction)).toInt(),
        (Color.blue(a) * fraction + Color.blue(b) * (1 - fraction)).toInt())

    fun gradient(ctx: Context, tone: Int, fraction: Float, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(blend(Ui.c(ctx, tone), Ui.c(ctx, R.color.ag_card), fraction), Ui.c(ctx, R.color.ag_card))
    ).apply { cornerRadius = Ui.dp(ctx, radius.toFloat()) }

    fun line(ctx: Context) = View(ctx).apply {
        setBackgroundColor(Ui.c(ctx, R.color.ag_s0)); layoutParams = lp(-1, Ui.dp(ctx, 1))
    }
    fun section(ctx: Context, title: String, note: String? = null) = row(ctx).apply {
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 8))
        addView(label(ctx, title.uppercase(), 12, R.color.ag_sub0, 900).apply { letterSpacing = .058f }, lp(0, -2, 1f))
        if (note != null) addView(label(ctx, note, 12, R.color.ag_sub0))
    }
    fun icon(ctx: Context, resource: Int, tone: Int = R.color.ag_sub1) = ImageView(ctx).apply {
        setImageResource(resource); if (resource != R.drawable.ic_money) setColorFilter(Ui.c(ctx, tone)) // the gold money stack is never tinted
    }
    fun iconButton(ctx: Context, resource: Int, description: String, action: () -> Unit) = icon(ctx, resource).apply {
        setPadding(Ui.dp(ctx, 7), Ui.dp(ctx, 7), Ui.dp(ctx, 7), Ui.dp(ctx, 7))
        background = ripple(ctx, Ui.rounded(Color.TRANSPARENT, 10, ctx))
        contentDescription = description; setOnClickListener { action() }
    }
    fun ripple(ctx: Context, shape: GradientDrawable) = RippleDrawable(
        ColorStateList.valueOf((Ui.c(ctx, R.color.ag_ov) and 0x00ffffff) or 0x33000000), shape, null)

    /** Mockup .mini: compact secondary actions, not settings-form buttons. */
    fun smallButton(ctx: Context, text: String, primary: Boolean = false, action: () -> Unit): TextView =
        label(ctx, text, 12, if (primary) R.color.ag_base else R.color.ag_text, 900).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 7), Ui.dp(ctx, 10), Ui.dp(ctx, 7))
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, if (primary) R.color.ag_mauve else R.color.ag_s0), 10, ctx))
            setOnClickListener { action() }
        }

    fun primary(ctx: Context, text: String, action: () -> Unit) = smallButton(ctx, text, true, action).apply {
        textSize = 14f
        setPadding(Ui.dp(ctx, 11), Ui.dp(ctx, 11), Ui.dp(ctx, 11), Ui.dp(ctx, 11))
        background = ripple(ctx, Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx))
    }

    /** A [primary] button of the deck-finished screen: the same 48 dp minimum height as [secondary] ("Back to decks"). */
    fun finishedPrimary(ctx: Context, text: String, action: () -> Unit) = primary(ctx, text, action).apply { minimumHeight = Ui.dp(ctx, 48) }

    /** The secondary look next to [primary]: same size, corner radius and text size, `ag_s0` fill; at least 48 dp tall. */
    fun secondary(ctx: Context, text: String, action: () -> Unit) = smallButton(ctx, text, false, action).apply {
        textSize = 14f
        minimumHeight = Ui.dp(ctx, 48)
        setPadding(Ui.dp(ctx, 11), Ui.dp(ctx, 11), Ui.dp(ctx, 11), Ui.dp(ctx, 11))
        background = ripple(ctx, Ui.rounded(Ui.c(ctx, R.color.ag_s0), 12, ctx))
    }

    /** .ab: independent label/interval typography, pressed scale and disabled alpha. */
    fun answer(ctx: Context, text: String, interval: String?, tone: Int, enabled: Boolean, hint: String? = null, action: () -> Unit): View {
        val dark = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val ink = if (dark) Ui.c(ctx, R.color.ag_crust) else Color.WHITE
        return column(ctx).apply {
            gravity = Gravity.CENTER
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, tone), 12, ctx))
            addView(label(ctx, text, if (interval == null) 15 else 14, R.color.ag_text, 900).apply { setTextColor(ink); gravity = Gravity.CENTER }, lp(-1, -2))
            if (interval != null) addView(label(ctx, interval, 11, R.color.ag_text, 800).apply { setTextColor(ink); alpha = .92f; gravity = Gravity.CENTER }, lp(-1, -2))
            else if (hint != null) addView(label(ctx, hint, 10, R.color.ag_text, 800).apply { setTextColor(ink); alpha = .92f; gravity = Gravity.CENTER }, lp(-1, -2))
            isEnabled = enabled; alpha = if (enabled) 1f else .55f
            contentDescription = if (interval != null) "$text, next in $interval" else if (hint != null) "$text, $hint" else text
            isFocusable = true
            setOnClickListener { action() }
            setOnTouchListener { v, e ->
                if (v.isEnabled) when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { v.scaleX = .95f; v.scaleY = .95f }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.scaleX = 1f; v.scaleY = 1f }
                }
                false
            }
        }
    }

    fun ellipsis(tv: TextView) = tv.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
}
