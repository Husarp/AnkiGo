package com.husarp.ankigo.help

import org.junit.Assert.assertTrue
import org.junit.Test

class R30QHelpTest {
    @Test fun guideExplainsKnowledgeUnlockAndBattleTags() {
        val text = GameGuide.chapters().flatMap { it.points }.joinToString(" ")
        assertTrue(text.contains("defeat or catch a species"))
        assertTrue(text.contains("any form counts"))
        assertTrue(text.contains("Wild Matchups appear after your first attack"))
        assertTrue(text.contains("your Main earns"))
        assertTrue(text.contains("Bond Level 3"))
        assertTrue(text.contains("species and its forms"))
        assertTrue(text.contains("Each type starts as ???"))
        assertTrue(text.contains("A catch or defeat alone reveals no matchups"))
    }
}
