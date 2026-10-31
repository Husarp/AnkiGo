package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.CompareMovePopup
import com.husarp.ankigo.ui.DetailSections
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R23CUiMoveTextTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val curl = Move("defensecurl", "Defense Curl", Type.NORMAL, Category.STATUS, 0, null,
        target = "self", boosts = mapOf("def" to 1))
    private val rollout = Move("rollout", "Rollout", Type.ROCK, Category.PHYSICAL, 30, 90)
    private val world = TestData.world()
    private val data = GameData(world.species, world.moves + listOf(curl, rollout).associateBy { it.id }, world.growth, world.itemPrices)
    private fun text(v: View): String = when (v) {
        is TextView -> v.text.toString()
        is ViewGroup -> (0 until v.childCount).joinToString(" ") { text(v.getChildAt(it)) }
        else -> ""
    }

    @Test fun detailUsesKnownMovesWhileDexKeepsGenericText() {
        val ctx = Shots.files(paparazzi.context)
        assertFalse(text(DetailSections.moves(ctx, data, listOf(curl.id), false)).contains("Rollout and"))
        assertTrue(text(DetailSections.moves(ctx, data, listOf(curl.id, rollout.id), false)).contains("Rollout deals"))
        assertFalse(text(DetailSections.moves(ctx, data, listOf(curl.id, rollout.id), false)).contains("Ice Ball"))
        assertTrue(text(DetailSections.moves(ctx, data, listOf(curl.id), false, levels = listOf("Lv 1"))).contains("Rollout and"))
    }

    @Test fun comparePopupUsesItsSuppliedMoveset() {
        val ctx = Shots.files(paparazzi.context)
        assertFalse(text(CompareMovePopup.content(ctx, curl, listOf(curl))).contains("Rollout"))
        assertTrue(text(CompareMovePopup.content(ctx, curl, listOf(curl, rollout))).contains("Rollout"))
    }
}
