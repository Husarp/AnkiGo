package com.husarp.ankigo.shots

import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalettes
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.Rule
import org.junit.Test

class R47SGrassOrbShots {
    @get:Rule val paparazzi = Shots.rule()
    private val plan = MoveFxPlan(MoveLook.ORB_TEMPLATE, 1f, FxKit.GRASS, FxPalettes.of(Type.GRASS))

    @Test fun grassStorm() {
        for (theme in Theme.entries) for (frame in MoveFxShotKit.Frame.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            for ((phase, times) in mapOf("launch" to listOf(250f, 310f, 470f), "impact" to listOf(650f, 740f, 1050f))) {
                paparazzi.snapshot(MoveFxShotKit.sheet(ctx, "Grass storm + aura", frame, plan, times),
                    "s-grass-$phase-${frame.name.lowercase()}-${theme.name.lowercase()}", 1000)
            }
        }
    }
}
