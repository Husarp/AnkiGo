package com.husarp.ankigo.gfx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.husarp.ankigo.data.SpriteImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// TICKET T03 owns this file. Public members are fixed (the battle panel / screens use them); add private helpers freely.

/**
 * Draws a pixel-art sprite crisply (nearest-neighbour, integer device-pixel scale; 3D animations bigger than pixel art
 * are scaled to the room and smoothed), animated if it has frames,
 * bottom-centred in the view. Effects for the battle panel: [flash] (white-ish hit flash), [silhouette],
 * [mirror] (used when only a front sprite exists for the player's back view), and a placeholder (type-coloured
 * circle + text) when there's no image yet. Animation runs only while attached and visible.
 */
class SpriteView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    /** Size multiplier in dp per sprite pixel (mockup: 2). The image never exceeds the view. */
    var scaleDp: Float = 2f
        set(v) { field = v; requestLayout(); invalidate() }
    var mirror: Boolean = false
        set(v) { field = v; invalidate() }
    var silhouette: Boolean = false
        set(v) { field = v; invalidate() }
    /** Colour of the [silhouette] (ARGB). */
    var silhouetteColor: Int = 0x55000000
        set(v) { field = v; invalidate() }
    /** 0..1 hit flash strength; animate it for the flash effect. */
    var flash: Float = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (c != field) { field = c; invalidate() } }

    /**
     * Move animations (round 7): a 4 x 5 colour matrix (20 floats, android.graphics.ColorMatrix order) applied AFTER the flash;
     * null = none. A [silhouette] stays a plain solid colour.
     */
    var fxMatrix: FloatArray? = null
        set(v) { if (v != null || field != null) { field = v; invalidate() } }

    /**
     * Round 8: true = the picture's lowest visible row (over all frames) sits on the view's bottom, so a Pokémon's feet stand on the
     * view's bottom edge even when its frames keep empty rows under them. False (the default) = the frame's bottom is the view's
     * bottom. Placeholders are the same either way.
     */
    var anchorFeet: Boolean = false
        set(v) { if (v != field) { field = v; measureFoot(); invalidate() } }

    /**
     * Round 8 picture mode: true = the sprite is drawn at (the view's height / 96) device px per sprite pixel, so a small species
     * is small and a big one big in its 96-px frame ([frameScale]); the view is the Pokémon's square frame. False (the default)
     * = [scaleDp], as before.
     */
    var byFrame: Boolean = false
        set(v) { if (v != field) { field = v; invalidate() } }

    /**
     * Round 10 B20 (Pokémon size): the picture is drawn this many times bigger (or smaller) than it would be in a view 1 / [sizeFactor] as
     * big: the whole-pixel scale of that smaller view (as before), times the factor, so a size of 130 % is exactly 1.3 x what 100 % draws
     * and pixel art goes up smoothly instead of in whole-pixel jumps (it stays nearest-neighbour, the pixels a little uneven). 1 = off:
     * exactly the scale before the setting. The battle arena sets it with the holder it scaled by the same factor.
     */
    var sizeFactor: Float = 1f
        set(v) { if (v != field) { field = v; invalidate() } }

    /**
     * Round 8 picture mode: called after [setImage] / [setPlaceholder] changed what the view shows, so the arena can compare [artShare]
     * with what it planned its cards for (null = nobody listens). Never called while drawing.
     */
    internal var onArtChange: (() -> Unit)? = null

    private var image: SpriteImage? = null
    /** Empty rows of the image under its feet while [anchorFeet] (worked out when the image is set, never while drawing). */
    private var rowsBelow = 0
    private var frame = 0
    private var placeholderText: String? = null
    private var placeholderColor = 0
    private var ticking = false

    // Nearest-neighbour: no bilinear filtering (except for 3D animations, see [scale]), no anti-aliasing.
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val src = Rect()
    private val dst = Rect()
    private val tick = Runnable {
        ticking = false
        val img = image ?: return@Runnable
        if (!img.animated) return@Runnable
        frame = (frame + 1) % img.frames.size
        invalidate()
        schedule()
    }

    fun setImage(img: SpriteImage?) {
        image = img
        boundsFor = null; bounds = null
        measureFoot()
        frame = 0
        removeCallbacks(tick); ticking = false
        requestLayout()
        invalidate()
        updateAnimation()
        onArtChange?.invoke()
    }

    fun setPlaceholder(text: String, color: Int) {
        placeholderText = text
        placeholderColor = color
        invalidate()
        onArtChange?.invoke()
    }

    /** Height in px the current image is drawn at (for placing damage numbers). */
    fun drawnHeight(): Int {
        val img = image
        if (img != null) return (img.height * scaleFor(img, width, height)).roundToInt()
        if (placeholderText != null) return placeholderDiameter(width, height).roundToInt()
        return 0
    }

    /**
     * How many px the drawn image reaches below the view's bottom edge: the empty rows under the feet while [anchorFeet] (0 otherwise
     * and for placeholders). The drawn box is then [drawnHeight] high with its bottom this far under the view's.
     */
    fun drawnBottomInset(): Int {
        val img = image ?: return 0
        return footInset(scaleFor(img, width, height))
    }

    private fun footInset(s: Float): Int = if (rowsBelow > 0) (rowsBelow * s).roundToInt() else 0

    private fun measureFoot() {
        val img = image
        if (!anchorFeet || img == null) { rowsBelow = 0; return }
        val row = IntArray(img.frames.maxOf { it.width })
        val lowest = SpriteFoot.lowestRow(img.frames.size, img.height) { f, y ->
            val b = img.frames[f]
            y < b.height && b.width > 0 && run { b.getPixels(row, 0, b.width, 0, y, b.width, 1); SpriteFoot.anyVisible(row, 0, b.width) }
        }
        rowsBelow = SpriteFoot.rowsBelow(img.height, lowest)
    }

    /** Width in px the current image is drawn at (0 while nothing is shown yet). */
    fun drawnWidth(): Int {
        val img = image
        if (img != null) return (img.width * scaleFor(img, width, height)).roundToInt()
        if (placeholderText != null) return placeholderDiameter(width, height).roundToInt()
        return 0
    }

    /** Device pixels per sprite pixel for this view ([scale], or [frameScale] in picture mode). */
    private fun scaleFor(img: SpriteImage, vw: Int, vh: Int): Float {
        val wanted = maxOf(1, floor(scaleDp * resources.displayMetrics.density).toInt())
        val k = sizeFactor
        return if (k == 1f) {
            if (byFrame) frameScale(img.width, img.height, vw, vh, vh.toFloat()) else scale(img.width, img.height, vw, vh, wanted)
        } else sized(k, img.width, img.height, vw, vh, byFrame, wanted)
    }

    /**
     * Picture mode (round 9): the Pokémon's REAL art box in a square frame [sidePx] wide, as shares of the frame ([ArtShare]): the
     * box of the pixels it actually draws (alpha above [SpriteFoot.ALPHA], the union over all the animation's frames), mapped through
     * the draw transform: the frame scale, the horizontal centring, the feet on the frame's bottom (picture mode always anchors the
     * feet), mirrored when [mirror]. For what is shown now: the image, else the placeholder circle, else nothing = null.
     */
    internal fun artShare(sidePx: Float): com.husarp.ankigo.ui.ArtShare? {
        if (!(sidePx > 0f)) return null
        val side = Math.round(sidePx)
        val img = image
        if (img != null) return artShare(img.width, img.height, visibleBounds(img), mirror, side, sizeFactor)
        if (placeholderText == null) return null
        val d = (placeholderDiameter(side, side) / sidePx).coerceIn(0f, 1f)
        return com.husarp.ankigo.ui.ArtShare.bottomCentred(d, d)
    }

    /** The visible pixels' bounds of [img] over its frames ([SpriteFoot.addFrame]; null = nothing visible), worked out once per image. */
    private fun visibleBounds(img: SpriteImage): IntArray? {
        if (boundsFor === img) return bounds
        val box = SpriteFoot.emptyBounds()
        var buf = IntArray(0)
        for (b in img.frames) {
            val w = b.width
            val h = b.height
            if (w <= 0 || h <= 0) continue
            if (buf.size < w * h) buf = IntArray(w * h)
            b.getPixels(buf, 0, w, 0, 0, w, h)
            SpriteFoot.addFrame(box, buf, w, h)
        }
        boundsFor = img
        bounds = if (SpriteFoot.isEmpty(box)) null else box
        return bounds
    }
    private var boundsFor: SpriteImage? = null
    private var bounds: IntArray? = null

    private fun placeholderDiameter(vw: Int, vh: Int): Float =
        min(min(vw, vh) * 0.8f, 72f * resources.displayMetrics.density).coerceAtLeast(0f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val img = image
        val wanted = maxOf(1, floor(scaleDp * resources.displayMetrics.density).toInt())
        val dw = if (img != null) img.width * wanted else (72f * resources.displayMetrics.density).roundToInt()
        val dh = if (img != null) img.height * wanted else (72f * resources.displayMetrics.density).roundToInt()
        setMeasuredDimension(resolveSize(dw + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dh + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val vw = width
        val vh = height
        val img = image
        if (img == null) {
            drawPlaceholder(canvas, vw, vh)
            return
        }
        val s = scaleFor(img, vw, vh)
        if (s <= 0f) return
        val bmp = img.frames[frame.coerceIn(0, img.frames.size - 1)]
        // Whole-pixel destination so every sprite pixel is the same size.
        val dw = (bmp.width * s).roundToInt()
        val dh = (bmp.height * s).roundToInt()
        val left = (vw - dw) / 2
        val top = vh - dh + footInset(s)
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(left, top, left + dw, top + dh)
        paint.colorFilter = currentFilter()
        paint.isFilterBitmap = !pixelArt(bmp.width, bmp.height)
        val save = canvas.save()
        if (mirror) canvas.scale(-1f, 1f, vw / 2f, vh / 2f)
        canvas.drawBitmap(bmp, src, dst, paint)
        canvas.restoreToCount(save)
    }

    private fun currentFilter(): android.graphics.ColorFilter? {
        if (silhouette) return PorterDuffColorFilter(silhouetteColor, PorterDuff.Mode.SRC_IN)
        val f = flash
        val fx = fxMatrix
        if (f <= 0f) return if (fx != null && fx.size == 20) ColorMatrixColorFilter(ColorMatrix(fx)) else null
        // CSS brightness(2.2) saturate(.3) at full strength, blended from the identity.
        val sat = 1f + (0.3f - 1f) * f
        val b = 1f + (2.2f - 1f) * f
        val cm = ColorMatrix().apply { setSaturation(sat) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        if (fx != null && fx.size == 20) cm.postConcat(ColorMatrix(fx))
        return ColorMatrixColorFilter(cm)
    }

    private fun drawPlaceholder(canvas: Canvas, vw: Int, vh: Int) {
        val text = placeholderText ?: return
        val d = placeholderDiameter(vw, vh)
        if (d <= 0f) return
        val drawable = com.husarp.ankigo.ui.SpritePlaceholder(placeholderColor)
        drawable.setBounds(((vw-d)/2).toInt(), (vh-d).toInt(), ((vw+d)/2).toInt(), vh)
        drawable.draw(canvas)
    }

    // ---- animation: runs only while attached and shown ----

    private fun schedule() {
        val img = image ?: return
        if (ticking) return
        ticking = true
        postDelayed(tick, img.delays[frame.coerceIn(0, img.delays.size - 1)].coerceAtLeast(20).toLong())
    }

    private fun updateAnimation() {
        val img = image
        val run = android.animation.ValueAnimator.areAnimatorsEnabled() && !context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false) && img != null && img.animated && isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (run) schedule() else { removeCallbacks(tick); ticking = false }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); ticking = false; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }

    internal companion object {
        /** Gen-5 pixel art is at most 96 x 96; Showdown's 3D animations are often bigger. */
        private const val PIXEL_ART_MAX = 96

        fun pixelArt(w: Int, h: Int) = w <= PIXEL_ART_MAX && h <= PIXEL_ART_MAX

        /**
         * Device pixels per sprite pixel for a [w] x [h] picture in a [vw] x [vh] view, at most [wanted]. Pixel art: a
         * whole number reduced to fit the view, and only fractional (below 1) if even 1 does not fit. A bigger picture
         * (a 3D animation, not pixel art) takes the room up to [wanted], fractional (drawn smoothed), so a big Pokémon
         * isn't left at 1x in a corner of its space.
         */
        fun scale(w: Int, h: Int, vw: Int, vh: Int, wanted: Int): Float {
            if (vw <= 0 || vh <= 0) return 0f
            val fit = min(vw.toFloat() / w, vh.toFloat() / h)
            if (!pixelArt(w, h)) return min(wanted.toFloat(), fit)
            val whole = min(wanted, floor(fit).toInt())
            return if (whole >= 1) whole.toFloat() else fit
        }

        /**
         * Round 10 B20: device pixels per sprite pixel at the Pokémon size [k] (not 1): the whole-pixel scale ([scale], or [frameScale] when
         * [byFrame]) of a view 1 / [k] as big, times [k]; never past what the view allows. The view [vw] x [vh] is meant to be today's
         * holder scaled by [k], so this is [k] times the scale today's holder gets.
         */
        fun sized(k: Float, w: Int, h: Int, vw: Int, vh: Int, byFrame: Boolean, wanted: Int): Float {
            if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0 || !(k > 0f)) return 0f
            val aw = Math.round(vw / k)
            val ah = Math.round(vh / k)
            val base = if (byFrame) frameScale(w, h, aw, ah, vh / k) else scale(w, h, aw, ah, wanted)
            return min(base * k, min(vw.toFloat() / w, vh.toFloat() / h))
        }

        /** The frame every Pokémon is drawn in, in sprite pixels (the approved stage page draws each one at frame / 96). */
        private const val FRAME = 96f

        /** A holder rounded to whole pixels can be a hair under a multiple of 96: that is still the multiple. */
        private const val ROUNDING = 0.02f

        /**
         * Picture mode: device pixels per sprite pixel for a [w] x [h] picture in a [vw] x [vh] view that is a [frame]-px frame. The
         * frame wants [frame] / 96: pixel art at the whole number below it (at least 1; fractional only if even 1 does not fit the
         * view), a bigger picture (a 3D animation, drawn smoothed) at that fractional scale. Never more than the view allows.
         */
        fun frameScale(w: Int, h: Int, vw: Int, vh: Int, frame: Float): Float {
            if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return 0f
            val fit = min(vw.toFloat() / w, vh.toFloat() / h)
            val want = frame / FRAME
            if (!pixelArt(w, h)) return min(want, fit)
            val whole = min(max(1, floor(want + ROUNDING).toInt()), floor(fit).toInt())
            return if (whole >= 1) whole.toFloat() else fit
        }

        /**
         * The real art box of a [w] x [h] picture drawn in a square frame [side] px wide at [frameScale], as shares of the frame:
         * [bounds] = its visible pixels (left, top, right, bottom, right and bottom exclusive; null = the whole bitmap), put where
         * [SpriteView.onDraw] puts the bitmap (centred, its lowest visible row on the frame's bottom, flipped when [mirror]).
         */
        fun artShare(w: Int, h: Int, bounds: IntArray?, mirror: Boolean, side: Int, sizeFactor: Float = 1f): com.husarp.ankigo.ui.ArtShare? {
            if (side <= 0 || w <= 0 || h <= 0) return null
            val s = if (sizeFactor == 1f) frameScale(w, h, side, side, side.toFloat()) else sized(sizeFactor, w, h, side, side, true, 1)
            if (s <= 0f) return null
            val dw = (w * s).roundToInt()
            val dh = (h * s).roundToInt()
            val b = bounds ?: intArrayOf(0, 0, w, h)
            val rowsBelow = (h - b[3]).coerceAtLeast(0)
            val left = (side - dw) / 2
            val top = side - dh + (if (rowsBelow > 0) (rowsBelow * s).roundToInt() else 0)
            val sx = dw.toFloat() / w
            val sy = dh.toFloat() / h
            val bl = if (mirror) w - b[2] else b[0]
            val br = if (mirror) w - b[0] else b[2]
            val x0 = (left + bl * sx).coerceIn(0f, side.toFloat())
            val x1 = (left + br * sx).coerceIn(x0, side.toFloat())
            val y0 = (top + b[1] * sy).coerceIn(0f, side.toFloat())
            val y1 = (top + b[3] * sy).coerceIn(y0, side.toFloat())
            return com.husarp.ankigo.ui.ArtShare(x0 / side, y0 / side, (x1 - x0) / side, (y1 - y0) / side)
        }
    }
}
