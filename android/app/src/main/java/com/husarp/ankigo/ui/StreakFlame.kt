package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import android.view.View
import android.widget.ImageView
import com.husarp.ankigo.R

/**
 * The streak flame: Noto fire artwork by Google with a seamless 4.8-second vertical-lick animation (res/raw/streak_flame.webp, CC BY 4.0). Animated on API 28+ while it is on
 * screen; the original still artwork (drawable-nodpi/streak_flame_still.png) on API 26-27, with reduced motion, in the screenshot
 * renderer or when decoding fails. [alive] = false (streak 0) shows the still frame grey and faded. Size it with layout params.
 */
class StreakFlame internal constructor(ctx: Context,
    private val decodeImage: (android.content.res.Resources) -> android.graphics.drawable.Drawable?,
    private val decodeIo: java.util.concurrent.Executor,
    private val deliver: (Runnable) -> Unit = { main.post(it); Unit },
) : ImageView(ctx) {
    constructor(ctx: Context) : this(ctx, { res ->
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(res, R.raw.streak_flame))
    }, decoder)
    private var anim: AnimatedImageDrawable? = null
    private var triedDecode = false
    private var decodeFailed = false
    private var windowVisible = false
    private var stillShown = false

    /** False at a streak of 0: grey, faded, never moving. */
    var alive: Boolean = true
        set(v) { if (field != v) { field = v; refresh() } }

    init {
        scaleType = ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageResource(R.drawable.streak_flame_still)
        stillShown = true
        refresh()
    }

    private fun reducedMotion() = !ValueAnimator.areAnimatorsEnabled() ||
        context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    private fun picture() = StreakFlameRules.picture(Build.VERSION.SDK_INT, reducedMotion(), renderer(), alive, decodeFailed)

    /** Paparazzi / layoutlib has no real ImageDecoder animation: it gets the still frame. */
    private fun renderer() = isInEditMode || Build.DEVICE == "layoutlib" || Build.FINGERPRINT.contains("layoutlib")

    /** Picks still or animated, greys it when not alive, and starts or stops the animation. */
    private fun refresh() {
        imageAlpha = StreakFlameRules.alpha(alive)
        colorFilter = if (alive) null else GREY
        if (picture() == StreakFlameRules.Picture.ANIMATED) {
            if (anim == null && !triedDecode && isAttachedToWindow && windowVisible && isShown) decode()
            val a = anim
            if (a != null) { if (drawable !== a) { setImageDrawable(a); stillShown = false } } else setStill()
        } else {
            stop()
            setStill()
        }
        sync()
    }

    private fun setStill() { if (!stillShown) { setImageResource(R.drawable.streak_flame_still); stillShown = true } }

    /** Keep the still frame usable while the animated image is decoded once on the worker. */
    internal fun decode() {
        if (triedDecode) return
        triedDecode = true
        val res = resources
        decodeIo.execute {
            val d = com.husarp.ankigo.debug.LagLog.work("streak decode") {
                runCatching {
                    decodeImage(res)?.also { if (it is AnimatedImageDrawable) it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
                }.getOrNull()
            }
            deliver(Runnable {
                anim = d as? AnimatedImageDrawable
                decodeFailed = anim == null
                refresh()
            })
        }
    }

    private fun stop() { try { anim?.stop() } catch (_: Throwable) {} }

    private fun sync() {
        val a = anim ?: return
        try {
            val run = StreakFlameRules.shouldRun(picture(), isAttachedToWindow, windowVisible, isShown)
            if (run) { if (!a.isRunning) a.start() } else if (a.isRunning) a.stop()
        } catch (_: Throwable) { decodeFailed = true }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        windowVisible = windowVisibility == VISIBLE
        refresh()
    }

    override fun onDetachedFromWindow() {
        windowVisible = false
        sync()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        windowVisible = visibility == VISIBLE
        if (isAttachedToWindow) refresh() else sync()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) refresh() else sync()
    }

    companion object {
        private val main by lazy { Handler(Looper.getMainLooper()) }
        private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r, "streak-decode").apply { isDaemon = true } }
        private val GREY = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        private val stills = LinkedHashMap<Int, Bitmap>()

        /** The still flame at [sizePx] (square) for canvas drawing, e.g. widgets; cached, null when it cannot be decoded. */
        @Synchronized
        fun stillBitmap(ctx: Context, sizePx: Int): Bitmap? {
            val size = sizePx.coerceAtLeast(1)
            stills[size]?.let { return it }
            return try {
                var b = BitmapFactory.decodeResource(ctx.resources, R.drawable.streak_flame_still) ?: return null
                // halve step by step so a big downscale stays crisp
                while (b.width / 2 >= size) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
                if (b.width != size) b = Bitmap.createScaledBitmap(b, size, size, true)
                if (stills.size >= 8) stills.remove(stills.keys.first())
                stills[size] = b
                b
            } catch (_: Throwable) { null }
        }
    }
}
