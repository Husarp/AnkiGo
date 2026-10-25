package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One floating battle text in the comic style (Round 3 pick 9C): heavy italic capitals, slightly tilted, a dark outline,
 * coloured by what it means. "Super effective!" sits in a yellow burst; a stat change is a small green / red badge with
 * one chevron per stage and the stat's short name. The view is as big as the tilted drawing, so the arena can clamp it
 * with [FloatSpot]. Never takes touches.
 */
internal class FloatTextView(ctx: Context, val ft: FloatText, floorSp: Float) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val badge = (ft.kind == FloatKind.STAT_UP || ft.kind == FloatKind.STAT_DOWN) && ft.stat != null && ft.stages > 0
    private val burst = ft.kind == FloatKind.SUPER
    private val label = (if (badge) ft.stat!! else ft.text).uppercase(Locale.ROOT)
    private val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextMath.at(Look.sizeSp(ft.kind), floorSp), ctx.resources.displayMetrics)
    private val fillColor = if (ft.kind == FloatKind.STATUS_DAMAGE) InfoBoxSkin.statusFill(ft.stat) else Look.fill(ft.kind) // PLAN 217: the status badge colour
    /** PLAN 172: "can't move" floats carry the status key; its badge is drawn in front of the text. */
    private val statusKey = if (ft.kind == FloatKind.STATUS && ft.stat in setOf("slp", "frz", "par")) ft.stat else null
    private val statusBadge = statusKey?.let { StatusBadge(ctx, it) }
    private val tiltDeg = if (badge) 0f else Look.tilt(ft.text)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 900)
        textSize = sizePx
        if (!badge) textSkewX = -0.22f
        letterSpacing = if (badge) 0.04f else 0.02f
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePx = max(2f * d, sizePx * 0.11f)

    // the unrotated drawing's size
    private val tw = text.measureText(label)
    // the ink of the (all-capitals) label, so the box is as tall as the letters and not as the font's ascent + descent
    private val ink = android.graphics.Rect().also { text.getTextBounds(label, 0, label.length, it) }
    private val th = ink.height().toFloat()
    private val inkMid = (ink.top + ink.bottom) / 2f
    private val skewPad = if (badge) 0f else abs(text.textSkewX) * th * 0.5f
    // R19-C: the chevron of mockup A1-C / A1-A: box 8 wide, 4 high, the next stage 5 units lower, stroke 2.4 (StatPill's shape)
    private val chevW = sizePx * 0.62f
    private val chevH = chevW * 0.5f
    private val chevStep = chevW * 0.625f
    private val chevStroke = chevW * 0.3f
    private val contentW: Float
    private val contentH: Float
    /** R19-I: the burst's font share (the text inside it is smaller so no letter touches the star) and the badge's side padding. */
    private val burstFit: FloatFrame.Burst?
    private val badgePad: Float
    /** R19-I: the room kept between the outline's outer edge and the view's edge. */
    private val room = FloatFrame.ROOM_DP * d

    init {
        val total = chevH + (ft.stages - 1).coerceAtLeast(0) * chevStep
        val badgeH = max(max(th + sizePx * 0.55f, total + sizePx * 0.6f), FloatFrame.pillMinH(max(th, total + max(1.6f * d, chevStroke)), d))
        burstFit = if (burst) FloatFrame.burst(tw, th, tw + skewPad + sizePx * 2.7f, th + sizePx * 1.3f, d) else null
        badgePad = if (badge) FloatFrame.pillPad(sizePx * 0.6f, badgeH, d) { pad ->
            val w = pad + chevW + 3f * d + tw + pad
            val cx = -w / 2f + pad + chevW / 2f
            val chev = FloatFrame.Ink(cx, 0f, chevW + max(1.6f * d, chevStroke), total + max(1.6f * d, chevStroke))
            val txt = FloatFrame.Ink(cx + chevW / 2f + 3f * d + tw / 2f, 0f, tw, th)
            w to listOf(chev, txt)
        } else 0f
        burstFit?.let { text.textSize = sizePx * it.k }
        contentW = when {
            badge -> badgePad + chevW + 3f * d + tw + badgePad
            burst -> burstFit!!.w
            statusBadge != null -> tw + FloatFrame.SKEW * th + 2f * strokePx + th * 1.4f + 2f * room
            else -> tw + FloatFrame.SKEW * th + 2f * strokePx + 2f * room
        }
        contentH = when {
            badge -> badgeH
            burst -> burstFit!!.h
            statusBadge != null -> th * 1.3f + 2f * strokePx + 2f * room
            else -> th + 2f * strokePx + 2f * room
        }
        isClickable = false
        contentDescription = FloatTexts.spoken(ft.text, ft.kind, ft.stat)
    }

    private val rad = Math.toRadians(tiltDeg.toDouble())
    private val boxW = ceil(contentW * abs(cos(rad)) + contentH * abs(sin(rad))).toInt()
    private val boxH = ceil(contentW * abs(sin(rad)) + contentH * abs(cos(rad))).toInt()

    /**
     * R19-I: the least room (dp) between the real ink (letters, chevrons) and the frame round it: the burst's or the pill's drawn edge, or
     * for a plain outlined text the view's edge past the outline. [FloatFrame.ROOM_DP] or more for every kind.
     */
    internal fun frameRoomDp(): Float {
        val px = when {
            burst -> FloatFrame.burstRoom(FloatFrame.skewedInk(tw, th, burstFit!!.k), contentW, contentH, d)
            badge -> {
                val total = chevH + (ft.stages - 1) * chevStep
                val cx = -contentW / 2f + badgePad + chevW / 2f
                FloatFrame.pillRoom(listOf(
                    FloatFrame.Ink(cx, 0f, chevW + max(1.6f * d, chevStroke), total + max(1.6f * d, chevStroke)),
                    FloatFrame.Ink(cx + chevW / 2f + 3f * d + tw / 2f, 0f, tw, th)), contentW, contentH, d)
            }
            else -> {
                val left = if (statusBadge != null) room else (contentW - (tw + FloatFrame.SKEW * th)) / 2f - strokePx
                minOf(left, (contentW - (tw + FloatFrame.SKEW * th)) / 2f - strokePx, (contentH - th) / 2f - strokePx)
            }
        }
        return px / d
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(boxW, boxH)

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(tiltDeg)
        val k = burstFit?.k ?: 1f
        val baseline = -inkMid * k
        when {
            badge -> drawBadge(canvas, baseline)
            burst -> {
                drawBurst(canvas)
                text.style = Paint.Style.FILL
                text.color = Look.BURST_INK
                text.clearShadowLayer()
                canvas.drawText(label, -(tw + FloatFrame.SKEW * th) * k / 2f, baseline, text)   // R19-I: the lean's overhang centred too
            }
            else -> {
                var tx = -(tw + FloatFrame.SKEW * th) / 2f
                if (statusBadge != null) {
                    val b = (th * 1.3f).toInt()
                    tx = -contentW / 2f + room + strokePx + th * 1.4f
                    statusBadge.layout(0, 0, b, b)
                    canvas.save()
                    canvas.translate(-contentW / 2f + room + strokePx, -b / 2f)
                    statusBadge.draw(canvas)
                    canvas.restore()
                }
                text.style = Paint.Style.STROKE
                text.strokeWidth = strokePx * 2f
                text.strokeJoin = Paint.Join.ROUND
                text.color = Look.OUTLINE
                text.setShadowLayer(3f * d, 0f, 1.2f * d, 0x99000000.toInt())
                canvas.drawText(label, tx, baseline, text)
                text.style = Paint.Style.FILL
                text.strokeWidth = 0f
                text.clearShadowLayer()
                text.color = fillColor
                canvas.drawText(label, tx, baseline, text)
            }
        }
        canvas.restore()
    }

    /** A 16-point star, as wide and high as the content, yellow with a dark rim. */
    private fun drawBurst(canvas: Canvas) {
        val rx = contentW / 2f - 1.5f * d
        val ry = contentH / 2f - 1.5f * d
        val p = Path()
        val n = 16
        for (i in 0 until n * 2) {
            val a = Math.PI * i / n
            val r = if (i % 2 == 0) 1f else 0.86f
            val x = (rx * r * cos(a)).toFloat()
            val y = (ry * r * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        shape.style = Paint.Style.FILL
        shape.color = fillColor
        canvas.drawPath(p, shape)
        line.color = Look.OUTLINE
        line.strokeWidth = 2f * d
        canvas.drawPath(p, line)
    }

    private fun drawBadge(canvas: Canvas, baseline: Float) {
        val r = RectF(-contentW / 2f, -contentH / 2f, contentW / 2f, contentH / 2f)
        shape.style = Paint.Style.FILL
        shape.color = fillColor
        canvas.drawRoundRect(r, contentH / 2f, contentH / 2f, shape)
        line.color = Look.OUTLINE
        line.strokeWidth = 1.5f * d
        canvas.drawRoundRect(r, contentH / 2f, contentH / 2f, line)
        // chevrons: one per stage, stacked, pointing up for a rise and down for a drop
        val up = ft.kind == FloatKind.STAT_UP
        val cx = r.left + badgePad + chevW / 2f
        val total = chevH + (ft.stages - 1) * chevStep
        line.color = 0xFFFFFFFF.toInt()
        line.strokeWidth = max(1.6f * d, chevStroke)
        val path = Path()
        for (i in 0 until ft.stages) {
            val top = -total / 2f + i * chevStep
            path.reset()
            if (up) { path.moveTo(cx - chevW / 2f, top + chevH); path.lineTo(cx, top); path.lineTo(cx + chevW / 2f, top + chevH) }
            else { path.moveTo(cx - chevW / 2f, top); path.lineTo(cx, top + chevH); path.lineTo(cx + chevW / 2f, top) }
            canvas.drawPath(path, line)
        }
        text.style = Paint.Style.FILL
        text.color = 0xFFFFFFFF.toInt()
        text.clearShadowLayer()
        canvas.drawText(label, cx + chevW / 2f + 3f * d, baseline, text)
    }

    /** Sizes, colours and tilt per kind. Fixed colours: they sit on the arena's sky and sprites in both themes, behind a dark outline. */
    internal object Look {
        const val OUTLINE = 0xE6141420.toInt()
        const val BURST_INK = 0xFF2A1B3D.toInt()

        fun sizeSp(k: FloatKind): Float = when (k) {
            FloatKind.DAMAGE -> 22f
            FloatKind.STATUS_DAMAGE -> 22f
            FloatKind.BIG_DAMAGE -> 27f
            FloatKind.MISS -> 17f
            FloatKind.NO_EFFECT -> 15f
            FloatKind.NOT_VERY -> 13f
            FloatKind.SUPER -> 13f
            FloatKind.CRIT -> 16f
            FloatKind.STATUS -> 15f
            FloatKind.STAT_UP, FloatKind.STAT_DOWN -> 10.5f
            FloatKind.HEAL -> 22f
            FloatKind.HESITATE -> 15f
            FloatKind.INFO -> 14f
        }

        fun fill(k: FloatKind): Int = when (k) {
            FloatKind.DAMAGE -> 0xFFFF5C77.toInt()      // red
            FloatKind.STATUS_DAMAGE -> 0xFFFF5C77.toInt() // the view uses InfoBoxSkin.statusFill(status); red only as a fallback
            FloatKind.BIG_DAMAGE -> 0xFFFF8A3D.toInt()  // hot orange
            FloatKind.MISS, FloatKind.HESITATE -> 0xFFFFFFFF.toInt()
            FloatKind.NO_EFFECT -> 0xFFF2F2FA.toInt()
            FloatKind.NOT_VERY -> 0xFFDADCF0.toInt()
            FloatKind.SUPER -> 0xFFFFD84A.toInt()       // the burst
            FloatKind.CRIT -> 0xFFFFE066.toInt()
            FloatKind.STATUS -> 0xFFFF9A4D.toInt()      // orange
            FloatKind.STAT_UP -> 0xFF2E9E34.toInt()
            FloatKind.STAT_DOWN -> 0xFFD21F44.toInt()
            FloatKind.HEAL -> 0xFF5EE07A.toInt()        // green
            FloatKind.INFO -> 0xFFB9C0FF.toInt()
        }

        private val TILTS = floatArrayOf(-6f, 4f, -4f, 6f, -3f)

        /** A small fixed tilt (degrees) per text, so the same text always leans the same way. */
        fun tilt(text: String): Float = TILTS[(text.hashCode() and 0x7FFFFFFF) % TILTS.size]
    }
}
