package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.movefx.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24HShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun signedChipsAcrossAllLooks() {
        for (theme in Theme.entries) for (look in InfoBoxLook.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxLook(ctx, look)
            val battle = com.husarp.ankigo.GameRepo.state!!.battle!!
            battle.player.boosts[0] = 6; battle.player.boosts[1] = -2; battle.player.boosts[4] = 1
            battle.enemy.boosts[0] = -6; battle.enemy.boosts[5] = 2
            val panel = BattlePanelView(ctx)
            panel.renderAll()
            paparazzi.snapshot(panel, "r24h-chips-${look.name.lowercase()}-${theme.name.lowercase()}")
            panel.arena.cancelAnimations()
        }
        BattleShotKit.reset()
    }

    @Test fun changedBarsHaveSquareEndsAndNormalTicks() {
        val ctx = paparazzi.context
        val color = android.graphics.Color.RED
        for (stage in listOf(-2, 1)) {
            val bar = StatChangeBar(ctx, .5f, stage, color)
            val inset = Ui.dp(ctx, 3)
            val height = inset * 2 + 14
            bar.layout(0, 0, 200, height)
            val bitmap = android.graphics.Bitmap.createBitmap(200, height, android.graphics.Bitmap.Config.ARGB_8888)
            bar.draw(android.graphics.Canvas(bitmap))
            // Current down-stage bar reaches x=50 with a square end, including its top edge.
            if (stage < 0) assertEquals(color, bitmap.getPixel(48, inset + 1))
            // The normal reference extends above the track.
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(100, 1)) > 0)
        }
    }

    @Test fun crownAtContactAndFade() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            val plan = MoveFxCatalog.plan("watergun", Type.WATER, Category.SPECIAL, 40)
            paparazzi.snapshot(MoveFxShotKit.sheet(ctx, "Water Gun · crown", MoveFxShotKit.Frame.WIDE,
                plan, listOf(210f, 340f, 480f, 600f)), "r24h-crown-${theme.name.lowercase()}", 1000)
        }
    }
}
