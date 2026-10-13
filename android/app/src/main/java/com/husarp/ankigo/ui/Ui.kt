package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared look of the game screens (docs/design/mockup.html): colours come from `@color/ag_*`, text is Nunito,
 * everything is built in code with plain Views. Each helper returns a ready View/Drawable; the caller adds it to its layout.
 */
object Ui {
    private val fonts = ConcurrentHashMap<Int, Typeface>()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    /** Colour int of a `@color/ag_*` resource (follows light/dark). */
    fun c(ctx: Context, @ColorRes res: Int): Int = ctx.getColor(res)

    /** Nunito at [weight] (400, 600, 700, 800 or 900), cached; falls back to the system font if the asset can't load. */
    fun font(ctx: Context, weight: Int): Typeface = fonts.getOrPut(weight) {
        try {
            Typeface.Builder(ctx.applicationContext.assets, "fonts/Nunito.ttf")
                .setFontVariationSettings("'wght' $weight").build()
                ?: Typeface.DEFAULT
        } catch (e: Exception) {
            if (weight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /** A TextView in Nunito. Font padding is off so heights match the mockup's CSS line boxes. */
    fun text(ctx: Context, sizeSp: Float, weight: Int, @ColorRes colorRes: Int): TextView = TextView(ctx).apply {
        typeface = font(ctx, weight)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(c(ctx, colorRes))
        includeFontPadding = false
    }

    // ---------- types ----------

    /** Mockup TYPE map. */
    fun typeColor(t: Type): Int = when (t) {
        Type.NORMAL -> 0xFFA8A77A.toInt(); Type.FIRE -> 0xFFEE8130.toInt(); Type.WATER -> 0xFF6390F0.toInt()
        Type.ELECTRIC -> 0xFFF7D02C.toInt(); Type.GRASS -> 0xFF7AC74C.toInt(); Type.ICE -> 0xFF96D9D6.toInt()
        Type.FIGHTING -> 0xFFC22E28.toInt(); Type.POISON -> 0xFFA33EA1.toInt(); Type.GROUND -> 0xFFE2BF65.toInt()
        Type.FLYING -> 0xFFA98FF3.toInt(); Type.PSYCHIC -> 0xFFF95587.toInt(); Type.BUG -> 0xFFA6B91A.toInt()
        Type.ROCK -> 0xFFB6A136.toInt(); Type.GHOST -> 0xFF735797.toInt(); Type.DRAGON -> 0xFF6F35FC.toInt()
        Type.DARK -> 0xFF705746.toInt(); Type.STEEL -> 0xFFB7B7CE.toInt(); Type.FAIRY -> 0xFFD685AD.toInt()
    }

    /** Text colour on a type chip: dark for the light-coloured types (mockup DARKTXT), else white. */
    fun typeTextColor(t: Type): Int = when (t) {
        Type.ELECTRIC, Type.GROUND, Type.ICE, Type.NORMAL, Type.STEEL, Type.GRASS, Type.BUG, Type.ROCK,
        Type.FIRE, Type.FAIRY, Type.FLYING -> 0xFF1E1E2E.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    /** The small coloured type label ("ELECTRIC"). */
    fun typeChip(ctx: Context, type: Type): TextView = text(ctx, 9f, 900, R.color.ag_text).apply {
        text = type.name
        setTextColor(typeTextColor(type))
        letterSpacing = 0.05f
        setPadding(dp(ctx, 6), dp(ctx, 1), dp(ctx, 6), dp(ctx, 1))
        background = rounded(typeColor(type), 6, ctx)
    }

    /** HP bar colour: green above 50 %, yellow above 20 %, else red. */
    fun hpColor(ctx: Context, frac: Float): Int =
        c(ctx, if (frac > 0.5f) R.color.ag_green else if (frac > 0.2f) R.color.ag_yellow else R.color.ag_red)

    // ---------- shapes ----------

    fun rounded(color: Int, radiusDp: Float, ctx: Context): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp)
    }

    fun rounded(color: Int, radiusDp: Int, ctx: Context): GradientDrawable = rounded(color, radiusDp.toFloat(), ctx)

    /** A vertical LinearLayout on `ag_card`, 14 dp corners, no padding (add your own). */
    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c(ctx, R.color.ag_card), 14, ctx)
    }

    /** Chip with a tinted icon and a number/text, tinted by [colorRes] (mockup `.chip`). */
    fun chip(ctx: Context, @DrawableRes iconRes: Int, @ColorRes colorRes: Int): Chip = Chip(ctx, iconRes, colorRes)

    /** Small bold label (mockup `.log .pill`); with [toneRes] it is filled with that colour and the text turns `ag_crust`. */
    fun pill(ctx: Context, text: String, @ColorRes toneRes: Int = 0): TextView =
        text(ctx, 10f, 900, if (toneRes == 0) R.color.ag_text else R.color.ag_crust).apply {
            this.text = text
            letterSpacing = 0.06f
            setPadding(dp(ctx, 7), dp(ctx, 3), dp(ctx, 7), dp(ctx, 3))
            background = rounded(c(ctx, if (toneRes == 0) R.color.ag_s0 else toneRes), 7, ctx)
        }

    /**
     * Screen title bar (mockup `.appbar`): optional back arrow (finishes [activity]), title 22 sp / 900, and an optional
     * right-hand pill, which can carry a tinted icon (Shop: coin + balance).
     */
    fun appBar(
        activity: Activity,
        title: String,
        pillText: String? = null,
        back: Boolean = false,
        @DrawableRes pillIconRes: Int = 0,
        @ColorRes pillIconColorRes: Int = R.color.ag_yellow,
    ): AppBar = AppBar(activity, title, pillText, back, pillIconRes, pillIconColorRes)

    // ---------- images ----------

    /** Bitmap as a drawable with filtering off, so upscaled pixel art stays crisp. Use with setImageDrawable. */
    fun pixelBitmap(ctx: Context, bmp: Bitmap): BitmapDrawable = BitmapDrawable(ctx.resources, bmp).apply {
        isFilterBitmap = false
        setAntiAlias(false)
    }

    /**
     * Shows the item icon in [view]: from disk if cached, else a letter badge now and the real icon when the download
     * lands (stays on the badge if PokeAPI has none). [item] is the PokeAPI-style id, e.g. "poke-ball".
     */
    fun itemIcon(ctx: Context, item: String, view: ImageView) {
        view.setTag(R.id.ag_tag_sprite, "item:$item")
        view.clearColorFilter()
        view.alpha = 1f
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        val have = Sprites.itemBitmap(ctx, item)
        if (have != null) {
            view.setImageDrawable(pixelBitmap(ctx, have))
            return
        }
        view.setImageDrawable(LetterBadge(ctx, item))
        Sprites.fetchItem(ctx, item) { bmp ->
            if (bmp != null && view.getTag(R.id.ag_tag_sprite) == "item:$item") view.setImageDrawable(pixelBitmap(ctx, bmp))
        }
    }

    /**
     * Shows the 96x96 static sprite of Pokédex number [num] in [view] (downloads it if missing; the view stays empty
     * until then). [silhouette] draws it as the grey "never met" shape (mockup `.unseen`).
     */
    fun staticSprite(ctx: Context, num: Int, shiny: Boolean, view: ImageView, silhouette: Boolean = false) {
        val key = "mon:$num:$shiny"
        view.setTag(R.id.ag_tag_sprite, key)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        if (silhouette) {
            val g = ctx.resources.getInteger(R.integer.ag_sil)
            view.colorFilter = PorterDuffColorFilter(0xFF000000.toInt() or (g shl 16) or (g shl 8) or g, PorterDuff.Mode.SRC_IN)
            view.alpha = 0.7f
        } else {
            view.clearColorFilter()
            view.alpha = 1f
        }
        val have = Sprites.staticBitmap(ctx, num, shiny)
        if (have != null) {
            view.setImageDrawable(pixelBitmap(ctx, have))
            return
        }
        view.setImageDrawable(null)
        Sprites.fetchStatic(ctx, num, shiny) { bmp ->
            if (bmp != null && view.getTag(R.id.ag_tag_sprite) == key) view.setImageDrawable(pixelBitmap(ctx, bmp))
        }
    }

    /** Short message toast (a plain system Toast: custom toast views are ignored on newer Android). */
    fun toast(ctx: Context, text: String) {
        Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    /** Fallback item icon: `ag_mantle` disc with the item's initials ("poke-ball" -> "PB", "potion" -> "PO"). */
    private class LetterBadge(private val ctx: Context, item: String) : Drawable() {
        private val letters: String = run {
            val words = item.split('-', '_', ' ').filter { it.isNotEmpty() }
            when {
                words.size >= 2 -> "${words[0][0]}${words[1][0]}"
                words.size == 1 -> words[0].take(2)
                else -> "?"
            }.uppercase()
        }
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(ctx, R.color.ag_mantle) }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c(ctx, R.color.ag_sub0)
            typeface = font(ctx, 900)
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val b: Rect = bounds
            val r = minOf(b.width(), b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, disc)
            ink.textSize = r * 0.85f
            canvas.drawText(letters, b.exactCenterX(), b.exactCenterY() - (ink.descent() + ink.ascent()) / 2f, ink)
        }

        override fun setAlpha(alpha: Int) { disc.alpha = alpha; ink.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { disc.colorFilter = cf; ink.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

/**
 * Icon + text on an `ag_mantle` pill (mockup `.chip`): 13 dp icon, 11 sp / 800 text. Icon and text share one tone colour.
 */
class Chip(ctx: Context, @DrawableRes iconRes: Int, @ColorRes toneRes: Int) : LinearLayout(ctx) {
    private val icon = ImageView(ctx).apply {
        setImageResource(iconRes)
        layoutParams = LayoutParams(Ui.dp(ctx, 13), Ui.dp(ctx, 13))
    }
    val label: TextView = Ui.text(ctx, 11f, 800, toneRes).apply {
        maxLines = 1
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.marginStart = Ui.dp(ctx, 4)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 7), Ui.dp(ctx, 4), Ui.dp(ctx, 7), Ui.dp(ctx, 4))
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 9, ctx)
        addView(icon)
        addView(label)
        setTone(toneRes)
    }

    fun setText(text: CharSequence) { label.text = text }

    /** Recolours icon and text, e.g. the combo chip goes `ag_sub0` at zero. */
    fun setTone(@ColorRes toneRes: Int) {
        val col = Ui.c(context, toneRes)
        icon.setColorFilter(col, PorterDuff.Mode.SRC_IN)
        label.setTextColor(col)
    }

    /** Short scale pulse (1 -> 1.12 -> 1 over 300 ms) when the value changes. */
    fun bump() {
        animate().cancel()
        scaleX = 1f; scaleY = 1f
        animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }
}

/** The screen title bar built by [Ui.appBar]. */
class AppBar(
    private val activity: Activity,
    title: String,
    pillText: String?,
    back: Boolean,
    @DrawableRes pillIconRes: Int,
    @ColorRes pillIconColorRes: Int,
) : LinearLayout(activity) {
    val titleView: TextView = Ui.text(activity, 22f, 900, R.color.ag_text).apply {
        text = title
        letterSpacing = -0.014f
        maxLines = 1
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    /** The right-hand pill (null when none was asked for). */
    val pill: TextView?
    private val pillBox: LinearLayout?

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Ui.c(activity, R.color.ag_base))
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12), Ui.dp(activity, 16), Ui.dp(activity, 10))
        if (back) {
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_back)
                setColorFilter(Ui.c(activity, R.color.ag_text), PorterDuff.Mode.SRC_IN)
                setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4))
                contentDescription = "Back"
                layoutParams = LayoutParams(Ui.dp(activity, 32), Ui.dp(activity, 32)).also { it.marginEnd = Ui.dp(activity, 6) }
                setOnClickListener { activity.finish() }
            })
        }
        addView(titleView)
        if (pillText != null) {
            val tv = Ui.text(activity, 12f, 800, if (pillIconRes != 0) R.color.ag_text else R.color.ag_sub1).apply { text = pillText }
            val box = LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 9), Ui.dp(activity, 5), Ui.dp(activity, 9), Ui.dp(activity, 5))
                background = Ui.rounded(Ui.c(activity, R.color.ag_mantle), 10, activity)
                if (pillIconRes != 0) {
                    addView(ImageView(activity).apply {
                        setImageResource(pillIconRes)
                        setColorFilter(Ui.c(activity, pillIconColorRes), PorterDuff.Mode.SRC_IN)
                        layoutParams = LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14)).also { it.marginEnd = Ui.dp(activity, 4) }
                    })
                }
                addView(tv)
            }
            addView(box)
            pill = tv
            pillBox = box
        } else {
            pill = null
            pillBox = null
        }
    }

    /** Changes the pill text (no-op when the bar has no pill). */
    fun setPill(text: String) { pill?.text = text }
}
