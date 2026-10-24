package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.MotionPrefs
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R18-C (PLAN 247): Settings -> Battle with the "Pokémon movement" slider (light + dark), and the SpriteView follows the setting:
 * a still picture's idle float starts at Always / Often, never at Never, and a view that does not follow the setting is untouched.
 * PNGs: r18c-battle-settings-<variant>-<light|dark>.
 */
class R18CMotionShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun page(ctx: Context, f: (SettingsPage) -> Unit = {}): View {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        f(p)
        return v
    }

    @Test fun battleSettings() {
        paparazzi.shootPage("r18c-battle-settings", listOf(Variant("tall", 360, 1500))) { ctx ->
            MotionPrefs.setStep(Shots.files(ctx), MotionPrefs.NOW_AND_THEN)
            page(ctx) { it.open(SettingsSection.BATTLE) }
        }
    }

    @Test fun battleSettingsNever() {
        paparazzi.shootPage("r18c-battle-settings-never", listOf(Variant("tall", 360, 1500)), themes = listOf(Theme.LIGHT)) { ctx ->
            MotionPrefs.setStep(Shots.files(ctx), MotionPrefs.NEVER)
            page(ctx) { it.open(SettingsSection.BATTLE) }
        }
    }

    private fun block(): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
        for (y in 30..80) for (x in 20..70) b.setPixel(x, y, 0xFF3366CC.toInt())
    }
    private val still get() = SpriteImage(listOf(block()), intArrayOf(100))

    private fun starts(step: Int?, follow: Boolean): Int {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        c.getSharedPreferences("ankigo_ui", 0).edit().apply { if (step == null) remove("pokemonMovement") else putInt("pokemonMovement", step) }.commit()
        val v = SpriteView(c).apply { idleMotion = true; followMovement = follow; setImage(still) }
        paparazzi.snapshot(FrameLayout(c).apply { addView(v, FrameLayout.LayoutParams(288, 288)) }, "r18c-sprite-$step-$follow", 300)
        return v.idleStarts
    }

    @Test fun theSpriteFollowsTheSetting() {
        assertEquals("default = always", 1, starts(null, true))
        assertEquals("always", 1, starts(MotionPrefs.ALWAYS, true))
        assertEquals("often", 1, starts(MotionPrefs.OFTEN, true))
        assertEquals("rarely", 1, starts(MotionPrefs.RARELY, true))
        assertEquals("never: no float", 0, starts(MotionPrefs.NEVER, true))
        assertEquals("a view that does not follow the setting keeps floating", 1, starts(MotionPrefs.NEVER, false))
    }

    // ---- PLAN 228: the plain fallback's lean (the "Attack animations" setting off)

    private fun arena(): ArenaView {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        AppPrefs.setAttackAnimations(c, false)
        val panel = BattlePanelView(c)
        val d = panel.resources.displayMetrics.density
        fun measure() {
            panel.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((869 * d).toInt(), View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        }
        measure()
        panel.arena.setEnemyShown(true)
        panel.arena.enemySprite.setPlaceholder("W", 0xFFB05050.toInt())
        panel.arena.playerSprite.setPlaceholder("P", 0xFF5070B0.toInt())
        measure()
        return panel.arena
    }

    @Test fun theLeanLiftsNothingAndEndsAtRestForBothSides() {
        for (byPlayer in listOf(true, false)) {
            val a = arena()
            val spr = if (byPlayer) a.playerSprite else a.enemySprite
            val holder = spr.parent as View
            a.lean(byPlayer, 250)
            assertEquals("not raised above the cards (R9-B5: only travelling attackers are)", 0f, holder.translationZ, 0f)
            assertFalse("nothing cuts the attacker off while it leans", a.clipChildren)
            a.cancelAnimations()   // the end action runs on a cancel too
            assertEquals(0f, spr.translationX, 0f); assertEquals(0f, spr.translationY, 0f)
            assertTrue("the arena clips its children again", a.clipChildren)
        }
    }

    @Test fun theLeanIsSmallAndTowardTheTarget() {
        for (byPlayer in listOf(true, false)) {
            val a = arena()
            val spr = if (byPlayer) a.playerSprite else a.enemySprite
            val dx = com.husarp.ankigo.ui.Ui.dp(spr.context, 5f).toFloat()
            a.lean(byPlayer, 250)
            val anim = ArenaView::class.java.getDeclaredField("live").apply { isAccessible = true }.get(a) as Set<*>
            val oa = anim.single() as android.animation.ObjectAnimator
            oa.setCurrentFraction(0.45f)
            val x = spr.translationX
            assertTrue("leans toward the target ($x)", if (byPlayer) x > 0f else x < 0f)
            assertTrue("about 5 dp, much less than the 14 dp lunge ($x)", Math.abs(x) <= dx + 1f && Math.abs(x) > dx * 0.8f)
            assertEquals(0f, spr.translationY, 0f)
            a.cancelAnimations()
        }
    }

    @Test fun noLeanInReducedMotion() {
        val a = arena()
        a.context.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        a.lean(true, 250)
        assertEquals(0f, a.playerSprite.translationX, 0f)
        assertTrue(a.clipChildren)
    }
}
