package com.husarp.ankigo.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    private val all = SettingsCatalog.all

    @Test fun idsAreUnique() {
        assertEquals(emptyList<String>(), all.groupBy { it.id }.filter { it.value.size > 1 }.keys.toList())
    }

    @Test fun everyEntryHasTitleAndShortExplanationWithoutFinalPeriod() {
        for (e in all) {
            assertTrue("${e.id} title", e.title.isNotBlank())
            assertTrue("${e.id} explain blank", e.explain.isNotBlank())
            assertTrue("${e.id} explain too long (${e.explain.length})", e.explain.length <= SettingsCatalog.MAX_EXPLAIN)
            assertTrue("${e.id} explain ends with a period", !e.explain.trimEnd().endsWith("."))
        }
    }

    @Test fun everySectionHasEntries() {
        for (sec in CatalogSection.entries) assertTrue("${sec.title} is empty", SettingsCatalog.bySection(sec).isNotEmpty())
    }

    @Test fun gameSectionHasThePlan28Groups() {
        val subs = SettingsCatalog.bySection(CatalogSection.GAME).map { it.sub }.toSet()
        for (g in listOf("Battle", "Difficulty", "Pokémon")) assertTrue(g, g in subs)
    }

    @Test fun reminderIdsMatchReminderPrefs() {
        val want = ("dueOn dueMin dueMode dueTime dueEveryHours minGapHours dailyCap decks goalOn goalKind goalCount " +
            "streakOn streakTime dailyGoalOn dailyGoalTime shopOn quietOn quietFrom quietTo").split(" ")
        val have = SettingsCatalog.bySection(CatalogSection.REMINDERS).map { it.id }
        assertEquals(want.sorted(), have.sorted())
    }

    @Test fun ankimonSectionIsRenamed() {
        assertEquals("Ankimon PC sync", SettingsSection.ANKIMON.title)
    }

    /** Every literal row title in the Settings sources must be in the catalog, so a new setting can't be forgotten. */
    @Test fun everySettingsRowTitleIsInTheCatalog() {
        val dir = File("src/main/java/com/husarp/ankigo/ui")
        val re = Regex("""Form\.(?:row|switchRow|stepperRow|stackRow)\(\s*(?:this|ctx),\s*"((?:[^"\\]|\\.)*)"""")
        val skip = setOf("", "The game isn't loaded")
        val missing = mutableListOf<String>()
        var seen = 0
        for (name in listOf("SettingsActivity.kt", "SettingsSound.kt")) {
            val f = File(dir, name)
            assertTrue("cannot find ${f.absolutePath}", f.exists())
            for (m in re.findAll(f.readText())) {
                val t = m.groupValues[1].replace("\\\"", "\"")
                if (t in skip) continue
                seen++
                if (SettingsCatalog.byTitle(t) == null) missing += "$name: $t"
            }
        }
        assertTrue("regex found too few rows ($seen)", seen > 25)
        assertEquals("Settings rows missing from SettingsCatalog", emptyList<String>(), missing)
    }
}
