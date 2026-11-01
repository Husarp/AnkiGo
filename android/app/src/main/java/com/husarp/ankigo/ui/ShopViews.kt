package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ItemInfo

// R10 W11 owns this file: the Shop tab's own pieces (mockups T5 / F5): the price views (coin + amount, and the discount layout), the deal card
// (F5 option A), the item tiles and the weekend card. The page that puts them together is ShopPage (ShopActivity.kt).

internal object ShopViews {
    /** Bare, unsmoothed inventory art at the requested size. */
    fun itemArt(ctx: Context, id: String, size: Int): View = ImageView(ctx).apply {
        Ui.itemIcon(ctx, id, this)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, size), Ui.dp(ctx, size))
    }

    private fun px(ctx: Context, dp: Float) = Math.round(Ui.dp(ctx, dp))

    /** The money coin ([R.drawable.coin_stack], fixed gold, never tinted) at [dp] square, [endDp] clear of what follows it. For a LinearLayout. */
    private fun coin(ctx: Context, dp: Int, endDp: Int, faded: Boolean = false) = ImageView(ctx).apply {
        setImageResource(R.drawable.coin_stack)
        (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
        scaleType = ImageView.ScaleType.FIT_CENTER
        if (faded) alpha = 0.65f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, dp + 3), Ui.dp(ctx, dp + 3)).also { it.marginEnd = Ui.dp(ctx, endDp) }
    }

    /** A normal price: the coin and the amount (mockup `.pr`). */
    fun priceNormal(ctx: Context, amount: Int, sp: Float = 12.5f, coinDp: Int = 13): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(coin(ctx, coinDp, 3))
        addView(Ui.text(ctx, sp, 900, R.color.ag_text).apply { text = Bits.fmt(amount); maxLines = 1 })
    }

    /** The old price of a discount: small, grey, the coin faded, one line through the coin and the number (mockup `.oldp`). */
    private class StrikeRow(ctx: Context) : LinearLayout(ctx) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ui.c(ctx, R.color.ag_sub0)
            strokeWidth = Ui.dp(ctx, 1.5f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val y = height / 2f
            canvas.drawLine(line.strokeWidth, y, width - line.strokeWidth, y, line)
        }
    }

    /**
     * A discounted price (mockup `priceD`): the old price on top, struck through with its faded coin, centred; under it the new price,
     * larger ([nowSp]), in the text colour with a full coin.
     */
    fun priceDeal(ctx: Context, was: Int, now: Int, nowSp: Float = 17f, oldSp: Float = 11f): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val old = StrikeRow(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0)
            addView(coin(ctx, 11, 2, faded = true))
            addView(Ui.text(ctx, oldSp, 800, R.color.ag_sub0).apply { text = Bits.fmt(was); maxLines = 1 })
        }
        addView(old, LinearLayout.LayoutParams(-2, -2))
        val big = Math.round(nowSp * 0.95f)
        val fresh = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(coin(ctx, big, 4))
            addView(Ui.text(ctx, nowSp, 900, R.color.ag_text).apply { text = Bits.fmt(now); maxLines = 1 })
        }
        addView(fresh, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
    }

    /** The price as the page shows it: the discount layout while it is discounted, else coin + amount. [nowSp] is the size of the amount (the new price of a discount). */
    fun price(ctx: Context, p: ShopPlan.Price, normalSp: Float = 12.5f, nowSp: Float = 17f, coinDp: Int = 13): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(if (p.was != null) priceDeal(ctx, p.was, p.now, nowSp) else priceNormal(ctx, p.now, normalSp, coinDp))
        }

    // ---- the item tiles ("always in stock", "today's specials") ----

    /**
     * A tile of the mockup `.stile`: [lead] (the 36 dp item or TM tile), the name (two lines kept), the short line ([sub], or [subView] such as a
     * TM's type chip) and [bottom] (the price, or "Owned") at the bottom. A tap calls [onTap] (PLAN 210: it opens the item's window; no arrow).
     */
    fun tile(ctx: Context, lead: View, name: String, bottom: View, description: String, sub: String? = null, subView: View? = null, onTap: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
        setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6))
        contentDescription = description
        setOnClickListener { onTap() }
        addView(lead)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name })
            sub?.let { addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = it }) }
            subView?.let { addView(it, LinearLayout.LayoutParams(-2, -2)) }
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8); it.marginEnd = Ui.dp(ctx, 6) })
        addView(bottom)
    }

    /** "✓ Owned" in a TM tile or card. */
    fun owned(ctx: Context, sp: Float = 12f): View = Ui.text(ctx, sp, 900, R.color.ag_green).apply { text = "✓ Owned" }

    // ---- the weekend card ----

    /** Match the offer heights after text wrapping, including at larger font sizes. */
    fun offers(ctx: Context, daily: View?, weekend: View?): View = object : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            daily?.let { addView(it, LayoutParams(-1, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 4) }) }
            weekend?.let { addView(it, LayoutParams(-1, -2)) }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            for (i in 0 until childCount) getChildAt(i).layoutParams.height = LayoutParams.WRAP_CONTENT
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (childCount == 2) {
                val offerHeight = maxOf(getChildAt(0).measuredHeight, getChildAt(1).measuredHeight)
                for (i in 0 until childCount) getChildAt(i).layoutParams.height = offerHeight
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    /** A purple companion matching the peach daily-deal card’s height. */
    fun weekendCard(ctx: Context, lead: View, name: String, note: String, right: View, description: String, onTap: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val purple = Ui.c(ctx, R.color.ag_mauve)
        val card = Ui.c(ctx, R.color.ag_card)
        background = glowBackground(ctx, Bits.blend(purple, card, 0.16f), card, purple)
        setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4))
        contentDescription = description
        setOnClickListener { onTap() }
        addView(Ui.text(ctx, 10f, 900, R.color.ag_mauve).apply { text = "WEEKEND SPECIAL"; gravity = Gravity.CENTER
            minimumHeight = Ui.text(ctx, 10.5f, 900, R.color.ag_text).lineHeight
        }, LinearLayout.LayoutParams(-1, -2))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 64)
            addView(lead)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name })
                addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = note })
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8); it.marginEnd = Ui.dp(ctx, 6) })
            if (ctx.resources.configuration.fontScale < 1.5f) addView(right)
        }, Form.lp(ctx, 6))
        if (ctx.resources.configuration.fontScale >= 1.5f) addView(right,
            LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.END })
    }

    // ---- the deal card (F5 option A) ----

    /** The glow: from [glow] at the left to the card colour at 70 % of the width. */
    private fun glowBackground(ctx: Context, glow: Int, card: Int, edge: Int): GradientDrawable {
        val colors = intArrayOf(glow, card, card)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            if (Build.VERSION.SDK_INT >= 29) setColors(colors, floatArrayOf(0f, 0.7f, 1f))
            cornerRadius = Ui.dp(ctx, 16f)
            setStroke(px(ctx, 1.5f), edge)
        }
    }

    /** The bare 64 dp deal icon. */
    private fun dealIcon(ctx: Context, item: String): View {
        val size = Ui.dp(ctx, 64)
        return FrameLayout(ctx).apply {
            val img = ImageView(ctx)
            Ui.itemIcon(ctx, item, img)
            addView(img, FrameLayout.LayoutParams(Ui.dp(ctx, 64), Ui.dp(ctx, 64), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    /**
     * The deal of the day (F5 option A): a soft peach glow from the left, a thin peach edge, the "−30 %" ribbon across the top-right corner,
     * "✦ DEAL OF THE DAY", the bare item icon with its name, effect and "ends at midnight · 7 h", and the discount price on the right.
     * A tap calls [onTap] (PLAN 210: the item's window opens, with Buy at the deal price). [line] is the text under the name.
     */
    fun dealCard(ctx: Context, item: String, name: String, price: ShopPlan.Price, ends: String, description: String, line: String = ItemInfo.effect(item), night: Boolean = false, onTap: () -> Unit): View {
        val dark = TypeChips.isDark(ctx)
        val peach = Ui.c(ctx, R.color.ag_peach)
        val card = Ui.c(ctx, R.color.ag_card)
        val labelCol = if (dark) peach else Bits.blend(0xFF000000.toInt(), peach, 0.25f)
        val label = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_ach_sparkle)
                setColorFilter(labelCol, PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)).also { it.marginEnd = Ui.dp(ctx, 4) })
            addView(Ui.text(ctx, 10.5f, 900, R.color.ag_text).apply { text = "${if (night) "DEAL OF THE NIGHT" else "DEAL OF THE DAY"} · ${ShopPlan.ribbon()}"; setTextColor(labelCol); letterSpacing = 0.07f })
        }
        val clock = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_clock)
                setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 11), Ui.dp(ctx, 11)).also { it.marginEnd = Ui.dp(ctx, 4) })
            addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = ends })
        }
        val middle = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 15f, 900, R.color.ag_text).apply { text = name })
            addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = line })
            addView(clock, Form.lp(ctx, 3))
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dealIcon(ctx, item))
            addView(middle, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12); it.marginEnd = Ui.dp(ctx, 8) })
            if (ctx.resources.configuration.fontScale < 1.5f)
                addView(priceDeal(ctx, price.was ?: price.now, price.now, 17f), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 6) })
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4))
            addView(label, LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.CENTER_HORIZONTAL })
            // The 64 dp icon plus 8 dp padding preserves 1.0.0’s 52 + 20 dp body height.
            addView(row, Form.lp(ctx, 6))
            if (ctx.resources.configuration.fontScale >= 1.5f) addView(priceDeal(ctx, price.was ?: price.now, price.now, 17f),
                LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.END })
        }
        return FrameLayout(ctx).apply {
            background = glowBackground(ctx, Bits.blend(peach, card, if (dark) 0.2f else 0.16f), card, Bits.blend(peach, card, 0.6f))
            isClickable = true
            contentDescription = description
            setOnClickListener { onTap() }
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
    }
}
