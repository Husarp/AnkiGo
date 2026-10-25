package com.husarp.ankigo.ui

import android.content.Intent
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** PLAN 286: task policy and entry-point wiring; actual Recents behaviour needs a device. */
class R19VWidgetTaskTest {
    private val main = File("src/main/java/com/husarp/ankigo")
    private val widgets get() = File(main, "Widgets.kt").readText()

    @Test fun realDestinationsUseTheNormalTaskAndReuseScreens() {
        val flags = WidgetTapActivity.APP_TASK_FLAGS
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(0, flags and (Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    @Test fun onlyTheTemporaryTaskIsExcluded() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = doc.getElementsByTagName("activity")
        val ns = "http://schemas.android.com/apk/res/android"
        val byName = (0 until activities.length).map { activities.item(it) as Element }
            .associateBy { it.getAttributeNS(ns, "name") }
        val tap = byName.getValue(".ui.WidgetTapActivity")
        assertTrue(tap.hasAttributeNS(ns, "taskAffinity"))
        assertEquals("", tap.getAttributeNS(ns, "taskAffinity"))
        assertEquals("true", tap.getAttributeNS(ns, "excludeFromRecents"))
        assertEquals("true", tap.getAttributeNS(ns, "noHistory"))
        val screens = Dest.entries.map { Nav.target(it).screen.simpleName }.toSet() + "StudyActivity"
        screens.forEach { name ->
            val screen = byName.values.single { it.getAttributeNS(ns, "name").substringAfterLast('.') == name }
            assertNotEquals(name, "true", screen.getAttributeNS(ns, "excludeFromRecents"))
            assertNotEquals(name, "true", screen.getAttributeNS(ns, "noHistory"))
            assertFalse(name, screen.hasAttributeNS(ns, "taskAffinity"))
        }
    }

    @Test fun bothTapTimeBranchesLeaveTheTemporaryTask() {
        val tap = File(main, "ui/WidgetTapActivity.kt").readText()
        assertTrue(tap.contains("openScreen(OpenOnPrefs.intent(this))"))
        assertTrue(tap.contains("openScreen(if (study) Nav.intent(this, Dest.STUDY) else OpenOnPrefs.intent(this))"))
        assertTrue(tap.contains("target.addFlags(APP_TASK_FLAGS)"))
        assertTrue(tap.contains("startActivities(arrayOf(Nav.intent(this, Dest.STUDY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), target))"))
        // Buddy and motivation enter an isolated task via activity PendingIntents, including on Android 12+.
        assertTrue(widgets.contains("setAction(com.husarp.ankigo.ui.WidgetTapActivity.ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)"))
        assertTrue(widgets.contains("WidgetTapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent"))
        assertFalse(widgets.contains("PendingIntent.getService"))
    }

    @Test fun studyNowAndDeckReviewKeepTheirOwnTargets() {
        assertTrue(widgets.contains("Nav.intent(ctx, com.husarp.ankigo.ui.Dest.STUDY).addFlags(com.husarp.ankigo.ui.WidgetTapActivity.APP_TASK_FLAGS)"))
        assertTrue(widgets.contains("Intent(ctx, StudyActivity::class.java).putExtra(\"deckId\", deckId).putExtra(\"deckName\", deckName ?: \"?\")"))
        assertTrue(widgets.contains("PendingIntent.getActivities(ctx, id * 10 + 8"))
        assertTrue(widgets.contains(".addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Intent(ctx, StudyActivity::class.java)"))
        assertTrue(widgets.contains("addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)"))
        val render = File(main, "ui/WidgetRender.kt").readText()
        assertTrue(render.contains("Widgets.openStudyOrBattle(ctx) else Widgets.openApp(ctx)"))
        assertTrue(render.contains("Widgets.openStudy(ctx)"))
    }
}
