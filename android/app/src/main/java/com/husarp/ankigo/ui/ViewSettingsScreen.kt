package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.study.PanelHeight
import com.husarp.ankigo.study.StudyLayout
import kotlin.math.max
import kotlin.math.min

// TICKET R15-F (PLAN 141): the battle's "View settings" as a full screen with a live battle preview.

/**
 * PLAN 141: the sizes of the "View settings" page (pure maths, no Android).
 * Portrait: the preview is fixed at the top, at most [PREVIEW_SHARE] of the window height; on the Study screen it is the panel's own
 * height for the picked Full / Strip / Mini ([PanelHeight.preset]), so what is shown is what the panel will be. Landscape: the preview
 * sits left, [LAND_SHARE] of the width and the whole height under the bar, and the rows scroll on the right.
 */
internal object ViewSettingsPlan {
    /** Portrait: the preview takes at most this share of the window's height. */
    const val PREVIEW_SHARE = 0.40f

    /** Landscape: the preview takes this share of the window's width. */
    const val LAND_SHARE = 0.46f

    /** The rows stay this wide at most (dp): on a tablet they would stretch across the whole window. */
    const val ROWS_MAX_DP = 600

    /** A window this much wider than tall puts the preview on the left; a near-square one (a split-screen half, 412 x 400 dp) stays stacked. */
    const val LAND_RATIO = 1.25f

    fun landscape(w: Int, h: Int): Boolean = w > h * LAND_RATIO

    /**
     * The preview's height in portrait, in dp: [panelMode] (Study screen: 0 Full, 1 Strip, 2 Mini) gives the panel's own height, at most
     * [PREVIEW_SHARE] of [windowHDp]; the Battle tab and the floating window (null) take that share.
     */
    fun portraitDp(windowHDp: Float, panelMode: Int?, tablet: Boolean): Float {
        val cap = windowHDp * PREVIEW_SHARE
        return if (panelMode == null) cap else min(PanelHeight.preset(tablet, panelMode).toFloat(), cap)
    }
}

/**
 * PLAN 141: "View settings" as a FULL-SCREEN page instead of the old bottom sheet. Top: the app's bar (back arrow + title). Under it a
 * live battle PREVIEW fixed at the top (a real [BattlePanelView] drawing the current battle from [GameRepo]: background, Pokémon, info
 * boxes, frame, bars, action bar), and under that the rows ([ViewSettingsSheet.rows]) in a scroll. In landscape the preview sits left.
 *
 * Every row writes the same preference Settings writes, and the preview follows live through the preference watcher every
 * [BattlePanelView] has (size, facing, info box style / look, bars, frame, stat cards, background pictures). The preview never plays
 * anything: it has no [EventPlayer] (so it takes no waiting fight events), the box over it takes every touch (no swipe-to-turn, no
 * bubbles, no buttons, no sounds) and TalkBack reads it as one picture.
 *
 * Panel height (Study screen only) is the one choice held back: picking it changes the PREVIEW (Full / Strip / Mini look), and the
 * host's setter is called once, with the final pick, when the page closes ([closed]) - and only if it differs from the mode the page
 * opened with. Built as a full-window [Dialog] ([show]), which works from an Activity and, as an overlay window, over other apps.
 */
internal class ViewSettingsScreen(
    private val ctx: Context,
    private val panelHeight: ViewSettingsSheet.PanelHeightPart? = null,
) {
    /** What the back arrow does besides [closed]: the dialog dismisses itself here. */
    var onClose: () -> Unit = {}

    /** The Panel height row's pick (0 Full, 1 Strip, 2 Mini); the host has not heard it before [closed]. */
    var heightMode: Int = panelHeight?.current()?.coerceIn(0, 2) ?: 1
        private set
    private val startMode = heightMode
    private var finished = false

    /** The preview: a battle panel with no [EventPlayer], so nothing ever plays in it. */
    val preview: BattlePanelView = BattlePanelView(ctx).apply {
        if (panelHeight != null) {
            setStudyLayout(heightMode != 0, heightMode == 2)
            restoreStudyCharge()
        }
        renderAll()
        // a preview is no entrance: the wild one stands there at once, not only after its background picture has loaded
        if (GameRepo.state?.battle != null) arena.setEnemyShown(true)
    }

    /** The rows' scroll. */
    val scroll: ScrollView

    private val box = PreviewBox(ctx)
    private val page: PageLayout

    /** The page's view (what the dialog shows; tests render it). */
    val root: View

    init {
        box.addView(preview, FrameLayout.LayoutParams(-1, -1))
        preview.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (GameRepo.state?.battle == null) {
            box.addView(Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply {
                text = "No battle yet"
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 7), Ui.dp(ctx, 14), Ui.dp(ctx, 7))
                background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12, ctx)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).also { it.topMargin = Ui.dp(ctx, 28); it.marginEnd = Ui.dp(ctx, 16) })   // where the wild one would stand
        }
        val rows = ViewSettingsSheet(ctx, panelHeight).rows { pickHeight(it) }.apply {
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 16, ctx)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
        }
        scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(CappedColumn(ctx, Ui.dp(ctx, ViewSettingsPlan.ROWS_MAX_DP)).apply {
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 20))
                addView(rows, LinearLayout.LayoutParams(-1, -2))
            }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        }
        val bar = Ui.appBar(ctx, "View settings", back = true, onBack = { close() })
        page = PageLayout(ctx, bar, box, View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, scroll) { w, h -> previewHeightPx(w, h) }
        page.setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        // the status / navigation bars and a camera cut-out are painted `ag_mantle` like the app's other screens: the page sits inside them
        root = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_mantle))
            addView(page, FrameLayout.LayoutParams(-1, -1))
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, ins ->
                val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                ins
            }
            // also when the window goes away without the dialog's own dismiss (the Activity closes under it, a turn of the screen)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { closed() }
            })
        }
    }

    /** The preview's height in px for a window of [w] x [h] px (portrait; landscape fills the height). */
    private fun previewHeightPx(w: Int, h: Int): Int {
        val d = ctx.resources.displayMetrics.density
        val tablet = ctx.resources.configuration.smallestScreenWidthDp >= StudyLayout.TABLET_DP
        val mode = if (panelHeight != null) heightMode else null
        return (ViewSettingsPlan.portraitDp(h / d, mode, tablet) * d + 0.5f).toInt()
    }

    private fun pickHeight(mode: Int) {
        if (mode == heightMode) return
        heightMode = mode
        preview.setStudyLayout(mode != 0, mode == 2)
        page.requestLayout()
    }

    /** The back arrow (or the dialog's Back): closes the page; the host hears the panel height here ([closed]). */
    fun close() {
        onClose()
        closed()
    }

    /**
     * The page is gone (dismissed, or its window removed): stops what the preview draws, and hands the Panel height pick to the host once
     * when it differs from the mode the page opened with. Safe to call again.
     */
    fun closed() {
        if (finished) return
        finished = true
        preview.cancelAnimations()
        val h = panelHeight
        if (h != null && heightMode != startMode) h.set(heightMode)
    }

    /** Opens the page in a full-window dialog; over other apps (no Activity) it is an overlay window. */
    fun show() {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        onClose = { dialog.dismiss() }
        dialog.setContentView(root)
        dialog.setOnDismissListener { closed() }
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Ui.c(ctx, R.color.ag_mantle)))
            WindowCompat.setDecorFitsSystemWindows(this, false)
            @Suppress("DEPRECATION")
            statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    /** The preview's holder: it takes every touch itself, so nothing in the panel (swipe, taps, buttons, bubbles) ever reacts; TalkBack says "Battle preview". */
    private class PreviewBox(ctx: Context) : FrameLayout(ctx) {
        init {
            contentDescription = "Battle preview"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }

    /** A column that is at most [maxPx] wide and centred (the rows on a tablet). */
    private class CappedColumn(ctx: Context, private val maxPx: Int) : LinearLayout(ctx) {
        init { orientation = VERTICAL }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val spec = if (MeasureSpec.getSize(widthMeasureSpec) > maxPx) MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY) else widthMeasureSpec
            super.onMeasure(spec, heightMeasureSpec)
        }
    }

    /**
     * bar / preview / rule / rows. Portrait: stacked, the preview a fixed height ([previewPx]); landscape: the bar on top, then the preview
     * on the left ([ViewSettingsPlan.LAND_SHARE] of the width, the whole height under the bar), a rule, and the rows on the right.
     */
    private class PageLayout(
        ctx: Context, private val bar: View, private val preview: View, private val rule: View, private val rows: View,
        private val previewPx: (w: Int, h: Int) -> Int,
    ) : ViewGroup(ctx) {
        private val line = Ui.dp(ctx, 1)

        init { for (v in listOf(bar, preview, rule, rows)) addView(v) }

        private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(max(0, px), MeasureSpec.EXACTLY)

        private var land = false
        private var previewW = 0
        private var previewH = 0
        private var barH = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val dm = resources.displayMetrics
            val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else dm.widthPixels }
            val h = MeasureSpec.getSize(heightMeasureSpec).let { if (it > 0) it else dm.heightPixels }
            bar.measure(exactly(w), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            barH = bar.measuredHeight
            val rest = max(0, h - barH)
            land = ViewSettingsPlan.landscape(w, h)
            if (land) {
                previewW = (w * ViewSettingsPlan.LAND_SHARE).toInt()
                previewH = rest
                preview.measure(exactly(previewW), exactly(previewH))
                rule.measure(exactly(line), exactly(rest))
                rows.measure(exactly(w - previewW - line), exactly(rest))
            } else {
                previewW = w
                previewH = min(previewPx(w, h), rest)
                preview.measure(exactly(w), exactly(previewH))
                rule.measure(exactly(w), exactly(line))
                rows.measure(exactly(w), exactly(rest - previewH - line))
            }
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l
            bar.layout(0, 0, w, barH)
            if (land) {
                preview.layout(0, barH, previewW, barH + previewH)
                rule.layout(previewW, barH, previewW + line, barH + previewH)
                rows.layout(previewW + line, barH, w, barH + previewH)
            } else {
                preview.layout(0, barH, w, barH + previewH)
                rule.layout(0, barH + previewH, w, barH + previewH + line)
                rows.layout(0, barH + previewH + line, w, b - t)
            }
        }
    }
}
