package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.shownName

/** Opened only from Pokémon details or its action menu; earning an offer never opens a window. */
internal object MoveOfferPrompt {
    fun mark(a: Context, mon: Mon, onClick: () -> Unit) =
        DetailParts.secondaryButton(a, "New move · ${mon.moveOffers.size} waiting", onClick).apply {
            contentDescription = "Move offer: ${mon.moveOffers.size} waiting"
        }

    fun show(a: Context, data: GameData, mon: Mon, answer: (String, Boolean, String?) -> ActionResult) {
        val offer = mon.moveOffers.firstOrNull() ?: return
        val move = data.move(offer.move)?.name ?: offer.move
        val old = if (mon.moves.size >= 4) Progression.replacementIndex(data, mon)?.let { mon.moves[it] } else null
        val message = (old?.let { "It replaces ${if (it in mon.discoveredMoves) data.move(it)?.name ?: it else "???"}." } ?: "It adds a move to the free slot.") +
            "\n\nSkip gives up this offer for good. Closing this window leaves it waiting." +
            if (mon.moveOffers.size > 1) "\n\n${mon.moveOffers.size - 1} more waiting." else ""
        Pickers.show(AppDialog.Builder(a).setTitle("Learn $move?")
            .setMessage("${mon.shownName} · Lv ${offer.level}\n\n$message")
            .setPositiveButton("Yes") { _, _ -> answer(offer.move, true, old) }
            .setNegativeButton("Skip") { _, _ -> answer(offer.move, false, old) }.create())
    }
}
