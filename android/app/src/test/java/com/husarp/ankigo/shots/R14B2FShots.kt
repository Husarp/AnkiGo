package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.study.NewCardOk
import com.husarp.ankigo.ui.AnswerRow
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R14-B2-F (PLAN 155, option A of docs/design/round14.html): the study screen's answer row on a new card with "New cards: one OK
 * button" on (OK | Good | Easy, OK in the calm sub-text grey, "again soon" under it) and off (the usual buttons), and the Settings row.
 * The real AnswerRow draws every row. PNGs: r14b2f-<rows|rows-wide|settings-on|settings-off>-<phone|tall>-<light|dark>.
 */
class R14B2FShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val times4 = listOf("1m", "6m", "10m", "4d")
    private val times3 = listOf("1m", "10m", "4d")
    private val times2 = listOf("1m", "10m")

    private fun cap(ctx: Context, t: String) = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = t; setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), 0, Ui.dp(ctx, 4)) }

    /** The buttons area of the study screen: a thin line, then the 8 dp padded column the real screen fills. Returns the area and its button column. */
    private fun area(ctx: Context): Pair<View, LinearLayout> {
        val col = StudyUi.column(ctx).apply { setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8)); setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
        val box = StudyUi.column(ctx).apply { addView(StudyUi.line(ctx)); addView(col) }
        return box to col
    }

    private fun scene(ctx: Context, widthDp: Int, build: LinearLayout.() -> Unit): View {
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; build() }
        return FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_mantle))
            addView(inner, FrameLayout.LayoutParams(if (widthDp > 0) Ui.dp(ctx, widthDp) else -1, -2))
        }
    }

    private fun LinearLayout.add(ctx: Context, caption: String, buttons: Int, times: List<String>, ok: Boolean, wide: Boolean = false, held: Int = 0): LinearLayout {
        addView(cap(ctx, caption))
        val (box, col) = area(ctx)
        AnswerRow.fill(ctx, col, buttons, times, ok, wide, true, held, { v, pressed -> v.isEnabled = false; v.alpha = if (pressed) 1f else .35f }) { }
        addView(box)
        return col
    }

    private fun texts(col: LinearLayout): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View) { if (v is TextView) out += v.text.toString() else if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(col)
        return out
    }

    @Test fun rows() {
        paparazzi.shootWith("r14b2f-rows", sizes = listOf(Size.PHONE)) { ctx ->
            scene(ctx, -1) {
                val off = add(ctx, "Setting off: a new card, the usual four buttons", 4, times4, ok = false)
                assertEquals(listOf("Again", "1m", "Hard", "6m", "Good", "10m", "Easy", "4d"), texts(off))
                val on = add(ctx, "Setting on: a new card, four buttons (option A)", 4, times4, ok = true)
                assertEquals(listOf("OK", "again soon", "Good", "10m", "Easy", "4d"), texts(on))
                val row = (on.getChildAt(0) as LinearLayout)
                assertEquals(3, row.childCount)
                assertEquals(2f, (row.getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight)
                assertEquals(1f, (row.getChildAt(1).layoutParams as LinearLayout.LayoutParams).weight)
                assertTrue(row.getChildAt(0).contentDescription.toString().startsWith("OK"))
                val three = add(ctx, "Setting on: a card with three buttons", 3, times3, ok = true)
                assertEquals(listOf("OK", "again soon", "Good", "10m", "Easy", "4d"), texts(three))
                val two = add(ctx, "Setting on: a card with two buttons", 2, times2, ok = true)
                assertEquals(listOf("OK", "again soon", "Good", "10m"), texts(two))
                add(ctx, "Setting on, the card is not new: nothing changes", 4, listOf("10m", "12m", "1d", "4d"), ok = false)
                add(ctx, "OK tapped while the next card is still being confirmed (OK full, the others dimmed)", 4, times4, ok = true, held = 1)
                add(ctx, "A Hard key on the OK row is held as OK", 4, times4, ok = true, held = 2)
            }
        }
    }

    @Test fun rowsWide() {
        // the landscape side column: two rows of buttons, 40 dp tall; OK has the first row to itself
        paparazzi.shootWith("r14b2f-rows-wide", sizes = listOf(Size.PHONE)) { ctx ->
            scene(ctx, 300) {
                val off = add(ctx, "Setting off: the usual 2 x 2", 4, times4, ok = false, wide = true)
                assertEquals(2, off.childCount)
                val on = add(ctx, "Setting on: OK, then Good | Easy", 4, times4, ok = true, wide = true)
                assertEquals(2, on.childCount)
                assertEquals(1, (on.getChildAt(0) as LinearLayout).childCount)
                assertEquals(2, (on.getChildAt(1) as LinearLayout).childCount)
                val two = add(ctx, "Setting on, two buttons: one row", 2, times2, ok = true, wide = true)
                assertEquals(1, two.childCount)
            }
        }
    }

    private fun settings(ctx: Context, on: Boolean): View {
        val c = ShotCtx(ctx)
        c.getSharedPreferences("study", 0).edit().putBoolean(NewCardOk.PREF, on).apply()
        val p = SettingsPage(c, NoHost)
        return pageScreen(c, p).also { p.open(SettingsSection.STUDY) }
    }

    @Test fun settingsRow() {
        val tall = Variant("tall", 412, 1500)
        paparazzi.shootPage("r14b2f-settings-off", listOf(tall)) { settings(it, false) }
        paparazzi.shootPage("r14b2f-settings-on", listOf(tall)) { settings(it, true) }
    }
}
