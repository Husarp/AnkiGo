package com.husarp.ankigo.ui.movefx

import com.husarp.ankigo.game.Type

/**
 * One [FxPalette] per type, for the category templates (the page's fallback rules 2 and 3). Ghost and Steel are exactly the
 * page's Shadow Ball / Flash Cannon tints; the others are derived from the type colour: deep = 45 % of it, smoke = 70 % of it,
 * glow = 40 % of the way to white, spark = 80 % of the way to white. Pure Kotlin.
 */
object FxPalettes {
    private val typeColors: Map<Type, Int> = mapOf(
        Type.NORMAL to 0xA8A77A, Type.FIRE to 0xEE8130, Type.WATER to 0x6390F0, Type.ELECTRIC to 0xF7D02C,
        Type.GRASS to 0x7AC74C, Type.ICE to 0x96D9D6, Type.FIGHTING to 0xC22E28, Type.POISON to 0xA33EA1,
        Type.GROUND to 0xE2BF65, Type.FLYING to 0xA98FF3, Type.PSYCHIC to 0xF95587, Type.BUG to 0xA6B91A,
        Type.ROCK to 0xB6A136, Type.DRAGON to 0x6F35FC, Type.DARK to 0x705746, Type.FAIRY to 0xD685AD,
    )

    private val palettes: Map<Type, FxPalette> = Type.entries.associateWith { t ->
        when (t) {
            Type.GHOST -> FxPalette(0xFF3A1458.toInt(), 0xFFB06BFF.toInt(), 0xFF52207A.toInt(), 0xFFE1B8FF.toInt())
            Type.STEEL -> FxPalette(0xFF3F4757.toInt(), 0xFF9EABC2.toInt(), 0xFF6F7889.toInt(), 0xFFFFFFFF.toInt())
            else -> derive(typeColors.getValue(t))
        }
    }

    fun of(type: Type): FxPalette = palettes.getValue(type)

    private fun derive(rgb: Int) = FxPalette(
        deep = scale(rgb, 0.45f), glow = towardWhite(rgb, 0.40f), smoke = scale(rgb, 0.70f), spark = towardWhite(rgb, 0.80f),
    )

    private fun scale(rgb: Int, f: Float) = argb(
        ((rgb shr 16) and 0xFF) * f, ((rgb shr 8) and 0xFF) * f, (rgb and 0xFF) * f,
    )

    private fun towardWhite(rgb: Int, f: Float): Int {
        fun ch(c: Int) = c + (255 - c) * f
        return argb(ch((rgb shr 16) and 0xFF), ch((rgb shr 8) and 0xFF), ch(rgb and 0xFF))
    }

    private fun argb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
}
