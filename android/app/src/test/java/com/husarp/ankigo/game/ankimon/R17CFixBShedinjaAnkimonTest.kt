package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.switchOutMain
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-FIX items 1 and 17.
 * 1: a Shedinja (base HP 1, 1 max HP) imported from Ankimon gets 1 HP, and the file the export writes has 1 in its stats / hp / current_hp
 *    and passes the export's own checks. (This is a Shedinja-rule change of the numbers written for such a Pokémon; every other row is the same.)
 * 17: an Ankimon import that changes the Main in a fight does not run the rule sets' onLeave (no Regenerator heal for the old Main), where
 *    a switch the player makes does.
 */
class R17CFixBShedinjaAnkimonTest {
    private val NOW = 1_800_000_000_000L
    private val data: GameData = TestData.data(
        TestData.world().species.values.toList() +
            TestData.species(292, "Shell", listOf(Type.BUG, Type.GHOST), TestData.stats(1, 90, 45, 30, 30, 40)),
    )

    private fun am(id: String, species: Int, main: Boolean = false, level: Int? = null) = AnkimonMon(
        individualId = id, isMain = main, id = species, name = null, nickname = null, level = level, xp = null, iv = emptyMap(), ev = emptyMap(),
        nature = null, gender = null, ability = null, attacks = emptyList(), shiny = false, heldItem = null, everstone = false,
        friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun sv(vararg mons: AnkimonMon, team: List<String> = emptyList()) =
        AnkimonSave(mons.toList(), team, emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())

    private fun merge(save: AnkimonSave, state: GameState = GameState(), useTeam: Boolean = true) =
        AnkimonMerge.merge(data, state, save, { null }, useTeam, NOW)

    // ---- item 1 ------------------------------------------------------------------------------------------------

    @Test fun anImportedShedinjaHasOneHp() {
        val state = GameState()
        merge(sv(am("s-1", 292, main = true, level = 40), team = listOf("s-1")), state)
        val m = state.main!!
        assertEquals(292, m.species)
        assertEquals(1, StatCalc.maxHp(data, m))
        assertEquals(1, m.hp)
    }

    @Test fun theExportWritesOneHpForAShedinjaAndPassesItsOwnChecks() {
        val state = TestData.state(data, 292, 40, listOf("tackle"))
        state.main!!.evs = Stats.ZERO.copy(hp = 252)
        val result = AnkimonExport.build(data, state, null, AnkimonRefs.NONE, NOW)   // verify() runs inside build and would throw
        assertTrue(AnkimonExport.verify(result).isEmpty())
        val image = SqliteReader(result.bytes).image()
        val table = image.table("captured_pokemon")!!
        val col = table.columns.indexOf("data")
        val o = JSONObject(table.rows.map { it.values[col] as String }.single())
        assertEquals(292, o.getInt("id"))
        assertEquals(1, o.getJSONObject("stats").getInt("hp"))
        assertEquals(1, o.getInt("hp"))
        assertEquals(1, o.getInt("current_hp"))
        assertEquals(40, o.getInt("level"))
        assertEquals(1, o.getJSONObject("base_stats").getInt("hp"))   // Ankimon's own base stats are untouched
    }

    // ---- item 17 -----------------------------------------------------------------------------------------------

    /** Two Pokémon in the team; the first (an Ankimon id "old", ability Regenerator) is out against a wild Birdie, hurt to 5 HP. */
    private fun fight(): Pair<GameState, Battle> {
        val state = TestData.state(data, 25, 30, listOf("tackle"))
        val old = state.main!!
        old.ability = "Regenerator"; old.ankimonId = "old"
        old.hp = 5
        val wild = TestData.mon(data, 16, 5, uid = "wild")
        val battle = Battle(wild, Tier.NORMAL, startedAt = NOW, player = BattleSide(intArrayOf(2, 0, 0, 0, 0, 0, 0), "par", 0))
        state.battle = battle
        return state to battle
    }

    @Test fun anImportThatChangesTheMainDoesNotRunRegenerator() {
        val (state, battle) = fight()
        val old = state.main!!
        val side = battle.player
        merge(sv(am("new", 1, main = true, level = 12), team = listOf("new")), state)
        assertFalse("the import made another Pokémon the main", state.main === old)
        assertEquals("no Regenerator heal from an import", 5, old.hp)
        assertNotSame("the new main starts the fight fresh", side, state.battle!!.player)
        assertNull(state.battle!!.player.status)
    }

    @Test fun aSwitchTheGameMakesStillRunsRegenerator() {
        // the control: the same Pokémon and ability, left through the switch path, is healed by a third of its max HP
        val (state, battle) = fight()
        val old = state.main!!
        val max = StatCalc.maxHp(data, old)
        battle.switchOutMain(data, old)
        assertTrue("Regenerator healed ${old.hp}", old.hp > 5 && old.hp <= max)
    }
}
