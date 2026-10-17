package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.Rule
import org.junit.Test

/**
 * R6-09: the "Continue" after a fight result. PNGs: r609-study-question / r609-study-answer (the Study screen with the result tiles
 * held: Continue in the answer row, on the question side and over the answer buttons), r609-battle (the full battle screen: the
 * Continue pill on the NEXT line of the held tiles). The floating battle, the picture-in-picture window and the Pokémon tab's
 * card are small windows: their results run on the 4 s timeout and never hold, so they have no Continue to show.
 */
class R609Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun reduced(ctx: Context) = ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
    private fun encounter() = GameEvent.Encounter(1, "Sparkmouse", 5, false, Tier.NORMAL, 10, 10, false, false)
    private fun caught() = listOf<GameEvent>(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.BallThrow("great-ball", 12, true, 1),
        GameEvent.Caught("w1", 147, "Sparkmouse", 12, false, "great-ball", true, true),
        GameEvent.XpGained("m", "Puddlet", 120, 8, 0, 100), GameEvent.Cash(10, 1240, CashKind.DEFEAT), encounter(),
    )

    private fun study(ctx: Context, flipped: Boolean): View {
        reduced(ctx)
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.setStudyLayout(false, false)
        panel.renderAll(); panel.restoreStudyCharge()
        (getField(act, "events") as EventPlayer).enqueue(caught())
        call(act, "showButtons")
        return root
    }

    @Test fun studyQuestion() = paparazzi.shootWith("r609-study-question", listOf(Size.PHONE)) { study(it, false) }
    @Test fun studyAnswer() = paparazzi.shootWith("r609-study-answer", listOf(Size.PHONE)) { study(it, true) }

    @Test fun battleScreen() = paparazzi.shootWith("r609-battle", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 400) { ctx ->
        reduced(ctx)
        val b = BattleShotKit.bound(ctx)
        b.player!!.enqueue(caught())
        b.root
    }
}
