package com.husarp.ankigo.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import com.husarp.ankigo.R

/**
 * Rounded progress bar (mockup `.hpbar` / `.xpbar` / `.bar2`): `ag_s0` track, coloured fill, corners = half the height.
 * Set its height from the layout (default 8 dp, wrap_content); [set] eases the fill over 450 ms.
 */
class BarView(ctx: Context) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_s0) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val unitGradient = Matrix()
    private var gradient: LinearGradient? = null
    private var gradFrom = 0
    private var gradTo = 0

    private var shown = 0f      // fraction currently drawn
    private var color = Ui.c(ctx, R.color.ag_green)
    private var anim: ValueAnimator? = null

    // "Ghost" of an HP drop: the part just lost, drawn behind the fill in a soft darkened red, held a moment, then shrinking away.
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Bits.blend(Ui.c(ctx, R.color.ag_red), 0xFF000000.toInt(), 0.6f)
        alpha = 165
    }
    private var ghostFrac = 0f
    private var ghostAnim: ValueAnimator? = null

    /** True on the HP bars: a drop leaves a fading ghost of what was lost. */
    var ghostOnDrop = false

    /**
     * Shows the ghost up to [from] (never below where the fill is heading, [dest]); it holds [GHOST_HOLD_MS],
     * then shrinks to the fill. [dest] defaults to what is drawn now; [set] passes the new fraction, because
     * the fill is still at the old one when the drop starts.
     */
    internal fun ghostFrom(from: Float, dest: Float = shown) {
        ghostAnim?.cancel()
        ghostFrac = ghostTop(from, dest)
        if (ghostFrac == 0f) { invalidate(); return }
        ghostAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = GHOST_HOLD_MS
            duration = GHOST_SHRINK_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            val top = ghostFrac
            addUpdateListener { ghostFrac = dest + (top - dest) * (it.animatedValue as Float); invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { ghostFrac = 0f; invalidate() }
            })
            start()
        }
        invalidate()
    }

    /** Draws the ghost still (no animation); screenshots use it. */
    internal fun ghostStill(from: Float) { ghostAnim?.cancel(); ghostFrac = from.coerceIn(0f, 1f); invalidate() }

    // ---- a label written inside the bar (Round 9 D12-B: "245 / 312" in the info box): dark text with a light glow, readable on every fill and on the empty track ----
    private var label: String? = null
    private var labelSizeSp = 0f
    private var labelPx = 0f
    private val labelInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1E2E.toInt(); textAlign = Paint.Align.CENTER; typeface = Ui.font(ctx, 900); letterSpacing = 0.02f
    }
    private val labelGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Ui.font(ctx, 900); letterSpacing = 0.02f
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val digitBounds = android.graphics.Rect()

    /** The text written inside the bar (null = none) and its size in sp. A label wider than the bar is drawn smaller (not under 8 dp), else not at all (never cut off). */
    fun setLabel(text: String?, sp: Float) {
        if (text == label && sp == labelSizeSp) return
        label = text
        labelSizeSp = sp
        labelPx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        labelSet(labelPx)
        invalidate()
    }

    /** The label and its size in sp (tests). */
    internal fun labelText(): String? = label
    internal fun labelSp(): Float = if (label == null) 0f else labelSizeSp

    private val meter = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 900); letterSpacing = 0.02f }
    private val meterBounds = android.graphics.Rect()

    /**
     * The height (px) a bar needs for a label of [sp]: the glyphs ("0", "/", "X", "P" about the digits' middle) plus the glow stroke
     * on both sides. The label's size follows the system font scale but a bar's height does not, so the card sets the bar at least
     * this tall ([ArenaView.fitBars]); otherwise the text and glow are cut off at a big font size.
     */
    internal fun labelNeedPx(sp: Float): Int {
        val px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        meter.textSize = px
        meter.getTextBounds("0", 0, 1, digitBounds)
        val cy = digitBounds.exactCenterY()
        meter.getTextBounds("0/XP", 0, 4, meterBounds)
        val half = maxOf(cy - meterBounds.top, meterBounds.bottom - cy, digitBounds.height() / 2f)
        val stroke = maxOf(1.5f * resources.displayMetrics.density, px * 0.22f)
        return kotlin.math.ceil(2f * half + stroke).toInt()
    }

    /** The text size (px) the label is drawn at: as asked, or smaller (not under 8 dp) when the bar is too narrow for it; 0 = not drawn. */
    private fun labelDrawPx(): Float {
        val t = label ?: return 0f
        if (t.isEmpty() || labelSizeSp <= 0f || width <= 0) return 0f
        val d = resources.displayMetrics.density
        val avail = width - 4f * d
        labelSet(labelPx)
        val need = labelInk.measureText(t)
        if (need <= avail) return labelPx
        val fit = labelPx * avail / need
        return if (fit >= minOf(labelPx, 8f * d)) fit else 0f
    }

    private fun labelSet(px: Float) {
        labelInk.textSize = px; labelGlow.textSize = px
        labelGlow.strokeWidth = maxOf(1.5f * resources.displayMetrics.density, px * 0.22f)
    }

    /** True when the label is drawn (it fits the bar, shrunk a little at a big font size) and the bar is tall enough for it (tests). */
    internal fun labelShown(): Boolean = labelDrawPx() > 0f && height >= labelNeedPx(labelSizeSp)

    private fun drawLabel(canvas: Canvas) {
        val t = label ?: return
        val px = labelDrawPx()
        if (px <= 0f) return
        labelSet(px)
        labelInk.getTextBounds("0", 0, 1, digitBounds)
        val y = height / 2f - digitBounds.exactCenterY()
        canvas.drawText(t, width / 2f, y, labelGlow)
        canvas.drawText(t, width / 2f, y, labelInk)
    }

    /** Track colour, `ag_s0` unless changed. */
    fun setTrackColor(argb: Int) { track.color = argb; invalidate() }

    /** Fill as a horizontal gradient (e.g. mauve -> pink for the goal bar) instead of the plain colour from [set]. */
    fun setGradient(fromColor: Int, toColor: Int) {
        gradFrom = fromColor
        gradTo = toColor
        gradient = LinearGradient(0f, 0f, 1f, 0f, fromColor, toColor, Shader.TileMode.CLAMP)
        invalidate()
    }

    /** Back to a plain-colour fill. */
    fun clearGradient() { gradient = null; invalidate() }

    /**
     * Fills [fraction] (0..1) in [fillColor] (ignored while a gradient is set). [animate] eases width and colour
     * over 450 ms with the mockup's cubic-bezier(.4,0,.2,1); false jumps there.
     */
    fun set(fraction: Float, fillColor: Int, animate: Boolean) {
        val to = fraction.coerceIn(0f, 1f)
        anim?.cancel()
        anim = null
        if (ghostOnDrop) {
            if (animate && isAttachedToWindow && to < shown) ghostFrom(maxOf(shown, ghostFrac), to)
            else if (to >= shown || !animate) { ghostAnim?.cancel(); ghostFrac = 0f }
        }
        if (!animate || !isAttachedToWindow || (to == shown && fillColor == color)) {
            shown = to
            color = fillColor
            invalidate()
            return
        }
        val fromFrac = shown
        val fromColor = color
        val ev = ArgbEvaluator()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                shown = fromFrac + (to - fromFrac) * t
                color = ev.evaluate(t, fromColor, fillColor) as Int
                invalidate()
            }
            start()
        }
    }

    // ---- goal sparks: tiny sparks rising above the bar once the daily goal is passed (GoalSparks) ----
    private var sparkLevel = 0
    private var sparkHost: ViewGroup? = null
    private var sparkDrawable: SparkDrawable? = null
    private var sparkRunning = false
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkRect = android.graphics.Rect()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowRect = RectF()
    private var glowRings: List<GoalGlow.Ring> = emptyList()
    private var glowKey = 0L    // (level, room in px) the cached rings were made for
    private val sparkTick = object : Runnable {
        override fun run() {
            if (!sparkRunning) return
            val ok = GoalSparks.keepTicking(sparkLevel, isAttachedToWindow, isShown, windowVisibility == VISIBLE, sparksStill())
            sparkDrawable?.refresh()
            if (!ok) { stopTicker(); if (sparkLevel == 0 || !isAttachedToWindow || !isShown) stopSparks(); return }
            postOnAnimationDelayed(this, SPARK_FRAME_MS)
        }
    }

    /**
     * Sparks above the bar for the goal's flame [level] (0 = none, else [GoalTone.flameLevel]'s 1..3). They are drawn in the
     * overlay of the bar's grandparent (so no layout changes and nothing takes touches) and animate only while the bar is
     * attached and shown; with reduced motion they stand still.
     */
    fun setGoalSparks(level: Int) {
        val l = level.coerceIn(0, 3)
        if (l == sparkLevel) return
        sparkLevel = l
        refreshSparks()
    }

    private fun sparksStill() = !ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    private fun refreshSparks() {
        val want = sparkLevel > 0 && isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (!want) { stopSparks(); return }
        if (sparkDrawable == null) {
            val host = (parent as? ViewGroup)?.let { it.parent as? ViewGroup ?: it } ?: return
            sparkHost = host
            sparkDrawable = SparkDrawable(host).also { host.overlay.add(it) }
        }
        sparkDrawable?.refresh()
        if (sparksStill()) { stopTicker(); return }
        if (!sparkRunning) { sparkRunning = true; postOnAnimationDelayed(sparkTick, SPARK_FRAME_MS) }
    }

    private fun stopTicker() { sparkRunning = false; removeCallbacks(sparkTick) }

    private fun stopSparks() {
        stopTicker()
        val d = sparkDrawable ?: return
        sparkHost?.overlay?.remove(d)
        sparkDrawable = null
        sparkHost = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSparks()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshSparks()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        refreshSparks()
    }

    /**
     * Draws the golden outline + glow round the bar and the sparks over its top edge, into the host's coordinates; computed at
     * draw time, so a moved bar is followed. The glow may reach as far as the host has room round the bar.
     */
    private inner class SparkDrawable(private val host: ViewGroup) : Drawable() {
        private val r = android.graphics.Rect()
        /**
         * Pixels per spark-dp: the sizes are for an 8 dp bar and follow the bar's real height, but a taller bar (the 14 dp goal
         * bar outside the battle) does not make the sparks and the glow bigger.
         */
        private val k get() = minOf(this@BarView.height.toFloat(), 8f * resources.displayMetrics.density) / GoalSparks.BAR_DP

        /** Pixels the glow may reach beyond the bar: the smallest gap between the bar and the host's edges. */
        private fun room(r: android.graphics.Rect): Int =
            minOf(minOf(r.left, host.width - r.right), minOf(r.top, host.height - r.bottom)).coerceAtLeast(0)

        /** The rings for the current level and room, made again only when one of them changes. */
        private fun rings(room: Int): List<GoalGlow.Ring> {
            val key = sparkLevel * 1_000_000L + room
            if (key != glowKey) { glowKey = key; glowRings = GoalGlow.rings(sparkLevel, room / k) }
            return glowRings
        }

        /** Moves the drawn area to where the bar is now and asks the host to redraw it. */
        fun refresh() {
            if (this@BarView.width == 0 || this@BarView.height == 0 || !host.isAttachedToWindow) return
            r.set(0, 0, this@BarView.width, this@BarView.height)
            host.offsetDescendantRectToMyCoords(this@BarView, r)
            val glow = (GoalGlow.extent(sparkLevel) * k).toInt().coerceAtMost(room(r))
            val pad = maxOf((GoalSparks.MAX_RADIUS * k).toInt(), glow) + 2
            sparkRect.set(r.left - pad, r.top - maxOf((GoalSparks.maxRise(3) * k).toInt(), glow) - pad, r.right + pad, r.bottom + glow + 2)
            bounds = sparkRect
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            if (this@BarView.width == 0 || this@BarView.height == 0 || sparkLevel == 0) return
            r.set(0, 0, this@BarView.width, this@BarView.height)
            host.offsetDescendantRectToMyCoords(this@BarView, r)
            for (g in rings(room(r))) {
                val o = g.outset * k
                glowPaint.color = g.color
                glowPaint.alpha = (g.alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                glowPaint.strokeWidth = g.width * k
                glowRect.set(r.left - o, r.top - o, r.right + o, r.bottom + o)
                canvas.drawRoundRect(glowRect, r.height() / 2f + o, r.height() / 2f + o, glowPaint)
            }
            val t = if (sparksStill()) null else SystemClock.uptimeMillis()
            for (sp in GoalSparks.frame(sparkLevel, t)) {
                sparkPaint.color = sp.color
                sparkPaint.alpha = (sp.alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                canvas.drawCircle(r.left + r.width() * sp.x, r.top - sp.rise * k, sp.radius * k, sparkPaint)
            }
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = resolveSize(Ui.dp(context, 8), heightMeasureSpec)
        setMeasuredDimension(resolveSize(Ui.dp(context, 100), widthMeasureSpec), h)
    }

    override fun onDetachedFromWindow() {
        stopSparks()
        anim?.cancel()
        ghostAnim?.cancel()
        ghostFrac = 0f
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, track)
        if (ghostFrac > shown) {
            rect.set(0f, 0f, maxOf(w * ghostFrac, minOf(h, w)), h)
            canvas.drawRoundRect(rect, r, r, ghostPaint)
        }
        if (shown <= 0f) { drawLabel(canvas); return }
        // At least a round dot so a tiny fraction stays visible.
        val fw = maxOf(w * shown, minOf(h, w))
        rect.set(0f, 0f, fw, h)
        val g = gradient
        if (g != null) {
            unitGradient.setScale(fw, 1f)
            g.setLocalMatrix(unitGradient)
            fill.shader = g
        } else {
            fill.shader = null
            fill.color = color
        }
        canvas.drawRoundRect(rect, r, r, fill)
        drawLabel(canvas)
    }

    internal companion object {
        /** Where the ghost of a drop starts: [from] (0..1), or 0 (no ghost) when it is not above where the fill is heading, [dest]. */
        fun ghostTop(from: Float, dest: Float): Float = from.coerceIn(0f, 1f).let { if (it <= dest) 0f else it }

        const val GHOST_HOLD_MS = 450L
        const val GHOST_SHRINK_MS = 550L
        const val SPARK_FRAME_MS = 33L   // ~30 fps is plenty for tiny sparks
    }
}
