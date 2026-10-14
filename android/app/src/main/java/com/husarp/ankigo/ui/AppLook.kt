package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import com.husarp.ankigo.R
import com.husarp.ankigo.update.UpdateOverlay
import com.husarp.ankigo.update.Updates

/** Accent colour choices (Settings → Look): each is one of the palette's own colours, so light and dark both fit. */
enum class Accent(val label: String, @ColorRes val res: Int) {
    MAUVE("Mauve", R.color.ag_mauve),
    BLUE("Blue", R.color.ag_blue),
    TEAL("Teal", R.color.ag_teal),
    GREEN("Green", R.color.ag_green),
    PEACH("Peach", R.color.ag_peach),
    PINK("Pink", R.color.ag_pink),
}

/**
 * The app's look settings: theme (System / Light / Dark), accent colour and interface size. Theme and size are applied
 * to each screen's resources before it builds anything ([wrap], from [AgActivity] and the floating windows); the accent
 * replaces `ag_mauve` wherever colours go through [Ui.c]. A change bumps [generation]: every [AgActivity] that was built
 * with an older look recreates itself when it comes back to the front.
 */
object AppLook {
    /** Interface sizes offered in Settings, in percent. */
    val SCALES = listOf(85, 90, 100, 110, 120, 130)

    @Volatile var generation = 0
        private set

    @Volatile private var accentCache: Accent? = null

    /** The colour resource that stands in for `ag_mauve` (the chosen accent). */
    @ColorRes
    fun accentRes(ctx: Context): Int = (accentCache ?: AppPrefs.accent(ctx).also { accentCache = it }).res

    fun setTheme(ctx: Context, mode: ThemeMode) {
        AppPrefs.putLook(ctx, "theme", mode.name)
        generation++
    }

    fun setAccent(ctx: Context, accent: Accent) {
        AppPrefs.putLook(ctx, "accent", accent.name)
        accentCache = accent
        generation++
    }

    fun setScale(ctx: Context, percent: Int) {
        AppPrefs.putScale(ctx, percent)
        generation++
    }

    /**
     * [base] with the chosen theme and interface size, or [base] itself when both are the defaults. The override holds
     * only those two fields (uiMode night bits, densityDpi), so the system's font size and the window size still change
     * as usual (split-screen resize keeps working).
     */
    fun wrap(base: Context): Context {
        val theme = AppPrefs.theme(base)
        val scale = AppPrefs.uiScale(base)
        if (theme == ThemeMode.SYSTEM && scale == 100) return base
        val now = base.resources.configuration
        val cfg = Configuration()
        cfg.fontScale = 0f // 0 = undefined: keep the system font size
        if (theme != ThemeMode.SYSTEM) {
            val night = if (theme == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            cfg.uiMode = (now.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        if (scale != 100) cfg.densityDpi = (now.densityDpi * scale + 50) / 100
        return base.createConfigurationContext(cfg)
    }
}

/**
 * Base of every AnkiGo screen: applies [AppLook] (theme, size) before the screen is built and recreates the screen
 * when the look changed while it was in the background.
 */
abstract class AgActivity : Activity() {
    private var lookGeneration = -1

    /** False on trampolines and the first-run screen: no floating update banner there. */
    open val showsUpdateBanner: Boolean = true
    /** The screen's content + floating update banner (null when [showsUpdateBanner] is off or before setContentView). */
    protected var updateOverlay: UpdateOverlay? = null
        private set

    private fun overlay(view: View): View =
        if (showsUpdateBanner) UpdateOverlay(this, view).also { updateOverlay = it } else view

    override fun setContentView(view: View) = super.setContentView(overlay(view))
    override fun setContentView(view: View, params: ViewGroup.LayoutParams) = super.setContentView(overlay(view), params)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLook.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lookGeneration = AppLook.generation
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)   // every screen: check at launch and every return, carry on a waiting install
        updateOverlay?.banner?.resetAway()
        if (lookGeneration != AppLook.generation) {
            lookGeneration = AppLook.generation
            recreate()
        }
    }

    override fun onPause() {
        Updates.onPause(this)
        super.onPause()
    }

    /** Picture-in-picture is too small for the banner. */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        updateOverlay?.banner?.suppressed = isInPictureInPictureMode
    }
}
