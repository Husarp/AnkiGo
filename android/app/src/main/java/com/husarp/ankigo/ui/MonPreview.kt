package com.husarp.ankigo.ui

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import kotlin.math.roundToInt
import com.husarp.ankigo.study.StudyRules

/**
 * Single-Pokémon previews (team rows, detail sheets, pickers, Dex hero): the looping battle GIF when the FRONT sprite is
 * already cached as a GIF, otherwise exactly [Ui.staticSprite]. Grids (box, Pokédex list) keep the static sprite on purpose
 * (dozens of decoded animations would cost memory and CPU).
 */
internal object MonPreview {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Pure choice: ANIMATED when the cached front sprite is a GIF, else STATIC when the PNG is on disk, else PLACEHOLDER. */
    fun source(cachedFrontName: String?, staticExists: Boolean): StudyRules.SpriteSource =
        StudyRules.spriteSource(cachedFrontName?.endsWith(".gif", ignoreCase = true) == true, staticExists)

    /** Frame durations for an AnimationDrawable: at least 20 ms (same floor as SpriteView). */
    fun durations(delays: IntArray, frames: Int): IntArray = IntArray(frames) { delays.getOrElse(it) { 100 }.coerceAtLeast(20) }

    /**
     * Shows species [num] in [view]. Never starts a download: a missing sprite stays the static PNG / placeholder (the
     * static path may still fetch its own PNG, as before). The GIF is decoded off the main thread; the newest request per
     * view wins. The animation runs while the view is attached and visible (ImageView toggles Drawable.setVisible).
     */
    fun show(ctx: Context, num: Int, shiny: Boolean, view: ImageView, silhouette: Boolean = false, idle: Boolean = false) {
        // PLAN 205 (R14): a view reused for another Pokémon starts over; [idle] (the three details pictures) floats a STILL picture only
        IdleFloat.stop(view)
        val sp = GameRepo.data?.species?.get(num)
        val front = if (sp == null || silhouette) null else Sprites.cached(ctx, sp, SpriteKind.FRONT, shiny)
        val static = Sprites.staticFile(ctx, num, shiny).isFile
        if (sp == null || source(front?.name, static) != StudyRules.SpriteSource.ANIMATED) {
            Ui.staticSprite(ctx, num, shiny, view, silhouette)
            if (idle && !silhouette) IdleFloat.start(view)
            return
        }
        // Animated sprite is on disk: static picture (or placeholder, no download) until the decode lands.
        Ui.staticSprite(ctx, num, shiny, view, download = false)
        val token = Any()
        view.setTag(R.id.ag_tag_sprite, token)
        PanelSprites.pool.execute {
            val img = Sprites.decode(front!!)
            main.post {
                if (view.getTag(R.id.ag_tag_sprite) !== token) return@post
                if (img == null || !img.animated) { if (idle) IdleFloat.start(view); return@post } // the decode failed: it stays a still picture
                IdleFloat.stop(view)
                view.setImageDrawable(animation(ctx, img))
                (view.drawable as? AnimationDrawable)?.start()
            }
        }
    }

    /** PLAN 205: the idle float's vertical shift in px (up = negative) at [phase] for a screen of [density]: the same height as [SpriteView]'s. */
    fun idleOffsetPx(phase: Float, density: Float): Float = -(SpriteView.IDLE_FLOAT_DP * density * SpriteView.idleLift(phase)).roundToInt().toFloat()

    /** True while [view] has the idle float running (for tests). */
    fun idleRunning(view: ImageView): Boolean = IdleFloat.running(view)

    /**
     * PLAN 205: the idle motion of a STILL details picture: translationY + scaleY about the view's bottom, [SpriteView]'s maths, period and
     * height. Runs only while the view is attached and shown, with animators on and no reduced motion ([SpriteView.idleShouldRun]); it is
     * stopped on detach, when the window is hidden, when the view is hidden, when an animation replaces the picture and when the view is
     * reused for another Pokémon. The controller lives in the view's own tag (nothing static holds a view, so a detail view and its
     * Activity can be collected); while attached it listens to the window visibility and to each draw pass (a cheap check) so a view
     * that becomes hidden stops and one that becomes visible starts, and it removes both listeners on detach.
     */
    private class IdleFloat(private val view: ImageView) : View.OnAttachStateChangeListener,
        ViewTreeObserver.OnWindowVisibilityChangeListener, ViewTreeObserver.OnPreDrawListener {
        private var anim: ValueAnimator? = null
        private var observer: ViewTreeObserver? = null
        private var hideCheck = false
        var starts = 0
            private set
        val running: Boolean get() = anim != null

        /** [windowVis]: the visibility just reported by the window listener (the view's own attach info may lag behind it). */
        fun update(detaching: Boolean = false, windowVis: Int = view.windowVisibility) {
            val shown = !detaching && view.isAttachedToWindow && windowVis == View.VISIBLE && view.isShown
            if (!shown) { if (anim != null || detaching) rest(); return }
            if (anim != null) return
            val ctx = view.context
            val run = SpriteView.idleShouldRun(true, true, false, ValueAnimator.areAnimatorsEnabled(),
                ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false), true)
            if (run) {
                starts++
                val density = ctx.resources.displayMetrics.density
                anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = SpriteView.IDLE_PERIOD_MS
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        // an attached but hidden view (INVISIBLE draws nothing, so no draw pass notices): stop after this tick
                        if (!view.isShown) {
                            if (!hideCheck) { hideCheck = true; view.post { hideCheck = false; update() } }
                            return@addUpdateListener
                        }
                        val p = it.animatedValue as Float
                        view.pivotX = view.width / 2f; view.pivotY = view.height.toFloat()
                        view.translationY = idleOffsetPx(p, density)
                        view.scaleY = SpriteView.idleBreath(p)
                    }
                    start()
                }
            }
        }

        fun rest() {
            anim?.let { it.removeAllUpdateListeners(); it.cancel() }
            anim = null
            view.translationY = 0f; view.scaleY = 1f
        }

        private fun listen() {
            unlisten()
            observer = view.viewTreeObserver.also {
                if (it.isAlive) { it.addOnWindowVisibilityChangeListener(this); it.addOnPreDrawListener(this) }
            }
        }

        private fun unlisten() {
            observer?.takeIf { it.isAlive }?.let { it.removeOnWindowVisibilityChangeListener(this); it.removeOnPreDrawListener(this) }
            observer = null
        }

        /** Detaches every listener and puts the view back at rest (the view is shown again or reused). */
        fun release() {
            view.removeOnAttachStateChangeListener(this)
            unlisten()
            rest()
        }

        override fun onViewAttachedToWindow(v: View) { listen(); update() }
        override fun onViewDetachedFromWindow(v: View) { unlisten(); update(detaching = true) }
        override fun onWindowVisibilityChanged(visibility: Int) = update(windowVis = visibility)
        override fun onPreDraw(): Boolean { update(); return true }

        companion object {
            fun start(view: ImageView) {
                val f = (view.getTag(R.id.ag_tag_idle_float) as? IdleFloat)
                    ?: IdleFloat(view).also { view.setTag(R.id.ag_tag_idle_float, it); view.addOnAttachStateChangeListener(it) }
                // already attached when started (a view reused in place): listen now, the attach callback will not come
                if (f.observer == null && view.isAttachedToWindow) f.listen()
                f.update()
            }

            fun stop(view: ImageView) {
                val f = view.getTag(R.id.ag_tag_idle_float) as? IdleFloat ?: return
                view.setTag(R.id.ag_tag_idle_float, null)
                f.release()
            }

            fun running(view: ImageView): Boolean = (view.getTag(R.id.ag_tag_idle_float) as? IdleFloat)?.running == true
            fun starts(view: ImageView): Int = (view.getTag(R.id.ag_tag_idle_float) as? IdleFloat)?.starts ?: 0
            fun attached(view: ImageView): Boolean = (view.getTag(R.id.ag_tag_idle_float) as? IdleFloat)?.observer != null
        }
    }

    /** True while the idle float of [view] holds window listeners (for tests: none after detach). */
    fun idleListening(view: ImageView): Boolean = IdleFloat.attached(view)

    /** How many times the idle float was started on [view] since it was last stopped (for tests). */
    fun idleStarts(view: ImageView): Int = IdleFloat.starts(view)

    private fun animation(ctx: Context, img: SpriteImage): AnimationDrawable {
        val times = durations(img.delays, img.frames.size)
        return AnimationDrawable().apply {
            img.frames.forEachIndexed { i, bmp -> addFrame(Ui.pixelBitmap(ctx, bmp), times[i]) }
            isOneShot = false
        }
    }
}
