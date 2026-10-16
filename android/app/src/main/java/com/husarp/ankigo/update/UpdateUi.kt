package com.husarp.ankigo.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.BarMeaning
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.DisplayNumbers
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.setMeaning

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Pill button in the app's style: the view is [heightDp] tall (touch target, 48 dp), the filled pill inside it is
 * [visualDp] tall. Primary = mauve with `ag_base` text, secondary = `ag_s0` with normal text.
 */
private fun pillButton(ctx: Context, label: String, primary: Boolean, textSp: Float, visualDp: Int, hPadDp: Int, onClick: () -> Unit): TextView {
    val fill = Ui.rounded(Ui.c(ctx, if (primary) R.color.ag_mauve else R.color.ag_s0), 12, ctx)
    val ripple = RippleDrawable(ColorStateList.valueOf(0x33000000), fill, null)
    val inset = Ui.dp(ctx, (48 - visualDp) / 2)
    return Ui.text(ctx, textSp, 900, if (primary) R.color.ag_base else R.color.ag_text).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = Ui.dp(ctx, 48)
        background = InsetDrawable(ripple, 0, inset, 0, inset)
        setPadding(Ui.dp(ctx, hPadDp), 0, Ui.dp(ctx, hPadDp), 0)   // after the background, which resets padding
        isClickable = true
        setOnClickListener { onClick() }
    }
}

/** A weight-1 slot in a horizontal row. */
private fun slot(gapStartDp: Int, ctx: Context) =
    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(ctx, gapStartDp) }

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
    private val text = Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply { setLineSpacing(0f, 1.15f) }
    private val bar = BarView(ctx)
    private val failRow = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        addView(pillButton(ctx, "TRY AGAIN", true, 13f, 40, 12) { Updates.update(ctx) }, slot(0, ctx))
        addView(pillButton(ctx, "GITHUB", false, 13f, 40, 12) { open(ctx, Updates.state.pageUrl) }, slot(8, ctx))
    }
    init {
        orientation = VERTICAL
        addView(text)
        addView(bar, LayoutParams(-1, Ui.dp(ctx, 6)).also { it.topMargin = Ui.dp(ctx, 8) })
        addView(failRow, LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
    }

    fun render(s: Updates.State) {
        val p = s.phase
        visibility = if (p == Updates.Phase.Idle) View.GONE else View.VISIBLE
        bar.visibility = if (p is Updates.Phase.Downloading) View.VISIBLE else View.GONE
        failRow.visibility = if (p is Updates.Phase.Failed) View.VISIBLE else View.GONE
        text.setTextColor(Ui.c(context, if (p is Updates.Phase.Failed) R.color.ag_red else R.color.ag_sub1))
        text.typeface = Ui.font(context, if (p is Updates.Phase.Failed || p is Updates.Phase.Downloading) 800 else 700)
        text.text = when (p) {
            Updates.Phase.Idle -> ""
            is Updates.Phase.Downloading -> {
                bar.setMeaning(BarMeaning.PROGRESS, p.percent / 100f, false)
                DisplayNumbers.progress(p.done, p.total, p.elapsedMs)
            }
            Updates.Phase.NeedsPermission -> "Allow \"Install unknown apps\" for AnkiGo, then come back - the update carries on by itself."
            Updates.Phase.Ready -> "Downloaded - Android's installer opens now."
            Updates.Phase.Installing -> "Confirm the update in Android's window."
            is Updates.Phase.Failed -> "Update failed. ${p.why}"
        }
    }
}

/**
 * The update banner: a slim card floating over the top of a screen (see [UpdateOverlay]); it never pushes the layout.
 * It slides in when it appears; while idle it slides away again after 8 s (or on a swipe up) and stays away for this
 * screen visit. While downloading / failed / waiting for Android it stays. ✕ hides it until tomorrow.
 */
class UpdateBanner(ctx: Context) : UpdatesView(ctx) {
    private val title = Ui.text(ctx, 14f, 900, R.color.ag_text).apply { maxLines = 1 }
    private val subtitle = Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "New version of AnkiGo"; maxLines = 1 }
    private val updateBtn = pillButton(ctx, "UPDATE", true, 13f, 32, 14) { Updates.update(ctx) }
    private val progress = UpdateProgress(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private var timerArmed = false
    private val autoHide = Runnable { timerArmed = false; if (Updates.state.phase == Updates.Phase.Idle) slideAway() }
    private val slop = Ui.dp(ctx, 24)
    private var downY = 0f
    /** Slid away (auto-hide or swipe) for this screen visit; [resetAway] on the next return to the screen. */
    private var away = false
    /** Switched off from outside (picture-in-picture). */
    var suppressed = false
        set(v) { field = v; render(Updates.state) }
    /** False in the screenshots (no animation, no timer): the banner is drawn at its final position. */
    var animated = true

    init {
        // the shadow of the card lives in this view's padding, so nothing above it may clip
        clipChildren = false
        clipToPadding = false
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        val card = LinearLayout(ctx).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6))
            elevation = Ui.dp(ctx, 6f)
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                cornerRadius = Ui.dp(ctx, 14f)
                setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_mauve) and 0x00FFFFFF or 0x59000000)
            }
        }
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_down)
            setColorFilter(Ui.c(ctx, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
            val pad = Ui.dp(ctx, 8)
            setPadding(pad, pad, pad, pad)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve) and 0x00FFFFFF or 0x2E000000, 11, ctx)
        }, LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36)).also { it.marginEnd = Ui.dp(ctx, 10) })
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL; addView(title); addView(subtitle, LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) }) }
        row.addView(texts, LayoutParams(0, -2, 1f))
        row.addView(updateBtn, LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            val pad = Ui.dp(ctx, 14)
            setPadding(pad, pad, pad, pad)
            contentDescription = "Close"
            isClickable = true
            setOnClickListener { Updates.dismissBanner(ctx) }
        }, LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 48)))
        card.addView(row)
        card.addView(progress, LayoutParams(-1, -2).also { it.marginEnd = Ui.dp(ctx, 8); it.bottomMargin = Ui.dp(ctx, 6) })
        addView(card, LayoutParams(-1, -2))
        visibility = View.GONE
    }

    /** A new visit to the screen (it came back to the front): the banner may slide in again. */
    fun resetAway() { away = false; render(Updates.state) }

    private fun slideAway() {
        stopTimer()
        away = true
        if (!animated || visibility != View.VISIBLE) { visibility = View.GONE; return }
        animate().translationY(-(height + Ui.dp(context, 8)).toFloat()).setDuration(200).setInterpolator(AccelerateInterpolator())
            .withEndAction { if (away) visibility = View.GONE }.start()
    }

    private fun slideIn() {
        if (!animated) { translationY = 0f; return }
        animate().cancel()
        translationY = -Ui.dp(context, 400f)   // out of sight until the first layout tells the real height
        val vto = viewTreeObserver
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (vto.isAlive) vto.removeOnPreDrawListener(this) else viewTreeObserver.removeOnPreDrawListener(this)
                translationY = -(height + Ui.dp(context, 8)).toFloat()
                animate().translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
                return true
            }
        })
    }

    private fun restartTimer() {
        stopTimer()
        if (animated && visibility == View.VISIBLE && Updates.state.phase == Updates.Phase.Idle) { timerArmed = true; handler.postDelayed(autoHide, 8_000L) }
    }

    private fun stopTimer() { timerArmed = false; handler.removeCallbacks(autoHide) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.rawY; restartTimer() }
            MotionEvent.ACTION_MOVE -> if (downY - e.rawY > slop) { slideAway(); return true }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_MOVE && downY - e.rawY > slop) slideAway()
        return true
    }

    override fun onDetachedFromWindow() { stopTimer(); super.onDetachedFromWindow() }

    override fun render(s: Updates.State) {
        val latest = s.latest
        if (away && s.phase != Updates.Phase.Idle) away = false   // progress and failures stay in view
        val show = latest != null && !suppressed && Updates.bannerVisible(context, s, Updates.current(context))
        if (!show) {
            stopTimer()
            animate().cancel()
            visibility = View.GONE
            return
        }
        title.text = "Update ${latest!!.version} · ${DisplayNumbers.size(latest.apkSize)}"
        subtitle.visibility = if (s.phase == Updates.Phase.Idle) View.VISIBLE else View.GONE
        Form.enable(updateBtn, s.phase !is Updates.Phase.Downloading)
        progress.render(s)
        if (away) return
        if (visibility != View.VISIBLE) { visibility = View.VISIBLE; slideIn() }
        if (s.phase != Updates.Phase.Idle) stopTimer() else if (!timerArmed) restartTimer()
    }
}

/**
 * A screen's content plus the floating [UpdateBanner] on top of it ([com.husarp.ankigo.ui.AgActivity.setContentView]
 * wraps every AnkiGo screen in one). The banner overlays the content (top, wrap height); it doesn't move it.
 */
class UpdateOverlay(ctx: Context, content: View) : FrameLayout(ctx) {
    val banner = UpdateBanner(ctx)
    init {
        addView(content, LayoutParams(-1, -1))
        addView(banner, LayoutParams(-1, -2, Gravity.TOP))
    }
}

/** Settings → Updates: cards with the auto-check switch, version + CHECK NOW, and the update status. */
class UpdateSection(ctx: Context) : UpdatesView(ctx) {
    private val cur = Updates.current(ctx)
    private val checkBtn = pillButton(ctx, "CHECK NOW", false, 13f, 40, 14) { Updates.checkNow(ctx) }
    private lateinit var getBtn: TextView
    private val statusCard = Ui.card(ctx).apply { setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12)) }
    private val message = Ui.text(ctx, 14f, 800, R.color.ag_text).apply { setLineSpacing(0f, 1.1f) }
    private val sub = Form.body(ctx, "A newer version of AnkiGo is ready.", 12f, R.color.ag_sub0)
    private val btnRow = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        addView(pillButton(ctx, "GET UPDATE", true, 13f, 40, 12) { Updates.update(ctx) }.also { getBtn = it }, slot(0, ctx))
        addView(pillButton(ctx, "GITHUB", false, 13f, 40, 12) { open(ctx, Updates.RELEASES_PAGE) }, slot(8, ctx))
    }
    private val progress = UpdateProgress(ctx)
    init {
        val switchRow = Form.switchRow(ctx, "Check for updates", "When the app opens or you come back to it.", Updates.checkEnabled(ctx)) { Updates.setCheckEnabled(ctx, it) }
        val versionRow = Form.row(ctx, "Version $cur", null, checkBtn).apply {
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4))
        }
        addView(Form.cardOf(ctx, listOf(switchRow, versionRow)))
        statusCard.addView(message)
        statusCard.addView(sub, Form.lp(ctx, 2))
        statusCard.addView(btnRow, Form.lp(ctx, 8))
        statusCard.addView(progress, Form.lp(ctx, 4))
        addView(statusCard, Form.lp(ctx, 12))
        addView(Form.body(ctx, "Android asks you to confirm every update, and only an update signed with AnkiGo's own key installs.", 12f, R.color.ag_sub0).apply {
            setTypeface(Ui.font(ctx, 700))
            setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), 0)
        }, Form.lp(ctx, 14))
    }
    override fun render(s: Updates.State) {
        val avail = Updates.available(s, cur)
        val failed = s.phase is Updates.Phase.Failed
        Form.enable(checkBtn, !s.checking)
        message.text = if (avail) "Update ${s.latest!!.version} · ${DisplayNumbers.size(s.latest.apkSize)}" else s.message ?: ""
        message.visibility = if (avail || s.message != null) View.VISIBLE else View.GONE
        sub.visibility = if (avail && s.phase == Updates.Phase.Idle) View.VISIBLE else View.GONE
        btnRow.visibility = if (avail && !failed) View.VISIBLE else View.GONE
        Form.enable(getBtn, s.phase !is Updates.Phase.Downloading)
        progress.render(s)
        statusCard.visibility = if (message.visibility == View.VISIBLE || progress.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }
}

/** The Updates screen: app bar + the cards. Split out of onCreate so the design screenshots can build it. */
internal fun updatesScreen(ctx: Context): View {
    val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
    root.addView(Ui.appBar(ctx, "Updates", back = true))
    val scroll = ScrollView(ctx).apply {
        addView(UpdateSection(ctx).apply { setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 28)) })
    }
    root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    return root
}

/** The Updates settings screen, opened from the main screen. */
class UpdatesActivity : com.husarp.ankigo.ui.AgActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Updates"
        setContentView(updatesScreen(this))
    }
}
