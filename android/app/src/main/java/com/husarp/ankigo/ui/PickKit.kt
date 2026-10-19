package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.husarp.ankigo.R

/** The Pokémon ticked for Compare, in tick order; any number (a [max] only for callers that want one). Owned and Pokédex picks are different keys. */
class PickSet(private val max: Int = Int.MAX_VALUE, private val min: Int = Compare.MIN) {
    enum class Result { ADDED, REMOVED, FULL }

    private val items = LinkedHashSet<ComparePick>()

    val size: Int get() = items.size
    val picks: List<ComparePick> get() = items.toList()
    val canCompare: Boolean get() = items.size >= min

    fun has(p: ComparePick): Boolean = p in items

    /** Ticks [p], or unticks it when it was ticked. A new tick beyond the maximum changes nothing and returns FULL. */
    fun toggle(p: ComparePick): Result = when {
        items.remove(p) -> Result.REMOVED
        items.size >= max -> Result.FULL
        else -> { items.add(p); Result.ADDED }
    }

    fun clear() = items.clear()

    /** Drops owned picks whose Pokémon is gone (released, traded away). */
    fun keepOwned(exists: (String) -> Boolean) {
        items.removeAll { it is ComparePick.Owned && !exists(it.uid) }
    }
}

/** What the Pokédex section needs from the Pokémon tab that hosts it while Compare picking is on. */
interface PickHost {
    val picking: Boolean
    fun isPicked(p: ComparePick): Boolean
    fun togglePick(p: ComparePick)

    /** Compare from a Pokémon's menu: picking mode on with [uid] already ticked. */
    fun startPickWith(uid: String)
}

/** The sections of the Pokémon tab (the Team tab until round 4). The Shop left it for a tab of its own in round 10: an old "SHOP" name is [Nav.OLD_SHOP_SECTION], which [of] answers with Team. */
enum class TeamSection(val label: String, val icon: Int) {
    TEAM("Team", R.drawable.ic_team),
    BOX("Box", R.drawable.ic_grid),
    DEX("Pokédex", R.drawable.ic_dex);

    companion object {
        fun of(name: String?): TeamSection = entries.firstOrNull { it.name == name } ?: TEAM
    }
}

/** Looks shared by team rows, box cells and Pokédex cells in pick mode. */
internal object PickKit {
    /** Card background; a picked one is tinted with the accent and gets a 3 dp accent outline, so it stands out from the others. */
    fun cardBg(ctx: Context, radiusDp: Int, picked: Boolean): Drawable {
        val card = Ui.c(ctx, R.color.ag_card)
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        return Ui.rounded(if (picked) Bits.blend(mauve, card, 0.24f) else card, radiusDp, ctx).apply {
            if (picked) setStroke(Ui.dp(ctx, 3), mauve)
        }
    }

    fun badge(ctx: Context): ImageView = ImageView(ctx).apply { visibility = View.GONE }

    /** Shows the tick badge while picking: ticked = accent, empty = grey outline. */
    fun bindBadge(img: ImageView, picking: Boolean, picked: Boolean) {
        val ctx = img.context
        img.visibility = if (picking) View.VISIBLE else View.GONE
        if (picked) {
            // ticked: the empty box's square filled with the accent (the box fills 18 of the icon's 24 units, so inset 1/8 per side) + a tick
            img.setImageResource(R.drawable.ic_checkbox_tick)
            img.setColorFilter(Ui.c(ctx, R.color.ag_crust), PorterDuff.Mode.SRC_IN)
            img.background = android.graphics.drawable.InsetDrawable(Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 3, ctx), .125f)
            img.setPadding(0, 0, 0, 0)
        } else {
            img.setImageResource(R.drawable.ic_checkbox_off)
            img.setColorFilter(Ui.c(ctx, R.color.ag_ov), PorterDuff.Mode.SRC_IN)
            img.background = null
            img.setPadding(0, 0, 0, 0)
        }
    }
}
