package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.StandPoint
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R9 C5 + C4 flow, driven through the real [EventPlayer], [BattlePanelView] and [ArenaView] (animations ON, so the events really queue).
 * Paparazzi's looper only moves inside a snapshot (see [advance]), so a test looks at the state right after an action (nothing has run
 * yet) and again after an [advance]. Battle pictures come from the FakeGame cache ([BackdropScenes], never the network); a held pool
 * ([holdLoads]) is a picture that loads "later".
 */
class R9NextFightFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = Backdrops.forget()

    private fun ctx(reduced: Boolean = false): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduced).commit()
        return c
    }

    private val held = ArrayList<Runnable>()
    private fun holdLoads() { Backdrops.pool = Executor { held += it } }
    private fun releaseLoads() { val r = held.toList(); held.clear(); r.forEach { it.run() } }

    private fun encounter(uid: String = "u2", species: Int = 1) = GameEvent.Encounter(species, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, uid)
    private fun defeatBatch(uid: String = "u2", species: Int = 1) = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(uid, species),
    )
    private fun catchBatch(uid: String = "u2") = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true),
        GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(uid),
    )
    private val answer = listOf<GameEvent>(GameEvent.Counted(3, 10, 5, 1, false), GameEvent.Charge(10, 1, 2))

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    /**
     * Runs what the looper has. Paparazzi's clock restarts in every snapshot; a timer posted before the first one is due at once, one
     * posted between two snapshots is due at (the clock's last stop + its delay). So each snapshot moves the stop on by a minute, and
     * three of them in a row let a chain of waits (a timer that posts the next timer) finish. The panel is put in the frame the first time.
     */
    private fun advance(c: Context, v: View? = null) {
        repeat(3) { i ->
            stop += 60_000
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r9-next-advance", stop)
        }
    }
    private var stop = 0L

    private fun priv(o: Any, name: String): Any? = getField(o, name)
    private fun enemyHolder(panel: BattlePanelView) = priv(panel.arena, "enemyHolder") as View
    private fun backdropOf(arena: ArenaView) = priv(arena, "backdrop") as ArenaBackdrop
    private fun queueKinds(p: EventPlayer): List<String> = (priv(p, "queue") as Collection<*>).map { item ->
        if (item!!.javaClass.simpleName == "Ev") priv(item, "e")!!.javaClass.simpleName else item.javaClass.simpleName
    }
    private fun parked(p: EventPlayer): Any? = priv(p, "parked")

    private fun pictureFor(species: Int, uid: String): Backdrop {
        val sp = GameRepo.data?.species?.get(species)
        return BackdropCatalog.pick(sp?.types.orEmpty(), BackdropCatalog.seed(uid), BackdropCatalog.isLegendary(sp))
    }
    private fun write(c: Context, bg: Backdrop) = BackdropScenes.write(Sprites.dir(c), bg, 0.1f)

    /** The next wild one: a uid (of the current wild one's species) whose picture differs from the current fight's, both pictures in the cache. */
    private class Next(val uid: String, val species: Int, val bg: Backdrop)

    private fun nextFight(c: Context): Next {
        val w = GameRepo.state!!.battle!!.wild
        val a = pictureFor(w.species, w.uid)
        write(c, a)
        for (i in 0 until 200) {
            val uid = "u$i"
            val b = pictureFor(w.species, uid)
            if (b.id != a.id) { write(c, b); return Next(uid, w.species, b) }
        }
        error("the catalog has one picture only")
    }

    /** The winning batch arrives: the saved game has the next wild one already (as it has when the real events come), the batch says so. */
    private fun arrive(player: EventPlayer, n: Next) {
        GameRepo.state!!.battle!!.wild.uid = n.uid
        player.enqueue(defeatBatch(n.uid, n.species))
    }

    /** The current fight's picture is on screen (applied by the looper). */
    private fun showCurrent(c: Context, panel: BattlePanelView) {
        panel.renderAll()
        advance(c, panel)
        assertNotNull("the first picture is applied", backdropOf(panel.arena).shown)
    }

    // ---------------------------------------------------------------- C5: when Continue shows

    @Test fun theHoldStartsWhenTheWinningBatchIsEnqueuedNotWhenTheTilesShow() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue("Continue is up at once", player.holding)
        assertFalse("the rewards are still playing", player.idle)
        assertFalse("the tiles are not up yet", panel.tilesShown)
        assertNull("the next fight is not parked yet", parked(player))
    }

    @Test fun aCatchHoldsAtOnceToo() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(catchBatch())
        assertTrue(player.holding); assertFalse(player.idle); assertFalse(panel.tilesShown)
    }

    @Test fun aSmallWindowHasNoContinueAndNoEarlyHold() {
        val panel = BattlePanelView(ctx())
        panel.setCompact(true)
        val player = EventPlayer(panel, continueOnPanel = false)
        player.enqueue(defeatBatch())
        assertFalse(player.holding)
        assertFalse(player.continueBattle())
    }

    @Test fun aFledWildOneAndAnEndWithoutANextFightKeepTodaysFlow() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(listOf(GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Fled("Wildling"), encounter()))
        assertFalse("a fled wild one is no result", player.holding)
        advance(c, panel)
        assertTrue(player.idle)
        player.clear()
        // a win with no Encounter behind it: no Continue while it plays, the tiles hold when they come (as before)
        player.enqueue(listOf(GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40), GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100)))
        assertFalse(player.holding)
        advance(c)
        assertTrue("the tiles came at the end", panel.tilesShown)
        assertTrue(player.holding)
    }

    @Test fun anAnswerThatArrivesMeanwhileEndsTheEarlyHold() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue(player.holding)
        player.enqueue(answer)   // answered in AnkiDroid meanwhile: nobody waits for a tap
        assertFalse(player.holding)
    }

    @Test fun theHoldIsToldOnceAndNeverFlickers() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        val told = ArrayList<Boolean>()
        panel.onHoldChanged = { told += it }
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue("posted, not inside the call", told.isEmpty())
        advance(c, panel)
        assertEquals("one notice for the whole fight result", listOf(true), told)
        player.continueBattle()
        advance(c)
        assertEquals(listOf(true, false), told)
    }

    @Test fun theTilesComeEarlyOnlyOnScreensWhoseContinueSitsUnderThem() {
        val c = ctx()
        val studyPanel = BattlePanelView(c)
        val study = EventPlayer(studyPanel, continueOnPanel = false)
        measure(studyPanel, 412, 869)
        study.enqueue(defeatBatch())
        assertEquals("the Study screen has its Continue in the answer row: the tiles stay where they were", listOf("XpGained", "Cash", "Tiles", "Encounter"), queueKinds(study))
        val cardPanel = BattlePanelView(c)
        val card = EventPlayer(cardPanel)   // the panel's own Continue
        measure(cardPanel, 412, 869)
        card.enqueue(defeatBatch())
        assertEquals("the tiles (and their Continue) come right behind the defeat", listOf("Tiles", "XpGained", "Cash", "Encounter"), queueKinds(card))
        advance(c, studyPanel)
        assertTrue(studyPanel.tilesShown && study.holding)
        assertTrue(cardPanel.tilesShown && card.holding)
    }

    @Test fun aCatchKeepsItsSwapLineAheadOfTheEarlyTiles() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        val swap = GameEvent.Duplicate("Wildling", 12, 10, com.husarp.ankigo.game.DuplicateResult.SWAPPED, releasedName = "Old")
        player.enqueue(listOf(GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true), swap, encounter()))
        // Counted played, Caught is playing: what is left is the swap line, the tiles, the next fight
        assertEquals(listOf("Duplicate", "Tiles", "Encounter"), queueKinds(player))
    }

    // ---------------------------------------------------------------- C5: Continue during the rewards

    @Test fun continueDuringTheRewardsEndsThemAndTheNextFightComes() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val m = GameRepo.state!!.main!!
        m.xp = 123   // the saved game: the XpGained event in the batch says 0 of 100, the save is what must show
        player.enqueue(defeatBatch())
        assertTrue(player.holding)
        assertEquals("the rewards are queued", listOf("XpGained", "Cash", "Tiles", "Encounter"), queueKinds(player))
        assertTrue("Continue did something", player.continueBattle())
        // right now, before the looper runs anything: the rewards are gone from the queue, the saved game is drawn
        assertEquals("only the next fight is left, and it is playing", emptyList<String>(), queueKinds(player))
        assertFalse(player.holding); assertFalse(panel.tilesShown)
        assertNull(parked(player))
        assertEquals("the screen shows the saved game", m.xp.toFloat() / Progression.xpToNext(GameRepo.data!!, m), panel.arena.xpShown, 0.01f)
        assertEquals(m.hp, panel.arena.playerHpShown)
        advance(c, panel)
        assertTrue("the next fight played to its end", player.idle)
        assertFalse("nothing is held any more", panel.holdsNextFight)
        assertFalse("the tiles never came", panel.tilesShown)
        assertEquals("the next wild one is on stage", View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun continueAfterACatchWorksToo() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(catchBatch())
        assertTrue(player.continueBattle())
        assertFalse(player.holding)
        advance(c, panel)
        assertTrue(player.idle)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun continueSkipsTheRewardsButTheActionsBehindTheEncounterStillRun() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val ran = ArrayList<String>()
        player.enqueue(defeatBatch())
        player.enqueueAction { ran += "after-batch" }   // behind the Encounter: the next fight's own action
        assertTrue(player.continueBattle())
        assertTrue("it waits for the entrance", ran.isEmpty())
        advance(c, panel)
        assertEquals("the queued action still ran", listOf("after-batch"), ran)
        assertTrue(player.idle)
    }

    @Test fun continueRunsTheActionsAheadOfTheEncounterAtOnce() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val ran = ArrayList<String>()
        player.enqueue(defeatBatch())
        // an action queued between the rewards and the next fight (a log line for a catch-up batch): put in by hand, the batch is one list
        val actClass = Class.forName("com.husarp.ankigo.ui.EventPlayer\$Item\$Act")
        val act = actClass.getDeclaredConstructor(Function0::class.java).apply { isAccessible = true }.newInstance({ ran += "ahead"; Unit })
        @Suppress("UNCHECKED_CAST") val queue = priv(player, "queue") as kotlin.collections.ArrayDeque<Any>
        queue.add(1, act)
        assertTrue(player.continueBattle())
        assertEquals("it ran at once, in the skip", listOf("ahead"), ran)
        advance(c, panel)
        assertEquals("and only once", listOf("ahead"), ran)
        assertTrue(player.idle)
    }

    @Test fun continueFromTheShownTilesStillWorks() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        advance(c, panel)   // everything played: the tiles are up, the next fight is parked
        assertTrue(panel.tilesShown); assertTrue(player.idle); assertNotNull(parked(player))
        assertTrue(player.continueBattle())
        assertFalse(panel.tilesShown); assertFalse(player.holding)
        advance(c)
        assertTrue(player.idle)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun continueWithNothingHeldDoesNothing() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel, continueOnPanel = false)
        assertFalse(player.continueBattle())
        player.enqueue(answer)
        assertFalse(player.continueBattle())
    }

    @Test fun leavingTheScreenDuringTheRewardsEndsThemAndDropsTheHold() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue(player.holding); assertFalse(player.idle)
        player.dropHold()   // what onStop does
        assertFalse(player.holding); assertFalse(panel.tilesShown)
        assertTrue("nothing is left to play to an invisible panel", player.idle)
    }

    private fun study(c: Context): StudyActivity {
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.setStudyLayout(false, false)
        measure(root, 412, 869)
        call(act, "showButtons")
        return act
    }

    @Test fun theStudyRowSwapsShowAnswerForContinueWhileTheRewardsPlay() {
        val c = ctx()
        val act = study(c)
        val events = getField(act, "events") as EventPlayer
        val buttons = getField(act, "buttons") as android.view.ViewGroup
        fun label() = buttons.getChildAt(0).contentDescription?.toString() ?: ""
        assertEquals("Show answer", label())
        events.enqueue(defeatBatch())
        assertFalse("the rewards are still playing", events.idle)
        assertTrue(events.holding)
        advance(c)   // the posted hold notice reaches the screen
        assertEquals("Continue, next battle", label())
        assertEquals("one button, the card is not touched", 1, buttons.childCount)
        assertEquals(false, getField(act, "flipped"))
    }

    @Test fun aTapOnShowAnswerDuringTheRewardsContinuesAndLeavesTheCard() {
        val c = ctx()
        val act = study(c)
        val events = getField(act, "events") as EventPlayer
        events.enqueue(defeatBatch())
        assertFalse(events.idle); assertTrue(events.holding)
        call(act, "flip")   // the same function the button, the keys and the card's tap reach
        assertFalse("it acted as Continue", events.holding)
        assertEquals("the card did not flip", false, getField(act, "flipped"))
        assertEquals("the rewards are gone from the queue", emptyList<String>(), queueKinds(events))
        advance(c)
        assertTrue(events.idle)
    }

    // ---------------------------------------------------------------- C4: the new picture first

    @Test fun theWildOneWaitsForItsPictureAndThenComesIn() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val n = nextFight(c)
        val first = backdropOf(panel.arena)
        showCurrent(c, panel)
        val current = first.shown!!
        holdLoads()
        arrive(player, n)
        assertEquals("the next fight's picture started loading when its batch arrived", 1, held.size)
        player.continueBattle()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility, "the wild side stays out while the picture loads")
        assertEquals("the old picture is still the one on screen", current.id, first.shown!!.id)
        assertTrue(panel.arena.backdropLoading)
        assertFalse("the player waits for it", player.idle)
        releaseLoads()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility, "loaded, but not applied yet: still out")
        advance(c)
        assertEquals("the new picture is on", n.bg.id, first.shown!!.id)
        assertEquals("the wild one came in after it", View.VISIBLE, enemyHolder(panel).visibility)
        assertTrue(player.idle)
    }

    @Test fun aPictureThatNeverComesDoesNotHoldTheFightUp() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val n = nextFight(c)
        val back = backdropOf(panel.arena)
        showCurrent(c, panel)
        val current = back.shown!!
        holdLoads()
        arrive(player, n)
        player.continueBattle()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility)
        advance(c)   // the cap runs out: the wild one comes in anyway
        assertEquals("the picture is still not there", current.id, back.shown!!.id)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
        assertTrue(player.idle)
        releaseLoads()   // it comes late: it is applied, nothing enters a second time
        advance(c)
        assertEquals(n.bg.id, back.shown!!.id)
        assertTrue(player.idle)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun theSamePictureOrNoPicturesMeansNoWait() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        val w = GameRepo.state!!.battle!!.wild
        write(c, pictureFor(w.species, w.uid))
        showCurrent(c, panel)
        holdLoads()
        // the next wild one has the same scene: it is on screen already
        player.enqueue(defeatBatch(uid = w.uid).dropLast(1) + encounter(w.uid, w.species))
        assertTrue(player.continueBattle())
        assertEquals("no wait", View.VISIBLE, enemyHolder(panel).visibility)
        advance(c)
        assertTrue(player.idle)
        // pictures off in the settings: plain background, nothing to wait for
        AppPrefs.setBackgroundPictures(c, false)
        arrive(player, nextFight(c))
        assertTrue(player.continueBattle())
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
        assertTrue("and no picture was asked for", held.isEmpty())
    }

    @Test fun aWildOneDrawnFromTheSaveWaitsForItsPictureToo() {
        val c = ctx()
        val panel = BattlePanelView(c)
        measure(panel, 412, 869)
        val w = GameRepo.state!!.battle!!.wild
        write(c, pictureFor(w.species, w.uid))
        holdLoads()
        panel.renderAll()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility, "a restart / resume / skipped batch: not at the old stand points")
        releaseLoads()
        advance(c, panel)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
        assertNotNull(backdropOf(panel.arena).shown)
    }

    @Test fun aWildOneDrawnFromTheSaveComesAnywayWhenThePictureNeverLoads() {
        val c = ctx()
        val panel = BattlePanelView(c)
        measure(panel, 412, 869)
        holdLoads()
        panel.renderAll()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility)
        advance(c, panel)   // the cap
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }

    @Test fun aHeldFightIsNotDrawnWhileThePictureLoads() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        measure(panel, 412, 869)
        holdLoads()
        panel.renderAll()
        advance(c, panel)   // the cap: the current wild one is on stage
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
        player.enqueue(defeatBatch())
        advance(c)   // the rewards are over: tiles up, the next wild one parked
        assertTrue(player.holding)
        panel.renderAll()
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility, "a held fight is not drawn")
        releaseLoads()
        advance(c)
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility, "a late picture does not bring a held wild one in")
    }

    @Test fun reducedMotionDrawsTheNewWildOneAfterItsPicture() {
        val c = ctx(reduced = true)
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        holdLoads()
        player.enqueue(listOf(encounter()))
        assertNotEquals(View.VISIBLE, enemyHolder(panel).visibility)
        advance(c, panel)
        assertEquals(View.VISIBLE, enemyHolder(panel).visibility)
    }


    private fun assertNotEquals(unexpected: Int, actual: Int, message: String = "") = assertTrue("$message (was $actual)", unexpected != actual)
}

/** R9 C4: the arena's background tells when the next picture is settled; the fade time the wild one waits for; the look-ahead load. */
class R9NextFightBackdropTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = Backdrops.forget()

    private fun setUp(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        return c
    }

    private val noGet: com.husarp.ankigo.data.HttpGet = { _, _ -> null }

    private fun bd(id: String) = Backdrop(id, BackdropSource.OWN, "png", 300, 200, StandPoint(.3f, .8f, .4f), StandPoint(.7f, .6f, .3f), crisp = true, dark = false)

    private fun preload(c: Context, b: Backdrop) {
        BackdropScenes.flat(b, 0xFF3366CC.toInt(), Sprites.dir(c))
        val pic = Backdrops.fetchBlocking(Sprites.dir(c), b, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), b.id, pic)
    }

    private var stop = 0L
    private fun flush(c: Context) { repeat(3) { stop += 60_000; paparazzi.snapshot(FrameLayout(c), "r9-next-backdrop", stop) } }

    @Test fun aPictureInMemoryIsSettledAtOnce() {
        val c = setUp()
        val a = bd("nf-a")
        preload(c, a)
        val back = ArenaBackdrop(c)
        var settled = 0
        back.onSettled = { settled++ }
        back.request(a, false)
        assertFalse(back.loading)
        assertEquals(1, settled)
        assertEquals(a.id, back.shown?.id)
        back.request(a, false)   // the same picture again: nothing to wait for, nothing to tell
        assertFalse(back.loading)
        assertEquals(1, settled)
    }

    @Test fun aPictureThatHasToLoadIsSettledWhenItIsApplied() {
        val c = setUp()
        val a = bd("nf-load")
        BackdropScenes.flat(a, 0xFF3366CC.toInt(), Sprites.dir(c))
        val back = ArenaBackdrop(c)
        var settled = 0
        var shownThen: String? = null
        back.onSettled = { settled++; shownThen = back.shown?.id }
        back.request(a, false)
        assertTrue(back.loading)
        assertEquals(0, settled)
        flush(c)
        assertFalse(back.loading)
        assertEquals(1, settled)
        assertEquals("applied before it was told", a.id, shownThen)
    }

    @Test fun aFailedLoadAndPlainAreSettledToo() {
        val c = setUp()
        val missing = bd("nf-missing")
        val back = ArenaBackdrop(c)
        var settled = 0
        back.onSettled = { settled++ }
        back.request(missing, false)   // not downloaded, downloads off: the load fails
        assertTrue(back.loading)
        flush(c)
        assertFalse(back.loading)
        assertFalse(back.showsPicture)
        assertEquals(1, settled)
        back.plain()
        assertEquals(2, settled)
    }

    @Test fun theFadeTheWildOneWaitsForIsTheRestOfTheCrossFade() {
        val c = setUp()
        val a = bd("nf-fa"); val b = bd("nf-fb")
        preload(c, a); preload(c, b)
        val back = ArenaBackdrop(c)
        val t = longArrayOf(1000)
        back.clock = { t[0] }
        back.reduceMotion = { true }
        back.request(a, false)
        assertEquals("a picture that appeared without a fade", 0L, back.fadeRemainingMs())
        back.reduceMotion = { false }
        back.request(b, false)
        assertEquals("the fade has not drawn a frame yet: all of it is left", 400L, back.fadeRemainingMs())
        val bmp = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        back.setBounds(0, 0, 300, 100)
        back.draw(Canvas(bmp))   // the first frame starts the clock
        t[0] = 1150
        assertEquals(250L, back.fadeRemainingMs())
        t[0] = 1400
        back.draw(Canvas(bmp))
        assertEquals(0L, back.fadeRemainingMs())
        back.reduceMotion = { true }
        assertEquals("reduced motion: no fade to wait for", 0L, back.fadeRemainingMs())
    }

    @Test fun prefetchLoadsWithoutShowingAndOnlyWhatIsMissing() {
        val c = setUp()
        val a = bd("nf-pa"); val b = bd("nf-pb")
        preload(c, a)
        BackdropScenes.flat(b, 0xFF33CC66.toInt(), Sprites.dir(c))
        val back = ArenaBackdrop(c)
        back.request(a, false)
        val held = ArrayList<Runnable>()
        Backdrops.pool = Executor { held += it }
        back.prefetch(a, false)   // on screen already
        assertTrue(held.isEmpty())
        back.prefetch(b, false)
        assertEquals(1, held.size)
        assertEquals("only loaded, not shown", a.id, back.shown?.id)
        held.forEach { it.run() }
        flush(c)
        assertNotNull(Backdrops.inMemory(b))
        assertEquals(a.id, back.shown?.id)
        held.clear()
        back.prefetch(b, false)   // in memory now
        assertTrue(held.isEmpty())
    }

    @Test fun theArenaRunsTheWaiterAfterThePictureIsAppliedAndAtOnceWhenNothingLoads() {
        val c = setUp()
        val w = GameRepo.state!!.battle!!.wild
        val sp = GameRepo.data!!.species[w.species]
        val bg = BackdropCatalog.pick(sp?.types.orEmpty(), BackdropCatalog.seed(w.uid), BackdropCatalog.isLegendary(sp))
        BackdropScenes.write(Sprites.dir(c), bg, 0.1f)
        val arena = ArenaView(c)
        val back = arena.background as ArenaBackdrop
        var now = 0
        arena.whenBackdropReady(1200) { now++ }
        assertEquals("nothing loads: at once", 1, now)
        var shownThen: String? = null
        var ran = 0
        arena.setBackdrop(sp?.types.orEmpty(), BackdropCatalog.seed(w.uid), BackdropCatalog.isLegendary(sp))
        assertTrue(arena.backdropLoading)
        arena.whenBackdropReady(1200) { ran++; shownThen = back.shown?.id }
        assertEquals(0, ran)
        flush(c)
        assertEquals(1, ran)
        assertEquals("the picture was applied when the waiter ran", bg.id, shownThen)
        assertFalse(arena.backdropLoading)
    }

    @Test fun aNewerWaiterReplacesAnOlderOne() {
        val c = setUp()
        val arena = ArenaView(c)
        val w = GameRepo.state!!.battle!!.wild
        val sp = GameRepo.data!!.species[w.species]
        arena.setBackdrop(sp?.types.orEmpty(), BackdropCatalog.seed(w.uid), BackdropCatalog.isLegendary(sp))
        var first = 0; var second = 0
        arena.whenBackdropReady(1200) { first++ }
        arena.whenBackdropReady(1200) { second++ }
        flush(c)
        assertEquals(0, first)
        assertEquals(1, second)
    }
}
