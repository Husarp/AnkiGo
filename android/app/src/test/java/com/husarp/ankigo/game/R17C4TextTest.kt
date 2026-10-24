package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17C-4: every move [MovePower] builds says one true sentence; no fake "Power 60" is shown for a move whose power is not its listed number. */
class R17C4TextTest {
    private val data = R17C4Fixture.data()
    private val built: List<Move> = R17C4Fixture.MOVES.filter { it.id != "shadowball2" }

    private fun move(id: String) = data.move(id) ?: error("no move $id")

    @Test fun theTicketBuildsEightyMoves() {
        assertEquals(80, built.map { it.id }.toSet().size)
    }

    @Test fun everyBuiltMoveGetsOneSentenceWithAFullStop() {
        for (m in built) {
            val lines = MovePower.describe(m)
            assertEquals(m.id, 1, lines.size)
            assertTrue("${m.id}: '${lines[0]}'", lines[0].isNotBlank() && lines[0].endsWith(".") && lines[0][0].isUpperCase())
            assertFalse("${m.id}: no arrow glyphs", lines[0].any { it in "→←↑↓➜" })
        }
    }

    @Test fun otherMovesGetNothingFromThisSet() {
        assertEquals(emptyList<String>(), MovePower.describe(move("tackle")))
        assertEquals(emptyList<String>(), MovePower.describe(move("growl")))
        assertEquals(emptyList<String>(), MovePower.describe(move("shadowball2")))
    }

    @Test fun theMoveTextCarriesItAndNeverFallsBackToNoExtraEffect() {
        for (m in built) {
            val text = MoveText.describe(m)
            assertTrue("${m.id}: '$text'", MovePower.describe(m)[0] in text)
            assertFalse("${m.id}: '$text'", "no extra effect" in text)
            assertTrue(text.endsWith("."))
        }
    }

    @Test fun showsPowerIsFalseWhereThePowerIsNotTheListedNumber() {
        val noNumber = listOf(
            "lowkick", "grassknot", "heavyslam", "heatcrash", "flail", "reversal", "eruption", "waterspout", "dragonenergy", "crushgrip", "wringout",
            "hardpress", "gyroball", "electroball", "return", "pikapapow", "veeveevolley", "frustration", "storedpower", "powertrip", "punishment",
            "magnitude", "present", "trumpcard", "ragefist", "psywave", "endeavor", "finalgambit", "counter", "mirrorcoat", "metalburst", "comeuppance",
            "beatup", "bide",
        )
        for (id in noNumber) assertFalse(id, MoveText.showsPower(move(id)))
        // a move whose listed power is its real (first) power keeps showing it
        for (id in listOf("hex", "facade", "venoshock", "knockoff", "tripleaxel", "echoedvoice", "rollout", "lastrespects", "retaliate", "fakeout", "poltergeist", "hiddenpower")) {
            assertTrue(id, MoveText.showsPower(move(id)))
        }
    }

    @Test fun theNumbersInTheTextsAreTheOnesTheRulesUse() {
        fun say(id: String) = MovePower.describe(move(id))[0]
        assertTrue(say("eruption").contains("150")); assertTrue(say("crushgrip").contains("120")); assertTrue(say("hardpress").contains("100"))
        assertTrue(say("return").contains("102")); assertTrue(say("frustration").contains("102"))
        assertTrue(say("ragefist").contains("350")); assertTrue(say("punishment").contains("200"))
        assertTrue(say("echoedvoice").contains("40") && say("echoedvoice").contains("200"))
        assertTrue(say("furycutter").contains("160"))
        assertTrue(say("tripleaxel").contains("20, 40, then 60")); assertTrue(say("triplekick").contains("10, 20, then 30"))
        assertTrue(say("trumpcard").contains("40") && say("trumpcard").contains("PP"))
        assertTrue(say("lastrespects").contains("fainted")); assertTrue(say("retaliate").contains("fainted"))
        assertTrue(say("knockoff").contains("never takes"))
    }
}
