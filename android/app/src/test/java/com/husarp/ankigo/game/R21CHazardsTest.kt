package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R21CHazardsTest {
    private val extra = (Hazards.handles).associateWith { Move(it, it, Type.NORMAL, Category.STATUS, 0, null) }
    private val data = TestData.data(listOf(
        TestData.species(19), TestData.species(25), TestData.species(16, types = listOf(Type.FIRE, Type.FLYING)),
        TestData.species(92, types = listOf(Type.GHOST, Type.POISON)), TestData.species(81, types = listOf(Type.STEEL)),
    ), TestData.MOVES + extra)
    private fun mon(id: Int = 19, uid: String = "main") = Mon(uid, id, "Test$id", 30, 0, Stats.ZERO, Stats.ZERO,
        Nature.entries.first(), 'N', false, "Testability", mutableListOf("tackle"), 1).also { it.hp = StatCalc.maxHp(data, it) }
    private fun battle() = Battle(mon(25, "wild"), Tier.NORMAL)
    private fun round(m: Mon, b: Battle) = BattleEngine.Round(data, b, m, 1.0, 1.0, Random(1))

    @Test fun pickedMovesExecuteThroughTheRealEngineAndDescribeTheirEffects() {
        for (id in Hazards.handles) {
            val m = mon(); val b = battle()
            b.wild.moves.clear(); b.wild.moves.add(id)
            val events = BattleEngine.playRound(data, b, m, 1.0, true, Random(1), mainActs = false)
            val attack = events.filterIsInstance<GameEvent.Attack>().single()
            assertFalse("$id executes", attack.failed)
            assertFalse("$id is described", MoveText.describe(extra.getValue(id)).contains("Never used"))
            if (id in Hazards.hazards) assertEquals(1, b.field.player[id])
            else assertNotNull("$id traps", b.player.volatiles[Hazards.TRAP])
        }
    }

    @Test fun hazardsStackToTheirCapsAndAreSelectable() {
        val m = mon(); val b = battle(); val r = round(m, b)
        for ((id, cap) in Hazards.hazards) {
            assertFalse(id in CoreRules.NEVER_PICKED)
            repeat(cap) { assertEquals(true, Hazards.statusMove(r, true, extra.getValue(id))) }
            assertEquals(false, Hazards.statusMove(r, true, extra.getValue(id)))
            assertEquals(cap, b.field.enemy[id])
            assertEquals(false, Hazards.useful(r, true, extra.getValue(id)))
        }
    }

    @Test fun spikesDamageUsesLayersAndDoesNotExpire() {
        for ((layers, divisor) in listOf(1 to 8, 2 to 6, 3 to 4)) {
            val m = mon(); val b = battle(); val hp = m.hp; val r = round(m, b)
            b.field.player["spikes"] = layers
            Hazards.onEnter(r, true)
            assertEquals(hp - hp / divisor, m.hp)
            repeat(10) { Hazards.endOfRound(r) }
            assertEquals(layers, b.field.player["spikes"])
            assertEquals(hp / divisor, r.events.filterIsInstance<GameEvent.Effect>().single().amount)
        }
    }

    @Test fun rocksUseDualTypeAndGroundHazardsMissFlyingAndLevitate() {
        val m = mon(16); val b = battle(); val hp = m.hp
        b.field.player.putAll(mapOf("stealthrock" to 1, "spikes" to 3, "toxicspikes" to 2, "stickyweb" to 1))
        Hazards.onEnter(round(m, b), true)
        assertEquals(hp - hp / 2, m.hp)
        assertNull(b.player.status); assertEquals(0, b.player.boosts[4])
        val lev = mon().also { it.ability = "Levitate" }; val lb = battle(); val lh = lev.hp
        lb.field.player["spikes"] = 3
        Hazards.onEnter(round(lev, lb), true)
        assertEquals(lh, lev.hp)
    }

    @Test fun toxicSpikesPoisonAbsorbAndRespectSteel() {
        for ((layers, status) in listOf(1 to "psn", 2 to "tox")) {
            val m = mon(); val b = battle(); b.field.player["toxicspikes"] = layers
            Hazards.onEnter(round(m, b), true); assertEquals(status, b.player.status)
        }
        for (id in listOf(92, 81)) {
            val b = battle(); b.field.player["toxicspikes"] = 2
            Hazards.onEnter(round(mon(id), b), true)
            assertNull(b.player.status)
            assertEquals(if (id == 92) null else 2, b.field.player["toxicspikes"])
        }
    }

    @Test fun webUsesExistingSpeedStageAndAbilityRules() {
        for ((ability, stage) in listOf("Testability" to -1, "Contrary" to 1, "Clear Body" to 0)) {
            val m = mon().also { it.ability = ability }; val b = battle(); b.field.player["stickyweb"] = 1
            Hazards.onEnter(round(m, b), true); assertEquals(stage, b.player.boosts[4])
        }
    }

    @Test fun pickedTrapsBlockThenExpireWithoutChangingBinding() {
        for (id in listOf("meanlook", "block", "spiderweb", "octolock", "fairylock")) {
            val m = mon(); val b = battle(); val r = round(m, b)
            assertEquals(true, Hazards.statusMove(r, false, extra.getValue(id)))
            assertTrue(Hazards.blocker(r)!!.contains("can't switch"))
            val turns = b.player.volatiles.getValue(Hazards.TRAP)
            repeat(turns) { Hazards.endOfRound(r) }
            assertNull(Hazards.blocker(r))
            if (id == "octolock") assertEquals(-3, b.player.boosts[1])
        }
        val b = battle(); b.player.volatiles["partiallytrapped"] = 4
        assertNull(Hazards.blocker(round(mon(), b)))
    }

    @Test fun abilitiesRespectGroundingGhostAndExpire() {
        for (ability in listOf("Arena Trap", "Shadow Tag")) {
            val m = mon(); val b = battle(); b.wild.ability = ability
            val r = round(m, b); Hazards.onEnter(r, false); b.enemy.entered = true
            assertNotNull(Hazards.blocker(r))
            repeat(3) { Hazards.endOfRound(r) }; assertNull(Hazards.blocker(r))
            val ghost = mon(92); assertNull(Hazards.blocker(round(ghost, b)))
        }
    }

    private fun game(): Game {
        val st = GameState(); val m = mon(); val bench = mon(25, "bench")
        st.mons.addAll(listOf(m, bench)); st.team.addAll(listOf(m.uid, bench.uid)); st.battle = battle()
        Hazards.statusMove(round(m, st.battle!!), false, extra.getValue("meanlook"))
        return Game(data, st, Random(1))
    }

    @Test fun everySwitchPathRefusesBeforeMutationAndSaveKeepsTheLock() {
        val g = game(); val st = g.state
        assertFalse(g.requestMain("bench").ok)
        assertFalse(g.requestMain("bench", false).ok)
        assertFalse(g.cycleMain(false).ok)
        assertFalse(g.moveInTeam("bench", -1, false).ok)
        assertFalse(g.toBox("main", false).ok)
        assertFalse(g.release("main", false).ok)
        assertEquals("main", st.main!!.uid); assertNull(st.pendingMain)
        st.battle!!.field.player["spikes"] = 2
        val loaded = SaveCodec.decode(SaveCodec.encode(st))
        assertEquals(2, loaded.battle!!.field.player["spikes"])
        assertEquals(3, loaded.battle!!.player.volatiles[Hazards.TRAP])
        assertTrue(Game(data, loaded).switchBlocker()!!.contains("meanlook"))
    }
    @Test fun freeSwitchAppliesHazardsImmediatelyOnceAndCanFaint() {
        val g = game(); val st = g.state; val b = st.battle!!
        b.player.volatiles.clear(); b.player.notes.clear()
        b.field.player["spikes"] = 3
        val incoming = st.mon("bench")!!; val hp = incoming.hp
        assertTrue(g.requestMain("bench", false).ok)
        assertEquals(hp - hp / 4, incoming.hp)
        // The next entry-hook pass must not deal the same entry damage twice.
        Hazards.onEnter(round(incoming, b), true)
        assertEquals(hp - hp / 4, incoming.hp)

        val dying = game(); val db = dying.state.battle!!
        db.player.volatiles.clear(); db.player.notes.clear(); db.field.player["spikes"] = 3
        dying.state.mon("bench")!!.hp = 1
        val result = dying.requestMain("bench", false)
        assertTrue(result.ok); assertNotSame(db, dying.state.battle)
        assertTrue(result.events.any { it is GameEvent.Fainted && it.player })
        assertTrue(result.events.any { it is GameEvent.MainHealed })
        assertTrue(result.message.contains("fainted on entry"))
        assertFalse(result.events.any { it is GameEvent.Attack })
    }

    @Test fun hazardPreviewAndActualLossRespectMagicGuard() {
        val m = mon().also { it.ability = "Magic Guard" }; val b = battle()
        b.field.player.putAll(mapOf("spikes" to 3, "stealthrock" to 1))
        val r = round(m, b); val hp = m.hp
        assertEquals(0, Hazards.entryDamage(r, true))
        Hazards.onEnter(r, true); assertEquals(hp, m.hp)
        m.ability = "Testability"
        val expected = Hazards.entryDamage(r, true)
        Hazards.onEnter(r, true); assertEquals(hp - expected, m.hp)
    }

    @Test fun abilityLocksAllowImmuneTargetsAndDoNotTickOnFreeHits() {
        val b = battle(); b.wild.ability = "Arena Trap"
        assertNull(Hazards.blocker(round(mon(16), b)))
        val m = mon(); val r = round(m, b)
        Hazards.onEnter(r, false); Hazards.afterFreeHit(r)
        assertEquals(3, b.player.volatiles[Hazards.TRAP])
        b.wild.ability = "Shadow Tag"; m.ability = "Shadow Tag"
        assertNull(Hazards.blocker(r))
    }

}
