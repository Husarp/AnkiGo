package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoNode
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.TypeMatchups
import java.util.Locale

// PLAN item 60 (pick 4B): the details are sections that open on tap; AnkiGo remembers which are open. Pure: no android.*, unit-tested.

/** The sections of the details, in screen order. [key] is the saved name (never rename), [open] whether it starts open. */
enum class DetailSection(val key: String, val title: String, val open: Boolean) {
    STATS("stats", "Stats", true),
    MOVES("moves", "Moves", true),
    MATCHUPS("matchups", "Matchups", false),
    TRAINING("training", "Training", false),
    INFO("info", "Info", false),
}

/** Where the open/closed answers are kept (AppPrefs on the phone, a map in the tests). */
interface DetailOpenStore {
    fun get(key: String): Boolean?
    fun set(key: String, open: Boolean)
}

/** Open/closed memory per section: one answer each, shared by every Pokémon, species and the battle's details window. */
class DetailOpen(private val store: DetailOpenStore) {
    fun isOpen(s: DetailSection): Boolean = store.get(s.key) ?: s.open

    /** Flips [s] and keeps the answer; returns the new state. */
    fun toggle(s: DetailSection): Boolean = (!isOpen(s)).also { store.set(s.key, it) }
}

/** Texts of the details sections. */
object DetailFormat {
    /** R18-G (PLAN 241): every tappable row, cell or title of the details is at least this tall (dp). */
    const val TAP_MIN_DP = 48

    /** R18-G (PLAN 254): the Stats title with the level in it: "Stats · Lv 23" (the screen paints the "Lv 23" in its level band colour). */
    fun levelTitle(title: String, level: Int): String = "$title · ${LevelTier.label(level)}"

    /** R18-G (PLAN 254): the line under the name: "#025 · Hasty" (the level moved into the Stats title). */
    fun numLine(baseNum: Int, natureLabel: String): String = "#%03d · %s".format(baseNum, natureLabel)

    /** "0.7 m", or null when the data has no height. */
    fun height(m: Double?): String? = m?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f m", it) }

    /** "9.6 kg", or null when the data has no weight. */
    fun weight(kg: Double?): String? = kg?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f kg", it) }

    const val IV_MAX_TOTAL = 6 * 31

    /** "255 / 255": friendship as the details show it (an older save's number above the cap shows as the cap). */
    fun friendship(value: Int): String = "${value.coerceIn(0, Tuning.FRIENDSHIP_MAX)} / ${Tuning.FRIENDSHIP_MAX}"

    /** "142 / 186" */
    fun ivTotal(ivs: Stats): String = "${ivs.total} / $IV_MAX_TOTAL"

    /** "HP 28 · Atk 12 · Def 25 ..." ([long]: full stat names; [skipZero]: only the stats above 0). */
    fun perStat(s: Stats, long: Boolean = false, skipZero: Boolean = false): String =
        com.husarp.ankigo.game.Stat.entries.filter { !skipZero || s[it] != 0 }
            .joinToString(" · ") { "${if (long) DetailMath.statName(it) else DetailMath.LABELS[it.ordinal]} ${s[it]}" }

    /** What the Matchups row says when closed: "weak to Rock ×4", "weak to Water, Ground ×2 +1", "no weaknesses". */
    fun matchupSummary(m: TypeMatchups): String {
        val top = m.weakTo.firstOrNull() ?: return "no weaknesses"
        val same = m.weakTo.filter { it.mult == top.mult }
        val names = same.take(2).joinToString(", ") { it.type.label }
        val more = (m.weakTo.size - minOf(2, same.size)).let { if (it > 0) " +$it" else "" }
        return "weak to $names ${top.label}$more"
    }

    /** "Others of its kind: Keen Eye · Gale Wings (hidden)": the abilities of [sp] except [current]; null when there is none. */
    fun otherAbilities(sp: Species, current: String?): String? {
        fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
        val own = current?.let(::norm)
        val list = sp.abilities.filter { norm(it) != own }.map { it } +
            listOfNotNull(sp.hiddenAbility?.takeIf { norm(it) != own }?.let { "$it (hidden)" })
        return list.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val REGIONS = listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea")

    /** "Gen 3 · Hoenn" (1 Kanto ... 9 Paldea); any other number is just "Gen N". */
    fun generation(gen: Int): String = REGIONS.getOrNull(gen - 1)?.let { "Gen $gen \u00B7 $it" } ?: "Gen $gen"

    /** What the species' learnset row says about when a move is learned: "Lv 12"; level 1 = "Start", level 0 (on evolving) = "Evo". */
    fun learnLevel(level: Int): String = when {
        level <= 0 -> "Evo"
        level == 1 -> "Start"
        else -> "Lv $level"
    }

    /** All abilities of a species (the Pokédex entry): "Static · Lightning Rod (hidden)"; null when none. */
    fun allAbilities(sp: Species): String? =
        (sp.abilities + listOfNotNull(sp.hiddenAbility?.let { "$it (hidden)" })).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** R18-G (PLAN 239): one thing the Holding row of the details shows: the held item or the Exp. Share. [sub] is its small line. */
class HoldLine(val kind: Kind, val item: String?, val name: String, val sub: String) {
    enum class Kind { ITEM, EXP_SHARE }
}

/**
 * R18-G (PLAN 239, picks 239B + 239S1): what the Holding row of the details says, pure. A Pokémon holds at most one item
 * ([Mon.heldItem]); the game also says the [xpShareUid] Pokémon "holds the Exp. Share", so that is a second line. The item's small
 * line is the Bag's row text ([ItemInfo.line]). "Nothing" and the "Give item" button show only when there is no line at all.
 */
object HoldingPlan {
    const val TITLE = "Holding"
    const val NOTHING = "Nothing"
    const val GIVE = "Give item"
    const val EXP_SHARE = "Exp. Share"
    const val EXP_SHARE_SUB = "It and your Main each get half the XP"
    /** The Exp. Share holder that is also the Main: Progression gives the whole XP to the Main then, nothing is shared. */
    const val EXP_SHARE_MAIN_SUB = "Does nothing while it is your Main"

    /** The lines of [mon]: its item first, then the Exp. Share when [mon] is the holder ([xpShareUid]); [mainUid] = the team's first. */
    fun lines(data: GameData?, mon: Mon, xpShareUid: String?, mainUid: String?): List<HoldLine> {
        val out = ArrayList<HoldLine>()
        mon.heldItem?.let { out += HoldLine(HoldLine.Kind.ITEM, it, Items.name(it), ItemInfo.line(data, it)) }
        if (xpShareUid != null && xpShareUid == mon.uid) {
            out += HoldLine(HoldLine.Kind.EXP_SHARE, null, EXP_SHARE, if (mon.uid == mainUid) EXP_SHARE_MAIN_SUB else EXP_SHARE_SUB)
        }
        return out
    }

    /** The "Give item" button: only when it holds nothing at all, and only in a window that has Options (the same picker as Options). */
    fun showGive(lines: List<HoldLine>, hasOptions: Boolean): Boolean = lines.isEmpty() && hasOptions

    /** The held items in the bag that [mon] could be given (what Options → "Give item…" lists); empty = it says "No held items in the bag". */
    fun giveable(state: GameState, mon: Mon): List<String> =
        state.bag.keys.filter { Items.def(it)?.kind == ItemKind.HELD && state.count(it) > 0 && it != mon.heldItem }
}

/**
 * R18-G (PLAN 243B): an evolution line with more than two targets from one Pokémon, all of them last forms (Eevee 8), wraps the
 * targets into a grid, [PER_ROW] per line, the method under each name. Two branches (Gloom, Slowpoke) and lines where a target
 * evolves on (Applin) stay the stacked rows.
 */
object EvoGrid {
    const val PER_ROW = 3
    const val MORE_THAN = 2

    /** True when the targets of [node] are shown as a grid. */
    fun isGrid(node: EvoNode): Boolean = node.children.size > MORE_THAN && node.children.all { it.children.isEmpty() }

    /** How many lines [targets] cells take. */
    fun lines(targets: Int): Int = (targets + PER_ROW - 1) / PER_ROW
}
