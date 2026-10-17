package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bench-rest fraction (R3-02) and Ankimon: kept on re-import, never written into the Ankimon file. */
class RestFracAnkimonTest {
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
        individualId = "rest-1", isMain = true, id = 25, name = "Sparkmouse", nickname = null, level = level, xp = 0,
        iv = emptyMap(), ev = emptyMap(), nature = null, gender = null, ability = null, attacks = listOf("tackle"), shiny = false,
        heldItem = null, everstone = false, friendship = null, capturedDate = null, defeated = null, tier = null,
    )

    private fun sv(level: Int) = AnkimonSave(listOf(am(level)), emptyList(), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())

    private fun merge(state: GameState, level: Int) = AnkimonMerge.merge(data, state, sv(level), { null }, false, NOW)

    @Test fun reImportKeepsTheFraction() {
        val state = GameState()
        merge(state, 10)
        val mon = state.mons.single()
        mon.restFrac = 123
        merge(state, 25) // further on in the file: upgraded in place
        assertTrue(state.mons.single() === mon)
        assertEquals(123, mon.restFrac)
        merge(state, 25)
        assertEquals(123, state.mons.single().restFrac)
    }

    private fun exported(frac: Int): String {
        val state = GameState()
        merge(state, 10)
        val img = AnkimonSchema.fresh()
        val row = """{"name":"sparkmouse","id":25,"level":10,"xp":0,"attacks":["tackle"],"individual_id":"rest-1"}"""
        img.table("captured_pokemon")!!.insert(mapOf("individual_id" to "rest-1", "is_main" to 1L, "data" to row))
        state.mons.single().apply { level = 40; restFrac = frac } // something that makes the row be rewritten
        AnkimonExport.edit(data, state, img, refs, NOW, false)
        val t = img.table("captured_pokemon")!!
        return t.rows.single().values[t.columns.indexOf("data")] as String
    }

    @Test fun exportOfAMonWithAFractionIsTheSameRowAsWithout() {
        val plain = exported(0)
        val resting = exported(137)
        assertTrue(plain.contains("\"level\": 40") || plain.contains("\"level\":40"))
        assertEquals(plain, resting)
        assertTrue(!resting.contains("restFrac"))
    }
}
