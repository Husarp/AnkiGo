package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import org.junit.Rule
import org.junit.Test

/** R2-V11: the battle screen with and without the "Continue studying" bar (PNGs r2v11-with / r2v11-without, PHONE, light + dark). */
class R2V11Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE)

    @Test fun withBar() = paparazzi.shootWith("r2v11-with", sizes) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.activity.setContinueDeck(Anki.Deck(7, "Languages::Sample Core 2k", 3, 31, 8, false))
        b.root
    }

    @Test fun withoutBar() = paparazzi.shootWith("r2v11-without", sizes) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.activity.setContinueDeck(null)
        b.root
    }

    /** Split screen / short window: the top bar is hidden, so is the Continue bar. */
    @Test fun splitHidden() = paparazzi.shootWith("r2v11-split", listOf(Size.SPLIT)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.activity.setContinueDeck(Anki.Deck(7, "Languages::Sample Core 2k", 3, 31, 8, false))
        b.root
    }
}
