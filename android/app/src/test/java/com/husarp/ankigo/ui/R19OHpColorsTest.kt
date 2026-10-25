package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

/** PLAN 163 / R19: exact mockup steps, continuous blend and type fallback. */
class R19OHpColorsTest {
    @Test fun stepsBothThemes() {
        for ((dark, colors) in listOf(false to listOf(0xFF2E9E3B,0xFFE8B100,0xFFD20F39), true to listOf(0xFF86D98D,0xFFF5C542,0xFFF4647F))) {
            for ((f, i) in listOf(1f to 0, .5001f to 0, .5f to 1, .2001f to 1, .2f to 2, 0f to 2))
                assertEquals(colors[i].toInt(), HpColors.color(f, HpColors.Mode.A, dark))
        }
    }
    @Test fun smoothStopsAndEveryPercent() {
        for (dark in listOf(false,true)) {
            for (f in listOf(0f,.5f,1f)) assertEquals(HpColors.color(f,HpColors.Mode.A,dark),HpColors.color(f,dark=dark))
            val orange = HpColors.color(.2f,dark=dark)
            assertTrue(((orange shr 16) and 255) > ((orange shr 8) and 255))
            assertTrue(((orange shr 8) and 255) > (orange and 255))
            assertEquals(101,(0..100).map { HpColors.color(it/100f,dark=dark) }.toSet().size)
        }
    }
    @Test fun typeAndLowHealth() {
        for (dark in listOf(false,true)) {
            assertEquals(0xFF6890F0.toInt(),HpColors.color(.21f,HpColors.Mode.C,dark,0xFF6890F0.toInt()))
            assertEquals(HpColors.color(.2f,HpColors.Mode.A,dark),HpColors.color(.2f,HpColors.Mode.C,dark,0xFF6890F0.toInt()))
            assertEquals(HpColors.color(.8f,dark=dark),HpColors.color(.8f,HpColors.Mode.C,dark))
        }
    }
    @Test fun defaultAndOldValues() {
        for (saved in listOf(null,"","old","D","0")) assertEquals(HpColors.Mode.B,HpColors.mode(saved))
        for (mode in HpColors.Mode.entries) assertEquals(mode,HpColors.mode(mode.name))
    }
    @Test fun clamp() {
        assertEquals(HpColors.color(0f),HpColors.color(-1f))
        assertEquals(HpColors.color(1f),HpColors.color(2f))
        assertEquals(HpColors.color(0f),HpColors.color(Float.NaN))
    }
}
