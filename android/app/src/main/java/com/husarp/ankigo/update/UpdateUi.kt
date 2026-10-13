package com.husarp.ankigo.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Context.button(label: String, onClick: () -> Unit) =
    Button(this).apply { text = label; textSize = 12f; setOnClickListener { onClick() } }

/** Redraws on every updater change while the view is on screen. */
abstract class UpdatesView(ctx: Context) : LinearLayout(ctx) {
    private val listener: () -> Unit = { render(Updates.state) }
    init { orientation = VERTICAL }
    abstract fun render(s: Updates.State)
    override fun onAttachedToWindow() { super.onAttachedToWindow(); Updates.listen(listener); render(Updates.state) }
    override fun onDetachedFromWindow() { Updates.unlisten(listener); super.onDetachedFromWindow() }
}

/** Download progress, the waiting-on-Android steps, and a failed update (TRY AGAIN + GITHUB). */
private class UpdateProgress(ctx: Context) : LinearLayout(ctx) {
    private val text = TextView(ctx).apply { textSize = 12f }
    private val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    private val failRow = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        addView(ctx.button("TRY AGAIN") { Updates.update(ctx) })
        addView(ctx.button("GITHUB") { open(ctx, Updates.state.pageUrl) })
    }
    init {
        orientation = VERTICAL
        setPadding(0, ctx.dp(4), 0, 0)
        addView(text); addView(bar, LayoutParams(-1, -2)); addView(failRow)
    }

    fun render(s: Updates.State) {
        val p = s.phase
        visibility = if (p == Updates.Phase.Idle) View.GONE else View.VISIBLE
        bar.visibility = if (p is Updates.Phase.Downloading) View.VISIBLE else View.GONE
        failRow.visibility = if (p is Updates.Phase.Failed) View.VISIBLE else View.GONE
        text.setTextColor(if (p is Updates.Phase.Failed) Color.rgb(176, 0, 32) else Color.DKGRAY)
        text.text = when (p) {
            Updates.Phase.Idle -> ""
            is Updates.Phase.Downloading -> { bar.progress = p.percent; "Downloading… ${p.percent}%" }
            Updates.Phase.NeedsPermission -> "Allow \"Install unknown apps\" for AnkiGo, then come back - the update carries on by itself."
            Updates.Phase.Ready -> "Downloaded - Android's installer opens now."
            Updates.Phase.Installing -> "Confirm the update in Android's window."
            is Updates.Phase.Failed -> "Update failed. ${p.why}"
        }
    }
}

/** Main-screen banner: the new version, UPDATE and ✕ (hides it until the app is next started). */
class UpdateBanner(ctx: Context) : UpdatesView(ctx) {
    private val title = TextView(ctx).apply { textSize = 14f; setTextColor(Color.BLACK) }
    private val updateBtn = ctx.button("UPDATE") { Updates.update(ctx) }
    private val progress = UpdateProgress(ctx)
    init {
        setBackgroundColor(Color.rgb(255, 243, 205))
        setPadding(ctx.dp(12), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(title, LayoutParams(0, -2, 1f))
        row.addView(updateBtn)
        row.addView(ctx.button("✕") { Updates.dismissBanner() }.apply { contentDescription = "Close" }, LayoutParams(ctx.dp(48), -2))
        addView(row); addView(progress)
    }
    override fun render(s: Updates.State) {
        val latest = s.latest
        val show = latest != null && Updates.bannerVisible(s, Updates.current(context))
        visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        title.text = "Update available: AnkiGo ${latest!!.version}"
        updateBtn.isEnabled = s.phase !is Updates.Phase.Downloading
        progress.render(s)
    }
}

/** Settings → Updates: auto-check switch, current version + CHECK NOW, GITHUB, GET UPDATE. */
class UpdateSection(ctx: Context) : UpdatesView(ctx) {
    private val cur = Updates.current(ctx)
    private val sw = Switch(ctx).apply {
        text = "Check for updates"; textSize = 15f
        isChecked = Updates.checkEnabled(ctx)
        setOnCheckedChangeListener { _, on -> Updates.setCheckEnabled(ctx, on) }
    }
    private val checkBtn = ctx.button("CHECK NOW") { Updates.checkNow(ctx) }
    private val message = TextView(ctx).apply { textSize = 14f; setTextColor(Color.BLACK); setPadding(0, ctx.dp(4), 0, 0) }
    private val getBtn = ctx.button("GET UPDATE") { Updates.update(ctx) }
    private val progress = UpdateProgress(ctx)
    init {
        addView(sw, LayoutParams(-1, -2))
        addView(TextView(ctx).apply { text = "When the app opens or you come back to it."; textSize = 12f })
        val ver = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, ctx.dp(12), 0, 0) }
        ver.addView(TextView(ctx).apply { text = "Version $cur"; textSize = 15f; setTextColor(Color.BLACK) }, LayoutParams(0, -2, 1f))
        ver.addView(checkBtn)
        addView(ver)
        addView(message)
        val btns = LinearLayout(ctx).apply { orientation = HORIZONTAL }
        btns.addView(ctx.button("GITHUB") { open(ctx, Updates.RELEASES_PAGE) })
        btns.addView(getBtn)
        addView(btns)
        addView(progress)
        addView(TextView(ctx).apply {
            text = "Android asks you to confirm every update, and only an update signed with AnkiGo's own key installs."
            textSize = 12f; setPadding(0, ctx.dp(12), 0, 0)
        })
    }
    override fun render(s: Updates.State) {
        checkBtn.isEnabled = !s.checking
        message.text = s.message ?: ""
        message.visibility = if (s.message == null) View.GONE else View.VISIBLE
        getBtn.visibility = if (Updates.available(s, cur)) View.VISIBLE else View.GONE
        getBtn.isEnabled = s.phase !is Updates.Phase.Downloading
        progress.render(s)
    }
}

/** The Updates settings screen, opened from the main screen. */
class UpdatesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Updates"
        val pad = dp(16)
        setContentView(ScrollView(this).apply {
            addView(UpdateSection(this@UpdatesActivity).apply { setPadding(pad, pad, pad, pad) })
        })
    }

    override fun onResume() { super.onResume(); Updates.onResume(this) }
    override fun onPause() { Updates.onPause(this); super.onPause() }
}
