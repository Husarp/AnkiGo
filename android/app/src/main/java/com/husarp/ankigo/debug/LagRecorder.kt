package com.husarp.ankigo.debug

import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** File access and rotation stay on one worker. Both the file and the pending work are bounded. */
class LagRecorder(private val file: File, private val limit: Int = 5000) : AutoCloseable {
    private val io = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(512),
        { r -> Thread(r, "study-lag-log").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())
    private val dropped = java.util.concurrent.atomic.AtomicLong()
    private var lines = -1
    @Volatile var enabled = true

    fun record(kind: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        if (io.queue.remainingCapacity() < 16) { dropped.incrementAndGet(); return }
        val line = "${Instant.now()}\t$kind" + fields.joinToString("") { (k, v) -> "\t$k=${v.toString().replace('\n', '|').replace('\r', ' ').replace('\t', ' ').take(12000)}" }
        io.execute {
            val lost = dropped.getAndSet(0)
            if (lost > 0) append("${Instant.now()}\tDROPPED\tcount=$lost")
            append(line)
        }
    }

    private fun append(line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            if (lines < 0) lines = if (file.exists()) file.useLines { it.count() } else 0
            file.appendText(line + "\n")
            lines++
            if (lines > limit) {
                val kept = file.readLines().takeLast(limit * 4 / 5)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
                lines = kept.size
            }
        }
    }

    fun snapshot(done: (String) -> Unit) { io.execute { done(runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")) } }
    fun clear(done: () -> Unit = {}) { io.execute { runCatching { file.delete() }; lines = -1; done() } }
    override fun close() { io.shutdown(); io.awaitTermination(5, TimeUnit.SECONDS) }

    companion object {
        /** Only structured numeric/id fields go here, never provider queries, card text, or exception messages. */
        fun summary(text: String): String {
            val rows = text.lineSequence().map { it.split('\t') }.filter { it.size >= 2 }.toList()
            fun List<String>.value(key: String) = drop(2).firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
            val cards = rows.filter { it[1] == "CARD" }
            val times = cards.mapNotNull { it.value("ms")?.toLongOrNull() }.sorted()
            fun percentile(p: Double) = if (times.isEmpty()) "—" else "${times[(ceil(times.size * p).toInt() - 1).coerceAtLeast(0)]} ms"
            val waits = cards.filter { it.value("source") == "wait" }.groupingBy { it.value("reason") ?: "unknown" }.eachCount()
            val stalls = rows.withIndex().filter { (_, row) -> row[1] == "STALL" || row.value("ping") != null && row[1] in setOf("STALL_START", "STACK") }
                .groupBy { (index, row) -> row.value("ping") ?: "row-$index" }
                .values.mapNotNull { entries -> entries.mapNotNull { it.value.value("ms")?.toLongOrNull() }.maxOrNull() }
            val calls = rows.filter { it[1] == "PROVIDER" }.groupBy { it.value("call") ?: "unknown" }
                .map { (call, entries) -> call to (entries.mapNotNull { it.value("ms")?.toLongOrNull() }.maxOrNull() ?: 0) }
                .sortedByDescending { it.second }.take(5)
            val work = rows.filter { it[1] == "WORK" }.groupBy { it.value("name") ?: "unknown" }
                .map { (name, entries) -> name to (entries.mapNotNull { it.value("ms")?.toLongOrNull() }.maxOrNull() ?: 0) }
                .sortedByDescending { it.second }.take(5)
            return "${cards.size} cards · tap → card\nMedian ${percentile(0.5)} · 90th ${percentile(0.9)} · max ${times.lastOrNull()?.let { "$it ms" } ?: "—"}\n" +
                "Waits: ${if (waits.isEmpty()) "none" else waits.entries.joinToString { "${it.key}: ${it.value}" }}\n" +
                "Stalls >100 ms: ${stalls.count { it > 100 }} · >700 ms: ${stalls.count { it > 700 }}\n" +
                "Slowest provider calls: ${if (calls.isEmpty()) "none" else calls.joinToString { "${it.first} ${it.second} ms" }}" +
                if (work.isEmpty()) "" else "\nSlowest work: ${work.joinToString { "${it.first} ${it.second} ms" }}"
        }
    }
}
