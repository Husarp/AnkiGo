package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/** An item works before one switch-style wild move; the player's round and own attack are preserved. */
class ItemTurnTest {
    private val data = TestData.world()
    private val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** Main t0 (Sparkmouse 25, Lv 20, hurt), a second team member t1 (hurt) and a fight against Ratty Lv 12 with [rounds] played. */
    private fun game(rounds: Int = 1, answers: Int = 0, fight: Boolean = true): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        for ((i, sp) in listOf(25, 16).withIndex()) {
            val m = TestData.mon(data, sp, 20, listOf("tackle"), uid = "t$i")
            m.hp = StatCalc.maxHp(data, m) / 2
            s.mons += m; s.team += m.uid
        }
        val g = Game(data, s, Random(7)) { now }
        if (fight) s.battle = Battle(TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = now, rounds = rounds, answers = answers)
        Economy.give(s, "potion", 3)
        return g
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    @Test fun aPotionOnTheMainInAFightHealsThenTheWildOneAttacksOnce() {
        val g = game()
        val main = g.state.main!!
        val amount = StatCalc.maxHp(data, main) / 4
        val healedTo = main.hp + amount
        val r = g.useItem("potion", main.uid)
        assertTrue(r.ok)
        assertEquals(2, g.state.count("potion"))
        val marker = r.events.of<GameEvent.SwitchHit>().single()
        assertEquals(GameEvent.SwitchHit("Ratty", main.name, healedTo, StatCalc.maxHp(data, main), "potion"), marker)
        val attacks = r.events.of<GameEvent.Attack>()
        assertEquals("the wild one uses one free move, the Main doesn't", 1, attacks.size)
        assertFalse(attacks.single().byPlayer)
        assertEquals(healedTo - attacks.single().damage, main.hp)
        assertTrue(r.message, r.message.startsWith("Potion healed ${main.name} by $amount HP — the wild Ratty"))
        // The extra wild move leaves the round count and pending answers alone.
        assertEquals(1, g.state.battle!!.rounds); assertEquals(0, g.state.battle!!.answers)
    }

    @Test fun noTargetMeansTheMainAndTakesTheTurnToo() {
        val g = game()
        val r = g.useItem("potion", null)
        assertTrue(r.ok)
        assertEquals(1, r.events.of<GameEvent.SwitchHit>().size)
    }

    @Test fun anItemOnAnotherPokemonOrBetweenFightsTakesNoTurn() {
        val other = game()
        val r = other.useItem("potion", "t1")
        assertTrue(r.ok); assertTrue(r.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })

        val noFight = game(fight = false)
        val n = noFight.useItem("potion", "t0")
        assertTrue(n.ok); assertTrue(n.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })
        assertEquals(StatCalc.maxHp(data, noFight.state.main!!) / 2 + StatCalc.maxHp(data, noFight.state.main!!) / 4, noFight.state.main!!.hp)

        val oneAnswer = game(rounds = 0, answers = 1)   // one answer waiting in the round: it counts
        assertEquals(1, oneAnswer.useItem("potion", "t0").events.of<GameEvent.SwitchHit>().size)

        val faintedWild = game()
        faintedWild.state.battle!!.wild.hp = 0
        assertTrue(faintedWild.useItem("potion", "t0").events.none { it is GameEvent.SwitchHit })
    }

    @Test fun itemsGiveTheWildOneAMoveEvenBeforeTheFirstAnswer() {
        val g = game(rounds = 0)
        val battle = g.state.battle!!
        val r = g.useItem("potion", "t0")
        assertTrue(r.ok)
        assertEquals(1, r.events.of<GameEvent.SwitchHit>().size)
        assertEquals(1, r.events.of<GameEvent.Attack>().size)
        assertEquals(0, battle.rounds)
        assertEquals(0, battle.answers)
        assertEquals(0, battle.tally)
    }

    @Test fun theFreeHitCanKnockOutTheHealedPokemonAndRunsTheNormalFaintFlow() {
        val g = game()
        val main = g.state.main!!
        val battle = g.state.battle!!
        battle.wild.moves.clear(); battle.wild.moves += "dragonrage"
        main.hp = 1
        val healedTo = 1 + StatCalc.maxHp(data, main) / 4
        val r = g.useItem("potion", main.uid)
        assertTrue(r.ok)
        assertEquals(healedTo, r.events.of<GameEvent.SwitchHit>().single().hp)
        assertEquals(healedTo, r.events.of<GameEvent.Attack>().single().damage)
        val attack = r.events.indexOfFirst { it is GameEvent.Attack }
        val faint = r.events.indexOfFirst { it is GameEvent.Fainted && it.player }
        val heal = r.events.indexOfFirst { it is GameEvent.MainHealed }
        val encounter = r.events.indexOfFirst { it is GameEvent.Encounter }
        assertTrue(attack < faint && faint < heal && heal < encounter)
        assertEquals(StatCalc.maxHp(data, main), main.hp)
        assertNotSame(battle, g.state.battle)
        assertTrue(r.message, r.message.contains("fainted") && r.message.contains("wild one is lost"))
    }

    @Test fun oldGateauHealsWithoutAnAttackAtAnyPointInBattle() {
        for (rounds in listOf(0, 1)) {
            val g = game(rounds = rounds)
            val main = g.state.main!!
            main.hp = 1
            val battle = g.state.battle!!
            Economy.give(g.state, "old-gateau")
            val r = g.useItem("old-gateau", null)
            assertTrue(r.ok)
            assertEquals(1 + StatCalc.maxHp(data, main) / 2, main.hp)
            assertEquals(0, g.state.count("old-gateau"))
            assertTrue(r.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })
            assertSame(battle, g.state.battle)
        }
    }

    @Test fun theItemHitKeepsThePendingAnswersAndOwnAttackOnTheNextRound() {
        val g = game(rounds = 2, answers = 1)
        g.roundAnswers = 2
        val battle = g.state.battle!!
        battle.tally = 10; battle.buttons += 3
        val result = g.useItem("potion", "t0")
        assertTrue(result.ok)
        assertEquals(2, battle.rounds)
        assertEquals(1, battle.answers)
        assertEquals(10, battle.tally)
        assertEquals(listOf(3), battle.buttons)
        assertTrue(result.events.none { it is GameEvent.RoundStart || it is GameEvent.Charge || it is GameEvent.Counted })
        val events = g.onAnswer(Answer(2000L, 3, now))
        assertEquals(1, events.of<GameEvent.RoundStart>().size)
        assertEquals(1, events.of<GameEvent.Attack>().count { it.byPlayer })
        assertEquals(3, battle.rounds)
    }

    @Test fun theFreeMoveDoesNotTickTimedHealingOrEndOfRoundDamage() {
        for (item in listOf("berry-juice", "sweet-heart")) {
            val g = game()
            val battle = g.state.battle!!
            battle.enemy.status = "psn"
            val wildHp = battle.wild.hp
            val mainHp = g.state.main!!.hp
            val max = StatCalc.maxHp(data, g.state.main!!)
            Economy.give(g.state, item)
            val r = g.useItem(item, "t0")
            val hit = r.events.of<GameEvent.SwitchHit>().single()
            assertEquals(mainHp + if (item == "berry-juice") max / 10 else 0, hit.hp)
            assertEquals(wildHp, battle.wild.hp)
            assertEquals(if (item == "berry-juice") 4 else 1,
                battle.field.player[if (item == "berry-juice") "heal.regen/t0" else "heal.pending/t0"])
            val heals = r.events.of<GameEvent.Effect>().filter { it.kind == EffectKind.HEAL }
            assertEquals(if (item == "berry-juice") listOf(max / 10) else emptyList<Int>(), heals.map { it.amount })
            assertTrue(heals.all { r.events.indexOf(it) < r.events.indexOf(hit) })
            assertTrue(r.events.drop(r.events.indexOf(hit)).none { it is GameEvent.Effect && it.kind in setOf(EffectKind.HEAL, EffectKind.STATUS_DAMAGE) })
        }
    }

    @Test fun theImmediateFreeAttackConsumesTheOneHitSnackShield() {
        val shielded = game(rounds = 0)
        Economy.give(shielded.state, "shalour-sable")
        val result = shielded.useItem("shalour-sable", "t0")
        assertTrue(result.ok)
        val hit = result.events.of<GameEvent.Attack>().single()
        assertFalse(hit.byPlayer)
        assertTrue(result.events.of<GameEvent.Effect>().any { it.text.contains("weakened the hit") })
        assertFalse(shielded.state.battle!!.player.volatiles.containsKey("snack.shield"))
        val plain = game(rounds = 0).useItem("potion", "t0").events.of<GameEvent.Attack>().single()
        assertTrue("the shield reduces this free attack", hit.damage < plain.damage)
        assertEquals(0, shielded.state.battle!!.rounds)
        assertEquals(0, shielded.state.battle!!.answers)
    }

    @Test fun aResultWaitingForContinueGivesNoAttack() {
        val g = game()
        val battle = g.state.battle!!
        battle.awaitingContinue = true
        val r = g.useItem("potion", "t0")
        assertTrue(r.ok)
        assertTrue(r.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })
        assertTrue(battle.awaitingContinue)
        assertSame(battle, g.state.battle)
    }

    @Test fun aHeldItemOrAnItemThatDidNothingTakesNoTurn() {
        val g = game()
        Economy.give(g.state, "lucky-egg")
        val held = g.useItem("lucky-egg", "t0")
        assertTrue(held.ok); assertTrue(held.events.none { it is GameEvent.SwitchHit })

        val full = game()
        val main = full.state.main!!
        main.hp = StatCalc.maxHp(data, main)
        val r = full.useItem("potion", main.uid)
        assertFalse(r.ok); assertTrue(r.events.isEmpty())
        assertEquals(3, full.state.count("potion"))
    }

    @Test fun anEvolutionItemOnTheMainInAFightTakesTheTurnAfterTheEvolution() {
        val g = game()
        g.state.main!!.bondPoints = 125
        Economy.give(g.state, "thunder-stone")
        val r = g.useItem("thunder-stone", "t0")
        assertTrue(r.message, r.ok)
        val evolved = r.events.indexOfFirst { it is GameEvent.Evolved }
        val marker = r.events.indexOfFirst { it is GameEvent.SwitchHit }
        assertTrue("${r.events}", evolved in 0 until marker)
        assertEquals("thunder-stone", (r.events[marker] as GameEvent.SwitchHit).item)
    }

    @Test fun ballsAndEscapeItemsKeepTheirOwnRules() {
        val g = game()
        Economy.give(g.state, "poke-ball")
        assertFalse(g.useItem("poke-ball", "t0").ok)
        Economy.give(g.state, "poke-doll")
        assertFalse(g.useItem("poke-doll", "t0").ok)
        assertEquals(1, g.state.count("poke-ball")); assertEquals(1, g.state.count("poke-doll"))
    }

    @Test fun theToastNeverSaysFreeHitOrTurnCost() {
        val g = game()
        val m = g.useItem("potion", "t0").message
        for (bad in listOf("free hit", "costs your turn", "skips your move")) assertFalse(m, m.contains(bad))
    }
}
