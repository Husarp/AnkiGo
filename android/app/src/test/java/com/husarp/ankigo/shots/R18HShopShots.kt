package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R18-H (PLAN 236 / 237 / 238): the item window with section titles in the accent colour, an evolution item that does not name Pokémon you
 * have not caught, and the Shop tile "Evolves ???". PNGs: r18h-<scene>-<p412|p360>-<light|dark>.
 */
class R18HShopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val st get() = GameRepo.state!!

    private val P412 = Variant("p412", 412, 892)
    private val P360 = Variant("p360", 360, 780)

    /** The kit's world plus Applin (Tart Apple: Applin into Flapple) so the apple has a species to hide. */
    private fun installWorld(): GameData {
        val base = ShopCardKit.world()
        val extra = mapOf(
            4010 to TestData.species(4010, "Applin", evolutions = listOf(Evolution(4011, EvoKind.ITEM, item = "tart-apple"))),
            4011 to TestData.species(4011, "Flapple"),
        )
        val d = GameData(base.species + extra, base.moves, base.growth, base.itemPrices)
        val f = GameRepo::class.java.getDeclaredField("loadedData"); f.isAccessible = true; f.set(GameRepo, d)
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild"); rebuild.isAccessible = true; rebuild.invoke(GameRepo)
        return d
    }

    private fun layoutNow(ctx: Context, v: View) {
        val dm = ctx.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun shoot(name: String, variants: List<Variant> = listOf(P412), check: (Context, View) -> Unit = { _, _ -> }, build: (Context) -> View) {
        for (v in variants) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            installWorld()
            val view = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
            layoutNow(ctx, frame)
            try { check(ctx, view) } catch (e: Throwable) { throw AssertionError("${v.label}/${theme.name}: ${e.message}", e) }
            paparazzi.snapshot(frame, "r18h-$name-${v.label}-${theme.name.lowercase()}", 1000)
        }
    }

    private fun texts(v: View) = ShopCardKit.texts(v)

    private fun bagPage(ctx: Context, key: String?, setup: () -> Unit = {}) =
        ShopCardKit.page(ctx, key, bag = true, setup = { st.bag.clear(); st.bag.putAll(ShopCardKit.bag()); st.tms.clear(); setup() })

    private fun caughtOnly(vararg nums: Int) { st.caught.clear(); st.caught += nums.toList() }

    // ---------- 236: the titles ----------

    @Test fun aHealItemCardHasAccentTitlesBiggerThanBefore() {
        shoot("card-heal", listOf(P412, P360), check = { ctx, screen ->
            val titles = ShopCardKit.allText(screen, { it.text.toString() in setOf("WHAT IT DOES", "HOW TO USE", "IN A FIGHT") })
            assertEquals(texts(screen).toString(), 3, titles.size)
            val accent = ctx.getColor(com.husarp.ankigo.ui.AppLook.accentRes(ctx))
            for (t in titles) {
                assertEquals("12.5 sp title", 12.5f, t.textSize / ctx.resources.displayMetrics.scaledDensity, 0.01f)
                assertEquals("the accent colour", accent, t.currentTextColor)
                assertNotEquals("not the old grey", ctx.getColor(R.color.ag_sub0), t.currentTextColor)
            }
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("super-potion")).second }
    }

    // ---------- 238: unknown evolutions ----------

    @Test fun anEvolutionCardNamesOnlyWhatYouCaught() {
        // Applin caught, Flapple not
        shoot("card-evo-from-caught", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen).map { com.husarp.ankigo.game.ItemInfo.plain(it) }
            assertTrue(t.toString(), "Evolves Applin into an unknown Pokémon" in t || t.any { it == "Evolves Applin into an unknown Pokémon." })
            assertTrue(t.toString(), t.none { it.contains("Flapple") })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("tart-apple")) { caughtOnly(4010) }.second }
        // nothing of it caught
        shoot("card-evo-none-caught", listOf(P412), check = { _, screen ->
            val t = texts(screen).map { com.husarp.ankigo.game.ItemInfo.plain(it) }
            assertTrue(t.toString(), t.any { it == "Evolves an unknown Pokémon." })
            assertTrue(t.toString(), t.none { it.contains("Applin") || it.contains("Flapple") })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("tart-apple")) { caughtOnly(25) }.second }
        // both caught: the full text
        shoot("card-evo-both-caught", listOf(P412), check = { _, screen ->
            val t = texts(screen).map { com.husarp.ankigo.game.ItemInfo.plain(it) }
            assertTrue(t.toString(), t.any { it == "Evolves Applin into Flapple." })
        }) { ctx -> bagPage(ctx, CardKeys.bagItem("tart-apple")) { caughtOnly(4010, 4011) }.second }
    }

    @Test fun theShopTileOfAnEvolutionItemSaysEvolvesQuestionMarks() {
        shoot("shop-tile", listOf(P412, P360), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Evolves ???" in t)
            assertTrue(t.toString(), t.none { it.contains("Applin") })
        }) { ctx -> ShopCardKit.page(ctx, null, setup = { caughtOnly(25) }).second }
    }

    @Test fun theBagRowSaysTheSame() {
        shoot("bag-rows", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "Evolves an unknown Pokémon" in t)
        }) { ctx -> bagPage(ctx, null) { caughtOnly(25) }.second }
    }

    // ---------- 237: the pill ----------

    @Test fun theShopBarStartsWithTheRealAmountOrNone() {
        shoot("shop-pill", listOf(P412), check = { _, screen ->
            val t = texts(screen)
            assertTrue(t.toString(), "5,000" in t)
            assertTrue("never a first 0", ShopPlan.pillText(null) == "")
        }) { ctx -> ShopCardKit.page(ctx, null, cash = 5000).second }
    }
}
