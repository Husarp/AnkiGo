package com.husarp.ankigo.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.BallTrade
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.ItemUse
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Progression

// TICKET T14 owns this file: the Bag tab of the Shop screen (owned items grouped, then TMs) and what a tap does.
// R14 batch 2 (PLAN 181): a tap on a row opens its card in place ([ItemCard]); the card's button starts what a tap used to do at once.

internal class BagActions(private val s: GamePage, private val mons: MonActions, private val host: CardHost) {
    private val data get() = s.data
    private val state get() = s.state

    private val groups = listOf(
        "Balls" to listOf(ItemKind.BALL),
        "Healing" to listOf(ItemKind.HEAL),
        "Evolution" to listOf(ItemKind.EVOLUTION),
        "Held" to listOf(ItemKind.HELD),
        "Fossils" to listOf(ItemKind.FOSSIL),
        "Escape" to listOf(ItemKind.ESCAPE),
    )

    /** Fills [into] with the bag sections. */
    fun fill(into: LinearLayout) {
        val ctx = s
        val kept = host.open.key?.takeIf { host.open.keepEmpty && it.startsWith("bag:") }?.removePrefix("bag:") // Y5: the open card whose last ball a trade took stays, showing 0
        val owned = state.bag.filter { it.value > 0 }.keys + listOfNotNull(kept)
        var any = false
        fun section(title: String, ids: List<String>) {
            if (ids.isEmpty()) return
            any = true
            val n = ids.sumOf { state.count(it) }
            into.addView(Bits.sect(ctx, title, "$n item${if (n == 1) "" else "s"}"))
            for (id in ids.sortedBy { Items.name(it) }) into.addView(if (host.open.isOpen(CardKeys.bagItem(id))) itemCard(id) else itemRow(id), cardLp(host.open.key == CardKeys.bagItem(id)))
        }
        for ((title, kinds) in groups) section(title, owned.filter { Items.def(it)?.kind in kinds })
        // anything the catalogue doesn't know
        section("Other", owned.filter { id -> groups.none { (_, k) -> Items.def(id)?.kind in k } })

        if (state.tms.isNotEmpty()) {
            any = true
            into.addView(Bits.sect(ctx, "TMs", "${state.tms.size} owned"))
            for (move in state.tms.sortedBy { data.move(it)?.name ?: it }) into.addView(if (host.open.isOpen(CardKeys.bagTm(move))) tmCard(move) else tmRow(move), cardLp(host.open.key == CardKeys.bagTm(move)))
        }
        if (!any) into.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply {
            text = "Your bag is empty. Items drop while you study, and the shop sells more."
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 4), 0)
        })
    }

    /** A card sits 2 dp under the row above and 8 dp above the next; a folded row has its own 6 dp below (Bits.cardRow). */
    private fun cardLp(card: Boolean): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).also {
        if (card) { it.topMargin = Ui.dp(s, 2); it.bottomMargin = Ui.dp(s, 8) } else it.bottomMargin = Ui.dp(s, 6)
    }

    /** The count "×N" and the small ▾ at the right of a folded row (the card says it in words). */
    private fun rowEnd(count: String?): View = LinearLayout(s).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (count != null) addView(Ui.text(s, 13f, 900, R.color.ag_sub1).apply { text = count })
        addView(ItemCard.chevron(s, "▾"), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(s, 2) })
    }

    /** A folded row: icon, name, the item's line, "×N", ▾. A tap opens its card (it no longer acts). */
    private fun itemRow(id: String): View {
        val ctx = s
        val n = state.count(id)
        val line = ItemInfo.plain(ItemInfo.line(data, id))
        val row = Bits.cardRow(ctx, Bits.itemTile(ctx, id), Bits.bodyText(ctx, Items.name(id), line), rowEnd("×$n"))
        row.contentDescription = ItemCardPlan.foldedLabel("${Items.name(id)}, $line, $n owned")
        row.setOnClickListener { host.toggleCard(CardKeys.bagItem(id)) }
        return row
    }

    /** A folded TM row: name and type chip (no count: a TM is owned once). */
    private fun tmRow(move: String): View {
        val ctx = s
        val m = data.move(move)
        val tile = Bits.tmTile(ctx, m?.type)
        val mid = Bits.bodyText(ctx, Items.tmName(data, move), null)
        if (m != null) mid.addView(Bits.typeRow(ctx, listOf(m.type)), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        val row = Bits.cardRow(ctx, tile, mid, rowEnd(null))
        row.contentDescription = ItemCardPlan.foldedLabel("${Items.tmName(data, move)}, ${m?.type?.label.orEmpty()}")
        row.setOnClickListener { host.toggleCard(CardKeys.bagTm(move)) }
        return row
    }

    // ---- the opened cards ----

    /** The card of an owned item: its texts, the action button of its kind, and for a ball the trade-up. */
    private fun itemCard(id: String): View {
        val key = CardKeys.bagItem(id)
        val kind = Items.def(id)?.kind
        val none = state.count(id) <= 0 // Y5: a card kept open after a trade took the last ball
        val act = BagAct.of(kind)?.let { a -> ItemCard.Act(a.label, a.usableNow(state) && !none, if (none) "You have none" else a.offFight) { tap(id) } }
        val offer = if (kind == ItemKind.BALL) BallTrade.offer(state, id) else null
        val trade = offer?.let { o ->
            val confirming = host.open.confirming && offer.canTrade
            ItemCard.Trade(o, confirming, ItemCardPlan.tradeConfirm(state, o),
                onAsk = { host.open.confirmTrade(true); s.requestRender() },
                onCancel = { host.open.confirmTrade(false); s.requestRender() },
                onConfirm = { tradeUp(id) })
        }
        return ItemCard.build(s, ItemCard.Spec(id, act = act, trade = trade, onFold = { host.toggleCard(key) })).also { host.cardShown(it) }
    }

    private fun tmCard(move: String): View {
        val key = CardKeys.bagTm(move)
        val act = ItemCard.Act(BagAct.TEACH.label, true, null) { tapTm(move) }
        return ItemCard.build(s, ItemCard.Spec(null, move, act = act, onFold = { host.toggleCard(key) })).also { host.cardShown(it) }
    }

    /** One batch of the trade-up (PLAN 199): the confirm closes, the card stays open with the new counts. */
    private fun tradeUp(ball: String) {
        host.open.confirmTrade(false)
        host.open.keepWhenEmpty() // Y5: if this uses the last ball of the card, the card stays open showing 0
        s.act { g -> BallTrade.trade(g.state, ball) }
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
                val ok = candidates().filter { ItemUse.canUse(data, state, item, it) || Progression.itemEvolutionTimeNeeded(data, it, item) != null } // time-blocked ones stay, hinted "(night)"
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
        Progression.itemEvolutionAnyTime(data, mon, item)?.let { e ->
            "→ ${data.sp(e.to).name}" + (Progression.itemEvolutionTimeNeeded(data, mon, item)?.let { " ($it)" } ?: "")
        } ?: "Lv ${mon.level}"

    private fun throwBall(ball: String) {
        val battle = state.battle
        if (battle == null) { Ui.toast(s, "No wild Pokémon right now. Throw balls from a battle."); return }
        val wild = battle.wild
        val chance = Catching.ballChance(ball)
        Pickers.confirm(s, "Throw ${Items.name(ball)}?", "Throw at wild ${wild.name} (chance $chance%)? ${Items.MISS_RULE}", "Throw") {
            val events = GameRepo.act(s) { it.throwBall(ball) }
            s.report(ballMessage(events, wild.name), events)
            s.requestRender()
        }
    }

    private fun ballMessage(events: List<GameEvent>, wildName: String): String {
        val throwEv = events.filterIsInstance<GameEvent.BallThrow>().firstOrNull() ?: return "Nothing happened."
        if (!throwEv.success) return "$wildName broke free and ran away! (${throwEv.chance}% chance, ${throwEv.left} left)"
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
