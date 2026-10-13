package com.husarp.ankigo.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Tuning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TICKET T14 owns this file: the Details dialog of one Pokémon and the evolution texts shared with the Pokédex.

object MonDetail {
    private val STAT_LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

    /** "Lv 16", "Thunder Stone", "Linking Cord", "Friendship", "knows Mimic" (+ day/night, other conditions). */
    fun method(data: GameData, e: Evolution): String {
        val base = when (e.kind) {
            EvoKind.LEVEL -> if (e.level != null) "Lv ${e.level}" else "Level up"
            EvoKind.FRIENDSHIP -> "Friendship"
            EvoKind.ITEM -> e.item?.let { Items.name(it) } ?: "an item"
            EvoKind.TRADE -> Items.name(e.item ?: "linking-cord")
            EvoKind.LEVEL_MOVE -> "knows ${e.move?.let { data.move(it)?.name ?: it } ?: "a move"}"
            EvoKind.OTHER -> "Linking Cord"
        }
        val extra = listOfNotNull(
            e.time,
            e.moveType?.let { "${it.label}-type move" },
            e.condition?.takeIf { e.kind != EvoKind.OTHER },
        )
        return if (extra.isEmpty()) base else "$base, ${extra.joinToString(", ")}"
    }

    /** "Raichu (Thunder Stone)" for each evolution of [sp] that exists in the data. */
    fun evolvesInto(data: GameData, sp: Species): List<String> =
        sp.evolutions.filter { data.species.containsKey(it.to) }.map { "${data.sp(it.to).name} (${method(data, it)})" }

    /** "Evolves with a Thunder Stone" style one-liner, or "Fully evolved". */
    fun nextEvoLine(data: GameData, sp: Species): String {
        val e = sp.evolutions.firstOrNull { data.species.containsKey(it.to) } ?: return "Fully evolved"
        val to = data.sp(e.to).name
        return "Evolves into $to · ${method(data, e)}"
    }

    /** "Pichu (Friendship)" for the species this one evolves from, or null. */
    fun evolvesFrom(data: GameData, sp: Species): String? {
        val prevo = sp.prevo?.let { data.species[it] } ?: return null
        val e = prevo.evolutions.firstOrNull { it.to == sp.num }
        return if (e == null) prevo.name else "${prevo.name} (${method(data, e)})"
    }

    fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(ms))

    private fun line(a: Activity, label: String, value: String): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, 84), -2))
        addView(Ui.text(a, 12f, 800, R.color.ag_text).apply { text = value }, LinearLayout.LayoutParams(0, -2, 1f))
        setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2))
    }

    private fun heading(a: Activity, text: String): TextView = Ui.text(a, 11f, 900, R.color.ag_sub0).apply {
        this.text = text.uppercase()
        letterSpacing = 0.06f
        setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4))
    }

    fun show(a: Activity, data: GameData, mon: Mon) {
        val sp = data.sp(mon.species)
        val stats = StatCalc.stats(sp, mon)
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 6))
        }
        // header: big sprite, name, #num, types, Lv / gender / nature
        val img = ImageView(a)
        Ui.staticSprite(a, mon.species, mon.shiny, img)
        col.addView(img, LinearLayout.LayoutParams(Ui.dp(a, 96), Ui.dp(a, 96)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(Ui.text(a, 20f, 900, R.color.ag_text).apply {
            text = (if (mon.shiny) "★ " else "") + mon.name + "  " + Bits.genderSign(mon.gender)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply {
            text = "#%03d · Lv %d · %s".format(sp.num, mon.level, mon.nature.label)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Bits.typeRow(a, sp.types), LinearLayout.LayoutParams(-2, -2).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = Ui.dp(a, 6)
        })

        col.addView(heading(a, "About"))
        col.addView(line(a, "Ability", mon.ability.ifEmpty { "–" }.replaceFirstChar { it.uppercase() }))
        col.addView(line(a, "Held item", mon.heldItem?.let { Items.name(it) } ?: "none"))
        col.addView(line(a, "Friendship", mon.friendship.toString()))
        col.addView(line(a, "Caught", if (mon.caughtAt > 0) date(mon.caughtAt) else "–"))
        col.addView(line(a, "Defeats", mon.defeats.toString()))
        val xpLine = if (mon.level >= Tuning.MAX_LEVEL) "Max level" else "${mon.xp} / ${Progression.xpToNext(data, mon)} to Lv ${mon.level + 1}"
        col.addView(line(a, "XP", xpLine))

        // stats with IVs and EVs; nature plus/minus coloured
        col.addView(heading(a, "Stats · IV · EV"))
        val iv = mon.ivs.toList()
        val ev = mon.evs.toList()
        Stat.entries.forEachIndexed { i, s ->
            val tone = when {
                mon.nature.plus == s -> R.color.ag_green
                mon.nature.minus == s -> R.color.ag_red
                else -> R.color.ag_text
            }
            val value = stats[s]
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2))
                addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = STAT_LABELS[i] }, LinearLayout.LayoutParams(Ui.dp(a, 34), -2))
                addView(Ui.text(a, 12f, 900, tone).apply { text = value.toString(); gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(a, 34), -2))
                val bar = BarView(a)
                bar.set((value / 300f), Ui.c(a, R.color.ag_mauve), false)
                addView(bar, LinearLayout.LayoutParams(0, Ui.dp(a, 5), 1f).also {
                    it.marginStart = Ui.dp(a, 8); it.marginEnd = Ui.dp(a, 8)
                })
                addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = "${iv[i]} · ${ev[i]}" }, LinearLayout.LayoutParams(Ui.dp(a, 62), -2))
            })
        }

        col.addView(heading(a, "Moves"))
        if (mon.moves.isEmpty()) col.addView(line(a, "", "none"))
        for (m in mon.moves) {
            val row = Bits.moveRow(a, data, m)
            row.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4))
            col.addView(row)
        }

        col.addView(heading(a, "Evolution"))
        val from = evolvesFrom(data, sp)
        if (from != null) col.addView(line(a, "From", from))
        val into = evolvesInto(data, sp)
        if (into.isEmpty()) col.addView(line(a, "Into", "fully evolved"))
        else into.forEach { col.addView(line(a, "Into", it)) }

        Pickers.info(a, "Details", Bits.scroll(a, col))
    }
}
