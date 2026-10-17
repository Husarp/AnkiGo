package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R7-07 battle backgrounds, plain vs picture vs night in the normal arena (PHONE, SPLIT), the study strip, picture-in-picture and
 * the floating battle window, light and dark. The pictures are GENERATED stand-ins (a fake scene with two light ground ovals where
 * the Showdown art has its floor spots); no Showdown file is in this test.
 * PNGs: r7-07-plain|picture|night-<size>-<theme>, r7-07-study-plain|picture|night-<size>-<theme>.
 */
class R707Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val big = listOf(Size.PHONE, Size.SPLIT)
    private val small = listOf(Size.PIP_169, Size.FLOATBATTLE)
    private val stripSizes = listOf(Size.SPLIT_TINY, Size.SPLIT_SHORT)

    enum class Mode { PLAIN, PICTURE, NIGHT }

    /** Puts the generated pictures into the sprite cache (after FakeGame.install, which empties it). */
    private fun pictures(ctx: Context, vararg names: String) {
        val dir = Sprites.dir(ctx)
        for (n in names.ifEmpty { Backdrops.NAMES.toTypedArray() }) FakeSprites.write(scene(n), Backdrops.file(dir, n))
    }

    private fun apply(ctx: Context, mode: Mode, panel: BattlePanelView) {
        when (mode) {
            Mode.PLAIN -> panel.arena.setPlainBackdrop()
            Mode.PICTURE -> panel.arena.setBackdrop(listOf(Type.GRASS), 1)   // forest
            Mode.NIGHT -> panel.arena.setBackdrop(listOf(Type.GHOST), 0)     // forest, night
        }
    }

    private fun full(ctx: Context, mode: Mode): View {
        pictures(ctx)
        if (mode == Mode.PLAIN) AppPrefs.setBackgroundPictures(ctx, false)
        val b = BattleShotKit.bound(ctx)
        apply(ctx, mode, b.panel!!)
        assertTrue(mode == Mode.PLAIN || b.arena!!.background != null)
        return b.root
    }

    private fun bare(ctx: Context, mode: Mode): View {
        pictures(ctx)
        if (mode == Mode.PLAIN) AppPrefs.setBackgroundPictures(ctx, false)
        val p = BattlePanelView(ctx)
        p.setCompact(true)
        p.renderAll()
        apply(ctx, mode, p)
        return p
    }

    /** The study strip (a split-screen strip above the card): the panel's study layout. */
    private fun study(ctx: Context, mode: Mode): View {
        pictures(ctx)
        if (mode == Mode.PLAIN) AppPrefs.setBackgroundPictures(ctx, false)
        val p = BattlePanelView(ctx)
        p.setStudyLayout(small = true, mini = false)
        p.renderAll()
        apply(ctx, mode, p)
        return p
    }

    @Test fun fullPlain() = paparazzi.shootWith("r7-07-plain", big) { ctx -> full(ctx, Mode.PLAIN) }
    @Test fun fullPicture() = paparazzi.shootWith("r7-07-picture", big) { ctx -> full(ctx, Mode.PICTURE) }
    @Test fun fullNight() = paparazzi.shootWith("r7-07-night", big) { ctx -> full(ctx, Mode.NIGHT) }

    @Test fun smallPlain() = paparazzi.shootWith("r7-07-plain", small) { ctx -> bare(ctx, Mode.PLAIN) }
    @Test fun smallPicture() = paparazzi.shootWith("r7-07-picture", small) { ctx -> bare(ctx, Mode.PICTURE) }
    @Test fun smallNight() = paparazzi.shootWith("r7-07-night", small) { ctx -> bare(ctx, Mode.NIGHT) }

    @Test fun studyPlain() = paparazzi.shootWith("r7-07-study-plain", stripSizes) { ctx -> study(ctx, Mode.PLAIN) }
    @Test fun studyPicture() = paparazzi.shootWith("r7-07-study-picture", stripSizes) { ctx -> study(ctx, Mode.PICTURE) }
    @Test fun studyNight() = paparazzi.shootWith("r7-07-study-night", stripSizes) { ctx -> study(ctx, Mode.NIGHT) }

    // ---- behaviour (checked in a render, so the posted loads run): ellipses, settings, same picture after a redraw, no download when off ----

    private fun arenaField(arena: com.husarp.ankigo.ui.ArenaView, name: String): View =
        com.husarp.ankigo.ui.ArenaView::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(arena) as View }

    private fun render(frame: android.widget.FrameLayout, tag: String) = paparazzi.snapshot(frame, "r7-07-check-$tag", 100)

    @Test fun picturesFollowTheSettingsAndTheEllipsesFollowThePicture() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        pictures(ctx)
        val p = BattlePanelView(ctx)
        val frame = android.widget.FrameLayout(ctx).apply { addView(p, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        val arena = p.arena
        val back = arena.background as com.husarp.ankigo.ui.ArenaBackdrop
        assertTrue("no picture before the first wild Pokemon", !back.showsPicture)
        assertTrue(arena.background is com.husarp.ankigo.ui.movefx.Joltable)

        p.renderAll(); render(frame, "on")
        assertTrue("picture shows", back.showsPicture)
        assertEquals(View.GONE, arenaField(arena, "platEnemy").visibility)
        assertEquals(View.GONE, arenaField(arena, "platPlayer").visibility)

        p.renderAll(); render(frame, "again")   // a redraw from the save keeps the same picture
        assertTrue(back.showsPicture)

        AppPrefs.setBackgroundPictures(ctx, false)
        p.renderAll()
        assertTrue("plain with the setting off", !back.showsPicture)
        assertEquals(View.VISIBLE, arenaField(arena, "platEnemy").visibility)
        assertEquals(View.VISIBLE, arenaField(arena, "platPlayer").visibility)

        AppPrefs.setBackgroundPictures(ctx, true)
        Sprites.setEnabled(ctx, false)
        p.renderAll()
        assertTrue("plain with downloads off", !back.showsPicture)

        Sprites.setEnabled(ctx, true)
        p.renderAll(); render(frame, "back")
        assertTrue(back.showsPicture)
        arena.setPlainBackdrop()
        assertTrue(!back.showsPicture)
        assertEquals(View.VISIBLE, arenaField(arena, "platEnemy").visibility)
    }

    @Test fun nothingIsDownloadedWhileOffAndAMissingPictureIsPlain() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)   // empty picture cache
        var asked = 0
        Backdrops.get = { _, _ -> asked++; null }
        val p = BattlePanelView(ctx)
        val frame = android.widget.FrameLayout(ctx).apply { addView(p, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        val back = p.arena.background as com.husarp.ankigo.ui.ArenaBackdrop

        Sprites.setEnabled(ctx, false)
        p.renderAll(); render(frame, "off1")
        AppPrefs.setBackgroundPictures(ctx, false); Sprites.setEnabled(ctx, true)
        p.renderAll(); render(frame, "off2")
        assertEquals("no request while downloads or the setting are off", 0, asked)

        AppPrefs.setBackgroundPictures(ctx, true)
        p.renderAll(); render(frame, "on")
        assertEquals("one request for the one picture", 1, asked)
        assertTrue("not found: plain", !back.showsPicture)
        assertEquals(View.VISIBLE, arenaField(p.arena, "platEnemy").visibility)
    }

    /** A fake arena scene 753 x 500: sky, a far band with fence posts, the ground with two lighter ovals where the floor spots are, 6 px dither. */
    private fun scene(name: String): FakeSprites.Img {
        val w = 753; val h = 500
        val img = FakeSprites.Img(w, h)
        val hash = name.hashCode()
        val base = intArrayOf(110 + (hash and 0x3f), 130 + ((hash shr 6) and 0x3f), 90 + ((hash shr 12) and 0x3f))
        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        fun inOval(x: Int, y: Int, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
            val dx = (x - cx * w) / (rx * w); val dy = (y - cy * h) / (ry * h)
            return dx * dx + dy * dy <= 1f
        }
        for (y in 0 until h) for (x in 0 until w) {
            val fy = y / h.toFloat()
            var r: Int; var g: Int; var b: Int
            when {
                fy < 0.2f -> { r = 160 + (fy * 40).toInt(); g = 205 + (fy * 20).toInt(); b = 240 }
                fy < 0.42f -> {
                    r = base[0] - 40; g = base[1] - 30; b = base[2] - 40
                    if (fy > 0.28f && (x / 22) % 6 == 0) { r = 120; g = 84; b = 56 } // fence posts
                }
                else -> { r = base[0]; g = base[1]; b = base[2] }
            }
            if (fy >= 0.42f) {
                val enemy = inOval(x, y, 0.60f, 0.47f, 0.19f, 0.065f)
                val player = inOval(x, y, 0.33f, 0.91f, 0.26f, 0.09f)
                if (enemy || player) { r += 38; g += 38; b += 30 }
                if (((x / 6) + (y / 6)) % 2 == 0) { r -= 8; g -= 8; b -= 8 }
            }
            img.px[y * w + x] = rgb(r, g, b)
        }
        return img
    }
}
