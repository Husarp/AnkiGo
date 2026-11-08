package com.husarp.ankigo.ui

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
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.ownedItemCount

// TICKET T14 owns this file: the Bag tab of the Shop screen (owned items grouped, then TMs) and what a tap does.
// R14 batch 2 (PLAN 181): a tap on a row opens its card ([ItemCard]); the card's button starts what a tap used to do at once.
// R15 (PLAN 210): the card is the item WINDOW ([ItemWindow]) over the list, and the button closes it just before it opens a picker or a confirm (an answer that is only a message keeps it open).

internal class BagActions(private val s: GamePage, private val mons: MonActions, private val host: CardHost,
                          private val onBattleEvents: ((List<GameEvent>) -> Unit)? = null,
                          private val fightAvailable: () -> Boolean = { true }) {
    private val data get() = s.data
    private val state get() = s.state

    private var heldOnly = false
    private var category = "All items"
    private var categoriesOpen = false
    private fun holders(id: String) = state.mons.filter { it.heldItem == id }

    private val groups = listOf(
        "Balls" to listOf(ItemKind.BALL),
        "Healing" to listOf(ItemKind.HEAL),
        "Evolution" to listOf(ItemKind.EVOLUTION),
        "Held" to listOf(ItemKind.HELD),
        "Fossils" to listOf(ItemKind.FOSSIL),
        "Escape" to listOf(ItemKind.ESCAPE),
        "Streak protection" to listOf(ItemKind.AUTO),
    )

    /** Fills [into] with the bag sections. */
    fun fill(into: LinearLayout) {
        val ctx = s
        val owned = (state.bag.filter { it.value > 0 }.keys + state.mons.mapNotNull { it.heldItem })
            .distinct().filter { !heldOnly || holders(it).isNotEmpty() }
        fun ids(label: String) = owned.filter { id ->
            val kinds = groups.firstOrNull { it.first == label }?.second
            if (label == "All items") true else if (kinds != null) Items.def(id)?.kind in kinds
            else label == "Other" && groups.none { (_, k) -> Items.def(id)?.kind in k }
        }
        fun count(label: String): Int = ids(label).sumOf { state.ownedItemCount(it) } +
            if (!heldOnly && label in listOf("All items", "TMs")) state.tms.size else 0
        val scope = LinearLayout(ctx).apply { tag = "bag-scope" }
        for (held in listOf(false, true)) {
            val label = if (held) "Held" else "All"
            scope.addView(Ui.text(ctx, 13f, 900, if (heldOnly == held) R.color.ag_base else R.color.ag_sub0).apply {
                text = label; contentDescription = label; gravity = android.view.Gravity.CENTER
                SubtabIcons.apply(this, if (held) R.drawable.ic_heart else R.drawable.ic_item_bag)
                minimumHeight = Ui.dp(ctx, 48); isSelected = heldOnly == held
                if (isSelected) background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx)
                setOnClickListener { heldOnly = held; s.requestRender() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        into.addView(scope)
        into.addView(Ui.text(ctx, 13f, 800, R.color.ag_mauve).apply {
            text = "${if (categoriesOpen) "‹" else "☷"}  $category · ${count(category)}  ${if (categoriesOpen) "Hide categories" else "Categories ›"}"
            contentDescription = if (categoriesOpen) "Collapse categories" else "Expand categories"
            minimumHeight = Ui.dp(ctx, 48); gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { categoriesOpen = !categoriesOpen; s.requestRender() }
        })
        val body = LinearLayout(ctx).apply { tag = "bag-body"; isBaselineAligned = false }
        into.addView(body, LinearLayout.LayoutParams(-1, -2))
        val categories = listOf("All items" to null, "Balls" to "poke-ball", "Healing" to "potion",
            "Evolution" to "water-stone", "Held" to "amulet-coin", "Fossils" to "helix-fossil",
            "Escape" to "fluffy-tail", "Other" to "rare-candy", "TMs" to "tm")
        if (categoriesOpen) body.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; tag = "bag-categories"
            for ((label, item) in categories) addView(LinearLayout(ctx).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(ctx, 48); contentDescription = "$label category"; isSelected = category == label
                background = Ui.rounded(Ui.c(ctx, if (isSelected) R.color.ag_mauve else R.color.ag_mantle), 10, ctx)
                val ink = if (isSelected) R.color.ag_base else R.color.ag_text
                val icon = when (item) {
                    null -> Ui.text(ctx, 20f, 900, ink).apply { text = "☷"; gravity = android.view.Gravity.CENTER }
                    "tm" -> Bits.tmTile(ctx, null, 24)
                    else -> Bits.itemTile(ctx, item, 24)
                }
                addView(icon, LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
                addView(Ui.text(ctx, 12f, 800, ink).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Ui.text(ctx, 11f, 800, ink).apply { text = "${count(label)}" })
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4))
                setOnClickListener { category = label; s.requestRender() }
            }, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 3) })
        }, LinearLayout.LayoutParams(0, -2, .46f).also { it.marginEnd = Ui.dp(ctx, 6) })
        val results = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; tag = "bag-results" }
        body.addView(results, LinearLayout.LayoutParams(0, -2, if (categoriesOpen) .54f else 1f))
        var any = false
        fun section(title: String, ids: List<String>) {
            if (ids.isEmpty() || category != "All items" && category != title) return
            any = true
            val n = ids.sumOf { state.ownedItemCount(it) }
            results.addView(Bits.sect(ctx, title, "$n item${if (n == 1) "" else "s"}"))
            BagTiles.rows(results, ids.distinct().sortedBy { Items.name(it) }.map { itemRow(it) }, columns = if (categoriesOpen) 1 else 2)
        }
        for ((title, kinds) in groups) section(title, owned.filter { Items.def(it)?.kind in kinds })
        // anything the catalogue doesn't know
        section("Other", owned.filter { id -> groups.none { (_, k) -> Items.def(id)?.kind in k } })

        if (!heldOnly && (category == "All items" || category == "TMs") && state.tms.isNotEmpty()) {
            any = true
            results.addView(Bits.sect(ctx, "TMs", "${state.tms.size} owned"))
            BagTiles.rows(results, state.tms.sortedBy { data.move(it)?.name ?: it }.map { tmRow(it) }, columns = if (categoriesOpen) 1 else 2)
        }
        if (!any) results.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply {
            text = if (category != "All items") "No items in this category." else if (heldOnly) "None of your Pokémon holds an item." else "Your bag is empty. Items drop while you study, and the shop sells more."
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 4), 0)
        })
    }

    /** Square inventory tile; a tap opens the existing item window. */
    private fun itemRow(id: String): View {
        val ctx = s
        val n = state.ownedItemCount(id)
        val line = ItemInfo.plain(ItemInfo.line(data, id, state.caught))
        val row = BagTiles.tile(ctx, ShopViews.itemArt(ctx, id, 80), Items.name(id), line + if (holders(id).isNotEmpty()) " · Held" else "", "×$n", large = true)
        if (holders(id).isNotEmpty()) row.background = Ui.rounded(Bits.blend(Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_card), 0.12f), 12, ctx)
        row.contentDescription = ItemCardPlan.detailsLabel("${Items.name(id)}, $line, $n owned" + if (holders(id).isNotEmpty()) ", Held" else "")
        row.setOnClickListener { host.openCard(CardKeys.bagItem(id)) }
        return row
    }

    /** TM tile retains its type disc; a TM is owned once. */
    private fun tmRow(move: String): View {
        val ctx = s
        val m = data.move(move)
        val row = BagTiles.tile(ctx, Bits.tmTile(ctx, m?.type, 80), Items.tmName(data, move), m?.type?.label.orEmpty(), "", large = true)
        row.contentDescription = ItemCardPlan.detailsLabel("${Items.tmName(data, move)}, ${m?.type?.label.orEmpty()}")
        row.setOnClickListener { host.openCard(CardKeys.bagTm(move)) }
        return row
    }

    // ---- what the item window shows ----

    /**
     * What the window of [key] shows and does, or null when that item is not in the bag any more. An item that a trade took the last of stays
     * while the window is kept open (Y5), showing 0.
     */
    fun spec(key: String): ItemCard.Spec? {
        CardKeys.bagItemId(key)?.let { id -> return if (state.count(id) > 0 || holders(id).isNotEmpty() || host.open.keepEmpty) itemSpec(id) else null }
        CardKeys.bagTmMove(key)?.let { move -> return if (move in state.tms) tmSpec(move) else null }
        return null
    }

    /** The window of an owned item: its texts, the action button of its kind, and for a ball the trade-up. The button closes the window just before a picker or confirm opens (a message-only answer keeps it open). */
    private fun itemSpec(id: String): ItemCard.Spec {
        val kind = Items.def(id)?.kind
        val none = state.count(id) <= 0 // Y5: a window kept open after a trade took the last ball
        val act = BagAct.of(kind)?.takeUnless { kind == ItemKind.HELD }?.let { a ->
            val waiting = a.fightOnly && !fightAvailable()
            ItemCard.Act(a.label, a.usableNow(state) && !none && !waiting,
                if (none) { if (holders(id).isNotEmpty()) "No free copies" else "You have none" }
                else if (waiting) "Tap next battle first" else a.offFight) { tap(id) { host.closeCard() } }
        }
        val offer = if (kind == ItemKind.BALL) BallTrade.offer(state, id) else null
        val trade = offer?.let { o ->
            val confirming = host.open.confirming && offer.canTrade
            ItemCard.Trade(o, confirming, ItemCardPlan.tradeConfirm(state, o),
                onAsk = { host.open.confirmTrade(true); s.requestRender() },
                onCancel = { host.open.confirmTrade(false); s.requestRender() },
                onConfirm = { tradeUp(id) })
        }
        return ItemCard.Spec(id, act = act, trade = trade, holders = holders(id))
    }

    private fun tmSpec(move: String): ItemCard.Spec =
        ItemCard.Spec(null, move, act = ItemCard.Act(BagAct.TEACH.label, true, null) { tapTm(move) { host.closeCard() } })

    /** One batch of the trade-up (PLAN 199): the confirm closes, the window stays open with the new counts. */
    private fun tradeUp(ball: String) {
        host.open.confirmTrade(false)
        host.open.keepWhenEmpty() // Y5: if this uses the last ball of the window, the window stays open showing 0
        s.act { g -> BallTrade.trade(g.state, ball) }
    }

    // ---- what a tap does ----

    /**
     * What a tap on the window's button does. [close] shuts the item window; it is called right before something else opens (a picker, a
     * confirm, the battle), never for an action that ends only in a message (those keep the window open and just show the toast).
     */
    private fun tap(item: String, close: () -> Unit) {
        val def = Items.def(item) ?: return
        if (BagAct.of(def.kind)?.fightOnly == true && !fightAvailable()) {
            Ui.toast(s, "Tap next battle first"); return
        }
        when (def.kind) {
            ItemKind.AUTO -> Unit
            ItemKind.HEAL -> {
                val team = state.teamMons()
                if (team.isEmpty()) return
                close()
                // main first (it is team[0]); HP shown so the choice is easy
                Pickers.pickMon(s, "${Items.name(item)} on…", data, team, { Bits.hpText(data, it) }) { mon ->
                    Pickers.hold(s, "Use ${Items.name(item)}?", "Use ${Items.name(item)} on ${mon.shownName}?", "Use") { mons.runUse(item, mon.uid) }
                }
            }
            ItemKind.EVOLUTION -> {
                val ok = candidates().filter { ItemUse.canUse(data, state, item, it) || Progression.itemEvolutionTimeNeeded(data, it, item) != null } // time-blocked ones stay, hinted "(night)"
                if (ok.isEmpty()) { Ui.toast(s, "No Pokémon can use ${Items.name(item)} right now"); return }
                close()
                Pickers.pickMonNames(s, "${Items.name(item)} on…", ok) { mons.useItem(it, item) }
            }
            ItemKind.HELD -> {
                val all = candidates().filter { it.heldItem != item }
                if (all.isEmpty()) { Ui.toast(s, "No Pokémon to give it to"); return }
                close()
                Pickers.pickMon(s, "Give ${Items.name(item)} to…", data, all, { mon ->
                    mon.heldItem?.let { "holds ${Items.name(it)}" } ?: "holds nothing"
                }) { mons.giveItemTo(it, item) }
            }
            ItemKind.FOSSIL -> {
                val sp = def.fossil?.let { data.species[it] }?.name ?: "a Pokémon"
                close()
                Pickers.hold(s, "Revive fossil?", "Use ${Items.name(item)} to revive $sp (Lv 5)?", "Revive") { mons.runUse(item, null) }
            }
            ItemKind.BALL -> throwBall(item, close)
            ItemKind.ESCAPE -> escape(item, close)
        }
    }

    /** Team first, then the box (by species number). */
    private fun candidates(): List<Mon> = state.teamMons() + state.boxMons().sortedWith(compareBy({ it.species }, { it.level }))

    private fun throwBall(ball: String, close: () -> Unit) {
        val battle = state.battle
        if (battle == null) { Ui.toast(s, "No wild Pokémon right now. Throw balls from a battle."); return }
        val wild = battle.wild
        close()
        val choose: (String, String?) -> Unit = { regular, special ->
            Pickers.hold(s, "Throw ${Catching.throwName(regular, special)}?", Items.MISS_RULE, "Throw") {
                if (!fightAvailable()) return@hold
                val events = GameRepo.act(s) { it.throwBall(regular, special) }
                if (onBattleEvents != null) onBattleEvents.invoke(events)
                else s.report(ballMessage(events, wild.name), events)
                s.requestRender()
            }
        }
        if (ball in com.husarp.ankigo.game.Tuning.OLD_BALLS) BallThrowUi.show(s, state, choose)
        else BallThrowUi.special(s, state, ball, choose)
    }

    private fun ballMessage(events: List<GameEvent>, wildName: String): String {
        val throwEv = events.filterIsInstance<GameEvent.BallThrow>().firstOrNull() ?: return "Nothing happened."
        if (!throwEv.success) return "$wildName broke free and ran away! (${throwEv.chance}% chance, ${throwEv.left} left)"
        val caught = events.filterIsInstance<GameEvent.Caught>().firstOrNull()
        return "Gotcha! $wildName was caught!" + if (caught?.newSpecies == true) " New Pokédex entry!" else ""
    }

    private fun escape(item: String, close: () -> Unit) {
        val battle = state.battle
        if (battle == null) { Ui.toast(s, "No wild Pokémon to escape from"); return }
        close()
        Pickers.hold(s, "Use ${Items.name(item)}?", "The wild ${battle.wild.name} leaves and a new one appears.", "Use") {
            if (!fightAvailable()) return@hold
            val events = GameRepo.act(s) { it.escape(item) }
            val fled = events.filterIsInstance<GameEvent.Fled>().firstOrNull()
            if (onBattleEvents != null) onBattleEvents.invoke(events)
            else s.report(if (fled != null) "${fled.name} left." else "Used ${Items.name(item)}.", events)
            s.requestRender()
        }
    }

    private fun tapTm(move: String, close: () -> Unit) {
        val able = candidates().filter { data.sp(it.species).tmMoves.contains(move) && move !in it.moves }
        if (able.isEmpty()) { Ui.toast(s, "None of your Pokémon can learn ${data.move(move)?.name ?: move}"); return }
        close()
        Pickers.pickMon(s, "Teach ${Items.tmName(data, move)} to…", data, able, { mon -> "${mon.moves.size}/4 moves" }) { mons.teach(it, move) }
    }
}
