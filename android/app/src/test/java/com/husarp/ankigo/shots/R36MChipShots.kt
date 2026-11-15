package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R36MChipShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun field(arena: ArenaView, name: String): View = ArenaView::class.java.getDeclaredField(name).let {
        it.isAccessible = true; it.get(arena) as View
    }
    private fun shot(size: Size, theme: Theme, name: String, wrapped: Boolean = false, classic: Boolean = false) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        if (classic) { AppPrefs.setBackgroundPictures(c, false); AppPrefs.setCardsUnderOwn(c, false) }
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = GameRepo.state!!.battle!!
        for (side in listOf(b.player, b.enemy)) {
            side.boosts.fill(0); side.volatiles.clear()
            side.boosts[0] = if (side === b.player) 1 else -1
            if (wrapped) {
                side.boosts[1] = 2; side.boosts[2] = -2
                side.volatiles["confusion"] = 3
            }
        }
        val screen = BattleShotKit.bound(c)
        if (size == Size.PIP) BattleShotKit.pip(screen)
        val arena = screen.arena!!
        var acted = false
        val wrapper = object : FrameLayout(c) {
            override fun dispatchDraw(canvas: Canvas) {
                if (!acted && arena.markRows().size >= 2) {
                    acted = true
                    for (slot in listOf(ConditionMarks.Slot.WILD, ConditionMarks.Slot.OURS)) {
                        val card = field(arena, if (slot == ConditionMarks.Slot.WILD) "enemyBlock" else "playerCard")
                        val row = arena.markRows().single { it.slot == slot }
                        val edge = card.left + if (slot == ConditionMarks.Slot.OURS) card.width * card.scaleX else 0f
                        for (line in row.chips.groupBy { it.rect.top }.values) {
                            assertEquals("$size/$slot outer edge", edge, if (slot == ConditionMarks.Slot.OURS) line.last().rect.right else line.first().rect.left, 1f)
                            assertTrue("$size/$slot side", if (slot == ConditionMarks.Slot.WILD) line.first().rect.bottom <= card.top else line.first().rect.top >= card.top + card.height * card.scaleY)
                        }
                        assertTrue(row.chips.all { it.rect.top >= 0 && it.rect.bottom <= arena.height })
                    }
                }
                super.dispatchDraw(canvas)
            }
        }
        wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
        paparazzi.snapshot(wrapper, "m-638-$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        assertTrue("rendered $name $size", acted)
    }

    @Test fun outerChipsAboveWildAndBelowPlayerAcrossWindowSizes() {
        for (size in listOf(Size.PHONE, Size.SMALL, Size.SPLIT, Size.PIP)) for (theme in Theme.entries)
            shot(size, theme, "battle")
        for (theme in Theme.entries) shot(Size.SMALL, theme, "wrapped", wrapped = true)
        for (theme in Theme.entries) shot(Size.PHONE, theme, "classic", wrapped = true, classic = true)
    }

}
