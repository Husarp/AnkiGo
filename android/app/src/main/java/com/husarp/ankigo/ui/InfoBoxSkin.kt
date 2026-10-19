package com.husarp.ankigo.ui

/**
 * What each [InfoBoxLook] paints (Round 10 W7, docs/design/round10.html D8, the `.bx.sk-*` skins): colours, edges and strokes as plain
 * numbers, no Android classes, so it is JVM-tested. The two users draw from this one spec and cannot drift apart: the real boxes
 * ([ArenaView], through [InfoBoxDrawable]) and the small previews ([BoxLookPreview]).
 *
 * A look has up to four parts:
 * - its background [layers] (bottom first): the blurred picture, a fill, a vertical veil, edge rings, a type stripe;
 * - [ink]: what the INSIDE of the box is painted in. The looks that are "the same in light and dark" (frame, plate, HUD) paint everything
 *   in the DARK palette (text, bar colours, level tiers, type chips, the status badge); the others keep the theme's colours (`null`);
 * - the [textShadow] and the [barRing] of the looks that have no plate behind their text;
 * - the corner placement (HUD, [corners]) and the drop shadow ([elevationDp], glass).
 *
 * All colours are ARGB ints.
 */
internal object InfoBoxSkin {
    // ---- the dark theme's palette (res/values-night/colors.xml; a test compares them) ----
    const val DARK_TEXT = 0xFFCDD6F4.toInt()
    const val DARK_SUB0 = 0xFFA6ADC8.toInt()
    const val DARK_RED = 0xFFF38BA8.toInt()
    const val DARK_PEACH = 0xFFFAB387.toInt()
    const val DARK_YELLOW = 0xFFF9E2AF.toInt()
    const val DARK_GREEN = 0xFFA6E3A1.toInt()
    const val DARK_SKY = 0xFF89DCEB.toInt()
    const val DARK_LAV = 0xFFB4BEFE.toInt()
    const val DARK_MAUVE = 0xFFCBA6F7.toInt()

    /** Level tiers 0..9 (bronze .. master) in the dark theme. */
    val DARK_TIERS = intArrayOf(
        0xFFE0A370.toInt(), 0xFFC5CDE0.toInt(), 0xFFF5CF63.toInt(), 0xFF7FE0A8.toInt(), 0xFF8FB6FF.toInt(),
        0xFFFF8FA8.toInt(), 0xFFCFA6FF.toInt(), 0xFFA9D4DE.toInt(), 0xFF8EE6FF.toInt(), 0xFFFF9AD0.toInt(),
    )

    /** The card colour in the dark theme (what a fixed-dark chip is washed onto). */
    const val DARK_CARD = 0xFF262637.toInt()

    // ---- the looks ----

    /** True for the looks that paint the same in light and dark: everything inside the box uses the dark palette. */
    fun fixedDark(look: InfoBoxLook): Boolean = look == InfoBoxLook.FRAME || look == InfoBoxLook.PLATE || look == InfoBoxLook.HUD

    /** True when the look has a box (a background); the HUD has none. */
    fun hasBox(look: InfoBoxLook): Boolean = look != InfoBoxLook.HUD

    /** True when the two boxes go to two opposite corners of the arena ([StageGeo]'s corners mode). */
    fun corners(look: InfoBoxLook): Boolean = look == InfoBoxLook.HUD

    /** True when the look shows the picture behind the box (blurred). */
    fun glass(look: InfoBoxLook): Boolean = look == InfoBoxLook.GLASS

    /** True when the look needs the Pokémon's first type colour. */
    fun needsType(look: InfoBoxLook): Boolean = look == InfoBoxLook.TYPE

    /** The box's corner radius (dp): [base] (12, or 10 in the study strip) except the plate. */
    fun radiusDp(look: InfoBoxLook, base: Float): Float = if (look == InfoBoxLook.PLATE) PLATE_RADIUS else base

    /** The extra elevation (dp) that casts the soft drop shadow of the glass look (0 for the others). */
    fun elevationDp(look: InfoBoxLook): Float = if (look == InfoBoxLook.GLASS) GLASS_ELEVATION else 0f

    const val PLATE_RADIUS = 9f
    const val GLASS_ELEVATION = 3f

    // ---- the background layers ----

    enum class Kind {
        /** The blurred picture (or the plain arena gradient) behind the box; drawn by the host ([GlassBackdrop]). */
        BLUR,
        /** The whole rounded box in [Layer.color]. */
        FILL,
        /** The whole rounded box as a vertical gradient from [Layer.color] (top) to [Layer.color2] (bottom). */
        GRADIENT,
        /** A ring of [Layer.widthDp] just inside the box, [Layer.insetDp] in from its edge. */
        EDGE,
        /** A stripe of [Layer.widthDp] on the box's left edge, inside the rounded rectangle. */
        STRIPE,
    }

    class Layer(val kind: Kind, val color: Int = 0, val color2: Int = 0, val widthDp: Float = 0f, val insetDp: Float = 0f)

    /** The card colour at 92 % (today's boxes). */
    const val TODAY_ALPHA = 0xEB

    private const val WHITE = 0xFFFFFF
    private const val PLATE_NAVY = 0xFF1F2537.toInt()
    private const val PLATE_GOLD = 0xFFD6B052.toInt()

    /**
     * The background layers of [look], bottom first. [card] is the theme's `ag_card` (opaque), [dark] the theme, [typeColor] the
     * Pokémon's first type colour (the type look; null = it has none, and the look is the plain card).
     */
    fun layers(look: InfoBoxLook, card: Int, dark: Boolean, typeColor: Int?): List<Layer> = when (look) {
        InfoBoxLook.TODAY -> listOf(Layer(Kind.FILL, withAlpha(card, TODAY_ALPHA)))
        // card at 40 % over the blurred picture, a 1 dp inner edge of white 50 %
        InfoBoxLook.GLASS -> listOf(Layer(Kind.BLUR), Layer(Kind.FILL, withAlpha(card, 0x66)), Layer(Kind.EDGE, withAlpha(WHITE, 0x80), widthDp = 1f))
        // rgba(10,12,24,.42) -> .22, a 1.5 dp frame of white 88 %
        InfoBoxLook.FRAME -> listOf(Layer(Kind.GRADIENT, 0x6B0A0C18, 0x380A0C18), Layer(Kind.EDGE, withAlpha(WHITE, 0xE0), widthDp = 1.5f))
        // navy, gold 2 dp, navy 1 dp, gold at 35 % 1 dp
        InfoBoxLook.PLATE -> listOf(
            Layer(Kind.FILL, PLATE_NAVY),
            Layer(Kind.EDGE, PLATE_GOLD, widthDp = 2f),
            Layer(Kind.EDGE, PLATE_NAVY, widthDp = 1f, insetDp = 2f),
            Layer(Kind.EDGE, withAlpha(PLATE_GOLD, 0x59), widthDp = 1f, insetDp = 3f),
        )
        InfoBoxLook.HUD -> emptyList()
        // the card washed with the type colour (30 % in the dark theme, 22 % in the light one) at 93 %, a 4 dp stripe of the type colour
        InfoBoxLook.TYPE ->
            if (typeColor == null) layers(InfoBoxLook.TODAY, card, dark, null)
            else listOf(Layer(Kind.FILL, withAlpha(TypeInk.mix(card, typeColor, if (dark) 0.30f else 0.22f), 0xED)), Layer(Kind.STRIPE, typeColor, widthDp = 4f))
    }

    // ---- what is inside ----

    /** What the inside of a box is painted in. */
    class Ink(
        val text: Int, val sub: Int, val track: Int,
        val green: Int, val yellow: Int, val red: Int, val xp: Int,
        /** The tier colour of a level (0..9 by [tierIndex]). */
        val tiers: IntArray,
    ) {
        /** HP bar colour: green above 50 %, yellow above 20 %, else red ([Ui.hpColor]). */
        fun hp(frac: Float): Int = if (frac > 0.5f) green else if (frac > 0.2f) yellow else red

        fun tier(level: Int): Int = tiers[tierIndex(level)]
    }

    /** The tier index of a level, as [LevelTier.index]. */
    fun tierIndex(level: Int): Int = (level.coerceIn(1, 100) / 10).coerceAtMost(9)

    /**
     * The colours of the inside of a [look] box; null for the looks that keep the theme's own colours (card, glass, type colour).
     * The frame, the plate and the HUD are the same in light and dark: the dark palette with the look's own text, sub text and track.
     */
    fun ink(look: InfoBoxLook): Ink? = INKS[look.ordinal]

    // built once: the boxes ask on every colour they set
    private val INKS: Array<Ink?> = Array(InfoBoxLook.entries.size) { inkOf(InfoBoxLook.entries[it]) }

    private fun inkOf(look: InfoBoxLook): Ink? = when (look) {
        InfoBoxLook.FRAME -> Ink(0xFFFFFFFF.toInt(), 0xFFE8EAF6.toInt(), 0x73000000, DARK_GREEN, DARK_YELLOW, DARK_RED, DARK_SKY, DARK_TIERS)
        InfoBoxLook.HUD -> Ink(0xFFFFFFFF.toInt(), 0xFFF0F0F8.toInt(), 0x80000000.toInt(), DARK_GREEN, DARK_YELLOW, DARK_RED, DARK_SKY, DARK_TIERS)
        InfoBoxLook.PLATE -> Ink(0xFFF3F3F7.toInt(), 0xFFC3CBE2.toInt(), 0xFF3B4460.toInt(), 0xFF5FD35A.toInt(), 0xFFF2C94C.toInt(), DARK_RED, 0xFF5AB8FF.toInt(), DARK_TIERS)
        else -> null
    }

    /** The badge colour of an engine status in the dark palette (poison mauve, burn peach, paralysis yellow, sleep lavender, freeze sky); as [StatusBadge.colorRes]. */
    fun statusFill(status: String?): Int = when (status) {
        "psn", "tox" -> DARK_MAUVE
        "brn" -> DARK_PEACH
        "par" -> DARK_YELLOW
        "slp" -> DARK_LAV
        else -> DARK_SKY
    }

    // ---- text shadows and the bar ring (the looks without a plate) ----

    /** A text shadow: radius, offset (dp) and colour. */
    class TextShadow(val radiusDp: Float, val dxDp: Float, val dyDp: Float, val color: Int)

    /** The shadow of the texts in the box (frame: soft; HUD: a strong dark outline that reads on any picture); null = none. */
    fun textShadow(look: InfoBoxLook): TextShadow? = when (look) {
        InfoBoxLook.FRAME -> TextShadow(2f, 0f, 1f, 0xB3000000.toInt())
        InfoBoxLook.HUD -> TextShadow(2.5f, 0f, 0.5f, 0xFF000000.toInt())
        else -> null
    }

    /** The ring round the HP / XP bars of the HUD: [color] [widthDp] wide, and a soft shadow under it; null = none. */
    class BarRing(val color: Int, val widthDp: Float, val shadow: TextShadow)

    fun barRing(look: InfoBoxLook): BarRing? =
        if (look == InfoBoxLook.HUD) BarRing(withAlpha(WHITE, 0xBF), 1f, TextShadow(3f, 0f, 1f, 0x99000000.toInt())) else null

    // ---- helpers ----

    /** [rgb] with alpha [a] (0..255). */
    fun withAlpha(rgb: Int, a: Int): Int = (rgb and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
