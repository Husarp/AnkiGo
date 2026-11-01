package com.husarp.ankigo.study

import com.husarp.ankigo.ui.MotionWebView

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import org.json.JSONArray

/** Keeps Android's selection toolbar and adds one action; template actions use the host’s separate JavaScript bridge. */
class ExplainWebView(context: Context, private val allowed: () -> Boolean, private val explain: (String) -> Unit, private val pageVersion: () -> Int = { 0 }) : MotionWebView(context) {
    private val explainId = 0x416e6b
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = super.startActionMode(wrap(callback))
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(wrap(callback), type)

    private fun wrap(original: ActionMode.Callback) = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = original.onCreateActionMode(mode, menu)
            if (created && allowed() && menu.findItem(explainId) == null) menu.add(0, explainId, 1, "Explain").apply {
                contentDescription = "Explain · ${CardExplain.NOTICE}"
                setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            return created
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val changed = original.onPrepareActionMode(mode, menu)
            menu.findItem(explainId)?.isVisible = allowed()
            return changed
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != explainId) return original.onActionItemClicked(mode, item)
            val version = pageVersion()
            if (allowed()) evaluateJavascript("window.getSelection().toString()") { raw ->
                val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
                if (allowed() && version == pageVersion() && text.isNotBlank()) explain(text)
                mode.finish()
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: android.view.View, rect: android.graphics.Rect) {
            if (original is ActionMode.Callback2) original.onGetContentRect(mode, view, rect) else super.onGetContentRect(mode, view, rect)
        }
    }
}
