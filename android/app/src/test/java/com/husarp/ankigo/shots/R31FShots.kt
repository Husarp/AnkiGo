package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31FShots {
    @get:Rule val paparazzi = Shots.rule()
    private val state get() = GameRepo.state!!
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun text(v: View, label: String) = all(v).filterIsInstance<TextView>().first { it.text.toString() == label }
    private fun scene(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p412", 412, 892).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.withSprites = true
            FakeGame.install(ctx); ShopCardKit.install()
            AnkiStatsStore.enabled = false
            try { paparazzi.snapshot(build(ctx), "f-$name-${theme.name.lowercase()}", 1000) }
            finally { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
        }
    }

    private fun boxToggles(page: TeamPage) {
        page.root
        fun row() = call(page, "boxControls") as FilterRow
        assertFalse(all(row()).filterIsInstance<TextView>().any { it.text.toString() == "Shiny" })
        for ((label, filter) in listOf("Favourites" to BoxFilter.FAVOURITES, "In team" to BoxFilter.IN_TEAM)) {
            val before = getField(page, "boxFilter")
            text(row(), label).performClick()
            assertTrue(filter in getField(page, "boxFilter") as Set<*>)
            assertTrue(text(row(), label).isSelected)
            text(row(), label).performClick()
            assertEquals(before, getField(page, "boxFilter"))
        }
        val sheet = page.sortSheet().build(Ui.dp(page, 600))
        text(sheet, "Shiny").performClick()
        assertTrue(BoxFilter.SHINY in getField(page, "boxFilter") as Set<*>)
        assertTrue(all(row()).filterIsInstance<ActiveChip>().single().contentDescription.toString().contains("Shiny"))
        text(page.sortSheet().build(Ui.dp(page, 600)), "Shiny").performClick()
    }

    @Test fun boxQuickChipsToggleAndShinyLivesInSheet() = scene("519-box") { ctx ->
        val page = TeamPage(ctx, NoHost); page.preset(TeamSection.BOX, filter = BoxFilter.ALL)
        boxToggles(page)
        pageScreen(ctx, page)
    }

    @Test fun giveToPickerQuickChipsToggle() = scene("519-give-to-picker") { ctx ->
        val source = ShopPage(ctx, NoHost)
        var page: TeamPage? = null
        BoxMonPicker.interceptShow = { dialog, p ->
            (p.root.parent as? ViewGroup)?.removeView(p.root)
            page = p
            dialog.dismiss()
        }
        try { BoxMonPicker.show(source, "Give Soothe Bell to…", { true }) {} }
        finally { BoxMonPicker.interceptShow = null }
        val p = checkNotNull(page)
        p.preset(TeamSection.BOX, filter = BoxFilter.ALL)
        boxToggles(p)
        p.root
    }

    @Test fun comparePickerQuickChipsToggle() = scene("519-compare-picker") { ctx ->
        val p = TeamPage(ctx, NoHost)
        p.preset(TeamSection.BOX, pick = listOf(ComparePick.Owned(state.mons.first().uid)), filter = BoxFilter.ALL)
        boxToggles(p)
        pageScreen(ctx, p)
    }

    private fun dexToggles(p: DexPage, embedded: Boolean = false) {
        if (!embedded) p.root
        setField(p, "filter", DexFilter())
        fun row(): View { call(p, "render"); return (getField(p, "rowBox") as ViewGroup).getChildAt(0) }
        for (label in listOf("★ Wishlist", "Favourites")) {
            text(row(), label).performClick()
            assertTrue(text(row(), label).isSelected)
            text(row(), label).performClick()
            assertFalse(text(row(), label).isSelected)
            assertFalse((getField(p, "filter") as DexFilter).active)
        }
    }

    @Test fun pokedexQuickChipsToggle() = scene("519-pokedex") { ctx ->
        val p = DexPage(ctx, NoHost); dexToggles(p); pageScreen(ctx, p)
    }

    @Test fun teamHostedPokedexQuickChipsToggle() = scene("519-team-pokedex") { ctx ->
        val p = TeamPage(ctx, NoHost); p.preset(TeamSection.DEX); p.root
        dexToggles((getField(p, "dexPage\$delegate") as Lazy<*>).value as DexPage, embedded = true)
        pageScreen(ctx, p)
    }

    @Test fun widgetPickerQuickChipsToggle() = scene("519-widget-picker") { ctx ->
        val activity = attach(WidgetLookActivity(), ShotCtx(ctx)); activity.buildContent()
        val card = activity.showPokemonSheet(show = false)
        for (label in listOf("Favourites", "Shiny")) {
            text(card, label).performClick(); assertTrue(text(card, label).isSelected)
            text(card, label).performClick(); assertFalse(text(card, label).isSelected)
        }
        card
    }

    @Test fun teamCardsHaveNoFramesOrPlainRarityIcon() = scene("514-team") { ctx ->
        state.mons.take(3).forEachIndexed { i, mon -> mon.tier = listOf(Tier.NORMAL, Tier.ULTRA, Tier.MYTHICAL)[i] }
        val p = TeamPage(ctx, NoHost); p.preset(TeamSection.TEAM)
        val screen = pageScreen(ctx, p); call(p, "render")
        val col = getField(p, "teamCol") as ViewGroup
        assertTrue(all(col).none { it.foreground is RarityFrame.Frame })
        val plain = state.mons.first { it.tier == Tier.NORMAL && GameRepo.data!!.sp(it.species).base.total >= 500 }
        assertFalse(text(col, plain.shownName).text is android.text.Spanned)
        assertTrue(all(col).filterIsInstance<TextView>().any { it.text is android.text.Spanned && it.text.toString().contains(state.mons[1].shownName) })
        screen
    }

    @Test fun bagLabelHasNoCount() = scene("517-bag") { ctx ->
        state.bag["soothe-bell"] = 12
        val p = ShopPage(ctx, NoHost); p.openBag()
        val screen = pageScreen(ctx, p); call(p, "render")
        assertEquals("Bag", (getField(p, "tabBag") as TextView).text.toString())
        screen
    }

    @Test fun itemMovesReportNamesAndReturnedItemsAtBottom() = scene("518-item-snackbar") { ctx ->
        val p = ShopPage(ctx, NoHost)
        val source = state.mons[0]; val target = state.mons[1]
        source.nickname = "Sparky"; target.nickname = "Fluffy"
        source.heldItem = "soothe-bell"; target.heldItem = "lucky-egg"
        val beforeBell = state.ownedItemCount("soothe-bell")
        val beforeEgg = state.ownedItemCount("lucky-egg")
        var r = p.act { ItemUse.giveHeld(it.data, it.state, "soothe-bell", source.uid, target.uid) }
        assertTrue(r.ok)
        assertEquals("Swapped Soothe Bell from Sparky to Fluffy; Lucky Egg went back to the Bag", Ui.lastToast)
        assertEquals(beforeBell, state.ownedItemCount("soothe-bell")); assertEquals(beforeEgg, state.ownedItemCount("lucky-egg"))
        r = p.act { ItemUse.takeHeld(it.state, target.uid) }
        assertTrue(r.ok); assertEquals("Took Soothe Bell from Fluffy", Ui.lastToast)
        r = MonActions(p).runUse("soothe-bell", source.uid)
        assertTrue(r.ok); assertEquals("Gave Soothe Bell to Sparky", Ui.lastToast)
        target.heldItem = "lucky-egg"; state.bag["soothe-bell"] = 1
        r = MonActions(p).runUse("soothe-bell", target.uid)
        assertTrue(r.ok); assertEquals("Gave Soothe Bell to Fluffy; Lucky Egg went back to the Bag", Ui.lastToast)
        FrameLayout(ctx).apply {
            addView(pageScreen(ctx, p), FrameLayout.LayoutParams(-1, -1))
            AppToast.show(this, checkNotNull(Ui.lastToast))
        }
    }

    @Test fun takeOffSnackbarAppearsAboveTheOpenItemSheet() = scene("518-open-item-sheet") { ctx ->
        state.mons.forEach { it.heldItem = null }
        state.mons.first().apply { nickname = "Sparky"; heldItem = "soothe-bell" }
        val p = ShopPage(ctx, NoHost)
        val window = ItemWindow(p) {}
        window.update(ItemCard.Spec("soothe-bell"))
        val host = FrameLayout(ctx).apply { addView(window.view, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM)) }
        all(window.view).first { it.contentDescription == "Take off" }.performClick()
        assertNull(state.mons.first().heldItem)
        assertEquals("Took Soothe Bell from Sparky", Ui.lastToast)
        window.update(ItemCard.Spec("soothe-bell"))
        assertTrue(all(host).filterIsInstance<TextView>().any { it.text.toString() == Ui.lastToast })
        host
    }

    @Test fun answerButtonsUseYearAndLeftAlignedFilters() = scene("532-answer-buttons") { ctx ->
        state.history.clear()
        state.history[state.day] = DayLog(buttons = intArrayOf(0, 2, 3, 8, 4))
        val p = StatsPage(ctx, NoHost); p.preset(metric = ChartMetric.BUTTONS, period = ChartPeriod.D365)
        val screen = pageScreen(ctx, p); call(p, "render")
        val year = text(screen, "This year")
        val filters = year.parent as ViewGroup
        assertSame(year, filters.getChildAt(0))
        assertTrue(all(filters).filterIsInstance<TextView>().none { it.text.toString() == "AnkiGo answers" || it.text.toString() == "Today" })
        screen
    }
}
