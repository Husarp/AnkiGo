package com.husarp.ankigo.help

import com.husarp.ankigo.game.Type

// R3-V3a: the pure parts of the Help screen (what is shown where, what a button does, the grey boxes in step texts).
// No android.* here, so it is unit-tested on the JVM. The Views are in HelpViews.kt / HelpActivity.kt.

/** What the Help screen shows: the home list, one problem page, or the game guide (with a chapter to open first). */
sealed class HelpRoute {
    object Home : HelpRoute()
    data class Problem(val id: String) : HelpRoute()
    /** [chapter] = a [GameGuide] chapter id to open and scroll to, or null (all chapters closed). */
    data class Guide(val chapter: String? = null) : HelpRoute()
    /** The "Set up pictures" checks (PictureSetup), opened from the pictures banner, Settings → Study and the help pages. */
    object Setup : HelpRoute()
}

object HelpRoutes {
    /** The `chapter` value that opens the guide with every chapter closed. */
    const val GUIDE = "guide"

    /** The `page` value that opens "Set up pictures" (not a [HelpContent] page). */
    const val SETUP = "setup-pictures"

    /** The route for the Activity's extras; unknown or missing ids fall back to the Help home. */
    fun parse(page: String?, chapter: String?): HelpRoute = when {
        page == SETUP -> HelpRoute.Setup
        page != null && HelpContent.page(page) != null -> HelpRoute.Problem(page)
        chapter == GUIDE -> HelpRoute.Guide(null)
        chapter != null && GameGuide.chapter(chapter) != null -> HelpRoute.Guide(chapter)
        else -> HelpRoute.Home
    }

    /** The app bar title. */
    fun title(route: HelpRoute): String = when (route) {
        HelpRoute.Home -> "Help"
        is HelpRoute.Problem -> HelpContent.page(route.id)?.title ?: "Help"
        is HelpRoute.Guide -> "How the game works"
        HelpRoute.Setup -> "Set up pictures"
    }

    /** Back from [route]: the route to show (null = leave the Help screen). [outside] = the screen was opened straight onto a page. */
    fun back(route: HelpRoute, outside: Boolean): HelpRoute? = if (route == HelpRoute.Home || outside) null else HelpRoute.Home
}

/** What a Help button does. */
enum class HelpAction { ALL_FILES_ACCESS, ANKIDROID_SETTINGS, OPEN_ANKIDROID, IMPORT_APKG, HELP_PAGE, OPEN_DIAGNOSTICS, OPEN_SETTINGS, OPEN_URL }

/** One button under "What to do". [arg] = the help page id (HELP_PAGE) or the web address (OPEN_URL). [link] buttons are the quiet ones. */
data class HelpButton(val action: HelpAction, val label: String, val arg: String? = null, val link: Boolean = false)

/** The result line next to "Check pictures now". */
data class PictureCheck(val ok: Boolean?, val text: String)

object HelpFixes {
    /** The buttons of one problem page: the quiet ones (its web links, links to other pages) first, then the filled fixes. */
    fun buttons(pageId: String): List<HelpButton> {
        val page = HelpContent.page(pageId) ?: return emptyList()
        val links = page.links.map { HelpButton(HelpAction.OPEN_URL, it.label, it.url, link = true) }
        return (links + fixes(pageId)).sortedByDescending { it.link } // quiet buttons first, filled ones last (the sort is stable)
    }

    /** The app's own fixes for a page. */
    fun fixes(pageId: String): List<HelpButton> = when (pageId) {
        HelpContent.PICTURES_DONT_SHOW -> listOf(
            HelpButton(HelpAction.ALL_FILES_ACCESS, "All files access"),
            HelpButton(HelpAction.IMPORT_APKG, "Import a deck file (.apkg)"),
            HelpButton(HelpAction.HELP_PAGE, "Moving AnkiDroid's folder", HelpContent.MOVE_COLLECTION, link = true),
        )
        HelpContent.MOVE_COLLECTION -> listOf(
            HelpButton(HelpAction.ANKIDROID_SETTINGS, "AnkiDroid app settings"),
            HelpButton(HelpAction.HELP_PAGE, "Pictures missing after a move", HelpContent.PICTURES_MISSING, link = true),
        )
        HelpContent.PICTURES_MISSING -> listOf(HelpButton(HelpAction.OPEN_ANKIDROID, "Open AnkiDroid"))
        HelpContent.PICTURES_SYNC -> listOf(HelpButton(HelpAction.OPEN_ANKIDROID, "Open AnkiDroid"))
        HelpContent.ANSWERS_NOT_DETECTED -> listOf(HelpButton(HelpAction.OPEN_DIAGNOSTICS, "Open Diagnostics"))
        HelpContent.WIDGET_NOT_UPDATING -> listOf(HelpButton(HelpAction.OPEN_DIAGNOSTICS, "Open Diagnostics"))
        HelpContent.BATTLE_POPS_UP -> listOf(HelpButton(HelpAction.OPEN_SETTINGS, "Open Settings"))
        HelpContent.ANKIMON_IMPORT_EXPORT -> listOf(HelpButton(HelpAction.OPEN_SETTINGS, "Open Settings"))
        else -> emptyList()
    }

    /** A "Set up pictures" fix button ([Fix] of [PictureSetup]) as a Help button. */
    fun fromFix(fix: Fix): HelpButton = when (fix.action) {
        FixAction.OPEN_ALL_FILES_ACCESS -> HelpButton(HelpAction.ALL_FILES_ACCESS, "All files access")
        FixAction.OPEN_ANKIDROID_SETTINGS -> HelpButton(HelpAction.ANKIDROID_SETTINGS, "AnkiDroid app settings")
        FixAction.OPEN_ANKIDROID -> HelpButton(HelpAction.OPEN_ANKIDROID, "Open AnkiDroid")
        FixAction.OPEN_GITHUB_RELEASES -> HelpButton(HelpAction.OPEN_URL, "AnkiDroid releases", RELEASES_URL, link = true)
        FixAction.IMPORT_APKG -> HelpButton(HelpAction.IMPORT_APKG, "Import a deck file (.apkg)")
        FixAction.HELP_PAGE -> HelpButton(HelpAction.HELP_PAGE, HelpContent.page(fix.helpPageId.orEmpty())?.title ?: "Read the help page", fix.helpPageId, link = true)
    }

    const val RELEASES_URL = "https://github.com/ankidroid/Anki-Android/releases"

    /** True for the pages whose "How to check it worked" has the "Check pictures now" button. */
    fun checksPictures(pageId: String): Boolean =
        pageId == HelpContent.PICTURES_DONT_SHOW || pageId == HelpContent.MOVE_COLLECTION || pageId == HelpContent.PICTURES_MISSING

    /** What "Check pictures now" says about [f] (the same sample as Set up pictures). [PictureCheck.ok] null = nothing to test yet. */
    fun pictureCheck(f: PictureFacts): PictureCheck = when {
        !f.ankiDroidInstalled -> PictureCheck(false, "AnkiDroid isn't installed")
        !f.apiAllowed -> PictureCheck(null, "Allow AnkiGo to read AnkiDroid first (Settings → Diagnostics)")
        f.sampleTotal == 0 -> PictureCheck(null, "No pictures on your next cards to test")
        f.sampleReadable >= f.sampleTotal -> PictureCheck(true, "${f.sampleReadable} of ${f.sampleTotal} read")
        else -> PictureCheck(false, "${f.sampleReadable} of ${f.sampleTotal} read")
    }
}

/** The words of the "Set up pictures" screen header (the steps themselves come from [PictureSetup.evaluate]). */
object SetupText {
    /** The big line: how the checks came out. */
    fun headline(steps: List<SetupStep>): String {
        val bad = steps.count { it.status == StepStatus.FAIL || it.status == StepStatus.WARN }
        return when {
            bad == 1 -> "1 thing to fix"
            bad > 1 -> "$bad things to fix"
            steps.any { it.status == StepStatus.UNKNOWN } -> "Nothing wrong so far"
            else -> "Pictures are set up"
        }
    }

    /** The small line under it. */
    fun sub(steps: List<SetupStep>): String = when (PictureSetup.overall(steps)) {
        StepStatus.OK -> "AnkiGo can read AnkiDroid's pictures."
        StepStatus.UNKNOWN -> "Some checks need cards with pictures, or a permission, first."
        else -> "Start at the first red or yellow step."
    }

    /** Words for the status icon (screen readers). */
    fun statusName(s: StepStatus): String = when (s) {
        StepStatus.OK -> "OK"
        StepStatus.WARN -> "Warning"
        StepStatus.FAIL -> "Problem"
        StepStatus.UNKNOWN -> "Not checked"
    }
}

/** Short names, and the made-up demo values the guide pictures show. */
object GuideDemo {
    private val SHORT = mapOf(
        GameGuide.BATTLES to "Battles & XP", GameGuide.MOVES to "Moves", GameGuide.LEVELS to "Levels & evolving", GameGuide.TYPES to "Types",
        GameGuide.STATS to "Stats & natures", GameGuide.CATCHING to "Catching", GameGuide.MONEY to "Money",
        GameGuide.HEALING to "Healing", GameGuide.ITEMS to "Items",
    )

    /** The short name of a chapter for the contents tiles. */
    fun shortTitle(id: String): String = SHORT[id] ?: GameGuide.chapter(id)?.title ?: id

    /** The words of a guide mark (the icon's content description; same words as in the details header). */
    fun tagLabel(kind: String): String = when (kind) {
        "MAIN" -> "Main"
        "FAINTED" -> "Fainted"
        "CAN_EVOLVE" -> "Can evolve"
        "NEW" -> "New"
        "EXP_SHARE" -> "Exp. Share"
        "HELD" -> "Holds an item"
        "SHINY" -> "Shiny"
        "REGIONAL" -> "Regional"
        else -> kind
    }

    val hpFraction = 0.72f
    val xpFraction = 0.55f
    /** One level per colour tier, bronze to master. */
    val levels = listOf(5, 15, 25, 35, 45, 55, 65, 75, 85, 95)
    val statuses = listOf("psn", "brn", "par", "slp", "frz")
    /** Made-up base stats (HP, Atk, Def, SpA, SpD, Spe) for the stat bars. */
    val stats = listOf(52, 60, 48, 70, 55, 64)
    const val STAT_BAR_MAX = 150
    val typeRow = listOf(Type.NORMAL, Type.FIRE, Type.WATER, Type.GRASS, Type.ELECTRIC, Type.PSYCHIC)
    /** The type whose weaknesses the matchup picture shows. */
    val matchupDefender = Type.GRASS
}

/** A piece of a help text: [boxed] pieces are menu or button names, shown in a grey box. */
data class TextPiece(val text: String, val boxed: Boolean)

/**
 * Finds the real menu and button names in a help text so they can be shown in grey boxes: whatever is in "double quotes", and
 * arrow paths such as `Settings → Diagnostics` or `Android Settings → Apps → AnkiDroid → Battery`.
 */
object HelpText {
    private val STOP = setOf(
        "and", "then", "or", "to", "is", "in", "if", "when", "with", "so", "tap", "choose", "open", "set", "use", "for", "from",
        "the", "a", "you", "it", "your", "which", "until", "but", "that", "once", "also", "click", "pick", "press",
    )
    private const val MAX_LEFT = 3
    private const val MAX_RIGHT = 4

    private class Tok(val text: String, val start: Int)

    private fun tokens(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            if (s[i] == ' ') { i++; continue }
            val st = i
            while (i < s.length && s[i] != ' ') i++
            out += Tok(s.substring(st, i), st)
        }
        return out
    }

    private fun endsWithPunct(w: String) = w.last() in ",.;:)!?\""
    private fun strip(w: String) = if (w.endsWith("...")) w else w.trimEnd(',', '.', ';', ':', ')', '!', '?', '"') // "Export Save File..." is a name

    /** A word that can be part of the name before the first arrow: capitalised (or a digit; the word right before the arrow may be lower case, as in "Ankimon menu"), not a stop word, no punctuation. */
    private fun leftName(w: String, adjacent: Boolean) =
        (adjacent || w.first().let { it.isUpperCase() || it.isDigit() }) && w.lowercase() !in STOP && !w.any { it in "(\"" } && w != "→"

    private fun rightName(w: String) = w.lowercase() !in STOP && w != "→" && !w.startsWith("(") && !w.startsWith("\"")

    /** The character ranges (start, end exclusive) that are names. */
    private fun ranges(s: String): List<IntRange> {
        val found = ArrayList<IntRange>()
        val quoted = Regex("\"([^\"]{1,60})\"").findAll(s).map { it.groups[1]!!.range }.toList()
        found += quoted
        val t = tokens(s)
        var k = 0
        while (k < t.size) {
            if (t[k].text != "→") { k++; continue }
            // left side: up to MAX_LEFT name words just before the arrow, none across a punctuation mark
            var l = k
            while (l - 1 >= 0 && k - (l - 1) <= MAX_LEFT && leftName(t[l - 1].text, l - 1 == k - 1) && (l - 1 == k - 1 || !endsWithPunct(t[l - 1].text))) l--
            // right side
            var chainEnd = k
            var arrow = k
            var endTok = -1
            while (true) {
                var r = arrow + 1
                var count = 0
                var stopAfter = false
                while (r < t.size && count < MAX_RIGHT && rightName(t[r].text) && !stopAfter) {
                    if (endsWithPunct(t[r].text)) stopAfter = true
                    r++; count++
                }
                if (count == 0) break
                endTok = r - 1
                chainEnd = endTok
                if (stopAfter || r >= t.size || t[r].text != "→") break
                arrow = r
            }
            if (endTok >= 0 && l < k) {
                val from = t[l].start
                val last = t[endTok]
                val to = last.start + strip(last.text).length
                if (to > from) found += from until to
            }
            k = chainEnd + 1
        }
        return found.filter { it.first <= it.last }.sortedBy { it.first }
    }

    /** [text] cut into plain and boxed pieces; the quote marks of a quoted name are dropped. Overlapping finds are merged. */
    fun pieces(text: String): List<TextPiece> {
        val merged = ArrayList<IntRange>()
        for (r in ranges(text)) {
            val prev = merged.lastOrNull()
            if (prev != null && r.first <= prev.last) merged[merged.size - 1] = prev.first..maxOf(prev.last, r.last)
            else merged += r
        }
        val out = ArrayList<TextPiece>()
        var at = 0
        for (r in merged) {
            // a quoted name: also take the quote marks off the plain text around it
            var from = r.first
            var to = r.last + 1
            if (from > 0 && text[from - 1] == '"') from--
            if (to < text.length && text[to] == '"') to++
            if (from > at) out += TextPiece(text.substring(at, from), false)
            out += TextPiece(text.substring(r.first, r.last + 1), true)
            at = maxOf(at, to)
        }
        if (at < text.length) out += TextPiece(text.substring(at), false)
        return out
    }
}
