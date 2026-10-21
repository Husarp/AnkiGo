package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
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
    private fun px(ctx: Context, dp: Float) = Math.round(Ui.dp(ctx, dp))

    /** The money coin ([R.drawable.ic_money], fixed gold, never tinted) at [dp] square, [endDp] clear of what follows it. For a LinearLayout. */
    private fun coin(ctx: Context, dp: Int, endDp: Int, faded: Boolean = false) = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_money)
        if (faded) alpha = 0.65f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, dp), Ui.dp(ctx, dp)).also { it.marginEnd = Ui.dp(ctx, endDp) }
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
        if (p.was != null) priceDeal(ctx, p.was, p.now, nowSp) else priceNormal(ctx, p.now, normalSp, coinDp)

    // ---- the item tiles ("always in stock", "today's specials") ----

    /** A tile of the mockup `.stile`: [lead] (the 36 dp item or TM tile), the name (two lines kept), and [bottom] (the price, or "Owned") at the bottom. A tap calls [onTap]. */
    fun tile(ctx: Context, lead: View, name: String, bottom: View, description: String, sub: String? = null, onTap: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 9), Ui.dp(ctx, 6), Ui.dp(ctx, 8))
        isClickable = true
        contentDescription = description
        setOnClickListener { onTap() }
        addView(lead)
        addView(Ui.text(ctx, 12f, 900, R.color.ag_text).apply {
            text = name; gravity = Gravity.CENTER; minLines = 2; maxLines = 3
        }, Form.lp(ctx, 6))
        sub?.let { addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = it; gravity = Gravity.CENTER; maxLines = 1 }, Form.lp(ctx, 2)) }   // PLAN 171: a ball's catch chance
        addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f)) // prices line up at the bottom of equal tiles
        addView(bottom, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
    }

    /** "✓ Owned" in a TM tile or card. */
    fun owned(ctx: Context, sp: Float = 12f): View = Ui.text(ctx, sp, 900, R.color.ag_green).apply { text = "✓ Owned" }

    // ---- the weekend card ----

    /** The weekend special: a card with a yellow edge (mockup T5), the item / TM tile, its name, [note] and the price on the right. */
    fun weekendCard(ctx: Context, lead: View, name: String, note: String, right: View, description: String, onTap: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(Ui.c(ctx, R.color.ag_card))
            cornerRadius = Ui.dp(ctx, 14f)
            setStroke(px(ctx, 1.5f), Ui.c(ctx, R.color.ag_yellow))
        }
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10))
        isClickable = true
        contentDescription = description
        setOnClickListener { onTap() }
        addView(lead)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name })
            addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = note }, Form.lp(ctx, 2))
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10); it.marginEnd = Ui.dp(ctx, 8) })
        addView(right)
    }

    // ---- the deal card (F5 option A) ----

    /** The "−30 %" ribbon across a card's top-right corner (mockup `.rib`): drawn clipped to the card's rounded corner. It does not scale with the text size (a badge, not text). */
    private class CornerRibbon(ctx: Context, private val label: String, private val fill: Int, private val ink: Int) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val radius = 16f * d
        private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 10.5f * d; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.03f
        }
        private val clip = Path()
        private val poly = Path()
        private val rect = RectF()

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val s = Math.round(64f * d)
            setMeasuredDimension(s, s)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            // only the card's own corner: a rounded rectangle whose top-right corner is the card's
            clip.rewind()
            rect.set(-w, 0f, w, height * 2f)
            clip.addRoundRect(rect, floatArrayOf(0f, 0f, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            // the band runs down-right across the corner: between the lines y = x - k1 and y = x - k2 (18 dp thick)
            val k1 = 20f * d
            val k2 = k1 + 18f * d * 1.4142f
            poly.rewind()
            poly.moveTo(k1, 0f); poly.lineTo(k2, 0f); poly.lineTo(w, w - k2); poly.lineTo(w, w - k1); poly.close()
            canvas.save()
            canvas.translate(0f, d)
            canvas.drawPath(poly, shadow)
            canvas.restore()
            canvas.drawPath(poly, band)
            val mx = (k1 + k2) / 2f / 2f + w / 2f   // the middle of the band's centre line, between the top edge and the right edge
            val my = (w - (k1 + k2) / 2f) / 2f
            canvas.save()
            canvas.rotate(45f, mx, my)
            val fm = text.fontMetrics
            canvas.drawText(label, mx, my - (fm.ascent + fm.descent) / 2f, text)
            canvas.restore()
            canvas.restore()
        }
    }

    /** The glow: from [glow] at the left to the card colour at 70 % of the width. */
    private fun glowBackground(ctx: Context, glow: Int, card: Int, edge: Int): GradientDrawable {
        val colors = intArrayOf(glow, card, card)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            if (Build.VERSION.SDK_INT >= 29) setColors(colors, floatArrayOf(0f, 0.7f, 1f))
            cornerRadius = Ui.dp(ctx, 16f)
            setStroke(px(ctx, 1.5f), edge)
        }
    }

    /** The lit 52 dp tile of the deal: a peach glow in the middle fading into the mantle colour. */
    private fun litTile(ctx: Context, item: String, peach: Int, dark: Boolean): View {
        val size = Ui.dp(ctx, 52)
        val bg = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.5f, 0.45f)
            gradientRadius = size * 0.62f
            colors = intArrayOf(Bits.blend(peach, Ui.c(ctx, R.color.ag_card), if (dark) 0.35f else 0.3f), Ui.c(ctx, R.color.ag_mantle))
            cornerRadius = Ui.dp(ctx, 14f)
        }
        return FrameLayout(ctx).apply {
            background = bg
            val img = ImageView(ctx)
            Ui.itemIcon(ctx, item, img)
            addView(img, FrameLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    /**
     * The deal of the day (F5 option A): a soft peach glow from the left, a thin peach edge, the "−30 %" ribbon across the top-right corner,
     * "✦ DEAL OF THE DAY", the item on a lit tile with its name, effect and "ends at midnight · 7 h", and the discount price on the right.
     * A tap calls [onTap] (the buy sheet at the deal price).
     */
    fun dealCard(ctx: Context, item: String, name: String, price: ShopPlan.Price, ends: String, description: String, onTap: () -> Unit): View {
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
            addView(Ui.text(ctx, 10.5f, 900, R.color.ag_text).apply { text = "DEAL OF THE DAY"; setTextColor(labelCol); letterSpacing = 0.07f; maxLines = 1 })
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
            addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = ItemInfo.effect(item) })
            addView(clock, Form.lp(ctx, 3))
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(litTile(ctx, item, peach, dark))
            addView(middle, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12); it.marginEnd = Ui.dp(ctx, 8) })
            addView(priceDeal(ctx, price.was ?: price.now, price.now, 17f), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 14) })
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
            addView(label)
            addView(row, Form.lp(ctx, 6))
        }
        return FrameLayout(ctx).apply {
            background = glowBackground(ctx, Bits.blend(peach, card, if (dark) 0.2f else 0.16f), card, Bits.blend(peach, card, 0.6f))
            isClickable = true
            contentDescription = description
            setOnClickListener { onTap() }
            addView(body, FrameLayout.LayoutParams(-1, -2))
            addView(CornerRibbon(ctx, ShopPlan.ribbon(), peach, MatchupGroups.pillInk(peach)), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        }
    }
}
