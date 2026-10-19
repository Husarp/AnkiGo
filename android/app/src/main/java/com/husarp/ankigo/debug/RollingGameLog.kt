package com.husarp.ankigo.debug

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Instant

/** Small persisted ring, stored separately from the game save. Call from one worker; reads are synchronized. */
class RollingGameLog(private val file: File, private val limit: Int = 500, private val flush: DelayedFlush? = null) {
    private val lines = ArrayDeque<String>()
    init {
        require(limit > 0)
        if (file.isFile && file.length() <= limit * 1024L) {
            file.useLines { source -> source.forEach { add(it.take(512)) } }
        }
    }
    private fun add(line: String) {
        lines.addLast(line)
        while (lines.size > limit) lines.removeFirst()
    }
    @Synchronized fun append(records: List<String>, at: Instant = Instant.now()) {
        if (records.isEmpty()) return
        records.forEach { add("$at ${it.replace('\n', ' ').replace('\r', ' ').take(400)}") }
        if (flush != null) flush.request() else persist()
    }
    @Synchronized fun all() = lines.joinToString("\n")
    @Synchronized fun clear() { lines.clear(); persist() }
    /** Writes the file now (the delayed flush calls this; [append] writes at once when no flush is set). */
    @Synchronized fun persist() {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        java.io.FileOutputStream(temp).use { out ->
            out.write((all() + if (lines.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8)); out.fd.sync()
        }
        try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}
