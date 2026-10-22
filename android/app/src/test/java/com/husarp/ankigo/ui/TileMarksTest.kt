package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.ankimon.AnkimonExport
import com.husarp.ankigo.game.ankimon.AnkimonRefs
import com.husarp.ankigo.game.ankimon.AnkimonSchema
import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** R4-V5: the marks of a Box tile (only six kinds), NEW until opened (persisted), the two new Box filters, the tile sprite scale. */
class TileMarksTest {
    private val data = TestData.world()
    private fun ms(h: Int) = LocalDateTime.of(2026, 10, 14, h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = ms(12)

    @Test fun onlyTheSixKindsExistAndInOrder() {
        assertEquals(listOf("NEW", "FAVOURITE", "SHINY", "IN_TEAM", "MAIN", "EXP_SHARE"), TileMark.entries.map { it.name })
        val st = TestData.state(data, 19, 10)
        val m = st.mons[0]
        m.caughtAt = now; m.favourite = true; m.shiny = true; m.heldItem = "lucky-egg"; m.hp = 0
        st.settings.xpShareUid = m.uid
        // held item, fainted, can evolve: none of them is a tile mark; the Exp. Share holder is the sixth
        assertEquals(listOf(TileMark.NEW, TileMark.FAVOURITE, TileMark.SHINY, TileMark.IN_TEAM, TileMark.MAIN, TileMark.EXP_SHARE), TileMarks.of(st, listOf(m), now))
    }

    @Test fun expShareMarksOnlyTheHolderAndAGroupWithTheHolder() {
        val st = TestData.state(data, 19, 10)
        val holder = TestData.mon(data, 16, 10, uid = "h"); val other = TestData.mon(data, 16, 11, uid = "o")
        st.mons += holder; st.mons += other
        assertTrue(TileMark.EXP_SHARE !in TileMarks.of(st, listOf(holder), now)) // nobody holds it yet
        st.settings.xpShareUid = "h"
        assertEquals(listOf(TileMark.EXP_SHARE), TileMarks.of(st, listOf(holder), now))
        assertTrue(TileMarks.of(st, listOf(other), now).isEmpty())
        assertEquals(listOf(TileMark.EXP_SHARE), TileMarks.of(st, listOf(holder, other), now))
        st.settings.xpShareUid = null
        assertTrue(TileMarks.of(st, listOf(holder), now).isEmpty())
    }

    @Test fun inTeamButNotMain() {
        val st = TestData.state(data, 19, 10)
        val other = TestData.mon(data, 16, 10, uid = "o"); st.mons += other; st.team += other.uid
        val box = TestData.mon(data, 16, 12, uid = "b"); st.mons += box
        assertEquals(listOf(TileMark.IN_TEAM, TileMark.MAIN), TileMarks.of(st, listOf(st.mons[0]), now))
        assertEquals(listOf(TileMark.IN_TEAM), TileMarks.of(st, listOf(other), now))
        assertTrue(TileMarks.of(st, listOf(box), now).isEmpty())
    }

    @Test fun aGroupShowsAMarkWhenAnyCopyHasIt() {
        val st = TestData.state(data, 19, 10)
        val a = TestData.mon(data, 16, 10, uid = "a"); val b = TestData.mon(data, 16, 11, uid = "b").also { it.shiny = true; it.favourite = true }
        st.mons += a; st.mons += b
        assertEquals(listOf(TileMark.FAVOURITE, TileMark.SHINY), TileMarks.of(st, listOf(a, b), now))
    }

    @Test fun newIsTodaysRuleAndGoesAwayOnceOpened() {
        val st = TestData.state(data, 19, 10); st.settings.dayStartHour = 4
        val m = st.mons[0]
        m.caughtAt = ms(1) // game day Oct 13: not new today
        assertFalse(TileMark.NEW in TileMarks.of(st, listOf(m), now))
        m.caughtAt = ms(5)
        assertTrue(TileMark.NEW in TileMarks.of(st, listOf(m), now))
        st.viewedMons += m.uid
        assertFalse(TileMark.NEW in TileMarks.of(st, listOf(m), now))
        assertFalse(TileMarks.isNew(st, m, now))
    }

    @Test fun openingMarksOnlyANewOneViewed() {
        val st = TestData.state(data, 19, 10)
        val old = TestData.mon(data, 16, 10, uid = "old"); old.caughtAt = 1_000L
        val fresh = TestData.mon(data, 16, 10, uid = "fresh"); fresh.caughtAt = 1_800_000_000_000L
        st.mons += old; st.mons += fresh
        val g = Game(data, st, clock = { 1_800_000_000_000L })
        g.markViewed("old"); g.markViewed("fresh"); g.markViewed("nobody")
        assertEquals(setOf("fresh"), st.viewedMons)
    }

    @Test fun viewedRoundTripsAndOnlyForOwnedMons() {
        val st = TestData.state(data, 19, 10)
        val m = TestData.mon(data, 16, 10, uid = "m1"); st.mons += m
        st.viewedMons += "m1"; st.viewedMons += "released"
        val back = SaveCodec.decode(SaveCodec.encode(st))
        assertEquals(setOf("m1"), back.viewedMons)
    }

    @Test fun anOldSaveWithoutTheKeyLoadsAnEmptySet() {
        val st = TestData.state(data, 19, 10); st.viewedMons += st.mons[0].uid
        val o = JSONObject(SaveCodec.encode(st))
        assertTrue(o.has("viewedMons"))
        o.remove("viewedMons")
        val back = SaveCodec.decode(o.toString())
        assertTrue(back.viewedMons.isEmpty())
        assertEquals(1, back.mons.size) // everything else loads as before
    }

    @Test fun neverWrittenIntoTheAnkimonFile() {
        val refs = AnkimonRefs.fromFiles { name ->
            when (name) {
                "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\n4,poke-ball,34,200,,\n"
                "moves.json" -> """{"tackle":{"num":33}}"""
                "pokemon.csv" -> "id,identifier,species_id,height,weight,base_experience,order,is_default\n"
                else -> null
            }
        }
        fun exported(viewed: Boolean): String {
            val st = TestData.state(data, 25, 10)
            val mon = st.mons[0]; mon.ankimonId = "v-1"; mon.level = 40
            if (viewed) st.viewedMons += mon.uid
            val img = AnkimonSchema.fresh()
            val row = """{"name":"sparkmouse","id":25,"level":10,"xp":0,"attacks":["tackle"],"individual_id":"v-1"}"""
            img.table("captured_pokemon")!!.insert(mapOf("individual_id" to "v-1", "is_main" to 1L, "data" to row))
            AnkimonExport.edit(data, st, img, refs, 1_800_000_000_000L, false)
            val t = img.table("captured_pokemon")!!
            return t.rows.single().values[t.columns.indexOf("data")] as String
        }
        assertEquals(exported(false), exported(true))
        assertFalse(exported(true).contains("viewed"))
    }

    @Test fun holdsAnItemAndCanEvolveFilters() {
        val st = TestData.state(data, 1, 16) // level evolution available
        val held = TestData.mon(data, 16, 10, uid = "h").also { it.heldItem = "lucky-egg" }
        val plain = TestData.mon(data, 16, 10, uid = "p")
        st.mons += held; st.mons += plain
        assertEquals(listOf("h"), BoxTiles.filter(st.mons, BoxFilter.HOLDS_ITEM).map { it.uid })
        val evolving = BoxTiles.filter(st.mons, BoxFilter.CAN_EVOLVE, st.team, canEvolve = { MonTags.canEvolve(data, st, it, now) })
        assertEquals(listOf(st.mons[0].uid), evolving.map { it.uid })
        assertTrue(BoxTiles.filter(st.mons, BoxFilter.CAN_EVOLVE).isEmpty()) // no answer given = nothing can evolve
        assertEquals(st.mons.size, BoxTiles.filter(st.mons, BoxFilter.ALL).size)
    }

    @Test fun tileScaleIsWholeWhenItFillsEnough() {
        assertEquals(2f, GridMath.tileScale(240, 224, 96, 96), 0f) // 2x = 192 of 224: 86 %
        assertEquals(1f, GridMath.tileScale(120, 120, 96, 96), 0f) // 1x = 80 %
        assertEquals(3f, GridMath.tileScale(300, 300, 96, 96), 0f)
    }

    @Test fun tileScaleFallsBackToExactFitWhenWholeWouldBeSmall() {
        // 184 px room: 1x = 96 = 52 % -> exact fit 1.916x
        assertEquals(184f / 96f, GridMath.tileScale(184, 184, 96, 96), 0.001f)
        // a room smaller than the sprite shrinks it
        assertEquals(0.5f, GridMath.tileScale(48, 60, 96, 96), 0.001f)
        // the shorter side decides
        assertEquals(2f, GridMath.tileScale(400, 200, 96, 96), 0f)
        assertEquals(1f, GridMath.tileScale(0, 0, 96, 96), 0f)
    }
}
