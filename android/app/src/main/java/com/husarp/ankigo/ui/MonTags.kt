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
import java.time.Instant
import java.time.ZoneId

/** Tags of one owned Pokémon, in priority order (most useful first). */
enum class TagKind { MAIN, FAINTED, CAN_EVOLVE, NEW, EXP_SHARE, HELD, SHINY, REGIONAL }

/** [item] is set for HELD (the held item id, for its icon). */
data class MonTag(val kind: TagKind, val label: String, val item: String? = null)

object MonTags {
    /** The day key like Game.dayKey: a game day starts at settings.dayStartHour. */
    private fun dayKey(state: GameState, ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .minusHours(state.settings.dayStartHour.coerceIn(0, 23).toLong()).toLocalDate().toString()

    fun regionalLabel(form: String): String = when (form.substringBefore('-').lowercase()) {
        "alola" -> "Alolan"
        "galar" -> "Galarian"
        "hisui" -> "Hisuian"
        "paldea" -> "Paldean"
        else -> form.substringBefore('-').replaceFirstChar { it.uppercase() }
    }

    fun of(data: GameData, state: GameState, mon: Mon, now: Long = System.currentTimeMillis()): List<MonTag> {
        val out = ArrayList<MonTag>()
        if (state.team.firstOrNull() == mon.uid) out += MonTag(TagKind.MAIN, "Main")
        if (mon.hp <= 0) out += MonTag(TagKind.FAINTED, "Fainted")
        val canEvolve = Progression.levelEvolution(data, state, mon, now) != null ||
            Progression.friendshipEvolution(data, state, mon, now) != null ||
            state.bag.any { (item, n) ->
                n > 0 && Items.def(item)?.kind == ItemKind.EVOLUTION && Progression.itemEvolution(data, mon, item) != null
            }
        if (canEvolve) out += MonTag(TagKind.CAN_EVOLVE, "Can evolve")
        if (mon.caughtAt != 0L && dayKey(state, mon.caughtAt) == dayKey(state, now)) out += MonTag(TagKind.NEW, "New")
        if (state.settings.xpShareUid == mon.uid) out += MonTag(TagKind.EXP_SHARE, "Exp. Share")
        mon.heldItem?.let { out += MonTag(TagKind.HELD, "Holds ${Items.name(it)}", it) }
        if (mon.shiny) out += MonTag(TagKind.SHINY, "★ Shiny")
        data.sp(mon.species).form?.let { out += MonTag(TagKind.REGIONAL, regionalLabel(it)) }
        return out
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
