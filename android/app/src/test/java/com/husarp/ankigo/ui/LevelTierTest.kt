package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelTierTest {
    @Test fun boundaries() {
        assertEquals("bronze", LevelTier.name(1)); assertEquals("bronze", LevelTier.name(9))
        assertEquals("silver", LevelTier.name(10)); assertEquals("silver", LevelTier.name(19))
        assertEquals("gold", LevelTier.name(20)); assertEquals("emerald", LevelTier.name(30))
        assertEquals("sapphire", LevelTier.name(40)); assertEquals("ruby", LevelTier.name(50))
        assertEquals("amethyst", LevelTier.name(60)); assertEquals("platinum", LevelTier.name(79))
        assertEquals("diamond", LevelTier.name(89)); assertEquals("master", LevelTier.name(90))
        assertEquals("master", LevelTier.name(99)); assertEquals("master", LevelTier.name(100))
        assertEquals(9, LevelTier.index(100)); assertEquals(0, LevelTier.index(1))
    }
    @Test fun outOfRangeIsClamped() {
        assertEquals(0, LevelTier.index(0)); assertEquals(9, LevelTier.index(150))
    }
    @Test fun labelAlwaysHasPrefix() {
        assertEquals("Lv 5", LevelTier.label(5)); assertEquals("Lv 100", LevelTier.label(100))
    }
}
