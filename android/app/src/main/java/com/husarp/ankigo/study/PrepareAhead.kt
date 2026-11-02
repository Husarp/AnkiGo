package com.husarp.ankigo.study

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Content-only worker, independent of ordered answer writes. Retrieval never waits on a preparation. */
class PrepareAhead<T>(private val io: ExecutorService, private val now: () -> Long, private val maxAgeMs: Long = 5_000) {
    private data class Entry<T>(val card: Prepared<T>, val at: Long)
    private val jobs = LinkedHashMap<StudyLoop.Key, Future<Entry<T>>>()

    @Synchronized fun start(key: StudyLoop.Key, read: () -> Prepared<T>) {
        jobs[key]?.let { job ->
            if (!job.isDone) return
            val entry = runCatching { job.get() }.getOrNull()
            if (entry != null && now() - entry.at in 0..maxAgeMs) return
            jobs.remove(key)
        }
        while (jobs.size >= 3) jobs.remove(jobs.keys.first())?.cancel(false)
        jobs[key] = io.submit<Entry<T>> { val at = now(); Entry(read(), at) }
    }

    /** Reuse only after reading this card's scheduling state again, with a short content lifetime. */
    @Synchronized fun take(key: StudyLoop.Key, before: Map<String, String?>): Prepared<T>? {
        val job = jobs.remove(key) ?: return null
        if (!job.isDone || job.isCancelled) { job.cancel(false); return null }
        val entry = runCatching { job.get() }.getOrNull() ?: return null
        return entry.card.takeIf { it.key == key && it.before == before && now() - entry.at in 0..maxAgeMs }
    }

    @Synchronized fun clear() { jobs.values.forEach { it.cancel(false) }; jobs.clear() }
    fun close() { clear(); io.shutdownNow() }
}
