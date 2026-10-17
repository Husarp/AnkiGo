package com.husarp.ankigo.shots

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalettes
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.Rule
import org.junit.Test

/**
 * R7-08: Thunderbolt, Psychic, Shadow Ball, Earthquake and the special / status templates (orb in steel and in fire + kit, aura in
 * steel and in fire + kit), 3-4 moments each, both directions, phone-wide and picture-in-picture arena, light and dark.
 * Placeholder creatures only (MoveFxShotKit). Files: `r708-<look>-<frame>-<theme>.png`.
 */
class MoveFxLooksBShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun shoot(name: String, label: String, frame: MoveFxShotKit.Frame, plan: MoveFxPlan, moments: List<Float>) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            val root = FrameLayout(ctx).apply { addView(MoveFxShotKit.sheet(ctx, label, frame, plan, moments), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(root, "r708-$name-${frame.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    /** The phone-wide arenas are tall: three moments per sheet (a, b), the small picture-in-picture arenas all four on one. */
    private fun both(name: String, label: String, plan: MoveFxPlan, moments: List<Float>) {
        if (moments.size <= 3) shoot(name, label, MoveFxShotKit.Frame.WIDE, plan, moments)
        else moments.chunked(2).forEachIndexed { i, m -> shoot("$name-${'a' + i}", label, MoveFxShotKit.Frame.WIDE, plan, m) }
        shoot(name, label, MoveFxShotKit.Frame.PIP, plan, moments)
    }

    private val steel = FxPalettes.of(Type.STEEL)
    private val fire = FxPalettes.of(Type.FIRE)

    @Test fun thunderbolt() = both("thunderbolt", "Thunderbolt", MoveFxPlan(MoveLook.THUNDERBOLT, 1f, FxKit.ELECTRIC, FxPalettes.of(Type.ELECTRIC)), listOf(180f, 260f, 420f, 800f))
    @Test fun thunderTwoBranches() = both("thunder-k16", "Thunder-like k 1.6", MoveFxPlan(MoveLook.THUNDERBOLT, 1.6f, FxKit.ELECTRIC, FxPalettes.of(Type.ELECTRIC)), listOf(260f, 420f))
    @Test fun psychic() = both("psychic", "Psychic", MoveFxPlan(MoveLook.PSYCHIC, 1f, FxKit.PSYCHIC, FxPalettes.of(Type.PSYCHIC)), listOf(200f, 450f, 700f, 1200f))
    @Test fun shadowBall() = both("shadowball", "Shadow Ball", MoveFxPlan(MoveLook.SHADOW_BALL, 1f, FxKit.GHOST, FxPalettes.of(Type.GHOST)), listOf(300f, 700f, 880f, 1000f))
    @Test fun orbSteel() = both("orb-steel", "Orb template, Steel (plain)", MoveFxPlan(MoveLook.ORB_TEMPLATE, 1f, FxKit.PLAIN, steel), listOf(400f, 800f, 920f, 1100f))
    @Test fun orbFire() = both("orb-fire", "Orb template, Fire kit", MoveFxPlan(MoveLook.ORB_TEMPLATE, 1.2f, FxKit.FIRE, fire), listOf(400f, 800f, 960f, 1150f))
    @Test fun auraSteel() = both("aura-steel", "Aura template, Steel (plain)", MoveFxPlan(MoveLook.AURA_TEMPLATE, 1f, FxKit.PLAIN, steel), listOf(100f, 300f, 550f, 800f))
    @Test fun auraFire() = both("aura-fire", "Aura template, Fire kit", MoveFxPlan(MoveLook.AURA_TEMPLATE, 1f, FxKit.FIRE, fire), listOf(100f, 300f, 550f, 800f))
    @Test fun earthquake() = both("earthquake", "Earthquake", MoveFxPlan(MoveLook.EARTHQUAKE, 1f, FxKit.GROUND, FxPalettes.of(Type.GROUND)), listOf(120f, 175f, 400f, 800f))
}
