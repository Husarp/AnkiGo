package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Bond
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30BDetailsShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun seenOnlyItemSourceOpensEvolutionWithoutSavingLayout() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = ShopCardKit.install()
        val state = GameRepo.state!!
        val source = com.husarp.ankigo.game.ItemInfo.evoPairs(data, "thunder-stone").first().fromNum
        state.seen += source
        state.caught -= source
        val page = ShopPage(ctx, NoHost)
        val order = DetailOrder.move(DetailOrder.default, DetailSection.EVOLUTION, 3)
        AppPrefs.setDetailOrder(ctx, order)
        val saved = DetailSection.entries.associate { it.key to false }.toMutableMap()
        val original = DetailSections.storeOf
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = saved[key]
            override fun set(key: String, open: Boolean) { saved[key] = open }
        } }
        try {
            val body = ItemCard.body(page, ItemCard.Spec("thunder-stone"))
            val cell = views(body).single { it.contentDescription == "${data.sp(source).name}. Opens Evolution details" }
            val dialog = DialogShots.capture { cell.performClick() }!!
            val scene = DialogShots.scene(ctx, dialog)
            paparazzi.snapshot(scene, "r30b-item-evolution-focus", 1000)
            val all = views(scene)
            val bar = all.single { it.contentDescription == "Evolution, open" }
            val scroll = all.filterIsInstance<ScrollView>().first { views(it).contains(bar) }
            assertTrue("scrolled to Evolution", scroll.scrollY > 0)
            assertTrue(saved.values.none { it })
            assertEquals(order, AppPrefs.detailOrder(ctx))
            val sectionOrder = views((scroll.getChildAt(0) as ViewGroup)).filter { it.tag is DetailSection }
                .map { it.tag }.distinct()
            assertTrue(sectionOrder.indexOf(DetailSection.STATS) < sectionOrder.indexOf(DetailSection.EVOLUTION))
            bar.performClick()
            assertEquals("Evolution, closed", bar.contentDescription)
            assertTrue(saved.values.none { it })
            val normal = DialogShots.capture { DexDetail.show(page, data.sp(source)) }!!
            assertTrue(views(normal.content()).any { it.contentDescription == "Evolution, closed" })
        } finally { DetailSections.storeOf = original }
    }

    @Test fun dexOwnedCopiesHaveTheirOwnBondIncludingBoxAndZeroPoints() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = GameRepo.data!!
        val state = GameRepo.state!!
        val mon = state.main!!
        mon.bondPoints = 0
        val copy = TestData.mon(data, mon.species, 50, uid = "r30-box").apply { bondPoints = Bond.thresholds[3]; nickname = "Box friend" }
        state.mons += copy
        val original = DetailSections.storeOf
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = key == "bond"
            override fun set(key: String, open: Boolean) {}
        } }
        try {
            val page = DexPage(ctx, NoHost)
            val dialog = DialogShots.capture { DexDetail.show(page, data.sp(mon.species)) }!!
            val scene = DialogShots.scene(ctx, dialog)
            val all = views(scene)
            val bond = all.single { it.tag == DetailSection.BOND }
            val scroll = all.filterIsInstance<ScrollView>().first { views(it).contains(bond) }
            scene.measure(View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.heightPixels, View.MeasureSpec.EXACTLY))
            scene.layout(0, 0, scene.measuredWidth, scene.measuredHeight)
            scroll.scrollTo(0, bond.top)
            paparazzi.snapshot(scene, "r30b-dex-owned-bond", 1000)
            val text = views(bond).filterIsInstance<TextView>().joinToString("\n") { it.text }
            assertTrue(text.contains("Level 0")); assertTrue(text.contains("Level 3"))
            assertTrue(text.contains("Box friend")); assertTrue(text.contains("In box")); assertTrue(text.contains("In team"))
            state.mons.removeAll { it.species == mon.species }
            val unowned = DialogShots.capture { DexDetail.show(page, data.sp(mon.species)) }!!
            assertFalse(views(unowned.content()).any { it.tag == DetailSection.BOND })
        } finally { DetailSections.storeOf = original }
    }
}
