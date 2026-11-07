package com.husarp.ankigo.shots

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30eMoveEffectsFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanUp() = BattleShotKit.reset()

    private fun run(move: String, kind: EffectKind, playerSide: Boolean = true, fast: Boolean = false,
                    animations: Boolean = true, cancel: Boolean = false) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        AppPrefs.setAttackAnimations(ctx, animations)
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        panel.arena.setEnemyShown(true)
        panel.arena.playerSprite.setPlaceholder("P", 0xFF5070B0.toInt())
        panel.arena.enemySprite.setPlaceholder("W", 0xFFB05050.toInt())
        panel.arena.moveOnScreen = { true }
        val density = ctx.resources.displayMetrics.density
        panel.measure(View.MeasureSpec.makeMeasureSpec((412 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((869 * density).toInt(), View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val floats = mutableListOf<FloatText>()
        var early = emptyList<FloatText>()
        var landed = emptyList<FloatText>()
        var healedHp = -1f
        var earlyHp = -1f
        val hpField = panel.arena.javaClass.getDeclaredField(if (playerSide) "pHpFrac" else "eHpFrac").apply { isAccessible = true }
        panel.arena.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { if (child is FloatTextView) floats += child.ft }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
        val drain = move == "absorb"
        val status = kind == EffectKind.STAT_CHANGE || kind == EffectKind.STATUS_APPLIED
        val attack = GameEvent.Attack(playerSide, "User", "Target", move, Type.GRASS,
            if (drain) Category.SPECIAL else Category.STATUS, if (drain) 20 else 0, if (drain) 1 else 0,
            1.0, false, false, false, 80, 100, 70, 100, move, 20, moveEffectCount = 1)
        val effect = GameEvent.Effect(playerSide, "Effect landed", 70, 100,
            status = if (kind == EffectKind.STATUS_APPLIED) "par" else null, kind = kind,
            amount = if (status) 0 else 10, stat = if (status) "atk" else null, stages = if (status) 1 else 0)
        val reply = attack.copy(byPlayer = !playerSide, moveId = "tackle", move = "Tackle", category = Category.PHYSICAL,
            damage = 5, hits = 1, targetHp = 65, userHp = 80, moveEffectCount = 0)
        val events = mutableListOf<GameEvent>(attack, effect, reply)
        if (fast) repeat(4) { events += GameEvent.Counted(0, 0, 10, 0, false, neutral = true) }
        val player = EventPlayer(panel, continueOnPanel = false)
        player.enqueue(events)
        if (cancel) player.clear()
        val factor = if (fast) 0.5 else 1.0
        val landing = if (!animations) 300L else if (drain) 990L else if (move == "recover") 150L else 100L
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ early = floats.toList(); earlyHp = hpField.getFloat(panel.arena) }, ((landing - 30) * factor).toLong())
        handler.postDelayed({ landed = floats.toList(); healedHp = hpField.getFloat(panel.arena) }, ((landing + 30) * factor).toLong())
        paparazzi.snapshot(FrameLayout(ctx).apply { addView(panel) }, "r30e-$move-$playerSide-$fast-$animations-$cancel", 1100)
        repeat(3) { paparazzi.snapshot(FrameLayout(ctx), "r30e-flush", 60_000L * (it + 1)) }
        val effectKind = when (kind) {
            EffectKind.STAT_CHANGE -> FloatKind.STAT_UP
            EffectKind.STATUS_APPLIED -> FloatKind.STATUS
            else -> FloatKind.HEAL
        }
        if (cancel) assertTrue(floats.isEmpty()) else {
            assertFalse("effect must wait for landing", early.any { it.kind == effectKind })
            assertTrue("effect appears at landing, before the reply", landed.any { it.kind == effectKind })
            assertFalse("wild reply waits for move recovery", landed.any { it.text == "−5 HP" })
            assertEquals("HP snapshot changes with the effect", 0.7f, healedHp, 0.001f)
            if (drain && animations) assertEquals("HP stays before the heal until the orb returns", 0.6f, earlyHp, 0.001f)
            assertEquals("exactly once", 1, floats.count { it.kind == effectKind })
            if (drain) assertEquals(listOf("−20 HP", "+10 HP", "−5 HP"), floats.map { it.text })
        }
        assertTrue(player.idle)
        player.clear()
    }

    @Test fun drainLandsWithReturningOrb() = run("absorb", EffectKind.DRAIN)
    @Test fun wildDrainLandsWithReturningOrb() = run("absorb", EffectKind.DRAIN, playerSide = false)
    @Test fun fastDrainLandsWithReturningOrb() = run("absorb", EffectKind.DRAIN, fast = true)
    @Test fun healLandsWithGreenGlow() = run("recover", EffectKind.HEAL)
    @Test fun statChangeLandsWithAura() = run("growth", EffectKind.STAT_CHANGE)
    @Test fun statusLandsWithAura() = run("stunspore", EffectKind.STATUS_APPLIED)
    @Test fun animationsOffStillHealWithTheMove() = run("absorb", EffectKind.DRAIN, animations = false)
    @Test fun leavingDropsPendingHeal() = run("absorb", EffectKind.DRAIN, cancel = true)
}
