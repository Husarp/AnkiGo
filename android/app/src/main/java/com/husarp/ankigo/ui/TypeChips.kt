package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewParent
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type

/**
 * Type symbols and chips (PLAN 33, pick 5C): the symbol and the name in the type colour on one rounded rectangle, drawn the way
 * Settings → Look → "Show types as" says ([AppPrefs.typeStyle]). [Ui.typeChip] is the one renderer every screen goes through.
 */
object TypeChips {
    /** The symbol of [t] (24 dp vector, one colour: tint it). Also used by the TM discs. */
    @DrawableRes
    fun symbol(t: Type): Int = when (t) {
        Type.NORMAL -> R.drawable.ic_type_normal; Type.FIRE -> R.drawable.ic_type_fire; Type.WATER -> R.drawable.ic_type_water
        Type.ELECTRIC -> R.drawable.ic_type_electric; Type.GRASS -> R.drawable.ic_type_grass; Type.ICE -> R.drawable.ic_type_ice
        Type.FIGHTING -> R.drawable.ic_type_fighting; Type.POISON -> R.drawable.ic_type_poison; Type.GROUND -> R.drawable.ic_type_ground
        Type.FLYING -> R.drawable.ic_type_flying; Type.PSYCHIC -> R.drawable.ic_type_psychic; Type.BUG -> R.drawable.ic_type_bug
        Type.ROCK -> R.drawable.ic_type_rock; Type.GHOST -> R.drawable.ic_type_ghost; Type.DRAGON -> R.drawable.ic_type_dragon
        Type.DARK -> R.drawable.ic_type_dark; Type.STEEL -> R.drawable.ic_type_steel; Type.FAIRY -> R.drawable.ic_type_fairy
    }

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Symbol and name colour of [t] on the current theme. */
    fun ink(ctx: Context, t: Type): Int = TypeInk.ink(Ui.typeColor(t), isDark(ctx))

    /** Soft chip fill of [t] on the current theme. */
    fun fill(ctx: Context, t: Type): Int = TypeInk.fill(Ui.typeColor(t), isDark(ctx))

    /** The symbol of [t] tinted [color] (a fresh drawable, no bounds set). */
    fun tinted(ctx: Context, t: Type, color: Int): Drawable =
        ContextCompat.getDrawable(ctx, symbol(t))!!.mutate().also { it.setTint(color) }

    /** A type chip in [style]. A TextView (callers set maxLines, text floors, click handlers on it as before). */
    fun chip(ctx: Context, t: Type, style: TypeStyle = AppPrefs.typeStyle(ctx)): TextView = Chip(ctx, t, style)

    /**
     * Dresses [tv] as a filter / sort chip of type [t]: the symbol before the name, both in the type colour; selected = soft tint
     * with a ring, else the quiet `ag_mantle` pill with a fainter ink. Call again to change [on].
     */
    fun styleFilter(tv: TextView, t: Type, on: Boolean) {
        val ctx = tv.context
        val ink = ink(ctx, t)
        val shown = if (on) ink else TypeInk.mix(Ui.c(ctx, R.color.ag_mantle), ink, 0.62f)
        tv.setTextColor(shown)
        val d = tinted(ctx, t, shown)
        val s = Ui.dp(ctx, 14f).toInt()
        d.setBounds(0, 0, s, s)
        tv.setCompoundDrawablesRelative(d, null, null, null)
        tv.compoundDrawablePadding = Ui.dp(ctx, 5f).toInt()
        val fill = GradientDrawable().apply {
            cornerRadius = Ui.dp(ctx, 10f)
            setColor(if (on) fill(ctx, t) else Ui.c(ctx, R.color.ag_mantle))
            if (on) setStroke(Ui.dp(ctx, 1.5f).toInt(), ink)
        }
        Ui.touchPill(tv, fill, 28, 10) // 48 dp touch height
    }

    private fun toast(v: View, name: String) = Ui.toast(v.context, name)

    private class Chip(ctx: Context, private val type: Type, private val style: TypeStyle) : TextView(ctx) {
        private val icon: Drawable? = if (style == TypeStyle.TEXT) null else tinted(ctx, type, ink(ctx, type))

        init {
            typeface = Ui.font(ctx, 900)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(ink(ctx, type))
            background = Ui.rounded(fill(ctx, type), 6, ctx)
            if (style == TypeStyle.ICON) {
                // symbol only: a small rounded chip; the name comes from a long press (or a tap that does nothing else)
                contentDescription = type.label
                val p = Ui.dp(ctx, 3f).toInt()
                setPadding(p, p, p, p)
                setOnLongClickListener { toast(this, type.label); true }
                setOnClickListener { forwardTap() }
            } else {
                text = type.label
                val v = Ui.dp(ctx, 1f).toInt()
                val h = Ui.dp(ctx, 6f).toInt()
                setPadding(if (icon != null) Ui.dp(ctx, 5f).toInt() else h, v, h, v)
                compoundDrawablePadding = Ui.dp(ctx, 3f).toInt()
            }
            syncIcon()
        }

        /** A tap on an icon-only chip goes to the nearest ancestor that takes taps; with none, it shows the name. */
        private fun forwardTap() {
            var p: ViewParent? = parent
            while (p is View) {
                if (p.hasOnClickListeners()) { p.performClick(); return }
                p = p.parent
            }
            toast(this, type.label)
        }

        /** The symbol follows the label size (the battle panel's text floor changes it). */
        private fun syncIcon() {
            val d = icon ?: return
            val s = (textSize * if (style == TypeStyle.ICON) 1.45f else 1.2f).toInt()
            d.setBounds(0, 0, s, s)
            setCompoundDrawablesRelative(d, null, null, null)
        }

        override fun setTextSize(unit: Int, size: Float) {
            super.setTextSize(unit, size)
            syncIcon()
        }
    }
}
