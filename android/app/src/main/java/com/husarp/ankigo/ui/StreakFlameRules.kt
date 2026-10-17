package com.husarp.ankigo.ui

/** Pure rules of [StreakFlame] (which picture, whether it may move); no Android classes, so they are unit-tested on the JVM. */
object StreakFlameRules {
    /** First Android version with ImageDecoder / AnimatedImageDrawable. */
    const val ANIMATED_MIN_SDK = 28
    /** Opacity of the flame at a streak of 0 (a grey, faded flame). */
    const val DEAD_ALPHA = 115 // ~45 % of 255

    enum class Picture { ANIMATED, STILL }

    /** The animated WebP only on API 28+, with motion allowed, outside the screenshot renderer, when the streak is alive and decoding worked. */
    fun picture(sdk: Int, reducedMotion: Boolean, renderer: Boolean, alive: Boolean, decodeFailed: Boolean = false): Picture =
        if (sdk >= ANIMATED_MIN_SDK && !reducedMotion && !renderer && alive && !decodeFailed) Picture.ANIMATED else Picture.STILL

    /** The animation runs only while it can be seen: attached, window visible and the view itself shown. */
    fun shouldRun(picture: Picture, attached: Boolean, windowVisible: Boolean, shown: Boolean): Boolean =
        picture == Picture.ANIMATED && attached && windowVisible && shown

    /** Alpha of the image: full for a live streak, [DEAD_ALPHA] for 0. */
    fun alpha(alive: Boolean): Int = if (alive) 255 else DEAD_ALPHA
}
