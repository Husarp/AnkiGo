package com.husarp.ankigo.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.StreakFreeze
import com.husarp.ankigo.game.Shop
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

internal class ShopPage(base: Context, host: PageHost, private val bagOnly: Boolean = false,
                             private val onBattleEvents: ((List<GameEvent>) -> Unit)? = null,
                             private val fightAvailable: () -> Boolean = { true }) : GamePage(base, host), CardHost {
    override val tab = BottomNav.Tab.SHOP

    private class CategoryLayer(ctx: Context) : android.widget.FrameLayout(ctx) {
        var anchor: View? = null
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val toggle = anchor ?: return
            val drawer = getChildAt(1) ?: return
            val rect = android.graphics.Rect(0, 0, toggle.width, toggle.height)
            (parent as android.view.ViewGroup).offsetDescendantRectToMyCoords(toggle, rect)
            val y = rect.bottom.coerceIn(0, height)
            val x = Ui.dp(context, 12)
            val w = minOf(Ui.dp(context, 208), (width - x * 2).coerceAtLeast(0))
            drawer.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height - y, View.MeasureSpec.AT_MOST))
            drawer.layout(x, y, x + w, y + drawer.measuredHeight)
        }
    }
    private lateinit var categoryLayer: CategoryLayer

    override fun wrapRoot(column: View): View = android.widget.FrameLayout(this).apply {
        addView(column, android.widget.FrameLayout.LayoutParams(-1, -1))
        categoryLayer = CategoryLayer(this@ShopPage).apply { visibility = View.GONE }
        addView(categoryLayer, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    internal fun categories(anchor: View, list: View, close: () -> Unit) {
        categoryLayer.removeAllViews()
        categoryLayer.anchor = anchor
        categoryLayer.visibility = View.VISIBLE
        categoryLayer.addView(View(this).apply {
            contentDescription = "Close categories"; setOnClickListener { closeCategories(close) }
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        val drawer = MotionScrollView(this).apply {
            tag = "bag-category-drawer"
            background = Ui.rounded(Ui.c(this@ShopPage, R.color.ag_card), 12, this@ShopPage)
            elevation = Ui.dp(this@ShopPage, 8).toFloat()
            isClickable = true
            addView(list)
        }
        categoryLayer.addView(drawer, android.widget.FrameLayout.LayoutParams(Ui.dp(this, 208), -2, Gravity.TOP or Gravity.START).apply {
            marginStart = Ui.dp(this@ShopPage, 12)
        })
    }

    internal fun closeCategories(close: () -> Unit) {
        val drawer = categoryLayer.getChildAt(1)
        if (drawer == null) close() else UiMotion.depart(drawer, sideways = true, close = close)
    }

    private lateinit var appBar: AppBar
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabShop: TextView
    private lateinit var tabBag: TextView
    private var showBag = bagOnly
    private val dealClock = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateDeal = object : Runnable {
        override fun run() {
            rollDay()
            requestRender()
            val now = System.currentTimeMillis()
            dealClock.postDelayed(this, 60_000L - now % 60_000L)
        }
    }

    internal fun openBag() {
        closeCard()
        showBag = true
        requestRender()
    }
    private val bag by lazy { BagActions(this, MonActions(this), this, onBattleEvents, fightAvailable) }

    override fun act(beforeReport: (ActionResult) -> Unit,
                     f: (Game) -> ActionResult): ActionResult {
        val play = onBattleEvents ?: return super.act(beforeReport, f)
        val result = GameRepo.run(this, f)
        beforeReport(result)
        report(if (result.ok && result.events.any { it is GameEvent.MoveLearned }) "" else result.message, result.events)
        play(result.events)
        requestRender()
        return result
    }

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
        bag.collapseCategories()
        if (::categoryLayer.isInitialized) {
            categoryLayer.visibility = View.GONE
            categoryLayer.removeAllViews()
        }
        dealClock.removeCallbacks(updateDeal)
        super.onHide()
    }

    override fun onGone() {
        dealClock.removeCallbacks(updateDeal)
        closeWindow()
        super.onGone()
    }

    override fun buildBar(): View {
        // PLAN 237: start with the real amount (the game is usually loaded when you switch to this tab), or no pill until render() knows it: never "0"
        val first = ShopPlan.firstPill(GameRepo.status == GameRepo.Status.READY, GameRepo.state?.cash)
        appBar = Ui.appBar(this, if (bagOnly) "Bag" else "Shop", first, pillIconRes = R.drawable.ic_money)
        appBar.setPill(first)
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
            if (!bagOnly) addView(tabs, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 4) })
            addView(content)
        }
        scroll = MotionScrollView(ctx).apply { addView(col) }
        return scroll
    }

    /** PLAN 218: Shop / Bag are a switch at the top, not sub-pages: the shown one stays and scrolls to its top. */
    override fun onTabReselected() {
        bag.collapseCategories()
        requestRender()
        scrollToTop(scroll)
    }

    override fun onShow() {
        super.onShow()
        if (bagOnly) return
        rollDay()
        dealClock.removeCallbacks(updateDeal)
        dealClock.postDelayed(updateDeal, 60_000L - System.currentTimeMillis() % 60_000L)
    }

    /** New game day: roll the day over and restock once (not in render(), which GameRepo's listener would loop on). */
    internal fun rollDay() {
        if (GameRepo.status == GameRepo.Status.READY && GameRepo.state != null && GameRepo.data != null) {
            GameRepo.run(this) { g ->
                val events = g.tick()
                Shop.ensureToday(g.data, g.state)
                events
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
        if (!bagOnly) markSeen()
        appBar.setPill(Bits.fmt(st.cash))
        tabShop.text = "Shop"
        tabBag.text = "Bag"
        SubtabIcons.apply(tabShop, R.drawable.ic_shop)
        SubtabIcons.apply(tabBag, R.drawable.ic_item_bag)
        for ((t, on) in listOf(tabShop to !showBag, tabBag to showBag)) {
            t.setTextColor(Ui.c(this, if (on) R.color.ag_text else R.color.ag_sub0))
            t.background = if (on) Ui.rounded(Ui.c(this, R.color.ag_card), 10, this) else null
        }
        categoryLayer.visibility = View.GONE
        categoryLayer.removeAllViews()
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
            "streak-freeze" -> if (Shop.freezeOnSale(st)) itemSpec(StreakFreeze.ITEM) else null
            else -> (Shop.basics().map { ShopPlan.Tile.Item(it) }.firstOrNull { CardKeys.basic(it) == key }
                ?: ShopPlan.specials(st.shopItems, st.shopTms).firstOrNull { CardKeys.special(it) == key })?.let { tileSpec(it) }
        }
    }

    private fun tileSpec(t: ShopPlan.Tile): ItemCard.Spec = when (t) {
        is ShopPlan.Tile.Item -> itemSpec(t.id)
        is ShopPlan.Tile.Tm -> tmSpec(t.move)
    }

    /** A Shop item with its current price for affordability and the two-tap purchase action. */
    private fun itemSpec(item: String) = ItemCard.Spec(item, buy = ItemCard.Buy(ShopPlan.price(data, state, item), Shop.soldOut(state, item), Shop.limited(item) || item == StreakFreeze.ITEM) { buyItem(item) })

    /** A Shop TM with Buy, or "✓ Owned" once it is yours. */
    private fun tmSpec(move: String) = ItemCard.Spec(null, move, buy = ItemCard.Buy(ShopPlan.tmPrice(), move in state.tms) { buyTm(move) })

    // ---- shop tab ----

    private fun fillShop() {
        val ctx = this
        val st = state
        val cols = 1
        val minutesLeft = ShopPlan.minutesToNewDay(System.currentTimeMillis(), st.settings.dayStartHour)

        // The deal card keeps its unit and old prices; a tap opens the item window for confirmation.
        val dealNow = System.currentTimeMillis()
        val dailyOffer = Shop.dealFor(data, st.day, dealNow)?.let { deal ->
            val price = ShopPlan.price(data, st.day, deal.item, dealNow)
            val name = Items.name(deal.item)
            ShopViews.dealCard(ctx, deal.item, name, price, ShopPlan.dealEndsText(dealNow),
                ItemCardPlan.detailsLabel(ShopPlan.describe("$name, ${ItemInfo.plain(ItemInfo.line(data, deal.item, st.caught))}", price)), ItemInfo.plain(ItemInfo.line(data, deal.item, st.caught)), night = Shop.isNight(dealNow), stock = stock(deal.item)) { openCard(CardKeys.DEAL) }
        }

        // the weekend special (Saturday and Sunday only)
        val weekendOffer = Shop.weekendFor(data, st.day)?.let { weekendCard(it) }
        content.addView(ShopViews.offers(ctx, dailyOffer, weekendOffer), LinearLayout.LayoutParams(-1, -2))

        content.addView(Bits.sect(ctx, "Basics"))
        if (Shop.freezeOnSale(st)) content.addView(
            ShopViews.tile(ctx, ShopViews.itemArt(ctx, StreakFreeze.ITEM, 64), "Streak Freeze",
                priceView(ShopPlan.price(data, st, StreakFreeze.ITEM), 14f), "Streak Freeze, ${st.count(StreakFreeze.ITEM)} held",
                sub = "In Bag: ${st.count(StreakFreeze.ITEM)} / 3 · used automatically", stock = stock(StreakFreeze.ITEM)) { openCard("streak-freeze") },
            LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })

        // always in stock: the basics at their normal price
        tileRows(Shop.basics().map { ShopPlan.Tile.Item(it) }, cols, CardKeys::basic)

        // today's specials: title and "new in N h", then the "Refresh shop" button (a real button, so it is easy to find; PLAN 108), then the tiles
        content.addView(Bits.sect(ctx, "Today's specials", "New in ${ShopPlan.countdown(minutesLeft)}"))
        content.addView(refreshButton(), LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 10) })
        val specials = ShopPlan.specials(st.shopItems, emptyList())
        if (specials.isEmpty()) content.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "Nothing in the shop yet." })
        tileRows(specials, cols, CardKeys::special)
        if (st.shopTms.isNotEmpty()) {
            content.addView(Bits.sect(ctx, "TMs"))
            tileRows(st.shopTms.map { ShopPlan.Tile.Tm(it) }, cols, CardKeys::special)
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
            val price = ShopPlan.price(data, state, t.id)
            val name = Items.name(t.id)
            val short = ItemInfo.plain(ItemInfo.short(data, t.id, state.caught))
            ShopViews.tile(this, ShopViews.itemArt(this, t.id, 64), name, priceView(price, 14f),
                ItemCardPlan.detailsLabel(ShopPlan.describe("$name, $short", price)), sub = short, stock = stock(t.id)) { openCard(key) }
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
        return ShopViews.tile(this, Bits.tmTile(this, m?.type, 64), name, bottom,
            ItemCardPlan.detailsLabel(ShopPlan.describe(listOfNotNull(name, m?.type?.label).joinToString(", "), price, owned)),
            subView = m?.let { Ui.typeChip(this, it.type) }, stock = if (owned) "Sold out" else "1 left") { openCard(key) }
    }

    /** The weekend special: an exclusive held item at its fixed price, until Sunday night. */
    private fun weekendCard(w: Shop.Special): View {
        val ctx = this
        return if (w.isTm) {
            val owned = w.id in state.tms
            val name = Items.tmName(data, w.id)
            val price = ShopPlan.tmPrice()
            ShopViews.weekendCard(ctx, Bits.tmTile(ctx, data.move(w.id)?.type, 48), name, ShopPlan.weekendEndsText(w.endsDay, state.settings.dayStartHour),
                if (owned) ShopViews.owned(ctx, 14f) else priceView(price, 17f), ItemCardPlan.detailsLabel(ShopPlan.describe(name, price, owned))) { openCard(CardKeys.WEEKEND) }
        } else {
            val price = ShopPlan.price(data, state.day, w.id)
            val name = Items.name(w.id)
            ShopViews.weekendCard(ctx, ShopViews.itemArt(ctx, w.id, 48), name, ShopPlan.weekendEndsText(w.endsDay, state.settings.dayStartHour), priceView(price, 17f),
                ItemCardPlan.detailsLabel(ShopPlan.describe(name, price)), stock = stock(w.id)) { openCard(CardKeys.WEEKEND) }
        }
    }

    private fun stock(item: String): String? = if (Shop.limited(item)) { if (Shop.soldOut(state, item)) "Sold out" else "1 left" } else null

    private fun buyItem(item: String) {
        val quantity = if (Shop.limited(item) || item == StreakFreeze.ITEM) 1 else open.quantity
        open.bought()
        if (act { g -> Shop.buy(g.data, g.state, item, quantity = quantity) }.ok) { GameSound.ui(this, SoundId.BUY); window?.purchase(appBar.pill ?: appBar) }
    }

    private fun buyTm(move: String) {
        open.bought()
        if (act { g -> Shop.buyTm(g.data, g.state, move) }.ok) { GameSound.ui(this, SoundId.BUY); window?.purchase(appBar.pill ?: appBar) }
    }

    /** "Refresh shop · [coin] 100": the accent button with the refresh icon (greyed, with a toast on tap, when the money is not enough). */
    private fun refreshButton(): View {
        val ctx = this
        val ink = Ui.c(ctx, R.color.ag_hold_ink)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8))
            alpha = if (state.cash >= Shop.refreshPrice(state)) 1f else 0.45f
            contentDescription = "Refresh shop for ${CoinText.word(Shop.refreshPrice(state))}"
            addView(android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_sync)
                setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginEnd = Ui.dp(ctx, 7) })
            addView(Ui.text(ctx, 14f, 900, R.color.ag_base).apply { text = TextUtils.concat("Refresh · ", CoinText.of(this, "${Shop.refreshPrice(state)}")); maxLines = 2; gravity = Gravity.CENTER })
            HoldToConfirm.bind(this, "refresh shop", allowed = { state.cash >= Shop.refreshPrice(state) }) { reroll() }
        }
    }

    private fun reroll() {
        if (state.cash < Shop.refreshPrice(state)) { Ui.toast(this, "Not enough money (need ${CoinText.word(Shop.refreshPrice(state))})"); return }
        act { g -> Shop.reroll(g.data, g.state, g.rng) }
    }
}

/** The Shop tab: a thin shell around [ShopPage] (R10 W11; Dest.SHOP). */
class ShopActivity : PageActivity() {
    private lateinit var shop: ShopPage
    override fun createPage(): GamePage = ShopPage(this, this).also {
        shop = it
        if (intent.getBooleanExtra(EXTRA_BAG, false)) it.openBag()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_BAG, false)) shop.openBag()
    }

    companion object {
        const val EXTRA_BAG = "open_bag"
        internal fun openBag(a: Context) {
            val intent = Nav.intent(a, Dest.SHOP).putExtra(EXTRA_BAG, true)
            if (Ui.findActivity(a) == null) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(intent)
        }
    }
}
