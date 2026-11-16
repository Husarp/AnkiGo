package com.husarp.ankigo.ui.movefx

/** Short snaps separated by a fresh random quiet interval, never a continuous lightning loop. */
internal class ConditionCrackle(private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private var since = Long.MIN_VALUE
    private var start = 0L
    private var end = 0L

    fun visible(now: Long, conditionSince: Long): Boolean {
        if (since != conditionSince) {
            since = conditionSince
            start = conditionSince + random.nextLong(1800, 5201)
            end = start + random.nextLong(160, 241)
        }
        if (now >= end) {
            start = now + random.nextLong(1800, 5201)
            end = start + random.nextLong(160, 241)
        }
        return now >= start && now < end
    }
}
