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
    @Test fun bagHasChancesMissRuleAndCombinedThrow() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        GameRepo.state!!.bag["poke-ball"] = 2
        GameRepo.state!!.bag["heal-ball"] = 1
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        try {
            var called = false
            val page = ShopPage(ctx, NoHost, bagOnly = true, onBattleEvents = { called = true })
            val stage = ShopCardKit.Stage(ctx, page, page.root).render()
            paparazzi.snapshot(stage, "r22r-battle-bag")
            all(stage).first { it.contentDescription?.toString()?.startsWith("Poké Ball,") == true }.performClick()
            stage.render()
            val item = page.itemWindow!!.view
            val texts = all(item).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.any { "20%" in it.replace(" ", "") })
            assertTrue(texts.any { "runs away" in it })
            (all(item).filterIsInstance<TextView>().first { it.text.toString() == "Throw" }.parent as View).performClick()
            assertFalse("Picking a regular ball must wait for the combined throw", called)
            assertEquals(2, GameRepo.state!!.count("poke-ball"))
            all(dialog!!.content()).filterIsInstance<TextView>().first { it.text.toString().startsWith("Heal Ball ×") }.performClick()
            dialog!!.button(android.content.DialogInterface.BUTTON_POSITIVE)!!.performClick()
            assertFalse("Throw selection must wait for the hold", called)
            dialog!!.button(android.content.DialogInterface.BUTTON_POSITIVE)!!.performClick()
            assertFalse("A tap must not throw", called)
            dialog!!.button(android.content.DialogInterface.BUTTON_POSITIVE)!!.performLongClick()
            assertTrue(called)
            assertEquals(0, GameRepo.state!!.count("heal-ball"))
            assertEquals(1, GameRepo.state!!.count("poke-ball"))
        } finally { Pickers.interceptApp = null; ItemWindow.interceptShow = null }
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
        } finally { Pickers.interceptApp = null; ItemWindow.interceptShow = null }
    }
    @Test fun noWildDisablesBallsAndFullHpCannotBePicked() {
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val state = GameRepo.state!!
        state.battle = null
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        try {
            val page = ShopPage(ctx, NoHost, bagOnly = true, onBattleEvents = { fail("Disabled ball acted") })
            val stage = ShopCardKit.Stage(ctx, page, page.root).render()
            all(stage).first { it.contentDescription?.toString()?.startsWith("Poké Ball,") == true }.performClick()
            stage.render()
            val throwButton = all(page.itemWindow!!.view).filterIsInstance<TextView>().first { it.text.toString() == "Throw" }
            val action = throwButton.parent as View
            assertEquals(0.5f, action.alpha, 0f)
            val before = state.count("poke-ball")
            action.performClick()
            assertEquals(before, state.count("poke-ball"))
            assertTrue(all(page.itemWindow!!.view).filterIsInstance<TextView>().any { "Balls are thrown from a fight" in it.text.toString() })
            BattleBag.pick(ctx, GameRepo.data!!, state, "super-potion") { fail("Full HP Pokémon picked") }
            val full = all(dialog!!.content()).filterIsInstance<TextView>().first { it.text.toString() == "Full HP" }
            assertFalse((full.parent.parent.parent as View).isClickable)
        } finally { Pickers.interceptApp = null; ItemWindow.interceptShow = null }
    }

}
