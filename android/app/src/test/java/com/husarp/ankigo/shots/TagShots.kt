package com.husarp.ankigo.shots

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.MonTag
import com.husarp.ankigo.ui.MonTags
import com.husarp.ankigo.ui.TagKind
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/* U-03: every Pokémon tag pill, alone and as a capped row. */
class TagShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun tagStrip() {
        val all = listOf(
            MonTag(TagKind.MAIN, "Main"), MonTag(TagKind.FAINTED, "Fainted"), MonTag(TagKind.CAN_EVOLVE, "Can evolve"),
            MonTag(TagKind.NEW, "New"), MonTag(TagKind.EXP_SHARE, "Exp. Share"),
            MonTag(TagKind.HELD, "Holds Lucky Egg", "lucky-egg"), MonTag(TagKind.SHINY, "★ Shiny"), MonTag(TagKind.REGIONAL, "Alolan"),
        )
        paparazzi.shoot("tags", sizes = listOf(Size.PHONE)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), 0)
                all.forEach { addView(MonTags.pill(ctx, it), LinearLayout.LayoutParams(-2, -2).also { p -> p.bottomMargin = Ui.dp(ctx, 8) }) }
                addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "row, max 3" })
                addView(MonTags.row(ctx, all, 3), LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
                addView(MonTags.row(ctx, all.subList(4, 8), 3))
            }
        }
    }
}
