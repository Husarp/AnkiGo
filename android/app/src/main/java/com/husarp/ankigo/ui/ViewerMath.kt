package com.husarp.ankigo.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * TICKET R10-B: the zoom / pan maths of the full-screen sprite viewer ([SpriteViewer]), pure so it is unit-tested on the JVM.
 *
 * Coordinates: the viewer box is [Layout.boxW] x [Layout.boxH]; the picture sits centred in it at zoom 1 ([Layout.baseW] x
 * [Layout.baseH] px, the "fit" size). A [Pose] is the zoom (1 .. [MAX_ZOOM]) and the pan of the picture's centre from the box
 * centre. Points handed in as "focal" are offsets from the box centre.
 */
internal object ViewerMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f
    /** Double-tap zooms to this, around the tap point. */
    const val DOUBLE_TAP_ZOOM = 2.5f
    /** From this zoom on the picture counts as zoomed (a tap outside does not close, a double-tap zooms out). */
    private const val ZOOMED = 1.01f
    /** A whole-number scale is used while it is at least this share of the exact fit. */
    const val WHOLE_SHARE = 0.85f

    data class Pose(val zoom: Float, val tx: Float, val ty: Float) {
        companion object { val FIT = Pose(1f, 0f, 0f) }
    }

    /** [baseW] x [baseH] = the picture at zoom 1; [boxW] x [boxH] = the whole viewer. */
    data class Layout(val baseW: Float, val baseH: Float, val boxW: Float, val boxH: Float)

    fun clampZoom(z: Float): Float = if (z.isNaN()) MIN_ZOOM else z.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun isZoomed(p: Pose): Boolean = p.zoom > ZOOMED

    /**
     * Device pixels per picture pixel at zoom 1 for an [imgW] x [imgH] picture in the free area [boxW] x [boxH]: pixel art takes the
     * largest whole number that fits (crisp) when that is at least [WHOLE_SHARE] of the exact fit, else the exact fit (a 3.96 x fit
     * as 3 x would waste a quarter of the screen; at 3.96 x only one pixel row in 25 is a pixel narrower); anything else (a 3D
     * animation, drawn smoothed) the exact fit. 0 for an empty picture or box.
     */
    fun fitScale(imgW: Int, imgH: Int, boxW: Int, boxH: Int, pixelArt: Boolean): Float {
        if (imgW <= 0 || imgH <= 0 || boxW <= 0 || boxH <= 0) return 0f
        val fit = min(boxW.toFloat() / imgW, boxH.toFloat() / imgH)
        if (!pixelArt) return fit
        val whole = floor(fit)
        return if (whole >= 1f && whole >= fit * WHOLE_SHARE) whole else fit
    }

    /** How far the picture's centre may move from the box centre on one axis: 0 while it is not bigger than the box. */
    fun panLimit(content: Float, box: Float): Float = max(0f, (content - box) / 2f)

    /** [p] with the zoom in range and the pan such that the picture's edge is never further in than the box's edge. */
    fun clamp(p: Pose, l: Layout): Pose {
        val z = clampZoom(p.zoom)
        val lx = panLimit(l.baseW * z, l.boxW)
        val ly = panLimit(l.baseH * z, l.boxH)
        return Pose(z, finite(p.tx).coerceIn(-lx, lx), finite(p.ty).coerceIn(-ly, ly))
    }

    private fun finite(v: Float): Float = if (v.isNaN() || v.isInfinite()) 0f else v

    /** Zoom to [newZoom] keeping the picture point under the focal point ([fx], [fy] from the box centre) where it is. */
    fun zoomAt(p: Pose, newZoom: Float, fx: Float, fy: Float, l: Layout): Pose {
        val z0 = clampZoom(p.zoom)
        val z1 = clampZoom(newZoom)
        val k = z1 / z0
        return clamp(Pose(z1, fx - (fx - p.tx) * k, fy - (fy - p.ty) * k), l)
    }

    /** Pinch step: multiply the zoom by [factor] around the focal point. */
    fun pinch(p: Pose, factor: Float, fx: Float, fy: Float, l: Layout): Pose = zoomAt(p, p.zoom * factor, fx, fy, l)

    /** Double-tap: zoomed -> back to fit; at fit -> [DOUBLE_TAP_ZOOM] around the tap point. */
    fun doubleTap(p: Pose, fx: Float, fy: Float, l: Layout): Pose =
        if (isZoomed(p)) Pose.FIT else zoomAt(p, DOUBLE_TAP_ZOOM, fx, fy, l)

    /** A finger drag of ([dx], [dy]) px moves the picture with it, inside the pan limits; nothing moves while it is not zoomed. */
    fun drag(p: Pose, dx: Float, dy: Float, l: Layout): Pose = if (!isZoomed(p)) p else clamp(Pose(p.zoom, p.tx + dx, p.ty + dy), l)

    /** The drawn picture as left, top, right, bottom in box coordinates (origin top-left). */
    fun spriteRect(p: Pose, l: Layout): FloatArray {
        val w = l.baseW * p.zoom
        val h = l.baseH * p.zoom
        val cx = l.boxW / 2f + p.tx
        val cy = l.boxH / 2f + p.ty
        return floatArrayOf(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** True when ([x], [y]) (box coordinates, origin top-left) is on the picture. */
    fun hits(p: Pose, x: Float, y: Float, l: Layout): Boolean {
        val r = spriteRect(p, l)
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]
    }

    /** A tap closes the viewer only while not zoomed and outside the picture. */
    fun closesOnTap(p: Pose, x: Float, y: Float, l: Layout): Boolean = !isZoomed(p) && !hits(p, x, y, l)
}
