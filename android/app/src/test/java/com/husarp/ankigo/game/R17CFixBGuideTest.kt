package com.husarp.ankigo.game

import com.husarp.ankigo.help.GameGuide
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guide explains Smart choices and the Main’s Struggle fallback. */
class R17CFixBGuideTest {
    private val battles = GameGuide.chapter(GameGuide.BATTLES)!!.let { (listOf(it.intro) + it.points).joinToString(" ") }

    @Test fun theSmartSentenceNamesWhatIsSkippedAndTheFallback() {
        assertTrue(battles, battles.contains("Smart and Smartest avoid ineffective status moves"))
        assertTrue(battles, battles.contains("attacks blocked by types or abilities"))
        assertTrue(battles, battles.contains("such as Wonder Guard"))
        assertTrue(battles, battles.contains("If your Main cannot damage the target, it uses Struggle; the wild Pokémon may fall back to another move"))
    }

    @Test fun theAbilityTheGuideNamesIsInTheGame() {
        assertTrue(AbilityText.of("wonderguard") != null)
    }
}
