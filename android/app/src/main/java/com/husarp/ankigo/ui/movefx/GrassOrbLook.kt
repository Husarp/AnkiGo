package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** PLAN 664, option C: a compressed leaf storm, then a leafy impact with a lingering aura and rings. */
internal object GrassOrbFx {
    const val LAUNCH = 250f
    const val IMPACT = 680f
    const val TOTAL = 1300f
    private val leaf = floatArrayOf(-1f, 0f, 0f, -.42f, 1f, 0f, 0f, .42f)

    /** Delayed points on the travelled path: every leaf is born at the attacker, never extrapolated behind it. */
    internal fun trailPoint(space: FxSpace, progress: Float, index: Int, out: FloatArray): Boolean {
        val age = progress - index * .028f
        if (age < 0f) return false
        val q = age.coerceIn(0f, 1f)
        val f = q * q
        val wave = sin(q * 18f + index) * 14f * sin(q * PI.toFloat()) * space.dir
        val length = space.pathLength.coerceAtLeast(1f)
        out[0] = space.betweenX(f, 0f) - (space.targetY - space.attackerY) / length * wave
        out[1] = space.betweenY(f, 0f) + (space.targetX - space.attackerX) / length * wave
        return true
    }

    private fun drawLeaf(c: FxCanvas, x: Float, y: Float, size: Float, angle: Float, color: Int, vein: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        c.scale(size)
        c.fillPoly(leaf, color)
        c.line(-.7f, 0f, .8f, 0f, .09f, vein)
        c.restore()
    }

    fun build(s: FxScript): FxTimes {
        val pal = s.plan.palette
        val u = s.u
        val k = s.k
        val a = s.space.fromAttacker(0f, 0f)
        val t = s.space.fromTarget(0f, 0f)
        val point = FloatArray(2)
        s.proc(0f, LAUNCH) { c, p, _ ->
            val radius = (5f + 13f * p) * k * u
            c.tex("circle_05", pal.glow, a.x, a.y, radius * 4f, radius * 4f)
            c.ellipse(a.x, a.y, radius, radius, 0f, pal.deep, .85f)
            c.ellipse(a.x, a.y, radius * .5f, radius * .5f, 0f, pal.spark)
        }
        s.proc(LAUNCH, IMPACT) { c, p, ms ->
            for (i in 18 downTo 0) {
                if (!trailPoint(s.space, p, i, point)) continue
                c.alpha = (1f - i / 24f) * (p * 8f).coerceAtMost(1f)
                drawLeaf(c, point[0], point[1], (8f + i * .18f) * k * u,
                    s.space.pathAngle + ms / 120f + i, pal.glow, pal.spark)
            }
            val f = p * p
            val x = s.space.betweenX(f, 0f)
            val y = s.space.betweenY(f, 0f)
            val radius = (18f - 4f * p) * k * u
            c.alpha = 1f
            c.tex("circle_05", pal.glow, x, y, radius * 4f, radius * 4f)
            c.ellipse(x, y, radius, radius, 0f, pal.deep, .9f)
            c.ellipse(x, y, radius * .75f, radius * .75f, 0f, pal.glow)
            c.ellipse(x, y, radius * .35f, radius * .35f, 0f, pal.spark)
        }
        s.at(IMPACT) {
            s.particle("circle_02", pal.glow, t.x, t.y, s0 = 24f * k, s1 = 140f * k, life = 580f, a0 = .85f)
            repeat(10) { i ->
                val angle = i * PI.toFloat() / 5f
                s.particle(x = t.x, y = t.y, vx = cos(angle) * 120f * k, vy = sin(angle) * 100f * k,
                    life = 520f, s0 = 10f * k, s1 = 5f * k, rot = angle, vrot = 5f,
                    draw = FxParticleDraw { c, size, _, _ -> drawLeaf(c, 0f, 0f, size, 0f, pal.glow, pal.spark) })
            }
        }
        s.proc(IMPACT, IMPACT + 580f) { c, p, ms ->
            c.alpha = (1f - p) * .7f
            val size = (50f + p * 115f) * k * u
            c.tex("circle_05", pal.glow, t.x, t.y, size, size)
            for (i in 0 until 10) {
                val angle = i * PI.toFloat() / 5f
                val inner = (10f + p * 25f) * k * u
                val outer = (25f + p * 65f) * k * u
                c.line(t.x + cos(angle) * inner, t.y + sin(angle) * inner,
                    t.x + cos(angle) * outer, t.y + sin(angle) * outer, (1f - p) * 3f * u, pal.spark)
            }
            repeat(3) { i ->
                c.save()
                c.translate(t.x, t.y)
                c.rotate(ms / 180f + i)
                c.scale(1f, .55f)
                c.tex("circle_02", pal.glow, 0f, 0f, size, size)
                c.restore()
            }
        }
        s.hit(IMPACT, 320f, push = 12f)
        return FxTimes(TOTAL, IMPACT, if (s.react) IMPACT + 320f else IMPACT)
    }
}
