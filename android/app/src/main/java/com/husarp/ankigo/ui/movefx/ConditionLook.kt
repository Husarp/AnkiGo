package com.husarp.ankigo.ui.movefx

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.husarp.ankigo.ui.AppPrefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** PLAN 603: visual state only; departures clear immediately, cures get a short exit. */
internal class ConditionLookState {
    var status: String? = null; private set
    var clearing = false; private set
    var since = 0L; private set
    val visible: Boolean get() = status != null

    fun set(value: String?, now: Long, immediate: Boolean = false) {
        val next = value?.takeIf { it == "frz" || it == "slp" }
        if (immediate) { status = next; clearing = false; since = now; return }
        if (next != null) {
            if (next != status || clearing) { status = next; clearing = false; since = now }
        } else if (status != null && !clearing) { clearing = true; since = now }
    }

    fun advance(now: Long) {
        if (clearing && now - since >= duration()) { status = null; clearing = false }
    }

    fun duration(): Long = if (status == "frz") 1000L else 420L
}

/** One retained renderer per battle sprite. Geometry, paints, cube and shards are reused on every frame. */
internal class ConditionLook(private val view: View, private val redraw: () -> Unit) {
    val state = ConditionLookState()
    private var requested: String? = null
    private var queued = false
    private var attached = false
    private var enabled = false
    private val prefs = view.context.getSharedPreferences("study", 0)
    private val uiPrefs = view.context.applicationContext.getSharedPreferences("ankigo_ui", 0)
    private val watch = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "reduceMotion" || key == "attackAnimations") refresh()
    }
    private val cube = IceCube(1f, 1f)
    private val fx = FxCanvas(FxArt.of(view.context), 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val zColor = if (view.resources.configuration.uiMode and 0x30 == 0x20) 0xFFA1AEFF.toInt() else 0xFF6A80FF.toInt()
    private val shard = floatArrayOf(-5f, -4f, 7f, -5f, 2f, 7f)
    private val tick = object : Runnable {
        override fun run() {
            queued = false
            state.advance(SystemClock.uptimeMillis())
            redraw()
            schedule()
        }
    }

    val cold: Boolean get() = enabled && state.status == "frz" && (!state.clearing || SystemClock.uptimeMillis() - state.since < 440L)
    internal val running: Boolean get() = queued

    fun set(status: String?, immediate: Boolean = false) {
        requested = status
        state.set(if (enabled) status else null, SystemClock.uptimeMillis(), immediate || !enabled)
        redraw()
        if (!canRun()) stop() else schedule()
    }

    fun attach() {
        attached = true
        prefs.registerOnSharedPreferenceChangeListener(watch)
        uiPrefs.registerOnSharedPreferenceChangeListener(watch)
        refresh()
    }

    fun detach() {
        attached = false
        prefs.unregisterOnSharedPreferenceChangeListener(watch)
        uiPrefs.unregisterOnSharedPreferenceChangeListener(watch)
        stop()
        // An exit interrupted by leaving the screen never appears on a different Pokémon later.
        state.set(if (enabled) requested else null, SystemClock.uptimeMillis(), immediate = true)
    }

    fun refresh() {
        val was = enabled
        enabled = ValueAnimator.areAnimatorsEnabled() && !prefs.getBoolean("reduceMotion", false) && AppPrefs.attackAnimations(view.context)
        if (was != enabled) state.set(if (enabled) requested else null, SystemClock.uptimeMillis(), immediate = true)
        redraw()
        if (!canRun()) stop() else schedule()
    }

    private fun canRun() = attached && enabled && state.visible && view.isShown && view.windowVisibility == View.VISIBLE && view.alpha > 0f && view.width > 0 && view.height > 0
    private fun stop() { view.removeCallbacks(tick); queued = false }
    private fun schedule() {
        if (!canRun() || queued) return
        queued = true
        view.postOnAnimation(tick)
    }

    /** Called inside the sprite's canvas transform, before its image, with the actual visible art bounds. */
    fun pose(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        if (!enabled || state.status != "slp") return
        val ms = (SystemClock.uptimeMillis() - state.since).toFloat()
        val size = max(r - l, b - t) / 100f
        if (state.clearing) {
            canvas.translate(sin(ms / 24f) * 2f * size * (1f - ms / 420f).coerceIn(0f, 1f), 0f)
        } else {
            val breath = sin(ms / 500f)
            canvas.translate(0f, breath * .7f * size)
            canvas.scale(1f, 1f + breath * .012f, (l + r) / 2f, b)
        }
    }

    fun draw(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, mirrored: Boolean, opacity: Float) {
        if (!enabled || !state.visible || r <= l || b <= t) return
        schedule()
        val now = SystemClock.uptimeMillis()
        state.advance(now)
        if (!state.visible) return
        val ms = (now - state.since).toFloat()
        val size = max(r - l, b - t) * 1.12f / 96f
        fx.raw = canvas
        fx.begin()
        canvas.save()
        canvas.translate((l + r) / 2f, (t + b) / 2f)
        canvas.scale(size, size)
        if (state.status == "frz") {
            if (!state.clearing || ms < 440f) {
                cube.drawLook(fx, 0f, 0f, if (mirrored) -1f else 1f, 1f, opacity,
                    if (state.clearing) .5f else (ms % 4000f) / 4000f,
                    if (state.clearing) (ms / 400f).coerceAtMost(1f) else 0f)
            } else {
                val p = ((ms - 440f) / 560f).coerceIn(0f, 1f)
                fx.alpha = (1f - p) * opacity
                if (mirrored) canvas.scale(-1f, 1f)
                for (i in 0 until 18) {
                    val a = i * 2.399f
                    canvas.save()
                    canvas.translate(cos(a) * (12f + i * 2f + p * 100f), sin(a) * 35f - p * 65f + 130f * p * p)
                    canvas.rotate((a + p * 5f) * 180f / PI.toFloat())
                    fx.fillPoly(shard, 0xFFBEEBFF.toInt(), .75f)
                    fx.strokePoly(shard, 1.2f, 0xFFFFFFFF.toInt(), .95f)
                    canvas.restore()
                }
            }
        } else {
            val fade = if (state.clearing) (1f - ms / 350f).coerceAtLeast(0f) else 1f
            paint.color = zColor
            for (i in 0 until 3) {
                val p = ((ms / 1000f * .27f) + i / 3f) % 1f
                paint.alpha = (sin(p * PI).toFloat() * fade * opacity * 255f).toInt().coerceIn(0, 255)
                paint.textSize = 9f + p * 5f
                canvas.drawText("z", (30f + p * 13f) * (if (mirrored) -1f else 1f), -28f - p * 30f, paint)
            }
        }
        canvas.restore()
    }

    companion object {
        val COLD = ColorMatrixColorFilter(ColorMatrix(FxColor.chain(FxColor.saturate(.5f), FxColor.brightness(1.18f), FxColor.hueRotate(-8f))))
    }
}
