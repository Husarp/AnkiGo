package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ItemUse
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * R9-W5-A: the new Main's picture shows at once in every battle host, whichever way the main changed.
 * Each team member of FakeGame gets its own picture colour (read back from the SpriteView), so "the old one is still there" is a
 * plain colour comparison. Main-looper work (the picture arrives by a post) runs during a snapshot, so each read snapshots first.
 */
class R9StaleMainShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun freshNotifier() = forgetQueuedNotify()
    @After fun cleanUp() { BattleShotKit.reset(); forgetQueuedNotify() }

    /**
     * GameRepo coalesces its listener calls behind a "queued" flag; a test that ends before the posted call ran (the looper of the next
     * test is a new one) leaves it set for the rest of the JVM, so no later change would notify anybody. Cleared around each test here.
     */
    private fun forgetQueuedNotify() {
        GameRepo::class.java.getDeclaredField("notifyQueued").apply { isAccessible = true }.setBoolean(GameRepo, false)
    }

    /** colour of the picture of each FakeGame team species (t1..t6 = 25, 2, 3, 92, 130, 133) and a few box ones */
    private val colours = mapOf(25 to 0xFFE00000.toInt(), 2 to 0xFF00E000.toInt(), 3 to 0xFF0000E0.toInt(), 92 to 0xFFE0E000.toInt(),
        130 to 0xFFE000E0.toInt(), 133 to 0xFF00E0E0.toInt(), 16 to 0xFFE08000.toInt(), 19 to 0xFF80E000.toInt(), 54 to 0xFF8000E0.toInt(), 26 to 0xFF804000.toInt())

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        val dir = Sprites.dir(c)
        for ((num, col) in colours) for (kind in listOf("front", "back", "static"))
            FakeSprites.write(FakeSprites.blob(96, col, back = kind == "back"), File(dir, "$kind/$num.png"))
        return c
    }

    private fun shown(v: SpriteView): Int {
        val f = SpriteView::class.java.getDeclaredField("image").apply { isAccessible = true }
        val img = f.get(v) as SpriteImage? ?: return 0
        return img.frames[0].getPixel(48, 58)
    }

    private fun settle(host: View, v: SpriteView, name: String): Int {
        val frame = host.parent as? View ?: FrameLayout(host.context).apply { addView(host, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, name, 1000)
        return shown(v)
    }

    /** A frame that runs [change] once it is on screen (a panel is only attached to the saved game's listeners while it is in a window). */
    private class Trigger(ctx: Context, val change: () -> Unit) : FrameLayout(ctx) {
        override fun onAttachedToWindow() { super.onAttachedToWindow(); post(change) }
    }

    /** Snapshots [host] in a window and runs [change] right after it is attached; returns what [v] shows afterwards. */
    private fun settleAfter(host: View, v: SpriteView, name: String, change: () -> Unit): Int {
        (host.parent as? android.view.ViewGroup)?.removeView(host)
        val frame = Trigger(host.context, change).apply { addView(host, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, name, 1000)
        return shown(v)
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

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity? get() = activity
        override fun closePage() {}
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    /** The real Pokémon tab (Team section) with its battle card, drawn. */
    private class Tab(val page: TeamPage, val screen: View) {
        val card: BattleCard get() = getField(page, "battleCard") as BattleCard
        val panel: BattlePanelView get() = getField(card, "panel") as BattlePanelView
    }

    private fun tab(c: Context): Tab {
        val p = TeamPage(c, ActivityHost(attach(Activity(), c)))
        p.cardSession = Live()
        p.preset(TeamSection.TEAM)
        val screen = pageScreen(c, p)
        call(p, "render")
        return Tab(p, screen)
    }

    // ---------------------------------------------------------------- the Pokémon tab's card

    @Test fun theTabsCardShowsTheNewMainAtOnceAfterSendToBox() {
        val c = ctx()
        val t = tab(c)
        val before = settle(t.screen, t.panel.arena.playerSprite, "r9-stalemain-tab-0")
        assertEquals("the first main's picture", colours[25], before)
        val r = t.page.act { it.toBox("t1") }
        assertEquals(true, r.ok)
        assertEquals("t2", GameRepo.state!!.main!!.uid)
        val after = settle(t.screen, t.panel.arena.playerSprite, "r9-stalemain-tab-1")
        assertEquals("the new main's picture (species 2)", colours[2], after)
        assertNotEquals(before, after)
        assertEquals("and its card", "t2", t.panel.arena.playerUid)
    }

    // ---------------------------------------------------------------- a panel nobody told: it follows the saved game by itself

    /** A battle panel on its own (any host: Study, Battle tab in split screen, PiP, floating window), drawn once. */
    private fun panelOf(c: Context): BattlePanelView = BattlePanelView(c).also { it.renderAll() }

    /** Every way the main changes outside the panel's own event stream (the Pokémon tab, the Pokédex, items, an import). */
    private val changes: List<Pair<String, (Game) -> Unit>> = listOf(
        "send to box" to { g -> assertTrue(g.toBox("t1").ok) },
        "make main from the team" to { g -> assertTrue(g.setMain("t3").ok) },
        "make main from the box (the team is full: the old main goes to the box)" to { g -> assertTrue(g.swapIntoTeam("b1", "t1").ok) },
        "make main from the box while the team is full (the last member goes to the box)" to { g -> assertTrue(g.setMain("b2").ok) },
        "move the second member up" to { g -> assertTrue(g.moveInTeam("t2", -1).ok) },
        "release the main" to { g -> assertTrue(g.release("t1").ok) },
        "next team member as main" to { g -> assertTrue(g.cycleMain(cardShowing = false).ok) },
        "evolve the main with a stone" to { g -> assertTrue(ItemUse.use(g.data, g.state, "thunder-stone", "t1", g.rng, g.clock()).ok) },
    )

    @Test fun anIdlePanelDrawsTheNewMainWheneverTheGameChangedElsewhere() {
        for ((what, change) in changes) {
            val c = ctx()
            val panel = panelOf(c)
            val own = settle(panel, panel.arena.playerSprite, "r9-stalemain-panel-0")
            val oldUid = panel.arena.playerUid
            // the panel is told nothing: it is on screen, listens to the saved game, and its host is idle
            val after = settleAfter(panel, panel.arena.playerSprite, "r9-stalemain-panel-1") {
                assertTrue(what, GameRepo.run(c) { g -> change(g); ActionResult(true, "") }.ok)
            }
            val main = GameRepo.state!!.main!!
            assertEquals("$what: the card", main.uid, panel.arena.playerUid)
            assertEquals("$what: the picture of ${main.uid} (species ${main.species})", colours[main.species], after)
            if (what != "evolve the main with a stone") assertNotEquals("$what: another Pokémon", oldUid, panel.arena.playerUid)
            assertNotEquals("$what: not the old picture", own, after)
        }
    }

    @Test fun aPanelWhoseHostIsBusyKeepsTheEventsInChargeOfWhatItDraws() {
        val c = ctx()
        val panel = panelOf(c)
        val own = settle(panel, panel.arena.playerSprite, "r9-stalemain-busy-0")
        panel.hostIdle = { false }   // an animation still plays: its events draw the new main, not the saved game
        assertEquals("busy host: untouched", own, settleAfter(panel, panel.arena.playerSprite, "r9-stalemain-busy-1") { GameRepo.run(c) { g -> g.toBox("t1") } })
        panel.hostIdle = { true }
        panel.refreshMain()   // what the host does when it is idle again
        assertEquals(colours[2], settle(panel, panel.arena.playerSprite, "r9-stalemain-busy-2"))
    }

    @Test fun aRenameOrAHealOnTheMainShowsOnAnIdlePanelToo() {
        val c = ctx()
        val panel = panelOf(c)
        settle(panel, panel.arena.playerSprite, "r9-stalemain-name-0")
        settleAfter(panel, panel.arena.playerSprite, "r9-stalemain-name-1") { GameRepo.run(c) { g -> g.rename("t1", "Zippy"); ActionResult(true, "") } }
        val name = getField(panel.arena, "pName") as android.widget.TextView
        assertEquals("Zippy", name.text.toString())
    }

    @Test fun nothingReloadsWhileTheMainIsUnchanged() {
        val c = ctx()
        val panel = panelOf(c)
        settle(panel, panel.arena.playerSprite, "r9-stalemain-same-0")
        val token = panel.arena.playerSprite.getTag(R.id.ag_tag_sprite)
        repeat(3) { GameRepo.run(c) { g -> g.setFavourite("b3", true); ActionResult(true, "") } }
        settle(panel, panel.arena.playerSprite, "r9-stalemain-same-1")
        panel.refreshMain()
        assertSame("no new load while nothing about the main changed", token, panel.arena.playerSprite.getTag(R.id.ag_tag_sprite))
    }

    // ---------------------------------------------------------------- the hosts

    @Test fun theFloatingBattleWindowShowsTheNewMainWhenItIsShownAgain() {
        val c = ctx()
        val self = object : android.content.ContextWrapper(c) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        call(v, "bind")
        val panel = getField(v, "panel") as BattlePanelView
        panel.renderAll()
        val window = getField(v, "window")!!
        val root = getField(window, "root") as View
        val own = settle(root, panel.arena.playerSprite, "r9-stalemain-float-0")
        GameRepo.run(c) { g -> g.toBox("t1") }
        v.refreshPicture()   // what show() does for a window that is already up (the service calls it on every game change)
        val after = settle(root, panel.arena.playerSprite, "r9-stalemain-float-1")
        assertEquals(colours[2], after)
        assertNotEquals(own, after)
        assertEquals("t2", panel.arena.playerUid)
    }

    @Test fun theStudyScreenShowsTheNewMainWhenItComesBack() {
        val c = ctx()
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll()
        val own = settle(root, panel.arena.playerSprite, "r9-stalemain-study-0")
        GameRepo.run(c) { g -> g.toBox("t1") }   // the Pokémon tab opened from its menu
        act.refreshMain()   // what onResume calls
        val after = settle(root, panel.arena.playerSprite, "r9-stalemain-study-1")
        assertEquals(colours[2], after)
        assertNotEquals(own, after)
        assertEquals("t2", panel.arena.playerUid)
    }

    @Test fun aBusyFloatingWindowAndABusyStudyScreenLeaveTheNewMainToTheirEvents() {
        val c = ctx()
        val self = object : android.content.ContextWrapper(c) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        call(v, "bind")
        val panel = getField(v, "panel") as BattlePanelView
        panel.renderAll()
        val root = getField(getField(v, "window")!!, "root") as View
        val own = settle(root, panel.arena.playerSprite, "r9-stalemain-floatbusy-0")
        (getField(v, "player") as com.husarp.ankigo.ui.EventPlayer).enqueue(listOf(GameEvent.Info("one"), GameEvent.Info("two")))
        GameRepo.run(c) { g -> g.toBox("t1") }
        v.refreshPicture()
        assertEquals("floating window, busy: untouched", own, settle(root, panel.arena.playerSprite, "r9-stalemain-floatbusy-1"))

        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        val sroot = act.buildContent()
        call(act, "layout")
        val spanel = getField(act, "panel") as BattlePanelView
        GameRepo.run(c) { g -> g.cycleMain(cardShowing = false); ActionResult(true, "") }   // the game moved on again: t3 is main now
        spanel.renderAll()
        val sown = settle(sroot, spanel.arena.playerSprite, "r9-stalemain-studybusy-0")
        (getField(act, "events") as com.husarp.ankigo.ui.EventPlayer).enqueue(listOf(GameEvent.Info("one"), GameEvent.Info("two")))
        GameRepo.run(c) { g -> g.cycleMain(cardShowing = false); ActionResult(true, "") }
        act.refreshMain()
        assertEquals("study screen, busy: untouched", sown, settle(sroot, spanel.arena.playerSprite, "r9-stalemain-studybusy-1"))
    }

    // ---------------------------------------------------------------- the widgets

    @Test fun theWidgetsAreToldAtOnceWhenTheTeamOrTheMainsLookChangesAndNotForAnswers() {
        val c = ctx()
        val told = ArrayList<Context>()
        val old = GameRepo.widgetRefresh
        GameRepo.widgetRefresh = { told += it }
        try {
            GameRepo.run(c) { ActionResult(true, "") }   // the baseline (whatever an earlier test left)
            told.clear()
            fun step(what: String, expect: Int, f: (Game) -> Unit) {
                told.clear()
                GameRepo.run(c) { g -> f(g); ActionResult(true, "") }
                assertEquals(what, expect, told.size)
            }
            step("an answer: HP, XP, cash", 0) { g -> g.state.mon("t1")!!.hp -= 1; g.state.mon("t1")!!.xp += 9; g.state.cash += 5 }
            step("a favourite in the box", 0) { g -> g.setFavourite("b3", true) }
            step("an evolution stone on the main", 1) { g -> ItemUse.use(g.data, g.state, "thunder-stone", "t1", g.rng, g.clock()) }
            step("send the main to the box", 1) { g -> g.toBox("t1") }
            step("make another one main", 1) { g -> g.setMain("t4") }
            step("move in the team", 1) { g -> g.moveInTeam("t5", -1) }
            step("a new look", 1) { g -> assertTrue(g.setLook("t2", 1).ok) }
            step("a nickname", 1) { g -> g.rename("t3", "Zippy") }
            step("release a team member", 1) { g -> g.release("t5") }
            step("the same again changes nothing", 0) { g -> assertTrue(g.setLook("t2", 1).ok) }
        } finally {
            GameRepo.widgetRefresh = old
        }
    }

    /** R9-W5-FIX: a Pokémon a widget shows by pick (a boxed one) refreshes the widgets when its picture changes; its HP does not. */
    @Test fun theWidgetsAlsoFollowAPickedBoxedPokemon() {
        val c = ctx()
        val told = ArrayList<Context>()
        val old = GameRepo.widgetRefresh
        val oldPicked = GameRepo.widgetPicked
        GameRepo.widgetRefresh = { told += it }
        GameRepo.widgetPicked = { setOf("b3", "gone-uid") }   // a boxed one and one that no longer exists
        try {
            GameRepo.run(c) { ActionResult(true, "") }   // the baseline with the picks
            told.clear()
            fun step(what: String, expect: Int, f: (Game) -> Unit) {
                told.clear()
                GameRepo.run(c) { g -> f(g); ActionResult(true, "") }
                assertEquals(what, expect, told.size)
            }
            step("an answer: HP, XP of the picked one", 0) { g -> g.state.mon("b3")!!.hp -= 1; g.state.mon("b3")!!.xp += 9 }
            step("a nickname of the picked boxed one", 1) { g -> g.rename("b3", "Zippy") }
            step("the same again changes nothing", 0) { g -> g.rename("b3", "Zippy") }
            step("release the picked boxed one", 1) { g -> g.release("b3") }
        } finally {
            GameRepo.widgetRefresh = old
            GameRepo.widgetPicked = oldPicked
        }
    }
}
