package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * R13 Part C: an item used on the Main in a fight takes one turn (one dot): the item works, then the wild one attacks the Main once at
 * Tuning.SWITCH_HIT_POWER, marked like a switch's attack ([GameEvent.SwitchHit] with the item). Free at the same moments as a switch;
 * never for another Pokémon, a held item, a ball or an escape item. Made-up species only (TestData.world).
 */
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
        val healedTo = main.hp + 20
        val r = g.useItem("potion", main.uid)
        assertTrue(r.ok)
        assertEquals(2, g.state.count("potion"))
        val marker = r.events.of<GameEvent.SwitchHit>().single()
        assertEquals(GameEvent.SwitchHit("Ratty", main.name, healedTo, StatCalc.maxHp(data, main), "potion"), marker)
        val attacks = r.events.of<GameEvent.Attack>()
        assertEquals("the wild one attacks once, the Main doesn't", 1, attacks.size)
        assertFalse(attacks.single().byPlayer)
        assertEquals(healedTo - attacks.single().damage, main.hp)
        assertTrue(r.message, r.message.startsWith("Potion healed ${main.name} by 20 HP — the wild Ratty"))
        // one turn, not a round: the round count and the answers waiting in the round stay
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
        assertTrue(n.ok); assertTrue(n.events.none { it is GameEvent.SwitchHit })

        val fresh = game(rounds = 0, answers = 0)   // nothing answered in this fight yet: like a switch, no attack
        assertTrue(fresh.useItem("potion", "t0").events.none { it is GameEvent.SwitchHit })

        val oneAnswer = game(rounds = 0, answers = 1)   // one answer waiting in the round: it counts
        assertEquals(1, oneAnswer.useItem("potion", "t0").events.of<GameEvent.SwitchHit>().size)

        val faintedWild = game()
        faintedWild.state.battle!!.wild.hp = 0
        assertTrue(faintedWild.useItem("potion", "t0").events.none { it is GameEvent.SwitchHit })
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
