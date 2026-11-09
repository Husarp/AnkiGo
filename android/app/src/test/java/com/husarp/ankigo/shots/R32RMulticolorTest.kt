package com.husarp.ankigo.shots

import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32RMulticolorTest {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { AppLook.setAccent(Shots.files(paparazzi.context), Accent.GREEN) }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun newSettingRoundTripsWithoutChangingExistingChoices() {
        val ctx = Shots.files(paparazzi.context)
        val prefs = ctx.getSharedPreferences("ankigo_ui", 0)
        assertEquals(Accent.GREEN, AppPrefs.accent(ctx))
        for (accent in Accent.entries) {
            prefs.edit().putString("accent", accent.name).commit()
            assertEquals(accent, AppPrefs.accent(ctx))
            val generation = AppLook.generation
            AppLook.setAccent(ctx, accent)
            assertEquals(generation + 1, AppLook.generation)
            assertEquals(accent.name, prefs.getString("accent", null))
            assertEquals(accent.res, AppLook.accentRes(ctx))
            assertEquals(accent == Accent.MULTICOLOR, Multicolor.enabled(ctx))
        }
        prefs.edit().putString("accent", "unknown").commit()
        assertEquals(Accent.GREEN, AppPrefs.accent(ctx))
        assertEquals("Multicolor", Accent.MULTICOLOR.label)
        assertEquals("System · Multicolor · 100 %", SettingsSummary.look(ThemeMode.SYSTEM, Accent.MULTICOLOR, 100))
    }

    @Test fun liftedPaletteKeepsHueAndContrastOnBothSectionCards() {
        for (tone in Multicolor.Tone.entries) {
            val base = FloatArray(3).also { ColorUtils.colorToHSL(tone.base, it) }
            for ((color, backgrounds) in listOf(
                tone.light to listOf("#e6e9ef", "#ffffff"), tone.dark to listOf("#181825", "#262637")
            )) {
                for (bg in backgrounds) assertTrue("$tone on $bg", ColorUtils.calculateContrast(color, Color.parseColor(bg)) >= 3.0)
                val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
                assertEquals("Hue: $tone", base[0], hsl[0], 1f)
                assertTrue("Lighter: $tone", hsl[2] > base[2])
                assertTrue(hsl[2] <= base[2] * 1.08f)
                if (color == tone.dark) assertEquals(base[2] * 1.07f, hsl[2], .003f)
                assertTrue(ColorUtils.calculateContrast(Multicolor.onFill(color), color) >= 4.5)
            }
        }
    }

    @Test fun mappingsAndRenderedSectionColours() {
        assertEquals(listOf(Multicolor.Tone.PURPLE, Multicolor.Tone.GREEN, Multicolor.Tone.BLUE, Multicolor.Tone.ORANGE,
            Multicolor.Tone.RED, Multicolor.Tone.AMBER, Multicolor.Tone.PINK, Multicolor.Tone.TEAL), DetailSection.entries.map(Multicolor::section))
        assertEquals(listOf(Multicolor.Tone.BLUE, Multicolor.Tone.GREEN, Multicolor.Tone.ORANGE, Multicolor.Tone.PURPLE,
            Multicolor.Tone.TEAL), BottomNav.Tab.entries.map(Multicolor::tab))
        assertEquals(listOf(Multicolor.Tone.BLUE, Multicolor.Tone.ORANGE, Multicolor.Tone.TEAL, Multicolor.Tone.GREEN),
            BarMeaning.entries.map(Multicolor::bar))
        assertEquals(Multicolor.Tone.PINK, Multicolor.place("Favourites"))
        assertEquals(Multicolor.Tone.PURPLE, Multicolor.place("Bag"))
        assertEquals(Multicolor.Tone.BLUE, Multicolor.place("Box"))
        for (group in listOf(listOf("Team", "Box", "Pokédex"), listOf("Shop", "Bag"),
            listOf("Statistics", "Achievements"), listOf("All", "Owned", "Seen", "Not owned"),
            listOf("Study", "Battle", "Collection", "Item collections", "Economy", "Special", "Badges", "Secret"))) {
            assertEquals(group.size, group.map(Multicolor::place).distinct().size)
        }
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            AppLook.setAccent(ctx, Accent.MULTICOLOR)
            val mem = DetailOpen(object : DetailOpenStore {
                override fun get(key: String) = false
                override fun set(key: String, open: Boolean) = Unit
            })
            for (section in DetailSection.entries) {
                val card = DetailSections.section(ctx, mem, section, null) { View(ctx) } as LinearLayout
                val bar = card.getChildAt(0) as LinearLayout
                val icon = bar.getChildAt(0) as ImageView
                assertEquals(Color.parseColor(listOf("#9d79db", "#3fb05c", "#5893e4", "#de7930", "#df6e74", "#c19706", "#d36d9f", "#1daca8")[section.ordinal]), (icon.colorFilter as PorterDuffColorFilter).color)
                assertEquals(Ui.c(ctx, R.color.ag_text), all(bar.getChildAt(1)).filterIsInstance<TextView>().single().currentTextColor)
            }
            val tabs = LinearLayout(ctx)
            SectionSwitch.draw(ctx, tabs, listOf(
                SectionSwitch.Seg("Team", R.drawable.ic_team), SectionSwitch.Seg("Box", R.drawable.ic_grid),
                SectionSwitch.Seg("Pokédex", R.drawable.ic_dex)), 1, SectionSwitch.Style.TABS) {}
            for ((index, tone) in listOf(Multicolor.Tone.GREEN, Multicolor.Tone.BLUE, Multicolor.Tone.TEAL).withIndex()) {
                val icon = (tabs.getChildAt(index) as LinearLayout).getChildAt(0) as ImageView
                val background = if (index == 1) Bits.blend(Multicolor.color(ctx, tone), Ui.c(ctx, R.color.ag_mantle), .20f)
                    else Ui.c(ctx, R.color.ag_mantle)
                assertEquals(Multicolor.icon(ctx, tone, background), (icon.colorFilter as PorterDuffColorFilter).color)
                assertTrue(ColorUtils.calculateContrast((icon.colorFilter as PorterDuffColorFilter).color, background) >= 3.0)
                assertTrue(ColorUtils.calculateContrast(Multicolor.ink(ctx, tone, background), background) >= 4.5)
            }
            for (meaning in BarMeaning.entries) for (style in BarStyle.entries) {
                AppLook.setBarStyle(ctx, style)
                assertEquals(BarFill(Multicolor.color(ctx, Multicolor.bar(meaning))), BarTones.fill(ctx, meaning, .5f))
            }
            assertEquals(BarFill(Multicolor.color(ctx, Multicolor.Tone.GREEN)), BarTones.fill(ctx, BarMeaning.GOAL, 1f))
            val offer = ShopViews.weekendCard(ctx, View(ctx), "Everstone", "Weekend", View(ctx), "Held item") {}
            val heading = all(offer).filterIsInstance<TextView>().single { it.text.toString() == "WEEKEND SPECIAL" }
            val band = Bits.blend(Multicolor.color(ctx, Multicolor.Tone.TEAL), Ui.c(ctx, R.color.ag_card), .22f)
            assertTrue("Offer heading is readable in $theme", ColorUtils.calculateContrast(heading.currentTextColor, band) >= 4.5)
            for (label in listOf("Team", "Box", "Favourites", "Can evolve", "Fainted")) {
                val chip = BoxViews.chip(ctx, label, true) {}
                assertEquals(Multicolor.onFill(Multicolor.color(ctx, Multicolor.place(label))), chip.currentTextColor)
            }
        }
    }

    @Test fun existingAccentsKeepSectionTabsAndBarBehaviour() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            for (accent in Accent.entries.filter { it != Accent.MULTICOLOR }) {
                AppLook.setAccent(ctx, accent)
                for (s in DetailSection.entries) {
                    val mem = DetailOpen(object : DetailOpenStore {
                        override fun get(key: String) = false
                        override fun set(key: String, open: Boolean) = Unit
                    })
                    val card = DetailSections.section(ctx, mem, s, null) { View(ctx) } as LinearLayout
                    val bar = card.getChildAt(0) as LinearLayout
                    assertEquals(ctx.getColor(accent.res), ((bar.getChildAt(0) as ImageView).colorFilter as PorterDuffColorFilter).color)
                    assertEquals(ctx.getColor(accent.res), all(bar.getChildAt(1)).filterIsInstance<TextView>().single().currentTextColor)
                }
                val legacy = listOf(R.color.ag_blue, R.color.ag_green, R.color.ag_peach, R.color.ag_mauve, R.color.ag_teal)
                BottomNav.Tab.entries.forEachIndexed { i, tab -> assertEquals(ctx.getColor(legacy[i]), BottomNav.tone(ctx, tab)) }
                for (style in BarStyle.entries) for (meaning in BarMeaning.entries) {
                    AppLook.setBarStyle(ctx, style)
                    assertEquals(BarTones.resolve(meaning, style, BarTones.palette(ctx), .5f), BarTones.fill(ctx, meaning, .5f))
                }
            }
        }
    }
}
