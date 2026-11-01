package com.husarp.ankigo.study

import com.husarp.ankigo.ui.MotionWebView

import android.app.Activity
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui
import android.webkit.*
import android.widget.*
import com.husarp.ankigo.*

/** Separate read-only card windows: no review bridge, rating, or scheduler mutation. */
class TemplateWindows(private val activity: Activity) {
    private val dialogs = mutableSetOf<AppDialog>()
    fun close() { dialogs.toList().forEach { it.dismiss() } }
    fun term(key: String, subject: String) {
        val file = TemplateBridge.dictionaryFile(subject) ?: return
        show(key, subject) { web ->
            Thread {
                val definition = runCatching { Media.open(activity, file)?.bufferedReader()?.use { TemplateBridge.definition(it.readText(), key) } }.getOrNull()
                activity.runOnUiThread { if (!activity.isDestroyed && web.tag == true) load(web, definition ?: CardHtml.esc(key), "") }
            }.start()
        }
    }
    fun note(id: Long) {
        show("Note $id") { web ->
            Thread {
                val result = runCatching {
                    val note = Anki.note(activity, id)
                    val card = Anki.card(activity, id, 0)
                        ?: Anki.searchCards(activity, "nid:$id", Anki.CARD_DEFAULT_COLS).minByOrNull { it["ord"]?.toIntOrNull() ?: Int.MAX_VALUE }
                    (card?.get("question").orEmpty() + "<hr>" + card?.get("answer").orEmpty()).takeIf { it.isNotBlank() && card != null }?.let {
                        Triple(it, note?.let { n -> Anki.model(activity, n.mid)?.css } ?: "", card?.get("ord")?.toIntOrNull() ?: 0)
                    }
                }.getOrNull()
                activity.runOnUiThread { if (!activity.isDestroyed && web.tag == true) load(web, result?.first ?: "Note unavailable", result?.second ?: "", result?.third ?: 0) }
            }.start()
        }
    }
    private fun load(web: WebView, html: String, css: String, ord: Int = 0) {
        web.loadDataWithBaseURL(CardHtml.BASE, CardHtml.build(html, css, ord, "a", null, dark = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES, tapShow = false) { null }.html, "text/html", "UTF-8", null)
    }
    private fun show(title: String, initialSubject: String = "", ready: (WebView) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val web = MotionWebView(activity).apply { tag = true }
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.addJavascriptInterface(TemplateBridge({ key, subject -> activity.runOnUiThread { term(key, subject) } }, { id -> activity.runOnUiThread { note(id) } }), "AnkiGo")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = resource(activity, request)
        }
        val row = LinearLayout(activity).apply {
            setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), 0)
        }
        fun field(label: String) = EditText(activity).apply {
            hint = label; contentDescription = label; textSize = 14f
            setTextColor(Ui.c(activity, R.color.ag_text)); setHintTextColor(Ui.c(activity, R.color.ag_sub0))
            typeface = Ui.text(activity, 14f, 700, R.color.ag_text).typeface
            setSingleLine()
        }
        val input = field("Check word")
        val subject = field("Subject").apply { setText(initialSubject) }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(subject, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(StudyUi.secondary(activity, "Check") { term(input.text.toString(), subject.text.toString()) })
        val frame = FrameLayout(activity).apply {
            addView(web, FrameLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * .48).toInt()))
        }
        val dialog = AppDialog.Builder(activity).setTitle(title.take(80)).setView(frame).setFooter(row).setPositiveButton("Close", null).create()
        dialog.setOnDismissListener { dialogs.remove(dialog); web.tag = false; web.destroy() }
        dialogs.add(dialog); dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .94).toInt(), (activity.resources.displayMetrics.heightPixels * .8).toInt())
        load(web, "Loading…", ""); ready(web)
    }
    companion object {
        fun resource(activity: Activity, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            when (CardResourcePolicy.route(request.isForMainFrame, uri.host, uri.path)) {
                // loadDataWithBaseURL owns the document; never replace it with a media 404.
                CardResourcePolicy.Route.DOCUMENT -> return null
                // The pre-1.0 offline policy: external subresources get empty success.
                CardResourcePolicy.Route.EMPTY -> return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                CardResourcePolicy.Route.MEDIA -> Unit
            }
            val name = uri.path?.trimStart('/').orEmpty()
            val stream = if (uri.scheme == "https" && uri.host == "ankigo.media") {
                if (name.startsWith("mathjax/") || name == "fonts/Nunito.ttf") runCatching { activity.assets.open(name) }.getOrNull() else Media.open(activity, name)
            } else null
            val mime = when (name.substringAfterLast('.').lowercase()) { "js" -> "application/javascript"; "css" -> "text/css"; else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "application/octet-stream" }
            return if (stream == null) WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream()) else WebResourceResponse(mime, null, stream)
        }
    }
}
