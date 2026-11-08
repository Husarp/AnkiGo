package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Bond
import com.husarp.ankigo.game.Mon

/** Wrap-content throughout: room for the whole filling Love Ball, narrow dialogs and larger text. */
object BondSection {
    private val balls = listOf(R.drawable.bond_0, R.drawable.bond_1, R.drawable.bond_2,
        R.drawable.bond_3, R.drawable.bond_4, R.drawable.bond_5)
    private val names = listOf("Getting acquainted", "A new connection", "Finding trust", "Growing close", "Trusted partners", "Inseparable")

    fun summary(mon: Mon): String = "Level ${Bond.level(mon.bondPoints)}"

    fun body(a: Context, mon: Mon): View = LinearLayout(a).apply {
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
        addView(ItemCard.factBlock(a, "Current perks", ""), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 12) })
        if (level == 0) addView(line("No perks yet"))
        else {
            addView(line("+${Bond.coins(level)} coins per defeated Pokémon"))
            addView(line("+${Bond.xpPercent(level)}% XP from wins and catches"), Form.lp(a, 6))
            if (level >= 3) addView(line("All species matchups revealed"), Form.lp(a, 6))
            val tough = Bond.toughChance(points)
            if (tough > 0) addView(line("$tough% chance to survive a KO with 1 HP"), Form.lp(a, 6))
        }
    }
}
