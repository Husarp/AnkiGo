package com.husarp.ankigo.ui

import com.husarp.ankigo.R
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.ItemUse
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Looks
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.shownName

// TICKET T14 owns this file: the per-Pokémon action menu (team + box) and the item / TM flows behind it.
// The Bag tab reuses [useItem], [giveItemTo] and [teach].

internal class MonActions(private val s: GamePage) {
    private val data get() = s.data
    private val state get() = s.state

    /** The action menu of one Pokémon, from a team row or a box cell, as an [AppMenu] under [anchor]. */
    fun menu(mon: Mon, anchor: android.view.View, onPick: () -> Unit = {}) {
        val m = AppMenu(anchor).header("${if (mon.shiny) "★ " else ""}${mon.shownName}  Lv ${mon.level}")
        val acts = mutableListOf<() -> Unit>()
        fun add(label: String, @androidx.annotation.DrawableRes icon: Int, danger: Boolean = false, sub: String? = null, f: () -> Unit) {
            m.item(acts.size, label, icon, sub = sub, danger = danger); acts += f
        }

        val idx = state.team.indexOf(mon.uid)
        val main = state.main
        if (idx >= 0) {
            if (idx > 0) add("Make main", R.drawable.ic_star) { s.act { g -> g.setMain(mon.uid).let { r -> if (r.ok) r.copy(message = "") else r } } } // the change is visible: no toast
            if (state.team.size > 1) add("Send to box", R.drawable.ic_team) { s.act { it.toBox(mon.uid) } }
        } else if (state.team.size < 6) {
            add("Add to team", R.drawable.ic_team) { s.act { it.toTeam(mon.uid) } }
            add("Make main", R.drawable.ic_star) {
                s.act { g -> g.toTeam(mon.uid).let { r -> if (r.ok) g.setMain(mon.uid).let { m -> if (m.ok) m.copy(message = "") else m } else r } }
            }
        } else if (main != null) {
            // Team full: the main goes to the box and this one takes its place at the front.
            add("Make main (${main.shownName} goes to the box)", R.drawable.ic_star) {
                s.act { g ->
                    g.toBox(main.uid).let { r1 ->
                        if (!r1.ok) r1 else g.toTeam(mon.uid).let { r2 -> if (r2.ok) g.setMain(mon.uid).let { m -> if (m.ok) m.copy(message = "") else m } else r2 }
                    }
                }
            }
        }
        add("Give item…", R.drawable.ic_gift) { giveItem(mon) }
        mon.heldItem?.let { held -> add("Take ${Items.name(held)}", R.drawable.ic_gift) { s.act { ItemUse.takeHeld(it.state, mon.uid) } } }
        add("Use item…", R.drawable.ic_ball) { useItem(mon) }
        add("Teach TM…", R.drawable.ic_tm) { teachMenu(mon) }
        if (mon.uid != main?.uid) {
            if (state.settings.xpShareUid == mon.uid) add("Exp. Share holder: turn off", R.drawable.ic_up) { s.act { it.setXpShare(null) } }
            else add("Make Exp. Share holder", R.drawable.ic_up) { s.act { it.setXpShare(mon.uid) } }
        }
        if (BuddyWidgetPrefs.widgetIds(s).isNotEmpty()) add("Show on Buddy widget", R.drawable.ic_open) { showOnBuddyWidget(mon) }
        add("Compare", R.drawable.ic_compare) {
            // inside the Team tab (its own page, or the Pokédex section it hosts) the picking starts right there
            val host: PickHost? = s as? TeamPage ?: (s as? DexPage)?.pickHost
            if (host != null) host.startPickWith(mon.uid)
            else s.startScreen(android.content.Intent(s, TeamActivity::class.java).putExtra(TeamActivity.EXTRA_PICK_UID, mon.uid))
        }
        if (Looks.hasChoice(data, mon)) add("Change look…", R.drawable.ic_palette, sub = "Pick a picture from its line") { changeLook(mon) }
        if (mon.favourite) add("Remove from favourites", R.drawable.ic_heart) { s.act { it.setFavourite(mon.uid, false) } }
        else add("Add to favourites", R.drawable.ic_heart_outline) { s.act { it.setFavourite(mon.uid, true) } }
        add("Rename…", R.drawable.ic_pencil) { rename(mon) }
        if (mon.favourite) { m.item(acts.size, "Release…", R.drawable.ic_trash, enabled = false, sub = "Remove ${mon.shownName} from favourites first.", danger = true); acts += {} }
        else add("Release…", R.drawable.ic_trash, danger = true) { release(mon) }
        m.show { onPick(); acts[it]() }
    }

    /** "Change look…": the 8B popup; the pick is saved through the game (purely visual) and every screen refreshes. */
    private fun changeLook(mon: Mon) {
        LookPicker.show(s, data, mon) { pick -> s.act { g -> g.setLook(mon.uid, pick).let { r -> if (r.ok) r.copy(message = "") else r } } }
    }

    /** Puts [mon] on the Buddy widget(s): with several widgets they all get it (each can be changed on its own from the widget's Reconfigure). */
    private fun showOnBuddyWidget(mon: Mon) {
        val ids = BuddyWidgetPrefs.widgetIds(s)
        if (ids.isEmpty()) { Ui.toast(s, "No Buddy widget on your home screen"); return }
        BuddyWidgetPrefs.setAll(s.getSharedPreferences("spike", 0), ids, mon.uid)
        com.husarp.ankigo.Widgets.refreshAll(s, study = false)
        Ui.toast(s, if (ids.size == 1) "${mon.shownName} is on the Buddy widget" else "${mon.shownName} is on all ${ids.size} Buddy widgets")
    }

    /** The rename dialog (menu "Rename…" and the pencil in the details); [onDone] runs after a rename was saved. */
    @JvmOverloads
    fun rename(mon: Mon, onDone: () -> Unit = {}) {
        Pickers.input(s, "Rename ${mon.shownName}", mon.name, mon.nickname.orEmpty(), Tuning.NICKNAME_MAX,
            neutral = if (mon.nickname != null) "Use species name" to { s.act { it.rename(mon.uid, "") }; onDone() } else null) { text ->
            s.act { it.rename(mon.uid, text) }
            onDone()
        }
    }

    private fun release(mon: Mon) {
        Pickers.confirm(s, "Release ${mon.shownName}?", "${mon.shownName} (Lv ${mon.level}) will be gone for good.", "Release…") {
            Pickers.confirm(s, "Really release ${mon.shownName}?", "This can't be undone.", "Release") {
                s.act { it.release(mon.uid) }
            }
        }
    }

    // ---- held items ----

    private fun giveItem(mon: Mon) {
        val held = state.bag.keys.filter { Items.def(it)?.kind == ItemKind.HELD && state.count(it) > 0 && it != mon.heldItem }
            .sortedBy { Items.name(it) }
        if (held.isEmpty()) { Ui.toast(s, "No held items in the bag"); return }
        Pickers.pickItem(s, "Give to ${mon.shownName}", state, held) { giveItemTo(mon, it) }
    }

    fun giveItemTo(mon: Mon, item: String) { useItem(mon, item) }

    // ---- heal / evolution items ----

    /** "Use item…" for one Pokémon: lists the heal and evolution items that would work on it. */
    private fun useItem(mon: Mon) {
        val usable = state.bag.keys.filter {
            val kind = Items.def(it)?.kind
            (kind == ItemKind.HEAL || kind == ItemKind.EVOLUTION) && ItemUse.canUse(data, state, it, mon)
        }.sortedBy { Items.name(it) }
        if (usable.isEmpty()) { Ui.toast(s, "Nothing in the bag helps ${mon.shownName} right now"); return }
        Pickers.pickItem(s, "Use on ${mon.shownName}", state, usable) { useItem(mon, it) }
    }

    /** Uses [item] on [mon] (evolution items ask first). */
    fun useItem(mon: Mon, item: String) {
        val kind = Items.def(item)?.kind
        if (kind == ItemKind.EVOLUTION) {
            Pickers.confirm(s, "Use ${Items.name(item)}?", "Use ${Items.name(item)} on ${mon.shownName}?", "Use") { runUse(item, mon.uid) }
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
        if (tms.isEmpty()) { Ui.toast(s, "${mon.shownName} can't learn any of your TMs"); return }
        Pickers.pickMoveId(s, "Teach ${mon.shownName}", data, tms) { teach(mon, it) }
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
            Pickers.pickMove(s, "${mon.shownName} knows 4 moves. Forget one for $name?", data, mon) { i ->
                s.act { ItemUse.teachTm(it.data, it.state, move, mon.uid, i) }
            }
        }
    }
}
