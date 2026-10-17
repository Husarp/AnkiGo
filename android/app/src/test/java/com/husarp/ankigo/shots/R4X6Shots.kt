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
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/* R4-X6: floating battle window option C: header (Menu + Close), open menu, paused pill (r4x6-*). Light + dark. */
class R4X6Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    private fun self(ctx: Context): Context = object : android.content.ContextWrapper(ctx) {
        override fun getApplicationContext(): Context = this
    }

    private fun scene(ctx: Context, menu: Boolean, paused: Boolean): View {
        val v = FloatBattleView(self(ctx), {}, {}, {})
        v.setPaused(paused)
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

    private fun shootSize(name: String, size: Size, menu: Boolean, paused: Boolean) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val frame = FrameLayout(ctx).apply { addView(scene(ctx, menu, paused), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun header() = shootSize("r4x6-float", Size.FLOATBATTLE, false, false)
    @Test fun menuOpen() = shootSize("r4x6-float-menu", Size.FLOAT, true, false)
    @Test fun pausedPill() = shootSize("r4x6-float-paused", Size.FLOATBATTLE, false, true)
    @Test fun pausedMenu() = shootSize("r4x6-float-paused-menu", Size.FLOAT, true, true)
}
