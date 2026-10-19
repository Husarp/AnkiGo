package com.husarp.ankigo.ui

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import kotlin.math.max
import kotlin.math.min

/** Fill and text colour of one chip. */
internal class ChipColors(val bg: Int, val fg: Int)

/**
 * The look of the move bubble (Round 9 D3-E2): colours, the shape with its tail, the header and the chip colours, in one place so
 * another mockup look can replace it without touching the bar or what the bubble says. [E2A] is the dark bubble like the status
 * bubble; [E2B] is a card in the card colour with a header band tinted by the move's type (Adam's pick). [MoveBubbleLook.create]
 * is the one place that chooses.
 */
internal abstract class MoveBubbleLook(protected val ctx: Context) {
    protected fun c(res: Int) = ctx.getColor(res)
    protected fun dp(v: Float) = Ui.dp(ctx, v)

    abstract val minDp: Float
    abstract val maxDp: Float
    /** Padding (dp: left, top, right, bottom) around the header, and around the number + chips row under it. */
    abstract val headerPad: FloatArray
    abstract val bodyPad: FloatArray
    abstract val damageSp: Float
    /** The header sits on a full-width band ([bandFill]) instead of the bubble's own fill. */
    open val band: Boolean get() = false

    /** Text colour of the number (the plain one). */
    abstract val text: Int
    abstract fun header(content: BubbleContent): View
    open fun bandFill(content: BubbleContent): Int = 0

    /** Draws the body (0..[h] tall, [w] wide, [bandH] of it the header band) and a tail pointing down at [tipX] below it. */
    abstract fun paint(canvas: Canvas, w: Float, h: Float, bandH: Float, tipX: Float, content: BubbleContent?)

    fun damageColor(tone: DamageTone): Int = when (tone) {
        DamageTone.PLAIN -> text
        DamageTone.RED -> c(R.color.ag_red)
        DamageTone.MUTED -> ColorUtils.setAlphaComponent(text, (255 * 0.78f).toInt())
    }

    /** The chips keep the same soft fills on either look: tinted from the card colour, dark text on them (the mockup's `soft()`). */
    fun chipColors(chip: BubbleChip): ChipColors {
        val card = c(R.color.ag_card)
        val txt = c(R.color.ag_text)
        fun soft(res: Int, pct: Int) = ColorUtils.blendARGB(card, c(res), pct / 100f)
        return when (chip.tone) {
            BubbleTone.GOOD -> ChipColors(c(R.color.ag_green), c(R.color.ag_crust))
            BubbleTone.BAD -> ChipColors(c(R.color.ag_red), c(R.color.ag_crust))
            BubbleTone.NEUTRAL -> ChipColors(soft(R.color.ag_ov, 40), txt)
            BubbleTone.IMMUNE -> ChipColors(soft(R.color.ag_blue, 22), txt)
            BubbleTone.CRIT -> ChipColors(c(R.color.ag_yellow), 0xFF1E1E2E.toInt())
            BubbleTone.STATUS -> ChipColors(ColorUtils.blendARGB(card, StatusBadge.fillColor(ctx, chip.status), 0.22f), txt)
            BubbleTone.BOOST -> ChipColors(soft(R.color.ag_teal, 24), txt)
            BubbleTone.MUTED -> ChipColors(c(R.color.ag_mantle), c(if (chip.icon == ChipIcon.NONE) R.color.ag_sub0 else R.color.ag_sub1))
        }
    }

    protected fun headerText(sp: Float, color: Int): TextView = Ui.text(ctx, sp, 800, R.color.ag_text).apply {
        setTextColor(color)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    // ------------------------------------------------------------ E2a: dark bubble

    private class E2A(ctx: Context) : MoveBubbleLook(ctx) {
        override val minDp = 180f
        override val maxDp = 250f
        override val headerPad = floatArrayOf(11f, 8f, 11f, 5f)
        override val bodyPad = floatArrayOf(11f, 0f, 11f, 9f)
        override val damageSp = 22f
        override val text get() = c(R.color.ag_card)
        private val fill get() = c(R.color.ag_text)
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tail = Path()

        override fun header(content: BubbleContent): View {
            val s = SpannableStringBuilder(content.who)
            s.setSpan(ForegroundColorSpan(ColorUtils.setAlphaComponent(text, (255 * 0.75f).toInt())), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.append(" · ").append(content.move)
            return headerText(11.5f, text).apply { this.text = s }
        }

        override fun paint(canvas: Canvas, w: Float, h: Float, bandH: Float, tipX: Float, content: BubbleContent?) {
            val r = dp(12f)
            body.color = fill
            body.setShadowLayer(dp(8f), 0f, dp(3f), 0x52000000)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, body)
            body.clearShadowLayer()
            Tail.draw(canvas, tail, body, tipX, h, dp(7f))
        }
    }

    // ------------------------------------------------------------ E2b: light card, type-coloured header band

    private class E2B(ctx: Context) : MoveBubbleLook(ctx) {
        override val minDp = 196f
        override val maxDp = 260f
        override val headerPad = floatArrayOf(10f, 6f, 10f, 6f)
        override val bodyPad = floatArrayOf(10f, 7f, 10f, 9f)
        override val damageSp = 24f
        override val band get() = true
        override val text get() = c(R.color.ag_text)
        private val card get() = c(R.color.ag_card)
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val clip = Path()
        private val tail = Path()
        private val rect = RectF()

        override fun bandFill(content: BubbleContent): Int = content.type?.let { TypeChips.fill(ctx, it) } ?: c(R.color.ag_mantle)
        private fun bandInk(content: BubbleContent): Int = content.type?.let { TypeChips.ink(ctx, it) } ?: c(R.color.ag_sub1)

        override fun header(content: BubbleContent): View {
            val ink = bandInk(content)
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val t = content.type
                if (t != null) {
                    val chip = Ui.typeChip(ctx, t)
                    chip.background = null   // the band is the tint
                    chip.setPadding(0, 0, 0, 0)
                    chip.minHeight = 0
                    addView(chip, LinearLayout.LayoutParams(-2, -2))
                } else addView(headerText(12f, ink).apply { this.text = content.move }, LinearLayout.LayoutParams(-2, -2))
                addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                addView(headerText(12f, ink).apply { this.text = content.who; alpha = 0.8f }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8f).toInt() })
            }
        }

        override fun paint(canvas: Canvas, w: Float, h: Float, bandH: Float, tipX: Float, content: BubbleContent?) {
            val r = dp(12f)
            body.color = card
            body.setShadowLayer(dp(8f), 0f, dp(3f), 0x47000000)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, body)
            body.clearShadowLayer()
            Tail.draw(canvas, tail, body, tipX, h, dp(7f))
            canvas.save()
            rect.set(0f, 0f, w, h)
            clip.rewind(); clip.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.clipPath(clip)
            if (content != null && bandH > 0f) { body.color = bandFill(content); canvas.drawRect(0f, 0f, w, bandH, body) }
            canvas.restore()
            ring.strokeWidth = dp(1f)
            ring.color = ColorUtils.setAlphaComponent(c(R.color.ag_text), 26)
            rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)
            canvas.drawRoundRect(rect, r, r, ring)
        }
    }

    private object Tail {
        /** A [half]-wide, [half]-tall triangle under the body at [tipX], pointing down, sharing the body's paint colour. */
        fun draw(canvas: Canvas, path: Path, paint: Paint, tipX: Float, bodyH: Float, half: Float) {
            path.rewind()
            path.moveTo(tipX - half, bodyH - 0.5f); path.lineTo(tipX + half, bodyH - 0.5f); path.lineTo(tipX, bodyH + half); path.close()
            canvas.drawPath(path, paint)
        }
    }

    enum class Variant { E2A, E2B }

    companion object {
        /** The look in use: Adam picked E2b (round 9, picks 3); the ticket had described E2a, which stays one constant away. */
        val CURRENT = Variant.E2B

        fun create(ctx: Context, variant: Variant = CURRENT): MoveBubbleLook = when (variant) { Variant.E2A -> E2A(ctx); Variant.E2B -> E2B(ctx) }
        const val TAIL_DP = 7f
    }
}

/** The symbols in the chips, drawn from the mockup's 16 x 16 paths in one colour. */
internal class ChipIconDrawable(private val icon: ChipIcon, color: Int) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val missDash = DashPathEffect(floatArrayOf(2.6f, 2f), 0f)
    private val path = Path()

    init {
        when (icon) {
            ChipIcon.UP -> { path.moveTo(8f, 2f); path.lineTo(14f, 9f); path.lineTo(10f, 9f); path.lineTo(10f, 14f); path.lineTo(6f, 14f); path.lineTo(6f, 9f); path.lineTo(2f, 9f); path.close() }
            ChipIcon.DOWN -> { path.moveTo(8f, 14f); path.lineTo(2f, 7f); path.lineTo(6f, 7f); path.lineTo(6f, 2f); path.lineTo(10f, 2f); path.lineTo(10f, 7f); path.lineTo(14f, 7f); path.close() }
            ChipIcon.CRIT -> {
                path.moveTo(8f, 0.8f); path.cubicTo(8.6f, 5.4f, 10.6f, 7.4f, 15.2f, 8f); path.cubicTo(10.6f, 8.6f, 8.6f, 10.6f, 8f, 15.2f)
                path.cubicTo(7.4f, 10.6f, 5.4f, 8.6f, 0.8f, 8f); path.cubicTo(5.4f, 7.4f, 7.4f, 5.4f, 8f, 0.8f); path.close()
            }
            else -> {}
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val k = b.width() / 16f
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(k, k)
        when (icon) {
            ChipIcon.UP, ChipIcon.DOWN, ChipIcon.CRIT -> canvas.drawPath(path, fill)
            ChipIcon.IMMUNE -> {
                line.strokeWidth = 1.8f; line.pathEffect = null
                canvas.drawCircle(8f, 8f, 6f, line)
                canvas.drawLine(4f, 12f, 12f, 4f, line)
            }
            ChipIcon.MISS -> {
                line.strokeWidth = 1.8f; line.pathEffect = missDash
                canvas.drawCircle(8f, 8f, 5.5f, line)
            }
            ChipIcon.NONE -> {}
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; line.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { fill.colorFilter = colorFilter; line.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * The details bubble over the action bar (Round 9 D3-E2): who and (look permitting) which move, the big number, and the chips of
 * [ActionBarModel.bubble]. Its tail points at the tapped half ([tailX]); the host places it. The look is [MoveBubbleLook].
 */
internal class MoveBubble(ctx: Context, private val look: MoveBubbleLook) : ViewGroup(ctx) {
    private var header: View? = null
    private var number: TextView? = null
    private val chipViews = ArrayList<View>()
    private var content: BubbleContent? = null

    /** x of the tail's tip, px from this view's left edge. */
    var tailX = 0f
        set(v) { field = v; invalidate() }

    private var bodyH = 0
    private var bandH = 0
    private var stacked = false
    private val tail get() = Ui.dp(context, MoveBubbleLook.TAIL_DP)

    init {
        setWillNotDraw(false)
        clipChildren = false
        isClickable = true   // a tap on the bubble stays on the bubble: nothing underneath reacts
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(c: BubbleContent) {
        content = c
        removeAllViews(); chipViews.clear()
        contentDescription = c.spoken()
        header = look.header(c).also { addView(it) }
        number = c.damage?.let { d ->
            Ui.text(context, look.damageSp, 900, R.color.ag_text).apply {
                text = d
                setTextColor(look.damageColor(c.damageTone))
                letterSpacing = -0.014f
                maxLines = 1
                if (c.damageTone == DamageTone.MUTED) TextFloor.set(this, 17f, 0f)
            }.also { addView(it) }
        }
        for (chip in c.chips) chipViews += chipView(chip).also { addView(it) }
    }

    private fun chipView(chip: BubbleChip): View {
        val colors = look.chipColors(chip)
        val sp = 11.5f
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(colors.bg, 7, context)
            val status = chip.status
            val icon: View? = when {
                chip.tone == BubbleTone.STATUS && status != null -> StatusBadge(context, status)
                chip.icon != ChipIcon.NONE -> ImageView(context).apply { setImageDrawable(ChipIconDrawable(chip.icon, colors.fg)) }
                else -> null
            }
            val side = Ui.dp(context, 7)
            if (icon != null) {
                val s = Ui.dp(context, if (status != null) 16 else 13)
                addView(icon, LinearLayout.LayoutParams(s, s).also { it.marginEnd = Ui.dp(context, 4) })
                setPadding(if (status != null) Ui.dp(context, 3) else side, Ui.dp(context, if (status != null) 2 else 3), side, Ui.dp(context, if (status != null) 2 else 3))
            } else setPadding(side, Ui.dp(context, 3), side, Ui.dp(context, 3))
            addView(Ui.text(context, sp, 900, R.color.ag_text).apply {
                text = chip.label
                setTextColor(colors.fg)
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, -2))
        }
    }

    // ------------------------------------------------------------ layout

    private val chipAt = ArrayList<IntArray>()
    private var hdrPad = IntArray(4)
    private var bodyPad = IntArray(4)
    private var rowTop = 0
    private var rowHeight = 0
    private var flowLeft = 0
    private var flowTop = 0
    private var numberTop = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val avail = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(widthMeasureSpec)
        val maxW = min((look.maxDp * d).toInt(), avail)
        val minW = min((look.minDp * d).toInt(), maxW)
        val hp = IntArray(4) { (look.headerPad[it] * d + 0.5f).toInt() }
        val bp = IntArray(4) { (look.bodyPad[it] * d + 0.5f).toInt() }
        hdrPad = hp; bodyPad = bp
        val gap = (10 * d).toInt(); val cg = (5 * d).toInt()
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val hdr = header
        if (content == null || hdr == null) { setMeasuredDimension(minW, 0); return }
        hdr.measure(unspec, unspec)
        val num = number
        num?.measure(unspec, unspec)
        chipViews.forEach { it.measure(unspec, unspec) }
        val numW = num?.measuredWidth ?: 0
        val numH = num?.measuredHeight ?: 0
        val chipsW = chipViews.sumOf { it.measuredWidth } + cg * max(0, chipViews.size - 1)
        val widest = chipViews.maxOfOrNull { it.measuredWidth } ?: 0
        val rowW = if (num != null && chipViews.isNotEmpty()) numW + gap + chipsW else numW + chipsW
        val w = max(max(hp[0] + hdr.measuredWidth + hp[2], bp[0] + rowW + bp[2]), minW).coerceAtMost(maxW)
        // the header takes what is left of the width (the band look right-aligns the name; a long name is cut)
        val hdrW = if (look.band) w - hp[0] - hp[2] else min(hdr.measuredWidth, w - hp[0] - hp[2])
        hdr.measure(MeasureSpec.makeMeasureSpec(max(hdrW, 0), MeasureSpec.EXACTLY), unspec)
        val inner = w - bp[0] - bp[2]
        val besideW = if (num != null) inner - numW - gap else inner
        stacked = num != null && chipViews.isNotEmpty() && widest > besideW   // too wide to sit beside the number: under it
        val flowW = if (stacked || num == null) inner else besideW
        chipAt.clear()
        var x = 0; var y = 0; var rowH = 0
        for (v in chipViews) {
            if (x > 0 && x + v.measuredWidth > flowW) { x = 0; y += rowH + cg; rowH = 0 }
            chipAt += intArrayOf(x, y)
            x += v.measuredWidth + cg
            rowH = max(rowH, v.measuredHeight)
        }
        val chipsH = if (chipViews.isEmpty()) 0 else y + rowH
        bandH = hp[1] + hdr.measuredHeight + hp[3]
        rowTop = bandH + bp[1]
        rowHeight = if (stacked) numH + cg + chipsH else max(numH, chipsH)
        numberTop = if (stacked) rowTop else rowTop + (rowHeight - numH) / 2
        flowLeft = bp[0] + if (num != null && !stacked) numW + gap else 0
        flowTop = if (stacked) rowTop + numH + cg else rowTop + (rowHeight - chipsH) / 2
        bodyH = rowTop + rowHeight + bp[3]
        setMeasuredDimension(w, bodyH + tail.toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val hdr = header ?: return
        hdr.layout(hdrPad[0], hdrPad[1], hdrPad[0] + hdr.measuredWidth, hdrPad[1] + hdr.measuredHeight)
        number?.let { it.layout(bodyPad[0], numberTop, bodyPad[0] + it.measuredWidth, numberTop + it.measuredHeight) }
        chipViews.forEachIndexed { i, v ->
            val px = flowLeft + chipAt[i][0]; val py = flowTop + chipAt[i][1]
            v.layout(px, py, px + v.measuredWidth, py + v.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val d = resources.displayMetrics.density
        val tip = tailX.coerceIn(15f * d, max(15f * d, w - 15f * d))
        look.paint(canvas, w, bodyH.toFloat(), if (look.band) bandH.toFloat() else 0f, tip, content)
    }

    /** The 140 ms pop (scale .94 to 1 with a fade); nothing with reduced motion. */
    fun popIn(still: Boolean) {
        animate().cancel()
        if (still) { scaleX = 1f; scaleY = 1f; alpha = 1f; return }
        scaleX = 0.94f; scaleY = 0.94f; alpha = 0f
        animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).setInterpolator(POP).start()
    }

    private companion object {
        val POP = TimeInterpolator { t -> 1f - (1f - t) * (1f - t) }
    }
}
