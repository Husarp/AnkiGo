package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalette
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.Rule
import org.junit.Test

/**
 * R7-04: Ember, Water Gun jet, Vine Whip (3 and 4 vines) and Ice Beam, each frozen at 4 moments, our creature attacking and the
 * wild one attacking, at the phone-wide and the picture-in-picture arena size, light and dark. Placeholder creatures only
 * (MoveFxShotKit). Files: `r704-<look>-<frame>-<theme>.png`.
 */
class MoveFxLooksAShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val palette = FxPalette(0xFF3A1458.toInt(), 0xFFB06BFF.toInt(), 0xFF52207A.toInt(), 0xFFE1B8FF.toInt())

    private fun plan(look: MoveLook, kit: FxKit, k: Float = 1f) = MoveFxPlan(look, k, kit, palette)

    /** One render per theme on a device sized to the sheet: two columns of [frame] x [rows] rows (the stock sizes are too short for four moments). */
    private fun shoot(name: String, frame: MoveFxShotKit.Frame, rows: Int, build: (Context) -> View) {
        for (theme in Theme.entries) {
            val w = 2 * frame.wDp + 60
            val h = rows * (frame.hDp + 8) + 120
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme).copy(screenWidth = w * 2, screenHeight = h * 2, orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
            val ctx = Shots.files(paparazzi.context)
            val root = FrameLayout(ctx).apply { addView(build(ctx), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(root, "$name-${theme.name.lowercase()}", 1000)
        }
    }

    private fun sheet(prefix: String, label: String, look: MoveLook, kit: FxKit, frame: MoveFxShotKit.Frame, moments: List<Float>, k: Float = 1f) =
        shoot("r704-$prefix-${frame.name.lowercase()}", frame, moments.size) { ctx -> MoveFxShotKit.sheet(ctx, label, frame, plan(look, kit, k), moments) }

    private val ember = listOf(170f, 300f, 460f, 700f)
    private val jet = listOf(200f, 330f, 600f, 840f)
    private val vines = listOf(260f, 430f, 560f, 800f)
    private val ice = listOf(200f, 520f, 1000f, 1320f)

    @Test fun emberWide() = sheet("ember", "Ember", MoveLook.EMBER, FxKit.FIRE, MoveFxShotKit.Frame.WIDE, ember)
    @Test fun emberPip() = sheet("ember", "Ember", MoveLook.EMBER, FxKit.FIRE, MoveFxShotKit.Frame.PIP, ember)
    @Test fun emberBigWide() = sheet("ember-k16", "Flamethrower-sized Ember (k 1.6)", MoveLook.EMBER, FxKit.FIRE, MoveFxShotKit.Frame.WIDE, ember, 1.6f)

    @Test fun jetWide() = sheet("jet", "Water Gun", MoveLook.WATER_JET, FxKit.WATER, MoveFxShotKit.Frame.WIDE, jet)
    @Test fun jetPip() = sheet("jet", "Water Gun", MoveLook.WATER_JET, FxKit.WATER, MoveFxShotKit.Frame.PIP, jet)
    @Test fun jetBigWide() = sheet("jet-k16", "Hydro-Pump-sized jet (k 1.6)", MoveLook.WATER_JET, FxKit.WATER, MoveFxShotKit.Frame.WIDE, jet, 1.6f)

    @Test fun vinesWide() = sheet("vine", "Vine Whip (3 vines)", MoveLook.VINE_WHIP, FxKit.GRASS, MoveFxShotKit.Frame.WIDE, vines)
    @Test fun vinesPip() = sheet("vine", "Vine Whip (3 vines)", MoveLook.VINE_WHIP, FxKit.GRASS, MoveFxShotKit.Frame.PIP, vines)
    @Test fun vinesFourWide() = sheet("vine-k16", "Power-Whip-sized (k 1.6, 4 vines)", MoveLook.VINE_WHIP, FxKit.GRASS, MoveFxShotKit.Frame.WIDE, listOf(300f, 560f, 780f, 1000f), 1.6f)

    @Test fun iceWide() = sheet("ice", "Ice Beam", MoveLook.ICE_BEAM, FxKit.ICE, MoveFxShotKit.Frame.WIDE, ice)
    @Test fun icePip() = sheet("ice", "Ice Beam", MoveLook.ICE_BEAM, FxKit.ICE, MoveFxShotKit.Frame.PIP, ice)
}
