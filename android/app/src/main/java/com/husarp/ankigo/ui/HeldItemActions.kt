package com.husarp.ankigo.ui

import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.game.*

/** Shared by every item window, including details and Shop. All mutations go through the saving repository. */
internal object HeldItemActions {
    fun buttons(page: GamePage, item: String, onChanged: () -> Unit = {}): View? {
        if (Items.def(item)?.kind != ItemKind.HELD || page.state.ownedItemCount(item) == 0) return null
        return LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            addView(ItemCard.actView(page, ItemCard.Act("Take off", page.state.itemHolders(item).isNotEmpty(), "No Pokémon holds this item") {
                source(page, item) { mon ->
                    if (mon != null) page.act(beforeReport = { if (it.ok) onChanged() }) { g ->
                        if (g.state.mon(mon.uid)?.heldItem != item) ActionResult(false, "That Pokémon no longer holds this item.")
                        else ItemUse.takeHeld(g.state, mon.uid)
                    }
                }
            }), LinearLayout.LayoutParams(-1, -2))
            addView(ItemCard.actView(page, ItemCard.Act("Give to another Pokémon", true, null) {
                source(page, item) { mon ->
                    BoxMonPicker.show(page, "Give ${Items.name(item)} to…", { it.heldItem != item }) { target ->
                        page.act(beforeReport = { if (it.ok) onChanged() }) { g -> ItemUse.giveHeld(g.data, g.state, item, mon?.uid, target.uid) }
                    }
                }
            }), Form.lp(page, 6))
        }
    }

    private fun source(page: GamePage, item: String, picked: (Mon?) -> Unit) {
        val holders = page.state.itemHolders(item)
        when (holders.size) {
            0 -> picked(null)
            1 -> picked(holders.single())
            else -> Pickers.pickMon(page, "Which Pokémon holds ${Items.name(item)}?", page.data, holders,
                { "Holds ${Items.name(item)}" }) { picked(it) }
        }
    }
}
