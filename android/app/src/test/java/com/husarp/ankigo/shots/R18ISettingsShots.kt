package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Rule
import org.junit.Test

/*
 * R18-I (PLAN 255, 256, 263): Settings home with the Widgets row, the Widgets card, Settings -> Study with the moved switch
 * (under "Advanced"), Diagnostics without the App group and with the shorter log box. Light + dark.
 */
class R18ISettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE)

    private fun page(ctx: Context, f: (SettingsPage) -> Unit = {}): View {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        f(p)
        return v
    }

    @Test fun home() { paparazzi.shootPage("r18i-settings-home", variants) { ctx -> page(ctx) } }

    @Test fun widgets() { paparazzi.shootPage("r18i-widgets", variants) { ctx -> page(ctx) { it.open(SettingsSection.WIDGETS) } } }

    @Test fun study() {
        paparazzi.shootPage("r18i-study-advanced", listOf(Variant("tall", 360, 2000))) { ctx ->
            page(ctx) { it.open(SettingsSection.STUDY) }
        }
    }

    @Test fun diagnostics() {
        paparazzi.shootPage("r18i-diagnostics", listOf(Variant("w412", 412, 869))) { ctx ->
            val a = attach(DiagnosticsActivity(), ShotCtx(ctx))
            val content = a.buildContent()
            (getField(a, "header") as TextView).text = "▶ com.ichi2.anki  v2.24.0  provider=yes  permission=granted"
            (getField(a, "logView") as TextView).text = (1..200).joinToString("\n") { "03:%02d C1 OK $it decks readable".format(it % 60) }
            content
        }
    }
}
