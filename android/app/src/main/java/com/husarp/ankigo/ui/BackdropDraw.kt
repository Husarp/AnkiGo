package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Where a battle background's pixels land in a panel (round 8, pure, JVM-tested; [ArenaBackdrop] only turns these numbers into
 * canvas calls). A crop window (in picture pixels, from a [Stage] or [coverCrop]) is stretched over the whole panel; a window
 * larger than the picture leaves the picture smaller than the panel, and the rest shows the blurred copy ([BackdropBlur]),
 * which always covers the panel (cover, centred, anchored at the bottom, with [OVERSCAN] on every side for Earthquake's jolt).
 */
internal object BackdropDraw {
    /** The part of the picture the panel shows, in picture pixels. */
    class Crop(val x: Float, val y: Float, val w: Float, val h: Float)

    /** The blurred copy extends this much of the panel's size beyond each edge. */
    const val OVERSCAN = 0.08f

    /** PLAN 157: the most Earthquake may jolt the picture, as a share of the panel's height (inside [OVERSCAN]: the blur still covers). */
    const val JOLT_MAX = 0.06f

    /** The jolt [dy] px as drawn on a panel [h] px high: at most [JOLT_MAX] of it either way, so the picture's edge never shows. */
    fun jolt(dy: Float, h: Float): Float = dy.coerceIn(-JOLT_MAX * h, JOLT_MAX * h)

    /** A shrunk picture's edge fades into the blur over this share of its drawn size. */
    const val FADE = 0.07f

    /** A picture edge closer to the panel's edge than this (pixels) is not "inside" it: no fade, no blur needed for it. */
    private const val EDGE_PX = 0.5f

    /**
     * Without a stage yet: the largest window of the panel's shape inside the picture, centred sideways, at the bottom (the sky
     * is cut first). Never larger than the picture.
     */
    fun coverCrop(picW: Int, picH: Int, w: Float, h: Float): Crop {
        if (picW <= 0 || picH <= 0 || w <= 0f || h <= 0f) return Crop(0f, 0f, max(picW, 1).toFloat(), max(picH, 1).toFloat())
        val panel = w / h
        val cw: Float
        val ch: Float
        if (picW.toFloat() / picH > panel) { ch = picH.toFloat(); cw = ch * panel } else { cw = picW.toFloat(); ch = cw / panel }
        return Crop((picW - cw) / 2f, picH - ch, cw, ch)
    }

    /**
     * The result, in panel pixels: the picture's rectangle ([picL]..[picR], [picT]..[picB]; it sticks out of the panel when the
     * crop is inside it), the scale from picture pixels ([kx], [ky]), which sides of it end inside the panel and so fade into the
     * blur, and the blurred copy's rectangle.
     */
    class Layout(
        val picL: Float, val picT: Float, val picR: Float, val picB: Float,
        val kx: Float, val ky: Float,
        val fadeLeft: Boolean, val fadeTop: Boolean, val fadeRight: Boolean, val fadeBottom: Boolean,
        val blurL: Float, val blurT: Float, val blurR: Float, val blurB: Float,
    ) {
        /** True when some side of the picture ends inside the panel (the blurred copy shows there). */
        val anyFade: Boolean get() = fadeLeft || fadeTop || fadeRight || fadeBottom
    }

    /** The layout of a [picW] x [picH] picture cropped by [crop] into a [w] x [h] panel, with a blurred copy of [blurW] x [blurH] pixels. */
    fun layout(picW: Int, picH: Int, crop: Crop, blurW: Int, blurH: Int, w: Float, h: Float): Layout {
        val kx = w / crop.w
        val ky = h / crop.h
        val l = -crop.x * kx
        val t = -crop.y * ky
        val r = l + picW * kx
        val b = t + picH * ky
        // the blurred copy: cover inside the panel plus the overscan, centred sideways, bottom aligned
        val boxW = w * (1f + 2f * OVERSCAN)
        val boxH = h * (1f + 2f * OVERSCAN)
        val s = max(boxW / max(blurW, 1), boxH / max(blurH, 1))
        val dw = blurW * s
        val dh = blurH * s
        val bl = -w * OVERSCAN + (boxW - dw) / 2f
        val bb = h * (1f + OVERSCAN)
        return Layout(
            l, t, r, b, kx, ky,
            fadeLeft = l > EDGE_PX, fadeTop = t > EDGE_PX, fadeRight = r < w - EDGE_PX, fadeBottom = b < h - EDGE_PX,
            blurL = bl, blurT = bb - dh, blurR = bl + dw, blurB = bb,
        )
    }

    /**
     * The picture's rectangle cut to the panel [w] x [h] plus [OVERSCAN] on every side: what the fading layer has to cover. The
     * extra room is for Earthquake's jolt, which moves the layer: sharp picture that sticks out of the panel (it does not fade
     * there) must still be in the layer when the jolt brings it into view. Left, top, right, bottom; worked out when the layout
     * changes, not per frame.
     */
    fun visible(l: Layout, w: Float, h: Float) = floatArrayOf(
        max(-w * OVERSCAN, l.picL), max(-h * OVERSCAN, l.picT), min(w * (1f + OVERSCAN), l.picR), min(h * (1f + OVERSCAN), l.picB),
    )
}
