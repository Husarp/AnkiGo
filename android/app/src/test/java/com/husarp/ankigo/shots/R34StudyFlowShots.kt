package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.NextDeck
import com.husarp.ankigo.study.PendingAnswers
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R34StudyFlowShots {
    @get:Rule val paparazzi = Shots.rule()
    private val done = Anki.Deck(2, "Geography::Capitals", 0, 0, 0, false)
    private val due = Anki.Deck(3, "Geography::Rivers", 0, 4, 0, false)

    private fun finishScreen(ctx: Context, decks: List<Anki.Deck>): Pair<StudyActivity, View> {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", done.id); setField(act, "deckName", done.name)
        val root = act.buildContent()
        act.finishQueueCheck = { it(Result.success(true)) }
        call(act, "layout")
        (getField(act, "panel") as BattlePanelView).apply { setStudyLayout(false, false); renderAll() }
        call(act, "waitForFinish", Triple(decks, 0, 0))
        val density = ctx.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((Size.PHONE.wDp * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((Size.PHONE.hDp * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bounds = android.graphics.Rect()
        assertTrue("the deck-end buttons must be on screen", finishRow(act).getGlobalVisibleRect(bounds))
        assertTrue(bounds.height() >= Ui.dp(act, 48))
        assertTrue(bounds.bottom <= root.height)
        return act to root
    }
    private fun finishRow(act: StudyActivity): ViewGroup {
        val buttons = getField(act, "buttons") as ViewGroup
        return if (buttons.getChildAt(0).contentDescription == null) buttons.getChildAt(0) as ViewGroup else buttons
    }

    @Test fun finishAndNextDeckLightAndDark() {
        paparazzi.shootWith("b-deck-end-next", listOf(Size.PHONE)) { ctx ->
            val (act, root) = finishScreen(ctx, listOf(done, due))
            val row = finishRow(act)
            assertEquals(2, row.childCount)
            assertEquals("Finish deck, see the summary", row.getChildAt(0).contentDescription)
            assertEquals("Next deck, Rivers", row.getChildAt(1).contentDescription)
            assertTrue(row.getChildAt(0).isEnabled); assertTrue(row.getChildAt(1).isEnabled)
            root
        }
    }

    @Test fun finishOnlyWhenNoOtherDeckIsDue() {
        paparazzi.shootWith("b-deck-end-only-finish", listOf(Size.PHONE)) { ctx ->
            val (act, root) = finishScreen(ctx, listOf(done, due.copy(review = 0)))
            assertEquals(1, finishRow(act).childCount)
            assertFalse(ShopCardKit.texts(root).contains("Next deck"))
            root
        }
    }

    @Test fun nextDeckWaitsForRewardsNavigatesDirectlyAndIgnoresDoubleTap() {
        paparazzi.shootWith("b-deck-end-next-pending", listOf(Size.PHONE)) { ctx ->
            val (act, root) = finishScreen(ctx, listOf(done, due))
            val ticket = (getField(act, "pending") as PendingAnswers).start()
            val opened = ArrayList<NextDeck.D>()
            act.nextDeckStart = { opened += it }
            finishRow(act).getChildAt(1).performClick()
            assertTrue(opened.isEmpty())
            assertFalse(finishRow(act).getChildAt(0).isEnabled)
            assertFalse(finishRow(act).getChildAt(1).isEnabled)
            call(act, "nextDeck", NextDeck.D(due.id, due.name, due.total))
            ticket.finish()
            assertEquals(listOf(NextDeck.D(due.id, due.name, due.total)), opened)
            assertNull(getField(act, "lastSummary"))
            root
        }
    }

    private class CapturingActivity : Activity() {
        val opened = ArrayList<Intent>()
        override fun startActivity(intent: Intent) { opened += intent }
    }

    private fun home(ctx: Context, decks: List<Anki.Deck>, lastId: Long = done.id): Triple<StudyHome, CapturingActivity, FrameLayout> {
        val act = attach(CapturingActivity(), ShotCtx(ctx))
        act.getSharedPreferences("study", 0).edit().putLong("lastDeck", lastId).apply()
        val home = StudyHome(act, object : StudyHome.Host {
            override fun studyInAnkiDroid(deckId: Long) {}
            override fun askAnkiAccess() {}
        }).apply { loadsInBackground = false }
        val body = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(act, com.husarp.ankigo.R.color.ag_base))
            addView(home.buildAppBar())
            addView(home.buildBody(), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(BottomNav.create(act, BottomNav.Tab.STUDY))
        }
        val root = FrameLayout(act).apply { addView(body) }
        setField(home, "decks", decks)
        home.render()
        return Triple(home, act, root)
    }
    private fun resume(home: StudyHome) = (getField(home, "resumeSlot") as ViewGroup).getChildAt(0) as ViewGroup

    @Test fun continueKeepsTheCurrentDeckAndOpensIt() {
        paparazzi.shootWith("b-study-continue-current", listOf(Size.PHONE)) { ctx ->
            val (home, act, root) = home(ctx, listOf(done.copy(learn = 1), due))
            val resume = resume(home)
            assertTrue(resume.contentDescription.toString().contains(done.name))
            resume.performClick()
            assertEquals(done.id, act.opened.single().getLongExtra("deckId", 0))
            root
        }
    }

    @Test fun continueSuggestsTheSameNextDeckAndBothTapTargetsOpenIt() {
        paparazzi.shootWith("b-study-continue-next", listOf(Size.PHONE)) { ctx ->
            val (home, act, root) = home(ctx, listOf(done, due))
            val resume = resume(home)
            assertTrue(resume.contentDescription.toString().contains(due.name))
            assertTrue(ShopCardKit.texts(resume).contains("CONTINUE"))
            resume.performClick(); resume.getChildAt(1).performClick()
            assertEquals(listOf(due.id, due.id), act.opened.map { it.getLongExtra("deckId", 0) })
            root
        }
    }

    @Test fun noReviewsGreyContinueStaysClickableAndGivesTheUsualBottomAlert() {
        paparazzi.shootWith("b-study-continue-all-done", listOf(Size.PHONE)) { ctx ->
            val (home, act, root) = home(ctx, listOf(done, due.copy(review = 0)))
            val resume = resume(home)
            assertTrue(resume.isEnabled && resume.isClickable)
            resume.performClick()
            assertEquals("No reviews left today. Try Custom study.", Ui.lastToast)
            resume.getChildAt(1).performClick()
            assertEquals("No reviews left today. Try Custom study.", Ui.lastToast)
            assertTrue(act.opened.isEmpty())
            AppToast.show(root, Ui.lastToast!!)
            root
        }
    }
}
