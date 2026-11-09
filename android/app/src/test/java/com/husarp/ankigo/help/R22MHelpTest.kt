package com.husarp.ankigo.help

import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeChart
import com.husarp.ankigo.ui.SettingsCatalog
import org.junit.Assert.*
import org.junit.Test

class R22MHelpTest {
    @Test fun everyChartSummaryMatchesAll324EngineCells() {
        for (atk in Type.entries) for (def in Type.entries) {
            val multiplier = TypeChart.single(atk, def)
            for (m in listOf(0.0, 0.5, 2.0)) assertEquals(multiplier == m, def in GuideTypeChart.targets(atk, m))
        }
    }
    @Test fun defensiveSummaryUsesIncomingMoves() {
        for (def in Type.entries) for (atk in Type.entries) for (m in listOf(0.0, 0.5, 2.0))
            assertEquals(TypeChart.single(atk, def) == m, atk in GuideTypeChart.targets(def, m, true))
    }
    @Test fun familiarImmunitiesAreShownAsNoEffect() {
        assertTrue(Type.GHOST in GuideTypeChart.targets(Type.NORMAL, 0.0))
        assertTrue(Type.GROUND in GuideTypeChart.targets(Type.ELECTRIC, 0.0))
        assertTrue(Type.GRASS in GuideTypeChart.targets(Type.FIRE, 2.0))
    }
    @Test fun allDetailedRulesStayReachable() {
        for (ch in GameGuide.chapters()) {
            val visible = GuideLessons.shortPoints(ch)
            val folded = GuideLessons.details(ch).flatMap { it.second }
            assertTrue(visible.size <= 4)
            assertEquals(folded.size, folded.toSet().size)
            ch.points.forEach { assertTrue("${ch.id}: $it", it in visible || it in folded) }
        }
    }
    @Test fun evolutionLessonExplainsTheExplicitChoice() {
        val text = GameGuide.chapters().first { it.id == GameGuide.LEVELS }.points.joinToString(" ")
        assertTrue(text.contains("Evolution never happens automatically"))
        assertTrue(text.contains("choose Evolve"))
        assertTrue(text.contains("The item is consumed"))
        assertFalse(text.contains("evolve after a win"))
    }
    @Test fun shopGuideMatchesTheCurrentPriceControls() {
        val text = GameGuide.chapters().first { it.id == GameGuide.MONEY }.points.joinToString(" ")
        assertFalse(text.contains("Tap ↓ or ↑"))
        assertTrue(text.contains("Refresh keeps prices"))
        assertTrue(text.contains("costs 100 more each time"))
        assertTrue(text.contains("resets the next game day"))
    }
    @Test fun internalLinksResolveSpecificSettings() {
        val links = HelpSettingsLinks.find("Open Settings → Battle panel & windows → On leaving. Then Settings → Game → Battle.")
        assertEquals(listOf("leaveMode", "moveChoiceSmart"), links.map { it.setting })
        links.forEach { link -> assertNotNull(SettingsCatalog.all.find { it.id == link.setting }) }
    }
    @Test fun appPrefixIsPartOfTheClickablePath() {
        val source = "AnkiGo → Settings → Diagnostics"
        assertEquals(HelpSettingsLinks.Link(0, source.length, "@DIAGNOSTICS"), HelpSettingsLinks.find(source).single())
    }
    @Test fun externalSettingsAreNotAnkiGoLinks() {
        assertTrue(HelpSettingsLinks.find("Android Settings → Apps → AnkiGo → Battery → Unrestricted.").isEmpty())
        assertTrue(HelpSettingsLinks.find("In AnkiDroid open Settings → Sync and raise Network timeout.").isEmpty())
        assertTrue(HelpSettingsLinks.find("In AnkiDroid open Settings → Study.").isEmpty())
    }
    @Test fun quotedCatchPathUsesOneLinkAndImportUsesTheRow() {
        val source = "Settings → Game → \"When you beat a wild Pokémon\""
        val links = HelpSettingsLinks.find(source)
        assertEquals(1, links.size); assertEquals(source.length, links.single().end)
        assertEquals("ankimonImport", HelpSettingsLinks.find("Settings → Ankimon PC sync → Import Ankimon save (.db)…").single().setting)
    }
}
