package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import java.util.Locale

/**
 * The sections of the Settings tab. [opens] = true: tapping opens it in place (same page, back arrow in the app bar);
 * false: the row starts its own screen (Help, Updates, Diagnostics), so a section with a single link isn't needed.
 */
enum class SettingsSection(val title: String, @DrawableRes val icon: Int, @ColorRes val tone: Int) {
    LOOK("Look", R.drawable.ic_palette, R.color.ag_mauve),
    GAME("Game", R.drawable.ic_game, R.color.ag_green),
    STUDY("Study", R.drawable.ic_study, R.color.ag_blue),
    BATTLE("Battle panel & windows", R.drawable.ic_pip, R.color.ag_peach),
    REMINDERS("Reminders", R.drawable.ic_bell, R.color.ag_red),
    SOUND("Sound", R.drawable.ic_sound, R.color.ag_pink),
    DOWNLOADS("Downloads", R.drawable.ic_download, R.color.ag_teal),
    ANKIMON("Ankimon PC sync", R.drawable.ic_swap, R.color.ag_yellow),
    SAVE("Save & backups", R.drawable.ic_save, R.color.ag_lav),
    HELP("Help & game guide", R.drawable.ic_help, R.color.ag_sky),
    UPDATES("Updates", R.drawable.ic_update, R.color.ag_lime),
    DIAGNOSTICS("Diagnostics", R.drawable.ic_bug, R.color.ag_sub0),
    ABOUT("About", R.drawable.ic_info, R.color.ag_sub1);

    val opens: Boolean get() = this != HELP && this != UPDATES && this != DIAGNOSTICS

    companion object {
        /** The home list in order, in groups (one card each). */
        val GROUPS: List<List<SettingsSection>> = listOf(
            listOf(LOOK, GAME, STUDY, BATTLE, REMINDERS),
            listOf(SOUND, DOWNLOADS, ANKIMON, SAVE),
            listOf(HELP, UPDATES, DIAGNOSTICS, ABOUT),
        )

        /** The small group titles over the cards of [GROUPS], one each (PLAN 87, pick 11C). */
        val GROUP_TITLES: List<String> = listOf("Play", "Sound & data", "Help & app")

        fun byName(name: String?): SettingsSection? = entries.firstOrNull { it.name == name }
    }
}

/** The one-line summaries under each section title on the Settings home. Pure text, no Android. */
object SettingsSummary {
    fun look(theme: ThemeMode, accent: Accent, scale: Int) = "${theme.label} · ${accent.label} · $scale %"

    /** "Gen 5", "Gens 1–8" (a straight run) or "Gens 1, 3, 9". */
    fun gens(gens: Iterable<Int>): String {
        val g = gens.toSortedSet().toList()
        return when {
            g.isEmpty() -> "No generation"
            g.size == 1 -> "Gen ${g[0]}"
            g.last() - g.first() == g.size - 1 -> "Gens ${g.first()}–${g.last()}"
            else -> "Gens " + g.joinToString(", ")
        }
    }

    fun game(gens: Iterable<Int>, cardsPerRound: Int, dailyGoal: Int) =
        "${gens(gens)} · $cardsPerRound answers per attack · goal $dailyGoal"

    fun study(inAnkiDroid: Boolean, swipe: Boolean, zoom: Int): String =
        (if (inAnkiDroid) "Decks open AnkiDroid" else "Studies inside AnkiGo") + (if (swipe) " · swipes" else "") + " · text $zoom%"

    fun leave(mode: LeaveMode) = when (mode) {
        LeaveMode.OFF -> "Nothing"
        LeaveMode.PIP -> "Picture-in-picture"
        LeaveMode.FLOAT -> "Floating battle window"
    }

    fun battle(mode: LeaveMode, statsBarSmall: Boolean, compact: Boolean, cardsOwn: Boolean = true): String =
        "On leaving: ${leave(mode)}" + (if (compact) " · compact cards" else "") + (if (!cardsOwn) " · classic cards" else "") +
            (if (!statsBarSmall) " · no stats bar when small" else "")

    fun sound(battle: Boolean, cries: Boolean, volume: Int): String = when {
        !battle && !cries -> "Off"
        else -> "${listOfNotNull("Battle sounds".takeIf { battle }, "cries".takeIf { cries }).joinToString(" and ")} · $volume%"
            .replaceFirstChar { it.uppercase() }
    }

    fun downloads(dataBytes: Long, pictureBytes: Long, working: Boolean): String =
        if (working) "Downloading…" else "Data ${bytes(dataBytes)} · pictures ${bytes(pictureBytes)}"

    fun save(savedAt: Long?, seq: Long?, format: (Long) -> String): String =
        if (savedAt == null || savedAt == 0L) "Not saved yet" else "Saved ${format(savedAt)}" + (seq?.let { " (#$it)" } ?: "")

    fun updates(version: String) = "Version $version"

    /** "Off", or "2 on · cards due, own goal" (the first two names, then "+n"). Quiet hours alone are not a reminder. */
    fun reminders(p: com.husarp.ankigo.remind.ReminderPrefs): String {
        val on = listOfNotNull("cards due".takeIf { p.dueOn }, "own goal".takeIf { p.goalOn }, "streak".takeIf { p.streakOn },
            "daily goal".takeIf { p.dailyGoalOn }, "shop news".takeIf { p.shopOn })
        if (on.isEmpty()) return "Off"
        val names = on.take(2).joinToString(", ") + if (on.size > 2) " +${on.size - 2}" else ""
        return "${on.size} on · $names"
    }

    fun bytes(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1f MB", n / 1_000_000.0)
        n >= 1_000 -> "${n / 1_000} KB"
        else -> "$n B"
    }
}

/** Home-list row builders. */
internal object SettingsHome {
    /** One section row: tinted icon tile, title, one summary line, chevron. */
    fun row(ctx: Context, s: SettingsSection, summary: String, onClick: () -> Unit): LinearLayout {
        val tone = Ui.c(ctx, s.tone)
        val tile = ImageView(ctx).apply {
            setImageResource(s.icon)
            setColorFilter(tone, PorterDuff.Mode.SRC_IN)
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            background = Ui.rounded((tone and 0x00FFFFFF) or 0x2E000000, 11, ctx)
        }
        val chevron = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_back)
            rotation = 180f
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 15f, 800, R.color.ag_text).apply { text = s.title })
            addView(Form.body(ctx, summary, 12f, R.color.ag_sub0).apply { tag = "sub" }, Form.lp(ctx, 2))
        }
        val row = Form.row(ctx, "", null, null, onClick)
        row.removeAllViews()
        row.minimumHeight = Ui.dp(ctx, 60)
        row.addView(tile, Form.lp(Ui.dp(ctx, 38), Ui.dp(ctx, 38)))
        row.addView(texts, Form.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(ctx, 12) })
        row.addView(chevron, Form.lp(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginStart = Ui.dp(ctx, 8) })
        row.contentDescription = "${s.title}. $summary"
        row.gravity = Gravity.CENTER_VERTICAL
        return row
    }

    fun group(ctx: Context, rows: List<View>): LinearLayout = Form.cardOf(ctx, rows)
}
