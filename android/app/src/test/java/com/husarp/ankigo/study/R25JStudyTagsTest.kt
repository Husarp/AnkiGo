package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class R25JStudyTagsTest {
    @Test fun emptyReadsAreCachedAndDecksStaySeparate() {
        val cache = StudyTags()
        assertNull(cache.get("A"))
        assertTrue(cache.put("A", emptyList(), cache.revision))
        assertEquals(emptyList<String>(), cache.get("A"))
        cache.put("B", listOf("vocabulary"), cache.revision)
        assertEquals(emptyList<String>(), cache.get("A"))
        assertEquals(listOf("vocabulary"), cache.get("B"))
    }
    @Test fun collectionChangeDiscardsOldReadsAndAllowsFreshOnes() {
        val cache = StudyTags()
        val old = cache.revision
        cache.put("A", listOf("old"), old)
        cache.invalidate()
        assertNull(cache.get("A"))
        assertFalse(cache.put("A", listOf("stale"), old))
        assertTrue(cache.put("A", listOf("new"), cache.revision))
        assertEquals(listOf("new"), cache.get("A"))
    }
    @Test fun bulkTagsDeduplicateSortAndKeepHierarchicalNames() {
        assertEquals(listOf("exam::2026", "task", "Vocabulary"), StudyTags.parse(listOf(" Vocabulary task ", "task\texam::2026\n")))
        assertEquals(emptyList<String>(), StudyTags.parse(listOf(" ", "")))
    }
    @Test fun newCardsPresetPersistsAndExcludesSuspendedAndBuried() {
        val filter = StudyFilter(listOf(DeckFilter(7, "A")), newOnly = true)
        assertEquals("((deck:\"A\" is:new -is:suspended -is:buried))", filter.search())
        assertEquals(filter, StudyFilter.decode(filter.encode()))
        assertFalse(filter.copy(newOnly = false).search().contains("is:new"))
        assertEquals("cid:0", filter.copy(decks = emptyList()).search())
    }
}
