package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeMatchupsTest {
    private fun types(vararg t: Type) = TypeMatchups.of(t.toList())

    @Test fun singleType() {
        val m = types(Type.FIRE)
        assertEquals(listOf(Type.WATER, Type.GROUND, Type.ROCK), m.weakTo.map { it.type })
        assertTrue(m.weakTo.all { it.mult == 2.0 })
        assertEquals(listOf(Type.FIRE, Type.GRASS, Type.ICE, Type.BUG, Type.STEEL, Type.FAIRY), m.resists.map { it.type })
        assertTrue(m.resists.all { it.mult == 0.5 })
        assertTrue(m.immune.isEmpty())
        assertEquals(listOf(Type.GRASS, Type.ICE, Type.BUG, Type.STEEL), m.strongAgainst.map { it.type })
        assertTrue(m.strongAgainst.all { it.mult == 2.0 })
    }

    @Test fun dualTypeWithQuadrupleAndImmunity() {
        val m = types(Type.WATER, Type.GROUND)
        assertEquals(listOf(Matchup(Type.GRASS, 4.0)), m.weakTo)
        assertEquals(listOf(Matchup(Type.ELECTRIC, 0.0)), m.immune)
        assertEquals(listOf(Type.FIRE, Type.POISON, Type.ROCK, Type.STEEL), m.resists.map { it.type })
    }

    @Test fun quadrupleBeforeDoubleAndQuarterBeforeHalf() {
        val m = types(Type.STEEL, Type.BUG)
        assertEquals(Matchup(Type.FIRE, 4.0), m.weakTo.first())
        assertTrue(m.weakTo.zipWithNext().all { (a, b) -> a.mult > b.mult || (a.mult == b.mult && a.type.ordinal < b.type.ordinal) })
        assertEquals(0.25, m.resists.first().mult, 0.0)
        assertTrue(m.resists.zipWithNext().all { (a, b) -> a.mult < b.mult || (a.mult == b.mult && a.type.ordinal < b.type.ordinal) })
        assertEquals(listOf(Type.POISON), m.immune.map { it.type })
    }

    @Test fun strongAgainstOfADualTypeIsTheUnionInTypeOrder() {
        val m = types(Type.WATER, Type.GROUND)
        assertEquals(listOf(Type.FIRE, Type.ELECTRIC, Type.POISON, Type.GROUND, Type.ROCK, Type.STEEL), m.strongAgainst.map { it.type })
        assertTrue(m.strongAgainst.all { it.mult == 2.0 })
    }

    @Test fun aTypeThatHitsNothingSuperEffectivelyHasAnEmptyList() {
        assertTrue(types(Type.NORMAL).strongAgainst.isEmpty())
        assertTrue(TypeMatchups.of(emptyList()).let { it.weakTo.isEmpty() && it.resists.isEmpty() && it.immune.isEmpty() && it.strongAgainst.isEmpty() })
    }
}
