package com.husarp.ankigo.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.ItemUse
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Progression

// TICKET T14 owns this file: the Bag tab of the Shop screen (owned items grouped, then TMs) and what a tap does.

internal class BagActions(private val s: GamePage, private val mons: MonActions) {
    private val data get() = s.data
    private val state get() = s.state

    private val groups = listOf(
        "Balls" to listOf(ItemKind.BALL),
        "Healing" to listOf(ItemKind.HEAL),
        "Evolution" to listOf(ItemKind.EVOLUTION),
        "Held" to listOf(ItemKind.HELD),
        "Fossils" to listOf(ItemKind.FOSSIL),
    )

    /** Fills [into] with the bag sections. */
    fun fill(into: LinearLayout) {
        val ctx = s
        val owned = state.bag.filter { it.value > 0 }.keys
        var any = false
        fun section(title: String, ids: List<String>) {
            if (ids.isEmpty()) return
            any = true
            into.addView(Bits.sect(ctx, title, "${ids.sumOf { state.count(it) }} items"))
            for (id in ids.sortedBy { Items.name(it) }) into.addView(itemRow(id))
        }
        for ((title, kinds) in groups) section(title, owned.filter { Items.def(it)?.kind in kinds })
        // escape items and anything the catalogue doesn't know
        section("Other", owned.filter { id -> groups.none { (_, k) -> Items.def(id)?.kind in k } })

        if (state.tms.isNotEmpty()) {
            any = true
            into.addView(Bits.sect(ctx, "TMs", "${state.tms.size} owned"))
            for (move in state.tms.sortedBy { data.move(it)?.name ?: it }) into.addView(tmRow(move))
        }
        if (!any) into.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply {
            text = "Your bag is empty. Items drop while you study, and the shop sells more."
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 4), 0)
        })
    }

    private fun itemRow(id: String): View {
        val ctx = s
        val n = state.count(id)
        val count = Ui.text(ctx, 13f, 900, R.color.ag_sub1).apply { text = "×$n" }
        val row = Bits.cardRow(ctx, Bits.itemTile(ctx, id), Bits.bodyText(ctx, Items.name(id), Items.def(id)?.note), count)
        row.setOnClickListener { tap(id) }
        return row
    }

    private fun tmRow(move: String): View {
        val ctx = s
        val m = data.move(move)
        val tile = Bits.textTile(ctx, "TM", m?.let { Ui.typeColor(it.type) } ?: Ui.c(ctx, R.color.ag_sub0))
        val mid = Bits.bodyText(ctx, Items.tmName(data, move), Bits.moveStats(data, move))
        if (m != null) mid.addView(Bits.typeRow(ctx, listOf(m.type)), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        val teach = Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply { text = "Teach" }
        val row = Bits.cardRow(ctx, tile, mid, teach)
        row.setOnClickListener { tapTm(move) }
        return row
    }

    // ---- what a tap does ----

    private fun tap(item: String) {
        val def = Items.def(item) ?: return
        when (def.kind) {
            ItemKind.HEAL -> {
                val team = state.teamMons()
                if (team.isEmpty()) return
                // main first (it is team[0]); HP shown so the choice is easy
                Pickers.pickMon(s, "${Items.name(item)} on…", data, team, { Bits.hpText(data, it) }) { mons.runUse(item, it.uid) }
            }
            ItemKind.EVOLUTION -> {
                val ok = candidates().filter { ItemUse.canUse(data, state, item, it) }
                if (ok.isEmpty()) { Ui.toast(s, "No Pokémon can use ${Items.name(item)} right now"); return }
                Pickers.pickMon(s, "${Items.name(item)} on…", data, ok, { mon -> evoHint(item, mon) }) { mons.useItem(it, item) }
            }
            ItemKind.HELD -> {
                val all = candidates().filter { it.heldItem != item }
                if (all.isEmpty()) { Ui.toast(s, "No Pokémon to give it to"); return }
                Pickers.pickMon(s, "Give ${Items.name(item)} to…", data, all, { mon ->
                    mon.heldItem?.let { "holds ${Items.name(it)}" } ?: "holds nothing"
                }) { mons.giveItemTo(it, item) }
            }
            ItemKind.FOSSIL -> {
                val sp = def.fossil?.let { data.species[it] }?.name ?: "a Pokémon"
                Pickers.confirm(s, "Revive fossil?", "Use ${Items.name(item)} to revive $sp (Lv 5)?", "Revive") { mons.runUse(item, null) }
            }
            ItemKind.BALL -> throwBall(item)
            ItemKind.ESCAPE -> escape(item)
        }
    }

    /** Team first, then the box (by species number). */
    private fun candidates(): List<Mon> = state.teamMons() + state.boxMons().sortedWith(compareBy({ it.species }, { it.level }))

    private fun evoHint(item: String, mon: Mon): String =
        Progression.itemEvolution(data, mon, item)?.let { "→ ${data.sp(it.to).name}" } ?: "Lv ${mon.level}"

    private fun throwBall(ball: String) {
        val battle = state.battle
        if (battle == null) { Ui.toast(s, "No wild Pokémon right now. Throw balls from a battle."); return }
        val wild = battle.wild
        val chance = Catching.ballChance(ball, data.sp(wild.species).types)
        Pickers.confirm(s, "Throw ${Items.name(ball)}?", "Throw at wild ${wild.name} (chance $chance%)?", "Throw") {
            val events = GameRepo.act(s) { it.throwBall(ball) }
            s.report(ballMessage(events, wild.name), events)
            s.requestRender()
        }
    }

    private fun ballMessage(events: List<GameEvent>, wildName: String): String {
        val throwEv = events.filterIsInstance<GameEvent.BallThrow>().firstOrNull() ?: return "Nothing happened."
        if (!throwEv.success) return "$wildName broke free! (${throwEv.chance}% chance, ${throwEv.left} left)"
        val caught = events.filterIsInstance<GameEvent.Caught>().firstOrNull()
        return "Gotcha! $wildName was caught!" + if (caught?.newSpecies == true) " New Pokédex entry!" else ""
    }

    private fun escape(item: String) {
        val battle = state.battle
        if (battle == null) { Ui.toast(s, "No wild Pokémon to escape from"); return }
        Pickers.confirm(s, "Use ${Items.name(item)}?", "The wild ${battle.wild.name} leaves and a new one appears.", "Use") {
            val events = GameRepo.act(s) { it.escape(item) }
            val fled = events.filterIsInstance<GameEvent.Fled>().firstOrNull()
            s.report(if (fled != null) "${fled.name} left." else "Used ${Items.name(item)}.", events)
            s.requestRender()
        }
    }

    private fun tapTm(move: String) {
        val able = candidates().filter { data.sp(it.species).tmMoves.contains(move) && move !in it.moves }
        if (able.isEmpty()) { Ui.toast(s, "None of your Pokémon can learn ${data.move(move)?.name ?: move}"); return }
        Pickers.pickMon(s, "Teach ${Items.tmName(data, move)} to…", data, able, { mon -> "${mon.moves.size}/4 moves" }) { mons.teach(it, move) }
    }
}
