package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.StreakFreeze
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R35JShopShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun sectionsKeepEveryOfferOnceAndHideEmptyGroups() {
        val items = listOf("super-potion", "everstone", "great-ball", "fire-stone", "fluffy-tail", "helix-fossil", "poke-ball")
        val sections = ShopPlan.sections(items, listOf("thunderbolt"), true)
        assertEquals(listOf("Healing", "Held items", "Balls", "TMs", "Evolution", "Escape items", "Fossils", "Streak protection"), sections.map { it.title })
        val tiles = sections.flatMap { it.tiles }
        assertEquals((items + "potion" + StreakFreeze.ITEM).toSet(), tiles.filterIsInstance<ShopPlan.Tile.Item>().map { it.id }.toSet())
        assertEquals(tiles.size, tiles.distinct().size)
        assertEquals(listOf("Healing", "Balls"), ShopPlan.sections(emptyList(), emptyList(), false).map { it.title })
    }

    @Test fun onePageInBothThemesWithPurchasesAndEmptySections() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            FakeGame.install(ctx)
            val base = ShopCardKit.install()
            val data = GameData(base.species, base.moves, base.growth, base.itemPrices + ("fluffy-tail" to 300))
            GameRepo::class.java.getDeclaredField("loadedData").apply { isAccessible = true }.set(GameRepo, data)
            GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
            val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-11-07", cash = 20000) {
                GameRepo.state!!.shopItems.apply { clear(); addAll(listOf("super-potion", "everstone", "great-ball", "fire-stone", "fluffy-tail", "helix-fossil")) }
                GameRepo.state!!.shopTms.apply { clear(); add("thunderbolt") }
                GameRepo.state!!.shopRefreshes = 0
            }
            fun texts() = all(stage).filterIsInstance<TextView>().map { it.text.toString() }
            val labels = texts()
            val headings = listOf(ShopCardKit.dealHeading(), "HEALING", "HELD ITEMS", "BALLS", "TMS", "EVOLUTION", "ESCAPE ITEMS", "FOSSILS", "STREAK PROTECTION", "WEEKEND SPECIAL")
            assertTrue(headings.toString(), headings.all { it in labels })
            assertEquals(headings.map { labels.indexOf(it) }.sorted(), headings.map { labels.indexOf(it) })
            assertFalse("BASICS" in labels)
            assertFalse("RARE FIND" in labels)
            assertTrue("Refresh · Free" in labels)
            paparazzi.snapshot(stage, "j-shop-top-${theme.name.lowercase()}", 1000)
            assertTrue(texts().containsAll(headings))
            val scroll = all(stage).filterIsInstance<ScrollView>().first()
            scroll.scrollTo(0, 480)
            paparazzi.snapshot(stage, "j-shop-sections-${theme.name.lowercase()}")
            scroll.fullScroll(View.FOCUS_DOWN)
            paparazzi.snapshot(stage, "j-shop-bottom-${theme.name.lowercase()}")
            fun clickItem(name: String) {
                var view: View = all(stage).filterIsInstance<TextView>().first { it.text.toString() == name }
                while (!view.isClickable) view = view.parent as View
                view.performClick(); stage.render()
                assertNotNull(page.itemWindow)
                assertTrue(all(page.itemWindow!!.view).filterIsInstance<TextView>().any { it.text.toString() == "Buy" })
                page.closeCard(); stage.render()
            }
            clickItem("Potion"); clickItem("Poké Ball"); clickItem("TM Thunderbolt")
            GameRepo.state!!.shopItems.clear(); GameRepo.state!!.shopTms.clear()
            GameRepo.state!!.bag[StreakFreeze.ITEM] = 3
            stage.render()
            assertFalse(texts().any { it in listOf("HELD ITEMS", "TMS", "EVOLUTION", "ESCAPE ITEMS", "FOSSILS", "STREAK PROTECTION") })
            assertTrue(texts().containsAll(listOf("HEALING", "BALLS", "Potion", "Poké Ball")))
            page.onGone()
        }
    }
}
