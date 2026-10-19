package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.remind.ReminderPermission
import com.husarp.ankigo.remind.ReminderPrefs
import com.husarp.ankigo.remind.ReminderStore
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Rule
import org.junit.Test

/*
 * R3-V2 (pick 2A): Settings home with the search box and the new Reminders / Help rows, search results, the Game section in
 * groups with a line under every setting, Study, Reminders in place, and a search hit highlighted. PHONE + SPLIT, light + dark.
 * Names: settings3-<what>-<phone|split>-<light|dark>.
 */
class SettingsR3Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE, Variants.SPLIT)

    private fun page(ctx: Context, f: (SettingsPage) -> Unit = {}): View {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        f(p)
        return v
    }

    @Test fun home() {
        paparazzi.shootPage("settings3-home", variants) { ctx ->
            ReminderStore.save(ReminderStore.prefs(ctx), ReminderPrefs(dueOn = true, streakOn = true))
            page(ctx)
        }
    }

    @Test fun searchResults() {
        paparazzi.shootPage("settings3-search", variants) { ctx -> page(ctx) { it.showSearch("catch") } }
        paparazzi.shootPage("settings3-search-none", listOf(Variants.PHONE)) { ctx -> page(ctx) { it.showSearch("zzzq") } }
    }

    @Test fun game() {
        paparazzi.shootPage("settings3-game", variants) { ctx -> page(ctx) { it.open(SettingsSection.GAME) } }
    }

    @Test fun study() {
        paparazzi.shootPage("settings3-study", variants) { ctx -> page(ctx) { it.open(SettingsSection.STUDY) } }
    }

    @Test fun reminders() {
        ReminderPermission.canPostOverride = { true }
        try {
            paparazzi.shootPage("settings3-reminders", variants) { ctx ->
                ReminderStore.save(ReminderStore.prefs(ctx), ReminderPrefs(dueOn = true, streakOn = true))
                page(ctx) { it.open(SettingsSection.REMINDERS) }
            }
        } finally { ReminderPermission.canPostOverride = null }
    }

    @Test fun hitHighlighted() {
        paparazzi.shootPage("settings3-hit", variants) { ctx ->
            page(ctx) { it.openSetting(SettingsCatalog.byId("autoMode1")!!) }
        }
    }

    /** B8: a search result for a reminder setting opens Settings → Reminders (and that reminder's sheet, a window the shot does not show). */
    @Test fun reminderHitOpensRemindersSection() {
        ReminderPermission.canPostOverride = { true }
        try {
            paparazzi.shootPage("settings3-reminder-hit", listOf(Variants.PHONE)) { ctx ->
                ReminderStore.save(ReminderStore.prefs(ctx), ReminderPrefs(dueOn = true, streakOn = true))
                page(ctx) { it.openSetting(SettingsCatalog.byId("minGapHours")!!) }
            }
        } finally { ReminderPermission.canPostOverride = null }
    }

    @Test fun otherSections() {
        for (s in listOf(SettingsSection.LOOK, SettingsSection.BATTLE, SettingsSection.SOUND, SettingsSection.DOWNLOADS, SettingsSection.SAVE)) {
            paparazzi.shootPage("settings3-${s.name.lowercase()}", listOf(Variants.PHONE)) { ctx -> page(ctx) { it.open(s) } }
        }
    }
}
