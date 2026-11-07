package com.husarp.ankigo.debug

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LagRecorderTest {
    private fun snapshot(log: LagRecorder): String {
        val done = CountDownLatch(1)
        var text = ""
        log.snapshot { text = it; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return text
    }
    @Test fun fileRotatesAndControlsShareTheWriterOrder() {
        val dir = Files.createTempDirectory("lag-test").toFile()
        try {
            LagRecorder(dir.resolve("lag.txt"), 20).use { log ->
                repeat(55) { log.record("CARD", "card" to it, "ms" to it, "source" to "prepared") }
                val text = snapshot(log)
                val lines = text.lines().filter { it.isNotEmpty() }
                assertTrue(lines.size <= 20)
                assertTrue(text.contains("card=54")); assertFalse(text.contains("card=0\t"))
                assertTrue(lines.all { it.substringBefore('\t').endsWith("Z") })
                log.clear()
                assertEquals("", snapshot(log))
                log.enabled = false
                log.record("CARD", "card" to 99)
                assertEquals("", snapshot(log))
                log.enabled = true
                log.record("CARD", "card" to 100, "ms" to 7)
                assertTrue(snapshot(log).contains("card=100"))
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun summaryCalculatesPercentilesWaitReasonsStallsAndSlowestCalls() {
        val lines = (1..10).map { "2026-11-07T00:00:00Z\tCARD\tcard=$it\tms=${it * 10}\tsource=${if (it > 8) "wait" else "prepared"}\treason=nothing prepared" } +
            listOf("t\tSTALL\tms=100", "t\tSTALL\tms=101", "t\tSTALL\tms=701",
                "t\tPROVIDER\tcall=nextCards\tms=1500", "t\tPROVIDER\tcall=nextCards\tms=500",
                "t\tPROVIDER\tcall=answer\tms=900", "t\tSTACK\tms=701\tmain=stack")
        val summary = LagRecorder.summary(lines.joinToString("\n"))
        assertTrue(summary, summary.contains("10 cards"))
        assertTrue(summary, summary.contains("Median 50 ms · 90th 90 ms · max 100 ms"))
        assertTrue(summary, summary.contains("nothing prepared: 2"))
        assertTrue(summary, summary.contains("Stalls >100 ms: 2 · >700 ms: 1"))
        assertTrue(summary, summary.contains("nextCards 1500 ms, answer 900 ms"))
        assertTrue(LagRecorder.summary("").contains("0 cards"))
    }
    @Test fun largeStackRemainsOneBoundedLineAndLogIsReadableAfterRestart() {
        val dir = Files.createTempDirectory("lag-stack").toFile()
        try {
            val file = dir.resolve("lag.txt")
            LagRecorder(file).use { log ->
                log.record("STACK", "ms" to 900, "main" to "frame\n".repeat(5000))
                val text = snapshot(log)
                assertEquals(1, text.lines().count { it.isNotEmpty() })
                assertTrue(text.length < 12100)
            }
            LagRecorder(file).use { assertTrue(snapshot(it).contains("STACK")) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun unfinishedStallsSurviveRestartAndAreNotCountedAgainWhenThePingCompletes() {
        val interrupted = "t\tSTALL_START\tping=123/1000\tms=200\n" +
            "t\tSTACK\tping=123/1000\tms=700\tmain=blocked frame\n" +
            "t\tSTALL_START\tping=123/1000\tms=800\n"
        assertTrue(LagRecorder.summary(interrupted).contains("Stalls >100 ms: 1 · >700 ms: 1"))
        val completed = interrupted + "t\tSTALL\tping=123/1000\tms=900\n"
        assertTrue(LagRecorder.summary(completed).contains("Stalls >100 ms: 1 · >700 ms: 1"))
    }

}
