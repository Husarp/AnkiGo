package com.husarp.ankigo

import android.text.Html

/** Builds the WebView page for one side of a card from the API's rendered HTML + note-type CSS. */
object CardHtml {
    const val BASE = "https://ankigo.media/"
    private val SOUND = Regex("""\[sound:(.+?)]""")
    private val AVREF = Regex("""\[anki:play:([qa]):(\d+)]""")
    private val TYPE = Regex("""\[\[type:(.+?)]]""")

    data class Page(val html: String, val sounds: List<String>, val notes: List<String>)

    fun esc(s: String) = Html.escapeHtml(s)

    /**
     * @param side "q" or "a"
     * @param typed what the user typed (answer side), or null
     * @param fieldValue resolves a type-in field name to its expected value
     */
    fun build(content: String, css: String, ord: Int, side: String, typed: String?, fieldValue: (String) -> String?): Page {
        val notes = ArrayList<String>()
        var body = content

        // type-in answer
        body = TYPE.replace(body) { m ->
            val field = m.groupValues[1]
            notes.add("type-in field '$field'")
            if (side == "q") {
                "<input id=\"typeans\" type=\"text\" autocomplete=\"off\" style=\"width:90%;font-size:1.1em\" placeholder=\"type answer\">"
            } else {
                val bare = field.substringAfterLast(':')
                val expected = Html.fromHtml(fieldValue(bare) ?: "?", 0).toString().trim()
                if (field.startsWith("cloze:")) notes.add("type-in on cloze: expected shown is the whole field (cloze part not extracted)")
                val ok = typed != null && typed.trim().equals(expected, ignoreCase = true)
                "<div style=\"font-family:monospace\">you: <b style=\"color:${if (ok) "green" else "red"}\">${esc(typed ?: "")}</b><br>correct: <b>${esc(expected)}</b></div>"
            }
        }

        // sounds: replay buttons; autoplay list (answer side: only what follows <hr id=answer>)
        val autoplayFrom = if (side == "a") listOf("<hr id=answer>", "<hr id=\"answer\">").map { body.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0 else 0
        val sounds = SOUND.findAll(body).filter { it.range.first >= autoplayFrom }.map { it.groupValues[1] }.toList()
        body = SOUND.replace(body) { m ->
            val n = m.groupValues[1].replace("\\", "\\\\").replace("'", "\\'")
            "<a class=\"ag-replay\" href=\"#\" onclick=\"AnkiGo.play('$n');return false;\">&#9654;</a>"
        }
        if (AVREF.containsMatchIn(body)) notes.add("TTS / [anki:play] tag present: not supported in spike")
        body = AVREF.replace(body) { "<span class=\"ag-tts\">[TTS n/a]</span>" }

        if (body.contains("\\(") || body.contains("\\[")) notes.add("MathJax content (loaded from CDN)")
        if (body.contains("image-occlusion", true) || body.contains("imageOcclusion")) notes.add("IMAGE OCCLUSION detected: needs AnkiDroid's reviewer JS (anki.imageOcclusion), not available")
        if (body.contains("<img", true)) notes.add("has <img>")
        if (body.contains("cloze", true)) notes.add("cloze markup")

        val html = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>$css
body{margin:0;padding:10px;} img{max-width:100%;height:auto;}
.ag-replay{display:inline-block;width:1.6em;height:1.6em;line-height:1.6em;text-align:center;border-radius:50%;background:#2a7de1;color:#fff;text-decoration:none;margin:0 .2em;}
.ag-tts{color:#999;font-size:.8em}
</style>
<script>
window.MathJax={tex:{inlineMath:[['\\(','\\)']],displayMath:[['\\[','\\]']]},startup:{typeset:true},
 options:{ignoreHtmlClass:'tex2jax_ignore'}};
window.onerror=function(m,s,l){try{AnkiGo.jsError(m+' @'+s+':'+l)}catch(e){}};
</script>
<script async src="https://cdn.jsdelivr.net/npm/mathjax@3.2.2/es5/tex-mml-chtml.js"
 onload="AnkiGo.mathjax('loaded')" onerror="AnkiGo.mathjax('FAILED to load (offline?)')"></script>
</head><body class="card card${ord + 1}"><div id="qa">$body</div></body></html>"""
        return Page(html, sounds, notes)
    }
}
