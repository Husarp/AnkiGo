package com.husarp.ankigo.shots

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.DotsPlace
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R19-B (PLAN 275): the turn dots in picture-in-picture, split screen and the floating battle window, light + dark. PNGs r19b-*.
 * Sizes in dp: PiP 3:2 240 x 160 and 16:9 240 x 135, a small PiP 160 x 107, the split half (412 x 400), the floating window's
 * default (240 x 190) and smallest (170 x 130) size.
 */
class R19BTurnDotsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun device(w: Int, h: Int, theme: Theme): DeviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = w * 2, screenHeight = h * 2, xdpi = 320, ydpi = 320, density = Density.XHIGH, nightMode = theme.night,
        orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    private fun shootAt(name: String, w: Int, h: Int, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(w, h, theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val v = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(v, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${theme.name.lowercase()}", 1000)
        }
    }

    /** [filled] answers already given this round (0, 1 or 2 of 2), as the save holds them. */
    private fun save(filled: Int) {
        val b = GameRepo.state!!.battle!!
        b.answers = filled.coerceAtMost(1); b.tally = if (filled == 0) 0 else 10
    }

    private fun counted(button: Int) = GameEvent.Counted(button, 10, 1, 1, false)

    /** Picture-in-picture as onPictureInPictureModeChanged(true) sets it up, with the dots. */
    private fun pip(ctx: Context, filled: Int, dots: DotsPlace = DotsPlace.MIDDLE, hDp: Float = 136f): View {
        AppPrefs.setTurnDots(ctx, dots)
        save(filled)
        val b = BattleShotKit.bound(ctx)
        b.setField("inPip", true)
        b.panel?.setCompact(true)
        b.relayout(hDp)
        b.call("updateTurnDots")
        if (filled == 2) { b.panel?.observeStudyEvent(GameEvent.Charge(10, 2, 2)); b.panel?.observeStudyEvent(counted(4)) }
        return b.root
    }

    @Test fun pipEmpty() = shootAt("r19b-pip-3x2-empty", 240, 160) { pip(it, 0, hDp = 160f) }
    @Test fun pipHalf() = shootAt("r19b-pip-3x2-half", 240, 160) { pip(it, 1, hDp = 160f) }
    @Test fun pipFull() = shootAt("r19b-pip-3x2-full", 240, 160) { pip(it, 2, hDp = 160f) }
    @Test fun pip169() = shootAt("r19b-pip-16x9-half", 240, 135) { pip(it, 1, hDp = 135f) }
    @Test fun pipSmall() = shootAt("r19b-pip-small-half", 160, 107) { pip(it, 1, hDp = 107f) }
    @Test fun pipRight() = shootAt("r19b-pip-3x2-dots-right", 240, 160) { pip(it, 1, DotsPlace.RIGHT_END, 160f) }

    /** The bottom half of a split screen: the dots on, the normal-width bar. */
    private fun split(ctx: Context, filled: Int): View {
        save(filled)
        val b = BattleShotKit.bound(ctx)
        b.panel?.setTurnDots(true)   // GameActivity.updateTurnDots with isInMultiWindowMode
        return b.root
    }

    @Test fun splitHalf() = shootAt("r19b-split-half", 412, 400) { split(it, 1) }
    @Test fun splitShort() = shootAt("r19b-split-short", 412, 300) { split(it, 1) }
    @Test fun tabNoDots() = shootAt("r19b-battle-tab-no-dots", 412, 869) { save(1); BattleShotKit.bound(it).root }

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    private fun floating(ctx: Context, filled: Int): View {
        save(filled)
        val self = object : ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        v.javaClass.getDeclaredMethod("bind").apply { isAccessible = true }.invoke(v)
        val panel: BattlePanelView? = field(v, "panel")
        panel?.renderAll()
        Thread.sleep(400)
        return field<Any>(field(v, "window"), "root") as View
    }

    @Test fun floatDefault() = shootAt("r19b-float-default", 240, 190) { floating(it, 1) }
    @Test fun floatDefaultEmpty() = shootAt("r19b-float-default-empty", 240, 190) { floating(it, 0) }
    @Test fun floatSmallest() = shootAt("r19b-float-smallest", 170, 130) { floating(it, 1) }
}
