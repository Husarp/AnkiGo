package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R27FWindowShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() = BattleShotKit.reset()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun evolutionItemWindow() {
        paparazzi.shoot("r27f-fire-stone", sizes = listOf(Size.SMALL)) { ctx ->
            val data = GameRepo.data!!
            val state = GameRepo.state!!
            val parent = data.sp(133)
            val mon = state.mons.single { it.species == 133 }
            state.bag["fire-stone"] = 1
            val details = DialogShots.capture { MonDetail.show(ctx, data, mon) }!!
            val dialog = EvolutionRows.explanationDialog(ctx, data, Evolution(136, EvoKind.ITEM, item = "fire-stone"),
                parent, state.caught, state, mon) {}
            dialog.joinStack()
            val all = views(dialog.content())
            val scroll = all.filterIsInstance<ScrollView>().single()
            val close = all.single { it.contentDescription == "Close" }
            assertFalse(views(scroll).contains(close))
            val text = all.filterIsInstance<TextView>().joinToString("\n") { it.text }
            assertTrue(text.contains("Fire Stone"))
            assertTrue(text.contains(com.husarp.ankigo.game.ItemInfo.whereToFind(data, "fire-stone")))
            FrameLayout(ctx).apply {
                addView(DialogShots.scene(ctx, details))
                addView(DialogShots.scene(ctx, dialog))
                // The actual close control must remove only the explanation from the stack.
                close.performClick()
                assertTrue(WindowStack.stacked(ctx).not())
                details.dismiss()
            }
        }
    }

    @Test fun longRequirementsScrollUnderPinnedClose() {
        paparazzi.shoot("r27f-long-info", sizes = listOf(Size.SMALL)) { ctx ->
            val body = Ui.text(ctx, 14f, 700, com.husarp.ankigo.R.color.ag_text).apply {
                text = "A long explanation stays inside the game window.\n".repeat(40)
            }
            val dialog = DialogShots.capture { Pickers.info(ctx, "Requirements", body) }!!
            val card = dialog.content()
            assertEquals(Ui.dp(ctx, 24), (body.parent as View).paddingLeft)
            assertEquals(Ui.dp(ctx, 24), (body.parent as View).paddingRight)
            val close = views(card).single { it.contentDescription == "Close" }
            assertFalse(views(views(card).filterIsInstance<ScrollView>().single()).contains(close))
            DialogShots.scene(ctx, dialog).also { dialog.dismiss() }
        }
    }
}
