package com.husarp.ankigo.update

import java.time.Instant
import java.time.ZoneId

/**
 * The updater's pure logic (no Android), unit-tested in UpdateLogicTest.
 * APP-STANDARDS sections 2 and 3: compare versions as numbers, find the APK by its ".apk" ending,
 * ask GitHub at most every 5 minutes, and a banner ✕ that hides until the next day (local date).
 */
object UpdateLogic {
    const val MIN_CHECK_GAP_MS = 5 * 60_000L

    /** A release file as GitHub lists it. */
    data class Asset(val name: String, val url: String, val size: Long)

    /** Compares two versions number by number ("0.5.10" > "0.5.9"); a leading "v" and any "-suffix" are ignored. */
    fun compare(a: String, b: String): Int {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** The version in an APK's file name ("AnkiGo-0.5.11.apk" -> "0.5.11"), or null if it has none. */
    fun apkVersion(name: String): String? {
        if (!name.endsWith(".apk", ignoreCase = true)) return null
        return Regex("""\d+(?:\.\d+)+""").findAll(name.dropLast(4)).lastOrNull()?.value
    }

    /**
     * The Android update among a release's files: the .apk files (found by their ending, never an exact
     * name) that carry a version, the highest one. The tag is not used: the file name carries the version.
     */
    fun pickApk(assets: List<Asset>): Pair<Asset, String>? =
        assets.mapNotNull { a -> apkVersion(a.name)?.let { a to it } }
            .maxWithOrNull { p, q -> compare(p.second, q.second) }

    /** A GitHub release: its page and files. */
    data class Release(val page: String, val assets: List<Asset>, val draft: Boolean = false, val prerelease: Boolean = false)

    /**
     * The newest Android update across recent releases: a release without an APK
     * must not hide the update on the one before it. Drafts and pre-releases don't count.
     * Returns (release, apk, version), or null when none of them carries an APK.
     */
    fun newestApk(releases: List<Release>): Triple<Release, Asset, String>? =
        releases.filter { !it.draft && !it.prerelease }
            .mapNotNull { r -> pickApk(r.assets)?.let { (a, v) -> Triple(r, a, v) } }
            .maxWithOrNull { p, q -> compare(p.third, q.third) }

    /** True when GitHub may be asked again ([lastMs] = time of the last ask, 0 = never). */
    fun mayCheck(nowMs: Long, lastMs: Long, gapMs: Long = MIN_CHECK_GAP_MS): Boolean =
        lastMs == 0L || nowMs - lastMs >= gapMs || nowMs < lastMs

    /** The "closed on" day while the banner was never closed. */
    const val NO_DAY = Long.MIN_VALUE

    /** The local calendar day (`LocalDate.toEpochDay()`) of [nowMs] in [zone]. */
    fun dayOf(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    /** The banner's ✕ hides it for the rest of that local day: it is hidden only on the day it was closed. */
    fun bannerHidden(dismissedDay: Long, today: Long): Boolean = dismissedDay == today

    /** The banner shows when a newer version is known and ✕ wasn't tapped today. */
    fun bannerVisible(latest: String?, current: String, dismissedDay: Long, today: Long): Boolean =
        latest != null && isNewer(latest, current) && !bannerHidden(dismissedDay, today)
}
