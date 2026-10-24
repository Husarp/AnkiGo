package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.FloatTexts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R17C-5: the floats of the side moves on a real battle panel (study layout, light + dark): "Protected" (a move was stopped by Protect) on
 * the wild side and "Reflect" (a screen went up, an info float) on yours. PNGs: r17c5-floats-<size>-<theme>.
 */
class R17C5SideShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun fx(onPlayer: Boolean, kind: EffectKind, text: String, stat: String? = null, stages: Int = 0, amount: Int = 0, label: String? = null) =
        GameEvent.Effect(onPlayer, text, 20, 30, null, kind, stat, stages, amount, label)

    @Test fun floats() {
        for (size in listOf(Size.SPLIT, Size.PHONE)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setStudyLayout(false, false); p.renderAll(); p.restoreStudyCharge()
            // the bar: a new round started
            p.observeStudyEvent(GameEvent.Encounter(1, "Gnarlpup", 5, false, Tier.NORMAL, 30, 30, false, false))
            p.observeStudyEvent(GameEvent.RoundStart(1.0, 1, true))
            // the floats the real events turn into
            val events = listOf(
                fx(false, EffectKind.BLOCKED, "Gnarlpup protected itself!", stat = "protect", label = "Protected"),
                fx(true, EffectKind.FIELD, "Reflect made Sparkmouse's team stronger against physical moves!", stat = "reflect", stages = 1, label = "Reflect"),
            )
            var done = false
            var floats = emptyList<FloatTextView>()
            p.arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (!done && v.width > 0 && v.height > 0) {
                    done = true
                    for (e in events) for (f in FloatTexts.of(e)) p.arena.floatText(f)
                    p.arena.measure(View.MeasureSpec.makeMeasureSpec(p.arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(p.arena.height, View.MeasureSpec.EXACTLY))
                    p.arena.layout(p.arena.left, p.arena.top, p.arena.right, p.arena.bottom)
                    floats = (0 until p.arena.childCount).map { p.arena.getChildAt(it) }.filterIsInstance<FloatTextView>()
                }
            }
            paparazzi.snapshot(p, "r17c5-floats-${size.name.lowercase()}-${theme.name.lowercase()}", 850)
            assertTrue("floats were fired", done)
            assertEquals(listOf("Protected", "Reflect"), floats.map { it.ft.text })
            for (f in floats) {
                assertTrue("${f.ft.text} inside the arena ($size $theme)", f.left >= 0 && f.top >= 0 && f.right <= p.arena.width && f.bottom <= p.arena.height)
            }
        }
    }
}
