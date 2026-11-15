package com.husarp.ankigo.shots

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R35IConditionLooksShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun cleanUp() = BattleShotKit.reset()

    private fun context(theme: Theme = Theme.LIGHT, size: Size = Size.PHONE): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        return Shots.files(paparazzi.context).also {
            FakeGame.install(it)
            val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
            for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
                val art = File(source, "${if (side == "static") "front" else side}/$n.png")
                if (art.isFile) File(Sprites.dir(it), "$side/$n.png").also { target ->
                    target.parentFile!!.mkdirs(); art.copyTo(target, overwrite = true)
                }
            }
            it.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            AppPrefs.setAttackAnimations(it, true)
        }
    }

    private fun panel(c: Context, status: String): BattlePanelView {
        GameRepo.state!!.battle!!.let { it.player.status = status; it.enemy.status = status }
        return BattlePanelView(c).also { it.renderAll() }
    }

    private fun still(status: String, name: String, exitMs: Long? = null, compact: Boolean = false, enlarged: Boolean = false) {
        for (theme in Theme.entries) {
            val c = context(theme, if (compact) Size.PIP else Size.PHONE)
            val panel = panel(c, status)
            if (compact) panel.setCompact(true)
            val arena = panel.arena
            if (enlarged) arena.pokemonSize = 1.3f
            val frame = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (enlarged) arena.playerSprite.mirror = true
                    // Pin the renderer's real clock at a chosen pose, leaving production timing unchanged.
                    for (sprite in arrayOf(arena.playerSprite, arena.enemySprite)) {
                        val state = sprite.conditionLook!!.state
                        val now = SystemClock.uptimeMillis()
                        state.set(status, now - 1700L, immediate = true)
                        if (exitMs != null) state.set(null, now - exitMs)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "i-$name-${theme.name.lowercase()}", 0)
            assertFalse(arena.playerSprite.conditionLook!!.running)
            assertFalse(arena.enemySprite.conditionLook!!.running)
        }
    }

    @Test fun frozenHoldsTheIceBeamCubeOnBothSides() = still("frz", "frozen")
    @Test fun asleepHasQuietZsOnBothSides() = still("slp", "asleep")
    @Test fun resizedAndMirroredSpritesCarryTheirCube() = still("frz", "sized-mirrored-frozen", enlarged = true)
    @Test fun thawCracksThenShatters() {
        still("frz", "thaw-cracks", 300L)
        still("frz", "thaw-shards", 650L)
    }
    @Test fun wakeFadesAndCompactSpritesKeepTheirLooks() {
        still("slp", "wake", 150L)
        still("frz", "compact-frozen", compact = true)
        still("slp", "compact-asleep", compact = true)
    }

    @Test fun statusEventsStartAndCuresEndBothLooksWithoutWaitingForARedraw() {
        val c = context()
        val panel = BattlePanelView(c)
        panel.renderAll()
        val arena = panel.arena
        val player = EventPlayer(panel, continueOnPanel = false)
        val play = EventPlayer::class.java.getDeclaredMethod("play", GameEvent::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        for (status in listOf("frz", "slp")) for (ours in listOf(true, false)) {
            val sprite = if (ours) arena.playerSprite else arena.enemySprite
            play.invoke(player, GameEvent.Effect(ours, "Condition starts", 20, 30, status = status), false)
            assertEquals(status, sprite.conditionLook!!.state.status)
            assertFalse(sprite.conditionLook!!.state.clearing)
            // All cure causes arrive as the same cleared status (natural thaw, Fire, thawing move, wake, item).
            play.invoke(player, GameEvent.Effect(ours, "Condition cleared", 20, 30, status = null), false)
            assertTrue(sprite.conditionLook!!.state.clearing)
            sprite.conditionLook!!.state.advance(SystemClock.uptimeMillis() + 1000L)
            assertFalse(sprite.conditionLook!!.state.visible)
        }
        player.clear()
    }

    @Test fun departuresAndSavedBattleEndClearImmediately() {
        val c = context()
        val panel = panel(c, "frz")
        val arena = panel.arena
        arena.conditionsSwitched()
        assertFalse(arena.playerSprite.conditionLook!!.state.visible)
        assertTrue(arena.enemySprite.conditionLook!!.state.visible)
        arena.setPlayerStatus("slp")
        arena.faint(true, 0)
        assertFalse(arena.playerSprite.conditionLook!!.state.visible)
        arena.setPlayerStatus("frz")
        arena.clearConditions()
        assertFalse(arena.playerSprite.conditionLook!!.state.visible)
        assertFalse(arena.enemySprite.conditionLook!!.state.visible)
        arena.setPlayerStatus("slp")
        arena.setEnemyStatus("slp")
        GameRepo.state!!.battle = null
        panel.renderAll()
        assertFalse(arena.playerSprite.conditionLook!!.state.visible)
        assertFalse(arena.enemySprite.conditionLook!!.state.visible)
    }

    @Test fun hiddenDetachedAndAnimationSettingsStopFrameCallbacks() {
        val c = context()
        val panel = panel(c, "slp")
        val sprite = panel.arena.playerSprite
        var checked = false
        val frame = object : FrameLayout(c) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (checked) return
                checked = true
                val look = sprite.conditionLook!!
                // Shots.MemPrefs stores no listeners; deliver the callback as the real preference store does.
                val watch = look.javaClass.getDeclaredField("watch").let {
                    it.isAccessible = true; it.get(look) as SharedPreferences.OnSharedPreferenceChangeListener
                }
                fun changed(store: String, key: String) = watch.onSharedPreferenceChanged(c.getSharedPreferences(store, 0), key)
                assertTrue("visible effect ticks", look.running)
                sprite.visibility = View.INVISIBLE
                assertFalse("hidden effect stops", look.running)
                sprite.visibility = View.VISIBLE
                assertTrue("shown effect resumes", look.running)
                AppPrefs.setAttackAnimations(c, false)
                changed("ankigo_ui", "attackAnimations")
                assertFalse("animation off stops", look.running)
                assertFalse(look.state.visible)
                AppPrefs.setAttackAnimations(c, true)
                changed("ankigo_ui", "attackAnimations")
                assertTrue(look.state.visible)
                c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                changed("study", "reduceMotion")
                assertFalse("reduced motion stops", look.running)
                assertFalse(look.state.visible)
                c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                changed("study", "reduceMotion")
                assertTrue("re-enabling restores current status", look.running)
                sprite.mirror = true
                sprite.sizeFactor = 1.3f
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "i-settings-lifecycle", 0)
        assertTrue(checked)
        assertFalse("detached effects stop", sprite.conditionLook!!.running)
    }
}
