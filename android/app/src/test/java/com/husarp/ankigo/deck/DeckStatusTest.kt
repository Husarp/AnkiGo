package com.husarp.ankigo.deck

import com.husarp.ankigo.stats.AnkiQueries
import org.junit.Assert.*
import org.junit.Test

class DeckStatusTest {
    private fun read(values: Map<String, Int> = emptyMap(), queries: MutableList<String> = mutableListOf()) =
        DeckStatus.read("Geo", true) { q -> queries.add(q); values[q.removePrefix("deck:\"Geo\" ")] ?: 0 }

    @Test fun fourStatesCombineRelearningAndUseActiveDenominator() {
        val s = read(mapOf(AnkiQueries.NEW to 22, AnkiQueries.LEARNING to 7,
            AnkiQueries.RELEARNING to 4, AnkiQueries.YOUNG to 44, AnkiQueries.MATURE to 143,
            AnkiQueries.SUSPENDED to 4, AnkiQueries.BURIED to 2))
        assertEquals(listOf(22, 11, 44, 143), s.states)
        assertEquals(220, s.active); assertEquals(65, s.known)
        assertEquals(4, s.suspended); assertEquals(2, s.buried)
    }
    @Test fun emptyDeckHasNoPercentage() { assertNull(read().known); assertEquals(0, read().streak) }
    @Test fun quotesWildcardsAndBackslashesAreLiteralInDeckName() {
        assertEquals("deck:\"A\\\"\\*\\_\\\\B\"", DeckStatus.scope("A\"*_\\B", true))
    }
    @Test fun descendantsCanBeExcludedWithoutExcludingParent() {
        assertEquals("deck:\"Geo::Asia\" -deck:\"Geo::Asia::*\"", DeckStatus.scope("Geo::Asia", false))
        val queries = mutableListOf<String>()
        DeckStatus.read("Geo", false) { queries.add(it); 0 }
        assertTrue(queries.all { it.startsWith("deck:\"Geo\" -deck:\"Geo::*\" ") })
    }
    @Test fun dueAndIntroducedExcludeSuspendedAndBuried() {
        val live = " -is:suspended -is:buried"
        val s = read(mapOf("is:due$live" to 25, "prop:due=1$live" to 18, "introduced:7$live" to 12))
        assertEquals(25, s.due); assertEquals(18, s.tomorrow); assertEquals(12, s.introduced)
    }
    @Test fun streakIncludesTodayAndStopsAtFirstGap() {
        val queries = mutableListOf<String>()
        val s = read(mapOf("prop:rated=0" to 5, "prop:rated=-1" to 2, "prop:rated=-2" to 1,
            "prop:rated=-4" to 3), queries)
        assertEquals(3, s.streak); assertTrue(s.todayIncluded)
        assertFalse(queries.any { it.endsWith("prop:rated=-4") })
    }
    @Test fun yesterdayStreakSurvivesUntilStudyToday() {
        val s = read(mapOf("prop:rated=-1" to 1, "prop:rated=-2" to 1))
        assertEquals(2, s.streak); assertFalse(s.todayIncluded)
    }
    @Test fun streakIsBounded() {
        val queries = mutableListOf<String>()
        val s = DeckStatus.read("Geo", true) { queries.add(it); 1 }
        assertEquals(365, s.streak)
        assertEquals(365, queries.count { "prop:rated=" in it })
    }
    @Test fun providerErrorsPropagateRatherThanBecomeEmptyDecks() {
        assertThrows(SecurityException::class.java) { DeckStatus.read("Geo", true) { throw SecurityException() } }
        assertThrows(IllegalArgumentException::class.java) { DeckStatus.read("Geo", true) { throw IllegalArgumentException() } }
    }
}
