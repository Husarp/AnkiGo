package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BackdropCatalog
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** PLAN 334: switching off downloads preserves saved media, and never requests missing backgrounds. */
class R23FDownloadsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun context() = Shots.files(paparazzi.context).also {
        FakeGame.withSprites = false
        FakeGame.install(it)
        Sprites.setEnabled(it, false)
        AppPrefs.setBackgroundPictures(it, true)
    }

    private fun png(file: File) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun savedBackgroundShowsWithDownloadsOffButPicturesOffIsPlain() {
        val ctx = context()
        val bg = BackdropCatalog.pick(listOf(Type.GRASS), 334, false)
        png(Backdrops.file(Sprites.dir(ctx), bg))
        var asked = 0
        Backdrops.get = { _, _ -> asked++; null }
        val arena = ArenaView(ctx)
        arena.setBackdrop(listOf(Type.GRASS), 334)
        paparazzi.snapshot(arena, "r23f-saved-background", 100)
        assertNotNull(arena.backdropElement)
        assertEquals(0, asked)
        AppPrefs.setBackgroundPictures(ctx, false)
        arena.setBackdrop(listOf(Type.GRASS), 334)
        assertNull(arena.backdropElement)
    }

    @Test fun prefetchLoadsSavedBackgroundAndMissingBackgroundNeverDownloads() {
        val ctx = context()
        val types = listOf(Type.WATER)
        val bg = BackdropCatalog.pick(types, 0, false)
        png(Backdrops.file(Sprites.dir(ctx), bg))
        var asked = 0
        Backdrops.get = { _, _ -> asked++; null }
        val arena = ArenaView(ctx)
        AppPrefs.setBackgroundPictures(ctx, false)
        arena.prefetchBackdrop(types, 0)
        assertNull(Backdrops.inMemory(bg))
        AppPrefs.setBackgroundPictures(ctx, true)
        arena.prefetchBackdrop(types, 0)
        paparazzi.snapshot(arena, "r23f-prefetch", 100)
        assertNotNull(Backdrops.inMemory(bg))
        Backdrops.forget()
        Backdrops.file(Sprites.dir(ctx), bg).delete()
        arena.setBackdrop(types, 0)
        paparazzi.snapshot(arena, "r23f-missing-background", 100)
        assertNull(arena.backdropElement)
        assertEquals(0, asked)
    }

    @Test fun savedPokemonPicturesRemainAvailableAndMissingPicturesStayMissing() {
        val ctx = context()
        val sp = FakeGame.data.species[25]!!
        for (kind in SpriteKind.entries) {
            val folder = if (kind == SpriteKind.FRONT) "front" else "back"
            val file = File(Sprites.dir(ctx), "$folder/25.png")
            png(file)
            assertEquals(file, Sprites.fetchBlocking(ctx, sp, kind, false))
            file.delete()
            File(file.parentFile, "25.gif").delete()
            assertNull(Sprites.fetchBlocking(ctx, sp, kind, false))
        }
        val file = Sprites.staticFile(ctx, 25)
        png(file)
        val warm = java.util.concurrent.FutureTask { Sprites.staticBitmap(ctx, 25) }
        Thread(warm).start()
        assertNotNull("Saved image decodes on a worker with downloads disabled", warm.get())
        assertNotNull(Sprites.staticBitmap(ctx, 25))
        assertNull(Sprites.staticBitmap(ctx, 99999))
    }

    @Test fun savedCryIsAvailableWithDownloadsOffAndMissingCryIsAbsent() {
        val ctx = context()
        val store = GameSound.cryStoreOf(ctx)
        val file = store.file(25)
        file.parentFile!!.mkdirs()
        file.writeText("OggSsaved-cry")
        assertFalse(Sprites.enabled(ctx))
        assertEquals(file, store.cached(25)) // GameSound.playCry reads this cache without a download gate.
        assertNull(store.cached(99999))
    }
}
