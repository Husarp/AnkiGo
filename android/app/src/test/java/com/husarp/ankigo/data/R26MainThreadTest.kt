package com.husarp.ankigo.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Looper
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.save.SaveStore
import com.husarp.ankigo.shots.Shots
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Layoutlib cannot enforce Android StrictMode disk policies. A deferred executor and a
 * preference-access tripwire enforce the equivalent boundary, plus a deliberately locked disk writer. */
class R26MainThreadTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val oldPool = Sprites.pool
    private val tasks = ArrayDeque<Runnable>()
    private val dir = Files.createTempDirectory("r26-main-thread").toFile()
    private val ctx by lazy {
        object : ContextWrapper(Shots.files(paparazzi.context)) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File {
                check(Looper.myLooper() != Looper.getMainLooper()) { "UI thread requested an Android storage directory" }
                return dir
            }
            override fun getFilesDir(): File = dir
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                check(Looper.myLooper() != Looper.getMainLooper()) { "UI thread touched storage preferences" }
                return super.getSharedPreferences(name, mode)
            }
        }
    }
    private fun defer() { Sprites.pool = Executor { tasks.addLast(it) } }
    private fun drain() {
        while (tasks.isNotEmpty()) {
            val task = tasks.removeFirst()
            val done = CountDownLatch(1)
            Thread { try { task.run() } finally { done.countDown() } }.start()
            assertTrue("worker completed", done.await(5, TimeUnit.SECONDS))
        }
    }
    private fun png(path: String) {
        val file = File(dir, "sprites/$path")
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff44aa88.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @After fun cleanup() { Sprites.pool = oldPool; dir.deleteRecursively() }

    @Test fun cacheMigrationWaitsForTheWorkerWithoutUiDirectoryAccess() {
        val flag = Sprites::class.java.getDeclaredField("upgraded").apply { isAccessible = true }
        val old = flag.getBoolean(Sprites)
        flag.setBoolean(Sprites, false)
        try {
            defer()
            val folder = File(dir, "sprites")
            assertFalse(File(folder, ".battle-v2").exists())
            Sprites.loadStatic(ctx, 99, false, false) {}
            assertFalse(File(folder, ".battle-v2").exists())
            drain()
            assertTrue(File(folder, ".battle-v2").isFile)
        } finally { flag.setBoolean(Sprites, old) }
    }

    @Test fun cachedStaticDecodeIsDeferredEvenWithoutDownloads() {
        png("static/25.png")
        defer()
        Sprites.loadStatic(ctx, 25, false, false) {}
        assertEquals(1, tasks.size)
        assertNull("UI lookup only reads memory", Sprites.staticBitmap(ctx, 25))
        drain()
        assertNotNull("local PNG was decoded without enabling downloads", Sprites.staticBitmap(ctx, 25))
        val image = ImageView(ctx)
        Ui.staticSprite(ctx, 25, false, image, download = false)
        paparazzi.snapshot(image.apply { layoutParams = android.view.ViewGroup.LayoutParams(96, 96) }, "r26d-cached-static")
    }

    @Test fun cachedItemDecodeIsDeferred() {
        png("item/poke-ball.png")
        defer()
        Sprites.fetchItem(ctx, "poke-ball") {}
        assertEquals(1, tasks.size)
        assertNull(Sprites.itemBitmap(ctx, "poke-ball"))
        drain()
        assertNotNull(Sprites.itemBitmap(ctx, "poke-ball"))
    }

    @Test fun downloadingAndLocalOnlyRequestsCannotSwallowEachOther() {
        png("static/25.png")
        defer()
        Sprites.loadStatic(ctx, 25, false, false) {}
        Sprites.fetchStatic(ctx, 25, false) {}
        assertEquals(2, tasks.size)
        drain()
    }

    @Test fun lifecycleSaveReturnsWhileStorageIsBlockedAndKeepsSnapshotOrder() {
        val held = GameRepo::class.java.getDeclaredField("held").apply { isAccessible = true }
        val oldState = held.get(GameRepo)
        val state = GameState(day = "2026-11-01")
        val writer = GameRepo::class.java.getDeclaredField("writer").apply { isAccessible = true }
            .get(GameRepo) as java.util.concurrent.ExecutorService
        try {
            held.set(GameRepo, state)
            val diskLock = SaveStore::class.java.getDeclaredField("LOCK").apply { isAccessible = true }.get(null)!!
            synchronized(diskLock) {
                val started = System.nanoTime()
                state.cash = 12
                GameRepo.saveInBackground(ctx)
                state.cash = 34
                GameRepo.saveInBackground(ctx)
                assertTrue("lifecycle saves must return without a disk timeout", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
                assertEquals(2L, state.seq)
                assertFalse(File(dir, "game/save.json").exists())
            }
            writer.submit {}.get(5, TimeUnit.SECONDS)
            val store = SaveStore(File(dir, "game"))
            assertEquals(34, store.load().state!!.cash)
            assertEquals(12, store.read(File(dir, "game/save.bak"))!!.cash)
        } finally { held.set(GameRepo, oldState) }
    }
}
