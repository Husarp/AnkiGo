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

    /** Align measured ground under the fixed sprite feet, then constrain the artwork to cover the panel.
     * Sprite layout remains unchanged, including in tall hosts whose safe box exceeds the picture.
     */
    fun stageCrop(bg: Backdrop, stage: Stage): Crop {
        val u = (bg.wild.x * bg.w - stage.cropX) / stage.cropW
        val v = (bg.wild.y * bg.h - stage.cropY) / stage.cropH
        val zoom = max(max(1f, bg.groundZoom), stage.cropH / (bg.h * bg.groundMaxHeight.coerceIn(.01f, 1f)))
        val shrink = min(1f, min(bg.w * zoom / stage.cropW, bg.h * zoom / stage.cropH))
        val cw = stage.cropW / zoom * shrink
        val ch = stage.cropH / zoom * shrink
        return Crop(
            (bg.wildGround.x * bg.w - u * cw).coerceIn(0f, max(0f, bg.w - cw)),
            (bg.wildGround.y * bg.h - v * ch).coerceIn(0f, max(0f, bg.h - ch)),
            cw, ch,
        )
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

    /**
     * R19-F: a reduced copy of the picture, for a picture that lands under [BackdropSharp.HARD_MIN] screen px per file px. [srcL]..[srcB]
     * is the part of the FILE it is made from (file pixels), [outW] x [outH] its size in screen pixels, and [dstL]..[dstB] where it lands
     * in the panel (the layer's rectangle, [visible]: panel plus the jolt's room, cut to the picture). [sameView] = it shows the same part
     * of the picture in the same place (a change of screen scale alone only changes the size of the copy).
     */
    data class Sharp(
        val srcL: Float, val srcT: Float, val srcR: Float, val srcB: Float,
        val outW: Int, val outH: Int,
        val dstL: Float, val dstT: Float, val dstR: Float, val dstB: Float,
    ) {
        fun sameView(o: Sharp) = srcL == o.srcL && srcT == o.srcT && srcR == o.srcR && srcB == o.srcB &&
            dstL == o.dstL && dstT == o.dstT && dstR == o.dstR && dstB == o.dstB
    }

    /**
     * The copy the picture needs in a [w] x [h] panel ([l] from [layout]), or null when the GPU can draw it with no smoothing. [bx] / [by]
     * = file pixels per catalog pixel (a file of another size is scaled to the catalog's), [fileW] x [fileH] the file's size, [screen] =
     * how much the panel is scaled on the screen (the study layouts and small windows draw a "virtual" panel scaled down).
     */
    fun sharp(l: Layout, w: Float, h: Float, bx: Float, by: Float, fileW: Int, fileH: Int, screen: Float): Sharp? {
        if (!(screen > 0f) || fileW <= 0 || fileH <= 0 || !(bx > 0f) || !(by > 0f)) return null
        val sx = l.kx / bx // panel px per file px
        val sy = l.ky / by
        if (BackdropSharp.hard(min(sx, sy) * screen)) return null
        val v = visible(l, w, h)
        if (v[2] - v[0] < 1f || v[3] - v[1] < 1f) return null
        val outW = max(1, Math.round((v[2] - v[0]) * screen))
        val outH = max(1, Math.round((v[3] - v[1]) * screen))
        return Sharp(
            ((v[0] - l.picL) / sx).coerceIn(0f, fileW.toFloat()), ((v[1] - l.picT) / sy).coerceIn(0f, fileH.toFloat()),
            ((v[2] - l.picL) / sx).coerceIn(0f, fileW.toFloat()), ((v[3] - l.picT) / sy).coerceIn(0f, fileH.toFloat()),
            outW, outH, v[0], v[1], v[2], v[3],
        )
    }
}
