package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30FShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = if (v.visibility == View.GONE) emptyList() else listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
    private val state get() = GameRepo.state!!
    private val data get() = GameRepo.data!!
    private val pages = mutableListOf<GamePage>()
    private var afterShot: (() -> Unit)? = null
    @After fun clean() { pages.forEach { it.onGone() }; Pickers.interceptApp = null; ItemWindow.interceptShow = null; BattleShotKit.reset() }
    private fun scene(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 820).device(theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ShopCardKit.install()
            val v = build(ctx)
            paparazzi.snapshot(v, "f-$name-${theme.name.lowercase()}", 300)
            afterShot?.invoke(); afterShot = null
        }
    }
    private fun prepare() {
        state.mons.take(2).forEachIndexed { i, mon -> mon.heldItem = "soothe-bell"; mon.name = listOf("Pikachu", "Eevee")[i]; mon.nickname = null }
        state.bag.clear(); state.bag["soothe-bell"] = 1
    }
    private fun window(ctx: Context): Pair<ItemWindow, View> {
        val page = ShopPage(ctx, NoHost)
        val w = ItemWindow(page) {}
        w.update(ItemCard.Spec("soothe-bell"))
        return w to FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(w.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
    }
    @Test fun heldItemInfoUsesRealCountsAndBothActionsEverywhere() = scene("485-475-held-item") { ctx ->
        prepare()
        val (w, view) = window(ctx)
        assertTrue(texts(view).any { it.contains("3 Soothe Bells") && it.contains("2 held by Pikachu, Eevee") })
        assertTrue("Take off" in texts(view)); assertTrue("Give to another Pokémon" in texts(view))
        assertFalse("Close all" in texts(view))
        w.stackChanged(true)
        assertFalse("Close all" in texts(view))
        var picker: AppDialog? = null
        Pickers.interceptApp = { picker = it }
        Pickers.pickItem(ctx, "Give item", state, listOf("soothe-bell")) {}
        val pickerList = all(checkNotNull(picker).content()).filterIsInstance<ListView>().single()
        assertTrue("×3 · 1 free" in texts(pickerList.adapter.getView(0, null, pickerList)))
        picker!!.dismiss(); Pickers.interceptApp = null
        state.bag.clear()
        w.update(ItemCard.Spec("soothe-bell"))
        assertTrue(texts(view).any { it.contains("2 Soothe Bells") && it.contains("2 held by Pikachu, Eevee") })
        assertFalse(texts(BondSection.body(ctx, state.mons[0])).any { it.contains("decay", true) })
        view
    }
    @Test fun severalHoldersAskWhichOneBeforeTakingOff() = scene("485-choose-holder") { ctx ->
        prepare()
        val (w, _) = window(ctx)
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        all(w.view).first { it.contentDescription == "Take off" }.performClick()
        val d = checkNotNull(dialog)
        assertTrue(texts(d.content()).any { it.contains("Which Pokémon") })
        val list = all(d.content()).filterIsInstance<ListView>().single()
        val before = state.ownedItemCount("soothe-bell")
        afterShot = {
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            assertNull(state.mons[0].heldItem)
            assertEquals("soothe-bell", state.mons[1].heldItem)
            assertEquals(before, state.ownedItemCount("soothe-bell"))
            assertEquals(2, state.count("soothe-bell"))
        }
        AppDialogShots.scene(ctx, d)
    }
    @Test fun givingSelectsSourceThenReusesTheBoxSortAndFilterSheet() = scene("485-give-picker") { ctx ->
        prepare()
        val (w, _) = window(ctx)
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        all(w.view).first { it.contentDescription == "Give to another Pokémon" }.performClick()
        val source = checkNotNull(dialog)
        val sources = all(source.content()).filterIsInstance<ListView>().single()
        val before = state.ownedItemCount("soothe-bell")
        sources.performItemClick(sources.adapter.getView(1, null, sources), 1, 1)
        val recipients = checkNotNull(dialog)
        assertNotSame(source, recipients)
        assertTrue("Sort & filter" in texts(recipients.content()))
        assertEquals(before, state.ownedItemCount("soothe-bell")) // selecting a source alone never removes it
        val list = all(recipients.content()).filterIsInstance<ListView>().single()
        assertEquals(state.mons.size - 2, list.adapter.count)
        afterShot = {
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            assertNull(state.mons[1].heldItem)
            assertEquals("soothe-bell", state.mons[0].heldItem)
            assertEquals(before, state.ownedItemCount("soothe-bell"))
        }
        AppDialogShots.scene(ctx, recipients)
    }
    @Test fun cancelingThePickerAndPausingThePagePreserveItemsAndTheItemWindow() = scene("485-475-bag") { ctx ->
        prepare()
        val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem("soothe-bell"), bag = true, setup = { prepare() })
        pages += page
        val before = com.husarp.ankigo.game.save.SaveCodec.encode(state)
        val w = checkNotNull(page.itemWindow)
        var dialog: AppDialog? = null
        Pickers.interceptApp = { dialog = it }
        all(w.view).first { it.contentDescription == "Give to another Pokémon" }.performClick()
        checkNotNull(dialog).dismiss()
        assertEquals(before, com.husarp.ankigo.game.save.SaveCodec.encode(state))
        page.onHide(); page.onAppReturned(); page.onShow()
        assertSame(w, page.itemWindow)
        assertTrue(w.isShowing)
        stage
    }
    @Test fun requirementsHaveOneHeadingAndItemPopupHasNoCloseAll() = scene("487-metal-alloy") { ctx ->
        val evo = Evolution(884, EvoKind.ITEM, item = "metal-alloy")
        val mon = state.mons[0].apply { bondPoints = 399 }
        val dialog = EvolutionRows.explanationDialog(ctx, data, evo, data.sp(mon.species), state.caught, state, mon) {}
        dialog.joinStack(); dialog.stackChanged(true)
        assertEquals(1, texts(dialog.content()).count { it.equals("Requirements", true) })
        assertFalse(texts(dialog.content()).any { it.contains("decay", true) })
        assertFalse("Close all" in texts(dialog.content()))
        AppDialogShots.scene(ctx, dialog)
    }
    @Test fun giftHasNoStreakLine() = scene("488-daily-gift") { ctx ->
        AchievementPopup.giftLayer(ctx, data, GiftPop("poke-ball", null, 3, 17), {}).also { view ->
            assertTrue("DAILY GIFT" in texts(view))
            assertFalse(texts(view).any { it.contains("streak", true) || it.contains("30 days") || it.contains("rarer") })
        }
    }
    @Test fun switchTargetHasRoomAroundItsSprite() = scene("495-switch-main") { ctx ->
        state.pendingMain = state.mons[1].uid
        val panel = BattlePanelView(ctx)
        panel.renderAll(); panel.refreshSwitchNotice()
        val notice = panel.switchNotice
        val icon = notice.getChildAt(0) as ImageView
        assertEquals(Ui.dp(ctx, 48), icon.layoutParams.width)
        assertTrue(notice.paddingRight >= Ui.dp(ctx, 8))
        assertTrue(icon.paddingRight >= Ui.dp(ctx, 4))
        assertEquals(ImageView.ScaleType.FIT_CENTER, icon.scaleType)
        panel
    }
}
