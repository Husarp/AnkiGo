package com.husarp.ankigo.study
import org.junit.Assert.*
import org.junit.Test
class StudyRulesTest {
    @Test fun oldSchedulersPreserveExactGameButtons() {
        assertEquals(3, StudyRules.gameButton(2, 2))
        assertEquals(3, StudyRules.gameButton(3, 2))
        assertEquals(4, StudyRules.gameButton(3, 3))
        assertEquals(2, StudyRules.gameButton(4, 2))
    }
    @Test fun actionsRequireVerifiedProviderRows() {
        assertFalse(StudyRules.buried(mapOf("queue" to "2")))
        assertFalse(StudyRules.suspended(null))
        assertTrue(StudyRules.buried(mapOf("queue" to "-2")))
        assertTrue(StudyRules.buried(mapOf("queue" to "-3")))
        assertTrue(StudyRules.suspended(mapOf("queue" to "-1")))
    }
    @Test fun spriteFallbackWorksWithoutDownloads() {
        assertEquals(StudyRules.SpriteSource.PLACEHOLDER, StudyRules.spriteSource(false, false))
        assertEquals(StudyRules.SpriteSource.STATIC, StudyRules.spriteSource(false, true))
        assertEquals(StudyRules.SpriteSource.ANIMATED, StudyRules.spriteSource(true, true))
    }
}
