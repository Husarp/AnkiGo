package com.husarp.ankigo.ui

/** Round 2's four approved palettes; the remaining item sprites keep their characteristic shell colour. */
internal object BallTrail {
    class Colors(val main: Int, val core: Int)
    private val poke = Colors(0xFFFF4646.toInt(), 0xFFFFEBEB.toInt())
    private val great = Colors(0xFF4696FF.toInt(), 0xFFD7EBFF.toInt())
    private val ultra = Colors(0xFFFFD732.toInt(), 0xFFFFFAC8.toInt())
    private val master = Colors(0xFFB95AFF.toInt(), 0xFFF5D7FF.toInt())
    private val green = Colors(0xFF63BB72.toInt(), 0xFFDDFFE0.toInt())
    private val pink = Colors(0xFFEE8EBF.toInt(), 0xFFFFE0F2.toInt())
    private val dark = Colors(0xFF638D94.toInt(), 0xFFDEFFFF.toInt())
    private val white = Colors(0xFFE2E5ED.toInt(), 0xFFFFFFFF.toInt())
    fun colors(item: String): Colors = when (item) {
        "great-ball", "dive-ball", "net-ball", "quick-ball", "beast-ball" -> great
        "ultra-ball", "level-ball", "fast-ball", "repeat-ball", "park-ball" -> ultra
        "master-ball", "dream-ball" -> master
        "safari-ball", "nest-ball", "friend-ball", "dusk-ball" -> green
        "love-ball", "heal-ball" -> pink
        "heavy-ball", "lure-ball", "moon-ball", "luxury-ball" -> dark
        "premier-ball", "timer-ball", "sport-ball" -> white
        else -> poke
    }
}
