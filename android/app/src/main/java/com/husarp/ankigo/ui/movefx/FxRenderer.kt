package com.husarp.ankigo.ui.movefx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Draws what a [FxScript] holds at its [FxScript.clock]: the dark wash, layer-0 procs, the particles, layer-1 procs, the white
 * flash (the order of the mockup's `draw()`). One per animation; keeps its paints and helper, allocates nothing per frame.
 */
internal class FxRenderer(ctx: Context, unit: Float) {
    private val art = FxArt.of(ctx)
    private val fc = FxCanvas(art, unit)
    private val wash = Paint()

    fun draw(canvas: Canvas, s: FxScript) {
        val ms = s.clock
        fc.raw = canvas
        if (s.dim > 0f) fill(canvas, 6, 6, 30, s.dim)
        procs(canvas, s, ms, 0)
        val parts = s.particles
        for (i in 0 until parts.size) particle(canvas, parts[i], ms)
        procs(canvas, s, ms, 1)
        if (s.flashAlpha > 0f) fill(canvas, 255, 255, 255, s.flashAlpha)
    }

    private fun fill(canvas: Canvas, r: Int, g: Int, b: Int, a: Float) {
        wash.setARGB((FxMath.clamp01(a) * 255f + .5f).toInt(), r, g, b)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), wash)
    }

    private fun procs(canvas: Canvas, s: FxScript, ms: Float, layer: Int) {
        val list = s.procs
        for (i in 0 until list.size) {
            val p = list[i]
            if (p.layer != layer || ms < p.s || ms >= p.e) continue
            canvas.save()
            fc.begin()
            p.draw.draw(fc, (ms - p.s) / (p.e - p.s), ms)
            canvas.restore()
        }
    }

    private fun particle(canvas: Canvas, p: FxParticle, ms: Float) {
        if (ms < p.t0 || !p.alive(ms)) return
        val x = p.x(ms)
        val y = p.y(ms)
        val size = p.size(ms)
        canvas.save()
        fc.begin()
        fc.alpha = p.alpha(ms)
        fc.additive = p.add
        val bits = p.pix
        if (bits != null) {
            // pixel bit: colours cycle with time and spot, never rotated
            val i = floor(ms / 80f + p.x0 / fc.unit).toInt()
            fc.pixelBit(x, y, size, bits[Math.floorMod(i, bits.size)])
        } else {
            val custom = p.draw
            // hand-drawn particles (pixel art) sit on whole device pixels
            if (custom != null) canvas.translate(Math.round(x).toFloat(), Math.round(y).toFloat()) else canvas.translate(x, y)
            canvas.rotate(Math.toDegrees(p.angle(ms).toDouble()).toFloat())
            if (custom != null) {
                custom.draw(fc, size, p.progress(ms), ms)
            } else if (size > .25f) {
                var bmp = p.cache as? Bitmap
                if (bmp == null) {
                    bmp = p.img?.let { art.bitmap(it) }
                    p.cache = bmp
                }
                if (bmp != null) {
                    val w = size * p.stretch
                    val h = size / sqrt(p.stretch)
                    fc.bitmap(bmp, p.tint, -w / 2f, -h / 2f, w, h)
                }
            }
        }
        canvas.restore()
    }
}
