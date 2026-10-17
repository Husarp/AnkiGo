package com.husarp.ankigo.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

// TICKET R3-04: live answer timing, live-time.csv reading and writing.
class LiveTimerTest {
    @Test fun firstAnswerIsTimedFromTheWatchStart() {
        val t = LiveTimer()
        t.start(1_000)
        assertEquals(12_000L, t.onAnswer(false, 13_000))
    }

    @Test fun nextAnswersAreTimedFromThePreviousOne() {
        val t = LiveTimer()
        t.start(0)
        assertEquals(5_000L, t.onAnswer(false, 5_000))
        assertEquals(8_000L, t.onAnswer(false, 13_000))
        assertEquals(0L, t.onAnswer(false, 13_000))
    }

    @Test fun aLongGapCountsAtMostTheCap() {
        val t = LiveTimer()
        t.start(0)
        assertEquals(LIVE_ANSWER_CAP_MS, t.onAnswer(false, 10 * 60_000))
        assertEquals(60_000L, LIVE_ANSWER_CAP_MS)
        assertEquals(LIVE_ANSWER_CAP_MS, t.onAnswer(false, 10 * 60_000 + 60_001))
        assertEquals(60_000L, t.onAnswer(false, 10 * 60_000 + 120_001))
    }

    @Test fun pausingOrStoppingResetsTheSession() {
        val t = LiveTimer()
        t.start(0)
        t.onAnswer(false, 4_000)
        t.stop()
        assertNull(t.onAnswer(false, 90_000)) // no watch: nothing to write
        t.start(100_000)                      // resumed
        assertEquals(3_000L, t.onAnswer(false, 103_000)) // not 99 s since the last answer
    }

    @Test fun catchUpAnswersAreNeverWrittenAndLeaveTheMark() {
        val t = LiveTimer()
        t.start(0)
        assertNull(t.onAnswer(true, 1_000))
        assertNull(t.onAnswer(true, 2_000))
        assertEquals(7_000L, t.onAnswer(false, 7_000)) // still timed from the watch start
    }

    @Test fun anOwnAnswerMovesTheMarkSoItsTimeIsNotCountedTwice() {
        val t = LiveTimer()
        t.start(0)
        t.touch(50_000)
        assertEquals(4_000L, t.onAnswer(false, 54_000))
        val stopped = LiveTimer()
        stopped.touch(5_000) // no watch: still nothing
        assertNull(stopped.onAnswer(false, 6_000))
    }

    @Test fun aClockThatWentBackwardsCountsZero() {
        val t = LiveTimer()
        t.start(10_000)
        assertEquals(0L, t.onAnswer(false, 9_000))
    }

    // ---- the file ----

    private fun key(ms: Long) = AnkiGoTime.dayKey(ms, 4, java.time.ZoneOffset.UTC)
    private fun at(h: Int, day: Int = 14) = java.time.ZonedDateTime.of(2026, 10, day, h, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun readerSumsPerGameDayWithTheDayStartHour() {
        val lines = sequenceOf("${at(10)},5000", "${at(11)},7000", "${at(2, 15)},1000", "${at(5, 15)},2000")
        val m = timeLive(lines, ::key)
        assertEquals(13_000L, m["2026-10-14"]) // 02:00 on the 15th still belongs to the 14th
        assertEquals(2_000L, m["2026-10-15"])
    }

    @Test fun readerSkipsBadLinesAndCapsAnswers() {
        val lines = sequenceOf("", "garbage", "${at(10)}", "${at(10)},abc", "x,1000", "0,500", "${at(10)},-5", "${at(10)},1,2", "${at(10)},900000", "${at(10)},40")
        val m = timeLive(lines, ::key)
        assertEquals(mapOf("2026-10-14" to (LIVE_ANSWER_CAP_MS + 40)), m)
    }

    @Test fun readerSkipsAHalfWrittenLastLine() {
        assertEquals(mapOf("2026-10-14" to 3000L), timeLive(sequenceOf("${at(10)},3000", "${at(11)},"), ::key))
    }

    @Test fun appendWritesLinesTheReaderUnderstands() {
        val dir = Files.createTempDirectory("livetime").toFile()
        try {
            val latch = java.util.concurrent.CountDownLatch(2)
            LiveTimeFile.appendAsync(dir, at(10), 5_000) { latch.countDown() }
            LiveTimeFile.appendAsync(dir, at(11), 2_500) { latch.countDown() }
            val f = LiveTimeFile.file(dir)
            val until = System.currentTimeMillis() + 5_000
            while ((!f.exists() || f.readLines().size < 2) && System.currentTimeMillis() < until) Thread.sleep(10)
            assertEquals(listOf("${at(10)},5000", "${at(11)},2500"), f.readLines())
            assertEquals(7_500L, timeLive(f.readLines().asSequence(), ::key)["2026-10-14"])
            assertEquals(2L, latch.count) // no error callback ran
        } finally { dir.deleteRecursively() }
    }

    @Test fun aWriteFailureGoesToTheCallbackNotTheCaller() {
        val dir = File(Files.createTempDirectory("livetime").toFile(), "missing/deeper")
        val failed = java.util.concurrent.CountDownLatch(1)
        LiveTimeFile.appendAsync(dir, 1, 1) { failed.countDown() }
        assertTrue(failed.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test fun snapshotAddsInAppAndLiveTime() {
        val s = StatsSnapshot(timeByDay = mapOf("a" to 60_000L, "b" to 1L), liveTimeByDay = mapOf("a" to 30_000L, "c" to 5L))
        assertEquals(90_000L, s.timeTotalOn("a"))
        assertEquals(1L, s.timeTotalOn("b"))
        assertEquals(5L, s.timeTotalOn("c"))
        assertEquals(0L, s.timeTotalOn("zzz"))
        assertEquals(mapOf("a" to 90_000L, "b" to 1L, "c" to 5L), s.timeTotalByDay)
    }
}
