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

/** R17C-5: Substitute: the cost, the hits it takes, what it stops, the drain / recoil of a hit it took. */
class R17C5SubstituteTest {
    private val data = R17C5.data
    private fun main(vararg m: String) = R17C5.main(*m)
    private fun wild(vararg m: String) = R17C5.wild(*m)
    private fun battle(w: Mon) = R17C5.battle(w)
    private fun round(m: Mon, b: Battle, mainIds: List<String>, wildIds: List<String>, rng: Random = Random(1), wildPower: Double = 1.0) =
        R17C5.playing(mainIds, wildIds) { R17C5.round(m, b, rng, wildPower = wildPower) }

    private fun sub(b: Battle, p: Boolean) = (if (p) b.player else b.enemy).volatiles[SideMoves.SUBSTITUTE]

    // ---------------------------------------------------------------- making one

    @Test fun itCostsAQuarterOfTheMaxHpAndHasThatMuchHp() {
        val m = main("substitute"); val b = battle(wild("splash"))
        val max = R17C5.maxHp(m)
        val e = round(m, b, listOf("substitute"), listOf("splash"))
        assertEquals(max - max / 4, m.hp)
        assertEquals(max / 4, sub(b, true))
        val start = e.effects().first { it.kind == EffectKind.VOLATILE && it.stat == "substitute" }
        assertEquals("Fastie put in a substitute!", start.text); assertEquals("Substitute", start.label)
        assertTrue("the HP loss is shown", e.kinds(EffectKind.RESIDUAL).any { it.amount == max / 4 })
        assertFalse(e.attacks().first { it.byPlayer }.failed)
    }

    @Test fun itFailsWithOneUpAndWithTooLittleHp() {
        val m = main("substitute"); val b = battle(wild("splash"))
        round(m, b, listOf("substitute"), listOf("splash"))
        val hp = m.hp
        val e = round(m, b, listOf("substitute"), listOf("splash"))
        assertTrue(e.lines().contains("Fastie already has a substitute!"))
        assertEquals(hp, m.hp)

        val m2 = main("substitute"); val b2 = battle(wild("splash"))
        m2.hp = R17C5.maxHp(m2) / 4
        val e2 = round(m2, b2, listOf("substitute"), listOf("splash"))
        assertTrue(e2.lines().contains("But it does not have enough HP left to make a substitute!"))
        assertNull(sub(b2, true)); assertEquals(R17C5.maxHp(m2) / 4, m2.hp)

        val m3 = main("substitute"); val b3 = battle(wild("splash"))
        m3.hp = R17C5.maxHp(m3) / 4 + 1
        round(m3, b3, listOf("substitute"), listOf("splash"))
        assertEquals(1, m3.hp); assertEquals(R17C5.maxHp(m3) / 4, sub(b3, true))
    }

    // ---------------------------------------------------------------- taking hits

    @Test fun theFoesHitDamagesTheSubstituteNotThePokemon() {
        val m = main("splash"); val b = battle(wild("tackle"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        val hp = m.hp
        val e = round(m, b, listOf("splash"), listOf("tackle"))
        assertEquals(hp, m.hp)
        val hit = e.attacks().first { !it.byPlayer }
        assertEquals("the Attack event shows 0 damage", 0, hit.damage)
        assertFalse(hit.failed); assertFalse(hit.missed)
        assertTrue(e.lines().contains("The substitute took damage for Fastie!"))
        assertTrue(sub(b, true)!! < 1000)
    }

    @Test fun itBreaksAtZeroAndTheOverflowIsLost() {
        val m = main("splash"); val b = battle(wild("tackle"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 3
        val hp = m.hp
        val e = round(m, b, listOf("splash"), listOf("tackle"))
        assertEquals(hp, m.hp)
        assertNull(sub(b, true))
        val gone = e.effects().first { it.text == "Fastie's substitute faded!" }
        assertEquals(EffectKind.VOLATILE, gone.kind); assertEquals("Substitute broke", gone.label); assertEquals("substitute", gone.stat)
        assertFalse(e.lines().contains("The substitute took damage for Fastie!"))
        val e2 = round(m, b, listOf("splash"), listOf("tackle"))
        assertTrue("without it the next hit lands", m.hp < hp)
        assertTrue(e2.attacks().first { !it.byPlayer }.damage > 0)
    }

    @Test fun theLaterHitsOfAMultiHitMoveReachThePokemon() {
        val m = main("splash"); val b = battle(wild("bulletseed"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 8
        val hp = m.hp
        val e = round(m, b, listOf("splash"), listOf("bulletseed"))
        val hit = e.attacks().first { !it.byPlayer }
        assertEquals(4, hit.hits)
        assertTrue(m.hp < hp)
        assertEquals("the Attack event's damage is what the Pokémon lost", hp - m.hp, hit.damage)
        assertEquals(1, e.lines().count { it == "Fastie's substitute faded!" })
        assertNull(sub(b, true))
    }

    @Test fun theMainsHitsGoIntoTheWildOnesSubstituteToo() {
        val m = main("tackle"); val w = wild("splash"); val b = battle(w)
        b.enemy.volatiles[SideMoves.SUBSTITUTE] = 1000
        val hp = w.hp
        val e = round(m, b, listOf("tackle"), listOf("splash"))
        assertEquals(hp, w.hp)
        assertEquals(0, e.attacks().first { it.byPlayer }.damage)
        assertTrue(sub(b, false)!! < 1000)
        assertTrue(e.lines().contains("The substitute took damage for Slowpoke!"))
    }

    @Test fun theWildOneCanMakeOne() {
        val m = main("tackle"); val w = wild("substitute"); val b = battle(w)
        val max = R17C5.maxHp(w)
        round(m, b, listOf("splash"), listOf("substitute"))
        assertEquals(max / 4, sub(b, false))
        assertEquals(max - max / 4, w.hp)
    }

    // ---------------------------------------------------------------- what it stops

    @Test fun theFoesStatusMovesFailButSoundMovesGoThrough() {
        val m = main("splash"); val b = battle(wild("thunderwave"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        val e = round(m, b, listOf("splash"), listOf("thunderwave"))
        assertNull(b.player.status)
        assertTrue(e.attacks().first { !it.byPlayer }.failed)
        assertTrue(e.lines().contains("But it failed!"))
        assertEquals(1000, sub(b, true))

        val m2 = main("splash"); val b2 = battle(wild("growl"))
        b2.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        round(m2, b2, listOf("splash"), listOf("growl"))
        assertEquals("a sound move reaches the Pokémon", -1, b2.player.boosts[0])
    }

    @Test fun aSoundHitDamagesThePokemonAndLeavesTheSubstitute() {
        val m = main("splash"); val b = battle(wild("hypervoice"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        val hp = m.hp
        round(m, b, listOf("splash"), listOf("hypervoice"))
        assertTrue(m.hp < hp)
        assertEquals(1000, sub(b, true))
    }

    @Test fun theSecondariesOfAHitOnTheSubstituteDoNotReachThePokemon() {
        val m = main("splash"); val b = battle(wild("ember"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        round(m, b, listOf("splash"), listOf("ember"), R17C5.low)
        assertNull("no burn behind the substitute", b.player.status)
        val m2 = main("splash"); val b2 = battle(wild("psychic"))
        b2.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        round(m2, b2, listOf("splash"), listOf("psychic"), R17C5.low)
        assertEquals(0, b2.player.boosts[3])
    }

    @Test fun aHitThatBreaksItHasNoSecondaryEitherButTheNextOneHas() {
        val m = main("splash"); val b = battle(wild("ember"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1
        round(m, b, listOf("splash"), listOf("ember"), R17C5.low)
        assertNull(sub(b, true)); assertNull(b.player.status)
        round(m, b, listOf("splash"), listOf("ember"), R17C5.low)
        assertEquals("brn", b.player.status)
    }

    @Test fun theFoesFlinchAndConfusionDoNotReachItEither() {
        val m = main("splash"); val b = battle(wild("waterpulse"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        round(m, b, listOf("splash"), listOf("waterpulse"), R17C5.low)
        assertFalse("confusion" in b.player.volatiles)
        val m2 = main("splash"); val b2 = battle(wild("waterpulse"))
        round(m2, b2, listOf("splash"), listOf("waterpulse"), R17C5.low)
        assertTrue("without a substitute the same hit confuses", "confusion" in b2.player.volatiles)
    }

    @Test fun theUsersOwnEffectsAreNotStopped() {
        // the substitute of the TARGET does not stop the user's own stat rise (Rapid Spin's Speed)
        val m = main("rapidspin"); val w = wild("splash"); val b = battle(w)
        b.enemy.volatiles[SideMoves.SUBSTITUTE] = 1000
        round(m, b, listOf("rapidspin"), listOf("splash"), R17C5.low)
        assertEquals(1, b.player.boosts[4])
    }

    // ---------------------------------------------------------------- drain and recoil count what the substitute took

    @Test fun drainHealsFromTheDamageTheSubstituteTook() {
        val m = main("splash"); val w = wild("absorb"); val b = battle(w)
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        w.hp = R17C5.maxHp(w) - 200
        val before = w.hp
        round(m, b, listOf("splash"), listOf("absorb"))
        val taken = 1000 - sub(b, true)!!
        assertTrue(taken > 1)
        assertEquals(before + maxOf(1, taken / 2), w.hp)
    }

    @Test fun recoilCountsItToo() {
        val m = main("splash"); val w = wild("doubleedge"); val b = battle(w)
        b.player.volatiles[SideMoves.SUBSTITUTE] = 1000
        val before = w.hp
        val e = round(m, b, listOf("splash"), listOf("doubleedge"))
        val taken = 1000 - sub(b, true)!!
        assertEquals(before - maxOf(1, taken * 33 / 100), w.hp)
        assertTrue(e.lines().contains("Slowpoke is damaged by recoil!"))
    }

    // ---------------------------------------------------------------- leaving, the move choice, the text

    @Test fun switchingOutLosesTheSubstitute() {
        val m = main("splash"); val b = battle(wild("splash"))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 10
        b.switchOutMain(data, m)
        assertNull(sub(b, true))
    }

    @Test fun smartChoiceDoesNotMakeASecondOneNorOneItCannotPayFor() {
        val m = main("tackle"); val b = battle(wild("splash"))
        val move = data.move("substitute")!!
        assertEquals(true, SideMoves.useful(SimpleView.of(data, b, m), true, move))
        b.player.volatiles[SideMoves.SUBSTITUTE] = 5
        assertEquals(false, SideMoves.useful(SimpleView.of(data, b, m), true, move))
        b.player.volatiles.clear()
        m.hp = R17C5.maxHp(m) / 4
        assertEquals(false, SideMoves.useful(SimpleView.of(data, b, m), true, move))
    }

    @Test fun theTextSaysWhatItCostsAndStops() {
        val t = MoveText.describe(data.move("substitute")!!)
        assertTrue(t, "a quarter of your max HP" in t && "not sound moves" in t && t.endsWith("."))
    }
}
