package com.husarp.ankigo.ui

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PLAN 215: how big the Pokémon are in the Pokédex and Box grids. Five named steps; step 0 is exactly the pre-0.21.0 size
 * ([GridMath.tileScale] of the whole 96 x 96 picture, with all its empty border), the others zoom it up. The zoom never lets the
 * Pokémon's own (opaque) picture leave the room it has, so a big Pokémon stops growing instead of being cut or covering the tile's marks.
 * Pure (no Android); [TileSprite] applies it.
 */
internal object ListSpriteSize {
    val NAMES = listOf("Smallest", "Small", "Medium", "Large", "Largest")

    /** The zoom on step 0's size, per step. */
    val ZOOM = floatArrayOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    const val DEFAULT = 2 // Medium
    val LAST get() = NAMES.lastIndex

    /** A stored step; anything outside 0..4 is the default. */
    fun parse(stored: Int): Int = if (stored in NAMES.indices) stored else DEFAULT

    fun name(step: Int): String = NAMES[parse(step)]

    /** A picture within this share of a whole number is drawn at that whole number (crisp pixels). */
    private const val SNAP = 0.06f

    /**
     * The scale a picture of [canvasW] x [canvasH] px (its opaque part is [contentW] x [contentH]) is drawn at in a room of [availW] x [availH] px.
     * Step 0 is [GridMath.tileScale] exactly. Above it: base x zoom, at most what makes the opaque part fit the room, and a whole number
     * when one is within 6 % of it and still fits.
     */
    fun scale(availW: Int, availH: Int, canvasW: Int, canvasH: Int, contentW: Int, contentH: Int, step: Int): Float {
        val base = GridMath.tileScale(availW, availH, canvasW, canvasH)
        val s = parse(step)
        if (s == 0 || contentW <= 0 || contentH <= 0 || availW <= 0 || availH <= 0) return base
        val fitContent = min(availW.toFloat() / contentW, availH.toFloat() / contentH)
        val t = min(base * ZOOM[s], fitContent)
        val n = t.roundToInt()
        return if (n >= 1 && n <= fitContent && abs(n - t) <= SNAP * t) n.toFloat() else t
    }
}
