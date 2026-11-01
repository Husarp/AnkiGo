package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R26IRegressionTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() = BattleShotKit.reset()
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        (if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())

    @Test fun healingDoesNotWaitBehindEarlierFloats() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        panel.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 412), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 869), View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val arena = panel.arena
        arena.setPlayerHp(20, 100, false)
        arena.floatText(FloatText(true, "−8 HP", FloatKind.DAMAGE))
        arena.floatText(FloatText(true, "+18 HP", FloatKind.HEAL))
        val heal = (0 until arena.childCount).map { arena.getChildAt(it) }.filterIsInstance<FloatTextView>().last()
        assertEquals(0L, heal.animate().startDelay)
        arena.setPlayerHp(38, 100, true)
        paparazzi.snapshot(panel, "r26i-heal", 80)
    }

    @Test fun nestedInfoDoesNotChangeUnderlyingCardHeight() {
        val ctx = Shots.files(paparazzi.context)
        val dialog = AppDialog.Builder(ctx).setTitle("Pokémon details").setMessage("Stats\n".repeat(8)).create()
        dialog.joinStack()
        val card = dialog.content()
        fun height(): Int { card.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST)); return card.measuredHeight }
        val before = height()
        val popup = AppDialog.Builder(ctx).setTitle("Attack").setMessage("Stat explanation").create()
        popup.joinStack()
        assertEquals(before, height())
        paparazzi.snapshot(card, "r26i-details-stacked")
        popup.dismiss()
        dialog.dismiss()
    }

    @Test fun realStatPopupKeepsDetailsInPlace() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val details = DialogShots.capture { MonDetail.show(ctx, com.husarp.ankigo.GameRepo.data!!, com.husarp.ankigo.GameRepo.state!!.main!!) }!!
        details.joinStack()
        fun views(v: View): List<View> = listOf(v) + (if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
        val scroll = views(details.content()).filterIsInstance<android.widget.ScrollView>().first()
        val host = android.widget.FrameLayout(ctx).apply { addView(DialogShots.scene(ctx, details)) }
        paparazzi.snapshot(host, "r26i-real-details-before")
        scroll.scrollTo(0, Ui.dp(ctx, 80))
        paparazzi.snapshot(host, "r26i-real-details-scrolled")
        val before = scroll.scrollY
        val location = IntArray(2).also { scroll.getLocationInWindow(it) }
        val row = views(details.content()).first { it.tag is StatInfoText }
        val popup = DialogShots.capture { row.performClick() }!!
        try {
            popup.joinStack()
            host.addView(DialogShots.scene(ctx, popup))
            paparazzi.snapshot(host, "r26i-real-stat-popup")
            assertEquals("covered details keep their scroll position", before, scroll.scrollY)
            assertArrayEquals("covered details keep their screen position", location, IntArray(2).also { scroll.getLocationInWindow(it) })
        } finally { popup.dismiss(); details.dismiss() }
    }

    @Test fun verticalThumbCannotStretchWideDuringNavigation() {
        val ctx = paparazzi.context
        val thumb = ctx.getDrawable(com.husarp.ankigo.R.drawable.game_scrollbar_thumb)!!
        val bitmap = android.graphics.Bitmap.createBitmap(200, 400, android.graphics.Bitmap.Config.ARGB_8888)
        thumb.setBounds(0, 0, 200, 400)
        thumb.draw(android.graphics.Canvas(bitmap))
        assertTrue((0 until 200).count { bitmap.getPixel(it, 200) != 0 } <= Ui.dp(ctx, 3))
    }

    @Test fun stationaryScrollbarWakeIsSuppressed() {
        val ctx = Shots.files(paparazzi.context)
        val scroll = Bits.scroll(ctx, LinearLayout(ctx))
        val method = View::class.java.getDeclaredMethod("awakenScrollBars", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        assertEquals(false, method.invoke(scroll, 0, true))
    }

    @Test fun scrollingWakesTheBarAndTheNextTapDoesNot() {
        val ctx = Shots.files(paparazzi.context)
        val wakes = mutableListOf<Boolean>()
        val scroll = object : MotionScrollView(ctx) {
            override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
                super.awakenScrollBars(startDelay, invalidate).also { wakes += it }
        }
        scroll.addView(View(ctx).apply { minimumHeight = 3000; setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base)) }, ViewGroup.LayoutParams(400, 3000))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 400, 400)
        wakes.clear()
        scroll.scrollTo(0, 100)
        assertEquals(100, scroll.scrollY)
        assertTrue("moving content wakes the bar", wakes.contains(true))
        val method = View::class.java.getDeclaredMethod("awakenScrollBars", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        assertEquals("a later tap cannot wake it", false, method.invoke(scroll, 0, true))
        paparazzi.snapshot(scroll, "r26i-scrolling")
    }

    @Test fun savedNewBattleClearsMovesButSameBattleKeepsThem() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        val attack = GameEvent.Attack(true, "Main", "Wild", "Tackle", Type.NORMAL, Category.PHYSICAL, 8, 1, 1.0, false, false, false, 10, 30, 20, 30)
        panel.observeStudyEvent(attack)
        panel.observeStudyEvent(attack.copy(byPlayer = false))
        panel.renderAll()
        assertEquals(2, texts(panel).count { it == "Tackle" })
        val state = com.husarp.ankigo.GameRepo.state!!
        state.battle!!.wild.uid = "new-battle-r26i"
        panel.renderAll()
        assertEquals(2, texts(panel).count { it == "No move yet" })
        assertFalse(texts(panel).contains("Tackle"))
        paparazzi.snapshot(panel, "r26i-new-battle")
    }
}
