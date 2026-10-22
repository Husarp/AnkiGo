package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.DotsPlace
import com.husarp.ankigo.ui.LogKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R15-B (PLAN 211): the action bar before the first answer of a fight already has the current look: the segments
 * [my move | dots | their move] with empty halves, never the old whole-width "READY" line. PNGs r15b-*, light + dark.
 */
class R15BFirstBarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun bar(p: BattlePanelView): ActionBarView = BattlePanelView::class.java.getDeclaredField("logBox").apply { isAccessible = true }.get(p) as ActionBarView

    private fun quiet(ctx: Context) { ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() }

    private fun studyPanel(ctx: Context, strip: Boolean): BattlePanelView =
        BattlePanelView(ctx).also { it.setStudyLayout(strip, false); it.renderAll(); it.restoreStudyCharge() }

    private fun shoot(name: String, size: Size, dots: DotsPlace = DotsPlace.MIDDLE, build: (Context) -> View) =
        paparazzi.shootWith(name, listOf(size)) { ctx -> quiet(ctx); AppPrefs.setTurnDots(ctx, dots); build(ctx) }

    @Test fun freshStudyPanel() = shoot("r15b-study", Size.SPLIT) { studyPanel(it, false) }
    @Test fun freshStudyStrip() = shoot("r15b-strip", Size.PIP_169M) { studyPanel(it, true) }
    @Test fun freshStudyStripDotsRight() = shoot("r15b-strip-right", Size.PIP_169M, DotsPlace.RIGHT_END) { studyPanel(it, true) }
    @Test fun freshStudyPanelDotsRight() = shoot("r15b-study-right", Size.SPLIT, DotsPlace.RIGHT_END) { studyPanel(it, false) }
    @Test fun freshBattleTab() = shoot("r15b-battle", Size.PHONE) { BattleShotKit.bound(it).root }
    @Test fun freshBattleTabDotsRight() = shoot("r15b-battle-right", Size.PHONE, DotsPlace.RIGHT_END) { BattleShotKit.bound(it).root }

    @Test fun aFreshPanelShowsTheSegmentsNotALine() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        quiet(ctx)
        for (place in DotsPlace.entries) {
            AppPrefs.setTurnDots(ctx, place)
            val b = bar(studyPanel(ctx, false))
            assertFalse("no legacy line before the first answer", b.lineShown())
            b.dotsOn = true; b.dotsPlace = place
            assertEquals(if (place == DotsPlace.RIGHT_END) listOf("mine", "theirs", "dots") else listOf("mine", "dots", "theirs"), b.segmentOrder())
        }
    }

    @Test fun aNewsLineStillTakesTheWholeBarAndAMoveBringsTheSegmentsBack() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val p = studyPanel(ctx, false)
        val b = bar(p)
        assertFalse(b.lineShown())
        p.setLog(LogKind.SYS, "FAST", "Played fast: 3 answers", false)
        assertTrue(b.lineShown())
        p.observeStudyEvent(GameEvent.Attack(true, "A", "B", "Zap", Type.ELECTRIC, Category.SPECIAL, 3, 1, 1.0, false, false, false, 10, 30, 20, 30))
        assertFalse(b.lineShown())
    }
}
