package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Round 8 stage rule, part 1 (pure maths, no Android): lays one [Backdrop] into a panel of w x h (any unit) so both stand points
 * and the room above them stay in view, and says where the two Pokémon's feet are and how big their 96-px frames are. A port of
 * `layout()` and the sprite part of `panel()` in docs/design/backgrounds-stage-r8.html (the approved look).
 *
 * - The safe box holds both Pokémon at a generic frame (feet at 90 % of the frame, tallest sprites start at 8 %) plus a margin,
 *   clamped to the picture.
 * - The crop is the largest window of the panel's shape inside the picture. If it cannot hold the safe box the picture shrinks
 *   (crop bigger than the picture, [Stage.shrunk]); [zoom] below 1 (floating window) zooms in, but never past the safe box.
 * - Crop position: centred across, bottom first up and down (cuts the sky), moved only as far as the safe box needs. An axis where
 *   the crop is bigger than the picture is centred (negative start: the blurred copy fills there).
 * - Frame heights come from the picture's own scale, clamped to a share of the panel's height; the clamp scales with the share of
 *   the panel height the picture covers, so a tall panel showing the picture as a band does not blow the sprites up.
 */
internal object StageFit {
    /** The generic Pokémon frame used for the safe box: feet row, empty rows above the tallest usual sprite, body centre (shares of the frame). */
    private const val GEN_FOOT = 0.90f
    private const val GEN_HEAD = 0.08f
    private const val GEN_CX = 0.5f

    /** Margin around the safe box, share of the picture's height. */
    private const val PAD = 0.025f

    /** Frame height as a share of the panel's height: our Pokémon, the wild one. */
    private const val PLAYER_MIN = 0.30f
    private const val PLAYER_MAX = 0.80f
    private const val WILD_MIN = 0.20f
    private const val WILD_MAX = 0.62f

    /** Null when the panel or the picture has no size. */
    fun fit(bg: Backdrop, w: Float, h: Float, zoom: Float = 1f): Stage? {
        if (!(w > 0f) || !(h > 0f) || bg.w <= 0 || bg.h <= 0) return null
        val iw = bg.w.toFloat()
        val ih = bg.h.toFloat()
        val a = w / h
        val pad = PAD * ih
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (st in arrayOf(bg.player, bg.wild)) {
            val frame = st.s * ih
            val fx = st.x * iw
            val fy = st.y * ih
            l = min(l, fx - frame * GEN_CX)
            r = max(r, fx + frame * (1f - GEN_CX))
            t = min(t, fy - frame * (GEN_FOOT - GEN_HEAD))
            b = max(b, fy)
        }
        l = max(0f, l - pad)
        r = min(iw, r + pad)
        t = max(0f, t - pad)
        b = min(ih, b + pad)
        val sw = r - l
        val sh = b - t
        var cw: Float
        var ch: Float
        if (iw / ih > a) {
            ch = ih
            cw = ih * a
        } else {
            cw = iw
            ch = iw / a
        }
        val k = max(sw / cw, sh / ch)
        val shrunk = k > 1f
        val f = max(k, zoom)
        cw *= f
        ch *= f
        val cx = place(cw, iw, l, r, (iw - cw) / 2f)
        val cy = place(ch, ih, t, b, ih - ch)
        val ps = min(1f, ih / ch)
        val pf = frame(bg.player.s, ih, ch, PLAYER_MIN * ps, PLAYER_MAX * ps)
        val wf = frame(bg.wild.s, ih, ch, WILD_MIN * ps, WILD_MAX * ps)
        return Stage(
            cx, cy, cw, ch, shrunk,
            (bg.player.x * iw - cx) / cw * w, (bg.player.y * ih - cy) / ch * h, pf * h,
            (bg.wild.x * iw - cx) / cw * w, (bg.wild.y * ih - cy) / ch * h, wf * h,
        )
    }

    /** The crop start along one axis: [pref], moved to keep [lo]..[hi] inside, kept inside the picture; wider than the picture: centred. */
    private fun place(crop: Float, size: Float, lo: Float, hi: Float, pref: Float): Float {
        if (crop >= size) return (size - crop) / 2f
        var v = min(pref, lo)
        v = max(v, hi - crop)
        return max(0f, min(size - crop, v))
    }

    /** A frame's height as a share of the panel's height. */
    private fun frame(s: Float, ih: Float, ch: Float, lo: Float, hi: Float) = max(lo, min(hi, s * ih / ch))
}
