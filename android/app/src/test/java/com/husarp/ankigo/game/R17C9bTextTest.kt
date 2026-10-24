package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-9b: the sentence of every ability the game builds (the 102 of R17C-9a and the 115 more) and of every ability that does nothing here ("No effect in
 * AnkiGo battles: <why>"): plain, short, true and ending with a full stop. The real-data side (every ability of the species has one) is
 * [R17C9bRealDataTest].
 */
class R17C9bTextTest {
    private val noEffectPrefix = "No effect in AnkiGo battles: "

    @Test fun everyBuiltAbilityHasAShortPlainSentence() {
        assertEquals("102 + 115", 217, AbilityText.ids.size)
        assertEquals(AbilityText.ids, Abilities.BUILT)
        for (id in AbilityText.ids) {
            val t = AbilityText.of(id)
            assertNotNull(id, t)
            t!!
            assertTrue("$id ends with a full stop: $t", t.endsWith("."))
            assertTrue("$id starts with a capital: $t", t.first().isUpperCase())
            assertTrue("$id is short enough: ${t.length}", t.length <= 260)
            assertFalse("$id has no double space: $t", "  " in t)
            assertFalse("$id has no arrow glyph: $t", t.any { it in "→←↑↓➜➔" } || "->" in t)
            assertFalse("$id has no leftover template: $t", "\${" in t || "\$" in t)
            assertFalse("$id is not a no-effect sentence: $t", t.startsWith(noEffectPrefix))
            assertEquals(id, BattleView.normalizeAbility(AbilityText.name(id)!!))
        }
    }

    @Test fun theNumbersInTheSentencesAreTheCodes() {
        fun has(name: String, part: String) = assertTrue("$name should say '$part': ${AbilityText.of(name)}", AbilityText.of(name)!!.contains(part))
        has("Sand Force", "1.3 times")
        has("Steam Engine", "rises by ${AbilitiesMore.STEAM_ENGINE_STAGES}")
        has("Toxic Chain", "${AbilitiesMore.TOXIC_CHAIN_CHANCE} %")
        has("Slow Start", "${AbilitiesMore.SLOW_START_TURNS} turns")
        has("Quick Draw", "${AbilitiesMore.QUICK_DRAW_CHANCE} %")
        has("Triage", "+${AbilitiesMore.TRIAGE_PRIORITY}")
        has("Wonder Skin", "${AbilitiesMore.WONDER_SKIN_ACCURACY.toInt()} %")
        has("Sand Spit", "${FieldRules.WEATHER_TURNS} turns")
        has("Electric Surge", "${FieldRules.TERRAIN_TURNS} turns")
        has("Orichalcum Pulse", "1.33 times")
        has("Protosynthesis", "1.3 times")
        has("Protosynthesis", "1.5 times")
        has("Aura Break", "0.75 times")
        has("Normalize", "1.2 times")
        has("Gorilla Tactics", "1.5 times")
        has("Victory Star", "1.1 times")
        has("Water Compaction", "${AbilitiesMore.WATER_COMPACTION_STAGES}")
        has("Well-Baked Body", "${AbilitiesMore.WELL_BAKED_STAGES}")
        has("Fluffy", "0.5 times")
        has("Fluffy", "2 times")
    }

    @Test fun theAbilitiesThatDoNothingSayWhyInThePlannedWords() {
        val reasons = AbilityText.noEffectByReason
        val expected = mapOf(
            "there are no held items in fights." to 13,
            "it helps a partner Pokémon." to 12,
            "it works when Pokémon switch or run." to 9,
            "a fight ends when a Pokémon faints." to 6,
            "AnkiGo has no PP." to 1,
            "it only shows information." to 3,
            "it only changes how the Pokémon looks." to 1,
            "AnkiGo has no form changes." to 11,
            "AnkiGo has no critical hits." to 6,
            "there are no entry hazards in fights." to 1,
        )
        assertEquals(expected.keys, reasons.keys)
        for ((reason, n) in expected) assertEquals(reason, n, reasons.getValue(reason).size)
        assertEquals(63, AbilityText.noEffect.size)
        for (id in AbilityText.noEffect) {
            val t = AbilityText.of(id)
            assertNotNull(id, t)
            assertTrue("$id: $t", t!!.startsWith(noEffectPrefix) && t.endsWith("."))
            assertTrue("$id is not also built", id !in AbilityText.ids)
            assertEquals(id, BattleView.normalizeAbility(AbilityText.name(id)!!))
        }
        // the ones the audit named
        for (id in listOf("sniper", "superluck", "battlearmor", "shellarmor", "angerpoint", "merciless")) assertTrue(id, id in reasons.getValue("AnkiGo has no critical hits."))
        assertTrue("toxicdebris" in reasons.getValue("there are no entry hazards in fights."))
        assertTrue("pressure" in reasons.getValue("AnkiGo has no PP."))
        assertEquals("No effect in AnkiGo battles: AnkiGo has no PP.", AbilityText.of("Pressure"))
    }

    @Test fun anUnknownNameHasNoSentence() {
        assertNull(AbilityText.of("Nonsense"))
        assertNull(AbilityText.of(""))
        assertNull(AbilityText.of(null))
        assertNull(AbilityText.name("nonsense"))
        assertEquals("Soul-Heart", AbilityText.name("soulheart"))
        assertEquals(AbilityText.of("Run Away"), AbilityText.of("runaway"))
    }

    @Test fun theBreakableListCoversTheNewAbilitiesThatPromiseMoldBreaker() {
        for (id in listOf("bulletproof", "windrider", "eartheater", "wellbakedbody", "goodasgold", "wonderguard", "dazzling", "queenlymajesty", "armortail",
            "fluffy", "furcoat", "marvelscale", "icescales", "multiscale", "punkrock", "purifyingsalt", "waterbubble", "thermalexchange", "magmaarmor", "pastelveil",
            "immunity", "sweetveil", "aromaveil", "flowerveil", "wonderskin", "mirrorarmor", "lightmetal", "heavymetal", "aurabreak", "illuminate")) {
            assertTrue(id, id in Abilities.BREAKABLE)
        }
        for (id in listOf("stamina", "mummy", "tabletsofruin", "neutralizinggas", "fullmetalbody", "prismarmor", "shadowshield")) assertFalse(id, id in Abilities.BREAKABLE)
    }
}
