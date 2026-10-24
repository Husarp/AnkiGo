package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9b.attacks
import com.husarp.ankigo.game.R17C9b.effects
import com.husarp.ankigo.game.R17C9b.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-9b: the rarer abilities, part 2 - the statuses and conditions they keep away, what a hit sets off (Aftermath, Mummy, Gooey ...), the abilities
 * that act on entering, and the turn (Truant, Slow Start, Stall, Quick Draw, Triage, Poison Heal, Moody ...). Fixtures in [R17C9b].
 */
class R17C9bReactionTest {
    private fun hpLoss(m: Mon) = R17C9b.maxHp(m) - m.hp

    // ---------------------------------------------------------------- statuses and conditions kept away

    @Test fun theStatusProofAbilitiesStopTheirStatusesAndNoOthers() {
        // ability, move that gives the status, the status
        val cases = listOf(
            Triple("Sweet Veil", "hypnosis", "slp"), Triple("Magma Armor", "icebeam", "frz"), Triple("Pastel Veil", "toxic", "tox"),
            Triple("Immunity", "toxic", "tox"), Triple("Water Bubble", "willowisp", "brn"), Triple("Thermal Exchange", "willowisp", "brn"),
        )
        for ((ab, move, status) in cases) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w)
            val e = R17C9b.round(R17C9b.main("", move), b, move)
            assertNull("$ab stops $status", b.enemy.status)
            if (move != "icebeam") assertTrue("$ab says so: ${e.lines()}", e.effects().any { it.kind == EffectKind.BLOCKED })
        }
        // another status is not stopped
        val w = R17C9b.wild("Sweet Veil", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("", "thunderwave"), b, "thunderwave")
        assertEquals("par", b.enemy.status)
        // Mold Breaker gets past a breakable one
        val w2 = R17C9b.wild("Sweet Veil", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Mold Breaker", "hypnosis"), b2, "hypnosis")
        assertEquals("slp", b2.enemy.status)
    }

    @Test fun purifyingSaltAndComatoseStopEveryStatus() {
        for (ab in listOf("Purifying Salt", "Comatose")) for (move in listOf("willowisp", "toxic", "thunderwave", "hypnosis", "poisongas")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w)
            R17C9b.round(R17C9b.main("", move), b, move)
            assertNull("$ab stops $move", b.enemy.status)
        }
    }

    @Test fun yawnDoesNotWorkOnSweetVeilPurifyingSaltOrAGrassFlowerVeil() {
        for ((ab, sp) in listOf("Sweet Veil" to 801, "Purifying Salt" to 801, "Flower Veil" to 802)) {
            val w = R17C9b.wild(ab, sp, "tackle"); val b = R17C9b.battle(w)
            R17C9b.round(R17C9b.main("", "yawn"), b, "yawn")
            assertFalse("$ab", "yawn" in b.enemy.volatiles)
        }
        val w = R17C9b.wild("", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("", "yawn"), b, "yawn")
        assertTrue("without one it works", "yawn" in b.enemy.volatiles)
    }

    @Test fun flowerVeilProtectsGrassTypesFromStatusesOnly() {
        val grass = R17C9b.wild("Flower Veil", 802, "tackle"); val b = R17C9b.battle(grass)
        R17C9b.round(R17C9b.main("", "toxic"), b, "toxic")
        assertNull(b.enemy.status)
        val other = R17C9b.wild("Flower Veil", 801, "tackle"); val b2 = R17C9b.battle(other)
        R17C9b.round(R17C9b.main("", "toxic"), b2, "toxic")
        assertEquals("tox", b2.enemy.status)
    }

    @Test fun aromaVeilKeepsTauntAttractAndTormentAway() {
        for (move in listOf("taunt", "attract", "torment")) {
            val w = R17C9b.wild("Aroma Veil", 801, "tackle"); val b = R17C9b.battle(w)
            val e = R17C9b.round(R17C9b.main("", move), b, move)
            assertTrue("$move: ${b.enemy.volatiles}", b.enemy.volatiles.keys.none { it == move })
            assertTrue("$move says so", e.lines().any { "Aroma Veil prevents" in it })
            val plain = R17C9b.wild("", 801, "tackle"); val b2 = R17C9b.battle(plain)
            R17C9b.round(R17C9b.main("", move), b2, move)
            assertTrue("$move works without it", b2.enemy.volatiles.keys.any { it == move })
        }
        val w = R17C9b.wild("Aroma Veil", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("Mold Breaker", "taunt"), b, "taunt")
        assertTrue("Mold Breaker", "taunt" in b.enemy.volatiles)
    }

    @Test fun comatoseCountsAsAsleepAndHavingAStatus() {
        // Dream Eater needs a sleeper; Comatose is one
        val w = R17C9b.wild("Comatose", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "dreameater")
        val e = R17C9b.round(m, b, "dreameater")
        assertTrue("it hurts", w.hp < R17C9b.maxHp(w))
        assertFalse(e.attacks().single().failed)
        val awake = R17C9b.wild("", 801, "tackle"); val b2 = R17C9b.battle(awake)
        val e2 = R17C9b.round(R17C9b.main("", "dreameater"), b2, "dreameater")
        assertTrue("against an awake one it fails", e2.attacks().single().failed)
        // Hex: double against a status, Comatose counts
        assertEquals(2.0, R17C9b.ratio("", "Comatose", "hex", wildSp = 802), 0.1)
        // Snore needs a sleeper: Comatose is one, and it acts normally
        val snorer = R17C9b.main("Comatose", "snore", "tackle"); val b3 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val e3 = R17C9b.round(snorer, b3, "snore")
        assertFalse(e3.attacks().single().failed)
        val e4 = R17C9b.round(snorer, b3, "tackle")
        assertFalse("it acts normally", e4.attacks().single().failed)
        // the Smart choice knows
        val v = SimpleView.of(R17C9b.data, b2, R17C9b.main("", "dreameater"))
        assertEquals(false, rulesFirst { it.useful(v, true, R17C9b.data.move("dreameater")!!) })
        val v2 = SimpleView.of(R17C9b.data, b, R17C9b.main("", "dreameater"))
        assertNull(rulesFirst { it.useful(v2, true, R17C9b.data.move("dreameater")!!) })
    }

    @Test fun corrosionPoisonsSteelAndPoisonTypes() {
        for (sp in listOf(812, 811)) {
            val w = R17C9b.wild("", sp, "tackle"); val b = R17C9b.battle(w)
            R17C9b.round(R17C9b.main("Corrosion", "toxic"), b, "toxic")
            assertEquals("Corrosion, species $sp", "tox", b.enemy.status)
            val w2 = R17C9b.wild("", sp, "tackle"); val b2 = R17C9b.battle(w2)
            R17C9b.round(R17C9b.main("", "toxic"), b2, "toxic")
            assertNull("without it, species $sp", b2.enemy.status)
        }
        // other statuses still follow the types
        val w = R17C9b.wild("", 807, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("Corrosion", "willowisp"), b, "willowisp")
        assertNull(b.enemy.status)
        // the Smart choice finds the poisoning worth a try
        val b3 = R17C9b.battle(R17C9b.wild("", 812, "tackle")); val m = R17C9b.main("Corrosion", "toxic")
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, R17C9b.data.move("toxic")!!, SimpleView.of(R17C9b.data, b3, m), true))
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, R17C9b.data.move("toxic")!!, SimpleView.of(R17C9b.data, b3, R17C9b.main("", "toxic")), true))
    }

    // ---------------------------------------------------------------- what a hit sets off

    @Test fun aftermathHurtsTheContactAttackerThatKnocksItOut() {
        val w = R17C9b.wild("Aftermath", 801, "tackle"); w.hp = 1; val b = R17C9b.battle(w); val m = R17C9b.main("", "bodyslam")
        val e = R17C9b.round(m, b, "bodyslam")
        assertEquals(0, w.hp)
        assertEquals(R17C9b.maxHp(m) / 4, hpLoss(m))
        assertTrue(e.lines().any { "Aftermath!" in it })
        // not for a move without contact, nor an attacker with Damp or Long Reach, nor when it survives
        for ((ab, move) in listOf("" to "hyperbeam", "Damp" to "bodyslam", "Long Reach" to "bodyslam")) {
            val w2 = R17C9b.wild("Aftermath", 801, "tackle"); w2.hp = 1; val b2 = R17C9b.battle(w2); val m2 = R17C9b.main(ab, move)
            R17C9b.round(m2, b2, move)
            assertEquals("$ab $move", 0, hpLoss(m2))
        }
        val w3 = R17C9b.wild("Aftermath", 801, "tackle"); val b3 = R17C9b.battle(w3); val m3 = R17C9b.main("", "bodyslam")
        R17C9b.round(m3, b3, "bodyslam")
        assertEquals(0, hpLoss(m3))
    }

    @Test fun innardsOutTakesAsMuchAsTheKnockoutHitTookOff() {
        val w = R17C9b.wild("Innards Out", 801, "tackle"); w.hp = 50; val b = R17C9b.battle(w); val m = R17C9b.main("", "hyperbeam")
        val e = R17C9b.round(m, b, "hyperbeam")
        assertEquals(0, w.hp)
        assertEquals("the 50 HP it had", 50, hpLoss(m))
        assertTrue(e.lines().any { "Innards Out" in it })
        // Magic Guard keeps it
        val w2 = R17C9b.wild("Innards Out", 801, "tackle"); w2.hp = 50; val b2 = R17C9b.battle(w2); val m2 = R17C9b.main("Magic Guard", "hyperbeam")
        R17C9b.round(m2, b2, "hyperbeam")
        assertEquals(0, hpLoss(m2))
    }

    @Test fun staminaSteamEngineThermalExchangeAndWaterCompactionRaiseAStat() {
        data class C(val ab: String, val move: String, val idx: Int, val stages: Int)
        for (c in listOf(C("Stamina", "tackle", 1, 1), C("Steam Engine", "ember", 4, 6), C("Steam Engine", "watergun", 4, 6), C("Thermal Exchange", "ember", 0, 1), C("Water Compaction", "watergun", 1, 2))) {
            val w = R17C9b.wild(c.ab, 801, "tackle"); val b = R17C9b.battle(w)
            val e = R17C9b.round(R17C9b.main("", c.move), b, c.move)
            assertEquals("${c.ab} / ${c.move}", c.stages, b.enemy.boosts[c.idx])
            assertTrue(c.ab, e.lines().any { "${c.ab}!" in it })
        }
        // a move of another type does not set the typed ones off
        for ((ab, idx) in listOf("Steam Engine" to 4, "Thermal Exchange" to 0, "Water Compaction" to 1)) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w)
            R17C9b.round(R17C9b.main("", "tackle"), b, "tackle")
            assertEquals(ab, 0, b.enemy.boosts[idx])
        }
    }

    @Test fun windPowerAndElectromorphosisChargeTheNextElectricMove() {
        fun secondRoundLoss(ab: String, firstMove: String): Int {
            val w = R17C9b.wild(ab, 801, "thundershock"); val b = R17C9b.battle(w); val m = R17C9b.main("", firstMove, "tackle")
            R17C9b.play(m, b, listOf(firstMove, "tackle"), listOf(null, "thundershock"))
            return hpLoss(m)
        }
        val plain = secondRoundLoss("", "gust")
        assertTrue(plain > 0)
        assertEquals("Wind Power, hit by a wind move", plain * 2.0, secondRoundLoss("Wind Power", "gust").toDouble(), plain * 0.1 + 2)
        assertEquals("Wind Power, hit by another move", plain.toDouble(), secondRoundLoss("Wind Power", "tackle").toDouble(), 2.0)
        assertEquals("Electromorphosis, any hit", plain * 2.0, secondRoundLoss("Electromorphosis", "tackle").toDouble(), plain * 0.1 + 2)
        val w = R17C9b.wild("Electromorphosis", 801, "tackle"); val b = R17C9b.battle(w)
        val e = R17C9b.round(R17C9b.main("", "tackle"), b, "tackle")
        assertTrue(e.lines().any { "charged" in it && "with power" in it })
        assertEquals("charge", e.effects().first { it.kind == EffectKind.VOLATILE }.stat)
    }

    @Test fun mummyAndLingeringAromaSpreadWhenTheyAreTouched() {
        for ((ab, name) in listOf("Mummy" to "Mummy", "Lingering Aroma" to "Lingering Aroma")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "bodyslam", "hyperbeam")
            val e = R17C9b.round(m, b, "bodyslam")
            val v = SimpleView.of(R17C9b.data, b, m)
            assertEquals(ab, BattleView.normalizeAbility(ab), v.ability(true))
            assertTrue(ab, e.lines().any { "Ability became $name!" in it })
        }
        // a move without contact does not, and an ability that cannot be suppressed stays
        val w = R17C9b.wild("Mummy", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "hyperbeam")
        R17C9b.round(m, b, "hyperbeam")
        assertEquals("static", SimpleView.of(R17C9b.data, b, m).ability(true))
        val w2 = R17C9b.wild("Mummy", 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main("Comatose", "bodyslam")
        R17C9b.round(m2, b2, "bodyslam")
        assertEquals("comatose", SimpleView.of(R17C9b.data, b2, m2).ability(true))
    }

    @Test fun wanderingSpiritSwapsAbilitiesWithAContactAttacker() {
        val w = R17C9b.wild("Wandering Spirit", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "bodyslam")
        val e = R17C9b.round(m, b, "bodyslam")
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals("wanderingspirit", v.ability(true))
        assertEquals("static", v.ability(false))
        assertTrue(e.lines().any { "swapped Abilities" in it })
        // an attacker whose ability can't be swapped keeps it; a move without contact does nothing
        val w2 = R17C9b.wild("Wandering Spirit", 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main("Wonder Guard", "bodyslam")
        R17C9b.round(m2, b2, "bodyslam")
        assertEquals("wonderguard", SimpleView.of(R17C9b.data, b2, m2).ability(true))
        val w3 = R17C9b.wild("Wandering Spirit", 801, "tackle"); val b3 = R17C9b.battle(w3); val m3 = R17C9b.main("Static", "hyperbeam")
        R17C9b.round(m3, b3, "hyperbeam")
        assertEquals("static", SimpleView.of(R17C9b.data, b3, m3).ability(true))
    }

    @Test fun gooeyTanglingHairAndCottonDownLowerTheAttackersSpeed() {
        for (ab in listOf("Gooey", "Tangling Hair")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w)
            val e = R17C9b.round(R17C9b.main("", "bodyslam"), b, "bodyslam")
            assertEquals(ab, -1, b.player.boosts[4])
            assertTrue(ab, e.lines().any { "$ab!" in it })
            val b2 = R17C9b.battle(R17C9b.wild(ab, 801, "tackle"))
            R17C9b.round(R17C9b.main("", "hyperbeam"), b2, "hyperbeam")
            assertEquals("$ab: no contact", 0, b2.player.boosts[4])
        }
        val w = R17C9b.wild("Cotton Down", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("", "hyperbeam"), b, "hyperbeam")
        assertEquals("any hit", -1, b.player.boosts[4])
    }

    @Test fun sandSpitAndSeedSowerStartTheirField() {
        val w = R17C9b.wild("Sand Spit", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("", "tackle"), b, "tackle")
        assertEquals("sand", b.field.weather)
        val w2 = R17C9b.wild("Seed Sower", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("", "tackle"), b2, "tackle")
        assertEquals("grassy", b2.field.terrain)
    }

    @Test fun berserkAndAngerShellReactToTheHitThatTakesHalfOfTheHp() {
        val w = R17C9b.wild("Berserk", 801, "tackle"); val b = R17C9b.battle(w); val max = R17C9b.maxHp(w)
        w.hp = max / 2 + 5
        val e = R17C9b.round(R17C9b.main("", "tackle"), b, "tackle")
        assertTrue(w.hp * 2 <= max && w.hp > 0)
        assertEquals(1, b.enemy.boosts[2])
        assertTrue(e.lines().any { "Berserk!" in it })
        // full HP and a small hit: nothing; already below half: nothing more
        val w2 = R17C9b.wild("Berserk", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("", "tackle"), b2, "tackle")
        assertEquals(0, b2.enemy.boosts[2])
        val w3 = R17C9b.wild("Berserk", 801, "tackle"); val b3 = R17C9b.battle(w3); w3.hp = max / 2 - 100
        R17C9b.round(R17C9b.main("", "tackle"), b3, "tackle")
        assertEquals(0, b3.enemy.boosts[2])
        // Anger Shell
        val w4 = R17C9b.wild("Anger Shell", 801, "tackle"); val b4 = R17C9b.battle(w4); w4.hp = max / 2 + 5
        R17C9b.round(R17C9b.main("", "tackle"), b4, "tackle")
        assertEquals(listOf(1, -1, 1, -1, 1), b4.enemy.boosts.toList().take(5))
    }

    @Test fun toxicChainBadlyPoisonsOnHitsLikeAnyChance() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Toxic Chain", "hyperbeam"), b, "hyperbeam")
        assertEquals("tox", b.enemy.status)
        val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Toxic Chain", "hyperbeam"), b2, "hyperbeam", rng = R17C9b.fixed(99))
        assertNull("the chance was not met", b2.enemy.status)
        val b3 = R17C9b.battle(R17C9b.wild("Shield Dust", 801, "tackle"))
        R17C9b.round(R17C9b.main("Toxic Chain", "hyperbeam"), b3, "hyperbeam")
        assertNull("Shield Dust", b3.enemy.status)
        val b4 = R17C9b.battle(R17C9b.wild("", 812, "tackle"))
        R17C9b.round(R17C9b.main("Toxic Chain", "hyperbeam"), b4, "hyperbeam")
        assertNull("a Steel type", b4.enemy.status)
    }

    @Test fun poisonPuppeteerConfusesWhatItsMovePoisons() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val e = R17C9b.round(R17C9b.main("Poison Puppeteer", "toxic"), b, "toxic")
        assertEquals("tox", b.enemy.status)
        assertTrue("confusion" in b.enemy.volatiles)
        assertTrue(e.lines().any { "Poison Puppeteer!" in it })
        val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("", "toxic"), b2, "toxic")
        assertFalse("confusion" in b2.enemy.volatiles)
        val b3 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Poison Puppeteer", "thunderwave"), b3, "thunderwave")
        assertFalse("another status", "confusion" in b3.enemy.volatiles)
    }

    @Test fun disguiseTakesTheFirstHitThenCostsAnEighth() {
        val w = R17C9b.wild("Disguise", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "hyperbeam")
        val max = R17C9b.maxHp(w)
        val e = R17C9b.round(m, b, "hyperbeam")
        assertEquals("no damage from the hit, an eighth for the disguise", max - max / 8, w.hp)
        assertTrue(e.lines().any { "disguise served it as a decoy" in it })
        assertEquals(0, e.attacks().single().damage)
        val before = w.hp
        R17C9b.round(m, b, "hyperbeam")
        assertTrue("the second hit hurts", w.hp < before - 10)
        // Mold Breaker ignores it; Magic Guard keeps the eighth
        val w2 = R17C9b.wild("Disguise", 801, "tackle"); val b2 = R17C9b.battle(w2)
        val e2 = R17C9b.round(R17C9b.main("Mold Breaker", "hyperbeam"), b2, "hyperbeam")
        assertFalse("no decoy", e2.lines().any { "decoy" in it })
        assertTrue(e2.attacks().single().damage > 0)
    }

    @Test fun longReachNeverTouchesAndUnseenFistGoesThroughProtect() {
        val b = R17C9b.battle(R17C9b.wild("Static", 801, "tackle"))
        R17C9b.round(R17C9b.main("Long Reach", "bodyslam"), b, "bodyslam")
        assertNull("no contact, no paralysis", b.player.status)
        val b2 = R17C9b.battle(R17C9b.wild("Static", 801, "tackle"))
        R17C9b.round(R17C9b.main("", "bodyslam"), b2, "bodyslam")
        assertEquals("par", b2.player.status)
        // Protect
        fun after(ab: String, move: String): Int {
            val w = R17C9b.wild("", 801, "protect"); val bb = R17C9b.battle(w)
            R17C9b.round(R17C9b.main(ab, move), bb, move, "protect")
            return R17C9b.maxHp(w) - w.hp
        }
        assertEquals("blocked", 0, after("", "bodyslam"))
        assertTrue("Unseen Fist, a contact move", after("Unseen Fist", "bodyslam") > 0)
        assertEquals("Unseen Fist, no contact", 0, after("Unseen Fist", "hyperbeam"))
    }

    @Test fun screenCleanerEndsTheScreensOfBothSides() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        b.field.player["reflect"] = 3; b.field.enemy["lightscreen"] = 2; b.field.enemy["auroraveil"] = 4; b.field.enemy["safeguard"] = 4
        val e = R17C9b.round(R17C9b.main("Screen Cleaner", "tackle"), b, "tackle")
        assertNull(b.field.player["reflect"])
        assertNull(b.field.enemy["lightscreen"])
        assertNull(b.field.enemy["auroraveil"])
        assertNotNull("Safeguard stays", b.field.enemy["safeguard"])
        assertTrue(e.lines().any { "Screen Cleaner!" in it })
    }

    // ---------------------------------------------------------------- entering

    @Test fun supersweetSyrupLowersTheEvasionOfTheOtherPokemon() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Supersweet Syrup", "tackle"), b, "tackle")
        assertEquals(-1, b.enemy.boosts[6])
        val b2 = R17C9b.battle(R17C9b.wild("Supersweet Syrup", 801, "tackle"))
        R17C9b.round(R17C9b.main("", "tackle"), b2, "tackle")
        assertEquals("the wild one's too", -1, b2.player.boosts[6])
    }

    @Test fun theSurgeAbilitiesAndTheEnergyOnesStartTheirField() {
        for ((ab, terrain) in listOf("Electric Surge" to "electric", "Psychic Surge" to "psychic", "Grassy Surge" to "grassy", "Misty Surge" to "misty", "Hadron Engine" to "electric")) {
            val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
            val e = R17C9b.round(R17C9b.main(ab, "tackle"), b, "tackle")
            assertEquals(ab, terrain, b.field.terrain)
            assertTrue(ab, e.effects().any { it.kind == EffectKind.FIELD && it.label != null })
            assertTrue("$ab lasts ${FieldRules.TERRAIN_TURNS} rounds: the first is over", b.field.terrainTurns in 1 until FieldRules.TERRAIN_TURNS)
        }
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Orichalcum Pulse", "tackle"), b, "tackle")
        assertEquals("sun", b.field.weather)
    }

    @Test fun intrepidSwordAndDauntlessShieldRaiseOnEntering() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(R17C9b.main("Intrepid Sword", "tackle"), b, "tackle")
        assertEquals(1, b.player.boosts[0])
        val b2 = R17C9b.battle(R17C9b.wild("Dauntless Shield", 801, "tackle"))
        R17C9b.round(R17C9b.main("", "tackle"), b2, "tackle")
        assertEquals(1, b2.enemy.boosts[1])
    }

    @Test fun neutralizingGasSwitchesOffEveryOtherAbilityButNotTheOnesThatCannotBeSuppressed() {
        // the wild one is faster, so its gas is out before the Main's Intimidate would work
        val w = R17C9b.wild("Neutralizing Gas", 808, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Intimidate", "tackle")
        val e = R17C9b.round(m, b, "tackle")
        assertEquals("the Intimidate did nothing", 0, b.enemy.boosts[0])
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals("", v.ability(true))
        assertEquals("neutralizinggas", v.ability(false))
        assertTrue(e.lines().any { "Neutralizing gas filled the area!" in it })
        // damage abilities are off too
        val b2 = R17C9b.battle(R17C9b.wild("Neutralizing Gas", 808, "tackle"))
        b2.enemy.volatiles["ab.gas"] = 1
        val big = R17C9b.main("Huge Power", "doubleedge")
        val plain = R17C9b.main("", "doubleedge")
        assertEquals("Huge Power does nothing", R17C9b.dmg(b2, plain, "doubleedge"), R17C9b.dmg(b2, big, "doubleedge"))
        // an ability that cannot be suppressed stays
        assertEquals("disguise", SimpleView.of(R17C9b.data, b2, R17C9b.main("Disguise", "tackle")).ability(true))
        // without the flag (the holder has not entered) the abilities work
        val b3 = R17C9b.battle(R17C9b.wild("Neutralizing Gas", 808, "tackle"))
        assertEquals("hugepower", SimpleView.of(R17C9b.data, b3, big).ability(true))
    }

    @Test fun theRuinAbilitiesAndTheAurasSayTheyAreThere() {
        for ((ab, text) in listOf("Tablets of Ruin" to "Attack", "Sword of Ruin" to "Defense", "Vessel of Ruin" to "Sp. Atk", "Beads of Ruin" to "Sp. Def")) {
            val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
            val e = R17C9b.round(R17C9b.main(ab, "tackle"), b, "tackle")
            assertTrue(ab, e.lines().any { "weakened the $text of all surrounding Pokémon!" in it })
        }
        val e = R17C9b.round(R17C9b.main("Fairy Aura", "tackle"), R17C9b.battle(R17C9b.wild("", 801, "tackle")), "tackle")
        assertTrue(e.lines().any { "radiating a fairy aura!" in it })
        val e2 = R17C9b.round(R17C9b.main("Cloud Nine", "tackle"), R17C9b.battle(R17C9b.wild("", 801, "tackle")), "tackle")
        assertTrue(e2.lines().any { "effects of the weather disappeared" in it })
    }

    // ---------------------------------------------------------------- the turn

    @Test fun truantMovesEverySecondTurn() {
        val w = R17C9b.wild("", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Truant", "tackle")
        val rounds = R17C9b.play(m, b, listOf("tackle", "tackle", "tackle", "tackle"))
        val hit = rounds.map { r -> r.attacks().any { it.byPlayer && it.damage > 0 } }
        assertEquals(listOf(true, false, true, false), hit)
        val loaf = rounds[1].effects().first { it.kind == EffectKind.CANT_MOVE }
        assertEquals("truant", loaf.stat)
        assertTrue(loaf.text.endsWith("is loafing around!"))
    }

    @Test fun slowStartHalvesAttackAndSpeedForFiveTurns() {
        near("Attack, a new Pokémon", 0.5, R17C9b.ratio("Slow Start", "", "doubleedge"))
        near("Attack, five turns later", 1.0, R17C9b.ratio("Slow Start", "", "doubleedge") { b, _ -> b.player.volatiles["ab.turns"] = 5 })
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle")); val m = R17C9b.main("Slow Start", "tackle")
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals(0.5, rulesProduct { it.speedMod(v, true) }, 0.0)
        val rounds = R17C9b.play(m, b, List(5) { "tackle" }, List(5) { null })
        for (i in 0..3) assertFalse("round ${i + 1}", rounds[i].lines().any { "act together" in it })
        assertTrue(rounds[4].lines().any { "finally got its act together!" in it })
        assertEquals(1.0, rulesProduct { it.speedMod(v, true) }, 0.0)
    }

    private fun near(what: String, expected: Double, actual: Double) = assertEquals(what, expected, actual, 0.05)

    @Test fun stallMovesLastAndQuickDrawSometimesFirst() {
        fun firstMover(mainAb: String, wildAb: String, mainMove: String = "tackle", wildMove: String = "tackle", rng: Random = R17C9b.low): Boolean {
            val b = R17C9b.battle(R17C9b.wild(wildAb, 801, wildMove)); val m = R17C9b.main(mainAb, mainMove)
            return R17C9b.round(m, b, mainMove, wildMove, rng).attacks().first().byPlayer
        }
        assertTrue("the Main is faster", firstMover("", ""))
        assertFalse("Stall", firstMover("Stall", ""))
        assertTrue("both Stall: speed decides", firstMover("Stall", "Stall"))
        assertTrue("Quick Draw, not met", firstMover("", "Quick Draw", rng = R17C9b.fixed(99)))
        assertFalse("Quick Draw, met", firstMover("", "Quick Draw", rng = R17C9b.low))
        // a status move is not helped by Quick Draw
        assertTrue(firstMover("", "Quick Draw", wildMove = "growl"))
    }

    @Test fun quickDrawSaysSo() {
        val b = R17C9b.battle(R17C9b.wild("Quick Draw", 801, "tackle"))
        val e = R17C9b.round(R17C9b.main("", "tackle"), b, "tackle", "tackle")
        assertTrue(e.lines().any { "Quick Draw: it can act faster than normal!" in it })
    }

    @Test fun triageAndGaleWingsAddPriority() {
        fun mainFirst(mainAb: String, mainMove: String, wildAb: String, wildMove: String): Boolean {
            val w = R17C9b.wild(wildAb, 808, wildMove); val b = R17C9b.battle(w); val m = R17C9b.main(mainAb, mainMove)
            m.hp = R17C9b.maxHp(m) - 50
            return R17C9b.round(m, b, mainMove, wildMove).attacks().first().byPlayer
        }
        // the wild Slowfast (speed 300) is faster than the Main (200)
        assertFalse("plain: the faster wild one goes first", mainFirst("", "recover", "", "tackle"))
        assertTrue("Triage", mainFirst("Triage", "recover", "", "tackle"))
        assertTrue("Triage, a draining move", mainFirst("Triage", "gigadrain", "", "tackle"))
        assertFalse("Triage, an attack", mainFirst("Triage", "tackle", "", "tackle"))
        // Gale Wings: Flying moves at full HP
        val slow = R17C9b.wild("Gale Wings", 801, "gust"); val bs = R17C9b.battle(slow)
        assertFalse("full HP: +1 priority beats the faster Main", R17C9b.round(R17C9b.main("", "tackle"), bs, "tackle", "gust").attacks().first().byPlayer)
        val hurt = R17C9b.wild("Gale Wings", 801, "gust"); hurt.hp -= 20; val bh = R17C9b.battle(hurt)
        assertTrue("hurt: no", R17C9b.round(R17C9b.main("", "tackle"), bh, "tackle", "gust").attacks().first().byPlayer)
    }

    @Test fun myceliumMightGoesLastAndIgnoresAbilitiesWithStatusMoves() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val first = R17C9b.round(R17C9b.main("Mycelium Might", "growl"), b, "growl", "tackle").attacks().first()
        assertFalse("its status move comes last", first.byPlayer)
        val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        assertTrue("an attack is not held back", R17C9b.round(R17C9b.main("Mycelium Might", "tackle"), b2, "tackle", "tackle").attacks().first().byPlayer)
        // ignores Limber with Thunder Wave, but not with an attack
        val w = R17C9b.wild("Limber", 801, "tackle"); val b3 = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("Mycelium Might", "thunderwave"), b3, "thunderwave")
        assertEquals("par", b3.enemy.status)
        val w2 = R17C9b.wild("Limber", 801, "tackle"); val b4 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("", "thunderwave"), b4, "thunderwave")
        assertNull(b4.enemy.status)
        val w3 = R17C9b.wild("Bulletproof", 801, "tackle"); val b5 = R17C9b.battle(w3)
        R17C9b.round(R17C9b.main("Mycelium Might", "shadowball"), b5, "shadowball")
        assertEquals("an attack does not ignore the ability", R17C9b.maxHp(w3), w3.hp)
    }

    @Test fun poisonHealRainDishAndBadDreamsWorkAtTheEndOfTheRound() {
        val m = R17C9b.main("Poison Heal", "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle")); val max = R17C9b.maxHp(m)
        m.hp = max - 100; b.player.status = "psn"
        val e = R17C9b.round(m, b, "tackle")
        assertEquals(max - 100 + max / 8, m.hp)
        assertTrue(e.lines().any { "Poison Heal restored its HP!" in it })
        assertFalse("no poison damage", e.effects().any { it.kind == EffectKind.STATUS_DAMAGE })
        val full = R17C9b.main("Poison Heal", "tackle"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle")); b2.player.status = "tox"
        R17C9b.round(full, b2, "tackle")
        assertEquals("full HP stays full", max, full.hp)
        val blocked = R17C9b.main("Poison Heal", "tackle"); val b3 = R17C9b.battle(R17C9b.wild("", 801, "tackle")); b3.player.status = "psn"
        blocked.hp = max - 100; b3.player.volatiles["healblock"] = 5
        R17C9b.round(blocked, b3, "tackle")
        assertEquals("Heal Block stops the heal", max - 100, blocked.hp)
        // Rain Dish
        val rd = R17C9b.main("Rain Dish", "tackle"); val b4 = R17C9b.battle(R17C9b.wild("", 801, "tackle")); b4.field.weather = "rain"; b4.field.weatherTurns = 5
        rd.hp = max - 100
        R17C9b.round(rd, b4, "tackle")
        assertEquals(max - 100 + max / 16, rd.hp)
        val dry = R17C9b.main("Rain Dish", "tackle"); val b5 = R17C9b.battle(R17C9b.wild("", 801, "tackle")); dry.hp = max - 100
        R17C9b.round(dry, b5, "tackle")
        assertEquals(max - 100, dry.hp)
        // Bad Dreams: a sleeper opposite it loses an eighth (high numbers: nobody wakes up)
        val sleeper = R17C9b.main("", "tackle"); val w = R17C9b.wild("Bad Dreams", 801, "tackle"); val b6 = R17C9b.battle(w); b6.player.status = "slp"
        R17C9b.round(sleeper, b6, "tackle", null, rng = R17C9b.high)
        assertEquals(max / 8, hpLoss(sleeper))
        val awake = R17C9b.main("", "tackle"); val b7 = R17C9b.battle(R17C9b.wild("Bad Dreams", 801, "tackle"))
        R17C9b.round(awake, b7, "tackle")
        assertEquals(0, hpLoss(awake))
    }

    @Test fun moodyRaisesOneStatByTwoAndLowersAnotherByOne() {
        val m = R17C9b.main("Moody", "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val e = R17C9b.round(m, b, "tackle", rng = Random(3))
        val moved = b.player.boosts.take(5).filter { it != 0 }.sorted()
        assertEquals(listOf(-1, 2), moved)
        assertTrue(e.lines().any { "Moody!" in it })
        // a stat that is at its limit is left alone
        val m2 = R17C9b.main("Moody", "tackle"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        for (i in 1..4) b2.player.boosts[i] = 6
        R17C9b.round(m2, b2, "tackle", rng = Random(5))
        assertEquals("only Attack could rise", 2, b2.player.boosts[0])
        assertEquals("another stat dropped", 1, (1..4).count { b2.player.boosts[it] == 5 })
    }

    @Test fun gorillaTacticsKeepsUsingTheFirstMove() {
        val m = R17C9b.main("Gorilla Tactics", "watergun", "tackle", "ember"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        b.wild.hp = R17C9b.maxHp(b.wild)
        R17C9b.play(m, b, listOf("watergun"))
        assertEquals("watergun", b.player.notes["ab.lock"])
        val names = ArrayList<String>()
        val rng = Random(7)
        repeat(8) {
            b.wild.hp = R17C9b.maxHp(b.wild)
            val ev = BattleEngine.playRound(R17C9b.data, b, m, 1.0, false, rng)
            names += ev.attacks().filter { it.byPlayer }.map { it.move }
        }
        assertEquals(List(8) { "Water Gun" }, names)
        // a Pokémon without it picks freely
        val free = R17C9b.main("", "watergun", "tackle", "ember"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.play(free, b2, listOf("watergun"))
        assertNull(b2.player.notes["ab.lock"])
    }

    @Test fun stakeoutDoublesOnAPokemonThatJustCameIn() {
        fun loss(ab: String, freeHit: Boolean): Int {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "tackle")
            if (freeHit) R17C9b.freeHit(m, b, "tackle") else R17C9b.round(m, b, "tackle", "tackle")
            return hpLoss(m)
        }
        assertEquals("the free hit of a switch", loss("", true) * 2.0, loss("Stakeout", true).toDouble(), 6.0)
        assertEquals("the first round", loss("", false) * 2.0, loss("Stakeout", false).toDouble(), 6.0)
        // the round after the free hit is the Main's second turn
        val w = R17C9b.wild("Stakeout", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "tackle")
        R17C9b.freeHit(m, b, "tackle")
        val afterFree = m.hp
        R17C9b.round(m, b, "tackle", "tackle")
        val w2 = R17C9b.wild("", 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main("", "tackle")
        R17C9b.freeHit(m2, b2, "tackle")
        val plainAfterFree = m2.hp
        R17C9b.round(m2, b2, "tackle", "tackle")
        assertEquals("not doubled any more", (plainAfterFree - m2.hp).toDouble(), (afterFree - m.hp).toDouble(), 3.0)
        // the third round too
        val before = m.hp
        R17C9b.round(m, b, "tackle", "tackle")
        assertEquals((plainAfterFree - m2.hp).toDouble(), (before - m.hp).toDouble(), 3.0)
    }

    @Test fun noAbilityAndTheAbilitiesThatDoNothingChangeNothing() {
        // a Pokémon with no ability, an unknown one and one that does nothing in AnkiGo play a round like before
        for (ab in listOf("", "Testability", "Pressure", "Run Away")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w)
            val e = R17C9b.round(R17C9b.main(ab, "tackle"), b, "tackle", "tackle")
            assertEquals(ab, 2, e.attacks().size)
        }
    }
}
