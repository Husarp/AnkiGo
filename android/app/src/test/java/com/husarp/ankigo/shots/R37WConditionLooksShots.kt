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

class R37WConditionLooksShots {
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

    private fun panel(c: Context, status: String, poisonLove: Boolean = false): BattlePanelView {
        GameRepo.state!!.battle!!.let { it.player.status = if (status == "attract") (if (poisonLove) "psn" else null) else status; it.enemy.status = if (status == "attract") (if (poisonLove) "psn" else null) else status
            if (status == "attract") { it.player.volatiles["attract"] = 1; it.enemy.volatiles["attract"] = 1 } }
        return BattlePanelView(c).also { it.renderAll() }
    }

    private fun still(status: String, name: String, exitMs: Long? = null, compact: Boolean = false, enlarged: Boolean = false, poisonLove: Boolean = false) {
        for (theme in Theme.entries) {
            val c = context(theme, if (compact) Size.PIP else Size.PHONE)
            val panel = panel(c, status, poisonLove)
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
                        state.set(if (status == "attract") (if (poisonLove) "psn" else null) else status, now - 1700L, immediate = true)
                        if (status == "attract") sprite.conditionLook!!.loveState.set("attract", now - 1700L, immediate = true)
                        if (exitMs != null) state.set(null, now - exitMs)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "w-$name-${theme.name.lowercase()}", 0)
            assertFalse(arena.playerSprite.conditionLook!!.running)
            assertFalse(arena.enemySprite.conditionLook!!.running)
        }
    }

    @Test fun speciesMoveTitleDescribesTheLearnsetInBothThemes() {
        fun texts(view: View): List<String> = when (view) {
            is android.widget.TextView -> listOf(view.text.toString())
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }
        for (theme in Theme.entries) {
            val c = context(theme)
            val sp = GameRepo.data!!.sp(25)
            val original = com.husarp.ankigo.ui.DetailSections.storeOf
            val dialog = try {
                com.husarp.ankigo.ui.DetailSections.storeOf = {
                    object : com.husarp.ankigo.ui.DetailOpenStore {
                        override fun get(key: String): Boolean? = false
                        override fun set(key: String, open: Boolean) = Unit
                    }
                }
                DialogShots.capture {
                    com.husarp.ankigo.ui.DexDetail.show(com.husarp.ankigo.ui.DexPage(c, NoHost), sp)
                }!!
            } finally { com.husarp.ankigo.ui.DetailSections.storeOf = original }
            val scene = DialogShots.scene(c, dialog)
            assertTrue(texts(scene).contains("Moves it learns · ${sp.levelMoves.distinct().size}".uppercase()))
            paparazzi.snapshot(scene, "w-dex-learnset-${theme.name.lowercase()}", 0)
        }
    }

    @Test fun poisonAndToxicOnBothSides() { still("psn", "poison"); still("tox", "toxic") }
    @Test fun heartsCoexistWithPoisonAndFollowTheSprite() {
        still("attract", "love")
        still("attract", "love-poison", poisonLove = true)
        still("attract", "love-mirrored", enlarged = true)
        still("attract", "love-compact", compact = true)
    }
    @Test fun poisonCureFades() = still("psn", "poison-cure", 150L)

    @Test fun loveEventsCoexistWithStatusAndEndOnCureSwitchAndBattleEnd() {
        val c = context()
        val panel = panel(c, "psn")
        val arena = panel.arena
        val b = GameRepo.state!!.battle!!
        for (ours in listOf(true, false)) {
            val sprite = if (ours) arena.playerSprite else arena.enemySprite
            val side = if (ours) b.player else b.enemy
            side.volatiles["attract"] = 1
            arena.conditionEffect(GameEvent.Effect(ours, "In love", 20, 30, status = "psn",
                kind = com.husarp.ankigo.game.EffectKind.VOLATILE, stat = "attract", stages = 1), b)
            assertEquals("psn", sprite.conditionLook!!.state.status)
            assertEquals("attract", sprite.conditionLook!!.loveState.status)
            arena.setPlayerStatus("slp")
            assertTrue(sprite.conditionLook!!.loveState.visible)
            side.volatiles.remove("attract")
            arena.conditionEffect(GameEvent.Effect(ours, "Love ends", 20, 30,
                kind = com.husarp.ankigo.game.EffectKind.VOLATILE, stat = "attract", stages = -1), b)
            assertTrue(sprite.conditionLook!!.loveState.clearing)
            sprite.conditionLook!!.loveState.advance(SystemClock.uptimeMillis() + 500)
            assertFalse(sprite.conditionLook!!.loveState.visible)
        }
        b.player.volatiles["attract"] = 1
        arena.syncConditions(b)
        arena.conditionsSwitched()
        assertFalse(arena.playerSprite.conditionLook!!.loveState.visible)
        b.enemy.volatiles["attract"] = 1
        arena.syncConditions(b)
        arena.clearConditions()
        assertFalse(arena.enemySprite.conditionLook!!.visible)
    }

    @Test fun statusEventsStartAndCuresEndBothLooksWithoutWaitingForARedraw() {
        val c = context()
        val panel = BattlePanelView(c)
        panel.renderAll()
        val arena = panel.arena
        val player = EventPlayer(panel, continueOnPanel = false)
        val play = EventPlayer::class.java.getDeclaredMethod("play", GameEvent::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        for (status in listOf("frz", "slp", "psn", "tox")) for (ours in listOf(true, false)) {
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
        val panel = panel(c, "psn")
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
        val panel = panel(c, "psn")
        val sprite = panel.arena.playerSprite
        sprite.setConditionLove(true)
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
                assertFalse(look.loveState.visible)
                AppPrefs.setAttackAnimations(c, true)
                changed("ankigo_ui", "attackAnimations")
                assertTrue(look.state.visible)
                assertTrue(look.loveState.visible)
                c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                changed("study", "reduceMotion")
                assertFalse("reduced motion stops", look.running)
                assertFalse(look.state.visible)
                assertFalse(look.loveState.visible)
                c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                changed("study", "reduceMotion")
                assertTrue("re-enabling restores current status", look.running)
                sprite.mirror = true
                sprite.sizeFactor = 1.3f
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "w-settings-lifecycle", 0)
        assertTrue(checked)
        assertFalse("detached effects stop", sprite.conditionLook!!.running)
    }
}
