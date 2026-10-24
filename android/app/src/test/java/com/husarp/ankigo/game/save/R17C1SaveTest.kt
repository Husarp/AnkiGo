package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleField
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17C-1: the new battle state (volatiles, notes, last move ..., the field) is saved, and a save from before loads with defaults. */
class R17C1SaveTest {
    private fun mon(i: Int) = Mon(
        uid = "u$i", species = 100 + i, name = "Mon$i", level = 10, xp = 0, ivs = Stats(15, 15, 15, 15, 15, 15), evs = Stats.ZERO,
        nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Static", moves = mutableListOf("tackle"), hp = 20,
    )

    private fun stateWith(b: Battle): GameState {
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += mon(1); s.team += "u1"
        s.battle = b
        return s
    }

    private fun busyBattle(): Battle {
        val player = BattleSide(
            boosts = intArrayOf(1, 0, 0, 0, 0, 0, -2), status = "brn", statusTurns = 2,
            volatiles = mutableMapOf("confusion" to 3, "substitute" to 25),
            notes = mutableMapOf("encore" to "tackle", "transform" to "Ditto, \"quoted\" ♥"),
            charging = "solarbeam", lastMove = "tackle", lastMoveFailed = true, actions = 4, entered = true, timesHit = 2,
        )
        val enemy = BattleSide(volatiles = mutableMapOf("flinch" to 1), notes = mutableMapOf("types" to "fire,flying"), lastMove = "ember", actions = 7, entered = true)
        val field = BattleField(
            weather = "rain", weatherTurns = 4, terrain = "grassy", terrainTurns = 2,
            rooms = mutableMapOf("trickroom" to 3, "gravity" to 5),
            player = mutableMapOf("reflect" to 5, "tailwind" to 2), enemy = mutableMapOf("wish" to 1),
        )
        return Battle(wild = mon(9), tier = Tier.NORMAL, player = player, enemy = enemy, rounds = 3, field = field)
    }

    @Test fun everyNewFieldRoundTrips() {
        val back = SaveCodec.decode(SaveCodec.encode(stateWith(busyBattle()))).battle!!
        val p = back.player; val e = back.enemy
        assertEquals(mapOf("confusion" to 3, "substitute" to 25), p.volatiles)
        assertEquals(mapOf("encore" to "tackle", "transform" to "Ditto, \"quoted\" ♥"), p.notes)
        assertEquals("solarbeam", p.charging); assertEquals("tackle", p.lastMove); assertTrue(p.lastMoveFailed)
        assertEquals(4, p.actions); assertTrue(p.entered); assertEquals(2, p.timesHit)
        assertEquals("brn", p.status); assertEquals(2, p.statusTurns); assertEquals(-2, p.boosts[6])
        assertEquals(mapOf("flinch" to 1), e.volatiles); assertEquals(mapOf("types" to "fire,flying"), e.notes)
        assertNull(e.charging); assertEquals("ember", e.lastMove); assertFalse(e.lastMoveFailed); assertEquals(7, e.actions)
        val f = back.field
        assertEquals("rain", f.weather); assertEquals(4, f.weatherTurns); assertEquals("grassy", f.terrain); assertEquals(2, f.terrainTurns)
        assertEquals(mapOf("trickroom" to 3, "gravity" to 5), f.rooms)
        assertEquals(mapOf("reflect" to 5, "tailwind" to 2), f.player); assertEquals(mapOf("wish" to 1), f.enemy)
    }

    @Test fun encodingIsStable() {
        val once = SaveCodec.encode(stateWith(busyBattle()))
        assertEquals(once, SaveCodec.encode(SaveCodec.decode(once)))
    }

    @Test fun aSaveFromBeforeLoadsWithEmptyDefaults() {
        val b = SaveCodec.decode(
            """{"schema":1,"mons":[{"uid":"a","species":1,"level":1}],
               |"battle":{"wild":{"uid":"w","species":1,"level":1},"rounds":2,
               |"player":{"boosts":[1,2,0,0,0,0,0],"status":"par","statusTurns":1,"charged":["solarbeam"]},
               |"enemy":{"boosts":[0,0,0,0,0,0,0]}}}""".trimMargin(),
        ).battle!!
        for (side in listOf(b.player, b.enemy)) {
            assertTrue(side.volatiles.isEmpty()); assertTrue(side.notes.isEmpty())
            assertNull(side.charging); assertNull(side.lastMove); assertFalse(side.lastMoveFailed)
            assertEquals(0, side.actions); assertFalse(side.entered); assertEquals(0, side.timesHit)
        }
        assertEquals("par", b.player.status); assertEquals(1, b.player.statusTurns); assertEquals(2, b.rounds)
        assertNull(b.field.weather); assertNull(b.field.terrain); assertEquals(0, b.field.weatherTurns)
        assertTrue(b.field.rooms.isEmpty()); assertTrue(b.field.player.isEmpty()); assertTrue(b.field.enemy.isEmpty())
        assertNull("an old save's charged list is ignored (R17C-3: the old charge rule is gone)", b.player.charging)
    }

    @Test fun damagedEntriesAreLeftOutNotFatal() {
        val b = SaveCodec.decode(
            """{"schema":1,"mons":[{"uid":"a","species":1,"level":1}],
               |"battle":{"wild":{"uid":"w","species":1,"level":1},
               |"player":{"volatiles":{"confusion":2,"bad":"x","nul":null},"notes":{"a":"b","n":null},"actions":-3,"timesHit":-1},
               |"field":{"weather":"sun","weatherTurns":-5,"rooms":{"gravity":"five","trickroom":2}}}}""".trimMargin(),
        ).battle!!
        assertEquals(mapOf("confusion" to 2), b.player.volatiles)
        assertEquals(mapOf("a" to "b"), b.player.notes)
        assertEquals(0, b.player.actions); assertEquals(0, b.player.timesHit)
        assertEquals("sun", b.field.weather); assertEquals(0, b.field.weatherTurns)
        assertEquals(mapOf("trickroom" to 2), b.field.rooms)
    }
}
