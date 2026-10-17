package com.husarp.ankigo.ui.movefx

import kotlin.math.sin

/**
 * Psychic (the mockup's `psychic`, "C"): the attacker floats and glows pink, three waves of a ring, a light and a twirl close in on
 * the target, magic sparkles, and the target wobbles and swings its hue.
 *
 * The mockup's CSS `drop-shadow` glow cannot be a colour matrix, so the glow is a soft `circle_05` (pulsing, drawn under the
 * particles at the attacker) plus a light pink tint of the sprite ([Glow.tint], strength pulsing too).
 */
internal object PsychicFx {
    const val FIRST_RING = 100f
    const val FLOAT_MS = 1100f
    const val TOTAL = 1500f

    private const val PINK = 0xFFFF6AD5.toInt()
    private const val LIGHT_PINK = 0xFFFF9AE8.toInt()
    private const val VIOLET = 0xFFD58AFF.toInt()
    private const val SPARKLE = 0xFFFFB0F0.toInt()

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val b = s.space.fromTarget(0f, 0f)
        val glow = Glow(PINK, LIGHT_PINK)
        glow.float(s, FLOAT_MS)

        for (t0 in floatArrayOf(0f, 260f, 520f)) {
            s.at(t0 + FIRST_RING) {
                s.particle("circle_02", PINK, b.x, b.y, s0 = 160f * k, s1 = 30f, life = 420f, add = true, a0 = 0f, a1 = 1f)
                s.particle("light_02", 0xFFFF9AE8.toInt(), b.x, b.y, s0 = 40f, s1 = 170f * k, life = 520f, add = true, vrot = 5f, a0 = .9f)
                s.particle("twirl_01", VIOLET, b.x, b.y, s0 = 60f, s1 = 150f * k, life = 560f, add = true, vrot = -6f, a0 = .9f)
            }
        }
        s.ticks(100f, 900f) { _, _ ->
            s.particle("magic_05", SPARKLE, b.x + s.space.len(s.rnd(-60f, 60f)), b.y + s.space.len(s.rnd(-50f, 50f)),
                s0 = s.rnd(10f, 26f), s1 = 2f, life = 380f, add = true)
        }

        // the target's wobble: sideways shake, a squash and stretch, the hue swinging, all fading out
        val f = FxColor.Chain()
        s.tween(100f, 1000f) { p, ms ->
            if (p >= 1f) { s.target.reset(); return@tween }
            val q = 1f - p
            val w = sin(ms / 55f) * .04f * q
            s.target.set(
                x = sin(ms / 30f) * 3f * q, sx = 1f + w, sy = 1f - w,
                filter = f.reset().hueRotate(sin(ms / 90f) * 80f).brightness(1.2f).matrix,
            )
        }
        return FxTimes(TOTAL, FIRST_RING, FLOAT_MS)
    }

    /**
     * The glowing float of the attacker, shared by Psychic and the status template: bobs up and down, pulses a soft glow of
     * [glowColor] under the particles and tints the sprite toward [tintColor].
     */
    class Glow(private val glowColor: Int, private val tintColor: Int) {
        private val m = FxColor.identity()

        /** Floats the attacker for [ms] mockup ms from [t0]; the glow disc and the tint pulse as the mockup's blur did (6..12 px at 120 ms). */
        fun float(s: FxScript, ms: Float, t0: Float = 0f) {
            val u = s.u
            s.tween(t0, ms) { p, now ->
                if (p >= 1f) { s.attacker.reset(); return@tween }
                s.attacker.set(y = -sin(now / 90f) * 3f, filter = tint(.12f + .1f * (.5f + .5f * sin(now / 120f))))
            }
            val a = s.space.fromAttacker(0f, 0f)
            s.proc(t0, t0 + ms) { c, p, now ->
                val pulse = .5f + .5f * sin(now / 120f)
                val fade = if (p > .85f) (1f - p) / .15f else 1f
                val size = (112f + 24f * pulse) * u
                c.alpha = .55f * fade
                c.additive = true
                c.tex("circle_05", glowColor, a.x, a.y, size, size)
            }
        }

        /** A matrix that moves every colour [strength] of the way toward [tintColor] (a light tint, 0 = none). */
        fun tint(strength: Float): FloatArray {
            val r = ((tintColor shr 16) and 0xFF).toFloat()
            val g = ((tintColor shr 8) and 0xFF).toFloat()
            val b = (tintColor and 0xFF).toFloat()
            m[0] = 1f - strength; m[4] = r * strength
            m[6] = 1f - strength; m[9] = g * strength
            m[12] = 1f - strength; m[14] = b * strength
            return m
        }
    }
}

/**
 * The status template ([MoveLook.AURA_TEMPLATE], fallback rules 2 / 3): Psychic's motion around the USER instead of the target, in
 * the plan's palette (glow / spark / deep instead of the pinks), with a few particles of the plan's type kit rising around the user.
 * Never poses or filters the target (a status move deals no damage). About 1.0 s.
 */
internal object AuraFx {
    const val IMPACT = 100f
    const val FLOAT_MS = 900f
    const val TOTAL = 1000f

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val u = s.u
        val pal = s.plan.palette
        val a = s.space.fromAttacker(0f, 0f)
        PsychicFx.Glow(pal.glow, pal.glow).float(s, FLOAT_MS)
        // a soft dark base under the glow, so the aura also shows on a bright arena
        s.proc(0f, FLOAT_MS) { c, p, _ ->
            val fade = if (p > .85f) (1f - p) / .15f else 1f
            c.alpha = .28f * fade
            c.tex("circle_05", pal.deep, a.x, a.y, 150f * u, 150f * u)
        }
        for (t0 in floatArrayOf(0f, 170f, 340f)) {
            s.at(t0) {
                s.particle("circle_02", pal.glow, a.x, a.y, s0 = 160f * k, s1 = 30f, life = 420f, add = true, a0 = 0f, a1 = 1f)
                s.particle("light_02", pal.spark, a.x, a.y, s0 = 40f, s1 = 170f * k, life = 520f, add = true, vrot = 5f, a0 = .9f)
                s.particle("twirl_01", pal.glow, a.x, a.y, s0 = 60f, s1 = 150f * k, life = 560f, add = true, vrot = -6f, a0 = .9f)
            }
        }
        s.ticks(60f, 500f) { _, _ ->
            s.particle("magic_05", pal.spark, a.x + s.space.len(s.rnd(-60f, 60f)), a.y + s.space.len(s.rnd(-50f, 50f)),
                s0 = s.rnd(10f, 26f), s1 = 2f, life = 380f, add = true)
        }
        if (s.plan.kit != FxKit.PLAIN) {
            s.ticks(120f, 400f, 50f) { _, _ -> if (s.chance(.65f)) rise(s, s.plan.kit, a) }
        }
        return FxTimes(TOTAL, IMPACT, FLOAT_MS)
    }

    /** One particle of [kit] rising from around the user at [a] (each lives at most 450 ms, so the last one is gone by 0.97 s). */
    private fun rise(s: FxScript, kit: FxKit, a: FxPt) {
        val x = a.x + s.space.len(s.rnd(-45f, 45f))
        val y = a.y + s.space.len(s.rnd(5f, 50f))
        val vy = -s.rnd(60f, 130f)
        val dev = s.space.artPixels(1f)
        when (kit) {
            FxKit.NORMAL -> s.particle("star_04", 0xFFFFFFFF.toInt(), x, y, vy = vy, s0 = s.rnd(14f, 24f), s1 = 3f, life = 420f, add = true)
            FxKit.FIRE -> s.particle(
                x = x, y = y, vy = vy, life = s.rnd(380f, 450f), s0 = 1f, a1 = .2f,
                draw = FxParticleDraw { c, _, _, ms -> c.crisp("flame_small_${(ms / 70f).toInt() % 6}", -FxPixelArt.FLAME_HEAD_X * dev, -FxPixelArt.FLAME_HEAD_Y * dev, dev) },
            )
            FxKit.WATER -> s.particle("drop", 0, x, y, vy = vy * 1.2f, ay = 240f, s0 = s.rnd(10f, 16f), s1 = 5f, life = 440f, a0 = .95f, follow = true, stretch = 1.3f)
            FxKit.ELECTRIC -> s.particle("spark_05", 0xFFFFE84A.toInt(), x, y, vy = vy * .5f, s0 = s.rnd(26f, 46f), s1 = 14f, life = 200f, rot = s.rnd(-.5f, .5f), add = true)
            FxKit.GRASS -> {
                val phase = s.random.nextInt(4)
                s.particle(
                    x = x, y = y, vx = s.rnd(-30f, 30f), vy = vy, life = 450f, s0 = 1f, a1 = .3f,
                    draw = FxParticleDraw { c, _, _, ms -> c.crisp(LEAF_KEYS[((ms / 110f).toInt() + phase) % 4], -4.5f * dev, -3f * dev, dev) },
                )
            }
            FxKit.ICE -> s.particle("star_04", 0xFFDFF8FF.toInt(), x, y, vy = vy * .7f, s0 = s.rnd(14f, 26f), s1 = 3f, life = 450f, add = true)
            FxKit.PSYCHIC -> s.particle("magic_05", 0xFFFFB0F0.toInt(), x, y, vy = vy, s0 = s.rnd(14f, 26f), s1 = 3f, life = 420f, add = true)
            FxKit.GHOST -> s.particle("ps:purplewisp", 0, x, y, vy = vy * .8f, s0 = s.rnd(24f, 40f), s1 = 10f, life = 450f, a0 = .75f)
            FxKit.GROUND -> s.particle(
                if (s.chance(.5f)) "dirt_01" else "dirt_02", 0xFF9A7448.toInt(), x, a.y + s.space.len(40f),
                vx = s.rnd(-40f, 40f), vy = -s.rnd(110f, 160f), ay = 560f, s0 = s.rnd(12f, 22f), s1 = 8f, life = 440f, rot = s.rnd(0f, 6f), vrot = s.rnd(-4f, 4f), a0 = .95f,
            )
            FxKit.PLAIN -> Unit
        }
    }

    private val LEAF_KEYS = arrayOf("leaf", "leaf-x", "leaf-xy", "leaf-y")
}
