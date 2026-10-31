package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R23TTeachWarningTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun replacementShowsAppWarningBeforeChangingMovesAndCancelKeepsThem() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val world = GameRepo.data!!
        val extra = listOf(
            Move("defensecurl", "Defense Curl", Type.NORMAL, Category.STATUS, 0, null),
            Move("rollout", "Rollout", Type.ROCK, Category.PHYSICAL, 30, 90))
        val data = GameData(world.species, world.moves + extra.associateBy { it.id }, world.growth, world.itemPrices)
        GameRepo::class.java.getDeclaredField("loadedData").apply { isAccessible = true }.set(GameRepo, data)
        val mon = GameRepo.state!!.mons.first()
        mon.moves.clear(); mon.moves += listOf("defensecurl", "rollout", "tackle", "growl")
        val before = mon.moves.toList()
        for ((index, affected) in listOf(0 to "Rollout", 1 to "Defense Curl")) {
            val picker = DialogShots.capture { MonActions(TeamPage(ctx, NoHost)).teach(mon, "bodyslam") }!!
            val list = views(picker.content()).filterIsInstance<ListView>().single()
            val warning = DialogShots.capture { list.performItemClick(list.adapter.getView(index, null, list), index, index.toLong()) }!!
            val text = views(warning.content()).filterIsInstance<TextView>().joinToString(" ") { it.text }
            assertTrue(text.contains("$affected becomes useless"))
            assertTrue(text.contains("Teach anyway?"))
            assertEquals(before, mon.moves)
            views(warning.content()).filterIsInstance<TextView>().single { it.text.toString() == "Cancel" }.performClick()
            assertEquals(before, mon.moves)
        }
    }
}
