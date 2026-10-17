package com.husarp.ankigo.ui.movefx

import kotlin.math.cos
import kotlin.math.sin

/**
 * The impact bursts of the physical template ([MoveLook.HIT_TEMPLATE]): one per [FxKit], each made of that type's own particles
 * (the page's fallback rule 2) or of the plan's palette (rule 3, [FxKit.PLAIN]). All of them start at the target's hit spot at
 * [atMs], scale with the plan's size factor and are over well within the template's 1.1 s. Also home of [darkBurst], the dark
 * "C" burst that Shadow Ball and the special template share.
 */
internal object FxKits {
    private const val PI2 = 6.2831855f

    /** Schedules the burst of [kit] at [hitSpot] (pixels) at [atMs] mockup ms. */
    fun schedule(s: FxScript, kit: FxKit, atMs: Float, hitSpot: FxPt) {
        when (kit) {
            FxKit.NORMAL -> s.at(atMs) { normal(s, hitSpot) }
            FxKit.FIRE -> fire(s, atMs, hitSpot)
            FxKit.WATER -> water(s, atMs, hitSpot)
            FxKit.ELECTRIC -> electric(s, atMs, hitSpot)
            FxKit.GRASS -> s.at(atMs) { grass(s, hitSpot) }
            FxKit.ICE -> s.at(atMs) { ice(s, hitSpot) }
            FxKit.PSYCHIC -> psychic(s, atMs, hitSpot)
            FxKit.GHOST -> s.at(atMs) { darkBurst(s, hitSpot, s.plan.palette, s.k, .8f) }
            FxKit.GROUND -> s.at(atMs) { ground(s, hitSpot) }
            FxKit.PLAIN -> s.at(atMs) { plain(s, hitSpot) }
        }
    }

    /** Tackle's own burst (the mockup's `tackle`): a yellow star, a white ring, five sparks, eight stars, a white flash. */
    fun normal(s: FxScript, at: FxPt) {
        val k = s.k
        s.particle("star_06", 0xFFFFF3B0.toInt(), at.x, at.y, s0 = 30f, s1 = 130f * k, life = 280f, add = true, rot = .3f)
        s.particle("circle_02", 0xFFFFFFFF.toInt(), at.x, at.y, s0 = 20f, s1 = 150f * k, life = 340f, add = true)
        s.burst("spark_01", 0xFFFFE27A.toInt(), at, 5, 160f, 320f, 70f * k, 30f, true)
        s.burst("star_04", 0xFFFFFFFF.toInt(), at, 8, 220f, 380f, 36f * k, 6f, true)
        s.flashOnce(.35f, 60f)
    }

    /** The plain template: Tackle's burst in the palette's colours (a deep body so it also shows on a bright arena). */
    private fun plain(s: FxScript, at: FxPt) {
        val k = s.k
        val pal = s.plan.palette
        s.particle("circle_05", pal.deep, at.x, at.y, s0 = 20f, s1 = 100f * k, life = 360f, a0 = .55f)
        s.particle("star_06", pal.glow, at.x, at.y, s0 = 30f, s1 = 130f * k, life = 280f, add = true, rot = .3f)
        s.particle("circle_02", pal.spark, at.x, at.y, s0 = 20f, s1 = 150f * k, life = 340f, add = true)
        s.burst("spark_01", pal.glow, at, 5, 160f, 320f, 70f * k, 30f, true)
        s.burst("star_04", pal.spark, at, 8, 220f, 380f, 36f * k, 6f, true)
        s.flashOnce(.2f, 60f)
    }

    /** Fire: Ember's small pixel flames burst outward, a warm glow and pixel embers (no flight). */
    private fun fire(s: FxScript, atMs: Float, at: FxPt) {
        val k = s.k
        val px = s.artPx(k)
        val dev = s.space.artPixels(k)
        s.at(atMs) {
            s.particle("circle_05", 0xFFFFB040.toInt(), at.x, at.y, s0 = 16f * k, s1 = 56f * k, life = 220f, a0 = .45f, add = true)
            for (i in 0 until 7) {
                val a = s.rnd(0f, PI2)
                val sp = s.rnd(60f, 150f) * k
                val vx = cos(a) * sp
                val right = vx * s.dir >= 0f
                val first = s.random.nextInt(6)
                val fps = s.rnd(60f, 85f)
                s.particle(
                    x = at.x, y = at.y, vx = vx, vy = sin(a) * sp - 30f, ay = -60f,
                    life = s.rnd(380f, 560f), s0 = 1f, a0 = 1f, a1 = .2f,
                    draw = FxParticleDraw { c, _, _, ms ->
                        val key = FLAME_SMALL[if (right) 0 else 1][((ms / fps).toInt() + first) % 6]
                        val hx = if (right) FxPixelArt.FLAME_HEAD_X else 22f - FxPixelArt.FLAME_HEAD_X
                        c.crisp(key, -hx * dev, -FxPixelArt.FLAME_HEAD_Y * dev, dev)
                    },
                )
            }
            for (j in 0 until 9) {
                s.particle(
                    x = at.x + s.space.len(s.rnd(-8f, 8f)), y = at.y + s.space.len(s.rnd(-8f, 6f)),
                    vx = s.rnd(-110f, 110f) * k, vy = s.rnd(-170f, -40f) * k, ay = 280f,
                    life = s.rnd(350f, 600f), s0 = px * (if (s.chance(.35f)) 2f else 1f), a1 = .4f, pix = FxPixelArt.EMBER_COLORS,
                )
            }
        }
    }

    /** Water: a droplet splash and two rings (the Water Gun jet's landing). */
    private fun water(s: FxScript, atMs: Float, at: FxPt) {
        val k = s.k
        fun drop() {
            s.particle(
                "drop", 0, at.x + s.space.len(s.rnd(-10f, 10f)), at.y + s.space.len(s.rnd(-12f, 12f)),
                vx = s.rnd(-1f, 1f) * 200f * k, vy = s.rnd(-190f, -40f), ay = 640f,
                s0 = s.rnd(10f, 18f) * k, s1 = 5f, life = 520f, a0 = .95f, follow = true, stretch = 1.3f,
            )
        }
        s.at(atMs) {
            s.particle("circle_02", 0xFF6DBDFF.toInt(), at.x, at.y, s0 = 24f, s1 = 140f * k, life = 400f, add = true)
            repeat(5) { drop() }
        }
        s.ticks(atMs, 150f) { _, _ -> drop() }
        s.at(atMs + 200f) { s.particle("circle_02", 0xFF6DBDFF.toInt(), at.x, at.y, s0 = 24f, s1 = 140f * k, life = 400f, add = true) }
    }

    /** Electric: yellow sparks and a flash star (Thunderbolt's landing). */
    private fun electric(s: FxScript, atMs: Float, at: FxPt) {
        val k = s.k
        s.at(atMs) {
            s.flashOnce(.45f, 70f)
            s.particle("star_06", 0xFFFFF7B0.toInt(), at.x, at.y, s0 = 40f, s1 = 170f * k, life = 360f, add = true)
        }
        s.ticks(atMs, 330f) { _, _ ->
            if (s.chance(.5f)) s.particle("spark_05", 0xFFFFE84A.toInt(), at.x + s.space.len(s.rnd(-40f, 40f) * k), at.y + s.space.len(s.rnd(-30f, 40f)),
                s0 = s.rnd(30f, 60f) * k, s1 = 20f, life = 140f, rot = s.rnd(-.5f, .5f), add = true)
            if (s.chance(.35f)) s.particle("spark_01", 0xFFFFF2A0.toInt(), at.x + s.space.len(s.rnd(-40f, 40f) * k), at.y + s.space.len(s.rnd(-30f, 40f)),
                s0 = s.rnd(40f, 70f) * k, s1 = 30f, life = 160f, rot = s.rnd(0f, 6f), add = true)
        }
    }

    /** Grass: pixel leaves tumble out, a few dirt bits. */
    private fun grass(s: FxScript, at: FxPt) {
        val k = s.k
        val dev = s.space.artPixels(k * 1.3f)
        for (i in 0 until 8) {
            val phase = s.random.nextInt(4)
            val rate = s.rnd(90f, 130f)
            s.particle(
                x = at.x + s.space.len(s.rnd(-8f, 8f)), y = at.y + s.space.len(s.rnd(-8f, 8f)),
                vx = s.rnd(-130f, 130f) * k, vy = s.rnd(-210f, -60f) * k, ay = 380f,
                life = s.rnd(520f, 780f), s0 = 1f, a1 = .3f,
                draw = FxParticleDraw { c, _, _, ms ->
                    val key = LEAF_KEYS[((ms / rate).toInt() + phase) % 4]
                    c.crisp(key, -4.5f * dev, -3f * dev, dev)
                },
            )
        }
        val px = s.artPx(k)
        for (i in 0 until 4) {
            s.particle(
                x = at.x + s.space.len(s.rnd(-9f, 9f)), y = at.y + s.space.len(2f),
                vx = s.rnd(-70f, 70f), vy = s.rnd(-190f, -80f), ay = 700f,
                life = s.rnd(420f, 600f), s0 = px * (if (i % 3 != 0) 1f else 2f), a1 = 1f, pix = intArrayOf(FxPixelArt.DIRT_COLORS[i % 3]),
            )
        }
    }

    /** Ice: frost stars, a ring and small tumbling shards. */
    private fun ice(s: FxScript, at: FxPt) {
        val k = s.k
        s.particle("circle_02", 0xFFBDF0FF.toInt(), at.x, at.y, s0 = 40f, s1 = 160f * k, life = 380f, add = true)
        s.burst("star_04", 0xFFDFF8FF.toInt(), at, 10, 200f, 500f, 26f, 3f, true)
        for (i in 0 until 8) {
            val sh = floatArrayOf(-.5f, -.3f, .5f, -.45f, .15f, .55f)
            for (j in sh.indices) sh[j] += s.rnd(-.2f, .2f)
            val tmp = FloatArray(6)
            val sz = s.rnd(10f, 22f) * k
            val a = s.rnd(0f, PI2)
            val sp = s.rnd(60f, 200f) * k
            s.particle(
                x = at.x + s.space.len(s.rnd(-10f, 10f)), y = at.y + s.space.len(s.rnd(-10f, 10f)),
                vx = cos(a) * sp, vy = sin(a) * sp - s.rnd(60f, 160f), ay = 620f,
                life = 700f, s0 = sz, s1 = sz * .7f, rot = s.rnd(0f, 6f), vrot = s.rnd(-9f, 9f),
                draw = FxParticleDraw { c, size, _, _ ->
                    for (j in 0 until 6) tmp[j] = sh[j] * size
                    c.fillPoly(tmp, 0xFFBEEBFF.toInt(), .75f)
                    c.strokePoly(tmp, 1.2f * c.unit / 2f, 0xFFFFFFFF.toInt(), .95f)
                },
            )
        }
    }

    /** Psychic: a pink ring closes in on the spot, a twirl and a few magic sparkles. */
    private fun psychic(s: FxScript, atMs: Float, at: FxPt) {
        val k = s.k
        s.at(atMs) {
            s.particle("circle_02", 0xFFFF6AD5.toInt(), at.x, at.y, s0 = 160f * k, s1 = 30f, life = 420f, add = true, a0 = 0f, a1 = 1f)
            s.particle("twirl_01", 0xFFD58AFF.toInt(), at.x, at.y, s0 = 60f, s1 = 150f * k, life = 560f, add = true, vrot = -6f, a0 = .9f)
        }
        s.ticks(atMs, 300f, 50f) { _, _ ->
            s.particle("magic_05", 0xFFFFB0F0.toInt(), at.x + s.space.len(s.rnd(-60f, 60f)), at.y + s.space.len(s.rnd(-50f, 50f)),
                s0 = s.rnd(10f, 26f), s1 = 2f, life = 380f, add = true)
        }
    }

    /** Ground: dirt clods thrown up and a dust cloud. */
    private fun ground(s: FxScript, at: FxPt) {
        val k = s.k
        for (i in 0 until 7) {
            s.particle(
                if (s.chance(.5f)) "dirt_01" else "dirt_02", 0xFF9A7448.toInt(),
                at.x + s.space.len(s.rnd(-22f, 22f)), at.y + s.space.len(s.rnd(-4f, 8f)),
                vx = s.rnd(-90f, 90f) * k, vy = s.rnd(-200f, -80f) * k, ay = 480f,
                s0 = s.rnd(16f, 34f) * k, s1 = s.rnd(10f, 22f) * k, life = 700f, rot = s.rnd(0f, 6f), vrot = s.rnd(-4f, 4f), a0 = .95f,
            )
        }
        repeat(2) { i -> s.particle("smoke_08", 0xFF8B7355.toInt(), at.x + s.space.len((i * 2 - 1) * 14f), at.y + s.space.len(14f), s0 = 40f, s1 = 130f * k, life = 700f, a0 = .45f) }
    }

    /**
     * The dark "C" burst (the mockup's `darkHit`, particles only): a deep ring, a glowing ring, eight smoke puffs and eight stars
     * in [pal]'s colours, sized by [k]; every life is multiplied with [life] (the smoke lives up to 0.84 s at 1: shorten it when
     * the look ends soon after). Shadow Ball ends with it and the ghost / special templates reuse it.
     */
    fun darkBurst(s: FxScript, at: FxPt, pal: FxPalette, k: Float, life: Float = 1f) {
        s.particle("circle_05", pal.deep, at.x, at.y, s0 = 40f * k, s1 = 190f * k, life = 420f * life, a0 = 1f)
        s.particle("circle_05", pal.glow, at.x, at.y, s0 = 30f * k, s1 = 200f * k, life = 380f * life, a0 = .6f, add = true)
        s.burst("smoke_08", pal.smoke, at, 8, 160f * k, 700f * life, 50f * k, 100f * k, false)
        s.burst("star_04", pal.spark, at, 8, 220f * k, 500f * life, 30f * k, 4f, true)
    }

    private val FLAME_SMALL: Array<Array<String>> = Array(2) { f -> Array(6) { i -> "flame_small_$i" + if (f == 1) "-x" else "" } }
    private val LEAF_KEYS = arrayOf("leaf", "leaf-x", "leaf-xy", "leaf-y")
}
