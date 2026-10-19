package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.view.View
import com.husarp.ankigo.R
import com.husarp.ankigo.data.BackdropPic
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.game.Type
import kotlin.math.max
import kotlin.math.min

/**
 * A small live picture of ONE info box in one [InfoBoxLook] and [InfoBoxStyle] (Round 10 W7, for the choosers in Settings and the battle's
 * view settings): a tile of about 100 x 64 dp that draws a miniature of the box on a background, the battle picture last shown in this
 * process when one is kept in memory, else the plain arena gradient with its two platforms. It draws from the same [InfoBoxSkin] and
 * [InfoBoxDrawable] as the real boxes, in the theme's light or dark colours (a look that is the same in both uses the dark palette), so the
 * tile cannot drift from the box. The HUD, which has no box, stands in the tile's bottom-left corner. Change what it shows with [set].
 *
 * Nothing is allocated while drawing: everything is made when the look, the style or the size changes.
 */
class BoxLookPreview(ctx: Context, look: InfoBoxLook = InfoBoxLook.DEFAULT, style: InfoBoxStyle = InfoBoxStyle.DEFAULT) : View(ctx) {
    /** The look and the layout shown. */
    var look: InfoBoxLook = look
        private set
    var style: InfoBoxStyle = style
        private set

    /** Shows [look] and [style] (a no-op while it is the same). */
    fun set(look: InfoBoxLook, style: InfoBoxStyle) {
        if (look == this.look && style == this.style) return
        this.look = look
        this.style = style
        rebuild()
        invalidate()
    }

    private val d = ctx.resources.displayMetrics.density
    private val dark = TypeChips.isDark(ctx)

    // the tile's background
    private val tile = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val platPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgMatrix = Matrix()
    private var picture: Triple<Backdrop, android.graphics.Bitmap, BackdropPic>? = null
    private val glassPaint = GlassPaint()

    // the box, in tile pixels
    private var box: InfoBoxDrawable? = null
    private var boxX = 0f
    private var boxY = 0f
    private var boxW = 0f
    private var boxH = 0f
    private var s = 1f           // design dp -> tile px (the display density is already in it: every size is dp x s, never dp x d x s)

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 800) }
    private val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 900) }
    private val glow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 900) }   // the numbers written in the inside bars
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    private val fm = Paint.FontMetrics()   // reused by every line

    // what the content is painted in and with, worked out in rebuild() so that drawing allocates nothing
    private var colText = 0
    private var colSub = 0
    private var colTrack = 0
    private var colHp = 0
    private var colXp = 0
    private var colLvl = 0
    private var ringColor = 0
    private var ringW = 0f          // px; 0 = no ring (only the HUD has one)
    private var xpLabelW = 0f       // the width of the XP label beside the number-above style's XP bar

    /** The most any text line in the last drawing was taller than its row, in px (0 or less = every line fits); read by the tests. */
    internal var worstOverflow = 0f
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((TILE_W * d).toInt(), widthMeasureSpec), resolveSize((TILE_H * d).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rebuild()   // a picture may have come into memory since
    }

    /** Works out the background, the box's size and place and its drawable. */
    private fun rebuild() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        tile.set(0f, 0f, w, h)
        // the background: a cached battle picture laid as a cover crop of the tile, else the plain arena gradient
        picture = cachedPicture()
        val p = picture
        if (p != null) {
            val b = p.second
            val crop = BackdropDraw.coverCrop(b.width, b.height, w, h)
            bgMatrix.setScale(w / crop.w, h / crop.h)
            bgMatrix.preTranslate(-crop.x, -crop.y)
            bgPaint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.setLocalMatrix(bgMatrix) }
            val m = BackdropTint.matrix(dark && !p.first.dark)
            val tint = if (m == null) null else ColorMatrixColorFilter(ColorMatrix(m))
            bgPaint.colorFilter = tint
            val glass = GlassBlurCache.of(p.third)
            glassPaint.glassToBase.setScale(b.width.toFloat() / glass.width, b.height.toFloat() / glass.height)
            glassPaint.glassToBase.postConcat(bgMatrix)
            glassPaint.picture(glass, p.third.blur, false, tint)
        } else {
            bgPaint.colorFilter = null
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h, Ui.c(context, R.color.ag_arena_top), Ui.c(context, R.color.ag_arena_bot), Shader.TileMode.CLAMP)
            glassPaint.plain(Ui.c(context, R.color.ag_arena_top), Ui.c(context, R.color.ag_arena_bot), h)
        }
        platPaint.color = Ui.c(context, R.color.ag_plat2)
        // the box: a miniature of the real one, half size on a 100 x 64 dp tile (more on a bigger one)
        s = 0.5f * d * min(w / (TILE_W * d), h / (TILE_H * d))
        resolveContent()
        boxW = InfoBoxGeo.W * s
        boxH = InfoBoxGeo.height(style, wild = false, floor = 0f, xpText = true, reduced = false) * s
        val inset = 5f * d
        if (InfoBoxSkin.corners(look)) { boxX = inset; boxY = h - inset - boxH } else { boxX = (w - boxW) / 2f; boxY = (h - boxH) / 2f }
        val typeColor = Ui.typeColor(Type.WATER)   // a sample colour for the type look
        val card = Ui.c(context, R.color.ag_card)
        val glass = if (InfoBoxSkin.glass(look)) PreviewGlass() else null
        box = InfoBoxDrawable(s, InfoBoxSkin.layers(look, card, dark, typeColor), InfoBoxSkin.radiusDp(look, 12f), glass)
        box?.setBounds(0, 0, boxW.toInt(), boxH.toInt())
    }

    /** The colours, the text shadow, the bar ring and the XP label's width of the current look at the current size. */
    private fun resolveContent() {
        val ink = InfoBoxSkin.ink(look)
        colText = ink?.text ?: theme(R.color.ag_text)
        colSub = ink?.sub ?: theme(R.color.ag_sub0)
        colTrack = ink?.track ?: theme(R.color.ag_s0)
        colHp = ink?.hp(HP_FRAC) ?: Ui.hpColor(context, HP_FRAC)
        colXp = ink?.xp ?: theme(R.color.ag_sky)
        colLvl = ink?.tier(LEVEL) ?: theme(LevelTier.colorRes(LEVEL))
        val shadow = InfoBoxSkin.textShadow(look)
        for (p in arrayOf(text, bold)) {
            if (shadow == null) p.clearShadowLayer() else p.setShadowLayer(shadow.radiusDp * s, shadow.dxDp * s, shadow.dyDp * s, shadow.color)
        }
        val ring = InfoBoxSkin.barRing(look)
        ringColor = ring?.color ?: 0
        ringW = if (ring == null) 0f else max(1f, ring.widthDp * s)
        text.textSize = InfoBoxGeo.XP * s
        xpLabelW = text.measureText(XP_TEXT)
    }

    /** The glass: the tile's own background laid behind the box (the box sits at [boxX], [boxY] of the tile). */
    private inner class PreviewGlass : BoxBackdrop {
        private val toBox = Matrix()
        override fun drawBehind(canvas: Canvas, rect: RectF, r: Float) {
            toBox.setTranslate(-boxX, -boxY)
            glassPaint.draw(canvas, rect, r, toBox)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        // the background and its two platforms (the plain arena's)
        val rad = 8f * d
        canvas.drawRoundRect(tile, rad, rad, bgPaint)
        if (picture == null) {
            bar.set(tile.width() * 0.58f, tile.height() * 0.26f, tile.width() * 0.96f, tile.height() * 0.46f)
            canvas.drawOval(bar, platPaint)
            platPaint.color = Ui.c(context, R.color.ag_plat)
            bar.set(tile.width() * 0.04f, tile.height() * 0.62f, tile.width() * 0.52f, tile.height() * 0.92f)
            canvas.drawOval(bar, platPaint)
            platPaint.color = Ui.c(context, R.color.ag_plat2)
        }
        val b = box ?: return
        val save = canvas.save()
        canvas.translate(boxX, boxY)
        b.draw(canvas)
        drawContent(canvas)
        canvas.restoreToCount(save)
    }

    // ---- the box's content, a miniature of the real one (design dp x [s]); the same numbers as InfoBoxGeo ----

    private fun theme(res: Int) = Ui.c(context, res)

    private fun drawContent(canvas: Canvas) {
        worstOverflow = -Float.MAX_VALUE
        val frac = HP_FRAC
        val inside = style == InfoBoxStyle.INSIDE
        val padX = (if (inside) 8f else 9f) * s
        val x0 = padX
        val x1 = boxW - padX
        var y = 6f * s
        // the name line: the name on the left, "Lv" on the right
        val nameH = InfoBoxGeo.nameRow(0f, squares = false) * s
        line(canvas, "Name", x0, y, nameH, 14f, colText, Paint.Align.LEFT, bold)
        line(canvas, LV_TEXT, x1, y, nameH, 12f, colLvl, Paint.Align.RIGHT, bold)
        y += nameH
        if (!inside) {
            y += 3f * s
            val numH = TextMath.line(InfoBoxGeo.NUM) * s
            line(canvas, "HP", x0, y, numH, InfoBoxGeo.NUM, colSub, Paint.Align.LEFT, text)
            line(canvas, HP_TEXT, x1, y, numH, InfoBoxGeo.NUM, colText, Paint.Align.RIGHT, text)
            y += numH + 3f * s
            bar(canvas, x0, x1, y, 7f * s, frac, colHp, colTrack)
            y += 7f * s + 3f * s
            val xpH = max(3f * s, TextMath.line(InfoBoxGeo.XP) * s)
            bar(canvas, x0, x1 - xpLabelW - 5f * s, y + (xpH - 3f * s) / 2f, 3f * s, 0.64f, colXp, colTrack)
            line(canvas, XP_TEXT, x1, y, xpH, InfoBoxGeo.XP, colSub, Paint.Align.RIGHT, text)
        } else {
            y += 5f * s
            bar(canvas, x0, x1, y, 14f * s, frac, colHp, colTrack)
            label(canvas, HP_TEXT, (x0 + x1) / 2f, y, 14f * s, InfoBoxGeo.IN_HP)
            y += 14f * s + 3f * s
            bar(canvas, x0, x1, y, 10f * s, 0.64f, colXp, colTrack)
            label(canvas, XP_TEXT, (x0 + x1) / 2f, y, 10f * s, InfoBoxGeo.IN_XP)
        }
    }

    /** One line of text, [sp] design sp tall, centred in a row [rowH] px high starting at [top]; [x] is its left, right or centre by [align]. */
    private fun line(canvas: Canvas, str: String, x: Float, top: Float, rowH: Float, sp: Float, color: Int, align: Paint.Align, p: TextPaint) {
        p.textSize = sp * s
        p.color = color
        p.textAlign = align
        p.getFontMetrics(fm)
        worstOverflow = max(worstOverflow, (fm.descent - fm.ascent) - rowH)
        canvas.drawText(str, x, top + (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent, p)
    }

    /** A bar from [x0] to [x1], [h] px high at [top], filled [frac] of it in [color] over [track], with the HUD's ring round it (when the look has one). */
    private fun bar(canvas: Canvas, x0: Float, x1: Float, top: Float, h: Float, frac: Float, color: Int, track: Int) {
        if (x1 <= x0) return
        if (ringW > 0f) {
            val w = ringW
            fill.style = Paint.Style.STROKE; fill.strokeWidth = w; fill.color = ringColor
            bar.set(x0 - w / 2f, top - w / 2f, x1 + w / 2f, top + h + w / 2f)
            canvas.drawRoundRect(bar, bar.height() / 2f, bar.height() / 2f, fill)
            fill.style = Paint.Style.FILL
        }
        val r = h / 2f
        fill.style = Paint.Style.FILL; fill.color = track
        bar.set(x0, top, x1, top + h)
        canvas.drawRoundRect(bar, r, r, fill)
        fill.color = color
        bar.set(x0, top, x0 + (x1 - x0) * frac, top + h)
        canvas.drawRoundRect(bar, r, r, fill)
    }

    /** A number written in a bar (the inside style): dark with a light glow, as the real bar does it. */
    private fun label(canvas: Canvas, str: String, cx: Float, top: Float, h: Float, sp: Float) {
        glow.textSize = sp * s
        glow.textAlign = Paint.Align.CENTER
        glow.getFontMetrics(fm)
        worstOverflow = max(worstOverflow, glow.textSize - h)   // the digits sit in the bar: the font's size must not exceed it (its box is a little taller)
        val y = top + (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        glow.style = Paint.Style.STROKE; glow.strokeWidth = max(1f, glow.textSize * 0.22f); glow.color = 0xE6FFFFFF.toInt()
        canvas.drawText(str, cx, y, glow)
        glow.style = Paint.Style.FILL; glow.color = 0xFF1E1E2E.toInt()
        canvas.drawText(str, cx, y, glow)
    }

    private companion object {
        const val TILE_W = 100f
        const val TILE_H = 64f
        const val LEVEL = 24
        const val HP_FRAC = 0.8f
        const val LV_TEXT = "Lv 24"
        const val HP_TEXT = "98 / 120"
        const val XP_TEXT = "XP 64 / 100"

        /** The battle picture kept in memory (bg, its decoded bitmap, the pic) — or null. */
        fun cachedPicture(): Triple<Backdrop, android.graphics.Bitmap, BackdropPic>? {
            for (b in BackdropCatalog.ALL) {
                val p = Backdrops.inMemory(b) ?: continue
                return Triple(b, p.bitmap, p)
            }
            return null
        }
    }
}
