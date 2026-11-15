package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R34CTextTest {
    @Test fun beastBoostExplainsTemporaryStageRatherThanPermanentStatPoint() {
        val text = AbilityText.of("Beast Boost")!!
        assertTrue(text, text.contains("1 stage (×1.5) until this fight ends"))
        assertTrue(text, text.contains("Once per fight"))
        assertTrue(text, text.contains("half HP or below"))
    }

    @Test fun allAbilityPercentagesStayAttachedToTheirNumbers() {
        for (id in listOf("static", "poisonpoint", "flamebody", "shedskin", "hustle", "sandveil", "snowcloak",
            "cutecharm", "effectspore", "stench", "poisontouch", "cursedbody", "toxicchain", "wonderskin", "quickdraw")) {
            val text = AbilityText.of(id)!!
            assertFalse("$id: $text", Regex("\\d\\s+%").containsMatchIn(text))
            assertTrue("$id: $text", Regex("\\d%").containsMatchIn(text))
        }
    }
}
