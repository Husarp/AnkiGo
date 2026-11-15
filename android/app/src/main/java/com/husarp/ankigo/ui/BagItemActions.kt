package com.husarp.ankigo.ui

import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.game.*

internal object BagItemActions {
    fun buttons(page: GamePage, item: String): View? {
        if (page.state.count(item) <= 0) return null
        return LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            if (item in AbilityAdaptations.berries) addView(ItemCard.actView(page, ItemCard.Act("Feed berry…", true, null) {
                (page as? CardHost)?.closeCard()
                BoxMonPicker.show(page, "Feed ${Items.name(item)} to…", { true }) { mon -> feed(page, item, mon) }
            }), Form.lp(page, 0))
            if (page is CardHost && BagSell.allowed(item)) addView(sellView(page, item), Form.lp(page, 6))
        }.takeIf { it.childCount > 0 }
    }

    internal fun sellView(page: GamePage, item: String): View {
        val open = (page as CardHost).open
        val quantity = open.quantity
        val price = BagSell.price(page.data, item).toLong() * quantity
        return LinearLayout(page).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ItemCard.actView(page, ItemCard.Act("Sell · $price coins", page.state.count(item) >= quantity, "Not enough copies in the Bag") {
                open.keepWhenEmpty()
                page.act { g -> BagSell.sell(g.data, g.state, item, quantity) }
            }).apply {
                contentDescription = "Sell $quantity ${Items.name(item)} for $price coins. Hold to confirm"
                HoldToConfirm.bind(this, "sell $quantity ${Items.name(item)} for $price coins", allowed = { page.state.count(item) >= quantity }) {
                    open.keepWhenEmpty()
                    page.act { g -> BagSell.sell(g.data, g.state, item, quantity) }
                }
            }, LinearLayout.LayoutParams(0, -2, .8f))
            addView(ItemCard.actView(page, ItemCard.Act("×$quantity", true, null) {
                open.cycleQuantity(); page.requestRender()
            }).apply { contentDescription = "Sell quantity $quantity. Cycle 1, 3, 5" },
                LinearLayout.LayoutParams(0, -2, .2f).also { it.marginStart = Ui.dp(page, 6) })
        }
    }

    fun feedButton(page: GamePage, mon: Mon, onFed: () -> Unit = {}): View = ItemCard.actView(page, ItemCard.Act("Feed berry…", true, null) {
        val berries = page.state.bag.keys.filter { it in AbilityAdaptations.berries && page.state.count(it) > 0 }.sorted()
        if (berries.isEmpty()) Ui.toast(page, "No berry in the Bag.")
        else Pickers.choose(page, "Feed ${mon.shownName}", berries.map { Items.name(it) }) { index -> feed(page, berries[index], mon, onFed) }
    })

    private fun feed(page: GamePage, item: String, mon: Mon, onFed: () -> Unit = {}) {
        page.act(beforeReport = { if (it.ok) onFed() }) { it.feedBerry(item, mon.uid) }
    }
}
