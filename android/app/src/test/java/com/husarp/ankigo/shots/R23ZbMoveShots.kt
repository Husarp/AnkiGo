package com.husarp.ankigo.shots
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.movefx.*
import org.junit.Rule
import org.junit.Test
class R23ZbMoveShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun multiAndRecoil() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            for (before in listOf(true, false)) for ((id, type) in listOf("furyswipes" to Type.NORMAL, "doubleedge" to Type.NORMAL)) {
                val p = MoveFxCatalog.plan(id, type, Category.PHYSICAL, 80)
                paparazzi.snapshot(MoveFxShotKit.sheetOf(ctx, id, MoveFxShotKit.Frame.WIDE,
                    if (id == "furyswipes") listOf(410f, 660f, 910f) else listOf(410f, 700f, 840f)) { a, ms ->
                    if (before) {
                        val script = MoveFx.script(a.stage, p, true, 7)!!
                        val times = if (id == "furyswipes") R23ZbBeforeFx.multi(script) else R23ZbBeforeFx.recoil(script)
                        a.showEffect(MoveFxPlayer(a.stage, script, times, 1f) { FxRenderer(ctx, script.space.unit) }, ms)
                    } else a.showLook(p, ms)
                },
                    "r23zb-${if (before) "before" else "after"}-$id-${theme.name.lowercase()}", 1000)
            }
        }
    }
    @Test fun tintedGroupsAndHitCounts() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, Theme.DARK))
        val ctx = Shots.files(paparazzi.context)
        for ((id, type, count) in listOf(Triple("doublekick", Type.FIGHTING, 2), Triple("bulletseed", Type.GRASS, 5),
                Triple("populationbomb", Type.NORMAL, 10), Triple("bravebird", Type.FLYING, 1), Triple("flareblitz", Type.FIRE, 1))) {
            val multi = count > 1
            val p = MoveFxCatalog.plan(id, type, Category.PHYSICAL, 80, multiHit = multi, recoil = !multi, hits = count)
            paparazzi.snapshot(MoveFxShotKit.sheet(ctx, id, MoveFxShotKit.Frame.PIP, p,
                if (multi) listOf(410f, 660f, 380f + (count - 1) * 250f + 30f) else listOf(410f, 720f, 840f)), "r23zb-after-$id", 1000)
        }
    }

}
