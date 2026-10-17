package com.husarp.ankigo.ui.movefx

import java.util.concurrent.ConcurrentHashMap

/**
 * The cache behind [FxArt] (pure, no android.*): values by key, loaded once by [load] and kept. It can be warmed on a background
 * thread ([warm]) so that no decoding happens while an animation draws; the first frames of a look that starts before the warm-up
 * is through get what is ready and skip the rest ([get] returns null for a key that is not ready yet, and never loads it on the
 * calling thread meanwhile). With no warm-up ever started (a screenshot test, a fallback) [get] simply loads on the calling thread.
 * Safe for one warming thread plus the reading (drawing) thread: ready values are published through a concurrent map.
 */
internal class ArtCache<T : Any>(private val load: (String) -> T?) {
    private val ready = ConcurrentHashMap<String, T>()
    private val none = ConcurrentHashMap.newKeySet<String>()   // keys that have no art
    @Volatile private var state = IDLE

    /** The value for [key], or null: no such art, or it is still being prepared by the warm-up (it shows from the next frame on). */
    fun get(key: String): T? {
        ready[key]?.let { return it }
        if (none.contains(key) || state == WARMING) return null
        return fetch(key)
    }

    /** True when [key] can be drawn right now. */
    fun isReady(key: String): Boolean = ready.containsKey(key)

    /** True while a warm-up is running. */
    val warming: Boolean get() = state == WARMING

    /** Test / screenshot use: waits up to [ms] for a running warm-up to be through; false when it still runs after that. */
    fun awaitWarm(ms: Long): Boolean {
        val end = System.nanoTime() + ms * 1_000_000L
        while (state == WARMING) {
            if (System.nanoTime() > end) return false
            Thread.sleep(2)
        }
        return true
    }

    /** True once a warm-up was started (running or through). */
    val warmStarted: Boolean get() = state != IDLE

    /**
     * Loads all [keys] with [start] (it gets the work to run, normally on a new background thread), once: false and nothing happens
     * when a warm-up was started already. A key that fails to load is left for [get] to try later on its own thread.
     */
    fun warm(keys: List<String>, start: (Runnable) -> Unit): Boolean {
        synchronized(this) {
            if (state != IDLE) return false
            state = WARMING
        }
        start(Runnable {
            try {
                for (key in keys) {
                    if (ready.containsKey(key) || none.contains(key)) continue
                    try { fetch(key) } catch (_: Throwable) { /* get() tries this key again, later */ }
                }
            } finally {
                state = WARM
            }
        })
        return true
    }

    private fun fetch(key: String): T? {
        val v = load(key)
        if (v == null) none.add(key) else ready[key] = v
        return v
    }

    private companion object {
        const val IDLE = 0
        const val WARMING = 1
        const val WARM = 2
    }
}
