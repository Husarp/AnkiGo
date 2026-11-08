package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species

/** Evolution and held-item sections for Pokémon details. */
internal object DetailTopCard {
    fun forMon(a: Context, data: GameData, state: GameState?, mon: Mon, sp: Species, seen: Set<Int>, compact: Boolean,
               page: GamePage?, onSpecies: ((Species) -> Unit)?, onEvolved: (() -> Unit)? = null): View =
        evolution(a, data, sp, seen, compact, onSpecies, state, mon, page, onEvolved)

    fun evolutionOnly(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onSpecies: ((Species) -> Unit)?, forceOpen: Boolean = false): View =
        evolution(a, data, sp, seen, compact, onSpecies,
            state = com.husarp.ankigo.GameRepo.state.takeIf { com.husarp.ankigo.GameRepo.data === data }, forceOpen = forceOpen)

    fun heldItem(a: Context, data: GameData, mon: Mon, page: GamePage?): View? {
        val item = mon.heldItem ?: return null
        lateinit var section: View
        section = DetailSections.section(a, DetailSections.memory(a), DetailSection.HELD_ITEM, com.husarp.ankigo.game.Items.name(item)) {
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(holdLine(a, HoldingPlan.lines(data, mon, null, null).single(), page))
                if (page != null) HeldItemActions.buttons(page, item) {
                    if (mon.heldItem != item) section.visibility = View.GONE
                }?.let { addView(it, LinearLayout.LayoutParams(-1, -2)) }
            }
        }
        return section
    }

    fun expShare(a: Context, data: GameData, state: GameState?, mon: Mon): View? =
        HoldingPlan.lines(data, mon, state?.settings?.xpShareUid, state?.team?.firstOrNull())
            .firstOrNull { it.kind == HoldLine.Kind.EXP_SHARE }?.let { holdLine(a, it, null) }

    /** One thing it holds: icon, name, the small line under it; the item also has a chevron and opens its item window ([page] null = no window). */
    private fun holdLine(a: Context, line: HoldLine, page: GamePage?): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(a, DetailFormat.TAP_MIN_DP)
        val icon = ImageView(a).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        if (line.kind == HoldLine.Kind.ITEM) Ui.itemIcon(a, line.item.orEmpty(), icon) else icon.apply {
            setImageResource(R.drawable.ic_exp_share)
            setColorFilter(Ui.c(a, R.color.ag_teal), PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(icon, LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)))
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = line.name })
            addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = line.sub }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 2) })
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 8) })
        val item = line.item
        if (item != null && page != null) {
            addView(ImageView(a).apply {
                setImageResource(R.drawable.ic_chevron)
                setColorFilter(Ui.c(a, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)).also { it.marginStart = Ui.dp(a, 4) })
            isClickable = true
            background = DetailParts.rowRipple(a)
            contentDescription = "${line.name}, ${line.sub}. Tap for what it does"
            setOnClickListener { openItem(page, item) }
        } else contentDescription = "${line.name}, ${line.sub}"
    }

    /** The item's window, the same as a tap on it in the Bag (its texts; nothing to buy or use here). */
    private fun openItem(page: GamePage, item: String) {
        val w = ItemWindow(page) { }
        w.update(ItemCard.Spec(item))
        w.show()
    }

    // ---------- Evolution ----------

    /**
     * The collapsible evolution tree; owned Pokémon can choose ready branches from their requirement info.
     */
    private fun evolution(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onSpecies: ((Species) -> Unit)?, state: GameState? = null, mon: Mon? = null, page: GamePage? = null, onEvolved: (() -> Unit)? = null, forceOpen: Boolean = false): View {
        val line = EvoLine.of(data, sp)
        val canEvolve = sp.evolutions.any { data.species.containsKey(it.to) }
        return DetailSections.section(a, DetailSections.memory(a), DetailSection.EVOLUTION, null,
            title = if (canEvolve) "Evolution" else "Does not evolve", forceOpen = forceOpen, expandable = canEvolve) {
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                val evolve: ((com.husarp.ankigo.game.Evolution) -> Unit)? =
                    if (state != null && mon != null && page != null && state.mon(mon.uid) != null) {
                        { e ->
                            page.act(beforeReport = { result -> if (result.ok) onEvolved?.invoke() }) { g -> g.chooseEvolution(mon.uid, e.to) }
                        }
                    } else null
                addView(EvolutionRows(a, data, line.root, sp, seen, onSpecies, state, mon, evolve), LinearLayout.LayoutParams(-1, -2))
            }
        }
    }
}
