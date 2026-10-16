package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.overlay.FloatPage
import com.husarp.ankigo.overlay.FloatScreens
import com.husarp.ankigo.overlay.FloatWindow
import com.husarp.ankigo.ui.MiniBattleView
import org.junit.Rule
import org.junit.Test

/*
 * Floating windows. The real code adds the window's root View to WindowManager; here the same View tree is taken from
 * the FloatWindow object (private `root`, by reflection) and shot as is, so frame, title bar, tabs, buttons, resize
 * grip and the hosted page are the real ones. No main-code changes.
 */
class FloatShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    private fun call(o: Any, name: String, vararg args: Pair<Class<*>, Any?>): Any? {
        val m = o.javaClass.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
        m.isAccessible = true
        return m.invoke(o, *args.map { it.second }.toTypedArray())
    }

    /** FloatWindow themes `ctx.applicationContext`; in the renderer that is a stub, so hand it a context that returns itself. */
    private fun self(ctx: Context): Context = object : android.content.ContextWrapper(ctx) {
        override fun getApplicationContext(): Context = this
    }

    private fun rootOf(w: FloatWindow): View = field(w, "root")

    /** One shot per theme at [size]; [prepare] runs after FakeGame.install and returns the view to shoot. */
    private fun shootEach(name: String, size: Size, prepare: (Context) -> View, setup: () -> Unit = {}) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            setup()
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val v = prepare(ctx)
            val frame = FrameLayout(ctx).apply {
                addView(v, FrameLayout.LayoutParams(-1, -1))
            }
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    private fun screen(ctx: Context, which: FloatPage): View {
        call(FloatScreens, "teardown")
        val w = call(FloatScreens, "create", Context::class.java to self(ctx)) as FloatWindow
        call(FloatScreens, "showPage", FloatWindow::class.java to w, FloatPage::class.java to which, String::class.java to null)
        return rootOf(w)
    }

    @Test fun floatTeam() = shootEach("float-team", Size.FLOAT, { screen(it, FloatPage.TEAM) })
    @Test fun floatDex() = shootEach("float-dex", Size.FLOAT, { screen(it, FloatPage.DEX) })
    @Test fun floatShop() = shootEach("float-shop", Size.FLOAT, { screen(it, FloatPage.SHOP) })
    @Test fun floatSettings() = shootEach("float-settings", Size.FLOAT, { screen(it, FloatPage.SETTINGS) })

    /** Floating battle window: FloatBattleView (internal) with its real FloatWindow + compact BattlePanelView. */
    private fun battle(ctx: Context, paused: Boolean = false): View {
        val v = FloatBattleView(self(ctx), {}, {}, {})
        v.setPaused(paused)
        call(v, "bind")
        val panel: com.husarp.ankigo.ui.BattlePanelView? = field(v, "panel")
        panel?.renderAll() // FloatBattleView.show() does this once the window is showing
        panel?.setLog(com.husarp.ankigo.ui.LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}! It's super effective.", false)
        Thread.sleep(400) // PanelSprites decodes on a background thread, then posts to the main looper (drained by snapshot)
        return rootOf(field(v, "window"))
    }

    @Test fun floatBattle() = shootEach("float-battle", Size.FLOATBATTLE, { battle(it) })
    @Test fun floatBattlePaused() = shootEach("float-battle-paused", Size.FLOATBATTLE, { battle(it, paused = true) })
    @Test fun floatBattleNoSprites() = shootEach("float-battle-nosprites", Size.FLOATBATTLE, { battle(it) },
        setup = { FakeGame.withSprites = false })
    @Test fun floatBattleNoBattle() = shootEach("float-battle-idle", Size.FLOATBATTLE, { ctx ->
        GameRepo.state!!.battle = null
        battle(ctx)
    })
    /** Game not READY: the window shows a short text instead of the panel. */
    @Test fun floatBattleNoData() = shootEach("float-battle-nodata", Size.FLOATBATTLE, { battle(it) }, setup = { FakeGame.scenario = FakeGame.Scenario.NO_DATA })

    /** The one-line sprites + HP strip (MiniBattleView; not used by any window right now). */
    @Test fun miniBattle() = shootEach("mini-battle", Size.PIP, { ctx ->
        MiniBattleView(ctx).also { it.render() }
    })
}
