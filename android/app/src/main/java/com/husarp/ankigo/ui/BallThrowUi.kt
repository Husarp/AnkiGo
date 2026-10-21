package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items

/**
 * PLAN 171: the "Throw a ball" list the study screen and the game screen share: every ball in the bag (weakest first) with its count
 * and catch chance, and the miss rule in a short line under the list.
 */
internal object BallThrowUi {
    /** The balls in [st]'s bag, weakest first. */
    fun balls(st: GameState): List<String> =
        st.bag.filter { (id, n) -> n > 0 && Items.def(id)?.kind == ItemKind.BALL }.keys.sortedBy { Catching.ballChance(it) }

    /** "Great Ball  ×3  ·  50%". */
    fun label(st: GameState, ball: String): String = "${Items.name(ball)}  ×${st.count(ball)}  ·  ${Catching.ballChance(ball)}%"

    /** Opens the list; [onPick] gets the ball tapped. A bag without balls only says so. */
    fun show(ctx: Context, st: GameState, onPick: (String) -> Unit) {
        val balls = balls(st)
        if (balls.isEmpty()) { Ui.toast(ctx, "No Poké Balls. Buy some in the shop."); return }
        AppDialog.Builder(ctx).setTitle("Throw a ball")
            .setItems(balls.map { label(st, it) }.toTypedArray()) { _, i -> onPick(balls[i]) }
            .setFooter(Ui.text(ctx, 12.5f, 700, R.color.ag_sub0).apply {
                text = Items.MISS_RULE
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 8), Ui.dp(ctx, 24), Ui.dp(ctx, 2))
            })
            .setNegativeButton("Cancel", null).showCard()
    }
}
