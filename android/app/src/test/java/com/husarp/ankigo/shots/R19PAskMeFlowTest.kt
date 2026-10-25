package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.CatchAskPopup
import com.husarp.ankigo.ui.EventPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-P (PLAN 281): the real [EventPlayer] hands the question to [CatchAskPopup.offer] exactly when a fight's batch says one waits
 * ([GameEvent.Defeated.ask] / [GameEvent.Duplicate.ask]), behind the rewards of that batch, and never for a plain defeat. (What the offer
 * does with a context that has no full screen is checked in R19PAskMeShots.noPopUpWithoutAFullScreen.)
 */
class R19PAskMeFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { CatchAskPopup.offerSpy = null; BattleShotKit.reset() }

    private fun ctx(reduceMotion: Boolean): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduceMotion).commit()
        return c
    }

    private fun measure(v: View) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private var stop = 0L
    private fun runAll(c: Context, player: EventPlayer, v: View) {
        var n = 0
        do {
            repeat(3) { i ->
                stop += 60_000
                paparazzi.snapshot(FrameLayout(c).apply { if (n == 0 && i == 0 && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r19p-flow-advance", stop)
            }
        } while (!player.idle && n++ < 12)
    }

    private fun batch(ask: Boolean) = listOf(
        GameEvent.Counted(3, 10, 6, 1, false), GameEvent.Defeated(16, "Birdie", 84, ask),
        GameEvent.XpGained("m", "Mainy", 84, 7, 0, 100), GameEvent.Cash(10, 20, CashKind.DEFEAT),
        GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2"),
    )

    private fun play(reduceMotion: Boolean, ask: Boolean): Int {
        val c = ctx(reduceMotion)
        val calls = ArrayList<Context>()
        CatchAskPopup.offerSpy = { calls += it }
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        player.enqueue(batch(ask))
        runAll(c, player, panel)
        assertTrue("every event played", player.idle)
        assertTrue("the question is offered over the panel's own context", calls.all { it === panel.context })
        return calls.size
    }

    @Test fun anAskedFightOffersTheQuestionOnceItsRewardsPlayed() {
        assertEquals(1, play(reduceMotion = false, ask = true))
    }

    @Test fun anAskedFightOffersItWithReducedMotionToo() {
        assertEquals(1, play(reduceMotion = true, ask = true))
    }

    @Test fun aPlainDefeatOffersNothing() {
        assertEquals(0, play(reduceMotion = false, ask = false))
    }
}
