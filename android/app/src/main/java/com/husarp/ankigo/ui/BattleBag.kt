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
    fun show(ctx: Context, fightAvailable: () -> Boolean = { true }, onEvents: (List<GameEvent>) -> Unit) {
        if (GameRepo.state == null || GameRepo.data == null) return
        lateinit var dialog: AppDialog
        val host = object : PageHost {
            override val hostActivity get() = ctx as? android.app.Activity
            override fun closePage() = dialog.dismiss()
            override fun startForResult(intent: android.content.Intent, onResult: (Int, android.content.Intent?) -> Unit) {
                com.husarp.ankigo.overlay.ResultActivity.launch(ctx, intent, onResult)
            }
        }
        val page = ShopPage(ctx, host, bagOnly = true, onBattleEvents = onEvents, fightAvailable = fightAvailable)
        val frame = android.widget.FrameLayout(ctx).apply {
            addView(page.root, android.widget.FrameLayout.LayoutParams(-1, Ui.dp(ctx,
                minOf(480, (resources.displayMetrics.heightPixels / resources.displayMetrics.density * .65f).toInt()))))
        }
        dialog = AppDialog.Builder(ctx).setView(frame).setNegativeButton("Close", null).create()
        dialog.setOnDismissListener {
            page.onHide(); page.onGone()
        }
        page.onShow()
        Pickers.show(dialog)
    }

    /** MoveLearned supplies success feedback; failed teaching still needs its explanation. */
    internal fun tmResult(result: ActionResult, toast: (String) -> Unit, onEvents: (List<GameEvent>) -> Unit) {
        if (!result.ok) toast(result.message)
        onEvents(result.events)
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
            var tab = "Box"; var query = ""; var sort = 0
            val tabLabels = mutableListOf<Pair<String, android.widget.TextView>>()
            val sortLabels = mutableListOf<Pair<Int, android.widget.TextView>>()
            fun render() {
                tabLabels.forEach { (label, view) -> view.setTextColor(Ui.c(ctx, if (label == tab) R.color.ag_mauve else R.color.ag_sub0)) }
                sortLabels.forEach { (sortIndex, view) -> view.setTextColor(Ui.c(ctx, if (sortIndex == sort) R.color.ag_mauve else R.color.ag_sub0)) }
                rows.removeAllViews()
                val source = if (!more) st.teamMons() else when (tab) { "Team" -> st.teamMons(); "Favourites" -> st.favouriteMons(); else -> st.boxMons() }
                val mons = source.filter { it.shownName.contains(query, true) || it.name.contains(query, true) }.let { if (!more) it else when (sort) { 1 -> it.sortedByDescending { m -> m.level }; 2 -> it.sortedByDescending { m -> m.evolvedAt }; else -> it.sortedByDescending { m -> m.caughtAt } } }
                for (mon in mons) {
                    val ok = if (tm) item in data.sp(mon.species).tmMoves && item !in mon.moves else ItemUse.canUse(data, st, item, mon)
                    val max = StatCalc.maxHp(data, mon)
                    val hint = if (ok) if (Items.def(item)?.kind == ItemKind.EVOLUTION) "" else Bits.hpText(data, mon) else if (Items.def(item)?.kind == ItemKind.HEAL && mon.hp >= max) "Full HP" else Progression.itemEvolutionTimeNeeded(data, mon, item)?.let { if (it == "day") "In the daytime" else "At night" } ?: when {
                        tm -> "Cannot learn this move"
                        Items.def(item)?.kind == ItemKind.EVOLUTION -> "No evolution with this item"
                        Items.def(item)?.kind == ItemKind.HELD -> "Already holds ${Items.name(item)}"
                        else -> "Cannot use this item"
                    }
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        if (Items.def(item)?.kind == ItemKind.EVOLUTION) addView(Bits.monNameRow(ctx, mon)) else addView(Bits.monRow(ctx, data, mon, hint))
                        if (Items.def(item)?.kind != ItemKind.EVOLUTION) addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { this.max = max; progress = mon.hp; progressTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, if (mon.hp * 5 <= max) R.color.ag_red else if (mon.hp * 2 <= max) R.color.ag_yellow else R.color.ag_green)); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mantle)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 6)).apply { setMargins(Ui.dp(ctx, 60), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 6)) })
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
                for (sortIndex in 0..2) sorts.addView(Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply { text = listOf("Last caught", "Highest level", "Recently evolved")[sortIndex]; sortLabels += sortIndex to this; setPadding(12, 16, 12, 16); setOnClickListener { sort = sortIndex; render() } })
                body.addView(MotionHorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(sorts) })
            }
            body.addView(rows)
            if (!more) body.addView(Ui.text(ctx, 14f, 900, R.color.ag_mauve).apply { text = "More Pokémon"; minHeight = Ui.dp(ctx, 48); setOnClickListener { dialog.dismiss(); window(true) } })
            dialog = AppDialog.Builder(ctx).setTitle("${if (tm) Items.tmName(data, item) else Items.name(item)} on…").setView(body).setNegativeButton("Cancel", null).create()
            render(); Pickers.show(dialog)
        }
        window(false)
    }
}
