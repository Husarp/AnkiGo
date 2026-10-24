package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.ContinueBar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * R18-J (PLAN 261): the Battle view of a tall window shows ONE green Continue: the section with the deck name under the top bar.
 * The top bar (Back, Battle, Pop out, menu) has no mini button any more. Shots: r18j-battle-continue-phone-light / -dark.
 */
class R18JBattleShots {
    @get:Rule val paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val deck = Anki.Deck(7, "Languages::Sample Core 2k", 3, 31, 8, false)

    private fun all(v: View): List<View> = listOf(v) + (if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList())

    @Test fun oneContinueOnTheBattleScreen() {
        paparazzi.shootWith("r18j-battle-continue", listOf(Size.PHONE)) { ctx ->
            val b = BattleShotKit.bound(ctx)
            b.activity.setContinueDeck(deck)
            val buttons = all(b.root).filter { it.contentDescription?.toString()?.startsWith("Continue") == true }
            // the section's own bar ("Continue · Deck, N due") is the only Continue on screen: no "Continue <deck>, N due" mini button
            assertEquals(buttons.map { it.contentDescription.toString() }.toString(), 1, buttons.size)
            assertEquals("Continue · Sample Core 2k, 42 due", buttons[0].contentDescription.toString())
            assertEquals(View.VISIBLE, buttons[0].visibility)
            assertNull(all(b.root).firstOrNull { it.contentDescription?.toString() == ContinueBar.description(deck) })
            assertNotNull(all(b.root).firstOrNull { it.contentDescription?.toString() == "Pop out: small battle window" })
            b.root
        }
    }
}
