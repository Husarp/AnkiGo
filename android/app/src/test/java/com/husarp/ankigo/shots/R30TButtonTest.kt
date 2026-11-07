package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.sound.ButtonSounds
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.sound.SoundId
import com.husarp.ankigo.sound.SoundPrefs
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30TButtonTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun ordinaryProgrammaticAndAccessibilityClicksGetOneDefaultSound() {
        val ctx = Shots.files(paparazzi.context)
        val view = Ui.text(ctx, 14f, 700, R.color.ag_text)
        var clicks = 0
        view.setOnClickListener { clicks++ }
        val before = GameSound.uiSerial
        assertTrue(view.performClick())
        assertEquals(1, clicks)
        assertEquals(before + 1, GameSound.uiSerial)
    }

    @Test fun dedicatedSoundsTakePriorityEvenWhenTheirGroupIsOff() {
        val ctx = Shots.files(paparazzi.context)
        val view = Ui.text(ctx, 14f, 700, R.color.ag_text)
        for (id in listOf(SoundId.AGAIN, SoundId.HARD, SoundId.GOOD, SoundId.EASY, SoundId.BUY, SoundId.BALL_BTN)) {
            SoundPrefs.setGroupOn(ctx, id.group, false)
            view.setOnClickListener { GameSound.ui(ctx, id) }
            val before = GameSound.uiSerial
            view.performClick()
            assertEquals(before + 1, GameSound.uiSerial)
        }
    }

    @Test fun noListenerMeansNoClickOrSound() {
        val ctx = Shots.files(paparazzi.context)
        val view = Ui.text(ctx, 14f, 700, R.color.ag_text)
        val before = GameSound.uiSerial
        assertFalse(ButtonSounds.click(view) { false })
        assertEquals(before, GameSound.uiSerial)
    }
}
