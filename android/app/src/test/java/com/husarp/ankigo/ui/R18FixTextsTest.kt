package com.husarp.ankigo.ui

import com.husarp.ankigo.game.ItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R18 fix batch (FIX-2): plurals of a coin gain (RV-7), the texts of RV-4 / NICE-1 / NICE-2 / NICE-4. */
class R18FixTextsTest {
    private val main = File("src/main/java/com/husarp/ankigo")
    private fun src(rel: String) = File(main, rel).readText()

    @Test fun aGainIsSingularForOne() {
        assertEquals("+1 coin", CoinText.plus(1))
        assertEquals("+2 coins", CoinText.plus(2))
        assertEquals("+25 coins", CoinText.plus(25))
    }

    @Test fun noPlaceBuildsAPluralByHand() {
        for (f in listOf("ui/EventPlayer.kt", "ui/BattleLogActivity.kt", "live/LiveSession.kt", "ui/AnkimonExportFlow.kt")) {
            val t = src(f)
            assertFalse("$f has a hand-made \"coins\"", Regex("\\$\\{?[A-Za-z.()]+}? coins\"").containsMatchIn(t))
        }
    }

    @Test fun theOpenOnLineIsTrue() {
        val line = "Where the app icon and the Buddy widget take you"
        assertTrue(SettingsCatalog.byId("openOn")!!.explain.startsWith(line))
        assertTrue(src("ui/SettingsActivity.kt").contains("\"$line.\""))
    }

    @Test fun theMovementLineSaysReducedMotionTurnsItOff() {
        assertTrue(SettingsCatalog.byId("pokemonMovement")!!.explain.contains("off while reduced motion is on"))
        assertTrue(src("ui/SettingsActivity.kt").contains("Off while reduced motion is on."))
    }

    @Test fun theBenchItemTextAndTheLevelTextAreTrue() {
        assertTrue(ItemInfo.facts(null, "potion").any { it.text.contains("Used on a Pokémon on the bench, it never brings an attack.") })
        assertEquals("Stats grow with level. Wins and catches give XP.", StatInfo.LEVEL.text)
    }
}
