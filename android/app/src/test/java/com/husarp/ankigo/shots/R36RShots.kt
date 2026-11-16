package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R36RShots {
    @get:Rule val paparazzi = Shots.rule()
    private val pages = mutableListOf<GamePage>()
    @After fun clean() { pages.forEach { it.onGone() }; ItemWindow.interceptShow = null; BoxMonPicker.interceptShow = null; Pickers.interceptApp = null; BattleShotKit.reset() }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun setup(theme: Theme, width: Int = 412, scale: Float = 1f): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p$width", width, 892).device(theme).copy(fontScale = scale))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.withSprites = true
        FakeGame.install(ctx); ShopCardKit.install()
        // Optional local PokeAPI item cache for the unattended evidence run; portable fixtures still work without it.
        File("/tmp/r36r-item-icons").takeIf { it.isDirectory }?.listFiles()?.forEach { source ->
            val target = File(Sprites.dir(ctx), "item/${source.name}"); target.parentFile!!.mkdirs(); source.copyTo(target, overwrite = true)
        }
        val worker = Thread { for (id in BattleItems.snacks.keys + BattleItems.held.keys + listOf("awakening", "oran-berry")) Sprites.itemBitmap(ctx, id) }
        worker.start(); worker.join()
        return ctx
    }
    private fun bag(ctx: Context, item: String, count: Int = 6): Pair<ShopPage, ShopCardKit.Stage> =
        ShopCardKit.page(ctx, CardKeys.bagItem(item), bag = true, setup = {
            GameRepo.state!!.bag.clear(); GameRepo.state!!.bag[item] = count
        }).also { pages += it.first }

    @Test fun newItemWindowsAndFeedingInLightAndDark() {
        for (theme in Theme.entries) for (item in listOf("rage-candy-bar", "old-gateau", "rocky-helmet", "focus-sash", "awakening", "oran-berry")) {
            val ctx = setup(theme); val (_, stage) = bag(ctx, item)
            assertTrue(ShopCardKit.texts(stage).contains(Items.name(item)))
            if (item == "oran-berry") assertTrue("Feed berry…" in ShopCardKit.texts(stage))
            if (item == "old-gateau") assertTrue(ShopCardKit.texts(stage).any { it.contains("without using the turn") })
            paparazzi.snapshot(stage, "r-item-$item-${theme.name.lowercase()}", 300)
        }
    }
    @Test fun sellQuantityFlowUpdatesInventoryAndCoinsOnlyAfterHold() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val (page, stage) = bag(ctx, "potion")
            val state = GameRepo.state!!; val coins = state.cash
            val sell = all(stage).first { it.contentDescription?.toString()?.startsWith("Sell 1 Potion for 10 coins") == true }
            sell.performClick(); assertEquals(6, state.count("potion"))
            paparazzi.snapshot(stage, "r-sell-one-${theme.name.lowercase()}", 300)
            all(stage).first { it.contentDescription == "Sell quantity 1. Cycle 1, 3, 5" }.performClick(); stage.render()
            assertEquals(3, page.open.quantity)
            paparazzi.snapshot(stage, "r-sell-three-${theme.name.lowercase()}", 300)
            all(stage).first { it.contentDescription?.toString()?.startsWith("Sell 3 Potion for 30 coins") == true }.performLongClick(); stage.render()
            assertEquals(3, state.count("potion")); assertEquals(coins + 30, state.cash)
            assertNotNull(page.itemWindow)
            paparazzi.snapshot(stage, "r-sell-done-${theme.name.lowercase()}", 300)
            val finalSale = all(stage).first { it.contentDescription?.toString()?.startsWith("Sell 3 Potion for 30 coins") == true }
            finalSale.performLongClick(); stage.render()
            assertEquals(0, state.count("potion")); assertEquals(coins + 60, state.cash)
            assertNotNull(page.itemWindow)
            finalSale.performLongClick(); assertEquals(coins + 60, state.cash)
            paparazzi.snapshot(stage, "r-sell-empty-${theme.name.lowercase()}", 300)
        }
    }
    @Test fun smallWindowAtLargeFontRetainsSellAndQuantityControls() {
        for (theme in Theme.entries) {
            val ctx = setup(theme, width = 360, scale = 1.5f); val (_, stage) = bag(ctx, "potion")
            paparazzi.snapshot(stage, "r-sell-large-font-${theme.name.lowercase()}", 300)
            val sell = all(stage).first { it.contentDescription?.toString()?.startsWith("Sell 1 Potion for 10 coins") == true }
            assertTrue(sell.width >= Ui.dp(ctx, 48)); assertTrue(sell.height >= Ui.dp(ctx, 48))
        }
    }
    @Test fun feedingFromBagAndDetailsUsesSavingActionAndShowsOnlyHungerMessageOnThirdTry() {
        val ctx = setup(Theme.LIGHT); val (page, stage) = bag(ctx, "oran-berry")
        val mon = page.state.main!!; val before = mon.bondPoints
        var onPick: ((Mon) -> Unit)? = null
        BoxMonPicker.interceptShow = { dialog, recipient ->
            pages += recipient
            val request = getField(recipient, "recipient") as TeamPage.Recipient
            onPick = request.picked
            dialog.setOnDismissListener { recipient.onHide(); recipient.onGone() }
        }
        ShopCardKit.tap(ShopCardKit.findText(stage) { it == "Feed berry…" })
        assertNotNull(onPick)
        onPick!!(mon)
        assertEquals(before + 5, mon.bondPoints)
        assertEquals(5, page.state.count("oran-berry"))
        // The details control opens the same berry selection and saves through Game.feedBerry.
        var picker: AppDialog? = null
        Pickers.interceptApp = { picker = it }
        BagItemActions.feedButton(page, mon).performClick()
        assertNotNull(picker)
        picker!!.dismiss(); Pickers.interceptApp = null
        page.act { it.feedBerry("oran-berry", mon.uid) }
        val result = page.act { it.feedBerry("oran-berry", mon.uid) }
        assertFalse(result.ok)
        assertEquals("${mon.shownName} isn't hungry right now.", result.message)
        assertEquals(4, page.state.count("oran-berry"))
    }
    @Test fun heldItemDetailsOutsideTheBagKeepTransferActionsWithoutRequiringShopQuantityState() {
        val ctx = setup(Theme.LIGHT); val page = TeamPage(ctx, NoHost); pages += page
        page.state.bag["leftovers"] = 1
        val footer = ItemCard.footer(page, ItemCard.Spec("leftovers"))
        val labels = all(footer).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Unequip Leftovers" in labels); assertTrue("Give to another Pokémon" in labels)
        assertFalse(labels.any { it.startsWith("Sell") })
    }
    @Test fun snackConditionsHaveSmallIconsAndExplanations() {
        for (key in BattleItems.boosts.values) {
            assertNotNull(ConditionIcons.of(key))
            val b = ConditionBoard(); b.start(ConditionMarks.Slot.OURS, key, 1)
            assertTrue(ConditionExplanation.effect(b.all().single()).isNotBlank())
        }
    }
}
