package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * R9-C6: a changed look shows in every battle host. Cause: only a battle view that is told to draw again redraws the main's picture,
 * and three hosts were never told when the look changed somewhere else: the floating battle window stays up while the floating Team /
 * Pokedex card on top of it changes the look (its `show()` only re-attached the sink), and the Study screen waits in the back stack
 * (resume did not look at the game). The panel itself (`renderPlayer`) and the Team-tab card (`refresh`) were right.
 * The fix was `BattlePanelView.refreshLook()` (picture only, same main only, key-checked so nothing reloads when nothing changed), called by
 * `FloatBattleView.refreshPicture()` and `StudyActivity.refreshLook()` (onResume). R9-W5-A widened it to every change of the main
 * (`refreshMain()`, see R9StaleMainShots): the look is one part of it, the key check still keeps the picture from reloading.
 *
 * The fake pictures are plain blobs coloured by type; the look species (172) gets a colour of its own, read back from the SpriteView.
 * Main-looper work (the picture arrives by a post) runs during a snapshot, so each check snapshots once before it reads the picture.
 */
class R9LookInBattleShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private companion object {
        const val LOOK_COLOUR = 0xFFE000E0.toInt()
        const val LOOK_SPECIES = 172   // the pre-form of the main (25) in the made-up world; caught in FakeGame
        const val MAIN = "t1"
    }

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        // the look species gets its own colour (before anything decodes it)
        val dir = Sprites.dir(c)
        for (kind in listOf("front", "back", "static")) FakeSprites.write(FakeSprites.blob(96, LOOK_COLOUR, back = kind == "back"), File(dir, "$kind/$LOOK_SPECIES.png"))
        return c
    }

    /** The colour of the picture a SpriteView holds now (its middle; blobs have no eye there). */
    private fun shown(v: SpriteView): Int {
        val f = SpriteView::class.java.getDeclaredField("image").apply { isAccessible = true }
        val img = f.get(v) as SpriteImage? ?: return 0
        return img.frames[0].getPixel(48, 58)
    }

    /** Lets the posted work run (a snapshot drains the main looper) and returns what the sprite shows. */
    private fun settle(host: View, v: SpriteView, name: String): Int {
        val frame = host.parent as? View ?: FrameLayout(host.context).apply { addView(host, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, name, 1000)
        return shown(v)
    }

    private fun setLook(look: Int?) {
        assertEquals(true, GameRepo.game!!.setLook(MAIN, look).ok)
    }

    private fun panelOf(c: Context): BattlePanelView = BattlePanelView(c).also { it.renderAll() }

    @Test fun thePanelFollowsALookChangeOnlyWhenItIsToldToDrawAgain() {
        val c = ctx()
        val panel = panelOf(c)
        val own = settle(panel, panel.arena.playerSprite, "r9-lookinbattle-panel-0")
        assertNotEquals("the own look first", LOOK_COLOUR, own)
        setLook(LOOK_SPECIES)
        // a host nobody told keeps the old picture: this is the bug
        assertEquals(own, settle(panel, panel.arena.playerSprite, "r9-lookinbattle-panel-stale"))
        panel.refreshMain()
        assertEquals("the new look after refreshMain", LOOK_COLOUR, settle(panel, panel.arena.playerSprite, "r9-lookinbattle-panel-1"))
        // and back to its own
        setLook(null)
        panel.refreshMain()
        assertEquals(own, settle(panel, panel.arena.playerSprite, "r9-lookinbattle-panel-2"))
    }

    @Test fun refreshMainReloadsNothingWhenThePictureIsUnchanged() {
        val c = ctx()
        val panel = panelOf(c)
        settle(panel, panel.arena.playerSprite, "r9-lookinbattle-same-0")
        val token = panel.arena.playerSprite.getTag(R.id.ag_tag_sprite)
        repeat(3) { panel.refreshMain() }
        assertSame("no new load while nothing changed", token, panel.arena.playerSprite.getTag(R.id.ag_tag_sprite))
        setLook(LOOK_SPECIES)
        panel.refreshMain()
        val after = panel.arena.playerSprite.getTag(R.id.ag_tag_sprite)
        assertNotEquals("one load for the change", token, after)
        repeat(3) { panel.refreshMain() }
        assertSame("and none after it", after, panel.arena.playerSprite.getTag(R.id.ag_tag_sprite))
    }

    @Test fun anEvolvedOrSwitchedMainIsDrawnByRefreshMainToo() {
        val c = ctx()
        val panel = panelOf(c)
        val token = panel.arena.playerSprite.getTag(R.id.ag_tag_sprite)
        val st = GameRepo.state!!
        // R9-W5-A: refreshMain follows every change of the main (the hosts call it only while their event player is idle, so an
        // evolution whose event still waits in the queue is drawn by that event): an evolved main ...
        st.mon(MAIN)!!.species = 26
        panel.refreshMain()
        val evolved = panel.arena.playerSprite.getTag(R.id.ag_tag_sprite)
        assertNotEquals(token, evolved)
        // ... and a switched main
        st.mon(MAIN)!!.species = 25
        st.team.remove("t2"); st.team.add(0, "t2")
        panel.refreshMain()
        assertEquals("t2", panel.arena.playerUid)
        assertNotEquals(evolved, panel.arena.playerSprite.getTag(R.id.ag_tag_sprite))
    }

    @Test fun theFloatingBattleWindowFollowsALookChangeMadeOnTopOfIt() {
        val c = ctx()
        val self = object : android.content.ContextWrapper(c) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        val bind = FloatBattleView::class.java.getDeclaredMethod("bind").apply { isAccessible = true }
        bind.invoke(v)
        val panel = FloatBattleView::class.java.getDeclaredField("panel").apply { isAccessible = true }.get(v) as BattlePanelView
        panel.renderAll()
        val window = FloatBattleView::class.java.getDeclaredField("window").apply { isAccessible = true }.get(v)
        val root = window.javaClass.getDeclaredField("root").apply { isAccessible = true }.get(window) as View
        val own = settle(root, panel.arena.playerSprite, "r9-lookinbattle-float-0")
        setLook(LOOK_SPECIES)
        v.refreshPicture()   // what show() does for a window that is already up
        assertEquals(LOOK_COLOUR, settle(root, panel.arena.playerSprite, "r9-lookinbattle-float-1"))
        assertNotEquals(own, LOOK_COLOUR)
    }

    @Test fun theStudyScreenFollowsALookChangeWhenItComesBack() {
        val c = ctx()
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll()
        val own = settle(root, panel.arena.playerSprite, "r9-lookinbattle-study-0")
        setLook(LOOK_SPECIES)
        assertEquals("nothing changes behind its back", own, settle(root, panel.arena.playerSprite, "r9-lookinbattle-study-stale"))
        act.refreshMain()   // what onResume calls
        assertEquals(LOOK_COLOUR, settle(root, panel.arena.playerSprite, "r9-lookinbattle-study-1"))
    }

    private class Live : BattleCard.Live {
        override val paused = false
        override fun hold(ctx: Context, owner: Any) {}
        override fun release(ctx: Context, owner: Any) {}
        override fun addSink(s: LiveSession.Sink) {}
        override fun removeSink(s: LiveSession.Sink) {}
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
    }

    private class Host : BattleCard.Host {
        override fun expand() {}
        override fun popOut() {}
    }

    @Test fun theTeamTabCardFollowsALookChangeThroughItsRefresh() {
        val c = ctx()
        val card = BattleCard(c, Host(), Live()) { null }
        card.start()
        card.refresh()
        val panel = BattleCard::class.java.getDeclaredField("panel").apply { isAccessible = true }.get(card) as BattlePanelView
        val own = settle(card.view, panel.arena.playerSprite, "r9-lookinbattle-card-0")
        setLook(LOOK_SPECIES)
        card.refresh()   // TeamPage.render does this on every game change
        assertEquals(LOOK_COLOUR, settle(card.view, panel.arena.playerSprite, "r9-lookinbattle-card-1"))
        assertNotEquals(own, LOOK_COLOUR)
        card.stop()
    }
}
