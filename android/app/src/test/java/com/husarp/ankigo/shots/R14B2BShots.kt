package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ItemWindow
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Shop tab / Bag drawn with one item window open (PLAN 168 / 181 / 199, since R15 PLAN 210 a window over the list), shared by the R14-B2-B and
 * R15-A tests and the old popup tests that became card shots.
 */
internal object ShopCardKit {
    const val WEEKDAY = "2026-10-14" // a Wednesday

    /** The fake world with a price for every item the cards show, and several species that can learn Thunderbolt by TM. */
    fun world(): GameData {
        val base = TestData.world()
        val learners = setOf(25, 2, 3, 92, 130, 133, 26, 63, 64)
        val species = base.species.mapValues { (num, sp) -> if (num in learners) sp.copy(tmMoves = sp.tmMoves + "thunderbolt") else sp }
        val prices = base.itemPrices + mapOf(
            "poke-ball" to 200, "great-ball" to 600, "ultra-ball" to 800, "tart-apple" to 2200, "sweet-apple" to 2200, "cracked-pot" to 1600,
            "dragon-scale" to 38000, "linking-cord" to 8000, "amulet-coin" to 10000, "lucky-egg" to 10000, "poke-doll" to 300, "helix-fossil" to 7000,
            "fire-stone" to 3000, "everstone" to 3000, "super-potion" to 700, "potion" to 200,
        )
        return GameData(species, base.moves, base.growth, prices)
    }

    fun install(): GameData {
        val d = world()
        val f = GameRepo::class.java.getDeclaredField("loadedData"); f.isAccessible = true; f.set(GameRepo, d)
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild"); rebuild.isAccessible = true; rebuild.invoke(GameRepo)
        return d
    }

    /** A game day whose deal is [item] (the deal is date-seeded); the first day found, else [WEEKDAY]. */
    fun dayWithDeal(d: GameData, item: String): String {
        var day = java.time.LocalDate.parse(WEEKDAY)
        repeat(1500) {
            if (day.dayOfWeek.value <= 5 && Shop.dealFor(d, day.toString())?.item == item) return day.toString()
            day = day.plusDays(1)
        }
        return WEEKDAY
    }

    /** How many item windows were put on the screen since the last [Stage] (a re-render must not make another one). */
    var windowsShown = 0

    /**
     * The page as the user sees it: the screen, and over it, dimmed, the item window the page holds (its sheet at the bottom). A JVM test has
     * no window manager, so [ItemWindow.interceptShow] counts the windows instead of showing them (it stays set: no test wants a real Dialog).
     * [render] draws the page and puts its current window (a new one, the same one drawn again, or none) over the screen.
     */
    class Stage(ctx: Context, val page: ShopPage, val screen: View) : FrameLayout(ctx) {
        private var sheet: View? = null
        private var dim: View? = null

        init {
            windowsShown = 0
            ItemWindow.interceptShow = { windowsShown++ }
            addView(screen, LayoutParams(-1, -1))
        }

        fun render(): Stage {
            call(page, "render")
            val now = page.itemWindow?.view
            if (now !== sheet) {
                sheet?.let { removeView(it) }
                dim?.let { removeView(it) }
                sheet = now
                dim = null
                if (now != null) {
                    dim = View(context).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }
                    addView(dim, LayoutParams(-1, -1))
                    addView(now, LayoutParams(-1, -2, Gravity.BOTTOM))
                }
            }
            return this
        }
    }

    /** The Shop tab (or the Bag) with the window of [key] open (null = none): the page, laid out like the app does it. */
    fun page(ctx: Context, key: String?, bag: Boolean = false, day: String = WEEKDAY, cash: Int = 5000, setup: () -> Unit = {}): Pair<ShopPage, Stage> {
        val st = GameRepo.state!!
        AppPrefs.setShopSeenDay(ctx, "never")
        val p = ShopPage(ctx, NoHost)
        val stage = Stage(ctx, p, pageScreen(ctx, p))
        st.day = day
        st.shopDay = day
        st.shopItems.clear(); st.shopItems += listOf("great-ball", "tart-apple", "cracked-pot", "linking-cord", "amulet-coin", "dragon-scale")
        st.shopTms.clear(); st.shopTms += listOf("thunderbolt", "watergun", "earthquake")
        st.cash = cash
        setup()
        if (bag) setField(p, "showBag", true)
        key?.let { p.openCard(it) }
        return p to stage.render()
    }

    /** A bag with one of everything the shots show. */
    fun bag(): Map<String, Int> = mapOf(
        "poke-ball" to 7, "great-ball" to 3, "ultra-ball" to 1, "super-potion" to 2, "tart-apple" to 1, "cracked-pot" to 1, "linking-cord" to 1,
        "amulet-coin" to 1, "poke-doll" to 1, "helix-fossil" to 1,
    )

    fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    fun findText(v: View, pred: (String) -> Boolean): TextView? {
        if (v is TextView && v.visibility != View.GONE && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    /** The first view whose content description is [desc]. */
    fun findDesc(v: View, desc: String): View? {
        if (v.visibility == View.GONE) return null
        if (v.contentDescription?.toString() == desc) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findDesc(v.getChildAt(i), desc)?.let { return it }
        return null
    }

    fun allText(v: View, pred: (TextView) -> Boolean, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (v.visibility == View.GONE) return out
        if (v is TextView && pred(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) allText(v.getChildAt(i), pred, out)
        return out
    }

    /** Taps the nearest clickable view around [tv]. */
    fun tap(tv: View?): View {
        var v: View? = tv
        while (v != null && !v.isClickable) v = v.parent as? View
        assertNotNull("nothing clickable around the text", v)
        v!!.performClick()
        return v
    }
}

/*
 * R14-B2-B (PLAN 168 / 181 / 200 / 199), moved to a window by R15-A (PLAN 210): the item windows of the Shop and the Bag, "You own" in words, the
 * trade-up in the Bag. PNGs: r14b2b-<scene>-<p412|p360...>-<light|dark>; the design is docs/design/round14-shop.html (the card), the window is the
 * app's bottom sheet look. The window rules (one at a time, stays open through Buy and Trade, closes before a picker) are in R15AItemWindowShots.
 */
class R14B2BShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val P412 = Variant("p412", 412, 892)
    private val P360 = Variant("p360", 360, 780)

    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /** Renders [build] for every variant and theme; [check] runs on the laid-out view before the snapshot. */
    private fun shoot(name: String, variants: List<Variant> = listOf(P412), scale: Float = 1f, themes: List<Theme> = Theme.entries,
                      check: (Context, View) -> Unit = { _, _ -> }, build: (Context) -> View) {
        for (v in variants) for (theme in themes) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ShopCardKit.install()
            val view = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
            layoutNow(ctx, frame)
            try { check(ctx, view) } catch (e: Throwable) { throw AssertionError("${v.label}/${theme.name}: ${e.message}", e) }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r14b2b-$name-$label-${theme.name.lowercase()}", 1000)
        }
    }

    private fun texts(v: View) = ShopCardKit.texts(v)

    /** Every Pokémon that knows Thunderbolt by default in the fake team: make some of them not know it, so "knows it" is a real mark. */
    private fun someDontKnowThunderbolt() {
        for (uid in listOf("t2", "t3", "t5", "t6")) st.mon(uid)!!.moves.apply { clear(); add("tackle") }
    }

    /** The arrows ▾ ▴ are gone from every tile, row and card (PLAN 210). */
    private fun assertNoArrows(v: View) = assertTrue(texts(v).toString(), texts(v).none { it == "▾" || it == "▴" })

    // ---------- the Shop ----------

    @Test fun aGreatBallOpensItsWindowWithBuyAndNoTrade() {
        shoot("shop-great", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Great Ball" in t)
            assertNotNull("the window has its ✕", ShopCardKit.findDesc(screen, "Close"))
            assertNoArrows(screen)
            assertTrue(t.toString(), "WHAT IT DOES" in t)
            assertTrue(t.any { it.startsWith("You own ") && it.contains("Great Ball") })
            assertTrue(t.toString(), t.any { it == "Buy" })
            assertTrue("no Trade in the Shop", t.none { it.startsWith("Trade ") && " for 1 " in it })
            assertTrue("no Price row any more", "Price" !in t && "PRICE" !in t)
            assertTrue("no stars", t.none { it.contains("**") })
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball"))).second }
    }

    @Test fun aTmOpensWithItsMoveAndWhoCanLearnIt() {
        shoot("shop-tm", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "TM Thunderbolt" in t)
            assertTrue("POWER" in t && "ACCURACY" in t && "90" in t && "100" in t)
            assertTrue("the Special tag", "SPECIAL" in t)
            assertTrue("Who can learn it" in t)
            assertTrue(t.toString(), t.any { it.endsWith("of your Pokémon") })
            assertTrue("You don't own this TM yet" in t)
            assertTrue("Buy" in t)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt")), setup = ::someDontKnowThunderbolt).second }
    }

    @Test fun anOwnedTmSaysOwnedInsteadOfBuy() {
        shoot("shop-tm-owned", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val t = texts(screen)
            assertTrue("✓ Owned" in t)
            assertTrue("You own this TM" in t)
            assertTrue("Buy" !in t)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt")), setup = { st.tms += "thunderbolt" }).second }
    }

    @Test fun theDealOpensWithTheOldPriceStruckThrough() {
        val day = lazy { ShopCardKit.dayWithDeal(ShopCardKit.world(), "super-potion") }
        shoot("shop-deal", listOf(P412), check = { _, screen ->
            val deal = Shop.dealFor(gd, day.value)!!
            val struck = ShopCardKit.allText(screen, { it.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG != 0 })
            assertEquals(struck.map { it.text }.toString(), listOf(com.husarp.ankigo.ui.Bits.fmt(deal.oldPrice)), struck.map { it.text.toString() })
            assertTrue("the deal card stays under the window", "DEAL OF THE DAY" in texts(screen))
            assertTrue("Buy" in texts(screen))
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.DEAL, day = day.value).second }
    }

    @Test fun anOpenWeekendWindowLeavesTheWeekendCardUnderIt() {
        val sat = "2026-10-17"
        shoot("shop-weekend", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val t = texts(screen)
            assertTrue("WEEKEND SPECIAL" in t)
            assertTrue("the weekend card is still there", ShopPlan.WEEKEND_NOTE in t)
            assertNotNull(ShopCardKit.findDesc(screen, "Close"))
            assertTrue("Buy" in t)
            assertNoArrows(screen)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.WEEKEND, day = sat).second }
    }

    @Test fun tilesShowTheirShortLineAndATmItsTypeChipAndNoArrow() {
        shoot("shop-tiles", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertNoArrows(screen)
            assertNull("no window is open", ShopCardKit.findDesc(screen, "Close"))
            for (id in listOf("poke-ball", "great-ball", "tart-apple")) assertTrue(id, ItemInfo.plain(ItemInfo.short(gd, id, st.caught)) in t)
            assertTrue("a TM tile shows its type, not a line", "Electric" in t || "ELECTRIC" in t || t.any { it.equals("Electric", true) })
        }) { ctx -> ShopCardKit.page(ctx, null).second }
    }

    @Test fun tooLittleMoneyFadesBuyAndTheWindowStaysOpen() {
        shoot("shop-poor", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("tart-apple")), cash = 100)
            val buy = ShopCardKit.allText(screen, { it.text.toString() == "Buy" }).single()
            var v: View = buy
            while (v.alpha == 1f && v.parent is View) v = v.parent as View
            assertTrue("Buy is faded", v.alpha < 1f)
            val before = st.cash
            ShopCardKit.tap(buy)
            assertEquals("nothing was bought", before, st.cash)
            assertEquals(0, st.count("tart-apple"))
            assertNotNull("the window is still open", p.itemWindow)
            screen
        }
    }

    @Test fun bigTextKeepsTheOpenWindowReadable() {
        shoot("shop-x2", listOf(Variant("w360", 360, 780)), scale = 2f, check = { ctx, screen ->
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
            assertTrue("Who can learn it" in texts(screen))
            assertTrue("Buy" in texts(screen))
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt"))).second }
    }

    // ---------- the Bag ----------

    private fun bagPage(ctx: Context, key: String?, setup: () -> Unit = {}) =
        ShopCardKit.page(ctx, key, bag = true, setup = { st.bag.clear(); st.bag.putAll(ShopCardKit.bag()); st.tms.clear(); st.tms += listOf("thunderbolt", "bodyslam"); setup() })

    @Test fun aPokeBallOpensWithThrowAndTheTradeButton() {
        shoot("bag-poke", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Poké Ball" in t)
            assertNoArrows(screen)
            assertTrue("Throw" in t)
            assertTrue(t.toString(), "Trade 5 Poké Balls for 1 Great Ball" in t)
            assertTrue("no arrow glyphs", t.none { it.contains("→") || it.contains("⇄") })
            assertTrue("a fight is on in the fake game, so Throw is usable", t.none { it == "Balls are thrown from a fight" })
            assertTrue("You own 7 Poké Balls" in t.map { ItemInfo.plain(it) })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("poke-ball")).second }
    }

    @Test fun theTradeConfirmIsInsideTheWindowAndOneTapTradesOneBatch() {
        shoot("bag-trade-confirm", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Cancel" in t && "Trade 5 for 1" in t)
            assertTrue(t.toString(), t.any { it.startsWith("5 Poké Balls (1,000 coins in the Shop) for 1 Great Ball (700 coins). On purpose worse than buying. You keep 2 Poké Balls.") })
            assertTrue("5 ×" in t && "1 ×" in t && "for" in t)
            assertTrue("no arrow glyphs", t.none { it.contains("→") || it.contains("⇄") })
            assertTrue("the Throw button hides while the confirm shows", "Throw" !in t)
        }) { ctx ->
            val (_, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); screen.render()
            screen
        }
        shoot("bag-trade-done", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            val w = p.itemWindow
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); screen.render()
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 for 1" }); screen.render()
            assertEquals(2, st.count("poke-ball"))
            assertEquals(4, st.count("great-ball"))
            val t = texts(screen)
            assertSame("the window stays, drawn again", w, p.itemWindow)
            assertTrue("the confirm is closed", "Cancel" !in t)
            assertTrue(t.toString(), "You own 2 Poké Balls" in t.map { ItemInfo.plain(it) })
            assertTrue("2 Poké Balls are too few for another batch", t.any { it == "Need 5, you have 2" })
            screen
        }
    }

    @Test fun tradingTheLastBallsKeepsTheWindowOpenWithZero() {
        shoot("bag-trade-last", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertNotNull("the window stays open", ShopCardKit.findDesc(screen, "Close"))
            assertTrue(t.toString(), "You don't own any Poké Balls yet" in t.map { ItemInfo.plain(it) })
            assertTrue(t.toString(), "Need 5, you have 0" in t)
            assertTrue("Throw is off with no ball", "You have none" in t)
            assertTrue("the Poké Ball row is gone from the list behind", "Poké Ball" !in texts((screen as ShopCardKit.Stage).screen))
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"), setup = { st.bag["poke-ball"] = 5 })
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); screen.render()
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 for 1" }); screen.render()
            assertEquals(0, st.count("poke-ball"))
            assertEquals(4, st.count("great-ball"))
            assertNotNull(p.itemWindow)
            screen
        }
    }

    @Test fun bigTextKeepsTheBagTmWindowReadable() {
        shoot("bag-tm-x2", listOf(Variant("w360", 360, 780)), scale = 2f, check = { ctx, screen ->
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
            val t = texts(screen)
            assertTrue("Who can learn it" in t && t.any { it.endsWith("of your Pokémon") })
            assertTrue("Teach…" in t)
        }) { ctx -> bagPage(ctx, CardKeys.bagTm("thunderbolt"), setup = ::someDontKnowThunderbolt).second }
    }

    @Test fun tooFewBallsGreysTheTradeButton() {
        shoot("bag-poke-few", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Need 5, you have 3" in t)
            assertTrue("Trade 5 Poké Balls for 1 Great Ball" in t)
        }) { ctx ->
            val (_, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball")) { st.bag["poke-ball"] = 3 }
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); screen.render()
            assertEquals("a tap on the greyed button opens no confirm", 0, texts(screen).count { it == "Cancel" })
            assertEquals(3, st.count("poke-ball"))
            screen
        }
    }

    @Test fun aGreatBallAndAnOldBallTradeUp() {
        shoot("bag-great", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            assertTrue("Trade 3 Great Balls for 1 Ultra Ball" in texts(screen))
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("great-ball")).second }
        shoot("bag-oldball", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Net Ball" in t)
            assertTrue(t.toString(), "Trade 3 for 1" in t)
            assertTrue(t.toString(), t.any { it.startsWith("1 Net Ball and 2 Great Balls for 1 Ultra Ball.") })
        }) { ctx ->
            val (_, screen) = bagPage(ctx, CardKeys.bagItem("net-ball")) { st.bag["net-ball"] = 1 }
            assertTrue("an old ball's window counts Great Balls", "Trade 3 Great Balls for 1 Ultra Ball" in texts(screen))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it.startsWith("Trade 3 Great Balls") }); screen.render()
            screen
        }
    }

    @Test fun ultraAndMasterBallsHaveNoTradeButton() {
        shoot("bag-ultra", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Ultra Ball" in t && "Throw" in t)
            assertTrue(t.toString(), t.none { it.startsWith("Trade") })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("ultra-ball")).second }
    }

    @Test fun throwWorksOnlyInAFightWithoutOneTheButtonIsGreyedWithAReason() {
        shoot("bag-throw-off", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            assertTrue(texts(screen).toString(), "Balls are thrown from a fight" in texts(screen))
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball")) { st.battle = null }
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Throw" })
            assertNull(st.battle)
            assertEquals(7, st.count("poke-ball"))
            assertNotNull("a greyed button only says why: the window stays", p.itemWindow)
            screen
        }
        // in a fight the button closes the window and starts today's confirm
        shoot("bag-throw-on", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Throw" }) }
            assertNotNull("Throw opens the confirm of today", d)
            assertNull("and the window is gone", p.itemWindow)
            screen
        }
    }

    @Test fun otherKindsOpenWithTheirActionButton() {
        shoot("bag-potion", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Use on…" in t)
            assertNoArrows(screen)
            assertTrue("You own 2 Super Potions" in t.map { ItemInfo.plain(it) })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("super-potion")).second }
        shoot("bag-amulet", listOf(P412), check = { _, screen -> assertTrue("Give to…" in texts(screen)) }) { ctx -> bagPage(ctx, CardKeys.bagItem("amulet-coin")).second }
        shoot("bag-evo", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen -> assertTrue("Use on…" in texts(screen)) }) { ctx -> bagPage(ctx, CardKeys.bagItem("tart-apple")).second }
        shoot("bag-fossil", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen -> assertTrue("Revive" in texts(screen)) }) { ctx -> bagPage(ctx, CardKeys.bagItem("helix-fossil")).second }
        shoot("bag-escape", listOf(P412), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val t = texts(screen)
            assertTrue("the Escape group", "ESCAPE" in t && "OTHER" !in t)
            assertTrue("Use" in t)
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("poke-doll")).second }
    }

    @Test fun aTmOpensInTheBagWithTeachAndWhoCanLearnIt() {
        shoot("bag-tm", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Teach…" in t && "Who can learn it" in t)
            assertTrue("POWER" in t && "SPECIAL" in t)
            assertTrue("You own this TM" in t)
            assertTrue("no Buy in the Bag", "Buy" !in t)
        }) { ctx -> bagPage(ctx, CardKeys.bagTm("thunderbolt"), setup = ::someDontKnowThunderbolt).second }
    }

    @Test fun whoCanLearnItListsYourPokemonAndMarksTheOnesThatKnowIt() {
        shoot("who", listOf(Variant("p412", 412, 900)), check = { _, v ->
            val t = texts(v)
            assertTrue(t.toString(), "knows it" in t)
            assertTrue(t.any { it.startsWith("Lv") })
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagTm("thunderbolt"), setup = ::someDontKnowThunderbolt)
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Who can learn it" }) } ?: error("no list")
            assertNotNull("the list is a read-only view: the window stays under it", p.itemWindow)
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun rowsHaveTheCountAndNoArrowAndATapOpensTheWindowInsteadOfActing() {
        shoot("bag-folded", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertNoArrows(screen)
            assertTrue("×7" in t && "×3" in t)
            assertTrue("the Escape group", "ESCAPE" in t)
        }) { ctx -> bagPage(ctx, null).second }
        shoot("bag-tap", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, null)
            val potions = st.count("super-potion")
            val hp0 = st.team.map { st.mon(it)!!.hp }
            // a tap on a row opens the window: nothing is used, no picker
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Super Potion" }) }
            assertNull("a tap on a row shows no picker", d)
            screen.render()
            assertNotNull(p.itemWindow)
            assertEquals(potions, st.count("super-potion"))
            assertEquals(hp0, st.team.map { st.mon(it)!!.hp })
            // the window's button closes it and starts the picker of today
            val picker = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Use on…" }) }
            assertNotNull("Use on… opens the Pokémon picker", picker)
            assertNull(p.itemWindow)
            screen
        }
    }

    @Test fun usingTheLastItemClosesItsWindow() {
        shoot("bag-last", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-doll"))
            assertNotNull(p.itemWindow)
            st.bag.remove("poke-doll")
            screen.render()
            assertNull("the window of an item that is gone is closed", p.itemWindow)
            assertNull(ShopCardKit.findDesc(screen, "Close"))
            // and it does not pop open again when the item comes back
            st.bag["poke-doll"] = 1
            screen.render()
            assertNull(p.itemWindow)
            screen
        }
    }
}
