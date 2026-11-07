package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22IShopTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun setup() = Shots.files(paparazzi.context).also {
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.withSprites = true
        FakeGame.install(it)
        ShopCardKit.install()
    }
    private fun tap(view: View, label: String) = ShopCardKit.tap(ShopCardKit.findText(view) { it == label })

    @Test fun buyRequiresHoldAndUsesCurrentQuantity() {
        val ctx = setup()
        val (page, stage) = ShopCardKit.page(ctx, CardKeys.basic(ShopPlan.Tile.Item("potion")))
        val state = GameRepo.state!!
        val cash = state.cash
        val count = state.count("potion")
        tap(stage, "Hold to buy"); stage.render()
        assertEquals(cash, state.cash)
        assertTrue(ShopCardKit.texts(stage).contains("Hold to buy"))
        paparazzi.snapshot(stage, "r22i-confirm")
        tap(stage, "×1"); stage.render()
        assertFalse(page.open.buying)
        tap(stage, "Hold to buy"); stage.render()
        (ShopCardKit.findText(stage) { it == "Hold to buy" }!!.parent as View).performLongClick(); stage.render()
        assertEquals(count + 3, state.count("potion"))
        assertEquals(cash - 3 * com.husarp.ankigo.game.Shop.priceOf(GameRepo.data!!, state, "potion"), state.cash)
        assertFalse(page.open.buying)
    }
    @Test fun heldItemWithNoFreeCopiesCanBeTakenOffOrTransferred() {
        val ctx = setup()
        val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem("amulet-coin"), bag = true, setup = {
            val state = GameRepo.state!!
            state.bag.clear()
            state.mons.first().heldItem = "amulet-coin"
        })
        val text = ShopCardKit.texts(stage)
        assertTrue(text.toString(), text.contains("Held by"))
        assertTrue(text.contains("Take off"))
        assertTrue(text.contains("Give to another Pokémon"))
        assertTrue(text.contains("All"))
        assertTrue(text.contains("Held"))
        paparazzi.snapshot(stage, "r22i-held")
        GameRepo.state!!.mons.forEach { it.heldItem = "amulet-coin" }
        stage.render()
        assertTrue(ShopCardKit.texts(stage).contains("Take off"))
        assertTrue(ShopCardKit.texts(stage).contains("Give to another Pokémon"))
        paparazzi.snapshot(stage, "r22i-held-many")
        page.closeCard(); stage.render()
        tap(stage, "Held"); stage.render()
        assertTrue(ShopCardKit.texts(stage).any { it == "Amulet Coin" })
    }
    @Test fun evolutionShowsSeenSourcesAndIndividualUnknownSilhouettes() {
        val ctx = setup()
        val (_, stage) = ShopCardKit.page(ctx, CardKeys.bagItem("thunder-stone"), bag = true, setup = {
            val state = GameRepo.state!!
            state.bag["thunder-stone"] = 1
            state.caught.clear(); state.caught += 25
            state.seen.clear(); state.seen += 25
        })
        val text = ShopCardKit.texts(stage)
        assertTrue(text.toString(), text.any { it == "???" })
        assertFalse(ShopCardKit.findDesc(stage, "???")!!.isClickable)
        assertFalse(text.toString(), text.any { it.startsWith("…and") })
        assertTrue(ShopCardKit.findDesc(stage, "${GameRepo.data!!.sp(25).name}. Opens Evolution details")!!.isClickable)
        paparazzi.snapshot(stage, "r22i-evolution")
        GameRepo.state!!.caught += listOf(26, 135)
        GameRepo.state!!.seen += 133 // Seen-only sources are tappable too.
        stage.render()
        val allKnown = ShopCardKit.texts(stage)
        assertFalse(allKnown.any { it.startsWith("…and") || it == "???" })
        assertFalse(allKnown.any { it == "⟶" })
        for (num in listOf(25, 133)) assertTrue(ShopCardKit.findDesc(stage, "${GameRepo.data!!.sp(num).name}. Opens Evolution details")!!.isClickable)
        for (num in listOf(26, 135)) assertNull(ShopCardKit.findDesc(stage, "${GameRepo.data!!.sp(num).name}. Opens Evolution details"))
        paparazzi.snapshot(stage, "r22i-evolution-known")
    }

    @Test fun tmConfirmsOnceAndHasNoQuantityControl() {
        val ctx = setup()
        val (_, stage) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt")), setup = { GameRepo.state!!.tms.clear() })
        assertFalse(ShopCardKit.texts(stage).contains("×1"))
        tap(stage, "Hold to buy"); stage.render()
        assertFalse("thunderbolt" in GameRepo.state!!.tms)
        (ShopCardKit.findText(stage) { it == "Hold to buy" }!!.parent as View).performLongClick(); stage.render()
        assertTrue("thunderbolt" in GameRepo.state!!.tms)
        assertTrue(ShopCardKit.texts(stage).contains("✓ Owned"))
    }
    @Test fun insufficientMoneyDoesNotArmConfirmation() {
        val ctx = setup()
        val price = com.husarp.ankigo.game.Shop.priceOf(GameRepo.data!!, ShopCardKit.WEEKDAY, "potion")
        val (page, stage) = ShopCardKit.page(ctx, CardKeys.basic(ShopPlan.Tile.Item("potion")), cash = price - 1)
        val count = GameRepo.state!!.count("potion")
        tap(stage, "Hold to buy"); stage.render()
        assertFalse(page.open.buying)
        assertEquals(price - 1, GameRepo.state!!.cash)
        assertEquals(count, GameRepo.state!!.count("potion"))
    }

}
