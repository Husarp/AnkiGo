package com.husarp.ankigo.ui

import android.widget.TextView
import com.husarp.ankigo.R

/** Compact navigation icons; value choices keep their existing presentation. */
internal object SubtabIcons {
    fun apply(view: TextView, icon: Int, above: Boolean = false) {
        val ctx = view.context
        val drawable = ctx.getDrawable(icon)!!.mutate()
        drawable.setTint(if (view.currentTextColor == Ui.c(ctx, R.color.ag_base) || view.currentTextColor == Ui.c(ctx, R.color.ag_crust)) view.currentTextColor else Ui.c(ctx, R.color.ag_mauve))
        val size = Ui.dp(ctx, 16)
        drawable.setBounds(0, 0, size, size)
        view.compoundDrawablePadding = Ui.dp(ctx, 6)
        if (above) view.setCompoundDrawablesRelative(null, drawable, null, null)
        else {
            view.setCompoundDrawablesRelative(drawable, null, null, null)
            if (view.getTag(R.id.ag_tag_subtab_padding) == null) {
                val original = intArrayOf(-1, -1)
                view.setTag(R.id.ag_tag_subtab_padding, original)
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (original[0] < 0) { original[0] = view.paddingStart; original[1] = view.paddingEnd }
                    val textWidth = view.paint.measureText(view.text.toString())
                    val content = textWidth + size + view.compoundDrawablePadding
                    val inset = ((view.width - content) / 2f).toInt().coerceAtLeast(0)
                    val start = maxOf(original[0], inset)
                    val end = maxOf(original[1], inset)
                    if (view.paddingStart != start || view.paddingEnd != end)
                        view.setPaddingRelative(start, view.paddingTop, end, view.paddingBottom)
                }
            }
        }
    }

    fun navigation(labels: List<String>): List<Int>? = when (labels) {
        listOf("All", "Team", "Box") -> listOf(R.drawable.ic_grid, R.drawable.ic_team, R.drawable.ic_item_bag)
        listOf("Look", "Content") -> listOf(R.drawable.ic_palette, R.drawable.ic_study)
        listOf("Attack", "Defend") -> listOf(R.drawable.ic_swords, R.drawable.ic_ach_shield)
        else -> null
    }

    fun achievement(label: String): Int = when (label) {
        "Study" -> R.drawable.ic_study
        "Battle" -> R.drawable.ic_swords
        "Collection" -> R.drawable.ic_dex
        "Item collections" -> R.drawable.ic_item_bag
        "Economy" -> R.drawable.ic_ach_money
        "Special" -> R.drawable.ic_ach_sparkle
        "Badges" -> R.drawable.ic_ach_trophy
        "Secret" -> R.drawable.ic_ach_lock
        else -> R.drawable.ic_grid
    }
}
