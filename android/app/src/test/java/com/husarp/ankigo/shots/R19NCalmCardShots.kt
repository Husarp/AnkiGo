package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-N (PLAN 130, 130A "Quiet words"): the Study screen after a fight ended, the result waiting for Continue; the card area holds the calm
 * card instead of the next card. PNGs: r19n-calm-<size>-<theme> (phone, tablet, split = the short card area), r19n-card-<size>-<theme> (after Continue: the card).
 */
class R19NCalmCardShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun defeatBatch() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(147, "Wildling", 40),
        GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter(),
    )

    private fun screen(ctx: Context, size: Size, held: Boolean): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).putString("timer", "Always").commit()
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
        if (size == Size.TABLET) setField(act, "heightMode", 0)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Mongolia?</div>"); setField(act, "answerHtml", "<div>Ulaanbaatar</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", false); setField(act, "locked", false); setField(act, "background", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.setStudyLayout(false, false)
        panel.renderAll(); panel.restoreStudyCharge()
        val d = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        call(act, "showButtons")
        if (held) {
            (getField(act, "events") as EventPlayer).enqueue(defeatBatch())
            call(act, "showButtons")
            assertTrue("the calm card shows", getField(act, "calmOn") as Boolean)
        }
        root.measure(View.MeasureSpec.makeMeasureSpec((size.wDp * d.density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((size.hDp * d.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private fun sizeOf(c: Context): Size = when {
        c.resources.configuration.smallestScreenWidthDp >= 600 -> Size.TABLET
        c.resources.configuration.screenHeightDp < 600 -> Size.SPLIT
        else -> Size.PHONE
    }

    @Test fun calmCard() = paparazzi.shootWith("r19n-calm", listOf(Size.PHONE, Size.SPLIT, Size.TABLET)) { screen(it, sizeOf(it), true) }
    @Test fun cardAfterContinue() = paparazzi.shootWith("r19n-card", listOf(Size.PHONE)) { screen(it, sizeOf(it), false) }
}
