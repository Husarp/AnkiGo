package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.graphics.PathParser
import com.husarp.ankigo.game.PokemonRarity

/** PLAN 483, option 1C. Magic UI SparklesText (https://github.com/magicuidesign/magicui), MIT;
 * notice in assets/licences/magicui-LICENSE.txt.
 * Original star, rotation and burst envelope; twice the duration and one third the occupancy.
 */
internal object RarityFrame {
    data class Effect(val color: Int, val stars: Int, val intensity: Float)

    fun effect(rarity: PokemonRarity?, dark: Boolean): Effect? = when (rarity) {
        PokemonRarity.ULTRA -> Effect(if (dark) 0xffc191fa.toInt() else 0xff842ef4.toInt(), 3, 1f)
        PokemonRarity.LEGENDARY -> Effect(if (dark) 0xffffa770.toInt() else 0xffff6004.toInt(), 5, 1.3f)
        PokemonRarity.MYTHICAL -> Effect(if (dark) 0xffff5b59.toInt() else 0xffd20934.toInt(), 7, 1.65f)
        else -> null
    }

    const val BURST_MS = 5600L // original 2800 ms, including its quiet tail
    const val CYCLE_MS = BURST_MS * 3
    fun envelope(timeMs: Long): Float {
        val phase = (timeMs % CYCLE_MS).toFloat() / BURST_MS
        return when {
            phase < .14f -> ease(phase / .14f)
            phase < .29f -> 1f - ease((phase - .14f) / .15f)
            else -> 0f
        }
    }
    private fun ease(t: Float) = t * t * (3 - 2 * t)

    fun bind(view: View, rarity: PokemonRarity?, cornerDp: Int = 12) {
        val dark = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val effect = effect(rarity, dark)
        val old = view.foreground as? Frame
        if (effect == null) {
            old?.dispose()
            if (old != null) view.foreground = null
        } else if (old?.effect != effect) {
            old?.dispose()
            view.foreground = Frame(view, effect, cornerDp)
        }
    }

    internal class Frame(private val view: View, val effect: Effect, cornerDp: Int) : Drawable(),
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val visibleRect = Rect()
        private val density = view.resources.displayMetrics.density
        private val corner = cornerDp * density
        private var observer: ViewTreeObserver? = null
        internal var frozenTimeMs: Long? = null

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }
        override fun onViewAttachedToWindow(v: View) {
            observer = v.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        }
        override fun onViewDetachedFromWindow(v: View) {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
            Clock.remove(this)
        }
        fun dispose() {
            onViewDetachedFromWindow(view)
            view.removeOnAttachStateChangeListener(this)
        }
        override fun onPreDraw(): Boolean { refresh(); return true }
        internal fun refresh(enabled: Boolean = ValueAnimator.areAnimatorsEnabled()) {
            if (enabled && frozenTimeMs == null && view.isAttachedToWindow && view.windowVisibility == View.VISIBLE &&
                view.isShown && view.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty) Clock.add(this)
            else Clock.remove(this)
        }
        internal fun tick() {
            refresh()
            if (this in Clock.active) view.invalidate()
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val inset = .75f * density
            paint.color = effect.color
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            canvas.drawRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, corner, corner, paint)
            if (frozenTimeMs == null && !ValueAnimator.areAnimatorsEnabled()) return
            val time = frozenTimeMs ?: Clock.timeMs
            paint.style = Paint.Style.FILL
            val side = 8 * density * effect.intensity
            for (i in 0 until effect.stars) {
                // The page's placements and 390 ms stagger, slowed 2x and spread 3x further apart.
                val elapsed = time + i * 2340L + (view.id.coerceAtLeast(0) % 17) * 137L
                val alpha = envelope(elapsed)
                if (alpha <= 0f) continue
                val p = anchors[i]
                val save = canvas.save()
                // Inset enough to keep the whole star within the card's clipped bounds.
                val margin = side / 2
                canvas.translate(b.left + margin + (b.width() - side) * p.first,
                    b.top + margin + (b.height() - side) * p.second)
                val phase = (elapsed % CYCLE_MS).toFloat() / BURST_MS
                canvas.rotate(if (phase < .14f) 75 + 45 * ease(phase / .14f) else 120 + 30 * ease(((phase - .14f) / .15f).coerceIn(0f, 1f)))
                canvas.scale(side / 21 * alpha, side / 21 * alpha)
                canvas.translate(-10.5f, -10.5f)
                paint.alpha = (alpha * 255).toInt()
                canvas.drawPath(star, paint)
                canvas.restoreToCount(save)
            }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    internal object Clock : Choreographer.FrameCallback {
        val active = linkedSetOf<Frame>()
        var timeMs = 0L
            private set
        internal var posted = false
            private set
        fun add(frame: Frame) {
            active.add(frame)
            if (!posted) { posted = true; Choreographer.getInstance().postFrameCallback(this) }
        }
        fun remove(frame: Frame) {
            active.remove(frame)
            if (active.isEmpty() && posted) {
                Choreographer.getInstance().removeFrameCallback(this)
                posted = false
            }
        }
        override fun doFrame(frameTimeNanos: Long) {
            posted = false
            timeMs = frameTimeNanos / 1_000_000
            active.toList().forEach { it.tick() }
            if (active.isNotEmpty() && !posted) {
                posted = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private val anchors = listOf(.15f to 0f, 1f to .55f, .38f to 1f, 1f to .12f, 0f to .68f, .65f to 0f, 1f to .88f)
    private val star by lazy { PathParser.createPathFromPathData("M9.82531 0.843845C10.0553 0.215178 10.9446 0.215178 11.1746 0.843845L11.8618 2.72026C12.4006 4.19229 12.3916 6.39157 13.5 7.5C14.6084 8.60843 16.8077 8.59935 18.2797 9.13822L20.1561 9.82534C20.7858 10.0553 20.7858 10.9447 20.1561 11.1747L18.2797 11.8618C16.8077 12.4007 14.6084 12.3916 13.5 13.5C12.3916 14.6084 12.4006 16.8077 11.8618 18.2798L11.1746 20.1562C10.9446 20.7858 10.0553 20.7858 9.82531 20.1562L9.13819 18.2798C8.59932 16.8077 8.60843 14.6084 7.5 13.5C6.39157 12.3916 4.19225 12.4007 2.72023 11.8618L0.843814 11.1747C0.215148 10.9447 0.215148 10.0553 0.843814 9.82534L2.72023 9.13822C4.19225 8.59935 6.39157 8.60843 7.5 7.5C8.60843 6.39157 8.59932 4.19229 9.13819 2.72026L9.82531 0.843845Z")!! }
}
