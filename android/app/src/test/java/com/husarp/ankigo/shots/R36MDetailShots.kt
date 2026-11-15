package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R36MDetailShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() = BattleShotKit.reset()

    private fun pills(v: View): List<StatPillView> = (if (v is StatPillView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { pills(v.getChildAt(it)) } else emptyList()

    @Test fun detailsUseBattleChipShapesAndChevrons() = paparazzi.shootWith("m-644-details", listOf(Size.PHONE)) { c ->
        FakeGame.install(c)
        val mon = GameRepo.state!!.main!!
        val sp = GameRepo.data!!.sp(mon.species)
        val boosts = intArrayOf(1, -2, 3, -4, 5, -6, 1)
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), Ui.dp(c, 24), Ui.dp(c, 14), 0)
            setBackgroundColor(Ui.c(c, R.color.ag_base))
            addView(DetailSections.section(c, DetailSections.memory(c), DetailSection.STATS, "Total", titleLevel = mon.level, boosts = boosts, forceOpen = true) {
                DetailSections.statsBody(c, StatCalc.stats(sp, mon), mon.level, mon.nature, sp, false, boosts)
            })
            val views = pills(this)
            assertEquals(7, views.size)
            val style = StatPillView::class.java.getDeclaredField("battleStyle").apply { isAccessible = true }
            assertTrue(views.all { style.getBoolean(it) })
            assertEquals(boosts.toList(), views.map { it.stages })
        }
    }
}
