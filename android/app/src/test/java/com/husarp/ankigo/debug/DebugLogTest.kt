package com.husarp.ankigo.debug

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** R13 Part E: dated lines, rotation (at most ~1 MB, the newest kept), the range filter, the crash handler. */
class DebugLogTest {
    @get:Rule val temp = TemporaryFolder()
    private val at = LocalDateTime.of(2026, 10, 21, 14, 3, 22, 123_000_000)

    @Test fun everyLineHasTheDateAndMilliseconds() {
        assertEquals("2026-10-21 14:03:22.123 [STUDY] card shown", LogLine.format(at, "STUDY", "card shown"))
        assertEquals(at, LogLine.timeOf("2026-10-21 14:03:22.123 [STUDY] card shown"))
        val multi = LogLine.format(at, "X", "first\nsecond\r\nthird\n")
        assertEquals("2026-10-21 14:03:22.123 [X] first\n    second\n    third", multi)
        assertNull(LogLine.timeOf("    second"))
        assertNull("the old format has no date", LogLine.timeOf("14:03:22 [STUDY] old line"))
    }

    @Test fun rotationKeepsAtMostTwoFilesAndTheNewestLines() {
        val log = RotatingLog(temp.root, "report", maxBytes = 4_000)
        for (i in 1..1_000) log.append(LogLine.format(at.plusSeconds(i.toLong()), "T", "line $i") + "\n")
        log.flush()
        val total = log.current.length() + log.older.length()
        assertTrue("at most ~2 x maxBytes: $total", total <= 8_000)
        val all = log.readAll().lines().filter { it.isNotEmpty() }
        assertTrue(all.last().endsWith("line 1000"))
        val numbers = all.map { it.substringAfterLast(' ').toInt() }
        assertEquals("newest lines, in order, nothing missing in between", (numbers.first()..1000).toList(), numbers)
        assertEquals(listOf("report.1.txt", "report.txt"), temp.root.list()!!.sorted())
    }

    @Test fun anOversizedOldReportKeepsOnlyItsTail() {
        val f = File(temp.root, "report.txt")
        f.writeText((1..5_000).joinToString("\n", postfix = "\n") { "14:00:00 [OLD] line $it" })   // the unlimited report.txt of before
        val log = RotatingLog(temp.root, "report", maxBytes = 2_000)
        assertTrue(log.older.length() <= 2_000)
        assertEquals(0L, log.current.length())
        val lines = log.readAll().lines().filter { it.isNotEmpty() }
        assertTrue(lines.first(), lines.first().startsWith("14:00:00 [OLD] line "))   // starts at a whole line
        assertTrue(lines.last().endsWith("line 5000"))
    }

    @Test fun clearEmptiesBothFiles() {
        val log = RotatingLog(temp.root, "report", maxBytes = 100)
        repeat(20) { log.append(LogLine.format(at, "T", "x$it") + "\n") }
        log.clear()
        assertEquals("", log.readAll())
    }

    @Test fun theRangeFilterKeepsStackLinesWithTheirLine() {
        val text = listOf(
            "09:00:00 [OLD] before the update",
            LogLine.format(LocalDateTime.of(2026, 10, 20, 23, 59, 59, 999_000_000), "A", "yesterday late"),
            LogLine.format(LocalDateTime.of(2026, 10, 21, 0, 0), "B", "today first\nat stack 1\nat stack 2"),
            LogLine.format(LocalDateTime.of(2026, 10, 21, 12, 30), "C", "earlier today"),
            LogLine.format(LocalDateTime.of(2026, 10, 21, 14, 0), "D", "now"),
        ).joinToString("\n", postfix = "\n")
        val now = LocalDateTime.of(2026, 10, 21, 14, 10)
        fun tags(kind: LogRange.Kind, day: LocalDate? = null) = LogRange.filter(text, LogRange.window(kind, now, day)).lines().filter { it.isNotEmpty() }
        assertEquals(1, tags(LogRange.Kind.LAST_HOUR).size)
        assertTrue(tags(LogRange.Kind.LAST_HOUR).single().endsWith("[D] now"))
        val today = tags(LogRange.Kind.TODAY)
        assertEquals(listOf("[B]", "    at stack 1", "    at stack 2", "[C]", "[D]"), today.map { if (it.startsWith("    ")) it else it.substring(24, 27) })
        assertEquals(listOf("[A]"), tags(LogRange.Kind.YESTERDAY).map { it.substring(24, 27) })
        assertEquals(tags(LogRange.Kind.YESTERDAY), tags(LogRange.Kind.DAY, LocalDate.of(2026, 10, 20)))
        assertEquals(text, LogRange.filter(text, LogRange.window(LogRange.Kind.EVERYTHING, now)))
        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20)), LogRange.days(text))
    }

    @Test fun theCrashHandlerWritesTheCrashBeforeHandingItOn() {
        val log = RotatingLog(temp.root, "report")
        val w = LogWriter(log) { at }
        w.write(LogLine.format(at, "T", "queued earlier") + "\n", flush = false)
        var handedOn: Throwable? = null
        var before = false
        val h = w.handler({ _, e -> handedOn = e }) { before = true }
        val boom = IllegalStateException("boom", RuntimeException("the cause"))
        h.uncaughtException(Thread.currentThread(), boom)
        assertSame(boom, handedOn)
        assertTrue(before)
        val onDisk = log.current.readText()   // flushed, not only buffered
        assertTrue(onDisk, onDisk.contains("queued earlier"))
        assertTrue(onDisk, onDisk.contains("[CRASH] uncaught on thread ${Thread.currentThread().name}: java.lang.IllegalStateException: boom"))
        assertTrue(onDisk, onDisk.contains("\n      at ") && onDisk.contains("Caused by: java.lang.RuntimeException: the cause"))
    }

    @Test fun aStackTraceIsBoundedAndNamesTheCauses() {
        val s = LogLine.stack(RuntimeException("top", IllegalArgumentException("inner")), frames = 2)
        assertTrue(s.startsWith("java.lang.RuntimeException: top\n  at "))
        assertTrue(s.contains("more"))
        assertTrue(s.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }
}
