package com.husarp.ankigo.deck

import org.junit.Assert.*
import org.junit.Test

/** What the note editor keeps when its activity is recreated. */
class NoteDraftTest {
    private val loaded = listOf("front", "back")

    @Test fun nothingToKeepWhenNothingWasTyped() {
        assertNull(NoteDraft.toSave(loaded, loaded))
        assertNull(NoteDraft.toSave(loaded, listOf("front")))
    }

    @Test fun aHugeDraftIsNotKept() {
        val big = "x".repeat(NoteDraft.MAX_CHARS)
        assertNull(NoteDraft.toSave(listOf("a", "b"), listOf(big, "b")))
        assertNotNull(NoteDraft.toSave(listOf("a", "b"), listOf("a2", "b")))
    }

    @Test fun theDraftAndTheOriginalTextsComeBack() {
        val saved = NoteDraft.toSave(loaded, listOf("front", "back, edited"))!!
        val shown = NoteDraft.resolve(saved, loaded)
        assertTrue(shown.restored)
        assertEquals(listOf("front", "back, edited"), shown.texts)
        assertEquals(loaded, shown.before)
    }

    @Test fun aNoteChangedInAnkiDroidMeanwhileKeepsTheOldBeforeSoSavingIsRefused() {
        val saved = NoteDraft.Saved(loaded, listOf("front", "back, edited"))
        val now = listOf("front", "changed elsewhere")
        val shown = NoteDraft.resolve(saved, now)
        assertTrue(shown.restored)
        assertEquals(listOf("front", "back, edited"), shown.texts)
        assertEquals(loaded, shown.before)       // != the note now: DeckOps.editNote says "changed meanwhile"
        assertNotEquals(now, shown.before)
    }

    @Test fun aSaveThatWentThroughBeforeTheRecreationIsNotRestored() {
        val saved = NoteDraft.Saved(loaded, listOf("front", "back, edited"))
        val shown = NoteDraft.resolve(saved, listOf("front", "back, edited"))
        assertFalse(shown.restored)
        assertEquals(listOf("front", "back, edited"), shown.before)
        assertEquals(shown.before, shown.texts)
    }

    @Test fun noDraftOrADraftOfAnotherShapeShowsTheNoteAsLoaded() {
        for (saved in listOf(null, NoteDraft.Saved(listOf("a"), listOf("b")), NoteDraft.Saved(loaded, listOf("x")), NoteDraft.Saved(listOf("a"), loaded))) {
            val shown = NoteDraft.resolve(saved, loaded)
            assertFalse(shown.restored)
            assertEquals(loaded, shown.texts); assertEquals(loaded, shown.before)
        }
    }

    @Test fun unchangedDraftShowsTheLatestNote() {
        // before == texts can't be saved (toSave refuses it), but a hand-made one must not freeze an outdated note
        val shown = NoteDraft.resolve(NoteDraft.Saved(loaded, loaded), listOf("front", "newer"))
        assertFalse(shown.restored)
        assertEquals(listOf("front", "newer"), shown.texts)
    }
}
