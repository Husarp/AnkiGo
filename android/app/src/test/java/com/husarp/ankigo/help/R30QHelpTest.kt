package com.husarp.ankigo.help

import org.junit.Assert.assertTrue
import org.junit.Test

class R30QHelpTest {
    @Test fun guideExplainsKnowledgeUnlockAndBattleTags() {
        val text = GameGuide.chapters().flatMap { it.points }.joinToString(" ")
        assertTrue(text.contains("Until you defeat or catch a species"))
        assertTrue(text.contains("any form counts"))
        assertTrue(text.contains("Battle effectiveness tags still"))
        assertTrue(text.contains("Bond is only for your own Pokémon"))
        assertTrue(text.contains("Each attacking type starts as ???"))
        assertTrue(text.contains("Catching or defeating reveals no extra matchups"))
    }
}
