package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stat

/** The selected stage-chevron geometry, shared by nature effects and arena chips. */
internal object StageChevron {
    fun draw(c: Canvas, rect: RectF, count: Int, up: Boolean, color: Int) {
        c.save(); c.translate(rect.left, rect.top)
        c.scale(rect.width() / (if (count > 3) 41.6f else 24f), rect.height() / 24f)
        if (!up) { c.translate(0f, 24f); c.scale(1f, -1f) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = 3f; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        c.translate(2.4f, -0.5f); c.scale(1.6f, 1f)
        StatPill.chevrons(path, count, true)
        c.drawPath(path, p); c.restore()
    }
}

internal object NatureChanges {
    fun line(a: Context, nature: Nature): View {
        val plus = nature.plus; val minus = nature.minus
        if (plus == null || minus == null) return Ui.text(a, 12f, 700, R.color.ag_sub1).apply {
            text = DetailMath.natureEffect(nature)
        }
        return FlowRow(a, Ui.dp(a, 14), centered = false).apply {
            for ((stat, up) in listOf(plus to true, minus to false)) addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                val color = StatPill.ink(up, StatPill.isDark(a))
                addView(object : View(a) {
                    override fun onDraw(c: Canvas) = StageChevron.draw(c,
                        RectF(0f, 0f, width.toFloat(), height.toFloat()), 2, up, color)
                }, LinearLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 18)))
                val name = when (stat) {
                    Stat.ATK -> "Atk"; Stat.DEF -> "Def"; Stat.SPA -> "SpA"; Stat.SPD -> "SpD"; Stat.SPE -> "Spe"; Stat.HP -> "HP"
                }
                val words = "$name ${if (up) "+" else "−"}10%"
                addView(Ui.text(a, 12f, 800, R.color.ag_text).apply {
                    text = words; setTextColor(color)
                }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 4) })
                contentDescription = words
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            })
        }
    }
}
