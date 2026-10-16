package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.SwitchPopup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R2-V13: the "Switch Pokémon" grid popup (item 99). PNGs: r2-switch-<size>-<theme> (team of 6, the 5th is fainted). */
class R2V13Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun popup() = paparazzi.shootPage("r2-switch") { ctx ->
        val d = DialogShots.capture { SwitchPopup.show(ctx, GameRepo.data!!, GameRepo.state!!) {} } ?: error("no dialog was shown")
        DialogShots.scene(ctx, d)
    }

    @Test fun faintedCantBePicked() {
        assertTrue(SwitchPopup.pickable(1))
        assertFalse(SwitchPopup.pickable(0))
        assertFalse(SwitchPopup.pickable(-3))
    }

    /** A tap on a standing member picks it; a tap on the fainted one (t5 in the fake team) does nothing. */
    @Test fun tapsPickOnlyStandingOnes() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val picked = ArrayList<String>()
        val root = SwitchPopup.content(ctx, GameRepo.data!!, GameRepo.state!!, { picked += it }, {})
        val cards = ArrayList<android.view.View>()
        fun walk(v: android.view.View) { if (v.hasOnClickListeners()) cards += v; (v as? android.view.ViewGroup)?.let { g -> for (i in 0 until g.childCount) walk(g.getChildAt(i)) } }
        walk(root)
        assertEquals(6, cards.size)
        cards.forEach { it.performClick() }
        assertEquals(listOf("t1", "t2", "t3", "t4", "t6"), picked)
    }
}
