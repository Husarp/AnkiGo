package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Tuning

/** One regular-ball row, then an optional special row; one combined throw. Shared by battle, study and Bag. */
internal object BallThrowUi {
    fun balls(st: GameState): List<String> =
        st.bag.filter { (id, n) -> n > 0 && id in Tuning.BALL_CHANCE }.keys.sortedBy { Catching.ballChance(it) }

    fun label(st: GameState, ball: String): String = "${Items.name(ball)}  ×${st.count(ball)}  ·  ${Catching.ballChance(ball)}%"

    fun show(ctx: Context, st: GameState, onPick: (String, String?) -> Unit) = special(ctx, st, null, onPick)

    fun special(ctx: Context, st: GameState, ball: String?, onPick: (String, String?) -> Unit) {
        if (balls(st).isEmpty()) { Ui.toast(ctx, "No regular balls. Buy one in the Shop."); return }
        Pickers.show(dialog(ctx, st, ball, onPick))
    }

    /** Builds the same content for a real window and screenshot tests. The caller must own a regular ball. */
    fun dialog(ctx: Context, st: GameState, initial: String? = null, onPick: (String, String?) -> Unit): AppDialog {
        val regulars = balls(st)
        var regular = initial?.takeIf { it in regulars } ?: regulars.first()
        var special: String? = null
        val specials = Tuning.OLD_BALLS.filter { st.count(it) > 0 }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24), 0, Ui.dp(ctx, 24), Ui.dp(ctx, 8))
        }
        val preview = Ui.text(ctx, 15f, 900, R.color.ag_text)
        val effect = Ui.text(ctx, 12.5f, 700, R.color.ag_sub0)
        val choices = mutableListOf<Triple<TextView, String?, Boolean>>()
        fun refresh() {
            val data = GameRepo.data
            val battle = st.battle
            val chance = if (data != null && battle != null)
                Catching.ballChance(regular, special, data, st, battle, System.currentTimeMillis()) else Catching.ballChance(regular)
            preview.text = "${Catching.throwName(regular, special)} · $chance%"
            effect.text = special?.let {
                Items.specialNote(it) + " " + Items.MISS_RULE +
                    (if (regular == "master-ball") " Catch-chance specials do nothing with a Master Ball." else "")
            } ?: Items.MISS_RULE
            for ((view, id, isSpecial) in choices) {
                val selected = if (isSpecial) special == id else regular == id
                view.isSelected = selected
                view.contentDescription = "${view.text}, ${if (selected) "selected" else "not selected"}"
                view.background = Ui.rounded(Ui.c(ctx, if (selected) R.color.ag_s1 else R.color.ag_mantle), 10, ctx)
            }
        }
        fun row(title: String, ids: List<String?>, isSpecial: Boolean) {
            body.addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply {
                text = title; setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 6))
            })
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (id in ids) {
                val choice = Ui.text(ctx, 13f, 800, R.color.ag_text).apply {
                    text = when { id == null -> "None"; isSpecial -> "${Items.name(id)} ×${st.count(id)}"; else -> label(st, id) }
                    gravity = Gravity.CENTER
                    minHeight = Ui.dp(ctx, 48)
                    setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
                    setOnClickListener { if (isSpecial) special = id else regular = id!!; refresh() }
                }
                choices += Triple(choice, id, isSpecial)
                row.addView(choice, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(ctx, 6) })
            }
            body.addView(MotionHorizontalScrollView(ctx).apply { addView(row); isHorizontalScrollBarEnabled = true })
        }
        row("Regular ball", regulars, false)
        if (specials.isNotEmpty()) row("Special ball (optional)", listOf(null) + specials, true)
        body.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 16) })
        body.addView(effect)
        refresh()
        return AppDialog.Builder(ctx).setTitle("Throw a ball").setView(body)
            .setPositiveButton("Throw") { _, _ -> onPick(regular, special) }
            .setNegativeButton("Cancel", null).create()
    }
}
