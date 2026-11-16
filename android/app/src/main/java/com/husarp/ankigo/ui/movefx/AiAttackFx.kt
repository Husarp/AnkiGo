package com.husarp.ankigo.ui.movefx

import kotlin.math.*

/** PLAN 705: the approved round 2–5 sheets and code motion, in design-stage units. */
internal class AiAttackFx private constructor(private val s: FxScript) {
    private val dx = s.space.towardX
    private val dy = s.space.towardY
    private val lx = 55f
    private val ly = -30f
    private val parts = ArrayList<Part>()
    private val color = FxColor.Chain()
    private fun r(a: Float = 0f, b: Float = 1f) = s.rnd(a, b)
    private fun cl(p: Float) = p.coerceIn(0f, 1f)
    private fun out(p: Float) = 1f - (1f - p).pow(2)
    private fun io(p: Float) = FxEase.INOUT.at(p)
    private fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
    private fun shake(t: Float, start: Float, dur: Float, amp: Float, push: Float = 0f): Float {
        val p = (t - start) / dur
        return if (p !in 0f..1f) 0f else sin(p * 38f) * amp * (1f - p) + push * sin(p * PI.toFloat())
    }

    private class Part(val sheet: AiSheet, val frame: Int, val start: Float, val life: Float,
        val x: Float, val y: Float, val vx: Float, val vy: Float, val gravity: Float, val scale: Float,
        val rotation: Float, val spin: Float, val add: Boolean, val width: Float = sheet.w, val height: Float = sheet.h,
        val splash: Boolean = false)

    private fun part(sheet: AiSheet, frame: Int, start: Float, life: Float, x: Float, y: Float,
        vx: Float = 0f, vy: Float = 0f, gravity: Float = 0f, scale: Float = 1f, add: Boolean = false) {
        parts += Part(sheet, frame, start, life, x, y, vx, vy, gravity, scale, r(0f, 6f), r(-4f, 4f), add)
    }

    private fun burst(sheet: AiSheet, frames: IntArray, start: Float, x: Float, y: Float, count: Int,
        low: Float, high: Float, gravity: Float, scale: Float, life: Float, add: Boolean = false) {
        repeat(count) { i ->
            val a = r(0f, 6.28f); val v = r(low, high)
            part(sheet, frames[i % frames.size], start, life + r(0f, 150f), x, y,
                cos(a) * v, sin(a) * v - 30f, gravity, scale * r(.8f, 1.3f), add)
        }
    }

    private fun drawParts(c: FxCanvas, t: Float) {
        for (i in 0 until parts.size) {
            val p = parts[i]; val u = (t - p.start) / p.life
            if (u !in 0f..1f) continue
            val sec = (t - p.start) / 1000f
            val fade = if (p.splash) .55f else .65f
            val alpha = if (u < fade) 1f else (1f - u) / (1f - fade)
            val grow = if (p.splash && u < .12f) .55f + .45f * u / .12f else 1f
            c.ai(p.sheet, p.frame, p.x + p.vx * sec, p.y + p.vy * sec + .5f * p.gravity * sec * sec,
                p.scale * grow, alpha * if (p.splash) .95f else 1f, p.add, p.rotation + p.spin * sec,
                w = p.width, h = p.height)
        }
    }

    private fun proc(end: Float, draw: FxDraw) {
        s.proc(0f, end) { c, _, t ->
            c.translate(s.space.attackerX, s.space.attackerY)
            c.scale(s.dir * s.u, s.u)
            draw.draw(c, 0f, t)
        }
    }

    private fun flash(c: FxCanvas, alpha: Float, color: Int = 0xFFFFFF) {
        if (alpha <= 0f) return
        c.raw.drawColor((cl(alpha) * 255f).roundToInt().shl(24) or color)
    }

    private fun interface Filter { fun at(p: Float, t: Float): FloatArray? }

    private fun filters(start: Float, dur: Float, attacker: Boolean = false, filter: Filter): Array<FloatArray?> {
        val frames = ceil(dur / (1000f / 60f)).toInt().coerceAtLeast(1)
        return Array(frames + 1) { i ->
            if (i == frames) null else filter.at(i.toFloat() / frames, start + dur * i / frames)?.copyOf()?.also { s.prepareFilter(attacker, it) }
        }
    }

    private fun filterAt(filters: Array<FloatArray?>, p: Float): FloatArray? = filters[(p * filters.lastIndex).roundToInt().coerceIn(0, filters.lastIndex)]

    private fun reaction(start: Float, dur: Float, amp: Float, push: Float = 0f,
        filter: Filter = Filter { _, _ -> null }) {
        val prepared = filters(start, dur, filter = filter)
        s.tween(start, dur) { p, t -> s.target.set(shake(t, start, dur, amp, push), filter = filterAt(prepared, p)) }
    }

    private fun projectile(c: FxCanvas, sheet: AiSheet, p: Float, bx: Float, by: Float, bend: Float,
        scale: Float, alpha: Float = 1f, rot: Float = Float.NaN) {
        val x = mix(lx, bx, p); val y = mix(ly, by, p) - bend * sin(p * PI.toFloat())
        val angle = if (!rot.isNaN()) rot else atan2(by - ly - bend * PI.toFloat() * cos(p * PI.toFloat()), bx - lx)
        c.ai(sheet, 0, x, y, scale * s.k, alpha, rot = angle)
    }

    private class Jet(val start: Float, val duration: Float, val x: Float, val y: Float, val bend: Float, val scale: Float)

    private fun stream(water: Boolean): FxTimes {
        val jets = ArrayList<Jet>()
        val pj = if (water) AiSheet.WATER_PROJ2_A else AiSheet.FIRE_PROJ_BX
        val pt = if (water) AiSheet.WATER_PART_A else AiSheet.FIRE_PART_BX
        val im = if (water) AiSheet.WATER_IMP3 else AiSheet.FIRE_IMPACT_BX
        val n = if (water) 7 else 5
        repeat(n) { i ->
            val start = if (water) 40f + i * 48f else 70f + i * 78f + r(0f, 24f)
            val dur = if (water) r(150f, 165f) else r(250f, 280f)
            val bx = dx + if (water) r(-20f, -4f) else r(-30f, -4f)
            val by = dy + if (water) r(-2f, 16f) else r(-5f, 23f)
            val bend = if (water) r(3f, 9f) else r(6f, 32f)
            val size = if (water) r(1.25f, 1.5f) + if (i == n - 1) .5f else 0f else 1.5f
            jets += Jet(start, dur, bx, by, bend, size)
            repeat(if (water) 3 else 4) { j ->
                val p = if (water) .25f + j * .25f else (j + 1) / 5f
                part(pt, j % 3, start + dur * p, 380f, mix(lx, bx, p) - if (water) 0f else 10f,
                    mix(ly, by, p) - bend * sin(p * PI.toFloat()),
                    if (water) r(60f, 140f) else r(-50f, -20f), if (water) r(0f, 30f) else r(-60f, -20f),
                    if (water) 200f else 60f, if (water) 1.7f else 1.5f)
            }
            if (water) spray(start + dur, bx, by, if (i == n - 1) 26 else r(11f, 15f).toInt(), if (i == n - 1) 400f else 320f)
            else burst(pt, intArrayOf(0, 1, 2), start + dur, bx, by, if (i == n - 1) 9 else 4, 60f, 180f, 240f, 1.5f, 450f)
        }
        val first = jets.first().let { it.start + it.duration }
        val last = jets.maxOf { it.start + it.duration }
        val done = last + if (water) 320f else 280f
        s.tween(0f, if (water) 130f else 200f) { p, _ -> s.attacker.set(-(if (water) 14f else 10f) * sin(p * PI.toFloat())) }
        reaction(first, done - first, if (water) 6f else 7f, if (water) 20f else 0f) { _, t ->
            if (water) color.reset().saturate(1.5f).brightness(1.2f).matrix
            else color.reset().sepia(.55f).saturate(2.2f).brightness(1.1f + .2f * sin(t / 25f)).matrix
        }
        if (water) s.tween(first, done - first) { _, t -> s.rotateTarget(shake(t, first, done - first, 6f, 20f) * .45f) }
        if (water) s.tween(last - 40f, 260f) { _, t -> s.sceneJolt(shake(t, last - 40f, 260f, 3.5f), shake(t, last - 40f, 260f, 3.5f) * .5f) }
        val total = last + if (water) 520f else 420f
        proc(total) { c, _, t ->
            for (index in jets.indices) {
                val b = jets[index]
                val p = (t - b.start) / b.duration
                if (p in 0f..1f) {
                    for (g in (if (water) 4 else 3) downTo 1) {
                        val pg = p - g * if (water) .1f else .08f
                        if (pg > 0f) projectile(c, pj, pg, b.x, b.y, b.bend, b.scale * if (water) 1f - g * .04f else 1f, (if (water) .32f else .22f) / g * 1.6f)
                    }
                    c.soft(mix(lx, b.x, p), mix(ly, b.y, p) - b.bend * sin(p * PI.toFloat()), if (water) 46f * b.scale else 34f, if (water) 46f * b.scale else 34f, if (water) 0x4AA8FF else 0xFFA030, if (water) .38f else .5f)
                    projectile(c, pj, p, b.x, b.y, b.bend, b.scale)
                }
            }
            drawParts(c, t)
            for (i in jets.indices) {
                val b = jets[i]; val u = (t - b.start - b.duration) / if (water) 380f else 340f
                if (u !in 0f..1f) continue
                val big = i == n - 1
                val px = if (water) if (big) 4.6f else 3.3f else if (big) 2.2f else 1.4f
                c.soft(b.x, b.y, if (water) 90f * px / 3.2f else 50f * px, if (water) 50f * px / 3.2f else 50f * px, if (water) 0x78C8FF else 0xFF9020, .5f * (1f - u))
                if (water) {
                    if (u < .25f) flash(c, .28f * (1f - u / .25f) * if (big) 1f else .5f, 0xD2EBFF)
                    if (u < .3f) c.ai(im, 0, b.x, b.y + 2f, (if (big) 2.6f else 1.7f) * s.k, 1f - u / .3f)
                } else c.ai(im, floor(u * 4).toInt(), b.x, b.y, px * s.k)
            }
        }
        return FxTimes(total, first, done)
    }

    private fun spray(start: Float, x: Float, y: Float, count: Int, high: Float) {
        repeat(count) {
            val sign = if (r() < .5f) -1 else 1; val a = r(-1.5f, .6f); val v = r(110f, high); val f = r(0f, 10f).toInt()
            val width = SPLASH_W[f]; val height = SPLASH_H[f]; val scale = SPLASH_SIZE[f] * r(.6f, 1.5f) / max(width, height)
            val vx = sign * cos(a) * v; val vy = sin(a) * v
            val heading = SPLASH_HEADING[f]
            parts += Part(AiSheet.WATER_SPL5, f, start + r(0f, 50f), r(450f, 750f), x + sign * r(0f, 8f), y + r(4f, 10f),
                vx, vy, r(380f, 560f), scale, if (heading.isNaN()) r(0f, 6.28f) else atan2(vy, vx) - heading * PI.toFloat() / 180f,
                if (heading.isNaN()) r(-4.5f, 4.5f) else 0f, false, width, height, true)
        }
    }

    private fun thunder(): FxTimes {
        repeat(9) { i -> part(AiSheet.ELEC_PART_B, intArrayOf(0, 1, 3)[i % 3], r(0f, 220f), r(160f, 250f), r(9f, 59f), r(-75f, -5f), vy = -10f, scale = 2f, add = true) }
        val times = floatArrayOf(170f, 400f)
        for (i in times.indices) {
            val x = dx - 2f - i * 20f
            burst(AiSheet.ELEC_PART_B, intArrayOf(0, 1, 3), times[i] + 40f, x, dy + 10f, 8, 90f, 220f, 200f, 2f, 360f, true)
            reaction(times[i] + 30f, 260f, 6f) { _, t -> color.reset().brightness(1.5f + .5f * sin(t / 20f)).saturate(1.8f).sepia(.4f).matrix }
        }
        proc(950f) { c, _, t ->
            for (i in times.indices) {
                val x = dx - 2f - i * 20f; val u = (t - times[i]) / 230f
                if (u in 0f..1f) {
                    c.soft(x, dy - 35f, 78f, 150f, 0xFFEC6E, .55f * (1f - u))
                    c.soft(x, dy + 16f, 56f, 40f, 0xFFF096, .6f * (1f - u))
                    c.ai(AiSheet.ELEC_PROJ_B, 0, x, dy + 24f + 22.67f * 3f - 155.33f * 1.5f, 3f * s.k,
                        if (u < .15f) 1f else 1f - (u - .15f) * .5f, sx = if (floor((t - times[i]) / 38f).toInt() % 2 == 0) 1f else -1f)
                    if (u < .3f) flash(c, .5f * (1f - u / .3f))
                }
                val v = (t - times[i] - 50f) / 400f
                if (v in 0f..1f) {
                    c.soft(x, dy + 8f, 70f, 70f, 0xFFE040, .5f * (1f - v))
                    c.ai(AiSheet.ELEC_IMPACT_B, (v * 4).toInt(), x, dy + 8f, 2.2f * s.k)
                }
            }
            drawParts(c, t)
        }
        return FxTimes(950f, 200f, 690f)
    }

    private fun ghost(): FxTimes {
        val bx = dx - 8f; val by = dy + 6f
        val angles = FloatArray(9) { r(0f, 6.28f) }; val radii = FloatArray(9) { r(60f, 94f) }
        burst(AiSheet.GHOST_PART_A, intArrayOf(0, 1, 2, 3), 700f, bx, by, 11, 70f, 220f, -20f, 1.5f, 520f)
        burst(AiSheet.GHOST_FX_A, intArrayOf(2), 700f, bx, by, 8, 120f, 280f, 80f, 1.1f, 560f)
        // Pre-sample emissions: the same sparks at every frame rate, with no particles allocated while drawing.
        for (t in 0..690 step 45) {
            val p = cl((t - 330f) / 370f).pow(2); val a = r(0f, 6.28f)
            part(AiSheet.GHOST_FX_A, 2, t.toFloat(), 360f, mix(lx, bx, p), mix(ly, by, p) - 14f * sin(p * PI.toFloat()), cos(a) * 90f, sin(a) * 90f, scale = r(.55f, .95f))
        }
        for (t in 330..690 step 32) {
            val p = cl((t - 330f) / 370f).pow(2)
            part(AiSheet.GHOST_PART_A, r(0f, 2f).toInt(), t.toFloat(), 380f,
                mix(lx, bx, p) - 14f, mix(ly, by, p) - 14f * sin(p * PI.toFloat()) + r(-7f, 7f),
                -40f, -10f, scale = 1.2f)
        }
        s.tween(0f, 330f) { p, t -> s.attacker.set(y = if (p < 1f) -sin(t / 60f) * 1.5f else 0f) }
        reaction(700f, 560f, 9f) { p, _ -> color.reset().brightness(.45f + .55f * p).saturate(1.5f).matrix }
        s.tween(700f, 280f) { _, t -> s.sceneJolt(shake(t, 700f, 280f, 4f), shake(t, 700f, 280f, 4f) * .4f) }
        proc(1280f) { c, _, t ->
            flash(c, if (t < 200f) t / 200f * .28f else if (t < 860f) .28f else max(0f, .28f * (1f - (t - 860f) / 300f)), 0x0E041E)
            drawParts(c, t)
            for (i in 0..8) {
                val p = (t - i * 28f) / 300f
                if (p in 0f..1f && t < 330f) c.ai(AiSheet.GHOST_PART_A, i % 2, lx + cos(angles[i]) * radii[i] * (1f - p * p), ly + sin(angles[i]) * radii[i] * (1f - p * p), 1.6f, min(1f, p * 3) * (1f - p * .4f))
            }
            if (t < 700f) {
                val gr = if (t < 330f) out(cl(t / 330f)) else 1f
                val p = cl((t - 330f) / 370f).pow(2); val x = mix(lx, bx, p); val y = mix(ly, by, p) - 14f * sin(p * PI.toFloat())
                val sc = 1.45f * (.15f + .85f * gr) * s.k; val pul = 1f + .09f * sin(t / 42f) + .04f * sin(t / 17f); val wob = 1f + .06f * sin(t / 55f)
                if (t >= 330f) for (g in 5 downTo 1) {
                    val pg = p - g * .07f * (1f + p)
                    if (pg > 0f) projectile(c, AiSheet.GHOST_ORB_A, pg, bx, by, 14f, sc / s.k * (1f - g * .07f), .24f / g * 1.7f, 0f)
                }
                for (i in 0..3) { val u = ((t + i * 150f) % 600f) / 600f; c.ai(AiSheet.GHOST_FX_A, 0, x, y, sc * (.8f + 1.7f * u), .85f * (1f - u) * gr, true) }
                c.ai(AiSheet.GHOST_FX_A, 1, x, y, sc * 1.7f * pul, .75f * gr, true, t / 260f)
                c.ai(AiSheet.GHOST_FX_A, 1, x, y, sc * 1.15f * pul, .55f * gr, true, -t / 190f)
                c.ai(AiSheet.GHOST_FX_A, 3, x, y, sc * 1.5f, .6f * gr, rot = t / 330f)
                c.soft(x, y, 60f * sc, 60f * sc, 0xB050FF, .55f * gr)
                c.ai(AiSheet.GHOST_ORB_A, 0, x, y, sc, rot = t / 700f, sx = pul * wob, sy = pul / wob)
                c.soft(x, y, 17f * sc * (1f + .35f * sin(t / 34f)), 17f * sc * (1f + .35f * sin(t / 34f)), 0xFF8CFF, .55f + .3f * sin(t / 34f))
            }
            val u = (t - 700f) / 500f
            if (u in 0f..1f) {
                c.soft(bx, by, 110f, 110f, 0x9A4CFF, .5f * (1f - u))
                c.ai(AiSheet.GHOST_IMPACT_A, (u * 4).toInt(), bx, by, 2.7f * s.k)
                c.ai(AiSheet.GHOST_FX_A, 0, bx, by, 1f + 4f * u, .9f * (1f - u), true)
                c.ai(AiSheet.GHOST_FX_A, 0, bx, by, .6f + 2.6f * u, .8f * (1f - u), true)
            }
        }
        return FxTimes(1280f, 700f, 1260f)
    }

    private fun earth(): FxTimes {
        val xs = floatArrayOf(0f, dx, dx / 2f); val ys = floatArrayOf(64f, dy + 50f, dy / 2f + 20.5f)
        for (t in 80 until 1250 step 30) for (i in 0..2) if (r() < .55f) part(AiSheet.EQ_FX_A, 3, t + r(0f, 20f), 700f, xs[i] + r(-75f, 75f), ys[i] + r(-4f, 4f), r(-45f, 45f), -r(120f, 290f), 640f, r(.34f, .64f))
        val dust = ArrayList<Jet>()
        for (t in intArrayOf(120, 520, 920)) for (i in 0..2) dust += Jet(t + r(0f, 60f), 900f, xs[i] + r(-15f, 15f), ys[i], 0f, r(1.6f, 2.4f))
        s.tween(0f, 1900f) { p, t ->
            val k = if (p < 1f) exp(-t / 650f) else 0f
            s.attacker.set(sin(t / 26f) * 5f * k, -abs(sin(t / 55f)) * 11f * k, sy = 1f - .1f * abs(sin(t / 55f)) * k)
            s.target.set(sin(t / 22f) * 6f * k, -abs(sin(t / 48f)) * 13f * k)
            s.jolt(if (p < 1f) sin(t / 30f) * 11f * exp(-t / 700f) else 0f)
        }
        val total = 1900f
        proc(total) { c, _, t ->
            for (index in dust.indices) { val d = dust[index]; val u = (t - d.start) / 900f
                if (u in 0f..1f) c.ai(AiSheet.EQ_FX_A, 2, d.x + (u - .5f) * 30f, d.y - 6f - u * 24f, d.scale * (.5f + 1.2f * u), .8f * (1f - u) * if (u < .12f) u / .12f else 1f)
            }
            drawParts(c, t)
        }
        return FxTimes(total + 1f, 80f, 1900f)
    }

    private fun psychic(): FxTimes {
        repeat(26) { i -> part(AiSheet.PSY_FX_B, 2, 300f + i * 38f, 620f, dx + r(-60f, 60f), dy + r(-55f, 55f), vy = -26f, scale = r(.45f, .95f), add = true) }
        s.tween(0f, 1500f) { p, t -> s.attacker.set(y = if (p < 1f) -sin(t / 90f) * 3f else 0f) }
        val prepared = filters(280f, 1220f) { _, t -> color.reset().hueRotate(sin(t / 90f) * 80f).brightness(1.2f).matrix }
        s.tween(280f, 1220f) { p, t -> s.target.set(sin(t / 30f) * 3f * (1f - p), sx = 1f + sin(t / 55f) * .05f * (1f - p), sy = 1f - sin(t / 55f) * .05f * (1f - p), filter = filterAt(prepared, p)) }
        proc(1700f) { c, _, t ->
            if (t < 340f) { val g = out(cl(t / 300f)); c.soft(lx, ly, 40f, 40f, 0xFF8AE6, .5f * g); c.ai(AiSheet.PSY_FX_B, 3, lx, ly, .9f * g * (1f + .08f * sin(t / 40f)), add = true) }
            if (t > 300f && t < 1000f) {
                val u = (t - 300f) / 700f; val p = out(u)
                for (i in 0..20) { val a = i / 21f; val b = min(p, a + .02f); if (a < p) c.line(mix(lx, dx, a), mix(ly, dy, a), mix(lx, dx, b), mix(ly, dy, b), 3f, 0xFFA0F0, .45f * sin(u * PI.toFloat())) }
            }
            if (t in 280f..1500f) c.soft(dx, dy, 120f, 100f, 0xFF64DC, .3f * sin((t - 280f) / 1220f * PI.toFloat()))
            for (i in 0..2) {
                val u = (t - (200f + i * 260f) - 180f) / 700f
                if (u !in 0f..1f) continue
                c.ai(AiSheet.PSY_FX_B, 0, dx, dy, (1f + 3f * out(u)) * s.k, .95f * (1f - u), true)
                c.ai(AiSheet.PSY_FX_B, 0, dx, dy, (.5f + 2.2f * out(u)) * s.k, .8f * (1f - u), true)
                c.ai(AiSheet.PSY_FX_B, 1, dx, dy, 1.1f + .8f * u, .85f * sin(u * PI.toFloat()), true, (if (i % 2 == 1) -1 else 1) * t / 150f)
            }
            drawParts(c, t)
        }
        return FxTimes(1700f, 380f, 1500f)
    }

    private fun ice(): FxTimes {
        val body = s.targetBody
        val bx = (body.cx - s.space.attackerX) * s.dir / s.u
        val by = (body.cy - s.space.attackerY) / s.u
        val cw = IceCubeFit.width(body.w / s.u, body.h / s.u)
        val ch = IceCubeFit.height(body.w / s.u, body.h / s.u)
        val avg = (cw + ch) / 2f; val k = (avg / 110f).coerceIn(.45f, 1.2f)
        repeat(18) { i -> part(AiSheet.ICE_CUBE3, 3, 100f + i * 14f, 520f, mix(lx, bx, r()), mix(ly, by, r()) + r(-9f, 9f), r(-10f, 10f), r(10f, 30f), scale = r(.25f, .5f), add = true) }
        repeat(12) { val a = r(0f, 6.28f); val rad = sqrt(r()) * .45f; val v = r(70f, 270f)
            part(AiSheet.ICE_CUBE3, 2, 1200f, r(700f, 850f), bx + cos(a) * rad * cw, by + sin(a) * rad * ch, cos(a) * v, sin(a) * v - 30f, 380f, .75f * k * r(.8f, 1.3f))
        }
        burst(AiSheet.ICE_CUBE3, intArrayOf(3), 1200f, bx, by, 6, 40f, 130f, 0f, .5f * k, 700f, true)
        val prepared = filters(230f, 1270f) { _, t ->
            val cold = if (t < 1200f) 1f else cl(1f - (t - 1200f) / 300f)
            color.reset().hueRotate(60f * cold).saturate(1f - .25f * cold).brightness(1f + .12f * cold).matrix
        }
        s.tween(230f, 1270f) { p, t ->
            s.target.set(if (t >= 1200f) shake(t, 1200f, 380f, 7f) else if (t > 900f) sin(t / 20f) * 1.4f else 0f,
                filter = filterAt(prepared, p))
        }
        proc(1900f) { c, _, t ->
            if (t < 520f) {
                val g = cl(t / 150f); val fa = if (t < 380f) 1f else 1f - (t - 380f) / 140f
                val dist = hypot(bx - lx, by - ly); val len = dist * out(g)
                if (len > 8f) {
                    c.ai(AiSheet.ICE_PROJ_A, 0, lx + (bx - lx) / dist * len / 2f, ly + (by - ly) / dist * len / 2f,
                        a = fa * (.88f + .12f * sin(t / 22f)), rot = atan2(by - ly, bx - lx), w = len, h = min(46f, AiSheet.ICE_PROJ_A.h * len / AiSheet.ICE_PROJ_A.w * .9f))
                    c.soft(mix(lx, bx, g / 2f), mix(ly, by, g / 2f), len / 2f + 20f, 26f, 0x96E1FF, .45f * fa)
                }
            }
            drawParts(c, t)
            if (t >= 140f && t < 1200f) {
                val u = cl((t - 140f) / 300f); val sc = (.55f + .45f * out(u)) * (1f + if (u >= 1f) .015f * sin(t / 60f) else 0f)
                c.soft(bx, by, cw * .95f, ch * .85f, 0xA0E6FF, .3f * u)
                c.ai(AiSheet.ICE_CUBE3, 0, bx, by, sc, .9f * u, w = cw * 192f / 160f, h = ch * 192f / 167f)
                if (t > 900f) { val crack = cl((t - 900f) / 150f); c.ai(AiSheet.ICE_CUBE3, 1, bx + sin(t / 15f) * crack * 1.2f, by, sc, crack, w = cw * 192f / 160f, h = ch * 192f / 167f) }
            }
            val v = (t - 1200f) / 520f
            if (v in 0f..1f) {
                if (v < .3f) flash(c, .35f * (1f - v), 0xDCF5FF)
                c.soft(bx, by, 40f + avg * .55f, 40f + avg * .55f, 0xAEEAFF, .5f * (1f - v))
                c.ai(AiSheet.ICE_CUBE3, 3, bx, by, (1.1f + 1.1f * v) * k * 1.4f, 1f - v, true)
            }
        }
        return FxTimes(1900f, 230f, 1500f)
    }

    private fun vine(): FxTimes {
        val ts = floatArrayOf(150f, 540f, 930f)
        val xs = floatArrayOf(dx - 96f, dx + 98f, dx - 80f)
        val ys = floatArrayOf(dy + 46f, dy + 54f, dy + 62f)
        for (i in 0..2) {
            burst(AiSheet.VINE_FX_B, intArrayOf(0), ts[i] + 190f, dx + if (i == 1) 10f else -10f, dy + 4f, if (i == 2) 11 else 6, 90f, 250f, 420f, .75f, 760f)
            reaction(ts[i] + 190f, 280f, 6f, if (i == 1) -7f else 7f) { _, _ -> color.reset().brightness(1.35f).saturate(1.3f).hueRotate(-18f).matrix }
        }
        s.tween(0f, 1300f) { p, t -> s.attacker.set(y = if (p < 1f) -sin(t / 110f) * 2f else 0f) }
        proc(1900f) { c, _, t ->
            for (i in 0..2) {
                val u = (t - ts[i]) / 560f
                if (u in 0f..1f) {
                    val pr = if (u < .34f) (u / .34f).pow(2) else if (u < .5f) 1f else 1f - io((u - .5f) / .5f) * .96f
                    val swing = (if (i == 2) -1.3f else -1.25f) + (if (i == 2) 1.5f else 1.35f) * cl(u / .34f).pow(2)
                    val sc = (.35f + .65f * pr) * 1.12f * s.k
                    c.save(); c.translate(xs[i], ys[i]); if (i == 1) c.scale(-1f, 1f); c.rotate(swing)
                    c.ai(AiSheet.VINE_PROJ_B, 0, AiSheet.VINE_PROJ_B.w * sc / 2f, 0f, sc, if (u > .78f) 1f - (u - .78f) / .22f else 1f); c.restore()
                }
                val hu = (t - ts[i] - 190f) / 330f
                if (hu in 0f..1f) {
                    val hx = dx + if (i == 1) 12f else -12f
                    c.soft(hx, dy + 4f, 70f, 60f, 0x78FF64, .45f * (1f - hu))
                    c.ai(AiSheet.VINE_FX_B, 1, hx, dy + 4f, 1.3f + 1.1f * hu, 1f - hu * .6f, rot = if (i == 1) .5f else -.4f)
                    c.ai(AiSheet.VINE_FX_B, 2, hx, dy + 4f, 1.7f, 1f - hu, true, if (i == 1) -.5f else .5f, if (i == 1) -1f else 1f)
                }
            }
            drawParts(c, t)
        }
        return FxTimes(1900f, 340f, 1490f)
    }

    private fun tackleX(t: Float): Float {
        val dist = dx - 60f
        return if (t < 260f) dist * cl(t / 260f).pow(2) else if (t < 370f) dist else dist * (1f - io(cl((t - 370f) / 340f)))
    }

    private fun tackle(): FxTimes {
        burst(AiSheet.TACKLE_FX_B, intArrayOf(3), 300f, dx - 22f, dy + 4f, 6, 70f, 190f, 160f, .5f, 480f)
        s.tween(0f, 710f) { _, t -> val x = tackleX(t); s.attacker.set(x, sx = 1f + .05f * if (abs(dx - 60f) > .01f) x / (dx - 60f) else 0f) }
        reaction(300f, 380f, 7f, 12f) { p, _ -> color.reset().brightness(1.5f - .5f * p).matrix }
        s.tween(300f, 160f) { _, t -> s.sceneJolt(shake(t, 300f, 160f, 2.5f), 0f) }
        proc(1000f) { c, _, t ->
            if (t < 340f) for (i in 1..5) {
                val tt = t - i * 18f
                if (tt > 0f && tt < 260f) c.rect(tackleX(tt) - 50f - i * 6f, -14f + i % 2 * 9f, tackleX(tt) - 24f - i * 6f, -11f + i % 2 * 9f, 0xFFFFFF, .14f * (6 - i) / 5f * cl(t / 260f))
            }
            val u = (t - 300f) / 360f
            if (u in 0f..1f) {
                if (u < .2f) flash(c, .4f * (1f - u / .2f))
                c.soft(dx - 22f, dy + 4f, 80f, 80f, 0xFFF4C0, .5f * (1f - u))
                c.ai(AiSheet.TACKLE_FX_B, (u * 4).toInt(), dx - 22f, dy + 4f, 2.2f * s.k)
                if (u < .5f) c.ring(dx - 22f, dy + 4f, 14f + u * 120f, 3f * (1f - u * 2f) + .5f, 0xFFFFFF, .8f * (1f - u * 2f))
            }
            drawParts(c, t)
        }
        return FxTimes(1000f, 300f, 710f)
    }

    private fun drain(): FxTimes {
        val bx = dx - 10f; val by = dy + 4f; val ax = 6f; val ay = -10f
        burst(AiSheet.DRAIN_FX_B, intArrayOf(3), 460f, bx, by, 9, 80f, 200f, 80f, .6f, 520f, true)
        repeat(22) { i -> part(AiSheet.DRAIN_FX_B, if (i % 3 != 0) 2 else 3, 1010f + i * 55f, 780f, ax + r(-30f, 30f), ay + r(20f, 40f), vy = -38f, scale = r(.32f, .54f), add = true) }
        reaction(460f, 330f, 6f, 8f) { p, _ -> color.reset().brightness(1.4f - .4f * p).hueRotate(60f).matrix }
        val prepared = filters(1000f, 600f, attacker = true) { p, _ -> color.reset().brightness(1f + .25f * sin(p * PI.toFloat())).matrix }
        s.tween(1000f, 600f) { p, _ -> s.attacker.set(filter = filterAt(prepared, p)) }
        proc(2300f) { c, _, t ->
            val p = (t - 100f) / 360f
            if (p in 0f..1f) {
                for (g in 5 downTo 1) { val pg = p - g * .06f; if (pg > 0f) c.ai(AiSheet.DRAIN_FX_B, 0, mix(lx, bx, pg), mix(ly, by, pg), .75f * (1f - g * .1f), .3f / g * 1.4f, true) }
                val x = mix(lx, bx, p); val y = mix(ly, by, p)
                c.soft(x, y, 40f, 40f, 0x7DFF8A, .5f); c.ai(AiSheet.DRAIN_FX_B, 0, x, y, 1.05f * s.k, rot = t / 200f)
            }
            val hu = (t - 460f) / 400f
            if (hu in 0f..1f) {
                c.soft(bx, by, 70f, 70f, 0x8DFF8D, .5f * (1f - hu))
                c.ai(AiSheet.DRAIN_FX_B, 3, bx, by, 1f + 2f * hu, 1f - hu, true, hu * 2f)
                c.ai(AiSheet.DRAIN_FX_B, 3, bx, by, .6f + 1.4f * hu, 1f - hu, true, -hu * 3f)
            }
            for (o in 0..3) {
                val pb = (t - 540f - o * 70f) / 560f
                if (pb !in 0f..1f) continue
                val sz = (if (o == 0) .95f else .62f) * (1f - pb * .25f)
                for (g in 3 downTo 0) {
                    val pp = io(max(0f, pb - g * .05f)); val x = mix(bx, ax, pp); val y = mix(by, ay, pp) - 44f * sin(pp * PI.toFloat()) + sin(pp * 11f + o * 2f) * 7f * sin(pp * PI.toFloat())
                    if (g == 0) c.soft(x, y, 28f * sz + 14f, 28f * sz + 14f, 0x6DFF7A, .5f)
                    c.ai(AiSheet.DRAIN_FX_B, if (g == 0 && o == 0) 0 else 1, x, y, sz * if (g == 0) 1f else 1f - g * .12f, if (g == 0) 1f else .26f / g * 1.5f, g > 0, t / (if (g == 0) 180f else 150f) + o)
                }
            }
            val h = (t - 990f) / 1100f
            if (h in 0f..1f) {
                c.soft(ax, ay + 10f, 80f, 70f, 0x6EFF82, .5f * sin(h * PI.toFloat()))
                c.ai(AiSheet.DRAIN_FX_B, 3, ax, ay, .8f + 2.6f * h, .9f * (1f - h), true, h * 2f)
                for (i in 0..2) { val u = h * 1.5f - i * .18f; if (u > 0f && u < 1f) c.ai(AiSheet.DRAIN_FX_B, 2, ax + (i - 1) * 24f, ay + 8f - u * 50f, .75f, sin(u * PI.toFloat()), true) }
            }
            drawParts(c, t)
        }
        return FxTimes(2300f, 460f, 1600f)
    }

    companion object {
        val looks = setOf(MoveLook.EMBER, MoveLook.WATER_JET, MoveLook.THUNDERBOLT, MoveLook.SHADOW_BALL,
            MoveLook.EARTHQUAKE, MoveLook.PSYCHIC, MoveLook.ICE_BEAM, MoveLook.VINE_WHIP, MoveLook.TACKLE, MoveLook.DRAIN)
        fun build(script: FxScript): FxTimes = AiAttackFx(script).run {
            when (script.plan.look) {
                MoveLook.EMBER -> stream(false)
                MoveLook.WATER_JET -> stream(true)
                MoveLook.THUNDERBOLT -> thunder()
                MoveLook.SHADOW_BALL -> ghost()
                MoveLook.EARTHQUAKE -> earth()
                MoveLook.PSYCHIC -> psychic()
                MoveLook.ICE_BEAM -> ice()
                MoveLook.VINE_WHIP -> vine()
                MoveLook.TACKLE -> tackle()
                MoveLook.DRAIN -> drain()
                else -> error("Not an approved AI look")
            }
        }
        private val SPLASH_W = floatArrayOf(152f, 104f, 108f, 270f, 251f, 268f, 158f, 182f, 257f, 73f)
        private val SPLASH_H = floatArrayOf(142f, 122f, 105f, 98f, 175f, 84f, 147f, 116f, 164f, 67f)
        private val SPLASH_SIZE = floatArrayOf(16f, 15f, 13f, 30f, 34f, 36f, 24f, 26f, 32f, 20f)
        private val SPLASH_HEADING = floatArrayOf(Float.NaN, Float.NaN, Float.NaN, Float.NaN, -45f, 0f, 135f, Float.NaN, Float.NaN, Float.NaN)
    }
}
