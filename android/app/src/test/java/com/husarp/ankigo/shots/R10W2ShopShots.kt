package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.ShopPlan
import com.husarp.ankigo.ui.ShopViews
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/*
 * TICKET R10-W2-K (W11, T5 / F5): the Shop as the 5th bottom tab and its page: the deal of the day (card A: peach glow, ribbon, the old price struck
 * through with its coin over the bigger new price), ALWAYS IN STOCK, TODAY'S SPECIALS (items + TMs in tiles), the weekend special (Sat / Sun), the buy sheet
 * at the deal price, and the red dot on the Shop tab of the 5-tab bar.
 * PNGs: r10w2shop-<weekday|weekend|weekend-tm|poor|bag|float|deal-card|prices|sheet-deal|sheet-plain|bar>-<phone|tall|w320|land|float>[-x2_0]-<light|dark>.
 */
class R10W2ShopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val WEEKDAY = "2026-10-14" // a Wednesday
    private val SATURDAY = "2026-10-17"
    private val SUNDAY = "2026-10-18"

    private val TALL = Variant("tall", 412, 1500)
    private val W320 = Variant("w320", 320, 1900)
    private val LAND = Variant("land", 869, 700)
    private val FLOAT = Variant("float", 340, 480)
    private val CARD = Variant("card", 412, 170)
    private val CARD320 = Variant("card320", 320, 330)
    private val PRICES = Variant("prices", 412, 260)
    private val BAR412 = Variant("p412", 412, 250)
    private val BAR320 = Variant("p320", 320, 250)

    // ---------- helpers ----------

    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /** Renders [build] for every variant and theme (font [scale]); [check] runs on the laid-out view before the snapshot. */
    private fun shoot(name: String, variants: List<Variant>, scale: Float = 1f, themes: List<Theme> = Theme.entries,
                      check: (Context, View) -> Unit = { _, _ -> }, build: (Context) -> View) {
        for (v in variants) for (theme in themes) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val view = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
            layoutNow(ctx, frame)
            try { check(ctx, view) } catch (e: Throwable) { throw AssertionError("${v.label}/${theme.name}: ${e.message}", e) }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r10w2shop-$name-$label-${theme.name.lowercase()}", 1000)
        }
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun descriptions(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        v.contentDescription?.let { out += it.toString() }
        if (v is ViewGroup) for (i in 0 until v.childCount) descriptions(v.getChildAt(i), out)
        return out
    }

    private fun findText(v: View, pred: (String) -> Boolean): TextView? {
        if (v is TextView && v.visibility != View.GONE && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    private fun clickable(v: View?): View? { var x = v; while (x != null && !x.isClickable) x = x.parent as? View; return x }

    /** The game day [day] with its own stock, plus a few TMs (the fake world has one weak TM only), and [cash] coins. */
    private fun forceDay(day: String, cash: Int, tms: List<String> = listOf("thunderbolt", "bodyslam", "earthquake")) {
        st.day = day
        st.shopDay = null
        Shop.ensureToday(gd, st)
        st.shopTms.clear(); st.shopTms += tms
        st.cash = cash
    }

    /** The Shop tab drawn on [day]: onShow's roll-over ticks to the real day, so the day is forced afterwards and the page drawn again. */
    private fun shopTab(ctx: Context, day: String, cash: Int = 5000, tms: List<String> = listOf("thunderbolt", "bodyslam", "earthquake")): Pair<ShopPage, View> {
        AppPrefs.setShopSeenDay(ctx, "never")
        val p = ShopPage(ctx, NoHost)
        val screen = pageScreen(ctx, p)
        forceDay(day, cash, tms)
        call(p, "render")
        return p to screen
    }

    /** Swaps the fake world for one with a species that learns strong TMs (the weekend special can then be a TM). */
    private fun withTmWorld() {
        val base = TestData.world()
        val tmSpecies = TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "earthquake", "bodyslam"))
        val d = GameData(base.species + (900 to tmSpecies), base.moves, base.growth, base.itemPrices)
        val f = GameRepo::class.java.getDeclaredField("loadedData"); f.isAccessible = true; f.set(GameRepo, d)
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild"); rebuild.isAccessible = true; rebuild.invoke(GameRepo)
    }

    private fun saturdayWith(tm: Boolean): String =
        generateSequence(LocalDate.parse(SATURDAY)) { it.plusWeeks(1) }.take(80).map { it.toString() }.first { Shop.weekendFor(gd, it)?.isTm == tm }

    // ---------- the page ----------

    @Test fun aWeekdayShowsTheDealTheBasicsAndTheSpecialsButNoWeekendCard() {
        shoot("weekday", listOf(Variants.PHONE, TALL), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "DEAL OF THE DAY" in t)
            assertTrue("ALWAYS IN STOCK" in t)
            assertTrue("TODAY'S SPECIALS" in t)
            assertTrue(t.any { it.startsWith("new in ") && it.endsWith(" h") })
            assertTrue("no weekend card on a Wednesday", "WEEKEND SPECIAL" !in t)
            assertTrue("Poké Ball" in t && "Potion" in t)
            // the deal: its name once on the card, the old price struck through (a text of its own) and the new price
            val deal = Shop.dealFor(gd, WEEKDAY)!!
            assertTrue(Items.name(deal.item) in t)
            assertTrue(Bits.fmt(deal.oldPrice) in t)
            assertTrue(Bits.fmt(deal.price) in t)
            assertTrue(t.any { it.startsWith("ends at ") && it.contains(" h") })
            // the TMs are tiles of the specials now
            assertTrue("TM Thunderbolt" in t && "TM Body Slam" in t)
            assertTrue("the old TM list heading is gone", "TODAY'S TMS" !in t)
            assertTrue("no ₽ text where the coin shows", t.none { it.endsWith(" ₽") && it != "Refresh shop · 100 ₽" })
            assertTrue("the Refresh button stays", "Refresh shop · ${Tuning.REROLL_PRICE} ₽" in t)
        }) { ctx -> shopTab(ctx, WEEKDAY).second }
    }

    @Test fun theWeekendShowsTheSpecialCardOnSaturdayAndSunday() {
        for (day in listOf(SATURDAY, SUNDAY)) {
            shoot("weekend-${day.takeLast(2)}", listOf(TALL), themes = listOf(Theme.LIGHT, Theme.DARK), check = { _, screen ->
                val t = texts(screen)
                assertTrue(t.toString(), "WEEKEND SPECIAL" in t)
                assertTrue("Sat & Sun" in t)
                assertTrue(ShopPlan.WEEKEND_NOTE in t)
                val w = Shop.weekendFor(gd, day)!!
                assertTrue(if (w.isTm) Items.tmName(gd, w.id) in t else Items.name(w.id) in t)
            }) { ctx -> shopTab(ctx, day).second }
        }
    }

    @Test fun aWeekendTmCardShowsTheTmAndItsPrice() {
        val day = lazy { saturdayWith(true) }
        shoot("weekend-tm", listOf(TALL), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "WEEKEND SPECIAL" in t)
            assertTrue(Items.tmName(gd, Shop.weekendFor(gd, day.value)!!.id) in t)
            assertTrue(Bits.fmt(Tuning.TM_PRICE) in t)
        }) { ctx -> withTmWorld(); shopTab(ctx, day.value, tms = listOf("thunderbolt", "watergun")).second }
    }

    @Test fun tooLittleMoneyFadesThePricesButKeepsTheTilesTappable() {
        shoot("poor", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val tile = clickable(findText(screen) { it == "Poké Ball" })!!
            assertTrue(tile.isClickable)
            assertTrue("the coins in the bar", "20" in texts(screen))
        }) { ctx -> shopTab(ctx, WEEKDAY, cash = 20).second }
    }

    @Test fun theBagSwitchIsStillThereAndOwnedTmsSayOwned() {
        shoot("bag", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT), check = { _, screen ->
            assertTrue("Bag · ${st.bag.values.sum() + st.tms.size}" in texts(screen))
            assertTrue("the Bag replaced the shop", "TODAY'S SPECIALS" !in texts(screen))
        }) { ctx ->
            val (p, screen) = shopTab(ctx, WEEKDAY)
            st.tms += "thunderbolt"
            setField(p, "showBag", true)
            call(p, "render")
            screen
        }
        shoot("owned", listOf(TALL), themes = listOf(Theme.LIGHT), check = { _, screen ->
            assertTrue("✓ Owned" in texts(screen))
        }) { ctx -> AppPrefs.setShopSeenDay(ctx, "never"); shopTab(ctx, WEEKDAY).also { st.tms += "thunderbolt"; call(it.first, "render") }.second }
    }

    @Test fun narrowAndBigTextKeepsEverythingReadable() {
        // 320 dp at font 2.0: two tiles across, nothing cut (the nav labels are checked in the bar test)
        shoot("weekday", listOf(W320), scale = 2f, check = { ctx, screen ->
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
            val t = texts(screen)
            assertTrue("DEAL OF THE DAY" in t && "ALWAYS IN STOCK" in t)
        }) { ctx -> shopTab(ctx, SATURDAY).second }
        shoot("weekday", listOf(Variants.PHONE), scale = 1.3f, themes = listOf(Theme.LIGHT)) { ctx -> shopTab(ctx, WEEKDAY).second }
        shoot("weekday", listOf(W320), themes = listOf(Theme.LIGHT)) { ctx -> shopTab(ctx, SATURDAY).second }
    }

    @Test fun landscapeAndTheFloatingCard() {
        shoot("weekend", listOf(LAND), themes = listOf(Theme.LIGHT)) { ctx -> shopTab(ctx, SATURDAY).second }
        shoot("float", listOf(FLOAT)) { ctx ->
            AppPrefs.setShopSeenDay(ctx, "never")
            val p = ShopPage(ctx, NoHost)
            val root = p.root
            p.onShow()
            forceDay(WEEKDAY, 5000)
            call(p, "render")
            root
        }
    }

    // ---------- the deal card and the prices ----------

    @Test fun theDealCardCloseUp() {
        shoot("deal-card", listOf(CARD), check = { _, v ->
            val t = texts(v)
            assertTrue(t.toString(), "DEAL OF THE DAY" in t)
            assertTrue("Super Potion" in t)
            assertTrue("700" in t && "490" in t)
        }) { ctx ->
            val price = ShopPlan.Price(490, 700)
            val card = ShopViews.dealCard(ctx, "super-potion", Items.name("super-potion"), price, ShopPlan.endsText(0, 7), ShopPlan.describe("Super Potion", price)) {}
            assertEquals(1, descriptions(card).count { it.startsWith("Super Potion, deal, 490 instead of 700") })
            scene(ctx, card)
        }
        // the same card with the biggest text
        shoot("deal-card", listOf(CARD320), scale = 2f) { ctx ->
            scene(ctx, ShopViews.dealCard(ctx, "super-potion", Items.name("super-potion"), ShopPlan.Price(490, 700), ShopPlan.endsText(0, 7), "deal") {})
        }
    }

    @Test fun thePriceLayoutsCloseUp() {
        shoot("prices", listOf(PRICES), check = { _, v ->
            val t = texts(v)
            assertTrue(t.toString(), "300" in t && "700" in t && "490" in t)
            assertTrue("no ₽ text on a price", t.none { it.contains("₽") })
        }) { ctx ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
                for (v in listOf(ShopViews.priceNormal(ctx, 300), ShopViews.priceDeal(ctx, 700, 490), ShopViews.priceDeal(ctx, 700, 490, nowSp = 14f))) {
                    addView(v, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 24) })
                }
            }
            val zoom = FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_card))
                addView(ShopViews.priceDeal(ctx, 700, 490).apply { scaleX = 2f; scaleY = 2f; pivotX = 0f; pivotY = 0f },
                    FrameLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 20), Ui.dp(ctx, 12), 0, 0) })
            }
            scene(ctx, LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(row, LinearLayout.LayoutParams(-1, -2))
                addView(zoom, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 100)).also { it.topMargin = Ui.dp(ctx, 12) })
            })
        }
    }

    /** [v] on the page colour with the page's side margins, at the top of the window. */
    private fun scene(ctx: Context, v: View): View = FrameLayout(ctx).apply {
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        addView(v, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).also { val m = Ui.dp(ctx, 12); it.setMargins(m, m, m, m) })
    }

    // ---------- the opened card (PLAN 168: it replaced the buy sheet) ----------

    /** Paints the pieces of [v] that are struck through (the old price of a deal). */
    private fun struck(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView && v.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG != 0) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) struck(v.getChildAt(i), out)
        return out
    }

    @Test fun theWindowOfTheDealShowsTheOldPriceStruckThroughOnItsBuyButton() {
        shoot("card-deal", listOf(Variants.PHONE), check = { _, v ->
            val t = texts(v)
            val deal = Shop.dealFor(gd, WEEKDAY)!!
            assertEquals("the old price, struck through, once (on the Buy button; the deal card behind draws its own line)", listOf(Bits.fmt(deal.oldPrice)), struck(v))
            assertTrue("the new price", Bits.fmt(deal.price) in t)
            assertTrue("the deal card stays under the window", "DEAL OF THE DAY" in t)
            assertTrue(descriptions(v).any { it.startsWith("Buy for ${Bits.fmt(deal.price)}") })
        }) { ctx ->
            val (p, screen) = shopTab(ctx, WEEKDAY)
            val stage = ShopCardKit.Stage(ctx, p, screen)
            p.openCard(CardKeys.DEAL)
            stage.render()
        }
        shoot("card-plain", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT), check = { _, v ->
            val basic = Shop.basics().first()
            assertTrue("nothing struck through on a plain price", struck(v).isEmpty())
            assertTrue(descriptions(v).any { it == "Buy for ${Bits.fmt(gd.price(basic))}" })
            assertTrue("no Price row", "Price" !in texts(v))
        }) { ctx ->
            val (p, screen) = shopTab(ctx, WEEKDAY)
            val stage = ShopCardKit.Stage(ctx, p, screen)
            p.openCard(CardKeys.basic(ShopPlan.Tile.Item(Shop.basics().first())))
            stage.render()
        }
    }

    @Test fun aDealPurchaseChargesTheDealPriceAsOftenAsYouLike() {
        shoot("buy", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = shopTab(ctx, WEEKDAY, cash = 20_000)
            val deal = Shop.dealFor(gd, WEEKDAY)!!
            val n0 = st.count(deal.item)
            for (i in 1..2) {
                val r = p.act { g -> Shop.buy(g.data, g.state, deal.item) }
                assertTrue("buy $i of ${deal.item}: ${r.message}", r.ok)
            }
            assertEquals(n0 + 2, st.count(deal.item))
            assertEquals(20_000 - 2 * deal.price, st.cash)
            call(p, "render")
            assertTrue(Bits.fmt(20_000 - 2 * deal.price) in texts(screen))
            // a tap on the deal card opens its item window (not a dialog), with the count in words
            val stage = ShopCardKit.Stage(ctx, p, screen)
            val card = clickable(findText(screen) { it == "DEAL OF THE DAY" })!!
            assertNull(DialogShots.capture { card.performClick() })
            stage.render()
            assertTrue("the item window is open", p.itemWindow != null)
            assertTrue(ItemInfo.plain(ItemInfo.ownLine(deal.item, n0 + 2)) in texts(stage))
            stage
        }
    }

    @Test fun markingTheShopSeenStoresTodaysGameDay() {
        shoot("seen", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            AppPrefs.setShopSeenDay(ctx, "never")
            val p = ShopPage(ctx, NoHost)
            val screen = pageScreen(ctx, p)
            forceDay(WEEKDAY, 5000)
            call(p, "render")
            assertEquals(WEEKDAY, AppPrefs.shopSeenDay(ctx))
            forceDay(SATURDAY, 5000)
            call(p, "render")
            assertEquals("a new game day is stored on the next render", SATURDAY, AppPrefs.shopSeenDay(ctx))
            screen
        }
    }

    // ---------- the 5-tab bar and its dot ----------

    /** One bar per tab of [tabs] in a column, all built with the Shop tab [seen] (or not) today: the dot is the same on every screen. */
    private fun bars(ctx: Context, seen: Boolean, tabs: List<BottomNav.Tab>): View {
        val act = attach(Activity(), ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
        AppPrefs.setShopSeenDay(ctx, if (seen) st.day else "never")
        for ((i, t) in tabs.withIndex()) col.addView(BottomNav.create(act, t), LinearLayout.LayoutParams(-1, -2).also { if (i > 0) it.topMargin = Ui.dp(ctx, 8) })
        return col
    }

    private fun items(bar: View): List<ViewGroup> = ((bar as ViewGroup).getChildAt(1) as ViewGroup).let { row -> (0 until row.childCount).map { row.getChildAt(it) as ViewGroup } }

    private fun dotOf(item: ViewGroup): View? = (item.getChildAt(0) as ViewGroup).let { pill -> if (pill.childCount > 1) pill.getChildAt(1) else null }

    /** [dot]: the Shop tab shows the dot (on every bar except the Shop's own, which has none). */
    private fun checkBars(dot: Boolean, tabs: List<BottomNav.Tab>) = { ctx: Context, v: View ->
        val col = v as ViewGroup
        val d = ctx.resources.displayMetrics.density
        var size = -1f
        for ((n, tab) in tabs.withIndex()) {
            val bar = items(col.getChildAt(n))
            assertEquals(5, bar.size)
            val shopHasDot = dot && tab != BottomNav.Tab.SHOP
            val expect = listOf("Study", "Pokémon", if (shopHasDot) "Shop, new stock" else "Shop", "Stats", "Settings")
            assertEquals("$tab", expect, bar.map { it.contentDescription.toString() })
            if (tab == BottomNav.Tab.SHOP) assertNull("on the Shop tab itself there is no dot", dotOf(bar[2]))
            else assertEquals("$tab", if (shopHasDot) View.VISIBLE else View.GONE, dotOf(bar[2])!!.visibility)
            if (shopHasDot) assertTrue("the dot has a size", dotOf(bar[2])!!.let { it.width > 0 && it.height > 0 })
            // every label is one line, fits its tab (no cut-off text), is not under 10 dp, and all labels have one size
            for (item in bar) {
                val label = item.getChildAt(1) as TextView
                assertEquals(label.text.toString(), 1, label.lineCount)
                assertTrue("${label.text} needs ${label.layout.getLineWidth(0)} of ${item.width}", label.layout.getLineWidth(0) <= item.width + 0.5f)
                assertTrue("${label.text} ${label.textSize / d} dp", label.textSize >= 10f * d - 0.5f)
                if (size < 0f) size = label.textSize
                assertEquals(label.text.toString(), size, label.textSize, 0.01f)
            }
            // five equal tabs (up to the pixel)
            assertTrue(bar.maxOf { it.width } - bar.minOf { it.width } <= 1)
        }
        Unit
    }

    private val WITH_DOT = listOf(BottomNav.Tab.STUDY, BottomNav.Tab.POKEMON, BottomNav.Tab.SHOP)
    private val NO_DOT = listOf(BottomNav.Tab.STUDY, BottomNav.Tab.SHOP, BottomNav.Tab.STATS)

    @Test fun theFiveTabBarWithAndWithoutTheDot() {
        shoot("bar-dot", listOf(BAR412, BAR320), check = checkBars(true, WITH_DOT)) { ctx -> bars(ctx, seen = false, WITH_DOT) }
        shoot("bar-nodot", listOf(BAR412, BAR320), check = checkBars(false, NO_DOT)) { ctx -> bars(ctx, seen = true, NO_DOT) }
    }

    @Test fun theFiveTabBarAtFontTwo() {
        shoot("bar-dot", listOf(BAR412, BAR320), scale = 2f, check = checkBars(true, WITH_DOT)) { ctx -> bars(ctx, seen = false, WITH_DOT) }
        shoot("bar-nodot", listOf(BAR412, BAR320), scale = 2f, check = checkBars(false, NO_DOT)) { ctx -> bars(ctx, seen = true, NO_DOT) }
    }

    @Test fun theDotComesBackOnResumeAndOnANewGameDay() {
        shoot("dot-refresh", listOf(BAR412), themes = listOf(Theme.LIGHT)) { ctx ->
            val act = attach(Activity(), ctx)
            AppPrefs.setShopSeenDay(ctx, st.day)
            val bar = BottomNav.create(act, BottomNav.Tab.STUDY)
            layoutNow(ctx, bar)
            assertEquals("seen today", null, dotOf(items(bar)[2])?.takeIf { it.visibility == View.VISIBLE })
            // the Shop tab was opened yesterday (another screen was in front): back in front = a refresh
            AppPrefs.setShopSeenDay(ctx, "2000-01-01")
            bar.onWindowFocusChanged(true)
            assertEquals(View.VISIBLE, dotOf(items(bar)[2])!!.visibility)
            assertEquals("Shop, new stock", items(bar)[2].contentDescription.toString())
            // opening the Shop tab stores today: the next refresh clears the dot
            AppPrefs.setShopSeenDay(ctx, st.day)
            bar.onWindowFocusChanged(true)
            assertEquals(View.GONE, dotOf(items(bar)[2])!!.visibility)
            // a new game day (GameRepo's listener / the window coming back) brings it back
            st.day = LocalDate.parse(st.day).plusDays(1).toString()
            bar.onWindowFocusChanged(true)
            assertEquals(View.VISIBLE, dotOf(items(bar)[2])!!.visibility)
            // no game day yet (a fresh install): no dot
            st.day = ""
            bar.onWindowFocusChanged(true)
            assertEquals(View.GONE, dotOf(items(bar)[2])!!.visibility)
            bar
        }
    }
}
