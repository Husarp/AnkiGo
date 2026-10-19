package com.husarp.ankigo.ui

import kotlin.math.abs

/**
 * Round 10 B20 "Pokémon size" (pure maths, no Android): one global setting, 80 % to 130 % in steps of 5 (default 100), that scales BOTH
 * battle sprites in every battle host. It is not per species. 100 % is exactly the layout the rules gave before the setting existed
 * (every function here returns its input unchanged at 1).
 *
 * Where it applies:
 * - with a battle picture: [StageGeo.plan] runs the info boxes' rule with the size applied to the sprite frames (boxes shrink first,
 *   then the frames step down); a frame bigger than today's must keep its real art inside the arena ([StageGeo.PokemonSizeRule]).
 * - without one: [fitHolders] scales the two sprite holders around their feet (bottom-centre) after the plain layout was made and keeps
 *   the growth to the room it has (the art stays inside the arena and clear of both cards).
 */
internal object PokemonSize {
    /** The SharedPreferences key ("ankigo_ui") of [AppPrefs.pokemonSize]; the battle panel watches it. */
    const val KEY = "pokemonSize"

    const val MIN = 80
    const val MAX = 130
    const val STEP = 5
    const val DEFAULT = 100

    /** The slider's values: 80, 85 .. 130. */
    val VALUES: List<Int> = (MIN..MAX step STEP).toList()

    /** The size to use for a stored value: nothing stored or outside 80..130 = 100; in range it is rounded to the nearest step. */
    fun parse(stored: Int?): Int {
        if (stored == null || stored < MIN || stored > MAX) return DEFAULT
        return MIN + Math.round((stored - MIN) / STEP.toFloat()) * STEP
    }

    /** The slider position (0-based) of [pct]. */
    fun index(pct: Int): Int = (parse(pct) - MIN) / STEP

    /** The size at slider position [index] (kept inside the slider). */
    fun at(index: Int): Int = VALUES[index.coerceIn(0, VALUES.size - 1)]

    /** [pct] as a multiplier: 1 at 100. */
    fun factor(pct: Int): Float = parse(pct) / 100f

    fun label(pct: Int): String = "${parse(pct)}%"

    // ---------------------------------------------------------------- the plain layouts

    /** [b] drawn [f] times as big around its feet (bottom-centre stays), then moved [dx] sideways. */
    fun atFeet(b: ArenaGeo.Box, f: Float, dx: Float = 0f): ArenaGeo.Box =
        ArenaGeo.Box(b.x + b.w / 2f - b.w * f / 2f + dx, b.bottom - b.h * f, b.w * f, b.h * f)

    /** What [fitHolders] decided: the two holders, the factor really used (1 .. the wished size, or the wished size under 1) and each one's sideways move. */
    class Sized(val wild: ArenaGeo.Box, val player: ArenaGeo.Box, val f: Float, val wildDx: Float, val playerDx: Float) {
        val changed: Boolean get() = abs(f - 1f) > 0.0001f
    }

    private const val EPS = 0.01f

    /**
     * The two sprite holders ([wild], [player]; any unit, an arena of [w] x [h]) at the wished [size]. At 1 nothing changes. Under 1
     * they simply get smaller around their feet. Over 1 the largest step from [size] down to 1 is used where, after moving each one
     * sideways just far enough to bring its art box (85 %, bottom-centred, as [ArenaGeo.Box.art]) into the arena, both art boxes lie
     * inside the arena, neither touches an [obstacles] box (the info cards) and they do not touch each other unless they already did at
     * 1; none fits = 1.
     */
    fun fitHolders(w: Float, h: Float, wild: ArenaGeo.Box, player: ArenaGeo.Box, obstacles: List<ArenaGeo.Box>, size: Float): Sized {
        if (abs(size - 1f) < 0.0001f) return Sized(wild, player, 1f, 0f, 0f)
        if (size < 1f) return Sized(atFeet(wild, size), atFeet(player, size), size, 0f, 0f)
        val touching = wild.art().hits(player.art())
        var pct = Math.round(size * 100f)
        while (pct > 100) {
            val f = pct / 100f
            val a = atFeet(wild, f)
            val b = atFeet(player, f)
            val dxa = nudge(a.art(), w)
            val dxb = nudge(b.art(), w)
            if (dxa != null && dxb != null) {
                val wa = atFeet(wild, f, dxa)
                val pa = atFeet(player, f, dxb)
                if (clear(w, h, wa, pa, obstacles, touching)) return Sized(wa, pa, f, dxa, dxb)
            }
            pct -= STEP
        }
        return Sized(wild, player, 1f, 0f, 0f)
    }

    /** The sideways move that brings [art] inside 0..[w]; null when it is wider than the arena. */
    private fun nudge(art: ArenaGeo.Box, w: Float): Float? = when {
        art.w > w + EPS -> null
        art.x < 0f -> -art.x
        art.right > w -> w - art.right
        else -> 0f
    }

    private fun clear(w: Float, h: Float, wild: ArenaGeo.Box, player: ArenaGeo.Box, obstacles: List<ArenaGeo.Box>, touching: Boolean): Boolean {
        val wa = wild.art()
        val pa = player.art()
        for (art in listOf(wa, pa)) {
            if (art.x < -EPS || art.y < -EPS || art.right > w + EPS || art.bottom > h + EPS) return false
            for (o in obstacles) if (o.hits(art)) return false
        }
        return touching || !wa.hits(pa)
    }
}
