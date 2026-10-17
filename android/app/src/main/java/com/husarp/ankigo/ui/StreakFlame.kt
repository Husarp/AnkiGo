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
import android.view.View
import android.widget.ImageView
import com.husarp.ankigo.R

/**
 * The streak flame: Google's animated Noto fire (res/raw/streak_flame.webp, CC BY 4.0). Animated on API 28+ while it is on
 * screen; the still first frame (drawable-nodpi/streak_flame_still.png) on API 26-27, with reduced motion, in the screenshot
 * renderer or when decoding fails. [alive] = false (streak 0) shows the still frame grey and faded. Size it with layout params.
 */
class StreakFlame(ctx: Context) : ImageView(ctx) {
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
            if (anim == null && !triedDecode) decode()
            val a = anim
            if (a != null) { if (drawable !== a) { setImageDrawable(a); stillShown = false } } else setStill()
        } else {
            stop()
            setStill()
        }
        sync()
    }

    private fun setStill() { if (!stillShown) { setImageResource(R.drawable.streak_flame_still); stillShown = true } }

    private fun decode() {
        triedDecode = true
        try {
            val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, R.raw.streak_flame))
            if (d is AnimatedImageDrawable) { d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; anim = d } else decodeFailed = true
        } catch (_: Throwable) { decodeFailed = true }
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
        sync()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        sync()
    }

    companion object {
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
