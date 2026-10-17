package com.husarp.ankigo.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.Ui

/**
 * A card window over other apps (TYPE_APPLICATION_OVERLAY): header (drag to move; title; extra buttons; close X),
 * content, resize grip bottom-right. Remembers bounds per [key] (AppPrefs.bounds) and keeps itself on screen.
 * Main thread only. Needs the overlay permission ([Overlay.canDraw]); [show] returns false without it.
 */
class FloatWindow(
    ctx: Context,
    val key: String,
    private val defaultWdp: Int,
    private val defaultHdp: Int,
    private val minWdp: Int,
    private val minHdp: Int,
    private val onClose: () -> Unit,
) {
    /** Themed application context: build the content with it. */
    val context: Context = ContextThemeWrapper(com.husarp.ankigo.ui.AppLook.wrap(ctx.applicationContext), R.style.GameTheme)

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val minW = Ui.dp(context, minWdp)
    private val minH = Ui.dp(context, minHdp)
    /** Transparent margin round the card so its shadow isn't cut off by the window edge. */
    private val pad = Ui.dp(context, 4)

    private val titleView = Ui.text(context, 13f, 900, R.color.ag_text).apply {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 6), 0)
    }
    private val viewsBox = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val buttonsBox = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val closeButton: ImageView
    private val header: LinearLayout
    private val contentBox = FrameLayout(context)
    private val root: Root
    private var lp: WindowManager.LayoutParams? = null
    private var focusable = false

    /** Smaller header (battle window); default false. */
    var compactHeader: Boolean = false
        set(v) {
            field = v
            applyHeaderSize()
        }

    val isShowing: Boolean get() = lp != null

    init {
        closeButton = makeButton(R.drawable.ic_close, "Close", R.color.ag_red) { close() }
        header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.c(context, R.color.ag_mantle))
            addView(titleView, LinearLayout.LayoutParams(-2, -2))
            addView(viewsBox, LinearLayout.LayoutParams(-2, -2))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(buttonsBox, LinearLayout.LayoutParams(-2, -2))
            addView(closeButton)
            setOnTouchListener(dragListener())
        }
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Ui.c(context, R.color.ag_card))
                cornerRadius = Ui.dp(context, 16f)
                setStroke(Ui.dp(context, 1), Ui.c(context, R.color.ag_s1))
            }
            clipToOutline = true
            elevation = Ui.dp(context, 4f)
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(header, LinearLayout.LayoutParams(-1, 0))
                addView(contentBox, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            addView(col, FrameLayout.LayoutParams(-1, -1))
            val gripSize = Ui.dp(context, 28)
            val grip = ImageView(context).apply {
                setImageResource(R.drawable.ic_resize)
                setColorFilter(Ui.c(context, R.color.ag_sub1), PorterDuff.Mode.SRC_IN)
                setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4))
                contentDescription = "Resize"
                setOnTouchListener(gripListener())
            }
            addView(grip, FrameLayout.LayoutParams(gripSize, gripSize, Gravity.BOTTOM or Gravity.END))
        }
        root = Root(context).apply {
            setPadding(pad, pad, pad, pad)
            addView(card, FrameLayout.LayoutParams(-1, -1))
        }
        com.husarp.ankigo.ui.AppToast.attach(root) // messages of the floating screens (Ui.toast without an Activity) show inside this window
        applyHeaderSize()
    }

    fun setTitle(text: String) { titleView.text = text }

    fun setContent(view: View) {
        contentBox.removeAllViews()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        contentBox.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    /** Adds an icon button to the header (left of the close X); returns it so callers can retint/swap its icon. */
    fun addHeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit): ImageView {
        val b = makeButton(icon, description, R.color.ag_sub1, onClick)
        buttonsBox.addView(b)
        applyHeaderSize()
        return b
    }

    /** Adds any view to the header after the title (tabs). */
    fun addHeaderView(v: View) {
        viewsBox.addView(v)
    }

    /** Adds the window; false without permission or on failure; a no-op (true) when already showing. */
    fun show(): Boolean {
        if (isShowing) return true
        if (!Overlay.canDraw(context)) return false
        val (sw, sh) = screenSize()
        val saved = AppPrefs.bounds(context, key)
        val box = if (saved != null) {
            FloatGeometry.clamp(FloatGeometry.Box(saved[0], saved[1], saved[2], saved[3]), sw, sh, minW, minH)
        } else {
            FloatGeometry.default(sw, sh, Ui.dp(context, defaultWdp), Ui.dp(context, defaultHdp), minW, minH)
        }
        val params = WindowManager.LayoutParams(
            box.w, box.h,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = box.x
            y = box.y
            title = "AnkiGo"
        }
        return try {
            focusable = false
            wm.addView(root, params)
            lp = params
            true
        } catch (e: Exception) {
            Report.fail("floatwin", "add $key", e)
            false
        }
    }

    /** Removes the window but keeps this object for [show] again. */
    fun hide() {
        if (lp == null) return
        lp = null
        try {
            wm.removeViewImmediate(root)
        } catch (e: Exception) {
            Report.fail("floatwin", "remove $key", e)
        }
    }

    /** [hide] and tell the owner. */
    fun close() {
        hide()
        onClose()
    }

    // ---------- internals ----------

    private fun makeButton(@DrawableRes icon: Int, description: String, tint: Int, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(context, tint), PorterDuff.Mode.SRC_IN)
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { onClick() }
        }

    /** Header height 36 dp (compact 26 dp); square 18 dp icon buttons sized to it. */
    private fun applyHeaderSize() {
        val h = Ui.dp(context, if (compactHeader) 26 else 36)
        val p = (h - Ui.dp(context, 18)) / 2
        header.layoutParams = LinearLayout.LayoutParams(-1, h)
        size(closeButton, h, p)
        for (i in 0 until buttonsBox.childCount) size(buttonsBox.getChildAt(i), h, p)
    }

    private fun size(v: View, h: Int, p: Int) {
        v.layoutParams = LinearLayout.LayoutParams(h, h)
        v.setPadding(p, p, p, p)
    }

    /**
     * Whole-display size in px (overlay windows are laid out against the full screen, see [BASE_FLAGS]).
     * Uses maximumWindowMetrics: currentWindowMetrics would be only the app's half in split screen.
     */
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun current(): FloatGeometry.Box? = lp?.let { FloatGeometry.Box(it.x, it.y, it.width, it.height) }

    /** Applies [b] to the live window; a failure (permission revoked, window gone) hides it instead of crashing. */
    private fun apply(b: FloatGeometry.Box) {
        val p = lp ?: return
        p.x = b.x; p.y = b.y; p.width = b.w; p.height = b.h
        update()
    }

    private fun update() {
        val p = lp ?: return
        try {
            wm.updateViewLayout(root, p)
        } catch (e: Exception) {
            Report.fail("floatwin", "update $key", e)
            hide()
        }
    }

    private fun reclamp() {
        val b = current() ?: return
        val (sw, sh) = screenSize()
        val c = FloatGeometry.clamp(b, sw, sh, minW, minH)
        if (c != b) apply(c)
    }

    private fun save() {
        val b = current() ?: return
        AppPrefs.setBounds(context, key, b.x, b.y, b.w, b.h)
    }

    /** Typing needs a focusable window; AnkiDroid gets its keys back when the touch lands outside. */
    private fun setFocusable(on: Boolean) {
        val p = lp ?: return
        if (focusable == on) return
        focusable = on
        p.flags = if (on) BASE_FLAGS and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() else BASE_FLAGS
        update()
    }

    private fun dragListener() = object : View.OnTouchListener {
        private var start: FloatGeometry.Box? = null
        private var rx = 0f
        private var ry = 0f
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { start = current(); rx = e.rawX; ry = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    val s = start ?: return true
                    val (sw, sh) = screenSize()
                    apply(FloatGeometry.move(s, (e.rawX - rx).toInt(), (e.rawY - ry).toInt(), sw, sh, minW, minH))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (start != null) save(); start = null }
            }
            return true
        }
    }

    private fun gripListener() = object : View.OnTouchListener {
        private var start: FloatGeometry.Box? = null
        private var rx = 0f
        private var ry = 0f
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { start = current(); rx = e.rawX; ry = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    val s = start ?: return true
                    val (sw, sh) = screenSize()
                    apply(FloatGeometry.resize(s, (e.rawX - rx).toInt(), (e.rawY - ry).toInt(), sw, sh, minW, minH))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (start != null) save(); start = null }
            }
            return true
        }
    }

    /** Window root: focus handling for the keyboard, Back key, and re-clamping after rotation / split-screen resize. */
    private inner class Root(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> setFocusable(true)
                MotionEvent.ACTION_OUTSIDE -> setFocusable(false)
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) close()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            post { reclamp() }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            post { reclamp() }
        }
    }

    private companion object {
        /**
         * Touches outside go to the app below. LAYOUT_IN_SCREEN makes x/y relative to the whole display (what the
         * clamping assumes); HARDWARE_ACCELERATED is needed for the shadow on a non-activity window.
         */
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}
