package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9.attacks
import com.husarp.ankigo.game.R17C9.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-9a: the abilities that change the numbers of a hit: power (Overgrow ... Technician ... Sheer Force), the attack stat (Huge Power, Guts,
 * Hustle), STAB, the final damage (Thick Fat, Solid Rock, Tinted Lens ...), type rules (Scrappy), accuracy (Compound Eyes, Sand Veil, No Guard ...)
 * and the stages a hit ignores (Unaware, Keen Eye). Each has a case where it works and one where it does not. Fixtures in [R17C9].
 */
class R17C9aDamageTest {
    private val delta = 0.05

    private fun ratio(mainAbility: String, move: String, wildAbility: String = "", wildSp: Int = 801, setup: (Battle, Mon) -> Unit = { _, _ -> }) =
        R17C9.ratio(mainAbility, wildAbility, move, wildSp, setup)

    private fun pinch(b: Battle, m: Mon) { m.hp = R17C9.maxHp(m) / 3 }

    // ---------------------------------------------------------------- power

    @Test fun overgrowBlazeTorrentSwarmBoostTheirTypeAtAThirdOfTheHp() {
        for ((ab, move) in listOf("Overgrow" to "razorleaf", "Blaze" to "ember", "Torrent" to "watergun", "Swarm" to "bugbite")) {
            assertEquals("$ab at 1/3", 1.5, ratio(ab, move, setup = ::pinch), delta)
            assertEquals("$ab at full HP", 1.0, ratio(ab, move), delta)
            assertEquals("$ab, another type", 1.0, ratio(ab, "karatechop", setup = ::pinch), delta)
        }
        assertEquals("1/3 of the max HP exactly counts, one HP more does not", 1.0, ratio("Blaze", "ember") { _, m -> m.hp = R17C9.maxHp(m) / 3 + 1 }, delta)
    }

    @Test fun technicianBoostsPowerSixtyOrLess() {
        assertEquals(1.5, ratio("Technician", "tackle"), delta)          // 40
        assertEquals(1.5, ratio("Technician", "bugbite"), delta)         // 60
        assertEquals(1.0, ratio("Technician", "bodyslam"), delta)        // 85
    }

    @Test fun flagMovesAreBoosted() {
        assertEquals(1.2, ratio("Iron Fist", "machpunch"), delta)
        assertEquals(1.0, ratio("Iron Fist", "karatechop"), delta)
        assertEquals(1.5, ratio("Strong Jaw", "crunch"), delta)
        assertEquals(1.0, ratio("Strong Jaw", "darkpulse"), delta)
        assertEquals(1.5, ratio("Mega Launcher", "aurasphere"), delta)
        assertEquals(1.0, ratio("Mega Launcher", "karatechop"), delta)
        assertEquals(1.3, ratio("Tough Claws", "bodyslam"), delta)
        assertEquals(1.0, ratio("Tough Claws", "watergun"), delta)
        assertEquals(1.2, ratio("Reckless", "doubleedge"), delta)
        assertEquals(1.2, ratio("Reckless", "highjumpkick"), delta)
        assertEquals(1.0, ratio("Reckless", "bodyslam"), delta)
    }

    @Test fun sheerForceBoostsAMoveWithAnExtraEffectAndTakesTheEffectAway() {
        assertEquals(1.3, ratio("Sheer Force", "firepunch"), delta)
        assertEquals(1.0, ratio("Sheer Force", "bodyslam"), delta)
        // the burn of Fire Punch never comes, however lucky the roll (low = every chance is met)
        val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val m = R17C9.main("Sheer Force", "firepunch")
        R17C9.round(m, b, "firepunch")
        assertNull(b.enemy.status)
        val b2 = R17C9.battle(R17C9.wild("", 801, "tackle")); val m2 = R17C9.main("", "firepunch")
        R17C9.round(m2, b2, "firepunch")
        assertEquals("brn", b2.enemy.status)
    }

    @Test fun sereneGraceDoublesTheChanceOfAnExtraEffect() {
        // headbutt: 30 % flinch; the roll is 45, so only the doubled 60 % is met. The Main is faster, so the wild one has not acted yet.
        fun flinched(ability: String): Boolean {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val m = R17C9.main(ability, "headbutt")
            val e = R17C9.round(m, b, "headbutt", "tackle", R17C9.fixed(45))
            return e.lines().any { "flinched" in it }
        }
        assertTrue(flinched("Serene Grace"))
        assertFalse(flinched(""))
    }

    @Test fun rivalryDependsOnTheGenders() {
        fun dmg(mg: Char, wg: Char, ab: String): Int {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle", gender = wg)); val m = R17C9.main(ab, "tackle", gender = mg)
            return R17C9.dmg(b, m, "tackle")
        }
        val plain = dmg('M', 'M', "")
        assertEquals(1.25, dmg('M', 'M', "Rivalry").toDouble() / plain, delta)
        assertEquals(1.25, dmg('F', 'F', "Rivalry").toDouble() / plain, delta)
        assertEquals(0.75, dmg('M', 'F', "Rivalry").toDouble() / plain, delta)
        assertEquals(1.0, dmg('M', 'N', "Rivalry").toDouble() / plain, delta)
        assertEquals(1.0, dmg('N', 'N', "Rivalry").toDouble() / plain, delta)
    }

    @Test fun flashFireBoostsFireMovesOnlyOnceLit() {
        val b = R17C9.battle(R17C9.wild("Flash Fire", 801, "ember")); val m = R17C9.main("", "ember")
        val before = R17C9.dmg(b, m, "ember", byMain = false)
        R17C9.round(m, b, "ember") // absorbed
        val after = R17C9.dmg(b, m, "ember", byMain = false)
        assertEquals(1.5, after.toDouble() / before, delta)
        assertEquals("only Fire moves", R17C9.dmg(R17C9.battle(R17C9.wild("", 801)), m, "watergun", byMain = false), R17C9.dmg(b, m, "watergun", byMain = false))
    }

    // ---------------------------------------------------------------- attack, STAB

    @Test fun hugePowerAndPurePowerDoubleThePhysicalAttack() {
        for (ab in listOf("Huge Power", "Pure Power")) {
            assertEquals(ab, 2.0, ratio(ab, "tackle"), 0.08)
            assertEquals("$ab, a special move", 1.0, ratio(ab, "watergun"), delta)
        }
    }

    @Test fun gutsBoostsWithAStatusAndIgnoresTheBurn() {
        val burned = { b: Battle, _: Mon -> b.player.status = "brn" }
        assertEquals("a burn halves the plain hit, Guts gives 1.5 and no halving", 3.0, ratio("Guts", "tackle", setup = burned), 0.15)
        assertEquals("poisoned: 1.5", 1.5, ratio("Guts", "tackle") { b, _ -> b.player.status = "psn" }, delta)
        assertEquals("no status: nothing", 1.0, ratio("Guts", "tackle"), delta)
        assertEquals("a special move is not helped", 1.0, ratio("Guts", "watergun") { b, _ -> b.player.status = "psn" }, delta)
    }

    @Test fun hustleBoostsAttackAndCostsPhysicalAccuracy() {
        assertEquals(1.5, ratio("Hustle", "tackle"), delta)
        assertEquals(1.0, ratio("Hustle", "watergun"), delta)
        val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val m = R17C9.main("Hustle", "tackle")
        assertEquals(80.0, R17C9.accuracy(b, m, "tackle")!!, 0.001)
        assertEquals(100.0, R17C9.accuracy(b, m, "watergun")!!, 0.001)
    }

    @Test fun adaptabilityMakesStabTwo() {
        assertEquals(2.0 / 1.5, ratio("Adaptability", "tackle"), delta) // Normal user, Normal move
        assertEquals(1.0, ratio("Adaptability", "watergun"), delta)     // no STAB
    }

    // ---------------------------------------------------------------- the final damage

    @Test fun thickFatHeatproofAndDrySkinChangeFireAndIce() {
        assertEquals(0.5, ratio("", "ember", "Thick Fat"), delta)
        assertEquals(0.5, ratio("", "icebeam", "Thick Fat"), delta)
        assertEquals(1.0, ratio("", "watergun", "Thick Fat"), delta)
        assertEquals(0.5, ratio("", "ember", "Heatproof"), delta)
        assertEquals(1.0, ratio("", "icebeam", "Heatproof"), delta)
        assertEquals(1.25, ratio("", "ember", "Dry Skin"), delta)
    }

    @Test fun solidRockAndFilterWeakenSuperEffectiveHits() {
        for (ab in listOf("Solid Rock", "Filter")) {
            assertEquals(ab, 0.75, ratio("", "karatechop", ab), delta)   // Fighting vs Normal
            assertEquals("$ab, a neutral hit", 1.0, ratio("", "tackle", ab), delta)
        }
    }

    @Test fun tintedLensDoublesNotVeryEffectiveHits() {
        assertEquals(2.0, ratio("Tinted Lens", "tackle", wildSp = 806), 0.1) // Normal vs Rock
        assertEquals(1.0, ratio("Tinted Lens", "tackle"), delta)
    }

    @Test fun scrappyHitsGhostTypes() {
        val b = R17C9.battle(R17C9.wild("", 804, "tackle")); val plain = R17C9.main("", "tackle"); val scrappy = R17C9.main("Scrappy", "tackle")
        for (move in listOf("tackle", "karatechop")) {
            assertEquals("$move: no effect without it", 0, R17C9.dmg(b, plain, move))
            assertTrue("$move: hits with Scrappy", R17C9.dmg(b, scrappy, move) > 0)
        }
    }

    @Test fun moldBreakerIgnoresTheBreakableDefences() {
        assertEquals(1.0, ratio("Mold Breaker", "karatechop", "Solid Rock"), delta)
        assertEquals(1.0, ratio("Teravolt", "ember", "Thick Fat"), delta)
        assertEquals(1.0, ratio("Turboblaze", "ember", "Heatproof"), delta)
        assertEquals("Mold Breaker does not touch what is not breakable", 1.0, ratio("Mold Breaker", "tackle", "Rough Skin"), delta)
    }

    // ---------------------------------------------------------------- accuracy

    @Test fun accuracyAbilities() {
        val b = R17C9.battle(R17C9.wild("", 801, "tackle"))
        fun acc(mainAb: String, wildAb: String, move: String = "tackle", setup: (Battle) -> Unit = {}): Double? {
            val bb = R17C9.battle(R17C9.wild(wildAb, 801, "tackle")); setup(bb)
            return R17C9.accuracy(bb, R17C9.main(mainAb, "tackle"), move)
        }
        assertEquals(130.0, acc("Compound Eyes", "")!!, 0.001)
        assertEquals(100.0, acc("", "")!!, 0.001)
        assertEquals(80.0, acc("", "Sand Veil") { it.field.weather = "sand" }!!, 0.001)
        assertEquals("Sand Veil needs the sandstorm", 100.0, acc("", "Sand Veil") { it.field.weather = "rain" }!!, 0.001)
        assertEquals(80.0, acc("", "Snow Cloak") { it.field.weather = "hail" }!!, 0.001)
        assertEquals(80.0, acc("", "Snow Cloak") { it.field.weather = "snow" }!!, 0.001)
        assertEquals(100.0, acc("", "Snow Cloak") { it.field.weather = "sand" }!!, 0.001)
        assertEquals(50.0, acc("", "Tangled Feet") { it.enemy.volatiles["confusion"] = 3 }!!, 0.001)
        assertEquals("Tangled Feet needs the confusion", 100.0, acc("", "Tangled Feet")!!, 0.001)
        assertNull("No Guard: its own moves", acc("No Guard", ""))
        assertNull("No Guard: moves against it", acc("", "No Guard"))
        assertEquals("Mold Breaker ignores Sand Veil", 100.0, acc("Mold Breaker", "Sand Veil") { it.field.weather = "sand" }!!, 0.001)
        assertNull("a move that never misses stays so", R17C9.accuracy(b, R17C9.main("Compound Eyes", "aurasphere"), "aurasphere"))
    }

    @Test fun skillLinkLaterHitsOfAMultiAccuracyMoveAlwaysConnect() {
        val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val m = R17C9.main("Skill Link", "tripleaxel")
        assertEquals("the first hit rolls", 90.0, R17C9.accuracy(b, m, "tripleaxel", hitIndex = 0)!!, 0.001)
        assertNull("the later hits do not", R17C9.accuracy(b, m, "tripleaxel", hitIndex = 1))
        assertEquals(90.0, R17C9.accuracy(b, R17C9.main("", "tripleaxel"), "tripleaxel", hitIndex = 1)!!, 0.001)
        // a played Triple Axel: the first check passes (0.0), the second would miss (0.99) without Skill Link
        fun hits(ab: String): Int {
            val bb = R17C9.battle(R17C9.wild("", 801, "tackle"))
            val queue = ArrayDeque(listOf(0.0, 0.99, 0.99))
            val rng = object : kotlin.random.Random() { override fun nextBits(bitCount: Int) = 0; override fun nextDouble() = queue.removeFirstOrNull() ?: 0.0 }
            return R17C9.round(R17C9.main(ab, "tripleaxel"), bb, "tripleaxel", null, rng).attacks().single().hits
        }
        assertEquals(3, hits("Skill Link"))
        assertEquals(1, hits(""))
    }

    @Test fun skillLinkMakesMultiHitMovesHitFiveTimes() {
        fun hits(ab: String): Int {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle"))
            return R17C9.round(R17C9.main(ab, "bulletseed"), b, "bulletseed", null, R17C9.low).attacks().single().hits
        }
        assertEquals(5, hits("Skill Link"))
        assertEquals("with the lowest roll a plain one hits twice", 2, hits(""))
    }

    // ---------------------------------------------------------------- stages

    @Test fun keenEyeIgnoresTheTargetsEvasionAndUnawareIgnoresStages() {
        val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.enemy.boosts[6] = 6
        assertEquals(-6, BattleEngine.accuracyStage(R17C9.hit(b, R17C9.main("", "tackle"), "tackle")))
        assertEquals(0, BattleEngine.accuracyStage(R17C9.hit(b, R17C9.main("Keen Eye", "tackle"), "tackle")))
        assertEquals(0, BattleEngine.accuracyStage(R17C9.hit(b, R17C9.main("Unaware", "tackle"), "tackle")))
        // Unaware on the target ignores the attacker's accuracy stage
        val b2 = R17C9.battle(R17C9.wild("Unaware", 801, "tackle")); b2.player.boosts[5] = 6
        assertEquals(0, BattleEngine.accuracyStage(R17C9.hit(b2, R17C9.main("", "tackle"), "tackle")))
        val b3 = R17C9.battle(R17C9.wild("", 801, "tackle")); b3.player.boosts[5] = 6
        assertEquals(6, BattleEngine.accuracyStage(R17C9.hit(b3, R17C9.main("", "tackle"), "tackle")))
    }

    @Test fun unawareIgnoresStatChangesInDamage() {
        // the target's Defense +6: Unaware attacker ignores it
        fun withDef6(ab: String): Int { val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.enemy.boosts[1] = 6; return R17C9.dmg(b, R17C9.main(ab, "tackle"), "tackle") }
        val plainNoBoost = R17C9.dmg(R17C9.battle(R17C9.wild("", 801, "tackle")), R17C9.main("", "tackle"), "tackle")
        assertEquals(plainNoBoost, withDef6("Unaware"))
        assertTrue(withDef6("") < plainNoBoost)
        // the attacker's Attack +6: an Unaware target ignores it
        fun withAtk6(wildAb: String): Int { val b = R17C9.battle(R17C9.wild(wildAb, 801, "tackle")); b.player.boosts[0] = 6; return R17C9.dmg(b, R17C9.main("", "tackle"), "tackle") }
        assertEquals(plainNoBoost, withAtk6("Unaware"))
        assertTrue(withAtk6("") > plainNoBoost)
        // Mold Breaker ignores the target's Unaware
        fun withAtk6Mb(): Int { val b = R17C9.battle(R17C9.wild("Unaware", 801, "tackle")); b.player.boosts[0] = 6; return R17C9.dmg(b, R17C9.main("Mold Breaker", "tackle"), "tackle") }
        assertTrue(withAtk6Mb() > plainNoBoost)
    }
}
