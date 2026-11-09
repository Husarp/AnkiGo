package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** PLAN 559: renders the production Multicolor setting, with real cached Pokémon art. */
class R32RMulticolorShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    private val originalStore = DetailSections.storeOf
    private val data by lazy {
        val dir = File(System.getenv("ANKIGO_REAL_DATA") ?: "/tmp/r17c-data2")
        fun read(n: String) = File(dir, n).readText()
        Parsers.build(Parsers.Sources(read("pokedex.json"), read("moves.json"), read("learnsets.json"),
            read("pokemon_species.csv"), read("pokemon.csv"), read("pokemon_stats.csv"),
            read("pokemon_evolution.csv"), read("experience.csv"), read("items.csv")))
    }
    @After fun clean() {
        DetailSections.storeOf = originalStore
        BattleShotKit.reset()
        FakeGame.withSprites = true
        Pickers.interceptApp = null
        AppLook.setAccent(Shots.files(paparazzi.context), Accent.GREEN)
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun detailsCollapsedAndHeldItemOpen() {
        for (theme in Theme.entries) for (open in listOf(false, true)) {
            // A taller frame exposes all eight headers when Held item is open.
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(screenHeight = (if (open) 1100 else 915) * 2))
            val a = Shots.files(paparazzi.context)
            AppLook.setAccent(a, Accent.MULTICOLOR)
            assertEquals(824, a.resources.displayMetrics.widthPixels)
            FakeGame.withSprites = false
            FakeGame.install(a)
            val source = File(System.getenv("ANKIGO_R32J_SPRITES") ?: "/tmp/ankigo-r31c-sprites")
            for (n in listOf(1, 2, 3)) {
                val sprite = File(source, "static/$n.png")
                require(sprite.length() > 500) { "Real Pokémon sprite required: $sprite" }
                val target = File(Sprites.dir(a), "static/$n.png")
                target.parentFile!!.mkdirs(); sprite.copyTo(target, overwrite = true)
            }
            val item = File(source, "item/everstone.png")
            require(item.length() > 100) { "Real Everstone sprite required" }
            File(Sprites.dir(a), "item/everstone.png").also { it.parentFile!!.mkdirs(); item.copyTo(it, overwrite = true) }
            Thread {
                for (n in listOf(1, 2, 3)) Sprites.staticBitmap(a, n, false)
                Sprites.itemBitmap(a, "everstone")
            }.apply { start(); join() }
            assertNotNull("Real hero sprite is decoded before rendering", Sprites.staticBitmap(a, 1, false))
            assertNotNull("Real held item is decoded before rendering", Sprites.itemBitmap(a, "everstone"))
            BattleShotKit.gameRepoSet("loadedData", data)
            BattleShotKit.gameRepoSet("held", FakeGame.state(data))
            GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
            a.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val mon = Mon("r32j", 1, data.sp(1).name, 26, 0, Stats(20, 20, 20, 20, 20, 20), Stats.ZERO,
                Nature.BOLD, 'M', false, "overgrow", mutableListOf("tackle", "growl"), 70, bondPoints = 360).apply {
                    heldItem = "everstone"
                    hp = (StatCalc.maxHp(data, this) * .8).toInt()
                    xp = (Progression.xpToNext(data, this) * .4).toInt()
                }
            GameRepo.state!!.apply { mons += mon; caught += listOf(1, 2, 3) }
            DetailSections.storeOf = { object : DetailOpenStore {
                override fun get(key: String): Boolean = open && key == "held-item"
                override fun set(key: String, open: Boolean) = Unit
            } }
            val dialog = AppDialogShots.capture { MonDetail.show(a, data, mon) }!!
            // Resolve the already cached real sprite synchronously; cancel the idle preview's pending lookup.
            val hero = all(dialog.content()).filterIsInstance<ImageView>().single { it.contentDescription == "Show the picture full screen" }
            Ui.staticSprite(a, 1, false, hero, download = false)
            assertFalse(hero.drawable is SpritePlaceholder)
            val cards = all(dialog.content()).filter { it.tag is DetailSection }
            assertEquals(8, cards.size)
            assertEquals(DetailSection.entries.toSet(), cards.map { it.tag }.toSet())
            for (card in cards) {
                val section = card.tag as DetailSection
                val bar = (card as LinearLayout).getChildAt(0) as LinearLayout
                val icon = bar.getChildAt(0) as ImageView
                val title = all(bar.getChildAt(1)).filterIsInstance<TextView>().single()
                assertEquals(section.title.uppercase(), title.text.toString())
                val colour = Multicolor.color(a, Multicolor.section(section))
                assertEquals(Ui.c(a, R.color.ag_text), title.currentTextColor)
                assertEquals(colour, (icon.colorFilter as android.graphics.PorterDuffColorFilter).color)
                assertNull("Bare section icons", icon.background)
                assertTrue(bar.contentDescription.toString().endsWith(if (open && section == DetailSection.HELD_ITEM) "open" else "closed"))
            }
            val scene = AppDialogShots.scene(a, dialog)
            paparazzi.snapshot(scene, "r-details-${if (open) "open" else "collapsed"}-${theme.name.lowercase()}", 1000)
            for (card in cards) {
                var top = 0
                var ancestor: View? = card
                while (ancestor != null && ancestor !== scene) {
                    top += ancestor.top - ancestor.scrollY
                    ancestor = ancestor.parent as? View
                }
                assertSame(scene, ancestor)
                assertTrue("Complete section fits: ${card.tag}", top >= Ui.dp(a, 24) && top + card.height <= scene.height - Ui.dp(a, 24))
            }
        }
    }
    @Test fun boxShopAndLookWithProductionTabs() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            AppLook.setAccent(ctx, Accent.MULTICOLOR)
            FakeGame.withSprites = false
            FakeGame.install(ctx)
            val source = File("/tmp/ankigo-r31c-sprites")
            val numbers = GameRepo.state!!.mons.map { it.species }.distinct()
            for (n in numbers) {
                val sprite = File(source, "static/$n.png")
                require(sprite.length() > 100) { "Real Box sprite required: $sprite" }
                File(Sprites.dir(ctx), "static/$n.png").also { it.parentFile!!.mkdirs(); sprite.copyTo(it, overwrite = true) }
            }
            val items = File(source, "item").listFiles().orEmpty().filter { it.extension == "png" }
            for (item in items) File(Sprites.dir(ctx), "item/${item.name}").also {
                it.parentFile!!.mkdirs(); item.copyTo(it, overwrite = true)
            }
            Thread {
                for (n in numbers) Sprites.staticBitmap(ctx, n, false)
                for (item in items) Sprites.itemBitmap(ctx, item.nameWithoutExtension)
            }.apply { start(); join() }
            GameRepo.state!!.mons.first().favourite = true
            val box = TeamPage(ctx, NoHost).apply { preset(TeamSection.BOX, group = false) }
            val boxView = pageScreen(ctx, box)
            call(box, "render")
            paparazzi.snapshot(boxView, "r-box-${theme.name.lowercase()}", 1000)
            assertTrue(all(boxView).filterIsInstance<TextView>().any { it.text.toString() == "Box" })
            box.onHide()
            ShopCardKit.install()
            val (shop, stage) = ShopCardKit.page(ctx, null)
            assertTrue(ShopCardKit.texts(stage).contains("Shop"))
            assertTrue(ShopCardKit.texts(stage).contains("Bag"))
            paparazzi.snapshot(stage, "r-shop-${theme.name.lowercase()}", 1000)
            shop.onHide()
            val lookCtx = ShotCtx(ctx)
            val look = SettingsPage(lookCtx, NoHost)
            val lookView = pageScreen(lookCtx, look)
            look.open(SettingsSection.LOOK)
            val swatch = all(lookView).single { it.contentDescription == "Multicolor, selected" }
            assertTrue(swatch.isClickable)
            assertEquals("Settings is fully visible", 1f, look.root.alpha, 0f)
            assertEquals(0f, look.root.translationX, 0f)
            paparazzi.snapshot(lookView, "r-settings-look-${theme.name.lowercase()}", 1000)
            assertTrue(swatch.width > 0 && swatch.height > 0)
            var ancestor: View? = swatch
            while (ancestor != null && ancestor !== lookView) {
                assertEquals(View.VISIBLE, ancestor.visibility)
                assertEquals(1f, ancestor.alpha, 0f)
                ancestor = ancestor.parent as? View
            }
            assertSame(lookView, ancestor)
            look.onHide()
        }
    }

}
