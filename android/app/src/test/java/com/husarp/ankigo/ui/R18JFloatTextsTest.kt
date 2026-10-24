package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R18-J (PLAN 259 / 217 check): which Effect kinds float in a status colour with a status word, and which stay a plain red number.
 * Every Effect carries the side's status (fx), so a Leech Seed / binding / Salt Cure / weather hit on a burned Pokémon must NOT read "Burning".
 */
class R18JFloatTextsTest {
    private fun fx(kind: EffectKind, status: String? = null, amount: Int = 5, label: String? = null, onPlayer: Boolean = true) =
        GameEvent.Effect(onPlayer, "text", 15, 40, status, kind, null, 0, amount, label)

    @Test fun onlyTheEnginesBurnAndPoisonTicksNameTheirStatus() {
        assertEquals(listOf(FloatText(true, "−5 HP Burning", FloatKind.STATUS_DAMAGE, "brn")), FloatTexts.of(fx(EffectKind.STATUS_DAMAGE, "brn")))
        assertEquals(listOf(FloatText(true, "−5 HP Poisoning", FloatKind.STATUS_DAMAGE, "psn")), FloatTexts.of(fx(EffectKind.STATUS_DAMAGE, "psn")))
        assertEquals(listOf(FloatText(false, "−5 HP Poisoning", FloatKind.STATUS_DAMAGE, "tox")), FloatTexts.of(fx(EffectKind.STATUS_DAMAGE, "tox", onPlayer = false)))
    }

    @Test fun residualHitsOfABurnedOrPoisonedOneStayPlainRed() {
        for (st in listOf("brn", "psn", "tox", null)) {
            assertEquals(listOf(FloatText(true, "−5 HP", FloatKind.DAMAGE)), FloatTexts.of(fx(EffectKind.RESIDUAL, st)))   // Leech Seed, binding, Curse, Nightmare, Salt Cure, weather
        }
    }

    @Test fun aResidualWithItsOwnLabelFloatsLabelThenNumber() {
        assertEquals(
            listOf(FloatText(true, "Solar Power", FloatKind.INFO), FloatText(true, "−5 HP", FloatKind.DAMAGE)),
            FloatTexts.of(fx(EffectKind.RESIDUAL, "brn", label = "Solar Power")),
        )
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.RESIDUAL, "brn", amount = 0, label = "Solar Power")))
    }
}
