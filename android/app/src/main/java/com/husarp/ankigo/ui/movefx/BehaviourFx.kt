package com.husarp.ankigo.ui.movefx

import kotlin.math.sin

/** PLAN 248: the four picked demonstrations. HP numbers still come from actual battle events, never estimates. */
internal object BehaviourFx {
    private val green = 0xFF5FE08B.toInt()
    private val white = 0xFFFFFFFF.toInt()

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
        s.tween(start, 1000f) { p, _ ->
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
        healing(s, 150f)
        return FxTimes(2000f, 1000f, 1150f)
    }

    fun drain(s: FxScript): FxTimes {
        fun orb(start: Float, duration: Float, back: Boolean) {
            s.proc(start, start + duration) { c, p, _ ->
                val q = if (back) 1f - p else p
                val pos = s.space.between(q, 0f, -sin(p * Math.PI.toFloat()) * if (back) 30f else 0f)
                val radius = (if (back) 16f - p * 4f else 13f) * s.u
                c.additive = true
                c.tex("circle_05", green, pos.x, pos.y, radius * 5f, radius * 5f)
                c.additive = false // keep the green body visible on a bright arena
                c.ellipse(pos.x, pos.y, radius, radius, 0f, green, .55f)
                c.ellipse(pos.x, pos.y, 7f * s.u, 7f * s.u, 0f, white, .9f)
            }
        }
        orb(150f, 420f, false)
        s.at(570f) { s.burst("star_04", green, s.space.fromTarget(0f, 0f), 8, 90f, 380f, 18f, 4f, true) }
        s.hit(570f, 300f)
        orb(740f, 640f, true)
        healing(s, 1390f)
        return FxTimes(3240f, 570f, 2390f)
    }

    fun multi(s: FxScript): FxTimes {
        s.lunge(100f)
        repeat(4) { i ->
            val t = 380f + i * 170f
            s.at(t) {
                val at = s.space.fromTarget(if (i % 2 == 0) -4f else 4f, (i - 1) * 3f)
                s.particle("slash_03", white, at.x, at.y, s0 = 46f, s1 = 58f, life = 260f, rot = -.6f + i * .5f)
                s.burst("spark_01", s.plan.palette.glow, at, 5, 110f, 190f, 24f, 6f, true)
            }
            s.hit(t, 170f, push = 4f)
        }
        return FxTimes(1300f, 380f, 1080f)
    }

    fun recoil(s: FxScript): FxTimes {
        s.lunge(70f)
        s.at(380f) { FxKits.normal(s, TackleFx.hitSpot(s)) }
        s.hit(380f, 300f, push = 12f)
        val red = FxColor.identity().also { it[4] = 80f; it[6] = .6f; it[12] = .6f }
        s.at(520f) { s.burst("spark_01", 0xFFFF6A5A.toInt(), s.space.fromAttacker(6f, -8f), 6, 110f, 340f, 20f, 6f) }
        // Added after the lunge so the return no longer overwrites the recoil shake.
        s.tween(520f, 520f) { p, _ -> s.attacker.set(x = sin(p * 30f) * 5f * (1f - p), filter = if (p < 1f) red else null) }
        return FxTimes(1400f, 380f, 1040f)
    }
}
