package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Matchup
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeMatchups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-W3-A: the D8-V1 matchup groups (order, which show, tap messages, colours). Made-up names only. */
class MatchupGroupsTest {
    private fun names(groups: List<Pair<MatchupGroup, List<Type>>>) = groups.map { it.first }

    @Test fun orderAndPills() {
        assertEquals(listOf("×4", "×2", "×½", "×¼", "×0", "×2"), MatchupGroup.entries.map { it.pill })
        assertEquals(listOf("Very weak against", "Weak against", "Resists", "Strongly resists", "Immune to", "Strong against"), MatchupGroup.entries.map { it.label })
        assertEquals(MatchupGroup.STRONG_AGAINST, MatchupGroup.entries.last())
    }

    @Test fun fireFlyingHasAllFiveAndStrongLast() {
        // Fire/Flying: Rock x4, Water + Electric x2, Fire/Fighting/Steel/Fairy x1/2, Grass + Bug x1/4, Ground x0
        val g = MatchupGroups.details(TypeMatchups.of(listOf(Type.FIRE, Type.FLYING)))
        assertEquals(listOf(MatchupGroup.VERY_WEAK, MatchupGroup.WEAK, MatchupGroup.RESISTS, MatchupGroup.STRONGLY_RESISTS, MatchupGroup.IMMUNE, MatchupGroup.STRONG_AGAINST), names(g))
        assertEquals(listOf(Type.ROCK), g[0].second)
        assertEquals(setOf(Type.WATER, Type.ELECTRIC), g[1].second.toSet())
        assertEquals(setOf(Type.GRASS, Type.BUG), g[3].second.toSet())
        assertEquals(listOf(Type.GROUND), g[4].second)
    }

    @Test fun emptyGroupsAreLeftOut() {
        // pure Water: weak to Electric/Grass (x2), resists Fire/Water/Ice/Steel (x1/2); nothing x4, x1/4 or x0
        val g = MatchupGroups.details(TypeMatchups.of(listOf(Type.WATER)))
        assertEquals(listOf(MatchupGroup.WEAK, MatchupGroup.RESISTS, MatchupGroup.STRONG_AGAINST), names(g))
    }

    @Test fun compareRowsShowTheirOwnGroups() {
        val m = TypeMatchups.of(listOf(Type.FIRE, Type.FLYING))
        assertEquals(listOf(MatchupGroup.VERY_WEAK, MatchupGroup.WEAK), names(MatchupGroups.of(m.weakTo)))
        assertEquals(listOf(MatchupGroup.RESISTS, MatchupGroup.STRONGLY_RESISTS), names(MatchupGroups.of(m.resists)))
        assertEquals(listOf(MatchupGroup.IMMUNE), names(MatchupGroups.of(m.immune)))
        assertTrue(MatchupGroups.of(emptyList()).isEmpty())
        // the Strong against group never comes out of a defender row
        assertTrue(MatchupGroup.STRONG_AGAINST !in names(MatchupGroups.of(listOf(Matchup(Type.ICE, 2.0)))))
    }

    @Test fun typeList() {
        assertEquals("", MatchupGroups.list(emptyList()))
        assertEquals("Fire", MatchupGroups.list(listOf("Fire")))
        assertEquals("Fire and Water", MatchupGroups.list(listOf("Fire", "Water")))
        assertEquals("Fire, Water and Grass", MatchupGroups.list(listOf("Fire", "Water", "Grass")))
        assertEquals("A, B, C and D", MatchupGroups.list(listOf("A", "B", "C", "D")))
    }

    @Test fun toastTexts() {
        val n = "Zorblax"
        assertEquals("Rock moves hit Zorblax 4× as hard: both its types are weak to them.", MatchupGroups.toast(MatchupGroup.VERY_WEAK, listOf(Type.ROCK), n))
        assertEquals("Water and Electric moves hit Zorblax twice as hard.", MatchupGroups.toast(MatchupGroup.WEAK, listOf(Type.WATER, Type.ELECTRIC), n))
        assertEquals("Fire, Fighting and Steel moves do only half damage to Zorblax.", MatchupGroups.toast(MatchupGroup.RESISTS, listOf(Type.FIRE, Type.FIGHTING, Type.STEEL), n))
        assertEquals("Grass and Bug moves do only a quarter of the damage to Zorblax.", MatchupGroups.toast(MatchupGroup.STRONGLY_RESISTS, listOf(Type.GRASS, Type.BUG), n))
        assertEquals("Ground moves can't hurt Zorblax at all.", MatchupGroups.toast(MatchupGroup.IMMUNE, listOf(Type.GROUND), n))
        assertEquals("Zorblax's own-type moves hit Grass and Ice Pokémon twice as hard.", MatchupGroups.toast(MatchupGroup.STRONG_AGAINST, listOf(Type.GRASS, Type.ICE), n))
    }

    @Test fun pillInkFollowsContrast() {
        assertEquals(0xFFFFFFFF.toInt(), MatchupGroups.pillInk(0xFFD20F39.toInt()))   // red: white
        assertEquals(0xFF1E1E2E.toInt(), MatchupGroups.pillInk(0xFF34D77A.toInt()))   // bright green: dark ink
        assertEquals(0xFF1E1E2E.toInt(), MatchupGroups.pillInk(0xFFC0C4D0.toInt()))   // light grey: dark ink
        assertEquals(0xFFFFFFFF.toInt(), MatchupGroups.pillInk(0xFF8C90A2.toInt()))   // the light x0 grey: white
    }

    @Test fun outlineAndBox() {
        val red = 0xFFD20F39.toInt()
        val o = MatchupGroups.outline(red, false)
        assertTrue("darker than the group colour", TypeInk.contrast(o, 0xFFFFFFFF.toInt()) > TypeInk.contrast(red, 0xFFFFFFFF.toInt()))
        assertEquals(0xFFFF4A64.toInt(), MatchupGroups.outline(0xFFFF4A64.toInt(), true))
        assertEquals(0xFFE6E9EF.toInt(), MatchupGroups.boxColor(0xFFFFFFFF.toInt(), 0xFFE6E9EF.toInt(), false))
        assertEquals(0xFF1F1F2D.toInt(), MatchupGroups.boxColor(0xFF262637.toInt(), 0xFF181825.toInt(), true))
    }

    @Test fun darkGroupColoursReadOnTheDarkBox() {
        val box = MatchupGroups.boxColor(0xFF262637.toInt(), 0xFF181825.toInt(), true)
        for (c in listOf(0xFFFF4A64, 0xFFFF6652, 0xFF31D58F, 0xFF34D77A, 0xFF8D93AB, 0xFF5C9DFF)) {
            assertTrue(Integer.toHexString(c.toInt()), TypeInk.contrast(c.toInt(), box) >= 3.0)
        }
    }
}
