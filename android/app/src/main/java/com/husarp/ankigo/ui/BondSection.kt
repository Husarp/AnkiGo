package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Bond
import com.husarp.ankigo.game.Mon

/** Current and next rewards share a text size so even narrow windows never wrap a line. */
object BondSection {
    private val balls = listOf(R.drawable.bond_0, R.drawable.bond_1, R.drawable.bond_2,
        R.drawable.bond_3, R.drawable.bond_4, R.drawable.bond_5)
    private val names = listOf("Getting acquainted", "A new connection", "Finding trust", "Growing close", "Trusted partners", "Inseparable")

    fun summary(mon: Mon): String = "Level ${Bond.level(mon.bondPoints)}"

    private class FittedBody(a: Context) : LinearLayout(a) {
        private fun texts(v: View): List<TextView> = if (v is TextView) listOf(v)
            else if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val labels = texts(this)
            labels.forEach { it.setSingleLine(true); it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
            // Measure again after each shared reduction: weighted columns and wrap-content ball labels both change width.
            repeat(12) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                val ratio = labels.minOfOrNull { label ->
                    val room = (label.measuredWidth - label.compoundPaddingLeft - label.compoundPaddingRight).coerceAtLeast(0)
                    val needed = android.text.Layout.getDesiredWidth(label.text, label.paint)
                    if (needed <= room || needed == 0f) 1f else room / needed
                } ?: 1f
                if (ratio >= 1f) return
                val size = labels.first().textSize * ratio * 0.99f
                labels.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.coerceAtLeast(0.1f)) }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun body(a: Context, mon: Mon): View = FittedBody(a).apply {
        orientation = LinearLayout.VERTICAL
        val points = mon.bondPoints.coerceIn(0, Bond.MAX_POINTS)
        val level = Bond.level(points)
        val next = Bond.thresholds.getOrNull(level + 1)
        fun line(value: String, sub: Boolean = false) = Ui.text(a, if (sub) 12f else 13f, if (sub) 700 else 800,
            if (sub) R.color.ag_sub0 else R.color.ag_text).apply { text = value; setLineSpacing(0f, 1.1f) }
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            DetailParts.tapRow(this, StatInfo.BOND)
            clipChildren = false
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(a).apply {
                    setImageResource(balls[level])
                    (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)))
                addView(Ui.text(a, 14f, 900, R.color.ag_bond_level).apply { text = "Level $level"; gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 4) })
            }, LinearLayout.LayoutParams(-2, -2))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(a, 18f, 900, R.color.ag_text).apply { text = names[level] })
                addView(line(if (next == null) "Max Bond" else "$points total points", true), Form.lp(a, 5))
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 14) })
        }, LinearLayout.LayoutParams(-1, -2))
        val start = Bond.thresholds[level]
        val progress = if (next == null) "Max Bond · $points points" else "${points - start} / ${next - start} points to Level ${level + 1}"
        addView(line(progress), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 8) })
        addView(BarView(a).apply {
            set(if (next == null) 1f else (points - start).toFloat() / (next - start), Ui.c(a, R.color.ag_pink), false)
            contentDescription = progress
        }, LinearLayout.LayoutParams(-1, Ui.dp(a, 7)).also { it.topMargin = Ui.dp(a, 6) })
        fun perks(l: Int, title: String): View = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(ItemCard.factBlock(a, title, "").apply {
                if (l != level) ((this as LinearLayout).getChildAt(0) as TextView).apply {
                    val icon = a.getDrawable(R.drawable.ic_evolve)!!.mutate()
                    icon.setTint(Ui.c(a, R.color.ag_mauve))
                    icon.setBounds(0, 0, Ui.dp(a, 16), Ui.dp(a, 16))
                    setCompoundDrawablesRelative(icon, null, null, null)
                }
            }, LinearLayout.LayoutParams(-1, -2))
            fun perk(icon: Int, value: String, sub: String? = null, tint: Int? = null) {
                addView(LinearLayout(a).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(a).apply {
                        setImageResource(icon)
                        if (tint != null) setColorFilter(Ui.c(a, tint))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)))
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(line(value), LinearLayout.LayoutParams(-1, -2))
                        if (sub != null) addView(line(sub, true), LinearLayout.LayoutParams(-1, -2))
                    }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 8) })
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 10) })
            }
            if (l == 0) addView(line("No perks yet"), Form.lp(a, 10))
            else {
                perk(R.drawable.coin_stack, "+${Bond.coins(l)} coins", "per defeated Pokémon")
                perk(R.drawable.ic_bolt, "+${Bond.xpPercent(l)}% XP", "wins and catches", R.color.ag_sky)
            }
            if (l >= 3) perk(R.drawable.ic_compare, "All species matchups revealed", tint = R.color.ag_sky)
            val tough = Bond.toughChance(Bond.thresholds[l])
            if (tough > 0) perk(R.drawable.ic_ach_shield, "$tough% chance to survive a KO with 1 HP", tint = R.color.ag_green)
            if (l in 2..4) perk(R.drawable.ic_evolve, when (l) {
                2 -> "Normal evolutions"
                3 -> "First Bond evolutions"
                else -> "Higher Bond evolutions"
            }, tint = R.color.ag_green)
        }
        addView(LinearLayout(a).apply {
            addView(perks(level, "Current · $level"), LinearLayout.LayoutParams(0, -2, 1f))
            if (next != null) addView(perks(level + 1, "Next · ${level + 1}"),
                LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 18) })
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 28) })
    }
}
