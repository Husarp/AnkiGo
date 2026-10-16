package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.ArenaView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** R2-06: the tap on our Pokémon's sprite reaches ArenaView.onPlayerTap (the holder is clickable and described). */
class PlayerTapShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun tapReachesCallback() {
        val a = ArenaView(paparazzi.context)
        var taps = 0
        a.onPlayerTap = { taps++ }
        val holder = (0 until a.childCount).map { a.getChildAt(it) }.first { it.contentDescription?.startsWith("Your Pokémon") == true }
        assertEquals(true, holder.isClickable)
        holder.performClick()
        assertEquals(1, taps)
    }
}
