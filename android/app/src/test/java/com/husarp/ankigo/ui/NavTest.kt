package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-01: where every destination lives (Nav.target), which bottom-bar tab holds it, and the tab order. */
class NavTest {
    private fun screen(d: Dest) = Nav.target(d).screen
    private fun extra(d: Dest) = Nav.target(d).extra

    @Test fun everyDestinationHasAScreen() {
        // Study and the full-screen battle are the two views of one screen (R4-02)
        assertEquals(GameActivity::class.java, screen(Dest.STUDY))
        assertEquals(GameActivity::class.java, screen(Dest.BATTLE))
        for (d in listOf(Dest.TEAM, Dest.BOX, Dest.DEX, Dest.SHOP)) assertEquals("$d", TeamActivity::class.java, screen(d))
        for (d in listOf(Dest.STATS, Dest.ACHIEVEMENTS)) assertEquals("$d", StatsActivity::class.java, screen(d))
        assertEquals(SettingsActivity::class.java, screen(Dest.SETTINGS))
        assertEquals(Dest.entries.size, Dest.entries.map { it.name }.toSet().size)
    }

    @Test fun pokemonTabSectionsAreExtras() {
        assertEquals(TeamActivity.EXTRA_SECTION to "TEAM", extra(Dest.TEAM))
        assertEquals(TeamActivity.EXTRA_SECTION to "BOX", extra(Dest.BOX))
        assertEquals(TeamActivity.EXTRA_SECTION to "DEX", extra(Dest.DEX))
        assertEquals(TeamActivity.EXTRA_SECTION to "SHOP", extra(Dest.SHOP))
        // every extra value names a real section, and the Shop is one of them
        for (d in listOf(Dest.TEAM, Dest.BOX, Dest.DEX, Dest.SHOP)) assertEquals(d.name, TeamSection.of(extra(d)!!.second).name)
    }

    @Test fun statsTabSectionsAreExtras() {
        assertEquals(StatsActivity.EXTRA_SECTION to "STATISTICS", extra(Dest.STATS))
        assertEquals(StatsActivity.EXTRA_SECTION to "ACHIEVEMENTS", extra(Dest.ACHIEVEMENTS))
        assertEquals(StatsSection.ACHIEVEMENTS, StatsSection.of(extra(Dest.ACHIEVEMENTS)!!.second))
        assertEquals(StatsSection.STATISTICS, StatsSection.of(null))
        assertEquals(StatsSection.STATISTICS, StatsSection.of("nonsense"))
    }

    @Test fun homeViewsAreExtras() {
        assertEquals(HomeView.EXTRA_VIEW to "study", extra(Dest.STUDY))
        assertEquals(HomeView.EXTRA_VIEW to "battle", extra(Dest.BATTLE))
        // every value names a real view
        assertEquals(HomeView.STUDY, HomeView.of(extra(Dest.STUDY)!!.second))
        assertEquals(HomeView.BATTLE, HomeView.of(extra(Dest.BATTLE)!!.second))
    }

    /** An open home screen is brought to the front and told the view (onNewIntent), never started a second time. */
    @Test fun homeStartsReorderToFrontAndSingleTop() {
        val want = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        for (d in listOf(Dest.STUDY, Dest.BATTLE)) assertEquals("$d", want, Nav.target(d).flags)
        assertEquals("Settings has no onNewIntent: no reuse flags", 0, Nav.target(Dest.SETTINGS).flags)
    }

    /** R4 fix B2: the tab targets carry the reuse flags in [Nav.intent] itself, so a start that skips [Nav.open] (GameActivity.openScreen) never stacks a second tab. */
    @Test fun tabTargetsReuseAnOpenScreen() {
        val want = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        for (d in listOf(Dest.TEAM, Dest.BOX, Dest.DEX, Dest.SHOP, Dest.STATS, Dest.ACHIEVEMENTS)) {
            assertEquals("$d", want, Nav.target(d).flags)
            assertTrue("$d", Nav.target(d).flags and android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
            assertTrue("$d", Nav.target(d).flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }
        assertEquals(want, Nav.TAB_FLAGS)
    }

    @Test fun otherScreensNeedNoExtra() {
        assertNull(extra(Dest.SETTINGS))
    }

    @Test fun barHasFourTabsInOrder() {
        assertEquals(listOf("Study", "Pokémon", "Stats", "Settings"), BottomNav.Tab.entries.map { it.label })
        assertEquals(
            listOf(Dest.STUDY, Dest.TEAM, Dest.STATS, Dest.SETTINGS),
            BottomNav.Tab.entries.map { it.dest },
        )
    }

    @Test fun everyDestinationHighlightsATab() {
        assertEquals(BottomNav.Tab.STUDY, BottomNav.tabOf(Dest.STUDY))
        assertEquals(BottomNav.Tab.STUDY, BottomNav.tabOf(Dest.BATTLE)) // the battle is the second view of Study
        for (d in listOf(Dest.TEAM, Dest.BOX, Dest.DEX, Dest.SHOP)) assertEquals("$d", BottomNav.Tab.POKEMON, BottomNav.tabOf(d))
        for (d in listOf(Dest.STATS, Dest.ACHIEVEMENTS)) assertEquals("$d", BottomNav.Tab.STATS, BottomNav.tabOf(d))
        assertEquals(BottomNav.Tab.SETTINGS, BottomNav.tabOf(Dest.SETTINGS))
        // a tab's own destination sits under that tab
        for (t in BottomNav.Tab.entries) assertEquals(t, BottomNav.tabOf(t.dest))
    }

    @Test fun shopIsASectionOfThePokemonTab() {
        assertEquals(listOf("Team", "Box", "Pokédex", "Shop"), TeamSection.entries.map { it.label })
        assertEquals(TeamSection.SHOP, TeamSection.of("SHOP"))
        assertTrue(BottomNav.Tab.entries.none { it.label == "Shop" || it.label == "Battle" })
    }
}
