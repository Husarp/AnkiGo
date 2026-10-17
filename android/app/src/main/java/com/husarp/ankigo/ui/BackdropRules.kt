package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type

/**
 * Round 7 battle backgrounds, which picture the wild Pokémon gets (pure, JVM-tested). The picture follows the wild one's type: its
 * first, or its second when the first is Normal and it has one (Normal/Flying = Flying). Every type has two or more pictures
 * (Ice has only the ice cave, Dark only the night forest), picked by `species % options` so the same species always gets the same picture, also after a
 * restart or a redraw from the save. NIGHT = the forest drawn darkened with a blue tint ([BackdropTint]) and with fireflies ([FireflyRules]); it is the only night picture.
 */
internal object BackdropRules {
    /** One of the 14 downloaded pictures ([com.husarp.ankigo.data.Backdrops.NAMES]), drawn as it is or as night. */
    data class Choice(val name: String, val night: Boolean = false)

    private fun c(name: String, night: Boolean = false) = Choice(name, night)

    private val OPTIONS: Map<Type, List<Choice>> = mapOf(
        Type.NORMAL to listOf(c("meadow"), c("route")),
        Type.FIRE to listOf(c("volcanocave"), c("desert")),
        Type.WATER to listOf(c("beach"), c("river"), c("beachshore"), c("deepsea")),
        Type.GRASS to listOf(c("forest"), c("meadow")),
        Type.ELECTRIC to listOf(c("city"), c("route")),
        Type.ICE to listOf(c("icecave")),
        Type.FIGHTING to listOf(c("mountain"), c("route")),
        Type.POISON to listOf(c("dampcave"), c("forest")),
        Type.GROUND to listOf(c("desert"), c("earthycave")),
        Type.FLYING to listOf(c("mountain"), c("meadow")),
        Type.PSYCHIC to listOf(c("city"), c("forest")),
        Type.BUG to listOf(c("forest"), c("meadow")),
        Type.ROCK to listOf(c("mountain"), c("earthycave")),
        Type.GHOST to listOf(c("forest", night = true), c("dampcave")), // night = the forest only (R7-X1), with fireflies
        Type.DRAGON to listOf(c("mountain"), c("volcanocave")),
        Type.DARK to listOf(c("forest", night = true)),
        Type.STEEL to listOf(c("city"), c("earthycave")),
        Type.FAIRY to listOf(c("meadow"), c("forest")),
    )

    /** The pictures a type can get, in the order `species % size` picks from. */
    fun options(type: Type): List<Choice> = OPTIONS.getValue(type)

    /** The type the picture follows; Normal when there is none (no species data). */
    fun typeFor(types: List<Type>): Type {
        val first = types.firstOrNull() ?: return Type.NORMAL
        return if (first == Type.NORMAL) types.getOrNull(1) ?: Type.NORMAL else first
    }

    /** The picture for a wild Pokémon of [species] (its number) with [types]. */
    fun pick(types: List<Type>, species: Int): Choice {
        val options = options(typeFor(types))
        return options[Math.floorMod(species, options.size)]
    }
}

/** The colour of a picture in the arena (pure): the dark theme's dusk tint of the mockup, and the darker, bluer night. */
internal object BackdropTint {
    /** Per-channel multipliers (r, g, b) of the dark theme: the mockup's `multiply rgba(34,38,96,.42)` = 1 - .42 + .42 * c / 255. */
    private val DUSK = floatArrayOf(0.636f, 0.643f, 0.738f)

    /** Night: darker and bluer than dusk, in both themes (picked by eye on the forest and route). */
    private val NIGHT = floatArrayOf(0.34f, 0.42f, 0.70f)

    /** The multipliers for a picture in the light or dark theme, as a normal day picture or as night. */
    fun scale(night: Boolean, dark: Boolean): FloatArray = FloatArray(3) { i ->
        (if (night) NIGHT[i] else 1f) * (if (dark) DUSK[i] else 1f)
    }

    /** The same as a 4 x 5 colour matrix (android.graphics.ColorMatrix order), null when the picture is drawn as it is. */
    fun matrix(night: Boolean, dark: Boolean): FloatArray? {
        if (!night && !dark) return null
        val s = scale(night, dark)
        return floatArrayOf(
            s[0], 0f, 0f, 0f, 0f,
            0f, s[1], 0f, 0f, 0f,
            0f, 0f, s[2], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
