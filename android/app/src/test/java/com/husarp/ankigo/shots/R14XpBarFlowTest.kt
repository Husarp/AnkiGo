package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R14 PLAN 138: through the real [EventPlayer] / [ArenaView], a gain that crosses a level plays "fill to the end, hold, the level
 * number changes, the bar starts from EMPTY and slides on", once per level (the arena's [xpTrace] hears what the bar and level are told).
 * Without orbs (the renderer has no window: `xpOnScreen` false) the bar is slid by the player itself, so the loop is what is tested.
 */
class R14XpBarFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun ctx(xpAnimation: Boolean = true): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        com.husarp.ankigo.ui.AppPrefs.setXpAnimation(c, xpAnimation)
        return c
    }

    private fun measure(v: View) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private var stop = 0L
    private fun advance(c: Context, v: View? = null) {
        repeat(3) { i ->
            stop += 60_000
            paparazzi.snapshot(FrameLayout(c).apply { if (i == 0 && v != null && v.parent == null) addView(v, FrameLayout.LayoutParams(-1, -1)) }, "r14-xpbar-advance", stop)
        }
    }

    private fun runAll(c: Context, player: EventPlayer, v: View) {
        advance(c, v)
        var n = 0
        while (!player.idle && n++ < 12) advance(c)
    }

    /** Plays [events] on the main; returns what the XP bar and level number were told, in order. */
    private fun play(xpAnimation: Boolean, events: (uid: String, level: Int) -> List<GameEvent>): Pair<List<String>, Int> {
        val c = ctx(xpAnimation)
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel, continueOnPanel = false)
        panel.renderAll()
        measure(panel)
        val arena = panel.arena
        arena.xpOnScreen = { false }   // no orbs: the player slides the bar itself
        val trace = ArrayList<String>()
        arena.xpTrace = { trace += it }
        val level = arena.playerLevel
        player.enqueue(events(GameRepo.state!!.main!!.uid, level))
        runAll(c, player, panel)
        assertTrue("every event played", player.idle)
        // the redraw of the saved game after the events ("bar 0.62", the unchanged save) is not part of the XP play
        return trace.dropLastWhile { it == "bar 0.62" } to level
    }

    private fun up(uid: String, level: Int) = GameEvent.LevelUp(uid, "Mainy", level, 30, 40)

    @Test fun oneLevelFillsToTheEndHoldsChangesTheLevelAndRestartsFromEmpty() {
        val (trace, lv) = play(true) { uid, l ->
            listOf(GameEvent.XpGained(uid, "Mainy", 60, l + 1, 20, 100, 80, listOf(90)), up(uid, l + 1))
        }
        val i = trace.indexOf("bar 1.00 slide")
        assertTrue(trace.toString(), i >= 0)
        assertEquals(listOf("bar 1.00 slide", "level ${lv + 1}", "bar 0.00", "bar 0.20 slide"), trace.subList(i, i + 4))
        assertEquals("the final value is the last bar", "bar 0.20 slide", trace.last { it.startsWith("bar") })
        assertEquals("the LevelUp event does not set the level a second time: $trace", 1, trace.count { it.startsWith("level") })
    }

    @Test fun severalLevelsRunOneLoopPerLevel() {
        val (trace, lv) = play(true) { uid, l ->
            listOf(GameEvent.XpGained(uid, "Mainy", 500, l + 3, 50, 100, 70, listOf(90, 95, 100)), up(uid, l + 1), up(uid, l + 2), up(uid, l + 3))
        }
        val i = trace.indexOf("bar 1.00 slide")
        assertEquals(
            listOf("bar 1.00 slide", "level ${lv + 1}", "bar 0.00", "bar 1.00 slide", "level ${lv + 2}", "bar 0.00", "bar 1.00 slide",
                "level ${lv + 3}", "bar 0.00", "bar 0.50 slide"),
            trace.subList(i, i + 10))
        assertEquals(3, trace.count { it.startsWith("level") })
    }

    @Test fun aGainThatLandsExactlyOnALevelEndsOnAnEmptyBarInTheNewLevel() {
        val (trace, lv) = play(true) { uid, l ->
            listOf(GameEvent.XpGained(uid, "Mainy", 20, l + 1, 0, 100, 80, listOf(100)), up(uid, l + 1))
        }
        val i = trace.indexOf("bar 1.00 slide")
        assertEquals(listOf("bar 1.00 slide", "level ${lv + 1}", "bar 0.00", "bar 0.00"), trace.subList(i, i + 4))
    }

    @Test fun withTheXpAnimationOffTheEndStateComesAtOnce() {
        val (trace, lv) = play(false) { uid, l ->
            listOf(GameEvent.XpGained(uid, "Mainy", 60, l + 1, 20, 100, 80, listOf(90)), up(uid, l + 1))
        }
        assertEquals("the bar is told the final value once, without a slide; the LevelUp event sets the level", listOf("bar 0.20", "level ${lv + 1}"), trace)
    }

    @Test fun aGainThatStaysInTheLevelIsStillOnePlainSlide() {
        val (trace, _) = play(true) { uid, l -> listOf(GameEvent.XpGained(uid, "Mainy", 18, l, 80, 100, 62)) }
        assertEquals(listOf("bar 0.80 slide"), trace)
    }
}
