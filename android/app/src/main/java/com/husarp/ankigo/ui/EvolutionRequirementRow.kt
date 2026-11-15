package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** PLAN 640: the same marks, wording and trailing item in every evolution requirement list. */
internal class EvolutionRequirementRow(a: Context, requirement: EvolutionRowText.Requirement) : LinearLayout(a) {
    init {
        gravity = Gravity.TOP
        setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5))
        val ink = if (!requirement.met) Ui.c(a, R.color.ag_sub1) else if (Multicolor.enabled(a))
            Multicolor.ink(a, Multicolor.Tone.GREEN, Ui.c(a, R.color.ag_card)) else Ui.c(a, R.color.ag_green)
        addView(Ui.text(a, 13.5f, 800, R.color.ag_sub1).apply {
            text = if (requirement.met) "✓" else "✗"
            setTextColor(ink)
            contentDescription = if (requirement.met) "Met" else "Not met"
        }, LayoutParams(Ui.dp(a, 28), -2))
        addView(Ui.text(a, 13.5f, 800, R.color.ag_sub1).apply {
            text = requirement.label
            setTextColor(ink)
        }, LayoutParams(0, -2, 1f))
        requirement.item?.let { item ->
            addView(ImageView(a).apply {
                Ui.itemIcon(a, item, this)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)).apply { marginStart = Ui.dp(a, 8) })
        }
    }
}
