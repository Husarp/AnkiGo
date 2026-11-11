package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class LiveRefreshTest {
    private class Rig {
        var now = 100_000L
        var pending: Pair<Long, () -> Unit>? = null
        var blocked = false
        val shown = mutableListOf<Int>()
        val reads = mutableListOf<(Int?, Long?) -> Unit>()
        val live = LiveRefresh<Int>({ now }, { ms, f -> pending = now + ms to f }, { pending = null }, { blocked }, { reads += it }, { shown += it })
        fun run() { val (at, f) = pending!!; pending = null; now = at; f() }
        fun finish(n: Int?, due: Long? = null) { reads.removeAt(0)(n, due) }
    }

    @Test fun answersRefreshWithoutWaitingForNextCardAndLearningWakesIdleScreenAtDueTime() {
        val r = Rig(); r.live.start(); r.run(); r.finish(15, 103_000)
        assertEquals(listOf(15), r.shown)
        assertEquals(103_000L, r.pending!!.first)
        r.now = 101_000; r.live.invalidate(); r.run(); r.finish(14, 103_000)
        assertEquals(listOf(15, 14), r.shown)
        r.run(); assertEquals(103_000L, r.now); r.finish(16)
        assertEquals(listOf(15, 14, 16), r.shown)
        assertEquals(133_000L, r.pending!!.first)
    }

    @Test fun rapidPreparedAnswersNeverPublishStaleCountsOrQueueParallelReadsAndWaitForWrites() {
        val r = Rig(); r.live.start(); r.run()
        repeat(20) { r.live.invalidate() }
        assertEquals(1, r.reads.size)
        r.blocked = true; r.finish(15)
        assertTrue(r.shown.isEmpty()); r.run(); assertTrue(r.reads.isEmpty())
        r.blocked = false; r.run(); r.finish(5)
        assertEquals(listOf(5), r.shown)
    }

    @Test fun slowCountReadCrossingALearningDeadlineImmediatelyReadsAgain() {
        val r = Rig(); r.live.start(); r.run()
        val startedAt = r.now
        r.now = 104_000
        r.finish(15, LiveRefresh.nextLearning(listOf(mapOf("queue" to "1", "due" to "103")), startedAt))
        assertEquals(104_001L, r.pending!!.first)
        r.run(); r.finish(16)
        assertEquals(listOf(15, 16), r.shown)
    }

    @Test fun backgroundRetiresResultAndResumeRefreshesWithoutAnotherAnswer() {
        val r = Rig(); r.live.start(); r.run(); r.live.stop(); r.finish(15)
        assertTrue(r.shown.isEmpty()); assertNull(r.pending)
        r.now += 60_000; r.live.start(); r.run(); r.finish(30)
        assertEquals(listOf(30), r.shown)
        r.live.stop(); assertNull(r.pending)
    }

    @Test fun returnWhileOldReadStillRunsCoalescesIntoOneFreshReadAndFailedReadRetries() {
        val r = Rig(); r.live.start(); r.run(); r.live.stop(); r.live.start()
        assertNull(r.pending); r.finish(15); r.run(); r.finish(null)
        assertTrue(r.shown.isEmpty()); assertEquals(r.now + 30_000, r.pending!!.first)
        r.run(); r.finish(16); assertEquals(listOf(16), r.shown)
    }

    @Test fun learningDeadlinesIgnoreDayNumbersSuspendedMalformedAndPastRows() {
        fun row(queue: String, due: String) = mapOf("queue" to queue, "due" to due)
        assertEquals(102_000L, LiveRefresh.nextLearning(listOf(row("1", "100"), row("1", "103"), row("4", "102"), row("3", "101"), row("-1", "101"), row("1", "bad")), 100_000))
        assertNull(LiveRefresh.nextLearning(listOf(row("2", "101"), row("1", "9223372036854775807")), 100_000))
    }
}
