package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R36PShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private val realData by lazy {
        System.getenv("ANKIGO_REAL_DATA")?.let { path ->
            fun text(name: String) = File(path, name).readText()
            Parsers.build(Parsers.Sources(text("pokedex.json"), text("moves.json"), text("learnsets.json"),
                text("pokemon_species.csv"), text("pokemon.csv"), text("pokemon_stats.csv"),
                text("pokemon_evolution.csv"), text("experience.csv"), text("items.csv")))
        }
    }

    private fun warmArt(ctx: android.content.Context) {
        val source = System.getenv("ANKIGO_R36_SPRITES")?.let(::File) ?: return
        source.copyRecursively(Sprites.dir(ctx), overwrite = true)
        Thread {
            source.resolve("static").listFiles().orEmpty().forEach { file ->
                file.nameWithoutExtension.toIntOrNull()?.let { Sprites.staticBitmap(ctx, it) }
            }
            source.resolve("item").listFiles().orEmpty().forEach { Sprites.itemBitmap(ctx, it.nameWithoutExtension) }
        }.apply { start(); join() }
    }

    private fun checkRows(root: View) {
        val texts = all(root).filterIsInstance<TextView>().map { it.text.toString() }
        assertFalse(texts.any { it.endsWith(" met") || Regex("· [0-9]+ in your Bag").containsMatchIn(it) })
        val rows = all(root).filterIsInstance<EvolutionRequirementRow>()
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            val content = row.getChildAt(0) as ViewGroup
            val mark = content.getChildAt(0) as TextView
            assertTrue(mark.text.toString() in listOf("✓", "✗"))
            val label = (content.getChildAt(1) as TextView).text.toString()
            assertTrue(label, listOf("Reach ", "Use ", "Know ", "Evolve ", "Be ", "Have ").any { label.startsWith(it) })
            if (content.childCount == 3) assertTrue(content.getChildAt(2) is ImageView)
        }
    }

    @Test fun marksAndItemsHugTextIncludingWrappedRows() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            for (width in listOf(160, 360)) for (met in listOf(false, true)) {
                for (label in listOf("Use Syrupy Apple", "Use Syrupy Apple after reaching the required bond level")) {
                    val row = EvolutionRequirementRow(ctx, EvolutionRowText.Requirement(label, met, "syrupy-apple"))
                    row.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, width), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    val content = row.getChildAt(0) as ViewGroup
                    val mark = content.getChildAt(0) as TextView
                    val text = content.getChildAt(1) as TextView
                    val icon = content.getChildAt(2)
                    assertEquals(Ui.dp(ctx, 6), text.left - mark.right)
                    assertEquals(Ui.dp(ctx, 8), icon.left - text.right)
                    assertTrue(icon.right <= row.width)
                    assertTrue(text.width > 0)
                    assertEquals(if (met) "✓" else "✗", mark.text.toString())
                    if (width == 360 && label == "Use Syrupy Apple") {
                        assertEquals(1, text.lineCount)
                        assertTrue("item must follow the short text, leaving trailing room", icon.right < row.width - Ui.dp(ctx, 60))
                    }
                    if (width == 160 && label.length > 30) assertTrue(text.lineCount > 1)
                }
            }
        }
    }

    @Test fun distinctSpeciesAndRequirementsLightAndDark() {
        val cases = listOf(
            Triple(5, "Charmeleon", Evolution(6, EvoKind.LEVEL, level = 36)),
            Triple(840, "Applin", Evolution(1011, EvoKind.ITEM, item = "syrupy-apple")),
            Triple(108, "Lickitung", Evolution(463, EvoKind.LEVEL_MOVE, move = "rollout")),
            Triple(215, "Sneasel", Evolution(461, EvoKind.ITEM, item = "razor-claw", time = "night")),
            Triple(95, "Onix", Evolution(208, EvoKind.TRADE, item = "metal-coat")),
            Triple(64, "Kadabra", Evolution(65, EvoKind.TRADE)),
            Triple(133, "Eevee", Evolution(196, EvoKind.FRIENDSHIP, minFriendship = 160, time = "day")),
            Triple(281, "Kirlia", Evolution(475, EvoKind.ITEM, item = "dawn-stone", gender = 'M')),
            Triple(674, "Pancham", Evolution(675, EvoKind.LEVEL, level = 32, condition = "with a Dark-type in the party")),
            Triple(133, "Eevee-Fairy", Evolution(700, EvoKind.FRIENDSHIP, moveType = Type.FAIRY)),
            Triple(236, "Tyrogue", Evolution(106, EvoKind.LEVEL, level = 20, condition = "with an Atk stat > its Def stat")),
            Triple(299, "Nosepass", Evolution(476, EvoKind.OTHER, condition = "in a special place")),
        )
        for (theme in Theme.entries) for ((num, name, fixtureEvo) in cases) {
            val evo = realData?.sp(num)?.evolutions?.single { it.to == fixtureEvo.to } ?: fixtureEvo
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            warmArt(ctx)
            val target = mapOf(6 to "Charizard", 1011 to "Dipplin", 463 to "Lickilicky", 461 to "Weavile",
                208 to "Steelix", 65 to "Alakazam", 196 to "Espeon", 475 to "Gallade", 675 to "Pangoro",
                700 to "Sylveon", 106 to "Hitmonlee", 476 to "Probopass").getValue(evo.to)
            val data = realData ?: TestData.data(listOf(TestData.species(num, name, evolutions = listOf(evo)),
                TestData.species(evo.to, target, prevo = num)),
                TestData.MOVES + ("rollout" to Move("rollout", "Rollout", Type.ROCK, Category.PHYSICAL, 30, 90)))
            val state = TestData.state(data, num, 30).apply { bag.clear(); bag["metal-coat"] = 1 }
            val mon = state.main!!.apply { bondPoints = 125; gender = 'F' }
            val dialog = EvolutionRows.explanationDialog(ctx, data, evo, data.sp(num), setOf(num, evo.to), state, mon) {}
            checkRows(dialog.content())
            paparazzi.snapshot(DialogShots.scene(ctx, dialog), "p-evolution-${name.lowercase()}-${theme.name.lowercase()}")
            val rows = EvolutionRows(ctx, data, EvoLine.of(data, data.sp(num)).root, data.sp(num), setOf(num), null, state, mon)
            checkRows(rows)
            if (name == "Applin") paparazzi.snapshot(rows, "p-details-applin-${theme.name.lowercase()}")
        }
    }

    @Test fun actualItemWindowsUseTheSameRowsForOwnedAndMissingItems() {
        for (theme in Theme.entries) for (owned in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            warmArt(ctx)
            val base = realData ?: ShopCardKit.world()
            val data = realData ?: GameData(base.species + mapOf(
                840 to TestData.species(840, "Applin", evolutions = listOf(Evolution(1011, EvoKind.ITEM, item = "syrupy-apple"))),
                1011 to TestData.species(1011, "Dipplin", prevo = 840)), base.moves, base.growth,
                base.itemPrices + ("syrupy-apple" to 2200))
            GameRepo::class.java.getDeclaredField("loadedData").apply { isAccessible = true }.set(GameRepo, data)
            GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
            val (_, screen) = ShopCardKit.page(ctx, "special:item:syrupy-apple", setup = {
                GameRepo.state!!.apply {
                    bag.clear()
                    if (owned) bag["syrupy-apple"] = 1
                    shopItems.clear(); shopItems += "syrupy-apple"
                    mons.add(TestData.mon(data, 840, 20).apply { bondPoints = if (owned) 125 else 0 })
                    seen.add(840); caught.add(840)
                }
            })
            checkRows(screen)
            val bond = all(screen).filterIsInstance<EvolutionRequirementRow>().single {
                ((it.getChildAt(0) as ViewGroup).getChildAt(1) as TextView).text.toString() == "Reach Level 2 Bond"
            }
            assertEquals(if (owned) "✓" else "✗", ((bond.getChildAt(0) as ViewGroup).getChildAt(0) as TextView).text.toString())
            val row = all(screen).filterIsInstance<EvolutionRequirementRow>().single {
                ((it.getChildAt(0) as ViewGroup).getChildAt(1) as TextView).text.toString() == "Use Syrupy Apple"
            }
            assertEquals(if (owned) "✓" else "✗", ((row.getChildAt(0) as ViewGroup).getChildAt(0) as TextView).text.toString())
            paparazzi.snapshot(screen, "p-item-syrupy-apple-${if (owned) "owned" else "missing"}-${theme.name.lowercase()}")
        }
    }
}
