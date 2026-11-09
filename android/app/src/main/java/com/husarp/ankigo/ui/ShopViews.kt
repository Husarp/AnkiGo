package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
     * larger ([nowSp]), in the text colour with a full coin. A small discount pill sits to the left of this price column.
     */
    fun priceDeal(ctx: Context, was: Int, now: Int, nowSp: Float = 17f, oldSp: Float = 11f,
                  percent: Int = Math.round((was - now) * 100f / was.coerceAtLeast(1))): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val amounts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val old = StrikeRow(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0)
            addView(coin(ctx, 11, 2, faded = true))
            addView(Ui.text(ctx, oldSp, 800, R.color.ag_sub0).apply { text = Bits.fmt(was); maxLines = 1 })
        }
        amounts.addView(old, LinearLayout.LayoutParams(-2, -2))
        val big = Math.round(nowSp * 0.95f)
        val fresh = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(coin(ctx, big, 4))
            addView(Ui.text(ctx, nowSp, 900, R.color.ag_text).apply { text = Bits.fmt(now); maxLines = 1 })
        }
        amounts.addView(fresh, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        addView(Ui.text(ctx, 10f, 900, R.color.ag_green).apply {
            text = ShopPlan.ribbon(percent)
            maxLines = 1
            background = Ui.rounded(Bits.blend(Ui.c(ctx, R.color.ag_green), Ui.c(ctx, R.color.ag_card), 0.15f), 6, ctx)
            setPadding(Ui.dp(ctx, 5), Ui.dp(ctx, 2), Ui.dp(ctx, 5), Ui.dp(ctx, 2))
        }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 5) })
        addView(amounts)
    }

    /** The price as the page shows it: the discount layout while it is discounted, else coin + amount. [nowSp] is the size of the amount (the new price of a discount). */
    fun price(ctx: Context, p: ShopPlan.Price, normalSp: Float = 12.5f, nowSp: Float = 17f, coinDp: Int = 13): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(if (p.was != null) priceDeal(ctx, p.was, p.now, nowSp, percent = p.percent ?: Math.round((p.was - p.now) * 100f / p.was.coerceAtLeast(1))) else priceNormal(ctx, p.now, normalSp, coinDp))
        }

    // ---- the item tiles ("always in stock", "today's specials") ----

    /**
     * A tile of the mockup `.stile`: [lead] (the 36 dp item or TM tile), the name (two lines kept), the short line ([sub], or [subView] such as a
     * TM's type chip) and [bottom] (the price, or "Owned") at the bottom. A tap calls [onTap] (PLAN 210: it opens the item's window; no arrow).
     */
    fun tile(ctx: Context, lead: View, name: String, bottom: View, description: String, sub: String? = null, subView: View? = null, stock: String? = null, onTap: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
        setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6))
        contentDescription = description + (stock?.let { ", $it" } ?: "")
        setOnClickListener { onTap() }
        addView(lead)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name })
            sub?.let { addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = it }) }
            subView?.let { addView(it, LinearLayout.LayoutParams(-2, -2)) }
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8); it.marginEnd = Ui.dp(ctx, 6) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            stock?.let { addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = it; gravity = Gravity.END }, LinearLayout.LayoutParams(-1, -2)) }
            addView(bottom, LinearLayout.LayoutParams(-2, -2))
        })
        if (stock == "Sold out") alpha = 0.45f
    }

    /** "✓ Owned" in a TM tile or card. */
    fun owned(ctx: Context, sp: Float = 12f): View = Ui.text(ctx, sp, 900, R.color.ag_green).apply { text = "✓ Owned" }

    // ---- the weekend card ----

    fun offers(ctx: Context, daily: View?, weekend: View?): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        daily?.let { addView(it, LinearLayout.LayoutParams(-1, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 8) }) }
        weekend?.let { addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }

    fun weekendCard(ctx: Context, lead: View, name: String, note: String, right: View, description: String, stock: String? = null, onTap: () -> Unit): View =
        offerCard(ctx, "WEEKEND SPECIAL", note, lead, name, "Weekend exclusive", right,
            R.color.ag_teal, description, stock, onTap)

    fun dealCard(ctx: Context, item: String, name: String, price: ShopPlan.Price, ends: String, description: String,
                 line: String = ItemInfo.effect(item), night: Boolean = false, stock: String? = null, onTap: () -> Unit): View =
        offerCard(ctx, if (night) "DEAL OF THE NIGHT" else "DEAL OF THE DAY", ends,
            itemArt(ctx, item, 48), name, line,
            price(ctx, price),
            if (night) R.color.ag_blue else R.color.ag_peach, description, stock, onTap)

    /** Compact offer with a filled title band separated from its item and price. */
    private fun offerCard(ctx: Context, title: String, ends: String, lead: View, name: String,
                          detail: String, price: View, accentRes: Int, description: String, stock: String?, onTap: () -> Unit): View {
        val accent = Multicolor.color(ctx, Multicolor.resource(accentRes), accentRes)
        val card = Ui.c(ctx, R.color.ag_card)
        val ink = if (Multicolor.enabled(ctx)) Multicolor.ink(ctx, Multicolor.resource(accentRes), Bits.blend(accent, card, .22f))
            else if (TypeChips.isDark(ctx)) accent else Bits.blend(0xFF000000.toInt(), accent, 0.35f)
        fun shape(fill: Int, stroke: Int = 0) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = Ui.dp(ctx, 12f)
            if (stroke > 0) setStroke(Ui.dp(ctx, stroke), accent)
        }
        val large = ctx.resources.configuration.fontScale >= 1.5f
        val header = LinearLayout(ctx).apply {
            orientation = if (large) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 7), Ui.dp(ctx, 10), Ui.dp(ctx, 7))
            background = shape(Bits.blend(accent, card, 0.22f)).apply {
                // Round only the outside top corners; the band fills both corners at the divider.
                val radius = Ui.dp(ctx, 12f)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            addView(Ui.text(ctx, 10f, 900, R.color.ag_text).apply { text = title; setTextColor(ink) },
                if (orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
            addView(Ui.text(ctx, 10f, 700, R.color.ag_sub0).apply { text = ends })
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(card)
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8))
            addView(lead)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name })
                addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = detail })
                if (large) {
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                        stock?.let { addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = it; gravity = Gravity.END }, LinearLayout.LayoutParams(-1, -2)) }
                        addView(price, LinearLayout.LayoutParams(-2, -2))
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8); it.marginEnd = Ui.dp(ctx, 6) })
            if (!large) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                stock?.let { addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = it; gravity = Gravity.END }, LinearLayout.LayoutParams(-1, -2)) }
                addView(price, LinearLayout.LayoutParams(-2, -2))
            })
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(card, 2)
            setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2))
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(View(ctx).apply { setBackgroundColor(accent) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 1)))
            addView(body, LinearLayout.LayoutParams(-1, -2))
            if (stock == "Sold out") alpha = 0.45f
            contentDescription = "$title, $ends, $description" + (stock?.let { ", $it" } ?: "")
            setOnClickListener { onTap() }
        }
    }
}
