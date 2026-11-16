package com.husarp.ankigo.shots

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Trainer
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R49YShots {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun trainerHeaderStages() = paparazzi.shootPage("y-trainer", listOf(Variant("phone", 412, 920), Variant("narrow", 320, 1040)), withSprites = false) { ctx ->
        val state = GameRepo.state!!
        val data = GameRepo.data!!
        val icons = listOf(R.drawable.trainer_rank_1, R.drawable.trainer_rank_2, R.drawable.trainer_rank_3,
            R.drawable.trainer_rank_4, R.drawable.trainer_rank_5)
        val page = StatsPage(ctx, NoHost)
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            for ((i, level) in listOf(1, 20, 40, 60, 100).withIndex()) {
                state.trainerLevel = level
                state.trainerXp = Trainer.xpForNext(level) / 3
                state.caught.clear(); repeat(listOf(0, 250, 350, 600, 900)[i]) { state.caught += it + 1 }
                state.badges.clear(); state.badges.addAll(1..8)
                state.mons.clear()
                repeat(listOf(0, 1, 2, 10, 50)[i].coerceAtLeast(1)) { n ->
                    state.mons += TestData.mon(data, 25, listOf(5, 40, 60, 80, 99)[i], uid = "rank-$n").also { it.shiny = i > 0 }
                }
                assertEquals(i + 1, Trainer.rankStage(state))
                val card = call(page, "trainerCard") as View
                fun images(v: View): List<ImageView> = (if (v is ImageView) listOf(v) else emptyList()) +
                    if (v is ViewGroup) (0 until v.childCount).flatMap { images(v.getChildAt(it)) } else emptyList()
                val icon = images(card).first().drawable as BitmapDrawable
                assertFalse(icon.isFilterBitmap)
                val expected = android.graphics.BitmapFactory.decodeResource(ctx.resources, icons[i],
                    android.graphics.BitmapFactory.Options().apply { inScaled = false })
                assertTrue(icon.bitmap.sameAs(expected))
                assertFalse(icon.paint.isAntiAlias)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, images(card).first().importantForAccessibility)
                addView(card, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 12) })
            }
        }
    }
}
