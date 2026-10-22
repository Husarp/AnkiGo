package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type

/**
 * Drawn item icons (PLAN 90, picks 7A + 7D): the TM disc (type colour, type symbol in the middle) and the kind badge (own symbol on a
 * soft tint of the kind's colour) that stands in for items without a picture. [Ui.itemIcon] / [Ui.tmIcon] are the renderers.
 */
object ItemIcons {
    @DrawableRes
    fun symbol(k: ItemSymbol): Int = when (k) {
        ItemSymbol.GEM -> R.drawable.ic_item_gem; ItemSymbol.TAG -> R.drawable.ic_item_tag; ItemSymbol.FOSSIL -> R.drawable.ic_item_fossil
        ItemSymbol.BOTTLE -> R.drawable.ic_item_bottle; ItemSymbol.BALL -> R.drawable.ic_item_ball; ItemSymbol.ESCAPE -> R.drawable.ic_item_escape
        ItemSymbol.BAG -> R.drawable.ic_item_bag
    }

    @ColorRes
    fun colorRes(k: ItemSymbol): Int = when (k) {
        ItemSymbol.GEM -> R.color.ag_mauve; ItemSymbol.TAG -> R.color.ag_teal; ItemSymbol.FOSSIL -> R.color.ag_peach
        ItemSymbol.BOTTLE -> R.color.ag_green; ItemSymbol.BALL -> R.color.ag_red; ItemSymbol.ESCAPE -> R.color.ag_sky
        ItemSymbol.BAG -> R.color.ag_sub0
    }

    /** PLAN 166A: the own 16x16 pixel icon (`res/drawable-nodpi/item_<id>.png`) of an [ItemIconPlan.DRAWN] item; null = none (kind badge instead). */
    @DrawableRes
    fun drawnRes(itemId: String): Int? = when (itemId) {
        "sweet-apple" -> R.drawable.item_sweet_apple; "tart-apple" -> R.drawable.item_tart_apple
        "cracked-pot" -> R.drawable.item_cracked_pot; "chipped-pot" -> R.drawable.item_chipped_pot
        "linking-cord" -> R.drawable.item_linking_cord
        else -> null
    }

    /** The pixel icon of [itemId] as a crisp (nearest-neighbour) drawable, or null if it has none. */
    fun drawn(ctx: Context, itemId: String): Drawable? {
        val res = drawnRes(itemId) ?: return null
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(ctx.resources, res, opts) ?: return null
        return Ui.pixelBitmap(ctx, bmp)
    }

    /** A kind badge for [k]: rounded square in a soft tint of the kind colour, the symbol in its ink. */
    fun badge(ctx: Context, k: ItemSymbol): Drawable = Badge(ctx, symbol(k), ctx.getColor(colorRes(k)))

    /** The badge of an item id ([ItemIconPlan.symbolOf]). */
    fun badge(ctx: Context, itemId: String): Drawable = badge(ctx, ItemIconPlan.symbolOf(itemId))

    /** The TM disc of a move of [type]; [type] null (move not in the data) = the plain grey TM disc. */
    fun tmDisc(ctx: Context, type: Type?): Drawable =
        if (type != null) Disc(ctx, TypeChips.symbol(type), Ui.typeColor(type))
        else Disc(ctx, R.drawable.ic_tm, ctx.getColor(R.color.ag_sub0))

    /** Colour of the symbol on a disc of colour [disc]: near-black on a light disc, white on a dark one (contrast-checked by luminance). */
    internal fun discInk(disc: Int): Int {
        val lum = (0.299 * ((disc shr 16) and 0xFF) + 0.587 * ((disc shr 8) and 0xFF) + 0.114 * (disc and 0xFF)) / 255.0
        return if (lum > 0.5) 0xFF1E1E2E.toInt() else 0xFFFFFFFF.toInt()
    }

    private class Badge(ctx: Context, @DrawableRes res: Int, color: Int) : Drawable() {
        private val dark = TypeChips.isDark(ctx)
        private val symbol: Drawable = ContextCompat.getDrawable(ctx, res)!!.mutate().also { it.setTint(TypeInk.ink(color, dark)) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = TypeInk.fill(color, dark) }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val r = minOf(b.width(), b.height()) * 0.28f
            canvas.drawRoundRect(RectF(b), r, r, fill)
            val pad = (minOf(b.width(), b.height()) * 0.2f).toInt()
            symbol.setBounds(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
            symbol.draw(canvas)
        }

        override fun setAlpha(alpha: Int) { fill.alpha = alpha; symbol.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { fill.colorFilter = cf; symbol.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class Disc(ctx: Context, @DrawableRes res: Int, private val color: Int) : Drawable() {
        private val symbol: Drawable = ContextCompat.getDrawable(ctx, res)!!.mutate().also { it.setTint(discInk(color)) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@Disc.color }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = TypeInk.mix(this@Disc.color, 0xFFFFFFFF.toInt(), 0.35f)
            strokeWidth = Ui.dp(ctx, 1.5f)
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val d = minOf(b.width(), b.height())
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            canvas.drawCircle(cx, cy, d / 2f, fill)
            canvas.drawCircle(cx, cy, d / 2f - ring.strokeWidth / 2f - d * 0.04f, ring)
            val half = (d * 0.3f).toInt()
            symbol.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            symbol.draw(canvas)
        }

        override fun setAlpha(alpha: Int) { fill.alpha = alpha; ring.alpha = alpha; symbol.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { fill.colorFilter = cf; ring.colorFilter = cf; symbol.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
