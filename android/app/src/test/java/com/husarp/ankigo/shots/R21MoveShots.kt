package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.movefx.*
import org.junit.Rule
import org.junit.Test

/** Same real move renderer and contact-sheet kit as the existing move shot tests. */
class R21MoveShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun shoot(before: Boolean) {
        val examples = listOf(
            Triple("absorb", Type.GRASS, Category.SPECIAL), Triple("recover", Type.NORMAL, Category.STATUS),
            Triple("furyswipes", Type.NORMAL, Category.PHYSICAL), Triple("doubleedge", Type.NORMAL, Category.PHYSICAL),
            Triple("growl", Type.NORMAL, Category.STATUS),
        )
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            for ((id, type, category) in examples) {
                val power = when (id) { "absorb" -> 20; "furyswipes" -> 18; "doubleedge" -> 120; else -> 0 }
                val p = MoveFxCatalog.plan(if (before) "" else id, type, category, power)
                val moments = when (id) {
                    "absorb" -> listOf(570f, 1050f, 1500f)
                    "recover" -> listOf(300f, 700f, 1000f)
                    else -> listOf(380f, 550f, 900f)
                }
                val sheet = MoveFxShotKit.sheetOf(ctx, id, MoveFxShotKit.Frame.WIDE, moments) { a, ms ->
                    if (before && category == Category.STATUS) {
                        val script = MoveFx.script(a.stage, p, false, 7)!!
                        script.lean(0f) // exact original R18 status-aura lean
                        val times = AuraFx.build(script, moving = true)
                        val fx = MoveFxPlayer(a.stage, script, times, 1f) { FxRenderer(ctx, script.space.unit) }
                        a.showEffect(fx, ms)
                    } else a.showLook(p, ms, react = category != Category.STATUS)
                }
                paparazzi.snapshot(sheet, "r21b-${if (before) "before" else "after"}-$id-${theme.name.lowercase()}", 1000)
            }
            for (type in listOf(Type.FIGHTING, Type.POISON, Type.FLYING, Type.BUG, Type.ROCK, Type.DRAGON, Type.DARK, Type.STEEL, Type.FAIRY)) {
                val p = MoveFxCatalog.plan("", type, Category.PHYSICAL, 40).let { if (before) it.copy(kit = FxKit.PLAIN) else it }
                paparazzi.snapshot(MoveFxShotKit.sheet(ctx, type.name, MoveFxShotKit.Frame.PIP, p, listOf(330f, 500f)),
                    "r21b-${if (before) "before" else "after"}-kit-${type.name.lowercase()}-${theme.name.lowercase()}", 1000)
            }
        }
    }
    @Test fun originalLooks() = shoot(true)
    @Test fun pickedLooks() = shoot(false)
}
