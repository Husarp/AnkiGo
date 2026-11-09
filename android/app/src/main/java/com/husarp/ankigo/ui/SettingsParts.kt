package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
            setTextColor(AppDialogRules.onFill(Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_crust), TypeInk::contrast))
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 12, ctx))
            isClickable = true
            setOnClickListener { UiMotion.press(this); onClick() }
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
            background = ripple(ctx, Ui.rounded(Ui.c(ctx, if (onPage) R.color.ag_s0 else R.color.ag_mantle), 12, ctx))
            isClickable = true
            setOnClickListener { UiMotion.press(this); onClick() }
        }

    /** Greys a button out and stops taps (or brings it back). */
    fun enable(v: View, on: Boolean) {
        v.isEnabled = on
        v.alpha = if (on) 1f else 0.45f
    }

    internal fun sectionIcon(title: String): Int = when (title) {
        "Play" -> R.drawable.ic_game
        "Sound & data", "Card pictures & sound" -> R.drawable.ic_sound
        "Help & app" -> R.drawable.ic_help
        "Advanced", "Fix a problem" -> R.drawable.ic_bug
        "When you beat a wild Pokémon", "Battle", "Battle panel", "Battle look" -> R.drawable.ic_swords
        "Always catch" -> R.drawable.ic_ball
        "Difficulty" -> R.drawable.ic_practice_alert
        "Pokémon", "Box", "Pokémon details" -> R.drawable.ic_dex
        "Theme & colours", "Bright scenes", "Dark scenes", "Colours & background" -> R.drawable.ic_palette
        "Types & notifications", "Study & game reminders" -> R.drawable.ic_bell
        "Text & size", "Pokémon size", "Panel height" -> R.drawable.ic_resize
        "Study cards & decks", "Study & accessibility" -> R.drawable.ic_study
        "Timing", "Time", "Remind every", "Remind at", "From", "To", "Not more often than" -> R.drawable.ic_clock
        "Answer controls", "Info boxes & bars", "Arena & layout", "Buddy layout & movement" -> R.drawable.ic_layout
        "When you leave AnkiGo" -> R.drawable.ic_open
        "Pop out opens", "Small battle windows", "Floating screens" -> R.drawable.ic_pip
        "Storage used" -> R.drawable.ic_save
        "Downloads & cache" -> R.drawable.ic_download
        "Facing" -> R.drawable.ic_turn
        "Remind when at least", "Count", "At most" -> R.drawable.ic_stats
        "Decks" -> R.drawable.ic_book
        "Goal" -> R.drawable.ic_ach_target
        "Quiet hours" -> R.drawable.ic_moon
        "Playback & test" -> R.drawable.ic_play
        else -> R.drawable.ic_star
    }

    /** Mockup `.sect`: small uppercase grey title, optional right-hand note. */
    fun sect(ctx: Context, title: String, right: String? = null): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 8))
        addView(android.widget.ImageView(ctx).apply {
            setImageResource(sectionIcon(title)); setColorFilter(Ui.c(ctx, R.color.ag_mauve))
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)).also { it.marginEnd = Ui.dp(ctx, 6) })
        addView(Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply {
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
            tag = ROW_TAG
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

    /** Tag of the root of a row built by [row] or [stackRow]; [rowsIn] and [titleOf] find rows by it. */
    const val ROW_TAG = "row"

    /** Every row built by [row] / [stackRow] inside [root] (including [root] itself), top to bottom. */
    fun rowsIn(root: View): List<View> {
        val out = ArrayList<View>()
        fun walk(v: View) {
            if (v.tag == ROW_TAG) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    /** The title of a row built by [row] / [stackRow] (its first text), or null for anything else. */
    fun titleOf(row: View): String? {
        fun firstText(v: View): TextView? {
            if (v is TextView) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) firstText(v.getChildAt(i))?.let { return it }
            return null
        }
        return if (row.tag == ROW_TAG) firstText(row)?.text?.toString() else null
    }

    /** Sets the grey line under the title of a row built by [row] / [stackRow]: replaces the one it has, or adds it. */
    fun setExplain(row: View, text: String) {
        val ctx = row.context
        (row.findViewWithTag<View>("sub") as? TextView)?.let { it.text = text; return }
        val sub = body(ctx, text, 12f, R.color.ag_sub0).apply { tag = "sub" }
        val box = row as? LinearLayout ?: return
        if (box.orientation == LinearLayout.VERTICAL) box.addView(sub, 1, lp(ctx, 2)) // stackRow: under the head, above the control
        else (box.getChildAt(0) as? LinearLayout)?.addView(sub, lp(ctx, 2))
    }

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
        val sw = MotionSwitch(ctx).apply {
            isChecked = checked
            Ui.tintSwitch(this)
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        return row(ctx, title, sub, sw, onClick = { sw.toggle() })
    }

    /** A settings row with its [control] under the title (and grey [sub]) instead of beside it: segmented pills, swatches. */
    fun stackRow(ctx: Context, title: String, sub: String?, control: View, right: View? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 6))
            tag = ROW_TAG
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = title }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (right != null) addView(right)
            }
            addView(head)
            if (sub != null) addView(body(ctx, sub, 12f, R.color.ag_sub0).apply { tag = "sub" }, lp(ctx, 2))
            addView(control, lp(ctx, 2))
        }

    /** Equal-width, centred choices; every segment grows to the tallest wrapped label. */
    fun segmented(ctx: Context, labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout =
        LookChoosers.segmented(ctx, labels, selected, onPick) as LinearLayout

    /** Round colour swatches (the palette's own colours, not the chosen accent); the picked one has a ring and a tick. */
    fun swatches(ctx: Context, colors: List<Pair<String, Int>>, selected: Int, onPick: (Int) -> Unit): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            colors.forEachIndexed { i, (label, color) ->
                val on = i == selected
                val cell = FrameLayout(ctx).apply {
                    contentDescription = if (on) "$label, selected" else label
                    isClickable = true
                    setOnClickListener { onPick(i) }
                }
                if (on) cell.addView(View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0)
                        setStroke(Ui.dp(ctx, 2), Ui.c(ctx, R.color.ag_text))
                    }
                }, FrameLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 40), Gravity.CENTER))
                cell.addView(View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                }, FrameLayout.LayoutParams(Ui.dp(ctx, 32), Ui.dp(ctx, 32), Gravity.CENTER))
                if (on || label == Accent.MULTICOLOR.label) cell.addView(android.widget.ImageView(ctx).apply {
                    setImageResource(if (on) R.drawable.ic_check else R.drawable.ic_palette)
                    setColorFilter(if (label == Accent.MULTICOLOR.label) Multicolor.onFill(color) else Ui.c(ctx, R.color.ag_crust), android.graphics.PorterDuff.Mode.SRC_IN)
                }, FrameLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Gravity.CENTER))
                addView(cell, lp(0, Ui.dp(ctx, 48), 1f))
            }
        }

    /** Small coloured caps title on top of a card, with a tinted icon (mockup `.s39-ct`). */
    fun cardTitle(ctx: Context, @androidx.annotation.DrawableRes icon: Int, title: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 2))
        addView(android.widget.ImageView(ctx).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(ctx, R.color.ag_mauve), android.graphics.PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)).also { it.marginEnd = Ui.dp(ctx, 7) })
        addView(Ui.text(ctx, 12.5f, 900, R.color.ag_mauve).apply {
            text = title.uppercase()
            letterSpacing = 0.04f
        })
    }

    /** A 60 × 64 dp bare play target beside the independent group switch. */
    fun soundRow(ctx: Context, title: String, sub: String?, checked: Boolean, onPlay: (((Boolean) -> Unit) -> Unit)?, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = MotionSwitch(ctx).apply {
            isChecked = checked
            Ui.tintSwitch(this)
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (onPlay != null) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = "Hear $title"
                val icon = android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_play)
                    setColorFilter(Ui.c(ctx, R.color.ag_mauve), android.graphics.PorterDuff.Mode.SRC_IN)
                }
                val caption = Ui.text(ctx, 10f, 800, R.color.ag_mauve).apply { text = "Tap to play" }
                addView(icon, LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
                addView(caption)
                var generation = 0
                setOnClickListener {
                    val request = ++generation
                    isActivated = false; caption.text = "Tap to play"
                    caption.setTextColor(Ui.c(ctx, R.color.ag_mauve)); icon.visibility = View.VISIBLE; background = null
                    contentDescription = "Hear $title"
                    onPlay { playing ->
                        if (request == generation) {
                            isActivated = playing
                            caption.text = if (playing) "♪ Playing…" else "Tap to play"
                            caption.setTextColor(Ui.c(ctx, if (playing) R.color.ag_green else R.color.ag_mauve))
                            icon.visibility = if (playing) View.GONE else View.VISIBLE
                            background = if (playing) Ui.rounded(android.graphics.Color.TRANSPARENT, 0, ctx).apply {
                                setStroke(Ui.dp(ctx, 2), Ui.c(ctx, R.color.ag_green))
                            } else null
                            contentDescription = if (playing) "Playing $title" else "Hear $title"
                        }
                    }
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        generation++; isActivated = false; caption.text = "Tap to play"
                        caption.setTextColor(Ui.c(ctx, R.color.ag_mauve)); icon.visibility = View.VISIBLE; background = null
                        contentDescription = "Hear $title"
                    }
                })
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 60), Ui.dp(ctx, 64)))
            addView(sw)
        }
        return row(ctx, title, sub, right, onClick = { sw.toggle() })
    }

    /**
     * A volume slider row: [title] with the value ("70%") at the right and a drag-or-tap slider under it, 0..100 in steps of
     * [SoundUiRules.STEP]. [onChange] gets every new value while the thumb moves. The slider is a plain SeekBar in the app colours,
     * so TalkBack reads and adjusts it.
     */
    fun sliderRow(ctx: Context, title: String, valuePct: Int, onChange: (Int) -> Unit): LinearLayout {
        val label = Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = "${SoundUiRules.snapVolume(valuePct)}%" }
        val bar = android.widget.SeekBar(ctx).apply {
            max = 100 / SoundUiRules.STEP
            progress = SoundUiRules.snapVolume(valuePct) / SoundUiRules.STEP
            progressTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            progressBackgroundTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1))
            thumbTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            splitTrack = false
            minimumHeight = Ui.dp(ctx, 40)
            contentDescription = title
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    val v = progress * SoundUiRules.STEP
                    label.text = "$v%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }
        return stackRow(ctx, title, null, bar, label)
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
            background = null
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
            setOnClickListener { UiMotion.pop(this); onClick() }
            setChip(this, on)
        }

    /** Recolours a chip made by [toggleChip] after its state changed. */
    fun setChip(chip: TextView, on: Boolean) {
        val ctx = chip.context
        val fill = Multicolor.color(ctx, Multicolor.place(chip.text.toString()))
        chip.setTextColor(if (on && Multicolor.enabled(ctx)) Multicolor.onFill(fill)
            else Ui.c(ctx, if (on) R.color.ag_crust else R.color.ag_text))
        Ui.touchPill(chip, Ui.rounded(if (on) fill else Ui.c(ctx, R.color.ag_mantle), 12, ctx), 32, 4)
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
        bar.setMeaning(BarMeaning.PROGRESS, fraction, false)
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
    private val gate = UpdateGate(UPDATE_GAP_MS)

    val running get() = state == State.RUNNING

    fun begin(text: String) {
        cancelled = false; error = null; fraction = 0f; label = text; state = State.RUNNING
        ping()
    }

    fun progress(f: Float, text: String) { fraction = f.coerceIn(0f, 1f); label = text; ping(urgent = false) }
    fun done(text: String = "") { fraction = 1f; label = text; state = State.DONE; ping() }
    fun fail(message: String) { error = message; state = State.FAILED; ping() }
    fun cancel() { cancelled = true }
    /** Back to IDLE after the screen has shown a finished/failed result. */
    fun reset() { state = State.IDLE; error = null; cancelled = false; ping() }

    /**
     * Tells the screen to re-read the fields. At most one message is waiting at any time, and progress ticks are
     * spaced [UPDATE_GAP_MS] apart, so workers finishing hundreds of files a second can't flood the main thread.
     */
    private fun ping(urgent: Boolean = true) {
        val delay = gate.request(SystemClock.uptimeMillis(), urgent)
        if (delay < 0) return
        main.postDelayed({
            gate.fired(SystemClock.uptimeMillis())
            listener?.invoke()
        }, delay)
    }

    companion object { const val UPDATE_GAP_MS = 100L }
}

/**
 * Decides when a screen update is posted: one at a time, and (unless urgent) not sooner than [minGapMs] after the last
 * one ran. Pure, so it can be tested without a Looper.
 */
internal class UpdateGate(private val minGapMs: Long) {
    private var last = Long.MIN_VALUE / 2
    private var waiting = false

    /** Delay in ms for the post the caller must make now, or -1 when an update is already waiting (it reads the latest state). */
    @Synchronized fun request(now: Long, urgent: Boolean): Long {
        if (waiting) return -1
        waiting = true
        return if (urgent) 0 else (last + minGapMs - now).coerceIn(0, minGapMs)
    }

    /** Call when the posted update starts running. */
    @Synchronized fun fired(now: Long) { waiting = false; last = now }
}
