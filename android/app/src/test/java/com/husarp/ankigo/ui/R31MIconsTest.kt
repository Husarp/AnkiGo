package com.husarp.ankigo.ui

import com.husarp.ankigo.R
import com.husarp.ankigo.help.GameGuide
import com.husarp.ankigo.help.HelpViews
import org.junit.Assert.*
import org.junit.Test

class R31MIconsTest {
    @Test fun selectedAchievementAndBadgeIcons() {
        assertEquals(R.drawable.ic_calendar, AchStyle.icon("days"))
        assertEquals(R.drawable.ic_bolt, AchStyle.icon("xp"))
        assertEquals(R.drawable.ic_ach_sparkle, AchStyle.icon("badge-ultra"))
        assertEquals(R.drawable.ic_item_tag, AchStyle.icon("badge-item"))
        assertEquals(R.drawable.ic_stats, AchStyle.icon("cards"))
        assertEquals(R.drawable.ic_game, AchStyle.icon("combo"))
        assertEquals(R.drawable.ic_palette, AchStyle.icon("types"))
    }
    @Test fun eachStatPopupUsesItsOwnExistingIcon() {
        val icons = mapOf("atk" to R.drawable.ic_stat_swords, "def" to R.drawable.ic_stat_shield,
            "spa" to R.drawable.ic_stat_sparkles, "spd" to R.drawable.ic_stat_shield_plus,
            "spe" to R.drawable.ic_stat_gauge, "accuracy" to R.drawable.ic_ach_target, "evasion" to R.drawable.ic_ach_eye)
        icons.forEach { (stat, icon) -> assertEquals(stat, icon, ConditionIcons.of("stat.$stat")) }
        assertNull(ConditionIcons.of("unrecognized"))
    }
    @Test fun helpTypesChangesAndLevelsStays() {
        assertEquals(R.drawable.ic_palette, HelpViews.chapterIcon(GameGuide.TYPES))
        assertEquals(R.drawable.ic_evolve, HelpViews.chapterIcon(GameGuide.LEVELS))
    }
    @Test fun settingsPicksAndUnauditedFallback() {
        assertEquals(R.drawable.ic_game, Form.sectionIcon("Play"))
        assertEquals(R.drawable.ic_sound, Form.sectionIcon("Sound & data"))
        assertEquals(R.drawable.ic_help, Form.sectionIcon("Help & app"))
        assertEquals(R.drawable.ic_bug, Form.sectionIcon("Advanced"))
        assertEquals(R.drawable.ic_swords, Form.sectionIcon("When you beat a wild Pokémon"))
        assertEquals(R.drawable.ic_layout, Form.sectionIcon("Answer controls"))
        assertEquals(R.drawable.ic_layout, Form.sectionIcon("Info boxes & bars"))
        assertEquals(R.drawable.ic_layout, Form.sectionIcon("Buddy layout & movement"))
        assertEquals(R.drawable.ic_star, Form.sectionIcon("Other heading"))
    }
}
