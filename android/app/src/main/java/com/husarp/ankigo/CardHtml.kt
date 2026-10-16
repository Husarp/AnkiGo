package com.husarp.ankigo

import android.text.Html
import com.husarp.ankigo.study.IoScript
import com.husarp.ankigo.study.JsApiScript
import com.husarp.ankigo.study.TtsRules

/** Builds the WebView page for one side of a card from the API's rendered HTML + note-type CSS. */
object CardHtml {
    const val BASE = "https://ankigo.media/"
    /** In document order: 1 = a sound file; a play reference (speech from the template); 2/3 = a literal [anki:tts] tag (options, text). */
    private val AV = Regex("""\[sound:(.+?)]|\[anki:play:[qa]:\d+]|\[anki:tts\b([^\]]*)](.*?)\[/anki:tts]""", RegexOption.DOT_MATCHES_ALL)
    private val PLAY_REF = Regex("""\[anki:play:[qa]:\d+]""")
    private const val SPEAKER = "<svg viewBox=\"0 0 24 24\" width=\"14\" height=\"14\" aria-hidden=\"true\"><path fill=\"#fff\" d=\"M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z\"/></svg>"
    private val TYPE = Regex("""\[\[type:(.+?)]]""")

    /**
     * Notes with a footer/header styled position:fixed or sticky: on a phone that floats over the text (and the footer
     * vanishes or hides lines on a long card). Such an element goes back into normal flow, so it scrolls with the card;
     * one that sat in the lower half of the view (a footer) is moved after the text and pushed to the bottom of a short
     * card, like AnkiDroid shows it, without covering anything. Others (headers, plain elements) are untouched.
     */
    const val UNFIX_JS = """(function(){
var body=document.body,qa=document.getElementById('qa');if(!qa)return;
var vh=window.innerHeight||document.documentElement.clientHeight,list=[];
body.querySelectorAll('*').forEach(function(el){
 if(el.closest('.ag-hint'))return;
 var p=getComputedStyle(el).position;if(p!=='fixed'&&p!=='sticky')return;
 for(var i=0;i<list.length;i++)if(list[i].el.contains(el))return;
 var r=el.getBoundingClientRect();
 list.push({el:el,bottom:p==='fixed'&&r.top+r.height/2>vh/2});
});
if(!list.length)return;
var anchor=qa.nextSibling;
list.forEach(function(it){
 var el=it.el,s=el.style;
 s.setProperty('position','static','important');
 ['top','bottom','left','right','inset','transform'].forEach(function(k){s.setProperty(k,'auto','important')});
 s.setProperty('transform','none','important');
 if(it.bottom){s.setProperty('margin-top','auto','important');body.insertBefore(el,anchor);qa.style.margin='auto 0 0'}
});
})();"""

    /** [av] = every sound and speech of the page in document order (a button's number is its index); [autoplay] = the part that plays by itself; [sounds] = the sound files of that part. */
    data class Page(val html: String, val sounds: List<String>, val notes: List<String>, val av: List<TtsRules.Av> = emptyList(), val autoplay: List<TtsRules.Av> = emptyList())

    fun esc(s: String) = Html.escapeHtml(s)

    /**
     * The tap listener of a side: the question side shows the answer when [tapShow] is on; the answer side goes back to the
     * question when [tapShow] and [tapHide] are both on. Taps on inputs, links, buttons and the like are left alone.
     */
    fun tapScript(side: String, tapShow: Boolean, tapHide: Boolean): String {
        val call = when {
            !tapShow -> return ""
            side == "q" -> "AnkiGo.flip()"
            tapHide -> "AnkiGo.unflip()"
            else -> return ""
        }
        return "document.addEventListener('click',function(e){if(!e.target.closest('input,a,button,textarea,select'))$call});"
    }

    /**
     * @param side "q" or "a"
     * @param typed what the user typed (answer side), or null
     * @param speech the card's speech slots for this side (TtsRules), bound to the page's play references in order
     * @param jsPage the page token card JavaScript must present (JsApi): calls from an older page are ignored
     * @param tapShow, tapHide the tap settings (Settings → Study), see [tapScript]
     * @param fieldValue resolves a type-in field name to its expected value
     */
    fun build(content: String, css: String, ord: Int, side: String, typed: String?, dark: Boolean = false, speech: List<TtsRules.Item> = emptyList(), jsPage: Int = 0, tapShow: Boolean = true, tapHide: Boolean = false, fieldValue: (String) -> String?): Page {
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

        // sounds and speech: one ordered list (button i = av[i]); autoplay list (answer side: only what follows <hr id=answer>)
        val autoplayFrom = if (side == "a") Regex("""<hr\b[^>]*\bid\s*=\s*["']?answer\b[^>]*>""", RegexOption.IGNORE_CASE).find(body)?.range?.last ?: 0 else 0
        val bound = TtsRules.bind(speech, PLAY_REF.findAll(body).count())
        val av = ArrayList<TtsRules.Av>(); var firstAuto = -1; var ref = 0; var chips = 0
        body = AV.replace(body) { m ->
            val speak = when {
                m.groups[1] != null -> null
                m.value.startsWith("[anki:play") -> bound[ref++].also { if (it == null) chips++ }
                else -> TtsRules.item(m.groupValues[2], m.groupValues[3]).also { if (it == null) chips++ }
            }
            if (m.groups[1] == null && speak == null) return@replace "<span class=\"ag-tts\" title=\"Can't be read aloud here\">TTS</span>"
            if (speak != null && speak.blank) return@replace ""
            if (firstAuto < 0 && m.range.first >= autoplayFrom) firstAuto = av.size
            val i = av.size
            if (speak != null) {
                av.add(TtsRules.Av(speech = speak))
                "<a class=\"ag-replay ag-speak\" href=\"#\" role=\"button\" aria-label=\"Read aloud\" onclick=\"AnkiGo.speak($i);return false;\">$SPEAKER</a>"
            } else {
                val name = m.groupValues[1]
                av.add(TtsRules.Av(file = name))
                val n = name.replace("\\", "\\\\").replace("'", "\\'")
                "<a class=\"ag-replay\" href=\"#\" onclick=\"AnkiGo.play('${esc(n)}');return false;\">&#9654;</a>"
            }
        }
        val autoplay = if (firstAuto < 0) emptyList() else av.drop(firstAuto)
        val sounds = autoplay.mapNotNull { it.file }
        if (chips > 0) notes.add("$chips speech reference${if (chips == 1) "" else "s"} couldn't be matched to the template")

        if (body.contains("\\(") || body.contains("\\[")) notes.add("MathJax content (offline)")
        val io = IoScript.needed(body)
        if (body.contains("<img", true)) notes.add("has <img>")
        if (body.contains("cloze", true)) notes.add("cloze markup")

        val html = """<!doctype html><html class="mobile android linux js"><head><meta charset="utf-8">
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
${if (io) IoScript.CSS else ""}
/* Note-type typography and cloze styles take precedence over the defaults. */
$css
/* Keep the frame padding and AnkiDroid night surface around the note's CSS. */
body{margin:0;padding:14px 18px${if (side == "q") ";padding-bottom:30px" else ""}}
${if (dark) "body.nightMode{background:#202124;color:#e8eaed}" else ""}
</style>
${if (io) "<script>${IoScript.JS}</script>" else ""}
<script>${JsApiScript.token(jsPage)}${JsApiScript.JS}</script>
<script>
window.MathJax={tex:{inlineMath:[['\\(','\\)']],displayMath:[['\\[','\\]']]},startup:{typeset:true},
 options:{ignoreHtmlClass:'tex2jax_ignore'}};
window.onerror=function(m,s,l){try{AnkiGo.jsError(m+' @'+s+':'+l)}catch(e){}};
</script>
${if (body.contains("\\(") || body.contains("\\[")) """<script async src="https://ankigo.media/mathjax/tex-mml-chtml.js"
 onload="AnkiGo.mathjax('loaded')" onerror="AnkiGo.mathjax('FAILED')"></script>""" else ""}
</head><body class="card card${ord + 1} chrome${if (dark) " nightMode night_mode" else ""}"><div id="qa">$body</div>${if (side == "q" && tapShow) "<div class=\"ag-hint\">Tap the card to show the answer</div>" else ""}<script>$UNFIX_JS</script><script>
${tapScript(side, tapShow, tapHide)}
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
        return Page(html, sounds, notes, av, autoplay)
    }
}
