package com.husarp.ankigo.ui

/** Mean Rec.709 RGB luma / 255, measured once from the approved final-50 WebPs. */
internal object BackdropLight {
    const val MAX_DIM = .2f
    const val NORMAL_LUMA = .45f
    val luminance = mapOf(
        "sandbar-isle" to 0.6495f,
        "reef-bay" to 0.6540f,
        "canyon" to 0.5175f,
        "crystal-storm" to 0.2057f,
        "haunted-mansion" to 0.1689f,
        "mine" to 0.1993f,
        "snow-aurora" to 0.4611f,
        "legendary-shrine" to 0.4724f,
        "dark-town" to 0.1737f,
        "dark-forest" to 0.0818f,
        "fire-savanna" to 0.3595f,
        "fighting-training" to 0.6624f,
        "forest-tech-b" to 0.2693f,
        "autumn-forest" to 0.4131f,
        "countryside-route" to 0.5890f,
        "windy-hilltop" to 0.5919f,
        "bamboo-dojo" to 0.4529f,
        "snowy-pines" to 0.7083f,
        "power-plant" to 0.4235f,
        "desert-oasis" to 0.7317f,
        "starry-plateau" to 0.2731f,
        "lava-field" to 0.2408f,
        "night-rooftops" to 0.2986f,
        "old-graveyard" to 0.2264f,
        "starry-plateau-galaxy" to 0.2817f,
        "dojo" to 0.6900f,
        "graveyard" to 0.2887f,
        "ruined-castle" to 0.1515f,
        "snow-field" to 0.7518f,
        "volcano-b" to 0.2248f,
        "night-forest-2" to 0.1544f,
        "normal-meadow" to 0.6330f,
        "grass-glade" to 0.5243f,
        "poison-swamp" to 0.2228f,
        "flying-ridge" to 0.6161f,
        "violet-plateau" to 0.4888f,
    )

    fun dim(luma: Float): Float = ((NORMAL_LUMA - luma) / NORMAL_LUMA * MAX_DIM).coerceIn(0f, MAX_DIM)
    fun forScene(id: String?): Float = luminance[id]?.let(::dim) ?: 0f
    fun blend(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction.coerceIn(0f, 1f)
}
