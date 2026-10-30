package com.husarp.ankigo.shots

import android.view.Gravity
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AnswerRow
import com.husarp.ankigo.ui.PickKit
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The real answer buttons and shared multi-selection marks, before and after R20. */
class R20VisualShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun clean() = BattleShotKit.reset()

    @Test fun paletteAndSelection() {
        paparazzi.shootPage("r20-palette-selection", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                addView(Ui.text(ctx, 20f, 900, R.color.ag_text).apply { text = "Study" })
                AnswerRow.fill(ctx, this, 4, listOf("1m", "6m", "10m", "4d"), false, false, true, 0, { _, _ -> }, {})
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "Pick Pokémon to compare" }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 24) })
                listOf(true, false).forEach { selected ->
                    addView(LinearLayout(ctx).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = PickKit.cardBg(ctx, 12, selected)
                        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16))
                        addView(Ui.text(ctx, 16f, 800, R.color.ag_text).apply { text = if (selected) "Sparky · selected" else "Birdie" }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(PickKit.badge(ctx).also { PickKit.bindBadge(it, true, selected) }, LinearLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28)))
                    }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 12) })
                }
            }
        }
    }
}
