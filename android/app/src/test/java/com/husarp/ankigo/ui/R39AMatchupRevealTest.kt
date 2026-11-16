package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R39AMatchupRevealTest {
    @Test fun steelHitsRevealOnlyTheDefendersSectionInBothDirections() {
        // Ice is weak to Steel but its own attacks are also strong against Steel's other targets.
        for (types in listOf(listOf(Type.ICE), listOf(Type.ICE, Type.ROCK), listOf(Type.FIRE),
            listOf(Type.FIRE, Type.STEEL), listOf(Type.WATER), listOf(Type.FIGHTING))) {
            val data = TestData.data(listOf(TestData.species(16, types = types), TestData.species(25, types = types)))
            for (byPlayer in listOf(true, false)) {
                val s = TestData.state(data)
                val b = Battle(TestData.mon(data, 16, 20, uid = "wild"), Tier.NORMAL)
                val event = GameEvent.Attack(byPlayer, "User", "Target", "Steel hit", Type.STEEL, Category.PHYSICAL,
                    5, 1, TypeChart.eff(Type.STEEL, types), false, false, false, 10, 20, 10, 20)
                MatchupKnowledge.observe(data, s, b, s.main!!, listOf(event))
                val target = if (byPlayer) 16 else 25
                val other = if (byPlayer) 25 else 16
                val known = MatchupKnowledge.of(data, s, target)
                val sections = MatchupGroups.sections(types, known)
                val defending = sections.filterKeys { it != "strong" }.values.flatMap { it.entries }
                assertEquals(listOf(Matchup(Type.STEEL, TypeChart.eff(Type.STEEL, types))), defending)
                assertTrue(sections.getValue("strong").entries.isEmpty())
                assertTrue(MatchupKnowledge.seen(data, s, other).isEmpty())
            }
        }
    }

    @Test fun aTypeThatIsBothDefensiveAndOffensiveNeverLeaksIntoStrongAgainst() {
        // Fighting both resists Rock and hits Rock super effectively: the old section helper revealed both.
        val types = listOf(Type.FIGHTING)
        val sections = MatchupGroups.sections(types, TypeMatchups.discovered(types, setOf(Type.ROCK)))
        assertEquals(listOf(Matchup(Type.ROCK, 0.5)), sections.getValue("resist").entries)
        assertTrue(sections.getValue("strong").entries.isEmpty())
        assertTrue(sections.getValue("strong").hidden.any { it.type == Type.ROCK })
    }

    @Test fun allDefensiveDiscoveriesStillDoNotRevealOffensiveEntriesButExplicitFullDataDoes() {
        val types = listOf(Type.FIGHTING)
        val defensive = MatchupGroups.sections(types, TypeMatchups.discovered(types, Type.entries.toSet()))
        assertTrue(defensive.getValue("strong").entries.isEmpty())
        val full = MatchupGroups.sections(types, TypeMatchups.of(types))
        assertEquals(TypeMatchups.of(types).strongAgainst, full.getValue("strong").entries)
    }

    @Test fun bondStillRevealsTheFullAttackingListIndependentlyOfHits() {
        val data = TestData.data(listOf(TestData.species(25, types = listOf(Type.FIGHTING))))
        val s = TestData.state(data)
        s.seenMatchups[25] = Type.entries.toMutableSet()
        assertTrue(MatchupGroups.sections(data.sp(25).types, MatchupKnowledge.of(data, s, 25)).getValue("strong").entries.isEmpty())
        MatchupKnowledge.reveal(data, s, s.main!!, mutableListOf())
        assertEquals(TypeMatchups.of(data.sp(25).types).strongAgainst,
            MatchupGroups.sections(data.sp(25).types, MatchupKnowledge.of(data, s, 25)).getValue("strong").entries)
    }
}
