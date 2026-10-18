package com.husarp.ankigo.shots

import android.content.Context
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R2-05: the battle with and without "Show my Pokémon from the front". PNGs: r205-<mode>-<size>-<theme>,
 * mode = off | front; full panel at PHONE / SPLIT, small window at PIP / FLOATB.
 */
class R205Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun set(ctx: Context, front: Boolean) {
        AppPrefs.setPlayerFront(ctx, front)
    }

    private fun bare(ctx: Context): BattlePanelView = BattlePanelView(ctx).also {
        it.setCompact(true)
        it.renderAll()
    }

    private fun modes() = listOf("off" to false, "front" to true)

    @Test fun full() {
        for ((name, m) in modes()) paparazzi.shootWith("r205-$name", listOf(Size.PHONE, Size.SPLIT)) { ctx -> set(ctx, m); BattleShotKit.bound(ctx).root }
    }

    @Test fun small() {
        for ((name, m) in modes()) paparazzi.shootWith("r205-small-$name", listOf(Size.PIP, Size.FLOATB)) { ctx -> set(ctx, m); bare(ctx) }
    }
}
