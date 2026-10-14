package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.Tuning
import java.util.Calendar

// TICKET T14 owns this file: the daily shop and the bag (mockup frame E). The Bag tab lives in ShopBag.kt.
// Ankimon has no always-in-stock list: today's items and TMs are all there is (reroll costs 100).
class ShopPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab = BottomNav.Tab.SHOP

    private lateinit var appBar: AppBar
    private lateinit var content: LinearLayout
    private lateinit var tabShop: TextView
    private lateinit var tabBag: TextView
    private var showBag = false
    private val bag by lazy { BagActions(this, MonActions(this)) }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Shop", "0", pillIconRes = R.drawable.ic_coin)
        return appBar
    }

    override fun buildBody(): View {
        val ctx = this
        tabShop = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { showBag = false; requestRender() } }
        tabBag = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { showBag = true; requestRender() } }
        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
            for (t in listOf(tabShop, tabBag)) {
                t.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12)) // 42 dp tabs in a 48 dp control
                addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 12))
            addView(tabs, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 4) })
            addView(content)
        }
        return ScrollView(ctx).apply { addView(col) }
    }

    override fun onShow() {
        super.onShow()
        // New game day: roll the day over and restock once (not in render(), which GameRepo's listener would loop on).
        if (GameRepo.status == GameRepo.Status.READY && GameRepo.state != null && GameRepo.data != null) {
            GameRepo.run(this) { g ->
                g.tick()
                Shop.ensureToday(g.data, g.state)
            }
        }
    }

    override fun render() {
        val st = state
        appBar.setPill(Bits.fmt(st.cash))
        val bagCount = st.bag.values.sum() + st.tms.size
        tabShop.text = "Shop"
        tabBag.text = "Bag · $bagCount"
        for ((t, on) in listOf(tabShop to !showBag, tabBag to showBag)) {
            t.setTextColor(Ui.c(this, if (on) R.color.ag_text else R.color.ag_sub0))
            t.background = if (on) Ui.rounded(Ui.c(this, R.color.ag_card), 10, this) else null
        }
        content.removeAllViews()
        if (showBag) bag.fill(content) else fillShop()
    }

    // ---- shop tab ----

    private fun hoursToNewDay(): Int {
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, state.settings.dayStartHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return ((next - now + 3_599_999L) / 3_600_000L).toInt().coerceAtLeast(1)
    }

    private fun fillShop() {
        val ctx = this
        val st = state
        // Section "Today's specials": title, "new in N h", reroll button (48 dp touch height, 28 dp look).
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 4), 0, 0, 0)
            addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply { text = "TODAY'S SPECIALS"; letterSpacing = 0.058f })
            addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "  new in ${hoursToNewDay()} h" }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Ui.text(ctx, 11f, 900, R.color.ag_text).apply {
                text = "Reroll ${Tuning.REROLL_PRICE} ₽"
                Ui.touchPill(this, Ui.rounded(Bits.blend(Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_mantle), 0.22f), 10, ctx), 28, 9)
                alpha = if (st.cash >= Tuning.REROLL_PRICE) 1f else 0.45f
                setOnClickListener { reroll() }
            })
        }
        content.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        if (st.shopItems.isEmpty()) content.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "Nothing in the shop yet." })
        // the specials as equal tiles in rows of three (mockup E)
        for (chunk in st.shopItems.chunked(3)) {
            val tiles = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 3) {
                val item = chunk.getOrNull(i)
                val lp = LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = Ui.dp(ctx, 8) }
                tiles.addView(if (item != null) specialTile(item) else View(ctx), lp)
            }
            content.addView(tiles, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
        }

        content.addView(Bits.sect(ctx, "Today's TMs", "${Bits.fmt(Tuning.TM_PRICE)} ₽ each, never used up"))
        if (st.shopTms.isEmpty()) content.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "No TMs today." })
        for (move in st.shopTms) {
            val m = data.move(move)
            val owned = move in st.tms
            val right: View = if (owned) {
                Ui.text(ctx, 12f, 900, R.color.ag_green).apply { text = "✓ Owned" }
            } else {
                Bits.buyButton(ctx, Tuning.TM_PRICE, st.cash >= Tuning.TM_PRICE) { buyTm(move) }
            }
            val tile = if (m != null) Bits.textTile(ctx, "TM", Ui.typeColor(m.type)) else Bits.textTile(ctx, "TM", Ui.c(ctx, R.color.ag_sub0))
            val mid = Bits.bodyText(ctx, Items.tmName(data, move), Bits.moveStats(data, move))
            if (m != null) mid.addView(Bits.typeRow(ctx, listOf(m.type)), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
            content.addView(Bits.cardRow(ctx, tile, mid, right))
        }
    }

    /** One of today's specials (mockup `.spec`): icon, name, note, price pill, "×N" owned in the corner. The whole tile buys. */
    private fun specialTile(item: String): View {
        val ctx = this
        val st = state
        val price = data.price(item)
        val tile = FrameLayout(ctx).apply {
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
            isClickable = true
            setOnClickListener { buyItem(item) }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 8))
        }
        col.addView(Bits.itemTile(ctx, item, 44))
        col.addView(Ui.text(ctx, 12f, 900, R.color.ag_text).apply {
            text = Items.name(item); gravity = Gravity.CENTER; maxLines = 2
        }, Form.lp(ctx, 7))
        col.addView(Ui.text(ctx, 10.5f, 700, R.color.ag_sub0).apply {
            text = Items.def(item)?.note.orEmpty(); gravity = Gravity.CENTER; maxLines = 2
        }, Form.lp(ctx, 2))
        col.addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f)) // price pills line up at the bottom of equal tiles
        col.addView(Bits.buyButton(ctx, price, st.cash >= price, compact = true) { buyItem(item) },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        tile.addView(col, FrameLayout.LayoutParams(-1, -1))
        tile.addView(Ui.text(ctx, 10f, 800, R.color.ag_sub0).apply { text = "×${st.count(item)}" },
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).also { it.topMargin = Ui.dp(ctx, 7); it.rightMargin = Ui.dp(ctx, 8) })
        return tile
    }

    private fun buyItem(item: String) {
        act { g -> Shop.buy(g.data, g.state, item) }
    }

    private fun buyTm(move: String) {
        act { g -> Shop.buyTm(g.data, g.state, move) }
    }

    private fun reroll() {
        if (state.cash < Tuning.REROLL_PRICE) { Ui.toast(this, "Not enough money (need ${Tuning.REROLL_PRICE} ₽)"); return }
        Pickers.confirm(this, "Reroll the shop?", "New items and TMs for ${Tuning.REROLL_PRICE} ₽.", "Reroll") {
            act { g -> Shop.reroll(g.data, g.state, g.rng) }
        }
    }
}

/** The Shop screen as its own Activity: a thin shell around [ShopPage]. */
class ShopActivity : PageActivity() {
    override fun createPage(): GamePage = ShopPage(this, this)
}
