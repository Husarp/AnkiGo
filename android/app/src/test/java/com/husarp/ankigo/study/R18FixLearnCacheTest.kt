package com.husarp.ankigo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18 FIX-1 (RV-9): the Prepares of one load share one learning read; a new load, or a read that failed, reads again. */
class R18FixLearnCacheTest {
    private var reads = 0
    private var fail = false
    private val cache = LearnCache {
        reads++
        PredictRule.learnRead(if (fail) null else listOf(mapOf("_id" to "5", "queue" to "1", "due" to "100", "deck_id" to "7", "original_deck_id" to "0")), 7L, reads * 1000L)
    }

    @Test fun theSameLoadReadsOnce() {
        val a = cache.get(3); val b = cache.get(3); val c = cache.get(3)
        assertEquals(1, reads)
        assertSame(a, b); assertSame(a, c)
        assertEquals(listOf(PredictRule.Due(5L, 100L)), a.due)
    }

    @Test fun aNewLoadReadsAgainAndTheOldOneIsNotCameBackTo() {
        val a = cache.get(3); val b = cache.get(4)
        assertEquals(2, reads)
        assertTrue(a !== b)
        cache.get(4); assertEquals(2, reads)
        cache.get(3); assertEquals("only the last load is kept", 3, reads)
    }

    @Test fun aFailedReadIsNotKeptSoTheNextPrepareAsksAgain() {
        fail = true
        assertNull(cache.get(3).due); assertNull(cache.get(3).due)
        assertEquals(2, reads)
        fail = false
        assertTrue(cache.get(3).due != null); cache.get(3)
        assertEquals("the first read that worked is kept", 3, reads)
    }

    @Test fun theFirstLoadWorksWhateverItsToken() {
        assertEquals(1, run { cache.get(0); reads })
        cache.get(0); assertEquals(1, reads)
    }
}
