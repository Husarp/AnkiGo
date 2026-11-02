package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.husarp.ankigo.R
import kotlin.math.abs
import kotlin.math.max

/**
 * R19-C (PLAN 125 X1): the stat-change pill of round 10 mockup A1-C. A short stat name (Atk / Def / SpA / SpD / Spe / Acc / Eva)
 * and drawn chevrons, one per stage, in columns of up to three; green for a rise, red for a drop. The pure rules live here; the
 * move bubble's chip is a [StatPillView]; the floating badge over the Pokémon ([FloatTextView]) shares the chevron shape.
 */
object StatPill {
    const val MAX_STAGES = 6

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

    /** How many chevrons a change of [stages] (signed) draws: its size, 1..6 (a change of 6 shows six). */
    fun count(stages: Int): Int = abs(stages).coerceIn(1, MAX_STAGES)

    /** "Attack up 2" / "Defense down 1" (the stages as really applied; all six stages are drawn). */
    fun spoken(stat: String, stages: Int): String = "${longName(stat)} ${if (stages > 0) "up" else "down"} ${abs(stages)}"

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Shared semantic palette, independent of the chosen accent. */
    fun background(ctx: Context, up: Boolean): Int = if (isDark(ctx)) {
        if (up) 0xFF293D34.toInt() else 0xFF462D3A.toInt()
    } else if (up) 0xFFE9F5ED.toInt() else 0xFFFCECEF.toInt()

    fun ink(up: Boolean, dark: Boolean = false): Int = if (dark) {
        if (up) 0xFF9BD4AD.toInt() else 0xFFFF9BAB.toInt()
    } else if (up) 0xFF287944.toInt() else 0xFFB22C42.toInt()

    const val CHIP_SCALE = 0.8f
    const val BOX_W = 12f
    const val STROKE = 2f
    fun boxHeight(n: Int): Float = 25f
    fun boxWidth(n: Int): Float = if (n > 3) 23f else 12f

    /** Each column is centred independently, including the one/two arrows after stage three. */
    fun chevrons(path: Path, n: Int, up: Boolean) {
        for (i in 0 until n) {
            val column = i / 3
            val count = minOf(3, n - column * 3)
            val y = (25f - (4f + (count - 1) * 7f)) / 2f + (i % 3) * 7f
            val x = column * 11f
            path.moveTo(x + 2f, y + if (up) 4f else 0f)
            path.lineTo(x + 6f, y + if (up) 0f else 4f)
            path.lineTo(x + 10f, y + if (up) 4f else 0f)
        }
    }
}

/**
 * The pill as a view: rounded 9 dp tint, the short name in 12 sp bold, then the chevrons in columns of three (8 dp chevrons, 7 dp vertical pitch).
 * [stages] is signed (positive = rise). Read by TalkBack as "Attack up 2".
 */
internal class StatPillView(ctx: Context, val stat: String, val stages: Int) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val up = stages > 0
    private val n = StatPill.count(stages)
    private val ink = StatPill.ink(up, StatPill.isDark(ctx))
    private val fill = StatPill.background(ctx, up)
    private val label = StatPill.shortName(stat) ?: stat
    private val unit = d * StatPill.CHIP_SCALE

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
        val w = padX * 2 + textW + gap + StatPill.boxWidth(n) * unit
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
