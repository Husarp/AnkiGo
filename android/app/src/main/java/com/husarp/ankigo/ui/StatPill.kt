package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import kotlin.math.abs
import kotlin.math.max

/**
 * R19-C (PLAN 125 X1): the stat-change pill of round 10 mockup A1-C. A short stat name (Atk / Def / SpA / SpD / Spe / Acc / Eva)
 * and drawn chevrons, one per stage (at most three), stacked; green for a rise, red for a drop. The pure rules live here; the
 * move bubble's chip is a [StatPillView]; the floating badge over the Pokémon ([FloatTextView]) shares the chevron shape.
 */
object StatPill {
    const val MAX_STAGES = 3

    /** The mockup's green and red (text + chevrons; the pill is a tint of the same colour). */
    const val UP = 0xFF2E9E4F.toInt()
    const val DOWN = 0xFFD6453D.toInt()

    /** Tint strength of the pill: light 13 % (green) / 12 % (red), dark 22 % (both), as in the mockup. */
    fun tint(up: Boolean, dark: Boolean): Float = if (dark) 0.22f else if (up) 0.13f else 0.12f

    /** The short label of an engine stat key ("atk", "accuracy" ...), or null for a key that is no battle stat. */
    fun shortName(stat: String?): String? = when (stat) {
        "atk" -> "Atk"; "def" -> "Def"; "spa" -> "SpA"; "spd" -> "SpD"; "spe" -> "Spe"
        "accuracy" -> "Acc"; "evasion" -> "Eva"
        else -> null
    }

    /** The stat's full name, for what a screen reader says. */
    fun longName(stat: String): String = when (stat) {
        "atk" -> "Attack"; "def" -> "Defense"; "spa" -> "Special Attack"; "spd" -> "Special Defense"; "spe" -> "Speed"
        "accuracy" -> "Accuracy"; "evasion" -> "Evasion"
        else -> stat
    }

    /** How many chevrons a change of [stages] (signed) draws: its size, 1..3 (a change of 6 shows three). */
    fun count(stages: Int): Int = abs(stages).coerceIn(1, MAX_STAGES)

    /** "Attack up 2" / "Defense down 1" (the stages as really applied; only drawn chevrons are capped). */
    fun spoken(stat: String, stages: Int): String = "${longName(stat)} ${if (stages > 0) "up" else "down"} ${abs(stages)}"

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** The pill's fill: the card colour tinted with the pill's colour (opaque, so it sits on any bubble). */
    fun background(ctx: Context, up: Boolean): Int =
        ColorUtils.blendARGB(ctx.getColor(R.color.ag_card), if (up) UP else DOWN, tint(up, isDark(ctx)))

    fun ink(up: Boolean): Int = if (up) UP else DOWN

    // The chevron in the mockup's 12 x 11 box (viewBox units): M2 7 L6 3 L10 7, the next stage 5 units lower; stroke 2.4, round caps.
    const val BOX_W = 12f
    private const val BOX_H = 11f
    private const val STEP = 5f
    const val STROKE = 2.4f

    /** Height of [n] stacked chevrons in box units. */
    fun boxHeight(n: Int): Float = BOX_H + STEP * (n - 1)

    /** Adds the [n] stacked chevrons of a rise ([up]) or a drop to [path], in box units (x 0..12, y 0..boxHeight). */
    fun chevrons(path: Path, n: Int, up: Boolean) {
        for (i in 0 until n) {
            val y = i * STEP
            if (up) { path.moveTo(2f, y + 7f); path.lineTo(6f, y + 3f); path.lineTo(10f, y + 7f) }
            else { path.moveTo(2f, y + 3f); path.lineTo(6f, y + 7f); path.lineTo(10f, y + 3f) }
        }
    }
}

/**
 * The pill as a view: rounded 9 dp tint, the short name in 12 sp bold, then the stacked chevrons (13 dp wide box, as the mockup's svg).
 * [stages] is signed (positive = rise). Read by TalkBack as "Attack up 2".
 */
internal class StatPillView(ctx: Context, val stat: String, val stages: Int) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val up = stages > 0
    private val n = StatPill.count(stages)
    private val ink = StatPill.ink(up)
    private val fill = StatPill.background(ctx, up)
    private val label = StatPill.shortName(stat) ?: stat
    private val unit = 13f * d / StatPill.BOX_W // dp per box unit: the mockup's 12-unit box is drawn 13 px wide

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 800)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, ctx.resources.displayMetrics)
        color = ink
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        strokeWidth = StatPill.STROKE; color = ink
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val path = Path().also { StatPill.chevrons(it, n, up) }

    private val padX = 7f * d
    private val padY = 3f * d
    private val gap = 3f * d
    private val textW = text.measureText(label)
    private val textH = text.fontMetrics.let { it.descent - it.ascent }
    private val boxH = StatPill.boxHeight(n) * unit

    init {
        contentDescription = StatPill.spoken(stat, stages)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = padX * 2 + textW + gap + StatPill.BOX_W * unit
        val h = padY * 2 + max(textH, boxH)
        setMeasuredDimension(Math.ceil(w.toDouble()).toInt(), Math.ceil(h.toDouble()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 9f * d, 9f * d, bg)
        val fm = text.fontMetrics
        canvas.drawText(label, padX, h / 2f - (fm.ascent + fm.descent) / 2f, text)
        canvas.save()
        canvas.translate(padX + textW + gap, (h - boxH) / 2f)
        canvas.scale(unit, unit) // the scale carries the stroke width too (box units)
        canvas.drawPath(path, line)
        canvas.restore()
    }
}
