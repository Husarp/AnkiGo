package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9.attacks
import com.husarp.ankigo.game.R17C9.effects
import com.husarp.ankigo.game.R17C9.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-9a: the abilities of the turn: what happens on entering (Intimidate, Download, Trace, the weather makers), stat rules (Clear Body, Hyper Cutter,
 * Simple, Contrary, Defiant ...), statuses and lasting conditions (Insomnia, Limber, Own Tempo, Oblivious ...), speed and priority, sleep (Early Bird),
 * the end of a round (Shed Skin, Speed Boost, Ice Body, Dry Skin ...), damage that is not a hit (Magic Guard, Rock Head, Heatproof, Liquid Ooze) and
 * Regenerator. Fixtures in [R17C9].
 */
class R17C9aTurnTest {
    private fun round(main: Mon, b: Battle, rng: Random = Random(1)) = BattleEngine.Round(R17C9.data, b, main, 1.0, 1.0, rng)

    // ---------------------------------------------------------------- entering

    @Test fun intimidateLowersTheOtherAttackWhenItEnters() {
        val m = R17C9.main("Intimidate", "tackle"); val b = R17C9.battle(R17C9.wild("", 801, "tackle"))
        val e = R17C9.round(m, b, "tackle")
        assertEquals(-1, b.enemy.boosts[0])
        assertTrue(e.lines().any { "Fastie's Intimidate!" in it })
        assertTrue(e.lines().any { "Slowpoke's Attack fell!" in it })
        assertEquals("a float with its name", "Intimidate", e.effects().first { it.label == "Intimidate" }.label)
        // once: the next round does not lower it again
        R17C9.round(m, b, "tackle")
        assertEquals(-1, b.enemy.boosts[0])
        // the wild one has it too, against the Main
        val b2 = R17C9.battle(R17C9.wild("Intimidate", 801, "tackle"))
        R17C9.round(R17C9.main("", "tackle"), b2, "tackle")
        assertEquals(-1, b2.player.boosts[0])
    }

    @Test fun intimidateDoesNotWorkOnTheImmuneAndIsStoppedByClearBody() {
        for (ab in listOf("Inner Focus", "Own Tempo", "Oblivious", "Scrappy")) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle"))
            val e = R17C9.round(R17C9.main("Intimidate", "tackle"), b, "tackle")
            assertEquals(ab, 0, b.enemy.boosts[0])
            assertTrue(ab, e.lines().any { "$ab: Intimidate doesn't work on it." in it })
        }
        for (ab in listOf("Clear Body", "White Smoke", "Hyper Cutter")) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle"))
            val e = R17C9.round(R17C9.main("Intimidate", "tackle"), b, "tackle")
            assertEquals(ab, 0, b.enemy.boosts[0])
            assertTrue(ab, e.lines().any { "$ab prevents" in it })
        }
        // a substitute is not affected
        val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.enemy.volatiles["substitute"] = 50
        R17C9.round(R17C9.main("Intimidate", "tackle"), b, "tackle")
        assertEquals(0, b.enemy.boosts[0])
    }

    @Test fun intimidateSetsOffDefiantCompetitiveRattledContrary() {
        fun stages(ab: String): List<Int> { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("Intimidate", "tackle"), b, "tackle"); return b.enemy.boosts.toList() }
        assertEquals("Defiant: -1 then +2", 1, stages("Defiant")[0])
        assertEquals("Competitive: Attack -1, Sp. Atk +2", listOf(-1, 2), stages("Competitive").slice(0..2 step 2))
        assertEquals("Rattled: Speed +1", listOf(-1, 1), listOf(stages("Rattled")[0], stages("Rattled")[4]))
        assertEquals("Contrary: +1", 1, stages("Contrary")[0])
        assertEquals("Simple: -2", -2, stages("Simple")[0])
    }

    @Test fun downloadRaisesTheStatTheFoeIsWeakerIn() {
        fun stage(wildSp: Int): List<Int> { val b = R17C9.battle(R17C9.wild("", wildSp, "tackle")); R17C9.round(R17C9.main("Download", "tackle"), b, "tackle"); return b.player.boosts.toList() }
        assertEquals("a low Sp. Def: Sp. Atk", listOf(0, 0, 1, 0, 0, 0, 0), stage(809))
        assertEquals("a low Defense: Attack", listOf(1, 0, 0, 0, 0, 0, 0), stage(810))
        assertEquals("equal: Attack", listOf(1, 0, 0, 0, 0, 0, 0), stage(801))
    }

    @Test fun traceCopiesTheFoesAbilityAndSetsItOff() {
        val m = R17C9.main("Trace", "tackle"); val b = R17C9.battle(R17C9.wild("Intimidate", 801, "tackle"))
        val e = R17C9.round(m, b, "tackle")
        assertEquals("intimidate", b.player.notes[Abilities.OVERRIDE])
        assertEquals("intimidate", round(m, b).ability(true))
        assertTrue(e.lines().any { "Fastie traced Slowpoke's Intimidate!" in it })
        assertEquals("the copy acts: the wild one is intimidated", -1, b.enemy.boosts[0])
        assertEquals("and it intimidates the Main too", -1, b.player.boosts[0])
        // it can't copy these
        for (ab in listOf("Trace", "Illusion", "Imposter", "Multitype", "")) {
            val b2 = R17C9.battle(R17C9.wild(ab, 801, "tackle")); val m2 = R17C9.main("Trace", "tackle")
            R17C9.round(m2, b2, "tackle")
            assertNull("'$ab'", b2.player.notes[Abilities.OVERRIDE])
            assertEquals("trace", round(m2, b2).ability(true))
        }
    }

    @Test fun theWeatherMakersStartTheirWeatherForFiveRounds() {
        for ((ab, weather) in listOf("Drizzle" to "rain", "Drought" to "sun", "Sand Stream" to "sand", "Snow Warning" to "snow")) {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val e = R17C9.round(R17C9.main(ab, "tackle"), b, "tackle")
            assertEquals(ab, weather, b.field.weather)
            assertEquals("$ab: the round it started in counts", 4, b.field.weatherTurns)
            assertTrue(ab, e.lines().any { "$ab!" in it })
            // the wild one makes weather too
            val b2 = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("", "tackle"), b2, "tackle")
            assertEquals(ab, weather, b2.field.weather)
            // the same weather already up: nothing new
            val b3 = R17C9.battle(R17C9.wild("", 801, "tackle")); b3.field.weather = weather; b3.field.weatherTurns = 3
            val e3 = R17C9.round(R17C9.main(ab, "tackle"), b3, "tackle")
            assertFalse("$ab: no second start", e3.lines().any { "$ab!" in it })
        }
    }

    @Test fun moldBreakerTeravoltAndTurboblazeSayTheirLine() {
        for ((ab, text) in listOf("Mold Breaker" to "breaks the mold!", "Teravolt" to "is radiating a bursting aura!", "Turboblaze" to "is radiating a blazing aura!")) {
            val e = R17C9.round(R17C9.main(ab, "tackle"), R17C9.battle(R17C9.wild("", 801, "tackle")), "tackle")
            assertTrue(ab, e.lines().any { "Fastie $text" in it })
        }
    }

    // ---------------------------------------------------------------- stat rules

    @Test fun clearBodyHyperCutterBigPecksKeenEyeStopTheirDrops() {
        // ability, the move, the stat index that must stay 0
        for ((ab, move, idx) in listOf(Triple("Clear Body", "growl", 0), Triple("White Smoke", "leer", 1), Triple("Hyper Cutter", "growl", 0),
            Triple("Big Pecks", "leer", 1), Triple("Keen Eye", "sandattack", 5))) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); val e = R17C9.round(R17C9.main("", move), b, move)
            assertEquals("$ab vs $move", 0, b.enemy.boosts[idx])
            assertTrue("$ab says so", e.lines().any { "$ab prevents" in it })
        }
        // only their stat
        for ((ab, move, idx) in listOf(Triple("Hyper Cutter", "leer", 1), Triple("Big Pecks", "growl", 0), Triple("Keen Eye", "growl", 0))) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("", move), b, move)
            assertEquals("$ab vs $move", -1, b.enemy.boosts[idx])
        }
        // the control: without the ability the drop comes
        val b2 = R17C9.battle(R17C9.wild("", 801, "tackle")); R17C9.round(R17C9.main("", "growl"), b2, "growl")
        assertEquals(-1, b2.enemy.boosts[0])
    }

    @Test fun simpleDoublesAndContraryReversesTheStatChanges() {
        fun after(ab: String): Int { val m = R17C9.main(ab, "swordsdance"); val b = R17C9.battle(R17C9.wild("", 801, "tackle")); R17C9.round(m, b, "swordsdance"); return b.player.boosts[0] }
        assertEquals(2, after("")); assertEquals(4, after("Simple")); assertEquals(-2, after("Contrary"))
        // the foe's drop on a Contrary / Simple holder
        fun dropped(ab: String): Int { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("", "growl"), b, "growl"); return b.enemy.boosts[0] }
        assertEquals(1, dropped("Contrary")); assertEquals(-2, dropped("Simple"))
        // Mold Breaker ignores both
        fun droppedMb(ab: String): Int { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("Mold Breaker", "growl"), b, "growl"); return b.enemy.boosts[0] }
        assertEquals(-1, droppedMb("Contrary")); assertEquals(-1, droppedMb("Simple"))
    }

    @Test fun bellyDrumWithSimpleAndContrary() {
        fun atk(ab: String): Int { val m = R17C9.main(ab, "bellydrum"); val b = R17C9.battle(R17C9.wild("", 801, "tackle")); R17C9.round(m, b, "bellydrum"); return b.player.boosts[0] }
        assertEquals(6, atk("")); assertEquals(6, atk("Simple")); assertEquals(-6, atk("Contrary"))
    }

    @Test fun defiantAndCompetitiveAnswerTheFoesDrop() {
        fun stages(ab: String): List<Int> { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); val e = R17C9.round(R17C9.main("", "growl"), b, "growl"); return b.enemy.boosts.toList() }
        assertEquals(1, stages("Defiant")[0])
        assertEquals(listOf(-1, 2), listOf(stages("Competitive")[0], stages("Competitive")[2]))
        // a rise of the other Pokémon is not a drop of its own
        val b = R17C9.battle(R17C9.wild("Defiant", 801, "tackle"))
        R17C9.round(R17C9.main("", "swordsdance"), b, "swordsdance")
        assertEquals(0, b.enemy.boosts[0])
    }

    // ---------------------------------------------------------------- statuses and conditions

    @Test fun insomniaVitalSpiritLimberWaterVeilAndLeafGuardBlockTheirStatus() {
        // ability, move, the status that must not come, a weather the ability needs
        val cases = listOf(
            Triple("Insomnia", "hypnosis", null), Triple("Vital Spirit", "hypnosis", null), Triple("Limber", "thunderwave", null),
            Triple("Water Veil", "willowisp", null), Triple("Leaf Guard", "toxic", "sun"),
        )
        for ((ab, move, weather) in cases) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); b.field.weather = weather; b.field.weatherTurns = if (weather == null) 0 else 5
            val e = R17C9.round(R17C9.main("", move), b, move)
            assertNull("$ab vs $move", b.enemy.status)
            assertTrue("$ab says so", e.lines().any { "$ab" in it && "prevents" in it || "keeps it awake" in it })
            // and the control: without the ability the status comes
            val c = R17C9.battle(R17C9.wild("", 801, "tackle")); c.field.weather = weather; c.field.weatherTurns = if (weather == null) 0 else 5
            R17C9.round(R17C9.main("", move), c, move)
            assertNotEquals("control for $ab", null, c.enemy.status)
        }
        // Leaf Guard needs the sun
        val b = R17C9.battle(R17C9.wild("Leaf Guard", 801, "tackle")); R17C9.round(R17C9.main("", "toxic"), b, "toxic")
        assertEquals("tox", b.enemy.status)
        // a secondary chance fails quietly
        val b2 = R17C9.battle(R17C9.wild("Water Veil", 801, "tackle")); val e2 = R17C9.round(R17C9.main("", "firepunch"), b2, "firepunch")
        assertNull(b2.enemy.status); assertFalse(e2.lines().any { "Water Veil" in it })
    }

    @Test fun ownTempoInnerFocusAndObliviousBlockTheirConditions() {
        fun has(wildAb: String, move: String, key: String, mainGender: Char = 'M', wildGender: Char = 'F'): Boolean {
            val b = R17C9.battle(R17C9.wild(wildAb, 801, "tackle", gender = wildGender)); R17C9.round(R17C9.main("", move, gender = mainGender), b, move)
            return key in b.enemy.volatiles
        }
        assertFalse(has("Own Tempo", "confuseray", "confusion")); assertTrue(has("", "confuseray", "confusion"))
        assertFalse(has("Oblivious", "attract", "attract")); assertTrue(has("", "attract", "attract"))
        assertFalse(has("Oblivious", "taunt", "taunt")); assertTrue(has("", "taunt", "taunt"))
        // Inner Focus: no flinch
        fun flinched(ab: String): Boolean {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); return R17C9.round(R17C9.main("", "headbutt"), b, "headbutt", "tackle").lines().any { "flinched" in it }
        }
        assertFalse(flinched("Inner Focus")); assertTrue(flinched(""))
        // Own Tempo also stops the confusion of a hit
        val b = R17C9.battle(R17C9.wild("Own Tempo", 801, "tackle")); val m = R17C9.main("", "tackle")
        R17C9.round(m, b, "tackle"); assertFalse("confusion" in b.enemy.volatiles)
    }

    // ---------------------------------------------------------------- speed, priority, sleep

    @Test fun speedAbilitiesNeedTheirWeather() {
        fun speed(ab: String, weather: String?): Double {
            val m = R17C9.main("", "tackle"); val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); b.field.weather = weather; b.field.weatherTurns = if (weather == null) 0 else 5
            return round(m, b).speed(false)
        }
        val base = speed("", null)
        assertEquals(2.0, speed("Swift Swim", "rain") / base, 0.001)
        assertEquals(1.0, speed("Swift Swim", "sun") / base, 0.001)
        assertEquals(2.0, speed("Chlorophyll", "sun") / base, 0.001)
        assertEquals(1.0, speed("Chlorophyll", null) / base, 0.001)
        assertEquals(2.0, speed("Sand Rush", "sand") / base, 0.001)
        assertEquals(1.0, speed("Sand Rush", "rain") / base, 0.001)
    }

    @Test fun quickFeetBoostsWithAStatusAndParalysisDoesNotSlowIt() {
        fun speed(ab: String, status: String?): Double {
            val m = R17C9.main("", "tackle"); val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); b.enemy.status = status
            return round(m, b).speed(false)
        }
        val base = speed("", null)
        assertEquals(1.5, speed("Quick Feet", "brn") / base, 0.001)
        assertEquals("paralysis: boosted, not halved", 1.5, speed("Quick Feet", "par") / base, 0.001)
        assertEquals(1.0, speed("Quick Feet", null) / base, 0.001)
        assertEquals("a plain paralyzed one is halved", 0.5, speed("", "par") / base, 0.001)
    }

    @Test fun earlyBirdWakesTwiceAsOften() {
        // the roll is 0.5: a plain sleeper (33 %) stays asleep, Early Bird (66 %) wakes
        fun woke(ab: String): Boolean { val m = R17C9.main(ab, "tackle"); val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.player.status = "slp"
            return R17C9.round(m, b, "tackle", null, R17C9.fixed(0, 0.5)).lines().any { "woke up!" in it } }
        assertTrue(woke("Early Bird")); assertFalse(woke(""))
        // a counted sleep (Rest) is over in half the rounds
        fun roundsAsleep(ab: String): Int {
            val m = R17C9.main(ab, "tackle"); val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.player.status = "slp"; b.player.volatiles[CoreRules.SLEEP_TURNS] = 2
            var n = 0
            while (b.player.status == "slp" && n < 6) { R17C9.round(m, b, "tackle"); n++ }
            return n
        }
        assertEquals(3, roundsAsleep("")); assertEquals(2, roundsAsleep("Early Bird"))
    }

    // ---------------------------------------------------------------- the end of a round

    @Test fun shedSkinAndHydrationCureAtTheEndOfARound() {
        fun status(ab: String, weather: String?, rng: Random): String? {
            val m = R17C9.main("", "splash"); val b = R17C9.battle(R17C9.wild(ab, 801, "splash")); b.enemy.status = "brn"; b.field.weather = weather; b.field.weatherTurns = if (weather == null) 0 else 5
            R17C9.round(m, b, "splash", "splash", rng); return b.enemy.status
        }
        assertNull(status("Shed Skin", null, R17C9.fixed(0)))
        assertEquals("brn", status("Shed Skin", null, R17C9.fixed(60)))   // 33 % is not met
        assertNull(status("Hydration", "rain", R17C9.fixed(99)))
        assertEquals("brn", status("Hydration", null, R17C9.fixed(0)))
        assertEquals("brn", status("Hydration", "sun", R17C9.fixed(0)))
        val e = run { val m = R17C9.main("", "splash"); val b = R17C9.battle(R17C9.wild("Shed Skin", 801, "splash")); b.enemy.status = "psn"; R17C9.round(m, b, "splash", "splash", R17C9.fixed(0)) }
        assertTrue(e.lines().any { "Shed Skin cured its poison!" in it })
        assertFalse("cured before the poison can hurt", e.lines().any { "hurt by poison" in it })
    }

    @Test fun speedBoostRaisesSpeedEveryRoundFromTheSecond() {
        val m = R17C9.main("Speed Boost", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash"))
        val seen = ArrayList<Int>()
        repeat(4) { R17C9.round(m, b, "splash", "splash"); seen += b.player.boosts[4] }
        assertEquals(listOf(0, 1, 2, 3), seen)
        val m2 = R17C9.main("", "splash"); val b2 = R17C9.battle(R17C9.wild("", 801, "splash"))
        repeat(3) { R17C9.round(m2, b2, "splash", "splash") }
        assertEquals(0, b2.player.boosts[4])
        // it stops at +6 without a line each round
        val m3 = R17C9.main("Speed Boost", "splash"); val b3 = R17C9.battle(R17C9.wild("", 801, "splash"))
        repeat(8) { R17C9.round(m3, b3, "splash", "splash") }
        assertEquals(6, b3.player.boosts[4])
        assertFalse(R17C9.round(m3, b3, "splash", "splash").lines().any { "Speed Boost" in it })
    }

    @Test fun iceBodyAndDrySkinWorkInTheirWeather() {
        fun hp(ab: String, weather: String?): Int {
            val m = R17C9.main(ab, "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.field.weather = weather; b.field.weatherTurns = if (weather == null) 0 else 5
            val max = R17C9.maxHp(m); m.hp = max - 200
            R17C9.round(m, b, "splash", "splash"); return m.hp - (max - 200)
        }
        val max = R17C9.maxHp(R17C9.main("", "splash"))
        assertEquals(max / 16, hp("Ice Body", "hail")); assertEquals(max / 16, hp("Ice Body", "snow")); assertEquals(0, hp("Ice Body", "rain")); assertEquals(0, hp("Ice Body", null))
        assertEquals(max / 8, hp("Dry Skin", "rain")); assertEquals(0, hp("Dry Skin", null))
        assertEquals("the sun hurts a Dry Skin", -(max / 8), hp("Dry Skin", "sun"))
        assertEquals("but not a Magic Guard one", 0, hp("Magic Guard", "sun"))
        // hail does not hurt Ice Body (FieldRules)
        val m = R17C9.main("Ice Body", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.field.weather = "hail"; b.field.weatherTurns = 5
        R17C9.round(m, b, "splash", "splash")
        assertEquals(R17C9.maxHp(m), m.hp)
    }

    // ---------------------------------------------------------------- damage that is not a hit

    @Test fun magicGuardOnlyLosesHpToAttacks() {
        val max = R17C9.maxHp(R17C9.main("", "splash"))
        // burn, poison
        for (st in listOf("brn", "psn", "tox")) {
            val m = R17C9.main("Magic Guard", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.player.status = st
            R17C9.round(m, b, "splash", "splash")
            assertEquals(st, max, m.hp)
            val c = R17C9.main("", "splash"); val bc = R17C9.battle(R17C9.wild("", 801, "splash")); bc.player.status = st
            R17C9.round(c, bc, "splash", "splash")
            assertTrue("control $st", c.hp < max)
        }
        // recoil
        val m = R17C9.main("Magic Guard", "doubleedge"); R17C9.round(m, R17C9.battle(R17C9.wild("", 801, "splash")), "doubleedge")
        assertEquals(max, m.hp)
        // Leech Seed, binding, curse: nothing is lost and the seeder is not healed
        val w = R17C9.main("Magic Guard", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash"))
        b.player.volatiles["leechseed"] = 1; b.player.volatiles["partiallytrapped"] = 4; b.player.volatiles["curse"] = 1
        b.wild.hp = R17C9.maxHp(b.wild) - 100
        R17C9.round(w, b, "splash", "splash")
        assertEquals(max, w.hp)
        assertEquals("the seeder is not healed", R17C9.maxHp(b.wild) - 100, b.wild.hp)
        // the sandstorm
        val w3 = R17C9.main("Magic Guard", "splash"); val b3 = R17C9.battle(R17C9.wild("", 801, "splash")); b3.field.weather = "sand"; b3.field.weatherTurns = 5
        R17C9.round(w3, b3, "splash", "splash")
        assertEquals(max, w3.hp)
        // an attack still hurts
        val w2 = R17C9.battle(R17C9.wild("", 801, "tackle")); val m2 = R17C9.main("Magic Guard", "splash")
        R17C9.round(m2, w2, "splash", "tackle")
        assertTrue(m2.hp < max)
    }

    @Test fun rockHeadHasNoRecoilOrCrashDamage() {
        val max = R17C9.maxHp(R17C9.main("", "splash"))
        val m = R17C9.main("Rock Head", "doubleedge"); R17C9.round(m, R17C9.battle(R17C9.wild("", 801, "splash")), "doubleedge")
        assertEquals(max, m.hp)
        val c = R17C9.main("", "doubleedge"); R17C9.round(c, R17C9.battle(R17C9.wild("", 801, "splash")), "doubleedge")
        assertTrue(c.hp < max)
        // High Jump Kick misses (accuracy 90, roll 0.95): crash damage for a plain one, none for Rock Head
        fun hpAfterMiss(ab: String): Int { val u = R17C9.main(ab, "highjumpkick"); R17C9.round(u, R17C9.battle(R17C9.wild("", 801, "splash")), "highjumpkick", null, R17C9.fixed(99, 0.95)); return u.hp }
        assertTrue(hpAfterMiss("") < max); assertEquals(max, hpAfterMiss("Rock Head"))
        // it does not stop the burn
        val u = R17C9.main("Rock Head", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.player.status = "brn"
        R17C9.round(u, b, "splash", "splash")
        assertTrue(u.hp < max)
    }

    @Test fun heatproofHalvesTheBurnDamage() {
        val max = R17C9.maxHp(R17C9.main("", "splash"))
        fun lost(ab: String): Int { val u = R17C9.main(ab, "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.player.status = "brn"; R17C9.round(u, b, "splash", "splash"); return max - u.hp }
        assertEquals(maxOf(1, max / 16), lost(""))
        assertEquals(maxOf(1, maxOf(1, max / 16) / 2), lost("Heatproof"))
    }

    @Test fun liquidOozeTurnsADrainIntoALoss() {
        val max = R17C9.maxHp(R17C9.main("", "splash"))
        fun hp(wildAb: String): Int { val u = R17C9.main("", "gigadrain"); u.hp = max - 100; R17C9.round(u, R17C9.battle(R17C9.wild(wildAb, 801, "splash")), "gigadrain"); return u.hp }
        assertTrue("a plain drain heals", hp("") > max - 100)
        assertTrue("Liquid Ooze hurts the drainer", hp("Liquid Ooze") < max - 100)
        // Leech Seed: the seeder loses what it would drain
        val u = R17C9.main("", "splash"); u.hp = max - 100
        val w = R17C9.wild("Liquid Ooze", 801, "splash"); val b = R17C9.battle(w); b.enemy.volatiles["leechseed"] = 1
        R17C9.round(u, b, "splash", "splash")
        assertTrue(u.hp < max - 100)
    }

    // ---------------------------------------------------------------- leaving

    @Test fun regeneratorHealsAThirdWhenSwitchedOut() {
        val m = R17C9.main("Regenerator", "tackle"); val b = R17C9.battle(R17C9.wild("", 801, "tackle"))
        val max = R17C9.maxHp(m); m.hp = 10
        b.switchOutMain(R17C9.data, m)
        assertEquals(10 + max / 3, m.hp)
        val dead = R17C9.main("Regenerator", "tackle"); dead.hp = 0
        R17C9.battle(R17C9.wild("", 801, "tackle")).switchOutMain(R17C9.data, dead)
        assertEquals(0, dead.hp)
        val plain = R17C9.main("", "tackle"); plain.hp = 10
        R17C9.battle(R17C9.wild("", 801, "tackle")).switchOutMain(R17C9.data, plain)
        assertEquals(10, plain.hp)
        val nearly = R17C9.main("Regenerator", "tackle"); nearly.hp = max - 1
        R17C9.battle(R17C9.wild("", 801, "tackle")).switchOutMain(R17C9.data, nearly)
        assertEquals("never above the max", max, nearly.hp)
    }

    // ---------------------------------------------------------------- weather damage, both sides

    @Test fun weatherProofAbilitiesTakeNoSandstormOrHailDamage() {
        fun loss(ab: String, weather: String): Int {
            val m = R17C9.main(ab, "splash"); val b = R17C9.battle(R17C9.wild("", 801, "splash")); b.field.weather = weather; b.field.weatherTurns = 5
            R17C9.round(m, b, "splash", "splash"); return R17C9.maxHp(m) - m.hp
        }
        for (ab in listOf("Sand Rush", "Sand Veil", "Overcoat", "Magic Guard")) assertEquals("$ab in a sandstorm", 0, loss(ab, "sand"))
        for (ab in listOf("Snow Cloak", "Ice Body", "Overcoat", "Magic Guard")) assertEquals("$ab in hail", 0, loss(ab, "hail"))
        assertTrue(loss("", "sand") > 0); assertTrue(loss("", "hail") > 0)
        assertTrue("Snow Cloak does not help in a sandstorm", loss("Snow Cloak", "sand") > 0)
    }

    @Test fun bothSidesFollowTheSameRules() {
        // the Main's Static paralyzes the wild one that touches it
        val m = R17C9.main("Static", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "tackle"))
        R17C9.round(m, b, "splash", "tackle")
        assertEquals("par", b.enemy.status)
        // the Main's Levitate dodges the wild one's Earthquake
        val m2 = R17C9.main("Levitate", "splash"); val b2 = R17C9.battle(R17C9.wild("", 801, "earthquake"))
        val e2 = R17C9.round(m2, b2, "splash", "earthquake")
        assertEquals(R17C9.maxHp(m2), m2.hp)
        assertTrue(e2.attacks().last().failed)
        // the Main's Sturdy survives a hit that would knock it out
        val m3 = R17C9.mon(800, 5, "Sturdy", "splash"); val b3 = R17C9.battle(R17C9.wild("", 801, "bodyslam"))
        R17C9.round(m3, b3, "splash", "bodyslam")
        assertEquals(1, m3.hp)
        // the wild one's Mold Breaker ignores the Main's Levitate
        val m4 = R17C9.main("Levitate", "splash"); val b4 = R17C9.battle(R17C9.wild("Mold Breaker", 801, "earthquake"))
        R17C9.round(m4, b4, "splash", "earthquake")
        assertTrue(m4.hp < R17C9.maxHp(m4))
    }

    // ---------------------------------------------------------------- nothing else changes

    @Test fun anUnknownOrEmptyAbilityChangesNothing() {
        fun run(ab: String): List<String> { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); return R17C9.round(R17C9.main(ab, "tackle", "watergun"), b, "tackle", "tackle", Random(5)).lines() + listOf(b.wild.hp.toString()) }
        assertEquals(run(""), run("Testability"))
        assertEquals(run(""), run("Pressure"))
    }

    @Test fun theAbilityIsMatchedByItsNormalizedId() {
        for (spelling in listOf("Keen Eye", "keeneye", "KEEN EYE", "keen-eye")) {
            val b = R17C9.battle(R17C9.wild(spelling, 801, "tackle")); val e = R17C9.round(R17C9.main("", "sandattack"), b, "sandattack")
            assertEquals(spelling, 0, b.enemy.boosts[5])
            assertTrue(spelling, e.lines().any { "prevents accuracy loss" in it })
        }
    }

    @Test fun everyIdOfTheTableIsItsNameNormalized() {
        // a guard against a typo in a row of AbilityText: the id is the name without spaces
        for (id in Abilities.BUILT) assertEquals(id, BattleView.normalizeAbility(Abilities.name(id)))
    }
}
