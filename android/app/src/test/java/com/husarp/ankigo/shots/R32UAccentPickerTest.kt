package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32UAccentPickerTest {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    @After fun reset() { AppLook.setAccent(Shots.files(paparazzi.context), Accent.GREEN) }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun savedIdsNeedNoMigrationAndGreenRemainsDefault() {
        val ctx = Shots.files(paparazzi.context)
        val prefs = ctx.getSharedPreferences("ankigo_ui", 0)
        val legacy = listOf("MAUVE", "BLUE", "TEAL", "GREEN", "PEACH", "PINK", "MULTICOLOR")
        assertEquals(legacy, Accent.entries.take(7).map { it.name })
        assertEquals(22, Accent.entries.size)
        assertEquals(22, Accent.entries.map { it.res to it.label }.toSet().size)
        assertEquals(Accent.GREEN, AppPrefs.accent(ctx))
        for (accent in Accent.entries) {
            prefs.edit().putString("accent", accent.name).commit()
            assertEquals(accent, AppPrefs.accent(ctx))
            assertEquals("Reading a saved choice does not rewrite it", accent.name, prefs.getString("accent", null))
            AppLook.setAccent(ctx, accent)
            assertEquals(accent.res, AppLook.accentRes(ctx))
            assertEquals(accent.name, prefs.getString("accent", null))
        }
        prefs.edit().putString("accent", "future-or-invalid").commit()
        assertEquals(Accent.GREEN, AppPrefs.accent(ctx))
    }

    @Test fun everyAccentAndMulticolorSegmentContrastsOnBothThemes() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            for (accent in Accent.entries) {
                AppLook.setAccent(ctx, accent)
                assertEquals(ctx.getColor(accent.res), Ui.c(ctx, R.color.ag_mauve))
                val colours = if (accent == Accent.MULTICOLOR) Multicolor.Tone.entries.map {
                    if (theme == Theme.DARK) it.dark else it.light
                } else listOf(Ui.c(ctx, R.color.ag_mauve))
                for (colour in colours) for (surface in listOf(R.color.ag_card, R.color.ag_mantle, R.color.ag_base)) {
                    assertTrue("$accent / $theme / $surface", ColorUtils.calculateContrast(colour, ctx.getColor(surface)) >= 3.0)
                    assertTrue("Selected check is readable", ColorUtils.calculateContrast(Multicolor.onFill(colour), colour) >= 4.5)
                }
            }
        }
    }

    @Test fun productionRowAndLivePickerAtRequestedWidths() {
        for (width in listOf(360, 412)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(screenWidth = width * 2,
                screenHeight = 869 * 2))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            AppLook.setAccent(ctx, Accent.GREEN)
            val page = SettingsPage(ctx, NoHost)
            val screen = pageScreen(ctx, page)
            page.open(SettingsSection.LOOK)
            fun row() = Form.rowsIn(screen).single { Form.titleOf(it) == "Accent" }
            assertTrue(all(row()).filterIsInstance<TextView>().any { it.text == "Green" })
            val dialog = AppDialogShots.capture {
                all(row()).filterIsInstance<TextView>().single { it.text == "Pick" }.performClick()
            }!!
            val body = dialog.content()
            val choices = all(body).filter { it.isClickable && it.contentDescription?.toString() in
                Accent.entries.flatMap { a -> listOf(a.label, "${a.label}, selected") } }
            assertEquals(22, choices.size)
            assertEquals(listOf("Green, selected"), choices.filter { it.isSelected }.map { it.contentDescription.toString() })
            for (accent in Accent.entries) {
                val choice = choices.single { it.contentDescription.toString().substringBefore(",") == accent.label }
                assertNull("No tile behind a swatch", choice.background)
                choice.performClick()
                assertSame("One persistent picker", body, dialog.content())
                assertEquals(accent, AppPrefs.accent(ctx))
                assertEquals(listOf(choice), choices.filter { it.isSelected })
                assertTrue("Current colour changes behind the dialog", all(row()).filterIsInstance<TextView>().any { it.text == accent.label })
                val progress = Form.rowsIn(screen).single { Form.titleOf(it) == "Progress bars" }
                assertEquals(accent == Accent.MULTICOLOR, all(progress).filterIsInstance<TextView>().any { it.text == "By meaning · Multicolor" })
            }
            choices.single { it.contentDescription == "Multicolor" }.performClick()
            paparazzi.snapshot(screen, "u-settings-row-$width-${theme.name.lowercase()}", 1000)
            val stage = FrameLayout(ctx).apply {
                (screen.parent as? ViewGroup)?.removeView(screen)
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(AppDialogShots.scene(ctx, dialog), FrameLayout.LayoutParams(-1, -1))
            }
            paparazzi.snapshot(stage, "u-picker-$width-${theme.name.lowercase()}", 1000)
            for (choice in choices) {
                assertTrue("Touch width", choice.width >= Ui.dp(ctx, 48))
                assertTrue("Touch height", choice.height >= Ui.dp(ctx, 48))
                val label = all(choice).filterIsInstance<TextView>().single()
                assertEquals(0, label.layout.getEllipsisCount(0))
            }
            page.onGone()
        }
    }

    @Test fun narrowShortPickerWrapsAndScrollsToLastChoice() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.FLOATBATTLE, theme))
            val ctx = Shots.files(paparazzi.context)
            AppLook.setAccent(ctx, Accent.SLATE)
            val dialog = AccentPicker.dialog(ctx) {}
            val scene = AppDialogShots.scene(ctx, dialog)
            paparazzi.snapshot(scene, "u-picker-320-${theme.name.lowercase()}", 1000)
            val scroll = all(scene).filterIsInstance<ScrollView>().single()
            assertTrue("Short windows can scroll", scroll.getChildAt(0).height > scroll.height)
            val last = all(scene).single { it.contentDescription == "Slate, selected" }
            val parent = last.parent as View
            assertTrue(last.left >= parent.paddingLeft)
            assertTrue(last.right <= parent.width - parent.paddingRight)
            assertTrue("Wrapped grid", last.top > Ui.dp(ctx, 200))
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            paparazzi.snapshot(scene, "u-picker-320-scrolled-${theme.name.lowercase()}", 1000)
            assertTrue(scroll.scrollY > 0)
            assertTrue("Last choice scrolls into view", last.bottom + parent.top - scroll.scrollY <= scroll.height)
        }
    }
}
