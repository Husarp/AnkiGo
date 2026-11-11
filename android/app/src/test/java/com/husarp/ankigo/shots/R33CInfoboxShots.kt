package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33CInfoboxShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() = BattleShotKit.reset()

    private fun field(arena: ArenaView, name: String): View = ArenaView::class.java.getDeclaredField(name).let {
        it.isAccessible = true; it.get(arena) as View
    }
    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    private fun shot(size: Size, theme: Theme, name: String, wrapped: Boolean = false, style: InfoBoxStyle? = null, typeStyle: TypeStyle? = null, act: (ArenaView) -> Unit = {}) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        style?.let { AppPrefs.setInfoBoxStyle(c, it) }
        typeStyle?.let { AppLook.setTypeStyle(c, it) }
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
                        val center = card.left + card.width * card.scaleX / 2f
                        for (line in row.chips.groupBy { it.rect.top }.values) {
                            assertEquals("$size/$slot centered", center, (line.first().rect.left + line.last().rect.right) / 2f, 1f)
                            assertTrue("$size/$slot side", if (slot == ConditionMarks.Slot.WILD) line.first().rect.bottom <= card.top else line.first().rect.top >= card.top + card.height * card.scaleY)
                        }
                        assertTrue(row.chips.all { it.rect.top >= 0 && it.rect.bottom <= arena.height })
                    }
                    act(arena)
                    arena.measure(MeasureSpec.makeMeasureSpec(arena.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(arena.height, MeasureSpec.EXACTLY))
                    arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                }
                super.dispatchDraw(canvas)
            }
        }
        wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
        paparazzi.snapshot(wrapper, "c-$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        assertTrue("rendered $name $size", acted)
    }

    @Test fun centeredChipsAboveWildAndBelowPlayerAcrossWindowSizes() {
        for (size in listOf(Size.PHONE, Size.SMALL, Size.SPLIT, Size.PIP)) for (theme in Theme.entries)
            shot(size, theme, "battle")
        for (theme in Theme.entries) shot(Size.SMALL, theme, "wrapped", wrapped = true)
    }

    @Test fun actualStatTapsShowThatOwnersNumbersAndTheDetailsArrow() {
        for (theme in Theme.entries) for (slot in listOf(ConditionMarks.Slot.WILD, ConditionMarks.Slot.OURS)) {
            shot(Size.SMALL, theme, "stat-${slot.name.lowercase()}") { arena ->
                val chip = arena.markRows().single { it.slot == slot }.chips.first()
                val t = android.os.SystemClock.uptimeMillis()
                for (action in listOf(0, 1)) {
                    val e = MotionEvent.obtain(t, t + action * 40L, action, chip.rect.centerX(), chip.rect.centerY(), 0)
                    arena.dispatchTouchEvent(e); e.recycle()
                }
                val card = arena.conditionListShown()!!
                val state = GameRepo.state!!
                val stats = com.husarp.ankigo.game.SimpleView.of(GameRepo.data!!, state.battle!!, state.main!!).stats(slot == ConditionMarks.Slot.OURS)
                val expected = ConditionStat.values(chip.mark!!, stats)!!
                val effect = texts(card).single { it.tag == "condition-effect" }
                assertTrue(effect.text.toString().startsWith(expected))
                assertTrue(card.spoken.contains(expected))
                val spans = effect.text as Spanned
                assertEquals(1, spans.getSpans(0, spans.length, ReplacementSpan::class.java).size)
            }
        }
    }

    @Test fun typeAndInfoBoxTapsNeverOpenTheRetiredListAndStatusesKeepOwnCards() {
        for (theme in Theme.entries) for (style in InfoBoxStyle.entries) for (types in TypeStyle.entries) {
            shot(Size.SMALL, theme, "types-${style.name.lowercase()}-${types.name.lowercase()}", style = style, typeStyle = types) { arena ->
                for (name in listOf("enemyBlock", "playerCard", "eTypes", "pTypes", "eIcons", "pIcons")) {
                    val v = field(arena, name)
                    v.performClick()
                    if (v is ViewGroup) for (i in 0 until v.childCount) v.getChildAt(i).performClick()
                    assertNull(name, arena.conditionListShown())
                }
                arena.setEnemyStatus("brn")
                field(arena, "eStatus").performClick()
                assertTrue(arena.conditionListShown()!!.spoken.contains("Burn"))
                arena.conditionListShown()!!.performClick()
                assertNull(arena.conditionListShown())
            }
        }
    }
}
