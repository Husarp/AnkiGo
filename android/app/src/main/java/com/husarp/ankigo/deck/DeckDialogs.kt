package com.husarp.ankigo.deck

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.Ui
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The dialogs of deck and card management, in the app's card look (shown through [Pickers.show], so screenshots capture them).
 * Each has its own buttons inside the card (no platform button bar), so what a dialog looks like is the same on every Android.
 */
internal object DeckDialogs {
    /** A plain message with one OK button. */
    fun say(ctx: Context, title: String, message: String) {
        lateinit var dlg: AlertDialog
        val box = DeckViews.box(ctx, title)
        box.addView(DeckViews.message(ctx, message), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        box.addView(DeckViews.button(ctx, "OK", R.color.ag_mauve) { dlg.dismiss() }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 16) })
        dlg = AlertDialog.Builder(ctx).setView(box).create()
        Pickers.show(dlg)
    }

    /**
     * Cancel + [yes] ([danger] = red). [onYes] runs after the dialog closes. [onCancel] runs when the dialog goes away without a yes
     * (Cancel, a tap outside, Back), so a screen that locked something for the question can unlock it.
     */
    fun confirm(ctx: Context, title: String, body: CharSequence, yes: String, danger: Boolean, onCancel: () -> Unit = {}, onYes: () -> Unit): AlertDialog {
        lateinit var dlg: AlertDialog
        var answered = false
        val box = DeckViews.box(ctx, title)
        box.addView(DeckViews.message(ctx, body), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        val row = DeckViews.buttons(ctx, DeckViews.button(ctx, "Cancel") { dlg.dismiss() },
            DeckViews.button(ctx, yes, if (danger) R.color.ag_red else R.color.ag_mauve) { answered = true; dlg.dismiss(); onYes() })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 18) })
        dlg = AlertDialog.Builder(ctx).setView(box).setOnDismissListener { if (!answered) onCancel() }.create()
        Pickers.show(dlg)
        return dlg
    }

    /**
     * "New deck" / "New subdeck": one name field, the problem under it in red, Cancel + Create. [inside] = the parent deck ("Inside Geography").
     * [create] gets the typed text and runs the creation on its own thread; it reports back through [Name.fail] (the dialog stays open with
     * the message) or [Name.done] (the dialog closes).
     */
    class Name(private val ctx: Context, private val title: String, private val inside: String?, private val hint: String, private val create: (String, Name) -> Unit) {
        private lateinit var input: EditText
        private lateinit var error: TextView
        private lateinit var make: TextView
        private lateinit var dlg: AlertDialog
        private val working = AtomicBoolean(false)

        /** The dialog's content (the screenshots build it without a window). */
        fun view(): View {
            val box = DeckViews.box(ctx, title)
            if (inside != null) box.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = "Inside $inside"; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
            input = EditText(ctx).apply {
                this.hint = this@Name.hint
                typeface = Ui.font(ctx, 700)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Ui.c(ctx, R.color.ag_text))
                setHintTextColor(Ui.c(ctx, R.color.ag_ov))
                Ui.tintEdit(this)
                setSingleLine()
                filters = arrayOf(InputFilter.LengthFilter(120))
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_DONE
                background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
                backgroundTintList = null // tintEdit's underline tint would paint the whole rounded fill
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
                setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
            }
            box.addView(input, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 46)).also { it.topMargin = Ui.dp(ctx, 12) })
            error = Ui.text(ctx, 12.5f, 800, R.color.ag_red).apply { visibility = View.GONE; setLineSpacing(0f, 1.15f) }
            box.addView(error, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 8) })
            make = DeckViews.button(ctx, "Create", R.color.ag_mauve) { submit() }
            box.addView(DeckViews.buttons(ctx, DeckViews.button(ctx, "Cancel") { dlg.dismiss() }, make), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            return box
        }

        fun show(): AlertDialog {
            dlg = AlertDialog.Builder(ctx).setView(view()).create()
            Pickers.show(dlg)
            dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            input.requestFocus()
            return dlg
        }

        /** What is typed now. */
        val typed: String get() = input.text.toString()

        /** Shows [text] under the field (null clears it). */
        fun problem(text: String?) {
            error.text = text.orEmpty()
            error.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        /** The creation failed: the message stays under the field and the user can fix the name. Main thread. */
        fun fail(message: String) { working.set(false); DeckViews.enable(make, true); problem(message) }

        /** The creation worked: close. Main thread. */
        fun done() { working.set(false); dlg.dismiss() }

        private fun submit() {
            if (working.get()) return
            DeckTexts.nameProblem(typed)?.let { problem(it); return }
            problem(null)
            working.set(true); DeckViews.enable(make, false)
            create(typed, this)
        }
    }

    /**
     * The progress card of a running "Move all cards" ([MoveAllJob]): the text, a bar and Cancel while it runs; the result sentence and OK
     * when it is over. Several screens can show it in turn (a turned screen builds a new one for the same job).
     */
    class MoveProgress(private val ctx: Context, private val job: MoveAllJob, private val onClose: () -> Unit) {
        private lateinit var dlg: AlertDialog
        private val holder = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        fun view(): View { render(); return holder }

        fun show(): AlertDialog {
            dlg = AlertDialog.Builder(ctx).setView(view()).setCancelable(false).create()
            Pickers.show(dlg)
            return dlg
        }

        /** Draws the job's state again (called on every progress step, on the main thread). */
        fun render() {
            holder.removeAllViews()
            val box = DeckViews.box(ctx, if (job.result == null) "Moving cards" else "Move cards")
            val res = job.result
            val line = if (res != null) res.message else DeckTexts.progress(job.done, job.total)
            box.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = "From \"${job.from}\" to \"${job.to}\""; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
            box.addView(DeckViews.message(ctx, line), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 12) })
            if (res == null) {
                val f = if (job.total <= 0) 0f else job.done.toFloat() / job.total
                box.addView(DeckViews.bar(ctx, f), LinearLayout.LayoutParams(-1, Ui.dp(ctx, 8)).also { it.topMargin = Ui.dp(ctx, 12) })
                val stopping = job.cancel.get()
                val b = DeckViews.button(ctx, if (stopping) "Stopping…" else "Cancel") { job.cancel.set(true); render() }
                if (stopping) DeckViews.enable(b, false)
                box.addView(b, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            } else {
                box.addView(DeckViews.button(ctx, "OK", R.color.ag_mauve) { close() }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            }
            holder.addView(box)
        }

        private fun close() { if (::dlg.isInitialized) dlg.dismiss(); onClose() }
    }
}
