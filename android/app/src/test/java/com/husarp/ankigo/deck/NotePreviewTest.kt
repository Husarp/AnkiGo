package com.husarp.ankigo.deck

import com.husarp.ankigo.study.TtsRules.Template
import org.junit.Assert.*
import org.junit.Test

/** The editor's Preview: both sides from the template and the typed texts (made-up note types). */
class NotePreviewTest {
    private val basic = listOf(Template(0, "{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}"))
    private val reversed = basic + Template(1, "{{Back}}", "{{FrontSide}}<hr id=answer>{{Front}}")
    private val names = listOf("Front", "Back")

    @Test fun basicCardRendersTheTypedTexts() {
        val s = NotePreview.build(basic, false, names, listOf("Capital of Mongolia?", "Ulaanbaatar"), 0)!!
        assertEquals("Capital of Mongolia?", s.question)
        assertEquals("Capital of Mongolia?<hr id=answer>Ulaanbaatar", s.answer)
    }

    @Test fun theSecondCardOfAReversedNoteTurnsItAround() {
        val s = NotePreview.build(reversed, false, names, listOf("Q", "A"), 1)!!
        assertEquals("A", s.question); assertEquals("A<hr id=answer>Q", s.answer)
    }

    @Test fun conditionalsFollowTheFields() {
        val t = listOf(Template(0, "{{Front}}{{#Extra}}<small>{{Extra}}</small>{{/Extra}}", "{{Back}}"))
        val three = listOf("Front", "Back", "Extra")
        assertEquals("Q<small>hint</small>", NotePreview.build(t, false, three, listOf("Q", "A", "hint"), 0)!!.question)
        assertEquals("Q", NotePreview.build(t, false, three, listOf("Q", "A", ""), 0)!!.question)
    }

    @Test fun speechTagsBecomeTheirText() {
        val t = listOf(Template(0, "{{tts en_US:Front}}", "{{Back}}"))
        assertEquals("Hello", NotePreview.build(t, false, names, listOf("Hello", "x"), 0)!!.question)
    }

    @Test fun clozeShowsTheBlankOnTheQuestionAndTheTextOnTheAnswer() {
        val t = listOf(Template(0, "{{cloze:Text}}", "{{cloze:Text}}"))
        val s = NotePreview.build(t, true, listOf("Text"), listOf("The capital is {{c1::Ulaanbaatar::city}}."), 0)!!
        assertEquals("The capital is [city].", s.question)
        assertEquals("The capital is Ulaanbaatar.", s.answer)
    }

    @Test fun anEmptyBoxRendersEmpty() {
        val s = NotePreview.build(basic, false, names, listOf("", ""), 0)!!
        assertEquals("", s.question)
    }

    @Test fun noTemplateForTheCardGivesNull() {
        assertNull(NotePreview.build(basic, false, names, listOf("a", "b"), 3))
        assertNull(NotePreview.build(emptyList(), false, names, listOf("a", "b"), 0))
    }

    @Test fun fieldsOnlyListsEveryFieldUnderItsName() {
        val h = NotePreview.fieldsOnly(listOf("Front", "Back <x>"), listOf("one", "<b>two</b>"))
        assertTrue("FRONT".equals("FRONT") && h.indexOf("Front") < h.indexOf("one") && h.indexOf("one") < h.indexOf("Back"))
        assertTrue("Back &lt;x&gt;" in h); assertTrue("<b>two</b>" in h); assertTrue("<hr>" in h)
    }
}
