package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Rect
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
// R14 batch 2 (PLAN 168): a tap on a tile, the deal, the weekend card or a Bag row opens its card IN PLACE ([ItemCard]); one card at a time.

/** What the Shop and Bag lists need from their page to open and fold cards. */
internal interface CardHost {
    val open: OpenCard
    fun toggleCard(key: String)
    /** The page drew the open card [view] (it is scrolled into view the first time). */
    fun cardShown(view: View)
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

    // the open card: its key survives a re-render (Buy keeps the card open with the new count) but not leaving the page
    override val open = OpenCard()
    private var openView: View? = null
    private var revealed: String? = null

    override fun toggleCard(key: String) {
        open.toggle(key)
        requestRender()
    }

    override fun cardShown(view: View) { openView = view }

    override fun onHide() {
        open.reset()
        super.onHide()
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
        tabShop = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { open.reset(); showBag = false; requestRender() } }
        tabBag = Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { gravity = Gravity.CENTER; setOnClickListener { open.reset(); showBag = true; requestRender() } }
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
        openView = null
        open.beginRender()
        if (showBag) bag.fill(content) else fillShop()
        open.endRender()
        revealOpenCard()
    }

    /** The card that was just opened scrolls into view (once; a re-render of the same open card, e.g. after Buy, leaves the scroll alone). */
    private fun revealOpenCard() {
        val key = open.key
        if (key == null) { revealed = null; return }
        val card = openView ?: return
        if (key == revealed) return
        revealed = key
        card.post { card.requestRectangleOnScreen(Rect(0, 0, card.width, card.height), false) }
    }

    // ---- shop tab ----

    private fun hoursToNewDay(): Int = ShopPlan.hoursToNewDay(System.currentTimeMillis(), state.settings.dayStartHour)

    private fun fillShop() {
        val ctx = this
        val st = state
        val cols = ShopPlan.columns(resources.configuration.screenWidthDp, resources.configuration.fontScale)
        val gap = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 4) }

        // the deal of the day (F5 option A): the card at the top; opened, its item card replaces it (Buy shows the old price struck through)
        Shop.dealFor(data, st.day)?.let { deal ->
            val price = ShopPlan.price(data, st.day, deal.item)
            val name = Items.name(deal.item)
            if (open.isOpen(CardKeys.DEAL)) content.addView(shopCard(CardKeys.DEAL, deal.item, null, price), cardLp())
            else content.addView(ShopViews.dealCard(ctx, deal.item, name, price, ShopPlan.endsText(st.settings.dayStartHour, hoursToNewDay()),
                ItemCardPlan.foldedLabel(ShopPlan.describe("$name, ${ItemInfo.plain(ItemInfo.line(data, deal.item))}", price)), ItemInfo.plain(ItemInfo.line(data, deal.item))) { toggleCard(CardKeys.DEAL) }, gap)
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
            content.addView(if (open.isOpen(CardKeys.WEEKEND)) weekendOpen(w) else weekendCard(w), if (open.key == CardKeys.WEEKEND) cardLp() else LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** An opened card sits 2 dp under what is above it and 8 dp above what follows. */
    private fun cardLp() = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 2); it.bottomMargin = Ui.dp(this, 8) }

    /** [tiles] in rows of [cols] equal tiles (the last row is filled up with blanks so its tiles keep the width); the open tile is its card across the full width, the rest of its row follows below. */
    private fun tileRows(tiles: List<ShopPlan.Tile>, cols: Int, key: (ShopPlan.Tile) -> String) {
        val ctx = this
        val openTile = tiles.firstOrNull { open.key == key(it) }
        for (r in ShopPlan.rows(tiles, cols, openTile)) {
            if (r is ShopPlan.Row.Card) {
                val k = key(r.tile)
                open.isOpen(k)
                content.addView(when (val t = r.tile) {
                    is ShopPlan.Tile.Item -> shopCard(k, t.id, null, ShopPlan.price(data, state.day, t.id))
                    is ShopPlan.Tile.Tm -> shopCard(k, null, t.move, ShopPlan.tmPrice())
                }, cardLp())
                continue
            }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val chunk = (r as ShopPlan.Row.Tiles).tiles
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
                ItemCardPlan.foldedLabel(ShopPlan.describe("$name, $short", price)), sub = short) { toggleCard(key) }
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
            ItemCardPlan.foldedLabel(ShopPlan.describe(listOfNotNull(name, m?.type?.label).joinToString(", "), price, owned)),
            subView = m?.let { Ui.typeChip(this, it.type) }) { toggleCard(key) }
    }

    /** The opened card of a Shop item ([item]) or TM ([move]) with its Buy button; a tap on its header folds it. */
    private fun shopCard(key: String, item: String?, move: String?, price: ShopPlan.Price): View {
        val owned = move != null && move in state.tms
        val spec = ItemCard.Spec(item, move, buy = ItemCard.Buy(price, owned) { if (item != null) buyItem(item) else buyTm(move.orEmpty()) }, onFold = { toggleCard(key) })
        return ItemCard.build(this, spec).also { cardShown(it) }
    }

    /** The weekend special: an evolution stone or a strong TM at its normal price, until Sunday night. */
    private fun weekendCard(w: Shop.Special): View {
        val ctx = this
        return if (w.isTm) {
            val owned = w.id in state.tms
            val name = Items.tmName(data, w.id)
            val price = ShopPlan.tmPrice()
            ShopViews.weekendCard(ctx, Bits.tmTile(ctx, data.move(w.id)?.type, 40), name, ShopPlan.WEEKEND_NOTE,
                if (owned) ShopViews.owned(ctx, 14f) else priceView(price, 17f), ItemCardPlan.foldedLabel(ShopPlan.describe(name, price, owned))) { toggleCard(CardKeys.WEEKEND) }
        } else {
            val price = ShopPlan.price(data, state.day, w.id)
            val name = Items.name(w.id)
            ShopViews.weekendCard(ctx, Bits.itemTile(ctx, w.id, 40), name, ShopPlan.WEEKEND_NOTE, priceView(price, 17f),
                ItemCardPlan.foldedLabel(ShopPlan.describe(name, price))) { toggleCard(CardKeys.WEEKEND) }
        }
    }

    private fun weekendOpen(w: Shop.Special): View =
        if (w.isTm) shopCard(CardKeys.WEEKEND, null, w.id, ShopPlan.tmPrice()) else shopCard(CardKeys.WEEKEND, w.id, null, ShopPlan.price(data, state.day, w.id))

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
