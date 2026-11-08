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

class R30U1ConditionShots {
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

    @Test fun realBattleRowsAt360And412InBothThemes() {
        for (size in listOf(Size.SMALL, Size.PHONE)) for (theme in Theme.entries) for (wrapped in listOf(false, true)) {
            val c = install(size, theme, wrapped)
            val screen = BattleShotKit.bound(c)
            paparazzi.snapshot(screen.root, "u1-chips-${size.wDp}-${if (wrapped) "wrapped" else "one-row"}-${theme.name.lowercase()}", 1000)
            val arena = screen.arena!!
            for (slot in listOf(ConditionMarks.Slot.WILD, ConditionMarks.Slot.OURS)) {
                val row = arena.markRows().single { it.slot == slot }
                assertEquals(if (wrapped) 8 else 2, row.chips.size)
                assertTrue(row.chips.all { it.more == 0 && it.mark != null && it.stages.isEmpty() })
                val tops = row.chips.map { it.rect.top }.distinct()
                assertEquals(if (wrapped) 2 else 1, tops.size)
                if (wrapped) {
                    assertTrue(row.chips.take(5).all { it.mark!!.key.startsWith("stat.") })
                    assertTrue(row.chips.drop(5).all { it.rect.top > row.chips.first().rect.top })
                }
                for (chip in row.chips) {
                    val r = chip.rect
                    assertFalse("Chips clear cards and art", arena.markScenePublic().blocks.any {
                        it.hits(ConditionSpot.R(r.left, r.top, r.right, r.bottom))
                    })
                }
            }
            val rects = arena.markRects()
            for (i in rects.indices) for (j in 0 until i) assertFalse(RectF.intersects(rects[i], rects[j]))
        }
    }

    @Test fun tappingActualChipsOpensTheSelectedAnchoredCard() {
        for (size in listOf(Size.SMALL, Size.PHONE)) for (theme in Theme.entries) for (key in listOf("confusion", "leechseed", "stat.atk")) {
            val c = install(size, theme, true)
            val screen = BattleShotKit.bound(c)
            val arena = screen.arena!!
            var opened = false
            var capturedCard: ConditionList? = null
            val wrapper = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!opened && arena.markRows().isNotEmpty()) {
                        val slot = if (key == "leechseed") ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD
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
            paparazzi.snapshot(wrapper, "u1-info-${key.replace('.', '-')}-${size.wDp}-${theme.name.lowercase()}", 1000)
            assertTrue(opened)
            val card = capturedCard!!
            assertTrue(card.left >= 0 && card.right <= arena.width && card.top >= 0 && card.bottom <= arena.height)
            val effect = texts(card).single { it.tag == "condition-effect" }
            assertEquals(ConditionExplanation.effect(arena.conditions.of(if (key == "leechseed") ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD).single { it.key == key }), effect.text.toString())
            assertEquals(1, effect.lineCount)
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
        for (theme in Theme.entries) {
            val c = install(Size.SMALL, theme, false)
            GameRepo.state!!.battle!!.enemy.status = "par"
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
            paparazzi.snapshot(wrapper, "u1-info-paralysis-360-${theme.name.lowercase()}", 1000)
            val card = capturedCard!!
            assertTrue(card.spoken.contains("Paralysed"))
            assertEquals(1, texts(card).single { it.tag == "condition-effect" }.lineCount)
            assertTrue(card.top >= 0 && card.bottom <= arena.height)
            ArenaView::class.java.getDeclaredField("eStatus").let { it.isAccessible = true; it.get(arena) as View }.performClick()
            assertTrue(arena.bubbleOpen())
            arena.conditionListShown()!!.performClick()
            assertFalse(arena.bubbleOpen())
        }
    }

    @Test fun everyEngineInventoryIconLoadsInBothThemes() {
        val keys = "switchtrap confusion attract partiallytrapped taunt encore disable torment healblock throatchop yawn imprison lockedmove uproar smackdown tarshot foresight miracleeye leechseed curse nightmare saltcure syrupbomb substitute destinybond aquaring ingrain stockpile magnetrise telekinesis charge defensecurl minimize powertrick powershift ab.flashfire ab.gas spikes toxicspikes stealthrock stickyweb reflect lightscreen auroraveil safeguard mist tailwind wish mp.fs rain sun sand hail snow electric grassy misty psychic trickroom gravity magicroom wonderroom mudsport watersport iondeluge brn psn tox par slp frz embargo perishsong happyhour flinch recharge charging glaiverush protect kingsshield spikyshield banefulbunker burningbulwark silktrap obstruct quickguard wideguard craftyshield matblock endure electrify roost magiccoat snatch powder lockon transform typechange bide mp.rollout mp.rage".split(" ")
        assertEquals(101, keys.size)
        for (theme in Theme.entries) {
            val c = install(Size.SMALL, theme, false)
            for (key in keys) assertNotNull(key, c.getDrawable(ConditionIcons.of(key)!!))
            for (info in StatusInfo.all) {
                val card = ConditionList(c, 8f)
                card.buildStatus(info, "Your Sparkmouse", Ui.dp(c, 332), Ui.dp(c, 400)) {}
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                assertEquals(info.status, 1, texts(card).single { it.tag == "condition-effect" }.lineCount)
            }
        }
    }

    @Test fun everyRegisteredConditionHasAnIconAndItsCompleteEffectFitsOneLine() {
        for (theme in Theme.entries) {
            val c = install(Size.SMALL, theme, false)
            for (key in ConditionMarks.allKeys) {
                val res = ConditionIcons.of(key)
                assertNotNull(key, res)
                assertNotNull(key, c.getDrawable(res!!))
                val mark = ConditionMarks.Mark(key, ConditionMarks.Slot.OURS, ConditionMarks.def(key)!!, 2, 1)
                val card = ConditionList(c, 8f)
                card.buildMark(mark, "Your Sparkmouse", Ui.dp(c, 332), Ui.dp(c, 400)) {}
                card.layout(0, 0, card.measuredWidth, card.measuredHeight)
                val effect = texts(card).single { it.tag == "condition-effect" }
                assertEquals("$key: ${effect.text}", 1, effect.lineCount)
                assertFalse(effect.text.contains("Until switching", true))
            }
        }
    }
}
