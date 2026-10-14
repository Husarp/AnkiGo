package com.husarp.ankigo.study

import com.husarp.ankigo.study.TtsRules.Item
import org.junit.Assert.*
import org.junit.Test

class TtsRulesTest {
    private fun card(q: String, a: String = "{{FrontSide}}<hr id=answer>", fields: Map<String, String> = emptyMap(), ord: Int = 0) =
        TtsRules.card(TtsRules.Template(0, q, a), fields, ord)
    private fun texts(items: List<Item>) = items.map { it.text }

    @Test fun simpleFilterAndLanguageTag() {
        val c = card("{{tts en_US:Front}}", fields = mapOf("Front" to "Hello world"))
        assertEquals(listOf(Item("Hello world", "en-US", 1f)), c.question)
    }

    @Test fun optionsVoicesAreIgnoredSpeedIsKept() {
        val c = card("{{tts ja_JP voices=Apple_Kyoko,Google speed=1.2:Front}}", fields = mapOf("Front" to "猫"))
        assertEquals(Item("猫", "ja-JP", 1.2f), c.question.single())
        assertEquals(1f, card("{{tts en_US speed=fast:Front}}", fields = mapOf("Front" to "x")).question.single().rate)
        assertEquals(4f, card("{{tts en_US speed=9:Front}}", fields = mapOf("Front" to "x")).question.single().rate)
    }

    @Test fun answerSideStartsWithTheFrontSideItems() {
        val c = card("{{tts en_US:Front}}", "{{FrontSide}}<hr id=answer>{{tts fr_FR:Back}}", mapOf("Front" to "cat", "Back" to "chat"))
        assertEquals(listOf("cat"), texts(c.question))
        assertEquals(listOf("cat", "chat"), texts(c.answer))
        assertEquals(listOf("en-US", "fr-FR"), c.answer.map { it.lang })
        assertEquals(listOf("en-US", "fr-FR"), c.languages)
    }

    @Test fun answerSideWithoutFrontSideHasOnlyItsOwn() {
        val c = card("{{tts en_US:Front}}", "<hr id=answer>{{tts en_US:Back}}", mapOf("Front" to "a", "Back" to "b"))
        assertEquals(listOf("b"), texts(c.answer))
    }

    @Test fun htmlAndEntitiesAreCleaned() {
        val c = card("{{tts en_US:Front}}", fields = mapOf("Front" to "<div>Tom &amp; Jerry</div><b>are</b>&nbsp;here<br>now &#233; &#x41; &lt;ok&gt;"))
        assertEquals("Tom & Jerry are here now é A <ok>", c.question.single().text)
    }

    @Test fun styleScriptAndCommentsAreDropped() {
        assertEquals("a b", TtsRules.speakable("a<style>p{color:red}</style><!-- hi --><script>x()</script> b"))
    }

    @Test fun soundTagsAreNotSpoken() {
        val c = card("{{tts en_US:Front}}", fields = mapOf("Front" to "hello [sound:a.mp3] there"))
        assertEquals("hello there", c.question.single().text)
    }

    @Test fun clozeQuestionHidesTheActiveOneAndAnswerShowsIt() {
        val f = mapOf("Text" to "The capital of {{c1::France::country}} is {{c2::Paris}}.")
        val c = card("{{tts en_US:cloze:Text}}", "{{tts en_US:cloze:Text}}", f, ord = 0)
        assertEquals("The capital of [country] is Paris.", c.question.single().text)
        assertEquals("The capital of France is Paris.", c.answer.last().text)
        val c2 = card("{{tts en_US:cloze:Text}}", "{{tts en_US:cloze:Text}}", f, ord = 1)
        assertEquals("The capital of France is blank.", c2.question.single().text)
    }

    @Test fun clozeBlankIsSpokenAsBlankOrTheCustomWord() {
        val f = mapOf("Text" to "{{c1::Paris}} is big")
        assertEquals("blank is big", card("{{tts en_US:cloze:Text}}", fields = f).question.single().text)
        assertEquals("gap is big", card("{{tts en_US cloze_blank=gap:cloze:Text}}", fields = f).question.single().text)
    }

    @Test fun clozeOnlyReadsTheActiveClozes() {
        val f = mapOf("Text" to "{{c1::one}} {{c2::two}} {{c1::three::hint}}")
        val c = card("{{tts en_US:cloze-only:Text}}", "{{tts en_US:cloze-only:Text}}", f, ord = 0)
        assertEquals("blank, [hint]", c.question.single().text)
        assertEquals("one, three", c.answer.last().text)
    }

    @Test fun nestedClozeAndNoActiveCloze() {
        val f = mapOf("Text" to "{{c1::a {{c2::b}} c}}")
        assertEquals("a b c", card("{{tts en_US:cloze:Text}}", "{{tts en_US:cloze:Text}}", f, 0).answer.last().text)
        assertEquals("a blank c", card("{{tts en_US:cloze:Text}}", "x", f, 1).question.single().text)
        assertTrue(card("{{tts en_US:cloze:Text}}", "x", f, 4).question.single().blank)
    }

    @Test fun textFilterAndFurigana() {
        val f = mapOf("Back" to "<b>bold</b> &amp; plain", "Jp" to "漢字[かんじ] 日本[にほん]")
        assertEquals("bold & plain", card("{{tts en_US:text:Back}}", fields = f).question.single().text)
        assertEquals("かんじにほん", card("{{tts ja_JP:kana:Jp}}", fields = f).question.single().text)
        assertEquals("漢字日本", card("{{tts ja_JP:kanji:Jp}}", fields = f).question.single().text)
    }

    @Test fun unknownFilterIsIdentityAndPlainFieldsHaveNoSlot() {
        val f = mapOf("Front" to "x")
        assertEquals("x", card("{{tts en_US:whatever:Front}}", fields = f).question.single().text)
        assertTrue(card("{{Front}} and {{hint:Front}}", fields = f).question.isEmpty())
    }

    @Test fun conditionalsDecideWhichSlotsExist() {
        val q = "{{#Front}}{{tts en_US:Front}}{{/Front}}{{^Extra}}{{tts de_DE:Front}}{{/Extra}}{{#Extra}}{{tts fr_FR:Extra}}{{/Extra}}"
        assertEquals(listOf("en-US", "de-DE"), card(q, fields = mapOf("Front" to "a", "Extra" to "")).question.map { it.lang })
        assertEquals(listOf("en-US", "fr-FR"), card(q, fields = mapOf("Front" to "a", "Extra" to "e")).question.map { it.lang })
        assertTrue(card(q, fields = mapOf("Front" to "<br>", "Extra" to "<div></div>")).question.single().lang == "de-DE")
    }

    @Test fun emptyFieldKeepsABlankSlot() {
        val c = card("{{tts en_US:A}}{{tts en_US:B}}", fields = mapOf("A" to "", "B" to "b"))
        assertEquals(2, c.question.size)
        assertTrue(c.question[0].blank)
        assertEquals(listOf("en-US"), c.languages)
    }

    @Test fun literalTagsInTemplateOrFieldsAreFound() {
        val c = card("[anki:tts lang=en_US]fixed[/anki:tts] {{Front}}", fields = mapOf("Front" to "[anki:tts lang=\"de_DE\" speed=0.5]hallo [sound:x.mp3][/anki:tts]"))
        assertEquals(listOf(Item("fixed", "en-US", 1f), Item("hallo", "de-DE", .5f)), c.question)
    }

    @Test fun tagWithoutLanguageIsNotSpeech() {
        assertTrue(TtsRules.extract("[anki:tts]hi[/anki:tts]").isEmpty())
        assertNull(TtsRules.item("", "x"))
    }

    @Test fun typeAndHintFiltersAddNothing() {
        assertTrue(card("{{type:Front}}{{hint:Front}}", fields = mapOf("Front" to "x")).question.isEmpty())
    }

    @Test fun templateChoice() {
        val t = listOf(TtsRules.Template(1, "q1", "a1"), TtsRules.Template(0, "q0", "a0"))
        assertEquals("q1", TtsRules.templateFor(t, 1, false)?.question)
        assertNull(TtsRules.templateFor(t, 2, false))
        assertEquals("q0", TtsRules.templateFor(t, 5, true)?.question)
        assertNull(TtsRules.templateFor(emptyList(), 0, true))
    }

    @Test fun bindMapsReferencesToSlotsInOrder() {
        val a = Item("a", "en-US"); val b = Item("b", "en-US")
        assertEquals(listOf(a, b), TtsRules.bind(listOf(a, b), 2))
        assertEquals(listOf(a, null, null), TtsRules.bind(listOf(a), 3))
        assertEquals(emptyList<Item?>(), TtsRules.bind(listOf(a), 0))
    }

    @Test fun bindSkipsBlankSlotsWhenTheCountsDiffer() {
        val blank = Item("", "en-US"); val b = Item("b", "en-US")
        // same count: a blank slot is a reference that says nothing
        assertEquals(listOf(blank, b), TtsRules.bind(listOf(blank, b), 2))
        // a conditional hid the reference: the blank slot has none
        assertEquals(listOf(b), TtsRules.bind(listOf(blank, b), 1))
    }

    @Test fun soundTagsDoNotShiftTheBinding() {
        val c = card("[sound:one.mp3]{{tts en_US:A}}[sound:two.mp3]{{tts en_US:B}}", fields = mapOf("A" to "x", "B" to "y"))
        // the rendered side shows [sound:..] for files and [anki:play:q:1] / [anki:play:q:3] for speech: two references
        assertEquals(listOf("x", "y"), texts(TtsRules.bind(c.question, 2).filterNotNull()))
    }

    @Test fun missingVoiceNote() {
        assertEquals("No Japanese voice on this phone", TtsRules.missingVoiceNote(listOf("Japanese")))
        assertEquals("No Japanese and Polish voices on this phone", TtsRules.missingVoiceNote(listOf("Japanese", "Polish")))
        assertEquals("No German, French and Polish voices on this phone", TtsRules.missingVoiceNote(listOf("German", "French", "Polish")))
        assertEquals("No text-to-speech voice on this phone", TtsRules.missingVoiceNote(emptyList()))
    }

    @Test fun voiceAvailabilityCodes() {
        assertFalse(TtsRules.voiceMissing(0)); assertFalse(TtsRules.voiceMissing(1)); assertFalse(TtsRules.voiceMissing(2))
        assertTrue(TtsRules.voiceMissing(-1)); assertTrue(TtsRules.voiceMissing(-2))
    }

    @Test fun langTagConversion() {
        assertEquals("en-US", TtsRules.langTag("en_US")); assertEquals("zh-CN", TtsRules.langTag(" zh_CN ")); assertEquals("de", TtsRules.langTag("de"))
    }

    // ---------------------------------------------------------------- the page (CardHtml)

    private fun page(body: String, side: String, speech: List<Item>) = com.husarp.ankigo.CardHtml.build(body, "", 0, side, null, false, speech) { null }
    private val en = Item("hello", "en-US")
    private val fr = Item("bonjour", "fr-FR", 1.2f)

    @Test fun pageTurnsReferencesIntoSpeakerButtonsInOrder() {
        val p = page("Front [anki:play:q:0] and [anki:play:q:1]", "q", listOf(en, fr))
        assertEquals(listOf(en, fr), p.av.map { it.speech })
        assertEquals(p.av, p.autoplay)
        assertTrue(p.html.contains("AnkiGo.speak(0)")); assertTrue(p.html.contains("AnkiGo.speak(1)"))
        assertTrue(p.html.contains("aria-label=\"Read aloud\""))
        assertFalse(p.html.contains("[anki:play"))
        assertTrue(p.sounds.isEmpty())
    }

    @Test fun answerSideAutoplaysOnlyWhatFollowsTheDivider() {
        val p = page("Front [anki:play:q:0]<hr id=answer>Back [anki:play:a:1]", "a", listOf(en, fr))
        assertEquals(2, p.av.size)
        assertEquals(listOf(fr), p.autoplay.map { it.speech })
        // both buttons still work (they address the full list)
        assertTrue(p.html.contains("AnkiGo.speak(0)")); assertTrue(p.html.contains("AnkiGo.speak(1)"))
    }

    @Test fun unmatchedReferencesBecomeAChipNotAButton() {
        val p = page("x [anki:play:q:0] y [anki:play:q:1]", "q", listOf(en))
        assertEquals(1, p.av.size)
        assertEquals(1, Regex("""class="ag-tts"""").findAll(p.html).count())
        assertTrue(p.notes.any { it.contains("couldn't be matched") })
        val none = page("x [anki:play:q:0]", "q", emptyList())
        assertTrue(none.av.isEmpty()); assertTrue(none.autoplay.isEmpty())
        assertTrue(none.html.contains("class=\"ag-tts\""))
    }

    @Test fun blankSlotShowsNothing() {
        val p = page("x [anki:play:q:0] y", "q", listOf(Item("", "en-US")))
        assertTrue(p.av.isEmpty()); assertFalse(p.html.contains("ag-speak\"")); assertFalse(p.html.contains("class=\"ag-tts\""))
    }

    @Test fun literalTagInThePageIsHandledToo() {
        val p = page("a [anki:tts lang=de_DE speed=0.8]Guten Tag[/anki:tts] b", "q", emptyList())
        assertEquals(Item("Guten Tag", "de-DE", .8f), p.av.single().speech)
        assertFalse(p.html.contains("[anki:tts"))
    }
}
