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
    // PLAN 175: the answer side shows again after a resume only for the same card in the same state
    private val row = mapOf("_id" to "7", "note_id" to "70", "reps" to "3", "queue" to "2", "due" to "100")
    private val keep = StudyRules.AnswerKeep(StudyLoop.Key(70, 0), row, "typed", -1)
    @Test fun theSameUnansweredCardKeepsItsAnswerSide() {
        assertTrue(keep.same(StudyLoop.Key(70, 0), row.toMap()))
    }
    @Test fun aCardAnsweredInAnkiDroidMeanwhileShowsItsQuestion() {
        assertFalse(keep.same(StudyLoop.Key(70, 0), row + ("reps" to "4")))   // answered (Again brings it back on top)
        assertFalse(keep.same(StudyLoop.Key(70, 0), row + ("due" to "200")))  // rescheduled
    }
    @Test fun anotherCardOrNoRowShowsTheQuestion() {
        assertFalse(keep.same(StudyLoop.Key(70, 1), row))
        assertFalse(keep.same(StudyLoop.Key(71, 0), row))
        assertFalse(StudyRules.AnswerKeep(StudyLoop.Key(70, 0), emptyMap(), "", -1).same(StudyLoop.Key(70, 0), emptyMap()))
    }
}
