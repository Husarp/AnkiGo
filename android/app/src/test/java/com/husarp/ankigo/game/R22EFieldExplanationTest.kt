package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R22EFieldExplanationTest {
    private val data = TestData.world()
    private val main = TestData.mon(data, 25, 12, listOf("tackle"), uid = "main")
    private val battle = Battle(TestData.mon(data, 92, 12, listOf("lick"), uid = "wild"), Tier.NORMAL)
    private fun view() = SimpleView.of(data, battle, main)

    @Test fun rainUsesEngineMultiplierAndCurrentAbility() {
        battle.field.weather = "rain"
        main.ability = "Swift Swim"
        val text = FieldRules.explain("rain", view())
        assertTrue(text.contains("Water moves ×1.5"))
        assertTrue(text.contains("Fire moves ×0.5"))
        assertTrue(text.contains("Swift Swim"))
        assertTrue(text.contains("Doubles its Speed"))
    }

    @Test fun suppressedWeatherDoesNotPromiseBoosts() {
        battle.field.weather = "rain"
        main.ability = "Cloud Nine"
        val text = FieldRules.explain("rain", view())
        assertTrue(text.contains("suppressed"))
        assertFalse(text.contains("×1.5"))
    }

    @Test fun terrainReadsEffectiveGrounding() {
        battle.field.terrain = "electric"
        main.ability = "Levitate"
        assertTrue(FieldRules.explain("electric", view()).contains("off the ground"))
        battle.field.rooms["gravity"] = 3
        assertTrue(FieldRules.explain("electric", view()).contains("Electric moves ×1.3"))
    }

    @Test fun weatherDamageRespectsImmunityAndUsesActualMaxHp() {
        battle.field.weather = "sand"
        main.ability = "Overcoat"
        assertTrue(FieldRules.explain("sand", view()).contains("no weather damage"))
        main.ability = "Static"
        assertTrue(FieldRules.explain("sand", view()).contains("loses ${maxOf(1, view().maxHp(true) / 16)} HP"))
    }

    @Test fun magicRoomHasAVisibleChipAndExplainsSuppression() {
        battle.field.rooms["magicroom"] = 3
        val mark = com.husarp.ankigo.ui.ConditionMarks.fromBattle(battle).single { it.second.key == "magicroom" }
        assertEquals(com.husarp.ankigo.ui.ConditionMarks.Slot.FIELD, mark.first)
        assertEquals(3, mark.third)
        assertNotNull(com.husarp.ankigo.ui.FieldIcons.of("magicroom"))
        assertTrue(FieldRules.explain("magicroom", view()).contains("Held items do nothing"))
        assertTrue(FieldRules.explain("magicroom", view()).contains("keeps its item"))
    }

    @Test fun everyFieldChipHasAnExplanation() {
        for (key in listOf("rain", "sun", "sand", "hail", "snow", "electric", "grassy", "misty", "psychic", "trickroom", "gravity", "wonderroom", "magicroom", "mudsport", "watersport", "iondeluge")) {
            battle.field.weather = if (key in listOf("rain", "sun", "sand", "hail", "snow")) key else null
            assertTrue(key, FieldRules.explain(key, view()).isNotBlank())
        }
    }
}
