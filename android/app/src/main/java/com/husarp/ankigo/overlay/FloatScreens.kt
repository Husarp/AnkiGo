package com.husarp.ankigo.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.Nav
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.Ui

/** The screens that can open in a floating window over other apps. */
enum class FloatPage { TEAM, DEX, SHOP, SETTINGS }

/**
 * Floating screens: Team, Pokédex, Shop and Settings in one movable, resizable card over other apps (AnkiDroid),
 * with a tab row to switch screens, "open full screen" and close. Needs the overlay permission ([Overlay.canDraw]).
 * Only the current page is kept alive; switching rebuilds the next one (cheap).
 */
object FloatScreens {
    private class Tab(val page: FloatPage, @DrawableRes val icon: Int, val label: String, val dest: Dest)

    private val tabs = listOf(
        Tab(FloatPage.TEAM, R.drawable.ic_team, "Pokémon", Dest.TEAM),
        Tab(FloatPage.DEX, R.drawable.ic_dex, "Pokédex", Dest.DEX),
        Tab(FloatPage.SHOP, R.drawable.ic_shop, "Shop", Dest.SHOP),
        Tab(FloatPage.SETTINGS, R.drawable.ic_settings, "Settings", Dest.SETTINGS),
    )

    private var window: FloatWindow? = null
    private var page: GamePage? = null
    private var current: FloatPage? = null
    private val tabViews = HashMap<FloatPage, ImageView>()

    private val host = object : PageHost {
        override val hostActivity: Activity? get() = null
        override fun closePage() = close()
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) {
            val ctx = window?.context ?: throw IllegalStateException("floating card is closed")
            ResultActivity.launch(ctx, intent, onResult)
        }
    }

    /**
     * Opens [page] in the floating card (creates the card, or switches it to [page]). Main thread. False if it can't (no permission).
     * [showUid] (Team only): also opens that Pokémon's details (tap on your Pokémon in a battle).
     */
    fun open(ctx: Context, page: FloatPage, showUid: String? = null): Boolean {
        if (!Overlay.canDraw(ctx)) return false
        if (window?.isShowing == false) teardown() // the window removed itself (e.g. permission revoked): start clean
        val w = window ?: create(ctx).also { window = it }
        if (!w.show()) {
            teardown()
            return false
        }
        showPage(w, page, showUid)
        return true
    }

    /** Closes the card if it is open. Main thread. */
    fun close() {
        window?.close()
    }

    /** True while the card is showing. */
    val isOpen: Boolean get() = window?.isShowing == true

    // ---------- internals ----------

    private fun create(ctx: Context): FloatWindow {
        val w = FloatWindow(ctx, "screens", 340, 480, 260, 300) { teardown() }
        val row = LinearLayout(w.context).apply { gravity = Gravity.CENTER_VERTICAL }
        val size = Ui.dp(w.context, 32)
        val pad = Ui.dp(w.context, 7)
        tabViews.clear()
        for (t in tabs) {
            val v = ImageView(w.context).apply {
                setImageResource(t.icon)
                setPadding(pad, pad, pad, pad)
                contentDescription = t.label
                setOnClickListener { window?.let { showPage(it, t.page) } }
            }
            tabViews[t.page] = v
            row.addView(v, LinearLayout.LayoutParams(size, size))
        }
        w.addHeaderView(row)
        w.addHeaderButton(R.drawable.ic_open, "Open full screen") { openFullScreen(w) }
        return w
    }

    private fun showPage(w: FloatWindow, which: FloatPage, showUid: String? = null) {
        if (current == which && page != null) {
            if (showUid != null) (page as? TeamPage)?.showDetailsOnce(showUid)
            return
        }
        discardPage()
        val p = try {
            build(which, w.context).also { if (showUid != null) (it as? TeamPage)?.showDetailsOnce(showUid) }
        } catch (e: Exception) {
            Report.fail("floatscreens", "build $which", e)
            close()
            return
        }
        page = p
        current = which
        w.setContent(p.root)
        for ((k, v) in tabViews) {
            val tint = Ui.c(w.context, if (k == which) R.color.ag_mauve else R.color.ag_sub0)
            v.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
        w.setTitle("")
        p.onShow()
    }

    private fun build(which: FloatPage, ctx: Context): GamePage = when (which) {
        FloatPage.TEAM -> TeamPage(ctx, host)
        FloatPage.DEX -> TeamPage(ctx, host, TeamSection.DEX) // the Pokédex section of the Team tab
        FloatPage.SHOP -> ShopPage(ctx, host)
        FloatPage.SETTINGS -> SettingsPage(ctx, host)
    }

    private fun openFullScreen(w: FloatWindow) {
        val which = current ?: return
        val t = tabs.first { it.page == which }
        try {
            w.context.startActivity(Nav.intent(w.context, t.dest).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Report.fail("floatscreens", "open full screen $which", e)
            return
        }
        close()
    }

    private fun discardPage() {
        val p = page ?: return
        page = null
        current = null
        p.onHide()
        p.onGone()
    }

    /** The card is gone (closed or failed): drop the page and the window. */
    private fun teardown() {
        discardPage()
        val w = window
        window = null
        tabViews.clear()
        w?.hide()
    }
}
