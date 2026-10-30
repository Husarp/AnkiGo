package com.husarp.ankigo.shots

import com.husarp.ankigo.GameRepo
import org.junit.Rule
import org.junit.Test

class R21CHazardShots {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun hazardsAndTrap() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val b = GameRepo.state!!.battle!!
            b.player.volatiles["switchtrap"] = 2
            b.field.player["spikes"] = 2
            b.field.player["stealthrock"] = 1
            b.field.enemy["toxicspikes"] = 2
            b.field.enemy["stickyweb"] = 1
            val scene = BattleShotKit.bound(ctx)
            paparazzi.snapshot(scene.root, name = "r21c-hazards-${theme.name.lowercase()}", offsetMillis = 1000)
            val picker = com.husarp.ankigo.ui.SwitchPopup.content(ctx, GameRepo.data!!, GameRepo.state!!, {}, {})
            paparazzi.snapshot(picker, name = "r21c-switch-${theme.name.lowercase()}")
        }
    }
}
