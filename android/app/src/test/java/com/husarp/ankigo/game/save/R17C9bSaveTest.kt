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

/** R17C-9b: what the rarer abilities keep in a fight (rounds since entering, Truant, Disguise, Gorilla Tactics' lock, Color Change, a given ability) is saved with it; an old save has none and loads. */
class R17C9bSaveTest {
    private fun mon(i: Int) = Mon(
        uid = "u$i", species = 100 + i, name = "Mon$i", level = 10, xp = 0, ivs = Stats(15, 15, 15, 15, 15, 15), evs = Stats.ZERO,
        nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Truant", moves = mutableListOf("tackle"), hp = 20,
    )

    @Test fun theAbilityStateRoundTrips() {
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += mon(1); s.team += "u1"
        val player = BattleSide(
            volatiles = mutableMapOf("ab.turns" to 3, "ab.truant" to 1, "ab.busted" to 1, "ab.gas" to 1),
            notes = mutableMapOf(Abilities.OVERRIDE to "mummy", "ab.lock" to "watergun", "ab.color" to "WATER|NORMAL"),
        )
        s.battle = Battle(wild = mon(9), tier = Tier.NORMAL, player = player)
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals(mapOf("ab.turns" to 3, "ab.truant" to 1, "ab.busted" to 1, "ab.gas" to 1), back.player.volatiles)
        assertEquals(mapOf(Abilities.OVERRIDE to "mummy", "ab.lock" to "watergun", "ab.color" to "WATER|NORMAL"), back.player.notes)
        assertEquals(emptyMap<String, Int>(), back.enemy.volatiles)
    }
}
