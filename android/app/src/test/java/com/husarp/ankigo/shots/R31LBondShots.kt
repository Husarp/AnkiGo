package com.husarp.ankigo.shots

import android.view.View
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31LBondShots {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun matchupRevealUsesTheBondCardWithTheFullMessage() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 820).device(theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val panel = BattlePanelView(ctx)
            panel.renderAll()
            val dm = ctx.resources.displayMetrics
            panel.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, dm.widthPixels, dm.heightPixels)
            val message = "You know Pikachu's matchups now"
            panel.setLog(LogKind.SYS, "BOND", message, pop = false)
            val card = checkNotNull(panel.toastLayer.shownCard)
            paparazzi.snapshot(panel, "r31l-matchup-alert-${theme.name.lowercase()}", 300)
            assertEquals(message, card.main.text.toString())
            assertEquals(message to null, ToastText.split(ToastKind.BOND, message))
            assertFalse(card.truncated)
            assertTrue(card.main.lineCount in 1..2)
        }
    }
}
