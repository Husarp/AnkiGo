package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.Nav
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StudyReturn
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/*
 * R4 fix batch, FIX-B (UI): the checks that need an Android Context (rendered by Paparazzi) and the shots of what changed.
 * B3 explicit destination beats the "Study in AnkiDroid" return; B4 studying without the game in a short window;
 * B5 the battle card in a short window; B6 the card's Grant button; B7 a held result meets a small window;
 * N1 evolution line in the 340 dp card (see also R4V3Shots r4v3-info-branch). Shots: r4fixb-*.
 */
class R4FixBShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun cleanUp() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot(); BattleShotKit.reset() }

    private fun ctx(size: Size = Size.PHONE, scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
        FakeGame.scenario = scenario
        val c = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(c)
        return c
    }

    private fun vis(b: BattleShotKit.Built, field: String) = (b.field(field) as View).visibility

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun findText(v: View, anyVisibility: Boolean = false, pred: (String) -> Boolean): TextView? {
        if (!anyVisibility && v.visibility == View.GONE) return null
        if (v is TextView && pred(v.text.toString())) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), anyVisibility, pred)?.let { return it }
        return null
    }

    // ---------------- B3: an explicit destination beats the "Study in AnkiDroid" return

    @Test fun aWidgetBattleStartWinsOverAPendingStudyReturn() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        val r = b.field("studyReturn") as StudyReturn
        r.requested(); r.ankiStarting()                              // "Study in AnkiDroid", no PiP: coming back would show Study
        b.call("onNewIntent", Nav.intent(c, Dest.BATTLE))            // a widget / notification / the floating window asks for the battle
        assertEquals(HomeView.BATTLE, b.field("homeView"))
        assertFalse("onResume must not switch back to Study", r.resumed(inPip = false))
        assertEquals(HomeView.BATTLE, b.field("homeView"))
    }

    @Test fun anIntentWithoutAViewKeepsThePendingStudyReturn() {
        val c = ctx()
        val b = HomeShotKit.home(c, HomeView.STUDY)
        val r = b.field("studyReturn") as StudyReturn
        r.requested(); r.ankiStarting()
        b.call("onNewIntent", Intent())                              // the "reviewDeck" hand-over and old PendingIntents
        assertTrue("the way back to Study is still pending", r.resumed(inPip = false))
    }

    // ---------------- B4: studying without the game in a short window

    private fun studyOnlyShort(size: Size): BattleShotKit.Built {
        val c = ctx(size, FakeGame.Scenario.NO_DATA)
        return HomeShotKit.studyOnly(c)
    }

    @Test fun studyOnlyInASplitHalfKeepsTheDeckListAndTheSetupRow() {
        for (size in listOf(Size.SPLIT, Size.SPLIT_SHORT, Size.LAND)) {
            val b = studyOnlyShort(size)
            assertEquals("$size studyBar", View.VISIBLE, vis(b, "studyBar"))
            assertEquals("$size deck list", View.VISIBLE, vis(b, "deckPane"))
            assertEquals("$size nav", View.VISIBLE, vis(b, "nav"))
            assertEquals("$size content (the slot)", View.VISIBLE, vis(b, "content"))
            assertEquals(View.GONE, vis(b, "topBar"))
            val lp = (b.field("content") as View).layoutParams as LinearLayout.LayoutParams
            assertEquals("$size the slot is as tall as its row", -2, lp.height)
            assertTrue("$size the row offers the first run", texts(b.field("content") as View).contains("Set up the game"))
        }
    }

    @Test fun theSlotOfAShortStudyLayoutIsCappedToHalf() {
        val b = studyOnlyShort(Size.SPLIT)
        val cap = (b.field("capped") as View).let { getField(it, "cap") as Float }
        assertEquals(0.5f, cap, 0f)
        b.relayout(869f) // a tall window: no cap
        assertEquals(0f, getField(b.field("capped")!!, "cap") as Float, 0f)
    }

    @Test fun aSaveErrorCardDoesNotPushTheDeckListOutOfAShortWindow() {
        val c = ctx(Size.SPLIT)
        BattleShotKit.gameRepoSet("held", null)
        BattleShotKit.gameRepoSet("error", "The save file is damaged (unexpected end of data at line 1).")
        GameRepo::class.java.getDeclaredMethod("rebuild").also { it.isAccessible = true }.invoke(GameRepo)
        val b = BattleShotKit.build(c)
        b.call("onStatus", GameRepo.status)
        b.relayout(c)
        setField(b.activity.home, "decks", StudyDeckKit.decks.sortedBy { it.name }); call(b.activity.home, "render")
        HomeShotKit.layOut(b, c)
        val content = b.field("content") as View
        val deckPane = b.field("deckPane") as View
        assertEquals(View.VISIBLE, deckPane.visibility)
        assertTrue("the card is capped to half of the room (${content.height} of ${content.height + deckPane.height} px)", content.height * 2 <= content.height + deckPane.height + 2)
        assertTrue("the deck list keeps real room (${deckPane.height} px)", deckPane.height > 0 && deckPane.height >= content.height - 2)
    }

    @Test fun studyOnlyShots() {
        paparazzi.shootWith("r4fixb-home-studyonly", listOf(Size.SPLIT, Size.LAND, Size.SPLIT_SHORT), scenario = FakeGame.Scenario.NO_DATA) { ctx ->
            HomeShotKit.studyOnly(ShotCtx(ctx)).root
        }
    }

    @Test fun saveErrorShots() {
        paparazzi.shootWith("r4fixb-home-saveerror", listOf(Size.SPLIT)) { ctx ->
            val ss = ShotCtx(ctx)
            ss.getSharedPreferences("study", 0).edit().putLong("lastDeck", 2).apply()
            BattleShotKit.gameRepoSet("held", null)
            BattleShotKit.gameRepoSet("error", "The save file is damaged (unexpected end of data at line 1).")
            GameRepo::class.java.getDeclaredMethod("rebuild").also { it.isAccessible = true }.invoke(GameRepo)
            val b = BattleShotKit.build(ss)
            b.call("onStatus", GameRepo.status)
            b.relayout(ss)
            setField(b.activity.home, "decks", StudyDeckKit.decks.sortedBy { it.name }); call(b.activity.home, "render")
            HomeShotKit.layOut(b, ss).root
        }
    }

    // ---------------- B5 + B6: the battle card

    private class FakeLive : BattleCard.Live {
        override var paused = false
        val holders = LinkedHashSet<Any>()
        val sinks = ArrayList<LiveSession.Sink>()
        var restarts = 0
        override fun hold(ctx: Context, owner: Any) { holders += owner }
        override fun release(ctx: Context, owner: Any) { holders -= owner }
        override fun addSink(s: LiveSession.Sink) { sinks += s }
        override fun removeSink(s: LiveSession.Sink) { sinks -= s }
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
        override fun restart(ctx: Context) { restarts++ }
    }

    private class CardHost : BattleCard.Host {
        var grants = 0
        override fun expand() {}
        override fun popOut() {}
        override fun grantAccess() { grants++ }
    }

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity? get() = activity
        override fun closePage() {}
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    @Test fun theGrantButtonAsksTheHostAndAGrantRestartsLiveDetection() {
        val c = ctx()
        val live = FakeLive()
        val host = CardHost()
        val card = BattleCard(c, host, live)
        card.start()
        assertTrue(card.isLive)
        // live status: the AnkiDroid permission is missing -> the panel strip offers "Grant"
        live.sinks.single().onStatus(false, "AnkiDroid permission missing", true)
        val grant = findText(card.view) { it == "Grant" }
        assertNotNull("the Grant button shows", grant)
        grant!!.performClick()
        assertEquals("the card asks its host (the activity requests the permission)", 1, host.grants)

        card.accessAnswered(granted = false)
        assertEquals("denied: nothing restarts", 0, live.restarts)
        card.accessAnswered(granted = true)
        assertEquals("granted: detection starts again", 1, live.restarts)

        card.stop()
        card.accessAnswered(granted = true)
        assertEquals("a card that is not on screen starts nothing", 1, live.restarts)
    }

    @Test fun theTeamSectionShowsAndHoldsTheCardInASplitHalf() {
        val c = ctx(Size.SPLIT)
        val live = FakeLive()
        val p = TeamPage(c, ActivityHost(attach(Activity(), c)))
        p.cardSession = live
        p.preset(TeamSection.TEAM)
        val screen = pageScreen(c, p)
        call(p, "render")
        assertTrue("BATTLE" in texts(screen))
        assertTrue(p.cardLive)
        assertEquals("the LiveSession hold rule: held while the card is on screen", 1, live.holders.size)
        p.onHide()
        assertEquals("not while the page is hidden", 0, live.holders.size)
        p.onShow(); call(p, "render")
        assertEquals(1, live.holders.size)
        p.onGone()
        assertEquals(0, live.holders.size)
    }

    private fun team(c: Context): View {
        val p = TeamPage(c, ActivityHost(attach(Activity(), c)))
        p.cardSession = FakeLive()
        p.preset(TeamSection.TEAM)
        val screen = pageScreen(c, p)
        call(p, "render")
        return screen
    }

    /** Size-based (a Variant with width > height renders swapped: its device config is portrait). */
    @Test fun teamCardInShortWindowsShots() {
        paparazzi.shoot("r4fixb-team-card", listOf(Size.SPLIT, Size.SPLIT_SHORT, Size.LAND)) { c -> team(c) }
    }

    // ---------------- B13: several achievements, "See it" + "Nice" (no "See all")

    private fun one(rowId: String, tier: Int): com.husarp.ankigo.game.AchCelebration {
        val row = com.husarp.ankigo.game.AchievementTable.row(rowId)!!
        return com.husarp.ankigo.game.AchCelebration(row.id, row.title, tier, com.husarp.ankigo.game.AchievementTable.tierName(tier), row.tiers.getOrNull(tier))
    }

    private fun tap(root: View, label: String) {
        var v: View? = findText(root) { it == label }
        assertNotNull("no \"$label\"", v)
        while (v != null && !v.isClickable) v = v.parent as? View
        v!!.performClick()
    }

    @Test fun manyAchievementsOfferSeeItForTheFirstAndNice() {
        val c = ctx()
        val items = listOf(one("dex-caught", 2), one("catches", 1), one("answers", 3))
        var nice = 0; var seen = "none"
        val layer = com.husarp.ankigo.ui.AchievementPopup.layer(c, items, onNice = { nice++ }, onSee = { seen = it ?: "list" })
        val t = texts(layer)
        assertTrue(t.toString(), "3 achievements!" in t && "See it" in t && "Nice" in t)
        assertFalse("no \"See all\"", "See all" in t)
        tap(layer, "See it")
        assertEquals("opens the first listed achievement's sheet", items[0].rowId, seen)
        tap(layer, "Nice"); assertEquals(1, nice)
        tap(layer, items[2].title)
        assertEquals("a row stays tappable and opens its own", items[2].rowId, seen)
    }

    @Test fun manyAchievementsShots() {
        paparazzi.shoot("r4fixb-popup-many", listOf(Size.PHONE, Size.FLOAT, Size.SPLIT)) { c ->
            val layer = com.husarp.ankigo.ui.AchievementPopup.layer(c, listOf(one("dex-caught", 2), one("catches", 1), one("answers", 3)), onNice = {}, onSee = {})
            FrameLayout(c).apply { addView(layer, FrameLayout.LayoutParams(-1, -1)); setBackgroundColor(android.graphics.Color.argb(150, 0, 0, 0)) }
        }
    }

    // ---------------- N1: the evolution line fits the 340 dp floating card

    private fun evoLine(c: Context, num: Int, compact: Boolean): View {
        val gd = GameRepo.data!!
        return com.husarp.ankigo.ui.DetailSections.evolution(c, gd, gd.sp(num), setOf(172, 25, 26, 63, 64, 65, 133, 134, 135, 700), compact, null)
    }

    @Test fun aThreeStageLineIsDrawnToFitTheRoomOfTheFloatingCard() {
        val c = ctx()
        val d = c.resources.displayMetrics.density
        val room = (259 * d).toInt() // the dialog's inner width in the 340 dp card
        for ((num, compact) in listOf(25 to false, 64 to false, 25 to true, 133 to false)) {
            val line = evoLine(c, num, compact)
            line.measure(View.MeasureSpec.makeMeasureSpec(room, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            line.layout(0, 0, line.measuredWidth, line.measuredHeight)
            val inner = (line as ViewGroup).getChildAt(0)
            assertEquals("$num $compact: as wide as the room", room, line.measuredWidth)
            assertTrue("$num $compact: ${inner.measuredWidth * inner.scaleX} px drawn in $room", inner.measuredWidth * inner.scaleX <= room + 1)
            assertTrue("$num $compact: its left edge is inside", inner.left >= 0)
            assertTrue("$num $compact: it keeps a height", line.measuredHeight > 0)
        }
        // wide enough: not scaled
        val line = evoLine(c, 25, true)
        line.measure(View.MeasureSpec.makeMeasureSpec((800 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertEquals(1f, (line as ViewGroup).getChildAt(0).scaleX, 0f)
    }

    @Test fun evolutionLineShots() {
        for (num in listOf(25, 64)) paparazzi.shoot("r4fixb-evo-$num", listOf(Size.FLOAT, Size.PHONE)) { c ->
            val d = c.resources.displayMetrics.density
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = com.husarp.ankigo.ui.Ui.rounded(com.husarp.ankigo.ui.Ui.c(c, com.husarp.ankigo.R.color.ag_card), 18, c)
                val p = (20 * d).toInt(); setPadding(p, p, p, p)
                addView(evoLine(c, num, com.husarp.ankigo.ui.DetailParts.compact(c)))
            }
            FrameLayout(c).apply {
                setBackgroundColor(android.graphics.Color.argb(150, 0, 0, 0))
                addView(card, FrameLayout.LayoutParams(minOf((340 * d).toInt() - (40 * d).toInt(), (340 * d).toInt()), -2, android.view.Gravity.CENTER))
            }
        }
    }

    // ---------------- B7: a held result meets a small window

    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun defeatBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    @Test fun aHeldResultThatMeetsPictureInPictureTimesOutAndReleasesTheNextWildOne() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue("tiles show after the fight", panel.tilesShown)
        assertTrue("the next wild one waits behind them", panel.holdsNextFight)

        var released = 0
        panel.onHeldReleased = { released++ }
        panel.setCompact(true)                  // the window became picture-in-picture
        measure(panel, 320, 180)
        assertTrue("the result stays, now on the small-window timeout", panel.tilesShown)
        assertFalse("the next wild one is let go", panel.holdsNextFight)
        // the release is posted (never from inside a measure pass): the snapshot lets the main looper run it
        paparazzi.snapshot(FrameLayout(c).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }, "r4fixb-b7-pip", 500)
        assertEquals("its owner is told once", 1, released)
        measure(panel, 320, 180)
        assertEquals("a further layout pass changes nothing", 1, released)
    }

    @Test fun theReleasedNextWildOnePlaysItsEntranceAtOnce() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)   // its own release listener (no override)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        val parked = EventPlayer::class.java.getDeclaredField("parked").apply { isAccessible = true }
        assertNotNull("the next fight's Encounter is held", parked.get(player))
        panel.setCompact(true)
        measure(panel, 320, 180)
        paparazzi.snapshot(FrameLayout(c).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }, "r4fixb-b7-pip-played", 500)
        assertNull("it played now, not with the next answer", parked.get(player))
        assertTrue(player.idle)
    }

    @Test fun aResultInTheFullPanelStaysHeldAcrossLayoutPasses() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        panel.refreshPrefs()
        measure(panel, 412, 869)
        measure(panel, 412, 800)
        assertTrue(panel.tilesShown); assertTrue(panel.holdsNextFight)
    }

    @Test fun aResultThatHasNoRoomLeftGoesAtOnce() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue(panel.holdsNextFight)
        panel.setCompact(true)
        measure(panel, 120, 60)                 // so small that the log line steps out
        assertFalse("no room for the tiles", panel.tilesShown)
        assertFalse(panel.holdsNextFight)
    }
}
