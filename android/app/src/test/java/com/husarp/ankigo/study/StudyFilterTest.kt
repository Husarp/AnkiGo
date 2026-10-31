package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StudyFilterTest {
    @Test fun emptySelectionNeverMatchesCollection() { assertEquals("cid:0", StudyFilter().search()) }
    @Test fun scheduledSearchKeepsDeckAndTagsGrouped() {
        val f = StudyFilter(listOf(DeckFilter(1, "Biology")), setOf("task", "vocabulary"))
        assertEquals("((deck:\"Biology\" is:due -is:suspended -is:buried (tag:\"task\" or tag:\"vocabulary\")))", f.search())
    }
    @Test fun freeStudyIncludesAllMatchingCardsAndCanIgnoreGlobalTags() {
        val f = StudyFilter(listOf(DeckFilter(1, "A", setOf("task"), true, true), DeckFilter(2, "B")), setOf("vocabulary"))
        assertEquals("((deck:\"A\" (tag:\"task\")) or (deck:\"B\" is:due -is:suspended -is:buried (tag:\"vocabulary\")))", f.search())
    }
    @Test fun queryQuotesSearchSyntaxInsteadOfInjectingIt() {
        assertEquals("\"" + "a\\\" or deck:" + "\\*" + "\\\\" + "\\_" + "\\*" + "\"", StudyFilter.quote("a\" or deck:*\\_*"))
    }
    @Test fun globalAndOwnTagsBothNarrowDeck() {
        val search = StudyFilter(listOf(DeckFilter(1, "D", setOf("own"))), setOf("global")).search()
        assertTrue(search.contains("(tag:\"global\") (tag:\"own\")"))
    }
    @Test fun deckTagsComeOnlyFromItsDistinctNotes() {
        val reads = mutableListOf<Long>()
        val tags = StudyFilter.deckTags(listOf(1, 1, 2)) { id -> reads += id; mapOf(1L to " vocabulary task ", 2L to " task exam::2026 ")[id] }
        assertEquals(listOf(1L, 2L), reads)
        assertEquals(listOf("exam::2026", "task", "vocabulary"), tags)
    }
    @Test fun deckTagListsDoNotLeakBetweenDecks() {
        val notes = mapOf(1L to "task", 2L to "vocabulary")
        assertEquals(listOf("task"), StudyFilter.deckTags(listOf(1)) { notes[it] })
        assertEquals(listOf("vocabulary"), StudyFilter.deckTags(listOf(2)) { notes[it] })
    }
    @Test fun emptyDeckTagsAreEmpty() { assertTrue(StudyFilter.deckTags(listOf(1)) { "  " }.isEmpty()) }
    @Test fun recentListDeduplicatesAndKeepsLastTen() {
        var l = FilterLibrary()
        repeat(12) { i -> l = l.used("$i", StudyFilter(listOf(DeckFilter(i.toLong(), "$i")))) }
        assertEquals(10, l.recent.size); assertEquals("11", l.recent.first().name); assertEquals("2", l.recent.last().name)
        l = l.used("again", l.recent.last().filter)
        assertEquals(10, l.recent.size); assertEquals("again", l.recent.first().name)
    }
    @Test fun editingLoadedStarCreatesNewRecentWithoutMutatingFrozenOriginal() {
        val original = StudyFilter(listOf(DeckFilter(1, "A")), setOf("task"))
        var l = FilterLibrary().star("Exam", original)
        val edited = l.starred.first().filter.copy(tags = setOf("vocabulary"))
        l = l.used("Exam edited", edited)
        val restored = FilterLibrary.decode(l.encode())
        assertEquals(original, restored.starred.first().filter)
        assertEquals(edited, restored.recent.first().filter)
        assertEquals(original, StudyFilter.decode(original.encode()))
    }
    @Test fun starsAreNotEvictedAndCanBeExplicitlyRemoved() {
        var l = FilterLibrary().star("Star", StudyFilter())
        repeat(20) { l = l.used("$it", StudyFilter(listOf(DeckFilter(it.toLong(), "$it")))) }
        assertEquals(1, l.starred.size)
        assertEquals(1, l.star("Duplicate", StudyFilter()).starred.size)
        assertTrue(l.unstar(0).starred.isEmpty())
    }
    @Test fun damagedLibraryFallsBackSafely() { assertTrue(FilterLibrary.decode("broken").recent.isEmpty()) }
    @Test fun localAnswersNeverInvokeProviderAndDueAnswersDo() {
        var api = 0; var local = 0
        repeat(4) { FilterAnswer.route(false, { api++ }, { local++ }) }
        assertEquals(0, api); assertEquals(4, local)
        FilterAnswer.route(true, { api++ }, { local++ })
        assertEquals(1, api); assertEquals(4, local)
    }
    @Test fun advancingLargeSessionOnlyWritesTheCursor() {
        val file = File.createTempFile("filter-session", ".json")
        try {
            val session = FilterSession((1..3000).map { mapOf("_id" to "$it") })
            session.save(file)
            val original = file.readText()
            session.position = 123; session.saveProgress(file)
            assertEquals(original, file.readText())
            assertEquals("123", File(file.path + ".position").readText())
            assertEquals(123, FilterSession.read(file).position)
        } finally { file.delete(); File(file.path + ".position").delete() }
    }
    @Test fun sessionPersistsConsumedPositionAndAllCardIds() {
        val file = File.createTempFile("filter-session", ".json")
        try {
            val session = FilterSession(listOf(mapOf("_id" to "123", "note_id" to "42", "ord" to "2", "deck_id" to "8")))
            session.position++; session.save(file)
            val loaded = FilterSession.decode(file.readText())
            assertEquals(1, loaded.position); assertEquals(session.cards, loaded.cards)
        } finally { file.delete() }
    }
}
