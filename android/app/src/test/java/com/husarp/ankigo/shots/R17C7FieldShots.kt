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
 * R17C-7: the floats of the field effects on a real battle panel (study layout, light + dark): "Rain" (the FIELD float of a weather move, on the
 * side of the Pokémon that used it) and the sandstorm's damage (a RESIDUAL float, plain "-6 HP" on both sides). PNGs: r17c7-field-<size>-<theme>.
 */
class R17C7FieldShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun fx(onPlayer: Boolean, kind: EffectKind, text: String, stat: String? = null, stages: Int = 0, amount: Int = 0, label: String? = null) =
        GameEvent.Effect(onPlayer, text, 20, 30, null, kind, stat, stages, amount, label)

    @Test fun rainAndSandstormFloats() {
        for (size in listOf(Size.SPLIT, Size.PHONE)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setStudyLayout(false, false); p.renderAll(); p.restoreStudyCharge()
            p.observeStudyEvent(GameEvent.Encounter(1, "Gnarlpup", 5, false, Tier.NORMAL, 30, 30, false, false))
            p.observeStudyEvent(GameEvent.RoundStart(1.0, 1, true))
            // the lines the bar shows: the weather move, then the sandstorm
            p.observeStudyEvent(fx(true, EffectKind.FIELD, "A sandstorm kicked up!", stat = "sand", stages = 1, label = "Sandstorm"))
            // the floats the real events turn into: Rain on the Pokémon that used Rain Dance, then the sandstorm hurting both
            val events = listOf(
                fx(true, EffectKind.FIELD, "It started to rain!", stat = "rain", stages = 1, label = "Rain"),
                fx(true, EffectKind.RESIDUAL, "Sparkmouse is buffeted by the sandstorm!", amount = 6),
                fx(false, EffectKind.RESIDUAL, "Gnarlpup is buffeted by the sandstorm!", amount = 5),
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
            paparazzi.snapshot(p, "r17c7-field-${size.name.lowercase()}-${theme.name.lowercase()}", 850)
            assertTrue("floats were fired", done)
            assertEquals(listOf("Rain", "−6 HP", "−5 HP"), floats.map { it.ft.text })
            for (f in floats) {
                assertTrue("${f.ft.text} inside the arena ($size $theme)", f.left >= 0 && f.top >= 0 && f.right <= p.arena.width && f.bottom <= p.arena.height)
            }
        }
    }
}
