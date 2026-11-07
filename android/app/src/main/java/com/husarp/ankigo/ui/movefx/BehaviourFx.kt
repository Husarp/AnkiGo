package com.husarp.ankigo.ui.movefx

import kotlin.math.sin

/** PLAN 248: the picked behaviour templates. HP numbers still come from actual battle events, never estimates. */
internal object BehaviourFx {
    private val green = 0xFF5FE08B.toInt()
    private val white = 0xFFFFFFFF.toInt()
    const val HEAL_AT = 150f
    const val DRAIN_HIT_AT = 460f
    const val DRAIN_HEAL_AT = 990f
    private const val HEAL_GLOW_MS = 600f

    private fun ring(s: FxScript, at: FxPt, time: Float, life: Float = 700f) {
        s.at(time) { s.particle("circle_02", green, at.x, at.y, s0 = 20f, s1 = 96f, life = life, a0 = .9f, add = true) }
    }

    private fun healing(s: FxScript, start: Float) {
        val a = s.space.fromAttacker(0f, 0f)
        ring(s, a, start)
        repeat(12) { i -> s.at(start + 50f + i * 70f) {
            val pos = s.space.fromAttacker(s.rnd(-22f, 22f), s.rnd(-4f, 16f))
            s.particle("star_04", if (i % 2 == 0) white else green, pos.x, pos.y,
                vx = s.rnd(-6f, 6f), vy = -34f, life = 760f, s0 = s.rnd(8f, 15f), s1 = 3f, add = true)
        } }
        // Green light and rising plus signs, with no words or move names in the arena.
        val tint = FxColor.identity()
        s.tween(start, HEAL_GLOW_MS) { p, _ ->
            val light = sin(p * Math.PI.toFloat())
            tint[6] = 1f + .25f * light; tint[9] = 24f * light
            s.attacker.set(filter = if (p < 1f) tint else null)
        }
        s.proc(start, start + 1100f) { c, p, _ ->
            c.alpha = .55f * sin(p * Math.PI.toFloat())
            c.additive = true
            c.tex("circle_05", green, a.x, a.y, 88f * s.u, 88f * s.u)
        }
        repeat(3) { i -> s.at(start + 150f + i * 260f) {
            val pos = s.space.fromAttacker(if (i == 0) -16f else if (i == 1) 14f else 0f, -4f - i * 3f)
            s.particle(x = pos.x, y = pos.y, vy = -22f, life = 1000f, s0 = 9f, s1 = 9f,
                draw = FxParticleDraw { c, size, _, _ ->
                    c.rect(-size / 2, -size / 7, size / 2, size / 7, green)
                    c.rect(-size / 7, -size / 2, size / 7, size / 2, green)
                })
        } }
    }

    fun heal(s: FxScript): FxTimes {
        healing(s, HEAL_AT)
        return FxTimes(2000f, HEAL_AT, HEAL_AT + HEAL_GLOW_MS)
    }

    fun drain(s: FxScript): FxTimes {
        fun orb(start: Float, duration: Float, back: Boolean) {
            s.proc(start, start + duration) { c, p, _ ->
                val q = if (back) 1f - p else p
                val pos = s.space.between(q, 0f, -sin(p * Math.PI.toFloat()) * if (back) 30f else 0f)
                val radius = (if (back) 16f - p * 4f else 13f) * s.u
                c.additive = true
                c.tex("circle_05", if (back) green else s.plan.palette.glow, pos.x, pos.y, radius * 5f, radius * 5f)
                c.additive = false // keep the green body visible on a bright arena
                c.ellipse(pos.x, pos.y, radius, radius, 0f, if (back) green else s.plan.palette.deep, .55f)
                c.ellipse(pos.x, pos.y, 7f * s.u, 7f * s.u, 0f, white, .9f)
            }
        }
        orb(100f, 360f, false)
        s.at(DRAIN_HIT_AT) { s.burst("star_04", s.plan.palette.glow, s.space.fromTarget(0f, 0f), 8, 90f, 380f, 18f, 4f, true) }
        s.hit(DRAIN_HIT_AT, 300f)
        orb(540f, 440f, true)
        healing(s, DRAIN_HEAL_AT)
        return FxTimes(2840f, DRAIN_HIT_AT, DRAIN_HEAL_AT + HEAL_GLOW_MS)
    }

    /** Preparation is its own event, so weather/item skips never play it. No displacement or target reaction. */
    fun charge(s: FxScript): FxTimes {
        val a = s.space.fromAttacker(0f, 0f)
        s.proc(0f, 900f) { c, p, _ ->
            c.alpha = sin(p * Math.PI.toFloat()) * .8f
            c.additive = true
            c.tex("circle_05", s.plan.palette.glow, a.x, a.y, (60f + p * 35f) * s.u, (60f + p * 35f) * s.u)
        }
        s.at(80f) { s.particle("circle_02", s.plan.palette.glow, a.x, a.y, s0 = 28f, s1 = 80f, life = 760f, add = true) }
        return FxTimes(1000f, 0f, 900f)
    }

    /** The following real faint event supplies the sacrificial fade and the HP event supplies its number. */
    fun blast(s: FxScript): FxTimes {
        val a = s.space.fromAttacker(0f, 0f)
        s.at(180f) {
            s.flashOnce(.8f, 80f)
            s.particle("circle_05", white, a.x, a.y, s0 = 24f, s1 = 220f, life = 520f, a0 = .95f, add = true)
            s.particle("circle_02", s.plan.palette.glow, a.x, a.y, s0 = 30f, s1 = 290f, life = 650f, add = true)
        }
        s.hit(180f, 300f)
        return FxTimes(900f, 180f, 480f)
    }

    // Shared with event playback: one damage float at each slash, at either playback pace.
    const val FIRST_HIT = 380f
    const val HIT_GAP = 250f
    const val RECOIL_AT = 700f
    fun hitTime(index: Int): Float = FIRST_HIT + index * HIT_GAP

    private fun impact(s: FxScript, at: FxPt, time: Float, slash: Boolean, alternate: Boolean = false) {
        val palette = s.plan.palette
        // Soft halo, solid tinted body and bright core keep the impact legible in both themes.
        s.proc(time, time + 300f) { c, p, _ ->
            val q = FxEase.OUT2.at(p)
            c.additive = true
            c.alpha = .5f * (1f - p) * (1f - p)
            val size = (96f + 88f * q) * s.u
            c.tex("circle_05", palette.glow, at.x, at.y, size, size)
        }
        s.at(time) {
            if (slash) {
                val angle = if (alternate) .5f else -.5f
                s.particle("slash_03", palette.deep, at.x, at.y, s0 = 112f, s1 = 132f,
                    life = 240f, rot = angle, a0 = .8f)
                s.particle("slash_03", palette.glow, at.x, at.y, s0 = 100f, s1 = 124f,
                    life = 210f, rot = angle)
                s.particle("slash_03", palette.spark, at.x, at.y, s0 = 84f, s1 = 112f,
                    life = 150f, rot = angle, add = true)
            } else {
                s.particle("star_04", palette.glow, at.x, at.y, s0 = 100f, s1 = 138f, life = 280f)
                s.particle("star_04", palette.spark, at.x, at.y, s0 = 58f, s1 = 100f, life = 180f, add = true)
            }
            s.particle("circle_02", palette.glow, at.x, at.y, s0 = 42f, s1 = 138f, life = 300f, a0 = .65f, add = true)
            s.burst("spark_01", palette.glow, at, 9, 180f, 320f, 24f, 3f, true)
            s.burst("star_04", palette.spark, at, 5, 120f, 420f, 14f, 1f, true)
        }
    }

    fun multi(s: FxScript): FxTimes {
        s.lunge(70f)
        repeat(s.plan.hitCount.coerceAtLeast(0)) { i ->
            val t = hitTime(i)
            impact(s, s.space.fromTarget(if (i % 2 == 0) -6f else 6f, (i % 3 - 1) * 5f),
                t, slash = true, alternate = i % 2 == 1)
            s.hit(t, 210f, push = 4f)
        }
        val last = hitTime((s.plan.hitCount - 1).coerceAtLeast(0))
        return FxTimes(last + 520f, FIRST_HIT, maxOf(680f, last + 220f))
    }

    fun recoil(s: FxScript): FxTimes {
        s.lunge(70f)
        impact(s, TackleFx.hitSpot(s), FIRST_HIT, slash = false)
        s.hit(FIRST_HIT, 300f, push = 12f)
        if (s.plan.showRecoil) {
            val at = s.space.fromAttacker(0f, -6f)
            val red = 0xFFFF6A5A.toInt()
            s.at(RECOIL_AT) {
                s.burst("spark_01", red, at, 10, 130f, 420f, 24f, 2f, true)
                s.burst("star_04", s.plan.palette.glow, at, 7, 90f, 480f, 16f, 1f, true)
                s.particle("circle_02", red, at.x, at.y, s0 = 40f, s1 = 112f, life = 360f, a0 = .6f, add = true)
            }
            s.proc(RECOIL_AT, RECOIL_AT + 360f) { c, p, _ ->
                c.additive = true; c.alpha = .5f * (1f - p) * (1f - p)
                c.tex("circle_05", red, at.x, at.y, 110f * s.u, 110f * s.u)
            }
            // Starts after the lunge returns. Flash and shake decay together to the exact rest pose.
            val tint = FxColor.identity()
            s.tween(RECOIL_AT, 440f) { p, _ ->
                val q = (1f - p) * (1f - p)
                tint[4] = 95f * q; tint[6] = 1f - .45f * q; tint[12] = 1f - .45f * q
                s.attacker.set(x = sin(p * 26f) * 6f * q, filter = if (p < 1f) tint else null)
            }
        }
        return FxTimes(1240f, FIRST_HIT, if (s.plan.showRecoil) 1140f else 680f)
    }
}
