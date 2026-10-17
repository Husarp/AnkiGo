package com.husarp.ankigo.ui.movefx

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Vine Whip (docs/design/attack-anims-v2/v2-fx.js `vinewhip`, the approved v5): three short pixel-art vines peek out of the ground
 * at the target's feet, flick over it once each (staggered), and sink back. Outlined, flat-shaded, at the pixel size of the
 * sprites; a few pixel dirt bits. A stronger move (k >= 1.35, e.g. Power Whip) has a fourth vine. The vines are painted into a
 * small pixel buffer ([VineRaster]) that is drawn without smoothing, like the mockup's ImageData.
 */
internal object VineWhipFx {
    /** Mockup ms of one vine's life: grows for [G], waits [WAIT], lashes in [F], holds [HOLD], lashes back in [BACK], sinks in [S]. */
    const val G = 220f
    const val WAIT = 70f
    const val F = 140f
    const val HOLD = 90f
    const val BACK = 180f
    const val S = 230f
    const val TOT = G + WAIT + F + HOLD + BACK + S

    /** From this size factor on the move has a fourth vine. */
    const val FOUR_AT = 1.35f

    /** Ground line at the target's feet, in stage units below its centre. */
    private const val GROUND = 42f

    /** One vine in the mockup frame (x positive toward the target): [gx], [gy] = where it comes out, from the target's centre plus [GROUND]; [dir] = the side it lashes toward. */
    class Vine(@JvmField val gx: Float, @JvmField val gy: Float, @JvmField val t0: Float, @JvmField val dir: Float, @JvmField val len: Float) {
        /** Mockup ms of the flick landing. */
        val hitAt: Float get() = t0 + G + WAIT + F * .85f
    }

    private val ALL = arrayOf(
        Vine(-38f, 0f, 0f, 1f, 28f),     // front-left of the target, flicks right
        Vine(30f, 7f, 210f, -1f, 22f),   // front-right, flicks left
        Vine(-2f, 13f, 400f, 1f, 25f),   // middle, a bit lower, flicks right
        Vine(56f, 3f, 580f, -1f, 24f),   // far right (only for k >= FOUR_AT), flicks left
    )

    fun vinesFor(k: Float): List<Vine> = ALL.take(if (k >= FOUR_AT) 4 else 3)

    /** 0 = wound up away from the target, 1 = lashed over it (a little more at the peak). */
    fun whipAt(msIn: Float): Float {
        val ms = msIn - (G + WAIT)
        if (ms <= 0f) return 0f
        if (ms < F) return FxEase.IN2.at(ms / F) * 1.1f
        if (ms < F + HOLD) return 1.1f - .1f * (ms - F) / HOLD
        return max(.2f, 1f - .8f * FxEase.INOUT.at(min(1f, (ms - F - HOLD) / BACK)))
    }

    /** How far a vine has grown at [ms] of its life: 0..1 (out, stay, in). */
    fun grow(ms: Float): Float = if (ms < G) FxEase.OUT2.at(ms / G) else if (ms < TOT - S) 1f else 1f - FxEase.IN2.at(min(1f, (ms - TOT + S) / S))

    /** Art pixels around a vine's reachable points that its drawing may still touch: outline, leaves (9 wide, 5 up), rounding. */
    private const val EDGE = 12
    /** Art pixels below a vine's root that the dirt hole may touch. */
    private const val BELOW_ROOT = 6

    /**
     * The pixel buffer over the vines and where each root sits in it (pure maths). Sized from the vines themselves, in whole
     * art pixels of [dev] device pixels: a vine of length `len x k` never reaches farther than that from its root, so the buffer
     * reaches `len x k + EDGE` art pixels around the outermost roots (and a few below them). Unlike a buffer sized in stage units,
     * this never clips however small the arena is: a vine always takes `len x k` art pixels, but an art pixel can no longer shrink
     * below one device pixel.
     */
    class Layout(
        @JvmField val left: Float, @JvmField val top: Float, @JvmField val bw: Int, @JvmField val bh: Int,
        /** Each root in buffer art pixels. */
        @JvmField val baseX: FloatArray, @JvmField val baseY: FloatArray,
        /** The side each vine lashes toward on screen: +1 right, -1 left. */
        @JvmField val sdir: FloatArray,
    )

    fun layout(sp: FxSpace, k: Float, dev: Int, vines: List<Vine> = vinesFor(k)): Layout {
        val gx = FloatArray(vines.size)
        val gy = FloatArray(vines.size)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((i, v) in vines.withIndex()) {
            val g = sp.fromTarget(v.gx, GROUND + v.gy)
            gx[i] = g.x; gy[i] = g.y
            minX = min(minX, g.x); maxX = max(maxX, g.x); minY = min(minY, g.y); maxY = max(maxY, g.y)
        }
        val reach = (ceil(vines.maxOf { it.len } * k).toInt() + EDGE) * dev
        val left = minX - reach
        val top = minY - reach
        val bw = max(1, ceil((maxX + reach - left) / dev).toInt())
        val bh = max(1, ceil((maxY + BELOW_ROOT * dev - top) / dev).toInt())
        return Layout(
            left, top, bw, bh,
            FloatArray(vines.size) { (gx[it] - left) / dev }, FloatArray(vines.size) { (gy[it] - top) / dev },
            FloatArray(vines.size) { vines[it].dir * sp.dir },
        )
    }

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val sp = s.space
        val dev = sp.artPixels(k)
        val px = s.artPx(k)
        val vines = vinesFor(k)
        // the pixel buffer over the vines, whole art pixels
        val lay = layout(sp, k, dev, vines)
        val bw = lay.bw
        val bh = lay.bh
        val left = lay.left
        val top = lay.top
        val raster = VineRaster(bw, bh, k)
        val baseX = lay.baseX
        val baseY = lay.baseY
        val sdir = lay.sdir
        val lastT0 = vines.maxOf { it.t0 }
        var bmp: Bitmap? = null
        s.proc(0f, lastT0 + TOT, 1) { c, _, ms ->
            raster.clear()
            for (i in vines.indices) {
                val v = vines[i]
                if (ms >= v.t0 && ms < v.t0 + TOT) raster.vine(v.len, baseX[i], baseY[i], sdir[i], ms - v.t0)
            }
            val b = bmp ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bmp = it }
            b.setPixels(raster.pix, 0, bw, 0, 0, bw, bh)
            c.crisp(b, left, top, dev)
        }
        var lastHit = 0f
        for (v in vines) {
            val gp = sp.fromTarget(v.gx, GROUND + v.gy)
            val dirt = FxPixelArt.DIRT_COLORS
            s.at(v.t0) {                                           // a few pixel dirt bits as it breaks the ground
                for (i in 0 until 6) {
                    s.particle(
                        x = gp.x + sp.len(s.rnd(-9f, 9f)), y = gp.y - sp.len(2f),
                        vx = s.rnd(-70f, 70f), vy = s.rnd(-190f, -80f), ay = 700f, life = s.rnd(420f, 600f),
                        s0 = px * (if (i % 3 != 0) 1f else 2f), a1 = 1f, pix = intArrayOf(dirt[i % 3]),
                    )
                }
            }
            val hitAt = v.hitAt
            val spark = sp.fromTarget(-v.dir * 14f, 18f)
            s.at(hitAt) {                                          // the flick lands: a tiny pixel sparkle
                for (i in 0 until 5) {
                    s.particle(
                        x = spark.x + sp.len(s.rnd(-4f, 4f)), y = spark.y + sp.len(s.rnd(-4f, 4f)),
                        vx = s.rnd(-90f, 90f), vy = s.rnd(-100f, 40f), life = s.rnd(160f, 260f),
                        s0 = px, a1 = .5f, pix = SPARKLE,
                    )
                }
            }
            s.hit(hitAt, HIT_MS, push = 7f * v.dir)
            s.at(v.t0 + TOT - S * .6f) {                           // sinks back: a couple of bits fall in
                for (i in 0 until 3) {
                    s.particle(
                        x = gp.x + sp.len(s.rnd(-7f, 7f)), y = gp.y - sp.len(1f),
                        vx = s.rnd(-30f, 30f), vy = s.rnd(-80f, -30f), ay = 500f, life = s.rnd(300f, 420f),
                        s0 = px, a1 = 1f, pix = intArrayOf(dirt[i]),
                    )
                }
            }
            lastHit = max(lastHit, hitAt)
        }
        val impact = vines[0].hitAt
        return FxTimes(lastT0 + TOT + 450f, impact, if (s.react) lastHit + HIT_MS else impact)
    }

    private const val HIT_MS = 320f
    private val SPARKLE = intArrayOf(0xFFFFFFFF.toInt(), 0xFFD8F7B8.toInt())
}

/**
 * Paints vines into an ARGB pixel buffer of [bw] x [bh] art pixels (pure, no android): the dirt hole, the vine as a thick
 * line shaded by side with an outline, two leaves, the front lip of the hole. Positions are art pixels (floats). Buffers are
 * reused; nothing is allocated per frame.
 */
internal class VineRaster(val bw: Int, val bh: Int, private val k: Float) {
    @JvmField val pix = IntArray(bw * bh)
    private val cap = ceil(MAX_LEN * max(k, 1f)).toInt() + 4
    private val ptX = FloatArray(cap)
    private val ptY = FloatArray(cap)
    private val segX = FloatArray(cap)
    private val segY = FloatArray(cap)
    private val segInv = FloatArray(cap)

    fun clear() = pix.fill(0)

    private fun put(x: Float, y: Float, c: Int) {
        val ix = floor(x + .5f).toInt()
        val iy = floor(y + .5f).toInt()
        if (ix in 0 until bw && iy in 0 until bh) pix[iy * bw + ix] = c
    }

    /** The dirt hole at ([cx], [cy]): [front] = its near lip (drawn over the vine), else the back with the dark inside. */
    private fun mound(cx: Float, cy: Float, g: Float, front: Boolean) {
        val rw = floor(7f * min(1f, g * 3f) + .5f).toInt()
        if (rw < 1) return
        for (x in -rw..rw) {
            val e = floor(sqrt(1f - (x * x).toFloat() / (rw * rw + .01f)) * 2f + .5f).toInt()
            if (!front) {
                for (y in -e..0) put(cx + x, cy + y, if (y == -e) RIM_L else if (abs(x) < rw - 1) (if (y == 0) HOLE_D else HOLE) else RIM)
            } else {
                put(cx + x, cy + e, RIM)
                if (e > 0) put(cx + x, cy + e - 1, if (abs(x) < rw - 1) HOLE_D else RIM)
            }
        }
    }

    /**
     * One vine [ms] after it started: base at ([bx], [by]), [dir] = +1 / -1 the side it lashes toward (on screen), [len] its length
     * at k = 1 in art pixels. The tip lags behind the base; the shading follows the side of the centre line.
     */
    fun vine(len: Float, bx: Float, by: Float, dir: Float, ms: Float) {
        val g = VineWhipFx.grow(ms)
        mound(bx, by, g, false)
        val lk = len * k
        val steps = floor(lk * g + .5f).toInt().coerceAtMost(cap - 2)
        if (steps < 2) { mound(bx, by, g, true); return }
        ptX[0] = bx; ptY[0] = by
        for (i in 1..steps) {                                      // walk up the vine one art pixel at a time
            val s = i.toFloat() / steps
            val w = VineWhipFx.whipAt(ms - s * 45f)
            val a = dir * (FxMath.lerp(-.3f, .5f, w) + FxMath.lerp(-.9f, 1.7f, w) * s * s)
            ptX[i] = ptX[i - 1] + sin(a)
            ptY[i] = ptY[i - 1] - cos(a)
            val dx = ptX[i] - ptX[i - 1]
            val dy = ptY[i] - ptY[i - 1]
            segX[i - 1] = dx; segY[i - 1] = dy; segInv[i - 1] = 1f / (dx * dx + dy * dy)
        }
        val r0 = 3.3f * sqrt(k)
        var minX = 1e9f; var maxX = -1e9f; var minY = 1e9f; var maxY = -1e9f
        for (i in 0..steps) {
            minX = min(minX, ptX[i]); maxX = max(maxX, ptX[i]); minY = min(minY, ptY[i]); maxY = max(maxY, ptY[i])
        }
        val x0 = max(0, floor(minX - 4f).toInt())
        val x1 = min(bw - 1, ceil(maxX + 4f).toInt())
        val y0 = max(0, floor(minY - 4f).toInt())
        val y1 = min(bh - 1, ceil(maxY).toInt())
        for (y in y0..y1) for (x in x0..x1) {                      // nearest point on the centre line -> shading by side
            var best2 = 1e18f
            var side = 0f
            var sp = 0f
            val cx = x + .5f
            val cy = y + .5f
            for (j in 0 until steps) {
                val ax = ptX[j]; val ay = ptY[j]
                val dx = segX[j]; val dy = segY[j]
                val t = ((cx - ax) * dx + (cy - ay) * dy) * segInv[j]
                val tc = if (t < 0f) 0f else if (t > 1f) 1f else t
                val ex = cx - ax - tc * dx
                val ey = cy - ay - tc * dy
                val d2 = ex * ex + ey * ey
                if (d2 < best2) { best2 = d2; side = (dx * ey - dy * ex) * dir; sp = j + tc }
            }
            val best = sqrt(best2)
            val r = r0 * (1f - .55f * sp / lk)
            if (best >= r) continue
            val rel = side / r
            val rounded = floor(sp + .5f).toInt()
            var c = if (best > r - 1f) OL else if (rel > .3f) LT else if (rel > -.3f) (if (rounded % 5 == 2) ND else MD) else DK
            if (c == LT && rounded % 5 == 4) c = HL
            pix[y * bw + x] = c
        }
        for (li in 0..1) {                                         // two leaves on the outer sides
            val at = if (li == 0) .42f else .7f
            val idx = floor(at * lk + .5f).toInt()
            if (idx >= steps - 2) continue
            val side = (if (li == 0) 1f else -1f) * dir
            val ox = floor(ptX[idx] + .5f) + (if (side > 0f) 1f else -1f)
            val oy = floor(ptY[idx] + .5f) - 5f
            for (ry in FxPixelArt.LEAF_ROWS.indices) {
                val row = FxPixelArt.LEAF_ROWS[ry]
                for (rx in row.indices) {
                    val ch = row[if (side > 0f) rx else row.length - 1 - rx]
                    if (ch != '.') put(ox + (if (side > 0f) rx else rx - row.length + 1), oy + ry, LEAF[ch] ?: 0)
                }
            }
        }
        mound(bx, by, g, true)
    }

    private companion object {
        const val MAX_LEN = 28f
        const val OL = 0xFF173D1A.toInt()
        const val DK = 0xFF2B6A27.toInt()
        const val MD = 0xFF3F9136.toInt()
        const val LT = 0xFF69BD48.toInt()
        const val HL = 0xFFA4DC72.toInt()
        const val ND = 0xFF24561F.toInt()
        const val RIM = 0xFF8A643C.toInt()
        const val RIM_L = 0xFFA98250.toInt()
        const val HOLE = 0xFF3A2614.toInt()
        const val HOLE_D = 0xFF24170B.toInt()
        val LEAF: Map<Char, Int> = FxPixelArt.LEAF_PALETTE
    }
}
