package com.husarp.ankigo.update

import android.content.Context
import com.husarp.ankigo.R

/**
 * R18-K (PLAN 201): the release notes bundled in the app (`res/raw/whats_new.txt`, newest first), the version-compare logic of
 * the "AnkiGo X.Y.Z installed" card, and the last version the user has been told about. Parsing and decisions are pure (no
 * Android), unit-tested in R18KWhatsNewTest; only [load] and the prefs touch the context.
 *
 * File format: `## 0.24.0` starts a block, each `- text` line is one change, ` @ Settings → X → Y` at the end of a line is the
 * path of a setting. A `### Fixes` line inside a block starts its bug fixes (shown folded under "Bugs & fixes"). `#` lines are
 * comments, `**` markers are dropped.
 */
object WhatsNew {
    data class Item(val text: String, val path: String? = null)
    data class Version(val version: String, val items: List<Item>, val fixes: List<Item> = emptyList())

    /** What the card does for an app start. */
    enum class Action { SHOW, NONE, MARK_SEEN }

    private const val PREFS = "updates"
    private const val KEY_SEEN = "whatsNewSeen"

    fun parse(text: String): List<Version> {
        val out = ArrayList<Version>()
        var name: String? = null
        var items = ArrayList<Item>()
        var fixes = ArrayList<Item>()
        var inFixes = false
        fun close() { name?.let { out += Version(it, items, fixes) }; items = ArrayList(); fixes = ArrayList(); inFixes = false }
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("### ") && name != null -> inFixes = true
                line.startsWith("## ") -> { close(); name = line.removePrefix("## ").trim() }
                line.startsWith("- ") && name != null -> {
                    val body = line.removePrefix("- ").replace("**", "").trim()
                    val at = body.lastIndexOf(" @ ")
                    (if (inFixes) fixes else items) += if (at > 0) Item(body.substring(0, at).trim(), body.substring(at + 3).trim()) else Item(body)
                }
            }
        }
        close()
        return out.filter { it.items.isNotEmpty() || it.fixes.isNotEmpty() }.sortedWith { a, b -> UpdateLogic.compare(b.version, a.version) }
    }

    /** The blocks up to and including [current], newest first (a block written ahead of the version bump stays hidden). */
    fun upTo(all: List<Version>, current: String): List<Version> =
        all.filter { UpdateLogic.compare(it.version, current) <= 0 }.sortedWith { a, b -> UpdateLogic.compare(b.version, a.version) }

    /** The blocks the user has not been told about: newer than [lastSeen] (null = all), up to [current]. */
    fun unseen(all: List<Version>, lastSeen: String?, current: String): List<Version> =
        upTo(all, current).filter { lastSeen == null || UpdateLogic.isNewer(it.version, lastSeen) }

    /**
     * The card after an update. [lastSeen] = the version stored when the user last tapped the card (null = never stored).
     * A first install ([wasUpdated] false, nothing stored) gets no card but the version is stored; an update from a version that
     * never stored one ([wasUpdated] true) counts as older than everything.
     */
    fun decide(lastSeen: String?, current: String, wasUpdated: Boolean): Action = when {
        lastSeen == null -> if (wasUpdated) Action.SHOW else Action.MARK_SEEN
        UpdateLogic.isNewer(current, lastSeen) -> Action.SHOW
        else -> Action.NONE
    }

    /** "1 change", "3 changes". */
    fun changes(n: Int): String = if (n == 1) "1 change" else "$n changes"

    // ---- Android side ----

    fun load(ctx: Context): List<Version> =
        runCatching { ctx.resources.openRawResource(R.raw.whats_new).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun lastSeen(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString(KEY_SEEN, null)

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    /** The user has been told about [current]: the card goes away on every open screen. */
    fun markSeen(ctx: Context, current: String = Updates.current(ctx)) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_SEEN, current).apply()
        listeners.forEach { it() }
    }

    /** True when this installation was updated at least once (Android keeps the first install time). */
    private fun wasUpdated(ctx: Context): Boolean = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pi.lastUpdateTime - pi.firstInstallTime > 10_000L
    }.getOrDefault(false)

    /** Whether the card shows now. Stores the version on a first install, and when an update brought nothing to tell. */
    fun cardPending(ctx: Context): Boolean {
        val cur = Updates.current(ctx)
        if (cur == "?") return false
        val seen = lastSeen(ctx)
        return when (decide(seen, cur, wasUpdated(ctx))) {
            Action.NONE -> false
            Action.MARK_SEEN -> { markSeen(ctx, cur); false }
            Action.SHOW -> if (unseen(load(ctx), seen, cur).isEmpty()) { markSeen(ctx, cur); false } else true
        }
    }
}
