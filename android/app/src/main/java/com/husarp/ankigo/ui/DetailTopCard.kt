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

/**
 * R18-G (PLAN 243B + 239B / 239S1, docs/design/round17-details.html): the grey card right under the HP and XP bars of the Pokémon details.
 * It never folds. Your Pokémon's card holds the Holding row (its item and the Exp. Share) and, under a thin line, the Evolution; the
 * Pokédex entry and the wild Pokémon's window have the Evolution only. The whole title row of each part opens its explanation.
 */
internal object DetailTopCard {
    /** Your Pokémon's card: Holding, then Evolution. [give] = the "Give item" action (null = no Options in this window, so no button). */
    fun forMon(a: Context, data: GameData, state: GameState?, mon: Mon, sp: Species, seen: Set<Int>, compact: Boolean,
               page: GamePage?, give: (() -> Unit)?, onSpecies: ((Species) -> Unit)?): View =
        card(a, listOf(holding(a, data, state, mon, page, give), evolution(a, data, sp, seen, compact, onSpecies)))

    /** The Pokédex entry's / wild Pokémon's card: the Evolution only (a species holds nothing). */
    fun evolutionOnly(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onSpecies: ((Species) -> Unit)?): View =
        card(a, listOf(evolution(a, data, sp, seen, compact, onSpecies)))

    /** The card with its [parts] one under the other, a thin line between them. */
    private fun card(a: Context, parts: List<View>): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 14, a)
        setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), 0)
        parts.forEachIndexed { i, v ->
            if (i > 0) addView(DetailSections.divider(a), LinearLayout.LayoutParams(-1, 1))
            addView(v, LinearLayout.LayoutParams(-1, -2))
        }
    }

    // ---------- Holding ----------

    /** The Holding row: its title at the left (the whole row opens [StatInfo.HOLDING]), one line per thing it holds at the right. */
    private fun holding(a: Context, data: GameData, state: GameState?, mon: Mon, page: GamePage?, give: (() -> Unit)?): View {
        val lines = HoldingPlan.lines(data, mon, state?.settings?.xpShareUid, state?.team?.firstOrNull())
        val things = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        lines.forEach { things.addView(holdLine(a, it, page), LinearLayout.LayoutParams(-1, -2)) }
        if (lines.isEmpty()) things.addView(nothing(a, HoldingPlan.showGive(lines, give != null), give), LinearLayout.LayoutParams(-1, -2))
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = HoldingPlan.TITLE }, LinearLayout.LayoutParams(Ui.dp(a, 70), -2))
            addView(things, LinearLayout.LayoutParams(0, -2, 1f))
            DetailParts.tapRow(this, StatInfo.HOLDING)
        }
    }

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

    /** "Nothing", and when [showGive] the mauve "Give item" text button (48 dp to touch) at the right. */
    private fun nothing(a: Context, showGive: Boolean, give: (() -> Unit)?): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(a, DetailFormat.TAP_MIN_DP)
        addView(Ui.text(a, 14f, 900, R.color.ag_sub0).apply { text = HoldingPlan.NOTHING }, LinearLayout.LayoutParams(0, -2, 1f))
        if (showGive && give != null) addView(Ui.text(a, 13f, 900, R.color.ag_mauve).apply {
            text = HoldingPlan.GIVE
            gravity = Gravity.CENTER
            minHeight = Ui.dp(a, DetailFormat.TAP_MIN_DP)
            setPadding(Ui.dp(a, 8), 0, 0, 0)
            isClickable = true
            contentDescription = "Give an item to hold"
            setOnClickListener { give() }
        }, LinearLayout.LayoutParams(-2, -2))
    }

    /** The item's window, the same as a tap on it in the Bag (its texts; nothing to buy or use here). */
    private fun openItem(page: GamePage, item: String) {
        val w = ItemWindow(page) { }
        w.update(ItemCard.Spec(item))
        w.show()
    }

    // ---------- Evolution ----------

    /**
     * The Evolution part: the small "Evolution" title (the whole title row opens [StatInfo.EVOLUTION]) and the evolution line centred
     * under it; a Pokémon whose line is just itself gets one row, "Evolution   Does not evolve".
     */
    private fun evolution(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onSpecies: ((Species) -> Unit)?): View {
        if (EvoLine.of(data, sp).standalone) return DetailSections.fact(a, "Evolution", StatInfo.EVOLUTION, "Does not evolve", null, compact)
        val heading: TextView = Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Evolution"; gravity = Gravity.CENTER_VERTICAL }
        DetailParts.tapRow(heading, StatInfo.EVOLUTION)
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading, LinearLayout.LayoutParams(-1, -2))
            addView(DetailSections.evolution(a, data, sp, seen, compact, onSpecies), LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(a, 10) })
        }
    }
}
