package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.Abilities
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

/** R17C-9a: what the abilities keep in a fight (a traced ability, a lit Flash Fire, Speed Boost's count) is saved with it; an old save has none and loads. */
class R17C9aSaveTest {
    private fun mon(i: Int) = Mon(
        uid = "u$i", species = 100 + i, name = "Mon$i", level = 10, xp = 0, ivs = Stats(15, 15, 15, 15, 15, 15), evs = Stats.ZERO,
        nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Trace", moves = mutableListOf("tackle"), hp = 20,
    )

    @Test fun theAbilityStateRoundTrips() {
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += mon(1); s.team += "u1"
        val player = BattleSide(volatiles = mutableMapOf("ab.flashfire" to 1, "ab.rounds" to 3), notes = mutableMapOf(Abilities.OVERRIDE to "intimidate"))
        s.battle = Battle(wild = mon(9), tier = Tier.NORMAL, player = player)
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals(mapOf("ab.flashfire" to 1, "ab.rounds" to 3), back.player.volatiles)
        assertEquals(mapOf(Abilities.OVERRIDE to "intimidate"), back.player.notes)
        assertEquals(emptyMap<String, String>(), back.enemy.notes)
    }
}
