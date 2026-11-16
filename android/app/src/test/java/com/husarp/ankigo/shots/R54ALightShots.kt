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

class R54ALightShots {
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
            paparazzi.snapshot(built.root, "r54a-$id-${theme.name.lowercase()}", 1000)
            assertEquals(id, arena.backdropScenery)
            assertEquals(com.husarp.ankigo.ui.BackdropLight.forScene(id), art.lightDim, .00001f)
        }
    }

    @Test fun lightingTracksTheActualBackdropFadeAndInterruptedFade() {
        val ctx = Shots.files(paparazzi.context)
        val dark = BackdropCatalog.byId("dark-forest")!!
        val bright = BackdropCatalog.byId("normal-meadow")!!
        val pic = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        for (bg in listOf(dark, bright)) Backdrops.finish(Backdrops.currentGeneration(), bg.id, BackdropPic(pic, pic))
        var now = 0L
        val art = ArenaBackdrop(ctx).apply { reduceMotion = { false }; clock = { now }; setBounds(0, 0, 200, 120) }
        val canvas = Canvas(Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888))
        art.request(dark, false)
        art.draw(canvas)
        assertEquals(0f, art.lightDim, 0f)
        now = 200; art.draw(canvas)
        val halfway = art.lightDim
        assertEquals(com.husarp.ankigo.ui.BackdropLight.forScene(dark.id) * 128f / 255f, halfway, .00001f)
        art.request(bright, false)
        art.draw(canvas)
        assertEquals(halfway, art.lightDim, .00001f)
        now = 400; art.draw(canvas)
        assertTrue(art.lightDim in 0f..halfway)
        now = 600; art.draw(canvas)
        assertEquals(0f, art.lightDim, 0f)
        art.reduceMotion = { true }; art.request(dark, false)
        assertTrue(art.lightDim > 0f)
        art.plain()
        assertEquals(0f, art.lightDim, 0f)
    }

    @Test fun darkBackground() = battle("dark-forest")
    @Test fun brightBackground() = battle("normal-meadow")
}
