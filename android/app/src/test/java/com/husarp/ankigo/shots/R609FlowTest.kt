package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R6-09 flow: "Continue" after a fight result instead of a wasted card, driven through the real [EventPlayer] and [BattlePanelView]
 * (reduced motion, so the events play at once) and the real [StudyActivity] views (attached, no onCreate). Rendered by Paparazzi only
 * for its Android Context; a snapshot also lets the main looper run the posted hold notices.
 */
class R609FlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return c
    }

    private val answer = listOf<GameEvent>(GameEvent.Counted(3, 10, 5, 1, false), GameEvent.Charge(10, 1, 2))
    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun defeatBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )
    private fun catchBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true),
        GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )

    private fun parked(p: EventPlayer): Any? = EventPlayer::class.java.getDeclaredField("parked").apply { isAccessible = true }.get(p)

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    /** Lets the main looper run what the views posted (the hold notices). */
    private fun flush(c: Context, v: View? = null) {
        paparazzi.snapshot(FrameLayout(c).apply { if (v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r609-flush", 300)
    }

    // ---------------------------------------------------------------- the player

    @Test fun continueLetsTheHeldFightInWithoutAnAnswer() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue(player.holding); assertNotNull("the next fight is parked", parked(player))
        assertTrue("Continue did something", player.continueBattle())
        assertFalse(player.holding); assertFalse(panel.tilesShown); assertFalse(panel.holdsNextFight)
        assertNull("the parked fight played", parked(player))
        assertTrue(player.idle)
    }

    @Test fun continueAfterACatchWorksToo() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(catchBatch())
        assertTrue(player.holding)
        assertTrue(player.continueBattle())
        assertFalse(panel.tilesShown)
    }

    @Test fun continueWithNothingHeldDoesNothing() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        assertFalse(player.continueBattle())
        player.enqueue(answer)
        assertFalse(player.continueBattle())
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue(player.continueBattle())
        assertFalse("the second tap finds nothing held", player.continueBattle())
    }

    @Test fun anAnswerAfterContinueIsAnOrdinaryAnswer() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        player.continueBattle()
        player.enqueue(answer)
        assertFalse(panel.tilesShown); assertFalse(player.holding)
        assertTrue(player.idle)
    }

    @Test fun anAnswerThatArrivesAnywayStillReleases() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        player.enqueue(answer)   // answered in AnkiDroid meanwhile
        assertFalse(player.holding); assertFalse(panel.tilesShown)
        assertNull(parked(player))
        assertFalse("nothing left to continue", player.continueBattle())
    }

    @Test fun leavingTheScreenDropsTheHoldWithoutAnEntrance() {
        val panel = BattlePanelView(ctx())
        val player = EventPlayer(panel)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        player.dropHold()
        assertFalse(player.holding); assertFalse(panel.tilesShown)
        assertNull(parked(player))
        assertTrue(player.idle)
        player.dropHold()   // nothing held: harmless
    }

    @Test fun aSmallWindowNeverHoldsSoThereIsNothingToContinue() {
        val panel = BattlePanelView(ctx())
        panel.setCompact(true)
        val player = EventPlayer(panel)
        player.enqueue(defeatBatch())
        assertFalse(player.holding)
        assertFalse(player.continueBattle())
    }

    @Test fun theHoldIsToldOnTheMainLooperAndOnlyWhenItChanges() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        val told = ArrayList<Boolean>()
        panel.onHoldChanged = { told += it }
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        assertTrue("posted, not inside the call", told.isEmpty())
        flush(c, panel)
        assertEquals(listOf(true), told)
        player.continueBattle()
        flush(c, panel)
        assertEquals(listOf(true, false), told)
        player.continueBattle()
        flush(c, panel)
        assertEquals("no change, no notice", listOf(true, false), told)
    }

    // ---------------------------------------------------------------- the tap target on the panel

    @Test fun theTilesTakeTheContinueTapOnScreensWithoutAnAnswerRow() {
        val c = ctx()
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)   // default: the panel carries Continue
        assertNotNull(panel.onContinue)
        measure(panel, 412, 869)
        player.enqueue(defeatBatch())
        val tiles = getField(panel, "tiles") as View
        assertTrue("the tiles are a tap target", tiles.isClickable)
        tiles.performClick()
        assertFalse("the tap continued", panel.holdsNextFight)
    }

    @Test fun theStudyScreenKeepsItsContinueInTheAnswerRowNotOnTheTiles() {
        val c = ctx()
        val panel = BattlePanelView(c)
        EventPlayer(panel, continueOnPanel = false)
        assertNull(panel.onContinue)
        measure(panel, 412, 869)
        val tiles = getField(panel, "tiles") as View
        assertFalse(tiles.isClickable)
    }

    // ---------------------------------------------------------------- the Study screen

    private fun study(c: Context, flipped: Boolean = false): StudyActivity {
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.setStudyLayout(false, false)   // the full panel (a strip never holds)
        measure(root, 412, 869)
        call(act, "showButtons")
        return act
    }

    private fun answerRow(act: StudyActivity): ViewGroup = getField(act, "buttons") as ViewGroup
    private fun rowText(act: StudyActivity): String = answerRow(act).getChildAt(0).contentDescription?.toString() ?: ""
    private fun events(act: StudyActivity) = getField(act, "events") as EventPlayer

    @Test fun theQuestionRowShowsContinueWhileAResultHolds() {
        val c = ctx()
        val act = study(c)
        assertEquals("Show answer", rowText(act))
        events(act).enqueue(defeatBatch())
        flush(c)
        assertEquals("Continue, next battle", rowText(act))
        assertEquals("one button, same place", 1, answerRow(act).childCount)
        // the button does not touch the card
        answerRow(act).getChildAt(0).performClick()
        assertFalse(events(act).holding)
        assertEquals("Show answer", rowText(act))
        assertEquals("the same card", false, getField(act, "flipped"))
    }

    @Test fun theAnswerButtonsAreReplacedWhenTheResultComesWhileTheAnswerShows() {
        val c = ctx()
        val act = study(c, flipped = true)
        assertTrue(answerRow(act).childCount >= 1)
        assertTrue("answer buttons first", rowText(act) != "Continue, next battle")
        events(act).enqueue(defeatBatch())
        flush(c)
        assertEquals("Continue, next battle", rowText(act))
        assertEquals(1, answerRow(act).childCount)
        answerRow(act).getChildAt(0).performClick()
        assertFalse(events(act).holding)
        assertEquals(true, getField(act, "flipped"))
        assertTrue("the answer buttons are back", answerRow(act).childCount >= 1 && rowText(act) != "Continue, next battle")
    }

    @Test fun aFlipRequestWhileAResultHoldsActsAsContinueAndLeavesTheCard() {
        val c = ctx()
        val act = study(c)
        events(act).enqueue(defeatBatch())
        call(act, "flip")   // the same function the keys, the card's tap and the card's script reach
        assertFalse(events(act).holding)
        assertEquals("the card did not flip", false, getField(act, "flipped"))
    }

    @Test fun anAnswerRequestWhileAResultHoldsActsAsContinueAndAnswersNothing() {
        val c = ctx()
        val act = study(c, flipped = true)
        events(act).enqueue(defeatBatch())
        call(act, "answer", 3)   // keys 1-4, a swipe and the card's script come through here
        assertFalse(events(act).holding)
        assertFalse("nothing was answered", getField(act, "locked") as Boolean)
        assertEquals(0, (getField(act, "counts") as IntArray).sum())
    }

    @Test fun theAutoTimerDoesNotContinueByItself() {
        val c = ctx()
        val act = study(c)
        events(act).enqueue(defeatBatch())
        call(act, "fireAuto")
        assertTrue("still held: only a tap continues", events(act).holding)
        assertEquals(false, getField(act, "flipped"))
    }

    @Test fun leavingTheScreenDropsTheHold() {
        val c = ctx()
        val act = study(c)
        events(act).enqueue(defeatBatch())
        events(act).dropHold()   // what onStop does
        assertFalse(events(act).holding)
    }

    @Test fun aHiddenPanelReleasesTheHoldSoNoContinueComesLaterForAFlippedCard() {
        val c = ctx()
        val act = study(c)
        val panel = getField(act, "panel") as BattlePanelView
        events(act).enqueue(defeatBatch())
        flush(c)
        assertTrue(events(act).holding)
        panel.visibility = View.GONE   // the type-in keyboard layout hides the panel under the result
        call(act, "releaseHiddenHold")
        assertFalse("released at once", events(act).holding)
        // a flip request now acts on the card, nothing is parked
        assertFalse(call(act, "heldContinue") as Boolean)
        panel.visibility = View.VISIBLE
        flush(c)
        assertFalse(events(act).holding)
        assertNotEquals("Continue, next battle", rowText(act))
    }
}
