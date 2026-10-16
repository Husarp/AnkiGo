package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Favourite Pokémon (R2-02): the flag, setFavourite, release protection, favouriteMons order and the save. */
class FavouriteTest {
    private val data = TestData.world()

    /** Team t0 (Sparkmouse 25), t1 (16), box b2 (19) and b3 (16, Lv 30), b4 (16, Lv 12), b5 (25, Lv 5). */
    private fun roster(): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        fun add(uid: String, sp: Int, lv: Int, team: Boolean) {
            s.mons += TestData.mon(data, sp, lv, listOf("tackle"), uid = uid)
            if (team) s.team += uid
        }
        add("t0", 25, 10, true); add("t1", 16, 11, true)
        add("b2", 19, 12, false); add("b3", 16, 30, false); add("b4", 16, 12, false); add("b5", 25, 5, false)
        return s
    }

    private fun game(s: GameState = roster()) = Game(data, s, Random(1)) { 0L }

    @Test fun setFavouriteTogglesAndSaysSo() {
        val g = game()
        val on = g.setFavourite("b2", true)
        assertTrue(on.ok); assertTrue(on.message.endsWith("is a favourite now."))
        assertTrue(g.state.mon("b2")!!.favourite)
        val off = g.setFavourite("b2", false)
        assertTrue(off.ok); assertTrue(off.message.endsWith("is no longer a favourite."))
        assertFalse(g.state.mon("b2")!!.favourite)
    }

    @Test fun setFavouriteOnAnUnknownOrUnsupportedUidFails() {
        val s = roster()
        s.unsupported += UnsupportedMon("ank-1", 4000, "Futuremon", null, 3, false, "not in data", null)
        val g = game(s)
        assertFalse(g.setFavourite("nope", true).ok)
        assertFalse(g.setFavourite("ank-1", true).ok)
    }

    @Test fun aFavouriteCannotBeReleasedUntilUnfavourited() {
        val g = game()
        g.setFavourite("b2", true)
        val r = g.release("b2")
        assertFalse(r.ok)
        assertTrue(r.message, r.message.startsWith("Remove ") && r.message.endsWith(" from favourites first."))
        assertTrue(g.state.mon("b2") != null)
        g.setFavourite("b2", false)
        assertTrue(g.release("b2").ok)
        assertTrue(g.state.mon("b2") == null)
    }

    @Test fun favouriteMonsAreTeamFirstThenSpeciesThenLevelDesc() {
        val s = roster()
        for (m in s.mons) m.favourite = true
        s.mon("b4")!!.favourite = false
        // team order t1 before t0 to prove it follows the team list, not species / insertion order
        s.team.clear(); s.team += listOf("t1", "t0")
        assertEquals(listOf("t1", "t0", "b3", "b2", "b5"), s.favouriteMons().map { it.uid })
        // no favourites -> empty
        for (m in s.mons) m.favourite = false
        assertTrue(s.favouriteMons().isEmpty())
    }

    @Test fun favouriteSurvivesTheSaveRoundTrip() {
        val s = roster()
        s.mon("b2")!!.favourite = true
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(back.mon("b2")!!.favourite)
        assertFalse(back.mon("t0")!!.favourite)
        assertEquals(SaveCodec.encode(s), SaveCodec.encode(back))
    }

    @Test fun anOldSaveWithoutTheKeyLoadsAsNotFavourite() {
        val b = SaveCodec.decode("""{"schema":1,"mons":[{"uid":"a","species":25,"level":5}]}""")
        assertFalse(b.mons[0].favourite)
    }
}
