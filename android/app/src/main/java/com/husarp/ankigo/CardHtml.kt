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
    fun build(content: String, css: String, ord: Int, side: String, typed: String?, dark: Boolean = false, fieldValue: (String) -> String?): Page {
        val notes = ArrayList<String>()
        var body = content

        // type-in answer
        body = TYPE.replace(body) { m ->
            val field = m.groupValues[1]
            notes.add("type-in field '$field'")
            if (side == "q") {
                "<input id=\"typeans\" type=\"text\" autocomplete=\"off\" class=\"ag-typeans\" placeholder=\"type answer\">"
            } else {
                val bare = field.substringAfterLast(':')
                val raw = fieldValue(bare) ?: "?"
                val expected = Html.fromHtml(if (field.startsWith("cloze:")) CardMedia.clozeExpected(raw, ord) else raw, 0).toString().trim()
                "<div style=\"font-family:monospace\">${CardMedia.comparison(typed ?: "", expected, ::esc)}<br>correct: <b>${esc(expected)}</b></div>"
            }
        }

        // sounds: replay buttons; autoplay list (answer side: only what follows <hr id=answer>)
        val autoplayFrom = if (side == "a") Regex("""<hr\b[^>]*\bid\s*=\s*["']?answer\b[^>]*>""", RegexOption.IGNORE_CASE).find(body)?.range?.last ?: 0 else 0
        val sounds = SOUND.findAll(body).filter { it.range.first >= autoplayFrom }.map { it.groupValues[1] }.toList()
        body = SOUND.replace(body) { m ->
            val n = m.groupValues[1].replace("\\", "\\\\").replace("'", "\\'")
            "<a class=\"ag-replay\" href=\"#\" onclick=\"AnkiGo.play('${esc(n)}');return false;\">&#9654;</a>"
        }
        if (AVREF.containsMatchIn(body)) notes.add("TTS / [anki:play] tag present: not supported in spike")
        body = AVREF.replace(body) { "<span class=\"ag-tts\">[TTS n/a]</span>" }

        if (body.contains("\\(") || body.contains("\\[")) notes.add("MathJax content (offline)")
        if (body.contains("image-occlusion", true) || body.contains("imageOcclusion")) notes.add("IMAGE OCCLUSION detected: needs AnkiDroid's reviewer JS (anki.imageOcclusion), not available")
        if (body.contains("<img", true)) notes.add("has <img>")
        if (body.contains("cloze", true)) notes.add("cloze markup")

        val html = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
@font-face{font-family:Nunito;src:url('https://ankigo.media/fonts/Nunito.ttf');font-weight:100 900}
html{box-sizing:border-box}*,*:before,*:after{box-sizing:inherit}
body{margin:0;min-height:100vh;padding:14px 18px;font-family:Arial,Helvetica,sans-serif;font-size:21px;line-height:1.35;text-align:center;display:flex;flex-direction:column;position:relative;background:${if (dark) "#202124" else "#fafafa"};color:${if (dark) "#e8eaed" else "#202124"}}
#qa{width:100%;margin:auto 0}
hr{border:0;border-top:1px solid ${if (dark) "#3c4043" else "#e3e3e3"};margin:14px 10%}
img{max-width:100%;height:auto}
.ag-replay{display:inline-flex;width:26px;height:26px;align-items:center;justify-content:center;border-radius:50%;background:#2a7de1;color:#fff;text-decoration:none;margin-left:6px;font-size:11px;vertical-align:middle}
.ag-typeans{font:inherit;font-size:18px;width:80%;margin:12px auto 0;padding:6px 8px;border:1px solid ${if (dark) "#3c4043" else "#e3e3e3"};border-radius:6px;background:transparent;color:inherit;text-align:center}
.ag-tts{color:${if (dark) "#9aa0a6" else "#5f6368"};font-size:14px}
.ag-hint{position:absolute;left:0;right:0;bottom:8px;font-family:Nunito,sans-serif;font-size:11px;font-weight:800;color:${if (dark) "#9aa0a6" else "#5f6368"};opacity:.8;pointer-events:none}
.ag-placeholder{width:210px;max-width:100%;height:118px;border:2px dashed ${if (dark) "#3c4043" else "#e3e3e3"};border-radius:10px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;color:${if (dark) "#9aa0a6" else "#5f6368"};font-size:12px;margin:8px auto 0;font-family:Nunito,sans-serif;font-weight:700;overflow-wrap:anywhere}
.ag-placeholder svg{width:30px;height:30px}
/* Note-type typography and cloze styles take precedence over the defaults. */
$css
/* Keep the frame padding and AnkiDroid night surface around the note's CSS. */
body{margin:0;padding:14px 18px${if (side == "q") ";padding-bottom:30px" else ""}}
${if (dark) "body.nightMode{background:#202124;color:#e8eaed}" else ""}
</style>
<script>
window.MathJax={tex:{inlineMath:[['\\(','\\)']],displayMath:[['\\[','\\]']]},startup:{typeset:true},
 options:{ignoreHtmlClass:'tex2jax_ignore'}};
window.onerror=function(m,s,l){try{AnkiGo.jsError(m+' @'+s+':'+l)}catch(e){}};
</script>
${if (body.contains("\\(") || body.contains("\\[")) """<script async src="https://ankigo.media/mathjax/tex-mml-chtml.js"
 onload="AnkiGo.mathjax('loaded')" onerror="AnkiGo.mathjax('FAILED')"></script>""" else ""}
</head><body class="card card${ord + 1}${if (dark) " nightMode night_mode" else ""}"><div id="qa">$body</div>${if (side == "q") "<div class=\"ag-hint\">Tap the card to show the answer</div>" else ""}<script>
${if (side == "q") "document.addEventListener('click',function(e){if(!e.target.closest('input,a,button,textarea,select'))AnkiGo.flip()});" else ""}
document.addEventListener('contextmenu',function(e){e.preventDefault();AnkiGo.actions()});
document.querySelectorAll('img').forEach(function(i){
 i.onerror=function(){
  var p=document.createElement('div');p.className='ag-placeholder';p.setAttribute('role','img');
  p.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="10" r="2" fill="currentColor"/><path d="M4 18l5-5 3 3 3-3 5 5M3 3l18 18" fill="none" stroke="currentColor" stroke-width="2"/></svg>';
  var n=document.createElement('span');var file=i.getAttribute('src')||'';
  try{file=decodeURIComponent(file.split('/').pop())}catch(e){}
  n.textContent=file||'Picture unavailable';p.appendChild(n);p.setAttribute('aria-label','Picture unavailable: '+n.textContent);i.replaceWith(p);
  document.getElementById('qa').style.marginTop='8px';
  document.querySelectorAll('.ag-hint').forEach(function(h){h.remove()});
 };
 if(i.complete&&i.naturalWidth===0)i.onerror();
});
</script></body></html>"""
        return Page(html, sounds, notes)
    }
}
