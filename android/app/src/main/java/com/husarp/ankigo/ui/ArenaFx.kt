package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * One canvas effect on [ArenaFxView]. [t] is milliseconds since the effect started; return false once it has nothing left to draw.
 * [end] runs once when the effect is over or cut short (it puts back what the effect changed on other views).
 */
fun interface FxEffect {
    fun draw(canvas: Canvas, t: Float): Boolean
    fun end() {}
}

/**
 * The arena's canvas-effect overlay (round 5): one full-size View on top of [ArenaView]'s children that draws a small list of
 * time-based [FxEffect]s (the shiny star burst, the XP particles). It redraws only while an effect is alive, never takes
 * touches and is not announced to accessibility. [ArenaView] clears it with its animations and when it leaves the screen.
 */
class ArenaFxView(ctx: Context) : View(ctx) {
    private class Live(val effect: FxEffect, val start: Long)

    private val live = ArrayList<Live>()
    private val pending = ArrayList<Runnable>()
    /** Test-only: draw every effect as if it were this many ms old, with no clock and no redraw loop. */
    private var still = -1f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    /** Starts [effect] now. */
    fun add(effect: FxEffect) {
        live += Live(effect, SystemClock.uptimeMillis())
        postInvalidateOnAnimation()
    }

    /** Starts the effect [make] builds after [delayMs] (it is built then, so it sees the layout of that moment). */
    fun addAfter(delayMs: Long, make: () -> FxEffect?) {
        if (delayMs <= 0L) { make()?.let { add(it) }; return }
        val r = object : Runnable { override fun run() { pending -= this; make()?.let { add(it) } } }
        pending += r
        postDelayed(r, delayMs)
    }

    /** Ends every effect, also the ones still waiting for their start. */
    fun clear() {
        pending.forEach { removeCallbacks(it) }
        pending.clear()
        val ended = live.map { it.effect }
        live.clear()
        still = -1f
        ended.forEach { it.end() }
        invalidate()
    }

    /** Ends [effect] at once if it is running. */
    fun stop(effect: FxEffect) {
        val i = live.indexOfFirst { it.effect === effect }
        if (i < 0) return
        live.removeAt(i)
        effect.end()
        invalidate()
    }

    /** Test-only: shows [effect] frozen at [atMs]. */
    internal fun showStill(effect: FxEffect, atMs: Float) {
        live.clear()
        live += Live(effect, 0L)
        still = atMs
        invalidate()
    }

    override fun onDetachedFromWindow() { clear(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        if (live.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        var i = 0
        while (i < live.size) {
            val l = live[i]
            val t = if (still >= 0f) still else (now - l.start).toFloat()
            if (l.effect.draw(canvas, t)) i++ else { live.removeAt(i); l.effect.end() }
        }
        if (live.isNotEmpty() && still < 0f) postInvalidateOnAnimation()
    }
}

/** The shiny entrance burst around a sprite box: centre ([cx], [cy]) and unit scale [sc] from [ShinyBurst]. */
class ShinyBurstFx(private val cx: Float, private val cy: Float, private val sc: Float) : FxEffect {
    private val star = Path().apply {
        for (i in 0 until 8) {
            val a = i * Math.PI.toFloat() / 4f
            val r = if (i % 2 == 1) 0.28f else 1f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var canvas: Canvas
    private val sink = ShinyBurst.Sink { x, y, r, alpha, color, flash -> drawStar(x, y, r, alpha, color, flash) }

    override fun draw(canvas: Canvas, t: Float): Boolean {
        if (ShinyBurst.finished(t)) return false
        this.canvas = canvas
        ShinyBurst.forEach(t, cx, cy, sc, sink)
        return true
    }

    private fun drawStar(x: Float, y: Float, r: Float, alpha: Float, color: Int, flash: Boolean) {
        if (r <= 0f || alpha <= 0f) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        // A faint dark halo underneath, so white stars still show on a light arena.
        paint.color = HALO
        paint.alpha = (alpha * (if (flash) 70 else 95)).toInt()
        canvas.save(); canvas.scale(1.22f, 1.22f); canvas.drawPath(star, paint); canvas.restore()
        paint.color = color
        paint.alpha = (alpha * 255).toInt()
        canvas.drawPath(star, paint)
        canvas.restore()
    }

    private companion object { const val HALO = 0xFF463C14.toInt() }
}

/**
 * The XP animation (pick 38A): soft blue sparks on their own curves from the [from] box (the defeated Pokémon) into the [to] box
 * (ours), a small white star where each lands, and the comic "+N XP" number over the defeated one's spot. [onFlash] gets our
 * Pokémon's white flash every frame and 0 when the effect ends. Maths in [XpParticles]; boxes are in the overlay's pixels.
 */
class XpAbsorbFx(
    ctx: Context,
    private val plan: XpParticles.Plan,
    private val from: XpParticles.Box,
    private val to: XpParticles.Box,
    arenaW: Float,
    private val xp: Int,
    floorSp: Float,
    private val onFlash: (Float) -> Unit,
) : FxEffect {
    private val d = ctx.resources.displayMetrics.density
    private val unit = XpParticles.unit(arenaW, d)

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // white core, soft blue body, clear edge (the mockup's radial glow), for a radius of 1: drawn scaled
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0xE678C8FF.toInt(), 0x0078C8FF), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    private val star = Path().apply {
        for (i in 0 until 8) {
            val a = i * Math.PI.toFloat() / 4f
            val r = if (i % 2 == 1) 0.32f else 1f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // the number: the comic look of the floating battle texts (heavy italic, dark outline), a smaller size
    private val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextMath.at(19f, floorSp), ctx.resources.displayMetrics)
    private val strokePx = maxOf(2f * d, sizePx * 0.11f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 900)
        textSize = sizePx
        textSkewX = -0.22f
        letterSpacing = 0.02f
        textAlign = Paint.Align.CENTER
    }
    private val label = "+$xp XP"
    private val ink = Rect().also { text.getTextBounds(label, 0, label.length, it) }
    private val inkMid = (ink.top + ink.bottom) / 2f
    // room for the pop (x 1.16) and the tilt on each side
    private val spot = XpParticles.numberSpot(from, arenaW, (text.measureText(label) / 2f + strokePx) * 1.2f, (ink.height() / 2f + strokePx) * 1.2f, 2f * d)
    private lateinit var canvas: Canvas
    private val sink = XpParticles.Sink { kind, x, y, r, alpha -> mark(kind, x, y, r, alpha) }

    override fun draw(canvas: Canvas, t: Float): Boolean {
        if (t >= plan.end) return false
        onFlash(XpParticles.flash(plan, t))
        this.canvas = canvas
        XpParticles.forEach(plan, t, from, to, unit, sink)
        drawNumber(canvas, t)
        return true
    }

    override fun end() = onFlash(0f)

    private fun mark(kind: Int, x: Float, y: Float, r: Float, alpha: Float) {
        if (r <= 0f || alpha <= 0f) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        if (kind == XpParticles.LAND) {
            starPaint.color = 0xFFFFFFFF.toInt()
            starPaint.alpha = (alpha * 255).toInt()
            canvas.drawPath(star, starPaint)
        } else {
            glow.alpha = (alpha * 255).toInt()
            canvas.drawCircle(0f, 0f, 1f, glow)
        }
        canvas.restore()
    }

    private fun drawNumber(canvas: Canvas, t: Float) {
        val a = XpParticles.numberAlpha(plan, t)
        if (a <= 0f) return
        val shown = "+${XpParticles.count(plan, t, xp)} XP"
        val s = XpParticles.numberScale(plan, t)
        canvas.save()
        canvas.translate(spot.first, spot.second)
        canvas.rotate(XpParticles.TILT_DEG)
        canvas.scale(s, s)
        val baseline = -inkMid
        text.style = Paint.Style.STROKE
        text.strokeWidth = strokePx * 2f
        text.strokeJoin = Paint.Join.ROUND
        text.color = FloatTextView.Look.OUTLINE
        text.alpha = (a * (FloatTextView.Look.OUTLINE ushr 24)).toInt()
        text.setShadowLayer(3f * d, 0f, 1.2f * d, ((a * 0x99).toInt() shl 24))
        canvas.drawText(shown, 0f, baseline, text)
        text.style = Paint.Style.FILL
        text.strokeWidth = 0f
        text.clearShadowLayer()
        text.color = 0xFFFFFFFF.toInt()
        text.alpha = (a * 255).toInt()
        canvas.drawText(shown, 0f, baseline, text)
        canvas.restore()
    }
}
