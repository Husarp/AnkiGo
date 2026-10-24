package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17C-9a: the sentence of every ability the game builds (shown under the ability in the details): plain, short, true and ending with a full stop. */
class R17C9aTextTest {
    @Test fun everyBuiltAbilityHasAShortPlainSentence() {
        assertTrue("102 of R17C-9a and 115 of R17C-9b are built: ${Abilities.BUILT.size}", Abilities.BUILT.size in 210..225) // R17C-9b: was 100..110
        for (id in Abilities.BUILT) {
            val t = AbilityText.of(id)
            assertNotNull(id, t)
            t!!
            assertTrue("$id ends with a full stop: $t", t.endsWith("."))
            assertTrue("$id starts with a capital: $t", t.first().isUpperCase())
            assertTrue("$id is short enough: ${t.length}", t.length <= 260)
            assertFalse("$id has no double space: $t", "  " in t)
            assertFalse("$id has no arrow glyph: $t", t.any { it in "→←↑↓➜➔" } || "->" in t)
            assertFalse("$id has no leftover template: $t", "\${" in t || "\$" in t)
            val name = AbilityText.name(id)
            assertNotNull(id, name)
            assertEquals(id, BattleView.normalizeAbility(name!!))
        }
    }

    @Test fun theNameIsUnderstoodInEverySpelling() {
        val keen = AbilityText.of("Keen Eye")
        assertNotNull(keen)
        for (spelling in listOf("keeneye", "KEEN EYE", "keen-eye", "Keen  Eye")) assertEquals(spelling, keen, AbilityText.of(spelling))
        assertNull(AbilityText.of(null))
        assertNull(AbilityText.of(""))
        assertNull("no such ability", AbilityText.of("Nonsense")) // R17C-9b: Pressure and the others that do nothing have a sentence now
        assertNull(AbilityText.name("nonsense"))
    }

    @Test fun theNumbersInTheSentencesAreTheCodes() {
        assertTrue(AbilityText.of("Static")!!.contains("${Abilities.CONTACT_CHANCE} %"))
        assertTrue(AbilityText.of("Shed Skin")!!.contains("${Abilities.SHED_SKIN_CHANCE} %"))
        assertTrue(AbilityText.of("Stench")!!.contains("${Abilities.STENCH_CHANCE} %"))
        assertTrue(AbilityText.of("Technician")!!.contains("${Abilities.TECHNICIAN_MAX_POWER}"))
        assertTrue(AbilityText.of("Cursed Body")!!.contains("${Abilities.CURSED_BODY_TURNS} turns") && AbilityText.of("Cursed Body")!!.contains("${Abilities.CURSED_BODY_CHANCE} %"))
        assertTrue(AbilityText.of("Drizzle")!!.contains("${FieldRules.WEATHER_TURNS} turns"))
    }

    @Test fun theBreakableListMatchesWhatTheSentencesPromise() {
        // the abilities whose sentence says Mold Breaker's user ignores them must be on the list
        for (id in listOf("sturdy", "levitate", "clearbody", "solidrock", "thickfat", "voltabsorb", "soundproof", "overcoat", "unaware", "simple", "contrary")) assertTrue(id, id in Abilities.BREAKABLE)
        for (id in listOf("roughskin", "static", "intimidate", "moldbreaker", "magicguard", "synchronize")) assertFalse(id, id in Abilities.BREAKABLE)
    }
}
