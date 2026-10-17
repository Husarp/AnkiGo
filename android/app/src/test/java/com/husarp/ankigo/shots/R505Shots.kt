package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.XpParticles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R5-05: still frames of the XP animation (pick 38A: sparks from the defeated Pokémon into ours + the "+N XP" number), and its setting. */
class R505Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun xpAfterLayout(arena: ArenaView, atMs: Float) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                arena.xpAbsorbStill(48, atMs)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun full(ctx: Context, atMs: Float): View {
        val b = BattleShotKit.bound(ctx)
        xpAfterLayout(b.arena!!, atMs)
        return b.root
    }

    private fun small(ctx: Context, atMs: Float): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        xpAfterLayout(p.arena, atMs)
        return p
    }

    // mid-flight (the number is counting), the last sparks landing, the number fading
    @Test fun midFull() = paparazzi.shootWith("r505-mid", listOf(Size.PHONE)) { ctx -> full(ctx, 1300f) }
    @Test fun lateFull() = paparazzi.shootWith("r505-late", listOf(Size.PHONE)) { ctx -> full(ctx, 2750f) }
    @Test fun popFull() = paparazzi.shootWith("r505-pop", listOf(Size.PHONE)) { ctx -> full(ctx, 500f) }
    // our Pokémon white from the first landings
    @Test fun landFull() = paparazzi.shootWith("r505-land", listOf(Size.PHONE)) { ctx -> full(ctx, XpParticles.plan(XpParticles.MOCK_SEED).firstLanding + 40f) }
    @Test fun landSmall() = paparazzi.shootWith("r505-land-small", listOf(Size.FLOATBATTLE)) { ctx -> small(ctx, XpParticles.plan(XpParticles.MOCK_SEED).firstLanding + 40f) }
    @Test fun midSmall() = paparazzi.shootWith("r505-mid-small", listOf(Size.FLOATBATTLE)) { ctx -> small(ctx, 1300f) }
    @Test fun lateSmall() = paparazzi.shootWith("r505-late-small", listOf(Size.FLOATBATTLE)) { ctx -> small(ctx, 2750f) }
    @Test fun midSplit() = paparazzi.shootWith("r505-mid-split", listOf(Size.SPLIT)) { ctx -> full(ctx, 1300f) }

    private fun appCtx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        return c
    }

    @Test fun theSettingIsOnByDefaultAndKeepsItsValue() {
        val c = appCtx()
        c.getSharedPreferences("ankigo_ui", 0).edit().clear().commit()
        assertTrue(AppPrefs.xpAnimation(c))
        AppPrefs.setXpAnimation(c, false)
        assertFalse(AppPrefs.xpAnimation(c))
        AppPrefs.setXpAnimation(c, true)
        assertTrue(AppPrefs.xpAnimation(c))
    }

    @Test fun reducedMotionStartsNothingAndTheBarDoesNotWait() {
        val c = appCtx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val panel = BattlePanelView(c)
        assertEquals(0L, panel.arena.xpAbsorb(48, 1f))
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
    }

    @Test fun noXpOrNotOnScreenStartsNothing() {
        val c = appCtx()
        val panel = BattlePanelView(c)
        assertEquals(0L, panel.arena.xpAbsorb(0, 1f))      // nothing gained
        assertEquals(0L, panel.arena.xpAbsorb(48, 1f))     // not attached to a window
    }
}
