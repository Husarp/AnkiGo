package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.BackdropCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R8-03: the Encounter event carries the wild Pokémon's uid, which picks the battle background (one fight, one picture). */
class EncounterWildUidTest {
    private val data = TestData.world()

    private fun game(seed: Int = 7): Game {
        val state = TestData.state(data, 1, 5, listOf("tackle", "growl"))
        return Game(data, state, Random(seed)) { 1_800_000_000_000L }
    }

    @Test fun theEventsUidIsTheBattlesWildUid() {
        val g = game()
        val ev = g.ensureEncounter().filterIsInstance<GameEvent.Encounter>().single()
        assertTrue(ev.wildUid.isNotBlank())
        assertEquals(g.state.battle!!.wild.uid, ev.wildUid)
    }

    @Test fun theEventDefaultIsNoUidSoOldCallersStillCompile() {
        val ev = GameEvent.Encounter(1, "Wild", 5, false, Tier.NORMAL, 10, 10, false, false)
        assertEquals("", ev.wildUid)
        assertEquals(BackdropCatalog.seed(""), 0)
    }

    @Test fun theSameSeedGivesTheSamePictureFromTheEventAndFromTheSavedBattle() {
        val g = game()
        val ev = g.ensureEncounter().filterIsInstance<GameEvent.Encounter>().single()
        val types = data.species[ev.species]?.types.orEmpty()
        val played = BackdropCatalog.pick(types, BackdropCatalog.seed(ev.wildUid))             // EventPlayer.playEncounter
        val rebuilt = SaveCodec.decode(SaveCodec.encode(g.state))                                // a restart / PiP / floating window
        val w = rebuilt.battle!!.wild
        val redrawn = BackdropCatalog.pick(data.species[w.species]?.types.orEmpty(), BackdropCatalog.seed(w.uid)) // BattlePanelView.renderEnemy
        assertEquals(ev.wildUid, w.uid)
        assertEquals(played.id, redrawn.id)
    }

    @Test fun eachEncounterHasItsOwnUid() {
        val g = game()
        val uids = HashSet<String>()
        repeat(12) {
            val ev = g.ensureEncounter().filterIsInstance<GameEvent.Encounter>().single()
            assertTrue("new uid", uids.add(ev.wildUid))
            g.state.battle = null // the next wild one comes
        }
        assertEquals(12, uids.size)
    }
}
