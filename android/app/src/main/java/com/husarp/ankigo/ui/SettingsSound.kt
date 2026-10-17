package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.sound.SoundChannel
import com.husarp.ankigo.sound.SoundGroup
import com.husarp.ankigo.sound.SoundId
import com.husarp.ankigo.sound.SoundPrefs

/**
 * Settings -> Sound (pick 84B): three cards (Study buttons, Battle, Pokémon cries), each with its switches, a small ▶ to hear a group
 * and its own volume slider, then the quiet-on-card-audio rule and a test row. Prefs live in [SoundPrefs].
 */
internal fun soundCard(ctx: Context): View {
    fun group(title: String, sub: String?, g: SoundGroup, hear: SoundId?) =
        Form.soundRow(ctx, title, sub, SoundPrefs.groupOn(ctx, g), hear?.let { id -> { GameSound.preview(ctx, id)?.let { Ui.toast(ctx, it) } } }) {
            SoundPrefs.setGroupOn(ctx, g, it)
        }
    fun volume(title: String, ch: SoundChannel) =
        Form.sliderRow(ctx, title, SoundPrefs.volume(ctx, ch)) { SoundPrefs.setVolume(ctx, ch, it) }
    fun card(icon: Int, title: String, rows: List<View>) = Form.cardOf(ctx, rows).also { it.addView(Form.cardTitle(ctx, icon, title), 0) }

    val buttons = card(R.drawable.ic_study, "Study buttons", listOf(
        group("Answer buttons", "Again, Hard, Good, Easy", SoundGroup.ANSWER_BUTTONS, SoundId.GOOD),
        group("Show answer", null, SoundGroup.SHOW_ANSWER, SoundId.SHOW_ANSWER),
        group("Menus & tabs", "Tabs, menus, ball button", SoundGroup.MENUS, SoundId.TAB),
        group("Shop", null, SoundGroup.SHOP, SoundId.BUY),
        volume("Button volume", SoundChannel.BUTTONS),
    ))
    val battle = card(R.drawable.ic_flame, "Battle", listOf(
        group("Battle effects", "Hits, faint, catch, level up, misses", SoundGroup.BATTLE, SoundId.HIT),
        group("Status & moments", "Burn, poison, shiny, evolution, cash, goals", SoundGroup.MOMENTS, SoundId.STAT_UP),
        volume("Battle volume", SoundChannel.BATTLE),
    ))
    val cries = card(R.drawable.ic_sound, "Pokémon cries", listOf(
        group("Pokémon cries", "Downloaded once each, ~7 KB", SoundGroup.CRIES, null),
        volume("Cries volume", SoundChannel.CRIES),
    ))
    val rules = Form.cardOf(ctx, listOf(
        Form.switchRow(ctx, "Quiet on cards with sound", "While studying in AnkiGo, game sounds pause when the card plays audio", SoundPrefs.quietOnCardAudio(ctx)) {
            SoundPrefs.setQuietOnCardAudio(ctx, it)
        },
        Form.row(ctx, "Play a test sound", "Follows your phone's silent/vibrate mode and media volume.",
            Ui.text(ctx, 13f, 800, R.color.ag_mauve).apply { text = "Play" }) {
            GameSound.test(ctx)?.let { Ui.toast(ctx, it) }
        },
    ))
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(buttons)
        addView(battle, Form.lp(ctx, 12))
        addView(cries, Form.lp(ctx, 12))
        addView(rules, Form.lp(ctx, 12))
    }
}

/** The line under "Sound" on the Settings home: the volume of each channel that has something on, or "Off". */
internal fun soundSummary(ctx: Context): String {
    fun vol(ch: SoundChannel) =
        if (SoundGroup.entries.any { it.channel == ch && SoundPrefs.groupOn(ctx, it) }) SoundPrefs.volume(ctx, ch) else null
    return SettingsSummary.sound(vol(SoundChannel.BUTTONS), vol(SoundChannel.BATTLE), vol(SoundChannel.CRIES))
}
