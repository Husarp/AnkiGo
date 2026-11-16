package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.data.BackdropPic
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropPrefs
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.StandPoint
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class R50BackgroundShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { Backdrops.forget(); BattleShotKit.reset() }

    private fun battle(id: String) {
        val bg = BackdropCatalog.byId(id)!!
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.withSprites = false
            FakeGame.install(ctx)
            val sp = TestData.species(9999, "Wildling", types = BackdropCatalog.typesFor(id).take(1))
            val data = TestData.data(FakeGame.data.species.values.toList() + sp)
            val state = GameRepo.state!!
            state.battle = Battle(TestData.mon(data, sp.num, 25, uid = "r50-wild"), Tier.NORMAL)
            BattleShotKit.gameRepoSet("loadedData", data)
            BattleShotKit.gameRepoSet("current", Game(data, state))
            ctx.getSharedPreferences("ankigo_ui", 0).edit().putStringSet("disabledBackdrops",
                BackdropCatalog.ALL.map { it.id }.toSet() - id).commit()
            val dir = Sprites.dir(ctx)
            // Generated sprite shapes keep this preview independent of optional local Pokémon artwork.
            for (species in data.species.values) for (kind in listOf("front", "back", "static")) {
                FakeSprites.write(FakeSprites.blob(96, Ui.typeColor(species.types.first()), back = kind == "back"),
                    File(dir, "$kind/${species.num}.png"))
            }
            val mirror = System.getenv("ANKIGO_R50_ASSETS")?.let { File(it, "bg-${Backdrops.fileName(bg)}") }
            if (mirror != null && mirror.isFile) mirror.copyTo(Backdrops.file(dir, bg).apply { parentFile?.mkdirs() }, overwrite = true)
            Backdrops.installBundled(ctx, dir, bg)
            assumeTrue("Downloadable design art requires ANKIGO_R50_ASSETS", Backdrops.file(dir, bg).isFile)
            val pic = Backdrops.fetchBlocking(dir, bg, false) { _, _ -> null }!!
            assertEquals(bg.w, pic.bitmap.width); assertEquals(bg.h, pic.bitmap.height)
            Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
            val built = BattleShotKit.bound(ctx)
            val arena = built.arena!!
            val backdrop = ArenaBackdrop::class.java // the real battle's drawable, including theme tint and crop
            assertTrue(bg.crisp); assertFalse(bg.sharp)
            val field = arena.javaClass.getDeclaredField("backdrop").apply { isAccessible = true }
            val art = backdrop.cast(field.get(arena))!!
            art.reduceMotion = { true }
            paparazzi.snapshot(built.root, "bg-$id-${theme.name.lowercase()}", 1000)
            assertEquals(id, arena.backdropScenery)
        }
    }

    @Test fun windyHilltop() = battle("windy-hilltop")
    @Test fun lavaField() = battle("lava-field")
    @Test fun darkForest() = battle("dark-forest")
    @Test fun normalMeadow() = battle("normal-meadow")

    @Test fun nativePixelsStayUnmixedWhenEnlarged() {
        val ctx = Shots.files(paparazzi.context)
        val red = 0xFFFF0000.toInt(); val blue = 0xFF0000FF.toInt()
        val bmp = Bitmap.createBitmap(intArrayOf(red, blue, blue, red), 2, 2, Bitmap.Config.ARGB_8888)
        val bg = Backdrop("pixel-check", BackdropSource.OWN, "webp", 2, 2,
            StandPoint(.3f,.8f,.44f), StandPoint(.7f,.5f,.32f), true, false, sharp = false)
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, BackdropPic(bmp, bmp))
        val art = ArenaBackdrop(ctx).apply { reduceMotion = { true }; request(bg, false) }
        for (size in listOf(16, 21, 80)) {
            art.setBounds(0, 0, size, size)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            art.draw(Canvas(output))
            val pixels = IntArray(size * size)
            output.getPixels(pixels, 0, size, 0, 0, size, size)
            assertEquals("native nearest-neighbour at $size px", setOf(red, blue), pixels.toSet())
        }
    }

    @Test fun removedSavedChoicesFallBackAndNewRowsShowTheirTypes() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("ankigo_ui", 0).edit().putStringSet("disabledBackdrops", setOf("night-forest", "poison-marsh", "beach")).commit()
        assertFalse(BackdropPrefs.setEnabled(ctx, "night-forest", true))
        assertNotNull(BackdropCatalog.pickEnabled(listOf(com.husarp.ankigo.game.Type.POISON), 0, false, BackdropPrefs.disabled(ctx)))
        val page = BackdropPrefs.page(ctx)
        val texts = mutableListOf<String>()
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView) texts.add(v.text.toString())
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(page)
        for (name in listOf("Normal meadow", "Grass glade", "Poison swamp", "Flying ridge", "Violet plateau")) assertTrue(name, name in texts)
        assertTrue(texts.containsAll(listOf("Normal", "Grass", "Poison", "Flying", "Psychic")))
    }
}
