package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Round 8 stage rule, part 2 (pure maths, virtual dp like [ArenaGeo]): the Pokémon stand on the [Stage]'s feet, the two stat cards
 * go where they cover neither Pokémon. The wild card sits in the top row (beside the wild one, top-left, between the two, top-right),
 * ours in the bottom row (beside ours, bottom-right, between, bottom-left). Search order: sprite scale 1, .9 .. .6, and for each the
 * card scale from the caller's k0 down to 1; the clear pair with the best preference wins. From sprite scale [TOP_FROM] on, when the
 * usual rows have no clear pair, "both cards on top" is tried before the sprites shrink further: the wild card in its top row as
 * ever, ours in the top row too (top-right, top-middle). A card never has to clear more than the Pokémon's visible picture: the
 * caller can say how much of its frame that is ([ArtShare]; the default is [ArenaGeo.Box.art]'s 85 %). Nothing clear: sprites at .7,
 * cards at 1, the pair with the least weighted overlap ([StagePlan.clear] false): a card on the wild Pokémon counts 3x, on ours 1x,
 * the cards on each other 3x, card area outside the panel 4x.
 */
internal object StageGeo {
    private const val EDGE = 8f
    private const val BOTTOM = 10f
    private const val GAP = 6f
    private val SCALES = floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)
    private const val FALLBACK_SCALE = 0.7f

    /** From this sprite scale down, "both cards on top" is tried too. */
    private const val TOP_FROM = 0.8f

    /** Weights of the fallback's overlap score. */
    private const val W_WILD = 3f
    private const val W_OURS = 1f
    private const val W_CARDS = 3f
    private const val W_OUTSIDE = 4f

    /** Wild positions: BW beside the wild one, TL, TM between the two, TR. */
    private const val BW = 0
    private const val TL = 1
    private const val TM = 2
    private const val TR = 3

    /** Player positions: BP beside ours, BR, BM between the two, BL (bottom row); PTR, PTM top-right and top-middle ("both on top"). */
    private const val BP = 0
    private const val BR = 1
    private const val BM = 2
    private const val BL = 3
    private const val PTR = 4
    private const val PTM = 5

    private val WILD_OWN = intArrayOf(BW, TL, TM, TR)
    private val WILD_CORNERS = intArrayOf(TL, BW, TM, TR)
    private val PLAYER_OWN = intArrayOf(BP, BR, BM, BL)
    private val PLAYER_CORNERS = intArrayOf(BR, BP, BM, BL)
    private val PLAYER_TOP = intArrayOf(PTR, PTM)

    /** A pair of cards and whether ours is in the top row. */
    private class Pick(val wild: ArenaGeo.Box, val player: ArenaGeo.Box, val top: Boolean)

    /**
     * [wildArt] / [playerArt]: how much of its square frame (the argument = the frame's side, dp) each Pokémon's picture takes;
     * null = [ArtShare.DEFAULT]. The frame's side changes with the sprite scale, and a sprite's drawn size can change in steps with it.
     */
    fun plan(
        w: Float, h: Float, stage: Stage,
        wildCardW: Float, wildCardH: Float, playerCardW: Float, playerCardH: Float,
        k0: Float, own: Boolean,
        wildArt: ((Float) -> ArtShare?)? = null, playerArt: ((Float) -> ArtShare?)? = null,
    ): StagePlan {
        val wildOrder = if (own) WILD_OWN else WILD_CORNERS
        val playerOrder = if (own) PLAYER_OWN else PLAYER_CORNERS
        for (f in SCALES) {
            val ws = sprite(stage.wildFeetX, stage.wildFeetY, stage.wildFrame * f)
            val ps = sprite(stage.playerFeetX, stage.playerFeetY, stage.playerFrame * f)
            val wa = art(ws, wildArt)
            val pa = art(ps, playerArt)
            for (top in if (f <= TOP_FROM + 0.001f) booleanArrayOf(false, true) else booleanArrayOf(false)) {
                var k = max(1f, k0)
                while (true) {
                    val pick = best(
                        w, h, wa, pa, wildCardW * k, wildCardH * k, playerCardW * k, playerCardH * k,
                        wildOrder, if (top) PLAYER_TOP else playerOrder, onlyClear = true,
                    )
                    if (pick != null) return StagePlan(ws, ps, pick.wild, pick.player, k, f, true, pick.top)
                    if (k <= 1f) break
                    k = max(1f, k - 0.05f)
                }
            }
        }
        val ws = sprite(stage.wildFeetX, stage.wildFeetY, stage.wildFrame * FALLBACK_SCALE)
        val ps = sprite(stage.playerFeetX, stage.playerFeetY, stage.playerFrame * FALLBACK_SCALE)
        val wa = art(ws, wildArt)
        val pa = art(ps, playerArt)
        val rows = playerOrder + PLAYER_TOP
        val pick = best(w, h, wa, pa, wildCardW, wildCardH, playerCardW, playerCardH, wildOrder, rows, onlyClear = false)!!
        return StagePlan(ws, ps, pick.wild, pick.player, 1f, FALLBACK_SCALE, false, pick.top)
    }

    /** The square sprite holder: [side] wide and high, centred on the feet, bottom = the feet. */
    private fun sprite(feetX: Float, feetY: Float, side: Float) = ArenaGeo.Box(feetX - side / 2f, feetY - side, side, side)

    /** The Pokémon's visible picture in its holder: bottom-centred, [art] of the frame (null = the default share). */
    private fun art(b: ArenaGeo.Box, art: ((Float) -> ArtShare?)?): ArenaGeo.Box {
        val a = art?.invoke(b.w) ?: ArtShare.DEFAULT
        return ArenaGeo.Box(b.x + b.w * (1f - a.w) / 2f, b.y + b.h * (1f - a.h), b.w * a.w, b.h * a.h)
    }

    private fun cx(x: Float, cw: Float, w: Float) = max(EDGE, min(x, w - EDGE - cw))

    /**
     * The best pair of cards for these pictures ([wa] wild, [pa] ours): with [onlyClear] the clear pair with the lowest summed
     * preference index (null when none); else the pair with the least weighted overlap (then the best preference; a pair with ours
     * in the top row only wins a tie against none), always one. [playerRows] holds the player positions to try.
     */
    private fun best(
        w: Float, h: Float, wa: ArenaGeo.Box, pa: ArenaGeo.Box, wcw: Float, wch: Float, pcw: Float, pch: Float,
        wildOrder: IntArray, playerRows: IntArray, onlyClear: Boolean,
    ): Pick? {
        val between = (pa.right + wa.x) / 2f
        var bestPick: Pick? = null
        var bestScore = Float.MAX_VALUE
        var bestPref = Int.MAX_VALUE
        for ((wi, wp) in wildOrder.withIndex()) {
            val x = when (wp) {
                BW -> wa.x - GAP - wcw
                TL -> EDGE
                TM -> between - wcw / 2f
                else -> w - EDGE - wcw
            }
            val wild = ArenaGeo.Box(cx(x, wcw, w), EDGE, wcw, wch)
            for ((pi, pp) in playerRows.withIndex()) {
                val top = pp >= PTR
                val px = when (pp) {
                    BP -> pa.right + GAP
                    BR -> w - EDGE - pcw
                    BM -> between - pcw / 2f
                    BL -> EDGE
                    PTR -> w - EDGE - pcw
                    else -> between - pcw / 2f
                }
                val player = ArenaGeo.Box(cx(px, pcw, w), if (top) EDGE else h - BOTTOM - pch, pcw, pch)
                val pref = wi + pi + if (top) 8 else 0
                if (onlyClear) {
                    if (clear(w, h, wild, player, wa, pa, top) && pref < bestPref) {
                        bestPref = pref
                        bestPick = Pick(wild, player, top)
                    }
                } else {
                    val score = overlap(w, h, wild, player, wa, pa)
                    if (score < bestScore - 0.001f || (score <= bestScore + 0.001f && pref < bestPref)) {
                        bestScore = score
                        bestPref = pref
                        bestPick = Pick(wild, player, top)
                    }
                }
            }
        }
        return bestPick
    }

    private fun inside(w: Float, h: Float, b: ArenaGeo.Box) = b.x >= -0.01f && b.y >= -0.01f && b.right <= w + 0.01f && b.bottom <= h + 0.01f

    /**
     * Both cards inside the panel (the wild card with 4 dp of slack at the bottom, like [ArenaGeo]), neither on a sprite's picture nor
     * on the other card; two cards in the same row ([sameRow]) also keep a [GAP] between them.
     */
    private fun clear(w: Float, h: Float, wild: ArenaGeo.Box, player: ArenaGeo.Box, wa: ArenaGeo.Box, pa: ArenaGeo.Box, sameRow: Boolean): Boolean {
        val gap = if (sameRow) GAP else 0f
        val apart = !wild.hits(ArenaGeo.Box(player.x - gap, player.y, player.w + 2f * gap, player.h))
        return inside(w, h - 4f, wild) && inside(w, h, player) && !wild.hits(wa) && !wild.hits(pa) && !player.hits(wa) && !player.hits(pa) && apart
    }

    /**
     * Weighted overlap of the cards with both pictures and each other, plus the part of a card outside the panel: the wild Pokémon
     * covered counts [W_WILD] x, ours [W_OURS] x, the cards on each other [W_CARDS] x, card area off the panel [W_OUTSIDE] x.
     */
    private fun overlap(w: Float, h: Float, wild: ArenaGeo.Box, player: ArenaGeo.Box, wa: ArenaGeo.Box, pa: ArenaGeo.Box) =
        W_WILD * (area(wild, wa) + area(player, wa)) + W_OURS * (area(wild, pa) + area(player, pa)) +
            W_CARDS * area(wild, player) + W_OUTSIDE * (outside(w, h, wild) + outside(w, h, player))

    private fun area(a: ArenaGeo.Box, b: ArenaGeo.Box): Float {
        val dx = min(a.right, b.right) - max(a.x, b.x)
        val dy = min(a.bottom, b.bottom) - max(a.y, b.y)
        return if (dx > 0f && dy > 0f) dx * dy else 0f
    }

    private fun outside(w: Float, h: Float, b: ArenaGeo.Box): Float {
        val dx = min(b.right, w) - max(b.x, 0f)
        val dy = min(b.bottom, h) - max(b.y, 0f)
        val inner = if (dx > 0f && dy > 0f) dx * dy else 0f
        return b.w * b.h - inner
    }
}
