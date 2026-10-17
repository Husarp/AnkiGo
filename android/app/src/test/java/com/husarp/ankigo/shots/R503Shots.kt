package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.sound.SoundChannel
import com.husarp.ankigo.sound.SoundGroup
import com.husarp.ankigo.sound.SoundPrefs
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Rule
import org.junit.Test

/*
 * R5-03: Settings -> Sound as pick 84B (three cards with ▶ and a slider each). "r503-sound-*" = defaults,
 * "r503-sound-mixed-*" = some groups off and other volumes. Light + dark, PHONE + SPLIT.
 */
class R503Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun page(ctx: Context): View {
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val page = pageScreen(ShotCtx(ctx), sp)
        sp.open(SettingsSection.SOUND)
        return page
    }

    private fun defaults(ctx: Context) {
        for (g in SoundGroup.entries) SoundPrefs.setGroupOn(ctx, g, true)
        SoundPrefs.setVolume(ctx, SoundChannel.BUTTONS, 40)
        SoundPrefs.setVolume(ctx, SoundChannel.BATTLE, 70)
        SoundPrefs.setVolume(ctx, SoundChannel.CRIES, 70)
        SoundPrefs.setQuietOnCardAudio(ctx, true)
    }

    @Test fun soundSettings() {
        paparazzi.shootPage("r503-sound", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> defaults(ctx); page(ctx) }
        paparazzi.shootPage("r503-sound-mixed", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            defaults(ctx)
            SoundPrefs.setGroupOn(ctx, SoundGroup.SHOW_ANSWER, false); SoundPrefs.setGroupOn(ctx, SoundGroup.MOMENTS, false)
            SoundPrefs.setGroupOn(ctx, SoundGroup.CRIES, false)
            SoundPrefs.setVolume(ctx, SoundChannel.BUTTONS, 0); SoundPrefs.setVolume(ctx, SoundChannel.BATTLE, 100); SoundPrefs.setVolume(ctx, SoundChannel.CRIES, 20)
            page(ctx)
        }
    }
}
