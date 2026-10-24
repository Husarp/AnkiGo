package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C5.attacks
import com.husarp.ankigo.game.R17C5.effects
import com.husarp.ankigo.game.R17C5.kinds
import com.husarp.ankigo.game.R17C5.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R17C-5: the heals and cures written as code, Stockpile / Swallow / Spit Up, Pain Split, Strength Sap, Belly Drum and friends, Haze. */
class R17C5HealsTest {
    private val data = R17C5.data
    private fun main(vararg m: String, sp: Int = 300) = R17C5.main(*m, sp = sp)
    private fun wild(vararg m: String, sp: Int = 301) = R17C5.wild(*m, sp = sp)
    private fun battle(w: Mon) = R17C5.battle(w)
    private fun round(m: Mon, b: Battle, mainIds: List<String>, wildIds: List<String>, rng: Random = Random(1), wildPower: Double = 1.0) =
        R17C5.playing(mainIds, wildIds) { R17C5.round(m, b, rng, wildPower = wildPower) }

    /** The Main (at 1 HP) uses [move] once in the given [weather]; returns the HP it gained. */
    private fun healedBy(move: String, weather: String?, extra: BattleRules? = null): Int {
        val m = main(move); val b = battle(wild("splash")); b.field.weather = weather
        m.hp = 1
        R17C5.playingAlone(listOf(move), listOf("splash"), listOfNotNull(extra)) { R17C5.round(m, b, Random(1)) }
        return m.hp - 1
    }

    private val max = R17C5.maxHp(R17C5.main("splash"))

    // ---------------------------------------------------------------- Moonlight, Morning Sun, Synthesis, Shore Up

    @Test fun theMoonlightFamilyHealsHalfAndTheWeatherChangesIt() {
        for (move in listOf("moonlight", "morningsun", "synthesis")) {
            assertEquals("$move", max / 2, healedBy(move, null))
            assertEquals("$move in the sun", max * 2 / 3, healedBy(move, "sun"))
            for (w in listOf("rain", "sand", "hail", "snow")) assertEquals("$move in $w", max / 4, healedBy(move, w))
        }
    }

    @Test fun shoreUpHealsMoreOnlyInASandstorm() {
        assertEquals(max / 2, healedBy("shoreup", null))
        assertEquals(max * 2 / 3, healedBy("shoreup", "sand"))
        assertEquals("rain does not change it", max / 2, healedBy("shoreup", "rain"))
        assertEquals(max / 2, healedBy("shoreup", "sun"))
    }

    @Test fun aRuleThatSuppressesTheWeatherIsRespected() {
        val cloudNine = object : BattleRules { override fun suppressWeather(v: BattleView) = true }
        assertEquals(max / 2, healedBy("moonlight", "sun", cloudNine))
    }

    @Test fun aHealAtFullHpFailsAndSaysSo() {
        val m = main("moonlight"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("moonlight"), listOf("splash"))
        assertTrue(e.attacks().first { it.byPlayer }.failed)
        assertTrue(e.lines().contains("But it failed!"))
    }

    @Test fun healBlockStopsEveryHealOfTheSet() {
        for (id in listOf("moonlight", "morningsun", "synthesis", "shoreup", "rest", "wish", "junglehealing", "lunarblessing", "strengthsap")) {
            val m = main(id); val b = battle(wild("splash"))
            m.hp = 10; b.player.volatiles["healblock"] = 2
            // SideMoves on its own: with R17C-8's MoveControl in the list a blocked heal is not picked at all (the Pokémon uses Struggle, see R17C8ControlTest)
            val e = R17C5.playingAlone(listOf(id), listOf("splash")) { R17C5.round(m, b, Random(1)) }
            assertEquals("$id", 10, m.hp)
            assertTrue("$id says why", e.lines().contains("Fastie can't heal while Heal Block lasts!"))
            assertEquals("$id", "Heal blocked", e.kinds(EffectKind.BLOCKED).single().label)
        }
        val m = main("moonlight"); val b = battle(wild("splash"))
        m.hp = 10; b.player.volatiles["healblock"] = 0
        round(m, b, listOf("moonlight"), listOf("splash"))
        assertTrue("a Heal Block that ran out does nothing", m.hp > 10)
    }

    @Test fun theWildOneHealsByItsOwnMaxHp() {
        val m = main("splash"); val w = wild("moonlight"); val b = battle(w)
        val wMax = R17C5.maxHp(w)
        w.hp = 1
        round(m, b, listOf("splash"), listOf("moonlight"))
        assertEquals(1 + wMax / 2, w.hp)
    }

    // ---------------------------------------------------------------- Rest

    @Test fun restHealsFullyCuresTheStatusAndSleepsExactlyTwoTurns() {
        val m = main("rest"); val b = battle(wild("splash"))
        m.hp = max / 2; b.player.status = "par"
        val ids = listOf("rest", "tackle")
        val e1 = round(m, b, ids, listOf("splash"), R17C5.high)
        assertEquals(max, m.hp)
        assertEquals("slp", b.player.status); assertEquals(2, b.player.volatiles[CoreRules.SLEEP_TURNS])
        assertTrue(e1.lines().contains("Fastie fell asleep!"))
        assertEquals(max - max / 2, e1.kinds(EffectKind.HEAL).single().amount)
        val e2 = round(m, b, ids, listOf("splash"), R17C5.high)
        assertEquals(listOf("Fastie is fast asleep."), e2.kinds(EffectKind.CANT_MOVE).map { it.text })
        val e3 = round(m, b, ids, listOf("splash"), R17C5.high)
        assertEquals(listOf("Fastie is fast asleep."), e3.kinds(EffectKind.CANT_MOVE).map { it.text })
        val e4 = round(m, b, ids, listOf("splash"), R17C5.high)
        assertTrue(e4.lines().contains("Fastie woke up!"))
        assertNull(b.player.status)
        assertFalse("it acts in the round it wakes", e4.attacks().first { it.byPlayer }.failed)
        assertFalse(CoreRules.SLEEP_TURNS in b.player.volatiles)
    }

    @Test fun restFailsAtFullHpAndWhenSleepIsBlocked() {
        val m = main("rest"); val b = battle(wild("splash"))
        round(m, b, listOf("rest"), listOf("splash"))
        assertNull("full HP: no sleep", b.player.status)

        val keptAwake = object : BattleRules {
            override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? = if (tp && status == "slp") "Fastie is kept awake!" else null
        }
        val m3 = main("rest"); val b3 = battle(wild("splash"))
        m3.hp = 10; b3.player.status = "par"
        val e3 = R17C5.withExtra(keptAwake) { round(m3, b3, listOf("rest"), listOf("splash"), R17C5.high) }
        assertEquals(10, m3.hp); assertEquals("par", b3.player.status)
        assertTrue(e3.lines().contains("Fastie is kept awake!"))
        assertTrue(e3.attacks().first { it.byPlayer }.failed)
    }

    // ---------------------------------------------------------------- Wish

    @Test fun wishHealsHalfTheWishersMaxHpAtTheEndOfTheNextRound() {
        val m = main("wish"); val b = battle(wild("splash"))
        m.hp = 1
        val e1 = round(m, b, listOf("wish", "splash"), listOf("splash"))
        assertEquals(1, m.hp)
        assertEquals("Wish", e1.kinds(EffectKind.FIELD).single().label)
        val e2 = round(m, b, listOf("wish", "splash"), listOf("splash"))
        assertEquals(1 + max / 2, m.hp)
        assertTrue(e2.lines().contains("Fastie's wish came true!"))
        assertTrue(b.field.player.isEmpty())
    }

    @Test fun aWishThatIsWaitingMakesAnotherFail() {
        val m = main("wish"); val b = battle(wild("splash"))
        round(m, b, listOf("wish"), listOf("splash"))
        val e = round(m, b, listOf("wish"), listOf("splash"))
        assertTrue(e.lines().contains("But it failed!"))
    }

    @Test fun theWishHealsWhoeverIsOutWhenItComesTrue() {
        val a = main("wish"); val b = battle(wild("splash"))
        round(a, b, listOf("wish"), listOf("splash"))
        b.switchOutMain(data, a)
        val incoming = main("splash", sp = 303)
        incoming.hp = 1
        val e = round(incoming, b, listOf("splash"), listOf("splash"))
        assertEquals(1 + max / 2, incoming.hp)
        assertTrue(e.lines().contains("Boo's wish came true!"))
    }

    @Test fun theWildOneCanWishToo() {
        val m = main("splash"); val w = wild("wish"); val b = battle(w)
        val wMax = R17C5.maxHp(w)
        w.hp = 1
        round(m, b, listOf("splash"), listOf("wish", "splash"))
        round(m, b, listOf("splash"), listOf("wish", "splash"))
        assertEquals(1 + wMax / 2, w.hp)
    }

    // ---------------------------------------------------------------- Stockpile, Swallow, Spit Up

    @Test fun stockpileStacksThreeTimesWithDefenseAndSpecialDefense() {
        val m = main("stockpile"); val b = battle(wild("splash"))
        val e1 = round(m, b, listOf("stockpile"), listOf("splash"))
        val start = e1.kinds(EffectKind.VOLATILE).first { it.stat == "stockpile" }
        assertEquals("Fastie stockpiled 1!", start.text); assertEquals("Stockpile 1", start.label)
        assertEquals(1, b.player.boosts[1]); assertEquals(1, b.player.boosts[3])
        repeat(2) { round(m, b, listOf("stockpile"), listOf("splash")) }
        assertEquals(3, b.player.volatiles["stockpile"]); assertEquals(3, b.player.boosts[1]); assertEquals(3, b.player.boosts[3])
        val e4 = round(m, b, listOf("stockpile"), listOf("splash"))
        assertTrue(e4.lines().contains("But it failed!"))
        assertEquals(3, b.player.volatiles["stockpile"]); assertEquals(3, b.player.boosts[1])
    }

    @Test fun swallowHealsByTheLayersAndTakesTheRisesBack() {
        for ((layers, expected) in listOf(1 to max / 4, 2 to max / 2, 3 to max - 1)) {
            val m = main("stockpile"); val b = battle(wild("splash"))
            m.hp = 1
            val ids = List(layers) { "stockpile" } + "swallow"
            val rounds = layers + 1
            val all = ArrayList<GameEvent>()
            repeat(rounds) { all += R17C5.playing(ids, listOf("splash")) { R17C5.round(m, b, Random(1)) } }
            assertEquals("$layers layers", 1 + expected, m.hp)
            assertEquals("$layers layers", 0, b.player.boosts[1]); assertEquals(0, b.player.boosts[3])
            assertFalse("stockpile" in b.player.volatiles)
            assertTrue(all.lines().contains("Fastie's stockpile effect wore off!"))
        }
    }

    @Test fun swallowFailsWithNoStockpileAndAtFullHpItStillUsesItUp() {
        val m = main("swallow"); val b = battle(wild("splash"))
        m.hp = 1
        val e = round(m, b, listOf("swallow"), listOf("splash"))
        assertTrue(e.lines().contains("But it failed!")); assertEquals(1, m.hp)

        val m2 = main("stockpile"); val b2 = battle(wild("splash"))
        val ids = listOf("stockpile", "swallow")
        round(m2, b2, ids, listOf("splash"))
        val e2 = round(m2, b2, ids, listOf("splash"))
        assertTrue("full HP: nothing to heal", e2.attacks().first { it.byPlayer }.failed)
        assertFalse("stockpile" in b2.player.volatiles); assertEquals(0, b2.player.boosts[1])
    }

    @Test fun onlyWhatTheStockpileReallyGaveIsTakenBack() {
        val m = main("stockpile"); val b = battle(wild("splash"))
        b.player.boosts[1] = 6 // Defense is maxed: the stockpile gives no Defense
        val ids = listOf("stockpile", "swallow")
        m.hp = 1
        round(m, b, ids, listOf("splash"))
        assertEquals(6, b.player.boosts[1]); assertEquals(1, b.player.boosts[3])
        round(m, b, ids, listOf("splash"))
        assertEquals("Defense stays at +6", 6, b.player.boosts[1]); assertEquals(0, b.player.boosts[3])
    }

    @Test fun spitUpHasHundredPowerPerLayerAndFailsWithNone() {
        val m = main("splash"); val w = wild("splash"); val b = battle(w)
        val spit = data.move("spitup")!!
        for (layers in 1..3) {
            b.player.volatiles["stockpile"] = layers
            val hit = BattleEngine.newHit(SimpleView.of(data, b, m), true, m, b.player, w, b.enemy, spit)
            assertEquals(100 * layers, SideMoves.basePower(hit))
        }
        val m2 = main("spitup"); val w2 = wild("splash"); val b2 = battle(w2)
        val hp = w2.hp
        val e = round(m2, b2, listOf("spitup"), listOf("splash"))
        assertEquals(hp, w2.hp)
        assertTrue(e.attacks().first { it.byPlayer }.failed)
        assertTrue(e.lines().contains("But it failed!"))
    }

    @Test fun spitUpHitsThenEndsTheStockpileAndTakesTheRisesBack() {
        val m = main("stockpile"); val w = wild("splash"); val b = battle(w)
        val ids = listOf("stockpile", "stockpile", "spitup")
        round(m, b, ids, listOf("splash")); round(m, b, ids, listOf("splash"))
        val hp = w.hp
        val e = round(m, b, ids, listOf("splash"))
        assertTrue(w.hp < hp)
        assertTrue(e.attacks().first { it.byPlayer }.damage > 0)
        assertFalse("stockpile" in b.player.volatiles)
        assertEquals(0, b.player.boosts[1]); assertEquals(0, b.player.boosts[3])
        assertTrue(e.lines().contains("Fastie's stockpile effect wore off!"))
    }

    // ---------------------------------------------------------------- Strength Sap, Pain Split

    @Test fun strengthSapHealsByTheTargetsAttackThenLowersIt() {
        val m = main("strengthsap"); val w = wild("splash"); val b = battle(w)
        val atk = StatCalc.stats(data, w).atk
        m.hp = 1
        val e = round(m, b, listOf("strengthsap"), listOf("splash"))
        assertEquals(1 + atk, m.hp)
        assertEquals(-1, b.enemy.boosts[0])
        assertTrue(e.lines().contains("Fastie sapped strength and regained health!"))
    }

    @Test fun strengthSapUsesTheTargetsStagesAndFailsAtMinusSix() {
        val m = main("strengthsap"); val w = wild("splash"); val b = battle(w)
        val atk = StatCalc.stats(data, w).atk
        b.enemy.boosts[0] = -2
        m.hp = 1
        round(m, b, listOf("strengthsap"), listOf("splash"))
        assertEquals(1 + (atk * BattleEngine.stageMult(-2)).toInt(), m.hp)
        assertEquals(-3, b.enemy.boosts[0])

        val m2 = main("strengthsap"); val b2 = battle(wild("splash"))
        b2.enemy.boosts[0] = -6; m2.hp = 1
        val e2 = round(m2, b2, listOf("strengthsap"), listOf("splash"))
        assertEquals(1, m2.hp); assertTrue(e2.attacks().first { it.byPlayer }.failed)
    }

    @Test fun strengthSapAtFullHpStillLowersTheAttack() {
        val m = main("strengthsap"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("strengthsap"), listOf("splash"))
        assertFalse(e.attacks().first { it.byPlayer }.failed)
        assertEquals(-1, b.enemy.boosts[0])
    }

    @Test fun painSplitMakesBothHpTheAverage() {
        val m = main("painsplit"); val w = wild("splash"); val b = battle(w)
        m.hp = 40; w.hp = 100
        val e = round(m, b, listOf("painsplit"), listOf("splash"))
        assertEquals(70, m.hp); assertEquals(70, w.hp)
        assertTrue(e.lines().contains("The battlers shared their pain!"))
        assertEquals(30, e.kinds(EffectKind.HEAL).single().amount)
        assertEquals(30, e.kinds(EffectKind.RESIDUAL).single().amount)
        val m2 = main("painsplit"); val w2 = wild("splash"); val b2 = battle(w2)
        m2.hp = 140; w2.hp = 20
        round(m2, b2, listOf("painsplit"), listOf("splash"))
        assertEquals(80, m2.hp); assertEquals(80, w2.hp)
    }

    @Test fun painSplitNeverGivesMoreThanTheMaxHp() {
        val m = main("painsplit"); val w = wild("splash"); val b = battle(w)
        m.hp = 100; w.hp = 600
        round(m, b, listOf("painsplit"), listOf("splash"))
        assertEquals(max, m.hp); assertEquals(350, w.hp)
    }

    @Test fun aSubstituteStopsPainSplitAndStrengthSap() {
        for (id in listOf("painsplit", "strengthsap")) {
            val m = main(id); val w = wild("splash"); val b = battle(w)
            m.hp = 40; b.enemy.volatiles[SideMoves.SUBSTITUTE] = 100
            round(m, b, listOf(id), listOf("splash"))
            assertEquals(id, 40, m.hp)
        }
    }

    // ---------------------------------------------------------------- cures

    @Test fun jungleHealingAndLunarBlessingHealAQuarterAndCure() {
        for (id in listOf("junglehealing", "lunarblessing")) {
            val m = main(id); val b = battle(wild("splash"))
            m.hp = 1; b.player.status = "par"
            val e = round(m, b, listOf(id), listOf("splash"), R17C5.high)
            assertEquals(id, 1 + max / 4, m.hp); assertNull(id, b.player.status)
            assertTrue(e.lines().contains("Fastie was cured of its paralysis!"))
        }
    }

    @Test fun jungleHealingWorksWithOnlyAStatusOrOnlyDamageAndFailsWithNeither() {
        val m = main("junglehealing"); val b = battle(wild("splash")); b.player.status = "brn"
        round(m, b, listOf("junglehealing"), listOf("splash"))
        assertNull(b.player.status)
        val m2 = main("junglehealing"); val b2 = battle(wild("splash")); m2.hp = 1
        round(m2, b2, listOf("junglehealing"), listOf("splash"))
        assertEquals(1 + max / 4, m2.hp)
        val m3 = main("junglehealing"); val b3 = battle(wild("splash"))
        assertTrue(round(m3, b3, listOf("junglehealing"), listOf("splash")).attacks().first { it.byPlayer }.failed)
    }

    @Test fun healBellAndAromatherapyCureTheUsersStatus() {
        for ((id, line) in listOf("healbell" to "A bell chimed.", "aromatherapy" to "A soothing aroma wafted through the air!")) {
            val m = main(id); val b = battle(wild("splash")); b.player.status = "par"
            val e = round(m, b, listOf(id), listOf("splash"), R17C5.high)
            assertNull(id, b.player.status)
            assertTrue(e.lines().contains(line))
            val cured = e.kinds(EffectKind.STATUS_CURED).single()
            assertEquals("par", cured.stat); assertNull(cured.status)
            val m2 = main(id); val b2 = battle(wild("splash"))
            assertTrue("$id with no status", round(m2, b2, listOf(id), listOf("splash")).attacks().first { it.byPlayer }.failed)
        }
    }

    @Test fun healBellIgnoresTheFoesSubstituteAndProtect() {
        val m = main("healbell"); val b = battle(wild("protect"))
        b.player.status = "brn"; b.enemy.volatiles[SideMoves.SUBSTITUTE] = 100
        round(m, b, listOf("healbell"), listOf("protect"))
        assertNull(b.player.status)
    }

    @Test fun refreshCuresOnlyBurnPoisonAndParalysis() {
        for (st in listOf("brn", "psn", "tox", "par")) {
            val m = main("refresh"); val b = battle(wild("splash")); b.player.status = st
            round(m, b, listOf("refresh"), listOf("splash"), R17C5.high)
            assertNull(st, b.player.status)
        }
        val m = main("refresh"); val b = battle(wild("splash"))
        assertTrue(round(m, b, listOf("refresh"), listOf("splash")).attacks().first { it.byPlayer }.failed)
    }

    @Test fun takeHeartRaisesBothSpecialStatsAndCures() {
        val m = main("takeheart"); val b = battle(wild("splash")); b.player.status = "psn"
        round(m, b, listOf("takeheart"), listOf("splash"))
        assertEquals(1, b.player.boosts[2]); assertEquals(1, b.player.boosts[3]); assertNull(b.player.status)
        val m2 = main("takeheart"); val b2 = battle(wild("splash"))
        b2.player.boosts[2] = 6; b2.player.boosts[3] = 6
        assertTrue(round(m2, b2, listOf("takeheart"), listOf("splash")).attacks().first { it.byPlayer }.failed)
    }

    // ---------------------------------------------------------------- Belly Drum, Fillet Away, Clangorous Soul, Haze

    @Test fun bellyDrumPaysHalfForMaxAttack() {
        val m = main("bellydrum"); val b = battle(wild("splash"))
        b.player.boosts[0] = -3
        val e = round(m, b, listOf("bellydrum"), listOf("splash"))
        assertEquals(max - max / 2, m.hp); assertEquals(6, b.player.boosts[0])
        assertTrue(e.lines().contains("Fastie cut its own HP!"))
        assertTrue(e.lines().contains("Fastie's Attack rose drastically!"))
    }

    @Test fun bellyDrumFailsAtHalfHpOrWithAttackAtPlusSix() {
        val m = main("bellydrum"); val b = battle(wild("splash"))
        m.hp = max / 2
        val e = round(m, b, listOf("bellydrum"), listOf("splash"))
        assertTrue(e.attacks().first { it.byPlayer }.failed); assertEquals(max / 2, m.hp); assertEquals(0, b.player.boosts[0])
        val m2 = main("bellydrum"); val b2 = battle(wild("splash")); b2.player.boosts[0] = 6
        round(m2, b2, listOf("bellydrum"), listOf("splash"))
        assertEquals(max, m2.hp)
    }

    @Test fun filletAwayPaysHalfFirstThenRaisesItsStats() {
        val m = main("filletaway"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("filletaway"), listOf("splash"))
        assertEquals(max - max / 2, m.hp)
        assertEquals(2, b.player.boosts[0]); assertEquals(2, b.player.boosts[2]); assertEquals(2, b.player.boosts[4])
        val order = e.effects().map { it.kind }
        assertTrue("the cost comes before the rises", order.indexOf(EffectKind.RECOIL) < order.indexOf(EffectKind.STAT_CHANGE))
    }

    @Test fun filletAwayFailsAtHalfHpAndWhenNothingCanRise() {
        val m = main("filletaway"); val b = battle(wild("splash"))
        m.hp = max / 2
        assertTrue(round(m, b, listOf("filletaway"), listOf("splash")).attacks().first { it.byPlayer }.failed)
        assertEquals(0, b.player.boosts[0])
        val m2 = main("filletaway"); val b2 = battle(wild("splash"))
        b2.player.boosts[0] = 6; b2.player.boosts[2] = 6; b2.player.boosts[4] = 6
        assertTrue(round(m2, b2, listOf("filletaway"), listOf("splash")).attacks().first { it.byPlayer }.failed)
        assertEquals("no HP is paid for nothing", max, m2.hp)
    }

    @Test fun clangorousSoulPaysAThirdAndRaisesEverything() {
        val m = main("clangoroussoul"); val b = battle(wild("splash"))
        round(m, b, listOf("clangoroussoul"), listOf("splash"))
        assertEquals(max - max / 3, m.hp)
        for (i in 0..4) assertEquals(1, b.player.boosts[i])
        val m2 = main("clangoroussoul"); val b2 = battle(wild("splash"))
        m2.hp = max / 3
        assertTrue(round(m2, b2, listOf("clangoroussoul"), listOf("splash")).attacks().first { it.byPlayer }.failed)
        assertEquals(0, b2.player.boosts[0])
    }

    @Test fun hazeResetsEveryStageOfBothSides() {
        val m = main("haze"); val b = battle(wild("splash"))
        b.player.boosts[0] = 2; b.player.boosts[4] = -1; b.enemy.boosts[1] = 3; b.enemy.boosts[6] = -2
        val e = round(m, b, listOf("haze"), listOf("splash"))
        assertTrue(b.player.boosts.all { it == 0 } && b.enemy.boosts.all { it == 0 })
        val fx = e.kinds(EffectKind.FIELD).single()
        assertEquals("All stat changes were eliminated!", fx.text); assertEquals("Haze", fx.label)
        assertFalse(e.attacks().first { it.byPlayer }.failed)
    }

    @Test fun hazeGoesThroughProtectAndASubstitute() {
        val m = main("haze"); val b = battle(wild("protect"))
        b.player.boosts[0] = 2; b.enemy.volatiles[SideMoves.SUBSTITUTE] = 100
        round(m, b, listOf("haze"), listOf("protect"))
        assertEquals(0, b.player.boosts[0])
    }

    // ---------------------------------------------------------------- the move choice and the texts

    @Test fun smartChoiceUsesTheHealsOnlyWhenTheyWork() {
        val m = main("tackle"); val b = battle(wild("tackle"))
        fun useful(id: String) = SideMoves.useful(SimpleView.of(data, b, m), true, data.move(id)!!)
        for (id in listOf("moonlight", "morningsun", "synthesis", "shoreup", "rest", "wish", "swallow", "junglehealing", "lunarblessing")) assertEquals("$id at full HP", false, useful(id))
        m.hp = 10
        for (id in listOf("moonlight", "morningsun", "synthesis", "shoreup", "rest", "wish", "junglehealing", "lunarblessing")) assertEquals("$id hurt", true, useful(id))
        assertEquals("swallow with no stockpile", false, useful("swallow"))
        assertEquals("spit up with no stockpile", false, useful("spitup"))
        assertEquals(true, useful("stockpile"))
        b.player.volatiles["stockpile"] = 3
        assertEquals(false, useful("stockpile")); assertEquals(true, useful("swallow")); assertEquals(true, useful("spitup"))
        b.player.volatiles["healblock"] = 2
        assertEquals("no heal under Heal Block", false, useful("moonlight"))
        b.player.volatiles.clear(); b.player.status = "slp"
        assertEquals("Rest while asleep", false, useful("rest"))
    }

    @Test fun smartChoiceKnowsWhenTheStatMovesAndSplitsCanWork() {
        val m = main("tackle"); val w = wild("tackle"); val b = battle(w)
        fun useful(id: String) = SideMoves.useful(SimpleView.of(data, b, m), true, data.move(id)!!)
        assertEquals(true, useful("bellydrum")); assertEquals(true, useful("filletaway")); assertEquals(true, useful("clangoroussoul"))
        m.hp = max / 2
        assertEquals(false, useful("bellydrum")); assertEquals(false, useful("filletaway")); assertEquals(true, useful("clangoroussoul"))
        assertEquals("the foe has more HP than us: Pain Split helps", true, useful("painsplit"))
        m.hp = max; w.hp = 5
        assertEquals(false, useful("painsplit"))
        assertEquals("nothing changed yet: Haze is pointless", false, useful("haze"))
        b.enemy.boosts[0] = 2
        assertEquals(true, useful("haze"))
        assertEquals(false, useful("healbell")); b.player.status = "par"; assertEquals(true, useful("healbell"))
        assertEquals(true, useful("refresh")); b.player.status = "slp"; assertEquals(false, useful("refresh"))
    }

    @Test fun theTextsGiveTheNumbers() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        assertEquals("Heals you by half your max HP: two thirds in the sun, a quarter in rain, a sandstorm, hail or snow.", say("moonlight"))
        assertEquals("Heals you by half your max HP: two thirds in a sandstorm.", say("shoreup"))
        assertTrue(say("rest"), "you sleep through exactly 2 tries to move and wake up on the next one" in say("rest")) // R17C-10: "tries": the sleep counts the sleeper's own moves
        assertEquals("At the end of the next turn, your team's Pokémon is healed by half your max HP.", say("wish"))
        assertTrue(say("swallow"), "a quarter" in say("swallow") && "all of it with 3" in say("swallow"))
        assertEquals("Its power is 100 times your stockpile, which it then ends. Fails with none.", say("spitup"))
        assertTrue(say("bellydrum"), "half" in say("bellydrum") && "Fails when your HP is half of your max HP or less." in say("bellydrum"))
        assertTrue(say("clangoroussoul"), "a third" in say("clangoroussoul") && "a third of your max HP or less" in say("clangoroussoul"))
        assertEquals("Raises your Attack, Sp. Atk and Speed by 2. Pay half your max HP first. Fails when your HP is half of your max HP or less.", say("filletaway"))
        assertEquals("Sets every stat change of both Pokémon back to 0.", say("haze").removeSuffix(" Never misses."))
    }
}
