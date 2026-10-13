package com.husarp.ankigo.ui

import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.ItemUse
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon

// TICKET T14 owns this file: the per-Pokémon action menu (team + box) and the item / TM flows behind it.
// The Bag tab reuses [useItem], [giveItemTo] and [teach].

internal class MonActions(private val s: GameScreen) {
    private val data get() = s.data
    private val state get() = s.state

    /** The action dialog of one Pokémon, from a team row or a box cell. */
    fun menu(mon: Mon) {
        val labels = mutableListOf<String>()
        val acts = mutableListOf<() -> Unit>()
        fun add(label: String, f: () -> Unit) { labels += label; acts += f }

        val idx = state.team.indexOf(mon.uid)
        val main = state.main
        if (idx >= 0) {
            if (idx > 0) add("Make main") { s.act { it.setMain(mon.uid) } }
            if (idx > 0) add("Move up") { s.act { it.moveInTeam(mon.uid, -1) } }
            if (idx < state.team.size - 1) add("Move down") { s.act { it.moveInTeam(mon.uid, 1) } }
            if (state.team.size > 1) add("Send to box") { s.act { it.toBox(mon.uid) } }
        } else if (state.team.size < 6) {
            add("Add to team") { s.act { it.toTeam(mon.uid) } }
            add("Make main") {
                s.act { g -> g.toTeam(mon.uid).let { r -> if (r.ok) g.setMain(mon.uid) else r } }
            }
        } else if (main != null) {
            // Team full: the main goes to the box and this one takes its place at the front.
            add("Make main (${main.name} goes to the box)") {
                s.act { g ->
                    g.toBox(main.uid).let { r1 ->
                        if (!r1.ok) r1 else g.toTeam(mon.uid).let { r2 -> if (r2.ok) g.setMain(mon.uid) else r2 }
                    }
                }
            }
        }
        add("Give item…") { giveItem(mon) }
        mon.heldItem?.let { held -> add("Take ${Items.name(held)}") { s.act { ItemUse.takeHeld(it.state, mon.uid) } } }
        add("Use item…") { useItem(mon) }
        add("Teach TM…") { teachMenu(mon) }
        if (mon.uid != main?.uid) {
            if (state.settings.xpShareUid == mon.uid) add("Exp. Share holder: turn off") { s.act { it.setXpShare(null) } }
            else add("Make Exp. Share holder") { s.act { it.setXpShare(mon.uid) } }
        }
        add("Details") { MonDetail.show(s, data, mon) }
        add("Release…") { release(mon) }
        Pickers.choose(s, "${if (mon.shiny) "★ " else ""}${mon.name}  Lv ${mon.level}", labels) { acts[it]() }
    }

    private fun release(mon: Mon) {
        Pickers.confirm(s, "Release ${mon.name}?", "${mon.name} (Lv ${mon.level}) will be gone for good.", "Release…") {
            Pickers.confirm(s, "Really release ${mon.name}?", "This can't be undone.", "Release") {
                s.act { it.release(mon.uid) }
            }
        }
    }

    // ---- held items ----

    private fun giveItem(mon: Mon) {
        val held = state.bag.keys.filter { Items.def(it)?.kind == ItemKind.HELD && state.count(it) > 0 && it != mon.heldItem }
            .sortedBy { Items.name(it) }
        if (held.isEmpty()) { Ui.toast(s, "No held items in the bag"); return }
        Pickers.pickItem(s, "Give to ${mon.name}", state, held) { giveItemTo(mon, it) }
    }

    fun giveItemTo(mon: Mon, item: String) { useItem(mon, item) }

    // ---- heal / evolution items ----

    /** "Use item…" for one Pokémon: lists the heal and evolution items that would work on it. */
    private fun useItem(mon: Mon) {
        val usable = state.bag.keys.filter {
            val kind = Items.def(it)?.kind
            (kind == ItemKind.HEAL || kind == ItemKind.EVOLUTION) && ItemUse.canUse(data, state, it, mon)
        }.sortedBy { Items.name(it) }
        if (usable.isEmpty()) { Ui.toast(s, "Nothing in the bag helps ${mon.name} right now"); return }
        Pickers.pickItem(s, "Use on ${mon.name}", state, usable) { useItem(mon, it) }
    }

    /** Uses [item] on [mon] (evolution items ask first). */
    fun useItem(mon: Mon, item: String) {
        val kind = Items.def(item)?.kind
        if (kind == ItemKind.EVOLUTION) {
            Pickers.confirm(s, "Use ${Items.name(item)}?", "Use ${Items.name(item)} on ${mon.name}?", "Use") { runUse(item, mon.uid) }
        } else {
            runUse(item, mon.uid)
        }
    }

    /** Runs ItemUse.use through GameRepo (also used for fossils: [uid] null). */
    fun runUse(item: String, uid: String?): ActionResult =
        s.act { g -> ItemUse.use(g.data, g.state, item, uid, g.rng, g.clock()) }

    // ---- TMs ----

    private fun teachMenu(mon: Mon) {
        val tms = tmsFor(mon)
        if (tms.isEmpty()) { Ui.toast(s, "${mon.name} can't learn any of your TMs"); return }
        Pickers.pickMoveId(s, "Teach ${mon.name}", data, tms) { teach(mon, it) }
    }

    /** Owned TMs this Pokémon's species can learn and doesn't know yet, by move name. */
    fun tmsFor(mon: Mon): List<String> {
        val sp = data.sp(mon.species)
        return state.tms.filter { it in sp.tmMoves && it !in mon.moves && data.move(it) != null }
            .sortedBy { data.move(it)?.name ?: it }
    }

    /** Teaches TM [move]; with 4 moves known it asks which one to forget. */
    fun teach(mon: Mon, move: String) {
        if (mon.moves.size < 4) {
            s.act { ItemUse.teachTm(it.data, it.state, move, mon.uid, null) }
        } else {
            val name = data.move(move)?.name ?: move
            Pickers.pickMove(s, "${mon.name} knows 4 moves. Forget one for $name?", data, mon) { i ->
                s.act { ItemUse.teachTm(it.data, it.state, move, mon.uid, i) }
            }
        }
    }
}
