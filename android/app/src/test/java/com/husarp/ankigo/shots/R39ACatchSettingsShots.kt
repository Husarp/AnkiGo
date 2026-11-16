package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.CatchChoice
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.SettingsPage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R39ACatchSettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun catchChoices() = paparazzi.shootPage("a-catch-settings", listOf(Variants.PHONE)) { ctx ->
        CatchChoice.apply(GameRepo.state!!.settings, CatchChoice.ASK_EVERY)
        val c = ShotCtx(ctx)
        val page = SettingsPage(c, NoHost)
        val screen = pageScreen(c, page)
        page.openSetting(SettingsCatalog.byId("catchEvery")!!)
        val rows = Form.rowsIn(page.root)
        for (choice in listOf(CatchChoice.NEW_AND_BETTER, CatchChoice.ASK_EVERY, CatchChoice.ASK_ME, CatchChoice.ASK_EVERY)) {
            rows.single { Form.titleOf(it) == choice.title }.performClick()
            val saved = SaveCodec.decode(SaveCodec.encode(GameRepo.state!!))
            assertEquals(choice, CatchChoice.of(saved.settings))
        }
        screen
    }
}
