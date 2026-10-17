package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.Ui

/** One render size for the pages: a harness [Size], or the TALL window (412 x 2200 dp) that shows a whole scrolling page. */
class Variant(val label: String, val wDp: Int, val hDp: Int) {
    fun device(theme: Theme) = Shots.device(Size.PHONE, theme).copy(screenWidth = wDp * 2, screenHeight = hDp * 2)
}

object Variants {
    val PHONE = Variant("phone", Size.PHONE.wDp, Size.PHONE.hDp)
    val SPLIT = Variant("split", Size.SPLIT.wDp, Size.SPLIT.hDp)
    val TALL = Variant("tall", 412, 2200)
    /** Settings is longer than 2200 dp: same width, taller window (still labelled tall). */
    val TALL_SETTINGS = Variant("tall", 412, 3600)
}

/**
 * Like [shootWith] but with [Variant]s. [build] gets the render context (storage under build/) after the fake game is
 * installed; the returned View fills the frame.
 */
fun Paparazzi.shootPage(
    name: String,
    variants: List<Variant> = listOf(Variants.PHONE, Variants.SPLIT),
    scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME,
    withSprites: Boolean = true,
    themes: List<Theme> = Theme.entries,
    build: (Context) -> View,
) {
    for (v in variants) for (theme in themes) {
        unsafeUpdateConfig(deviceConfig = v.device(theme))
        FakeGame.scenario = scenario
        FakeGame.withSprites = withSprites
        val ctx = Shots.files(context)
        FakeGame.install(ctx)
        val view = build(ctx)
        val frame = FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }
        snapshot(frame, "$name-${v.label}-${theme.name.lowercase()}", 1000)
    }
}

/** Builds a dialog's window content without showing it, and puts it on a scrim like a real dialog (the app's [AppDialog]). */
object DialogShots {
    /** Runs [open] with [Pickers.interceptApp] set and returns the dialog it tried to show (null when none was shown). */
    fun capture(open: () -> Unit): AppDialog? = AppDialogShots.capture(open)

    /** The scene: scrim + the dialog card centred. [widthDp] overrides the card width. */
    fun scene(ctx: Context, d: AppDialog, widthDp: Int? = null): View {
        if (widthDp == null) return AppDialogShots.scene(ctx, d)
        val scene = FrameLayout(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }
        (d.content().parent as? android.view.ViewGroup)?.removeView(d.content())
        scene.addView(d.content(), FrameLayout.LayoutParams(Ui.dp(ctx, widthDp), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).also {
            val m = Ui.dp(ctx, 24); it.topMargin = m; it.bottomMargin = m
        })
        return scene
    }
}

/** A page-less ScrollView on the base colour, for plain Activities whose content is a bare View tree. */
fun scrollOnBase(ctx: Context, child: View): View = ScrollView(ctx).apply {
    setBackgroundColor(Ui.c(ctx, R.color.ag_base)); addView(child)
}

/**
 * The context of the app's plain Activities (Diagnostics, Updates): the application theme (AppTheme, Material Light with
 * a dark action bar, no night variant), the fake package manager, and no network.
 */
fun appThemed(base: Context, ankiVersion: String? = "2.24.0", granted: Boolean = true): Context =
    android.view.ContextThemeWrapper(ShotCtx(base, ankiVersion, granted = granted), R.style.AppTheme)

/** A plain Activity's window as the system draws it: dark title bar with [title], content below on the window background. */
fun legacyWindow(ctx: Context, title: String, content: View): View {
    val bg = ctx.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground)).use { it.getDrawable(0) }
    return android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        background = bg
        addView(android.widget.TextView(ctx).apply {
            text = title; textSize = 20f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(33, 33, 33))
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), 0)
        }, android.widget.LinearLayout.LayoutParams(-1, Ui.dp(ctx, 56)))
        addView(content, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
    }
}
