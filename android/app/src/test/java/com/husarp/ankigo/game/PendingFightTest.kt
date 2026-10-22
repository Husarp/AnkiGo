package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN 191: the fight events made on a screen that is not a battle wait in [PendingFight] and are taken ONCE. Pure Kotlin: the
 * list (add -> take once -> empty, another fight drops them, the cap, an append of the same fight) and what is kept of an action
 * ([PendingFight.fightPart]) and drawn before the hit ([PendingFight.hpBeforeHit]).
 */
class PendingFightTest {
    private fun hit(hp: Int = 30, mon: String = "Pikachu", item: String? = null) = GameEvent.SwitchHit("Wild Ratty", mon, hp, 60, item)
    private fun attack(damage: Int = 12, hp: Int = 18) = GameEvent.Attack(
        byPlayer = false, user = "Wild Ratty", target = "Pikachu", move = "Tackle", moveType = null, category = Category.PHYSICAL, damage = damage,
        hits = 1, effectiveness = 1.0, crit = false, missed = false, failed = false, targetHp = hp, targetMaxHp = 60, userHp = 40, userMaxHp = 40,
    )
    private val changed = GameEvent.MainChanged("t2", "Pikachu")

    // ---- the list

    @Test fun addedEventsAreTakenOnceAndNeverTwice() {
        val p = PendingFight()
        assertTrue(p.isEmpty)
        p.add("wild-1", listOf(changed, hit(), attack()))
        assertEquals(3, p.peek("wild-1").size)
        assertEquals("peeking takes nothing", 3, p.peek("wild-1").size)
        assertEquals(listOf(changed, hit(), attack()), p.take("wild-1"))
        assertTrue("the second take finds nothing", p.take("wild-1").isEmpty())
        assertTrue(p.isEmpty)
    }

    @Test fun anotherFightDropsThemAndNothingComesBack() {
        val p = PendingFight()
        p.add("wild-1", listOf(changed, hit()))
        assertTrue("peek for another fight shows nothing", p.peek("wild-2").isEmpty())
        assertTrue(p.take("wild-2").isEmpty())
        assertTrue("and they are gone for the old fight too", p.take("wild-1").isEmpty())
        p.add("wild-1", listOf(hit()))
        assertTrue("no battle at all: dropped", p.take(null).isEmpty())
        assertTrue(p.isEmpty)
    }

    @Test fun anActionsEventsAreAppendedInOrderAndANewFightStartsOver() {
        val p = PendingFight()
        p.add("wild-1", listOf(hit(30)))
        p.add("wild-1", listOf(attack()))
        assertEquals(listOf(hit(30), attack()), p.peek("wild-1"))
        p.add("wild-2", listOf(hit(20)))
        assertTrue("the old fight's events no longer lead to the saved game", p.peek("wild-1").isEmpty())
        assertEquals(listOf(hit(20)), p.take("wild-2"))
    }

    @Test fun clearForgetsEverything() {
        val p = PendingFight()
        p.add("wild-1", listOf(hit()))
        p.clear()
        assertTrue(p.isEmpty)
        assertTrue(p.take("wild-1").isEmpty())
    }

    @Test fun emptyAddsChangeNothing() {
        val p = PendingFight()
        p.add("wild-1", listOf(hit()))
        p.add("wild-2", emptyList())
        assertEquals(1, p.peek("wild-1").size)
    }

    @Test fun theListKeepsTheLastEventsOnly() {
        val p = PendingFight(cap = 5)
        p.add("wild-1", (1..4).map { hit(it) })
        p.add("wild-1", (5..9).map { hit(it) })
        assertEquals((5..9).map { hit(it) }, p.take("wild-1"))
        val big = PendingFight()
        big.add("w", (1..100).map { hit(it) })
        val kept = big.take("w")
        assertEquals(PendingFight.CAP, kept.size)
        assertEquals(hit(100), kept.last())
        assertEquals(hit(100 - PendingFight.CAP + 1), kept.first())
    }

    // ---- what is kept of an action

    @Test fun aSwitchAndTheWildOnesAttackAreKept() {
        val kept = PendingFight.fightPart(listOf(changed, hit(), attack(), GameEvent.Effect(true, "Pikachu is poisoned", 18, 60, status = "psn")))
        assertEquals(4, kept!!.size)
        assertTrue(kept[0] is GameEvent.MainChanged && kept[1] is GameEvent.SwitchHit && kept[2] is GameEvent.Attack && kept[3] is GameEvent.Effect)
    }

    @Test fun anItemUsedOnTheMainKeepsOnlyTheFightPartNotTheDialogEvents() {
        val kept = PendingFight.fightPart(listOf(GameEvent.Info("Pikachu evolved"), GameEvent.Evolved("t1", "Pikachu", "Raichu", 26), hit(40, item = "potion"), attack()))
        assertEquals(listOf(hit(40, item = "potion"), attack()), kept)
    }

    @Test fun aFreeSwitchWithoutTheHitIsKeptToo() {
        assertEquals(listOf(changed), PendingFight.fightPart(listOf(changed)))
    }

    @Test fun nothingToPlayIsNull() {
        assertNull(PendingFight.fightPart(emptyList()))
        assertNull("a rename, a favourite: no fight event", PendingFight.fightPart(listOf(GameEvent.Info("x"))))
        assertNull(PendingFight.fightPart(listOf(attack())))
    }

    @Test fun anActionThatEndedTheFightIsNotKept() {
        val enc = GameEvent.Encounter(19, "Ratty", 10, false, Tier.NORMAL, 20, 20, false, false, "wild-2")
        assertNull("the main fainted and was healed, the wild one is lost", PendingFight.fightPart(listOf(changed, hit(), attack(), GameEvent.Fainted(true, "Pikachu"), GameEvent.MainHealed("t2", "Pikachu", 60), enc)))
        assertNull("the wild one was defeated by the recoil", PendingFight.fightPart(listOf(hit(), attack(), GameEvent.Defeated(19, "Wild Ratty", 12))))
        assertNull("a ball", PendingFight.fightPart(listOf(GameEvent.BallThrow("poke-ball", 40, true, 3, 1), hit())))
        assertNull("a new wild one", PendingFight.fightPart(listOf(hit(), enc)))
    }

    // ---- what moves the fight on by itself

    @Test fun anAnswerOrAnOwnSwitchOnABattleHostMovesTheFightOn() {
        assertTrue(PendingFight.movesFightOn(listOf(GameEvent.Counted(3, 10, 5, 2, false))))
        assertTrue(PendingFight.movesFightOn(listOf(changed)))
        assertTrue(PendingFight.movesFightOn(listOf(hit())))
        assertTrue(PendingFight.movesFightOn(listOf(GameEvent.BallThrow("poke-ball", 40, false, 3, 1))))
        assertTrue(!PendingFight.movesFightOn(listOf(GameEvent.Info("x"), GameEvent.Cash(5, 100))))
        assertTrue(!PendingFight.movesFightOn(emptyList()))
    }

    // ---- the HP drawn before the hit

    @Test fun theHpBeforeTheHitIsTheFirstMarkerOfThatPokemon() {
        val events = listOf(changed, hit(30, "Pikachu"), attack(), hit(18, "Pikachu"), attack(10, 8))
        val h = PendingFight.hpBeforeHit(events, "Pikachu")
        assertNotNull(h)
        assertEquals("the first hit's HP: where the Pokémon stood when it came in", 30, h!!.hp)
        assertEquals(60, h.maxHp)
        assertNull("another Pokémon: nothing to override", PendingFight.hpBeforeHit(events, "Raichu"))
        assertNull(PendingFight.hpBeforeHit(emptyList(), "Pikachu"))
    }
}
