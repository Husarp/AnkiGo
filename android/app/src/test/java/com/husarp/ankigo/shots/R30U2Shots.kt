package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30U2Shots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun selectedBondPlacementAndEvolutionRequirement() {
        for (theme in Theme.entries) for (mode in listOf("expanded", "collapsed", "requirement")) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p412", 412, 869).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val state = GameRepo.state!!
            val original = GameRepo.data!!
            val data = if (mode == "requirement") {
                val source = original.sp(133)
                val branch = source.evolutions.single { it.kind == EvoKind.FRIENDSHIP }.copy(moveType = null)
                GameData(original.species + (133 to source.copy(evolutions = listOf(branch))), original.moves,
                    original.growth, original.itemPrices)
            } else original
            val mon = if (mode == "requirement") state.mons.first { it.species == 133 } else state.main!!
            mon.bondPoints = if (mode == "requirement") 125 else 284
            val oldStore = DetailSections.storeOf
            DetailSections.storeOf = { object : DetailOpenStore {
                override fun get(key: String) = key == if (mode == "expanded") "bond" else if (mode == "requirement") "evolution" else ""
                override fun set(key: String, open: Boolean) {}
            } }
            try {
                val dialog = DialogShots.capture { MonDetail.show(ctx, data, mon) }!!
                val scene = DialogShots.scene(ctx, dialog)
                val dm = ctx.resources.displayMetrics
                scene.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, dm.widthPixels, dm.heightPixels)
                val bond = all(scene).single { it.tag == DetailSection.BOND }
                val scroll = all(scene).filterIsInstance<ScrollView>().first { bond in all(it) }
                if (mode != "requirement") scroll.scrollTo(0, bond.top)
                else scroll.scrollTo(0, Ui.dp(ctx, 170))
                paparazzi.snapshot(scene, "u2-$mode-${theme.name.lowercase()}", 1000)
                val visible = all(bond).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }
                val level = visible.single { it.text.toString() == "Level ${if (mode == "requirement") 2 else 3}" }
                assertEquals(Ui.c(ctx, com.husarp.ankigo.R.color.ag_bond_level), level.currentTextColor)
                if (mode == "expanded") {
                    val ball = all(bond).filterIsInstance<ImageView>().single { it.layoutParams.width == Ui.dp(ctx, 72) }
                    assertSame(ball.parent, level.parent)
                    assertTrue(level.top >= ball.bottom)
                    assertTrue(kotlin.math.abs((level.left + level.right) - (ball.left + ball.right)) <= 1)
                    assertTrue(visible.any { it.text.toString() == "Growing close" })
                } else {
                    val bar = all(bond).single { it.contentDescription == "Bond, closed" }
                    assertSame(bar, level.parent)
                    assertTrue(level.left > bar.width / 2)
                }
                assertFalse(all(scene).filterIsInstance<TextView>().any { it.text.contains('♥') })
                if (mode == "requirement") assertTrue(all(scene).filterIsInstance<TextView>().any {
                    it.text.toString().startsWith("Level 3 Bond")
                })
                dialog.dismiss()
            } finally { DetailSections.storeOf = oldStore }
        }
    }
}
