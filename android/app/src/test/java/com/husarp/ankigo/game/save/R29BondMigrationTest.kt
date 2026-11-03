package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.game.ankimon.AnkimonMon
import com.husarp.ankigo.game.ankimon.AnkimonSave
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class R29BondMigrationTest {
    private val data = TestData.world()

    @Test fun everyLegacyValueConvertsOnceIncludingExactOldMaxAndNeverPlayed() {
        for (old in ((0..255).toList() + listOf(300, 999))) {
            val state = TestData.state(data)
            val mon = state.main!!.apply { friendship = old; nickname = "Keep me"; defeats = if (old == 0) 0 else 12 }
            val root = JSONObject(SaveCodec.encode(state))
            root.getJSONArray("mons").getJSONObject(0).apply { remove("bondPoints"); remove("bondHalf") }
            val back = SaveCodec.decode(root.toString())
            val expected = minOf(old * 2, 500)
            assertEquals("legacy=$old", expected, back.main!!.bondPoints)
            if (old >= 250) assertEquals(4, Bond.level(back.main!!.bondPoints))
            if (old == 0) assertEquals(0, Bond.level(back.main!!.bondPoints))
            assertEquals(old, back.main!!.friendship)
            assertEquals(mon.nickname, back.main!!.nickname)
            assertEquals(mon.defeats, back.main!!.defeats)
            assertEquals(state.team, back.team); assertEquals(mon.ivs, back.main!!.ivs); assertEquals(mon.evs, back.main!!.evs)
            assertEquals(state.cash, back.cash)
            val sameMon = JSONObject(SaveCodec.encode(back)).getJSONArray("mons").getJSONObject(0)
            sameMon.remove("bondPoints"); sameMon.remove("bondHalf")
            assertTrue("all existing Pokémon fields survive legacy=$old", root.getJSONArray("mons").getJSONObject(0).similar(sameMon))
            val current = SaveCodec.encode(back)
            assertEquals(current, SaveCodec.encode(SaveCodec.decode(current)))
            assertEquals(expected, SaveCodec.decode(current).main!!.bondPoints)
        }
    }

    @Test fun explicitZeroAndCurrentProgressAreNeverReconvertedFromLegacyFriendship() {
        val s = TestData.state(data); val m = s.main!!; m.friendship = 255
        for (points in listOf(0, 49, 125, 370, 500, 799, 800)) {
            m.bondPoints = points; m.bondHalf = if (points == 800) 0 else 1
            val back = SaveCodec.decode(SaveCodec.encode(s)).main!!
            assertEquals(points, back.bondPoints); assertEquals(m.bondHalf, back.bondHalf)
            assertEquals(255, back.friendship)
        }
    }

    @Test fun oldBattleAndPendingCatchPokemonAlsoLoadWithoutLosingTheirData() {
        val s = TestData.state(data)
        val w = TestData.mon(data, 16, 12, uid = "wild").apply { friendship = 70 }
        s.battle = Battle(w, Tier.NORMAL).apply { answers = 2; buttons += listOf(3, 4) }
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, w.uid, w, Tier.NORMAL, at = 123)
        val root = JSONObject(SaveCodec.encode(s))
        root.getJSONObject("battle").getJSONObject("wild").apply { remove("bondPoints"); remove("bondHalf") }
        root.getJSONArray("asks").getJSONObject(0).getJSONObject("wild").apply { remove("bondPoints"); remove("bondHalf") }
        val back = SaveCodec.decode(root.toString())
        assertEquals(w.uid, back.battle!!.wild.uid); assertEquals(2, back.battle!!.answers)
        assertEquals(listOf(3, 4), back.battle!!.buttons); assertEquals(w.hp, back.battle!!.wild.hp)
        assertEquals(w.uid, back.asks.single().wild!!.uid); assertEquals(70, back.asks.single().wild!!.friendship)
    }

    @Test fun newCaughtAndNewlyCreatedPokemonStartAtHeartZero() {
        val s = TestData.state(data)
        val wild = TestData.mon(data, 16, 20).apply { bondPoints = 800; friendship = 255 }
        val caught = Catching.addToCollection(data, s, wild, Tier.NORMAL, null, 123, mutableListOf())
        assertEquals(0, caught.bondPoints)
        assertEquals(0, Progression.newMon(data, 25, 20, kotlin.random.Random(1), 123).bondPoints)
    }

    @Test fun ankimonImportsConvertLegacyPointsAndNeverLowerAnEarnedBond() {
        val source = AnkimonMon("pc", true, 25, "Sparkmouse", null, 20, 0,
            emptyMap(), emptyMap(), null, null, null, listOf("tackle"), false, null, false,
            255, null, 12, null)
        val save = AnkimonSave(listOf(source), listOf("pc"), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList())
        val state = GameState()
        AnkimonMerge.merge(data, state, save, { null }, true, 123)
        val mon = state.mons.single()
        assertEquals(500, mon.bondPoints)
        mon.bondPoints = 799; mon.bondHalf = 1
        AnkimonMerge.merge(data, state, save.copy(mons = listOf(source.copy(friendship = 70))), { null }, true, 124)
        assertEquals(799, mon.bondPoints); assertEquals(1, mon.bondHalf)
        assertEquals(1, state.mons.size)
    }
}
