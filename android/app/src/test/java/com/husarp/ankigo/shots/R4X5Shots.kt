package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.overlay.FloatWindow
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * R4-X5: Settings → Battle with the "Pop out opens" choice (r4x5-settings-battle) and the CURRENT floating battle window,
 * closed and with its menu open (r4x5-float, r4x5-float-menu), for docs/design/float-window.html. Light + dark.
 */
class R4X5Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun settingsBattle() {
        paparazzi.shootPage("r4x5-settings-battle", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            val v = pageScreen(c, p)
            p.open(SettingsSection.BATTLE)
            v
        }
    }

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    private fun self(ctx: Context): Context = object : android.content.ContextWrapper(ctx) {
        override fun getApplicationContext(): Context = this
    }

    /** The real FloatBattleView's window root (320 x 300 dp) in a frame; with [menu] the real menu card hangs under its menu button. */
    private fun scene(ctx: Context, menu: Boolean): View {
        val v = FloatBattleView(self(ctx), {}, {}, {})
        v.setPaused(false)
        val bind = v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }
        bind.invoke(v)
        val panel: BattlePanelView? = field(v, "panel")
        panel?.renderAll()
        panel?.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}! It's super effective.", false)
        Thread.sleep(400)
        val root: View = field(field<FloatWindow>(v, "window"), "root")
        val frame = FrameLayout(ctx)
        frame.addView(root, FrameLayout.LayoutParams(Ui.dp(ctx, 320), Ui.dp(ctx, 300)))
        if (menu) {
            val m = MenuKit.capture { v.javaClass.getDeclaredMethod("showMenu").also { it.isAccessible = true }.invoke(v) }
            requireNotNull(m) { "no menu was shown" }
            frame.addView(m.content(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).also {
                it.topMargin = Ui.dp(ctx, 36); it.marginEnd = Ui.dp(ctx, 4)
            })
        }
        return frame
    }

    private fun shootSize(name: String, size: Size, menu: Boolean) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val frame = FrameLayout(ctx).apply { addView(scene(ctx, menu), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun floatCurrent() {
        shootSize("r4x5-float", Size.FLOATBATTLE, false)
        shootSize("r4x5-float-menu", Size.FLOAT, true)
    }
}
