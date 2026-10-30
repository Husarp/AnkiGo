package com.husarp.ankigo.study

import android.content.Intent
import android.net.Uri
import android.text.Html

/** Only visible card text leaves the phone, and only when the learner asks. */
object CardExplain {
    const val PREF_TEMPLATE = "explainTemplate"
    const val DEFAULT_TEMPLATE = "Explain briefly and to the point, in the language of this flashcard. Focus on the word(s) a learner is least likely to know (rare, technical, foreign terms): what they mean, plus one short example. 2–4 sentences, no intro."
    const val NOTICE = "Opens Google in the browser"
    const val MAX_TEXT = 1500

    fun plain(html: String): String {
        val cleaned = html.replace(Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("\\[sound:[^]]*]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<(audio|video)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<(img|audio|video|source)\\b[^>]*>", RegexOption.IGNORE_CASE), " ")
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    }

    fun prompt(front: String, back: String, shown: Boolean, selected: String? = null, template: String = DEFAULT_TEMPLATE): String {
        val words = selected?.trim()?.take(300).orEmpty()
        val instruction = if (words.isEmpty()) template.ifBlank { DEFAULT_TEMPLATE }.take(1000)
            else "Explain «$words» in the context of this flashcard. " + template.ifBlank { DEFAULT_TEMPLATE }.take(1000)
        val f = plain(front)
        val b = if (shown) plain(back) else ""
        // Reserve room for both sides on long cards; the answer never enters the question-side prompt.
        val frontText = f.take(if (shown && b.isNotEmpty()) MAX_TEXT / 2 else MAX_TEXT)
        val backText = b.take(MAX_TEXT - frontText.length)
        return "$instruction Flashcard front: $frontText" + if (shown) " back: $backText" else ""
    }

    fun intent(prompt: String): Intent = Intent(Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("udm", "50").appendQueryParameter("q", prompt).build()).addCategory(Intent.CATEGORY_BROWSABLE)
}
