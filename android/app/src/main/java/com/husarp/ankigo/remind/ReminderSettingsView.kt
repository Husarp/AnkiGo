package com.husarp.ankigo.remind

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.Ui

/**
 * Settings → Reminders (PLAN 85, pick 4E): one compact row per reminder (icon, title, one-line summary, switch) and a Quiet
 * hours row. Tap a row and a bottom sheet opens with that reminder's options as chips; every change is saved at once through
 * [ReminderStore.update] (which sets the alarm again). Switching a reminder on asks for the notification permission on Android 13+;
 * while notifications are off a slim row says so, with a button to the app's notification settings.
 * R3-V2 puts [build] into the Reminders section of Settings.
 */
object ReminderSettingsView {
    /**
     * The Reminders page of Settings: rows only (no title bar); [page] is the Settings page that shows it. [openFor] = a catalog setting id
     * from the Settings search ("minGapHours", "quietFrom", ...): the sheet that holds it opens at once.
     */
    fun build(page: GamePage, openFor: String? = null): View {
        val screen = ReminderScreen(page)
        if (openFor != null) screen.root.post { screen.openSheetFor(openFor) } // once the page is on screen
        return screen.root
    }
}

/** The reminders (and quiet hours) the screen lists; each has a row, all but the shop a sheet. */
internal enum class ReminderTopic(val title: String, val icon: Int) {
    DUE("Cards due", R.drawable.ic_book),
    GOAL("Own goal", R.drawable.ic_flag),
    STREAK("Streak about to end", R.drawable.ic_flame),
    DAILY("Daily goal not reached", R.drawable.ic_check),
    SHOP("Shop news", R.drawable.ic_shop),
    QUIET("Quiet hours", R.drawable.ic_moon);

    companion object {
        /** The reminder that holds the Settings catalog setting [id] (the Reminders entries of [com.husarp.ankigo.ui.SettingsCatalog]); null for any other id. */
        fun forSetting(id: String): ReminderTopic? = when (id) {
            "dueOn", "dueMin", "dueMode", "dueTime", "dueEveryHours", "minGapHours", "dailyCap", "decks" -> DUE
            "goalOn", "goalKind", "goalCount" -> GOAL
            "streakOn", "streakTime" -> STREAK
            "dailyGoalOn", "dailyGoalTime" -> DAILY
            "shopOn" -> SHOP
            "quietOn", "quietFrom", "quietTo" -> QUIET
            else -> null
        }
    }
}

internal class ReminderScreen(
    private val page: GamePage,
    /** Reads the AnkiDroid decks off the main thread and gives them to the callback on the main thread (null = could not be read). */
    private val loadDecks: ((List<Anki.Deck>?) -> Unit) -> Unit = { done -> readDecks(page, done) },
    private val canPost: () -> Boolean = { ReminderPermission.canPost(page) },
) {
    private val ctx: Context get() = page
    private var prefs: ReminderPrefs = ReminderStore.load(page)
    private val handler = Handler(Looper.getMainLooper())

    /** The open sheet's body (refilled after every change) and which sheet it is. */
    private var sheetBody: LinearLayout? = null
    private var sheetTopic: ReminderTopic? = null
    private var deckBody: LinearLayout? = null
    private var decks: List<Anki.Deck>? = null
    private var decksLoading = true

    val root: LinearLayout = object : LinearLayout(page) {
        /** Back from Android's permission dialog or the notification settings: the permission row may have to go. */
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) { prefs = ReminderStore.load(page); redraw() }
        }
    }.apply { orientation = LinearLayout.VERTICAL }

    init { drawRows() }

    // ---- state ----

    /** Saves a change (re-arms the alarm) and redraws the rows and whatever sheet is open. */
    private fun change(f: (ReminderPrefs) -> ReminderPrefs) {
        ReminderStore.update(page, f)
        prefs = ReminderStore.load(page)
        redraw()
    }

    private fun redraw() {
        handler.post {
            drawRows()
            sheetBody?.let { b -> sheetTopic?.let { b.removeAllViews(); fillSheet(it, b) } }
            deckBody?.let { b -> b.removeAllViews(); fillDecks(b) }
        }
    }

    private fun isOn(t: ReminderTopic) = when (t) {
        ReminderTopic.DUE -> prefs.dueOn; ReminderTopic.GOAL -> prefs.goalOn; ReminderTopic.STREAK -> prefs.streakOn
        ReminderTopic.DAILY -> prefs.dailyGoalOn; ReminderTopic.SHOP -> prefs.shopOn; ReminderTopic.QUIET -> prefs.quietOn
    }

    private fun summary(t: ReminderTopic) = when (t) {
        ReminderTopic.DUE -> ReminderText.summary(ReminderKind.CARDS_DUE, prefs)
        ReminderTopic.GOAL -> ReminderText.summary(ReminderKind.OWN_GOAL, prefs)
        ReminderTopic.STREAK -> ReminderText.summary(ReminderKind.STREAK, prefs)
        ReminderTopic.DAILY -> ReminderText.summary(ReminderKind.DAILY_GOAL, prefs)
        ReminderTopic.SHOP -> ReminderText.summary(ReminderKind.SHOP, prefs)
        ReminderTopic.QUIET -> ReminderText.quietSummary(prefs)
    }

    private fun setOn(t: ReminderTopic, on: Boolean) {
        change {
            when (t) {
                ReminderTopic.DUE -> it.copy(dueOn = on); ReminderTopic.GOAL -> it.copy(goalOn = on)
                ReminderTopic.STREAK -> it.copy(streakOn = on); ReminderTopic.DAILY -> it.copy(dailyGoalOn = on)
                ReminderTopic.SHOP -> it.copy(shopOn = on); ReminderTopic.QUIET -> it.copy(quietOn = on)
            }
        }
        // Quiet hours alone send nothing, so only a reminder asks for the permission (a floating card has no Activity to ask from: the slim row has the button)
        if (on && t != ReminderTopic.QUIET) page.host.hostActivity?.let { ReminderPermission.ask(it) }
    }

    // ---- the page ----

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, top) }

    private fun drawRows() {
        root.removeAllViews()
        if (prefs.anyOn && !canPost()) root.addView(permissionRow(), lp())
        root.addView(
            Form.cardOf(ctx, listOf(ReminderTopic.DUE, ReminderTopic.GOAL, ReminderTopic.STREAK, ReminderTopic.DAILY, ReminderTopic.SHOP).map { row(it) }),
            lp(if (prefs.anyOn && !canPost()) 10 else 0),
        )
        root.addView(Form.cardOf(ctx, listOf(row(ReminderTopic.QUIET))), lp(12))
    }

    private fun row(t: ReminderTopic): View {
        val on = isOn(t)
        val sw = Switch(ctx).apply {
            isChecked = on
            Ui.tintSwitch(this)
            contentDescription = t.title
            setOnCheckedChangeListener { _, v -> setOn(t, v) }
        }
        val tile = FrameLayout(ctx).apply {
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx)
            addView(ImageView(ctx).apply {
                setImageResource(t.icon)
                setColorFilter(Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            }, FrameLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Gravity.CENTER))
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = t.title; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            addView(Ui.text(ctx, 11.5f, 700, R.color.ag_sub0).apply { text = summary(t); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; tag = "sub" },
                lp(1))
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 58)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
            addView(tile, LinearLayout.LayoutParams(Ui.dp(ctx, 34), Ui.dp(ctx, 34)))
            addView(col, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12) })
            addView(sw, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 10) })
            isClickable = true
            background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, ColorDrawable(0xFFFFFFFF.toInt()))
            // the shop news has no options: the row is the switch
            setOnClickListener { if (t == ReminderTopic.SHOP) sw.toggle() else openSheet(t) }
        }
    }

    /** Slim row: notifications are off for the app, with a button to its notification settings. */
    private fun permissionRow(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6))
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx).apply { setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_peach)) }
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_alert)
            setColorFilter(Ui.c(ctx, R.color.ag_peach), PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)))
        addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { text = "Notifications are off for AnkiGo" },
            LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10); it.marginEnd = Ui.dp(ctx, 8) })
        addView(Form.secondary(ctx, "Settings") { openNotificationSettings() })
    }

    private fun openNotificationSettings() {
        try {
            page.startScreen(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, page.packageName))
        } catch (e: Exception) {
            Report.fail("REMIND", "opening the notification settings", e)
            Ui.toast(ctx, "Open Android's Settings → Apps → AnkiGo → Notifications.")
        }
    }

    // ---- the sheets ----

    private fun openSheet(t: ReminderTopic) {
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        sheetBody = body
        sheetTopic = t
        fillSheet(t, body)
        showSheet(frame(body)) { sheetBody = null; sheetTopic = null }
    }

    /**
     * Opens the sheet of the reminder that holds the Settings catalog setting [id] (a search result). The shop news has no sheet (its row
     * is its switch). The topic whose sheet opened, or null when none did.
     */
    fun openSheetFor(id: String, open: (ReminderTopic) -> Unit = ::openSheet): ReminderTopic? {
        val t = ReminderTopic.forSetting(id)?.takeIf { it != ReminderTopic.SHOP } ?: return null
        open(t)
        return t
    }

    /** The content of a sheet as a View (no window): what a renders-only test shows. */
    internal fun sheetView(t: ReminderTopic): View {
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        sheetBody = body
        sheetTopic = t
        fillSheet(t, body)
        return frame(body)
    }

    /** The deck picker's content as a View (no window), with [list] as the decks read (null = could not be read, empty = still loading when [loading]). */
    internal fun deckPickerView(list: List<Anki.Deck>?, loading: Boolean = false): View {
        decks = list
        decksLoading = loading
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        deckBody = body
        fillDecks(body)
        return frame(body)
    }

    private fun fillSheet(t: ReminderTopic, body: LinearLayout) {
        body.addView(sheetHeader(t))
        when (t) {
            ReminderTopic.DUE -> {
                body.addView(label("Remind when at least"))
                body.addView(numberChips(listOf(10, 20, 50), prefs.dueMin, { "$it" }, 1, 9999, "Cards due at least") { v -> change { it.copy(dueMin = v) } })
                body.addView(label("Time"))
                body.addView(Form.segmented(ctx, listOf("Fixed time", "App picks", "Every X h"), prefs.dueMode.ordinal) { i ->
                    change { it.copy(dueMode = DueMode.entries[i]) }
                }, lp(2))
                when (prefs.dueMode) {
                    DueMode.FIXED -> body.addView(chipRow(listOf(timeChip(prefs.dueTime) { v -> change { it.copy(dueTime = v) } })))
                    DueMode.APP_PICKS -> body.addView(note("The hour you study most."))
                    DueMode.EVERY_HOURS -> body.addView(hourChips(prefs.dueEveryHours))
                }
                body.addView(label("Not more often than"))
                body.addView(numberChips(listOf(1, 3, 6), prefs.minGapHours, { "$it h" }, 0, 24, "Hours between reminders") { v -> change { it.copy(minGapHours = v) } })
                body.addView(label("At most"))
                body.addView(chipRow(listOf(1, 2).map { n ->
                    Form.toggleChip(ctx, "$n a day", prefs.dailyCap == n) { change { it.copy(dailyCap = n) } }
                }))
                body.addView(label("Decks"))
                body.addView(chipRow(listOf(
                    Form.toggleChip(ctx, "All decks", prefs.allDecks) { change { it.copy(allDecks = true) } },
                    Form.toggleChip(ctx, if (prefs.allDecks || prefs.deckIds.isEmpty()) "Choose…" else "Choose · ${prefs.deckIds.size}", !prefs.allDecks) { openDeckPicker() },
                )))
            }
            ReminderTopic.GOAL -> {
                val reviews = prefs.goalKind == GoalKind.REVIEWS
                body.addView(label("Count"))
                body.addView(Form.segmented(ctx, listOf("Reviews", "New cards"), prefs.goalKind.ordinal) { i ->
                    change { it.copy(goalKind = GoalKind.entries[i]) }
                }, lp(2))
                body.addView(label("Goal"))
                body.addView(numberChips(if (reviews) listOf(50, 100, 200) else listOf(10, 20, 50), prefs.goalCount, { "$it" }, 1, 99_999,
                    if (reviews) "Reviews a day" else "New cards a day") { v -> change { it.withGoalCount(v) } })
                body.addView(label("Remind every"))
                body.addView(hourChips(prefs.dueEveryHours))
            }
            ReminderTopic.STREAK -> {
                body.addView(label("Remind at"))
                body.addView(chipRow(listOf(timeChip(prefs.streakTime) { v -> change { it.copy(streakTime = v) } })))
            }
            ReminderTopic.DAILY -> {
                body.addView(label("Remind at"))
                body.addView(chipRow(listOf(timeChip(prefs.dailyGoalTime) { v -> change { it.copy(dailyGoalTime = v) } })))
            }
            ReminderTopic.QUIET -> {
                body.addView(label("From"))
                body.addView(chipRow(listOf(timeChip(prefs.quietFrom) { v -> change { it.copy(quietFrom = v) } })))
                body.addView(label("To"))
                body.addView(chipRow(listOf(timeChip(prefs.quietTo) { v -> change { it.copy(quietTo = v) } })))
            }
            ReminderTopic.SHOP -> Unit
        }
    }

    private fun sheetHeader(t: ReminderTopic): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = t.title; maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(Switch(ctx).apply {
            isChecked = isOn(t)
            Ui.tintSwitch(this)
            contentDescription = t.title
            setOnCheckedChangeListener { _, v -> setOn(t, v) }
        }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 10) })
    }

    private fun label(text: String): View = Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply {
        this.text = text.uppercase()
        letterSpacing = 0.06f
        setPadding(0, Ui.dp(ctx, 12), 0, 0)
    }

    private fun note(text: String): View = Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply {
        this.text = text
        setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 4))
    }

    private fun chipRow(chips: List<View>): View = Wrap(ctx, Ui.dp(ctx, 6), -Ui.dp(ctx, 8)).apply { chips.forEach { addView(it) } }

    /** "1 h  2 h  3 h  4 h  6 h": how often a reminder repeats ([ReminderPrefs.dueEveryHours], also what the own goal uses). */
    private fun hourChips(current: Int): View = chipRow(listOf(1, 2, 3, 4, 6).map { h ->
        Form.toggleChip(ctx, "$h h", current == h) { change { it.copy(dueEveryHours = h) } }
    })

    /** The usual values as chips, and "Custom…" (a number dialog) which shows the value when it is none of them. */
    private fun numberChips(values: List<Int>, current: Int, fmt: (Int) -> String, min: Int, max: Int, title: String, onSet: (Int) -> Unit): View {
        val chips = values.map { v -> Form.toggleChip(ctx, fmt(v), v == current) { onSet(v) } }.toMutableList()
        val custom = current !in values
        chips += Form.toggleChip(ctx, if (custom) "Custom · ${fmt(current)}" else "Custom…", custom) {
            askNumber(title, current, min, max, onSet)
        }
        return chipRow(chips)
    }

    private fun timeChip(minute: Int, onPick: (Int) -> Unit): View =
        Form.toggleChip(ctx, ReminderText.timeText(minute), true) { pickTime(minute, onPick) }

    // ---- dialogs ----

    private fun pickTime(minute: Int, onPick: (Int) -> Unit) {
        Pickers.show(TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minute / 60, minute % 60, true))
    }

    private fun askNumber(title: String, current: Int, min: Int, max: Int, onSave: (Int) -> Unit) {
        val field = EditText(ctx).apply {
            typeface = Ui.font(ctx, 700)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Ui.c(ctx, R.color.ag_text))
            Ui.tintEdit(this)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(max.toString().length))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            backgroundTintList = null
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
            setText(current.toString())
            setSelection(text.length)
        }
        val box = FrameLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 8), Ui.dp(ctx, 24), 0)
            addView(field, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 44)))
        }
        fun save() { field.text.toString().toIntOrNull()?.coerceIn(min, max)?.let(onSave) }
        val dlg = AlertDialog.Builder(ctx).setTitle(title).setView(box)
            .setPositiveButton("Save") { _, _ -> save() }.setNegativeButton("Cancel", null).create()
        field.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { dlg.dismiss(); save(); true } else false
        }
        Pickers.show(dlg)
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.requestFocus()
    }

    // ---- the deck picker ----

    private fun openDeckPicker() {
        // decks chosen before and set aside by "All decks" come back
        if (prefs.allDecks && prefs.deckIds.isNotEmpty()) change { it.copy(allDecks = false) }
        decks = null
        decksLoading = true
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        deckBody = body
        fillDecks(body)
        val dialog = showSheet(frame(body)) {
            deckBody = null
            // nothing chosen is "all decks", not "no decks"
            if (!prefs.allDecks && prefs.deckIds.isEmpty()) change { it.copy(allDecks = true) }
        }
        loadDecks { list ->
            if (!dialog.isShowing) return@loadDecks
            decks = list
            decksLoading = false
            deckBody?.let { b -> b.removeAllViews(); fillDecks(b) }
        }
    }

    private fun fillDecks(body: LinearLayout) {
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = "Decks" }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Form.secondary(ctx, "Done") { dismissTop() })
        body.addView(head)
        val list = decks
        when {
            decksLoading -> body.addView(note("Loading the decks…"))
            list == null -> body.addView(note("Can't read AnkiDroid's decks. Check that AnkiDroid is installed and AnkiGo may use it."))
            list.isEmpty() -> body.addView(note("AnkiDroid has no decks."))
            else -> {
                val chosen = prefs.deckIds
                val names = list.associateBy { it.name }
                for (d in list.sortedBy { it.name.replace("::", "\u0000").lowercase() }) {
                    val parts = d.name.split("::")
                    val inside = parts.indices.drop(1).any { i -> names[parts.take(i).joinToString("::")]?.id in chosen } // a chosen parent already counts it
                    body.addView(deckRow(d, parts.size - 1, d.id in chosen, inside))
                }
            }
        }
    }

    private fun deckRow(d: Anki.Deck, depth: Int, on: Boolean, inside: Boolean): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(ctx, 48)
        setPadding(Ui.dp(ctx, 4 + 18 * depth.coerceAtMost(4)), 0, Ui.dp(ctx, 4), 0)
        background = RippleDrawable(ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mantle)), Ui.rounded(Color.TRANSPARENT, 12, ctx), null)
        contentDescription = d.name
        addView(ImageView(ctx).apply {
            val shown = on || inside
            setImageResource(if (shown) R.drawable.ic_checkbox_on else R.drawable.ic_checkbox_off)
            setColorFilter(Ui.c(ctx, if (on) R.color.ag_mauve else if (inside) R.color.ag_sub0 else R.color.ag_ov), PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)))
        addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply {
            text = d.name.substringAfterLast("::")
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12) })
        setOnClickListener {
            change { p ->
                val ids = if (d.id in p.deckIds) p.deckIds - d.id else p.deckIds + d.id
                p.copy(deckIds = ids, allDecks = false)
            }
        }
    }

    // ---- sheet window ----

    private var topDialog: Dialog? = null

    private fun dismissTop() { topDialog?.dismiss() }

    /** Rounded top corners, grab handle, [body] scrolling when it is taller than 85 % of the window. */
    private fun frame(body: View, maxPx: Int = (ctx.resources.displayMetrics.heightPixels * 0.85f).toInt()): View {
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
            addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
                LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 8)
                })
            addView(body)
        }
        return MaxHeightScroll(ctx, maxPx).apply {
            isVerticalScrollBarEnabled = false
            addView(sheet, FrameLayout.LayoutParams(-1, -2))
        }
    }

    /** Shows [content] at the bottom of the window (an overlay window when the page is a floating card); [onClosed] runs when it goes. */
    private fun showSheet(content: View, onClosed: () -> Unit): Dialog {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        val before = topDialog
        topDialog = dialog
        dialog.setOnDismissListener { if (topDialog === dialog) topDialog = before; onClosed() }
        dialog.show()
        dialog.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), -2)
        return dialog
    }

    /** A ScrollView that never grows past [maxPx]: the sheet grows with its content, then scrolls. */
    private class MaxHeightScroll(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxPx else minOf(maxPx, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** Left-aligned wrapping row; lines overlap by [lineGapPx] (negative) so 48 dp touch chips sit close together. */
    private class Wrap(ctx: Context, private val gapPx: Int, private val lineGapPx: Int) : ViewGroup(ctx) {
        private val lineStart = ArrayList<Int>()
        private val lineH = ArrayList<Int>()

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
            lineStart.clear(); lineH.clear()
            var x = 0; var h = 0; var start = 0
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
                if (x > 0 && x + gapPx + c.measuredWidth > maxW) { lineStart += start; lineH += h; start = i; x = 0; h = 0 }
                x += (if (x > 0) gapPx else 0) + c.measuredWidth
                h = maxOf(h, c.measuredHeight)
            }
            if (x > 0) { lineStart += start; lineH += h }
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), lineH.sum() + lineGapPx * maxOf(0, lineH.size - 1) + paddingTop + paddingBottom)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            var y = paddingTop
            for (li in lineStart.indices) {
                var x = paddingLeft
                val end = if (li + 1 < lineStart.size) lineStart[li + 1] else childCount
                for (i in lineStart[li] until end) {
                    val c = getChildAt(i)
                    if (c.visibility == GONE) continue
                    c.layout(x, y + (lineH[li] - c.measuredHeight) / 2, x + c.measuredWidth, y + (lineH[li] + c.measuredHeight) / 2)
                    x += c.measuredWidth + gapPx
                }
                y += lineH[li] + lineGapPx
            }
        }
    }

    companion object {
        /** The default deck reader: [Anki.decks] on a background thread, the answer on the main thread (null = no permission or an error). */
        fun readDecks(ctx: Context, done: (List<Anki.Deck>?) -> Unit) {
            val app = ctx.applicationContext
            Thread {
                val list = try {
                    Anki.load(app)
                    if (Anki.hasPermission(app)) Anki.decks(app) else null
                } catch (e: Exception) {
                    Report.fail("REMIND", "reading the decks for the reminders", e)
                    null
                }
                Handler(Looper.getMainLooper()).post { done(list) }
            }.start()
        }
    }
}
