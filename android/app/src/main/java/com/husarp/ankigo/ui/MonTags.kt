package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Type
import java.time.Instant
import java.time.ZoneId

/** Tags of one owned Pokémon, in priority order (most useful first). */
enum class TagKind { MAIN, FAINTED, CAN_EVOLVE, NEW, EXP_SHARE, HELD, SHINY, REGIONAL,
    /** Only in the details header ([MonTags.detailTags]): in the team / a favourite (the tiles show these two as marks). */
    TEAM, FAVOURITE }

/** [item] is set for HELD (the held item id, for its icon). */
data class MonTag(val kind: TagKind, val label: String, val item: String? = null)

object MonTags {
    /** The day key like Game.dayKey: a game day starts at settings.dayStartHour. */
    private fun dayKey(state: GameState, ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .minusHours(state.settings.dayStartHour.coerceIn(0, 23).toLong()).toLocalDate().toString()

    /** True when [mon] was caught during the current game day: today's NEW rule (before "opened" is taken into account). */
    fun caughtToday(state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): Boolean =
        mon.caughtAt != 0L && dayKey(state, mon.caughtAt) == dayKey(state, now)

    fun regionalLabel(form: String): String = when (form.substringBefore('-').lowercase()) {
        "alola" -> "Alolan"
        "galar" -> "Galarian"
        "hisui" -> "Hisuian"
        "paldea" -> "Paldean"
        else -> form.substringBefore('-').replaceFirstChar { it.uppercase() }
    }

    /** True when [mon] can evolve right now: by level, by friendship or with an evolution item in the bag. */
    fun canEvolve(data: GameData, state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): Boolean =
        Progression.levelEvolution(data, state, mon, now) != null ||
            Progression.friendshipEvolution(data, state, mon, now) != null ||
            state.bag.any { (item, n) ->
                n > 0 && Items.def(item)?.kind == ItemKind.EVOLUTION && Progression.itemEvolution(data, mon, item) != null
            }

    fun of(data: GameData, state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): List<MonTag> {
        val out = ArrayList<MonTag>()
        if (state.team.firstOrNull() == mon.uid) out += MonTag(TagKind.MAIN, "Main")
        if (mon.hp <= 0) out += MonTag(TagKind.FAINTED, "Fainted")
        if (canEvolve(data, state, mon, now)) out += MonTag(TagKind.CAN_EVOLVE, "Can evolve")
        if (mon.caughtAt != 0L && dayKey(state, mon.caughtAt) == dayKey(state, now)) out += MonTag(TagKind.NEW, "New")
        if (state.settings.xpShareUid == mon.uid) out += MonTag(TagKind.EXP_SHARE, "Exp. Share")
        mon.heldItem?.let { out += MonTag(TagKind.HELD, "Holds ${Items.name(it)}", it) }
        if (mon.shiny) out += MonTag(TagKind.SHINY, "★ Shiny")
        data.sp(mon.species).form?.let { out += MonTag(TagKind.REGIONAL, regionalLabel(it)) }
        return out
    }

    /** The order of the details header tags (R10-W2-D): the tiles' marks first (Main, Team, Exp. Share, Favourite), then the states. */
    private val DETAIL_ORDER = listOf(
        TagKind.MAIN, TagKind.TEAM, TagKind.EXP_SHARE, TagKind.FAVOURITE, TagKind.FAINTED, TagKind.CAN_EVOLVE,
        TagKind.NEW, TagKind.HELD, TagKind.SHINY, TagKind.REGIONAL,
    )

    /** [of] plus Team (in the team, the main too) and Favourite, in [DETAIL_ORDER]: what the details header shows. */
    fun detailTags(data: GameData, state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): List<MonTag> {
        val out = ArrayList(of(data, state, mon, now))
        if (mon.uid in state.team) out += MonTag(TagKind.TEAM, "Team")
        if (mon.favourite) out += MonTag(TagKind.FAVOURITE, "Favourite")
        // the star is the tag's icon here, so the name is just "Shiny"
        return out.map { if (it.kind == TagKind.SHINY) it.copy(label = "Shiny") else it }.sortedBy { DETAIL_ORDER.indexOf(it.kind) }
    }

    @ColorRes private fun tone(kind: TagKind): Int = when (kind) {
        TagKind.MAIN -> R.color.ag_mauve
        TagKind.FAINTED -> R.color.ag_red
        TagKind.CAN_EVOLVE -> R.color.ag_green
        TagKind.NEW -> R.color.ag_sky
        TagKind.EXP_SHARE -> R.color.ag_teal
        TagKind.HELD -> 0
        TagKind.SHINY -> R.color.ag_yellow
        TagKind.REGIONAL -> R.color.ag_lav
        TagKind.TEAM -> R.color.ag_mauve
        TagKind.FAVOURITE -> R.color.ag_pink
    }

    fun pill(ctx: Context, tag: MonTag): View {
        val text = Ui.pill(ctx, tag.label.uppercase(), tone(tag.kind))
        text.maxLines = 1
        val item = tag.item
        if (tag.kind != TagKind.HELD || item == null) return text
        // neutral pill with the item icon before the label
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_s0), 7, ctx)
        }
        val img = ImageView(ctx)
        Ui.itemIcon(ctx, item, img)
        box.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)).also { it.marginStart = Ui.dp(ctx, 6) })
        text.background = null
        text.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 7), Ui.dp(ctx, 3))
        box.addView(text, LinearLayout.LayoutParams(-2, -2))
        return box
    }

    fun row(ctx: Context, tags: List<MonTag>, max: Int = 3): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        tags.take(max).forEachIndexed { i, t ->
            addView(pill(ctx, t), LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.marginStart = Ui.dp(ctx, 4) })
        }
    }
}

/** What kind of mark a tag is (PLAN 57): the look ticket styles each kind its own way. */
enum class MarkKind { STATE, ITEM, RARITY, FORM }

/** One mark on the redone card: [icon] is a key for the look ticket's drawn / existing icon, [label] the long-press text, [item] the held item id. */
data class MonMark(val tag: TagKind, val kind: MarkKind, val icon: String, val label: String, val item: String? = null)

/**
 * The redone card (PLAN 57): [types] have their own spot (never among the tags), [isNew] is the corner badge, [tags] are the
 * other marks as icons in today's priority order. Nothing draws this yet; [MonTags.of] / pill / row keep today's look.
 */
data class MonMarks(val types: List<Type>, val isNew: Boolean, val tags: List<MonMark>) {
    companion object {
        /**
         * [seen] = uids the player has already opened (the screen keeps that set; this stays pure): NEW is today's rule
         * (caught during this game day, [MonTags.of]) and goes away once the Pokémon is in [seen].
         */
        fun of(data: GameData, state: GameState, mon: Mon, now: Long = System.currentTimeMillis(), seen: Set<String> = state.viewedMons): MonMarks {
            val all = MonTags.of(data, state, mon, now)
            val marks = all.filter { it.kind != TagKind.NEW }.map { t ->
                when (t.kind) {
                    TagKind.MAIN -> MonMark(t.kind, MarkKind.STATE, "main", "Main Pokémon")
                    TagKind.FAINTED -> MonMark(t.kind, MarkKind.STATE, "fainted", "Fainted")
                    TagKind.CAN_EVOLVE -> MonMark(t.kind, MarkKind.STATE, "can-evolve", "Can evolve")
                    TagKind.EXP_SHARE -> MonMark(t.kind, MarkKind.ITEM, "exp-share", "Exp. Share")
                    TagKind.HELD -> MonMark(t.kind, MarkKind.ITEM, "held", t.label, t.item)
                    TagKind.SHINY -> MonMark(t.kind, MarkKind.RARITY, "shiny", "Shiny")
                    TagKind.REGIONAL -> MonMark(t.kind, MarkKind.FORM, "regional", "${t.label} form")
                    TagKind.NEW -> error("NEW is the corner badge, not a mark")
                    TagKind.TEAM -> MonMark(t.kind, MarkKind.STATE, "team", "In team")
                    TagKind.FAVOURITE -> MonMark(t.kind, MarkKind.STATE, "favourite", "Favourite")
                }
            }
            val isNew = all.any { it.kind == TagKind.NEW } && mon.uid !in seen
            return MonMarks(data.sp(mon.species).types, isNew, marks)
        }
    }
}

/**
 * The marks a Box / Pokédex tile shows (R4-V5, 6A in Adam's version): ONLY these six, in this order everywhere. Held item,
 * can evolve, fainted and regional form are not on tiles (the details show them). Shiny is a golden glow behind the sprite on a
 * tile (a ★ on the Team row), never a ★ icon on a tile.
 */
enum class TileMark(val label: String, val legend: String) {
    NEW("New", "Caught today and not opened yet"),
    FAVOURITE("Favourite", "A favourite"),
    SHINY("Shiny", "Shiny: a golden glow behind the sprite"),
    IN_TEAM("In team", "In your team"),
    MAIN("Main", "Your main Pokémon (first in the team)"),
    EXP_SHARE("Exp. Share", "Holds the Exp. Share: gets half the XP"),
}

object TileMarks {
    /** NEW = caught during this game day and not opened yet (today's NEW rule + [GameState.viewedMons]). */
    fun isNew(state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): Boolean =
        mon.uid !in state.viewedMons && MonTags.caughtToday(state, mon, now)

    /**
     * The marks of a tile of [members] (one Pokémon, or the copies of a group): a mark shows when any copy has it, so grouping never
     * hides a shiny / favourite / team member. Always in [TileMark] order.
     */
    fun of(state: GameState, members: List<Mon>, now: Long = System.currentTimeMillis()): List<TileMark> {
        val out = ArrayList<TileMark>()
        if (members.any { isNew(state, it, now) }) out += TileMark.NEW
        if (members.any { it.favourite }) out += TileMark.FAVOURITE
        if (members.any { it.shiny }) out += TileMark.SHINY
        if (members.any { it.uid in state.team }) out += TileMark.IN_TEAM
        val main = state.team.firstOrNull()
        if (main != null && members.any { it.uid == main }) out += TileMark.MAIN
        val share = state.settings.xpShareUid
        if (share != null && members.any { it.uid == share }) out += TileMark.EXP_SHARE
        return out
    }
}
