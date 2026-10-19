package com.husarp.ankigo.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Round 9 D1 "the info boxes' automatic rule" (pure maths, virtual dp like [ArenaGeo]; a port of the mockup's `autoRule` in
 * docs/design/boxes-layout.html, whose JS is the spec). While a battle picture shows, the two Pokémon stand on the [Stage]'s feet and
 * the two info boxes (the stat cards) follow ONE rule, in every host, with no setting:
 *
 * 1. Box size comes from the panel's on-screen height first ([boxScales]): full size from [H_FULL] dp, linearly down to
 *    [SHARE_MIN] at [H_SMALL] dp, so a small panel gets small boxes from the start, not only after a collision. The card scale k can
 *    be under 1.
 * 2. Vertical: on the OUTER side of the box's own Pokémon, touching the panel's edge (tall panels: ours at the bottom, the wild one
 *    at the top; wide ones: ours at the top, the wild one at the bottom). Horizontal: centred on its Pokémon's feet, kept inside the
 *    panel (near an edge it becomes edge-aligned).
 * 3. A box keeps a gap ([gap]) from both Pokémon's real art boxes and ours from the wild box. If that fails, in steps: (a) right
 *    next to its own Pokémon (just above / under its art box), boxes down to [SHARE_NEAR]; (b) the edge-aligned spots; (c) shrink on
 *    to [SHARE_MIN]; (d) the other vertical side. Boxes always shrink before the Pokémon: only when all of that fails do the sprites
 *    get smaller (frames x .9 .. .6 around their feet) and the whole search runs again.
 * 4. Nothing is clear anywhere: the pair with the least cover (then the least nearness) at the smallest box, [StagePlan.clear] false.
 *
 * With a battle frame ([inset] > 0) the "panel" for the boxes is the arena inset by that much on every side.
 *
 * Round 9 wave 3 (D12), on top of the mockup's rule: **no text in a box is drawn under [PanelFit.MIN_SP] on screen** (its size x the
 * panel's scale x the box scale). So the box scale has a floor [textScale] worked out from the smallest text a box draws, and
 * [SHARE_MIN] only applies above it. The smallest texts are the optional parts (the wild type chips, the XP number): below the
 * scale where those are readable the box is "reduced" (chips -> type symbols, no XP number: [StagePlan.reduced], its own, shorter
 * heights) and its floor is the next smallest text. Boxes still shrink before the Pokémon do; with the floor, a roomy box that no
 * longer fits makes the sprites smaller (frames x .9 .. .6) rather than the text.
 *
 * Round 10 B20 ("Pokémon size", [PokemonSize]): the player's size multiplies the frame scales ([frameScales]); 100 % is the rule above,
 * unchanged. A size over 100 % may only use a step while both sprites' real art boxes stay inside the arena (inset by the battle frame)
 * and do not touch each other (the last, smallest step always runs).
 */
internal object StageGeo {
    /** The sprite frames' scales, tried in this order; the boxes shrink before the next one is used. */
    private val SCALES = floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)

    /**
     * Round 10 B20: the frame scales to try for the Pokémon size [size]. The arena draws both sprites [size] times what a frame 1 / [size]
     * as big would ([com.husarp.ankigo.gfx.SpriteView.sizeFactor]), so the rule's own steps are those smaller frames: `size x` each step.
     * - 1: today's [SCALES] (the same array).
     * - over 1: `size x` the usual steps, continued (.5, .4 ..) until the last is no bigger than the usual worst case (.6), so a big
     *   size is brought back down by the rule's own steps as far as 100 % could go.
     * - under 1: `size x atOne x` the usual steps; [atOne] = the frame scale the same panel gets at 100 %, so a smaller size is never
     *   bigger than 100 %.
     */
    internal fun frameScales(size: Float, atOne: Float = 1f): FloatArray {
        if (abs(size - 1f) < F_EPS) return SCALES
        if (size < 1f) return FloatArray(SCALES.size) { SCALES[it] * atOne * size }
        val steps = ArrayList<Float>(SCALES.toList())
        while (steps.last() * size > SCALES.last() + 0.001f) steps += steps.last() - 0.1f
        return FloatArray(steps.size) { steps[it] * size }
    }

    /**
     * Round 10 B20: whether the plan [sized] made for the Pokémon size [size] is to be used, or [one] (the plan at 100 %, sprites drawn
     * as before). A smaller size always is. A bigger one only if its rule step (`spriteScale / size`, the frame it draws the sprites like)
     * is not lower than the 100 % plan's, and it is clear whenever the 100 % plan is: so a bigger size is never a smaller Pokémon, and
     * never leaves a box over one that 100 % kept clear.
     */
    internal fun keepSized(size: Float, one: StagePlan, sized: StagePlan): Boolean =
        size <= 1f + F_EPS || (sized.spriteScale / size >= one.spriteScale - 0.001f && !(one.clear && !sized.clear))

    /** On-screen height (dp) of the arena from which the boxes are full size, and the one where they are smallest. */
    const val H_FULL = 260f
    const val H_SMALL = 130f

    /** The smallest box (share of its design size on screen), and the one a box may shrink to while it still tries to sit right by its Pokémon. */
    const val SHARE_MIN = 0.55f
    const val SHARE_NEAR = 0.85f

    private const val K_STEP = 0.05f

    /** A scale this close under [BoxScales.full] still counts as full (the steps and the threshold are floats). */
    private const val K_EPS = 0.0001f

    /** Same tolerance as [ArenaGeo.Box.hits]. */
    private const val EPS = 0.01f

    /** A size this close to 1 is 1. */
    private const val F_EPS = 0.0001f

    private const val OURS = 0
    private const val WILD = 1

    /** A spot's anchor: centred on its Pokémon's feet, right next to the Pokémon (just above / under its art box), the left edge, the right edge. */
    private const val CENTRE = 0
    private const val NEXT = 1
    private const val LEFT = 2
    private const val RIGHT = 3

    /** The loosest spot level a phase may use: 0 outer side centred, 1 next to its Pokémon, 2 edge-aligned, 3 anything (the other side too). */
    private val PHASE_LEVEL = intArrayOf(0, 1, 2, 2, 3)

    /** What a phase's k runs between: 0 = kStart, 1 = kNear, 2 = kMin. */
    private val PHASE_FROM = intArrayOf(0, 0, 0, 1, 0)
    private val PHASE_TO = intArrayOf(1, 1, 1, 2, 2)

    /**
     * The box scales of a panel: where the boxes start, the smallest they get while sitting right by their Pokémon, and the smallest
     * of all; [full] = the scale from which the optional parts (chips, XP number) are readable, under it a box is reduced (0 = no text floor).
     */
    class BoxScales(val start: Float, val near: Float, val min: Float, val full: Float = 0f)

    /**
     * The box scale at which a text of design size [sp] is drawn [PanelFit.MIN_SP] on screen: the text is raised to the panel's
     * floor [floorSp] first and then drawn at [panelScale] x the box scale. 0 for no text ([sp] <= 0); never above 1 (a text that is
     * under the floor at scale 1 can only be raised, which the floor already did).
     */
    fun textScale(sp: Float, panelScale: Float, floorSp: Float): Float =
        if (sp <= 0f) 0f else min(1f, PanelFit.MIN_SP / (max(sp, floorSp) * max(panelScale, 0.01f)))

    /** The share of its design size on screen a box should have in a panel [onScreenH] dp high: [SHARE_MIN] up to full size. */
    fun targetShare(onScreenH: Float): Float =
        (SHARE_MIN + (onScreenH - H_SMALL) / H_SMALL * (1f - SHARE_MIN)).coerceIn(SHARE_MIN, 1f)

    /**
     * The card scales for an arena [onScreenH] dp high on screen drawn at [panelScale] with text floor [floorSp]: [k0] is the scale a
     * roomy panel draws its boxes at (1 in the study layouts). The panel's scale and the raised text already changed the cards by
     * `panelScale * max(1, floorSp / 12)`, so a card only needs the rest of the way to the wanted on-screen share.
     * [minSp]: the smallest text (sp) a reduced box draws, [fullSp] the smallest of a full one (default: the same); both 0 = no
     * text floor. No scale goes under [textScale] of [minSp], so no text is drawn under the readable size.
     */
    fun boxScales(onScreenH: Float, panelScale: Float, floorSp: Float, k0: Float, minSp: Float = 0f, fullSp: Float = minSp): BoxScales {
        val already = max(panelScale, 0.01f) * max(1f, floorSp / 12f)
        fun kv(screen: Float) = k0 * min(1f, screen / already)
        val readable = textScale(minSp, panelScale, floorSp)
        val start = max(kv(targetShare(onScreenH)), readable)
        return BoxScales(start, max(min(start, kv(SHARE_NEAR)), readable), max(min(start, kv(SHARE_MIN)), readable), max(readable, textScale(fullSp, panelScale, floorSp)))
    }

    /** The gap (virtual dp) a box keeps from the art boxes and the other box: 8 dp on screen, in small panels down to 4 dp. */
    fun gap(onScreenH: Float, panelScale: Float): Float = max(4f, 8f * min(1f, onScreenH / H_FULL)) / max(panelScale, 0.01f)

    /** True for a tall arena: ours sits at the bottom and the wild one at the top; else ours at the top and the wild one at the bottom. */
    fun isTall(w: Float, h: Float): Boolean = h > w * 1.1f

    /**
     * Round 10 W7 (the HUD look) for the layouts that are not made from a picture's stage (the plain arena, the study layouts): the two
     * boxes in opposite corners of a [w] x [h] arena, [inset] in from its edges. [ours] and [wild] give the sizes (their positions are
     * ignored). Ours is on the left edge, the wild one on the right edge; the primary sides are [plan]'s (tall: ours at the bottom, the
     * wild one at the top; wide: ours at the top, the wild one at the bottom). Returns (wild, ours).
     */
    fun cornerBoxes(w: Float, h: Float, wild: ArenaGeo.Box, ours: ArenaGeo.Box, inset: Float = 0f): Pair<ArenaGeo.Box, ArenaGeo.Box> {
        val i = max(0f, inset)
        val oursTop = !isTall(w, h)
        fun put(b: ArenaGeo.Box, left: Boolean, top: Boolean): ArenaGeo.Box {
            val x = if (left) i else w - i - b.w
            val y = if (top) i else h - i - b.h
            return ArenaGeo.Box(max(i, min(w - i - b.w, x)), max(i, min(h - i - b.h, y)), b.w, b.h)
        }
        return put(wild, left = false, top = !oursTop) to put(ours, left = true, top = oursTop)
    }

    /**
     * [w] x [h]: the arena in virtual dp; [panelScale]: the panel's draw scale, so the arena is `h * panelScale` dp high on screen;
     * [floorSp]: the text floor; [inset]: how far a battle frame reaches in (0 = none). The cards' design sizes at k = 1 are
     * [wildCardW] x [wildCardH] and [playerCardW] x [playerCardH]; [k0] the roomy scale. [wildArt] / [playerArt]: each Pokémon's real
     * art box in its square frame (the argument = the frame's side, dp; a sprite's drawn size can change in steps with it); null =
     * [ArtShare.DEFAULT]. The heights are the full boxes'; [wildCardHReduced] / [playerCardHReduced] the same boxes with their optional
     * parts dropped (used under the scale [BoxScales.full], where [StagePlan.reduced] is true); [minSp] / [fullSp] the smallest text
     * of a reduced / full box in design sp (both 0 = no readability floor). [sizeScale]: the Pokémon size (Round 10 B20, 1 = today; [frameScales]);
     * [atOne]: for a size under 1, the frame scale the same panel gets at 1 (0 = worked out here).
     */
    fun plan(
        w: Float, h: Float, stage: Stage,
        wildCardW: Float, wildCardH: Float, playerCardW: Float, playerCardH: Float,
        k0: Float,
        panelScale: Float = 1f, floorSp: Float = 0f, inset: Float = 0f,
        wildArt: ((Float) -> ArtShare?)? = null, playerArt: ((Float) -> ArtShare?)? = null,
        wildCardHReduced: Float = wildCardH, playerCardHReduced: Float = playerCardH,
        minSp: Float = 0f, fullSp: Float = minSp,
        sizeScale: Float = 1f, atOne: Float = 0f,
        corners: Boolean = false,
    ): StagePlan {
        val onScreen = h * panelScale
        val scales = boxScales(onScreen, panelScale, floorSp, k0, minSp, fullSp)
        val run = Run(w, h, max(0f, inset), gap(onScreen, panelScale), isTall(w, h), stage,
            floatArrayOf(playerCardW, wildCardW), floatArrayOf(playerCardH, wildCardH), floatArrayOf(playerCardHReduced, wildCardHReduced),
            scales.full, wildArt, playerArt)
        // a size under 100 % is that share of the frame the same panel gets at 100 % (given, or worked out first)
        val one = if (sizeScale < 1f - F_EPS && !(atOne > 0f)) plan(w, h, stage, wildCardW, wildCardH, playerCardW, playerCardH, k0, panelScale, floorSp, inset,
            wildArt, playerArt, wildCardHReduced, playerCardHReduced, minSp, fullSp, 1f, 0f, corners).spriteScale else if (atOne > 0f) atOne else 1f
        val chain = frameScales(sizeScale, one)
        val big = sizeScale > 1f + F_EPS
        for ((i, f) in chain.withIndex()) {
            run.setFrames(f)
            if (big && i < chain.lastIndex && !run.bigFramesOk()) continue   // a bigger Pokémon must keep its real art inside the arena and off the other one (the last step always runs)
            if (corners) {   // Round 10 W7 (the HUD look): the two corners at the start scale, no search; the Pokémon keep their frame
                run.keepCorners(scales.start)
                return run.result(scales.start, f, true)
            }
            for (ph in PHASE_LEVEL.indices) {
                val hi = scales.at(PHASE_FROM[ph])
                val lo = scales.at(PHASE_TO[ph])
                var k = hi
                while (k > lo + 0.001f) {   // hi down in 0.05 steps, then lo itself
                    if (run.findOk(k, PHASE_LEVEL[ph])) return run.result(k, f, true)
                    k -= K_STEP
                }
                if (run.findOk(lo, PHASE_LEVEL[ph])) return run.result(lo, f, true)
            }
        }
        // nothing is clear at any sprite scale: the least-bad pair at the smallest box
        var bestScore = Double.MAX_VALUE
        var bestF = chain[0]
        for ((i, f) in chain.withIndex()) {
            run.setFrames(f)
            if (big && i < chain.lastIndex && !run.bigFramesOk()) continue
            val score = run.leastBad(scales.min)
            if (score < bestScore) {
                bestScore = score
                bestF = f
                run.keepLeastBad()
            }
        }
        run.setFrames(bestF)
        return run.result(scales.min, bestF, false, keep = true)
    }

    private fun BoxScales.at(i: Int) = when (i) { 0 -> start; 1 -> near; else -> min }

    /** One call of [plan]: the arena, the two kinds' spots, and the scratch boxes (no allocation while searching). */
    private class Run(
        val vw: Float, val vh: Float, val inset: Float, val g: Float, tall: Boolean, val stage: Stage,
        val baseW: FloatArray, val baseH: FloatArray, val reducedH: FloatArray, val kFull: Float,
        val wildArtOf: ((Float) -> ArtShare?)?, val playerArtOf: ((Float) -> ArtShare?)?,
    ) {
        // spot tables per kind: index 0-3 on the primary side (centre, next, left, right), 4-7 on the other
        private val spotTop = arrayOf(BooleanArray(8), BooleanArray(8))
        private val spotAnchor = intArrayOf(CENTRE, NEXT, LEFT, RIGHT, CENTRE, NEXT, LEFT, RIGHT)
        private val spotLevel = intArrayOf(0, 1, 2, 2, 3, 3, 3, 3)
        private val feetX = floatArrayOf(stage.playerFeetX, stage.wildFeetX)

        // the frames and art boxes at the current sprite scale: [x, y, right, bottom] (art), Box (frame)
        private val art = arrayOf(FloatArray(4), FloatArray(4))
        private val frame = arrayOfNulls<ArenaGeo.Box>(2)
        private val artBox = arrayOfNulls<ArenaGeo.Box>(2)

        // scratch: the two placed boxes [x, y, w, h]
        private val pOurs = FloatArray(4)
        private val pWild = FloatArray(4)

        // the chosen pair
        private val keepOurs = FloatArray(4)
        private val keepWild = FloatArray(4)
        private var keepLevel = 0

        init {
            val oursTop = !tall
            for (i in 0 until 8) {
                spotTop[OURS][i] = if (i < 4) oursTop else !oursTop
                spotTop[WILD][i] = if (i < 4) !oursTop else oursTop
            }
        }

        fun setFrames(f: Float) {
            setKind(OURS, stage.playerFeetX, stage.playerFeetY, stage.playerFrame * f, playerArtOf)
            setKind(WILD, stage.wildFeetX, stage.wildFeetY, stage.wildFrame * f, wildArtOf)
        }

        private fun setKind(kind: Int, fx: Float, fy: Float, side: Float, of: ((Float) -> ArtShare?)?) {
            val a = (of?.invoke(side) ?: ArtShare.DEFAULT).inFrame(fx - side / 2f, fy - side, side)
            frame[kind] = ArenaGeo.Box(fx - side / 2f, fy - side, side, side)
            artBox[kind] = a
            art[kind][0] = a.x; art[kind][1] = a.y; art[kind][2] = a.right; art[kind][3] = a.bottom
        }

        /**
         * Round 10 B20: a Pokémon size over 100 % is allowed a step while both real art boxes lie inside the arena (inset by the battle
         * frame) and do not touch each other.
         */
        fun bigFramesOk(): Boolean {
            for (kind in 0..1) {
                val a = art[kind]
                if (a[0] < inset - EPS || a[1] < inset - EPS || a[2] > vw - inset + EPS || a[3] > vh - inset + EPS) return false
            }
            val a = art[OURS]
            val b = art[WILD]
            return !(a[0] < b[2] - EPS && b[0] < a[2] - EPS && a[1] < b[3] - EPS && b[1] < a[3] - EPS)
        }

        /** Puts spot [i] of [kind] at box scale [k] into [out] (x, y, w, h). */
        private fun place(kind: Int, i: Int, k: Float, out: FloatArray) {
            val bw = baseW[kind] * k
            val bh = (if (k < kFull - K_EPS) reducedH[kind] else baseH[kind]) * k
            val top = spotTop[kind][i]
            val x = when (spotAnchor[i]) {
                LEFT -> inset
                RIGHT -> vw - inset - bw
                else -> max(inset, min(vw - inset - bw, feetX[kind] - bw / 2f))
            }
            val y = if (spotAnchor[i] == NEXT) (if (top) art[kind][1] - g - bh else art[kind][3] + g) else if (top) inset else vh - inset - bh
            out[0] = x; out[1] = max(inset, min(vh - inset - bh, y)); out[2] = bw; out[3] = bh
        }

        /** [b] (x, y, w, h) grown by [d] on every side meets the box [o] (x, y, right, bottom)? Same tolerance as [ArenaGeo.Box.hits]. */
        private fun hits(b: FloatArray, d: Float, o: FloatArray) =
            b[0] - d < o[2] - EPS && o[0] < b[0] + b[2] + d - EPS && b[1] - d < o[3] - EPS && o[1] < b[1] + b[3] + d - EPS

        /** Same for a box given as (x, y, w, h). */
        private fun hitsBox(b: FloatArray, d: Float, ow: FloatArray) =
            b[0] - d < ow[0] + ow[2] - EPS && ow[0] < b[0] + b[2] + d - EPS && b[1] - d < ow[1] + ow[3] - EPS && ow[1] < b[1] + b[3] + d - EPS

        /** First pair (ours' spots x the wild one's) of level up to [level] at scale [k] where nothing is in the way; it is kept. */
        fun findOk(k: Float, level: Int): Boolean {
            for (oi in 0 until 8) {
                if (spotLevel[oi] > level) continue
                place(OURS, oi, k, pOurs)
                // ours (grown by the gap) must clear both art boxes
                if (hits(pOurs, g, art[WILD]) || hits(pOurs, g, art[OURS])) continue
                for (wi in 0 until 8) {
                    if (spotLevel[wi] > level) continue
                    place(WILD, wi, k, pWild)
                    if (hits(pWild, g, art[WILD]) || hits(pWild, g, art[OURS]) || hitsBox(pOurs, g, pWild)) continue
                    System.arraycopy(pOurs, 0, keepOurs, 0, 4)
                    System.arraycopy(pWild, 0, keepWild, 0, 4)
                    keepLevel = max(spotLevel[oi], spotLevel[wi])
                    return true
                }
            }
            return false
        }

        private val leastOurs = FloatArray(4)
        private val leastWild = FloatArray(4)

        /** The least-bad pair at scale [k] and the current frames: its score `cover * 1e6 + near` (cover = overlap areas, near = the same with the boxes grown by the gap). */
        fun leastBad(k: Float): Double {
            var best = Double.MAX_VALUE
            for (oi in 0 until 8) {
                place(OURS, oi, k, pOurs)
                for (wi in 0 until 8) {
                    place(WILD, wi, k, pWild)
                    val cover = cover(0f)
                    val near = cover(g)
                    val score = cover.toDouble() * 1e6 + near
                    if (score < best) {
                        best = score
                        System.arraycopy(pOurs, 0, leastOurs, 0, 4)
                        System.arraycopy(pWild, 0, leastWild, 0, 4)
                    }
                }
            }
            return best
        }

        /** Overlap areas of both boxes (grown by [d]) with both art boxes, and of ours (grown) with the wild one. */
        private fun cover(d: Float): Float =
            area(pOurs, d, art[WILD]) + area(pOurs, d, art[OURS]) + area(pWild, d, art[WILD]) + area(pWild, d, art[OURS]) + areaBox(pOurs, d, pWild)

        private fun area(b: FloatArray, d: Float, o: FloatArray): Float {
            val dx = min(b[0] + b[2] + d, o[2]) - max(b[0] - d, o[0])
            val dy = min(b[1] + b[3] + d, o[3]) - max(b[1] - d, o[1])
            return if (dx > 0f && dy > 0f) dx * dy else 0f
        }

        private fun areaBox(b: FloatArray, d: Float, o: FloatArray): Float {
            val dx = min(b[0] + b[2] + d, o[0] + o[2]) - max(b[0] - d, o[0])
            val dy = min(b[1] + b[3] + d, o[1] + o[3]) - max(b[1] - d, o[1])
            return if (dx > 0f && dy > 0f) dx * dy else 0f
        }

        /** Round 10 W7: the HUD's pair at scale [k]: ours on the left edge, the wild one on the right edge, each on its primary side (top / bottom as the rule says). */
        fun keepCorners(k: Float) {
            place(OURS, 2, k, keepOurs)
            place(WILD, 3, k, keepWild)
            keepLevel = 2
        }

        /** The pair [leastBad] just found is the one to return. */
        fun keepLeastBad() {
            System.arraycopy(leastOurs, 0, keepOurs, 0, 4)
            System.arraycopy(leastWild, 0, keepWild, 0, 4)
            keepLevel = -1
        }

        /** The plan for the kept pair at box scale [k] and sprite scale [f] (the frames must be set for [f]). */
        fun result(k: Float, f: Float, clear: Boolean, keep: Boolean = false): StagePlan {
            if (!clear && !keep) throw IllegalStateException("a plan that is not clear comes from the least-bad search")
            return StagePlan(
                frame[WILD]!!, frame[OURS]!!,
                ArenaGeo.Box(keepWild[0], keepWild[1], keepWild[2], keepWild[3]),
                ArenaGeo.Box(keepOurs[0], keepOurs[1], keepOurs[2], keepOurs[3]),
                k, f, clear, artBox[WILD]!!, artBox[OURS]!!, g, if (clear) keepLevel else -1, k < kFull - K_EPS,
            )
        }
    }
}
