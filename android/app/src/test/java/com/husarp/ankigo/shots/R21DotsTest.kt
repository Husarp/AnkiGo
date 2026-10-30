package com.husarp.ankigo.shots

import android.view.View
import android.widget.FrameLayout
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.sound.SoundPrefs
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R21DotsTest {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun preferencesDefaultAndPersistIndependently() {
        val c = Shots.files(paparazzi.context)
        c.getSharedPreferences("ankigo_ui", 0).edit().remove("flyingTurnDots").commit()
        c.getSharedPreferences("ankigo_sound", 0).edit().remove("typeSounds").commit()
        assertFalse(AppPrefs.flyingTurnDots(c)); assertTrue(SoundPrefs.typeSounds(c))
        AppPrefs.setFlyingTurnDots(c, true); SoundPrefs.setTypeSounds(c, false)
        assertTrue(AppPrefs.flyingTurnDots(c)); assertFalse(SoundPrefs.typeSounds(c))
        AppPrefs.setFlyingTurnDots(c, false); SoundPrefs.setTypeSounds(c, true)
    }

    @Test fun emptyBarKeepsDotsInMiddleThenHonoursTheirPlaceWithoutHiding() {
        val c = Shots.files(paparazzi.context)
        val bar = ActionBarView(c)
        bar.dotsOn = false; bar.dotsPlace = DotsPlace.RIGHT_END
        assertEquals(listOf("mine", "dots", "theirs"), bar.segmentOrder())
        assertEquals(2, bar.flightSources().size)
        bar.observe(GameEvent.Attack(true, "A", "B", "Tackle", Type.NORMAL, Category.PHYSICAL, 3, 1, 1.0,
            false, false, false, 10, 20, 20, 20))
        assertEquals(listOf("mine", "theirs", "dots"), bar.segmentOrder())
        bar.dotsOn = false
        assertEquals(2, bar.flightSources().size)
    }

    @Test fun bothDotsFlyTogetherSameColoursShareDestinationGreyOnlyFadesAndCancelCleansUp() {
        val c = Shots.files(paparazzi.context)
        val layer = FrameLayout(c)
        layer.layout(0, 0, 400, 400)
        val sources = List(3) { i -> View(c).also { layer.addView(it); it.layout(180 + i * 12, 350, 190 + i * 12, 360) } }
        val attacker = View(c).also { layer.addView(it); it.layout(40, 100, 100, 160) }
        val original = layer.childCount
        val flight = TurnDotFlight(layer)
        flight.start(sources, List(3) { attacker }, listOf(3, 3, StudyCharge.OK), 500)
        flight.seek(250)
        val a = layer.getChildAt(original); val b = layer.getChildAt(original + 1); val grey = layer.getChildAt(original + 2)
        assertTrue(a.y < sources[0].top && b.y < sources[1].top)
        assertEquals(a.y, b.y, .01f)
        assertEquals(sources[2].left.toFloat(), grey.x, .01f)
        assertEquals(sources[2].top.toFloat(), grey.y, .01f)
        assertEquals(.5f, grey.alpha, .01f)
        flight.seek(450)
        assertTrue(a.alpha < 1f && a.scaleX < .3f)
        flight.cancel()
        assertEquals(original, layer.childCount)
        flight.seek(500)
        assertEquals(original, layer.childCount)
    }

    @Test fun consumingDotsDoesNotChangeTheNextRound() {
        val c = StudyCharge()
        fun counted(button: Int) = GameEvent.Counted(button, 10, 1, 1, false)
        c.observe(counted(1), 2); c.observe(counted(3), 2)
        c.observe(GameEvent.RoundStart(1.0, 1, true), 2)
        c.consume(); assertTrue(c.filled.isEmpty())
        c.observe(counted(4), 2)
        assertEquals(listOf(4), c.filled)
    }
}
