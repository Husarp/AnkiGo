package com.husarp.ankigo.ui

/**
 * The colour of a battle background in the arena (pure): the dark theme's dusk tint of the mockup. A picture that is a night scene
 * already ([Backdrop.dark]) gets none ([ArenaBackdrop] passes `dark = darkTheme && !bg.dark`). The blurred copy gets the same tint.
 */
internal object BackdropTint {
    /** Per-channel multipliers (r, g, b) of the dark theme: the mockup's `multiply rgba(34,38,96,.42)` = 1 - .42 + .42 * c / 255. */
    private val DUSK = floatArrayOf(0.636f, 0.643f, 0.738f)

    /** The multipliers for a picture in the light ([dark] false: all 1) or dark theme. */
    fun scale(dark: Boolean): FloatArray = FloatArray(3) { i -> if (dark) DUSK[i] else 1f }

    /** The same as a 4 x 5 colour matrix (android.graphics.ColorMatrix order), null when the picture is drawn as it is. */
    fun matrix(dark: Boolean): FloatArray? {
        if (!dark) return null
        val s = scale(true)
        return floatArrayOf(
            s[0], 0f, 0f, 0f, 0f,
            0f, s[1], 0f, 0f, 0f,
            0f, 0f, s[2], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
