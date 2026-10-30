package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22RBagTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun bagHasChancesMissRuleAndDirectThrow() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        GameRepo.state!!.bag["poke-ball"] = 2
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        try {
            var called = false
            BattleBag.show(ctx) { called = true }
            paparazzi.snapshot(dialog!!.content(), "r22r-battle-bag")
            val views = all(dialog!!.content())
            assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().contains("20% to catch") })
            assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().contains("runs away") })
            views.first { it.contentDescription?.toString()?.startsWith("Poké Ball,") == true }.performClick()
            assertTrue(called)
            assertEquals(1, GameRepo.state!!.count("poke-ball"))
        } finally { Pickers.interceptApp = null }
    }
    @Test fun teamFirstAndMoreOpensSearchAndFilters() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        try {
            BattleBag.pick(ctx, GameRepo.data!!, GameRepo.state!!, "super-potion") {}
            paparazzi.snapshot(dialog!!.content(), "r22r-team-picker")
            val first = all(dialog!!.content())
            assertFalse(first.any { it is android.widget.EditText })
            first.filterIsInstance<TextView>().first { it.text.toString() == "More Pokémon" }.performClick()
            val more = all(dialog!!.content())
            assertTrue(more.any { it is android.widget.EditText })
            assertTrue(more.filterIsInstance<TextView>().any { it.text.toString() == "Highest level" })
            assertTrue(more.filterIsInstance<TextView>().any { it.text.toString() == "Favourites" })
        } finally { Pickers.interceptApp = null }
    }
    @Test fun noWildDisablesBallsAndFullHpCannotBePicked() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val state = GameRepo.state!!
        state.battle = null
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        try {
            BattleBag.show(ctx) { fail("Disabled ball acted") }
            val ball = all(dialog!!.content()).first { it.contentDescription?.toString()?.startsWith("Poké Ball,") == true }
            assertFalse(ball.isEnabled)
            assertTrue(ball.contentDescription.toString().contains("No wild Pokémon"))
            BattleBag.pick(ctx, GameRepo.data!!, state, "super-potion") { fail("Full HP Pokémon picked") }
            val full = all(dialog!!.content()).filterIsInstance<TextView>().first { it.text.toString() == "Full HP" }
            assertFalse((full.parent.parent.parent as View).isClickable)
        } finally { Pickers.interceptApp = null }
    }

}
