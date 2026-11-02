package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class SchedulerCardReadTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private fun card(n: Long) = Prepared(key(n), mapOf("_id" to "$n", "reps" to "2"), "question$n")

    @Test fun orderChangeDuringPreparationNeverPublishesTheOldCard() {
        val fetched = arrayListOf<Long>()
        val notes = arrayListOf<String>()
        val result = SchedulerCardRead.confirmed(
            initial = listOf(1L, 2L), read = { listOf(2L, 1L) }, key = ::key,
            prepare = { fetched += it; card(it) }, refresh = { p, _ -> p }, note = { notes += it },
        )
        assertEquals(listOf(1L, 2L), fetched)
        assertEquals(key(2), result.card!!.key)
        assertEquals(listOf(2L, 1L), result.queue)
        assertEquals(2, result.checks)
        assertTrue(notes.first().startsWith("display withheld"))
        assertTrue(notes.last().startsWith("display confirmed"))
    }

    @Test fun vanishedCardIsNotDisplayedAndDoesNotReuseOldQueue() {
        val result = SchedulerCardRead.confirmed(
            listOf(1L), read = { emptyList<Long>() }, key = ::key, prepare = ::card, refresh = { p, _ -> p },
        )
        assertNull(result.card)
        assertTrue(result.queue.isEmpty())
    }

    @Test fun changedStateOfTheSameTopIsPreparedAgainBeforeDisplay() {
        var fetches = 0
        val result = SchedulerCardRead.confirmed(
            listOf(1L), read = { listOf(1L) }, key = ::key,
            prepare = { fetches++; card(it).copy(view = "read$fetches") }, refresh = { p, _ -> p },
            valid = { fetches >= 2 },
        )
        assertEquals(2, fetches)
        assertEquals("read2", result.card!!.view)
    }

    @Test fun buttonsAndIntervalsComeFromTheFinalSchedulerRead() {
        val result = SchedulerCardRead.confirmed(
            listOf(1L to "old"), read = { listOf(1L to "fresh") }, key = { key(it.first) },
            prepare = { card(it.first) }, refresh = { p, r -> p.copy(view = r.second) },
        )
        assertEquals("fresh", result.card!!.view)
    }

    @Test(expected = SchedulerCardRead.Changed::class)
    fun repeatedReorderingYieldsToAnAutomaticRetryInsteadOfShowingAGuess() {
        var top = 1L
        SchedulerCardRead.confirmed(
            listOf(top), read = { listOf(++top) }, key = ::key,
            prepare = ::card, refresh = { p, _ -> p }, maxChecks = 3,
        )
    }
}
