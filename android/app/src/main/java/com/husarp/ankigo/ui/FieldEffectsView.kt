package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.husarp.ankigo.R
import kotlin.math.min
import kotlin.math.sin

/** PLAN 694: lasting field art. Fixed particle slots and one 16 fps ticker, shared by weather and ground. */
internal class FieldEffectsView(ctx: Context) : View(ctx) {
    internal val state = FieldEffectsState()
    private val paint = Paint()
    private val imagePaint = Paint().apply { isFilterBitmap = false }
    private val imageRect = RectF()
    private val hailCell = Rect()
    private var hailArt: Bitmap? = null
    private var terrainArt: Bitmap? = null
    private var loadedTerrain: String? = null
    internal val terrainOffsetSeconds = kotlin.random.Random.nextFloat() * 1000f
    private val study = ctx.getSharedPreferences("study", 0)
    private val ui = ctx.getSharedPreferences("ankigo_ui", 0)
    private val watcher = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private var started = 0L
    internal val sandOffsetSeconds = kotlin.random.Random.nextFloat() * 1000f
    private var attached = false
    internal var running = false
        private set
    internal var stillMs: Float? = null
        set(value) { field = value; refresh() }
    private val tick = object : Runnable {
        override fun run() {
            running = false
            if (!canRun()) return
            repaint()
            schedule()
        }
    }
    var invalidateGround: () -> Unit = {}
    var groundTop = 0.4f
    var terrainTop = 0.4f
    val wildRing = FieldRing()
    val playerRing = FieldRing()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun motion() = ValueAnimator.areAnimatorsEnabled() && !study.getBoolean("reduceMotion", false) && AppPrefs.attackAnimations(context)
    private fun canRun() = attached && isShown && windowVisibility == VISIBLE && state.animated && motion() && stillMs == null
    private fun schedule() {
        if (!running && canRun()) { running = true; postDelayed(tick, 63L) }
    }
    private fun repaint() { invalidate(); invalidateGround() }
    fun refresh() {
        removeCallbacks(tick); running = false
        repaint()
        schedule()
    }
    fun changed() {
        if (state.weather == "hail" && hailArt == null) hailArt = BitmapFactory.decodeResource(resources, R.drawable.field_hail)
        if (loadedTerrain != state.terrain) {
            loadedTerrain = state.terrain
            terrainArt = when (state.terrain) {
                "electric" -> R.drawable.field_electric
                "grassy" -> R.drawable.field_grassy
                "misty" -> R.drawable.field_misty
                else -> null
            }?.let { BitmapFactory.decodeResource(resources, it) }
        }
        if (!state.active) started = 0L else if (started == 0L) started = SystemClock.uptimeMillis()
        refresh()
    }
    private fun time(): Float = (stillMs ?: if (motion()) (SystemClock.uptimeMillis() - started).toFloat() else 1000f) / 1000f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true
        study.registerOnSharedPreferenceChangeListener(watcher); ui.registerOnSharedPreferenceChangeListener(watcher)
        refresh()
    }
    override fun onDetachedFromWindow() {
        attached = false; removeCallbacks(tick); running = false
        study.unregisterOnSharedPreferenceChangeListener(watcher); ui.unregisterOnSharedPreferenceChangeListener(watcher)
        super.onDetachedFromWindow()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); refresh() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); refresh() }

    override fun onDraw(canvas: Canvas) {
        if (!state.active || width <= 0 || height <= 0) return
        val t = time()
        val save = canvas.save()
        canvas.scale(width / 180f, height / 160f)
        when (state.weather) {
            "rain" -> rain(canvas, t)
            "sun" -> sun(canvas, t)
            "sand" -> sand(canvas, t)
            "snow" -> snow(canvas, t)
            "hail" -> hail(canvas, t)
        }
        canvas.restoreToCount(save)
    }

    fun drawGround(canvas: Canvas) {
        if (!state.active || width <= 0 || height <= 0) return
        val t = time()
        terrain(canvas, t)
        if (state.psychic) {
            rings(canvas, wildRing, t, 0f)
            rings(canvas, playerRing, t, .43f)
        }
        if (state.waterSport) {
            val save = canvas.save()
            canvas.scale(width / 180f, height / 160f)
            for (i in 0 until 9) {
                val q = phase(t * .45f + i * .19f)
                oval(canvas, noise(i + 28) * 180f, groundTop * 160f + (160f - groundTop * 160f) * noise(i + 51),
                    3f + q * 16f, 1f + q * 4f, 0xffa5d9f3.toInt(), .75f * (1f - q))
            }
            canvas.restoreToCount(save)
        }
    }

    private fun terrain(c: Canvas, t: Float) {
        val art = terrainArt ?: return
        val top = terrainTop * height
        if (top >= height) return
        val save = c.save()
        c.clipRect(0f, top, width.toFloat(), height.toFloat())
        when (state.terrain) {
            "electric" -> {
                val a = if (motion()) FieldEffectsState.electricAlpha(t + terrainOffsetSeconds) else .24f
                imagePaint.alpha = (a * 255).toInt()
                imageRect.set(0f, top, width.toFloat(), height.toFloat())
                if (a > 0f) c.drawBitmap(art, null, imageRect, imagePaint)
            }
            "grassy" -> {
                imagePaint.alpha = 215
                imageRect.set(0f, top, width.toFloat(), height.toFloat())
                c.drawBitmap(art, null, imageRect, imagePaint)
            }
            "misty" -> {
                // Two slow, mirrored drifts, as in the selected preview.
                val shift = sin(t * (kotlin.math.PI / 9).toFloat()) * width * .072f
                imagePaint.alpha = 204
                imageRect.set(-width * .15f + shift, top, width * 1.15f + shift, height.toFloat())
                c.drawBitmap(art, null, imageRect, imagePaint)
                val drift = sin(t * (kotlin.math.PI / 13).toFloat()) * width * .083f
                c.translate(width.toFloat(), 0f); c.scale(-1f, 1f)
                imagePaint.alpha = 140
                imageRect.set(-width * .15f + drift, top - height * .0375f, width * 1.15f + drift, height - height * .0375f)
                c.drawBitmap(art, null, imageRect, imagePaint)
            }
        }
        c.restoreToCount(save)
    }

    private fun hail(c: Canvas, t: Float) {
        val art = hailArt ?: return
        imagePaint.alpha = 255
        for (i in 0 until 34) {
            val landing = (groundTop + (1f - groundTop) * noise(i + 1201)) * 158f
            val speed = FieldEffectsState.hailSpeed(i)
            val fall = (landing + 8f) / speed
            val period = fall + FieldEffectsState.HAIL_SPLASH_SECONDS + .12f + noise(i + 1301) * .25f
            val elapsed = phase(t / period + noise(i + 1401)) * period
            val x = noise(i + 1501) * 176f + 2f
            val radius = .65f + noise(i + 1601) * .35f
            if (elapsed < fall) {
                val y = -8f + elapsed * speed
                val xx = x + (landing - y) * .18f
                hailCell.set(i % 12 * 10, 0, i % 12 * 10 + 10, 10)
                imageRect.set(xx - radius, y - radius, xx + radius, y + radius)
                c.drawBitmap(art, hailCell, imageRect, imagePaint)
            } else if (elapsed < fall + FieldEffectsState.HAIL_SPLASH_SECONDS) {
                val q = (elapsed - fall) / FieldEffectsState.HAIL_SPLASH_SECONDS
                val r = radius * (1f + q)
                line(c, x - r, landing - r * (1f - q), x - r * .4f, landing, 0xffc5f4ff.toInt(), 1f - q)
                line(c, x + r * .4f, landing, x + r, landing - r * (1f - q), 0xffc5f4ff.toInt(), 1f - q)
            }
        }
    }

    private fun rings(c: Canvas, ring: FieldRing, t: Float, shift: Float) {
        if (!ring.visible || ring.rx <= 0f || ring.ry <= 0f) return
        for (i in 0 until 4) {
            val q = phase(t * .3f + i * .25f + shift)
            oval(c, ring.x, ring.y, ring.rx * q, ring.ry * q, 0xffd9a0fc.toInt(), .8f * (1f - q), min(width / 180f, height / 160f))
        }
    }

    private fun rain(c: Canvas, t: Float) {
        for (i in 0 until 34) {
            val y = phase(noise(i) + t * .69f) * 180f - 12f
            val x = phase(noise(i + 71) - y * .0015f) * 192f - 6f
            line(c, x, y, x - 3f, y + 7f, 0xffd5f3ff.toInt(), .65f)
        }
        // Jitter stays inside separated cells: neighbouring splash shapes never meet, even at the front.
        for (i in 0 until 40) {
            val q = phase(t * 1.15f + noise(i + 151))
            if (q > .44f) continue
            val depth = (i / 8 + .3f + noise(i + 231) * .4f) / 5f
            val x = (i % 8 + .3f + noise(i + 191) * .4f) * 22.5f
            val y = (groundTop + (1f - groundTop) * depth) * 160f
            val p = q / .44f
            val r = (1.5f + p * 3f) * (.35f + depth * .65f)
            val a = (1f - p) * .8f
            when (i % 3) {
                0 -> oval(c, x, y, r, r * .32f, 0xffd5f3ff.toInt(), a)
                1 -> {
                    line(c, x - r, y - r * .7f, x - r * .4f, y, 0xffd5f3ff.toInt(), a)
                    line(c, x, y - r, x, y - r * .3f, 0xffd5f3ff.toInt(), a)
                    line(c, x + r, y - r * .7f, x + r * .4f, y, 0xffd5f3ff.toInt(), a)
                }
                else -> {
                    line(c, x - r, y, x - r * .3f, y - r * .5f, 0xffd5f3ff.toInt(), a)
                    line(c, x - r * .3f, y - r * .5f, x + r * .25f, y, 0xffd5f3ff.toInt(), a)
                    line(c, x + r * .25f, y, x + r, y - r * .35f, 0xffd5f3ff.toInt(), a)
                }
            }
        }
    }

    private fun sun(c: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        for (i in 0 until 5) {
            val shift = 10f + i * 40f + sin(t * .6f) * 6f
            val alpha = .08f + .07f * sin(t + i)
            var y = 0
            while (y < 160) {
                val x = kotlin.math.floor(shift + y * .4f)
                ink(0xffffeaa0.toInt(), alpha)
                c.drawRect(x, y.toFloat(), x + 8f, y + 3f, paint)
                ink(0xffffe28a.toInt(), .07f)
                c.drawRect(x + 8f, y.toFloat(), x + 11f, y + 3f, paint)
                y += 3
            }
        }
    }

    private fun sand(c: Canvas, elapsed: Float) {
        val t = elapsed + sandOffsetSeconds
        val density = FieldEffectsState.sandDensity(t)
        paint.style = Paint.Style.FILL
        // Independent gusts overlap, with a different seed, direction and strength each cycle.
        for (i in 0 until 7) {
            val cycle = kotlin.math.floor(t / (2.3f + i * .37f)).toInt()
            val q = phase(t / (2.3f + i * .37f))
            val head = q * 360f - 90f
            val y = noise(cycle * 17 + i * 93) * 160f
            val thick = 5f + noise(cycle + i * 31) * 15f * density
            ink(0xffd9b86c.toInt(), density * density * (.12f + noise(cycle + i * 43) * .65f))
            var x = 0f
            while (x < 180f) {
                if (x > head - 90f && x < head + 90f) {
                    val wave = y + sin(x / 30f - t * 1.6f + i) * 7f
                    c.drawRect(x, wave, x + 4f, wave + thick, paint)
                }
                x += 4f
            }
        }
        for (i in 0 until 180) {
            if (noise(i + 401) > density) continue
            val speed = 45f + noise(i + 511) * 70f
            val x = phase(noise(i + 601) + t * speed / 200f) * 200f - 10f
            val y = phase(noise(i + 701) + sin(t * .5f + i) * .025f) * 160f
            ink(0xffe4bd75.toInt(), .4f + density * .4f)
            c.drawRect(x, y, x + 2f + noise(i + 801) * 4f, y + 1f, paint)
        }
    }

    private fun snow(c: Canvas, t: Float) {
        paint.style = Paint.Style.FILL
        for (i in 0 until 30) {
            val y = phase(noise(i + 19) + t * (12f + noise(i + 39) * 9f) / 170f) * 170f - 5f
            val x = phase(noise(i + 59) + sin(t * .8f + i) * .04f) * 180f
            val r = .22f + noise(i + 79) * .14f
            ink(0xffecf7ff.toInt(), .85f)
            c.drawCircle(x, y, r, paint)
        }
    }

    private fun ink(color: Int, alpha: Float) { paint.color = color; paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt() }
    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, alpha: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; ink(color, alpha); c.drawLine(x, y, xx, yy, paint)
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Float, unit: Float = 1f) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = unit; ink(color, alpha); c.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
    }
    private fun phase(x: Float) = x - kotlin.math.floor(x)
    private fun noise(i: Int) = FieldEffectsState.noise(i)
}

internal class FieldRing {
    var x = 0f
    var y = 0f
    var rx = 0f
    var ry = 0f
    var visible = false
}

/** Only the selected art is enabled; unselected field conditions retain their chips alone. */
internal class FieldEffectsState {
    var weather: String? = null
        private set
    var psychic = false
        private set
    var waterSport = false
        private set
    var terrain: String? = null
        private set
    val active: Boolean get() = weather != null || psychic || waterSport || terrain != null
    val animated: Boolean get() = weather != null || psychic || waterSport || terrain == "electric" || terrain == "misty"

    fun set(weather: String?, psychic: Boolean, waterSport: Boolean, suppressed: Boolean, terrain: String? = null) {
        this.weather = if (!suppressed && (weather == "rain" || weather == "sun" || weather == "sand" || weather == "snow" || weather == "hail")) weather else null
        this.psychic = psychic
        this.waterSport = waterSport
        this.terrain = terrain?.takeIf { it == "electric" || it == "grassy" || it == "misty" }
    }

    companion object {
        const val HAIL_SPLASH_SECONDS = .09f
        fun hailSpeed(i: Int): Float = 220f + noise(i + 1101) * 100f
        fun electricAlpha(t: Float): Float {
            val cycle = kotlin.math.floor(t / 6f).toInt()
            val elapsed = t - cycle * 6f - (.6f + noise(cycle + 1701) * 2.7f)
            val duration = .36f + noise(cycle + 1801) * .24f
            if (elapsed < 0f || elapsed >= duration) return 0f
            return .32f * sin(elapsed / duration * kotlin.math.PI.toFloat())
        }
        fun noise(i: Int): Float {
            var n = i * 374761393 + 668265263
            n = (n xor (n ushr 13)) * 1274126177
            return ((n xor (n ushr 16)) and 0xffff) / 65535f
        }
        fun sandDensity(t: Float): Float {
            // Smooth, irregular envelopes; the peak can obscure parts of the scene.
            val cycle = kotlin.math.floor(t / 3.1f).toInt()
            val q = t / 3.1f - cycle
            val blend = q * q * (3f - 2f * q)
            return .18f + .82f * (noise(cycle + 901) * (1f - blend) + noise(cycle + 902) * blend)
        }
    }
}
