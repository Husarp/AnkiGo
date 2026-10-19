package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import com.husarp.ankigo.R
import com.husarp.ankigo.data.BackdropPic
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.ui.movefx.Joltable

/**
 * The arena's background (round 7, stage rule round 8). Plain mode is the gradient the arena always had (`ag_arena_top` ->
 * `ag_arena_bot`); picture mode draws a downloaded battle picture ([Backdrops]) cropped to a [Stage] ([setStage]; no stage yet = a
 * cover crop, centred, bottom first). A picture that had to shrink (the stage is [Stage.shrunk]) is drawn over its blurred, darkened
 * copy, which covers the panel, and its edges fade into the blur over [BackdropDraw.FADE] of their size. [jolt] (Earthquake) moves
 * picture and blur up or down; the blur is drawn first then too, so no gap shows. Pixel-art pictures ([Backdrop.crisp]) are scaled
 * without filtering, soft ones with. The dark theme's dusk tint ([BackdropTint]) is added to day pictures only, blur included.
 * While a picture loads, or if it cannot be had, the background is plain; a picture already showing stays until the next one
 * is there (a new encounter with the same scene never flashes). When the next one is there it cross-fades in over
 * [BackdropFade.DURATION_MS] (also gradient -> first picture); the outgoing picture keeps its own crop, blur and tint (its own [Frame]).
 * Picture -> plain, and reduced motion, are instant (reduced motion is also checked while a fade runs: it ends at once). Frames are
 * drawn only while a fade runs: [draw] asks for the next one itself and stops asking at the end; the fade's clock starts at its first
 * drawn frame. Otherwise nothing here animates. A picture that comes while a fade is part way does not make the screen jump: the
 * blend on screen is frozen once into a panel-sized bitmap ([snap]) that becomes the outgoing side of the new fade (not jolted).
 */
internal class ArenaBackdrop(private val ctx: Context) : Drawable(), Joltable {
    private val gradient = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)),
    )
    private val dark = TypeChips.isDark(ctx)
    private val fadePaint = Paint().apply { isAntiAlias = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    private var cur: Frame? = null
    private var old: Frame? = null

    /** The outgoing side of a fade that interrupted another: the blend as it looked then (gradient included); replaces [old]. */
    private var snap: Bitmap? = null
    private val snapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var wanted: Backdrop? = null
    private var stage: Stage? = null
    private var alphaValue = 255

    /** A fade is under way (or waiting for its first frame, [fadeStart] < 0); [old] is the outgoing picture, null = the gradient. */
    private var fading = false
    private var fadeStart = -1L

    /** The clock the fade runs on; tests set their own. */
    var clock: () -> Long = { SystemClock.uptimeMillis() }

    /** No fade: animations are off, the study setting says so, or the preview renderer (it draws once, at time 0). Tests set their own. */
    var reduceMotion: () -> Boolean = {
        !ValueAnimator.areAnimatorsEnabled() || ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false) ||
            Build.DEVICE == "layoutlib" || Build.FINGERPRINT.contains("layoutlib")
    }

    /** The picture drawn now (the new one from the start of a fade); null = the plain gradient. */
    var shown: Backdrop? = null
        private set

    /**
     * Called when the picture appears (true; also when another picture replaces the one showing, at the start of the fade, so the
     * arena can lay its Pokémon on the new stand points) or goes (false).
     */
    var onPictureChange: (Boolean) -> Unit = {}

    /**
     * R9 C4: called after every [request] that had to wait for a picture is settled: the picture was applied (after [onPictureChange]
     * and the new layout were asked for), or the background turned plain (also when the load failed). The arena lets the next wild
     * one in then, so it never shows at the old picture's stand points.
     */
    var onSettled: () -> Unit = {}

    /** True while a requested picture is not there yet (it loads); false once it is applied, the same one is shown, or the request was dropped. */
    val loading: Boolean get() = wanted != null

    /** True while a picture (not the gradient) is drawn. */
    val showsPicture: Boolean get() = cur != null

    /** R9 C4: how long the cross-fade of the picture just applied still has to run (ms; 0 = no fade under way). The next wild one comes in after it. */
    fun fadeRemainingMs(): Long {
        if (!fading || reduceMotion()) return 0L
        if (fadeStart < 0L) return BackdropFade.DURATION_MS
        return (BackdropFade.DURATION_MS - (clock() - fadeStart)).coerceIn(0L, BackdropFade.DURATION_MS)
    }

    /** R9 C4: starts loading [bg] without showing it (the next encounter's picture, known from its queued Encounter), so it is in memory when the wild one comes. */
    fun prefetch(bg: Backdrop, download: Boolean) {
        if (bg.id == shown?.id || Backdrops.inMemory(bg) != null) return
        Backdrops.load(ctx, bg, download) { }
    }

    override var jolt: Float = 0f
        set(v) { if (v != field) { field = v; invalidateSelf() } }

    /** The crop to draw (from [StageFit]); null = a cover crop of the panel's shape. Takes effect at once, for the picture shown (not an outgoing one). */
    fun setStage(stage: Stage?) {
        if (stage === this.stage) return
        this.stage = stage
        val c = cur ?: return
        c.stage = stage
        c.relayout()
        invalidateSelf()
    }

    /**
     * Shows [bg] as soon as it is loaded (at once when it is in memory); a failed or impossible load turns the background plain.
     * [download] = pictures may be fetched ([Backdrops.allowed]).
     */
    fun request(bg: Backdrop, download: Boolean) {
        if (bg.id == shown?.id && cur != null) { wanted = null; return }
        if (bg.id == wanted?.id) return
        wanted = bg
        val ready = Backdrops.inMemory(bg)
        if (ready != null) { apply(bg, ready); return }
        Backdrops.load(ctx, bg, download) { loaded ->
            if (wanted?.id != bg.id) return@load // a newer request, or plain, came in meanwhile
            if (loaded != null) apply(bg, loaded) else plain()
        }
    }

    /** The plain gradient (at once; a fade under way is dropped). */
    fun plain() {
        wanted = null
        shown = null
        val had = cur != null
        cur = null
        endFade()
        invalidateSelf()
        if (had) onPictureChange(false)
        onSettled()
    }

    private fun apply(bg: Backdrop, loaded: BackdropPic) {
        wanted = null
        shown = bg
        val f = Frame(bg, loaded, stage)
        val fade = !reduceMotion()
        if (!fade) {
            endFade()
        } else if (!fading) {
            old = cur // the outgoing frame keeps its own stage, layout and tint; the gradient (no frame) fades too
        } else {
            // another fade is under way: its outgoing side stays, or becomes the blend on screen now (never a jump back to a whole picture)
            when (BackdropFade.outgoingAt(fadeStart >= 0L, if (fadeStart >= 0L) clock() - fadeStart else 0L)) {
                BackdropFade.Outgoing.KEEP -> {}
                BackdropFade.Outgoing.INCOMING -> { releaseSnap(); old = cur }
                BackdropFade.Outgoing.SNAPSHOT -> freeze()
            }
        }
        fading = fade
        fadeStart = -1L
        cur = f
        f.relayout()
        invalidateSelf()
        onPictureChange(true)
        onSettled()
    }

    private fun endFade() {
        old = null
        releaseSnap()
        fading = false
        fadeStart = -1L
    }

    private fun releaseSnap() {
        snap?.recycle()
        snap = null
    }

    /** Draws what is on screen now (the fade at its current opacity, no jolt) into a new bitmap and makes it the outgoing side. Rare (an interrupt), outside [draw]. */
    private fun freeze() {
        val c = cur
        val b = bounds
        if (c?.layout == null || b.width() <= 0 || b.height() <= 0) return // nothing to freeze: the outgoing side stays as it is
        val bmp = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-b.left.toFloat(), -b.top.toFloat()) // [compose] works in the panel's place on the screen
        compose(canvas, BackdropFade.alpha255(clock() - fadeStart, false), 0f)
        releaseSnap()
        snap = bmp
        old = null
    }

    /** Everything about one picture: its bitmap, blur, tint, stage and where they land in the panel. Built when it is applied, not per frame. */
    private inner class Frame(val bg: Backdrop, val pic: BackdropPic, var stage: Stage?) {
        val paint = Paint().apply { isFilterBitmap = !bg.crisp; isAntiAlias = false; isDither = false; alpha = alphaValue }
        val blurPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = false; isDither = false; alpha = alphaValue }
        val picMatrix = Matrix()
        val blurMatrix = Matrix()
        val layer = RectF()
        var layout: BackdropDraw.Layout? = null
        var fadeX: Shader? = null
        var fadeY: Shader? = null

        init {
            val m = BackdropTint.matrix(dark && !bg.dark)
            val filter = if (m == null) null else ColorMatrixColorFilter(ColorMatrix(m))
            paint.colorFilter = filter
            blurPaint.colorFilter = filter
        }

        /** Works out where everything lands for the current stage and panel size; the fade shaders are built here, not per frame. */
        fun relayout() {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) { layout = null; fadeX = null; fadeY = null; layer.setEmpty(); return }
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val s = stage
            val crop = if (s != null && s.cropW > 0f && s.cropH > 0f) BackdropDraw.Crop(s.cropX, s.cropY, s.cropW, s.cropH)
            else BackdropDraw.coverCrop(bg.w, bg.h, w, h)
            val l = BackdropDraw.layout(bg.w, bg.h, crop, pic.blur.width, pic.blur.height, w, h)
            layout = l
            // the crop is in the catalog's picture pixels; a file of another size is scaled to them
            val bx = pic.bitmap.width.toFloat() / bg.w
            val by = pic.bitmap.height.toFloat() / bg.h
            picMatrix.setScale(l.kx / bx, l.ky / by)
            picMatrix.postTranslate(l.picL, l.picT)
            blurMatrix.setScale((l.blurR - l.blurL) / pic.blur.width, (l.blurB - l.blurT) / pic.blur.height)
            blurMatrix.postTranslate(l.blurL, l.blurT)
            fadeX = if (l.fadeLeft || l.fadeRight) fade(l.picL, 0f, l.picR, 0f, l.fadeLeft, l.fadeRight) else null
            fadeY = if (l.fadeTop || l.fadeBottom) fade(0f, l.picT, 0f, l.picB, l.fadeTop, l.fadeBottom) else null
            // the fading layer: the picture's own bounds cut to the panel plus the jolt's room, so a jolt never shows blur over sharp picture
            val v = BackdropDraw.visible(l, w, h)
            layer.set(v[0], v[1], v[2], v[3])
        }

        /** Draws this picture (blur first when it shows) at the jolt's offset; the canvas is at the panel's top left and clipped to it. */
        fun draw(canvas: Canvas, jolt: Float) {
            val l = layout ?: return
            val save = canvas.save()
            canvas.translate(0f, jolt)
            if (stage?.shrunk == true || jolt != 0f || l.anyFade) canvas.drawBitmap(pic.blur, blurMatrix, blurPaint)
            if (!l.anyFade) {
                canvas.drawBitmap(pic.bitmap, picMatrix, paint)
            } else {
                // a shrunk picture: drawn in its own layer ([layer]), whose sides that end inside the panel are multiplied by a ramp (DST_IN)
                val sv = canvas.saveLayer(layer, null)
                canvas.drawBitmap(pic.bitmap, picMatrix, paint)
                fadeX?.let { fadePaint.shader = it; canvas.drawRect(layer, fadePaint) }
                fadeY?.let { fadePaint.shader = it; canvas.drawRect(layer, fadePaint) }
                canvas.restoreToCount(sv)
            }
            canvas.restoreToCount(save)
        }
    }

    /** An alpha ramp along the picture's span: clear at a side that ends inside the panel, rising to full over [BackdropDraw.FADE]. */
    private fun fade(x0: Float, y0: Float, x1: Float, y1: Float, atStart: Boolean, atEnd: Boolean): Shader = LinearGradient(
        x0, y0, x1, y1,
        intArrayOf(if (atStart) CLEAR else SOLID, SOLID, SOLID, if (atEnd) CLEAR else SOLID),
        floatArrayOf(0f, BackdropDraw.FADE, 1f - BackdropDraw.FADE, 1f),
        Shader.TileMode.CLAMP,
    )

    override fun onBoundsChange(bounds: Rect) {
        gradient.bounds = bounds
        if (fading && (fadeStart >= 0L || snap != null)) endFade() // a resize in the middle of a fade just finishes it (a snapshot is of the old size)
        cur?.relayout()
        old?.relayout()
    }

    override fun draw(canvas: Canvas) {
        val c = cur
        if (c?.layout == null) { gradient.draw(canvas); return }
        // the fade: its clock starts at the first frame drawn; at the end the outgoing picture is let go
        var a = 255
        var again = false
        if (fading) {
            if (reduceMotion()) { // switched on while the fade was pending or running: it ends now
                endFade()
            } else {
                val now = clock()
                if (fadeStart < 0L) fadeStart = now
                a = BackdropFade.alpha255(now - fadeStart, false)
                if (a >= 255) endFade() else again = true
            }
        }
        compose(canvas, a, jolt)
        if (again) invalidateSelf() // the next frame of the fade; none is asked for after the last
    }

    /** The panel at incoming opacity [a] (255 = only the picture [cur]); the outgoing side is [snap], else [old], else the gradient. */
    private fun compose(canvas: Canvas, a: Int, jolt: Float) {
        val c = cur ?: return
        val b = bounds
        val save = canvas.save()
        canvas.clipRect(b)
        val o = old
        val sn = snap
        if (a < 255 && sn == null && o?.layout == null) gradient.draw(canvas) // gradient -> first picture (the gradient's bounds are absolute)
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        if (a >= 255) {
            c.draw(canvas, jolt)
        } else {
            if (sn != null) canvas.drawBitmap(sn, 0f, 0f, snapPaint) else o?.draw(canvas, jolt)
            val sv = canvas.saveLayerAlpha(0f, 0f, b.width().toFloat(), b.height().toFloat(), a)
            c.draw(canvas, jolt)
            canvas.restoreToCount(sv)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        cur?.let { it.paint.alpha = alpha; it.blurPaint.alpha = alpha }
        old?.let { it.paint.alpha = alpha; it.blurPaint.alpha = alpha }
        snapPaint.alpha = alpha
        gradient.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) { /* the tint is ours; the arena never asks for another */ }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val SOLID = 0xFF000000.toInt()
        const val CLEAR = 0x00000000
    }
}
