package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Rule
import org.junit.Test

/** R14 F (PLAN 202): the Study screen without the AnkiDroid pill, the warning cards, and the Settings About row. Names r14f-*. */
class R14FShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val both = listOf(Size.PHONE, Size.SPLIT)

    @Test fun ok() = paparazzi.shootWith("r14f-study-ok", both) { StudyDeckKit.build(it) }

    @Test fun tooOld() = paparazzi.shootWith("r14f-study-old", both) { StudyDeckKit.build(it, ankiVersion = "2.22.4") }

    @Test fun notInstalled() = paparazzi.shootWith("r14f-study-missing", both) {
        StudyDeckKit.build(it, ankiVersion = null, problem = "AnkiDroid isn't installed.")
    }

    @Test fun noPermission() = paparazzi.shootWith("r14f-study-perm", both) {
        StudyDeckKit.build(it, granted = false, problem = "Allow AnkiGo to use AnkiDroid first.")
    }

    @Test fun settingsAbout() = paparazzi.shootPage("r14f-settings-about", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
        val c = ShotCtx(ctx, ankiVersion = "2.25.1")
        val p = SettingsPage(c, NoHost)
        pageScreen(c, p).also { p.open(SettingsSection.ABOUT) }
    }

    @Test fun settingsAboutProblem() = paparazzi.shootPage("r14f-settings-about-old", listOf(Variants.PHONE)) { ctx ->
        val c = ShotCtx(ctx, ankiVersion = "2.22.4")
        val p = SettingsPage(c, NoHost)
        pageScreen(c, p).also { p.open(SettingsSection.ABOUT) }
    }
}
