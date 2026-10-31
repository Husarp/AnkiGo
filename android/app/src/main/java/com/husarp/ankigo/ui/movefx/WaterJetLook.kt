package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Water Gun (docs/design/attack-anims-v2/v2-fx.js `waterjet`, "B-style thick jet, redrawn by us and less white"): a wavy thick blue
 * jet shoots from the attacker to the target, holds, and drains back; a solid crown and sideways droplets splash where it lands. The size
 * factor k widens the jet and holds it longer. R22 pick B: 120 delay + 100 travel + 180 hold + 90 drain = 490 ms at k = 1.
 */
internal object WaterJetFx {
    /** Mockup ms: the jet starts at [S], its head needs [G] to reach the target, it drains over [OUT]; [BASE_HOLD] is the stream time at k = 1. */
    const val S = 120f
    const val G = 100f
    const val OUT = 90f
    const val BASE_HOLD = 180f
    const val RECOIL = 150f

    fun hold(k: Float): Float = BASE_HOLD * k
    fun end(k: Float): Float = S + G + hold(k) + OUT

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val sp = s.space
        val hold = hold(k)
        val end = end(k)
        val a = sp.fromAttacker(55f, -30f)
        val b = sp.fromTarget(-14f, 6f)
        val shape = WaterJetShape()
        s.tween(0f, RECOIL) { p, _ -> s.attacker.set(x = -12f * sin(p * 3.14f)) }
        s.proc(S, end) { c, _, ms ->
            shape.fill(a.x, a.y, b.x, b.y, sp.unit, sp.dir, k, hold, ms - S, ms)
            shape.draw(c, k, sp.unit, ms)
        }
        val hitAt = S + G
        // Pure script construction must not initialize Android native drawing paths.
        val crown by lazy { WaterCrownShape() }
        s.proc(hitAt, hitAt + 320f) { c, p, _ ->
            crown.draw(c, b.x, b.y, sp.unit * k, sp.dir, p)
        }
        s.at(hitAt) {
            repeat(10) { i ->
                val side = if (i % 2 == 0) 1f else -1f
                s.particle("drop", 0, b.x, b.y,
                    vx = side * (135f + i / 2 * 20f) * k, vy = -100f - (i / 2 % 4) * 35f, ay = 640f,
                    s0 = s.rnd(10f, 18f) * k, s1 = 5f, life = 520f, a0 = .95f, follow = true, stretch = 1.3f)
            }
        }
        val hitMs = hold + 140f
        s.hit(hitAt, hitMs, push = 16f)
        return FxTimes(end + 650f, hitAt, if (s.react) hitAt + hitMs else maxOf(hitAt, RECOIL))
    }
}

/**
 * The jet's centre line and widths at one moment (pure maths, buffers reused): [n] + 1 points along the wavy line from the attacker
 * to the target, [pts] as x, y pairs and [ws] the width at each point, all in pixels. [fill] computes, [draw] paints the passes.
 */
internal class WaterJetShape(val n: Int = 30) {
    @JvmField val pts = FloatArray((n + 1) * 2)
    @JvmField val ws = FloatArray(n + 1)
    private var nx = 0f
    private var ny = 0f
    private var angle = 0f

    /**
     * [ms] = mockup ms since the jet started, [now] = mockup ms since the animation began (the waves' phase), [dir] = +1 / -1 (the
     * normal flips with it so the light stays on the upper side), [hold] = the stream time.
     */
    fun fill(ax: Float, ay: Float, bx: Float, by: Float, unit: Float, dir: Float, k: Float, hold: Float, ms: Float, now: Float) {
        val dx = bx - ax
        val dy = by - ay
        val d = hypot(dx, dy).let { if (it < 1e-3f) 1f else it }
        nx = -dy / d * dir
        ny = dx / d * dir
        angle = atan2(dy, dx)
        val head = min(1f, ms / WaterJetFx.G)
        val tail = if (ms > WaterJetFx.G + hold) FxEase.IN2.at(min(1f, (ms - WaterJetFx.G - hold) / WaterJetFx.OUT)) else 0f
        for (i in 0..n) {
            val q = FxMath.lerp(tail, head, i.toFloat() / n)
            val env = sin(min(1f, q * 1.4f) * PI.toFloat() / 2f)
            val wv = sin(q * 11f - now / 40f) * 3.5f * k * env * unit
            pts[i * 2] = ax + dx * q + nx * wv
            pts[i * 2 + 1] = ay + dy * q + ny * wv - sin(q * PI.toFloat()) * 10f * unit
            ws[i] = (14f + 12f * q) * k * (1f + .1f * sin(q * 20f - now / 30f)) * unit
        }
    }

    /** One pass: every segment as a round-capped line of its width x [scale] (+ [extra] px), shifted sideways by [off] x its width. */
    private fun pass(c: FxCanvas, scale: Float, extra: Float, color: Int, a: Float, off: Float) {
        for (i in 0 until n) {
            c.line(
                pts[i * 2] + nx * off * ws[i], pts[i * 2 + 1] + ny * off * ws[i],
                pts[i * 2 + 2] + nx * off * ws[i + 1], pts[i * 2 + 3] + ny * off * ws[i + 1],
                ws[i] * scale + extra, color, a,
            )
        }
    }

    fun draw(c: FxCanvas, k: Float, unit: Float, now: Float) {
        // the dark body with a blue halo (the mockup's shadowBlur, as two wider, fainter passes)
        pass(c, 1.25f, 10f * unit, GLOW, .2f, 0f)
        pass(c, 1.25f, 5f * unit, GLOW, .3f, 0f)
        pass(c, 1.25f, 0f, DARK, .9f, 0f)
        pass(c, .95f, 0f, MID, 1f, 0f)
        pass(c, .55f, 0f, LIGHT, 1f, -.1f)
        pass(c, .18f, 0f, HIGH, .9f, -.22f)
        // a few moving glints instead of a white core
        for (g in 0 until 4) {
            val gi = ((now / 9f + g * 7.5f) % n).toInt()
            if (gi >= n) continue
            c.ellipse(pts[gi * 2] - nx * ws[gi] * .2f, pts[gi * 2 + 1] - ny * ws[gi] * .2f, 3.2f * k * unit, 1.6f * k * unit, angle, GLINT, .75f)
        }
    }

    private companion object {
        const val GLOW = 0xFF2F8FE8.toInt()
        const val DARK = 0xFF1A5FB8.toInt()
        const val MID = 0xFF2F8BE3.toInt()
        const val LIGHT = 0xFF5FB6FF.toInt()
        const val HIGH = 0xFFBFE6FF.toInt()
        const val GLINT = 0xFFE8F7FF.toInt()
    }
}

/** Pick B: solid scalloped crown, centred at contact, with a curved pale highlight. */
internal class WaterCrownShape {
    private val body = android.graphics.Path().apply {
        moveTo(0f, 13f); quadTo(-34f, 17f, -48f, -17f)
        lineTo(-28f, -6f); lineTo(-29f, -30f); lineTo(-10f, -10f)
        lineTo(0f, -36f); lineTo(12f, -10f); lineTo(31f, -30f)
        lineTo(28f, -5f); lineTo(49f, -17f); quadTo(35f, 19f, 0f, 13f); close()
    }
    private val shine = android.graphics.Path().apply {
        moveTo(-36f, -2f); quadTo(0f, 24f, 38f, -2f)
    }
    fun draw(c: FxCanvas, x: Float, y: Float, unit: Float, dir: Float, progress: Float) {
        val scale = if (progress < .375f) .15f + .85f * progress / .375f else 1f + .2f * (progress - .375f) / .625f
        val opacity = if (progress < .375f) 1f else (1f - progress) / .625f
        c.save(); c.translate(x, y); c.scale(unit * scale * dir, unit * scale)
        val paint = c.paint
        paint.reset(); paint.isAntiAlias = true; paint.color = 0xFF5FB6FF.toInt(); paint.alpha = (opacity * 255).toInt()
        c.raw.drawPath(body, paint)
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0xFF1A5FB8.toInt(); paint.alpha = (opacity * 255).toInt()
        c.raw.drawPath(body, paint)
        paint.strokeWidth = 2f; paint.color = 0xFFBFE6FF.toInt(); paint.alpha = (opacity * 255).toInt()
        c.raw.drawPath(shine, paint)
        c.restore()
    }
}
