package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R32PTextTest {
    @Test fun timerBallUsesPerRound() {
        assertEquals("+${com.husarp.ankigo.game.Tuning.TIMER_BONUS_PER_ROUND}% per round, max +${com.husarp.ankigo.game.Tuning.SPECIAL_BONUS}%", com.husarp.ankigo.game.Items.specialSummary("timer-ball"))
    }

    @Test fun recurringEffectsUseReadableFractionsPerRound() {
        for ((id, fraction) in listOf("brn" to "1/16", "psn" to "1/8")) {
            assertTrue(StatusInfo.of(id)!!.line.contains("$fraction of max HP per round"))
            assertTrue(StatusInfo.effect(id).contains("$fraction of max HP per round"))
        }
        assertTrue(StatusInfo.of("tox")!!.line.contains("an extra 1/16 of max HP per round, up to 15/16 of max HP"))
        assertTrue(StatusInfo.effect("tox").contains("per round, up to 15/16 of max HP"))
        for ((key, fraction) in listOf("leechseed" to "1/8", "saltcure" to "1/8", "ingrain" to "1/16", "aquaring" to "1/16", "curse" to "1/4", "nightmare" to "1/4", "partiallytrapped" to "1/8")) {
            assertTrue(key, ConditionExplanation.effects.getValue(key).contains("$fraction of max HP per round"))
        }
        assertTrue(ConditionExplanation.effects.getValue("saltcure").contains("Water or Steel lose 1/4"))
    }
}
