package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R37VAuraShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun auraAnnouncementWaitsForContinueInBothMotionModes() {
        for (reduced in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduced).commit()
            val wild = TestData.mon(FakeGame.data, 150, 80)
            GameRepo.state!!.battle = Battle(wild, Tier.LEGENDARY).apply { enemy.boosts.fill(1, 0, 5) }
            val encounter = GameEvent.Encounter(wild.species, wild.name, wild.level, false, Tier.LEGENDARY,
                wild.hp, wild.hp, true, true, wild.uid)
            val panel = BattlePanelView(ctx)
            panel.renderAll()
            panel.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 360), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 640), View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
            val announced = mutableListOf<String>()
            panel.toastLayer.announce = { announced += it }
            val player = EventPlayer(panel, continueOnPanel = false)
            player.enqueue(listOf(GameEvent.Defeated(54, "Previous", 25), encounter))
            assertTrue(player.holding)
            repeat(3) { step ->
                paparazzi.snapshot(panel, "v-aura-held-${if (reduced) "reduced" else "animated"}", (step + 1) * 60_000L)
            }
            assertTrue(player.holding)
            fun announcements() = listOfNotNull(panel.toastLayer.shown) + panel.toastLayer.pending()
            assertFalse(announced.any { it.contains("legendary aura") })
            assertFalse(announcements().any { it.text.contains("legendary aura") })
            assertTrue(player.continueBattle())
            // The renderer advances chained timers over successive snapshots; retain announcements after their toast expires.
            repeat(3) { step ->
                paparazzi.snapshot(panel, "v-aura-entrance-${if (reduced) "reduced" else "animated"}", (step + 4) * 60_000L)
            }
            assertTrue("reduced=$reduced, announced=$announced, pending=${announcements()}, idle=${player.idle}",
                announced.any { it.contains(encounter.auraText!!) })
            player.clear()
        }
    }

    @Test fun auraChipAndTappedInfoInLightAndDark() {
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val wild = TestData.mon(FakeGame.data, if (tier == Tier.LEGENDARY) 150 else 151, 80)
            GameRepo.state!!.battle = Battle(wild, tier).apply { enemy.boosts.fill(1, 0, 5) }
            val screen = BattleShotKit.bound(ctx)
            val arena = screen.arena!!
            val name = "v-aura-${tier.name.lowercase()}-${theme.name.lowercase()}"
            paparazzi.snapshot(screen.root, "$name-chip", 1000)
            val row = arena.markRows().single { it.slot == ConditionMarks.Slot.WILD }
            assertEquals("legendaryaura", row.chips.single().mark!!.key)
            assertEquals("Aura", row.chips.single().mark!!.chipText)
            var tapped = false
            var card: ConditionList? = null
            val wrapper = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!tapped && arena.markRows().isNotEmpty()) {
                        val chip = arena.markRows().single { it.slot == ConditionMarks.Slot.WILD }.chips.single()
                        val t = android.os.SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(t, t + action * 40L, action, chip.rect.centerX(), chip.rect.centerY(), 0)
                            arena.dispatchTouchEvent(event); event.recycle()
                        }
                        tapped = true
                        arena.measure(MeasureSpec.makeMeasureSpec(arena.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(arena.height, MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                        card = arena.conditionListShown()
                    }
                    super.dispatchDraw(canvas)
                }
            }
            wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
            paparazzi.snapshot(wrapper, "$name-info", 1000)
            assertTrue(tapped)
            assertNotNull(card)
            val effect = texts(card!!).single { it.tag == "condition-effect" }
            assertEquals("Legendary aura: every stat ×1.5 for this fight", effect.text.toString())
            assertTrue(card!!.spoken.contains(effect.text))
            assertTrue(card!!.left >= 0 && card!!.right <= arena.width && card!!.top >= 0 && card!!.bottom <= arena.height)
            assertTrue(effect.lineCount > 0)
            for (line in 0 until effect.lineCount) assertEquals(0, effect.layout.getEllipsisCount(line))
            assertTrue(effect.layout.getLineBottom(effect.lineCount - 1) <= effect.height)
            card!!.performClick()
            assertFalse(arena.bubbleOpen())
        }
    }
}
