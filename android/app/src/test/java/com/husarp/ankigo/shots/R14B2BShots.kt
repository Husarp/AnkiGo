package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Paint
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
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Shop tab / Bag drawn with one card open (PLAN 168 / 181 / 199), shared by the R14-B2-B tests and the old popup tests that became card shots. */
internal object ShopCardKit {
    const val WEEKDAY = "2026-10-14" // a Wednesday

    /** The fake world with a price for every item the cards show, and several species that can learn Thunderbolt by TM. */
    fun world(): GameData {
        val base = TestData.world()
        val learners = setOf(25, 2, 3, 92, 130, 133, 26, 63, 64)
        val species = base.species.mapValues { (num, sp) -> if (num in learners) sp.copy(tmMoves = sp.tmMoves + "thunderbolt") else sp }
        val prices = base.itemPrices + mapOf(
            "poke-ball" to 200, "great-ball" to 600, "ultra-ball" to 800, "tart-apple" to 2200, "sweet-apple" to 2200, "cracked-pot" to 1600,
            "chipped-pot" to 38000, "linking-cord" to 8000, "amulet-coin" to 10000, "lucky-egg" to 10000, "poke-doll" to 300, "helix-fossil" to 7000,
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

    /** The Shop tab (or the Bag) with the card [key] open (null = none): the page, laid out like the app does it. */
    fun page(ctx: Context, key: String?, bag: Boolean = false, day: String = WEEKDAY, cash: Int = 5000, setup: () -> Unit = {}): Pair<ShopPage, View> {
        val st = GameRepo.state!!
        AppPrefs.setShopSeenDay(ctx, "never")
        val p = ShopPage(ctx, NoHost)
        val screen = pageScreen(ctx, p)
        st.day = day
        st.shopDay = day
        st.shopItems.clear(); st.shopItems += listOf("great-ball", "tart-apple", "cracked-pot", "linking-cord", "amulet-coin", "chipped-pot")
        st.shopTms.clear(); st.shopTms += listOf("thunderbolt", "watergun", "earthquake")
        st.cash = cash
        setup()
        if (bag) setField(p, "showBag", true)
        key?.let { p.toggleCard(it) }
        call(p, "render")
        return p to screen
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
 * R14-B2-B (PLAN 168 / 181 / 200 / 199): the item cards that open in place in the Shop and the Bag, "You own" in words, the trade-up in the
 * Bag. PNGs: r14b2b-<scene>-<phone|w360|tall...>-<light|dark>; the design is docs/design/round14-shop.html.
 */
class R14B2BShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val P412 = Variant("p412", 412, 1250)
    private val P360 = Variant("p360", 360, 1350)
    private val TALL = Variant("tall", 412, 1900)
    private val BAGTM412 = Variant("p412", 412, 1750)
    private val BAGTM360 = Variant("p360", 360, 2100)

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

    // ---------- the Shop ----------

    @Test fun aGreatBallOpensInPlaceWithBuyAndNoTrade() {
        shoot("shop-great", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Great Ball" in t)
            assertEquals("one card open, one ▴", 1, t.count { it == "▴" })
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
            assertTrue("the deal card is replaced while its card is open", "DEAL OF THE DAY" !in texts(screen))
            assertTrue("Buy" in texts(screen))
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.DEAL, day = day.value).second }
    }

    @Test fun anOpenWeekendCardReplacesTheWeekendCard() {
        val sat = "2026-10-17"
        shoot("shop-weekend", listOf(TALL), themes = listOf(Theme.LIGHT), check = { _, screen ->
            val t = texts(screen)
            assertTrue("WEEKEND SPECIAL" in t)
            assertEquals(1, t.count { it == "▴" })
            assertTrue("Buy" in t)
            assertTrue(ShopPlan.WEEKEND_NOTE !in t)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.WEEKEND, day = sat).second }
    }

    @Test fun tilesShowTheirShortLineAndATmItsTypeChip() {
        shoot("shop-tiles", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertEquals("every tile has the small ▾, no card is open", 0, t.count { it == "▴" })
            assertTrue(t.count { it == "▾" } >= 8)
            for (id in listOf("poke-ball", "great-ball", "tart-apple")) assertTrue(id, ItemInfo.plain(ItemInfo.short(gd, id)) in t)
            assertTrue("a TM tile shows its type, not a line", "Electric" in t || "ELECTRIC" in t || t.any { it.equals("Electric", true) })
        }) { ctx -> ShopCardKit.page(ctx, null).second }
    }

    @Test fun oneCardAtATimeAndTheStateSurvivesBuyingButNotLeavingThePage() {
        shoot("flow", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            // a weekday whose deal is neither of the two tiles tapped below (the deal card shows its item's name too)
            val d = ShopCardKit.world()
            var day = java.time.LocalDate.parse(ShopCardKit.WEEKDAY)
            while (day.dayOfWeek.value > 5 || Shop.dealFor(d, day.toString())?.item in setOf("great-ball", "tart-apple")) day = day.plusDays(1)
            val (p, screen) = ShopCardKit.page(ctx, null, day = day.toString())
            fun tile(name: String) = ShopCardKit.tap(ShopCardKit.findText(screen) { it == name })
            assertEquals(0, texts(screen).count { it == "▴" })
            // a tap opens the card in place
            tile("Great Ball"); call(p, "render")
            assertEquals(1, texts(screen).count { it == "▴" })
            val n0 = st.count("great-ball")
            assertTrue(ItemInfo.plain(ItemInfo.ownLine("great-ball", n0)) in texts(screen))
            // another tile folds the first and opens its own
            tile("Tart Apple"); call(p, "render")
            assertEquals("still one card", 1, texts(screen).count { it == "▴" })
            assertTrue("the Great Ball tile is a tile again", ShopCardKit.findText(screen) { it == "Great Ball" } != null)
            // Buy keeps the same card open with the new count
            tile("Great Ball"); call(p, "render")
            val buy = ShopCardKit.allText(screen, { it.text.toString() == "Buy" }).single()
            ShopCardKit.tap(buy); call(p, "render")
            assertEquals(n0 + 1, st.count("great-ball"))
            assertEquals(1, texts(screen).count { it == "▴" })
            assertTrue(ItemInfo.plain(ItemInfo.ownLine("great-ball", n0 + 1)) in texts(screen))
            // the header folds it
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "▴" }); call(p, "render")
            assertEquals(0, texts(screen).count { it == "▴" })
            // leaving the page drops the open card; so does the Bag switch
            tile("Great Ball"); call(p, "render")
            assertEquals(1, texts(screen).count { it == "▴" })
            p.onHide(); call(p, "render")
            assertEquals(0, texts(screen).count { it == "▴" })
            tile("Great Ball"); call(p, "render")
            ShopCardKit.tap(getField(p, "tabBag") as TextView); call(p, "render")
            ShopCardKit.tap(getField(p, "tabShop") as TextView); call(p, "render")
            assertEquals(0, texts(screen).count { it == "▴" })
            screen
        }
    }

    @Test fun tooLittleMoneyFadesBuyAndTheCardStaysOpen() {
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
            assertEquals(1, texts(screen).count { it == "▴" })
            screen
        }
    }

    @Test fun bigTextKeepsTheOpenCardReadable() {
        shoot("shop-x2", listOf(Variant("w360", 360, 2200)), scale = 2f, check = { ctx, screen ->
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
            assertTrue("Who can learn it" in texts(screen))
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt"))).second }
    }

    // ---------- the Bag ----------

    private fun bagPage(ctx: Context, key: String?, setup: () -> Unit = {}) =
        ShopCardKit.page(ctx, key, bag = true, setup = { st.bag.clear(); st.bag.putAll(ShopCardKit.bag()); st.tms.clear(); st.tms += listOf("thunderbolt", "bodyslam"); setup() })

    @Test fun aPokeBallOpensWithThrowAndTheTradeButton() {
        shoot("bag-poke", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Poké Ball" in t)
            assertEquals(1, t.count { it == "▴" })
            assertTrue("Throw" in t)
            assertTrue(t.toString(), "Trade 5 Poké Balls for 1 Great Ball" in t)
            assertTrue("no arrow glyphs", t.none { it.contains("→") || it.contains("⇄") })
            assertTrue("a fight is on in the fake game, so Throw is usable", t.none { it == "Balls are thrown from a fight" })
            assertTrue("You own 7 Poké Balls" in t.map { ItemInfo.plain(it) })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("poke-ball")).second }
    }

    @Test fun theTradeConfirmIsInsideTheCardAndOneTapTradesOneBatch() {
        shoot("bag-trade-confirm", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Cancel" in t && "Trade 5 for 1" in t)
            assertTrue(t.toString(), t.any { it.startsWith("5 Poké Balls (1,000 ₽ in the Shop) for 1 Great Ball (700 ₽). On purpose worse than buying. You keep 2 Poké Balls.") })
            assertTrue("5 ×" in t && "1 ×" in t && "for" in t)
            assertTrue("no arrow glyphs", t.none { it.contains("→") || it.contains("⇄") })
            assertTrue("the Throw button hides while the confirm shows", "Throw" !in t)
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); call(p, "render")
            screen
        }
        shoot("bag-trade-done", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); call(p, "render")
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 for 1" }); call(p, "render")
            assertEquals(2, st.count("poke-ball"))
            assertEquals(4, st.count("great-ball"))
            val t = texts(screen)
            assertEquals("the card stays open", 1, t.count { it == "▴" })
            assertTrue("the confirm is closed", "Cancel" !in t)
            assertTrue(t.toString(), "You own 2 Poké Balls" in t.map { ItemInfo.plain(it) })
            assertTrue("2 Poké Balls are too few for another batch", t.any { it == "Need 5, you have 2" })
            screen
        }
    }

    @Test fun tradingTheLastBallsKeepsTheCardOpenWithZero() {
        shoot("bag-trade-last", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertEquals("the card stays open", 1, t.count { it == "▴" })
            assertTrue(t.toString(), "You don't own any Poké Balls yet" in t.map { ItemInfo.plain(it) })
            assertTrue(t.toString(), "Need 5, you have 0" in t)
            assertTrue("Throw is off with no ball", "You have none" in t)
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"), setup = { st.bag["poke-ball"] = 5 })
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); call(p, "render")
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 for 1" }); call(p, "render")
            assertEquals(0, st.count("poke-ball"))
            assertEquals(4, st.count("great-ball"))
            screen
        }
    }

    @Test fun bigTextKeepsTheBagTmCardReadable() {
        shoot("bag-tm-x2", listOf(Variant("w360", 360, 2800)), scale = 2f, check = { ctx, screen ->
            assertTrue(ctx.resources.configuration.fontScale == 2.0f)
            val t = texts(screen)
            assertTrue("Who can learn it" in t && t.any { it.endsWith("of your Pokémon") })
        }) { ctx -> bagPage(ctx, CardKeys.bagTm("thunderbolt"), setup = ::someDontKnowThunderbolt).second }
    }

    @Test fun tooFewBallsGreysTheTradeButton() {
        shoot("bag-poke-few", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Need 5, you have 3" in t)
            assertTrue("Trade 5 Poké Balls for 1 Great Ball" in t)
        }) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball")) { st.bag["poke-ball"] = 3 }
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); call(p, "render")
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
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("net-ball")) { st.bag["net-ball"] = 1 }
            assertTrue("an old ball's card counts Great Balls", "Trade 3 Great Balls for 1 Ultra Ball" in texts(screen))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it.startsWith("Trade 3 Great Balls") }); call(p, "render")
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
            screen
        }
        // in a fight the button starts today's confirm
        shoot("bag-throw-on", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (_, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Throw" }) }
            assertNotNull("Throw opens the confirm of today", d)
            screen
        }
    }

    @Test fun otherKindsOpenWithTheirActionButton() {
        shoot("bag-potion", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Use on…" in t)
            assertEquals(1, t.count { it == "▴" })
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
        shoot("bag-tm", listOf(BAGTM412, BAGTM360), check = { _, screen ->
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
            val (_, screen) = bagPage(ctx, CardKeys.bagTm("thunderbolt"), setup = ::someDontKnowThunderbolt)
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Who can learn it" }) } ?: error("no list")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun foldedRowsHaveTheCountAndAChevronAndATapOpensInsteadOfActing() {
        shoot("bag-folded", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertEquals(0, t.count { it == "▴" })
            assertTrue("×7" in t && "×3" in t)
            assertTrue("the Escape group", "ESCAPE" in t)
        }) { ctx -> bagPage(ctx, null).second }
        shoot("bag-tap", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, null)
            val potions = st.count("super-potion")
            val hp0 = st.team.map { st.mon(it)!!.hp }
            // a tap on a row opens the card: nothing is used, no picker
            val d = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Super Potion" }) }
            assertNull("a tap on a row shows no picker", d)
            call(p, "render")
            assertEquals(1, texts(screen).count { it == "▴" })
            assertEquals(potions, st.count("super-potion"))
            assertEquals(hp0, st.team.map { st.mon(it)!!.hp })
            // the card's button starts the picker of today
            val picker = DialogShots.capture { ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Use on…" }) }
            assertNotNull("Use on… opens the Pokémon picker", picker)
            screen
        }
    }

    @Test fun usingTheLastItemFoldsItsCard() {
        shoot("bag-last", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = bagPage(ctx, CardKeys.bagItem("poke-doll"))
            assertEquals(1, texts(screen).count { it == "▴" })
            st.bag.remove("poke-doll")
            call(p, "render")
            assertEquals("the card of an item that is gone is dropped", 0, texts(screen).count { it == "▴" })
            // and it does not pop open again when the item comes back
            st.bag["poke-doll"] = 1
            call(p, "render")
            assertEquals(0, texts(screen).count { it == "▴" })
            screen
        }
    }
}
