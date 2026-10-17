package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import com.husarp.ankigo.GameRepo

/**
 * Tap on your Pokémon in a battle (PLAN 86): the same Details window as in the Pokédex / Box, on top of the battle.
 * Close returns to the battle; nothing switches to the Team tab. The window is built on a bare [GamePage] so heart,
 * rename and "More options" work like in the Team tab.
 */
object BattleDetails {
    /** Shows the details of [uid] over whatever [ctx] belongs to (an Activity or an overlay window). False when there is nothing to show. */
    fun show(ctx: Context, uid: String): Boolean {
        val data = GameRepo.data ?: return false
        val mon = GameRepo.state?.mon(uid) ?: return false
        val host = object : PageHost {
            override val hostActivity: Activity? = Ui.findActivity(ctx)
            override fun closePage() {}
            override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) { ctx.startActivity(intent) }
        }
        MonDetail.show(BarePage(ctx, host), data, mon)
        return true
    }

    /** A page that is never shown: only the Context (and the game actions) the Details window needs. */
    private class BarePage(base: Context, host: PageHost) : GamePage(base, host) {
        override val tab: BottomNav.Tab? = null
        override val needsGame = false
        override fun buildBar(): View = View(this)
        override fun buildBody(): View = View(this)
        override fun render() {}
    }
}
