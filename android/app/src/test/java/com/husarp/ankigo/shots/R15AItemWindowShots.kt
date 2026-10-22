package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ItemWindow
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.ShopPlan
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R15-A (PLAN 210): a tap on a Shop tile, the deal, the weekend card or a Bag row opens the item's WINDOW (the app's bottom sheet) over the list
 * instead of pushing the list down; the small arrows are gone. PNGs: r15a-<scene>-<p412|p360|...>-<light|dark>.
 * The window helpers (Stage, page, texts) are ShopCardKit in R14B2BShots.kt.
 */
class R15AItemWindowShots {
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
            paparazzi.snapshot(frame, "r15a-$name-$label-${theme.name.lowercase()}", 1000)
        }
    }

    private fun texts(v: View) = ShopCardKit.texts(v)

    private fun bagPage(ctx: Context, key: String?, setup: () -> Unit = {}) =
        ShopCardKit.page(ctx, key, bag = true, setup = { st.bag.clear(); st.bag.putAll(ShopCardKit.bag()); st.tms.clear(); st.tms += listOf("thunderbolt", "bodyslam"); setup() })

    private fun noArrows(v: View) = assertTrue(texts(v).toString(), texts(v).none { it == "▾" || it == "▴" })

    /** [v] in the coordinates of [root] (no scrolling or translation in between). */
    private fun boundsIn(root: View, v: View): Rect {
        var l = 0; var t = 0; var x: View = v
        while (x !== root) { l += x.left; t += x.top; x = x.parent as View }
        return Rect(l, t, l + v.width, t + v.height)
    }

    private fun descriptions(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        v.contentDescription?.let { out += it.toString() }
        if (v is ViewGroup) for (i in 0 until v.childCount) descriptions(v.getChildAt(i), out)
        return out
    }

    /** Runs [act] and returns, besides the dialog it opened (null for none), whether the page still had an item window at the moment that dialog was shown. */
    private fun openedWith(p: ShopPage, act: () -> Unit): Pair<Any?, Boolean?> {
        var dialog: Any? = null
        var windowThen: Boolean? = null
        Pickers.interceptApp = { dialog = it; windowThen = p.itemWindow != null || p.open.key != null }
        try { act() } finally { Pickers.interceptApp = null }
        return dialog to windowThen
    }

    // ---------- the list behind: no arrows ----------

    @Test fun theShopListHasNoArrowsOnTilesTheDealOrTheWeekendCard() {
        shoot("shop-list", listOf(P412, P360), check = { _, screen ->
            noArrows(screen)
            assertNull("no window is open", ShopCardKit.findDesc(screen, "Close"))
            val d = descriptions(screen)
            assertTrue(d.toString(), d.any { it.endsWith(". Opens details") })
            assertTrue("the words of the old in-place card are gone", d.none { it.contains("double tap to open") || it.contains("in place") })
        }) { ctx -> ShopCardKit.page(ctx, null).second }
        shoot("shop-list-weekend", listOf(Variant("tall", 412, 1500)), themes = listOf(Theme.LIGHT), check = { _, screen ->
            noArrows(screen)
            assertTrue(ShopPlan.WEEKEND_NOTE in texts(screen))
        }) { ctx -> ShopCardKit.page(ctx, null, day = "2026-10-17").second }
    }

    @Test fun theBagListKeepsTheCountAndLosesTheArrow() {
        shoot("bag-list", listOf(P412), check = { _, screen ->
            noArrows(screen)
            assertTrue("×7" in texts(screen))
        }) { ctx -> bagPage(ctx, null).second }
    }

    // ---------- the window ----------

    @Test fun theWindowOfABallInTheShopHasBuy() {
        shoot("win-ball-buy", listOf(P412, P360), check = { ctx, screen ->
            val t = texts(screen)
            assertTrue("Buy" in t && "WHAT IT DOES" in t)
            val sheet = (screen as ShopCardKit.Stage).page.itemWindow!!.view
            val dm = ctx.resources.displayMetrics
            assertTrue("the window sits at the bottom of the screen", boundsIn(screen, sheet).bottom == screen.height)
            assertTrue("and takes at most 85 % of its height", sheet.height <= (dm.heightPixels * ItemWindow.MAX_SHARE).toInt() + 1)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball"))).second }
    }

    @Test fun theWindowOfATmHasTheMoveBlockAndWhoCanLearnIt() {
        shoot("win-tm", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("POWER" in t && "ACCURACY" in t && "SPECIAL" in t && "Who can learn it" in t)
        }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt"))).second }
    }

    @Test fun theWindowOfAnEvolutionItemInTheBagHasUse() {
        shoot("win-evo", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Use on…" in t)
            assertTrue("You own 1 Tart Apple" in t.map { ItemInfo.plain(it) })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("tart-apple")).second }
    }

    @Test fun theWindowOfABallInTheBagShowsTheTradeConfirm() {
        shoot("win-trade", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue("Cancel" in t && "Trade 5 for 1" in t)
        }) { ctx ->
            val (_, screen) = bagPage(ctx, CardKeys.bagItem("poke-ball"))
            ShopCardKit.tap(ShopCardKit.findText(screen) { it == "Trade 5 Poké Balls for 1 Great Ball" }); screen.render()
            screen
        }
    }

    @Test fun aBigFontWindowKeepsItsButtonsInViewAndTheBodyScrolls() {
        // 360 x 640 dp at font 1.6 and 2.0: the TM window is taller than the cap, so only its body gives way
        for (scale in listOf(1.6f, 2.0f)) {
            shoot("win-big", listOf(Variant("p360s", 360, 640)), scale = scale, check = { ctx, screen ->
                val stage = screen as ShopCardKit.Stage
                val sheet = stage.page.itemWindow!!.view
                val dm = ctx.resources.displayMetrics
                assertTrue("at most 85 % of the screen: ${sheet.height} of ${dm.heightPixels}", sheet.height <= (dm.heightPixels * ItemWindow.MAX_SHARE).toInt() + 1)
                val buy = ShopCardKit.allText(stage, { it.text.toString() == "Buy" }).single()
                val b = boundsIn(stage, buy)
                assertTrue("Buy is in view: $b of ${stage.height}", b.bottom <= stage.height && b.top >= stage.height - sheet.height)
                val close = boundsIn(stage, ShopCardKit.findDesc(stage, "Close")!!)
                assertTrue("the ✕ is a 48 dp touch area", close.width() >= (48 * dm.density).toInt() - 1 && close.height() >= (48 * dm.density).toInt() - 1)
            }) { ctx -> ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Tm("thunderbolt"))).second }
        }
        shoot("win-big", listOf(P360), scale = 1.6f, themes = listOf(Theme.LIGHT)) { ctx -> bagPage(ctx, CardKeys.bagItem("poke-ball")).second }
    }

    // ---------- the rules ----------

    @Test fun buyKeepsTheWindowOpenAndTheCountLineChanges() {
        shoot("buy", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, stage) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball")))
            val w = p.itemWindow
            assertNotNull(w)
            val n0 = st.count("great-ball")
            val cash0 = st.cash
            val bagTab = getField(p, "tabBag") as TextView
            val bag0 = bagTab.text.toString()
            assertTrue(ItemInfo.plain(ItemInfo.ownLine("great-ball", n0)) in texts(stage))
            ShopCardKit.tap(ShopCardKit.allText(stage, { it.text.toString() == "Buy" }).single()); stage.render()
            assertEquals(n0 + 1, st.count("great-ball"))
            assertTrue(st.cash < cash0)
            assertSame("the same window, drawn again", w, p.itemWindow)
            assertEquals("no second window", 1, ShopCardKit.windowsShown)
            assertTrue(ItemInfo.plain(ItemInfo.ownLine("great-ball", n0 + 1)) in texts(stage))
            assertTrue("the list behind counts the bag again: $bag0 to ${bagTab.text}", bagTab.text.toString() != bag0)
            // and again: still one window
            ShopCardKit.tap(ShopCardKit.allText(stage, { it.text.toString() == "Buy" }).single()); stage.render()
            assertTrue(ItemInfo.plain(ItemInfo.ownLine("great-ball", n0 + 2)) in texts(stage))
            assertEquals(1, ShopCardKit.windowsShown)
            stage
        }
    }

    @Test fun aButtonThatOpensAPickerClosesTheWindowFirst() {
        shoot("closes-first", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            // Use on… (a potion), Give to… (the amulet), Teach… (a TM), Revive (the fossil), Throw (a ball in a fight)
            for (uid in listOf("t2", "t3", "t5", "t6")) st.mon(uid)!!.moves.apply { clear(); add("tackle") } // some Pokémon that can still learn Thunderbolt
            for ((key, button) in listOf(CardKeys.bagItem("super-potion") to "Use on…", CardKeys.bagItem("amulet-coin") to "Give to…",
                CardKeys.bagTm("thunderbolt") to "Teach…", CardKeys.bagItem("helix-fossil") to "Revive", CardKeys.bagItem("poke-ball") to "Throw")) {
                val (p, stage) = bagPage(ctx, key)
                assertNotNull("$button: the window is open", p.itemWindow)
                val (dialog, windowThen) = openedWith(p) { ShopCardKit.tap(ShopCardKit.findText(stage) { it == button }) }
                assertNotNull("$button opens its picker or confirm", dialog)
                assertEquals("$button: the window was already gone when the dialog was shown", false, windowThen)
                assertNull("$button: no window afterwards", p.itemWindow)
                stage.render()
                assertNull("$button: a render does not bring it back", p.itemWindow)
            }
            bagPage(ctx, null).second
        }
    }

    @Test fun aButtonThatCannotBeUsedNowOnlySaysWhyAndTheWindowStays() {
        shoot("greyed", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, stage) = bagPage(ctx, CardKeys.bagItem("poke-ball")) { st.battle = null }
            val (dialog, _) = openedWith(p) { ShopCardKit.tap(ShopCardKit.findText(stage) { it == "Throw" }) }
            assertNull(dialog)
            assertNotNull(p.itemWindow)
            stage
        }
    }

    @Test fun aButtonThatEndsOnlyInAMessageKeepsTheWindowOpenAndShowsIt() {
        shoot("message-only", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            // an evolution item and a TM that nobody can use: the answer is a toast, so there is nothing to close the window for
            for ((key, button, message) in listOf(
                Triple(CardKeys.bagItem("tart-apple"), "Use on…", "No Pokémon can use Tart Apple right now"),
                Triple(CardKeys.bagTm("thunderbolt"), "Teach…", "None of your Pokémon can learn"))) {
                val (p, stage) = bagPage(ctx, key) { st.mons.clear(); st.team.clear() }
                assertNotNull("$button: the window is open", p.itemWindow)
                Ui.lastToast = null
                val (dialog, _) = openedWith(p) { ShopCardKit.tap(ShopCardKit.findText(stage) { it == button }) }
                assertNull("$button: no picker", dialog)
                assertNotNull("$button: the window stays open", p.itemWindow)
                assertTrue("$button: the message is shown (${Ui.lastToast})", Ui.lastToast?.startsWith(message) == true)
                stage.render()
                assertNotNull("$button: still open after a render", p.itemWindow)
            }
            bagPage(ctx, CardKeys.bagItem("tart-apple")) { st.mons.clear(); st.team.clear() }.second
        }
    }

    @Test fun switchingTheTabOrLeavingThePageClosesTheWindow() {
        shoot("leave", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, stage) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball")))
            assertNotNull(p.itemWindow)
            // Shop to Bag
            ShopCardKit.tap(getField(p, "tabBag") as TextView)
            assertNull("closed by the tap itself", p.itemWindow)
            stage.render()
            assertNull(p.open.key)
            // an open Bag window, then Bag to Shop
            st.bag["poke-ball"] = 7
            p.openCard(CardKeys.bagItem("poke-ball")); stage.render()
            assertNotNull(p.itemWindow)
            ShopCardKit.tap(getField(p, "tabShop") as TextView)
            assertNull(p.itemWindow)
            // leaving the page
            p.openCard(CardKeys.special(ShopPlan.Tile.Item("great-ball"))); stage.render()
            assertNotNull(p.itemWindow)
            p.onHide()
            assertNull(p.itemWindow)
            assertNull(p.open.key)
            stage.render()
            assertNull("it does not come back on the next render", p.itemWindow)
            stage
        }
    }

    @Test fun aRerenderKeepsExactlyOneWindowAndAnotherItemReusesIt() {
        shoot("one", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, stage) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball")))
            val w = p.itemWindow
            assertNotNull(w)
            repeat(3) { stage.render() }
            assertSame(w, p.itemWindow)
            assertEquals(1, ShopCardKit.windowsShown)
            p.openCard(CardKeys.special(ShopPlan.Tile.Tm("thunderbolt"))); stage.render()
            assertSame("another item changes the window's content, not the window", w, p.itemWindow)
            assertEquals(1, ShopCardKit.windowsShown)
            assertTrue("TM Thunderbolt" in texts(stage))
            assertEquals("one ✕, one Buy: one sheet", 1, ShopCardKit.allText(stage, { it.text.toString() == "Buy" }).size)
            // the ✕ closes it for good; a render brings nothing back, a tap opens a fresh one
            ShopCardKit.tap(ShopCardKit.findDesc(stage, "Close"))
            assertNull(p.itemWindow)
            assertNull(p.open.key)
            stage.render()
            assertNull(p.itemWindow)
            p.openCard(CardKeys.special(ShopPlan.Tile.Item("great-ball"))); stage.render()
            assertNotNull(p.itemWindow)
            assertEquals(2, ShopCardKit.windowsShown)
            stage
        }
    }

    @Test fun aWindowForSomethingThatIsNotOnTheTabAnyMoreIsClosed() {
        shoot("gone", listOf(P412), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, stage) = ShopCardKit.page(ctx, CardKeys.special(ShopPlan.Tile.Item("great-ball")))
            assertNotNull(p.itemWindow)
            st.shopItems.remove("great-ball") // a restock: the tile is gone
            stage.render()
            assertNull(p.itemWindow)
            assertNull(p.open.key)
            // the weekend card on a weekday
            p.openCard(CardKeys.WEEKEND); stage.render()
            assertNull("no weekend special on a weekday", p.itemWindow)
            stage
        }
    }
}
