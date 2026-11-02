package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.study.AutoAdvance
import com.husarp.ankigo.study.CalmCard
import com.husarp.ankigo.study.Cmd
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.ContinueRule
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-N (PLAN 130, 130A): while a fight result holds the next battle, the next card is not shown (the calm card covers it) and nothing of it
 * runs: the answer clock, the top pill, the auto-advance countdown, its speech. Continue shows the card and only then do they start. Drives
 * the real [StudyActivity] views by reflection (no onCreate), like R609FlowTest; the clock is the real one (short sleeps stand for the hold).
 */
class R19NCalmCardTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).putString("timer", "Always").putBoolean("autoAdvance", true).commit()
        return c
    }

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

    /** The Study screen on a question card whose page has loaded and whose clock runs, as right after an answer (the screen is in front). */
    private fun study(c: Context, flipped: Boolean = false, strip: Boolean = false): StudyActivity {
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        setField(act, "background", false)
        setField(act, "pageReady", true)
        call(act, "layout")
        (getField(act, "panel") as BattlePanelView).setStudyLayout(strip, false)
        measure(root, 412, 869)
        call(act, "showButtons")
        call(act, "startClock")   // what the page's onPageFinished does
        return act
    }

    private fun events(act: StudyActivity) = getField(act, "events") as EventPlayer
    private fun row(act: StudyActivity) = getField(act, "buttons") as ViewGroup
    private fun rowText(act: StudyActivity) = row(act).getChildAt(0)?.contentDescription?.toString() ?: ""
    private fun calmView(act: StudyActivity) = getField(act, "calmView") as View
    private fun calmOn(act: StudyActivity) = getField(act, "calmOn") as Boolean
    private fun shownAt(act: StudyActivity) = getField(act, "shownAt") as Long
    private fun elapsed(act: StudyActivity) = getField(act, "elapsed") as Long
    private fun pill(act: StudyActivity) = (getField(act, "timerPill") as TextView).text.toString()
    private fun auto(act: StudyActivity) = getField(act, "auto") as AutoAdvance

    /** A result holds the next battle (the hold notice is posted on the main looper; the test runs the same step itself, like R14CFinishDeckShots). */
    private fun hold(act: StudyActivity) {
        events(act).enqueue(defeatBatch())
        assertTrue("a result holds", events(act).holding)
        call(act, "showButtons")
    }

    // ---------------------------------------------------------------- the rule

    @Test fun theCalmCardShowsOnlyForAnUnseenCardUnderAHold() {
        assertTrue(CalmCard.on(holding = true, hasCard = true, flipped = false, finished = false, finishWait = false))
        assertFalse("no hold", CalmCard.on(false, true, false, false, false))
        assertFalse("no card", CalmCard.on(true, false, false, false, false))
        assertFalse("the answer side is up already", CalmCard.on(true, true, true, false, false))
        assertFalse("the summary", CalmCard.on(true, true, false, true, false))
        assertFalse("Finish deck waits", CalmCard.on(true, true, false, false, true))
    }

    @Test fun theClockRunsOnlyWhileTheCardIsOnScreenAndTheScreenInFront() {
        assertTrue(CalmCard.clockMayRun(calm = false, background = false))
        assertFalse(CalmCard.clockMayRun(calm = true, background = false))
        assertFalse(CalmCard.clockMayRun(calm = false, background = true))
        assertEquals(0L, CalmCard.pillMs(true, 30_000)); assertEquals(5_000L, CalmCard.pillMs(false, 5_000))
    }

    @Test fun theWordsAreTrueToTheRealButton() {
        assertTrue("the hint names the button", CalmCard.HINT.contains(ContinueRule.LABEL))
        assertEquals("Battle over", CalmCard.TITLE)
    }

    /** PLAN 433: exercise the actual held-result row, including the compact menu wrapper. */
    @Test fun continueMatchesShowAnswerColourInBothThemesAndLayouts() {
        for (theme in Theme.entries) for (compact in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val c = Shots.files(paparazzi.context)
            FakeGame.install(c)
            c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true)
                .putBoolean("compactView", compact).commit()
            val act = study(c)
            val gallery = android.widget.LinearLayout(c).apply { orientation = android.widget.LinearLayout.VERTICAL }
            for (choice in com.husarp.ankigo.ui.ShowAnswerColor.entries) {
                com.husarp.ankigo.ui.ShowAnswerColor.put(c, choice)
                call(act, "showButtons")
                if (!events(act).holding) hold(act)
                val container = row(act)
                fun descendants(v: View): List<View> = listOf(v) +
                    if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
                val button = descendants(container).first { it.contentDescription?.toString() == "Continue, next battle" }
                val shape = (button.background as android.graphics.drawable.RippleDrawable).getDrawable(0) as android.graphics.drawable.GradientDrawable
                val expected = choice.fill ?: com.husarp.ankigo.ui.Ui.c(c, com.husarp.ankigo.R.color.ag_mauve)
                assertEquals(expected, shape.color!!.defaultColor)
                if (choice.fill != null) {
                    descendants(button).filterIsInstance<TextView>().forEach { assertEquals(android.graphics.Color.WHITE, it.currentTextColor) }
                }
                assertTrue(button.isEnabled)
                gallery.addView(com.husarp.ankigo.ui.StudyUi.label(c, choice.label))
                val displayed = container.getChildAt(0)
                container.removeView(displayed)
                gallery.addView(displayed)
            }
            paparazzi.snapshot(gallery, "r27i-continue-${theme.name.lowercase()}-${if (compact) "compact" else "normal"}")
            com.husarp.ankigo.ui.ShowAnswerColor.put(c, com.husarp.ankigo.ui.ShowAnswerColor.DEFAULT)
        }
    }

    // ---------------------------------------------------------------- during the hold

    @Test fun duringTheHoldThePlaceholderShowsAndTheCardsClockDoesNotRun() {
        val c = ctx()
        val act = study(c)
        assertNotEquals("the card was up and timed before the result came", 0L, shownAt(act))
        assertEquals(View.GONE, calmView(act).visibility)
        hold(act)
        assertEquals("Continue, next battle", rowText(act))
        assertTrue(calmOn(act)); assertEquals(View.VISIBLE, calmView(act).visibility)
        assertEquals("that moment is not the card's time", 0L, shownAt(act)); assertEquals(0L, elapsed(act))
        // the card's page finishes loading behind the placeholder: still no clock
        call(act, "startClock")
        assertEquals(0L, shownAt(act))
        advance(c, 700)
        act.refreshTimer()
        assertEquals("0:00", pill(act))
        call(act, "pauseClock")
        assertEquals("a hold of any length records nothing", 0L, elapsed(act))
    }

    @Test fun autoAdvanceDoesNotRunOrFireDuringTheHold() {
        val act = study(ctx())
        hold(act)
        assertFalse("no countdown behind the placeholder", auto(act).running)
        call(act, "syncTiming")
        assertFalse(auto(act).running)
        call(act, "fireAuto")
        assertTrue("still held", events(act).holding)
        assertEquals(false, getField(act, "flipped"))
    }

    @Test fun theQuestionsSoundsWaitBehindThePlaceholder() {
        val act = study(ctx())
        hold(act)
        call(act, "showQuestion")   // a wrong prediction swapped, or the card reloaded: the question is built again behind the placeholder
        assertEquals("held, not played", true, getField(act, "avHeld"))
        assertFalse(getField(act, "audioBusy") as Boolean)
        assertTrue(calmOn(act))
        assertEquals("the clock restarts at 0 for the unseen card", 0L, shownAt(act))
        row(act).getChildAt(0).performClick()   // Continue
        assertEquals("played at the show", false, getField(act, "avHeld"))
    }

    @Test fun aKeyOrATapOnTheCardOnlyContinues() {
        val act = study(ctx())
        hold(act)
        call(act, "flip")
        assertFalse(events(act).holding); assertFalse(calmOn(act))
        assertEquals("the card did not flip", false, getField(act, "flipped"))
    }

    // ---------------------------------------------------------------- Continue

    @Test fun continueShowsTheCardAndItsTimeCountsFromTheShow() {
        val c = ctx()
        val act = study(c)
        hold(act)
        advance(c, 700)   // the hold
        row(act).getChildAt(0).performClick()   // Continue
        assertFalse(events(act).holding)
        assertFalse(calmOn(act)); assertEquals(View.GONE, calmView(act).visibility)
        assertNotEquals("the clock starts now", 0L, shownAt(act))
        assertNotEquals("Continue, next battle", rowText(act))
        advance(c, 300)   // reading the card
        call(act, "pauseClock")   // what an answer does before it takes the time
        val took = elapsed(act)
        assertTrue("about the 300 ms after the show, not the 1000 ms since the result: $took", took in 250L..650L)
    }

    @Test fun theTimeOfACardSeenBeforeTheResultIsNotCounted() {
        val c = ctx()
        val act = study(c)
        advance(c, 400)   // the card was on screen while the answer's result was still on its way
        hold(act)
        row(act).getChildAt(0).performClick()
        advance(c, 200)
        call(act, "pauseClock")
        assertTrue("only the time after the show: ${elapsed(act)}", elapsed(act) in 150L..380L)
    }

    @Test fun aCardThatWasAlreadyFlippedStaysWhenTheResultComesLate() {
        val act = study(ctx(), flipped = true)
        hold(act)
        assertEquals("Continue, next battle", rowText(act))
        assertFalse("the answer side is up: it stays", calmOn(act)); assertEquals(View.GONE, calmView(act).visibility)
    }

    // ---------------------------------------------------------------- other ways out and other layouts

    @Test fun aHiddenPanelReleasesTheHoldAndTheCardShows() {
        val c = ctx()
        val act = study(c)
        hold(act)
        assertTrue(calmOn(act))
        (getField(act, "panel") as BattlePanelView).visibility = View.GONE   // the type-in keyboard layout
        call(act, "releaseHiddenHold")
        assertFalse(events(act).holding); assertFalse(calmOn(act)); assertNotEquals(0L, shownAt(act))
    }

    @Test fun theDecksLastAnswerLeavesNoPlaceholder() {
        val act = study(ctx())
        hold(act)
        assertTrue(calmOn(act))
        setField(act, "answeredSinceCard", true)
        call(act, "perform", listOf(Cmd.ShowFinished))
        call(act, "waitForFinish", Triple(emptyList<Anki.Deck>(), 0, 0))
        assertFalse(calmOn(act)); assertEquals("Finish deck, see the summary", rowText(act))
    }

    @Test fun theStripAndMiniNeverHoldSoThereIsNoPlaceholder() {
        val act = study(ctx(), strip = true)
        events(act).enqueue(defeatBatch())
        call(act, "showButtons")
        assertFalse(events(act).holding); assertFalse(calmOn(act))
        assertNotEquals("the clock keeps running as before", 0L, shownAt(act))
    }

    @Test fun aNewCardAfterTheHoldStartsItsClockAtTheShow() {
        val act = study(ctx())
        hold(act)
        call(act, "showQuestion")   // the next card is built behind the placeholder (R19-R prepared it)
        assertEquals(0L, shownAt(act))
        row(act).getChildAt(0).performClick()
        assertEquals("its page is still loading: the clock waits for onPageFinished", 0L, shownAt(act))
        setField(act, "pageReady", true); call(act, "startClock")   // onPageFinished
        assertNotEquals(0L, shownAt(act))
    }

    @Test fun theScreenInTheBackgroundNeverStartsTheClock() {
        val act = study(ctx())
        hold(act)
        setField(act, "background", true)
        row(act).getChildAt(0).performClick()
        assertFalse(calmOn(act)); assertEquals(0L, shownAt(act))
    }

    private var passed = 0L

    /** Lets [ms] of (Paparazzi's) clock pass: [android.os.SystemClock] follows a snapshot's offset, counted from the first one of the test. */
    private fun advance(c: Context, ms: Long) {
        val before = android.os.SystemClock.elapsedRealtime()
        passed += ms
        paparazzi.snapshot(FrameLayout(c), "r19n-advance", passed)
        assertTrue("the clock moved by ${ms} ms", android.os.SystemClock.elapsedRealtime() - before >= ms)
    }
}
