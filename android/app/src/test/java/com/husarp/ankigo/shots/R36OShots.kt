package com.husarp.ankigo.shots

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class R36OShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun clean() { Pickers.interceptApp = null; AwayWindows.dismissAll(); AchievementPopup.reset(); BattleShotKit.reset() }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun text(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun tap(v: View, label: String) { all(v).filterIsInstance<TextView>().first { it.text.toString() == label }.performClick() }

    @Test fun giftRequiresClaimAndNeverGrantsAnotherReward() = paparazzi.shootPage("o-gift", listOf(Variants.PHONE)) { ctx ->
        val state = GameRepo.state!!
        val before = state.bag.toMap()
        var closed = 0
        val layer = AchievementPopup.giftLayer(ctx, GameRepo.data, GiftPop("poke-ball", null, 3, 6), { closed++ })
        repeat(3) { layer.performClick() }
        assertEquals(0, closed)
        assertTrue("Claim" in text(layer)); assertFalse("Nice" in text(layer))
        tap(layer, "Claim")
        assertEquals(1, closed)
        assertEquals(before, state.bag)
        FrameLayout(ctx).apply { setBackgroundColor(0x73000000); addView(layer, FrameLayout.LayoutParams(-1, -1)) }
    }

    @Test fun achievementSheetHidesMasterBallBeforeAndAfterTheTierIsReached() = paparazzi.shootPage("o-achievement", listOf(Variants.PHONE)) { ctx ->
        var result: View? = null
        for (days in listOf(27, 365)) {
            GameRepo.state!!.bestStreakDays = days
            val row = Achievements.view(GameRepo.state!!, GameRepo.data!!).first { it.id == "streak-days" }
            val layer = AchievementDetail.sheet(ctx, row, 0L, ZoneId.of("UTC"))
            assertFalse(text(layer).any { it.contains("Master Ball") })
            assertEquals("master-ball", AchPageMath.rewardOf(row.id, 4))
            if (days == 27) result = layer
        }
        FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(result!!, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
    }

    @Test fun allRewardBackgroundsLeaveTheCardOpenAndButtonsCloseIt() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val row = AchievementTable.row("streak-days")!!
        val award = AchCelebration(row.id, row.title, 1, AchievementTable.tierName(1), row.tiers[1])
        val badge = AchCelebration("badge", "First badge", -1, "Badge", null)
        var closed = 0
        var seen = 0
        val cards = listOf(
            AchievementPopup.layer(ctx, listOf(award), { closed++ }, { seen++ }),
            AchievementPopup.layer(ctx, listOf(award, badge), { closed++ }, { seen++ }),
            AchievementPopup.layer(ctx, listOf(badge), { closed++ }, { seen++ }),
            AchievementPopup.itemLayer(ctx, listOf(ItemFind("potion", 3)), { closed++ }),
            AchievementPopup.streakLayer(ctx, GameEvent.StreakSaved(1, 1, 0, "2026-11-14", "2026-11-14"), { closed++ }),
        )
        cards.forEachIndexed { i, card ->
            all(card).filter { it !is TextView || it.text.toString() !in listOf("Nice", "See it") }.forEach { it.performClick() }
            assertEquals(i, closed); assertEquals(0, seen)
            tap(card, "Nice")
            assertEquals(i + 1, closed)
        }
        val info = AppDialog.Builder(ctx).setTitle("Info").create()
        val field = AppDialog::class.java.getDeclaredField("cancelable").apply { isAccessible = true }
        assertTrue(field.getBoolean(info))
        val evolved = AppDialogShots.capture { Pickers.evolved(ctx, GameEvent.Evolved("t1", "Pikachu", "Raichu", 26), false) }!!
        assertFalse(field.getBoolean(evolved))
        evolved.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
    }

    @Test fun pokemonListAndEvolutionNamePickersPreviewWithoutPicking() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = GameRepo.data!!
        val mons = GameRepo.state!!.mons.take(2)
        for (namesOnly in listOf(false, true)) {
            var picked: Mon? = null
            val original = AppDialogShots.capture { MonDetail.show(ctx, data, mons[0]) }!!
            val dialog = AppDialogShots.capture {
                if (namesOnly) Pickers.pickMonNames(ctx, "Use on…", data, mons) { picked = it }
                else Pickers.pickMon(ctx, "Give item to…", data, mons, { "Ready" }) { picked = it }
            }!!
            val list = all(dialog.content()).filterIsInstance<ListView>().single()
            var details: AppDialog? = null
            Pickers.interceptApp = { details = it }
            assertTrue(list.onItemLongClickListener!!.onItemLongClick(list, list.adapter.getView(0, null, list), 0, 0))
            assertNull(picked)
            assertTrue(checkNotNull(details).pokemonDetails)
            assertNotSame(original, details)
            assertSame("The picker is still underneath the preview", details, WindowStack.pokemonWindow(ctx))
            details!!.dismiss()
            assertNull("The picker blocks reuse of the original details window", WindowStack.pokemonWindow(ctx))
            list.performItemClick(list.adapter.getView(1, null, list), 1, 1)
            assertSame(mons[1], picked)
            original.dismiss()
            Pickers.interceptApp = null
        }
    }

    @Test fun duplicatePickerAllowsInspectingIneligibleCopiesWithoutChoosingThem() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = TeamPage(ctx, NoHost)
        val mon = GameRepo.state!!.mons.first()
        var picked: Mon? = null
        val sheet = BoxCopiesSheet(page, BoxTile(listOf(mon)), { picked = it }, { _, _, _ -> }, { false })
        val view = sheet.build(800)
        val row = all(view).first { it.hasOnClickListeners() && it.isLongClickable }
        var details: AppDialog? = null
        Pickers.interceptApp = { details = it }
        try {
            assertTrue(row.isEnabled)
            assertTrue(row.performLongClick())
            assertTrue(checkNotNull(details).pokemonDetails)
            assertNull(picked)
            details!!.dismiss()
            row.performClick()
            assertNull(picked)
        } finally { page.onGone() }
    }

    @Test fun tmRecipientGridHoldShowsDetailsWithoutSelectingASpecies() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = DexPage(ctx, NoHost)
        var picked: Species? = null
        page.pickRecipient(DexPage.Recipient("Teach TM to…", { true }) { picked = it })
        page.onShow()
        try {
            page.root
            page.javaClass.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val field = DexPage::class.java.getDeclaredField("list").apply { isAccessible = true }
            val list = field.get(page) as ListView
            val row = list.adapter.getView(list.headerViewsCount, null, list)
            val cell = all(row).filterIsInstance<SquareFrame>().first { it.visibility == View.VISIBLE }
            var details: AppDialog? = null
            Pickers.interceptApp = { details = it }
            assertTrue(cell.performLongClick())
            assertNull(picked)
            assertTrue(checkNotNull(details).pokemonDetails)
            details!!.dismiss()
            cell.performClick()
            assertNotNull(picked)
        } finally { page.onHide(); page.onGone() }
    }

    @Test fun boxRecipientHoldOpensDetailsAndTapStillPicks() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = TeamPage(ctx, NoHost)
        var picked: Mon? = null
        page.pickRecipient(TeamPage.Recipient("Give item to…", { true }) { picked = it })
        page.onShow()
        try {
            page.root
            page.javaClass.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val field = TeamPage::class.java.getDeclaredField("boxList").apply { isAccessible = true }
            val list = field.get(page) as ListView
            val row = list.adapter.getView(list.headerViewsCount, null, list)
            val cell = all(row).filterIsInstance<SquareFrame>().first { it.visibility == View.VISIBLE }
            var details: AppDialog? = null
            Pickers.interceptApp = { details = it }
            assertTrue(cell.performLongClick())
            assertNull(picked)
            assertTrue(checkNotNull(details).pokemonDetails)
            details!!.dismiss()
            cell.performClick()
            assertNotNull(picked)
        } finally { page.onHide(); page.onGone() }
    }
}
