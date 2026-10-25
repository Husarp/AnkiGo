package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19-D (PLAN 250, 251, 252): the Seen owner filter, the "too many types" notice rule, the back-to-top arrow rule. */
class R19DDexTest {
    private val all = listOf(
        TestData.species(1, "Bulba", listOf(Type.GRASS, Type.POISON)), // owned
        TestData.species(4, "Char", listOf(Type.FIRE)),                // seen, never caught
        TestData.species(7, "Squirt", listOf(Type.WATER)),             // caught once, released
        TestData.species(25, "Pika", listOf(Type.ELECTRIC)),           // owned
        TestData.species(133, "Eevee", listOf(Type.NORMAL)),           // seen, never caught
        TestData.species(150, "Mewtwo", listOf(Type.PSYCHIC)),         // never met
    )
    private val owned = mapOf(1 to 100L, 25 to 300L)
    private val seen = setOf(4, 133)
    private val caughtEver = setOf(1, 7, 25)
    private val seenOnly = DexFilter.seenNotCaught(seen + caughtEver, caughtEver)
    private val gens = setOf(1)

    private fun run(f: DexFilter) = f.apply(all, owned, emptySet(), gens, seenOnly = seenOnly).map { it.num }

    // ---- 252

    @Test fun seenIsSeenAndNeverCaughtOnly() {
        assertEquals(setOf(4, 133), seenOnly)
        assertEquals(listOf(4, 133), run(DexFilter(owned = DexOwned.SEEN)))
    }

    @Test fun aCaughtThenReleasedSpeciesIsNotSeenOnlyButItIsNotOwned() {
        // the Pokédex draws it in colour (it was caught), so "Seen" (the grey ones) leaves it out; "Not owned" has it
        assertFalse(7 in run(DexFilter(owned = DexOwned.SEEN)))
        assertTrue(7 in run(DexFilter(owned = DexOwned.NOT_OWNED)))
    }

    @Test fun notOwnedStillMeansNotOwnedNowIncludingNeverSeen() {
        assertEquals(listOf(4, 7, 133, 150), run(DexFilter(owned = DexOwned.NOT_OWNED)))
        assertEquals(listOf(1, 25), run(DexFilter(owned = DexOwned.OWNED)))
        assertEquals(listOf(1, 4, 7, 25, 133, 150), run(DexFilter()))
    }

    @Test fun seenCombinesWithTheOtherFilters() {
        assertEquals(listOf(133), run(DexFilter(owned = DexOwned.SEEN, types = setOf(Type.NORMAL))))
        assertEquals(listOf(4), run(DexFilter(owned = DexOwned.SEEN, query = "char")))
        assertTrue(DexFilter(owned = DexOwned.SEEN).active)
    }

    @Test fun theSwitchOrderIsAllOwnedSeenNotOwned() {
        assertEquals(listOf(DexOwned.ALL, DexOwned.OWNED, DexOwned.SEEN, DexOwned.NOT_OWNED), DexOwned.entries)
    }

    @Test fun seenIsKeptAndOldSavedValuesLoad() {
        val f = DexFilter(owned = DexOwned.SEEN, gens = setOf(1, 2), wishlistOnly = true)
        assertEquals("SEEN;1,2;1;0", SortPrefs.encodeDex(f))
        assertEquals(DexOwned.SEEN, SortPrefs.decodeDex(SortPrefs.encodeDex(f)).owned)
        // values saved before R19 still load; unknown / garbled ones fall back to All
        for (old in DexOwned.entries.filter { it != DexOwned.SEEN }) {
            assertEquals(old, SortPrefs.decodeDex("${old.name};;0;0").owned)
        }
        assertEquals(DexOwned.OWNED, SortPrefs.decodeDex("OWNED;1;1;1").owned)
        assertEquals(DexOwned.NOT_OWNED, SortPrefs.decodeDex("NOT_OWNED;;0;0").owned)
        assertEquals(DexOwned.ALL, SortPrefs.decodeDex("SEEN_ONLY;;0;0").owned)
        assertEquals(DexOwned.ALL, SortPrefs.decodeDex(null).owned)
    }

    @Test fun theFilterTextsKnowSeenOnly() {
        assertEquals("No seen-only Pokémon.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.SEEN)))
        assertEquals("No seen-only Pokémon in Gen 1.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.SEEN, gens = setOf(1))))
        assertEquals("No unowned Pokémon.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.NOT_OWNED)))
        assertEquals("No owned Pokémon.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.OWNED)))
    }

    // ---- 251

    @Test fun aThirdTypeIsRefusedOnlyInAllOf() {
        val two = TypeFilter(linkedSetOf(Type.FIRE, Type.WATER), TypeMatch.ALL)
        assertNull(two.toggle(Type.ICE)) // refused: the sheet shows the notice
        assertEquals(setOf(Type.FIRE), two.toggle(Type.WATER)!!.types) // taking one off is fine
        for (m in listOf(TypeMatch.ANY, TypeMatch.ONLY)) {
            val three = TypeFilter(linkedSetOf(Type.FIRE, Type.WATER), m).toggle(Type.ICE)!!
            assertEquals(3, three.types.size) // valid, no notice
        }
    }

    @Test fun switchingToAllOfWithThreeOrMorePicksDropsToTheLastTwoAndSaysSo() {
        val three = TypeFilter(linkedSetOf(Type.FIRE, Type.WATER, Type.ICE), TypeMatch.ANY)
        assertTrue(three.dropsPicks(TypeMatch.ALL))
        assertEquals(listOf(Type.WATER, Type.ICE), three.withMatch(TypeMatch.ALL).types.toList())
        assertFalse(three.dropsPicks(TypeMatch.ONLY))
        assertFalse(three.dropsPicks(TypeMatch.ANY))
        assertFalse(TypeFilter(linkedSetOf(Type.FIRE, Type.WATER), TypeMatch.ANY).dropsPicks(TypeMatch.ALL))
        assertFalse(TypeFilter(linkedSetOf(Type.FIRE), TypeMatch.ANY).dropsPicks(TypeMatch.ALL))
    }

    @Test fun theNoticeSaysWhy() {
        assertEquals("A Pokémon has at most 2 types, so All of can't match 3.", TypeFilter.TOO_MANY)
    }

    // ---- 250

    @Test fun theArrowShowsAfterAboutOneScreenAndHidesAtTheTop() {
        val vp = 1000
        assertFalse(BackToTopRules.show(false, 0, vp))
        assertFalse(BackToTopRules.show(false, 999, vp))
        assertTrue(BackToTopRules.show(false, 1000, vp))
        assertTrue(BackToTopRules.show(false, 5000, vp))
        // once shown it stays until the list is back near the top, then it hides
        assertTrue(BackToTopRules.show(true, 600, vp))
        assertTrue(BackToTopRules.show(true, 251, vp))
        assertFalse(BackToTopRules.show(true, 250, vp))
        assertFalse(BackToTopRules.show(true, 0, vp))
        assertFalse("no list height yet: hidden", BackToTopRules.show(true, 5000, 0))
    }

    @Test fun theScrolledDistanceCountsTheHeaderAndTheRows() {
        assertEquals(0, BackToTopRules.offset(0, 0, 900, 900))
        assertEquals(300, BackToTopRules.offset(0, -300, 900, 900))
        // first visible = row 1 (just after the header) at its top: the header is fully scrolled away
        assertEquals(900, BackToTopRules.offset(1, 0, 200, 900))
        assertEquals(900 + 5 * 200 + 40, BackToTopRules.offset(6, -40, 200, 900))
        assertNotEquals(0, BackToTopRules.offset(1, -10, 200, 900))
    }
}
