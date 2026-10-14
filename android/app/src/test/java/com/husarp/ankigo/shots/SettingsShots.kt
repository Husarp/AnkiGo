package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.Accent
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BgJob
import com.husarp.ankigo.ui.FirstRunJob
import com.husarp.ankigo.ui.LeaveMode
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.ThemeMode
import org.junit.Rule
import org.junit.Test

/*
 * U-11: the Settings tab. Home list, and the sections Look (non-default accent, Dark chosen), Battle panel & windows,
 * Downloads (picture job running) and Game. PHONE, SPLIT and the floating card (340 x 480), light + dark.
 * Names: settings-<what>-<phone|split|float>-<light|dark>.
 */
class SettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE, Variants.SPLIT, Variant("float", 340, 480))

    private fun page(ctx: Context, section: SettingsSection?): View {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        if (section != null) p.open(section)
        return v
    }

    @Test fun home() {
        paparazzi.shootPage("settings-home", variants) { page(it, null) }
    }

    @Test fun homeNoGame() {
        paparazzi.shootPage("settings-home-nogame", listOf(Variants.PHONE), scenario = FakeGame.Scenario.EMPTY) { page(it, null) }
    }

    @Test fun look() {
        try {
            paparazzi.shootPage("settings-look", variants) { ctx ->
                AppLook.setAccent(ctx, Accent.TEAL)
                AppLook.setTheme(ctx, ThemeMode.DARK)
                page(ctx, SettingsSection.LOOK)
            }
            paparazzi.shootPage("settings-look-default", listOf(Variants.PHONE)) { ctx ->
                AppLook.setAccent(ctx, Accent.MAUVE)
                AppLook.setTheme(ctx, ThemeMode.SYSTEM)
                page(ctx, SettingsSection.LOOK)
            }
        } finally {
            AppLook.setAccent(Shots.files(paparazzi.context), Accent.MAUVE)
        }
    }

    @Test fun battle() {
        paparazzi.shootPage("settings-battle", variants) { ctx ->
            AppPrefs.setLeaveMode(ctx, LeaveMode.PIP)
            AppPrefs.setCompactCards(ctx, true)
            page(ctx, SettingsSection.BATTLE)
        }
        paparazzi.shootPage("settings-battle-default", listOf(Variants.PHONE)) { page(it, SettingsSection.BATTLE) }
    }

    @Test fun downloadsRunning() {
        val job = FirstRunJob.job
        job.begin("Starting…"); job.progress(0.9f, "Pictures (total estimated) · 0.7 MB of 1.5 MB")
        SettingsPage.picsFromSettings = true
        try {
            paparazzi.shootPage("settings-downloads-running", variants) { page(it, SettingsSection.DOWNLOADS) }
        } finally { job.reset(); SettingsPage.picsFromSettings = false }
        job.begin("Starting…"); job.fail("Many pictures didn't download. Check the internet connection and try again.")
        SettingsPage.picsFromSettings = true
        try {
            paparazzi.shootPage("settings-downloads-failed", listOf(Variants.PHONE)) { page(it, SettingsSection.DOWNLOADS) }
        } finally { job.reset(); SettingsPage.picsFromSettings = false }
        paparazzi.shootPage("settings-downloads", listOf(Variants.PHONE)) { page(it, SettingsSection.DOWNLOADS) }
    }

    @Test fun game() {
        paparazzi.shootPage("settings-game", variants) { page(it, SettingsSection.GAME) }
        paparazzi.shootPage("settings-game-nogame", listOf(Variants.PHONE), scenario = FakeGame.Scenario.EMPTY) { page(it, SettingsSection.GAME) }
    }

    @Test fun otherSections() {
        for (s in listOf(SettingsSection.STUDY, SettingsSection.SOUND, SettingsSection.ANKIMON, SettingsSection.SAVE, SettingsSection.ABOUT)) {
            paparazzi.shootPage("settings-${s.name.lowercase()}", listOf(Variants.PHONE)) { page(it, s) }
        }
    }
}
