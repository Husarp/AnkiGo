package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import com.husarp.ankigo.R
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.sound.SoundPrefs

/** Settings -> Sound: battle sounds, cries, volume, the quiet-on-card-audio rule and a test row. Prefs live in [SoundPrefs]. */
internal fun soundCard(ctx: Context): View = Form.cardOf(ctx, listOf(
    Form.switchRow(ctx, "Battle sounds", null, SoundPrefs.battleSounds(ctx)) { SoundPrefs.setBattleSounds(ctx, it) },
    Form.switchRow(ctx, "Pokémon cries", "Downloaded once each, ~7 KB", SoundPrefs.cries(ctx)) { SoundPrefs.setCries(ctx, it) },
    Form.stepperRow(ctx, "Volume", null, SoundPrefs.volume(ctx), 0, 100, 10, { "$it%" }) { SoundPrefs.setVolume(ctx, it) },
    Form.switchRow(ctx, "Quiet on cards with sound", "While studying in AnkiGo, game sounds pause when the card plays audio", SoundPrefs.quietOnCardAudio(ctx)) {
        SoundPrefs.setQuietOnCardAudio(ctx, it)
    },
    Form.row(ctx, "Play a test sound", "Follows your phone's silent/vibrate mode and media volume.",
        Ui.text(ctx, 13f, 800, R.color.ag_mauve).apply { text = "Play" }) {
        GameSound.test(ctx)?.let { Ui.toast(ctx, it) }
    },
))
