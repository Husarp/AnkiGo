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
    private fun render(page: GamePage) = GamePage::class.java.getDeclaredMethod("doRender").apply { isAccessible = true }.invoke(page)

    @Test fun boxMarksFilterCopiesPersistAndClearInLightAndDark() {
        paparazzi.shootPage("m-482-box", listOf(Variants.PHONE, Variant("narrow", 320, 820))) { ctx ->
            val page = TeamPage(ctx, NoHost).apply { preset(TeamSection.BOX) }
            val scene = pageScreen(ctx, page)
            render(page)
            RarityMark.setEnabled(ctx, false)
            val sheet = page.sortSheet().build(Ui.dp(ctx, 1100))
            val labels = all(sheet).filterIsInstance<TextView>().filter { it.contentDescription == "Starter / strong" || it.contentDescription == "Mythical" }
            assertEquals(2, labels.size)
            labels.forEach { assertEquals(1, (it.text as Spanned).getSpans(0, it.text.length, RarityMark.Mark::class.java).size) }
            assertTrue(tap(sheet, "Starter / strong"))
            render(page)
            val f = RarityFilter(setOf(PokemonRarity.COMMON))
            val expected = f.apply(page.data, page.state.mons)
            assertTrue(expected.isNotEmpty())
            assertEquals(expected.size, getField(page, "shown"))
            assertEquals(f, SortPrefs.loadBoxRarity(ctx))
            assertEquals(f, getField(TeamPage(ctx, NoHost), "boxRarity"))
            assertTrue(all(scene).filterIsInstance<ActiveChip>().any { "Starter / strong" in it.contentDescription.toString() })
            paparazzi.snapshot(page.sortSheet().build(Ui.dp(ctx, 1100)), "m-482-box-sheet-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            paparazzi.snapshot(scene, "m-482-active-${page.javaClass.simpleName}-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            clear(page.sortSheet().build(Ui.dp(ctx, 1100)))
            render(page)
            assertEquals(page.state.mons.size, getField(page, "shown"))
            assertFalse(SortPrefs.loadBoxRarity(ctx).active)
            scene
        }
    }

    @Test fun dexMarksCombinePersistAndClear() {
        paparazzi.shootPage("m-482-dex", listOf(Variants.PHONE)) { ctx ->
            val page = DexPage(ctx, NoHost)
            val scene = pageScreen(ctx, page)
            render(page)
            val sheet = page.sortSheet().build(Ui.dp(ctx, 1100))
            assertTrue(tap(sheet, "Baby"))
            assertTrue(tap(sheet, "Legendary"))
            render(page)
            val f = RarityFilter(setOf(PokemonRarity.BABY, PokemonRarity.LEGENDARY))
            assertEquals(f, (getField(page, "filter") as DexFilter).rarities)
            val expected = page.data.species.values.filter { it.gen in page.state.settings.gens && f.matches(Encounters.tierOf(it), it.base.total) }.map { it.num }.toSet()
            assertTrue(expected.isNotEmpty())
            assertEquals(expected, (getField(page, "shown") as List<*>).map { (it as Species).num }.toSet())
            assertEquals(f, (getField(DexPage(ctx, NoHost), "filter") as DexFilter).rarities)
            paparazzi.snapshot(page.sortSheet().build(Ui.dp(ctx, 1100)), "m-482-dex-sheet-${ctx.resources.configuration.uiMode}")
            paparazzi.snapshot(scene, "m-482-active-${page.javaClass.simpleName}-${ctx.resources.displayMetrics.widthPixels}-${ctx.resources.configuration.uiMode}")
            clear(page.sortSheet().build(Ui.dp(ctx, 1100)))
            assertFalse((getField(page, "filter") as DexFilter).rarities.active)
            scene
        }
    }

    @Test fun emptyRarityResultOffersClearAndRecipientPickerHasMarks() {
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
        val picker = OwnedMonPicker.content(page, { true }) {}
        val dialog = DialogShots.capture { all(picker).filterIsInstance<TextView>().single { it.text == "Sort & filter" }.performClick() }
        assertNotNull(dialog)
        assertTrue(all(dialog!!.content()).any { it.contentDescription == "Ultra Beast" })
        dialog.dismiss()
    }
}
