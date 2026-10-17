package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildInfoTest {
    private val data = TestData.world()
    private fun battle(species: Int = 92, moves: List<String> = listOf("lick", "hypnosis", "shadowball", "tackle"), tier: Tier = Tier.NORMAL) =
        Battle(TestData.mon(data, species, 12, moves, uid = "wild"), tier)

    private fun attack(byPlayer: Boolean, move: String) = GameEvent.Attack(
        byPlayer, if (byPlayer) "Me" else "Spooky", if (byPlayer) "Spooky" else "Me", move, Type.GHOST, Category.PHYSICAL,
        5, 1, 1.0, false, false, false, 10, 20, 10, 20,
    )

    @Test fun showsOnlyTheFactsAPlayerMaySee() {
        val b = battle(tier = Tier.LEGENDARY)
        b.wild.shiny = true
        b.wild.gender = 'F'
        val s = TestData.state(data)
        val i = WildInfo.of(data, s, b, emptySet())!!
        assertEquals("Spooky", i.name)
        assertEquals(92, i.dexNum)
        assertEquals(12, i.level)
        assertEquals('F', i.gender)
        assertTrue(i.shiny)
        assertEquals(listOf(Type.GHOST, Type.POISON), i.types)
        assertEquals(Tier.LEGENDARY, i.tier)
        assertEquals(data.sp(92).base, i.base) // base stats of the species, not the wild one's own (IV-based) stats
        assertEquals(TypeMatchups.of(listOf(Type.GHOST, Type.POISON)), i.matchups)
        // by construction there is no field for IVs, nature, EVs, ability or held item
        val fields = WildInfo::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it in setOf("ivs", "evs", "nature", "ability", "heldItem") })
    }

    @Test fun unusedMovesStayHiddenAsQuestionMarks() {
        val i = WildInfo.of(data, TestData.state(data), battle(), setOf("Lick"))!!
        assertEquals(4, i.moves.size)
        assertEquals(WildMoveSlot("lick", "Lick"), i.moves[0])
        assertTrue(i.moves.drop(1).all { it.id == null && it.name == null })
        val none = WildInfo.of(data, TestData.state(data), battle(), emptySet())!!
        assertTrue(none.moves.all { it.id == null })
        assertEquals(4, none.moves.size)
    }

    @Test fun usedMovesComeFirstAndALoneStruggleShowsNothing() {
        val i = WildInfo.of(data, TestData.state(data), battle(), setOf("Shadow Ball", "Hypnosis", "Struggle"))!!
        assertEquals(listOf("Hypnosis", "Shadow Ball", null, null), i.moves.map { it.name }) // wild.moves order, then "?"
        assertEquals(4, i.moves.size)
    }

    @Test fun caughtOrJustSeen() {
        val s = TestData.state(data) // knows species 25 only
        assertEquals(WildDex.SEEN, WildInfo.of(data, s, battle(), emptySet())!!.dex)
        s.caught += 92
        assertEquals(WildDex.CAUGHT, WildInfo.of(data, s, battle(), emptySet())!!.dex)
    }

    @Test fun unknownSpeciesGivesNothing() {
        val b = battle()
        b.wild.species = 99999
        assertNull(WildInfo.of(data, TestData.state(data), b, emptySet()))
    }

    @Test fun logKeepsTheWildOnesMovesOnly() {
        val b = battle()
        val log = WildMoveLog()
        log.observe(listOf(attack(true, "Tackle"), attack(false, "Lick"), attack(false, "Lick")), b)
        assertEquals(setOf("Lick"), log.used(b))
        log.observe(listOf(attack(false, "Hypnosis")), b)
        assertEquals(setOf("Lick", "Hypnosis"), log.used(b))
        assertTrue(log.used(battle()).isEmpty()) // another fight
        assertTrue(log.used(null).isEmpty())
    }

    @Test fun logStartsOverWithTheNextWildOne() {
        val first = battle()
        val log = WildMoveLog()
        log.observe(listOf(attack(false, "Lick")), first)
        val next = battle(19, listOf("tackle"))
        // the fight ends and the next wild one appears in one action; its first move comes after the Encounter
        log.observe(listOf(attack(false, "Hypnosis"), GameEvent.Defeated(92, "Spooky", 30),
            GameEvent.Encounter(19, "Ratty", 5, false, Tier.NORMAL, 20, 20, true, false), attack(false, "Tackle")), next)
        assertEquals(setOf("Tackle"), log.used(next))
        assertTrue(log.used(first).isEmpty())
    }

    @Test fun logForgetsAfterARestoreWithoutEvents() {
        val b = battle()
        val log = WildMoveLog()
        log.observe(listOf(attack(false, "Lick")), b)
        val restored = battle() // same wild after a restart: another object
        log.observe(listOf(GameEvent.Info("x")), restored)
        assertTrue(log.used(restored).isEmpty())
    }
}
