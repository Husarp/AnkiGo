package com.husarp.ankigo.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*

/** One inventory window shared by both battle hosts. Actions retain the engine's existing turn rules. */
internal object BattleBag {
    fun show(ctx: Context, onEvents: (List<GameEvent>) -> Unit) {
        val st = GameRepo.state ?: return
        val data = GameRepo.data ?: return
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0) }
        lateinit var dialog: AppDialog
        fun use(id: String, mon: Mon?) {
            val result = GameRepo.run(ctx) { it.useItem(id, mon?.uid) }
            Ui.toast(ctx, result.message); onEvents(result.events)
        }
        var any = false
        for ((title, kind) in listOf("Balls" to ItemKind.BALL, "Healing" to ItemKind.HEAL, "Evolution" to ItemKind.EVOLUTION, "Held" to ItemKind.HELD, "Fossils" to ItemKind.FOSSIL, "Escape" to ItemKind.ESCAPE)) {
            val ids = (st.bag.filter { it.value > 0 }.keys + st.mons.mapNotNull { it.heldItem }).filter { Items.def(it)?.kind == kind }.sortedBy { if (kind == ItemKind.BALL) Catching.ballChance(it).toString().padStart(3, '0') else Items.name(it) }
            if (ids.isEmpty()) continue
            any = true
            body.addView(Bits.sect(ctx, title, "${ids.sumOf { id -> st.count(id) + st.mons.count { it.heldItem == id } }} items"))
            if (kind == ItemKind.BALL) body.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = Items.MISS_RULE })
            BagTiles.rows(body, ids.map { id ->
                val held = st.mons.count { it.heldItem == id }
                val enabled = st.count(id) > 0 && (kind != ItemKind.BALL && kind != ItemKind.ESCAPE || st.battle?.wild?.hp?.let { it > 0 } == true)
                val note = if (st.count(id) <= 0) "No free copies · Held" else if (!enabled) "No wild Pokémon" else if (kind == ItemKind.BALL) { if (id in Tuning.OLD_BALLS) Items.specialNote(id) else "${Catching.ballChance(id)}% to catch" } else ItemInfo.plain(ItemInfo.line(data, id, st.caught))
                BagTiles.tile(ctx, Bits.itemTile(ctx, id), Items.name(id), note + if (held > 0 && st.count(id) > 0) " · Held" else "", "×${st.count(id) + held}").apply {
                    alpha = if (enabled) 1f else .45f
                    isEnabled = enabled
                    setOnClickListener {
                        dialog.dismiss()
                        when (kind) {
                            ItemKind.BALL -> {
                                val choose: (String, String?) -> Unit = { regular, special -> onEvents(GameRepo.act(ctx) { it.throwBall(regular, special) }) }
                                if (id in Tuning.OLD_BALLS) BallThrowUi.show(ctx, st, choose)
                                else BallThrowUi.special(ctx, st, id, choose)
                            }
                            ItemKind.ESCAPE -> Pickers.confirm(ctx, "Use ${Items.name(id)}?", "The wild Pokémon leaves and a new one appears.", "Use") { onEvents(GameRepo.act(ctx) { it.escape(id) }) }
                            ItemKind.FOSSIL -> Pickers.confirm(ctx, "Revive fossil?", "Use ${Items.name(id)} to revive an ancient Pokémon at Lv 5?", "Revive") { use(id, null) }
                            else -> pick(ctx, data, st, id) { mon ->
                                if (kind == ItemKind.EVOLUTION) Pickers.confirm(ctx, "Use ${Items.name(id)}?", "Use on ${mon.shownName}?", "Use") { use(id, mon) }
                                else use(id, mon)
                            }
                        }
                    }
                }
            })
        }
        if (st.tms.isNotEmpty()) {
            any = true
            body.addView(Bits.sect(ctx, "TMs"))
            BagTiles.rows(body, st.tms.sorted().map { move ->
                val m = data.move(move)
                BagTiles.tile(ctx, Bits.tmTile(ctx, m?.type), Items.tmName(data, move), m?.type?.label.orEmpty(), "").apply {
                    setOnClickListener {
                        dialog.dismiss()
                        pick(ctx, data, st, move, tm = true) { mon ->
                            fun teach(index: Int?) {
                                val r = GameRepo.run(ctx) { ItemUse.teachTm(it.data, it.state, move, mon.uid, index) }
                                Ui.toast(ctx, r.message); onEvents(r.events)
                            }
                            if (mon.moves.size < 4) teach(null)
                            else Pickers.pickMove(ctx, "Forget a move for ${m?.name ?: move}?", data, mon) { teach(it) }
                        }
                    }
                }
            })
        }
        if (!any) body.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "Your bag is empty. Items drop while you study, and the shop sells more." })
        dialog = AppDialog.Builder(ctx).setTitle("Use item").setView(BagScroll(ctx).apply { addView(body) }).setNegativeButton("Close", null).create()
        Pickers.show(dialog)
    }

    private class BagScroll(ctx: Context) : MotionScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limit = minOf(Ui.dp(context, 340), (resources.displayMetrics.heightPixels * .55f).toInt())
            val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) limit else minOf(limit, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
        }
    }

    internal fun pick(ctx: Context, data: GameData, st: GameState, item: String, tm: Boolean = false, onPick: (Mon) -> Unit) {
        fun window(more: Boolean) {
            val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0) }
            val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            lateinit var dialog: AppDialog
            var tab = "Box"; var query = ""; var level = false
            val tabLabels = mutableListOf<Pair<String, android.widget.TextView>>()
            val sortLabels = mutableListOf<Pair<Boolean, android.widget.TextView>>()
            fun render() {
                tabLabels.forEach { (label, view) -> view.setTextColor(Ui.c(ctx, if (label == tab) R.color.ag_mauve else R.color.ag_sub0)) }
                sortLabels.forEach { (byLevel, view) -> view.setTextColor(Ui.c(ctx, if (byLevel == level) R.color.ag_mauve else R.color.ag_sub0)) }
                rows.removeAllViews()
                val source = if (!more) st.teamMons() else when (tab) { "Team" -> st.teamMons(); "Favourites" -> st.favouriteMons(); else -> st.boxMons() }
                val mons = source.filter { it.shownName.contains(query, true) || it.name.contains(query, true) }.let { if (!more) it else if (level) it.sortedByDescending { m -> m.level } else it.sortedByDescending { m -> m.caughtAt } }
                for (mon in mons) {
                    val ok = if (tm) item in data.sp(mon.species).tmMoves && item !in mon.moves else ItemUse.canUse(data, st, item, mon)
                    val max = StatCalc.maxHp(data, mon)
                    val hint = if (ok) Bits.hpText(data, mon) + (Progression.itemEvolutionAnyTime(data, mon, item)?.let { " · → ${data.sp(it.to).name}" } ?: "") else if (Items.def(item)?.kind == ItemKind.HEAL && mon.hp >= max) "Full HP" else Progression.itemEvolutionTimeNeeded(data, mon, item)?.let { "Needs $it" } ?: when {
                        tm -> "Cannot learn this move"
                        Items.def(item)?.kind == ItemKind.EVOLUTION -> "No evolution with this item"
                        Items.def(item)?.kind == ItemKind.HELD -> "Already holds ${Items.name(item)}"
                        else -> "Cannot use this item"
                    }
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(Bits.monRow(ctx, data, mon, hint))
                        addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { this.max = max; progress = mon.hp; progressTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, if (mon.hp * 5 <= max) R.color.ag_red else if (mon.hp * 2 <= max) R.color.ag_yellow else R.color.ag_green)); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mantle)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 6)).apply { setMargins(Ui.dp(ctx, 60), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 6)) })
                        alpha = if (ok) 1f else .45f
                        if (ok) setOnClickListener { dialog.dismiss(); onPick(mon) }
                    }
                    rows.addView(row)
                }
                if (mons.isEmpty()) rows.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "No Pokémon" })
            }
            if (more) {
                val tabs = LinearLayout(ctx)
                for (label in listOf("Team", "Box", "Favourites")) tabs.addView(Ui.text(ctx, 13f, 900, R.color.ag_mauve).apply { text = label; tabLabels += label to this; setPadding(12, 16, 12, 16); setOnClickListener { tab = label; render() } })
                body.addView(tabs)
                body.addView(EditText(ctx).apply { hint = "Search by name"; setSingleLine(); addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); render() }
                    override fun afterTextChanged(s: Editable?) {}
                }) })
                val sorts = LinearLayout(ctx)
                for (byLevel in listOf(false, true)) sorts.addView(Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply { text = if (byLevel) "Highest level" else "Last caught"; sortLabels += byLevel to this; setPadding(12, 16, 12, 16); setOnClickListener { level = byLevel; render() } })
                body.addView(sorts)
            }
            body.addView(rows)
            if (!more) body.addView(Ui.text(ctx, 14f, 900, R.color.ag_mauve).apply { text = "More Pokémon"; minHeight = Ui.dp(ctx, 48); setOnClickListener { dialog.dismiss(); window(true) } })
            dialog = AppDialog.Builder(ctx).setTitle("${if (tm) Items.tmName(data, item) else Items.name(item)} on…").setView(body).setNegativeButton("Cancel", null).create()
            render(); Pickers.show(dialog)
        }
        window(false)
    }
}
