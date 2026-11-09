package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31AShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun shot(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            paparazzi.snapshot(build(ctx), "a-$name-${theme.name.lowercase()}")
        }
    }

    @Test fun evolutionWindowNamesTheTargetAndChecksEachRequirement() = shot("502-evolution-ready") { ctx ->
        val e = Evolution(135, EvoKind.LEVEL, level = 16)
        val sp = TestData.species(133, "Eevee", evolutions = listOf(e))
        val data = TestData.data(listOf(sp, TestData.species(135, "Jolteon", prevo = 133)))
        val state = TestData.state(data, 133, 20).apply { main!!.bondPoints = 125 }
        var evolved = 0
        val rows = EvolutionRows(ctx, data, EvoLine.of(data, sp).root, sp, setOf(133, 135), null, state, state.main) { evolved++ }
        val dialog = DialogShots.capture { all(rows).single { it.contentDescription == "Explain Level 16" }.performClick() }!!
        val content = dialog.content()
        assertTrue("Evolution · Jolteon" in texts(content))
        assertTrue("✓ Level 16" in texts(content)); assertTrue("✓ Level 2 Bond" in texts(content))
        val button = dialog.button(AppDialog.BUTTON_POSITIVE)!!
        assertEquals("Evolve", button.text.toString())
        assertEquals(Ui.dp(ctx, 56), button.minHeight)
        button.performClick(); assertEquals(0, evolved)
        button.performLongClick(); assertEquals(1, evolved)
        DialogShots.scene(ctx, dialog)
    }

    @Test fun evolutionItemWindowShowsPartialRequirementsAndMasksUnknownTarget() = shot("502-503-evolution-missing") { ctx ->
        val data = GameRepo.data!!; val state = GameRepo.state!!
        val mon = state.mons[0].apply { bondPoints = 125 }
        state.bag.clear()
        val e = Evolution(26, EvoKind.ITEM, item = "thunder-stone")
        val dialog = EvolutionRows.explanationDialog(ctx, data, e, data.sp(mon.species), emptySet(), state, mon) {}
        val text = texts(dialog.content())
        assertTrue("Evolution · ???" in text)
        assertTrue("✓ Level 2 Bond" in text); assertTrue("• Use Thunder Stone" in text)
        assertEquals(1, text.count { it.equals("Requirements", true) })
        assertTrue(text.any { "Shop" in it })
        DialogShots.scene(ctx, dialog)
    }

    @Test fun heldItemHasIndependentSectionAndRememberedState() = shot("504-held-item") { ctx ->
        val data = GameRepo.data!!; val state = GameRepo.state!!
        val mon = state.mons[0].apply { heldItem = "soothe-bell" }
        val page = TeamPage(ctx, NoHost)
        AppPrefs.setDetailOpen(page, "evolution", false)
        val section = DetailTopCard.heldItem(page, data, mon, page)!!
        assertEquals(DetailSection.HELD_ITEM, section.tag)
        assertTrue("Soothe Bell" in texts(section))
        assertTrue(texts(section).any { "Bond" in it })
        assertFalse("Take off" in texts(section)); assertFalse("Give to another Pokémon" in texts(section))
        val bar = (section as ViewGroup).getChildAt(0)
        bar.performClick()
        assertFalse(DetailSections.memory(page).isOpen(DetailSection.HELD_ITEM))
        assertFalse(DetailSections.memory(page).isOpen(DetailSection.EVOLUTION))
        val reopened = DetailTopCard.heldItem(page, data, mon, page) as ViewGroup
        assertEquals(View.GONE, reopened.getChildAt(1).visibility)
        bar.performClick()
        val evolution = DetailTopCard.forMon(page, data, state, mon, data.sp(mon.species), state.caught, false, page, null)
        assertFalse("Soothe Bell" in texts(evolution))
        assertFalse("Take off" in texts(evolution))
        val dialog = AppDialog.Builder(page).setInfoHeader("Pokémon details").setView(LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            addView(section); addView(evolution)
        }).create()
        DialogShots.scene(ctx, dialog)
    }

    @Test fun pokemonDetailsOrderTheNewSectionIndependently() = shot("504-details") { ctx ->
        val data = GameRepo.data!!; val state = GameRepo.state!!
        val mon = state.mons[0].apply { heldItem = "soothe-bell" }
        val page = TeamPage(ctx, NoHost)
        AppPrefs.setDetailOrder(page, listOf(DetailSection.HELD_ITEM, DetailSection.EVOLUTION))
        for (section in listOf(DetailSection.STATS, DetailSection.MOVES)) AppPrefs.setDetailOpen(page, section.key, false)
        val dialog = DialogShots.capture { MonDetail.show(page, data, mon) }!!
        val sections = all(dialog.content()).filter { it.tag is DetailSection }
        assertEquals(listOf(DetailSection.HELD_ITEM, DetailSection.EVOLUTION), sections.take(2).map { it.tag })
        assertTrue("Soothe Bell" in texts(sections[0]))
        assertFalse("Soothe Bell" in texts(sections[1]))
        DialogShots.scene(ctx, dialog)
    }

    @Test fun takingOffThroughTheItemWindowRemovesTheSectionAndReturnsItToTheBag() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val data = GameRepo.data!!; val state = GameRepo.state!!
        state.mons.forEach { it.heldItem = null }
        val mon = state.mons[0].apply { heldItem = "soothe-bell" }
        val page = TeamPage(ctx, NoHost)
        val before = state.count("soothe-bell")
        val section = DetailTopCard.heldItem(page, data, mon, page)!!
        var opened: ItemWindow? = null
        val prior = ItemWindow.interceptShow
        try {
            ItemWindow.interceptShow = { opened = it }
            all(section).single { it.contentDescription?.contains("Tap for what it does") == true }.performClick()
            all(checkNotNull(opened).view).single { it.contentDescription == "Take off" }.performClick()
        } finally { ItemWindow.interceptShow = prior }
        assertNull(mon.heldItem)
        assertEquals(before + 1, state.count("soothe-bell"))
        assertNull(DetailTopCard.heldItem(page, data, mon, page))
    }

    @Test fun noHeldItemHasNoSectionAndExpShareRemainsVisible() = shot("504-empty-held-item") { ctx ->
        val data = GameRepo.data!!; val state = GameRepo.state!!
        val mon = state.mons[0].apply { heldItem = null }
        state.settings.xpShareUid = mon.uid
        assertNull(DetailTopCard.heldItem(ctx, data, mon, null))
        val share = DetailTopCard.expShare(ctx, data, state, mon)!!
        assertTrue("Exp. Share" in texts(share))
        val evolution = DetailTopCard.forMon(ctx, data, state, mon, data.sp(mon.species), state.caught, false, null, null)
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(share); addView(evolution) }
    }

    @Test fun nonEvolvingPokemonAndFinalFormsHaveStaticTitle() = shot("505-does-not-evolve") { ctx ->
        val e = Evolution(2, EvoKind.LEVEL, level = 16)
        val data = TestData.data(listOf(TestData.species(1, "Bulbasaur", evolutions = listOf(e)),
            TestData.species(2, "Ivysaur", prevo = 1), TestData.species(99, "Loner")))
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            for (num in listOf(2, 99)) {
                val section = DetailTopCard.evolutionOnly(ctx, data, data.sp(num), setOf(1, 2, 99), false, null, forceOpen = true) as ViewGroup
                val heading = section.getChildAt(0)
                assertTrue("DOES NOT EVOLVE" in texts(heading))
                assertFalse(heading.isClickable)
                assertEquals("Does not evolve", heading.contentDescription)
                assertEquals(View.GONE, section.getChildAt(1).visibility)
                assertFalse(heading.performClick())
                addView(section)
            }
        }
    }

    @Test fun allHoldActionsKeepLabelsWithoutInstructionSpace() = shot("502-hold-buttons") { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            for (label in listOf("Evolve", "Buy", "Refresh", "Release", "Teach", "Use", "Revive", "Throw")) {
                val button = TextView(ctx).apply { text = label; gravity = android.view.Gravity.CENTER }
                val bottom = button.paddingBottom
                HoldToConfirm.bind(button, label.lowercase()) {}
                assertEquals(bottom, button.paddingBottom)
                assertEquals(Ui.dp(ctx, 56), button.minHeight)
                assertEquals(label, button.text.toString())
                button.background.level = 5000
                addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 8) })
            }
        }
    }
}
