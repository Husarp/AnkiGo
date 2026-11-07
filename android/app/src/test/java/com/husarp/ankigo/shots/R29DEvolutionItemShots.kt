package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import com.husarp.ankigo.data.Sprites
import java.io.File
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.CardKeys
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native item sheets: source-only grid, branching sources, seen visibility and adaptive columns. */
class R29DEvolutionItemShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun grids(v: View): List<GridLayout> =
        (if (v is GridLayout) listOf(v) else emptyList()) +
            (if (v is ViewGroup) (0 until v.childCount).flatMap { grids(v.getChildAt(it)) } else emptyList())

    private fun render(item: String, count: Int, caughtCount: Int = count, bag: Boolean = true) {
        for (width in listOf(360, 412, 800)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p$width", width, 892).device(theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val base = ShopCardKit.world()
            val names = when (item) {
                "tart-apple" -> listOf("Applin")
                "dawn-stone" -> listOf("Kirlia")
                "razor-claw" -> listOf("Sneasel")
                else -> listOf("Eevee", "Alolan Vulpix", "Alolan Sandshrew", "Galarian Darumaka", "Crabrawler", "Cetoddle")
            }
            val spriteIds = when (item) {
                "tart-apple" -> listOf(840)
                "dawn-stone" -> listOf(281)
                "razor-claw" -> listOf(215)
                else -> listOf(133, 10103, 10101, 10176, 739, 974)
            }
            for (i in 0 until count) {
                val file = File(Sprites.dir(ctx), "static/${4000 + i}.png")
                file.parentFile!!.mkdirs()
                javaClass.getResourceAsStream("/r29d/${spriteIds[i]}.png")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            // Production reads only warmed memory on the UI thread; refresh reused fixture ids
            // after replacing their PNGs so every scene gets its own species artwork.
            val warm = Thread { for (i in 0 until count) checkNotNull(Sprites.staticBitmap(ctx, 4000 + i)) }
            warm.start(); warm.join()
            for (i in 0 until count) assertNotNull(Sprites.staticBitmap(ctx, 4000 + i))
            val extra = (0 until count).associate { i ->
                (4000 + i) to TestData.species(4000 + i, names[i], evolutions = listOf(
                    Evolution(5000, EvoKind.ITEM, item = item, time = if (item == "razor-claw") "night" else null, gender = if (item == "dawn-stone") 'M' else null),
                    Evolution(5001, EvoKind.ITEM, item = item, time = if (item == "razor-claw") "night" else null, gender = if (item == "dawn-stone") 'M' else null),
                ))
            } + mapOf(5000 to TestData.species(5000, "HiddenResult"), 5001 to TestData.species(5001, "OtherResult"))
            val data = GameData(base.species + extra, base.moves, base.growth, base.itemPrices)
            GameRepo::class.java.getDeclaredField("loadedData").apply { isAccessible = true }.set(GameRepo, data)
            GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
            val (_, screen) = ShopCardKit.page(ctx, if (bag) CardKeys.bagItem(item) else "special:item:$item", bag = bag, setup = {
                GameRepo.state!!.apply {
                    seen.clear(); seen += (4000 until 4000 + caughtCount)
                    caught.clear(); caught += listOf(5000, 5001)
                    this.bag.clear(); this.bag[item] = 1
                    shopItems.clear(); shopItems += item
                }
            })
            val dm = ctx.resources.displayMetrics
            screen.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
            screen.layout(0, 0, dm.widthPixels, dm.heightPixels)
            val text = ShopCardKit.texts(screen)
            assertTrue(text.toString(), text.none { it.contains("HiddenResult") || it.contains("OtherResult") || it.contains("⟶") || it.contains(" into ") })
            if (item == "razor-claw") assertTrue(text.toString(), "night only" in text)
            if (item == "dawn-stone") assertTrue(text.toString(), "male only" in text)
            val grid = grids(screen).singleOrNull()
            assertNotNull(grid)
            assertEquals(count, grid!!.childCount)
            assertEquals(grid.width / com.husarp.ankigo.ui.Ui.dp(ctx, 68), grid.columnCount)
            assertEquals(count - caughtCount, text.count { it == "???" })
            assertFalse(text.contains("Use on…"))
            for (i in 0 until count) {
                val cell = grid.getChildAt(i)
                assertEquals(i < caughtCount, cell.isClickable)
                val sprite = (cell as ViewGroup).getChildAt(0) as android.widget.ImageView
                assertEquals(com.husarp.ankigo.ui.Ui.dp(ctx, 64), sprite.width)
                assertEquals(com.husarp.ankigo.ui.Ui.dp(ctx, 64), sprite.height)
                assertEquals(i >= caughtCount, sprite.background != null)
                assertEquals(i >= caughtCount, sprite.colorFilter != null)
                if (i >= caughtCount) assertFalse(cell.performClick())
                assertEquals(if (i < caughtCount) 1 else 0, text.count { it == names[i] })
                assertTrue("cell fits grid", cell.left >= 0 && cell.right <= grid.width)
                if (i >= grid.columnCount) assertTrue(cell.top > grid.getChildAt(i % grid.columnCount).top)
            }
            paparazzi.snapshot(screen, "r29d-$item-${if (bag) "bag" else "shop"}-$caughtCount-p$width-${theme.name.lowercase()}", 1000)
            if (grid != null) {
                // The same open sheet also adapts when a floating window changes width.
                for (dpWidth in listOf(400, 280, 400)) {
                    grid.measure(View.MeasureSpec.makeMeasureSpec((dpWidth * dm.density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    assertEquals(dpWidth / 68, grid.columnCount)
                }
            }
        }
    }

    @Test fun timeRestrictionRemainsVisible() = render("razor-claw", 1)
    @Test fun genderRestrictionRemainsVisible() = render("dawn-stone", 1)
    @Test fun iceStoneGrid() = render("ice-stone", 6)
    @Test fun branchingSourceAppearsOnceInShop() = render("tart-apple", 1, bag = false)
    @Test fun partialKnowledgeShowsSilhouettesEvenWhenResultsAreCaught() = render("ice-stone", 6, 2)
    @Test fun noKnownSourceStillShowsEverySourceSilhouette() = render("ice-stone", 6, 0)
}
