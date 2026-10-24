package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-4: every power formula of [MovePower] at its edges, the situational doubles and the type rules. Hits are built straight from a
 * [SimpleView] (no round), so only the rules that need the running round are left to R17C4TimingTest.
 */
class R17C4PowerTest {
    private val data = R17C4Fixture.data()
    private val F = R17C4Fixture

    private fun mon(sp: Species, lv: Int = 50, vararg moves: String) = TestData.mon(data, sp.num, lv, moves.toList().ifEmpty { listOf("tackle") })
    private fun move(id: String) = data.move(id) ?: error("no move $id")

    private fun hit(
        id: String, a: Mon, d: Mon, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide(), main: Boolean = true,
        battle: Battle? = null, index: Int = 0,
    ): Hit {
        val view = if (main) SimpleView(data, a, d, aSide, dSide, battle = battle) else SimpleView(data, d, a, dSide, aSide, battle = battle)
        return BattleEngine.newHit(view, main, a, aSide, d, dSide, move(id), index)
    }

    private fun power(h: Hit): Int = MovePower.basePower(h) ?: error("no power for ${h.move.id}")
    private fun power(id: String, a: Mon, d: Mon, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide(), main: Boolean = true) = power(hit(id, a, d, aSide, dSide, main))
    private fun mod(id: String, a: Mon, d: Mon, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide()) = MovePower.powerMod(hit(id, a, d, aSide, dSide))
    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    // ---------------------------------------------------------------- weight

    @Test fun lowKickAndGrassKnotFollowTheTargetWeight() {
        val expected = listOf(20, 40, 40, 60, 60, 80, 80, 100, 100, 120, 80) // 5, 10, 24.9, 25, 49.9, 50, 99.9, 100, 199.9, 200 kg, unknown (= 60 kg)
        val user = mon(F.HITTER)
        for (id in listOf("lowkick", "grassknot")) {
            F.WEIGHT_SPECIES.forEachIndexed { i, sp -> assertEquals("$id at ${F.WEIGHTS[i]} kg", expected[i], power(id, user, mon(sp))) }
        }
    }

    @Test fun heavySlamAndHeatCrashFollowTheWeightRatio() {
        fun p(userW: Double, targetW: Double, id: String): Int {
            val d = R17C4Fixture.data(listOf(F.HITTER.copy(num = 470, weightKg = userW), F.TANK.copy(num = 471, weightKg = targetW)))
            val a = TestData.mon(d, 470, 50); val t = TestData.mon(d, 471, 50)
            return MovePower.basePower(BattleEngine.newHit(SimpleView(d, a, t), true, a, BattleSide(), t, BattleSide(), d.move(id)!!))!!
        }
        for (id in listOf("heavyslam", "heatcrash")) {
            assertEquals(120, p(100.0, 20.0, id)) // exactly 5 times
            assertEquals(100, p(100.0, 25.0, id)) // 4
            assertEquals(80, p(100.0, 30.0, id))  // 3.33
            assertEquals(60, p(100.0, 50.0, id))  // 2
            assertEquals(40, p(100.0, 51.0, id))  // 1.96
            assertEquals(40, p(100.0, 100.0, id)) // 1
            assertEquals(40, p(50.0, 100.0, id))  // lighter than the target
        }
    }

    // ---------------------------------------------------------------- HP

    @Test fun flailAndReversalFollowTheUsersHp() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val max = maxHp(user)
        assertTrue(max > 150)
        fun at(hp: Int): Int { user.hp = hp; return power("flail", user, foe) }
        assertEquals(20, at(max))
        val x32 = (1..max).first { 48 * it / max == 32 }
        val x33 = (1..max).first { 48 * it / max == 33 }
        assertEquals(40, at(x32)); assertEquals(20, at(x33))
        assertEquals(80, at((1..max).first { 48 * it / max == 16 })); assertEquals(40, at((1..max).first { 48 * it / max == 17 }))
        assertEquals(100, at((1..max).first { 48 * it / max == 9 })); assertEquals(80, at((1..max).first { 48 * it / max == 10 }))
        assertEquals(150, at((1..max).first { 48 * it / max == 4 })); assertEquals(100, at((1..max).first { 48 * it / max == 5 }))
        assertEquals(200, at((1..max).first { 48 * it / max == 1 })); assertEquals(150, at((1..max).first { 48 * it / max == 2 }))
        assertEquals(200, at(1))
        user.hp = max / 2
        assertEquals(power("flail", user, foe), power("reversal", user, foe))
    }

    @Test fun eruptionFamilyIsHpTimes150AtLeastOne() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val max = maxHp(user)
        for (id in listOf("eruption", "waterspout", "dragonenergy")) {
            user.hp = max; assertEquals(150, power(id, user, foe))
            user.hp = max / 2; assertEquals(150 * (max / 2) / max, power(id, user, foe))
            user.hp = 1; assertEquals(maxOf(1, 150 / max), power(id, user, foe))
        }
    }

    @Test fun crushGripWringOutHardPressFollowTheTargetsHp() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val max = maxHp(foe)
        for ((id, top) in listOf("crushgrip" to 120, "wringout" to 120, "hardpress" to 100)) {
            foe.hp = max; assertEquals(id, top, power(id, user, foe))
            foe.hp = max / 4; assertEquals(id, top * (max / 4) / max, power(id, user, foe))
            foe.hp = 1; assertEquals(id, maxOf(1, top / max), power(id, user, foe))
        }
    }

    // ---------------------------------------------------------------- speed

    @Test fun gyroBallIsTheSlowerUsersPower() {
        val user = mon(F.HITTER); val foe = mon(F.HITTER)
        assertEquals(26, power("gyroball", user, foe)) // equal speed: 25 * 1 + 1
        val foeFast = BattleSide().also { it.boosts[4] = 2 }  // x2
        assertEquals(51, power("gyroball", user, foe, dSide = foeFast))
        val foeFaster = BattleSide().also { it.boosts[4] = 6 } // x4
        assertEquals(101, power("gyroball", user, foe, dSide = foeFaster))
        val userSlow = BattleSide().also { it.boosts[4] = -6 } // x1/4: ratio 16
        assertEquals(150, power("gyroball", user, foe, aSide = userSlow, dSide = foeFaster))
        // paralysis halves the speed of whoever has it
        assertEquals(51, power("gyroball", user, foe, aSide = BattleSide(status = "par"))) // the user at half speed: ratio 2
        assertEquals(13, power("gyroball", user, foe, dSide = BattleSide(status = "par"))) // 25 * 0.5 = 12.5 -> 12, + 1
    }

    @Test fun electroBallIsTheFasterUsersPower() {
        val user = mon(F.HITTER); val foe = mon(F.HITTER)
        assertEquals(60, power("electroball", user, foe)) // equal
        fun with(stage: Int) = power("electroball", user, foe, aSide = BattleSide().also { it.boosts[4] = stage })
        assertEquals(80, with(2))   // x2
        assertEquals(120, with(4))  // x3
        assertEquals(150, with(6))  // x4
        assertEquals(40, with(-2))  // slower
        assertEquals(60, power("electroball", mon(F.FAST), mon(F.HITTER)))
        assertEquals(40, power("electroball", mon(F.TANK), mon(F.HITTER)))
    }

    // ---------------------------------------------------------------- friendship

    @Test fun returnAndFrustrationUseTheMonsOwnFriendship() {
        val foe = mon(F.TANK)
        fun friend(f: Int): Mon = mon(F.HITTER).also { it.friendship = f }
        for (id in listOf("return", "pikapapow", "veeveevolley")) {
            assertEquals(id, 1, power(id, friend(0), foe))
            assertEquals(id, 40, power(id, friend(100), foe))
            assertEquals(id, 102, power(id, friend(255), foe))
            assertEquals(id, 102, power(id, friend(400), foe)) // an older save's higher number counts as 255
        }
        assertEquals(102, power("frustration", friend(0), foe))
        assertEquals(60, power("frustration", friend(105), foe)) // (255 - 105) * 10 / 25
        assertEquals(1, power("frustration", friend(255), foe))
    }

    @Test fun aWildPokemonCountsAsFriendshipSeventy() {
        val wild = mon(F.HITTER)  // friendship 0 in the data
        val main = mon(F.TANK)
        assertEquals(28, power("return", wild, main, main = false))
        assertEquals(74, power("frustration", wild, main, main = false))
    }

    // ---------------------------------------------------------------- stages

    @Test fun storedPowerAndPowerTripCountTheUsersRaisedStages() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("storedpower", "powertrip")) {
            assertEquals(20, power(id, user, foe))
            val side = BattleSide().also { it.boosts[0] = 2; it.boosts[4] = 1; it.boosts[1] = -3; it.boosts[6] = 1 } // +2 +1 +1 raised, -3 ignored
            assertEquals(id, 20 + 20 * 4, power(id, user, foe, aSide = side))
        }
    }

    @Test fun punishmentCountsTheTargetsRaisedStagesUpTo200() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(60, power("punishment", user, foe))
        assertEquals(60 + 20 * 3, power("punishment", user, foe, dSide = BattleSide().also { it.boosts[0] = 3 }))
        assertEquals(200, power("punishment", user, foe, dSide = BattleSide().also { it.boosts[0] = 6; it.boosts[1] = 6 }))
        // the user's own stages do not count
        assertEquals(60, power("punishment", user, foe, aSide = BattleSide().also { it.boosts[0] = 6 }))
    }

    @Test fun trumpCardAndRageFist() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(40, power("trumpcard", user, foe))
        assertEquals(50, power("ragefist", user, foe))
        assertEquals(50 + 50 * 3, power("ragefist", user, foe, aSide = BattleSide(timesHit = 3)))
        assertEquals(350, power("ragefist", user, foe, aSide = BattleSide(timesHit = 6)))
        assertEquals(350, power("ragefist", user, foe, aSide = BattleSide(timesHit = 40)))
    }

    // ---------------------------------------------------------------- random power

    @Test fun magnitudeAndPresentReadTheirRolledNumber() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val powers = mapOf(4 to 10, 5 to 30, 6 to 50, 7 to 70, 8 to 90, 9 to 110, 10 to 150)
        for ((level, expected) in powers) {
            val side = BattleSide().also { it.volatiles["mp.rand"] = level }
            assertEquals("Magnitude $level", expected, power("magnitude", user, foe, aSide = side))
        }
        assertEquals(71, power("magnitude", user, foe)) // an estimate uses the average
        for (p in listOf(40, 80, 120)) assertEquals(p, power("present", user, foe, aSide = BattleSide().also { it.volatiles["mp.rand"] = p }))
        assertEquals(52, power("present", user, foe))
    }

    // ---------------------------------------------------------------- situational doubles

    @Test fun statusDoublesHexAndInfernalParade() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("hex", "infernalparade")) {
            assertEquals(1.0, mod(id, user, foe), 0.0)
            for (s in listOf("par", "brn", "psn", "tox", "slp", "frz")) assertEquals("$id $s", 2.0, mod(id, user, foe, dSide = BattleSide(status = s)), 0.0)
        }
    }

    @Test fun poisonDoublesVenoshockAndBarbBarrage() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("venoshock", "barbbarrage")) {
            assertEquals(1.0, mod(id, user, foe), 0.0)
            assertEquals(1.0, mod(id, user, foe, dSide = BattleSide(status = "brn")), 0.0)
            assertEquals(2.0, mod(id, user, foe, dSide = BattleSide(status = "psn")), 0.0)
            assertEquals(2.0, mod(id, user, foe, dSide = BattleSide(status = "tox")), 0.0)
        }
    }

    @Test fun facadeDoublesWithAStatusAndIgnoresTheBurn() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(1.0, mod("facade", user, foe), 0.0)
        for (s in listOf("brn", "psn", "tox", "par")) assertEquals(s, 2.0, mod("facade", user, foe, aSide = BattleSide(status = s)), 0.0)
        assertEquals(1.0, mod("facade", user, foe, aSide = BattleSide(status = "slp")), 0.0)
        assertEquals(1.0, mod("facade", user, foe, dSide = BattleSide(status = "brn")), 0.0) // the TARGET's status does not count
        // the burn does not halve Facade (it halves a plain physical move) and it is exactly the same damage as a poisoned Facade
        fun dmg(move: String, status: String?) = BattleEngine.damage(data, user, BattleSide(status = status), foe, BattleSide(), move(move), Random(1), false, 100).damage
        assertEquals(dmg("facade", "psn"), dmg("facade", "brn"))
        assertEquals(dmg("tackle", null) / 2, dmg("tackle", "brn"))
        assertTrue(dmg("facade", "brn") > dmg("facade", null))
    }

    @Test fun brineDoublesAtHalfHp() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val max = maxHp(foe)
        foe.hp = max; assertEquals(1.0, mod("brine", user, foe), 0.0)
        foe.hp = max / 2 + 1; assertEquals(1.0, mod("brine", user, foe), 0.0)
        foe.hp = max / 2; assertEquals(2.0, mod("brine", user, foe), 0.0) // half or less
        foe.hp = 1; assertEquals(2.0, mod("brine", user, foe), 0.0)
    }

    @Test fun itemRulesAcrobaticsAndKnockOff() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(2.0, mod("acrobatics", user, foe), 0.0)
        user.heldItem = "everstone"; assertEquals(1.0, mod("acrobatics", user, foe), 0.0)
        assertEquals(1.0, mod("knockoff", user, foe), 0.0)
        foe.heldItem = "lucky-egg"
        assertEquals(1.5, mod("knockoff", user, foe), 0.0)
        assertEquals("lucky-egg", foe.heldItem) // the item is never taken (no battle code touches it)
    }

    @Test fun wakeUpSlapAndSmellingSaltsDoubleAgainstTheirStatus() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(1.0, mod("wakeupslap", user, foe), 0.0)
        assertEquals(2.0, mod("wakeupslap", user, foe, dSide = BattleSide(status = "slp")), 0.0)
        assertEquals(1.0, mod("wakeupslap", user, foe, dSide = BattleSide(status = "par")), 0.0)
        assertEquals(2.0, mod("smellingsalts", user, foe, dSide = BattleSide(status = "par")), 0.0)
        assertEquals(1.0, mod("smellingsalts", user, foe, dSide = BattleSide(status = "slp")), 0.0)
    }

    @Test fun stompingTantrumAndTemperFlareDoubleAfterAFailedMove() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("stompingtantrum", "temperflare")) {
            assertEquals(1.0, mod(id, user, foe), 0.0)
            assertEquals(2.0, mod(id, user, foe, aSide = BattleSide(lastMoveFailed = true)), 0.0)
        }
    }

    @Test fun lashOutDoublesWhenTheRoundNotedALoweredStat() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val battle = Battle(foe, Tier.NORMAL).also { it.rounds = 3 }
        val side = BattleSide()
        assertEquals(1.0, MovePower.powerMod(hit("lashout", user, foe, side, battle = battle)), 0.0)
        side.notes["mp.lowered"] = "3"
        assertEquals(2.0, MovePower.powerMod(hit("lashout", user, foe, side, battle = battle)), 0.0)
        battle.rounds = 4 // the next round: no longer "this round"
        assertEquals(1.0, MovePower.powerMod(hit("lashout", user, foe, side, battle = battle)), 0.0)
    }

    @Test fun lastRespectsAndRetaliateKeepTheirListedPower() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("lastrespects", "retaliate")) {
            val h = hit(id, user, foe)
            assertNull(id, MovePower.basePower(h))
            assertEquals(1.0, MovePower.powerMod(h), 0.0)
        }
        assertEquals(50, move("lastrespects").power); assertEquals(70, move("retaliate").power)
    }

    // ---------------------------------------------------------------- build-ups (state read by the formula)

    @Test fun echoedVoiceAddsFortyPerUseUpToTwoHundred() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        fun at(n: Int?) = power("echoedvoice", user, foe, aSide = BattleSide().also { if (n != null) it.volatiles["mp.echo"] = n })
        assertEquals(40, at(null)); assertEquals(80, at(1)); assertEquals(120, at(2)); assertEquals(160, at(3)); assertEquals(200, at(4))
        assertEquals(200, at(9))
    }

    @Test fun furyCutterDoublesUpTo160() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        fun at(n: Int?) = power("furycutter", user, foe, aSide = BattleSide().also { if (n != null) it.volatiles["mp.fury"] = n })
        assertEquals(40, at(null)); assertEquals(80, at(1)); assertEquals(160, at(2)); assertEquals(160, at(5))
    }

    @Test fun rolloutDoublesPerHitAndAgainAfterDefenseCurl() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        fun side(n: Int, curl: Boolean = false) = BattleSide(actions = n).also {
            it.volatiles["mp.rollout"] = n; it.volatiles["mp.rollout.at"] = n // the run stays valid while the side's action count is the stored one
            if (curl) it.volatiles["defensecurl"] = 3
        }
        for (id in listOf("rollout", "iceball")) {
            assertEquals(30, power(id, user, foe))
            assertEquals(60, power(id, user, foe, aSide = side(1)))
            assertEquals(120, power(id, user, foe, aSide = side(2)))
            assertEquals(480, power(id, user, foe, aSide = side(4)))
            assertEquals(60, power(id, user, foe, aSide = side(0, curl = true)))
            assertEquals(240, power(id, user, foe, aSide = side(2, curl = true)))
            // a run that was broken (the side acted without it) starts again
            val broken = BattleSide(actions = 5).also { it.volatiles["mp.rollout"] = 2; it.volatiles["mp.rollout.at"] = 3 }
            assertEquals(30, power(id, user, foe, aSide = broken))
        }
    }

    @Test fun tripleAxelAndTripleKickAddTheirPowerPerHit() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        assertEquals(listOf(20, 40, 60), (0..2).map { power(hit("tripleaxel", user, foe, index = it)) })
        assertEquals(listOf(10, 20, 30), (0..2).map { power(hit("triplekick", user, foe, index = it)) })
        assertNull(MovePower.basePower(hit("populationbomb", user, foe, index = 4))) // constant 20
    }

    @Test fun beatUpPowerIsFivePlusTheMembersBaseAttackOverTen() {
        val strong = F.HITTER.copy(num = 480, base = F.BASE.copy(atk = 134))
        val weak = F.HITTER.copy(num = 481, base = F.BASE.copy(atk = 9))
        val d = R17C4Fixture.data(listOf(strong, weak))
        val a = TestData.mon(d, 400, 50); val b = TestData.mon(d, 401, 50)
        val battle = Battle(b, Tier.NORMAL).also { it.team = listOf(a, TestData.mon(d, 480, 30, uid = "x"), TestData.mon(d, 481, 30, uid = "y")) }
        val view = SimpleView(d, a, b, battle = battle)
        fun p(i: Int) = MovePower.basePower(BattleEngine.newHit(view, true, a, BattleSide(), b, BattleSide(), d.move("beatup")!!, i))
        assertEquals(5 + 100 / 10, p(0))   // the user itself first: base Attack 100
        assertEquals(5 + 134 / 10, p(1))
        assertEquals(5 + 9 / 10, p(2))
        assertNotEquals(60, p(0)) // not the flat 60
        // the wild one is alone
        val wildView = SimpleView(d, b, a, battle = battle)
        assertEquals(5 + 100 / 10, MovePower.basePower(BattleEngine.newHit(wildView, false, a, BattleSide(), b, BattleSide(), d.move("beatup")!!, 0)))
    }

    // ---------------------------------------------------------------- special damage (the pieces that need no round)

    @Test fun endeavorBringsTheTargetDownToTheUsersHp() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        user.hp = 40
        assertEquals(foe.hp - 40, MovePower.fixedDamage(hit("endeavor", user, foe)))
        foe.hp = 40
        assertEquals(0, MovePower.fixedDamage(hit("endeavor", user, foe)))
        foe.hp = 10
        assertEquals(0, MovePower.fixedDamage(hit("endeavor", user, foe))) // never negative
    }

    @Test fun finalGambitDealsTheUsersHp() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        user.hp = 77
        assertEquals(77, MovePower.fixedDamage(hit("finalgambit", user, foe)))
        user.hp = 1
        assertEquals(1, MovePower.fixedDamage(hit("finalgambit", user, foe)))
    }

    @Test fun psywaveStaysBetweenHalfAndOneAndAHalfTheLevel() {
        val user = mon(F.HITTER, 60); val foe = mon(F.TANK)
        assertEquals(60, MovePower.fixedDamage(hit("psywave", user, foe))) // an estimate: the level
        for (pct in listOf(50, 100, 150)) {
            val side = BattleSide().also { it.volatiles["mp.rand"] = pct }
            assertEquals(60 * pct / 100, MovePower.fixedDamage(hit("psywave", user, foe, side)))
        }
    }

    @Test fun counterMovesWithoutAHitDealNothingInAnEstimate() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        for (id in listOf("counter", "mirrorcoat", "metalburst", "comeuppance")) assertEquals(id, 0, MovePower.fixedDamage(hit(id, user, foe)))
    }

    @Test fun noMoveOfTheTicketKeepsTheFlatSixty() {
        val user = mon(F.HITTER); val foe = mon(F.TANK)
        val ids = MovePower.VARIABLE_POWER - "spitup" // Spit Up is R17C-5's
        for (id in ids) {
            val h = hit(id, user, foe)
            assertTrue("$id keeps the flat 60", MovePower.basePower(h) != null || MovePower.fixedDamage(h) != null)
        }
    }

    // ---------------------------------------------------------------- type rules

    @Test fun hiddenPowerTypeComesFromTheIvs() {
        fun ivs(hp: Int, atk: Int, def: Int, spa: Int, spd: Int, spe: Int) = Stats(hp, atk, def, spa, spd, spe)
        assertEquals(Type.DARK, MovePower.hiddenPowerType(ivs(31, 31, 31, 31, 31, 31)))
        assertEquals(Type.FIGHTING, MovePower.hiddenPowerType(ivs(30, 30, 30, 30, 30, 30)))
        assertEquals(Type.FIRE, MovePower.hiddenPowerType(ivs(31, 30, 31, 30, 31, 30)))   // the games' Fire spread
        assertEquals(Type.ICE, MovePower.hiddenPowerType(ivs(31, 30, 30, 31, 31, 31)))    // the games' Ice spread
        assertEquals(Type.GROUND, MovePower.hiddenPowerType(ivs(31, 31, 31, 30, 30, 31))) // HP, Atk, Def, Spe odd: 1 + 2 + 4 + 8 = 15 -> 15 * 15 / 63 = 3
        val base = mon(F.HITTER)
        val foe = mon(F.TANK)
        val ivMon = Mon(base.uid, base.species, base.name, base.level, 0, ivs(31, 30, 31, 30, 31, 30), Stats.ZERO, base.nature, 'M', false, "x", mutableListOf("hiddenpower"), 1)
        assertEquals(Type.FIRE, hit("hiddenpower", ivMon, foe).type)
        assertEquals(Type.NORMAL, move("hiddenpower").type) // the data says Normal; the rule changes it for the hit
    }

    @Test fun revelationDanceTakesTheUsersFirstType() {
        val foe = mon(F.TANK)
        assertEquals(Type.GHOST, hit("revelationdance", mon(F.GHOST), foe).type)
        assertEquals(Type.NORMAL, hit("revelationdance", mon(F.FLYER), foe).type) // Normal / Flying: the first is Normal
        assertEquals(Type.FIRE, hit("revelationdance", mon(F.FIRE), foe).type)
    }

    @Test fun freezeDryHitsWaterSuperEffectively() {
        val user = mon(F.HITTER)
        fun eff(foe: Mon) = BattleEngine.damage(data, user, BattleSide(), foe, BattleSide(), move("freezedry"), Random(1), false, 100).effectiveness
        assertEquals(2.0, eff(mon(F.TANK)), 0.0)          // Ice vs Water is 1/2 in the chart
        assertEquals(1.0, eff(mon(F.HITTER)), 0.0)
    }

    @Test fun freezeDryOnWaterGrassUsesWaterDoubledAndGrassDoubled() {
        val user = mon(F.HITTER)
        val e = BattleEngine.damage(data, user, BattleSide(), mon(F.WATERGRASS), BattleSide(), move("freezedry"), Random(1), false, 100).effectiveness
        assertEquals(4.0, e, 0.0)
    }

    @Test fun flyingPressCountsFlyingToo() {
        val user = mon(F.HITTER)
        fun eff(id: String, foe: Mon) = BattleEngine.damage(data, user, BattleSide(), foe, BattleSide(), move(id), Random(1), false, 100).effectiveness
        // vs a Normal target: Fighting x2, Flying x1 = 2
        assertEquals(2.0, eff("flyingpress", mon(F.HITTER)), 0.0)
        // vs Normal / Flying: Fighting x2 * Fighting-vs-Flying 1/2 * Flying-vs-Normal 1 * Flying-vs-Flying 1 = 1
        assertEquals(1.0, eff("flyingpress", mon(F.FLYER)), 0.0)
        // vs a Ghost: Fighting is immune
        assertEquals(0.0, eff("flyingpress", mon(F.GHOST)), 0.0)
        // vs Psychic: Fighting x1/2, Flying x1 = 1/2
        assertEquals(0.5, eff("flyingpress", mon(F.PSY)), 0.0)
        // vs a Fire target (Flying vs Fire 1, Fighting vs Fire 1) = 1; vs Water 1
        assertEquals(1.0, eff("flyingpress", mon(F.FIRE)), 0.0)
        // the Flying part matters: vs Water / Grass a plain Fighting move is 1, Flying Press is 2 (Flying vs Grass)
        assertEquals(1.0, eff("lowkick", mon(F.WATERGRASS)), 0.0)
        assertEquals(2.0, eff("flyingpress", mon(F.WATERGRASS)), 0.0)
    }
}
