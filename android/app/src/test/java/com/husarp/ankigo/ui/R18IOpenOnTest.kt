package com.husarp.ankigo.ui

import com.husarp.ankigo.DiagnosticsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R18IOpenOnTest {
    @Test fun defaultsAndUnknownValuesAreStudy() {
        assertEquals(OpenOn.STUDY, OpenOn.DEFAULT)
        assertEquals(OpenOn.STUDY, OpenOn.of(null))
        assertEquals(OpenOn.STUDY, OpenOn.of("nonsense"))
        assertEquals(OpenOn.LAST, OpenOn.of("last"))
        assertEquals(OpenOn.BATTLE, OpenOn.of("battle"))
    }

    @Test fun studyAndBattleIgnoreTheLastTab() {
        assertEquals(Dest.STUDY, OpenOnPrefs.dest(OpenOn.STUDY, "SHOP"))
        assertEquals(Dest.BATTLE, OpenOnPrefs.dest(OpenOn.BATTLE, "SHOP"))
    }

    @Test fun lastTabFollowsTheSavedTabAndFallsBackToStudy() {
        assertEquals(Dest.STUDY, OpenOnPrefs.dest(OpenOn.LAST, null))
        assertEquals(Dest.STUDY, OpenOnPrefs.dest(OpenOn.LAST, "NOPE"))
        assertEquals(Dest.SHOP, OpenOnPrefs.dest(OpenOn.LAST, BottomNav.Tab.SHOP.name))
        assertEquals(Dest.TEAM, OpenOnPrefs.dest(OpenOn.LAST, BottomNav.Tab.POKEMON.name))
        assertEquals(Dest.SETTINGS, OpenOnPrefs.dest(OpenOn.LAST, BottomNav.Tab.SETTINGS.name))
    }

    @Test fun lastTabRemembersTheBattleViewAndOldTabNamesStillWork() { // R18 RV-5
        assertEquals(Dest.BATTLE, OpenOnPrefs.dest(OpenOn.LAST, "BATTLE"))
        assertEquals(Dest.STUDY, OpenOnPrefs.dest(OpenOn.LAST, "STUDY"))
        assertEquals(Dest.TEAM, OpenOnPrefs.dest(OpenOn.LAST, "POKEMON"))   // an old saved tab name
        assertEquals(Dest.SHOP, OpenOnPrefs.dest(OpenOn.LAST, "SHOP"))
        assertEquals(Dest.STUDY, OpenOnPrefs.dest(OpenOn.LAST, "junk"))
    }

    @Test fun theBuddyWidgetTapGoesThroughItsOwnTrampolineAndRequestCode() { // R18 RV-1
        assertEquals("com.husarp.ankigo.WIDGET_OPEN", WidgetTapActivity.ACTION_OPEN)
        val codes = listOf(com.husarp.ankigo.Widgets.REQ_STUDY_OR_BATTLE, com.husarp.ankigo.Widgets.REQ_OPEN_APP, com.husarp.ankigo.Widgets.REQ_OPEN_STUDY)
        assertEquals("one request code each (the same component + code would be one PendingIntent)", 3, codes.toSet().size)
    }

    @Test fun widgetIntentsPointWhereTheRuleSays() {
        assertEquals(GameActivity::class.java, OpenOnPrefs.target(OpenOn.STUDY, null).screen)
        assertEquals(HomeView.STUDY.key, OpenOnPrefs.target(OpenOn.STUDY, null).extra?.second)
        assertEquals(HomeView.BATTLE.key, OpenOnPrefs.target(OpenOn.BATTLE, null).extra?.second)
        assertEquals(StatsActivity::class.java, OpenOnPrefs.target(OpenOn.LAST, BottomNav.Tab.STATS.name).screen)
        assertEquals(ShopActivity::class.java, OpenOnPrefs.target(OpenOn.LAST, BottomNav.Tab.SHOP.name).screen)
    }

    @Test fun onlyAPlainLauncherStartFollowsTheSetting() {
        assertTrue(OpenOnPrefs.launcherStartUsesSetting(true, null, false))
        assertFalse(OpenOnPrefs.launcherStartUsesSetting(false, null, false))
        assertFalse(OpenOnPrefs.launcherStartUsesSetting(true, "battle", false))
        assertFalse(OpenOnPrefs.launcherStartUsesSetting(true, null, true))
    }

    @Test fun summaryNamesTheChoice() {
        assertEquals("Opens on Last tab", OpenOnPrefs.summary(OpenOn.LAST))
    }

    @Test fun widgetsSectionIsInTheHomeListAndSearchable() {
        assertTrue(SettingsSection.WIDGETS.opens)
        assertEquals(1, SettingsSection.GROUPS.count { SettingsSection.WIDGETS in it })
        val e = SettingsCatalog.byId("openOn")!!
        assertEquals(SettingsSection.WIDGETS, SettingsSearch.sectionOf(e))
        val moved = SettingsCatalog.byId("saveNotFirst")!!
        assertEquals(SettingsSection.STUDY, SettingsSearch.sectionOf(moved))
        assertEquals(1, SettingsCatalog.all.count { it.title == "Save answers for cards that aren't first (test)" })
    }

    @Test fun diagnosticsLogBoxIsHalfAsTall() {
        assertEquals(869f * 0.35f, DiagnosticsActivity.logHeightDp(869f), 0.01f) // ~304 dp (was 608)
        assertEquals(200f, DiagnosticsActivity.logHeightDp(400f), 0.01f)         // the 200 dp floor (35 % would be 140)
        assertEquals(180f, DiagnosticsActivity.logHeightDp(300f), 0.01f)         // never taller than the window minus the bars
    }
}
