package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.deck.NoteEditActivity
import com.husarp.ankigo.deck.NoteForEdit
import com.husarp.ankigo.study.TtsRules
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 161: the note editor's Preview opens, both sides, for every kind of note (basic, reversed, cloze, type-in, media, empty
 * fields, no template), the way the app opens it (Edit -> Preview -> Answer -> Question -> Edit). Made-up notes only.
 */
class R13NotePreviewShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class Kind(val name: String, val type: String, val fields: List<String>, val values: List<String>, val templates: List<TtsRules.Template>, val cloze: Boolean = false, val ord: Int = 0, val css: String = "")

    private val basic = listOf(TtsRules.Template(0, "{{Front}}", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}"))
    private val kinds = listOf(
        Kind("basic", "Basic", listOf("Front", "Back"), listOf("Capital of Peru?", "Lima"), basic, css = ".card { font-family: arial; font-size: 20px; }"),
        Kind("reversed", "Basic (and reversed card)", listOf("Front", "Back"), listOf("Capital of Peru?", "Lima"),
            basic + TtsRules.Template(1, "{{Back}}", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Front}}"), ord = 1),
        Kind("cloze", "Cloze", listOf("Text", "Back Extra"), listOf("{{c1::Lima}} is the capital of {{c2::Peru::country}}.", ""),
            listOf(TtsRules.Template(0, "{{cloze:Text}}", "{{cloze:Text}}<br>\n{{Back Extra}}")), cloze = true, ord = 1),
        Kind("cloze-unclosed", "Cloze", listOf("Text", "Back Extra"), listOf("{{c1::Lima is the capital", "{{c1::"),
            listOf(TtsRules.Template(0, "{{cloze:Text}}", "{{cloze:Text}}<br>\n{{Back Extra}}")), cloze = true),
        Kind("type-in", "Basic (type in the answer)", listOf("Front", "Back"), listOf("Capital of Peru?", "Lima"),
            listOf(TtsRules.Template(0, "{{Front}}\n\n{{type:Back}}", "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}"))),
        Kind("type-in-cloze", "Cloze (type in)", listOf("Text"), listOf("{{c1::Lima}} is in Peru"),
            listOf(TtsRules.Template(0, "{{cloze:Text}}\n{{type:cloze:Text}}", "{{cloze:Text}}\n{{type:cloze:Text}}")), cloze = true),
        Kind("media", "Basic", listOf("Front", "Back"), listOf("<img src=\"peru map.png\"> [sound:lima.mp3]", "Lima [sound:lima-2.mp3] {{tts en_US:Front}}"),
            listOf(TtsRules.Template(0, "{{Front}} {{tts en_US:Front}}", "{{FrontSide}}<hr id=answer>{{Back}} [anki:play:a:0]"))),
        Kind("empty-fields", "Basic (optional reversed card)", listOf("Front", "Back", "Add Reverse"), listOf("", "", ""),
            listOf(TtsRules.Template(0, "{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}"), TtsRules.Template(1, "{{#Add Reverse}}{{Back}}{{/Add Reverse}}", "{{FrontSide}}<hr id=answer>{{Front}}")), ord = 1),
        Kind("no-template", "Basic", listOf("Front", "Back"), listOf("Q", "A"), emptyList()),
        Kind("more-fields-than-boxes", "Basic", listOf("Front", "Back", "Source"), listOf("Q", "A", "S"), basic),
    )

    @Test fun thePreviewOpensForEveryKindOfNote() {
        var opened = 0
        paparazzi.shootWith("r13-note-preview", listOf(Size.PHONE)) { ctx ->
            var last: View? = null
            for (k in kinds) {
                val act = attach(NoteEditActivity(), ShotCtx(ctx))
                val root = act.buildContent()
                setField(act, "ord", k.ord)
                act.bind(NoteEditActivity.Loaded(NoteForEdit(7, k.type, k.fields, k.values), "Geography", 2, k.css, k.cloze, k.templates))
                val pages = ArrayList<String>()
                act.previewSink = { pages += it }
                call(act, "showPreview", true)              // Edit -> Preview (the question side)
                setField(act, "answerSide", true); call(act, "drawPreview")   // the Answer chip
                setField(act, "answerSide", false); call(act, "drawPreview")  // back to the Question
                call(act, "showPreview", false)             // back to Edit
                assertTrue(k.name, pages.size == 3 && pages.all { it.contains("<div id=\"qa\">") })
                opened++
                last = root
            }
            last!!
        }
        assertTrue(opened >= kinds.size)
    }

    /**
     * Without the test sink the real page path runs: this renderer has no working WebView (its settings call throws, like a phone
     * whose WebView can't be made), so it proves a failing page is caught and the preview says so instead of closing the app.
     */
    @Test fun aPageThatCannotBeShownNeverClosesTheApp() {
        paparazzi.shootWith("r13-note-preview-failed", listOf(Size.PHONE)) { ctx ->
            val act = attach(NoteEditActivity(), ShotCtx(ctx))
            val root = act.buildContent()
            act.bind(NoteEditActivity.Loaded(NoteForEdit(7, "Basic", listOf("Front", "Back"), listOf("Q", "A")), "Geography", 2, "", false, basic))
            call(act, "showPreview", true)
            val card = getField(act, "previewCard") as android.view.ViewGroup
            assertTrue((card.getChildAt(0) as android.widget.TextView).text.toString() == NoteEditActivity.PREVIEW_FAILED)
            assertTrue(getField(act, "web") == null)
            setField(act, "answerSide", true); call(act, "drawPreview")   // the Answer chip tries again, no crash either
            assertTrue(card.childCount == 1)
            root
        }
    }
}
