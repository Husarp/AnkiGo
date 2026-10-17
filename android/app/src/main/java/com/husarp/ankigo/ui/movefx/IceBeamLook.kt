package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Ice Beam (docs/design/attack-anims-v2/v2-fx.js `icebeam`): Showdown's projectile (four icicles stepped along the line, then
 * iceballs and frost wisps on the target; our own redrawn shards) plus our own ice cube that forms around the target, shines,
 * cracks and shatters. The cube and the shards grow with the size factor k; the target turns cold meanwhile.
 */
internal object IceBeamFx {
    /** Mockup ms: the cube forms at [F0] over [FORM], cracks from [CRACK], shatters at [BREAK]. */
    const val F0 = 430f
    const val FORM = 170f
    const val CRACK = 1150f
    const val BREAK = 1290f
    /** The first iceball lands on the target (Showdown: iceball at 100 ms growing, the first wisp at 200). */
    const val IMPACT = 150f
    const val HIT_MS = 320f
    const val TOTAL = BREAK + 850f

    /** The target's cold look while it is in the ice: saturate(.5) brightness(1.18) hue-rotate(-8deg). */
    private val COLD: FloatArray = FxColor.chain(FxColor.saturate(.5f), FxColor.brightness(1.18f), FxColor.hueRotate(-8f))

    /** Cube scale while it forms and settles (mockup `sc`). */
    fun scaleAt(ms: Float): Float {
        val fp = min(1f, (ms - F0) / FORM)
        return if (fp < 1f) FxMath.lerp(.7f, 1.06f, FxEase.OUT2.at(fp)) else 1f + .06f * max(0f, 1f - (ms - F0 - FORM) / 80f)
    }

    /** Cube opacity while it forms. */
    fun alphaAt(ms: Float): Float = min(1f, min(1f, (ms - F0) / FORM) * 1.3f)

    /** 0..1: how far the cracks have spread. */
    fun crackAt(ms: Float): Float = if (ms > CRACK) (ms - CRACK) / (BREAK - CRACK) else 0f

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val sp = s.space
        val ang = sp.pathAngle
        val shard = FxSprite("shard", 0, 80f, 30f)
        // Showdown icebeam: four icicles stepped along the line, then iceballs + frost wisps on the target
        for (i in 0 until 4) {
            s.psFx(shard, PsKey(z = 40f * (i + 1), scale = 1.5f * k, opacity = .6f, time = 40f * i), PsEnd(opacity = 0f, time = 40f * i + 600f), PsEase.LINEAR, PsAfter.NONE, add = true, rot = ang)
        }
        val ball = FxSprite("ps:iceball")
        val wisp = FxSprite("ps:wisp")
        s.psFx(ball, PsKey(z = 200f, scale = 0f, opacity = 1f, time = 100f), PsEnd(scale = 2f, opacity = 0f, time = 400f))
        s.psFx(ball, PsKey(z = 200f, scale = 0f, opacity = 1f, time = 300f), PsEnd(scale = 2f, opacity = 0f, time = 600f))
        s.psFx(wisp, PsKey(x = -30f, z = 200f, scale = 2f, opacity = .5f, time = 200f), PsEnd(scale = 4f, opacity = 0f, time = 600f), PsEase.LINEAR, PsAfter.FADE)
        s.psFx(wisp, PsKey(y = -30f, z = 200f, scale = 2f, opacity = .5f, time = 300f), PsEnd(scale = 4f, opacity = 0f, time = 650f), PsEase.LINEAR, PsAfter.FADE)
        s.psFx(wisp, PsKey(x = 15f, z = 200f, scale = 2f, opacity = .5f, time = 400f), PsEnd(scale = 4f, opacity = 0f, time = 700f), PsEase.LINEAR, PsAfter.FADE)
        // our ice cube: forms, shines, cracks, shatters
        val centre = sp.fromTarget(2f, 8f)
        val cube = IceCube(s, k)
        s.proc(F0, BREAK, 1) { c, _, ms -> cube.draw(c, ms, centre.x, centre.y, sp.dir) }
        s.ticks(F0, 800f) { _, _ ->
            if (s.chance(.25f)) {
                s.particle(
                    "star_04", 0xFFE8FBFF.toInt(), centre.x + sp.len(s.rnd(-cube.w / 2f, cube.w / 2f)), centre.y + sp.len(s.rnd(-cube.h / 2f, cube.h / 2f)),
                    s0 = s.rnd(12f, 22f), s1 = 2f, life = 300f, add = true,
                )
            }
        }
        s.tween(F0, BREAK - F0) { p, ms -> s.target.set(x = if (p > .85f) sin(ms / 12f) * 1.5f else 0f, filter = COLD) }
        s.at(BREAK) {
            val scratch = FloatArray(6)
            for (i in 0 until 18) {
                val ox = s.rnd(-cube.w / 2f, cube.w / 2f)
                val oy = s.rnd(-cube.h / 2f, cube.h / 2f)
                val size = s.rnd(10f, 22f) * k
                val sh = floatArrayOf(-.5f, -.3f, .5f, -.45f, .15f, .55f)
                for (j in sh.indices) sh[j] += s.rnd(-.2f, .2f)
                s.particle(
                    x = centre.x + sp.x(ox), y = centre.y + sp.y(oy),
                    vx = ox * s.rnd(3f, 6f), vy = oy * s.rnd(2f, 4f) - s.rnd(60f, 160f), ay = 620f,
                    s0 = size, s1 = size * .7f, life = 700f, rot = s.rnd(0f, 6f), vrot = s.rnd(-9f, 9f),
                    draw = FxParticleDraw { c, sz, _, _ ->
                        for (j in 0 until 6) scratch[j] = sh[j] * sz
                        c.fillPoly(scratch, 0xFFBEEBFF.toInt(), .75f)
                        c.strokePoly(scratch, 1.2f * c.unit, 0xFFFFFFFF.toInt(), .95f)
                    },
                )
            }
            s.burst("star_04", 0xFFDFF8FF.toInt(), centre, 10, 200f, 500f, 26f, 3f, true)
            s.particle("circle_02", 0xFFBDF0FF.toInt(), centre.x, centre.y, s0 = 40f, s1 = 160f * k, life = 380f, add = true)
            s.flashOnce(.2f, 50f)
        }
        s.hit(BREAK, HIT_MS, push = 6f)
        return FxTimes(TOTAL, IMPACT, if (s.react) BREAK + HIT_MS else IMPACT)
    }
}

/**
 * The ice cube of the Ice Beam, relative to its centre, in pixels (stage units x the unit): front face, top and side face, shine,
 * frost marks, cracks. Everything random is drawn once in the constructor ([w], [h] in stage units for the callers). Drawn mirrored
 * when the target is on the left, so the side faces stay on the outer edge.
 */
internal class IceCube(s: FxScript, private val k: Float) {
    private val u = s.u
    /** Front face size in stage units. */
    val w = 100f * k
    val h = 96f * k
    private val pw = w * u
    private val ph = h * u
    private val ddx = 22f * k * u
    private val ddy = -17f * k * u
    private val fx = -pw / 2f - ddx / 2f
    private val fy = -ph / 2f - ddy / 2f
    private val frost = Array(10) { floatArrayOf(s.rnd(0f, 1f), s.rnd(0f, 1f), s.rnd(5f, 12f), s.rnd(0f, PI.toFloat())) }
    private val cracks: Array<FloatArray> = Array(6) { r ->
        val a = r / 6f * 2f * PI.toFloat() + s.rnd(-.3f, .3f)
        s.jag(-6f * u, -4f * u, -6f * u + cos(a) * pw * .55f, -4f * u + sin(a) * ph * .55f, 4, 7f * u)
    }

    private val front = FloatArray(8)
    private val side = FloatArray(8)
    private val top = FloatArray(8)
    private val shineA = FloatArray(8)
    private val shineB = FloatArray(8)
    private val outlineA = FloatArray(10)
    private val outlineB = FloatArray(4)

    init {
        poly(front, fx, fy, fx + pw, fy, fx + pw, fy + ph, fx, fy + ph)
        poly(side, fx + pw, fy, fx + pw + ddx, fy + ddy, fx + pw + ddx, fy + ph + ddy, fx + pw, fy + ph)
        poly(top, fx, fy, fx + ddx, fy + ddy, fx + pw + ddx, fy + ddy, fx + pw, fy)
        outlineA[0] = fx; outlineA[1] = fy
        outlineA[2] = fx + ddx; outlineA[3] = fy + ddy
        outlineA[4] = fx + pw + ddx; outlineA[5] = fy + ddy
        outlineA[6] = fx + pw + ddx; outlineA[7] = fy + ph + ddy
        outlineA[8] = fx + pw; outlineA[9] = fy + ph
        outlineB[0] = fx + pw; outlineB[1] = fy; outlineB[2] = fx + pw + ddx; outlineB[3] = fy + ddy
    }

    private fun poly(p: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        p[0] = x0; p[1] = y0; p[2] = x1; p[3] = y1; p[4] = x2; p[5] = y2; p[6] = x3; p[7] = y3
    }

    /** Draws the cube centred on ([cx], [cy]) at mockup ms [ms]; [dir] -1 mirrors it. */
    fun draw(c: FxCanvas, ms: Float, cx: Float, cy: Float, dir: Float) {
        val sc = IceBeamFx.scaleAt(ms)
        val alpha = IceBeamFx.alphaAt(ms)
        val shine = (ms - 650f) / 500f
        val crack = IceBeamFx.crackAt(ms)
        c.save()
        c.translate(cx, cy)
        c.scale(dir * sc, sc)
        c.alpha = alpha
        c.fillPoly(side, 0xFF5AAAEB.toInt(), .42f)
        c.fillPoly(top, 0xFFE4F8FF.toInt(), .6f)
        c.gradientPoly(front, fx, fy, fx + pw, fy + ph, GRADIENT, STOPS)
        c.save()
        c.clipPoly(front)
        val bx = fx - pw * .4f + shine * pw * 1.8f
        val k16 = 16f * k * u
        val k24 = 24f * k * u
        val k30 = 30f * k * u
        poly(shineA, bx, fy + ph, bx + k16, fy + ph, bx + k16 + ph * .5f, fy, bx + ph * .5f, fy)
        c.fillPoly(shineA, WHITE, .34f)
        poly(shineB, bx + k24, fy + ph, bx + k30, fy + ph, bx + k30 + ph * .5f, fy, bx + k24 + ph * .5f, fy)
        c.fillPoly(shineB, WHITE, .22f)
        c.restore()
        c.strokeRect(fx + 6f * k * u, fy + 6f * k * u, fx + pw - 6f * k * u, fy + ph - 6f * k * u, 1.2f * u, WHITE, .35f)
        c.strokePoly(front, 2f * u, WHITE, .92f)
        c.strokePoly(outlineA, 2f * u, WHITE, .92f, closed = false)
        c.strokePoly(outlineB, 2f * u, WHITE, .92f, closed = false)
        for (fr in frost) {
            val x = fx + (if (fr[0] < .5f) fr[0] * .3f else 1f - (1f - fr[0]) * .3f) * pw
            val y = fy + (if (fr[1] < .5f) fr[1] * .3f else 1f - (1f - fr[1]) * .3f) * ph
            c.line(x, y, x + cos(fr[3]) * fr[2] * u, y + sin(fr[3]) * fr[2] * u, 1.4f * u, WHITE, .7f)
        }
        if (crack > 0f) {
            for (pts in cracks) {
                val m = max(2, ceil((pts.size / 2) * crack).toInt())
                c.stroke(pts, 1.8f * u, WHITE, 1f, count = min(m, pts.size / 2))
            }
        }
        c.restore()
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        val GRADIENT = intArrayOf(0x75D2F4FF, 0x4D87CDFA, 0x70B4E8FF)
        val STOPS = floatArrayOf(0f, .5f, 1f)
    }
}
