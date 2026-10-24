package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.TeamPage
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** R18-D (123, 226): the battle chip row without boxes, spread over the line; the Team row's ⋯ and the top-bar icons bare. */
class R18DNoBoxesShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    /** The Battle tab: top bar (⋯), the panel with its chip row. */
    @Test fun battle() = paparazzi.shootWith("r18d-battle", listOf(Size.PHONE, Size.SPLIT)) { ctx -> BattleShotKit.bound(ctx).root }

    /** Small windows: the row scaled / thinned. */
    @Test fun small() = paparazzi.shootWith("r18d-small", listOf(Size.PIP_169M, Size.FLOATB)) { ctx ->
        AppPrefs.setCompactCards(ctx, true)
        BattlePanelView(ctx).apply { setCompact(true); renderAll() }
    }

    @Test fun team() = paparazzi.shootWith("r18d-team", listOf(Size.PHONE)) { ctx -> pageScreen(ctx, TeamPage(ctx, NoHost)) }
}
