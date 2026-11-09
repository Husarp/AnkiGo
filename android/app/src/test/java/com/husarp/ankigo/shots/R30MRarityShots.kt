package com.husarp.ankigo.shots

import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30MRarityShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun tap(v: View, label: String) = all(v).single { it.contentDescription == label }.performClick()
    private fun clear(v: View) = all(v).filterIsInstance<TextView>().single { it.text.toString() == "Clear" }.performClick()
    private fun render(page: GamePage) {
        GamePage::class.java.getDeclaredMethod("doRender").apply { isAccessible = true }.invoke(page)
        val metrics = page.resources.displayMetrics
        page.root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        page.root.layout(0, 0, page.root.measuredWidth, page.root.measuredHeight)
    }

    private fun sheetShot(page: GamePage): View {
        val metrics = page.resources.displayMetrics
        val maxBody = (metrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(page, 150)
        val sheet = when (page) {
            is TeamPage -> page.sortSheet().build(maxBody)
            is DexPage -> page.sortSheet().build(maxBody)
            else -> error("Unsupported page")
        }
        sheet.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST))
        sheet.layout(0, 0, sheet.measuredWidth, sheet.measuredHeight)
        val heading = all(sheet).filterIsInstance<TextView>().single { it.text.toString().contains("RARITY MARK") }
        all(sheet).filterIsInstance<android.widget.ScrollView>().single().scrollTo(0, heading.top)
        return sheet
    }

    @Test fun boxMarksFilterCopiesPersistAndClearInLightAndDark() {
        paparazzi.shootPage("n-482-box", listOf(Variants.PHONE, Variant("narrow", 320, 820))) { ctx ->
            GameRepo.state!!.mons.filter { it.species == 172 }.forEach { it.tier = Tier.BABY }
            val page = TeamPage(ctx, NoHost).apply { preset(TeamSection.BOX) }
            val scene = pageScreen(ctx, page)
            render(page)
            RarityMark.setEnabled(ctx, false)
            val sheet = page.sortSheet().build(Ui.dp(ctx, 1100))
            val labels = all(sheet).filterIsInstance<TextView>().filter { it.contentDescription == "Great" }
            assertEquals(1, labels.size)
            labels.forEach { assertEquals(1, (it.text as Spanned).getSpans(0, it.text.length, RarityMark.Mark::class.java).size) }
            assertTrue(tap(sheet, "Great"))
            render(page)
            val f = RarityFilter(setOf(PokemonRarity.GREAT))
            val expected = f.apply(page.data, page.state.mons)
            assertTrue(expected.isNotEmpty())
            assertEquals(expected.size, getField(page, "shown"))
            assertEquals(f, SortPrefs.loadBoxRarity(ctx))
            assertEquals(f, getField(TeamPage(ctx, NoHost), "boxRarity"))
            assertTrue(all(scene).filterIsInstance<ActiveChip>().any { "Great" in it.contentDescription.toString() })
            paparazzi.snapshot(sheetShot(page), "n-482-box-sheet-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            paparazzi.snapshot(scene, "n-482-active-${page.javaClass.simpleName}-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            clear(page.sortSheet().build(Ui.dp(ctx, 1100)))
            render(page)
            assertEquals(page.state.mons.size, getField(page, "shown"))
            assertFalse(SortPrefs.loadBoxRarity(ctx).active)
            scene
        }
    }

    @Test fun dexMarksCombinePersistAndClear() {
        paparazzi.shootPage("n-482-dex", listOf(Variants.PHONE)) { ctx ->
            val page = DexPage(ctx, NoHost)
            val scene = pageScreen(ctx, page)
            render(page)
            val sheet = page.sortSheet().build(Ui.dp(ctx, 1100))
            assertTrue(tap(sheet, "Great"))
            assertTrue(tap(sheet, "Legendary"))
            render(page)
            val f = RarityFilter(setOf(PokemonRarity.GREAT, PokemonRarity.LEGENDARY))
            assertEquals(f, (getField(page, "filter") as DexFilter).rarities)
            val expected = page.data.species.values.filter { it.gen in page.state.settings.gens && f.matches(Encounters.tierOf(it), it.base.total) }.map { it.num }.toSet()
            assertTrue(expected.isNotEmpty())
            assertEquals(expected, (getField(page, "shown") as List<*>).map { (it as Species).num }.toSet())
            assertEquals(f, (getField(DexPage(ctx, NoHost), "filter") as DexFilter).rarities)
            paparazzi.snapshot(sheetShot(page), "n-482-dex-sheet-${ctx.resources.configuration.uiMode}")
            paparazzi.snapshot(scene, "n-482-active-${page.javaClass.simpleName}-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            clear(page.sortSheet().build(Ui.dp(ctx, 1100)))
            assertFalse((getField(page, "filter") as DexFilter).rarities.active)
            scene
        }
    }

    @Test fun emptyRarityResultOffersClear() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = TeamPage(ctx, NoHost).apply { preset(TeamSection.BOX) }
        val scene = pageScreen(ctx, page)
        render(page)
        page.state.mons.forEach { it.tier = Tier.NORMAL }
        tap(page.sortSheet().build(Ui.dp(ctx, 1000)), "Mythical")
        render(page)
        assertEquals(0, getField(page, "shown"))
        val button = all(scene).filterIsInstance<TextView>().single { it.text.toString() == "Clear filters" }
        assertEquals(View.VISIBLE, button.visibility)
        button.performClick(); render(page)
        assertFalse(SortPrefs.loadBoxRarity(ctx).active)

    }
}
