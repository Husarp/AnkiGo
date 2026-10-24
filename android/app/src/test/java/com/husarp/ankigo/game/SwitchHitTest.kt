package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * Round 10, W10: switching the Main in a fight costs a hit. Made-up species and moves only (TestData.world).
 * Rule list: every way of changing the Main hits the new Main once at power 1.0 with the move choice; free cases (no fight, same
 * Main, old Main fainted, wild at 0 HP); a new Main at 0 HP in a fight is settled at once (healed, wild lost, no hit); a fainting new Main is what a round does; no round counted, no
 * end-of-turn damage; pending answers stay; sleep / paralysis may stop the wild one; charge moves; recoil can end the fight;
 * stats and achievements count it; the save round-trips.
 */
class SwitchHitTest {
    private val base = TestData.world()
    private val data = GameData(
        base.species,
        TestData.MOVES + listOf(Move("wildswing", "Wild Swing", Type.NORMAL, Category.PHYSICAL, 60, 1)).associateBy { it.id },
        base.growth, base.itemPrices,
    )
    private val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /**
     * A game with a team of [team] (t0 = the main; species 25 / 16 / 19 / 54 / 92 ..., Lv 10+), [box] box mons (b3, b4 ...) and a fight
     * against a wild Ratty Lv [wildLevel] with [wildMoves]. RANDOM move choice unless [choice] says otherwise, so the picks are the old ones.
     */
    private fun game(
        seed: Int = 7, team: Int = 3, box: Int = 2, wildMoves: List<String> = listOf("tackle"), wildLevel: Int = 12,
        choice: MoveChoice = MoveChoice.RANDOM, fight: Boolean = true,
    ): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        val species = listOf(25, 16, 19, 54, 92, 133, 129, 63, 16)
        for (i in 0 until team + box) {
            val m = TestData.mon(data, species[i % species.size], 10 + i, listOf("tackle"), uid = if (i < team) "t$i" else "b$i")
            s.mons += m
            if (i < team) s.team += m.uid
            s.seen += m.species; s.caught += m.species
        }
        s.settings.moveChoice = choice
        val g = Game(data, s, Random(seed)) { now }
        if (fight) s.battle = Battle(TestData.mon(data, 19, wildLevel, wildMoves, uid = "wild"), Tier.NORMAL, startedAt = now, rounds = 1) // R11-135: a round was played, so the switch costs the hit
        return g
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()
    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** The wild one's attack in a plain engine call: the same seed, the same battle, the incoming Pokémon on the field, the Main doing nothing. */
    private fun direct(seed: Int, incomingUid: String, wildMoves: List<String> = listOf("tackle")): GameEvent.Attack {
        val g = game(seed, wildMoves = wildMoves)
        val events = BattleEngine.playRound(data, g.state.battle!!, g.state.mon(incomingUid)!!, 1.0, true, Random(seed), 1.0, false, MoveChoice.RANDOM)
        return events.of<GameEvent.Attack>().first { !it.byPlayer }
    }

    private class Path(val name: String, val newMain: String, val act: (Game) -> ActionResult)

    /** Every way the Main can change (the UI only calls these). */
    private val paths = listOf(
        Path("setMain: a team member", "t2") { it.setMain("t2") },
        Path("setMain: a box Pokémon joins the team", "b3") { it.setMain("b3") },
        Path("cycleMain", "t1") { it.cycleMain(cardShowing = false) },
        Path("moveInTeam: the second one up", "t1") { it.moveInTeam("t1", -1) },
        Path("moveInTeam: the main down", "t1") { it.moveInTeam("t0", 1) },
        Path("toBox: the main", "t1") { it.toBox("t0") },
        Path("swapIntoTeam: a box Pokémon for the main", "b3") { it.swapIntoTeam("b3", "t0") },
        Path("release: the main", "t1") { it.release("t0") },
    )

    // ---------------------------------------------------------------- the rule

    @Test fun theHitIsOneAgainAtFullPower() {
        assertEquals(1.0, Tuning.WILD_POWER[1], 0.0)
        assertEquals(Tuning.WILD_POWER[1], Tuning.SWITCH_HIT_POWER, 0.0)
    }

    @Test fun everyWayOfChangingTheMainHitsTheNewMainOnceAtNormalPower() {
        for (p in paths) {
            val g = game(seed = 7)
            val incoming = g.state.mon(p.newMain)!!
            val before = incoming.hp
            val wild = g.state.battle!!.wild
            val wildHp = wild.hp
            val r = p.act(g)
            assertTrue(p.name, r.ok)
            assertEquals(p.name, p.newMain, g.state.main!!.uid)
            val e = r.events
            assertEquals(p.name, GameEvent.MainChanged(p.newMain, incoming.name), e[0])
            assertEquals(p.name, GameEvent.SwitchHit("Ratty", incoming.name, before, maxHp(incoming)), e[1])
            val attack = e[2] as GameEvent.Attack
            assertFalse(p.name, attack.byPlayer)
            assertEquals(p.name, direct(7, p.newMain), attack) // damage, hits and numbers equal a direct engine call with the same seed
            assertTrue(p.name, attack.damage > 0)
            assertEquals(p.name, before - attack.damage, incoming.hp)
            assertEquals(p.name, wildHp, wild.hp) // the switch itself costs the wild one nothing
            assertEquals(p.name, 1, e.of<GameEvent.Attack>().size)
            assertTrue(p.name, e.none { it is GameEvent.RoundStart || it is GameEvent.Splash || it is GameEvent.Counted })
            assertSame(p.name, wild, g.state.battle!!.wild) // the fight goes on
            assertTrue(p.name + ": " + r.message, r.message.contains("the wild Ratty hit"))
        }
    }

    @Test fun theHitIsAtNormalPowerNotAHalf() {
        val g = game()
        val full = BattleEngine.switchHit(data, g.state.battle!!, g.state.mon("t1")!!, Random(3))
        val g2 = game()
        val half = BattleEngine.switchHit(data, g2.state.battle!!, g2.state.mon("t1")!!, Random(3), power = 0.5)
        val a = full.of<GameEvent.Attack>().single().damage
        val b = half.of<GameEvent.Attack>().single().damage
        assertEquals(direct(3, "t1").damage, a)
        assertTrue("$b vs $a", b < a && b >= a / 2 - 1)
    }

    @Test fun theMessageNamesTheHit() {
        val g = game()
        val r = g.setMain("t2")
        val hit = r.events.of<GameEvent.Attack>().single().damage
        assertEquals("${g.state.main!!.name} is your main now — the wild Ratty hit it for $hit HP.", r.message)
        // a message that does not name the new main: the hit names it
        val g2 = game()
        val r2 = g2.toBox("t0")
        val hit2 = r2.events.of<GameEvent.Attack>().single().damage
        assertEquals("${g2.state.mon("t0")!!.name} went to the box — the wild Ratty hit ${g2.state.main!!.name} for $hit2 HP.", r2.message)
    }

    // ---------------------------------------------------------------- free switches

    @Test fun noFightNoHit() {
        for (p in paths) {
            val g = game(fight = false)
            val r = p.act(g)
            assertTrue(p.name, r.ok)
            assertEquals(p.name, listOf<GameEvent>(GameEvent.MainChanged(p.newMain, g.state.main!!.name)), r.events)
            assertNull(p.name, g.state.battle)
            assertFalse(p.name, "wild" in r.message)
        }
    }

    @Test fun theSameMainIsNoSwitch() {
        val g = game()
        val before = g.state.main!!.hp
        val r = g.setMain("t0")
        assertTrue(r.events.isEmpty())
        // moves that leave the Main where it is
        val r2 = g.moveInTeam("t1", 1)
        assertTrue(r2.ok && r2.events.isEmpty())
        val r3 = g.toBox("t2")
        assertTrue(r3.ok && r3.events.isEmpty())
        assertEquals("t0", g.state.main!!.uid)
        assertEquals(before, g.state.main!!.hp)
        assertEquals(1, g.state.battle!!.rounds)   // no round was counted
    }

    @Test fun aFaintedOldMainMakesEveryPathFree() {
        for (p in paths) {
            val g = game()
            g.state.main!!.hp = 0
            val wild = g.state.battle!!.wild
            val incoming = g.state.mon(p.newMain)!!
            val before = incoming.hp
            val r = p.act(g)
            assertTrue(p.name, r.ok)
            assertEquals(p.name, listOf<GameEvent>(GameEvent.MainChanged(p.newMain, incoming.name)), r.events)
            assertEquals(p.name, before, incoming.hp)
            assertSame(p.name, wild, g.state.battle!!.wild)
            assertFalse(p.name, "wild" in r.message)
        }
    }

    // ---------------------------------------------------------------- R11-135: free until the first answer of the battle

    @Test fun beforeTheFirstRoundEveryPathIsFree() {
        for (p in paths) {
            val g = game()
            g.state.battle!!.rounds = 0
            val wild = g.state.battle!!.wild
            val incoming = g.state.mon(p.newMain)!!
            val before = incoming.hp
            val r = p.act(g)
            assertTrue(p.name, r.ok)
            assertEquals(p.name, listOf<GameEvent>(GameEvent.MainChanged(p.newMain, incoming.name)), r.events)
            assertEquals(p.name, before, incoming.hp)
            assertSame(p.name, wild, g.state.battle!!.wild)
            assertEquals(p.name, 0, g.state.battle!!.rounds)
        }
    }

    @Test fun afterOneRoundTheSwitchCostsTheHit() {
        val g = game()
        g.state.battle!!.rounds = 0
        g.onAnswer(Answer(1, 3, now)); g.onAnswer(Answer(2, 3, now))   // a whole round (2 answers) is played
        assertEquals(1, g.state.battle!!.rounds)
        val r = g.setMain("t1")
        assertTrue(r.events.of<GameEvent.SwitchHit>().isNotEmpty())
    }

    @Test fun anAnswerWaitingInTheFirstRoundAlreadyMakesTheSwitchCostTheHit() {
        val g = game()
        g.state.battle!!.rounds = 0
        g.onAnswer(Answer(1, 3, now))   // one of two answers: no round yet, but the first answer of the fight is given
        assertEquals(0, g.state.battle!!.rounds)
        assertTrue(g.setMain("t1").events.of<GameEvent.SwitchHit>().isNotEmpty())
    }

    @Test fun aWildOneWithNoHpLeftCannotHit() {
        for (p in paths) {
            val g = game()
            g.state.battle!!.wild.hp = 0
            val incoming = g.state.mon(p.newMain)!!
            val before = incoming.hp
            val r = p.act(g)
            assertEquals(p.name, listOf<GameEvent>(GameEvent.MainChanged(p.newMain, incoming.name)), r.events)
            assertEquals(p.name, before, incoming.hp)
        }
    }

    @Test fun aNewMainAtZeroHpIsSettledAtOnceLikeAFaintInARoundAndTakesNoHit() {
        for (p in paths.filter { it.newMain == "t1" }) {
            val g = game()
            val t1 = g.state.mon("t1")!!
            t1.hp = 0
            val oldBattle = g.state.battle!!
            val r = p.act(g)
            val e = r.events
            assertTrue(p.name, r.ok)
            assertEquals(p.name, "t1", g.state.main!!.uid)
            assertEquals(p.name, GameEvent.MainChanged("t1", t1.name), e[0])
            assertEquals(p.name, GameEvent.MainHealed("t1", t1.name, maxHp(t1)), e[1])
            assertTrue(p.name, e[2] is GameEvent.Encounter)
            assertTrue(p.name, e.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })
            assertEquals(p.name, maxHp(t1), t1.hp)
            assertNotSame(p.name, oldBattle, g.state.battle) // the wild one is lost, the next encounter started
            assertTrue(p.name + ": " + r.message, r.message.contains("the wild one is lost"))
        }
    }

    @Test fun switchingToAZeroHpMemberOutsideAFightChangesNothingElse() {
        val g = game(fight = false)
        g.state.mon("t1")!!.hp = 0
        val r = g.cycleMain(cardShowing = false)
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("t1", g.state.main!!.name)), r.events)
        assertEquals(0, g.state.main!!.hp) // as before: no fight, nothing to settle
    }

    @Test fun theTwoSwitchDodgeNoLongerWorks() {
        val g = game()
        g.state.mon("t1")!!.hp = 0
        val first = g.setMain("t1") // settled: healed, the fight is over
        assertTrue(first.events.any { it is GameEvent.MainHealed })
        // a new wild one is there (ensureEncounter); the next switch is a normal one and pays its hit
        val battle = g.state.battle!!
        assertTrue(battle.wild.hp > 0)
        battle.rounds = 1 // R11-135: the first answers of the new fight were given (before them a switch is free)
        val second = g.setMain("t2")
        assertTrue(second.events.any { it is GameEvent.SwitchHit })
    }

    @Test fun afterTheSettlementSwitchingWithoutAFightIsFreeAsBefore() {
        val g = game()
        g.state.mon("t1")!!.hp = 0
        g.setMain("t1")
        g.state.battle = null // the fight is over (no encounter running)
        val r = g.setMain("t2")
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("t2", g.state.main!!.name)), r.events)
    }

    @Test fun aZeroHpMemberComingInWhileTheWildOneHasNoHpLeftIsNotSettled() {
        val g = game()
        g.state.battle!!.wild.hp = 0
        g.state.mon("t1")!!.hp = 0
        val r = g.cycleMain(cardShowing = false)
        assertEquals(listOf<GameEvent>(GameEvent.MainChanged("t1", g.state.main!!.name)), r.events)
        assertEquals(0, g.state.main!!.hp)
    }

    // ---------------------------------------------------------------- what the action does and does not do

    @Test fun noRoundIsCountedAndThePendingAnswersStay() {
        val g = game()
        val b = g.state.battle!!
        b.rounds = 4; b.answers = 1; b.tally = 10; b.buttons += 3
        g.cycleMain(cardShowing = false)
        assertEquals(4, b.rounds)
        assertEquals(1, b.answers); assertEquals(10, b.tally); assertEquals(listOf(3), b.buttons)
        // the next answer completes the round as it would have
        val answers = g.onAnswer(Answer(2000L, 3, now, false))
        assertTrue(answers.any { it is GameEvent.RoundStart })
        assertEquals(5, b.rounds)
    }

    @Test fun noEndOfTurnDamageForEitherSide() {
        val g = game()
        val b = g.state.battle!!
        b.enemy.status = "psn" // the wild one is poisoned: a round would hurt it at the end
        val wildHp = b.wild.hp
        val r = g.cycleMain(cardShowing = false)
        assertEquals(wildHp, b.wild.hp)
        assertTrue(r.events.none { it is GameEvent.Effect && it.kind == EffectKind.STATUS_DAMAGE })
        // a status the hit puts on the new Main does not tick either
        var applied = false
        for (seed in 1..40) {
            val t = game(seed = seed, wildMoves = listOf("toxic"))
            val before = t.state.mon("t1")!!.hp
            val e = t.cycleMain(cardShowing = false).events
            assertEquals(before, t.state.main!!.hp) // toxic deals nothing now
            assertTrue(e.none { it is GameEvent.Effect && it.kind == EffectKind.STATUS_DAMAGE })
            if (e.any { it is GameEvent.Effect && it.kind == EffectKind.STATUS_APPLIED }) { applied = true; assertEquals("tox", t.state.battle!!.player.status) }
        }
        assertTrue(applied)
    }

    @Test fun theNewMainStartsOnFreshStagesAndTheHitCanLeaveAStatus() {
        var parked = false
        for (seed in 1..40) {
            val t = game(seed = seed, wildMoves = listOf("thunderwave"))
            t.state.battle!!.player.boosts[0] = 3
            t.state.battle!!.player.status = "brn"
            t.state.battle!!.enemy.boosts[1] = -2
            val e = t.cycleMain(cardShowing = false).events
            assertEquals(0, t.state.battle!!.player.boosts[0])
            assertEquals(-2, t.state.battle!!.enemy.boosts[1]) // the wild one keeps its own stages
            if (e.any { it is GameEvent.Effect && it.kind == EffectKind.STATUS_APPLIED }) { parked = true; assertEquals("par", t.state.battle!!.player.status) }
            else assertNull(t.state.battle!!.player.status) // the old Main's burn did not come along
        }
        assertTrue(parked)
    }

    // ---------------------------------------------------------------- the new Main faints

    @Test fun aFaintingNewMainIsWhatARoundDoes() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        t1.hp = 1
        val oldBattle = g.state.battle!!
        val r = g.cycleMain(cardShowing = false)
        val e = r.events
        assertEquals(GameEvent.MainChanged("t1", t1.name), e[0])
        assertEquals(GameEvent.SwitchHit("Ratty", t1.name, 1, maxHp(t1)), e[1])
        val attack = e[2] as GameEvent.Attack
        assertEquals(1, attack.damage) // never more than it had left
        assertEquals(GameEvent.Fainted(true, t1.name), e[3])
        assertEquals(GameEvent.MainHealed("t1", t1.name, maxHp(t1)), e[4])
        assertTrue(e[5] is GameEvent.Encounter) // the next wild one, like after a round
        assertEquals(maxHp(t1), t1.hp) // healed at once
        assertNotSame(oldBattle, g.state.battle) // the wild one is lost
        assertTrue(r.message.contains("fainted") && r.message.contains("lost"))
        // the same ending as a full round of two Again answers when the Main has 1 HP
        val g2 = game()
        g2.state.main!!.hp = 1
        val oldBattle2 = g2.state.battle!!
        val round = g2.onAnswer(Answer(1L, 1, now, false)) + g2.onAnswer(Answer(2L, 1, now, false))
        assertTrue(round.any { it is GameEvent.MainHealed })
        assertNotSame(oldBattle2, g2.state.battle)
        assertEquals(maxHp(g2.state.main!!), g2.state.main!!.hp)
    }

    @Test fun theWildOneCanFaintFromItsOwnRecoilAndThenTheFightIsOverLikeInARound() {
        val g = game(wildMoves = listOf("doubleedge"), wildLevel = 5)
        val wild = g.state.battle!!.wild
        wild.hp = 1
        val t1 = g.state.mon("t1")!!
        val r = g.cycleMain(cardShowing = false)
        val e = r.events
        assertTrue(e.any { it is GameEvent.Fainted && !it.player })
        assertTrue(e.any { it is GameEvent.Defeated || it is GameEvent.Caught })
        assertEquals(1, e.of<GameEvent.Encounter>().size)
        assertNotSame(wild, g.state.battle!!.wild)
        assertTrue(t1.hp > 0)
        assertTrue(r.message, r.message.contains("fainted too"))
    }

    // ---------------------------------------------------------------- move choice

    @Test fun smartSkipsAnImmuneMoveButRandomDoesNot() {
        // the new Main (Birdie, Normal / Flying) is immune to the wild one's Earthquake
        var randomWasImmune = false
        for (seed in 1..120) {
            val smart = game(seed = seed, choice = MoveChoice.SMART, wildMoves = listOf("earthquake", "tackle"))
            assertEquals("Tackle", smart.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single().move)
            val rnd = game(seed = seed, choice = MoveChoice.RANDOM, wildMoves = listOf("earthquake", "tackle"))
            val a = rnd.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single()
            if (a.move == "Earthquake") { randomWasImmune = true; assertEquals(0.0, a.effectiveness, 0.0); assertEquals(0, a.damage) }
        }
        assertTrue(randomWasImmune)
    }

    @Test fun smartSkipsAStatusThatCannotWorkButRandomDoesNot() {
        // the new Main (t4: Ghost / Poison) cannot be poisoned: Toxic would do nothing
        var randomToxic = false
        for (seed in 1..120) {
            val smart = game(seed = seed, team = 5, choice = MoveChoice.SMART, wildMoves = listOf("toxic", "watergun"))
            assertEquals("Water Gun", smart.setMain("t4").events.of<GameEvent.Attack>().single().move)
            val rnd = game(seed = seed, team = 5, choice = MoveChoice.RANDOM, wildMoves = listOf("toxic", "watergun"))
            if (rnd.setMain("t4").events.of<GameEvent.Attack>().single().move == "Toxic") randomToxic = true
        }
        assertTrue(randomToxic)
    }

    @Test fun smartestPrefersTheStrongerAttack() {
        var smartest = 0; var random = 0
        for (seed in 1..300) {
            val s = game(seed = seed, choice = MoveChoice.SMARTEST, wildMoves = listOf("tackle", "bodyslam"), wildLevel = 8)
            if (s.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single().move == "Body Slam") smartest++
            val r = game(seed = seed, choice = MoveChoice.RANDOM, wildMoves = listOf("tackle", "bodyslam"), wildLevel = 8)
            if (r.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single().move == "Body Slam") random++
        }
        assertTrue("smartest $smartest", smartest > 190) // 70 % of the picks
        assertTrue("random $random", random in 100..200) // about half
    }

    // ---------------------------------------------------------------- the wild one may not be able to act, or may miss

    @Test fun aSleepingWildOneMayFailToActAndStillTheFightGoesOn() {
        var asleep = false; var woke = false
        for (seed in 1..80) {
            val g = game(seed = seed)
            g.state.battle!!.enemy.status = "slp"
            val before = g.state.mon("t1")!!.hp
            val e = g.cycleMain(cardShowing = false).events
            assertEquals(GameEvent.MainChanged("t1", g.state.main!!.name), e[0])
            assertTrue(e[1] is GameEvent.SwitchHit)
            if (e.any { it is GameEvent.Effect && it.kind == EffectKind.CANT_MOVE }) {
                asleep = true
                assertTrue(e.none { it is GameEvent.Attack })
                assertEquals(before, g.state.main!!.hp)
                assertEquals("slp", g.state.battle!!.enemy.status)
            } else {
                woke = true
                assertEquals(1, e.of<GameEvent.Attack>().size)
                assertNull(g.state.battle!!.enemy.status)
            }
        }
        assertTrue(asleep && woke)
    }

    @Test fun aParalysedWildOneMayBeFullyParalysed() {
        var skipped = false; var acted = false
        for (seed in 1..80) {
            val g = game(seed = seed)
            g.state.battle!!.enemy.status = "par"
            val e = g.cycleMain(cardShowing = false).events
            if (e.any { it is GameEvent.Effect && it.kind == EffectKind.CANT_MOVE }) { skipped = true; assertTrue(e.none { it is GameEvent.Attack }) }
            else { acted = true; assertEquals(1, e.of<GameEvent.Attack>().size) }
        }
        assertTrue(skipped && acted)
    }

    @Test fun theToastSaysWhenTheWildOneCouldNotHit() {
        for (seed in 1..80) {
            val g = game(seed = seed)
            g.state.battle!!.enemy.status = "slp"
            val r = g.cycleMain(cardShowing = false)
            if (r.events.any { it is GameEvent.Effect && it.kind == EffectKind.CANT_MOVE }) {
                assertEquals("${g.state.main!!.name} is your main now — the wild Ratty couldn't attack.", r.message)
                return
            }
        }
        throw AssertionError("no seed left it asleep")
    }

    @Test fun aMissIsAMissInTheToastAndInTheStats() {
        // a 1 % move misses nearly always: take the first seed that does
        val seed = (1..50).first { game(seed = it, wildMoves = listOf("wildswing")).cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single().missed }
        val g = game(seed = seed, wildMoves = listOf("wildswing"))
        val r = g.cycleMain(cardShowing = false)
        val a = r.events.of<GameEvent.Attack>().single()
        assertTrue(a.missed)
        assertEquals(maxHp(g.state.main!!), g.state.main!!.hp)
        assertEquals("${g.state.main!!.name} is your main now — the wild Ratty attacked but missed.", r.message)
        assertEquals(1, g.state.history[g.state.day]!!.misses)
        assertEquals(1L, g.state.counters[Metric.MISSES.name])
    }

    @Test fun anImmuneTargetSaysSoAndLosesNothing() {
        val g = game(wildMoves = listOf("earthquake")) // t1 (Normal / Flying) is immune
        val r = g.cycleMain(cardShowing = false)
        val a = r.events.of<GameEvent.Attack>().single()
        assertEquals(0.0, a.effectiveness, 0.0)
        assertEquals(maxHp(g.state.main!!), g.state.main!!.hp)
        assertEquals("${g.state.main!!.name} is your main now — the wild Ratty's Earthquake had no effect on it.", r.message)
    }

    // ---------------------------------------------------------------- charge moves

    @Test fun aChargeMoveThatWouldOnlyChargeIsLeftOutWhileThereIsAnotherMove() {
        for (c in MoveChoice.entries) for (seed in 1..60) {
            val g = game(seed = seed, choice = c, wildMoves = listOf("fly", "tackle"))
            val e = g.cycleMain(cardShowing = false).events
            assertEquals("$c $seed", "Tackle", e.of<GameEvent.Attack>().single().move)
            assertNull(g.state.battle!!.enemy.charging)
        }
    }

    @Test fun aWildOneWithOnlyAChargeMoveSpendsTheHitCharging() {
        val g = game(wildMoves = listOf("fly"))
        val before = g.state.mon("t1")!!.hp
        val r = g.cycleMain(cardShowing = false)
        assertTrue(r.events.any { it is GameEvent.Effect && it.kind == EffectKind.CHARGE })
        assertEquals(before, g.state.main!!.hp)
        assertEquals("fly", g.state.battle!!.enemy.charging) // as in a round: its next action is the hit
        assertEquals("${g.state.main!!.name} is your main now — the wild Ratty was charging up.", r.message)
    }

    @Test fun aWildOneThatIsChargingReleasesTheChargeInTheFreeHit() {
        // R17C-3: its very next action is the hit, whatever it would have picked
        for (seed in 1..30) {
            val g = game(seed = seed, wildMoves = listOf("fly", "tackle"))
            g.state.battle!!.enemy.charging = "fly"
            val a = g.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single()
            assertEquals("Fly", a.move); assertTrue(a.damage > 0)
            assertNull(g.state.battle!!.enemy.charging)
        }
    }

    // ---------------------------------------------------------------- stats, achievements, save

    @Test fun theHitsDamageCountsLikeAnyWildAttack() {
        val g = game()
        val day = g.state.history[g.state.day]?.dmgTaken ?: 0
        val counter = g.state.counters[Metric.DAMAGE_TAKEN.name] ?: 0L
        val a = g.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single()
        assertTrue(a.damage > 0)
        assertEquals(day + a.damage, g.state.history[g.state.day]!!.dmgTaken)
        assertEquals(counter + a.damage.toLong(), g.state.counters[Metric.DAMAGE_TAKEN.name]!!)
        // a free switch counts nothing
        val free = game(fight = false)
        free.cycleMain(cardShowing = false)
        assertNull(free.state.history[free.state.day])
        assertNull(free.state.counters[Metric.DAMAGE_TAKEN.name])
    }

    @Test fun theSaveRoundTripsMidFightAfterAHit() {
        val g = game()
        val a = g.cycleMain(cardShowing = false).events.of<GameEvent.Attack>().single()
        assertTrue(a.damage > 0)
        val json = SaveCodec.encode(g.state)
        val back = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(back))
        assertEquals(g.state.main!!.hp, back.main!!.hp)
        assertEquals(g.state.battle!!.wild.hp, back.battle!!.wild.hp)
        assertEquals(g.state.battle!!.rounds, back.battle!!.rounds)
        // the loaded game plays on
        val next = Game(data, back, Random(3)) { now }
        assertTrue(next.onAnswer(Answer(77L, 3, now, false)).any { it is GameEvent.Counted })
        // nothing about a hit is saved: a state without one has the same top-level keys (old saves load as before)
        val keys = { s: String -> org.json.JSONObject(s).keys().asSequence().toSet() }
        assertEquals(keys(SaveCodec.encode(game().state)), keys(json))
    }

    // ---------------------------------------------------------------- the words

    @Test fun theTextForEachKindOfHit() {
        fun attack(damage: Int = 0, missed: Boolean = false, failed: Boolean = false, eff: Double = 1.0, category: Category = Category.PHYSICAL) =
            GameEvent.Attack(false, "Ratty", "Birdie", "Tackle", Type.NORMAL, category, damage, 1, eff, false, missed, failed, 10, 30, 20, 20)
        val marker = GameEvent.SwitchHit("Wild Ratty", "Birdie", 30, 30)
        assertEquals("the wild Ratty hit it for 12 HP", SwitchHitText.tail(listOf(marker, attack(12)), named = true))
        assertEquals("the wild Ratty hit Birdie for 12 HP", SwitchHitText.tail(listOf(marker, attack(12)), named = false))
        assertEquals("the wild Ratty attacked but missed", SwitchHitText.tail(listOf(marker, attack(missed = true)), true))
        assertEquals("the wild Ratty's Tackle had no effect on it", SwitchHitText.tail(listOf(marker, attack(eff = 0.0)), true))
        assertEquals("the wild Ratty used Tackle", SwitchHitText.tail(listOf(marker, attack(category = Category.STATUS)), true))
        assertEquals("the wild Ratty used Tackle, but it failed", SwitchHitText.tail(listOf(marker, attack(category = Category.STATUS, failed = true)), true))
        assertEquals("the wild Ratty couldn't attack", SwitchHitText.tail(listOf(marker), true))
        val fainted = listOf(marker, attack(30), GameEvent.Fainted(true, "Birdie"))
        assertEquals("the wild Ratty hit it for 30 HP and it fainted. It is back at full HP, but the wild one is lost", SwitchHitText.tail(fainted, true))
        assertEquals("", SwitchHitText.tail(emptyList(), true))
        assertEquals("Birdie is your main now — the wild Ratty hit it for 12 HP.", SwitchHitText.withHit("Birdie is your main now.", listOf(marker, attack(12)), true))
        assertEquals("Moved Birdie to place 1.", SwitchHitText.withHit("Moved Birdie to place 1.", emptyList(), true))
    }

    @Test fun makeMainStaysSilentUnlessItCostAHit() {
        val quiet = ActionResult(true, "X is your main now.", listOf(GameEvent.MainChanged("u", "X")))
        assertEquals("", quiet.quietUnlessHit().message)
        val hit = ActionResult(true, "X is your main now — hit.", listOf(GameEvent.MainChanged("u", "X"), GameEvent.SwitchHit("W", "X", 5, 9)))
        assertEquals("X is your main now — hit.", hit.quietUnlessHit().message)
        val failed = ActionResult(false, "Pokémon not found.")
        assertEquals("Pokémon not found.", failed.quietUnlessHit().message)
        // through the game: a free "Make main" is silent, a costly one speaks
        val free = game(fight = false).setMain("t1").quietUnlessHit()
        assertEquals("", free.message)
        val costly = game().setMain("t1").quietUnlessHit()
        assertTrue(costly.message, costly.message.contains("the wild Ratty hit"))
    }

    @Test fun aFaintedMemberComingInSpeaksEvenThoughMakeMainIsQuiet() {
        val g = game()
        g.state.mon("t1")!!.hp = 0
        val r = g.setMain("t1").quietUnlessHit()
        assertTrue(r.message, r.message.contains("the wild one is lost"))
    }

    // ---------------------------------------------------------------- the engine function itself

    @Test fun theEngineHitDoesNotCountARoundOrHurtAtTheEnd() {
        val g = game()
        val b = g.state.battle!!
        b.rounds = 2
        b.enemy.status = "brn"
        val wildHp = b.wild.hp
        val e = BattleEngine.switchHit(data, b, g.state.mon("t1")!!, Random(5))
        assertEquals(2, b.rounds)
        assertEquals(wildHp, b.wild.hp)
        assertEquals(1, e.of<GameEvent.Attack>().size)
        assertTrue(e.of<GameEvent.Attack>().all { !it.byPlayer })
        assertTrue(e.none { it is GameEvent.Splash || it is GameEvent.RoundStart })
        assertTrue(e.none { it is GameEvent.Effect && it.kind == EffectKind.STATUS_DAMAGE })
    }

    @Test fun theEngineHitOnAFaintedTargetOrFromAFaintedWildOneDoesNothing() {
        val g = game()
        val t1 = g.state.mon("t1")!!
        t1.hp = 0
        assertTrue(BattleEngine.switchHit(data, g.state.battle!!, t1, Random(5)).isEmpty())
        val g2 = game()
        g2.state.battle!!.wild.hp = 0
        assertTrue(BattleEngine.switchHit(data, g2.state.battle!!, g2.state.mon("t1")!!, Random(5)).isEmpty())
    }
}
