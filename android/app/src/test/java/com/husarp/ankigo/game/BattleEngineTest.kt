package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import com.husarp.ankigo.game.GameEvent.Fainted
import com.husarp.ankigo.game.GameEvent.Splash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleEngineTest {
    private val data = TestData.world()

    // Fixture numbers (all IVs 15, neutral nature, no EVs): Ratty 19 = Normal, Birdie 16 = Normal/Flying,
    // Duckling 54 = Water, Spooky 92 = Ghost/Poison, Sparkmouse 25 = Electric, Mindlord 150 = Psychic (big HP).
    private fun mon(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data, sp, lv, moves.toList())
    private fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)
    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)
    private fun round(main: Mon, wild: Mon, mult: Double = 1.0, acts: Boolean = true, seed: Int = 1, b: Battle = battle(wild)) =
        BattleEngine.playRound(data, b, main, mult, acts, Random(seed))

    private fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    private fun List<GameEvent>.effects() = filterIsInstance<Effect>().map { it.text }
    private fun dmg(a: Mon, d: Mon, move: String, crit: Boolean? = false, roll: Int? = 100, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide()) =
        BattleEngine.damage(data, a, aSide, d, dSide, data.move(move)!!, Random(1), crit, roll)

    // ---------------------------------------------------------------- type chart

    @Test fun typeChartSpotChecks() {
        assertEquals(2.0, TypeChart.single(Type.FIRE, Type.GRASS), 0.0)
        assertEquals(0.5, TypeChart.single(Type.FIRE, Type.WATER), 0.0)
        assertEquals(2.0, TypeChart.single(Type.STEEL, Type.FAIRY), 0.0)
        assertEquals(2.0, TypeChart.single(Type.FAIRY, Type.DRAGON), 0.0)
        assertEquals(0.5, TypeChart.single(Type.GHOST, Type.DARK), 0.0)
        assertEquals(1.0, TypeChart.single(Type.NORMAL, Type.FIRE), 0.0)
        assertEquals(4.0, TypeChart.eff(Type.ICE, listOf(Type.DRAGON, Type.FLYING)), 0.0)
        assertEquals(4.0, TypeChart.eff(Type.ROCK, listOf(Type.FIRE, Type.FLYING)), 0.0)
        assertEquals(0.25, TypeChart.eff(Type.FIRE, listOf(Type.WATER, Type.ROCK)), 0.0)
        assertEquals(0.25, TypeChart.eff(Type.GRASS, listOf(Type.FIRE, Type.DRAGON)), 0.0)
        assertEquals(1.0, TypeChart.eff(Type.GRASS, listOf(Type.WATER, Type.POISON)), 0.0) // 2 x 0.5
        assertEquals(0.0, TypeChart.eff(Type.GROUND, listOf(Type.FLYING, Type.FIRE)), 0.0)
    }

    @Test fun typeChartHasExactlyTheEightImmunities() {
        val zeros = Type.entries.flatMap { a -> Type.entries.filter { d -> TypeChart.single(a, d) == 0.0 }.map { a to it } }.toSet()
        val expected = setOf(
            Type.NORMAL to Type.GHOST, Type.FIGHTING to Type.GHOST, Type.GHOST to Type.NORMAL, Type.ELECTRIC to Type.GROUND,
            Type.GROUND to Type.FLYING, Type.PSYCHIC to Type.DARK, Type.POISON to Type.STEEL, Type.DRAGON to Type.FAIRY,
        )
        assertEquals(expected, zeros)
        // a dual type with one immunity is immune overall
        assertEquals(0.0, TypeChart.eff(Type.GROUND, listOf(Type.FLYING, Type.WATER)), 0.0)
    }

    @Test fun typeChartCountsOfSuperEffective() {
        // totals of the Gen 6+ chart: 51 super effective, 61 not very effective
        val twice = Type.entries.sumOf { a -> Type.entries.count { d -> TypeChart.single(a, d) == 2.0 } }
        val half = Type.entries.sumOf { a -> Type.entries.count { d -> TypeChart.single(a, d) == 0.5 } }
        assertEquals(51, twice)
        assertEquals(61, half)
    }

    // ---------------------------------------------------------------- damage formula

    @Test fun formulaHandComputed() {
        // Ratty L20 atk = floor((112+15)*20/100)+5 = 30; Birdie L20 def = floor((80+15)*20/100)+5 = 24
        // base = floor(floor(floor(2*20/5+2)=10 * 40 * 30 / 24 = 500) / 50) + 2 = 12
        val a = mon(19, 20, "tackle"); val d = mon(16, 20)
        val r100 = dmg(a, d, "tackle", roll = 100)
        assertEquals(18, r100.damage) // 12 x STAB 1.5
        assertEquals(1.0, r100.effectiveness, 0.0)
        assertFalse(r100.crit)
        assertEquals(15, dmg(a, d, "tackle", roll = 85).damage) // floor(12*85/100)=10 -> 15
        val c = dmg(a, d, "tackle", crit = true, roll = 100)
        assertTrue(c.crit)
        assertEquals(27, c.damage) // 12 -> 18 (crit) -> 27 (STAB)
        assertEquals(22, dmg(a, d, "tackle", crit = true, roll = 85).damage) // 18 -> 15 -> 22
    }

    @Test fun stabOnlyForOwnTypes() {
        // Duckling (Water) vs Birdie: physical Tackle has no STAB: base 11
        val duck = mon(54, 20); val bird = mon(16, 20)
        assertEquals(11, dmg(duck, bird, "tackle").damage)
        // Water Gun special: spa (130+15)*20/100+5 = 34 vs spd (70+15)*20/100+5 = 22 -> 14, STAB -> 21
        assertEquals(21, dmg(duck, bird, "watergun").damage)
    }

    @Test fun burnHalvesPhysicalOnly() {
        val a = mon(19, 20); val d = mon(16, 20)
        val burned = BattleSide(status = "brn")
        assertEquals(9, dmg(a, d, "tackle", aSide = burned).damage)
        val duck = mon(54, 20)
        assertEquals(21, dmg(duck, d, "watergun", aSide = burned).damage)
    }

    @Test fun effectivenessScalesAndImmuneIsZero() {
        val spark = mon(25, 20); val duck = mon(54, 20); val ghost = mon(92, 20)
        val se = dmg(spark, duck, "thunderbolt")
        assertEquals(2.0, se.effectiveness, 0.0)
        val neutral = dmg(spark, mon(16, 20), "thunderbolt") // Electric vs Normal/Flying = 2 too; use Normal-only
        assertEquals(2.0, neutral.effectiveness, 0.0)
        val rat = mon(19, 20)
        assertEquals(1.0, dmg(spark, rat, "thunderbolt").effectiveness, 0.0)
        assertTrue(se.damage > dmg(spark, rat, "thunderbolt").damage)
        val imm = dmg(rat, ghost, "tackle")
        assertEquals(0, imm.damage); assertEquals(0.0, imm.effectiveness, 0.0)
        assertEquals(0, dmg(ghost, rat, "lick").damage)
        // Ground vs Flying (Birdie is Normal/Flying)
        assertEquals(0, dmg(rat, mon(16, 20), "earthquake").damage)
        // never 0 when the target isn't immune, even with a tiny hit
        assertTrue(dmg(mon(19, 1), mon(1, 100), "tackle", roll = 85).damage >= 1)
    }

    @Test fun stagesChangeDamageAndCritIgnoresBadOnes() {
        val a = mon(19, 20); val d = mon(16, 20)
        val down = BattleSide().also { it.boosts[0] = -1 } // atk 30 -> floor(30*2/3)=20: base floor(10*40*20/24=333/50=6)+2 = 8 -> STAB 12
        assertEquals(12, dmg(a, d, "tackle", aSide = down).damage)
        assertEquals(27, dmg(a, d, "tackle", crit = true, aSide = down).damage) // negative attacker stage ignored on a crit
        val dUp = BattleSide().also { it.boosts[1] = 2 } // def 24 -> 48: base floor(10*40*30/48=250/50=5)+2 = 7 -> 10
        assertEquals(10, dmg(a, d, "tackle", dSide = dUp).damage)
        assertEquals(27, dmg(a, d, "tackle", crit = true, dSide = dUp).damage) // positive defender stage ignored on a crit
        val aUp = BattleSide().also { it.boosts[0] = 2 } // atk 60: floor(10*40*60/24=1000/50=20)+2 = 22 -> 33
        assertEquals(33, dmg(a, d, "tackle", aSide = aUp).damage)
    }

    @Test fun critRatesFollowTheTable() {
        val a = mon(19, 20); val d = mon(16, 20)
        val rng = Random(7)
        fun rate(move: Move, n: Int = 6000) = (1..n).count { BattleEngine.damage(data, a, BattleSide(), d, BattleSide(), move, rng).crit }.toDouble() / n
        assertEquals(1.0 / 24, rate(data.move("tackle")!!), 0.012)
        assertEquals(1.0 / 8, rate(data.move("slash")!!), 0.02)
        assertEquals(1.0, rate(data.move("tackle")!!.copy(willCrit = true), 200), 0.0)
    }

    @Test fun fixedLevelHalfHpAndOhkoDamage() {
        val a = mon(19, 20); val bird = mon(16, 20); val ghost = mon(92, 20)
        assertEquals(20, dmg(a, bird, "seismictoss").damage)
        assertEquals(0, dmg(a, ghost, "seismictoss").damage)
        assertEquals(40, dmg(a, bird, "dragonrage").damage)
        assertEquals(bird.hp / 2, dmg(a, bird, "superfang").damage)
        bird.hp = 1
        assertEquals(1, dmg(a, bird, "superfang").damage)
        bird.hp = 33
        assertEquals(0, dmg(a, bird, "fissure").damage) // Birdie is Flying: Ground is immune
        assertEquals(33, dmg(a, mon(19, 20).also { it.hp = 33 }, "fissure").damage)
    }

    // ---------------------------------------------------------------- move choice

    @Test fun pickPlayerMoveIsRandomAmongKnownMoves() {
        val main = mon(19, 20, "tackle", "vinewhip", "notamove")
        val wild = mon(16, 20)
        val picked = (1..60).map { BattleEngine.pickPlayerMove(data, main, wild, Random(it)).id }.toSet()
        assertEquals(setOf("tackle", "vinewhip"), picked)
    }

    @Test fun noKnownMovesFallsBackToTackle() {
        assertEquals("tackle", BattleEngine.pickPlayerMove(data, mon(19, 20), mon(16, 20), Random(1)).id)
        assertEquals("tackle", BattleEngine.pickPlayerMove(data, mon(19, 20, "nonsense"), mon(16, 20), Random(1)).id)
    }

    @Test fun struggleWhenNothingCanHurt() {
        val ghost = mon(92, 20)
        // Tackle (Normal) cannot hurt a Ghost, Growl is a status move
        for (s in 1..20) assertEquals("struggle", BattleEngine.pickPlayerMove(data, mon(19, 20, "tackle", "growl"), ghost, Random(s)).id)
        // Teleport only
        assertEquals("struggle", BattleEngine.pickPlayerMove(data, mon(63, 20, "teleport"), mon(16, 20), Random(1)).id)
        // a move that can hurt keeps the random choice (Lick is Ghost, hits a Ghost)
        assertEquals("lick", BattleEngine.pickPlayerMove(data, mon(92, 20, "lick"), ghost, Random(1)).id)
    }

    @Test fun struggleIsTypelessWithRecoil() {
        val main = mon(19, 20, "tackle", "growl"); val wild = mon(92, 20)
        val before = main.hp
        val ev = round(main, wild, acts = false)
        val atk = ev.attacks().single()
        assertEquals("Struggle", atk.move)
        assertNull(atk.moveType)
        assertEquals(1.0, atk.effectiveness, 0.0)
        assertTrue(atk.damage > 0)
        assertEquals(before - maxOf(1, maxHp(main) / 4), main.hp)
        assertTrue(ev.effects().any { "recoil" in it })
    }

    // ---------------------------------------------------------------- the round

    @Test fun enemyDoesNothingWhenItDoesNotAct() {
        val main = mon(19, 20, "tackle"); val wild = mon(150, 40, "tackle")
        val before = main.hp
        val b = battle(wild)
        val ev = round(main, wild, acts = false, b = b)
        assertEquals(before, main.hp)
        assertEquals(listOf("Mindlord"), ev.filterIsInstance<Splash>().map { it.name })
        assertEquals(1, ev.attacks().size)
        assertTrue(ev.attacks().single().byPlayer)
        assertEquals(1, b.rounds)
    }

    @Test fun multiplierZeroDealsExactlyOneAndOnePointFiveFloors() {
        val wild = { mon(150, 40, "tackle") }
        val z = round(mon(19, 20, "tackle"), wild(), mult = 0.0, acts = false).attacks().single()
        assertEquals(1, z.damage)
        val seeds = 1..10
        for (s in seeds) {
            val one = round(mon(19, 20, "tackle"), wild(), mult = 1.0, acts = false, seed = s).attacks().single().damage
            val onehalf = round(mon(19, 20, "tackle"), wild(), mult = 1.5, acts = false, seed = s).attacks().single().damage
            val two = round(mon(19, 20, "tackle"), wild(), mult = 2.0, acts = false, seed = s).attacks().single().damage
            assertEquals(Math.floor(one * 1.5).toInt(), onehalf)
            assertEquals(one * 2, two)
        }
        // multi-hit with multiplier 0: 1 HP per hit
        val m = round(mon(1, 20, "bulletseed"), wild(), mult = 0.0, acts = false).attacks().single()
        assertEquals(m.hits, m.damage)
        assertTrue(m.hits in 2..5)
    }

    @Test fun multiplierNeverScalesEnemyDamage() {
        for (s in 1..5) {
            val lost = { mult: Double ->
                val main = mon(19, 40, "tackle"); val wild = mon(150, 30, "tackle")
                val before = main.hp
                round(main, wild, mult = mult, seed = s)
                before - main.hp
            }
            assertEquals(lost(0.5), lost(0.0))
            assertTrue(lost(0.0) > 0)
        }
    }

    @Test fun immuneMoveDealsZeroAndNeverFaints() {
        val main = mon(19, 20, "tackle", "growl") // vs a Ghost the main uses Struggle
        main.hp = 1
        val wild = mon(92, 20, "lick") // Ghost vs Normal = immune, and Spooky (80) outruns Ratty (72)
        val ev = round(main, wild)
        val first = ev.first { it is Attack } as Attack
        assertFalse(first.byPlayer)
        assertEquals(0, first.damage)
        assertEquals(0.0, first.effectiveness, 0.0)
        assertEquals(1, first.targetHp)
        assertEquals(0, ev.indexOf(first)) // nothing happened before it
        assertTrue(ev.indexOf(first) < (ev.indexOfFirst { it is Fainted }.takeIf { it >= 0 } ?: Int.MAX_VALUE))
        assertEquals(0, ev.take(1).count { it is Fainted })
    }

    @Test fun orderByPriorityThenSpeedTieGoesToWild() {
        fun order(main: Mon, wild: Mon) = round(main, wild).attacks().map { it.byPlayer }
        // Quick Attack beats a faster Pokémon
        assertEquals(listOf(true, false), order(mon(16, 20, "quickattack"), mon(19, 20, "tackle")))
        // plain speed: Ratty 72 base vs Birdie 56 base
        assertEquals(listOf(true, false), order(mon(19, 20, "tackle"), mon(16, 20, "tackle")))
        assertEquals(listOf(false, true), order(mon(16, 20, "tackle"), mon(19, 20, "tackle")))
        // equal speed: the wild one goes first
        assertEquals(listOf(false, true), order(mon(19, 20, "tackle"), mon(19, 20, "tackle")))
        // a priority move for the wild one
        assertEquals(listOf(false, true), order(mon(19, 20, "tackle"), mon(16, 20, "quickattack")))
    }

    @Test fun paralysisHalvesSpeedAndStageRaisesIt() {
        val main = mon(19, 20, "tackle"); val wild = mon(16, 20, "tackle")
        val b = battle(wild)
        b.player.status = "par" // 72 -> 36 < 56
        assertEquals(listOf(false, true), round(main, wild, b = b).attacks().map { it.byPlayer })
        val main2 = mon(16, 20, "tackle"); val wild2 = mon(19, 20, "tackle")
        val b2 = battle(wild2)
        b2.player.boosts[4] = 2 // 56 x2 = 112 > 72
        assertEquals(listOf(true, false), round(main2, wild2, b = b2).attacks().map { it.byPlayer })
    }

    @Test fun drainAndRecoilAmounts() {
        val wild = { mon(150, 60, "splash") }
        // Absorb: heal floor(dealt/2), min 1
        val main = mon(1, 20, "absorb")
        main.hp = maxHp(main) - 10
        val start = main.hp
        val a = round(main, wild(), acts = false).attacks().single()
        assertTrue(a.damage > 0)
        assertEquals(minOf(maxHp(main), start + maxOf(1, a.damage / 2)), main.hp)
        assertEquals(main.hp, a.userHp)
        // Double-Edge: recoil floor(dealt*33/100), min 1
        val rat = mon(19, 20, "doubleedge")
        val before = rat.hp
        val d = round(rat, wild(), acts = false).attacks().single()
        assertEquals(before - maxOf(1, d.damage * 33 / 100), rat.hp)
        assertEquals(rat.hp, d.userHp)
        // the multiplier does not scale recoil: it follows the scaled damage that was dealt
        val low = mon(19, 20, "doubleedge")
        val l = round(low, wild(), mult = 0.0, acts = false).attacks().single()
        assertEquals(1, l.damage)
        assertEquals(low.hp, maxHp(low) - 1) // max(1, 1*33/100) = 1
    }

    @Test fun multihitCountsStayInRangeAndFollowTheTable() {
        val counts = IntArray(6)
        for (s in 1..2000) {
            val a = round(mon(1, 20, "bulletseed"), mon(150, 100, "splash"), acts = false, seed = s).attacks().single()
            counts[a.hits]++
            assertEquals(a.hits, a.hits.coerceIn(2, 5))
        }
        assertEquals(0.35, counts[2] / 2000.0, 0.05)
        assertEquals(0.35, counts[3] / 2000.0, 0.05)
        assertEquals(0.15, counts[4] / 2000.0, 0.04)
        assertEquals(0.15, counts[5] / 2000.0, 0.04)
    }

    @Test fun levelAndFixedMovesInARound() {
        val seismic = round(mon(19, 20, "seismictoss"), mon(16, 20, "splash"), acts = false).attacks().single()
        assertEquals(20, seismic.damage)
        val rage = round(mon(25, 30, "dragonrage"), mon(16, 20, "splash"), acts = false)
        // Dragon vs Normal/Flying is neutral: 40 (never more than the target had)
        assertEquals(40, rage.attacks().single().damage)
        val wild = mon(16, 20, "splash")
        val half = wild.hp / 2
        assertEquals(half, round(mon(19, 20, "superfang"), wild, acts = false, seed = 3).attacks().single().let { if (it.missed) half else it.damage })
    }

    @Test fun ohkoRulesAndAccuracy() {
        // fails against a higher level, no accuracy roll
        val weak = round(mon(19, 10, "fissure"), mon(19, 20, "splash"), acts = false)
        assertTrue(weak.attacks().single().failed)
        // hits for exactly the target's HP; accuracy = 30 + level difference (here 40 %)
        var hits = 0
        for (s in 1..600) {
            val wild = mon(19, 20, "splash")
            val hp = wild.hp
            val ev = round(mon(19, 30, "fissure"), wild, acts = false, seed = s)
            val a = ev.attacks().single()
            if (!a.missed) {
                hits++
                assertEquals(hp, a.damage)
                assertEquals(0, wild.hp)
                assertEquals(1, ev.count { it is Fainted && !it.player })
            }
        }
        assertEquals(0.40, hits / 600.0, 0.06)
    }

    // ---------------------------------------------------------------- stat stages and statuses

    @Test fun growlLowersAttackAndShowsText() {
        val main = mon(19, 20, "tackle"); val wild = mon(16, 20, "growl")
        val b = battle(wild)
        val ev = round(main, wild, b = b)
        assertEquals(-1, b.player.boosts[0])
        assertTrue(ev.effects().contains("Ratty's Attack fell!"))
        // the next hit uses the lowered stat
        val lowered = dmg(main, wild, "tackle", aSide = b.player).damage
        assertTrue(lowered < dmg(main, wild, "tackle").damage)
    }

    @Test fun stageTextsAndLimits() {
        val main = { mon(19, 5, "tackle") }; val wild = mon(150, 100, "swordsdance")
        val b = battle(wild)
        assertTrue(round(main(), wild, b = b).effects().contains("Mindlord's Attack rose sharply!"))
        assertEquals(2, b.enemy.boosts[0])
        b.enemy.boosts[0] = 5
        round(main(), wild, b = b)
        assertEquals(6, b.enemy.boosts[0])
        val ev = round(main(), wild, b = b)
        assertEquals(6, b.enemy.boosts[0])
        assertTrue(ev.effects().contains("Mindlord's Attack won't go any higher!"))
        assertTrue(ev.attacks().first { !it.byPlayer }.failed)
        // Growl at -6
        val b2 = battle(mon(16, 20, "growl")); b2.player.boosts[0] = -6
        val ev2 = round(mon(19, 20, "tackle"), b2.wild, b = b2)
        assertTrue(ev2.effects().contains("Ratty's Attack won't go any lower!"))
    }

    @Test fun statusMoveWithNoEffectSaysNothingHappened() {
        val ev = round(mon(19, 5, "tackle"), mon(150, 100, "splash"))
        assertTrue(ev.effects().contains("But nothing happened."))
        assertTrue(ev.attacks().first { !it.byPlayer }.failed)
    }

    @Test fun recoverHealsTheUserOnly() {
        val wild = mon(150, 50, "recover")
        wild.hp = 10
        val b = battle(wild)
        val ev = round(mon(150, 50, "splash"), wild, b = b, mult = 1.0)
        val rec = ev.attacks().first { !it.byPlayer }
        assertFalse(rec.failed)
        assertEquals(10 + maxHp(wild) / 2, rec.userHp)
        assertEquals(10 + maxHp(wild) / 2 - ev.attacks().first { a -> a.byPlayer }.damage, wild.hp)
    }

    @Test fun thunderWaveParalysesExceptElectricAndGround() {
        var ok = 0
        for (s in 1..40) {
            val main = mon(19, 20, "tackle"); val wild = mon(25, 20, "thunderwave")
            val b = battle(wild)
            val ev = round(main, wild, b = b, seed = s)
            val tw = ev.attacks().first { !it.byPlayer }
            if (tw.missed) assertNull(b.player.status) else { assertEquals("par", b.player.status); ok++; assertTrue(ev.effects().contains("Ratty was paralyzed!")) }
        }
        assertTrue(ok > 25)
        // Electric target is immune, a second status doesn't stack
        val b2 = battle(mon(25, 20, "thunderwave"))
        val ev2 = round(mon(25, 20, "tackle"), b2.wild, b = b2, seed = 2)
        assertNull(b2.player.status)
        val b3 = battle(mon(25, 20, "thunderwave")); b3.player.status = "brn"
        round(mon(19, 20, "tackle"), b3.wild, b = b3, seed = 2)
        assertEquals("brn", b3.player.status)
        // Toxic vs Poison type is immune; Hypnosis puts to sleep (no turn counter: each move attempt may wake)
        val b4 = battle(mon(19, 20, "toxic"))
        round(mon(92, 20, "lick"), b4.wild, b = b4, seed = 2)
        assertNull(b4.player.status)
        var slept = 0
        for (s in 1..40) {
            val b5 = battle(mon(92, 20, "hypnosis"))
            round(mon(19, 20, "tackle"), b5.wild, b = b5, seed = s)
            if (b5.player.status == "slp") { slept++; assertEquals(0, b5.player.statusTurns) }
        }
        assertTrue(slept in 10..35)
        assertNotNull(ev2)
    }

    @Test fun secondaryEffectChanceAndGuards() {
        // Body Slam: 30 % to paralyse
        var par = 0
        for (s in 1..600) {
            val b = battle(mon(150, 40, "splash"))
            round(mon(19, 20, "bodyslam"), b.wild, acts = false, seed = s, b = b)
            if (b.enemy.status == "par") par++
        }
        assertEquals(0.3, par / 600.0, 0.06)
        // Ember on a Fire type never burns
        val fireSp = TestData.species(5, "Embermon", listOf(Type.FIRE), TestData.stats(100, 50, 50, 50, 50, 50))
        val d2 = TestData.data(data.species.values.toList() + fireSp)
        val wild = TestData.mon(d2, 5, 40, listOf("splash"))
        val b = Battle(wild, Tier.NORMAL)
        val main = TestData.mon(d2, 54, 20, listOf("ember"))
        for (s in 1..100) BattleEngine.playRound(d2, b, main, 1.0, false, Random(s))
        assertNull(b.enemy.status)
    }

    @Test fun paralysisSkipRate() {
        var skipped = 0
        val n = 1500
        for (s in 1..n) {
            val main = mon(19, 20, "tackle"); val wild = mon(150, 50, "splash")
            val b = battle(wild); b.player.status = "par"
            if (round(main, wild, acts = false, seed = s, b = b).effects().contains("Ratty is fully paralyzed!")) skipped++
        }
        assertEquals(0.25, skipped.toDouble() / n, 0.04)
    }

    @Test fun freezeThawRate() {
        var thawed = 0
        val n = 1500
        for (s in 1..n) {
            val main = mon(19, 20, "tackle"); val wild = mon(150, 50, "splash")
            val b = battle(wild); b.player.status = "frz"
            val ev = round(main, wild, acts = false, seed = s, b = b)
            if (ev.effects().contains("Ratty thawed out!")) { thawed++; assertEquals(1, ev.attacks().size); assertNull(b.player.status) }
            else { assertTrue(ev.effects().contains("Ratty is frozen solid!")); assertEquals(0, ev.attacks().size); assertEquals("frz", b.player.status) }
        }
        assertEquals(0.2, thawed.toDouble() / n, 0.04)
    }

    @Test fun sleeperWakesWithTheWakeChanceAndActsThatTurn() {
        var woke = 0
        val n = 1500
        for (s in 1..n) {
            val main = mon(19, 20, "tackle"); val wild = mon(150, 100, "splash")
            val b = battle(wild); b.player.status = "slp"
            val ev = round(main, wild, acts = false, seed = s, b = b)
            if (ev.effects().contains("Ratty woke up!")) { woke++; assertEquals(1, ev.attacks().size); assertNull(b.player.status) }
            else { assertTrue(ev.effects().contains("Ratty is fast asleep.")); assertEquals(0, ev.attacks().size); assertEquals("slp", b.player.status) }
        }
        assertEquals(Tuning.WAKE_CHANCE, woke.toDouble() / n, 0.04)
    }

    @Test fun burnPoisonAndToxicAtEndOfTurn() {
        val wild = { mon(150, 60, "splash") }
        val burn = mon(19, 40, "tackle"); val bb = battle(wild()); bb.player.status = "brn"
        val h0 = burn.hp
        round(burn, bb.wild, acts = false, b = bb)
        assertEquals(h0 - maxOf(1, maxHp(burn) / 16), burn.hp)

        val psn = mon(19, 40, "tackle"); val pb = battle(wild()); pb.player.status = "psn"
        val p0 = psn.hp
        val pev = round(psn, pb.wild, acts = false, b = pb)
        assertEquals(p0 - maxHp(psn) / 8, psn.hp)
        assertTrue(pev.effects().contains("Ratty is hurt by poison!"))

        val tox = mon(19, 40, "tackle"); val tb = battle(wild()); tb.player.status = "tox"
        val t0 = tox.hp
        round(tox, tb.wild, acts = false, b = tb)
        assertEquals(t0 - maxHp(tox) / 16, tox.hp)
        val t1 = tox.hp
        round(tox, tb.wild, acts = false, b = tb)
        assertEquals(t1 - maxHp(tox) * 2 / 16, tox.hp)

        // the wild one burns too (and a tiny max HP still loses at least 1)
        val w = mon(150, 60, "splash"); val wb = battle(w); wb.enemy.status = "brn"
        val w0 = w.hp
        round(mon(19, 5, "growl"), w, acts = false, b = wb) // Struggle? Growl only -> Struggle; hits Mindlord anyway
        assertTrue(w.hp < w0)
    }

    // ---------------------------------------------------------------- fainting

    @Test fun nothingActsAfterFainting() {
        // the main is faster and knocks the wild one out: no enemy attack, one Fainted
        val wild = mon(16, 5, "tackle"); wild.hp = 1
        val ev = round(mon(19, 40, "tackle"), wild)
        assertEquals(1, ev.attacks().size)
        assertTrue(ev.attacks().single().byPlayer)
        assertEquals(listOf(false), ev.filterIsInstance<Fainted>().map { it.player })
        assertEquals(0, wild.hp)

        // the wild one is faster and knocks the main out: the main never moves
        val main = mon(16, 5, "tackle"); main.hp = 1
        val ev2 = round(main, mon(19, 60, "tackle"))
        assertEquals(1, ev2.attacks().size)
        assertFalse(ev2.attacks().single().byPlayer)
        assertEquals(listOf(true), ev2.filterIsInstance<Fainted>().map { it.player })
        assertEquals(0, main.hp)
    }

    @Test fun faintFromBurnIsReportedOnce() {
        val main = mon(150, 50, "splash"); main.hp = 1
        val b = battle(mon(150, 100, "splash")); b.player.status = "brn"
        val ev = round(main, b.wild, acts = false, b = b)
        assertEquals(1, ev.count { it is Fainted && it.player })
        assertEquals(0, main.hp)
    }

    @Test fun recoilAndSelfDestructCanFaintTheUser() {
        val main = mon(19, 20, "doubleedge"); main.hp = 1
        val ev = round(main, mon(150, 100, "splash"), acts = false)
        assertEquals(1, ev.count { it is Fainted && it.player })
        val boom = Move("explosion", "Explosion", Type.NORMAL, Category.PHYSICAL, 250, 100, selfDestruct = true)
        val d2 = TestData.data(data.species.values.toList(), TestData.MOVES + (boom.id to boom))
        val m = TestData.mon(d2, 19, 20, listOf("explosion"))
        val ev2 = BattleEngine.playRound(d2, Battle(TestData.mon(d2, 150, 100, listOf("splash")), Tier.NORMAL), m, 1.0, false, Random(1))
        assertEquals(0, m.hp)
        assertEquals(1, ev2.count { it is Fainted && it.player })
    }

    @Test fun roundsCounterAndDeterminism() {
        val b = battle(mon(150, 50, "tackle"))
        val m = mon(19, 20, "tackle")
        round(m, b.wild, b = b); round(m, b.wild, b = b)
        assertEquals(2, b.rounds)
        val e1 = round(mon(19, 20, "tackle", "bodyslam"), mon(16, 20, "tackle", "growl"), seed = 9)
        val e2 = round(mon(19, 20, "tackle", "bodyslam"), mon(16, 20, "tackle", "growl"), seed = 9)
        assertEquals(e1, e2)
    }

    // ---------------------------------------------------------------- charge moves (poke-engine charge flag)

    @Test fun chargeMoveChargesFirstThenHits() {
        val main = mon(19, 20, "solarbeam"); val wild = mon(150, 100, "splash")
        val b = battle(wild)
        val before = wild.hp
        val ev1 = round(main, wild, acts = false, b = b)
        assertEquals(before, wild.hp)
        assertTrue(ev1.attacks().isEmpty())
        assertEquals(listOf("Ratty absorbed light!"), ev1.effects())
        assertTrue(ev1.filterIsInstance<Effect>().single().onPlayer)
        assertEquals(setOf("solarbeam"), b.player.charged)
        val ev2 = round(main, wild, acts = false, b = b)
        assertEquals(1, ev2.attacks().size)
        assertTrue(wild.hp < before)
    }

    @Test fun solarBeamStaysChargedAndKeepsHitting() {
        val main = mon(19, 20, "solarbeam"); val wild = mon(150, 100, "splash")
        val b = battle(wild)
        round(main, wild, acts = false, b = b)
        for (i in 1..3) {
            val hp = wild.hp
            val ev = round(main, wild, acts = false, b = b)
            assertEquals(1, ev.attacks().size)
            assertTrue(wild.hp < hp)
            assertEquals(setOf("solarbeam"), b.player.charged)
        }
    }

    @Test fun flyLosesTheChargeAfterTheHitAndChargesAgain() {
        val main = mon(19, 20, "fly"); val wild = mon(150, 100, "splash")
        val b = battle(wild)
        val before = wild.hp
        assertEquals(listOf("Ratty flew up high!"), round(main, wild, acts = false, b = b).effects())
        assertEquals(before, wild.hp)
        assertEquals(1, round(main, wild, acts = false, b = b).attacks().size)
        assertTrue(b.player.charged.isEmpty())
        val hp = wild.hp
        val ev = round(main, wild, acts = false, b = b)
        assertEquals(listOf("Ratty flew up high!"), ev.effects())
        assertTrue(ev.attacks().isEmpty())
        assertEquals(hp, wild.hp)
        assertEquals(setOf("fly"), b.player.charged)
    }

    @Test fun anyUseCountsEvenAMissAndTheWildOneChargesToo() {
        val main = mon(19, 20, "tackle"); val wild = mon(150, 100, "solarbeam")
        val b = battle(wild)
        val mainHp = main.hp
        val ev1 = round(main, wild, b = b)
        assertEquals(listOf("Mindlord absorbed light!"), ev1.effects().filter { "absorbed" in it })
        assertFalse(ev1.filterIsInstance<Effect>().first { "absorbed" in it.text }.onPlayer)
        assertEquals(mainHp, main.hp)
        assertEquals(setOf("solarbeam"), b.enemy.charged); assertTrue(b.player.charged.isEmpty())
        round(main, wild, b = b)
        assertTrue(main.hp < mainHp)
    }

    @Test fun aSleepingUserCannotCharge() {
        val main = mon(19, 20, "solarbeam"); val wild = mon(150, 100, "splash")
        val b = battle(wild); b.player.status = "slp"
        for (s in 1..40) round(main, wild, acts = false, seed = s, b = b)
        // whenever it woke up it charged (once), and never hit before that
        assertTrue(b.player.charged.isEmpty() || b.player.charged == setOf("solarbeam"))
        val asleep = battle(mon(150, 100, "splash")); asleep.player.status = "slp"
        val ev = BattleEngine.playRound(data, asleep, main, 1.0, false, object : Random() {
            override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) // never wakes
        })
        assertTrue(ev.effects().contains("Ratty is fast asleep."))
        assertTrue(asleep.player.charged.isEmpty())
    }

    @Test fun chargeTablesMatchPokeEngine() {
        assertEquals(17, Tuning.CHARGE_MOVES.size)
        assertTrue(Tuning.CHARGE_CLEARS_AFTER_USE.all { it in Tuning.CHARGE_MOVES })
        assertEquals(6, Tuning.CHARGE_CLEARS_AFTER_USE.size)
    }

    // ---------------------------------------------------------------- status rules (poke-engine) and Effect.status

    private val leafmon = TestData.species(5, "Leafmon", listOf(Type.GRASS), TestData.stats(100, 50, 50, 50, 50, 50))
    private val extraMoves = TestData.MOVES + listOf(
        Move("scald", "Scald", Type.WATER, Category.SPECIAL, 80, 100),
        Move("flamewheel", "Flame Wheel", Type.FIRE, Category.PHYSICAL, 60, 100),
        Move("sleeppowder", "Sleep Powder", Type.GRASS, Category.STATUS, 0, 100, status = "slp"),
        Move("stunspore", "Stun Spore", Type.GRASS, Category.STATUS, 0, 100, status = "par"),
    ).associateBy { it.id }
    private val data2 = TestData.data(data.species.values.toList() + leafmon, extraMoves)
    private fun mon2(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data2, sp, lv, moves.toList())
    private fun round2(main: Mon, b: Battle, acts: Boolean = false, seed: Int = 1) =
        BattleEngine.playRound(data2, b, main, 1.0, acts, Random(seed))

    @Test fun frozenTargetThawsWhenHitByAFireMove() {
        for (s in 1..30) {
            val wild = mon2(150, 100, "splash")
            val b = Battle(wild, Tier.NORMAL); b.enemy.status = "frz"
            val ev = round2(mon2(19, 20, "flamewheel"), b, seed = s) // no burn side effect
            assertNull(b.enemy.status)
            val thaw = ev.filterIsInstance<Effect>().first { it.text == "Mindlord thawed out!" }
            assertFalse(thaw.onPlayer); assertNull(thaw.status)
        }
        // a Water Gun doesn't thaw, Scald does
        val b2 = Battle(mon2(150, 100, "splash"), Tier.NORMAL); b2.enemy.status = "frz"
        round2(mon2(19, 20, "watergun"), b2)
        assertEquals("frz", b2.enemy.status)
        val b3 = Battle(mon2(150, 100, "splash"), Tier.NORMAL); b3.enemy.status = "frz"
        round2(mon2(19, 20, "scald"), b3)
        assertNull(b3.enemy.status)
    }

    @Test fun frozenUserOfAThawMoveThawsAndActs() {
        for (s in 1..60) {
            val b = Battle(mon2(150, 100, "splash"), Tier.NORMAL); b.player.status = "frz"
            val ev = round2(mon2(19, 20, if (s % 2 == 0) "scald" else "flamewheel"), b, seed = s)
            assertTrue(ev.effects().contains("Ratty thawed out!"))
            assertEquals(1, ev.attacks().size)
            assertNull(b.player.status)
        }
        // Ember is not a thaw move: stays frozen on most tries
        var frozen = 0
        for (s in 1..100) {
            val b = Battle(mon2(150, 100, "splash"), Tier.NORMAL); b.player.status = "frz"
            round2(mon2(19, 20, "ember"), b, seed = s)
            if (b.player.status == "frz") frozen++
        }
        assertTrue(frozen > 60)
    }

    @Test fun powderMovesFailAgainstGrassTypes() {
        for (s in 1..30) {
            val grass = Battle(mon2(19, 20, "sleeppowder"), Tier.NORMAL)
            val ev = round2(mon2(5, 20, "tackle"), grass, acts = true, seed = s)
            assertNull(grass.player.status)
            val powder = ev.attacks().first { !it.byPlayer }
            assertTrue(powder.failed)
            assertTrue(ev.effects().contains("It doesn't affect Leafmon…"))
        }
        // against a non-Grass target it still works (Stun Spore from a Normal user)
        var par = 0
        for (s in 1..40) {
            val b = Battle(mon2(5, 20, "stunspore"), Tier.NORMAL)
            round2(mon2(19, 20, "tackle"), b, acts = true, seed = s)
            if (b.player.status == "par") par++
        }
        assertTrue(par > 25)
    }

    @Test fun effectEventCarriesTheSidesStatus() {
        // set: Thunder Wave from the wild one paralyses our main
        var seen = false
        for (s in 1..40) {
            val b = battle(mon(25, 20, "thunderwave"))
            val ev = round(mon(19, 20, "tackle"), b.wild, b = b, seed = s)
            val e = ev.filterIsInstance<Effect>().firstOrNull { it.text == "Ratty was paralyzed!" } ?: continue
            assertTrue(e.onPlayer); assertEquals("par", e.status); seen = true; break
        }
        assertTrue(seen)
        // damage at end of turn still shows the status; the stat change of the other side shows none
        val burn = mon(19, 40, "tackle"); val bb = battle(mon(150, 60, "splash")); bb.player.status = "brn"
        val ev = round(burn, bb.wild, acts = false, b = bb)
        assertEquals("brn", ev.filterIsInstance<Effect>().first { it.text.contains("burn") }.status)
        // woke up / thawed: null
        val sl = battle(mon(150, 100, "splash")); sl.player.status = "slp"
        for (s in 1..50) {
            sl.player.status = "slp"
            val e = round(mon(19, 20, "tackle"), sl.wild, acts = false, b = sl, seed = s).filterIsInstance<Effect>()
            e.firstOrNull { it.text.endsWith("woke up!") }?.let { assertNull(it.status) }
            e.firstOrNull { it.text.endsWith("fast asleep.") }?.let { assertEquals("slp", it.status) }
        }
        // "tox" is carried raw (the panel shows it as PSN)
        for (s in 1..40) {
            val b = battle(mon(19, 20, "toxic"))
            val ev = round(mon(150, 100, "splash"), b.wild, b = b, seed = s)
            val e = ev.filterIsInstance<Effect>().firstOrNull { it.text.endsWith("badly poisoned!") } ?: continue
            assertEquals("tox", e.status); break
        }
    }
}
