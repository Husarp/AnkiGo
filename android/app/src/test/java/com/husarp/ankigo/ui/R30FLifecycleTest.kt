package com.husarp.ankigo.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class R30FLifecycleTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private fun activities(): List<Element> {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = f.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("activity")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    @Test fun realScreensStayInRecentsAndKeepTheirWindowsOnSizeChanges() {
        val screens = activities().filter { it.getAttributeNS(ns, "theme") == "@style/GameTheme" && it.getAttributeNS(ns, "name") in setOf(
            ".ui.GameActivity", ".ui.TeamActivity", ".ui.ShopActivity", ".ui.SettingsActivity", ".ui.StatsActivity", ".help.HelpActivity", ".ui.CompareActivity", ".StudyActivity") }
        assertEquals(8, screens.size)
        for (screen in screens) {
            assertNotEquals("true", screen.getAttributeNS(ns, "excludeFromRecents"))
            assertNotEquals("true", screen.getAttributeNS(ns, "noHistory"))
            assertNotEquals("true", screen.getAttributeNS(ns, "autoRemoveFromRecents"))
            assertTrue(screen.getAttributeNS(ns, "configChanges").contains("screenLayout"))
            assertTrue(screen.getAttributeNS(ns, "configChanges").contains("smallestScreenSize"))
        }
    }
    @Test fun excludedHelpersCannotSeedTheNormalAppTask() {
        val helpers = activities().filter { it.getAttributeNS(ns, "excludeFromRecents") == "true" }
        assertEquals(4, helpers.size)
        for (helper in helpers) {
            assertTrue(helper.hasAttributeNS(ns, "taskAffinity"))
            assertEquals("", helper.getAttributeNS(ns, "taskAffinity"))
        }
    }
}
