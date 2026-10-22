package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.sound.SoundId

// TICKET T14 owns this file (the daily shop and the bag, mockup frame E); R10 W11 made it the Shop TAB (mockups T5 / F5): the deal of the day, always in
// stock, today's specials (items + TMs in rows), the weekend special. The pieces are in ShopViews.kt, the rules in game/Economy.kt `Shop`, the
// numbers in ShopPlan.kt. The Bag tab lives in ShopBag.kt. It is the page of ShopActivity (the Shop tab) and of the floating card.
// R14 batch 2 (PLAN 168) made a tap on a tile, the deal, the weekend card or a Bag row open its card in place; R15 (PLAN 210) opens it in a WINDOW
// over the list instead ([ItemWindow], the card's parts are [ItemCard]); one window at a time.

/** What the Shop and Bag lists need from their page to open and close the item window. */
internal interface CardHost {
    val open: OpenCard
    /** A tap on a tile, row or card: the window of the item with [key] opens. */
    fun openCard(key: String)
    /** The window closes (a button of it opens a picker or a confirm, which are shown over the page itself). */
    fun closeCard()
}

internal class ShopPage(base: Context, host: PageHost) : GamePage(base, host), CardHost {
    override val tab = BottomNav.Tab.SHOP

    private lateinit var appBar: AppBar
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabShop: TextView
    private lateinit var tabBag: TextView
    private var showBag = false
    private val bag by lazy { BagActions(this, MonActions(this), this) }

    // the open item: its key survives a re-render (Buy keeps its window open with the new count) but not leaving the page
    override val open = OpenCard()
    private var window: ItemWindow? = null

    /** The item window while one is open (the shots look at it). */
    internal val itemWindow: ItemWindow? get() = window

    override fun openCard(key: String) {
        open.open(key)
        requestRender()
    }

    override fun closeCard() {
        open.reset()
        closeWindow()
    }

    private fun closeWindow() {
        val w = window ?: return
        window = null
        w.dismiss()
    }

    override fun onHide() {
        closeCard()
        super.onHide()
    }

    override fun onGone() {
        closeWindow()
        super.onGone()
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Shop", "0", pillIconRes = R.drawable.ic_money)
        // "?" opens the guide chapter about money (R3-V3b)
        appBar.addAction(R.drawable.ic_help, "Help: how money works", R.color.ag_mauve) { openMoneyHelp() }
        return appBar
    }

    /** The guide chapter about money. */
    internal fun openMoneyHelp() {
        com.husarp.ankigo.help.HelpActivity.open(host.hostActivity ?: this, chapter = com.husarp.ankigo.help.GameGuide.MONEY)
    }

    override fun buildBody(): View {
        val ctx = this
        tabShop = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { closeCard(); showBag = false; requestRender() } }
        tabBag = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { closeCard(); showBag = true; requestRender() } }
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
        scroll = ScrollView(ctx).apply { addView(col) }
        return scroll
    }

    override fun onShow() {
        super.onShow()
        rollDay()
    }

    /** New game day: roll the day over and restock once (not in render(), which GameRepo's listener would loop on). */
    internal fun rollDay() {
        if (GameRepo.status == GameRepo.Status.READY && GameRepo.state != null && GameRepo.data != null) {
            GameRepo.run(this) { g ->
                g.tick()
                Shop.ensureToday(g.data, g.state)
            }
        }
    }

    /** The Shop tab was opened today: the red dot of the bottom bar goes ([Shop.unseen]). Written once per game day. */
    private fun markSeen() {
        val day = state.day
        if (day.isNotEmpty() && AppPrefs.shopSeenDay(this) != day) AppPrefs.setShopSeenDay(this, day)
    }

    override fun render() {
        val st = state
        markSeen()
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
        syncWindow()
    }

    /**
     * The window follows [open] on every render: it opens for the item asked for, is drawn again in place (the new count after Buy or a trade;
     * never a second window), and goes when the item is gone (the last Potion was used, the shop restocked) so it does not pop up again later.
     */
    private fun syncWindow() {
        val key = open.key
        val spec = key?.let { cardSpec(it) }
        if (key != null && spec == null) open.reset()
        if (spec == null) { closeWindow(); return }
        val w = window ?: ItemWindow(this) { window = null; open.reset() }.also { window = it }
        w.update(spec)
        w.show()
    }

    /** What the window of [key] shows and does, or null when that item is not on this tab any more. */
    private fun cardSpec(key: String): ItemCard.Spec? {
        if (showBag) return bag.spec(key)
        val st = state
        return when (key) {
            CardKeys.DEAL -> Shop.dealFor(data, st.day)?.let { itemSpec(it.item) }
            CardKeys.WEEKEND -> Shop.weekendFor(data, st.day)?.let { if (it.isTm) tmSpec(it.id) else itemSpec(it.id) }
            else -> (Shop.basics().map { ShopPlan.Tile.Item(it) }.firstOrNull { CardKeys.basic(it) == key }
                ?: ShopPlan.specials(st.shopItems, st.shopTms).firstOrNull { CardKeys.special(it) == key })?.let { tileSpec(it) }
        }
    }

    private fun tileSpec(t: ShopPlan.Tile): ItemCard.Spec = when (t) {
        is ShopPlan.Tile.Item -> itemSpec(t.id)
        is ShopPlan.Tile.Tm -> tmSpec(t.move)
    }

    /** A Shop item with its Buy button (the deal's item at the deal price, with the old price struck through). */
    private fun itemSpec(item: String) = ItemCard.Spec(item, buy = ItemCard.Buy(ShopPlan.price(data, state.day, item), false) { buyItem(item) })

    /** A Shop TM with Buy, or "✓ Owned" once it is yours. */
    private fun tmSpec(move: String) = ItemCard.Spec(null, move, buy = ItemCard.Buy(ShopPlan.tmPrice(), move in state.tms) { buyTm(move) })

    // ---- shop tab ----

    private fun hoursToNewDay(): Int = ShopPlan.hoursToNewDay(System.currentTimeMillis(), state.settings.dayStartHour)

    private fun fillShop() {
        val ctx = this
        val st = state
        val cols = ShopPlan.columns(resources.configuration.screenWidthDp, resources.configuration.fontScale)
        val gap = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 4) }

        // the deal of the day (F5 option A): the card at the top; a tap opens its window (Buy shows the old price struck through)
        Shop.dealFor(data, st.day)?.let { deal ->
            val price = ShopPlan.price(data, st.day, deal.item)
            val name = Items.name(deal.item)
            content.addView(ShopViews.dealCard(ctx, deal.item, name, price, ShopPlan.endsText(st.settings.dayStartHour, hoursToNewDay()),
                ItemCardPlan.detailsLabel(ShopPlan.describe("$name, ${ItemInfo.plain(ItemInfo.line(data, deal.item))}", price)), ItemInfo.plain(ItemInfo.line(data, deal.item))) { openCard(CardKeys.DEAL) }, gap)
        }

        // always in stock: the basics at their normal price
        content.addView(Bits.sect(ctx, "Always in stock"))
        tileRows(Shop.basics().map { ShopPlan.Tile.Item(it) }, cols, CardKeys::basic)

        // today's specials: title and "new in N h", then the "Refresh shop" button (a real button, so it is easy to find; PLAN 108), then the tiles
        content.addView(Bits.sect(ctx, "Today's specials", "new in ${hoursToNewDay()} h"))
        content.addView(refreshButton(), LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 10) })
        val specials = ShopPlan.specials(st.shopItems, st.shopTms)
        if (specials.isEmpty()) content.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "Nothing in the shop yet." })
        tileRows(specials, cols, CardKeys::special)

        // the weekend special (Saturday and Sunday only)
        Shop.weekendFor(data, st.day)?.let { w ->
            content.addView(Bits.sect(ctx, "Weekend special", "Sat & Sun"))
            content.addView(weekendCard(w), LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** [tiles] in rows of [cols] equal tiles (the last row is filled up with blanks so its tiles keep the width); a tap on a tile opens its window. */
    private fun tileRows(tiles: List<ShopPlan.Tile>, cols: Int, key: (ShopPlan.Tile) -> String) {
        val ctx = this
        for (chunk in tiles.chunked(cols)) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val t = chunk.getOrNull(i)
                val lp = LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = Ui.dp(ctx, 8) }
                row.addView(if (t != null) tile(t, key(t)) else View(ctx), lp)
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
        }
    }

    private fun tile(t: ShopPlan.Tile, key: String): View = when (t) {
        is ShopPlan.Tile.Item -> {
            val price = ShopPlan.price(data, state.day, t.id)
            val name = Items.name(t.id)
            val short = ItemInfo.plain(ItemInfo.short(data, t.id))
            ShopViews.tile(this, Bits.itemTile(this, t.id, 36), name, priceView(price, 14f),
                ItemCardPlan.detailsLabel(ShopPlan.describe("$name, $short", price)), sub = short) { openCard(key) }
        }
        is ShopPlan.Tile.Tm -> tmTile(t.move, key)
    }

    /** A price, faded while the money is short (a tap still opens the card; Buy there says so). */
    private fun priceView(price: ShopPlan.Price, discountSp: Float): View =
        ShopViews.price(this, price, nowSp = discountSp).also { if (state.cash < price.now) it.alpha = 0.5f }

    /** A TM tile: its type chip is the short line. */
    private fun tmTile(move: String, key: String): View {
        val owned = move in state.tms
        val m = data.move(move)
        val name = Items.tmName(data, move)
        val price = ShopPlan.tmPrice()
        val bottom = if (owned) ShopViews.owned(this) else priceView(price, 14f)
        return ShopViews.tile(this, Bits.tmTile(this, m?.type, 36), name, bottom,
            ItemCardPlan.detailsLabel(ShopPlan.describe(listOfNotNull(name, m?.type?.label).joinToString(", "), price, owned)),
            subView = m?.let { Ui.typeChip(this, it.type) }) { openCard(key) }
    }

    /** The weekend special: an evolution stone or a strong TM at its normal price, until Sunday night. */
    private fun weekendCard(w: Shop.Special): View {
        val ctx = this
        return if (w.isTm) {
            val owned = w.id in state.tms
            val name = Items.tmName(data, w.id)
            val price = ShopPlan.tmPrice()
            ShopViews.weekendCard(ctx, Bits.tmTile(ctx, data.move(w.id)?.type, 40), name, ShopPlan.WEEKEND_NOTE,
                if (owned) ShopViews.owned(ctx, 14f) else priceView(price, 17f), ItemCardPlan.detailsLabel(ShopPlan.describe(name, price, owned))) { openCard(CardKeys.WEEKEND) }
        } else {
            val price = ShopPlan.price(data, state.day, w.id)
            val name = Items.name(w.id)
            ShopViews.weekendCard(ctx, Bits.itemTile(ctx, w.id, 40), name, ShopPlan.WEEKEND_NOTE, priceView(price, 17f),
                ItemCardPlan.detailsLabel(ShopPlan.describe(name, price))) { openCard(CardKeys.WEEKEND) }
        }
    }

    private fun buyItem(item: String) {
        if (act { g -> Shop.buy(g.data, g.state, item) }.ok) GameSound.ui(this, SoundId.BUY)
    }

    private fun buyTm(move: String) {
        if (act { g -> Shop.buyTm(g.data, g.state, move) }.ok) GameSound.ui(this, SoundId.BUY)
    }

    /** "Refresh shop · 100 ₽": the accent button with the refresh icon (greyed, with a toast on tap, when the money is not enough). */
    private fun refreshButton(): View {
        val ctx = this
        val ink = Ui.c(ctx, R.color.ag_base)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(ctx, 44)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8))
            alpha = if (state.cash >= Tuning.REROLL_PRICE) 1f else 0.45f
            contentDescription = "Refresh shop for ${Tuning.REROLL_PRICE} ₽"
            addView(android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_sync)
                setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginEnd = Ui.dp(ctx, 7) })
            addView(Ui.text(ctx, 14f, 900, R.color.ag_base).apply { text = "Refresh shop · ${Tuning.REROLL_PRICE} ₽"; maxLines = 2; gravity = Gravity.CENTER })
            setOnClickListener { reroll() }
        }
    }

    private fun reroll() {
        if (state.cash < Tuning.REROLL_PRICE) { Ui.toast(this, "Not enough money (need ${Tuning.REROLL_PRICE} ₽)"); return }
        Pickers.confirm(this, "Refresh the shop?", "New items and TMs for ${Tuning.REROLL_PRICE} ₽.", "Refresh") {
            act { g -> Shop.reroll(g.data, g.state, g.rng) }
        }
    }
}

/** The Shop tab: a thin shell around [ShopPage] (R10 W11; Dest.SHOP). */
class ShopActivity : PageActivity() {
    override fun createPage(): GamePage = ShopPage(this, this)
}
