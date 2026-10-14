package com.husarp.ankigo.study

/**
 * The card-side half of the AnkiDroid JavaScript API: the `AnkiDroidJS` class (same 69 methods and signatures as
 * AnkiDroid's js-api.js), the legacy card.js globals and the `ankidroid.*` helpers. A WebView can't read POST bodies,
 * so instead of `fetch` every method calls the synchronous bridge `AnkiGoJs.call(page, endpoint, data)` and wraps the
 * answer in a resolved Promise. Nothing here throws: whatever goes wrong resolves to `{success:false,value:null}`.
 * Everything is assigned on `window` (not declared) so a template that pastes its own copy of js-api.js still works.
 */
object JsApiScript {
    private fun obj(pairs: Iterable<Pair<String, String>>) = pairs.joinToString(",", "{", "}") { (k, v) -> "'$k':'$v'" }

    /** `<script>` body; the page token (`window.__agPage`) is set by a script before it. */
    val JS: String = """
(function(){
var P=window.__agPage,L=${obj(JsApi.METHODS.map { it.key to it.value })},U=${obj(JsApi.UNSUPPORTED.map { it to "1" })},warned={};
function raw(e){return e.indexOf('nextTime')>=0||e.indexOf('deckName')>=0}
function bad(){return {success:false,value:null}}
function send(e,d){
 var t;
 try{t=AnkiGoJs.call(P,e,d===undefined||d===null?'':String(d))}catch(x){t='{"success":false,"value":null}'}
 if(U[e]&&!warned[e]){warned[e]=1;try{console.warn('AnkiGo does not support '+e+'; it returns success:false')}catch(x){}}
 return t;
}
function out(e,t){if(raw(e))return t;try{return JSON.parse(t)}catch(x){return bad()}}
window.AnkiDroidJS=class AnkiDroidJS{
 constructor(o){o=o||{};this.developer=o.developer;this.version=o.version;this.handleRequest=function(e,d){return Promise.resolve(out(e,send(e,d)))};this.handleRequest('init')}
 static init(o){return new AnkiDroidJS(o)}
};
var proto=window.AnkiDroidJS.prototype;
Object.keys(L).forEach(function(m){
 var e=L[m];
 if(m==='ankiAddTagToNote')proto[m]=async function(noteId,tag){console.warn('ankiAddTagToNote is deprecated. Use ankiSetNoteTags instead');return this.handleRequest(e,JSON.stringify({noteId:noteId,tag:tag}))};
 else if(m==='ankiSetNoteTags')proto[m]=async function(tags){return this.handleRequest(e,JSON.stringify({tags:tags}))};
 else if(m==='ankiTtsSpeak')proto[m]=async function(text,queueMode){return this.handleRequest(e,JSON.stringify({text:text,queueMode:queueMode===undefined?0:queueMode}))};
 else if(m==='ankiShowToast')proto[m]=async function(text,shortLength){return this.handleRequest(e,JSON.stringify({text:text,shortLength:shortLength===undefined?true:shortLength}))};
 else proto[m]=async function(data){return this.handleRequest(e,data)};
});
function go(u){window.location.href=u}
window.showAnswer=function(){go('signal:show_answer')};
for(var n=1;n<=4;n++)(function(n){window['buttonAnswerEase'+n]=function(){go('signal:answer_ease'+n)}})(n);
window.reloadPage=function(){};window._relinquishFocus=function(){document.body.style.cursor='default'};
window.taFocus=function(){};window.taChange=function(){};window.addHook=function(){};
window.taKey=function(i,ev){var k=window.event?window.event.keyCode:ev&&ev.which;if(k==13){go('signal:show_answer');return false}return true};
window.showAllHints=function(){document.querySelectorAll('a.hint').forEach(function(a){a.click()})};
window.showHint=function(){var a=document.querySelector("a.hint:not([style*='display: none'])");if(a)a.click()};
var A=window.ankidroid=window.ankidroid||{};
A.showHint=window.showHint;A.showAllHints=window.showAllHints;
A.onTypeAnswerKeyDown=function(ev){if(ev&&ev.key==='Enter')go('ankidroid://show-answer')};
A.userAction=function(n){try{var f=window['userJs'+n];if(typeof f==='function')f()}catch(x){}};
window.onUpdateHook=window.onUpdateHook||[];window.onShownHook=window.onShownHook||[];
function run(list){return Promise.all((Array.isArray(list)?list:[]).map(function(f){try{return Promise.resolve(f()).catch(function(){})}catch(x){return 0}}))}
window.addEventListener('load',function(){run(window.onUpdateHook).then(function(){return run(window.onShownHook)})});
})();
"""

    /** The page token script: calls from a page that is no longer on screen are ignored by the Kotlin side. */
    fun token(page: Int) = "window.__agPage=$page;"
}
