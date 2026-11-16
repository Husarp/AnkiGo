package com.husarp.ankigo.shots

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.LiveRefresh
import com.husarp.ankigo.study.PendingAnswers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R43DueRefreshShots {
    @get:Rule val paparazzi = Shots.rule()
    private val deck = Anki.Deck(2, "Geography::Capitals", 0, 0, 0, false)
    private val next = Anki.Deck(3, "Geography::Rivers", 0, 4, 0, false)
    private val learning = Anki.Review(500, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList())
    private fun snapshot(n: Int = 0, learn: Int = n, fresh: Int = 0) = StudyActivity.DueRefresh(
        listOf(deck.copy(learn = n), next), List(n) { learning.copy(nid = 500L + it) }, learn, fresh)

    private fun screen(ctx: Context): Pair<StudyActivity, View> {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", deck.id); setField(act, "deckName", deck.name)
        setField(act, "resumedNow", true)
        val root = act.buildContent()
        act.finishQueueCheck = { it(Result.success(snapshot())) }
        act.dueRefreshRead = { _, _ -> error("a test must provide its fake AnkiDroid read") }
        call(act, "layout")
        return act to root
    }
    private fun wait(act: StudyActivity) { call(act, "waitForFinish", Triple(listOf(deck, next), 0, 0)) }
    private fun buttons(act: StudyActivity) = getField(act, "buttons") as ViewGroup
    private fun row(act: StudyActivity) = buttons(act).getChildAt(0) as ViewGroup
    private fun counts(act: StudyActivity) = ShopCardKit.texts(getField(act, "queueCounts") as View)
    @Suppress("UNCHECKED_CAST")
    private fun live(act: StudyActivity) = (getField(act, "liveCounts\$delegate") as Lazy<*>).value as LiveRefresh<StudyActivity.DueRefresh>
    private fun wake(act: StudyActivity) {
        val run = getField(act, "countWake") as Runnable
        (getField(act, "main") as Handler).removeCallbacks(run)
        run.run()
    }

    @Test fun idleReadKeepsCardSideAndContentAndUpdatesTopCounts() = paparazzi.shootWith("r43k-idle-counts", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx)
        setField(act, "card", learning); setField(act, "locked", false); setField(act, "flipped", true)
        setField(act, "question", "Same question"); setField(act, "answerHtml", "Same answer")
        val surface = getField(act, "cardSurface")
        act.dueRefreshRead = { end, done -> assertFalse(end); done(Result.success(snapshot(1))) }
        val live = live(act)
        try {
            live.start(); wake(act)
            assertSame(learning, getField(act, "card")); assertSame(surface, getField(act, "cardSurface"))
            assertEquals(true, getField(act, "flipped"))
            assertEquals("Same question", getField(act, "question")); assertEquals("Same answer", getField(act, "answerHtml"))
            assertEquals(listOf("0", "1", "0"), counts(act))
        } finally { live.stop() }
        root
    }

    @Test fun lateReadAfterPauseCannotChangeEndButtonsAndResumeGetsFreshCounts() = paparazzi.shootWith("r43k-resume", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx); wait(act)
        var complete: ((Result<StudyActivity.DueRefresh>) -> Unit)? = null
        act.dueRefreshRead = { end, done -> assertTrue(end); complete = done }
        val live = live(act)
        try {
            live.start(); wake(act); live.stop()
            complete!!(Result.success(snapshot(1)))
            assertFalse(ShopCardKit.texts(root).contains("1 card is back"))
            live.start(); wake(act); complete!!(Result.success(snapshot(1)))
            assertTrue(ShopCardKit.texts(root).contains("1 card is back"))
            assertEquals(listOf("0", "1", "0"), counts(act))
        } finally { live.stop() }
        root
    }

    @Test fun finishRecheckOffersNewlyDueCardAndClearsStaleEndCounts() = paparazzi.shootWith("r43k-end-offer", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        val (act, root) = screen(ctx)
        setField(act, "lastCounts", deck.copy(learn = 9))
        wait(act)
        assertEquals(listOf("0", "0", "0"), counts(act))
        var complete: ((Result<StudyActivity.DueRefresh>) -> Unit)? = null
        act.finishQueueCheck = { complete = it }
        row(act).getChildAt(0).performClick()
        assertNull(getField(act, "lastSummary"))
        complete!!(Result.success(snapshot(1)))
        assertFalse(getField(act, "finished") as Boolean)
        assertEquals(2, row(act).childCount)
        assertEquals("Finish deck, see the summary", row(act).getChildAt(0).contentDescription)
        assertEquals("1 card is back · Continue", row(act).getChildAt(1).contentDescription)
        assertEquals(listOf("0", "1", "0"), counts(act))
        val top = getField(act, "queueCounts") as ViewGroup
        assertTrue((0 until top.childCount).none { top.getChildAt(it).contentDescription.toString().contains("current") })
        assertFalse(ShopCardKit.texts(root).contains("Next deck"))
        val d = ctx.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((ctx.resources.configuration.screenWidthDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((ctx.resources.configuration.screenHeightDp * d).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val offer = row(act).getChildAt(1) as ViewGroup
        for (i in 0 until offer.childCount) {
            val label = offer.getChildAt(i) as TextView
            val textLayout = requireNotNull(label.layout)
            assertTrue("the offer's text must fit in the narrow split layout", textLayout.getLineBottom(textLayout.lineCount - 1) + label.compoundPaddingTop + label.compoundPaddingBottom <= label.height)
            assertTrue(label.bottom <= offer.height)
        }
        root
    }

    @Test fun finishAfterAnOfferUsesFreshRemainingCountsAndStillLetsTheUserContinue() = paparazzi.shootWith("r43k-summary-offer", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx); wait(act)
        call(act, "applyDueRefresh", snapshot(1))
        act.finishQueueCheck = { it(Result.success(snapshot(1, learn = 3, fresh = 7))) }
        row(act).getChildAt(0).performClick()
        assertTrue(getField(act, "finished") as Boolean)
        assertEquals(snapshot(1, 3, 7).args, getField(act, "lastSummary"))
        assertTrue(ShopCardKit.texts(buttons(act)).contains("1 card is back · Continue"))
        assertFalse(getField(act, "finishTapped") as Boolean)
        root
    }

    @Test fun continueWaitsForRewardsAndOnlyStartsOneLoad() = paparazzi.shootWith("r43k-continue-pending", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx); wait(act)
        call(act, "applyDueRefresh", snapshot(2))
        val ticket = (getField(act, "pending") as PendingAnswers).start()
        var loads = 0
        act.continueDeckStart = { loads++ }
        row(act).getChildAt(1).performClick()
        call(act, "continueDeck")
        assertEquals(0, loads)
        assertFalse(row(act).getChildAt(0).isEnabled); assertFalse(row(act).getChildAt(1).isEnabled)
        ticket.finish()
        assertEquals(1, loads)
        root
    }

    @Test fun disappearingReturnedCardRemovesTheOfferAndRestoresNextDeck() = paparazzi.shootWith("r43k-end-no-return", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx); wait(act)
        call(act, "applyDueRefresh", snapshot(1)); call(act, "applyDueRefresh", snapshot())
        assertFalse(ShopCardKit.texts(root).contains("1 card is back"))
        assertEquals("Next deck, Rivers", row(act).getChildAt(1).contentDescription)
        assertEquals(listOf("0", "0", "0"), counts(act))
        root
    }

    @Test fun summaryRefreshAddsOneOfferAndUpdatesRemainingCountsWithoutDuplicatingIt() = paparazzi.shootWith("r43k-summary-idle", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx)
        call(act, "summary", listOf(deck, next), 9, 4)
        call(act, "applyDueRefresh", snapshot(1, 2, 3))
        val summary = getField(act, "summaryView")
        call(act, "applyDueRefresh", snapshot(1, 2, 3))
        assertSame(summary, getField(act, "summaryView"))
        assertEquals(1, ShopCardKit.texts(buttons(act)).count { it == "1 card is back · Continue" })
        assertEquals(snapshot(1, 2, 3).args, getField(act, "lastSummary"))
        var loads = 0; act.continueDeckStart = { loads++ }
        (getField(act, "summaryContinue") as View).performClick()
        assertEquals(1, loads)
        root
    }

    @Test fun failedFinishCheckKeepsTheEndScreenAndCanBeRetried() = paparazzi.shootWith("r43k-end-retry", listOf(Size.PHONE)) { ctx ->
        val (act, root) = screen(ctx); wait(act)
        act.finishQueueCheck = { it(Result.failure(IllegalStateException("Provider unavailable"))) }
        row(act).getChildAt(0).performClick()
        assertFalse(getField(act, "finished") as Boolean)
        assertNull(getField(act, "lastSummary"))
        assertTrue(ShopCardKit.texts(root).any { it.contains("Provider unavailable") })
        assertTrue(row(act).getChildAt(0).isEnabled)
        call(act, "applyDueRefresh", snapshot())
        assertFalse(ShopCardKit.texts(root).any { it.contains("Provider unavailable") })
        act.finishQueueCheck = { it(Result.success(snapshot(0, learn = 2, fresh = 3))) }
        row(act).getChildAt(0).performClick()
        assertTrue(getField(act, "finished") as Boolean)
        assertEquals(snapshot(0, 2, 3).args, getField(act, "lastSummary"))
        root
    }
}
