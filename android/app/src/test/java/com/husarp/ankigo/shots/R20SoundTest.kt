package com.husarp.ankigo.shots

import android.widget.SeekBar
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.sound.*
import com.husarp.ankigo.ui.soundCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R20SoundTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun legacyVolumeMigratesWithoutOverwritingExistingChannelChoices() {
        val ctx = Shots.files(paparazzi.context)
        val p = ctx.getSharedPreferences("ankigo_sound", 0)
        p.edit().clear().putInt("volume", 23).putInt("vol_cries", 81).commit()
        SoundPrefs.migrateVolumes(ctx)
        assertEquals(23, SoundPrefs.volume(ctx, SoundChannel.BATTLE))
        assertEquals(81, SoundPrefs.volume(ctx, SoundChannel.CRIES))
        assertEquals(40, SoundPrefs.volume(ctx, SoundChannel.BUTTONS))
        assertTrue(SoundChannel.entries.all { p.contains(SoundDefaults.volumeKey(it)) })
        SoundPrefs.setVolume(ctx, SoundChannel.BUTTONS, 10)
        SoundPrefs.migrateVolumes(ctx)
        assertEquals(10, SoundPrefs.volume(ctx, SoundChannel.BUTTONS))
        assertEquals(23, SoundPrefs.volume(ctx, SoundChannel.BATTLE))
        assertEquals(81, SoundPrefs.volume(ctx, SoundChannel.CRIES))
        p.edit().clear().commit()
    }

    @Test fun settingsHasThreeIndependentSlidersAndPlaybackRoutesBothGenerators() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("ankigo_sound", 0).edit().clear().commit()
        val sliders = all(soundCard(ctx)).filterIsInstance<SeekBar>()
        assertEquals(3, sliders.size)
        fun drag(bar: SeekBar, step: Int) {
            val field = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener").apply { isAccessible = true }
            (field.get(bar) as SeekBar.OnSeekBarChangeListener).onProgressChanged(bar, step, true)
        }
        drag(sliders[0], 1)
        assertEquals(10, SoundPrefs.volume(ctx, SoundChannel.BUTTONS))
        assertEquals(70, SoundPrefs.volume(ctx, SoundChannel.BATTLE))
        assertEquals(70, SoundPrefs.volume(ctx, SoundChannel.CRIES))
        drag(sliders[1], 3)
        assertEquals(30, SoundPrefs.volume(ctx, SoundChannel.BATTLE))
        assertEquals(10, SoundPrefs.volume(ctx, SoundChannel.BUTTONS))
        assertEquals(SoundChannel.BATTLE, SoundId.HIT.group.channel)
        assertEquals(SoundChannel.BATTLE, SoundId.STAT_UP.group.channel)
        assertEquals(SoundChannel.BUTTONS, SoundId.TAB.group.channel)
        assertEquals(SoundChannel.BUTTONS, SoundId.GOOD.group.channel)
        assertEquals(SoundChannel.CRIES, SoundGroup.CRIES.channel)
        ctx.getSharedPreferences("ankigo_sound", 0).edit().clear().commit()
    }
}
