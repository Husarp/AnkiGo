package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R38YShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun context(theme: Theme, size: Size = Size.SMALL): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        return Shots.files(paparazzi.context).also { c ->
            FakeGame.install(c)
            val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
            for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
                val art = File(source, "${if (side == "static") "front" else side}/$n.png")
                if (art.isFile) File(Sprites.dir(c), "$side/$n.png").also { it.parentFile!!.mkdirs(); art.copyTo(it, overwrite = true) }
            }
            c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            AppPrefs.setAttackAnimations(c, true)
        }
    }

    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun paralysisSnapsAndSixStarOrbitInBothThemes() {
        for (theme in Theme.entries) for (look in listOf("snap", "quiet", "confused", "together", "compact")) {
            val c = context(theme, if (look == "compact") Size.PIP else Size.SMALL)
            val b = GameRepo.state!!.battle!!
            val confused = look in listOf("confused", "together", "compact")
            for (side in listOf(b.player, b.enemy)) {
                side.status = if (look == "confused") "psn" else "par"
                if (confused) side.volatiles["confusion"] = 3
            }
            val panel = BattlePanelView(c).also { it.renderAll(); if (look == "compact") it.setCompact(true) }
            val frame = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    for (sprite in listOf(panel.arena.playerSprite, panel.arena.enemySprite)) {
                        val fx = sprite.conditionLook!!
                        val now = SystemClock.uptimeMillis()
                        fx.state.set(if (look == "confused") "psn" else "par", now - 2000, immediate = true)
                        if (confused) fx.confusionState.set("confusion", now - 1700, immediate = true)
                        // Pin only this screenshot's random burst so both the snap and the quiet frame are inspectable.
                        val crackle = fx.javaClass.getDeclaredField("crackle").let { it.isAccessible = true; it.get(fx) }
                        for ((key, value) in mapOf("since" to now - 2000, "start" to if (look == "quiet") now + 3000 else now - 50, "end" to now + 3200)) {
                            crackle.javaClass.getDeclaredField(key).apply { isAccessible = true }.setLong(crackle, value)
                        }
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "y-condition-$look-${theme.name.lowercase()}", 0)
            assertFalse(panel.arena.playerSprite.conditionLook!!.running)
        }
    }

    @Test fun conditionCuresSwitchesAndAnimationSettingsShareTheExistingLifecycle() {
        val c = context(Theme.LIGHT)
        val panel = BattlePanelView(c).also { it.renderAll() }
        val arena = panel.arena
        val b = GameRepo.state!!.battle!!
        for (ours in listOf(true, false)) {
            val sprite = if (ours) arena.playerSprite else arena.enemySprite
            val side = if (ours) b.player else b.enemy
            if (ours) arena.setPlayerStatus("par") else arena.setEnemyStatus("par")
            side.volatiles["confusion"] = 3
            arena.conditionEffect(GameEvent.Effect(ours, "Confused", 20, 30, "par", EffectKind.VOLATILE, "confusion", 1), b)
            assertEquals("par", sprite.conditionLook!!.state.status)
            assertEquals("confusion", sprite.conditionLook!!.confusionState.status)
            side.volatiles.clear()
            arena.conditionEffect(GameEvent.Effect(ours, "Snapped out of confusion", 20, 30, "par", EffectKind.VOLATILE, "confusion", -1), b)
            assertTrue(sprite.conditionLook!!.confusionState.clearing)
            if (ours) arena.setPlayerStatus(null) else arena.setEnemyStatus(null)
            assertTrue(sprite.conditionLook!!.state.clearing)
            sprite.conditionLook!!.state.advance(SystemClock.uptimeMillis() + 500)
            sprite.conditionLook!!.confusionState.advance(SystemClock.uptimeMillis() + 500)
            assertFalse(sprite.conditionLook!!.visible)
        }
        b.player.status = "par"; b.player.volatiles["confusion"] = 3
        panel.renderAll()
        arena.conditionsSwitched()
        assertFalse(arena.playerSprite.conditionLook!!.visible)
        b.enemy.status = "par"; b.enemy.volatiles["confusion"] = 3
        panel.renderAll()
        arena.clearConditions()
        assertFalse(arena.enemySprite.conditionLook!!.visible)
        panel.renderAll()
        AppPrefs.setAttackAnimations(c, false)
        arena.enemySprite.conditionLook!!.refresh()
        assertFalse(arena.enemySprite.conditionLook!!.visible)
        AppPrefs.setAttackAnimations(c, true)
        arena.enemySprite.conditionLook!!.refresh()
        assertTrue(arena.enemySprite.conditionLook!!.confusionState.visible)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        arena.enemySprite.conditionLook!!.refresh()
        assertFalse(arena.enemySprite.conditionLook!!.visible)
    }

    @Test fun statWindowsUseOwnIconsAndColouredNumbersBelowTheMultiplier() {
        for (theme in Theme.entries) {
            val c = context(theme, Size.PHONE)
            val column = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(Ui.c(c, R.color.ag_base)) }
            for ((key, stage) in listOf("atk" to 1, "def" to -1, "spa" to 2, "spd" to -2, "spe" to 1, "accuracy" to 1, "evasion" to -1)) {
                val mark = ConditionMarks.Mark("stat.$key", ConditionMarks.Slot.OURS, ConditionMarks.def("stat.$key")!!, stage, 1)
                val card = ConditionList(c, 8f).apply { buildMark(mark, "Pikachu", Ui.dp(c, 328), Ui.dp(c, 200), Stats(200, 156, 101, 89, 117, 73)) {} }
                val effect = texts(card).single { it.tag == "condition-effect" }
                val values = ConditionStat.values(mark, Stats(200, 156, 101, 89, 117, 73))
                val styled = effect.text as Spanned
                assertTrue("the stat name keeps its icon and color", styled.getSpans(0, styled.length, StatProse.NameSpan::class.java).isNotEmpty())
                assertTrue(effect.text.toString().startsWith(ConditionExplanation.effect(mark)))
                if (values != null) {
                    assertEquals(ConditionExplanation.effect(mark) + "\n" + values, effect.text.toString())
                    val span = styled.getSpans(0, styled.length, ForegroundColorSpan::class.java).single()
                    assertEquals(effect.text.toString().indexOf(values), styled.getSpanStart(span))
                    assertEquals(values.length, styled.getSpanEnd(span) - styled.getSpanStart(span))
                    assertEquals(StatPill.ink(stage > 0, theme == Theme.DARK), span.foregroundColor)
                }
                column.addView(card, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(c, 6) })
            }
            paparazzi.snapshot(MotionScrollView(c).apply { addView(column) }, "y-stat-windows-${theme.name.lowercase()}", 0)
        }
    }

    private fun battle(): LogBattle = BattleLogBuilder(now = { 38 }).apply {
        feed(GameEvent.Encounter(54, "Duckling", 25, false, Tier.NORMAL, 63, 63, false, false))
        feed(listOf(GameEvent.RoundStart(1.5, 10, true),
            GameEvent.Attack(true, "Sparkmouse", "Duckling", "Thunder Shock", Type.ELECTRIC, Category.SPECIAL, 19, 1, 2.0, false, false, false, 44, 63, 47, 58),
            GameEvent.Effect(false, "Duckling was paralysed!", 44, 63, "par"),
            GameEvent.Effect(true, "Sparkmouse's Defense fell", 47, 58, kind = EffectKind.STAT_CHANGE, stat = "def", stages = -1),
            GameEvent.Info("Sparkmouse's Oran Berry restored 10 HP")))
        alert("Switch blocked: Sparkmouse is trapped for 2 more rounds.")
    }.battles.single()

    @Test fun liveLogFollowsOnlyAtTheBottomAndNewEventsButtonReturnsThere() {
        val c = context(Theme.LIGHT)
        val current = battle()
        repeat(35) { current.entries += BattleLogEntry(2, Tone.PLAIN, "Sparkmouse used Thunder Shock · event $it") }
        val sheet = CurrentBattleLogSheet(c, { current }) {}
        sheet.showBattle(current)
        var checked = false
        val frame = object : FrameLayout(c) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (checked) return
                checked = true
                sheet.scroll.isSmoothScrollingEnabled = false
                sheet.scroll.scrollTo(0, Ui.dp(c, 100))
                sheet.scroll.scrollTo(0, 0)
                assertEquals(0, sheet.scroll.scrollY)
                current.entries += BattleLogEntry(3, Tone.PLAIN, "Duckling couldn't move · Fully paralysed")
                sheet.showBattle(current)
                assertEquals(View.VISIBLE, sheet.newEvents.visibility)
                assertEquals(0, sheet.scroll.scrollY)
                sheet.newEvents.performClick()
                assertEquals(View.GONE, sheet.newEvents.visibility)
                assertTrue(sheet.scroll.scrollY > 0)
            }
        }.apply { addView(sheet, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "y-battle-log-long", 0)
        assertTrue(checked)
    }

    @Test fun currentBattleSheetKeepsTheArenaVisibleAndClosesBackToIt() {
        for (theme in Theme.entries) {
            val c = context(theme)
            var closed = false
            val current = battle()
            val sheet = CurrentBattleLogSheet(c, { current }) { closed = true }
            sheet.showBattle(current)
            GameRepo.state!!.battle!!.let { it.enemy.status = "par"; it.player.boosts[1] = -1 }
            val panel = BattlePanelView(c).also { it.renderAll() }
            val scene = FrameLayout(c).apply {
                addView(panel, FrameLayout.LayoutParams(-1, -1))
                addView(sheet, FrameLayout.LayoutParams(-1, Ui.dp(c, 360), android.view.Gravity.BOTTOM))
            }
            paparazzi.snapshot(scene, "y-battle-log-${theme.name.lowercase()}", 0)
            assertTrue(texts(sheet).any { "Switch blocked" in it.text.toString() })
            texts(sheet).single { it.contentDescription == "Close battle log" }.performClick()
            assertTrue(closed)
            assertNotNull(GameRepo.state!!.battle)
        }
    }
}
