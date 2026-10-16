package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stat

// TICKET U-09: pieces shared by the Pokémon details (MonDetail) and the Pokédex entry (DexDetail).

/** Pure helpers of the details screens (unit-tested). */
object DetailMath {
    /** Top of a Pokémon's stat bars at [level]: a good stat of that level fills most of the bar. */
    fun monStatMax(level: Int): Int = 100 + level.coerceIn(1, 100) * 3

    /** Bar fill 0..1 of [value] against [max]. */
    fun fraction(value: Int, max: Int): Float = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)

    /** +1 when the nature raises [s], -1 when it lowers it, else 0. */
    fun natureDir(n: Nature, s: Stat): Int = when {
        n.plus == null || n.minus == null -> 0
        n.plus == s -> 1
        n.minus == s -> -1
        else -> 0
    }

    /** "▲", "▼" or "" for [natureDir]. */
    fun arrow(dir: Int): String = when { dir > 0 -> "▲"; dir < 0 -> "▼"; else -> "" }

    fun statName(s: Stat): String = when (s) {
        Stat.HP -> "HP"; Stat.ATK -> "Attack"; Stat.DEF -> "Defense"
        Stat.SPA -> "Sp. Atk"; Stat.SPD -> "Sp. Def"; Stat.SPE -> "Speed"
    }

    /** "Raises Speed, lowers Sp. Atk", or "Neutral" for the five natures that change nothing. */
    fun natureEffect(n: Nature): String {
        val p = n.plus
        val m = n.minus
        return if (p == null || m == null) "Neutral" else "Raises ${statName(p)}, lowers ${statName(m)}"
    }

    @ColorRes fun statColor(s: Stat): Int = when (s) {
        Stat.HP -> R.color.ag_green; Stat.ATK -> R.color.ag_peach; Stat.DEF -> R.color.ag_yellow
        Stat.SPA -> R.color.ag_blue; Stat.SPD -> R.color.ag_teal; Stat.SPE -> R.color.ag_pink
    }

    val LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
}

/** Wrapping row (centred lines) for the tag pills. */
class FlowRow(ctx: Context, private val gapPx: Int) : ViewGroup(ctx) {
    private val lines = ArrayList<IntArray>() // per line: first child index, end index, width, height

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        lines.clear()
        var start = 0
        var w = 0
        var h = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            val add = c.measuredWidth + if (w > 0) gapPx else 0
            if (w > 0 && w + add > maxW) {
                lines += intArrayOf(start, i, w, h)
                start = i; w = 0; h = 0
            }
            w += c.measuredWidth + if (w > 0) gapPx else 0
            h = maxOf(h, c.measuredHeight)
        }
        if (w > 0) lines += intArrayOf(start, childCount, w, h)
        val total = lines.sumOf { it[3] } + gapPx * maxOf(0, lines.size - 1) + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), total)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop
        val inner = r - l - paddingLeft - paddingRight
        for (line in lines) {
            var x = paddingLeft + (inner - line[2]) / 2
            for (i in line[0] until line[1]) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                c.layout(x, y + (line[3] - c.measuredHeight) / 2, x + c.measuredWidth, y + (line[3] + c.measuredHeight) / 2)
                x += c.measuredWidth + gapPx
            }
            y += line[3] + gapPx
        }
    }
}

/** Builders of the details look. [compact] = small window: tighter rows. */
object DetailParts {
    /** Small window (floating card, split screen): by width or height. */
    fun compact(ctx: Context): Boolean {
        val c = ctx.resources.configuration
        return c.screenWidthDp in 1..379 || c.screenHeightDp in 1..559
    }

    /** Section header: tinted icon + caps title. */
    fun header(a: Context, @DrawableRes icon: Int, title: String, compact: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(a, if (compact) 10 else 14), 0, Ui.dp(a, 4))
        val img = ImageView(a).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(a, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
        }
        addView(img, LinearLayout.LayoutParams(Ui.dp(a, 15), Ui.dp(a, 15)))
        addView(Ui.text(a, 11f, 900, R.color.ag_sub0).apply {
            text = title.uppercase(); letterSpacing = 0.06f
        }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 6) })
    }

    /** Popup with the explanation [i] (reuses the app's small info dialog). */
    fun showInfo(a: Context, i: StatInfoText) {
        val body = Ui.text(a, 14f, 700, R.color.ag_text).apply {
            text = i.text
            setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8))
        }
        Pickers.info(a, i.title, body)
    }

    /** Makes [v] open the popup for [i] (touch area at least 30 dp tall). */
    fun tapInfo(v: View, i: StatInfoText): View = v.apply {
        minimumHeight = Ui.dp(context, 30)
        contentDescription = "${i.title}: ${i.text}"
        setOnClickListener { showInfo(context, i) }
    }

    /** Small grey "Tap a stat name ..." line at the top of a stats block. */
    fun statHint(a: Context): View = Ui.text(a, 11f, 700, R.color.ag_ov).apply {
        text = StatInfo.HINT
        setPadding(0, 0, 0, Ui.dp(a, 2))
    }

    /** "Label   value" line for the Info / Evolution sections. */
    fun line(a: Context, label: String, value: CharSequence, labelDp: Int = 84, compact: Boolean = false): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, if (compact) 1 else 2), 0, Ui.dp(a, if (compact) 1 else 2))
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, labelDp), -2))
        addView(Ui.text(a, 12f, 800, R.color.ag_text).apply { text = value }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /**
     * One stat row: label, value (+ ▲/▼ [dir] in green/red), coloured bar of [frac], and the small grey [base] at the end
     * (null = none, the Pokédex).
     */
    fun statRow(a: Context, stat: Stat, value: Int, frac: Float, dir: Int, base: Int?, compact: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val v = Ui.dp(a, if (compact) 2 else 3)
        setPadding(0, v, 0, v)
        addView(tapInfo(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = DetailMath.LABELS[stat.ordinal]; gravity = Gravity.CENTER_VERTICAL }, StatInfo.of(stat)),
            LinearLayout.LayoutParams(Ui.dp(a, 32), -2))
        addView(Ui.text(a, 13f, 900, R.color.ag_text).apply { text = value.toString(); gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(a, 34), -2))
        addView(Ui.text(a, 11f, 900, if (dir > 0) R.color.ag_green else R.color.ag_red).apply {
            text = DetailMath.arrow(dir); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(Ui.dp(a, 16), -2))
        val bar = BarView(a)
        bar.set(frac, Ui.c(a, DetailMath.statColor(stat)), false)
        addView(bar, LinearLayout.LayoutParams(0, Ui.dp(a, 7), 1f).also { it.marginEnd = Ui.dp(a, 8) })
        if (base != null) addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply { text = base.toString(); gravity = Gravity.END },
            LinearLayout.LayoutParams(Ui.dp(a, 28), -2))
    }

    /** "Total   NNN" row under the stat bars; [base] is the grey base total at the end. */
    fun totalRow(a: Context, total: Int, base: Int?): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(a, 4), 0, 0)
        addView(tapInfo(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Total"; gravity = Gravity.CENTER_VERTICAL }, StatInfo.TOTAL),
            LinearLayout.LayoutParams(Ui.dp(a, 66), -2))
        addView(Ui.text(a, 13f, 900, R.color.ag_text).apply { text = total.toString() }, LinearLayout.LayoutParams(0, -2, 1f))
        if (base != null) addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply { text = base.toString(); gravity = Gravity.END },
            LinearLayout.LayoutParams(Ui.dp(a, 28), -2))
    }

    /** Move line for small windows: type chip, then the name over the small power / accuracy line (nothing gets cut). */
    fun compactMoveRow(a: Context, data: com.husarp.ankigo.game.GameData, id: String): View {
        val m = data.move(id) ?: return Ui.text(a, 13f, 800, R.color.ag_text).apply { text = id }
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3))
            addView(Ui.typeChip(a, m.type), LinearLayout.LayoutParams(Ui.dp(a, 62), -2))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(a, 14f, 800, R.color.ag_text).apply { text = m.name; maxLines = 1 })
                addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = Bits.moveStats(data, id); maxLines = 1 })
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 8) })
        }
    }

    /** Full-width secondary button (48 dp). */
    fun secondaryButton(a: Context, label: String, onClick: () -> Unit): TextView = Ui.text(a, 14f, 900, R.color.ag_text).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = Ui.dp(a, 48)
        background = Ui.rounded(Ui.c(a, R.color.ag_s0), 12, a)
        setOnClickListener { onClick() }
    }
}
