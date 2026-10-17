package com.husarp.ankigo.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Tuning

// TICKET R3-X2 owns this file: the popup a Shop tap opens (what it does, price, how many you own) with Buy at the bottom.

internal object ShopInfo {
    /** The popup of one shop item; [onBuy] runs after Buy (the popup closes first). Nothing is bought by the tap itself. */
    fun item(s: GamePage, item: String, onBuy: () -> Unit) {
        val price = s.data.price(item)
        val body = column(s)
        val lead = LinearLayout(s).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Bits.itemTile(s, item, 56))
            val text = LinearLayout(s).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { text = ItemInfo.effect(item) })
                ItemInfo.howToUse(item).takeIf { it.isNotEmpty() }?.let {
                    addView(Ui.text(s, 12f, 700, R.color.ag_sub1).apply { text = it }, Form.lp(s, 3))
                }
            }
            addView(text, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(s, 12) })
        }
        body.addView(lead)
        body.addView(fact(s, "Price", price))
        body.addView(fact(s, "You own", text = "×${s.state.count(item)}"))
        show(s, Items.name(item), body, price, buy = true, onBuy)
    }

    /** The popup of one shop TM. A TM can be owned once, so an owned one shows "Owned" instead of Buy. */
    fun tm(s: GamePage, move: String, onBuy: () -> Unit) {
        val m = s.data.move(move)
        val owned = move in s.state.tms
        val body = column(s)
        val lead = LinearLayout(s).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Bits.tmTile(s, m?.type, 56))
            val text = LinearLayout(s).apply {
                orientation = LinearLayout.VERTICAL
                if (m != null) addView(Bits.typeRow(s, listOf(m.type)))
                addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { text = Bits.moveStats(s.data, move) }, Form.lp(s, 4))
                addView(Ui.text(s, 12f, 700, R.color.ag_sub1).apply { text = ItemInfo.tmHowToUse() }, Form.lp(s, 3))
            }
            addView(text, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(s, 12) })
        }
        body.addView(lead)
        body.addView(fact(s, "Price", Tuning.TM_PRICE))
        body.addView(fact(s, "You own", text = if (owned) "Yes" else "No"))
        show(s, Items.tmName(s.data, move), body, Tuning.TM_PRICE, buy = !owned, onBuy)
    }

    private fun column(s: GamePage) = LinearLayout(s).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(s, 24), Ui.dp(s, 8), Ui.dp(s, 24), 0)
    }

    /** "Label ....... 🪙 200" or "Label ....... text". */
    private fun fact(s: GamePage, label: String, price: Int? = null, text: String? = null): View = LinearLayout(s).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(s, 40)
        addView(Ui.text(s, 13f, 700, R.color.ag_sub0).apply { this.text = label }, LinearLayout.LayoutParams(0, -2, 1f))
        if (price != null) {
            addView(ImageView(s).apply {
                setImageResource(R.drawable.ic_coin)
                setColorFilter(Ui.c(s, R.color.ag_yellow), android.graphics.PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(s, 14), Ui.dp(s, 14)).also { it.marginEnd = Ui.dp(s, 4) })
            addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { this.text = Bits.fmt(price) })
        } else {
            addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { this.text = text.orEmpty() })
        }
    }

    /** The Buy button: coin + price on the yellow-tinted pill, full width; faded (and a toast, no purchase) when the money is short. */
    private fun buyButton(s: GamePage, price: Int, onTap: () -> Unit): View = LinearLayout(s).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = Ui.dp(s, 48)
        val affordable = s.state.cash >= price
        background = Ui.rounded(Bits.blend(Ui.c(s, R.color.ag_yellow), Ui.c(s, R.color.ag_mantle), 0.22f), 14, s)
        alpha = if (affordable) 1f else 0.45f
        contentDescription = "Buy for ${Bits.fmt(price)}"
        addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { text = "Buy" })
        addView(ImageView(s).apply {
            setImageResource(R.drawable.ic_coin)
            setColorFilter(Ui.c(s, R.color.ag_yellow), android.graphics.PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(s, 14), Ui.dp(s, 14)).also { it.marginStart = Ui.dp(s, 10); it.marginEnd = Ui.dp(s, 4) })
        addView(Ui.text(s, 14f, 900, R.color.ag_text).apply { text = Bits.fmt(price) })
        setOnClickListener { onTap() }
    }

    private fun show(s: GamePage, title: String, body: LinearLayout, price: Int, buy: Boolean, onBuy: () -> Unit) {
        lateinit var dlg: AlertDialog
        if (buy) {
            body.addView(buyButton(s, price) {
                if (s.state.cash < price) { Ui.toast(s, "Not enough money (need ${Bits.fmt(price)} ₽)"); return@buyButton }
                dlg.dismiss()
                onBuy()
            }, Form.lp(s, 12))
        } else {
            body.addView(Ui.text(s, 14f, 900, R.color.ag_green).apply { text = "✓ Owned"; gravity = Gravity.CENTER }, Form.lp(s, 12))
        }
        dlg = AlertDialog.Builder(s).setTitle(title).setView(Bits.scroll(s, body))
            .setNegativeButton("Close", null).create()
        Pickers.show(dlg)
    }
}
