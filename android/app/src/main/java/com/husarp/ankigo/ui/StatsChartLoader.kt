package com.husarp.ankigo.ui

import com.husarp.ankigo.stats.ChartSeries
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Long-range aggregation runs on one worker; only the latest choice can publish. Keep four recent choices per page. */
internal class StatsChartLoader(
    private val worker: Executor = io,
    private val deliver: (() -> Unit) -> Unit,
) {
    private val cache = LinkedHashMap<Any, ChartSeries>()
    private var wanted: Any? = null
    private var generation = 0

    fun load(key: Any, build: () -> ChartSeries, ready: () -> Unit): ChartSeries? {
        cache[key]?.let { wanted = key; return it }
        if (wanted == key) return null
        wanted = key
        val token = ++generation
        worker.execute {
            val result = build()
            deliver {
                if (token == generation && wanted == key) {
                    cache[key] = result
                    while (cache.size > 4) cache.remove(cache.keys.first())
                    ready()
                }
            }
        }
        return null
    }

    fun clear() { generation++; wanted = null; cache.clear() }

    companion object {
        private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "stats-chart").apply { isDaemon = true } }
    }
}
