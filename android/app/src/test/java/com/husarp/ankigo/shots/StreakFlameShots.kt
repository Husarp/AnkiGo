package com.husarp.ankigo.shots

import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R7-06: the streak flame (the still frame in the renderer) on the battle panel chips row and the Study summary chip,
 * with a streak of 12 and of 0. Light + dark. PNGs: streakflame-battle-<n>-<size>-<theme>, streakflame-study-<n>-...
 */
class StreakFlameShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun setStreak(n: Int) {
        val g = GameRepo.game!!
        GameRepo.state!!.streakDays = n
        GameRepo.state!!.lastGoalDay = g.dayKey()
    }

    @Test fun battleChips() {
        for (n in listOf(12, 0)) paparazzi.shootWith("streakflame-battle-$n", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            setStreak(n)
            BattleShotKit.bound(ctx).root
        }
    }

    @Test fun studySummary() {
        for (n in listOf(12, 0)) paparazzi.shootWith("streakflame-study-$n", listOf(Size.PHONE)) { ctx ->
            setStreak(n)
            val act = attach(StudyActivity(), ShotCtx(ctx))
            setField(act, "deckId", 2L)
            setField(act, "deckName", "Geography::Capitals")
            val root = act.buildContent()
            call(act, "summary", StudyDeckKit.decks, 0, 3)
            root
        }
    }
}
