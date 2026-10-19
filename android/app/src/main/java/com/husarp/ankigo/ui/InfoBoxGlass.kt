package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.husarp.ankigo.R
import com.husarp.ankigo.data.BackdropPic
import com.husarp.ankigo.data.Backdrops

/**
 * What the glass look (Round 10 W7, D8-A) shows behind a box: a blurred copy of the battle picture, lined up with the picture, clipped
 * to the box's rounded rectangle. The user of an [InfoBoxDrawable] says how (the arena, a preview tile).
 */
internal interface BoxBackdrop {
    /** Draws what is behind the box into [canvas] over the rounded rectangle [rect] (radius [r]), in the box's own coordinates. */
    fun drawBehind(canvas: Canvas, rect: RectF, r: Float)
}

/**
 * The glass copy of a picture ([BackdropBlur.glass]: blurred a little, not darkened, saturated), made once per picture on a tiny copy
 * and dropped with the picture. Main thread; a few KB.
 */
internal object GlassBlurCache {
    private val cache = java.util.WeakHashMap<BackdropPic, Bitmap>()

    fun of(pic: BackdropPic): Bitmap = cache.getOrPut(pic) { make(pic.bitmap) }

    private fun make(bmp: Bitmap): Bitmap {
        val (w, h) = BackdropBlur.smallSize(bmp.width, bmp.height)
        val small = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bmp) small.recycle()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(BackdropBlur.glass(px, w, h), 0, w, 0, 0, w, h)
        return out
    }
}

/**
 * The painting side of the glass: a blurred picture laid in a "base" space (the arena, a preview tile) or, without a picture, the plain
 * arena gradient. [draw] takes the matrix from that base space to the box's own space. The caller fills [glassToBase] and friends
 * through [picture] / [plain]; nothing is allocated while drawing.
 */
internal class GlassPaint {
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glassShader: BitmapShader? = null
    private var blurShader: BitmapShader? = null
    private var gradient: LinearGradient? = null
    private var blurFirst = false

    /** Glass bitmap pixels -> base space. */
    val glassToBase = Matrix()

    /** The darkened blur bitmap's pixels -> base space (used where the picture does not reach: [blurAround]). */
    val blurToBase = Matrix()

    /** The picture's rectangle in base space, when it ends inside the base (the darkened blur shows round it). */
    val picRect = RectF()

    private val local = Matrix()
    private val tmp = RectF()

    /** A picture: [glass] (the glass copy) laid by [glassToBase]; [blur] by [blurToBase] fills round it when [blurAround]; [tint] the dusk tint of the dark theme (or null). */
    fun picture(glass: Bitmap, blur: Bitmap, blurAround: Boolean, tint: ColorFilter?) {
        glassShader = BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        blurShader = BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        glassPaint.shader = glassShader; blurPaint.shader = blurShader
        glassPaint.colorFilter = tint; blurPaint.colorFilter = tint
        blurFirst = blurAround
        gradient = null
    }

    /** No picture: the plain arena gradient from [top] to [bottom] over [baseH] of the base space. */
    fun plain(top: Int, bottom: Int, baseH: Float) {
        glassShader = null; blurShader = null; blurFirst = false
        gradient = LinearGradient(0f, 0f, 0f, baseH, top, bottom, Shader.TileMode.CLAMP)
        gradPaint.shader = gradient
    }

    fun draw(canvas: Canvas, rect: RectF, r: Float, baseToBox: Matrix) {
        val g = glassShader
        if (g != null) {
            if (blurFirst) {
                local.set(blurToBase); local.postConcat(baseToBox)
                blurShader?.setLocalMatrix(local)
                canvas.drawRoundRect(rect, r, r, blurPaint)
            }
            local.set(glassToBase); local.postConcat(baseToBox)
            g.setLocalMatrix(local)
            if (blurFirst) {
                baseToBox.mapRect(tmp, picRect)
                val save = canvas.save()
                canvas.clipRect(tmp)
                canvas.drawRoundRect(rect, r, r, glassPaint)
                canvas.restoreToCount(save)
            } else {
                canvas.drawRoundRect(rect, r, r, glassPaint)
            }
            return
        }
        val gr = gradient ?: return
        gr.setLocalMatrix(baseToBox)
        canvas.drawRoundRect(rect, r, r, gradPaint)
    }
}

/**
 * The glass of the arena's two info boxes: the battle picture as [ArenaBackdrop] lays it (the crop of the current [Stage] over the
 * arena, [BackdropDraw]), blurred and lined up with the picture; without a picture the arena's plain gradient. [card] is the box's
 * view; [bg] / [stage] say what the arena shows now. The layout is worked out again only when one of them, the picture's data or the
 * arena's size changes.
 */
internal class ArenaGlass(
    private val ctx: Context,
    private val arena: View,
    private val card: View,
    private val bg: () -> Backdrop?,
    private val stage: () -> Stage?,
) : BoxBackdrop {
    private val paint = GlassPaint()
    private val chain = Matrix()
    private val inv = Matrix()
    private val pic = Matrix()
    private val dark = TypeChips.isDark(ctx)
    private var keyBg: Backdrop? = null
    private var keyPic: BackdropPic? = null
    private var keyStage: Stage? = null
    private var keyW = -1
    private var keyH = -1

    override fun drawBehind(canvas: Canvas, rect: RectF, r: Float) {
        val w = arena.width
        val h = arena.height
        if (w <= 0 || h <= 0) return
        // the box's own space -> the arena's: each view up to the arena adds its own transform (the card is drawn scaled from its corner)
        chain.reset()
        var v: View = card
        while (v !== arena) {
            chain.postConcat(v.matrix)
            chain.postTranslate(v.left.toFloat(), v.top.toFloat())
            v = v.parent as? View ?: return
        }
        if (!chain.invert(inv)) return
        refresh(w, h)
        paint.draw(canvas, rect, r, inv)
    }

    private fun refresh(w: Int, h: Int) {
        val b = bg()
        val st = stage()
        val p = b?.let { Backdrops.inMemory(it) }
        if (b === keyBg && p === keyPic && st === keyStage && w == keyW && h == keyH) return
        keyBg = b; keyPic = p; keyStage = st; keyW = w; keyH = h
        if (b == null || p == null) {
            paint.plain(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot), h.toFloat())
            return
        }
        val crop = if (st != null && st.cropW > 0f && st.cropH > 0f) BackdropDraw.Crop(st.cropX, st.cropY, st.cropW, st.cropH)
        else BackdropDraw.coverCrop(b.w, b.h, w.toFloat(), h.toFloat())
        val glass = GlassBlurCache.of(p)
        val l = BackdropDraw.layout(b.w, b.h, crop, p.blur.width, p.blur.height, w.toFloat(), h.toFloat())
        // the picture's bitmap may be another size than the catalog says; the glass copy is smaller still
        val bx = p.bitmap.width.toFloat() / b.w
        val by = p.bitmap.height.toFloat() / b.h
        pic.setScale(l.kx / bx, l.ky / by)
        pic.postTranslate(l.picL, l.picT)
        paint.glassToBase.setScale(p.bitmap.width.toFloat() / glass.width, p.bitmap.height.toFloat() / glass.height)
        paint.glassToBase.postConcat(pic)
        paint.blurToBase.setScale((l.blurR - l.blurL) / p.blur.width, (l.blurB - l.blurT) / p.blur.height)
        paint.blurToBase.postTranslate(l.blurL, l.blurT)
        paint.picRect.set(l.picL, l.picT, l.picR, l.picB)
        val m = BackdropTint.matrix(dark && !b.dark)
        paint.picture(glass, p.blur, l.anyFade, if (m == null) null else ColorMatrixColorFilter(ColorMatrix(m)))
    }
}
