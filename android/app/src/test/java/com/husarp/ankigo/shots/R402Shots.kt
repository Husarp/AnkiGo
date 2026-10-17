package com.husarp.ankigo.shots

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.Nav
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The merged home screen (GameActivity with the Study and Battle views) built for the shots and checks of R4-02. */
object HomeShotKit {
    /** The home screen on [ss] with the game loaded, the deck list filled, in [view]. */
    fun home(ss: Context, view: HomeView, decks: List<com.husarp.ankigo.Anki.Deck> = StudyDeckKit.decks, expanded: Set<Long> = setOf(1L)): BattleShotKit.Built {
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).putLong("lastStudied:2", System.currentTimeMillis() - 12 * 60_000L).apply()
        val b = BattleShotKit.bound(ss, view)
        fillDecks(b, decks, expanded)
        return layOut(b, ss)
    }

    /**
     * Lays the root out once at the window's size, as the device does before the first frame: the activity's layout listener
     * (and with it relayout() without a height) now knows how tall the window is, so a switch of view changes the bars.
     */
    fun layOut(b: BattleShotKit.Built, ctx: Context): BattleShotKit.Built {
        val d = ctx.resources.displayMetrics.density
        val w = (ctx.resources.configuration.screenWidthDp * d).toInt()
        val h = (ctx.resources.configuration.screenHeightDp * d).toInt()
        b.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        b.root.layout(0, 0, w, h)
        return b
    }

    /** No game, "Study without the game" chosen: what onStatus does for NEED_DATA / NEED_STARTER. */
    fun studyOnly(ss: Context, view: HomeView = HomeView.STUDY): BattleShotKit.Built {
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).putLong("lastStudied:2", System.currentTimeMillis() - 12 * 60_000L).apply()
        ss.getSharedPreferences("firstrun", 0).edit().putBoolean("studyOnly", true).apply()
        val b = BattleShotKit.build(ss)
        b.setField("homeView", view)
        b.call("onStatus", GameRepo.status)
        b.relayout(ss)
        fillDecks(b, StudyDeckKit.decks, setOf(1L))
        return layOut(b, ss)
    }

    private fun fillDecks(b: BattleShotKit.Built, decks: List<com.husarp.ankigo.Anki.Deck>, expanded: Set<Long>) {
        setField(b.activity.home, "decks", decks.sortedBy { it.name })
        setField(b.activity.home, "expanded", expanded)
        call(b.activity.home, "render")
    }
}

/*
 * R4-02 + R4-V1 (PLAN 88 + 108): Battle merged into Study; the battle card moved to the Pokémon tab. Checks on the real Views
 * (which parts show for each window and view, Back with and without an origin, onNewIntent, PiP, the panel only on screen when
 * the battle is) and the shots r402-*: home study (phone, small phone, tablet, split: decks only, bottom bar), home without a
 * game, the full-screen Battle view (no bottom bar), the setup card / save-error card in the Study view's status slot, PiP.
 */
class R402Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val phones = listOf(Size.PHONE, Size.SMALL)

    /** A render context for the phone window (the layout decisions read its height) with the fake game installed. */
    private fun ctx(scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        FakeGame.scenario = scenario
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(ctx)
        return ctx
    }

    private fun footerTools(b: BattleShotKit.Built) = getField(b.panel!!, "toolsWanted") as Boolean

    private fun vis(b: BattleShotKit.Built, field: String) = (b.field(field) as View).visibility

    // ---------------- shots

    @Test fun homeStudy() = paparazzi.shootWith("r402-home-study", phones + Size.TABLET) { ctx -> HomeShotKit.home(ShotCtx(ctx), HomeView.STUDY).root }

    /** The split-screen half: the battle only, as before the merge (the Study view's deck list does not fit). */
    @Test fun homeSplit() = paparazzi.shootWith("r402-home-split", listOf(Size.SPLIT, Size.LAND)) { ctx -> HomeShotKit.home(ShotCtx(ctx), HomeView.STUDY).root }

    @Test fun homeBattleView() = paparazzi.shootWith("r402-home-battle", listOf(Size.PHONE, Size.TABLET)) { ctx -> HomeShotKit.home(ShotCtx(ctx), HomeView.BATTLE).root }

    @Test fun homeNoGame() = paparazzi.shootWith("r402-home-nogame", listOf(Size.PHONE), scenario = FakeGame.Scenario.NO_DATA) { ctx ->
        HomeShotKit.studyOnly(ShotCtx(ctx)).root
    }

    /** First run not finished (canceled) and a save error: the message / card in the card slot of the Study view, in the content area of the Battle view. */
    @Test fun homeCardsInTheSlot() {
        paparazzi.shootWith("r402-home-setupcard", listOf(Size.PHONE), scenario = FakeGame.Scenario.NO_DATA) { ctx ->
            val ss = ShotCtx(ctx)
            ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).apply()
            val b = BattleShotKit.build(ss)
            b.setField("firstRunCanceled", true)
            b.call("onStatus", GameRepo.status)
            b.relayout(ss)
            setField(b.activity.home, "decks", StudyDeckKit.decks.sortedBy { it.name }); call(b.activity.home, "render")
            b.root
        }
        paparazzi.shootWith("r402-home-setupcard-battle", listOf(Size.PHONE), scenario = FakeGame.Scenario.NO_DATA) { ctx ->
            val ss = ShotCtx(ctx)
            val b = BattleShotKit.build(ss)
            b.setField("homeView", HomeView.BATTLE)
            b.setField("firstRunCanceled", true)
            b.call("onStatus", GameRepo.status)
            b.relayout(ss)
            b.root
        }
        paparazzi.shootWith("r402-home-loading", listOf(Size.PHONE)) { ctx ->
            val ss = ShotCtx(ctx)
            ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).apply()
            val b = BattleShotKit.build(ss)
            b.call("showMessage", "Loading…")
            b.relayout(ss)
            setField(b.activity.home, "decks", StudyDeckKit.decks.sortedBy { it.name }); call(b.activity.home, "render")
            b.root
        }
    }

    @Test fun homeSaveError() = paparazzi.shootWith("r402-home-saveerror", listOf(Size.PHONE)) { ctx ->
        val ss = ShotCtx(ctx)
        ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).apply()
        BattleShotKit.gameRepoSet("held", null)
        BattleShotKit.gameRepoSet("error", "The save file is damaged (unexpected end of data at line 1).")
        GameRepo::class.java.getDeclaredMethod("rebuild").also { it.isAccessible = true }.invoke(GameRepo)
        val b = BattleShotKit.build(ss)
        b.call("onStatus", GameRepo.status)
        b.relayout(ss)
        setField(b.activity.home, "decks", StudyDeckKit.decks.sortedBy { it.name }); call(b.activity.home, "render")
        b.root
    }

    @Test fun homePip() = paparazzi.shootWith("r402-home-pip", listOf(Size.PIP)) { ctx ->
        val b = HomeShotKit.home(ShotCtx(ctx), HomeView.STUDY)
        BattleShotKit.pip(b)
        b.root
    }

    // ---------------- which parts show

    @Test fun tallStudyShowsAppBarDeckListAndBarAndNoBattle() {
        val b = HomeShotKit.home(ctx(), HomeView.STUDY)
        assertEquals(View.VISIBLE, vis(b, "studyBar"))
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
        assertEquals(View.VISIBLE, vis(b, "nav"))
        assertEquals(View.GONE, vis(b, "topBar"))
        // no battle on the Study tab: its area is gone and the panel is not in it (the battle card lives in the Pokémon tab)
        assertEquals(View.GONE, vis(b, "content"))
        assertNotNull(b.panel)
        assertEquals("the panel is loaded but not on screen", null, b.panel!!.parent)
        assertFalse("nothing animates behind the deck list", b.field("sinkOn") as Boolean)
    }

    @Test fun tallBattleShowsTopBarAndNoBottomBarAndNoDeckList() {
        val b = HomeShotKit.home(ctx(), HomeView.BATTLE)
        assertEquals(View.VISIBLE, vis(b, "topBar"))
        assertEquals("the full-screen battle has no bottom bar", View.GONE, vis(b, "nav"))
        assertEquals(View.GONE, vis(b, "studyBar"))
        assertEquals(View.GONE, vis(b, "deckPane"))
        val lp = (b.field("content") as View).layoutParams as LinearLayout.LayoutParams
        assertEquals(0, lp.height); assertEquals(1f, lp.weight, 0f)
        assertFalse(footerTools(b))
    }

    @Test fun shortWindowIsTheBattleOnlyWhateverTheView() {
        for (v in HomeView.entries) {
            val b = HomeShotKit.home(ctx(), v)
            b.relayout(400f)
            for (f in listOf("studyBar", "topBar", "deckPane", "nav")) assertEquals("$v $f", View.GONE, vis(b, f))
            val lp = (b.field("content") as View).layoutParams as LinearLayout.LayoutParams
            assertEquals(1f, lp.weight, 0f)
            assertTrue(footerTools(b))
            assertTrue("$v the panel is in the battle's area", b.panel!!.parent === b.field("content"))
        }
    }

    @Test fun thePanelIsOnScreenOnlyWhenTheBattleIs() {
        val b = HomeShotKit.home(ctx(), HomeView.STUDY)
        assertEquals(null, b.panel!!.parent)
        b.activity.setHomeView(HomeView.BATTLE)
        assertTrue(b.panel!!.parent === b.field("content"))
        b.activity.setHomeView(HomeView.STUDY)
        assertEquals(null, b.panel!!.parent)
        b.relayout(400f) // a split-screen half shows the battle whatever the view
        assertTrue(b.panel!!.parent === b.field("content"))
    }

    @Test fun relayoutFollowsTheWindowHeight() {
        val b = HomeShotKit.home(ctx(), HomeView.STUDY)
        b.relayout(400f)   // split screen
        assertEquals(View.GONE, vis(b, "deckPane"))
        b.relayout(869f)   // back to a full window
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
        assertEquals(View.VISIBLE, vis(b, "studyBar"))
        assertEquals(View.GONE, vis(b, "content"))
    }

    @Test fun noGameWithStudyOnlyShowsTheSetupRowInPlaceOfTheCard() {
        val b = HomeShotKit.studyOnly(ctx(FakeGame.Scenario.NO_DATA), HomeView.BATTLE) // even when the battle was asked for
        assertEquals(View.VISIBLE, vis(b, "studyBar"))
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
        assertEquals(View.GONE, vis(b, "topBar"))
        val lp = (b.field("content") as View).layoutParams as LinearLayout.LayoutParams
        assertEquals(-2, lp.height)
        assertTrue("the row offers the first run", texts(b.field("content") as View).contains("Set up the game"))
    }

    // ---------------- taps, Back, Intents

    @Test fun theBattleViewHasNoCardArenaButton() {
        // in the full-screen battle our Pokémon opens its details (no tap on the arena that opens "the battle")
        val b = HomeShotKit.home(ctx(), HomeView.BATTLE)
        assertFalse(b.arena!!.isClickable)
    }

    @Test fun backFromTheBattleViewGoesToStudy() {
        val b = HomeShotKit.home(ctx(), HomeView.BATTLE)
        b.call("onBackPressed")
        assertEquals(HomeView.STUDY, b.field("homeView"))
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
    }

    @Test fun theTopBarsBackArrowIsBack() {
        val b = HomeShotKit.home(ctx(), HomeView.BATTLE)
        val back = findDescription(b.field("topBar") as View, "Back")!!
        back.performClick()
        assertEquals(HomeView.STUDY, b.field("homeView"))
    }

    @Test fun theTopBarHasPopOutAndTheMenuButStatsIsInTheMenu() {
        val b = HomeShotKit.home(ctx(), HomeView.BATTLE)
        val bar = b.field("topBar") as View
        assertNotNull(findDescription(bar, "Pop out: small battle window"))
        assertNotNull(findDescription(bar, "Menu"))
        assertEquals("Stats moved into ⋯", null, findDescription(bar, "Stats"))
        assertTrue("Battle" in texts(bar))
        val m = com.husarp.ankigo.ui.AppMenu(View(paparazzi.context))
        b.activity.fillMenu(m)
        assertTrue(m.describe().map { it.second }.containsAll(listOf("Stats", "Battle log", "Switch Pokémon")))
    }

    /** The battle opened from the Pokémon tab's card: Back returns there, and the home screen waits on Study. */
    @Test fun backFromTheBattleTheCardOpenedGoesToThePokemonTab() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE, from = Dest.TEAM))
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        assertEquals(Dest.TEAM, b.field("battleOrigin"))
        // the renderer's Activity cannot start another one (no instrumentation): what matters is the state before that
        runCatching { b.call("onBackPressed") }
        assertEquals("the home screen waits on Study", HomeView.STUDY, b.field("homeView"))
        assertEquals(null, b.field("battleOrigin"))
    }

    @Test fun theOriginBelongsToTheBattleThatWasOpened() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE, from = Dest.TEAM))
        b.call("onNewIntent", android.content.Intent()) // the "reviewDeck" hand-over: no view, nothing changes
        assertEquals(Dest.TEAM, b.field("battleOrigin"))
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE)) // opened again from the PiP window / a widget: no origin
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        assertEquals(null, b.field("battleOrigin"))
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE, from = Dest.TEAM))
        b.call("onNewIntent", Nav.intent(c, Dest.STUDY))
        assertEquals(null, b.field("battleOrigin"))
    }

    @Test fun thePokemonTabsPopOutWaitsForTheGameAndTheScreen() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        b.call("onNewIntent", Nav.popOutIntent(c))
        assertEquals("a pop-out asks for no view", HomeView.STUDY, b.field("homeView"))
        assertTrue(b.field("popOutPending") as Boolean)
    }

    private fun findDescription(v: View, d: String): View? {
        if (v.contentDescription?.toString() == d) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findDescription(v.getChildAt(i), d)?.let { return it }
        return null
    }

    @Test fun anIntentSwitchesTheView() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE))
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        b.call("onNewIntent", android.content.Intent()) // the "reviewDeck" hand-over and old PendingIntents carry no view
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        b.call("onNewIntent", Nav.intent(c, Dest.STUDY))
        assertEquals(HomeView.STUDY, b.field("homeView"))
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
    }

    @Test fun pictureInPictureIsTheBattleOnly() {
        val b = HomeShotKit.home(ctx(), HomeView.STUDY)
        b.call("onPictureInPictureModeChanged", true, Configuration())
        for (f in listOf("studyBar", "topBar", "deckPane", "nav")) assertEquals(f, View.GONE, vis(b, f))
        b.call("onPictureInPictureModeChanged", false, Configuration())
        b.relayout(ctx())
        assertEquals(HomeView.STUDY, b.field("homeView")) // PiP out of the Study view returns to the Study view
        assertEquals(View.VISIBLE, vis(b, "deckPane"))
    }

    @Test fun expandedFromPipDuringAHandOverShowsTheBattle() {
        val b = HomeShotKit.home(ctx(), HomeView.STUDY)
        val r = b.field("studyReturn") as com.husarp.ankigo.ui.StudyReturn
        r.requested(); r.ankiStarting()
        b.call("onPictureInPictureModeChanged", true, Configuration())
        b.call("onPictureInPictureModeChanged", false, Configuration())
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        b.relayout(ctx())
        assertEquals(View.VISIBLE, vis(b, "topBar"))
        b.call("onBackPressed")
        assertEquals("Back from it goes to Study", HomeView.STUDY, b.field("homeView"))
    }

    // ---------------- Nav

    @Test fun studyAndBattleIntentsTargetTheHomeScreen() {
        val c = ctx()
        for ((d, view) in listOf(Dest.STUDY to "study", Dest.BATTLE to "battle")) {
            val i = Nav.intent(c, d)
            assertEquals("$d", "com.husarp.ankigo.ui.GameActivity", i.component?.className)
            assertEquals("$d", view, i.getStringExtra(HomeView.EXTRA_VIEW))
            assertTrue("$d", i.flags and android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
            assertTrue("$d", i.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }
    }

    /** StudyDecksActivity forwards (old PendingIntents / shortcuts) to exactly this Intent, then finishes. */
    @Test fun theOldStudyTabForwardsToStudy() {
        val c = ctx()
        val i = Nav.intent(c, Dest.STUDY)
        assertEquals("com.husarp.ankigo.ui.GameActivity", i.component?.className)
        assertEquals("study", i.getStringExtra(HomeView.EXTRA_VIEW))
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is android.widget.TextView) out += v.text.toString()
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }
}
