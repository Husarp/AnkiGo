package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.R

// Small form pieces shared by FirstRunActivity and SettingsActivity (T13). Look: docs/design/mockup.html (.sect, cards, chips).

/** Builders for buttons, section titles and settings rows. Everything is plain Views from [Ui]. */
internal object Form {
    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    fun lp(ctx: Context, top: Int, w: Int = ViewGroup.LayoutParams.MATCH_PARENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(ctx, top) }

    private fun ripple(ctx: Context, bg: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1) and 0x00FFFFFF or 0x55000000), bg, null)

    /** Filled mauve button (grey it out with [enable]). */
    fun primary(ctx: Context, label: String, onClick: () -> Unit): TextView =
        Ui.text(ctx, 14f, 800, R.color.ag_crust).apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 13), Ui.dp(ctx, 16), Ui.dp(ctx, 13))
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx))
            isClickable = true
            setOnClickListener { onClick() }
        }

    /**
     * Quiet button on `ag_mantle` (inside a card); [tone] colours the text (e.g. red for destructive ones). [onPage] = the button sits
     * on the page colour, not in a card: it is filled with `ag_s0` then, since `ag_mantle` is darker than the page in the dark palette.
     */
    fun secondary(ctx: Context, label: String, @ColorRes tone: Int = R.color.ag_text, onPage: Boolean = false, onClick: () -> Unit): TextView =
        Ui.text(ctx, 13f, 800, tone).apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11))
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, if (onPage) R.color.ag_s0 else R.color.ag_mantle), 11, ctx))
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** Greys a button out and stops taps (or brings it back). */
    fun enable(v: View, on: Boolean) {
        v.isEnabled = on
        v.alpha = if (on) 1f else 0.45f
    }

    /** Mockup `.sect`: small uppercase grey title, optional right-hand note. */
    fun sect(ctx: Context, title: String, right: String? = null): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 8))
        addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply {
            text = title.uppercase()
            letterSpacing = 0.06f
        }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, weight = 1f))
        if (right != null) addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = right })
    }

    fun body(ctx: Context, text: String, size: Number = 13, @ColorRes tone: Int = R.color.ag_sub1): TextView =
        Ui.text(ctx, size.toFloat(), 600, tone).apply {
            this.text = text
            setLineSpacing(0f, 1.15f)
        }

    /** One settings row: [title] (+ optional grey [sub]) on the left, [right] view (may be null) on the right. */
    fun row(ctx: Context, title: String, sub: String?, right: View?, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11))
            minimumHeight = Ui.dp(ctx, 52)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = title })
                if (sub != null) addView(body(ctx, sub, 12f, R.color.ag_sub0).apply { tag = "sub" }, lp(ctx, 2))
            }
            addView(col, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (right != null) addView(right, lp(ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = Ui.dp(ctx, 10) })
            if (onClick != null) {
                isClickable = true
                background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, ColorDrawable(0xFFFFFFFF.toInt()))
                setOnClickListener { onClick() }
            }
        }

    /** Replaces the grey sub text of a row built by [row]. */
    fun setSub(row: View, text: String) { (row.findViewWithTag<View>("sub") as? TextView)?.text = text }

    /** Thin line between rows of a card. */
    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(Ui.c(ctx, R.color.ag_mantle))
        layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1))
    }

    /** A card with [rows] separated by dividers. */
    fun cardOf(ctx: Context, rows: List<View>): LinearLayout = Ui.card(ctx).apply {
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(divider(ctx))
            addView(r)
        }
    }

    fun switchRow(ctx: Context, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = Switch(ctx).apply {
            isChecked = checked
            val on = Ui.c(ctx, R.color.ag_mauve)
            val off = Ui.c(ctx, R.color.ag_s2)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(states, intArrayOf(on, off))
            thumbTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return row(ctx, title, sub, sw, onClick = { sw.toggle() })
    }

    /** "- value +" control. Holding a button repeats. [format] turns the value into the label. */
    fun stepperRow(
        ctx: Context, title: String, sub: String?, value: Int, min: Int, max: Int, step: Int,
        format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit,
    ): LinearLayout {
        var v = value.coerceIn(min, max)
        val label = Ui.text(ctx, 14f, 800, R.color.ag_text).apply {
            text = format(v)
            gravity = Gravity.CENTER
            minWidth = Ui.dp(ctx, 46)
        }
        fun change(d: Int): Boolean {
            val n = (v + d).coerceIn(min, max)
            if (n == v) return false
            v = n
            label.text = format(v)
            onChange(v)
            return true
        }
        fun stepButton(sign: String, d: Int) = Ui.text(ctx, 18f, 900, R.color.ag_text).apply {
            text = sign
            gravity = Gravity.CENTER
            // 38 dp look inside a 48 x 48 dp touch area
            background = android.graphics.drawable.InsetDrawable(Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx), Ui.dp(ctx, 5))
            minWidth = Ui.dp(ctx, 48)
            minHeight = Ui.dp(ctx, 48)
            contentDescription = if (d > 0) "More" else "Less"
            attachRepeat { change(d * step) }
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(stepButton("−", -1))
            addView(label)
            addView(stepButton("+", 1))
        }
        return row(ctx, title, sub, box)
    }

    /** Tap = one step, hold = repeats faster and faster. [step] returns false when nothing changed (stops repeating). */
    private fun View.attachRepeat(step: () -> Boolean) {
        val h = Handler(Looper.getMainLooper())
        var delay = 350L
        val tick = object : Runnable {
            override fun run() {
                if (!step()) return
                delay = maxOf(40L, delay * 3 / 4)
                h.postDelayed(this, delay)
            }
        }
        isClickable = true
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    delay = 350L
                    if (step()) h.postDelayed(tick, delay)
                }
                MotionEvent.ACTION_UP -> { v.isPressed = false; h.removeCallbacks(tick); v.performClick() }
                MotionEvent.ACTION_CANCEL -> { v.isPressed = false; h.removeCallbacks(tick) }
            }
            true
        }
    }

    /** Small selectable pill (generation chips): mauve when on. 32 dp look inside a 48 dp touch height. */
    fun toggleChip(ctx: Context, label: String, on: Boolean, onClick: () -> Unit): TextView =
        Ui.text(ctx, 13f, 800, if (on) R.color.ag_crust else R.color.ag_text).apply {
            text = label
            isClickable = true
            setOnClickListener { onClick() }
            setChip(this, on)
        }

    /** Recolours a chip made by [toggleChip] after its state changed. */
    fun setChip(chip: TextView, on: Boolean) {
        val ctx = chip.context
        chip.setTextColor(Ui.c(ctx, if (on) R.color.ag_crust else R.color.ag_text))
        Ui.touchPill(chip, Ui.rounded(Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_mantle), 10, ctx), 32, 4)
    }
}

/** A progress label plus bar, for downloads. Hidden until [show] is called. */
internal class ProgressBlock(ctx: Context) : LinearLayout(ctx) {
    private val label = Form.body(ctx, "", 12f, R.color.ag_sub1)
    private val bar = BarView(ctx)

    init {
        orientation = VERTICAL
        addView(label)
        addView(bar, Form.lp(ctx, 6).also { it.height = Ui.dp(ctx, 8) })
    }

    fun show(fraction: Float, text: String) {
        label.text = text
        bar.set(fraction, Ui.c(context, R.color.ag_mauve), false)
    }
}

/**
 * A background job whose state survives the screen being recreated (rotation, split-screen resize): lives in an
 * object, the screen attaches [listener] while visible and re-reads the fields on every call.
 */
internal class BgJob {
    enum class State { IDLE, RUNNING, DONE, FAILED }

    @Volatile var state = State.IDLE
        private set
    @Volatile var fraction = 0f
        private set
    @Volatile var label = ""
        private set
    @Volatile var error: String? = null
        private set
    @Volatile var cancelled = false
        private set
    @Volatile var listener: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    val running get() = state == State.RUNNING

    fun begin(text: String) {
        cancelled = false; error = null; fraction = 0f; label = text; state = State.RUNNING
        ping()
    }

    fun progress(f: Float, text: String) { fraction = f.coerceIn(0f, 1f); label = text; ping() }
    fun done(text: String = "") { fraction = 1f; label = text; state = State.DONE; ping() }
    fun fail(message: String) { error = message; state = State.FAILED; ping() }
    fun cancel() { cancelled = true }
    /** Back to IDLE after the screen has shown a finished/failed result. */
    fun reset() { state = State.IDLE; error = null; cancelled = false; ping() }

    private fun ping() { main.post { listener?.invoke() } }
}
