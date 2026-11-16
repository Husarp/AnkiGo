package com.husarp.ankigo.ui.movefx

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.RectF
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
        val next = value?.takeIf { it == "frz" || it == "slp" || it == "psn" || it == "tox" || it == "attract" || it == "par" || it == "confusion" }
        if (immediate) { status = next; clearing = false; since = now; return }
        if (next != null) {
            if (next != status || clearing) { status = next; clearing = false; since = now }
        } else if (status != null && !clearing) { clearing = true; since = now }
    }

    fun advance(now: Long) {
        if (clearing && now - since >= duration()) { status = null; clearing = false }
    }

    enum class FrozenPhase { HIDDEN, HOLD, CRACK, SHATTER }
    fun frozenPhase(now: Long): FrozenPhase = when {
        status != "frz" -> FrozenPhase.HIDDEN
        !clearing -> FrozenPhase.HOLD
        now - since >= duration() -> FrozenPhase.HIDDEN
        now - since < 440L -> FrozenPhase.CRACK
        else -> FrozenPhase.SHATTER
    }

    fun duration(): Long = if (status == "frz") 1000L else 420L
}

/** One retained renderer per battle sprite. Geometry, paints, cube and shards are reused on every frame. */
internal class ConditionLook(private val view: View, private val redraw: () -> Unit) {
    val state = ConditionLookState()
    val loveState = ConditionLookState()
    val confusionState = ConditionLookState()
    val visible: Boolean get() = state.visible || loveState.visible || confusionState.visible
    private var loving = false
    private var confused = false
    private val crackle = ConditionCrackle()
    private val bolt = BitmapFactory.decodeResource(view.resources, com.husarp.ankigo.R.drawable.condition_lightning)
    private val bubble = BitmapFactory.decodeResource(view.resources, com.husarp.ankigo.R.drawable.condition_poison)
    private val heart = BitmapFactory.decodeResource(view.resources, com.husarp.ankigo.R.drawable.condition_heart)
    private val particleBounds = RectF()
    private val particlePaint = Paint()
    private var requested: String? = null
    private var queued = false
    private var attached = false
    private var enabled = false
    private val prefs = view.context.getSharedPreferences("study", 0)
    private val uiPrefs = view.context.applicationContext.getSharedPreferences("ankigo_ui", 0)
    private val watch = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "reduceMotion" || key == "attackAnimations") refresh()
    }
    var stageUnit = 1f
    private val fx = FxCanvas(FxArt.of(view.context), 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val zColor = if (view.resources.configuration.uiMode and 0x30 == 0x20) 0xFFA1AEFF.toInt() else 0xFF6A80FF.toInt()
    private val tick = object : Runnable {
        override fun run() {
            queued = false
            state.advance(SystemClock.uptimeMillis())
            loveState.advance(SystemClock.uptimeMillis())
            confusionState.advance(SystemClock.uptimeMillis())
            redraw()
            schedule()
        }
    }

    val cold: Boolean get() {
        if (!enabled) return false
        val phase = state.frozenPhase(SystemClock.uptimeMillis())
        return phase == ConditionLookState.FrozenPhase.HOLD || phase == ConditionLookState.FrozenPhase.CRACK
    }
    internal val running: Boolean get() = queued

    fun set(status: String?, immediate: Boolean = false) {
        if (immediate && status == null) { setLove(false, immediate = true); setConfused(false, immediate = true) }
        requested = status
        state.set(if (enabled) status else null, SystemClock.uptimeMillis(), immediate || !enabled)
        redraw()
        if (!canRun()) stop() else schedule()
    }

    fun setLove(value: Boolean, immediate: Boolean = false) {
        loving = value
        loveState.set(if (enabled && value) "attract" else null, SystemClock.uptimeMillis(), immediate || !enabled)
        redraw()
        if (!canRun()) stop() else schedule()
    }

    fun setConfused(value: Boolean, immediate: Boolean = false) {
        confused = value
        confusionState.set(if (enabled && value) "confusion" else null, SystemClock.uptimeMillis(), immediate || !enabled)
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
        loveState.set(if (enabled && loving) "attract" else null, SystemClock.uptimeMillis(), immediate = true)
        confusionState.set(if (enabled && confused) "confusion" else null, SystemClock.uptimeMillis(), immediate = true)
        // An exit interrupted by leaving the screen never appears on a different Pokémon later.
        state.set(if (enabled) requested else null, SystemClock.uptimeMillis(), immediate = true)
    }

    fun refresh() {
        val was = enabled
        enabled = ValueAnimator.areAnimatorsEnabled() && !prefs.getBoolean("reduceMotion", false) && AppPrefs.attackAnimations(view.context)
        if (was != enabled) {
            state.set(if (enabled) requested else null, SystemClock.uptimeMillis(), immediate = true)
            loveState.set(if (enabled && loving) "attract" else null, SystemClock.uptimeMillis(), immediate = true)
            confusionState.set(if (enabled && confused) "confusion" else null, SystemClock.uptimeMillis(), immediate = true)
        }
        redraw()
        if (!canRun()) stop() else schedule()
    }

    private fun canRun() = attached && enabled && visible && view.isShown && view.windowVisibility == View.VISIBLE && view.alpha > 0f && view.width > 0 && view.height > 0
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
        if (!enabled || !visible || r <= l || b <= t) return
        schedule()
        val now = SystemClock.uptimeMillis()
        state.advance(now)
        loveState.advance(now)
        confusionState.advance(now)
        if (!visible) return
        val ms = (now - state.since).toFloat()
        val size = max(r - l, b - t) * 1.12f / 96f
        fx.raw = canvas
        fx.begin()
        canvas.save()
        canvas.translate((l + r) / 2f, (t + b) / 2f)
        if (state.frozenPhase(now) != ConditionLookState.FrozenPhase.HIDDEN) {
            AiFrozenCube.draw(fx, r - l, b - t, stageUnit, ms, state.clearing, opacity)
        }
        canvas.scale(size, size)
        if (state.status == "slp") {
            val fade = if (state.clearing) (1f - ms / 350f).coerceAtLeast(0f) else 1f
            paint.color = zColor
            for (i in 0 until 3) {
                val p = ((ms / 1000f * .27f) + i / 3f) % 1f
                paint.alpha = (sin(p * PI).toFloat() * fade * opacity * 255f).toInt().coerceIn(0, 255)
                paint.textSize = 9f + p * 5f
                canvas.drawText("z", (30f + p * 13f) * (if (mirrored) -1f else 1f), -28f - p * 30f, paint)
            }
        }
        if (state.status == "psn" || state.status == "tox") particles(canvas, now, state, false, opacity)
        if (state.status == "par" && !state.clearing && crackle.visible(now, state.since)) lightning(canvas, ms, opacity)
        if (confusionState.visible) stars(canvas, now, opacity)
        if (loveState.visible) particles(canvas, now, loveState, true, opacity)
        canvas.restore()
    }

    private fun lightning(canvas: Canvas, ms: Float, opacity: Float) {
        for (i in 0 until 3) {
            canvas.save()
            canvas.translate(BOLT_X[i], BOLT_Y[i])
            canvas.rotate(BOLT_ANGLE[i])
            particleBounds.set(-6f + sin(ms / 67f + i) * 1.2f, -17.5f, 6f + sin(ms / 67f + i) * 1.2f, 17.5f)
            particlePaint.alpha = ((.5f + .45f * (.5f + .5f * sin(ms / 43f + i * 2.3f))) * opacity * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bolt, null, particleBounds, particlePaint)
            canvas.restore()
        }
    }

    private fun stars(canvas: Canvas, now: Long, opacity: Float) {
        val seconds = (now - confusionState.since) / 1000f
        val fade = if (confusionState.clearing) (1f - seconds / .42f).coerceIn(0f, 1f) else 1f
        paint.alpha = (fade * opacity * 255).toInt().coerceIn(0, 255)
        for (i in 0 until 6) {
            val angle = seconds * 2f * PI.toFloat() / 5.6f + i * PI.toFloat() / 3f
            val x = kotlin.math.round(cos(angle) * 53f) - 9f
            val y = kotlin.math.round(-59f + sin(angle) * 10f) - 9f
            for (layer in 0..1) {
                paint.color = if (layer == 0) 0xFFBD8518.toInt() else 0xFFF9D36A.toInt()
                paint.alpha = (fade * opacity * 255).toInt().coerceIn(0, 255)
                for (row in STAR.indices) for (col in STAR[row].indices) if (STAR[row][col] == '#') {
                    val top = y + row * 2f + if (layer == 0) 1f else -1f
                    canvas.drawRect(x + col * 2f, top, x + col * 2f + 2f, top + 2f, paint)
                }
            }
        }
    }

    private fun particles(canvas: Canvas, now: Long, state: ConditionLookState, hearts: Boolean, opacity: Float) {
        val seconds = (now - state.since) / 1000f
        val fade = if (state.clearing) (1f - seconds / .42f).coerceIn(0f, 1f) else 1f
        val count = if (hearts) 3 else 9
        for (i in 0 until count) {
            // Hearts keep the preview's flight time, with 10% fewer emissions over time.
            val p = if (hearts) (seconds * .27f + i / 3f) % 1f / .9f
                else (seconds * (.22f + i * .017f) + i * .381966f) % 1f
            if (p >= 1f) continue
            val x = if (hearts) (if (i % 2 == 1) 1f else -1f) * (25f + p * 14f) + sin(p * 7f) * 5f
                else (i % 3 - 1) * 30f + (i / 3 - 1) * 5f
            val y = if (hearts) 17f - p * 104f else 42f - p * 112f
            val width = if (hearts) 24f else 35f
            particleBounds.set(x - width / 2f, y - width / 2f, x + width / 2f, y + width / 2f)
            particlePaint.alpha = (sin(p * PI) * (if (hearts) .9f else .65f) * fade * opacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(if (hearts) heart else bubble, null, particleBounds, particlePaint)
        }
    }

    companion object {
        private val BOLT_X = floatArrayOf(-36f, 38f, 7f)
        private val BOLT_Y = floatArrayOf(-5f, 8f, 37f)
        private val BOLT_ANGLE = floatArrayOf(-14.3f, 17.2f, 65.9f)
        private val STAR = arrayOf("....#....", "...###...", "...###...", "#########", ".#######.", "..#####..", "..#####..", ".###.###.", ".#.....#.")
        val COLD = ColorMatrixColorFilter(ColorMatrix(FxColor.chain(FxColor.hueRotate(60f), FxColor.saturate(.75f), FxColor.brightness(1.12f))))
    }
}
