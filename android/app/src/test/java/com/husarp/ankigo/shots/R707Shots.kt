package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R7-07 battle backgrounds (R8-03: the 31-picture set, no night look), plain vs picture in the normal arena (PHONE, SPLIT), the study
 * strip, picture-in-picture and the floating battle window, light and dark. The pictures are GENERATED stand-ins ([BackdropScenes],
 * with two light ground ovals at the picture's stand points); no Showdown or Adam file is in this test.
 * PNGs: r7-07-plain|picture-<size>-<theme>, r7-07-study-plain|picture-<size>-<theme>.
 */
class R707Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val big = listOf(Size.PHONE, Size.SPLIT)
    private val small = listOf(Size.PIP_169, Size.FLOATBATTLE)
    private val stripSizes = listOf(Size.SPLIT_TINY, Size.SPLIT_SHORT)

    enum class Mode { PLAIN, PICTURE }

    /** The fight's wild Pokémon in FakeGame (uid "wild"): the picture its types and uid pick (the same one every time). */
    private fun wildPicture(): Backdrop {
        val st = GameRepo.state!!
        val w = st.battle!!.wild
        return BackdropCatalog.pick(GameRepo.data!!.species[w.species]?.types.orEmpty(), BackdropCatalog.seed(w.uid))
    }

    /** Puts the generated picture the fight gets into the sprite cache (after FakeGame.install, which empties it). */
    private fun pictures(ctx: Context) = BackdropScenes.write(Sprites.dir(ctx), wildPicture(), 0.5f)

    private fun apply(ctx: Context, mode: Mode, panel: BattlePanelView) {
        when (mode) {
            Mode.PLAIN -> panel.arena.setPlainBackdrop()
            Mode.PICTURE -> panel.renderAll()   // the panel picks the wild one's picture itself
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

    @Test fun smallPlain() = paparazzi.shootWith("r7-07-plain", small) { ctx -> bare(ctx, Mode.PLAIN) }
    @Test fun smallPicture() = paparazzi.shootWith("r7-07-picture", small) { ctx -> bare(ctx, Mode.PICTURE) }

    @Test fun studyPlain() = paparazzi.shootWith("r7-07-study-plain", stripSizes) { ctx -> study(ctx, Mode.PLAIN) }
    @Test fun studyPicture() = paparazzi.shootWith("r7-07-study-picture", stripSizes) { ctx -> study(ctx, Mode.PICTURE) }

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
}
