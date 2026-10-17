package com.husarp.ankigo.ui

import com.husarp.ankigo.sound.SoundId
import com.husarp.ankigo.study.StudyRules

/** Pure rules of the button click sounds and the Sound settings (pick 84B), so they are JVM-tested. */
object SoundUiRules {
    /** The slider goes 0..100 in steps of 10. */
    const val STEP = 10

    /** The answer click for [ease] (1..[buttons]) on a card with [buttons] answer buttons: 2 = Again/Good, 3 = Again/Good/Easy, 4 = all four. */
    fun answerSound(buttons: Int, ease: Int): SoundId = when (StudyRules.gameButton(buttons, ease)) {
        1 -> SoundId.AGAIN
        2 -> SoundId.HARD
        3 -> SoundId.GOOD
        else -> SoundId.EASY
    }

    /** A stored or dragged volume as the nearest slider step (0, 10 ... 100). */
    fun snapVolume(pct: Int): Int = ((pct.coerceIn(0, 100) + STEP / 2) / STEP) * STEP
}
