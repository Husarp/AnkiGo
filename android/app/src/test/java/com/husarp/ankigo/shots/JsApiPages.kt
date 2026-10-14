package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.CardHtml
import com.husarp.ankigo.study.JsApi
import java.io.File
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Writes card pages that use the AnkiDroid JavaScript API (real CardHtml.build output) to build/cardhtml/t47 for headless
 * Chrome. A stub `AnkiGoJs.call` answers with the real JsApi.call text, so the page shows what a card script would see.
 */
class JsApiPages {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val out = File(System.getProperty("user.dir"), "build/cardhtml/t47").apply { mkdirs() }
    private val row = mapOf(
        "_id" to "1700000000123", "note_id" to "1600000000456", "deck_id" to "42", "original_deck_id" to "0", "reps" to "9", "lapses" to "2",
        "type" to "2", "queue" to "2", "due" to "19876", "original_due" to "0", "left" to "0", "interval" to "33", "sm2_factor" to "2500", "flags" to "3",
    )
    private fun snap(answer: Boolean, dark: Boolean) = JsApi.snapshot(row, 4, listOf("<1m", "6m", "10m", "4d"), Triple(12, 3, 48), " verbs Marked ", "Languages::French::Verbs", true)
        .copy(page = 1, answer = answer, night = dark)

    private fun stub(answer: Boolean, dark: Boolean): String {
        val s = snap(answer, dark)
        val table = JSONObject()
        for (e in JsApi.METHODS.values + "init") table.put(e, JsApi.call(1, e, "", s).text)
        return "<script>window.__table=$table;" +
            "window.AnkiGo={flip:function(){},actions:function(){},play:function(){},speak:function(){},mathjax:function(){},jsError:function(m){(window.__errs=window.__errs||[]).push(m)}};" +
            "window.AnkiGoJs={call:function(p,e,d){window.__log=(window.__log||[]).concat(e);return p===1&&window.__table[e]||'${JsApi.FAIL}'}};" +
            "window.addEventListener('error',function(ev){(window.__errs=window.__errs||[]).push(String(ev.message))});" +
            "window.addEventListener('load',function(){setTimeout(function(){var e=document.getElementById('errs');if(e)e.textContent='script errors: '+((window.__errs||[]).join(' | ')||'none')},300)});</script>"
    }

    private val pasted = """
class AnkiDroidJS { constructor(o){ this.mine = true; } async ankiGetNewCardCount(){ return {success:true,value:-1} } }
"""
    private fun content(paste: Boolean) = """
<div style="font-size:26px;font-weight:700">Conjugate: <i>avoir</i></div>
<div id="out" style="text-align:left;font:13px/1.45 monospace;margin:14px 0 6px;white-space:pre-wrap"></div>
<div id="errs" style="text-align:left;font:12px monospace;color:#c53030"></div>
<p><a href="signal:show_answer">signal:show_answer link</a> &middot; <button onclick="showAnswer()">showAnswer()</button> &middot; <button onclick="buttonAnswerEase3()">ease 3</button></p>
<a class="hint" href="#" onclick="this.outerHTML='<b id=hinted>hint opened</b>';return false">show hint</a>
<script>
${if (paste) pasted else ""}
var api = ${if (paste) "new AnkiDroidJS({developer:'x@y.z',version:'0.0.3'})" else "new AnkiDroidJS({developer: 'x@y.z', version: '0.0.3'})"};
var lines = [];
function show(){ document.getElementById('out').textContent = lines.join('\n'); }
${if (paste) "lines.push('template-defined class used: ' + (api.mine === true)); show();" else """
(async function(){
  async function add(label, p){ var v; try { v = await p } catch(e){ v = 'THREW ' + e } lines.push(label + ' = ' + JSON.stringify(v) + (typeof v === 'string' ? '  [string]' : '')); show(); }
  await add('new/lrn/rev', Promise.all([api.ankiGetNewCardCount(), api.ankiGetLrnCardCount(), api.ankiGetRevCardCount()]).then(function(a){return a.map(function(r){return r.value}).join('/')}));
  await add('nextTime1', api.ankiGetNextTime1());
  await add('deckName', api.ankiGetDeckName());
  await add('isDisplayingAnswer', api.ankiIsDisplayingAnswer());
  await add('isInNightMode', api.ankiIsInNightMode());
  await add('cardId', api.ankiGetCardId());
  await add('cardFlag', api.ankiGetCardFlag());
  await add('getNoteTags', api.ankiGetNoteTags());
  await add('markCard (unsupported)', api.ankiMarkCard());
  await add('stale page call', Promise.resolve(AnkiGoJs.call(99, 'cardId', '')));
  onShownHook.push(function(){ lines.push('onShownHook ran, update hooks first: ' + (window.__upd === true)); show(); });
  onUpdateHook.push(function(){ window.__upd = true; });
  document.querySelector('.hint') && lines.push('classes: ' + document.documentElement.className + ' | body has chrome: ' + document.body.classList.contains('chrome'));
  show();
})();"""}
</script>
"""

    private fun write(name: String, side: String, dark: Boolean, paste: Boolean = false) {
        var html = CardHtml.build(content(paste), ".card { font-family: arial; font-size: 20px; text-align: center }", 0, side, null, dark, jsPage = 1) { null }.html
        html = html.replace("<meta charset=\"utf-8\">", "<meta charset=\"utf-8\">${stub(side == "a", dark)}")
        File(out, "$name.html").writeText(html)
    }

    @Test fun pages() {
        for (dark in listOf(false, true)) { val t = if (dark) "dark" else "light"; write("q-$t", "q", dark); write("a-$t", "a", dark) }
        write("pasted-q-light", "q", false, paste = true)
    }
}
