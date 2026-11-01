package com.husarp.ankigo.ui

/** PLAN 142 round 6: uninterrupted 6 dp rims only. Bands are outer, highlight, inner.
 * Round 6 supplies eleven palettes; the other three retain round 5 A structural materials.
 * Every catalog scene has an explicit group, independent of encounter type.
 */
internal object SceneryRims {
    private val palettes = mapOf(
        "jungle" to intArrayOf(0xFF4F402B.toInt(), 0xFFA08B50.toInt(), 0xFF292E22.toInt()),
        "beach" to intArrayOf(0xFFAB9264.toInt(), 0xFFF0DAA3.toInt(), 0xFF78694B.toInt()),
        "cave" to intArrayOf(0xFF715741.toInt(), 0xFFBEA078.toInt(), 0xFF4C3C31.toInt()),
        "snow" to intArrayOf(0xFF56738A.toInt(), 0xFFBED9DF.toInt(), 0xFF304D65.toInt()),
        "desert" to intArrayOf(0xFFA16D47.toInt(), 0xFFE2BC83.toInt(), 0xFF69442F.toInt()),
        "fire" to intArrayOf(0xFF59404B.toInt(), 0xFFB17B65.toInt(), 0xFF392B35.toInt()),
        "city" to intArrayOf(0xFF566579.toInt(), 0xFFAFBDC9.toInt(), 0xFF323F50.toInt()),
        "night" to intArrayOf(0xFF51445F.toInt(), 0xFFA58CAD.toInt(), 0xFF302A46.toInt()),
        "mountain" to intArrayOf(0xFF64778A.toInt(), 0xFFBFCED1.toInt(), 0xFF3F4E62.toInt()),
        "ruins" to intArrayOf(0xFF88775B.toInt(), 0xFFD6C08C.toInt(), 0xFF5B5140.toInt()),
        "dojo" to intArrayOf(0xFF866446.toInt(), 0xFFD7B381.toInt(), 0xFF5A4838.toInt()),
        "marsh" to intArrayOf(0xFF5F6555.toInt(), 0xFFB6BF92.toInt(), 0xFF3B3D3D.toInt()),
        "crystal" to intArrayOf(0xFF655581.toInt(), 0xFFBCA3CE.toInt(), 0xFF3E3659.toInt()),
        "cosmic" to intArrayOf(0xFF484D78.toInt(), 0xFFA4B1D0.toInt(), 0xFF292D50.toInt()),
    )
    internal val groups = mapOf(
        "jungle" to "jungle",
        "forest" to "jungle",
        "meadow" to "jungle",
        "flower-field" to "jungle",
        "route" to "jungle",
        "forest-tech-b" to "jungle",
        "autumn-forest" to "jungle",
        "countryside-route" to "jungle",
        "beach" to "beach",
        "coral-shallows" to "beach",
        "river" to "beach",
        "sunset-beach" to "beach",
        "deep-sea-cave" to "beach",
        "earthycave" to "cave",
        "dampcave" to "cave",
        "mine" to "cave",
        "icecave" to "snow",
        "snow-aurora" to "snow",
        "ice-lake" to "snow",
        "snowy-pines" to "snow",
        "desert" to "desert",
        "canyon" to "desert",
        "desert-oasis" to "desert",
        "fire-savanna" to "fire",
        "lava-field" to "fire",
        "city" to "city",
        "factory" to "city",
        "power-plant" to "city",
        "night-forest" to "night",
        "dark-forest" to "night",
        "haunted-mansion" to "night",
        "dark-town" to "night",
        "night-rooftops" to "night",
        "old-graveyard" to "night",
        "mountain" to "mountain",
        "dragon-peak" to "mountain",
        "cliff-clouds" to "mountain",
        "windy-hilltop" to "mountain",
        "ruins-courtyard" to "ruins",
        "legendary-shrine" to "ruins",
        "fighting-training" to "dojo",
        "bamboo-dojo" to "dojo",
        "poison-marsh" to "marsh",
        "toxic-cave" to "marsh",
        "crystal-storm" to "crystal",
        "starry-plateau" to "cosmic",
        "starry-plateau-galaxy" to "cosmic",
    )
    fun colors(scene: String?, dark: Boolean): IntArray {
        val key = when (scene) {
            "forest", "forest-tech-b" -> "forest"
            "coral-shallows", "dampcave" -> scene
            else -> groups[scene] ?: "jungle"
        }
        val colors = when (key) {
            "forest" -> intArrayOf(0xFF3B3D47.toInt(), 0xFF9C777B.toInt(), 0xFF1C2936.toInt())
            "coral-shallows" -> intArrayOf(0xFFB39A6D.toInt(), 0xFFEFD7A1.toInt(), 0xFF816F55.toInt())
            "dampcave" -> intArrayOf(0xFF40565B.toInt(), 0xFF91A4A1.toInt(), 0xFF263B40.toInt())
            else -> palettes.getValue(key)
        }
        return if (!dark) colors else colors.map { c ->
            (0xFF shl 24) or (Math.round((c shr 16 and 255) * .84f) shl 16) or
                (Math.round((c shr 8 and 255) * .84f) shl 8) or Math.round((c and 255) * .84f)
        }.toIntArray()
    }
}
