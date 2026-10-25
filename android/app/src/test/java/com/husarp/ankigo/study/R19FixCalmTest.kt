package com.husarp.ankigo.study

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R19-FIX (RV-6): a calm card that comes on AFTER the question was drawn (the hold arrives late) cuts the card's sound; Continue must play
 * it again, so [StudyActivity.applyCalm] sets `avHeld` before it stops the audio. The screen itself needs a device; this pins the order in
 * the source, like R19VWidgetTaskTest does for the widget flags.
 */
class R19FixCalmTest {
    @Test fun theCalmCardHoldsTheSoundBeforeItStopsIt() {
        val src = File("src/main/java/com/husarp/ankigo/StudyActivity.kt").readText()
        val body = src.substringAfter("private fun applyCalm()").substringBefore("private fun showButtons()")
        val on = body.substringAfter("if (on) {").substringBefore("} else if")
        val held = on.indexOf("avHeld = true")
        val stop = on.indexOf("stopAudio()")
        assertTrue("avHeld = true is in the calm-on branch", held >= 0)
        assertTrue("stopAudio() is in the calm-on branch", stop >= 0)
        assertTrue("avHeld is set before the audio stops", held < stop)
        assertTrue("Continue replays the held sound", body.contains("if (avHeld) { avHeld = false; playAv(avAuto) }"))
    }
}
