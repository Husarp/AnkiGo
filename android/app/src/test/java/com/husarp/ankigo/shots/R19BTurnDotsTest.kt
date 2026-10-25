package com.husarp.ankigo.shots

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-B (PLAN 275): the turn dots in picture-in-picture, split screen and the floating battle window (off on the full-screen Battle tab),
 * following the real charge there (restored from the save, advanced by the played events), and the small form on a narrow bar.
 * Runs on Paparazzi's Android Context like the other view tests.
 */
class R19BTurnDotsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun ctx(size: Size = Size.PHONE): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return c
    }

    private fun bar(p: BattlePanelView): ActionBarView = BattlePanelView::class.java.getDeclaredField("logBox").apply { isAccessible = true }.get(p) as ActionBarView
    private fun dotsText(b: ActionBarView): String {
        val f = ActionBarView::class.java.getDeclaredField("dots").apply { isAccessible = true }
        return (f.get(b) as View).contentDescription.toString()
    }
    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }
    private fun counted(button: Int) = GameEvent.Counted(button, 10, 1, 1, false)
    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    @Test fun aPlainPanelHasNoDotsAndSwitchingThemOnAndOffWorks() {
        val p = BattlePanelView(ctx()).also { it.renderAll() }
        val b = bar(p)
        assertFalse("the full Battle tab: no dots", b.dotsOn)
        assertEquals(listOf("mine", "theirs"), b.segmentOrder())
        p.setCompact(true)
        p.setTurnDots(true)   // picture-in-picture / split screen / the floating window
        assertTrue(b.dotsOn)
        assertEquals(listOf("mine", "dots", "theirs"), b.segmentOrder())
        p.setTurnDots(false)
        assertFalse(b.dotsOn)
        assertEquals(listOf("mine", "theirs"), b.segmentOrder())
    }

    @Test fun theStudyScreenKeepsItsDotsWhateverIsSet() {
        val p = BattlePanelView(ctx()).also { it.setStudyLayout(false, false); it.renderAll() }
        p.setTurnDots(false)
        assertTrue(bar(p).dotsOn)
    }

    @Test fun theChargeIsRestoredFromTheSaveAndAdvancedByEventsOutsideTheStudyScreen() {
        val c = ctx()
        val p = BattlePanelView(c).also { it.renderAll() }
        p.setTurnDots(true)
        val battle = GameRepo.state!!.battle!!
        battle.answers = 1; battle.tally = 10
        p.restoreStudyCharge()
        assertEquals("Battle charge 1 of 2", dotsText(bar(p)))
        // the next answer completes the round
        p.observeStudyEvent(GameEvent.Charge(10, 2, 2)); p.observeStudyEvent(counted(4))
        assertEquals("Battle charge 2 of 2", dotsText(bar(p)))
        p.observeStudyEvent(GameEvent.RoundStart(1.0, 1, true))
        p.observeStudyEvent(counted(3))   // the next round begins: the first pip of a fresh round
        assertEquals("Battle charge 1 of 2", dotsText(bar(p)))
    }

    @Test fun anEventsFeedAWindowThatShowsDotsLaterWithoutAJump() {
        val p = BattlePanelView(ctx()).also { it.renderAll() }
        p.observeStudyEvent(counted(3))        // on the Battle tab: no dots, but the charge is followed
        p.setTurnDots(true)                    // PiP entered
        assertEquals("Battle charge 1 of 2", dotsText(bar(p)))
    }

    @Test fun theDotsTakeTheirSmallFormOnANarrowBarAndTheFullFormWhenItFits() {
        val c = ctx()
        val p = BattlePanelView(c).also { it.renderAll(); it.setTurnDots(true) }
        val b = bar(p)
        measure(b, ActionBarView.NARROW_DP - 1, 34)
        assertTrue("narrow: small dots", b.dotsSmall())
        measure(b, ActionBarView.NARROW_DP + 1, 34)
        assertFalse("wide enough: the normal dots", b.dotsSmall())
        measure(b, 220, 34)
        assertTrue(b.dotsSmall())
    }

    @Test fun theFloatingBattleWindowShowsTheDotsFromTheSave() {
        val c = ctx(Size.FLOATBATTLE)
        val battle = GameRepo.state!!.battle!!
        battle.answers = 1; battle.tally = 20
        val self = object : ContextWrapper(c) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        val bind = v.javaClass.getDeclaredMethod("bind").apply { isAccessible = true }
        bind.invoke(v)
        val panel: BattlePanelView = field(v, "panel")
        assertTrue(bar(panel).dotsOn)
        assertEquals("Battle charge 1 of 2", dotsText(bar(panel)))
    }

    @Test fun theGameScreenShowsTheDotsInPictureInPictureAndNotOnTheBattleTab() {
        val c = ctx()
        val b = BattleShotKit.bound(c)
        assertFalse("full-screen Battle tab", bar(b.panel!!).dotsOn)
        b.setField("inPip", true)
        b.call("updateTurnDots")
        assertTrue(bar(b.panel!!).dotsOn)
        b.setField("inPip", false)
        b.call("updateTurnDots")
        assertFalse(bar(b.panel!!).dotsOn)
    }
}
