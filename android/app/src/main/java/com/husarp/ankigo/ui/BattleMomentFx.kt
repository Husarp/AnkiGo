package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.*
import com.husarp.ankigo.R
import kotlin.math.*
import com.husarp.ankigo.ui.BattleMoments.bell
import com.husarp.ankigo.ui.BattleMoments.mix
import com.husarp.ankigo.ui.BattleMoments.out2
import com.husarp.ankigo.ui.BattleMoments.out3
import com.husarp.ankigo.ui.BattleMoments.seg

private object MomentBitmaps {
    private val cache = HashMap<Int, android.graphics.Bitmap>()
    @Synchronized fun get(ctx: Context, id: Int): android.graphics.Bitmap = cache.getOrPut(id) {
        requireNotNull(BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false }))
    }
}

/** The approved AI sheets, decoded once per effect, never inside draw. All frame rectangles and paints are reused. */
internal class MomentArt(ctx: Context, id: Int, private val side: Float, private val count: Int = 4) {
    private val bitmap = MomentBitmaps.get(ctx, id)
    private val src = Array(count) { i -> Rect(i * bitmap.width / count, 0, (i + 1) * bitmap.width / count, bitmap.height) }
    private val dst = RectF(-side / 2f, -side / 2f, side / 2f, side / 2f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    fun draw(c: Canvas, frame: Int, x: Float, y: Float, scale: Float, alpha: Float, rotation: Float = 0f, sx: Float = 1f, sy: Float = 1f, additive: Boolean = true) {
        if (alpha <= .003f || scale <= 0f) return
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.xfermode = if (additive) add else null
        c.save(); c.translate(x, y); c.rotate(rotation * 180f / PI.toFloat()); c.scale(scale * sx, scale * sy)
        c.drawBitmap(bitmap, src[frame.coerceIn(0, count - 1)], dst, paint); c.restore()
    }
    fun ribbon(c: Canvas, x: Float, y: Float, scale: Float, alpha: Float, angle: Float, up: Boolean) {
        // Round 2's cropped comet head and direction, in the sheet's design units.
        c.save(); c.translate(x, y); c.rotate((angle - if (up) 2.662f else 2.425f) * 180f / PI.toFloat()); c.scale(scale, scale)
        val w = if (up) 101.67f else 100f
        val hx = if (up) 17.18f else 11.23f
        val hy = if (up) 69.64f else 72.42f
        dst.set(-hx, -hy, w - hx, 84f - hy)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt(); paint.xfermode = if (up) add else null
        c.drawBitmap(bitmap, src[0], dst, paint); c.restore()
    }
}

internal class MomentLight(color: Int, plate: Boolean = false) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = if (plate) RadialGradient(0f, 0f, 1f, intArrayOf(color, color, color and 0xFFFFFF), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        else RadialGradient(0f, 0f, 1f, intArrayOf(color, color and 0xFFFFFF), null, Shader.TileMode.CLAMP)
    }
    fun draw(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
        if (alpha <= .003f || rx <= 0f || ry <= 0f) return
        paint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        c.save(); c.translate(x, y); c.scale(rx, ry); c.drawCircle(0f, 0f, 1f, paint); c.restore()
    }
}

/** Rendered in mockup units around the actual visible sprite centre. */
internal class BattleMomentFx(
    ctx: Context, val kind: Kind, private val cx: Float, private val cy: Float, private val unit: Float,
    private val factor: Float = 1f, private val front: Boolean = true,
    private val radiusX: Float = 130f, private val radiusY: Float = 120f,
) : FxEffect {
    enum class Kind { CAUGHT, FREE, LEVEL, EVOLVE, SHINY, UP, DOWN, HIT }
    private val side = when (kind) { Kind.CAUGHT -> 80f; Kind.FREE -> 72f; Kind.LEVEL -> 120f; Kind.EVOLVE -> 130f; Kind.SHINY -> 64f; Kind.HIT -> 60f; else -> 100f }
    private val art = MomentArt(ctx, when (kind) {
        Kind.CAUGHT -> R.drawable.moment_catch_a; Kind.FREE -> R.drawable.moment_free_a
        Kind.LEVEL -> R.drawable.moment_levelup_a; Kind.EVOLVE -> R.drawable.moment_evo_a
        Kind.SHINY -> R.drawable.moment_shiny_a; Kind.HIT -> R.drawable.moment_hit_b
        Kind.UP -> R.drawable.moment_comet_up; Kind.DOWN -> R.drawable.moment_comet_down
    }, side, if (kind == Kind.UP || kind == Kind.DOWN) 1 else 4)
    private val gold = MomentLight(0xFFFFE16E.toInt())
    private val white = MomentLight(0xFFFFFFFF.toInt(), kind == Kind.EVOLVE)
    private val blue = MomentLight(0xFFCEE6FF.toInt(), true)
    private val red = MomentLight(0xFFFF7846.toInt())
    private val violet = MomentLight(0xFF6E78FF.toInt())
    private val ring = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val star = Path().apply {
        for (i in 0 until 8) { val a = i * PI.toFloat() / 4f; val r = if (i % 2 == 0) 1f else .2f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r) }; close()
    }
    // Mulberry32, as in the approved mockup. Random parts are all built here.
    private class Rng(var s: Int) {
        fun next(): Float { s += 0x6D2B79F5; var t = (s xor (s ushr 15)) * (1 or s); t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f }
    }
    private val seed = when (kind) { Kind.LEVEL -> 5; Kind.EVOLVE -> 9; Kind.SHINY -> 3; Kind.HIT -> 13; else -> 21 }
    private val random = Rng(seed)
    private val bits = Array(12) { FloatArray(4) }
    init {
        for (i in bits.indices) { val b = bits[i]
            when (kind) {
                Kind.LEVEL -> { b[0] = (random.next() - .5f) * 100f; b[1] = 200f + random.next() * 450f; b[2] = 70f + random.next() * 60f; b[3] = (random.next() - .5f) * .8f }
                Kind.SHINY -> { b[0] = (random.next() - .5f) * 70f; b[1] = (random.next() - .5f) * 70f; b[2] = 500f + i * 130f + random.next() * 80f; b[3] = random.next() * 3f }
                Kind.UP, Kind.DOWN -> { b[0] = random.next() * 6.28f; b[1] = 60f + random.next() * 700f; b[2] = 700f + random.next() * 300f; b[3] = .8f + random.next() * .35f }
                else -> { b[0] = random.next() * 6.28f; b[1] = if (kind == Kind.HIT) 100f + random.next() * 160f else 60f + random.next() * 80f; b[2] = random.next() * 6f }
            }
        }
    }
    private fun frame(c: Canvas, f: Int, x: Float, y: Float, sc: Float, a: Float, rot: Float = 0f, sx: Float = 1f, sy: Float = 1f, add: Boolean = true) = art.draw(c, f, x, y, sc, a, rot, sx, sy, add)
    override fun draw(canvas: Canvas, t: Float): Boolean {
        val tm = t / factor
        val end = when (kind) { Kind.LEVEL -> BattleMoments.LEVEL_END; Kind.EVOLVE -> BattleMoments.EVOLVE_END; Kind.SHINY -> BattleMoments.SHINY_END; Kind.UP, Kind.DOWN -> BattleMoments.STAT_END; Kind.HIT -> BattleMoments.HIT_END; Kind.FREE -> 800f; else -> 1200f }
        if (tm < 0f || tm >= end) return false
        canvas.save(); canvas.translate(cx, cy); canvas.scale(unit, unit)
        when (kind) {
            Kind.LEVEL -> level(canvas, tm)
            Kind.EVOLVE -> evolve(canvas, tm)
            Kind.SHINY -> shiny(canvas, tm)
            Kind.UP, Kind.DOWN -> spiral(canvas, tm, kind == Kind.UP)
            Kind.HIT -> hit(canvas, tm)
            Kind.CAUGHT -> caught(canvas, tm)
            Kind.FREE -> free(canvas, tm)
        }
        canvas.restore(); return true
    }
    private fun caught(c: Canvas, q: Float) {
        frame(c, 0, 0f, 0f, .5f + 1.4f * out3(seg(q, 0f, 350f)), 1f - seg(q, 40f, 400f), q / 500f)
        frame(c, 1, 0f, 0f, .5f + 1.9f * out3(seg(q, 0f, 650f)), 1f - seg(q, 100f, 650f))
        frame(c, 2, 0f, -4f, .9f + .9f * out3(seg(q, 60f, 600f)), bell(seg(q, 60f, 760f)), -q / 900f)
        for (i in 0 until 7) { val u = seg(q, 150f + i * 40f, 900f + i * 40f); frame(c, 3, (i - 3) * 20f, -6f - u * 60f, .55f, bell(u), (i - 3) * .15f) }
    }
    private fun free(c: Canvas, q: Float) {
        frame(c, 1, 0f, 0f, .5f + 1.8f * out3(seg(q, 0f, 380f)), 1f - seg(q, 60f, 420f))
        frame(c, 0, 0f, 0f, .6f + 1.5f * out3(seg(q, 0f, 300f)), 1f - seg(q, 80f, 340f), q / 400f)
        red.draw(c, 0f, 0f, 70f, 60f, .7f * (1f - seg(q, 0f, 380f)))
        for (i in 0 until 8) { val a = i / 8f * 2f * PI.toFloat() + .4f; val u = seg(q, 30f, 560f)
            frame(c, 2, cos(a) * u * 78f, sin(a) * u * 56f + 90f * u * u, .38f, 1f - u, a + u * 4f, add = false) }
        frame(c, 3, 0f, -10f - 20f * seg(q, 100f, 700f), .75f + .6f * seg(q, 100f, 700f), bell(seg(q, 100f, 700f)) * .9f, add = false)
    }
    private fun level(c: Canvas, t: Float) {
        val base = 50f; val g = bell(seg(t, 100f, 1100f))
        gold.draw(c, 0f, base - 20f, 120f, 100f, .55f * g); gold.draw(c, 0f, base, 90f, 26f, .9f * g)
        val sy = .25f + .75f * out2(seg(t, 120f, 760f))
        frame(c, 0, 0f, base - 60f * sy - 2f, 1.2f, bell(seg(t, 120f, 980f)), sy = sy * 1.1f)
        for (i in 0 until 2) { val u = seg(t, 200f + i * 260f, 900f + i * 260f); frame(c, 1, 0f, base - 4f - u * 18f, .6f + 1.1f * out3(u), bell(u), sy = .9f) }
        for (i in 0 until 9) { val b = bits[i]; val u = seg(t, b[1], b[1] + 650f); frame(c, 2, b[0], base - 8f - u * b[2] * 1.6f, .62f, bell(u), b[3]) }
        frame(c, 3, 0f, -10f, .4f + 1.4f * out3(seg(t, 60f, 420f)), 1f - seg(t, 120f, 480f), t / 600f)
    }
    private fun evolve(c: Canvas, t: Float) {
        val build = seg(t, 0f, 900f); val rel = seg(t, 900f, 1900f); val a = if (t < 900f) build * build else 1f - rel
        val k = maxOf(.6f, (radiusX + radiusY) / 140f); val gs = .5f + .5f * a
        blue.draw(c, 0f, 0f, radiusX * gs, radiusY * gs, .95f * a)
        frame(c, 0, 0f, 0f, (.35f + .9f * if (t < 900f) out2(build) else 1f - .3f * rel) * k, .85f * a, t / 900f)
        frame(c, 1, 0f, 0f, (1f + .3f * build) * k, .75f * a, -t / 700f)
        if (t >= 800f && t < 1500f) { val u = seg(t, 800f, 1500f)
            frame(c, 2, 0f, 0f, (.4f + 1.6f * out3(u)) * k, 1f - u, u * .8f)
            white.draw(c, 0f, 0f, radiusX * 1.15f, radiusY * 1.15f, .95f * (1f - seg(t, 800f, 1100f))) }
        for (i in bits.indices) { val b = bits[i]; val u = seg(t, 850f + i * 25f, 1750f + i * 25f); val d = b[1] * (k * .8f + .2f)
            frame(c, 3, cos(b[0]) * d * out3(u), sin(b[0]) * d * .8f * out3(u) - 20f * u, .32f * sqrt(k), bell(u), b[2]) }
        if (t < 900f) for (i in 0 until 6) { val q = (t / 500f + i / 6f) % 1f; val an = i * 1.05f + t / 400f
            frame(c, 3, cos(an) * 90f * k * (1f - q), sin(an) * 70f * k * (1f - q), .26f * sqrt(k), q * a, an) }
    }
    private fun shiny(c: Canvas, t: Float) {
        gold.draw(c, 0f, 0f, 110f, 100f, .5f * bell(seg(t, 0f, 900f)))
        frame(c, 0, 0f, 0f, .5f + 1.5f * out3(seg(t, 0f, 380f)), 1f - seg(t, 60f, 440f), t / 700f)
        val q = seg(t, 0f, 800f)
        if (q > 0f && q < 1f) for (i in 0 until 8) { val a = i / 8f * 2f * PI.toFloat() + .2f; val reach = 52f * 1.6f * out3(q); val tw = .6f + .4f * sin(t / 45f + i * 2f)
            frame(c, 1, cos(a) * reach, sin(a) * reach * .85f, (.45f + .25f * tw) * (1f - .4f * q), 1f - q * q, a * .3f + t / 400f) }
        frame(c, 2, 0f, 0f, .8f + 1.2f * out3(seg(t, 100f, 800f)), 1f - seg(t, 150f, 800f))
        for (i in 0 until 3) { val u = seg(t, 150f + i * 90f, 900f + i * 90f); frame(c, 3, (i - 1) * 40f, -10f - u * 50f, .9f, bell(u) * .9f) }
        for (i in 0 until 6) { val b = bits[i]; val u = seg(t + 450f, b[2], b[2] + 360f); frame(c, 1, b[0], b[1], .25f + .5f * bell(u), bell(u), b[3] + u * .8f) }
    }
    private fun hit(c: Canvas, q: Float) {
        val u = seg(q, 0f, 440f)
        if (q < 440f) { gold.draw(c, 0f, 0f, 62f, 52f, .6f * (1f - u)); frame(c, (q / 110f).toInt(), 0f, 0f, 1.15f + .35f * u, 1f - .6f * seg(q, 330f, 440f)) }
        for (i in 0 until 8) { val b = bits[i]; val us = seg(q, 20f, 460f)
            frame(c, 1, cos(b[0]) * b[1] * us * .5f, sin(b[0]) * b[1] * us * .4f + 60f * us * us, .3f, 1f - us, b[2] * us) }
    }
    private fun spiral(c: Canvas, t: Float, up: Boolean) {
        val main = if (up) 0xFFFF803C.toInt() else 0xFF7880FF.toInt(); val core = if (up) 0xFFFFECAA.toInt() else 0xFFD6D6FF.toInt()
        if (!front) { val g = bell(seg(t, 0f, 1300f)); val light = if (up) red else violet
            light.draw(c, 0f, 0f, 135f, 105f, .6f * g); light.draw(c, 0f, 52f, 118f, 26f, .8f * g) }
        for (i in 0 until 3) { val u = seg(t, 80f + i * 260f, 980f + i * 260f); val y = if (up) mix(42f, -46f, u) else mix(-46f, 42f, u); val sc = if (up) .8f + .3f * u else 1.1f - .3f * u
            ring.set(-88f * sc, y - 24f * sc, 88f * sc, y + 24f * sc)
            paint.style = Paint.Style.STROKE
            for (j in 0 until 3) { paint.strokeWidth = 2.2f * if (j == 0) 3.2f else if (j == 1) 1.6f else .8f
                paint.color = if (j == 2) core else main; paint.alpha = (255f * bell(u) * .9f * if (j == 0) .16f else if (j == 1) .35f else .9f).toInt().coerceIn(0, 255)
                c.drawArc(ring, if (front) 0f else 180f, 180f, false, paint) }
        }
        paint.style = Paint.Style.FILL
        for (i in 0 until 4) { val u = (t - 40f - i * 120f) / 1000f
            for (j in 2 downTo 0) { val uu = u - j * .045f; if (uu < 0f || uu > 1f) continue
                val an = BattleMoments.ribbonAngle(i, uu, up); val z = sin(an); if (BattleMoments.ribbonFront(an) != front) continue
                val an2 = BattleMoments.ribbonAngle(i, min(1f, uu + .012f), up)
                val x = 88f * cos(an); val y = BattleMoments.ribbonY(uu, up) + 24f * z
                val angle = atan2(BattleMoments.ribbonY(min(1f, uu + .012f), up) + 24f * sin(an2) - y, 88f * cos(an2) - x)
                art.ribbon(c, x, y, .7f * (if (i % 2 == 1) .85f else 1f) * (1f + .12f * z), bell(min(1f, uu * 1.15f)) * (if (j > 0) .5f else 1f) * (if (front) 1f else .8f), angle, up) }
        }
        for (b in bits) { val u = (t - b[1]) / b[2]; if (u <= 0f || u >= 1f) continue
            val a = b[0] + (if (up) 1f else -1f) * u * 1.6f * 2f * PI.toFloat(); if (BattleMoments.ribbonFront(a) != front) continue
            val tw = .5f + .5f * sin(t / 80f + b[0] * 3f); val r = 2.5f + 3.5f * tw
            paint.color = core; paint.alpha = (255f * bell(u) * (.5f + .5f * tw)).toInt().coerceIn(0,255)
            c.save(); c.translate(88f * b[3] * cos(a), BattleMoments.ribbonY(u, up) + 24f * sin(a)); c.scale(r, r); c.drawPath(star, paint); c.restore() }
    }
}

internal class CatchThrowFx(
    ctx: Context, private val ball: android.widget.ImageView, private val enemy: com.husarp.ankigo.gfx.SpriteView,
    private val launchX: Float, private val launchY: Float, private val targetX: Float, private val targetY: Float,
    private val floorX: Float, private val floorY: Float, private val unit: Float, private val success: Boolean,
    private val color: Int, private val core: Int, private val ultra: Boolean, private val factor: Float,
) : FxEffect {
    private val art = MomentArt(ctx, R.drawable.moment_catch_a, 80f)
    private val trail = MomentLight(color)
    private val light = MomentLight(core)
    private val gold = MomentLight(0xFFFFF0C8.toInt())
    private val white = MomentLight(0xFFFFFFFF.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Path().apply {
        for (i in 0 until 8) { val a = i * PI.toFloat() / 4f; val r = if (i % 2 == 0) 1f else .28f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r) }; close()
    }
    private val icon = Rect(-ball.layoutParams.width / 2, -ball.layoutParams.height / 2, ball.layoutParams.width / 2, ball.layoutParams.height / 2)
    private fun x(t: Float) = BattleMoments.ballX(t, launchX, targetX)
    private fun y(t: Float) = BattleMoments.ballY(t, launchY, targetY, floorY, unit)
    override fun draw(canvas: Canvas, t: Float): Boolean {
        val tm = t / factor
        val absorbed = BattleMoments.absorbed(tm)
        enemy.pivotX = enemy.width / 2f; enemy.pivotY = enemy.height.toFloat()
        enemy.scaleX = 1f - absorbed; enemy.scaleY = 1f - absorbed; enemy.alpha = 1f - absorbed
        enemy.flash = if (tm in BattleMoments.CONTACT..(BattleMoments.CONTACT + 280f)) .9f * (1f - absorbed) else 0f
        if (tm >= BattleMoments.catchEnd(success)) return false
        if (tm < BattleMoments.THROW) return true
        val bx = if (tm < BattleMoments.CONTACT) x(tm) else floorX
        val by = y(tm)
        if (tm < BattleMoments.CONTACT) {
            for (i in 11 downTo 1) { val past = tm - i * 20f; if (past < BattleMoments.THROW) continue
                val f = 1f - i / 12f; val px = x(past); val py = y(past)
                trail.draw(canvas, px, py, (17f * f + 4f) * unit, (17f * f + 4f) * unit, f)
                light.draw(canvas, px, py, (7f * f + 2f) * unit, (7f * f + 2f) * unit, .4f * f)
                if (ultra && i % 3 == 0) { paint.color = 0xFF1A1A2C.toInt(); paint.alpha = (100 * f).toInt(); canvas.drawCircle(px, py, (3.2f * f + .8f) * unit, paint) }
                if (i % 4 == 2) { val tw = .5f + .5f * sin(tm / 70f + i * 1.9f); val r = (2.5f + 3f * tw * f) * unit
                    paint.color = core; paint.alpha = (255f * f * (.5f + .5f * tw)).toInt()
                    canvas.save(); canvas.translate(px - 6f * unit, py + (-7f + i % 3 * 4f) * unit); canvas.scale(r, r); canvas.drawPath(spark, paint); canvas.restore() }
            }
            trail.draw(canvas, bx, by, 32f * unit, 32f * unit, .5f)
        } else {
            var gl = 0f
            for (i in 0 until if (success) 3 else 2) gl = maxOf(gl, bell(seg(tm, BattleMoments.LAND + i * 400f + 100f, BattleMoments.LAND + i * 400f + 300f)))
            gold.draw(canvas, bx, by + 4f * unit, 40f * unit, 30f * unit, .22f + .35f * gl)
        }
        canvas.save(); canvas.translate(bx, by)
        canvas.rotate(if (tm < BattleMoments.CONTACT) (tm - BattleMoments.THROW) / 70f * 180f / PI.toFloat() else BattleMoments.wobble(tm, success))
        val scale = if (tm < BattleMoments.CONTACT) .7f + .3f * seg(tm, BattleMoments.THROW, BattleMoments.CONTACT) else 1f
        canvas.scale(scale, scale)
        ball.drawable?.let { it.bounds = icon; it.draw(canvas) }
        canvas.restore()
        for (i in 0 until if (success) 3 else 2) { val u = seg(tm, BattleMoments.LAND + i * 400f + 200f, BattleMoments.LAND + i * 400f + 380f)
            art.draw(canvas, 2, bx, by - 6f * unit, (.4f + .3f * u) * unit, bell(u)) }
        if (tm >= BattleMoments.CONTACT && tm < BattleMoments.CONTACT + 420f) { val u = seg(tm, BattleMoments.CONTACT, BattleMoments.CONTACT + 420f)
            white.draw(canvas, targetX, targetY, 80f * unit, 70f * unit, .8f * (1f - u))
            art.draw(canvas, 2, targetX, targetY, (1.1f + .6f * u) * unit, 1f - u, u * 2f) }
        return true
    }
    override fun end() {
        enemy.flash = 0f
        ball.visibility = android.view.View.VISIBLE
        ball.drawable?.let { it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight) }
        ball.invalidate()
    }
}
