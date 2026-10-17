package com.husarp.ankigo.deck

import com.husarp.ankigo.study.TtsRules

/**
 * The card as the note editor's Preview shows it: both sides rendered from the note type's template and the TEXTS THAT ARE IN THE
 * EDITOR NOW (not the saved ones; AnkiDroid's API only renders saved notes). Pure, JVM-tested. Template rules (conditionals, FrontSide,
 * field filters, cloze) are [TtsRules.render]'s; speech tags are dropped to their text, `type:` boxes and hints render empty, and a cloze
 * shows as plain text (`[...]` on the question), without the colour Anki gives it.
 */
object NotePreview {
    /** The two sides as HTML bodies; [answer] holds the question too when the template says `{{FrontSide}}`, like in study. */
    class Sides(val question: String, val answer: String)

    /** Null when the note type has no template for card [ord] (or none could be read): the caller shows the fields instead. */
    fun build(templates: List<TtsRules.Template>, cloze: Boolean, fieldNames: List<String>, values: List<String>, ord: Int): Sides? {
        val t = TtsRules.templateFor(templates, ord, cloze) ?: return null
        val fields = fieldNames.zip(values).toMap()
        val front = TtsRules.render(t.question, fields, ord, true, "")
        val back = TtsRules.render(t.answer, fields, ord, false, front)
        return Sides(plain(front), plain(back))
    }

    /** Without templates: every field under its name, in order. */
    fun fieldsOnly(fieldNames: List<String>, values: List<String>): String =
        fieldNames.zip(values).joinToString("<hr>") { (n, v) -> "<div style=\"font-size:11px;opacity:.6;text-transform:uppercase\">${escape(n)}</div><div>$v</div>" }

    private fun plain(s: String) = TtsRules.TAG.replace(s) { it.groupValues[2] }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
