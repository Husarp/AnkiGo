package com.husarp.ankigo.game

import com.husarp.ankigo.help.GameGuide
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-FIX (coders' note): the guide's Smart / Smartest sentence says what the move choice really does after the review fixes: no status
 * move that would do nothing, no attack that can't hurt the target (its type, or an ability such as Wonder Guard), Struggle for the
 * Main and any move for a wild one when nothing else is left.
 */
class R17CFixBGuideTest {
    private val battles = GameGuide.chapter(GameGuide.BATTLES)!!.let { (listOf(it.intro) + it.points).joinToString(" ") }

    @Test fun theSmartSentenceNamesWhatIsSkippedAndTheFallback() {
        assertTrue(battles, battles.contains("Smart and Smartest never use a status move that would do nothing"))
        assertTrue(battles, battles.contains("a screen that is already up"))
        assertTrue(battles, battles.contains("never an attack the target is immune to (by its type or by an ability, such as Wonder Guard)"))
        assertTrue(battles, battles.contains("With nothing else to use, your Main uses Struggle and a wild Pokémon uses any move"))
    }

    @Test fun theAbilityTheGuideNamesIsInTheGame() {
        assertTrue(AbilityText.of("wonderguard") != null)
    }
}
