package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real event player / arena: per-hit numbers and recoil must not become a total or duplicate. */
class R23ZbPlaybackFlowTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanUp() = BattleShotKit.reset()

    private fun run(recoil: Boolean, playerSide: Boolean, fast: Boolean = false, cancel: Boolean = false, animations: Boolean = true, berry: Boolean = false) {
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
        panel.arena.moveOnScreen = { true } // Paparazzi has no window token; exercise the real animated path.
        val density = ctx.resources.displayMetrics.density
        panel.measure(View.MeasureSpec.makeMeasureSpec((412 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((869 * density).toInt(), View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val played = mutableListOf<Pair<MoveLook, Float>>()
        val maker = panel.arena.moveMaker
        panel.arena.moveMaker = { stage, plan, react, speed ->
            played += plan.look to speed
            maker(stage, plan, react, speed)
        }
        val floats = mutableListOf<FloatText>()
        panel.arena.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { if (child is FloatTextView) floats += child.ft }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
        val player = EventPlayer(panel, continueOnPanel = false)
        val attack = GameEvent.Attack(playerSide, "User", "Target", if (recoil) "Double-Edge" else "Bullet Seed",
            if (recoil) Type.NORMAL else Type.GRASS, Category.PHYSICAL, 19, if (recoil) 1 else 3, 1.0,
            false, false, false, 40, 100, 70, 100, if (recoil) "doubleedge" else "bulletseed", 40,
            hitDamages = if (recoil) listOf(19) else listOf(7, 9, 3), multiHit = !recoil,
            recoilMove = recoil, hitTargetHps = if (berry) listOf(52, 43, 40) else emptyList())
        val events = mutableListOf<GameEvent>(attack)
        if (recoil) events += GameEvent.Effect(playerSide, "User is damaged by recoil!", 70, 100,
            kind = EffectKind.RECOIL, amount = 6, moveRecoil = true)
        if (berry) events += GameEvent.Effect(!playerSide, "Target ate a berry!", 52, 100,
            kind = EffectKind.HEAL, amount = 10, hitIndex = 0)
        if (fast) repeat(4) { events += GameEvent.Counted(0, 0, 10, 0, false, neutral = true) }
        player.enqueue(events)
        if (animations) assertEquals(listOf((if (recoil) MoveLook.RECOIL else MoveLook.MULTI_HIT) to (if (fast) 2f else 1f)), played)
        else assertTrue(played.isEmpty())
        if (cancel) player.clear()
        val host = FrameLayout(ctx).apply { addView(panel) }
        paparazzi.snapshot(host, "r23zb-playback-${if (recoil) "recoil" else "multi"}-$playerSide", 1100)
        repeat(3) { paparazzi.snapshot(FrameLayout(ctx), "r23zb-playback-flush", 60_000L * (it + 1)) }
        assertTrue("queue finishes", player.idle)
        assertEquals(when { cancel -> emptyList(); berry -> listOf("−7 HP", "+10 HP", "−9 HP", "−3 HP"); recoil -> listOf("−19 HP", "−6 HP"); !animations -> listOf("−19 HP");
            else -> listOf("−7 HP", "−9 HP", "−3 HP") }, floats.map { it.text })
        assertEquals(when { cancel -> emptyList(); berry -> List(4) { !playerSide }; recoil -> listOf(!playerSide, playerSide);
            !animations -> listOf(!playerSide); else -> List(3) { !playerSide } }, floats.map { it.onPlayer })
        player.clear()
    }
    @Test fun leavingDropsPendingBerryEffects() = run(false, true, cancel = true, berry = true)
    @Test fun playerBerryEffectPlaysBetweenItsHitNumbers() = run(false, true, berry = true)
    @Test fun wildBerryEffectPlaysBetweenItsHitNumbers() = run(false, false, berry = true)
    @Test fun playerMultiHitNumbers() = run(false, true)
    @Test fun wildMultiHitNumbers() = run(false, false)
    @Test fun playerRecoilNumberOnce() = run(true, true)
    @Test fun wildRecoilNumberOnce() = run(true, false)
    @Test fun fastMultiHitNumbers() = run(false, true, fast = true)
    @Test fun fastRecoilNumberOnce() = run(true, true, fast = true)
    @Test fun leavingDropsPendingMultiHitNumbers() = run(false, true, cancel = true)
    @Test fun leavingDropsPendingRecoilNumber() = run(true, true, cancel = true)
    @Test fun animationsOffKeepTruthfulTotalAndRecoil() = run(true, true, animations = false)
    @Test fun animationsOffDoNotGuessHitAmounts() = run(false, true, animations = false)
}
