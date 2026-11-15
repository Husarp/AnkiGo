package com.husarp.ankigo.shots

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import kotlin.random.Random

class R34FFixesTest {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun text(v: View, s: String) = views(v).filterIsInstance<TextView>().first { it.text.toString() == s }
    private val phone = listOf(Variants.PHONE)

    @Test fun heroKeepsDateAndLevelWithoutEvolutionRequirements() {
        paparazzi.shootPage("f-dex-last-caught", phone) { ctx ->
            val p = DexPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            val hero = call(p, "hero") as View
            val t = texts(hero)
            assertTrue(t.any { it.contains("last caught") })
            assertTrue(t.any { it.contains(" now") && !it.contains('\n') })
            val last = GameRepo.state!!.mons.maxBy { it.caughtAt }
            val evo = MonDetail.nextEvoLine(p.data, p.data.sp(last.species))
            assertFalse(t.any { it.contains(evo) })
            assertTrue("Evolution details still exist", evo.isNotBlank())
            screen
        }
    }

    private fun abilityScene(ctx: android.content.Context, tap: String? = null): View {
        val page = DexPage(ctx, NoHost)
        val sp = page.data.sp(page.state.main!!.species).copy(
            abilities = listOf("Synchronize", "Early Bird"), hiddenAbility = "Magic Bounce")
        val original = DetailSections.storeOf
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = key == "stats"
            override fun set(key: String, open: Boolean) {}
        } }
        try {
            val dialog = DialogShots.capture { DexDetail.show(page, sp) }!!
            val content = dialog.content()
            assertTrue("Possible abilities" in texts(content))
            assertFalse(texts(content).any { it.contains("(hidden)") })
            for (name in sp.abilities + sp.hiddenAbility!!) {
                val ability = text(content, name)
                assertTrue(ability.isClickable)
                assertEquals(AbilityText.of(name) ?: "This ability has no battle effect in AnkiGo.", (ability.tag as StatInfoText).text)
                assertTrue(ability.minimumHeight >= Ui.dp(ctx, 48))
            }
            val rare = text(content, "rare")
            assertEquals(DexDetail.RARE_ABILITY_TEXT, (rare.tag as StatInfoText).text)
            if (tap != null) {
                val popup = DialogShots.capture { text(content, tap).performClick() }!!
                val expected = if (tap == "rare") DexDetail.RARE_ABILITY_TEXT else AbilityText.of(tap)!!
                assertTrue(expected in texts(popup.content()))
                dialog.dismiss()
                return DialogShots.scene(ctx, popup)
            }
            return DialogShots.scene(ctx, dialog)
        } finally { DetailSections.storeOf = original }
    }

    @Test fun possibleAbilities() = paparazzi.shootPage("f-dex-abilities", phone) { abilityScene(it) }
    @Test fun abilityExplanation() = paparazzi.shootPage("f-ability-explanation", phone) { abilityScene(it, "Synchronize") }
    @Test fun rareExplanation() = paparazzi.shootPage("f-rare-explanation", phone) { abilityScene(it, "rare") }

    @Test fun hiddenAbilitiesAreNeverRolledForNewOrWildPokemon() {
        val sp = TestData.species(25).copy(abilities = listOf("Static", "Run Away"), hiddenAbility = "Lightning Rod")
        val data = TestData.data(listOf(sp))
        val rng = Random(634)
        val abilities = (1..100).flatMap {
            listOf(Progression.newMon(data, 25, 10, rng, 1).ability,
                Encounters.makeWild(data, Encounters.Roll(25, Tier.NORMAL, false), 10, rng, 1).ability)
        }.toSet()
        assertEquals(sp.abilities.toSet(), abilities)
        val wild = Encounters.makeWild(data, Encounters.Roll(25, Tier.NORMAL, false), 10, rng, 1)
        val caught = Catching.addToCollection(data, GameState(), wild, Tier.NORMAL, null, 2, mutableListOf())
        assertEquals(wild.ability, caught.ability)
        assertTrue(caught.ability in sp.abilities)
        assertTrue(DexDetail.RARE_ABILITY_TEXT.contains("cannot be obtained"))
    }

    @Test fun lockedTiersKeepNamesThresholdsAndLocksWithoutCaptions() {
        paparazzi.shootPage("f-achievement-ladder", phone) { ctx ->
            val state = GameRepo.state!!
            state.defeats = 120
            Achievements.check(state, GameRepo.data!!, 1_800_000_000_000L, mutableListOf(), silent = true)
            val v = Achievements.view(state, GameRepo.data!!).first { it.id == "defeats" }
            assertTrue(AchPageMath.ladder(v).any { it.state == AchStep.State.LOCKED })
            FrameLayout(ctx).apply {
                setBackgroundColor(0x66000000)
                addView(AchievementDetail.sheet(ctx, v, state.achSince, ZoneId.of("UTC")), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }.also {
                val labels = texts(it)
                assertFalse(labels.any { t -> t.startsWith("next after ") || t.startsWith("locked until ") })
                assertTrue(labels.any { t -> t.startsWith("Mythical ·") })
            }
        }
    }

    @Test fun dexSheetContainsOnlySpeciesControlsAndClearPreservesQuickWishlist() {
        paparazzi.shootPage("f-dex-filter", phone) { ctx ->
            val p = DexPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            setField(p, "filter", DexFilter(wishlistOnly = true, gens = setOf(1), sort = DexSort.TOTAL))
            val sheet = p.sortSheet().build(Ui.dp(ctx, 610))
            val labels = texts(sheet)
            for (absent in listOf("Only wishlist", "SHOW", "Level", "Recently evolved", "STAT BASIS", "Current", "At Lv N")) assertFalse(absent, absent in labels)
            assertTrue("GENERATION" in labels)
            assertTrue("Total stats" in labels)
            assertHeadingsHaveIcons(sheet)
            text(sheet, "Clear").performClick()
            val filter = getField(p, "filter") as DexFilter
            assertTrue(filter.wishlistOnly)
            assertTrue(filter.gens.isEmpty())
            assertEquals(StatBasis.BASE, filter.basis)
            overlay(ctx, screen, sheet)
        }
    }

    @Test fun boxSheetKeepsMultipleRelevantShowChoicesAndLevel() {
        paparazzi.shootPage("f-box-filter", phone) { ctx ->
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX)
            val screen = pageScreen(ctx, p)
            val sheet = p.sortSheet().build(Ui.dp(ctx, 610))
            val labels = texts(sheet)
            assertTrue("Level" in labels)
            assertTrue("SHOW" in labels)
            assertTrue("Shiny" in labels && "Can evolve now" in labels && "Holds an item" in labels)
            assertHeadingsHaveIcons(sheet)
            overlay(ctx, screen, sheet)
        }
    }

    @Test fun oldDexLevelSortFallsBackToNumberAndBaseStats() {
        val ctx = Shots.files(paparazzi.context)
        for (key in listOf(SortKey.LEVEL, SortKey.RECENTLY_EVOLVED)) {
            SortPrefs.save(ctx, SortPrefs.Where.DEX, SortSpec.of(key).copy(basis = StatBasis.CURRENT))
            assertEquals(SortKey.DEX_NUMBER, SortViews.loadDex(ctx).key)
            assertEquals(StatBasis.BASE, SortViews.loadDex(ctx).basis)
        }
    }

    private fun assertHeadingsHaveIcons(root: View) {
        val titles = setOf("DIRECTION", "SORT BY", "GENERATION", "TYPE", "RARITY MARK", "SHOW", "STAT BASIS")
        views(root).filterIsInstance<TextView>().filter { it.text.toString() in titles }.forEach {
            assertNotNull(it.text.toString(), it.compoundDrawablesRelative[0])
        }
    }
    private fun overlay(ctx: android.content.Context, screen: View, sheet: View) = FrameLayout(ctx).apply {
        addView(screen, FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(0x7a000000) }, FrameLayout.LayoutParams(-1, -1))
        addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }
}
