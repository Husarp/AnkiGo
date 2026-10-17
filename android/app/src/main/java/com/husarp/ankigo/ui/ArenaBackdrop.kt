package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.animation.ValueAnimator
import android.os.SystemClock
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.ui.movefx.Joltable

/**
 * The arena's background (round 7). Plain mode is the gradient the arena always had (`ag_arena_top` -> `ag_arena_bot`); picture
 * mode draws a downloaded Showdown arena picture ([Backdrops]) scaled to cover, crisp (no filtering), placed by [BackdropFit] so
 * its ground spots sit under the two Pokémon ([setFeet]), with the dark theme's dusk tint or the night tint ([BackdropTint]).
 * [jolt] moves the picture up or down (Earthquake); it is drawn with room for that, so no gap shows.
 * The night picture (the forest, darkened and blue) also has [FireflyRules] fireflies: glowing dots on the picture's pixel grid that
 * twinkle slowly (still with reduced motion; the loop runs only while the arena is drawn, so nothing animates when it is hidden).
 * While a picture loads, or if it cannot be had, the background is plain; a picture already showing stays until the next one
 * is there (a new encounter with the same biome never flashes).
 */
internal class ArenaBackdrop(private val ctx: Context) : Drawable(), Joltable {
    private val gradient = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)),
    )
    private val dark = TypeChips.isDark(ctx)
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val matrix = Matrix()
    private val flyCore = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val flyGlow = Paint().apply {
        isAntiAlias = true
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x99D8F060.toInt(), 0x33B8E040, 0x00B8E040), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private var tickPending = false
    private val tick = Runnable { tickPending = false; invalidateSelf() }
    /** Tests pin the clock (ms); null = the real one. */
    internal var clockMs: (() -> Long)? = null

    private var bitmap: Bitmap? = null
    private var shown: BackdropRules.Choice? = null
    private var wanted: BackdropRules.Choice? = null
    private var feet: BackdropFit.Feet? = null
    private var placement: BackdropFit.Placement? = null

    /** Called when the picture appears or goes (the arena hides its plain ellipses while a picture shows). */
    var onPictureChange: (Boolean) -> Unit = {}

    /** True while a picture (not the gradient) is drawn. */
    val showsPicture: Boolean get() = bitmap != null

    override var jolt: Float = 0f
        set(v) { if (v != field) { field = v; invalidateSelf() } }

    /** The Pokémon's feet in the arena's pixels (the arena calls this after every layout); the picture moves to meet them. */
    fun setFeet(enemyX: Float, enemyY: Float, playerX: Float, playerY: Float) {
        val f = feet
        if (f != null && f.enemyX == enemyX && f.enemyY == enemyY && f.playerX == playerX && f.playerY == playerY) return
        feet = BackdropFit.Feet(enemyX, enemyY, playerX, playerY)
        refit()
        if (bitmap != null) invalidateSelf()
    }

    /**
     * Shows [choice] as soon as it is loaded (at once when it is in memory); a failed or impossible load turns the background plain.
     * [download] = pictures may be fetched ([Backdrops.allowed]).
     */
    fun request(choice: BackdropRules.Choice, download: Boolean) {
        if (choice == shown && bitmap != null) { wanted = null; return }
        if (choice == wanted) return
        wanted = choice
        val ready = Backdrops.inMemory(choice.name)
        if (ready != null) { apply(choice, ready); return }
        Backdrops.load(ctx, choice.name, download) { bmp ->
            if (wanted != choice) return@load // a newer request, or plain, came in meanwhile
            if (bmp != null) apply(choice, bmp) else plain()
        }
    }

    /** The plain gradient. */
    fun plain() {
        wanted = null
        shown = null
        val had = bitmap != null
        bitmap = null
        placement = null
        invalidateSelf()
        if (had) onPictureChange(false)
    }

    private fun apply(choice: BackdropRules.Choice, bmp: Bitmap) {
        wanted = null
        val had = bitmap != null
        shown = choice
        bitmap = bmp
        val m = BackdropTint.matrix(choice.night, dark)
        paint.colorFilter = if (m == null) null else ColorMatrixColorFilter(ColorMatrix(m))
        refit()
        invalidateSelf()
        if (!had) onPictureChange(true)
    }

    private fun refit() {
        val bmp = bitmap ?: return
        val b = bounds
        placement = BackdropFit.fit(bmp.width, bmp.height, b.width().toFloat(), b.height().toFloat(), feet)
    }

    override fun onBoundsChange(bounds: Rect) {
        gradient.bounds = bounds
        refit()
    }

    override fun draw(canvas: Canvas) {
        val bmp = bitmap
        val p = placement
        if (bmp == null || p == null) { gradient.draw(canvas); return }
        val b = bounds
        val save = canvas.save()
        canvas.clipRect(b)
        matrix.setScale(p.scale, p.scale)
        matrix.postTranslate(b.left + p.left, b.top + p.top + jolt)
        canvas.drawBitmap(bmp, matrix, paint)
        if (shown?.night == true) drawFireflies(canvas, bmp, p)
        canvas.restoreToCount(save)
    }

    private fun reducedMotion() = !ValueAnimator.areAnimatorsEnabled() ||
        ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    /** The fireflies, each snapped to a picture pixel; a glow (one scaled radial gradient) under a 1-2 pixel core. */
    private fun drawFireflies(canvas: Canvas, bmp: Bitmap, p: BackdropFit.Placement) {
        val still = reducedMotion()
        val now = clockMs?.invoke() ?: SystemClock.uptimeMillis()
        val ox = bounds.left + p.left
        val oy = bounds.top + p.top + jolt
        val s = p.scale
        for (f in FireflyRules.FLIES) {
            val a = if (still) FireflyRules.still(f) else FireflyRules.alpha(f, now)
            if (a < 0.03f) continue
            val px = (f.x * bmp.width).toInt()
            val py = (f.y * bmp.height).toInt()
            val cx = ox + (px + f.wide / 2f) * s
            val cy = oy + (py + f.tall / 2f) * s
            val r = FireflyRules.GLOW_PX * s + f.wide * s / 2f
            val sv = canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(r, r)
            flyGlow.alpha = (a * 255).toInt()
            canvas.drawCircle(0f, 0f, 1f, flyGlow)
            canvas.restoreToCount(sv)
            flyCore.color = 0xFFEEF59A.toInt()
            flyCore.alpha = (a * 255).toInt()
            canvas.drawRect(ox + px * s, oy + py * s, ox + (px + f.wide) * s, oy + (py + f.tall) * s, flyCore)
        }
        // the next frame, only while this one was drawn (a hidden arena draws nothing, so the loop ends by itself)
        if (!still && isVisible && !tickPending) {
            tickPending = true
            scheduleSelf(tick, SystemClock.uptimeMillis() + FireflyRules.FRAME_MS)
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible && tickPending) { unscheduleSelf(tick); tickPending = false }
        return super.setVisible(visible, restart)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; gradient.alpha = alpha }

    override fun setColorFilter(colorFilter: ColorFilter?) { /* the tint is ours; the arena never asks for another */ }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
