package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The round colour badge of a status effect on an info card (item 95): a filled circle with a drawn symbol, no text, so it
 * reads by shape as well as colour. Drop = poisoned (also badly poisoned), flame = burned, bolt = paralysed, Zz = asleep,
 * snowflake = frozen. The engine has no confusion. Colours come from the `ag_*` tokens (read raw: the accent mapping of
 * `Ui.c` must not turn the poison purple into the user's accent).
 */
class StatusBadge(ctx: Context, val status: String) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor(ctx, status) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkColor(fill.color) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink.color; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val glyph = Path()
    private val hole = Path()

    init {
        // the symbols live in a 24 x 24 box (circle radius 12); the view scales it
        when (status) {
            "psn", "tox" -> drop(glyph)
            "brn" -> flame(glyph, hole)
            "par" -> bolt(glyph)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val k = side / 24f
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(k, k)
        canvas.drawCircle(12f, 12f, 12f, fill)
        when (status) {
            "psn", "tox", "par" -> canvas.drawPath(glyph, ink)
            "brn" -> { canvas.drawPath(glyph, ink); canvas.drawPath(hole, fill) }
            "slp" -> zz(canvas)
            "frz" -> snow(canvas)
        }
        canvas.restore()
    }

    private fun zz(c: Canvas) {
        line.strokeWidth = 2.1f
        c.drawPath(Path().apply { moveTo(13.6f, 5.7f); lineTo(17.8f, 5.7f); lineTo(13.6f, 10.2f); lineTo(17.8f, 10.2f) }, line)
        line.strokeWidth = 2.5f
        c.drawPath(Path().apply { moveTo(6.2f, 14.1f); lineTo(12.4f, 14.1f); lineTo(6.2f, 19.7f); lineTo(12.4f, 19.7f) }, line)
    }

    private fun snow(c: Canvas) {
        line.strokeWidth = 2.1f
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 60.0)
            val dx = (cos(a) * 7.8).toFloat(); val dy = (sin(a) * 7.8).toFloat()
            c.drawLine(12f - dx, 12f - dy, 12f + dx, 12f + dy, line)
        }
        // a small V on each spoke gives the flake its look
        line.strokeWidth = 1.6f
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            val bx = 12f + (cos(a) * 4.6).toFloat(); val by = 12f + (sin(a) * 4.6).toFloat()
            for (side in listOf(-1.0, 1.0)) {
                val b = a + side * 0.8
                c.drawLine(bx, by, bx + (cos(b) * 2.6).toFloat(), by + (sin(b) * 2.6).toFloat(), line)
            }
        }
    }

    private fun drop(p: Path) {
        p.moveTo(12f, 4.2f)
        p.cubicTo(12f, 4.2f, 7.2f, 9.6f, 7.2f, 14f)
        p.arcTo(RectF(7.2f, 9.4f, 16.8f, 19f), 180f, -180f, false)
        p.cubicTo(16.8f, 9.6f, 12f, 4.2f, 12f, 4.2f)
        p.close()
    }

    private fun flame(outer: Path, inner: Path) {
        outer.moveTo(12.2f, 3.6f)
        outer.cubicTo(12.8f, 7f, 17.2f, 9.2f, 17.2f, 14.2f)
        outer.cubicTo(17.2f, 17.4f, 14.9f, 19.8f, 12f, 19.8f)
        outer.cubicTo(9.1f, 19.8f, 6.8f, 17.4f, 6.8f, 14.2f)
        outer.cubicTo(6.8f, 11.8f, 8f, 10.2f, 9.4f, 8.8f)
        outer.cubicTo(9.6f, 10.6f, 10.4f, 11.4f, 11.2f, 11.8f)
        outer.cubicTo(11f, 8.8f, 11.4f, 6f, 12.2f, 3.6f)
        outer.close()
        inner.moveTo(12f, 13f)
        inner.cubicTo(12f, 13f, 14.3f, 15f, 14.3f, 16.6f)
        inner.cubicTo(14.3f, 17.9f, 13.3f, 18.8f, 12f, 18.8f)
        inner.cubicTo(10.7f, 18.8f, 9.7f, 17.9f, 9.7f, 16.6f)
        inner.cubicTo(9.7f, 15f, 12f, 13f, 12f, 13f)
        inner.close()
    }

    private fun bolt(p: Path) {
        p.moveTo(13.8f, 3.4f)
        p.lineTo(7.2f, 13.2f)
        p.lineTo(11.4f, 13.2f)
        p.lineTo(10.2f, 20.6f)
        p.lineTo(16.8f, 10.4f)
        p.lineTo(12.6f, 10.4f)
        p.close()
    }

    companion object {
        /** The badge colour of an engine status: poison mauve, burn peach, paralysis yellow, sleep lavender, freeze sky. */
        @ColorRes fun colorRes(status: String?): Int = when (status) {
            "psn", "tox" -> R.color.ag_mauve
            "brn" -> R.color.ag_peach
            "par" -> R.color.ag_yellow
            "slp" -> R.color.ag_lav
            else -> R.color.ag_sky
        }

        fun fillColor(ctx: Context, status: String?): Int = ctx.getColor(colorRes(status))

        /** White symbol where it reads on the fill (the saturated light-theme colours), else the dark one (the pastel dark-theme colours). */
        fun inkColor(fill: Int): Int =
            if (ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), fill) >= 2.5) 0xFFFFFFFF.toInt() else 0xFF1E1E2E.toInt()
    }
}

/**
 * The small row directly under an info card's HP bar that holds the status badges (one per status; the engine has one at a
 * time). Its height is set by the card and stays whether or not a badge shows, so the bar keeps its full width and
 * nothing moves when an effect comes or goes. Tapping the row asks [onTap] for the explanation bubble.
 */
class StatusRow(ctx: Context) : LinearLayout(ctx) {
    var status: String? = null
        private set

    /** Called with this row when it is tapped while it shows a status. */
    var onTap: (StatusRow) -> Unit = {}

    private var badgePx = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { if (status != null) onTap(this) }
        isClickable = false
    }

    /** The badge in the row (null when there is none). */
    val badge: View? get() = getChildAt(0)

    fun set(status: String?) {
        val shown = StatusInfo.of(status)?.status
        if (shown == this.status && (shown == null) == (childCount == 0)) return
        this.status = shown
        removeAllViews()
        if (shown != null) addView(StatusBadge(context, shown), LayoutParams(badgePx, badgePx))
        isClickable = shown != null
        contentDescription = StatusInfo.of(shown)?.let { "${it.name}. Tap for what it does" }
    }

    /** The row is [heightPx] tall; its badge is a circle a little smaller than that. */
    fun fit(heightPx: Int) {
        badgePx = (heightPx - 2 * Ui.dp(context, 1)).coerceAtLeast(1)
        val lp = layoutParams as? LayoutParams
        if (lp != null && lp.height != heightPx) { lp.height = heightPx; layoutParams = lp }
        for (i in 0 until childCount) (getChildAt(i).layoutParams as LayoutParams).let { it.width = badgePx; it.height = badgePx }
        requestLayout()
    }
}

/**
 * The popup bubble of a status badge (mockup 12A): one line from [StatusInfo], dark on the light theme and light on the dark
 * one, a small tail towards the badge. It lives inside the arena so it can never leave the panel; [ArenaView] places it.
 */
class StatusBubble(ctx: Context) : TextView(ctx) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_text) }
    private val tail = Ui.dp(ctx, 5f)
    private val radius = Ui.dp(ctx, 10f)
    private val padX = Ui.dp(ctx, 10)
    private val padY = Ui.dp(ctx, 6)
    private var tailUp = false
    private var tailX = 0f
    private val path = Path()

    init {
        typeface = Ui.font(ctx, 700)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(ctx.getColor(R.color.ag_card))
        includeFontPadding = false
        maxLines = 3
        setLineSpacing(0f, 1.1f)
        isClickable = true
        prepare(false, 0f)
    }

    /** The tail points up (the bubble sits under the badge) or down, at [x] px from the bubble's left edge. */
    fun prepare(up: Boolean, x: Float) {
        tailUp = up; tailX = x
        setPadding(padX, padY + if (up) tail.toInt() else 0, padX, padY + if (up) 0 else tail.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val top = if (tailUp) tail else 0f
        val bottom = height - if (tailUp) 0f else tail
        canvas.drawRoundRect(0f, top, w, bottom, radius, radius, body)
        val x = tailX.coerceIn(radius + tail, (w - radius - tail).coerceAtLeast(radius + tail))
        path.rewind()
        if (tailUp) { path.moveTo(x - tail, top + 0.5f); path.lineTo(x, 0f); path.lineTo(x + tail, top + 0.5f) }
        else { path.moveTo(x - tail, bottom - 0.5f); path.lineTo(x, height.toFloat()); path.lineTo(x + tail, bottom - 0.5f) }
        path.close()
        canvas.drawPath(path, body)
        super.onDraw(canvas)
    }

    companion object {
        /** "Poisoned: loses ..." with the part up to the colon in the heavy weight. */
        fun text(ctx: Context, line: String): CharSequence {
            val s = android.text.SpannableString(line)
            val colon = line.indexOf(':')
            if (colon > 0) s.setSpan(HeavySpan(Ui.font(ctx, 900)), 0, colon + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }
    }

    private class HeavySpan(private val face: Typeface) : android.text.style.MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface = face }
        override fun updateMeasureState(p: TextPaint) { p.typeface = face }
    }
}
