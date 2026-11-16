package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Move details now live in their own dismissible window. */
class R9MoveTextShots {
    @get:Rule val paparazzi = Shots.rule()
    private val ids = listOf("thunderbolt", "thunderwave", "swordsdance", "seismictoss", "fissure", "splash", "quickattack", "doubleedge", "recover", "absorb")
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun closedListContainsNoInlineDescriptions() {
        paparazzi.shoot("r9movetext-closed", listOf(Size.PHONE, Size.FLOAT)) { ctx ->
            val moves = DetailSections.moves(ctx, GameRepo.data!!, ids, false)
            assertFalse(all(moves).filterIsInstance<TextView>().any { it.text.contains("Paralyzes") })
            scrollOnBase(ctx, moves)
        }
    }

    @Test fun tapsOpenTheSelectedMoveAndLeaveTheListUnchanged() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
            val moves = DetailSections.moves(ctx, GameRepo.data!!, ids, true)
            for (id in listOf("thunderbolt", "swordsdance", "fissure")) {
                val dialog = DialogShots.capture { moves.findViewWithTag<View>("detail-move:$id").performClick() }!!
                val text = all(dialog.content()).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(text.contains(GameRepo.data!!.move(id)!!.name))
                assertTrue(text.contains("Done"))
                assertFalse(all(moves).filterIsInstance<TextView>().any { it.text.contains("stage") })
                if (id == "swordsdance") { assertTrue(text.contains("Power")); assertTrue(text.contains("—")) }
                paparazzi.snapshot(DialogShots.scene(ctx, dialog), "r9movetext-$id-${theme.name.lowercase()}")
                dialog.dismiss()
            }
        }
    }
}
