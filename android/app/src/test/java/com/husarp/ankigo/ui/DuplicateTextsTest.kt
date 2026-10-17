package com.husarp.ankigo.ui

import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

/** R3-X2 item 4: how the "Auto-release duplicates" event reads in the floating texts and the battle log (made-up names). */
class DuplicateTextsTest {
    private fun dup(result: DuplicateResult, released: String? = null, kept: String? = null) =
        GameEvent.Duplicate("Wildling", 23, 20, result, releasedName = released, keptBecause = kept)

    @Test fun aSwapFloatsOverTheWildOneOnly() {
        assertEquals(listOf(FloatText(false, "Swapped for Lv 23", FloatKind.INFO)), FloatTexts.of(dup(DuplicateResult.SWAPPED, "Wildling")))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(dup(DuplicateResult.DEFEATED)))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(dup(DuplicateResult.KEPT_BOTH, kept = "a favourite")))
    }

    private fun logged(vararg tail: GameEvent): List<String> {
        var t = 1L
        val b = BattleLogBuilder(5, { t++ })
        b.feed(GameEvent.Encounter(1, "Wildling", 23, false, Tier.NORMAL, 30, 30, true, false))
        b.feed(listOf(*tail))
        return b.battles[0].result.map { it.text }
    }

    @Test fun theBattleLogExplainsEachResult() {
        val caught = GameEvent.Caught("u", 1, "Wildling", 23, false, null, false, false)
        assertEquals(
            listOf("Caught Wildling · Lv 23 · sent to the box", "Swapped for Lv 23 · your Lv 20 Zappy was released"),
            logged(caught, dup(DuplicateResult.SWAPPED, "Zappy")),
        )
        assertEquals(
            listOf("Caught Wildling · Lv 23 · sent to the box", "Kept both · your Lv 20 copy is a favourite"),
            logged(caught, dup(DuplicateResult.KEPT_BOTH, kept = "a favourite")),
        )
        assertEquals(
            listOf("Wildling was defeated · 40 XP", "Not higher than your Lv 20 copy · defeated, not caught"),
            logged(GameEvent.Defeated(1, "Wildling", 40), dup(DuplicateResult.DEFEATED)),
        )
    }
}
