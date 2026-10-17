package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chosen look (R3-03) and Ankimon: kept on re-import, never written into the Ankimon file. */
class LookAnkimonTest {
    private val NOW = 1_800_000_000_000L
    private val data: GameData = TestData.world()

    private val refs = AnkimonRefs.fromFiles { name ->
        when (name) {
            "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\n4,poke-ball,34,200,,\n"
            "moves.json" -> """{"tackle":{"num":33}}"""
            "pokemon.csv" -> "id,identifier,species_id,height,weight,base_experience,order,is_default\n"
            else -> null
        }
    }

    private fun am(level: Int) = AnkimonMon(
        individualId = "look-1", isMain = true, id = 25, name = "Sparkmouse", nickname = null, level = level, xp = 0,
        iv = emptyMap(), ev = emptyMap(), nature = null, gender = null, ability = null, attacks = listOf("tackle"), shiny = false,
        heldItem = null, everstone = false, friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun sv(level: Int) = AnkimonSave(listOf(am(level)), emptyList(), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())

    private fun merge(state: GameState, level: Int) = AnkimonMerge.merge(data, state, sv(level), { null }, false, NOW)

    @Test fun reImportKeepsTheChosenLook() {
        val state = GameState()
        merge(state, 10)
        val mon = state.mons.single()
        mon.look = 172
        val r = merge(state, 25) // the file is further on: the Mon is upgraded in place
        assertEquals(1, r.upgraded)
        assertTrue(state.mons.single() === mon)
        assertEquals(172, mon.look)
        merge(state, 25)
        assertEquals(172, state.mons.single().look)
    }

    private fun exported(look: Int?): String {
        val state = GameState()
        merge(state, 10)
        val img = AnkimonSchema.fresh()
        val row = """{"name":"sparkmouse","id":25,"level":10,"xp":0,"attacks":["tackle"],"individual_id":"look-1"}"""
        img.table("captured_pokemon")!!.insert(mapOf("individual_id" to "look-1", "is_main" to 1L, "data" to row))
        state.mons.single().apply { level = 40; this.look = look } // something that makes the row be rewritten
        AnkimonExport.edit(data, state, img, refs, NOW, false)
        val t = img.table("captured_pokemon")!!
        return t.rows.single().values[t.columns.indexOf("data")] as String
    }

    @Test fun exportOfAMonWithALookIsTheSameRowAsWithout() {
        val plain = exported(null)
        val looked = exported(172)
        assertTrue(plain.contains("\"level\": 40") || plain.contains("\"level\":40"))
        assertEquals(plain, looked)
        assertTrue(!looked.contains("\"look\""))
    }
}
