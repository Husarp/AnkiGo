package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class R32PConditionShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun install(size: Size, theme: Theme, wrapped: Boolean): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
        for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
            val f = File(source, "${if (side == "static") "front" else side}/$n.png")
            if (f.exists()) File(Sprites.dir(c), "$side/$n.png").also { it.parentFile!!.mkdirs(); f.copyTo(it, overwrite = true) }
        }
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = GameRepo.state!!.battle!!
        for (side in listOf(b.player, b.enemy)) {
            side.boosts.fill(0); side.volatiles.clear()
            side.boosts[0] = 2
            side.volatiles["confusion"] = 3
            if (wrapped) {
                side.boosts[1] = -1; side.boosts[2] = 6; side.boosts[3] = -6; side.boosts[4] = 1
                side.volatiles["leechseed"] = 1; side.volatiles["ingrain"] = 1
            }
        }
        return c
    }

    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun tappingActualChipsOpensTheSelectedAnchoredCard() {
        for (size in listOf(Size.SMALL, Size.PHONE)) for (theme in Theme.entries) for (key in listOf("leechseed", "ingrain", "saltcure")) {
            val c = install(size, theme, true)
            GameRepo.state!!.battle!!.player.volatiles["saltcure"] = 1
            val screen = BattleShotKit.bound(c)
            val arena = screen.arena!!
            var opened = false
            var capturedCard: ConditionList? = null
            val wrapper = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!opened && arena.markRows().isNotEmpty()) {
                        val slot = if (key in listOf("leechseed", "ingrain", "saltcure")) ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD
                        val chip = arena.markRows().single { it.slot == slot }.chips.single { it.mark!!.key == key }
                        val t = android.os.SystemClock.uptimeMillis()
                        for (action in listOf(0, 1)) {
                            val event = MotionEvent.obtain(t, t + action * 40L, action, chip.rect.centerX(), chip.rect.centerY(), 0)
                            arena.dispatchTouchEvent(event); event.recycle()
                        }
                        opened = true
                        arena.measure(MeasureSpec.makeMeasureSpec(arena.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(arena.height, MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                        capturedCard = arena.conditionListShown()
                    }
                    super.dispatchDraw(canvas)
                }
            }
            wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
            paparazzi.snapshot(wrapper, "p-info-${key.replace('.', '-')}-${size.wDp}-${theme.name.lowercase()}", 1000)
            assertTrue(opened)
            val card = capturedCard!!
            assertTrue(card.left >= 0 && card.right <= arena.width && card.top >= 0 && card.bottom <= arena.height)
            val effect = texts(card).single { it.tag == "condition-effect" }
            assertEquals(ConditionExplanation.effect(arena.conditions.of(if (key in listOf("leechseed", "ingrain", "saltcure")) ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD).single { it.key == key }), effect.text.toString())
            assertTrue(effect.lineCount > 0)
            assertTrue(effect.layout.getLineBottom(effect.lineCount - 1) <= effect.height)
            for (line in 0 until effect.lineCount) {
                assertEquals(0, effect.layout.getEllipsisCount(line))
                assertTrue(effect.layout.getLineWidth(line) <= effect.width)
            }
            val chip = arena.markRows().first { row -> row.chips.any { it.mark!!.key == key } }.chips.first { it.mark!!.key == key }
            val t = android.os.SystemClock.uptimeMillis()
            for (action in listOf(0, 1)) {
                val event = MotionEvent.obtain(t, t + action * 40L, action, chip.rect.centerX(), chip.rect.centerY(), 0)
                arena.dispatchTouchEvent(event); event.recycle()
            }
            assertTrue(arena.bubbleOpen())
            arena.conditionListShown()!!.performClick()
            assertFalse(arena.bubbleOpen())
        }
    }

    @Test fun majorStatusBadgesOpenTheCompactAnchoredCard() {
        for (theme in Theme.entries) for (status in listOf("brn", "psn", "tox")) {
            val c = install(Size.SMALL, theme, false)
            GameRepo.state!!.battle!!.enemy.status = status
            val screen = BattleShotKit.bound(c)
            val arena = screen.arena!!
            var opened = false
            var capturedCard: ConditionList? = null
            val wrapper = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!opened && arena.width > 0) {
                        ArenaView::class.java.getDeclaredField("eStatus").let { it.isAccessible = true; it.get(arena) as View }.performClick()
                        opened = true
                        arena.measure(MeasureSpec.makeMeasureSpec(arena.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(arena.height, MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                        capturedCard = arena.conditionListShown()
                    }
                    super.dispatchDraw(canvas)
                }
            }
            wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
            paparazzi.snapshot(wrapper, "p-info-$status-360-${theme.name.lowercase()}", 1000)
            val card = capturedCard!!
            assertTrue(card.spoken.contains(StatusInfo.of(status)!!.name))
            val effect = texts(card).single { it.tag == "condition-effect" }
            assertEquals(StatusInfo.effect(status), effect.text.toString())
            assertTrue(effect.layout.getLineBottom(effect.lineCount - 1) <= effect.height)
            for (line in 0 until effect.lineCount) {
                assertEquals(0, effect.layout.getEllipsisCount(line))
                assertTrue(effect.layout.getLineWidth(line) <= effect.width)
            }
            assertTrue(card.top >= 0 && card.bottom <= arena.height)
            ArenaView::class.java.getDeclaredField("eStatus").let { it.isAccessible = true; it.get(arena) as View }.performClick()
            assertTrue(arena.bubbleOpen())
            arena.conditionListShown()!!.performClick()
            assertFalse(arena.bubbleOpen())
        }
    }

}
