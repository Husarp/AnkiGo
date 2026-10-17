package com.husarp.ankigo.ui

import android.content.Context
import android.widget.LinearLayout
import com.husarp.ankigo.game.DailyGoal
import com.husarp.ankigo.game.Settings
import java.time.LocalDate

/**
 * Settings -> Game -> Daily goal (R6-04): a two-way switch "Same every day" / "Per weekday". Same = one number
 * ([Settings.dailyAverage]); per weekday = Monday .. Sunday with a number each ([Settings.goalByWeekday]). Switching to per
 * weekday pre-fills all seven with the current number; switching back keeps the number. Every change goes through [edit]
 * (the caller saves). Shown as an [AppDialog].
 */
internal object DailyGoalPicker {
    val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** The grey value on the Settings row: "100 a day", or "120 today" (+ "per weekday") when the goals differ by weekday. */
    fun valueText(s: Settings, today: String?): String =
        if (s.goalByWeekday == null) "${s.dailyAverage} a day" else "${DailyGoal.forSettings(s, today)} today"

    fun show(ctx: Context, s: Settings, today: String?, edit: ((Settings) -> Unit) -> Unit, onClose: () -> Unit) {
        var base = s.dailyAverage
        var table: List<Int>? = DailyGoal.cleanTable(s.goalByWeekday)
        val todayIdx = runCatching { LocalDate.parse(today).dayOfWeek.value - 1 }.getOrDefault(-1)

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        fun fill() {
            rows.removeAllViews()
            val t = table
            if (t == null) {
                rows.addView(Form.stepperRow(ctx, "Daily goal", "Reviews a day. A bigger goal also makes rare Pokémon more likely.", base, DailyGoal.MIN, DailyGoal.MAX, DailyGoal.STEP) { v ->
                    base = v; edit { it.dailyAverage = v }
                })
            } else {
                rows.addView(Form.body(ctx, "Reviews on each day of the week. Today's number is used for the goal bar, widgets, reminders and the streak.", 12, com.husarp.ankigo.R.color.ag_sub0),
                    Form.lp(ctx, 0).also { it.setMargins(Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4)) })
                DAYS.forEachIndexed { i, name ->
                    rows.addView(Form.stepperRow(ctx, name, if (i == todayIdx) "Today" else null, t[i], DailyGoal.MIN, DailyGoal.MAX, DailyGoal.STEP) { v ->
                        val next = (table ?: return@stepperRow).toMutableList().also { it[i] = v }
                        table = next
                        edit { it.goalByWeekday = next }
                    })
                }
            }
        }

        val switch = Form.segmented(ctx, listOf("Same every day", "Per weekday"), if (table == null) 0 else 1) { pick ->
            if (pick == 0) {
                table = null
                edit { it.goalByWeekday = null }
            } else if (table == null) {
                val seven = List(7) { base.coerceIn(DailyGoal.MIN, DailyGoal.MAX) }
                table = seven
                edit { it.goalByWeekday = seven }
            }
            fill()
        }
        body.addView(switch, Form.lp(ctx, 4).also { it.setMargins(Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4)) })
        body.addView(rows)
        fill()

        val dlg = AppDialog.Builder(ctx).setTitle("Daily goal")
            .setView(body)
            .setPositiveButton("Close", null).create()
        dlg.setOnDismissListener { onClose() }
        Pickers.show(dlg)
    }
}
