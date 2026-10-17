package com.husarp.ankigo.ui

/** Settings → Look → "Show types as" (PLAN 33). */
enum class TypeStyle(val label: String) {
    TEXT("Text"),
    ICON("Icon"),
    ICON_TEXT("Icon + text"),
}

/**
 * Colours of a type chip (PLAN 33, pick 5C): the symbol and the name in the type colour on a soft tint of that colour. Pure maths
 * (colour ints), so it is unit-tested. The pale types get a darker ink on the light theme and the dark ones a lighter ink on the
 * dark theme, so the ink always reaches [TARGET] contrast on the chip fill (never under [MIN]).
 */
object TypeInk {
    /** Contrast we aim for / the least a test accepts. */
    const val TARGET = 4.5
    const val MIN = 3.0

    private const val LIGHT_SURFACE = 0xFFFFFFFF.toInt() // ag_card, light
    private const val DARK_SURFACE = 0xFF262637.toInt()  // ag_card, dark

    /** Chip fill: [type] colour at 20 % (light) / 28 % (dark) over the card colour. Opaque. */
    fun fill(typeColor: Int, dark: Boolean): Int =
        mix(if (dark) DARK_SURFACE else LIGHT_SURFACE, typeColor, if (dark) 0.28f else 0.20f)

    /** Symbol and name colour: the type colour, pushed toward black (light) or white (dark) until it reads on [fill]. */
    fun ink(typeColor: Int, dark: Boolean): Int {
        val bg = fill(typeColor, dark)
        val to = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        var k = 0
        while (k <= 20) {
            val c = mix(typeColor, to, k / 20f)
            if (contrast(c, bg) >= TARGET) return c
            k++
        }
        return to
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    /** [a] with [t] (0..1) of [b] mixed in; alpha 255. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
