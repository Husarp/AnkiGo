package com.husarp.ankigo.ui

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R31EHoldCallersTest {
    @Test fun everyHoldButtonUsesTheSharedAnimatedBinding() {
        val files = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        val callers = files.filter { it.name != "HoldToConfirm.kt" && it.readText().contains("HoldToConfirm.bind(") }
        assertEquals(setOf("ItemCard.kt", "ShopActivity.kt", "EvolutionRows.kt", "Pickers.kt"), callers.map { it.name }.toSet())
        val labels = files.filter { it.name != "HoldToConfirm.kt" && it.readText().contains("\"Hold to ") }
        assertEquals(callers.toSet(), labels.toSet())
        for (file in callers) assertEquals(file.name, 1, Regex("HoldToConfirm\\.bind\\(").findAll(file.readText()).count())
        val component = files.single { it.name == "HoldToConfirm.kt" }.readText()
        assertTrue(component.contains("view.background = fill"))
        assertTrue(component.contains("fill.level ="))
        assertFalse(component.contains("ValueAnimator"))
    }
}
