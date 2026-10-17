package com.husarp.ankigo.ui

import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropRulesTest {
    private val all = Type.entries.flatMap { BackdropRules.options(it) }

    @Test fun everyTypeHasAtLeastOnePicture() {
        for (t in Type.entries) assertTrue("$t", BackdropRules.options(t).isNotEmpty())
    }

    @Test fun onlyIceAndDarkHaveASinglePicture() {
        for (t in Type.entries) {
            val n = BackdropRules.options(t).size
            if (t == Type.ICE || t == Type.DARK) assertEquals(1, n) else assertTrue("$t has $n", n >= 2)
        }
        assertEquals(listOf("icecave"), BackdropRules.options(Type.ICE).map { it.name })
    }

    @Test fun allFourteenPicturesAreUsedAndNoOtherName() {
        assertEquals(14, Backdrops.NAMES.size)
        assertEquals(Backdrops.NAMES.toSet(), all.map { it.name }.toSet())
    }

    @Test fun theTypeListsAreTheOnesFromThePlan() {
        fun names(t: Type) = BackdropRules.options(t).map { it.name + if (it.night) "-night" else "" }
        assertEquals(listOf("meadow", "route"), names(Type.NORMAL))
        assertEquals(listOf("volcanocave", "desert"), names(Type.FIRE))
        assertEquals(listOf("beach", "river", "beachshore", "deepsea"), names(Type.WATER))
        assertEquals(listOf("forest", "meadow"), names(Type.GRASS))
        assertEquals(listOf("city", "route"), names(Type.ELECTRIC))
        assertEquals(listOf("mountain", "route"), names(Type.FIGHTING))
        assertEquals(listOf("dampcave", "forest"), names(Type.POISON))
        assertEquals(listOf("desert", "earthycave"), names(Type.GROUND))
        assertEquals(listOf("mountain", "meadow"), names(Type.FLYING))
        assertEquals(listOf("city", "forest"), names(Type.PSYCHIC))
        assertEquals(listOf("forest", "meadow"), names(Type.BUG))
        assertEquals(listOf("mountain", "earthycave"), names(Type.ROCK))
        assertEquals(listOf("forest-night", "dampcave"), names(Type.GHOST))
        assertEquals(listOf("mountain", "volcanocave"), names(Type.DRAGON))
        assertEquals(listOf("forest-night"), names(Type.DARK))
        assertEquals(listOf("city", "earthycave"), names(Type.STEEL))
        assertEquals(listOf("meadow", "forest"), names(Type.FAIRY))
    }

    @Test fun nightIsOnlyGhostAndDarkForest() {
        val nights = Type.entries.flatMap { t -> BackdropRules.options(t).filter { it.night }.map { t to it.name } }
        assertEquals(setOf(Type.GHOST to "forest", Type.DARK to "forest"), nights.toSet())
        assertTrue(nights.all { it.second == "forest" })
    }

    @Test fun theFirstTypeCountsUnlessItIsNormal() {
        assertEquals(Type.FIRE, BackdropRules.typeFor(listOf(Type.FIRE, Type.FLYING)))
        assertEquals(Type.FLYING, BackdropRules.typeFor(listOf(Type.NORMAL, Type.FLYING)))
        assertEquals(Type.NORMAL, BackdropRules.typeFor(listOf(Type.NORMAL)))
        assertEquals(Type.WATER, BackdropRules.typeFor(listOf(Type.WATER)))
    }

    @Test fun noTypesIsNormal() {
        assertEquals(Type.NORMAL, BackdropRules.typeFor(emptyList()))
        assertEquals(BackdropRules.options(Type.NORMAL)[0], BackdropRules.pick(emptyList(), 0))
    }

    @Test fun theSpeciesNumberPicksModuloTheOptions() {
        val water = BackdropRules.options(Type.WATER)
        for (n in 0..11) assertEquals(water[n % 4], BackdropRules.pick(listOf(Type.WATER), n))
        val fire = BackdropRules.options(Type.FIRE)
        assertEquals(fire[0], BackdropRules.pick(listOf(Type.FIRE), 4))
        assertEquals(fire[1], BackdropRules.pick(listOf(Type.FIRE), 5))
        assertEquals(BackdropRules.options(Type.ICE)[0], BackdropRules.pick(listOf(Type.ICE), 777))
    }

    @Test fun theSameSpeciesAlwaysGetsTheSamePicture() {
        for (t in Type.entries) for (n in 1..60) {
            assertEquals(BackdropRules.pick(listOf(t), n), BackdropRules.pick(listOf(t), n))
            assertEquals(BackdropRules.pick(listOf(t, Type.FLYING), n), BackdropRules.pick(listOf(t, Type.FLYING), n))
        }
    }

    @Test fun differentSpeciesOfATypeSpreadOverItsPictures() {
        for (t in Type.entries) {
            val picked = (1..50).map { BackdropRules.pick(listOf(t), it) }.toSet()
            assertEquals("$t", BackdropRules.options(t).toSet(), picked)
        }
    }

    @Test fun aNegativeNumberStillPicksAnOption() {
        val c = BackdropRules.pick(listOf(Type.WATER), -3)
        assertTrue(c in BackdropRules.options(Type.WATER))
    }

    @Test fun dayPicturesAreDrawnAsTheyAreInTheLightTheme() {
        assertEquals(null, BackdropTint.matrix(night = false, dark = false))
    }

    @Test fun theDarkThemeGetsTheMockupsDuskMultiply() {
        // multiply rgba(34,38,96,.42): 1 - .42 + .42 * c / 255
        val s = BackdropTint.scale(night = false, dark = true)
        assertEquals(1f - .42f + .42f * 34f / 255f, s[0], 0.005f)
        assertEquals(1f - .42f + .42f * 38f / 255f, s[1], 0.005f)
        assertEquals(1f - .42f + .42f * 96f / 255f, s[2], 0.005f)
    }

    @Test fun nightIsDarkerAndBluerThanDayAndThanDusk() {
        for (dark in listOf(false, true)) {
            val day = BackdropTint.scale(false, dark)
            val night = BackdropTint.scale(true, dark)
            for (i in 0..2) assertTrue("channel $i dark=$dark", night[i] < day[i])
            // bluer: the blue channel keeps the biggest share
            assertTrue(night[2] / day[2] > night[0] / day[0])
            assertTrue(night[2] > night[0] && night[2] > night[1])
        }
        val nightDark = BackdropTint.scale(true, true)
        val nightLight = BackdropTint.scale(true, false)
        for (i in 0..2) assertTrue(nightDark[i] < nightLight[i])
    }

    @Test fun theMatrixKeepsAlphaAndHasNoOffset() {
        for ((n, d) in listOf(true to false, false to true, true to true)) {
            val m = BackdropTint.matrix(n, d)!!
            assertEquals(20, m.size)
            assertEquals(1f, m[18], 0f)
            for (i in listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 19)) assertEquals(0f, m[i], 0f)
            assertEquals(BackdropTint.scale(n, d)[0], m[0], 0f)
            assertEquals(BackdropTint.scale(n, d)[1], m[6], 0f)
            assertEquals(BackdropTint.scale(n, d)[2], m[12], 0f)
        }
        assertNotEquals(BackdropTint.matrix(true, false)!!.toList(), BackdropTint.matrix(false, true)!!.toList())
        assertFalse(BackdropTint.matrix(true, true)!!.any { it < 0f })
    }
}
